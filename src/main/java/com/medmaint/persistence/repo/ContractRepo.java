package com.medmaint.persistence.repo;

import com.medmaint.domain.model.Contract;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Contrats de maintenance (couverture équipements incluse). */
public final class ContractRepo {

    private static final String BASE = """
            SELECT c.*, s.company AS supplier_name,
                   (SELECT COUNT(*) FROM contract_equipment ce WHERE ce.contract_id = c.id) AS equipment_count
            FROM contracts c LEFT JOIN suppliers s ON s.id = c.supplier_id
            """;

    private static final Jdbc.RowMapper<Contract> MAP = rs -> {
        Contract c = new Contract();
        c.setId(rs.getLong("id"));
        c.setContractNo(rs.getString("contract_no"));
        c.setSupplierId(Jdbc.getLongOrNull(rs, "supplier_id"));
        c.setSupplierName(rs.getString("supplier_name"));
        c.setTitle(rs.getString("title"));
        c.setContractType(rs.getString("contract_type"));
        c.setStartDate(Jdbc.getDate(rs, "start_date"));
        c.setEndDate(Jdbc.getDate(rs, "end_date"));
        c.setAnnualCost(rs.getBigDecimal("annual_cost"));
        c.setSla(rs.getString("sla"));
        c.setResponseTime(rs.getString("response_time"));
        c.setContactName(rs.getString("contact_name"));
        c.setContactPhone(rs.getString("contact_phone"));
        c.setContactEmail(rs.getString("contact_email"));
        c.setStatus(rs.getString("status"));
        c.setNotes(rs.getString("notes"));
        c.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        c.setEquipmentCount(rs.getInt("equipment_count"));
        return c;
    };

    public Page<Contract> search(String text, String status, Long supplierId,
                                 int page, int pageSize, String sortCol, String sortDir) {
        SqlWhere w = new SqlWhere("1=1");
        w.addLikes(List.of("c.contract_no", "c.title", "s.company"), text)
                .add("c.status = ?", status)
                .add("c.supplier_id = ?", supplierId);
        long total = Jdbc.count(
                "SELECT COUNT(*) FROM contracts c LEFT JOIN suppliers s ON s.id=c.supplier_id WHERE " + w.clause(),
                w.params());
        String order = switch (sortCol == null ? "" : sortCol) {
            case "title" -> "c.title";
            case "supplier" -> "s.company";
            case "startDate" -> "c.start_date";
            case "endDate" -> "c.end_date";
            case "cost" -> "c.annual_cost";
            default -> "c.end_date";
        } + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        int offset = Math.max(0, (page - 1) * pageSize);
        List<Contract> items = Jdbc.query(
                BASE + " WHERE " + w.clause() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<Contract> findAll() {
        return Jdbc.query(BASE + " ORDER BY c.end_date", MAP);
    }

    public Optional<Contract> findById(long id) {
        return Jdbc.queryOne(BASE + " WHERE c.id=?", MAP, id);
    }

    public Optional<Contract> findByNo(String no) {
        return Jdbc.queryOne(BASE + " WHERE LOWER(c.contract_no)=LOWER(?)", MAP, no);
    }

    public boolean noExists(String no, Long excludeId) {
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM contracts WHERE LOWER(contract_no)=LOWER(?)"
                : "SELECT COUNT(*) FROM contracts WHERE LOWER(contract_no)=LOWER(?) AND id<>?";
        return (excludeId == null ? Jdbc.count(sql, no) : Jdbc.count(sql, no, excludeId)) > 0;
    }

    public long insert(Contract c) {
        return Jdbc.insert("""
                INSERT INTO contracts(contract_no, supplier_id, title, contract_type, start_date, end_date,
                    annual_cost, sla, response_time, contact_name, contact_phone, contact_email, status,
                    notes, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)""",
                c.getContractNo(), c.getSupplierId(), c.getTitle(), c.getContractType(),
                c.getStartDate(), c.getEndDate(), c.getAnnualCost(), c.getSla(), c.getResponseTime(),
                c.getContactName(), c.getContactPhone(), c.getContactEmail(), c.getStatus(), c.getNotes());
    }

    public void update(Contract c) {
        Jdbc.update("""
                UPDATE contracts SET supplier_id=?, title=?, contract_type=?, start_date=?, end_date=?,
                    annual_cost=?, sla=?, response_time=?, contact_name=?, contact_phone=?, contact_email=?,
                    status=?, notes=? WHERE id=?""",
                c.getSupplierId(), c.getTitle(), c.getContractType(), c.getStartDate(), c.getEndDate(),
                c.getAnnualCost(), c.getSla(), c.getResponseTime(), c.getContactName(), c.getContactPhone(),
                c.getContactEmail(), c.getStatus(), c.getNotes(), c.getId());
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM contract_equipment WHERE contract_id=?", id);
        Jdbc.update("DELETE FROM contracts WHERE id=?", id);
    }

    // ---------- Couverture équipements ----------

    public List<Long> equipmentIds(long contractId) {
        return Jdbc.query("SELECT equipment_id FROM contract_equipment WHERE contract_id=?",
                rs -> rs.getLong(1), contractId);
    }

    public void replaceEquipment(long contractId, List<Long> equipmentIds) {
        Jdbc.update("DELETE FROM contract_equipment WHERE contract_id=?", contractId);
        if (equipmentIds == null) return;
        for (Long eqId : equipmentIds) {
            if (eqId != null) {
                Jdbc.update("INSERT INTO contract_equipment(contract_id, equipment_id) VALUES (?,?)",
                        contractId, eqId);
            }
        }
    }

    public List<Object[]> equipmentDetails(long contractId) {
        return Jdbc.query("""
                SELECT e.code, e.name, d.name
                FROM contract_equipment ce
                JOIN equipment e ON e.id = ce.equipment_id
                LEFT JOIN departments d ON d.id = e.department_id
                WHERE ce.contract_id=? ORDER BY e.code""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3)}, contractId);
    }

    /** Contrats expirant dans N jours (alertes). */
    public List<Contract> findExpiringWithin(int days) {
        return Jdbc.query(BASE + """
                 WHERE c.status <> 'SUSPENDU' AND c.end_date >= CURRENT_DATE
                   AND c.end_date <= DATEADD('DAY', ?, CURRENT_DATE)
                 ORDER BY c.end_date""", MAP, days);
    }

    public long countExpiringWithin(int days) {
        return Jdbc.count("""
                SELECT COUNT(*) FROM contracts
                WHERE status <> 'SUSPENDU' AND end_date >= CURRENT_DATE
                  AND end_date <= DATEADD('DAY', ?, CURRENT_DATE)""", days);
    }

    public long countActive() {
        return Jdbc.count("SELECT COUNT(*) FROM contracts WHERE status='ACTIF'");
    }

    public BigDecimal sumAnnualCost() {
        return Jdbc.queryOne(
                "SELECT COALESCE(SUM(annual_cost),0) FROM contracts WHERE status <> 'EXPIRE'",
                rs -> rs.getBigDecimal(1)).orElse(BigDecimal.ZERO);
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
