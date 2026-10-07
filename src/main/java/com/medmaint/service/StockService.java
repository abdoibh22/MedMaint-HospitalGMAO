package com.medmaint.service;

import com.medmaint.domain.enums.NotificationType;
import com.medmaint.domain.enums.StockTxType;
import com.medmaint.domain.model.SparePart;
import com.medmaint.domain.model.StockTransaction;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.SparePartRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Inventaire des pièces de rechange.
 * Règle : le stock ne devient jamais négatif, sauf override explicite
 * d'un administrateur (tracé dans l'audit).
 */
public final class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final SparePartRepo repo = new SparePartRepo();
    private final AuditService audit;
    private final NotificationService notifications;

    public StockService(AuditService audit, NotificationService notifications) {
        this.audit = audit;
        this.notifications = notifications;
    }

    public Page<SparePart> search(String text, String category, Boolean lowStock, Long supplierId,
                                  boolean includeArchived, int page, int pageSize,
                                  String sort, String dir) {
        Session.get().require(Permission.PART_VIEW);
        return repo.search(text, category, lowStock, supplierId, includeArchived, page, pageSize, sort, dir);
    }

    public Optional<SparePart> get(long id) {
        Session.get().require(Permission.PART_VIEW);
        return repo.findById(id);
    }

    public SparePart create(SparePart p) {
        Session.get().require(Permission.PART_MANAGE);
        validate(p, null);
        if (p.getQuantity() < 0) {
            throw new ValidationException("La quantité initiale ne peut pas être négative.");
        }
        long id = repo.insert(p);
        p.setId(id);
        audit.created("Pièce de rechange", p.getPartNo(), p.getName());
        if (p.isLowStock()) {
            alertLowStock(p);
        }
        return p;
    }

    public void update(SparePart p) {
        Session.get().require(Permission.PART_MANAGE);
        validate(p, p.getId());
        SparePart before = repo.findById(p.getId())
                .orElseThrow(() -> new ValidationException("Pièce introuvable."));
        repo.update(p);
        audit.updated("Pièce de rechange", p.getPartNo(), p.getName(),
                String.valueOf(before.getMinQuantity()), String.valueOf(p.getMinQuantity()));
        SparePart after = repo.findById(p.getId()).orElse(p);
        if (after.isLowStock()) {
            alertLowStock(after);
        }
    }

    public void archive(long id, boolean archived) {
        Session.get().require(Permission.PART_MANAGE);
        SparePart p = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Pièce introuvable."));
        SparePart upd = new SparePart();
        upd.setId(p.getId());
        upd.setPartNo(p.getPartNo());
        upd.setName(p.getName());
        upd.setCategory(p.getCategory());
        upd.setManufacturer(p.getManufacturer());
        upd.setCompatibleEquipment(p.getCompatibleEquipment());
        upd.setMinQuantity(p.getMinQuantity());
        upd.setMaxQuantity(p.getMaxQuantity());
        upd.setUnitCost(p.getUnitCost());
        upd.setSupplierId(p.getSupplierId());
        upd.setStorageLocation(p.getStorageLocation());
        upd.setExpiryDate(p.getExpiryDate());
        upd.setArchived(archived);
        repo.update(upd);
        audit.action(archived ? "ARCHIVE" : "RESTORE", "Pièce de rechange", p.getPartNo(), p.getName());
    }

    public void delete(long id) {
        Session.get().require(Permission.PART_MANAGE);
        SparePart p = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Pièce introuvable."));
        repo.delete(id);
        audit.deleted("Pièce de rechange", p.getPartNo(), p.getName(), null);
    }

    /**
     * Transaction de stock générique : entrée / sortie / transfert / ajustement.
     * @param deltaDelta signe appliqué selon le type (déjà calculé par le caller via type)
     */
    public void transact(long partId, StockTxType type, int quantity, BigDecimal unitCost,
                         String note, boolean allowNegative) {
        Session s = Session.get();
        s.require(Permission.PART_MANAGE);
        if (quantity <= 0) {
            throw new ValidationException("La quantité doit être supérieure à zéro.");
        }
        SparePart p = repo.findById(partId)
                .orElseThrow(() -> new ValidationException("Pièce introuvable."));

        int newQty = switch (type) {
            case ENTREE -> p.getQuantity() + quantity;
            case SORTIE, USAGE -> p.getQuantity() - quantity;
            case AJUSTEMENT -> quantity; // valeur absolue d'inventaire
            case TRANSFERT -> p.getQuantity() - quantity;
        };
        if (newQty < 0) {
            if (!allowNegative || !s.isAdmin()) {
                throw new ValidationException(
                        "Stock insuffisant : " + p.getPartNo() + " (disponible " + p.getQuantity()
                                + ", demandé " + quantity + "). L'ajustement en négatif est réservé à un administrateur.");
            }
            audit.record("NEGATIVE_STOCK_OVERRIDE", "Pièce de rechange", p.getPartNo(), p.getName(),
                    String.valueOf(p.getQuantity()), String.valueOf(newQty));
            log.warn("Override stock négatif admin : {} → {}", p.getPartNo(), newQty);
        }
        if (p.getMaxQuantity() > 0 && newQty > p.getMaxQuantity()) {
            log.info("Stock au-delà du maximum pour {} : {}", p.getPartNo(), newQty);
        }

        repo.updateQuantity(partId, newQty);

        StockTransaction tx = new StockTransaction();
        tx.setPartId(partId);
        tx.setTxType(type.name());
        tx.setQuantity(quantity);
        tx.setUnitCost(unitCost != null ? unitCost : p.getUnitCost());
        tx.setUserId(s.userId());
        tx.setNote(note);
        repo.insertTransaction(tx);

        audit.record("STOCK_" + type.name(), "Pièce de rechange", p.getPartNo(), p.getName(),
                String.valueOf(p.getQuantity()), String.valueOf(newQty));

        SparePart after = repo.findById(partId).orElse(p);
        if (after.isLowStock()) {
            alertLowStock(after);
        }
    }

    private void alertLowStock(SparePart p) {
        notifications.notifyRole("MANAGER", NotificationType.STOCK_FAIBLE,
                "Stock faible — " + p.getPartNo(),
                p.getName() + " : " + p.getQuantity() + " unité(s), minimum " + p.getMinQuantity(),
                "WARN", "SPARE_PART", p.getId());
    }

    public List<SparePart> lowStock() {
        Session.get().require(Permission.PART_VIEW);
        return repo.findLowStock();
    }

    public long countLowStock() {
        return repo.countLowStock();
    }

    public List<StockTransaction> transactionsOf(long partId) {
        return repo.findTransactionsByPart(partId, 100);
    }

    public List<StockTransaction> recentTransactions(int limit) {
        return repo.findRecentTransactions(limit);
    }

    public List<String> categories() {
        return repo.findCategories();
    }

    public List<SparePart> all() {
        return repo.findAll();
    }

    private void validate(SparePart p, Long excludeId) {
        if (p.getPartNo() == null || p.getPartNo().isBlank()) {
            throw new ValidationException("Le numéro de pièce est obligatoire.");
        }
        if (p.getName() == null || p.getName().isBlank()) {
            throw new ValidationException("Le nom de la pièce est obligatoire.");
        }
        if (repo.partNoExists(p.getPartNo(), excludeId)) {
            throw new ValidationException("Le numéro \"" + p.getPartNo() + "\" existe déjà.");
        }
        if (p.getMinQuantity() < 0 || p.getMaxQuantity() < 0) {
            throw new ValidationException("Les seuils de stock ne peuvent pas être négatifs.");
        }
        if (p.getMaxQuantity() > 0 && p.getMaxQuantity() < p.getMinQuantity()) {
            throw new ValidationException(
                    "Le stock maximum doit être supérieur au stock minimum.");
        }
        if (p.getUnitCost() != null && p.getUnitCost().signum() < 0) {
            throw new ValidationException("Le coût unitaire ne peut pas être négatif.");
        }
    }

    public SparePartRepo repo() {
        return repo;
    }
}
