package com.medmaint.persistence.repo;

import com.medmaint.domain.model.SparePart;
import com.medmaint.domain.model.StockTransaction;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Pièces de rechange et transactions de stock. */
public final class SparePartRepo {

    private static final String BASE = """
            SELECT p.*, s.company AS supplier_name
            FROM spare_parts p LEFT JOIN suppliers s ON s.id = p.supplier_id
            """;

    private static final Jdbc.RowMapper<SparePart> MAP = rs -> {
        SparePart p = new SparePart();
        p.setId(rs.getLong("id"));
        p.setPartNo(rs.getString("part_no"));
        p.setName(rs.getString("name"));
        p.setCategory(rs.getString("category"));
        p.setManufacturer(rs.getString("manufacturer"));
        p.setCompatibleEquipment(rs.getString("compatible_equipment"));
        p.setQuantity(rs.getInt("quantity"));
        p.setMinQuantity(rs.getInt("min_quantity"));
        p.setMaxQuantity(rs.getInt("max_quantity"));
        p.setUnitCost(rs.getBigDecimal("unit_cost"));
        p.setSupplierId(Jdbc.getLongOrNull(rs, "supplier_id"));
        p.setSupplierName(rs.getString("supplier_name"));
        p.setStorageLocation(rs.getString("storage_location"));
        p.setExpiryDate(Jdbc.getDate(rs, "expiry_date"));
        p.setArchived(Jdbc.getBool(rs, "archived"));
        p.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        p.setUpdatedAt(Jdbc.getDateTime(rs, "updated_at"));
        return p;
    };

    private static final Jdbc.RowMapper<StockTransaction> TX_MAP = rs -> {
        StockTransaction t = new StockTransaction();
        t.setId(rs.getLong("id"));
        t.setPartId(rs.getLong("part_id"));
        t.setPartNo(rs.getString("part_no"));
        t.setPartName(rs.getString("part_name"));
        t.setTxType(rs.getString("tx_type"));
        t.setQuantity(rs.getInt("quantity"));
        t.setUnitCost(rs.getBigDecimal("unit_cost"));
        t.setWorkOrderId(Jdbc.getLongOrNull(rs, "work_order_id"));
        t.setWorkOrderNo(rs.getString("wo_no"));
        t.setEquipmentId(Jdbc.getLongOrNull(rs, "equipment_id"));
        t.setEquipmentCode(rs.getString("equipment_code"));
        t.setUserId(Jdbc.getLongOrNull(rs, "user_id"));
        t.setUserName(rs.getString("user_name"));
        t.setNote(rs.getString("note"));
        t.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        return t;
    };

    public Page<SparePart> search(String text, String category, Boolean lowStock, Long supplierId,
                                  boolean includeArchived, int page, int pageSize,
                                  String sortCol, String sortDir) {
        SqlWhere w = new SqlWhere("1=1");
        if (!includeArchived) {
            w.add("p.archived = FALSE", Boolean.FALSE);
        }
        w.addLikes(List.of("p.name", "p.part_no", "p.manufacturer"), text)
                .add("p.category = ?", category)
                .add("p.supplier_id = ?", supplierId);
        if (Boolean.TRUE.equals(lowStock)) {
            w.add("p.quantity < p.min_quantity", Boolean.TRUE);
        }
        long total = Jdbc.count(
                "SELECT COUNT(*) FROM spare_parts p LEFT JOIN suppliers s ON s.id=p.supplier_id WHERE " + w.clause(),
                w.params());
        String order = switch (sortCol == null ? "" : sortCol) {
            case "name" -> "p.name";
            case "category" -> "p.category";
            case "quantity" -> "p.quantity";
            case "minQuantity" -> "p.min_quantity";
            case "unitCost" -> "p.unit_cost";
            case "location" -> "p.storage_location";
            default -> "p.part_no";
        } + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        int offset = Math.max(0, (page - 1) * pageSize);
        List<SparePart> items = Jdbc.query(
                BASE + " WHERE " + w.clause() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<SparePart> findAll() {
        return Jdbc.query(BASE + " WHERE p.archived = FALSE ORDER BY p.name", MAP);
    }

    public Optional<SparePart> findById(long id) {
        return Jdbc.queryOne(BASE + " WHERE p.id=?", MAP, id);
    }

    public Optional<SparePart> findByNo(String partNo) {
        return Jdbc.queryOne(BASE + " WHERE LOWER(p.part_no)=LOWER(?)", MAP, partNo);
    }

    public boolean partNoExists(String partNo, Long excludeId) {
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM spare_parts WHERE LOWER(part_no)=LOWER(?)"
                : "SELECT COUNT(*) FROM spare_parts WHERE LOWER(part_no)=LOWER(?) AND id<>?";
        return (excludeId == null ? Jdbc.count(sql, partNo) : Jdbc.count(sql, partNo, excludeId)) > 0;
    }

    public long insert(SparePart p) {
        return Jdbc.insert("""
                INSERT INTO spare_parts(part_no, name, category, manufacturer, compatible_equipment,
                    quantity, min_quantity, max_quantity, unit_cost, supplier_id, storage_location,
                    expiry_date, archived, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)""",
                p.getPartNo(), p.getName(), p.getCategory(), p.getManufacturer(),
                p.getCompatibleEquipment(), p.getQuantity(), p.getMinQuantity(), p.getMaxQuantity(),
                p.getUnitCost(), p.getSupplierId(), p.getStorageLocation(), p.getExpiryDate());
    }

    public void update(SparePart p) {
        Jdbc.update("""
                UPDATE spare_parts SET part_no=?, name=?, category=?, manufacturer=?, compatible_equipment=?,
                    min_quantity=?, max_quantity=?, unit_cost=?, supplier_id=?, storage_location=?,
                    expiry_date=?, archived=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?""",
                p.getPartNo(), p.getName(), p.getCategory(), p.getManufacturer(),
                p.getCompatibleEquipment(), p.getMinQuantity(), p.getMaxQuantity(), p.getUnitCost(),
                p.getSupplierId(), p.getStorageLocation(), p.getExpiryDate(), p.isArchived(), p.getId());
    }

    /** Met à jour la quantité seule (appelé uniquement après validation de stock). */
    public void updateQuantity(long id, int quantity) {
        Jdbc.update("UPDATE spare_parts SET quantity=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                quantity, id);
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM spare_parts WHERE id=?", id);
    }

    public List<SparePart> findLowStock() {
        return Jdbc.query(BASE + """
                 WHERE p.archived = FALSE AND p.quantity < p.min_quantity
                 ORDER BY (p.min_quantity - p.quantity) DESC""", MAP);
    }

    public long countLowStock() {
        return Jdbc.count("SELECT COUNT(*) FROM spare_parts WHERE archived=FALSE AND quantity < min_quantity");
    }

    public List<String> findCategories() {
        return Jdbc.query(
                "SELECT DISTINCT category FROM spare_parts WHERE category IS NOT NULL ORDER BY category",
                rs -> rs.getString(1));
    }

    // ---------- Transactions ----------

    private static final String TX_SELECT = """
            SELECT t.*, p.part_no, p.name AS part_name, w.wo_no, e.code AS equipment_code, u.full_name AS user_name
            FROM stock_transactions t
            JOIN spare_parts p ON p.id = t.part_id
            LEFT JOIN work_orders w ON w.id = t.work_order_id
            LEFT JOIN equipment e ON e.id = t.equipment_id
            LEFT JOIN users u ON u.id = t.user_id
            """;

    public long insertTransaction(StockTransaction t) {
        return Jdbc.insert("""
                INSERT INTO stock_transactions(part_id, tx_type, quantity, unit_cost, work_order_id,
                    equipment_id, user_id, note, created_at)
                VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)""",
                t.getPartId(), t.getTxType(), t.getQuantity(), t.getUnitCost(), t.getWorkOrderId(),
                t.getEquipmentId(), t.getUserId(), t.getNote());
    }

    public List<StockTransaction> findTransactionsByPart(long partId, int limit) {
        return Jdbc.query(TX_SELECT + " WHERE t.part_id=? ORDER BY t.created_at DESC LIMIT ?",
                TX_MAP, partId, limit);
    }

    public List<StockTransaction> findTransactionsByWorkOrder(long workOrderId) {
        return Jdbc.query(TX_SELECT + " WHERE t.work_order_id=? ORDER BY t.created_at", TX_MAP, workOrderId);
    }

    public List<StockTransaction> findRecentTransactions(int limit) {
        return Jdbc.query(TX_SELECT + " ORDER BY t.created_at DESC LIMIT ?", TX_MAP, limit);
    }

    public BigDecimal totalConsumptionCost() {
        return Jdbc.queryOne("""
                SELECT COALESCE(SUM(t.quantity * t.unit_cost), 0)
                FROM stock_transactions t
                WHERE t.tx_type IN ('USAGE','SORTIE')""", rs -> rs.getBigDecimal(1)).orElse(BigDecimal.ZERO);
    }

    /** Consommation (quantité) par mois pour le graphe. */
    public List<Object[]> consumptionByMonth(int months) {
        return Jdbc.query("""
                SELECT TO_CHAR(t.created_at, 'YYYY-MM') AS ym, COALESCE(SUM(t.quantity), 0)
                FROM stock_transactions t
                WHERE t.tx_type IN ('USAGE','SORTIE')
                  AND t.created_at >= DATEADD('MONTH', ?, CURRENT_TIMESTAMP)
                GROUP BY TO_CHAR(t.created_at, 'YYYY-MM') ORDER BY ym""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getString(1), rs.getLong(2)}, -months);
    }

    /** Top pièces consommées (rapport). */
    public List<Object[]> topConsumedParts(int limit) {
        return Jdbc.query("""
                SELECT p.part_no, p.name, SUM(t.quantity) AS qty,
                       SUM(t.quantity * t.unit_cost) AS cost
                FROM stock_transactions t JOIN spare_parts p ON p.id = t.part_id
                WHERE t.tx_type IN ('USAGE','SORTIE')
                GROUP BY p.part_no, p.name ORDER BY qty DESC LIMIT ?""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4)}, limit);
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
