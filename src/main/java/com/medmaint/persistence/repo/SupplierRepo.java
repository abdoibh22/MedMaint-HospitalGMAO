package com.medmaint.persistence.repo;

import com.medmaint.domain.model.Supplier;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.util.List;
import java.util.Optional;

/** Fournisseurs. */
public final class SupplierRepo {

    private static final Jdbc.RowMapper<Supplier> MAP = rs -> {
        Supplier s = new Supplier();
        s.setId(rs.getLong("id"));
        s.setCompany(rs.getString("company"));
        s.setContactPerson(rs.getString("contact_person"));
        s.setAddress(rs.getString("address"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setWebsite(rs.getString("website"));
        s.setCategory(rs.getString("category"));
        s.setProducts(rs.getString("products"));
        s.setContractStatus(rs.getString("contract_status"));
        s.setNotes(rs.getString("notes"));
        s.setArchived(Jdbc.getBool(rs, "archived"));
        return s;
    };

    public Page<Supplier> search(String text, String category, boolean includeArchived,
                                 int page, int pageSize, String sortCol, String sortDir) {
        SqlWhere w = new SqlWhere("1=1");
        if (!includeArchived) {
            w.add("archived = FALSE", Boolean.FALSE);
        }
        w.addLikes(List.of("company", "contact_person", "category"), text)
                .add("category = ?", category);
        long total = Jdbc.count("SELECT COUNT(*) FROM suppliers WHERE " + w.clause(), w.params());
        String order = switch (sortCol == null ? "" : sortCol) {
            case "category" -> "category";
            case "contact" -> "contact_person";
            case "contractStatus" -> "contract_status";
            default -> "company";
        } + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        int offset = Math.max(0, (page - 1) * pageSize);
        List<Supplier> items = Jdbc.query(
                "SELECT * FROM suppliers WHERE " + w.clause() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<Supplier> findAll() {
        return Jdbc.query("SELECT * FROM suppliers WHERE archived = FALSE ORDER BY company", MAP);
    }

    public Optional<Supplier> findById(long id) {
        return Jdbc.queryOne("SELECT * FROM suppliers WHERE id=?", MAP, id);
    }

    public boolean companyExists(String company, Long excludeId) {
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM suppliers WHERE LOWER(company)=LOWER(?)"
                : "SELECT COUNT(*) FROM suppliers WHERE LOWER(company)=LOWER(?) AND id<>?";
        return (excludeId == null ? Jdbc.count(sql, company) : Jdbc.count(sql, company, excludeId)) > 0;
    }

    public long insert(Supplier s) {
        return Jdbc.insert("""
                INSERT INTO suppliers(company, contact_person, address, phone, email, website, category,
                    products, contract_status, notes, archived, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP)""",
                s.getCompany(), s.getContactPerson(), s.getAddress(), s.getPhone(), s.getEmail(),
                s.getWebsite(), s.getCategory(), s.getProducts(), s.getContractStatus(), s.getNotes());
    }

    public void update(Supplier s) {
        Jdbc.update("""
                UPDATE suppliers SET company=?, contact_person=?, address=?, phone=?, email=?, website=?,
                    category=?, products=?, contract_status=?, notes=?, archived=? WHERE id=?""",
                s.getCompany(), s.getContactPerson(), s.getAddress(), s.getPhone(), s.getEmail(),
                s.getWebsite(), s.getCategory(), s.getProducts(), s.getContractStatus(), s.getNotes(),
                s.isArchived(), s.getId());
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM suppliers WHERE id=?", id);
    }

    public boolean hasDependencies(long id) {
        return Jdbc.count("SELECT COUNT(*) FROM equipment WHERE supplier_id=?", id) > 0
                || Jdbc.count("SELECT COUNT(*) FROM contracts WHERE supplier_id=?", id) > 0
                || Jdbc.count("SELECT COUNT(*) FROM spare_parts WHERE supplier_id=?", id) > 0;
    }

    /** Historique : équipements fournis. */
    public List<Object[]> equipmentBySupplier(long supplierId) {
        return Jdbc.query("""
                SELECT code, name, status FROM equipment WHERE supplier_id=? ORDER BY code""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3)}, supplierId);
    }

    public List<Object[]> partsBySupplier(long supplierId) {
        return Jdbc.query("""
                SELECT part_no, name, quantity FROM spare_parts WHERE supplier_id=? ORDER BY part_no""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getLong(3)}, supplierId);
    }

    public List<Object[]> contractsBySupplier(long supplierId) {
        return Jdbc.query("""
                SELECT contract_no, title, start_date, end_date, status FROM contracts
                WHERE supplier_id=? ORDER BY end_date DESC""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getObject(3), rs.getObject(4), rs.getString(5)},
                supplierId);
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
