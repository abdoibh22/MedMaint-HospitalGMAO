package com.medmaint.persistence.repo;

import com.medmaint.domain.model.Equipment;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Inventaire des équipements : recherche filtrée/tournée/paginée, CRUD,
 * compteurs pour le tableau de bord.
 */
public final class EquipmentRepo {

    private static final String BASE = """
            SELECT e.*, c.name AS category_name, m.name AS manufacturer_name,
                   d.name AS department_name, l.name AS location_name,
                   s.company AS supplier_name, r.full_name AS responsible_name,
                   ct.contract_no
            FROM equipment e
            JOIN equipment_categories c ON c.id = e.category_id
            LEFT JOIN manufacturers m  ON m.id = e.manufacturer_id
            JOIN departments d         ON d.id = e.department_id
            LEFT JOIN locations l      ON l.id = e.location_id
            LEFT JOIN suppliers s      ON s.id = e.supplier_id
            LEFT JOIN users r          ON r.id = e.responsible_id
            LEFT JOIN contracts ct     ON ct.id = e.contract_id
            """;

    public static final Jdbc.RowMapper<Equipment> MAP = rs -> {
        Equipment e = new Equipment();
        e.setId(rs.getLong("id"));
        e.setCode(rs.getString("code"));
        e.setName(rs.getString("name"));
        e.setCategoryId(rs.getLong("category_id"));
        e.setCategoryName(rs.getString("category_name"));
        e.setSubcategory(rs.getString("subcategory"));
        e.setManufacturerId(Jdbc.getLongOrNull(rs, "manufacturer_id"));
        e.setManufacturerName(rs.getString("manufacturer_name"));
        e.setModel(rs.getString("model"));
        e.setSerialNumber(rs.getString("serial_number"));
        e.setReferenceNumber(rs.getString("reference_number"));
        e.setEquipmentType(rs.getString("equipment_type"));
        e.setCriticality(rs.getString("criticality"));
        e.setRiskLevel(rs.getString("risk_level"));
        e.setDeviceClass(rs.getString("device_class"));
        e.setStatus(rs.getString("status"));
        e.setDepartmentId(rs.getLong("department_id"));
        e.setDepartmentName(rs.getString("department_name"));
        e.setLocationId(Jdbc.getLongOrNull(rs, "location_id"));
        e.setLocationName(rs.getString("location_name"));
        e.setLocationDetails(rs.getString("location_details"));
        e.setSupplierId(Jdbc.getLongOrNull(rs, "supplier_id"));
        e.setSupplierName(rs.getString("supplier_name"));
        e.setPurchaseDate(Jdbc.getDate(rs, "purchase_date"));
        e.setReceptionDate(Jdbc.getDate(rs, "reception_date"));
        e.setCommissioningDate(Jdbc.getDate(rs, "commissioning_date"));
        BigDecimal price = rs.getBigDecimal("purchase_price");
        e.setPurchasePrice(price);
        e.setWarrantyMonths((Integer) rs.getObject("warranty_months"));
        e.setWarrantyEndDate(Jdbc.getDate(rs, "warranty_end_date"));
        e.setMaintenanceType(rs.getString("maintenance_type"));
        e.setPmFrequency(rs.getString("pm_frequency"));
        e.setLastMaintenanceDate(Jdbc.getDate(rs, "last_maintenance_date"));
        e.setNextMaintenanceDate(Jdbc.getDate(rs, "next_maintenance_date"));
        e.setResponsibleId(Jdbc.getLongOrNull(rs, "responsible_id"));
        e.setResponsibleName(rs.getString("responsible_name"));
        e.setContractId(Jdbc.getLongOrNull(rs, "contract_id"));
        e.setContractNo(rs.getString("contract_no"));
        e.setPowerSupply(rs.getString("power_supply"));
        e.setPowerRating(rs.getString("power_rating"));
        e.setDimensions(rs.getString("dimensions"));
        e.setWeightKg(rs.getBigDecimal("weight_kg"));
        e.setFabricationYear((Integer) rs.getObject("fabrication_year"));
        e.setNotes(rs.getString("notes"));
        e.setArchived(Jdbc.getBool(rs, "archived"));
        e.setCreatedBy(Jdbc.getLongOrNull(rs, "created_by"));
        e.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        e.setUpdatedAt(Jdbc.getDateTime(rs, "updated_at"));
        return e;
    };

    /** Filtres de liste (champ de recherche + facettes). */
    public record Filter(String text, Long categoryId, Long departmentId, String status,
                         Long manufacturerId, String criticality, String warranty,
                         String maintenanceStatus, boolean includeArchived,
                         Long serviceDepartmentId) {
        public Filter {
            text = (text == null || text.isBlank()) ? null : text;
        }
    }

    public Page<Equipment> search(Filter f, int page, int pageSize, String sortCol, String sortDir) {
        SqlWhere w = where(f);
        long total = Jdbc.count("SELECT COUNT(*) FROM equipment e " + joinAliases()
                + " WHERE " + w.clause(), w.params());
        String order = allowedSort(sortCol) + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        int offset = Math.max(0, (page - 1) * pageSize);
        List<Equipment> items = Jdbc.query(
                BASE + " WHERE " + w.clause() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<Equipment> findAll(Filter f, String sortCol, String sortDir) {
        SqlWhere w = where(f);
        String order = allowedSort(sortCol) + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        return Jdbc.query(BASE + " WHERE " + w.clause() + " ORDER BY " + order, MAP, w.params());
    }

    private SqlWhere where(Filter f) {
        SqlWhere w = new SqlWhere("1=1");
        if (!f.includeArchived()) {
            w.add("e.archived = FALSE", Boolean.FALSE);
        }
        w.addLikes(List.of("e.name", "e.code", "e.serial_number", "e.model"), f.text())
                .add("e.category_id = ?", f.categoryId())
                .add("e.department_id = ?", f.departmentId())
                .add("e.status = ?", f.status())
                .add("e.manufacturer_id = ?", f.manufacturerId())
                .add("e.criticality = ?", f.criticality())
                .add("e.department_id = ?", f.serviceDepartmentId());
        if ("Sous garantie".equals(f.warranty())) {
            w.add("e.warranty_end_date >= CURRENT_DATE", Boolean.TRUE);
        } else if ("Hors garantie".equals(f.warranty())) {
            w.add("(e.warranty_end_date IS NULL OR e.warranty_end_date < CURRENT_DATE)", Boolean.TRUE);
        }
        if ("En retard".equals(f.maintenanceStatus())) {
            w.add("e.next_maintenance_date < CURRENT_DATE", Boolean.TRUE);
        } else if ("À venir".equals(f.maintenanceStatus())) {
            w.add("e.next_maintenance_date >= CURRENT_DATE", Boolean.TRUE);
            w.add("e.next_maintenance_date <= DATEADD('DAY', 30, CURRENT_DATE)", Boolean.TRUE);
        } else if ("Sans plan".equals(f.maintenanceStatus())) {
            w.add("e.next_maintenance_date IS NULL", Boolean.TRUE);
        }
        return w;
    }

    private static String joinAliases() {
        return """
                JOIN equipment_categories c ON c.id = e.category_id
                JOIN departments d ON d.id = e.department_id
                LEFT JOIN manufacturers m ON m.id = e.manufacturer_id
                """;
    }

    private static String allowedSort(String col) {
        return switch (col == null ? "" : col) {
            case "name" -> "e.name";
            case "code" -> "e.code";
            case "category" -> "c.name";
            case "department" -> "d.name";
            case "manufacturer" -> "m.name";
            case "status" -> "e.status";
            case "criticality" -> "e.criticality";
            case "lastMaintenance" -> "e.last_maintenance_date";
            case "nextMaintenance" -> "e.next_maintenance_date";
            case "purchaseDate" -> "e.purchase_date";
            default -> "e.code";
        };
    }

    public Optional<Equipment> findById(long id) {
        return Jdbc.queryOne(BASE + " WHERE e.id = ?", MAP, id);
    }

    public Optional<Equipment> findByCode(String code) {
        return Jdbc.queryOne(BASE + " WHERE LOWER(e.code) = LOWER(?)", MAP, code);
    }

    public Optional<Equipment> findBySerial(String serial) {
        if (serial == null || serial.isBlank()) {
            return Optional.empty();
        }
        return Jdbc.queryOne(BASE + " WHERE LOWER(e.serial_number) = LOWER(?)", MAP, serial);
    }

    public boolean codeExists(String code, Long excludeId) {
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM equipment WHERE LOWER(code)=LOWER(?)"
                : "SELECT COUNT(*) FROM equipment WHERE LOWER(code)=LOWER(?) AND id<>?";
        long c = excludeId == null
                ? Jdbc.count(sql, code)
                : Jdbc.count(sql, code, excludeId);
        return c > 0;
    }

    public boolean serialExists(String serial, Long excludeId) {
        if (serial == null || serial.isBlank()) {
            return false;
        }
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM equipment WHERE LOWER(serial_number)=LOWER(?)"
                : "SELECT COUNT(*) FROM equipment WHERE LOWER(serial_number)=LOWER(?) AND id<>?";
        long c = excludeId == null
                ? Jdbc.count(sql, serial)
                : Jdbc.count(sql, serial, excludeId);
        return c > 0;
    }

    public long insert(Equipment e) {
        return Jdbc.insert("""
                INSERT INTO equipment(code, name, category_id, subcategory, manufacturer_id, model,
                    serial_number, reference_number, equipment_type, criticality, risk_level, device_class,
                    status, department_id, location_id, location_details, supplier_id,
                    purchase_date, reception_date, commissioning_date, purchase_price,
                    warranty_months, warranty_end_date, maintenance_type, pm_frequency,
                    last_maintenance_date, next_maintenance_date, responsible_id, contract_id,
                    power_supply, power_rating, dimensions, weight_kg, fabrication_year,
                    notes, archived, created_by, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)""",
                e.getCode(), e.getName(), e.getCategoryId(), e.getSubcategory(), e.getManufacturerId(),
                e.getModel(), e.getSerialNumber(), e.getReferenceNumber(), e.getEquipmentType(),
                e.getCriticality(), e.getRiskLevel(), e.getDeviceClass(), e.getStatus(),
                e.getDepartmentId(), e.getLocationId(), e.getLocationDetails(), e.getSupplierId(),
                e.getPurchaseDate(), e.getReceptionDate(), e.getCommissioningDate(), e.getPurchasePrice(),
                e.getWarrantyMonths(), e.getWarrantyEndDate(), e.getMaintenanceType(), e.getPmFrequency(),
                e.getLastMaintenanceDate(), e.getNextMaintenanceDate(), e.getResponsibleId(), e.getContractId(),
                e.getPowerSupply(), e.getPowerRating(), e.getDimensions(), e.getWeightKg(),
                e.getFabricationYear(), e.getNotes(), e.getCreatedBy());
    }

    public void update(Equipment e) {
        Jdbc.update("""
                UPDATE equipment SET name=?, category_id=?, subcategory=?, manufacturer_id=?, model=?,
                    serial_number=?, reference_number=?, equipment_type=?, criticality=?, risk_level=?,
                    device_class=?, status=?, department_id=?, location_id=?, location_details=?, supplier_id=?,
                    purchase_date=?, reception_date=?, commissioning_date=?, purchase_price=?,
                    warranty_months=?, warranty_end_date=?, maintenance_type=?, pm_frequency=?,
                    last_maintenance_date=?, next_maintenance_date=?, responsible_id=?, contract_id=?,
                    power_supply=?, power_rating=?, dimensions=?, weight_kg=?, fabrication_year=?,
                    notes=?, archived=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?""",
                e.getName(), e.getCategoryId(), e.getSubcategory(), e.getManufacturerId(), e.getModel(),
                e.getSerialNumber(), e.getReferenceNumber(), e.getEquipmentType(), e.getCriticality(),
                e.getRiskLevel(), e.getDeviceClass(), e.getStatus(), e.getDepartmentId(), e.getLocationId(),
                e.getLocationDetails(), e.getSupplierId(), e.getPurchaseDate(), e.getReceptionDate(),
                e.getCommissioningDate(), e.getPurchasePrice(), e.getWarrantyMonths(), e.getWarrantyEndDate(),
                e.getMaintenanceType(), e.getPmFrequency(), e.getLastMaintenanceDate(),
                e.getNextMaintenanceDate(), e.getResponsibleId(), e.getContractId(), e.getPowerSupply(),
                e.getPowerRating(), e.getDimensions(), e.getWeightKg(), e.getFabricationYear(),
                e.getNotes(), e.isArchived(), e.getId());
    }

    public void updateStatus(long id, String status) {
        Jdbc.update("UPDATE equipment SET status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id);
    }

    public void updateMaintenanceDates(long id, java.time.LocalDate last, java.time.LocalDate next) {
        Jdbc.update("UPDATE equipment SET last_maintenance_date=?, next_maintenance_date=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                last, next, id);
    }

    public void archive(long id, boolean archived) {
        Jdbc.update("UPDATE equipment SET archived=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", archived, id);
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM equipment WHERE id=?", id);
    }

    // ---------- Compteurs (dashboard / filtres) ----------

    public long countAll() {
        return Jdbc.count("SELECT COUNT(*) FROM equipment WHERE archived = FALSE");
    }

    public long countByStatus(String status) {
        return Jdbc.count("SELECT COUNT(*) FROM equipment WHERE status=? AND archived = FALSE", status);
    }

    public List<Object[]> countByCategory() {
        return Jdbc.query("""
                SELECT c.name, COUNT(e.id)
                FROM equipment_categories c
                LEFT JOIN equipment e ON e.category_id = c.id AND e.archived = FALSE
                GROUP BY c.id, c.name HAVING COUNT(e.id) > 0
                ORDER BY COUNT(e.id) DESC""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getString(1), rs.getLong(2)});
    }

    public List<Object[]> countByDepartment() {
        return Jdbc.query("""
                SELECT d.name, COUNT(e.id)
                FROM departments d
                LEFT JOIN equipment e ON e.department_id = d.id AND e.archived = FALSE
                GROUP BY d.id, d.name HAVING COUNT(e.id) > 0
                ORDER BY COUNT(e.id) DESC""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getString(1), rs.getLong(2)});
    }

    public List<Object[]> countByCriticality() {
        return Jdbc.query("""
                SELECT criticality, COUNT(*) FROM equipment WHERE archived = FALSE
                GROUP BY criticality ORDER BY COUNT(*) DESC""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    public List<Object[]> countByManufacturer() {
        return Jdbc.query("""
                SELECT COALESCE(m.name, 'Non renseigné'), COUNT(e.id)
                FROM equipment e LEFT JOIN manufacturers m ON m.id = e.manufacturer_id
                WHERE e.archived = FALSE
                GROUP BY COALESCE(m.name, 'Non renseigné') ORDER BY COUNT(e.id) DESC""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getString(1), rs.getLong(2)});
    }

    public List<Equipment> findDueForMaintenance(int days, int limit) {
        return Jdbc.query(BASE + """
                 WHERE e.archived = FALSE AND e.next_maintenance_date IS NOT NULL
                   AND e.next_maintenance_date <= DATEADD('DAY', ?, CURRENT_DATE)
                 ORDER BY e.next_maintenance_date LIMIT ?""", MAP, days, limit);
    }

    /** Top équipements par nombre d'interventions (analytique). */
    public List<Object[]> topFailingEquipment(int limit) {
        return Jdbc.query("""
                SELECT e.code, e.name, COUNT(w.id) AS failures
                FROM work_orders w JOIN equipment e ON e.id = w.equipment_id
                WHERE w.status IN ('CLOTUREE','VALIDEE','RESOLUE')
                GROUP BY e.code, e.name ORDER BY failures DESC LIMIT ?""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getLong(3)}, limit);
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
