package com.medmaint.service;

import com.medmaint.domain.enums.ContractStatus;
import com.medmaint.domain.enums.NotificationType;
import com.medmaint.domain.model.Contract;
import com.medmaint.domain.model.Supplier;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.ContractRepo;
import com.medmaint.persistence.repo.SupplierRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Fournisseurs et contrats de maintenance (avec alertes d'expiration). */
public final class SupplierContractService {

    private static final Logger log = LoggerFactory.getLogger(SupplierContractService.class);
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final int EXPIRY_WINDOW_DAYS = 60;

    private final SupplierRepo suppliers = new SupplierRepo();
    private final ContractRepo contracts = new ContractRepo();
    private final AuditService audit;
    private final NotificationService notifications;

    public SupplierContractService(AuditService audit, NotificationService notifications) {
        this.audit = audit;
        this.notifications = notifications;
    }

    // ---------- Fournisseurs ----------

    public Page<Supplier> searchSuppliers(String text, String category, boolean includeArchived,
                                          int page, int pageSize, String sort, String dir) {
        Session.get().require(Permission.SUPPLIER_VIEW);
        return suppliers.search(text, category, includeArchived, page, pageSize, sort, dir);
    }

    public Optional<Supplier> getSupplier(long id) {
        Session.get().require(Permission.SUPPLIER_VIEW);
        return suppliers.findById(id);
    }

    public Supplier createSupplier(Supplier s) {
        Session.get().require(Permission.SUPPLIER_MANAGE);
        validateSupplier(s, null);
        long id = suppliers.insert(s);
        s.setId(id);
        audit.created("Fournisseur", String.valueOf(id), s.getCompany());
        return s;
    }

    public void updateSupplier(Supplier s) {
        Session.get().require(Permission.SUPPLIER_MANAGE);
        validateSupplier(s, s.getId());
        Supplier before = suppliers.findById(s.getId())
                .orElseThrow(() -> new ValidationException("Fournisseur introuvable."));
        suppliers.update(s);
        audit.updated("Fournisseur", String.valueOf(s.getId()), s.getCompany(),
                before.getContractStatus(), s.getContractStatus());
    }

    public void archiveSupplier(long id, boolean archived) {
        Session.get().require(Permission.SUPPLIER_MANAGE);
        Supplier s = suppliers.findById(id)
                .orElseThrow(() -> new ValidationException("Fournisseur introuvable."));
        s.setArchived(archived);
        suppliers.update(s);
        audit.action(archived ? "ARCHIVE" : "RESTORE", "Fournisseur", s.getCompany(), s.getCompany());
    }

    public void deleteSupplier(long id) {
        Session.get().require(Permission.SUPPLIER_MANAGE);
        Supplier s = suppliers.findById(id)
                .orElseThrow(() -> new ValidationException("Fournisseur introuvable."));
        if (suppliers.hasDependencies(id)) {
            throw new ValidationException(
                    "Impossible de supprimer : ce fournisseur est référencé (équipements, pièces ou contrats).");
        }
        suppliers.delete(id);
        audit.deleted("Fournisseur", String.valueOf(id), s.getCompany(), null);
    }

    private void validateSupplier(Supplier s, Long excludeId) {
        if (s.getCompany() == null || s.getCompany().isBlank()) {
            throw new ValidationException("Le nom de l'entreprise est obligatoire.");
        }
        if (suppliers.companyExists(s.getCompany(), excludeId)) {
            throw new ValidationException("Ce fournisseur existe déjà : " + s.getCompany());
        }
        if (s.getEmail() != null && !s.getEmail().isBlank()
                && !EMAIL.matcher(s.getEmail()).matches()) {
            throw new ValidationException("Adresse e-mail du fournisseur invalide.");
        }
    }

    // ---------- Contrats ----------

    public Page<Contract> searchContracts(String text, String status, Long supplierId,
                                          int page, int pageSize, String sort, String dir) {
        Session.get().require(Permission.CONTRACT_VIEW);
        return contracts.search(text, status, supplierId, page, pageSize, sort, dir);
    }

    public Optional<Contract> getContract(long id) {
        Session.get().require(Permission.CONTRACT_VIEW);
        return contracts.findById(id);
    }

    /** Statut effectif (tient compte des dates + suspendu). */
    public ContractStatus effectiveStatus(Contract c) {
        return ContractStatus.effective(
                ContractStatus.fromCode(c.getStatus()),
                c.getStartDate(), c.getEndDate(), LocalDate.now());
    }

    public Contract createContract(Contract c) {
        Session.get().require(Permission.CONTRACT_MANAGE);
        validateContract(c, null);
        if (c.getStatus() == null) c.setStatus(ContractStatus.ACTIF.name());
        long id = contracts.insert(c);
        c.setId(id);
        audit.created("Contrat", c.getContractNo(), c.getTitle());
        return c;
    }

    public void updateContract(Contract c) {
        Session.get().require(Permission.CONTRACT_MANAGE);
        validateContract(c, c.getId());
        Contract before = contracts.findById(c.getId())
                .orElseThrow(() -> new ValidationException("Contrat introuvable."));
        contracts.update(c);
        audit.updated("Contrat", c.getContractNo(), c.getTitle(),
                before.getStatus(), c.getStatus());
    }

    public void deleteContract(long id) {
        Session.get().require(Permission.CONTRACT_MANAGE);
        Contract c = contracts.findById(id)
                .orElseThrow(() -> new ValidationException("Contrat introuvable."));
        contracts.delete(id);
        audit.deleted("Contrat", c.getContractNo(), c.getTitle(), null);
    }

    public void setContractEquipment(long contractId, List<Long> equipmentIds) {
        Session.get().require(Permission.CONTRACT_MANAGE);
        contracts.replaceEquipment(contractId, equipmentIds);
        audit.updated("Contrat", String.valueOf(contractId), "couverture équipements",
                null, equipmentIds != null ? equipmentIds.size() + " équipement(s)" : "0");
    }

    public List<Long> contractEquipment(long contractId) {
        return contracts.equipmentIds(contractId);
    }

    private void validateContract(Contract c, Long excludeId) {
        if (c.getContractNo() == null || c.getContractNo().isBlank()) {
            throw new ValidationException("Le numéro de contrat est obligatoire.");
        }
        if (c.getTitle() == null || c.getTitle().isBlank()) {
            throw new ValidationException("L'intitulé du contrat est obligatoire.");
        }
        if (c.getStartDate() == null || c.getEndDate() == null) {
            throw new ValidationException("Les dates de début et de fin sont obligatoires.");
        }
        if (c.getEndDate().isBefore(c.getStartDate())) {
            throw new ValidationException("La date de fin ne peut pas précéder la date de début.");
        }
        if (contracts.noExists(c.getContractNo(), excludeId)) {
            throw new ValidationException("Le numéro \"" + c.getContractNo() + "\" existe déjà.");
        }
        if (c.getAnnualCost() != null && c.getAnnualCost().signum() < 0) {
            throw new ValidationException("Le coût annuel ne peut pas être négatif.");
        }
        if (c.getContactEmail() != null && !c.getContactEmail().isBlank()
                && !EMAIL.matcher(c.getContactEmail()).matches()) {
            throw new ValidationException("E-mail de contact invalide.");
        }
    }

    /** Contrats expirant bientôt → notifications. */
    public void scanExpiring() {
        try {
            for (Contract c : contracts.findExpiringWithin(EXPIRY_WINDOW_DAYS)) {
                notifications.notifyRole("MANAGER", NotificationType.CONTRAT_EXPIRATION,
                        "Contrat expirant bientôt — " + c.getContractNo(),
                        c.getTitle() + " se termine le " + c.getEndDate(), "WARN",
                        "CONTRACT", c.getId());
            }
        } catch (RuntimeException e) {
            log.warn("Scan contrats : {}", e.getMessage());
        }
    }

    /** Garanties des équipements expirant bientôt → notifications. */
    public void scanWarranties() {
        try {
            List<Object[]> rows = com.medmaint.persistence.Jdbc.query("""
                    SELECT code, name, warranty_end_date FROM equipment
                    WHERE archived = FALSE AND warranty_end_date IS NOT NULL
                      AND warranty_end_date >= CURRENT_DATE
                      AND warranty_end_date <= DATEADD('DAY', 30, CURRENT_DATE)
                    ORDER BY warranty_end_date""",
                    (com.medmaint.persistence.Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                            rs.getString(1), rs.getString(2), rs.getDate(3)});
            for (Object[] row : rows) {
                notifications.notifyRole("MANAGER", NotificationType.GARANTIE_EXPIRATION,
                        "Garantie bientôt expirée — " + row[0],
                        row[1] + " : garantie jusqu'au " + row[2], "INFO", "EQUIPMENT", null);
            }
        } catch (RuntimeException e) {
            log.warn("Scan garanties : {}", e.getMessage());
        }
    }

    public long countExpiringContracts() {
        return contracts.countExpiringWithin(EXPIRY_WINDOW_DAYS);
    }

    public SupplierRepo suppliers() {
        return suppliers;
    }

    public ContractRepo contracts() {
        return contracts;
    }
}
