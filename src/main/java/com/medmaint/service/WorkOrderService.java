package com.medmaint.service;

import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.enums.NotificationType;
import com.medmaint.domain.enums.WorkOrderStatus;
import com.medmaint.domain.model.InterventionRequest;
import com.medmaint.domain.model.SparePart;
import com.medmaint.domain.model.StockTransaction;
import com.medmaint.domain.model.WorkOrder;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.persistence.repo.RequestRepo;
import com.medmaint.persistence.repo.SparePartRepo;
import com.medmaint.persistence.repo.WorkOrderRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.UnauthorizedException;
import com.medmaint.security.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cycle de vie des ordres de travail :
 * Demande → Approbation → Affectation → Planification → En cours
 * → En attente de pièce → Résolue → Validation → Clôturée.
 *
 * Règles appliquées (couche service = autorisation et intégrité) :
 * - clôture bloquée tant que diagnostic / travaux / technicien / date de fin manquent
 * - consommation de pièces vérifie le stock (défaut) et recalcule les coûts
 * - chaque transition est journalisée dans l'audit
 */
public final class WorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderService.class);

    /** Coût horaire de référence pour le calcul du coût main-d'œuvre (paramétrable). */
    private static final BigDecimal HOURLY_RATE = new BigDecimal("45.00");

    private final WorkOrderRepo repo = new WorkOrderRepo();
    private final RequestRepo requestRepo = new RequestRepo();
    private final EquipmentRepo equipmentRepo = new EquipmentRepo();
    private final SparePartRepo partRepo = new SparePartRepo();
    private final AuditService audit;
    private final NotificationService notifications;

    public WorkOrderService(AuditService audit, NotificationService notifications) {
        this.audit = audit;
        this.notifications = notifications;
    }

    public Page<WorkOrder> search(WorkOrderRepo.Filter f, int page, int pageSize, String sort, String dir) {
        Session.get().require(Permission.WORKORDER_VIEW);
        return repo.search(f, page, pageSize, sort, dir);
    }

    public WorkOrder get(long id) {
        Session.get().require(Permission.WORKORDER_VIEW);
        return repo.findById(id)
                .orElseThrow(() -> new ValidationException("Ordre de travail introuvable."));
    }

    /** Créé automatiquement à l'affectation d'une demande. */
    public WorkOrder createFromRequest(InterventionRequest r, long technicianId) {
        WorkOrder existing = repo.findAll(
                        new WorkOrderRepo.Filter(null, null, null, null, null, null, r.getId(), null, null), 1)
                .stream().findFirst().orElse(null);
        if (existing != null) {
            existing.setTechnicianId(technicianId);
            existing.setStatus(WorkOrderStatus.ASSIGNEE.name());
            repo.update(existing);
            requestRepo.linkWorkOrder(r.getId(), existing.getId());
            return existing;
        }
        WorkOrder w = new WorkOrder();
        w.setWoNo(repo.nextWoNo());
        w.setRequestId(r.getId());
        w.setEquipmentId(r.getEquipmentId());
        w.setTechnicianId(technicianId);
        w.setPriority(r.getPriority());
        w.setStatus(WorkOrderStatus.ASSIGNEE.name());
        w.setOpenedAt(LocalDateTime.now());
        w.setCreatedBy(Session.get() != null ? Session.get().userId() : null);
        w.setLaborCost(BigDecimal.ZERO);
        w.setPartsCost(BigDecimal.ZERO);
        w.setTotalCost(BigDecimal.ZERO);
        long id = repo.insert(w);
        w.setId(id);
        requestRepo.linkWorkOrder(r.getId(), id);
        audit.created("Ordre de travail", w.getWoNo(), r.getEquipmentCode());
        return repo.findById(id).orElse(w);
    }

    /** Création directe (sans demande) — ex. maintenance corrective planifiée. */
    public WorkOrder createDirect(long equipmentId, long technicianId, String priority,
                                  String problem, LocalDateTime scheduled) {
        Session.get().require(Permission.WORKORDER_UPDATE);
        WorkOrder w = new WorkOrder();
        w.setWoNo(repo.nextWoNo());
        w.setEquipmentId(equipmentId);
        w.setTechnicianId(technicianId);
        w.setPriority(priority != null ? priority : "NORMALE");
        w.setStatus(technicianId > 0 ? WorkOrderStatus.ASSIGNEE.name() : WorkOrderStatus.DEMANDE.name());
        w.setOpenedAt(LocalDateTime.now());
        w.setScheduledAt(scheduled);
        w.setComments(problem);
        w.setCreatedBy(Session.get().userId());
        w.setLaborCost(BigDecimal.ZERO);
        w.setPartsCost(BigDecimal.ZERO);
        w.setTotalCost(BigDecimal.ZERO);
        long id = repo.insert(w);
        w.setId(id);
        audit.created("Ordre de travail", w.getWoNo(), "création directe");
        return w;
    }

    // ---------- Transitions ----------

    public void approve(long id) {
        Session.get().require(Permission.REQUEST_APPROVE);
        WorkOrder w = transitionGuard(id, WorkOrderStatus.APPROUVEE, WorkOrderStatus.DEMANDE);
        audit.action("APPROVE", "Ordre de travail", w.getWoNo(), w.getWoNo());
    }

    public void schedule(long id, LocalDateTime scheduledAt) {
        Session s = Session.get();
        if (!(s.has(Permission.WORKORDER_UPDATE) || s.isAdmin())) {
            s.require(Permission.WORKORDER_UPDATE);
        }
        if (scheduledAt == null) {
            throw new ValidationException("La date de planification est obligatoire.");
        }
        WorkOrder w = get(id);
        w.setScheduledAt(scheduledAt);
        if (w.statusEnum() == WorkOrderStatus.ASSIGNEE || w.statusEnum() == WorkOrderStatus.APPROUVEE) {
            w.setStatus(WorkOrderStatus.PLANIFIEE.name());
        }
        repo.update(w);
        audit.updated("Ordre de travail", w.getWoNo(), w.getWoNo(), null,
                String.valueOf(scheduledAt));
    }

    /** Démarrage : passe En cours + horodatage. L'équipement passe en maintenance. */
    public void start(long id) {
        Session.get().require(Permission.WORKORDER_EXECUTE);
        WorkOrder w = get(id);
        WorkOrderStatus st = w.statusEnum();
        if (st != WorkOrderStatus.ASSIGNEE && st != WorkOrderStatus.PLANIFIEE
                && st != WorkOrderStatus.APPROUVEE) {
            throw new ValidationException("Impossible de démarrer un ordre " + st.label() + ".");
        }
        requireOwnOrManager(w);
        w.setStatus(WorkOrderStatus.EN_COURS.name());
        w.setStartedAt(LocalDateTime.now());
        repo.update(w);
        equipmentRepo.updateStatus(w.getEquipmentId(), EquipmentStatus.EN_MAINTENANCE.name());
        audit.action("START", "Ordre de travail", w.getWoNo(), w.getWoNo());
        log.info("Intervention {} démarrée", w.getWoNo());
    }

    public void waitingParts(long id, String reason) {
        Session.get().require(Permission.WORKORDER_EXECUTE);
        WorkOrder w = get(id);
        requireOwnOrManager(w);
        w.setStatus(WorkOrderStatus.EN_ATTENTE_PIECE.name());
        if (reason != null && !reason.isBlank()) {
            w.setComments(append(w.getComments(), reason));
        }
        repo.update(w);
        audit.action("WAIT_PARTS", "Ordre de travail", w.getWoNo(), w.getWoNo());
    }

    /** Résolution : vérifie les champs obligatoires puis passe Résolue. */
    public void resolve(long id, String diagnosis, String workPerformed, String failureCause,
                        String correctiveAction, BigDecimal laborHours, BigDecimal downtimeHours) {
        Session.get().require(Permission.WORKORDER_EXECUTE);
        WorkOrder w = get(id);
        if (w.getTechnicianId() == null) {
            throw new ValidationException("Un technicien doit être affecté avant la résolution.");
        }
        if (isBlank(diagnosis)) {
            throw new ValidationException("Le diagnostic est obligatoire pour résoudre l'intervention.");
        }
        if (isBlank(workPerformed)) {
            throw new ValidationException("Les travaux réalisés sont obligatoires pour résoudre l'intervention.");
        }
        WorkOrderStatus st = w.statusEnum();
        if (st != WorkOrderStatus.EN_COURS && st != WorkOrderStatus.EN_ATTENTE_PIECE) {
            throw new ValidationException("Seule une intervention en cours peut être résolue.");
        }
        w.setDiagnosis(diagnosis);
        w.setWorkPerformed(workPerformed);
        w.setFailureCause(failureCause);
        w.setCorrectiveAction(correctiveAction);
        w.setLaborHours(laborHours);
        w.setDowntimeHours(downtimeHours);
        w.setCompletedAt(LocalDateTime.now());
        w.setStatus(WorkOrderStatus.RESOLUE.name());
        recalcLaborCost(w);
        repo.update(w);
        // Mise à jour de la demande liée
        if (w.getRequestId() != null) {
            requestRepo.updateStatus(w.getRequestId(),
                    com.medmaint.domain.enums.RequestStatus.RESOLUE.name());
        }
        audit.action("RESOLVE", "Ordre de travail", w.getWoNo(), w.getWoNo());
        notifications.notifyRole("ENGINEER", NotificationType.VALIDATION_REQUISE,
                "Validation requise — " + w.getWoNo(),
                w.getEquipmentCode() + " : intervention à valider", "WARN", "WORK_ORDER", w.getId());
    }

    /** Validation par un ingénieur / responsable. */
    public void validate(long id, String comment) {
        Session.get().require(Permission.WORKORDER_VALIDATE);
        WorkOrder w = get(id);
        if (w.statusEnum() != WorkOrderStatus.RESOLUE) {
            throw new ValidationException("Seule une intervention résolue peut être validée.");
        }
        if (isBlank(w.getDiagnosis()) || isBlank(w.getWorkPerformed())) {
            throw new ValidationException("Diagnostic et travaux réalisés sont requis avant validation.");
        }
        w.setStatus(WorkOrderStatus.VALIDEE.name());
        w.setValidatedAt(LocalDateTime.now());
        w.setValidatedBy(Session.get().userId());
        if (comment != null && !comment.isBlank()) {
            w.setComments(append(w.getComments(), "Validation : " + comment));
        }
        repo.update(w);
        audit.action("VALIDATE", "Ordre de travail", w.getWoNo(), w.getWoNo());
    }

    /** Clôture : dernière étape ; l'équipement redevient En service. */
    public void close(long id) {
        Session.get().require(Permission.WORKORDER_VALIDATE);
        WorkOrder w = get(id);
        if (w.statusEnum() != WorkOrderStatus.VALIDEE) {
            throw new ValidationException("L'ordre doit être validé avant clôture.");
        }
        if (isBlank(w.getDiagnosis()) || isBlank(w.getWorkPerformed())
                || w.getTechnicianId() == null || w.getCompletedAt() == null) {
            throw new ValidationException(
                    "Clôture impossible : diagnostic, travaux, technicien et date de fin sont obligatoires.");
        }
        w.setStatus(WorkOrderStatus.CLOTUREE.name());
        w.setClosedAt(LocalDateTime.now());
        repo.update(w);
        equipmentRepo.updateStatus(w.getEquipmentId(), EquipmentStatus.EN_SERVICE.name());
        if (w.getRequestId() != null) {
            requestRepo.updateStatus(w.getRequestId(),
                    com.medmaint.domain.enums.RequestStatus.CLOTUREE.name());
        }
        audit.action("CLOSE", "Ordre de travail", w.getWoNo(), w.getWoNo());
        notifications.notifyRole("MANAGER", NotificationType.INTERVENTION_TERMINEE,
                "Intervention terminée — " + w.getWoNo(),
                w.getEquipmentCode() + " : " + w.getEquipmentName(), "SUCCESS", "WORK_ORDER", w.getId());
        log.info("Ordre {} clôturé", w.getWoNo());
    }

    /** Enregistre les champs d'exécution (diagnostic, travaux…) sans transition. */
    public void saveExecution(long id, String diagnosis, String failureCause,
                              String correctiveAction, String workPerformed, String failureType,
                              BigDecimal laborHours, BigDecimal downtimeHours, String comments) {
        Session.get().require(Permission.WORKORDER_EXECUTE);
        WorkOrder w = get(id);
        requireOwnOrManager(w);
        w.setDiagnosis(diagnosis);
        w.setFailureCause(failureCause);
        w.setCorrectiveAction(correctiveAction);
        w.setWorkPerformed(workPerformed);
        w.setFailureType(failureType);
        w.setLaborHours(laborHours);
        w.setDowntimeHours(downtimeHours);
        w.setComments(comments);
        recalcLaborCost(w);
        repo.update(w);
        audit.updated("Ordre de travail", w.getWoNo(), w.getWoNo(), null, "fiches d'exécution");
    }

    /** Affectation d'un technicien (réaffectation possible). */
    public void assignTechnician(long id, long technicianId) {
        Session.get().require(Permission.REQUEST_ASSIGN);
        WorkOrder w = get(id);
        w.setTechnicianId(technicianId);
        if (w.statusEnum() == WorkOrderStatus.DEMANDE || w.statusEnum() == WorkOrderStatus.APPROUVEE) {
            w.setStatus(WorkOrderStatus.ASSIGNEE.name());
        }
        repo.update(w);
        if (w.getRequestId() != null) {
            InterventionRequest r = requestRepo.findById(w.getRequestId()).orElse(null);
            if (r != null) {
                r.setAssignedTechnicianId(technicianId);
                r.setStatus(com.medmaint.domain.enums.RequestStatus.ASSIGNEE.name());
                requestRepo.update(r);
            }
        }
        audit.action("ASSIGN", "Ordre de travail", w.getWoNo(), "technicien#" + technicianId);
    }

    // ---------- Pièces ----------

    /**
     * Consomme une pièce sur l'ordre : vérifie le stock (sauf override admin),
     * enregistre la transaction, décrémente le stock, recalcule les coûts.
     */
    public void consumePart(long workOrderId, long partId, int quantity, boolean allowNegative,
                            String note) {
        Session s = Session.get();
        s.require(Permission.WORKORDER_EXECUTE);
        if (quantity <= 0) {
            throw new ValidationException("La quantité doit être supérieure à zéro.");
        }
        SparePart part = partRepo.findById(partId)
                .orElseThrow(() -> new ValidationException("Pièce introuvable."));
        if (part.getQuantity() < quantity) {
            if (!allowNegative || !s.isAdmin()) {
                throw new ValidationException(
                        "Stock insuffisant pour " + part.getPartNo() + " (disponible : "
                                + part.getQuantity() + ", demandé : " + quantity + ").");
            }
        }
        WorkOrder w = get(workOrderId);
        int newQty = part.getQuantity() - quantity;
        if (newQty < 0 && !s.isAdmin()) {
            throw new ValidationException("Le stock ne peut pas devenir négatif.");
        }
        partRepo.updateQuantity(partId, newQty);

        StockTransaction tx = new StockTransaction();
        tx.setPartId(partId);
        tx.setTxType("USAGE");
        tx.setQuantity(quantity);
        tx.setUnitCost(part.getUnitCost());
        tx.setWorkOrderId(workOrderId);
        tx.setEquipmentId(w.getEquipmentId());
        tx.setUserId(s.userId());
        tx.setNote(note);
        partRepo.insertTransaction(tx);

        recalcPartsCost(w);
        audit.record("CONSUME", "Pièce de rechange", part.getPartNo(), part.getName(),
                String.valueOf(part.getQuantity()), String.valueOf(newQty));

        // Alerte de stock bas
        if (newQty < part.getMinQuantity()) {
            notifications.notifyRole("MANAGER", NotificationType.STOCK_FAIBLE,
                    "Stock faible — " + part.getPartNo(),
                    part.getName() + " : " + newQty + " unité(s), minimum " + part.getMinQuantity(),
                    "WARN", "SPARE_PART", partId);
        }
    }

    /** Annule une consommation de pièce (remboursement de stock). */
    public void rollbackPart(long workOrderId, long partId, int quantity) {
        Session.get().require(Permission.WORKORDER_EXECUTE);
        SparePart part = partRepo.findById(partId)
                .orElseThrow(() -> new ValidationException("Pièce introuvable."));
        partRepo.updateQuantity(partId, part.getQuantity() + quantity);
        StockTransaction tx = new StockTransaction();
        tx.setPartId(partId);
        tx.setTxType("ENTREE");
        tx.setQuantity(quantity);
        tx.setUnitCost(part.getUnitCost());
        tx.setWorkOrderId(workOrderId);
        tx.setUserId(Session.get().userId());
        tx.setNote("Annulation de consommation");
        partRepo.insertTransaction(tx);
        WorkOrder w = get(workOrderId);
        recalcPartsCost(w);
    }

    public List<StockTransaction> partsOf(long workOrderId) {
        return partRepo.findTransactionsByWorkOrder(workOrderId);
    }

    private void recalcPartsCost(WorkOrder w) {
        BigDecimal total = partRepo.findTransactionsByWorkOrder(w.getId()).stream()
                .filter(t -> "USAGE".equals(t.getTxType()))
                .map(StockTransaction::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        w.setPartsCost(total);
        w.setTotalCost(total.add(w.getLaborCost() != null ? w.getLaborCost() : BigDecimal.ZERO));
        repo.update(w);
    }

    private void recalcLaborCost(WorkOrder w) {
        BigDecimal hours = w.getLaborHours() != null ? w.getLaborHours() : BigDecimal.ZERO;
        BigDecimal labor = HOURLY_RATE.multiply(hours);
        w.setLaborCost(labor);
        BigDecimal parts = w.getPartsCost() != null ? w.getPartsCost() : BigDecimal.ZERO;
        w.setTotalCost(labor.add(parts));
    }

    // ---------- Utilitaires ----------

    private WorkOrder transitionGuard(long id, WorkOrderStatus target, WorkOrderStatus... allowedFrom) {
        WorkOrder w = get(id);
        boolean ok = false;
        for (WorkOrderStatus a : allowedFrom) {
            if (w.statusEnum() == a) ok = true;
        }
        if (!ok) {
            throw new ValidationException("Transition impossible : l'ordre est "
                    + w.statusEnum().label() + ".");
        }
        w.setStatus(target.name());
        repo.update(w);
        return w;
    }

    private void requireOwnOrManager(WorkOrder w) {
        Session s = Session.get();
        if (s.isAdmin() || s.has(Permission.WORKORDER_VALIDATE)) {
            return;
        }
        if (s.role() != com.medmaint.security.Role.TECHNICIAN) {
            return;
        }
        // Un technicien n'exécute que ses propres interventions :
        // on rapproche l'utilisateur de sa fiche technicien.
        long techId = new com.medmaint.persistence.repo.TechnicianRepo()
                .findByUserId(s.userId()).map(t -> t.getId()).orElse(-1L);
        if (w.getTechnicianId() == null || w.getTechnicianId() != techId) {
            throw new UnauthorizedException("Vous n'êtes pas affecté à cet ordre de travail.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String append(String base, String add) {
        if (isBlank(base)) return add;
        return base + "\n" + add;
    }

    public WorkOrderRepo repo() {
        return repo;
    }
}
