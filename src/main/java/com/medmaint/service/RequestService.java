package com.medmaint.service;

import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.enums.NotificationType;
import com.medmaint.domain.enums.Priority;
import com.medmaint.domain.enums.RequestStatus;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.InterventionRequest;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.persistence.repo.RequestRepo;
import com.medmaint.persistence.repo.TechnicianRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Demandes d'intervention : création (dont flux « panne critique »),
 * approbation / rejet, affectation technicien, conversion en ordre de travail.
 */
public final class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);

    private final RequestRepo repo = new RequestRepo();
    private final EquipmentRepo equipmentRepo = new EquipmentRepo();
    private final TechnicianRepo technicianRepo = new TechnicianRepo();
    private final AuditService audit;
    private final NotificationService notifications;
    private final WorkOrderService workOrders;

    public RequestService(AuditService audit, NotificationService notifications, WorkOrderService workOrders) {
        this.audit = audit;
        this.notifications = notifications;
        this.workOrders = workOrders;
    }

    public Page<InterventionRequest> search(RequestRepo.Filter f, int page, int pageSize,
                                            String sort, String dir) {
        Session.get().require(Permission.REQUEST_VIEW);
        return repo.search(f, page, pageSize, sort, dir);
    }

    public List<InterventionRequest> recent(int limit) {
        Session.get().require(Permission.REQUEST_VIEW);
        return repo.findAll(new RequestRepo.Filter(null, null, null, null, null, null, null, null, null), limit);
    }

    public InterventionRequest get(long id) {
        Session.get().require(Permission.REQUEST_VIEW);
        return repo.findById(id)
                .orElseThrow(() -> new ValidationException("Demande introuvable."));
    }

    /**
     * Scénario B (signalement) : crée la demande « Nouvelle ».
     * Priorité critique → notifications manager + équipe biomédicale.
     */
    public InterventionRequest create(InterventionRequest r) {
        Session s = Session.get();
        s.require(Permission.REQUEST_CREATE);

        Equipment eq = equipmentRepo.findById(r.getEquipmentId())
                .orElseThrow(() -> new ValidationException("Équipement introuvable."));
        if (r.getProblemDescription() == null || r.getProblemDescription().isBlank()) {
            throw new ValidationException("La description du problème est obligatoire.");
        }
        if (r.getProblemDescription().trim().length() < 10) {
            throw new ValidationException("La description du problème doit contenir au moins 10 caractères.");
        }
        if (r.getPriority() == null) {
            throw new ValidationException("La priorité est obligatoire.");
        }
        if (eq.isRetired()) {
            throw new ValidationException("Cet équipement est " + eq.statusEnum().label()
                    + " : signalement impossible.");
        }

        r.setRequestNo(repo.nextRequestNo());
        r.setRequesterId(s.userId());
        r.setReportedAt(LocalDateTime.now());
        r.setStatus(RequestStatus.NOUVELLE.name());
        if (r.getDepartmentId() <= 0) {
            r.setDepartmentId(eq.getDepartmentId());
        }
        if (r.getAvailability() == null) {
            r.setAvailability("INDISPONIBLE");
        }
        long id = repo.insert(r);
        r.setId(id);
        r.setEquipmentCode(eq.getCode());
        r.setEquipmentName(eq.getName());

        audit.created("Demande d'intervention", r.getRequestNo(),
                eq.getCode() + " — priorité " + Priority.fromCode(r.getPriority()).label());

        // Équipement en attente de réparation si indisponible
        if ("INDISPONIBLE".equals(r.getAvailability())
                && eq.statusEnum() == EquipmentStatus.EN_SERVICE) {
            equipmentRepo.updateStatus(eq.getId(), EquipmentStatus.EN_ATTENTE_REPARATION.name());
        }

        // ---- Flux panne critique ----
        Priority p = Priority.fromCode(r.getPriority());
        if (p == Priority.CRITIQUE || r.isSafetyImpact()) {
            notifications.notifyRoles(
                    List.of("MANAGER", "ENGINEER"),
                    NotificationType.PANNE_CRITIQUE,
                    "Panne critique — " + eq.getCode(),
                    eq.getName() + " (" + eq.getDepartmentName() + ") : "
                            + truncate(r.getProblemDescription(), 200),
                    "CRITICAL", "REQUEST", id);
            log.warn("Panne critique signalée : {} / {}", r.getRequestNo(), eq.getCode());
        } else {
            notifications.notifyRole("MANAGER", NotificationType.NOUVELLE_DEMANDE,
                    "Nouvelle demande " + r.getRequestNo(),
                    eq.getCode() + " — " + eq.getName(), "INFO", "REQUEST", id);
        }
        return r;
    }

    /** Approbation : passe en Acceptée. */
    public void approve(long id, String comment) {
        Session.get().require(Permission.REQUEST_APPROVE);
        InterventionRequest r = get(id);
        if (r.statusEnum() == RequestStatus.REJETEE) {
            throw new ValidationException("Une demande rejetée ne peut pas être approuvée.");
        }
        if (!r.statusEnum().isOpen()) {
            throw new ValidationException("Cette demande est déjà " + r.statusEnum().label() + ".");
        }
        r.setStatus(RequestStatus.ACCEPTEE.name());
        if (comment != null && !comment.isBlank()) {
            r.setResolutionNote(comment);
        }
        repo.update(r);
        audit.action("APPROVE", "Demande d'intervention", r.getRequestNo(), r.getRequestNo());
    }

    /** Rejet avec motif obligatoire. */
    public void reject(long id, String reason) {
        Session.get().require(Permission.REQUEST_APPROVE);
        InterventionRequest r = get(id);
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Le motif de rejet est obligatoire.");
        }
        r.setStatus(RequestStatus.REJETEE.name());
        r.setRejectionReason(reason);
        repo.update(r);
        notifications.notifyUser(r.getRequesterId(), NotificationType.DEMANDE_MAJ,
                "Demande " + r.getRequestNo() + " rejetée", reason, "WARN", "REQUEST", id);
        audit.action("REJECT", "Demande d'intervention", r.getRequestNo(), r.getRequestNo());
    }

    /**
     * Affectation : assigne un technicien et crée l'ordre de travail
     * (Demande → Approbation → Affectation).
     */
    public void assign(long requestId, long technicianId) {
        Session.get().require(Permission.REQUEST_ASSIGN);
        InterventionRequest r = get(requestId);
        if (r.statusEnum() == RequestStatus.REJETEE) {
            throw new ValidationException("Une demande rejetée ne peut pas être affectée.");
        }
        var tech = technicianRepo.findById(technicianId)
                .orElseThrow(() -> new ValidationException("Technicien introuvable."));
        if (!"ACTIF".equalsIgnoreCase(tech.getStatus())) {
            throw new ValidationException("Ce technicien n'est pas actif.");
        }

        r.setAssignedTechnicianId(technicianId);
        r.setStatus(RequestStatus.ASSIGNEE.name());
        repo.update(r);

        // L'ordre de travail est créé (ou mis à jour) lors de l'affectation
        var wo = workOrders.createFromRequest(r, technicianId);

        notifications.notifyRole("TECHNICIAN", NotificationType.INTERVENTION_ASSIGNEE,
                "Intervention assignée — " + wo.getWoNo(),
                r.getEquipmentCode() + " : " + truncate(r.getProblemDescription(), 160),
                "INFO", "WORK_ORDER", wo.getId());

        audit.action("ASSIGN", "Demande d'intervention", r.getRequestNo(),
                "→ " + tech.getFullName());
        log.info("Demande {} affectée à {}", r.getRequestNo(), tech.getFullName());
    }

    public void setStatus(long id, RequestStatus status) {
        Session.get().require(Permission.REQUEST_VIEW);
        repo.updateStatus(id, status.name());
    }

    public void delete(long id) {
        Session.get().require(Permission.REQUEST_APPROVE);
        InterventionRequest r = get(id);
        if (r.getWorkOrderId() != null) {
            throw new ValidationException("Impossible : un ordre de travail est lié à cette demande.");
        }
        repo.delete(id);
        audit.deleted("Demande d'intervention", r.getRequestNo(), r.getEquipmentName(), null);
    }

    public long countOpen() {
        return repo.countOpen();
    }

    public long countUrgentOpen() {
        return repo.countUrgentOpen();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s.substring(0, max) + "…" : s;
    }

    public RequestRepo repo() {
        return repo;
    }
}
