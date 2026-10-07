package com.medmaint.service;

import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.enums.MaintenanceFrequency;
import com.medmaint.domain.enums.NotificationType;
import com.medmaint.domain.model.Equipment;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.CategoryRepo;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service équipements : validations (unicité code/série, dates de maintenance,
 * garantie), CRUD, archivage, duplication et calcul de la prochaine maintenance.
 */
public final class EquipmentService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentService.class);

    private final EquipmentRepo repo = new EquipmentRepo();
    private final CategoryRepo refs = new CategoryRepo();
    private final AuditService audit;
    private final NotificationService notifications;
    private final DocumentService documents;

    public EquipmentService(AuditService audit, NotificationService notifications, DocumentService documents) {
        this.audit = audit;
        this.notifications = notifications;
        this.documents = documents;
    }

    public Page<Equipment> search(EquipmentRepo.Filter f, int page, int pageSize,
                                  String sort, String dir, boolean canSeeArchived) {
        Session.get().require(Permission.EQUIPMENT_VIEW);
        if (!canSeeArchived && f != null && f.serviceDepartmentId() == null && f.departmentId() == null) {
            // rien à faire : le filtre archived est déjà posé par le repo
        }
        return repo.search(f, page, pageSize, sort, dir);
    }

    public Optional<Equipment> get(long id) {
        Session.get().require(Permission.EQUIPMENT_VIEW);
        return repo.findById(id);
    }

    public Equipment create(Equipment e) {
        Session.get().require(Permission.EQUIPMENT_CREATE);
        validate(e, null);
        e.setStatus(e.getStatus() == null ? EquipmentStatus.EN_SERVICE.name() : e.getStatus());
        e.setCreatedBy(Session.get().userId());
        if (e.getPmFrequency() != null && !e.getPmFrequency().isBlank()) {
            recomputeNextMaintenance(e, e.getLastMaintenanceDate() != null
                    ? e.getLastMaintenanceDate() : LocalDate.now());
        }
        long id = repo.insert(e);
        e.setId(id);
        audit.created("Équipement", String.valueOf(id), e.getCode() + " — " + e.getName());
        notifications.notifyRole("ENGINEER", NotificationType.EQUIPEMENT_AJOUTE,
                "Équipement ajouté", e.getCode() + " — " + e.getName(), "INFO",
                "EQUIPMENT", id);
        log.info("Équipement créé : {}", e.getCode());
        return e;
    }

    public void update(Equipment e) {
        Session.get().require(Permission.EQUIPMENT_UPDATE);
        validate(e, e.getId());
        Equipment before = repo.findById(e.getId())
                .orElseThrow(() -> new ValidationException("Équipement introuvable."));
        repo.update(e);
        audit.updated("Équipement", String.valueOf(e.getId()), e.getCode() + " — " + e.getName(),
                before.getStatus(), e.getStatus());
    }

    /** Duplique un équipement (code et n° de série régénérés). */
    public Equipment duplicate(long id) {
        Session.get().require(Permission.EQUIPMENT_CREATE);
        Equipment src = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Équipement introuvable."));
        Equipment copy = new Equipment();
        copy.setCode(nextCode());
        copy.setName(src.getName() + " (copie)");
        copy.setCategoryId(src.getCategoryId());
        copy.setSubcategory(src.getSubcategory());
        copy.setManufacturerId(src.getManufacturerId());
        copy.setModel(src.getModel());
        copy.setSerialNumber(null); // n° de série unique → la copie n'en a pas
        copy.setReferenceNumber(src.getReferenceNumber());
        copy.setEquipmentType(src.getEquipmentType());
        copy.setCriticality(src.getCriticality());
        copy.setRiskLevel(src.getRiskLevel());
        copy.setDeviceClass(src.getDeviceClass());
        copy.setStatus(EquipmentStatus.STOCK.name());
        copy.setDepartmentId(src.getDepartmentId());
        copy.setLocationId(src.getLocationId());
        copy.setLocationDetails(src.getLocationDetails());
        copy.setSupplierId(src.getSupplierId());
        copy.setPurchaseDate(src.getPurchaseDate());
        copy.setMaintenanceType(src.getMaintenanceType());
        copy.setPmFrequency(src.getPmFrequency());
        copy.setPowerSupply(src.getPowerSupply());
        copy.setPowerRating(src.getPowerRating());
        copy.setDimensions(src.getDimensions());
        copy.setWeightKg(src.getWeightKg());
        copy.setFabricationYear(src.getFabricationYear());
        copy.setNotes(src.getNotes());
        copy.setCreatedBy(Session.get().userId());
        long newId = repo.insert(copy);
        copy.setId(newId);
        audit.created("Équipement", String.valueOf(newId), "Duplication de " + src.getCode());
        return copy;
    }

    public void archive(long id, boolean archived) {
        Session.get().require(Permission.EQUIPMENT_DELETE);
        Equipment e = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Équipement introuvable."));
        repo.archive(id, archived);
        audit.action(archived ? "ARCHIVE" : "RESTORE", "Équipement", String.valueOf(id), e.getCode());
    }

    /** Suppression définitive : refusée si l'équipement a un historique. */
    public void delete(long id) {
        Session.get().require(Permission.EQUIPMENT_DELETE);
        Equipment e = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Équipement introuvable."));
        long history = new com.medmaint.persistence.repo.WorkOrderRepo().countOpenForEquipment(id);
        if (history > 0) {
            throw new ValidationException(
                    "Impossible de supprimer : cet équipement a des interventions. Utilisez l'archivage.");
        }
        repo.delete(id);
        audit.deleted("Équipement", String.valueOf(id), e.getCode() + " — " + e.getName(), null);
    }

    public void updateStatus(long id, EquipmentStatus status) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Équipement introuvable."));
        Session s = Session.get();
        if (!(s.has(Permission.EQUIPMENT_UPDATE) || s.has(Permission.WORKORDER_EXECUTE))) {
            s.require(Permission.EQUIPMENT_UPDATE);
        }
        String before = e.getStatus();
        repo.updateStatus(id, status.name());
        audit.updated("Équipement", String.valueOf(id), e.getCode(), before, status.name());
    }

    /** Calcule la prochaine maintenance à partir de la fréquence. */
    public void recomputeNextMaintenance(Equipment e, LocalDate from) {
        if (e.getPmFrequency() == null || e.getPmFrequency().isBlank()) {
            return;
        }
        MaintenanceFrequency f = MaintenanceFrequency.fromCode(e.getPmFrequency());
        e.setLastMaintenanceDate(from);
        e.setNextMaintenanceDate(f.nextDate(from, null));
    }

    public String nextCode() {
        long n = repo.countAll() + 1;
        String code;
        do {
            code = String.format("EQ-%04d", n);
            n++;
        } while (repo.findByCode(code).isPresent());
        return code;
    }

    public boolean existsCode(String code, Long excludeId) {
        return repo.codeExists(code, excludeId);
    }

    public boolean existsSerial(String serial, Long excludeId) {
        return repo.serialExists(serial, excludeId);
    }

    // ---------- Validations ----------

    private void validate(Equipment e, Long excludeId) {
        if (e.getCode() == null || e.getCode().isBlank()) {
            throw new ValidationException("Le code inventaire est obligatoire.");
        }
        if (e.getName() == null || e.getName().isBlank()) {
            throw new ValidationException("Le nom de l'équipement est obligatoire.");
        }
        if (e.getCategoryId() <= 0) {
            throw new ValidationException("La catégorie est obligatoire.");
        }
        if (e.getDepartmentId() <= 0) {
            throw new ValidationException("Le service hospitalier est obligatoire.");
        }
        if (repo.codeExists(e.getCode(), excludeId)) {
            throw new ValidationException("Le code inventaire \"" + e.getCode() + "\" existe déjà.");
        }
        if (e.getSerialNumber() != null && !e.getSerialNumber().isBlank()
                && repo.serialExists(e.getSerialNumber(), excludeId)) {
            throw new ValidationException("Le numéro de série \"" + e.getSerialNumber() + "\" existe déjà.");
        }
        if (e.getPurchaseDate() != null && e.getReceptionDate() != null
                && e.getReceptionDate().isBefore(e.getPurchaseDate())) {
            throw new ValidationException("La date de réception ne peut pas précéder la date d'achat.");
        }
        if (e.getCommissioningDate() != null && e.getReceptionDate() != null
                && e.getCommissioningDate().isBefore(e.getReceptionDate())) {
            throw new ValidationException("La mise en service ne peut pas précéder la réception.");
        }
        if (e.getWarrantyEndDate() != null && e.getPurchaseDate() != null
                && e.getWarrantyEndDate().isBefore(e.getPurchaseDate())) {
            throw new ValidationException("La fin de garantie ne peut pas précéder la date d'achat.");
        }
        if (e.getNextMaintenanceDate() != null && e.getLastMaintenanceDate() != null
                && e.getNextMaintenanceDate().isBefore(e.getLastMaintenanceDate())) {
            throw new ValidationException(
                    "La prochaine maintenance ne peut pas précéder la dernière maintenance.");
        }
        if (e.getPurchasePrice() != null && e.getPurchasePrice().signum() < 0) {
            throw new ValidationException("Le prix d'achat ne peut pas être négatif.");
        }
        if (e.getWarrantyMonths() != null && e.getWarrantyMonths() < 0) {
            throw new ValidationException("La durée de garantie ne peut pas être négative.");
        }
    }

    public EquipmentRepo repo() {
        return repo;
    }

    public CategoryRepo refs() {
        return refs;
    }

    public List<Equipment> dueSoon(int days, int limit) {
        return repo.findDueForMaintenance(days, limit);
    }
}
