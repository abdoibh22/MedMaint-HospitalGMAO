package com.medmaint.service;

import com.medmaint.domain.enums.ChecklistResult;
import com.medmaint.domain.enums.MaintenanceFrequency;
import com.medmaint.domain.enums.NotificationType;
import com.medmaint.domain.enums.PlanStatus;
import com.medmaint.domain.model.Checklist;
import com.medmaint.domain.model.MaintenancePlan;
import com.medmaint.domain.model.MaintenanceRecord;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.ChecklistRepo;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.persistence.repo.PlanRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maintenance préventive : plans, calcul automatique des échéances,
 * exécution de checklist, mise à jour de l'historique et des dates
 * dernière / prochaine maintenance de l'équipement.
 */
public final class PreventiveService {

    private static final Logger log = LoggerFactory.getLogger(PreventiveService.class);

    private final PlanRepo repo = new PlanRepo();
    private final ChecklistRepo checklistRepo = new ChecklistRepo();
    private final EquipmentRepo equipmentRepo = new EquipmentRepo();
    private final AuditService audit;
    private final NotificationService notifications;

    public PreventiveService(AuditService audit, NotificationService notifications) {
        this.audit = audit;
        this.notifications = notifications;
    }

    public Page<MaintenancePlan> search(PlanRepo.Filter f, int page, int pageSize, String sort, String dir) {
        Session.get().require(Permission.PM_VIEW);
        return repo.search(f, page, pageSize, sort, dir);
    }

    public Optional<MaintenancePlan> get(long id) {
        Session.get().require(Permission.PM_VIEW);
        return repo.findById(id);
    }

    /** État calculé d'un plan (à venir / aujourd'hui / en retard…). */
    public PlanStatus statusOf(MaintenancePlan p) {
        return PlanStatus.forDue(p.getNextDue(), false, p.isActive(), false);
    }

    public MaintenancePlan create(MaintenancePlan p) {
        Session.get().require(Permission.PM_MANAGE);
        validate(p);
        if (p.getStatus() == null) p.setStatus(PlanStatus.A_VENIR.name());
        long id = repo.insert(p);
        p.setId(id);
        audit.created("Plan de maintenance", String.valueOf(id),
                p.getEquipmentCode() + " — " + p.getMaintenanceType());
        return p;
    }

    public void update(MaintenancePlan p) {
        Session.get().require(Permission.PM_MANAGE);
        validate(p);
        MaintenancePlan before = repo.findById(p.getId())
                .orElseThrow(() -> new ValidationException("Plan introuvable."));
        repo.update(p);
        audit.updated("Plan de maintenance", String.valueOf(p.getId()), p.getMaintenanceType(),
                String.valueOf(before.getNextDue()), String.valueOf(p.getNextDue()));
    }

    public void delete(long id) {
        Session.get().require(Permission.PM_MANAGE);
        MaintenancePlan p = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Plan introuvable."));
        repo.delete(id);
        audit.deleted("Plan de maintenance", String.valueOf(id), p.getMaintenanceType(), null);
    }

    public void setActive(long id, boolean active) {
        Session.get().require(Permission.PM_MANAGE);
        MaintenancePlan p = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Plan introuvable."));
        p.setActive(active);
        p.setStatus(active ? PlanStatus.A_VENIR.name() : PlanStatus.ANNULEE.name());
        repo.update(p);
        audit.action(active ? "ENABLE" : "DISABLE", "Plan de maintenance", String.valueOf(id),
                p.getMaintenanceType());
    }

    /**
     * Scénario C : exécution d'une maintenance préventive avec checklist.
     * Met à jour plan, équipement (dernière/prochaine maintenance) et historique.
     */
    public MaintenanceRecord execute(long planId, List<MaintenanceRecord.ChecklistResultEntry> results,
                                     String observations, Integer durationMin, BigDecimal cost) {
        Session.get().require(Permission.PM_EXECUTE);
        MaintenancePlan plan = repo.findById(planId)
                .orElseThrow(() -> new ValidationException("Plan de maintenance introuvable."));
        if (!plan.isActive()) {
            throw new ValidationException("Ce plan est inactif.");
        }
        ChecklistResult overall = overallResult(results);

        MaintenanceRecord rec = new MaintenanceRecord();
        rec.setEquipmentId(plan.getEquipmentId());
        rec.setPlanId(planId);
        rec.setChecklistId(plan.getChecklistId());
        rec.setRecordType("PREVENTIVE");
        rec.setPerformedBy(Session.get().userId());
        rec.setPerformedAt(LocalDateTime.now());
        rec.setResult(overall.name());
        rec.setObservations(observations);
        rec.setDurationMin(durationMin);
        rec.setCost(cost != null ? cost : BigDecimal.ZERO);
        long recId = checklistRepo.insertRecord(rec);
        rec.setId(recId);

        if (results != null) {
            for (MaintenanceRecord.ChecklistResultEntry e : results) {
                checklistRepo.insertResult(recId, e);
            }
        }

        // 1. Met à jour le plan : dernière réalisée + prochaine échéance
        LocalDate today = LocalDate.now();
        plan.setLastPerformed(today);
        MaintenanceFrequency freq = MaintenanceFrequency.fromCode(plan.getFrequency());
        plan.setNextDue(freq.nextDate(today, plan.getCustomDays()));
        plan.setStatus(PlanStatus.A_VENIR.name());
        repo.update(plan);

        // 2. Met à jour l'équipement
        equipmentRepo.updateMaintenanceDates(plan.getEquipmentId(), today, plan.getNextDue());

        // 3. Historique = record créé ci-dessus (visible dans l'onglet Maintenance)
        audit.record("MAINTENANCE_DONE", "Maintenance préventive", String.valueOf(planId),
                plan.getEquipmentCode() + " — " + plan.getMaintenanceType(),
                String.valueOf(plan.getLastPerformed()), "prochaine : " + plan.getNextDue());

        log.info("Maintenance préventive réalisée : plan {} → prochaine {}",
                planId, plan.getNextDue());
        return checklistRepo.findRecord(recId).orElse(rec);
    }

    public List<Checklist> checklists() {
        return checklistRepo.findAllChecklists();
    }

    public Optional<Checklist> checklist(long id) {
        return checklistRepo.findChecklist(id);
    }

    public List<Checklist.ChecklistItem> checklistItems(long id) {
        return checklistRepo.findItems(id);
    }

    /** Vues : en retard / aujourd'hui / cette semaine / à venir. */
    public List<MaintenancePlan> dueWithin(int days) {
        Session.get().require(Permission.PM_VIEW);
        return repo.findDueWithin(days);
    }

    public long countOverdue() {
        return repo.countOverdue();
    }

    public long countDueIn(int days) {
        return repo.countDueIn(days);
    }

    /** Génère les notifications d'échéance (appelé au démarrage et au rafraîchissement). */
    public void scanDue() {
        try {
            LocalDate today = LocalDate.now();
            for (MaintenancePlan p : repo.findDueWithin(7)) {
                String msg = p.getEquipmentCode() + " — " + p.getMaintenanceType();
                if (p.getNextDue() != null && p.getNextDue().isBefore(today)) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(p.getNextDue(), today);
                    notifications.notifyRole("ENGINEER", NotificationType.PM_EN_RETARD,
                            "Maintenance en retard (" + days + " j) — " + p.getEquipmentCode(),
                            msg, "WARN", "MAINTENANCE_PLAN", p.getId());
                } else if (p.getNextDue() != null && !p.getNextDue().isAfter(today)) {
                    notifications.notifyRole("ENGINEER", NotificationType.PM_A_VENIR,
                            "Maintenance préventive à faire aujourd'hui — " + p.getEquipmentCode(),
                            msg, "WARN", "MAINTENANCE_PLAN", p.getId());
                } else {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(today, p.getNextDue());
                    notifications.notifyRole("ENGINEER", NotificationType.PM_A_VENIR,
                            "Maintenance préventive prévue dans " + days
                                    + (days <= 1 ? " jour — " : " jours — ") + p.getEquipmentCode(),
                            msg, "INFO", "MAINTENANCE_PLAN", p.getId());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Scan maintenance préventive : {}", e.getMessage());
        }
    }

    // ---------- Checklists (CRUD) ----------

    public Checklist saveChecklist(Checklist c, List<Checklist.ChecklistItem> items) {
        Session.get().require(Permission.PM_MANAGE);
        if (c.getName() == null || c.getName().isBlank()) {
            throw new ValidationException("Le nom de la checklist est obligatoire.");
        }
        if (items == null || items.isEmpty()) {
            throw new ValidationException("La checklist doit contenir au moins un item.");
        }
        long id;
        if (c.getId() > 0) {
            checklistRepo.updateChecklist(c);
            checklistRepo.replaceItems(c.getId(), items);
            id = c.getId();
            audit.updated("Checklist", String.valueOf(id), c.getName(), null, c.getName());
        } else {
            id = checklistRepo.insertChecklist(c);
            checklistRepo.replaceItems(id, items);
            audit.created("Checklist", String.valueOf(id), c.getName());
        }
        c.setId(id);
        return c;
    }

    public void deleteChecklist(long id) {
        Session.get().require(Permission.PM_MANAGE);
        long used = JdbcCountPlans(id);
        if (used > 0) {
            throw new ValidationException("Impossible : cette checklist est utilisée par "
                    + used + " plan(s) de maintenance.");
        }
        checklistRepo.deleteChecklist(id);
        audit.deleted("Checklist", String.valueOf(id), null, null);
    }

    private long JdbcCountPlans(long checklistId) {
        return com.medmaint.persistence.Jdbc.count(
                "SELECT COUNT(*) FROM maintenance_plans WHERE checklist_id=?", checklistId);
    }

    // ---------- Historique ----------

    public List<MaintenanceRecord> historyForEquipment(long equipmentId, int limit) {
        Session.get().require(Permission.EQUIPMENT_VIEW);
        return checklistRepo.withResults(checklistRepo.findRecordsByEquipment(equipmentId, limit));
    }

    public List<MaintenanceRecord> recentHistory(int limit) {
        return checklistRepo.withResults(checklistRepo.findRecentRecords(limit));
    }

    public ChecklistRepo checklistRepo() {
        return checklistRepo;
    }

    public PlanRepo repo() {
        return repo;
    }

    // ---------- Validations ----------

    private void validate(MaintenancePlan p) {
        if (p.getEquipmentId() <= 0) {
            throw new ValidationException("L'équipement est obligatoire.");
        }
        if (p.getMaintenanceType() == null || p.getMaintenanceType().isBlank()) {
            throw new ValidationException("Le type de maintenance est obligatoire.");
        }
        if (p.getFrequency() == null || p.getFrequency().isBlank()) {
            throw new ValidationException("La fréquence est obligatoire.");
        }
        if (p.getNextDue() == null) {
            throw new ValidationException("La prochaine échéance est obligatoire.");
        }
        MaintenanceFrequency f = MaintenanceFrequency.fromCode(p.getFrequency());
        if (f == MaintenanceFrequency.PERSONNALISEE
                && (p.getCustomDays() == null || p.getCustomDays() <= 0)) {
            throw new ValidationException(
                    "Une fréquence personnalisée exige un nombre de jours supérieur à 0.");
        }
        if (p.getLastPerformed() != null && p.getNextDue().isBefore(p.getLastPerformed())) {
            throw new ValidationException(
                    "La prochaine échéance ne peut pas précéder la dernière réalisation.");
        }
        if (p.getEstimatedDurationMin() != null && p.getEstimatedDurationMin() < 0) {
            throw new ValidationException("La durée estimée ne peut pas être négative.");
        }
    }

    private ChecklistResult overallResult(List<MaintenanceRecord.ChecklistResultEntry> results) {
        if (results == null || results.isEmpty()) {
            return ChecklistResult.CONFORME;
        }
        boolean anyFail = results.stream()
                .anyMatch(e -> ChecklistResult.NON_CONFORME.name().equals(e.getResult()));
        return anyFail ? ChecklistResult.NON_CONFORME : ChecklistResult.CONFORME;
    }

    /** Items par défaut d'une checklist vide (utilisé par l'éditeur). */
    public static List<Checklist.ChecklistItem> defaultItems() {
        List<Checklist.ChecklistItem> out = new ArrayList<>();
        String[] labels = {"Inspection visuelle", "Contrôle de l'alimentation",
                "Test de fonctionnement", "Nettoyage"};
        for (int i = 0; i < labels.length; i++) {
            Checklist.ChecklistItem it = new Checklist.ChecklistItem();
            it.setPosition(i + 1);
            it.setLabel(labels[i]);
            out.add(it);
        }
        return out;
    }
}
