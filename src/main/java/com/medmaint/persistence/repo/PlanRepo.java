package com.medmaint.persistence.repo;

import com.medmaint.domain.model.MaintenancePlan;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.util.List;
import java.util.Optional;

/** Plans de maintenance préventive. */
public final class PlanRepo {

    private static final String BASE = """
            SELECT p.*, e.code AS equipment_code, e.name AS equipment_name, e.status AS equipment_status,
                   e.category_id, c.name AS checklist_name, t.full_name AS responsible_name
            FROM maintenance_plans p
            JOIN equipment e ON e.id = p.equipment_id
            LEFT JOIN maintenance_checklists c ON c.id = p.checklist_id
            LEFT JOIN technicians t ON t.id = p.responsible_id
            """;

    public static final Jdbc.RowMapper<MaintenancePlan> MAP = rs -> {
        MaintenancePlan p = new MaintenancePlan();
        p.setId(rs.getLong("id"));
        p.setEquipmentId(rs.getLong("equipment_id"));
        p.setEquipmentCode(rs.getString("equipment_code"));
        p.setEquipmentName(rs.getString("equipment_name"));
        p.setEquipmentStatus(rs.getString("equipment_status"));
        p.setCategoryId(rs.getLong("category_id"));
        p.setMaintenanceType(rs.getString("maintenance_type"));
        p.setFrequency(rs.getString("frequency"));
        p.setCustomDays((Integer) rs.getObject("custom_days"));
        p.setChecklistId(Jdbc.getLongOrNull(rs, "checklist_id"));
        p.setChecklistName(rs.getString("checklist_name"));
        p.setResponsibleId(Jdbc.getLongOrNull(rs, "responsible_id"));
        p.setResponsibleName(rs.getString("responsible_name"));
        p.setLastPerformed(Jdbc.getDate(rs, "last_performed"));
        p.setNextDue(Jdbc.getDate(rs, "next_due"));
        p.setEstimatedDurationMin((Integer) rs.getObject("estimated_duration_min"));
        p.setRequiredParts(rs.getString("required_parts"));
        p.setRequiredTools(rs.getString("required_tools"));
        p.setInstructions(rs.getString("instructions"));
        p.setStatus(rs.getString("status"));
        p.setActive(Jdbc.getBool(rs, "active"));
        p.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        p.setUpdatedAt(Jdbc.getDateTime(rs, "updated_at"));
        return p;
    };

    public record Filter(String text, String frequency, String statusBucket, Long equipmentId,
                         Long responsibleId, Long departmentId, boolean includeInactive) {
    }

    public Page<MaintenancePlan> search(Filter f, int page, int pageSize, String sortCol, String sortDir) {
        SqlWhere w = where(f);
        long total = Jdbc.count("SELECT COUNT(*) FROM maintenance_plans p " + joins() + " WHERE " + w.clause(),
                w.params());
        String order = allowedSort(sortCol) + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        int offset = Math.max(0, (page - 1) * pageSize);
        List<MaintenancePlan> items = Jdbc.query(
                BASE + " WHERE " + w.clause() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<MaintenancePlan> findAll(Filter f, int limit) {
        SqlWhere w = where(f);
        return Jdbc.query(BASE + " WHERE " + w.clause() + " ORDER BY p.next_due LIMIT ?",
                MAP, concat(w.params(), limit));
    }

    private SqlWhere where(Filter f) {
        SqlWhere w = new SqlWhere("1=1");
        if (f == null) return w;
        if (!f.includeInactive()) {
            w.add("p.active = TRUE", Boolean.TRUE);
        }
        w.addLikes(List.of("e.name", "e.code"), f.text())
                .add("p.frequency = ?", f.frequency())
                .add("p.equipment_id = ?", f.equipmentId())
                .add("p.responsible_id = ?", f.responsibleId())
                .add("e.department_id = ?", f.departmentId());
        java.time.LocalDate today = java.time.LocalDate.now();
        switch (f.statusBucket() == null ? "" : f.statusBucket()) {
            case "EN_RETARD" -> w.add("p.next_due < ?", today);
            case "AUJOURD_HUI" -> w.add("p.next_due = ?", today);
            case "CETTE_SEMAINE" -> {
                w.add("p.next_due >= ?", today);
                w.add("p.next_due <= ?", today.plusDays(7));
            }
            case "A_VENIR" -> w.add("p.next_due > ?", today.plusDays(7));
            default -> { /* tous */ }
        }
        return w;
    }

    private static String joins() {
        return """
                JOIN equipment e ON e.id = p.equipment_id
                LEFT JOIN maintenance_checklists c ON c.id = p.checklist_id
                LEFT JOIN technicians t ON t.id = p.responsible_id
                """;
    }

    private static String allowedSort(String col) {
        return switch (col == null ? "" : col) {
            case "equipment" -> "e.name";
            case "frequency" -> "p.frequency";
            case "type" -> "p.maintenance_type";
            case "lastPerformed" -> "p.last_performed";
            case "status" -> "p.status";
            default -> "p.next_due";
        };
    }

    public Optional<MaintenancePlan> findById(long id) {
        return Jdbc.queryOne(BASE + " WHERE p.id = ?", MAP, id);
    }

    public List<MaintenancePlan> findByEquipment(long equipmentId) {
        return Jdbc.query(BASE + " WHERE p.equipment_id = ? ORDER BY p.next_due", MAP, equipmentId);
    }

    /** Plans échus dans N jours (y compris en retard). */
    public List<MaintenancePlan> findDueWithin(int days) {
        return Jdbc.query(BASE + """
                 WHERE p.active = TRUE AND p.next_due <= DATEADD('DAY', ?, CURRENT_DATE)
                 ORDER BY p.next_due""", MAP, days);
    }

    public long countOverdue() {
        return Jdbc.count("""
                SELECT COUNT(*) FROM maintenance_plans p JOIN equipment e ON e.id = p.equipment_id
                WHERE p.active = TRUE AND p.next_due < CURRENT_DATE AND e.archived = FALSE""");
    }

    public long countDueIn(int days) {
        return Jdbc.count("""
                SELECT COUNT(*) FROM maintenance_plans p JOIN equipment e ON e.id = p.equipment_id
                WHERE p.active = TRUE AND p.next_due >= CURRENT_DATE
                  AND p.next_due <= DATEADD('DAY', ?, CURRENT_DATE) AND e.archived = FALSE""", days);
    }

    public long countActive() {
        return Jdbc.count("SELECT COUNT(*) FROM maintenance_plans WHERE active = TRUE");
    }

    public long insert(MaintenancePlan p) {
        return Jdbc.insert("""
                INSERT INTO maintenance_plans(equipment_id, maintenance_type, frequency, custom_days,
                    checklist_id, responsible_id, last_performed, next_due, estimated_duration_min,
                    required_parts, required_tools, instructions, status, active, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)""",
                p.getEquipmentId(), p.getMaintenanceType(), p.getFrequency(), p.getCustomDays(),
                p.getChecklistId(), p.getResponsibleId(), p.getLastPerformed(), p.getNextDue(),
                p.getEstimatedDurationMin(), p.getRequiredParts(), p.getRequiredTools(),
                p.getInstructions(), p.getStatus(), p.isActive());
    }

    public void update(MaintenancePlan p) {
        Jdbc.update("""
                UPDATE maintenance_plans SET equipment_id=?, maintenance_type=?, frequency=?, custom_days=?,
                    checklist_id=?, responsible_id=?, last_performed=?, next_due=?, estimated_duration_min=?,
                    required_parts=?, required_tools=?, instructions=?, status=?, active=?,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=?""",
                p.getEquipmentId(), p.getMaintenanceType(), p.getFrequency(), p.getCustomDays(),
                p.getChecklistId(), p.getResponsibleId(), p.getLastPerformed(), p.getNextDue(),
                p.getEstimatedDurationMin(), p.getRequiredParts(), p.getRequiredTools(),
                p.getInstructions(), p.getStatus(), p.isActive(), p.getId());
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM maintenance_plans WHERE id=?", id);
    }

    /** Plans concernant un équipement (pour garde-fou de suppression). */
    public long countByEquipment(long equipmentId) {
        return Jdbc.count("SELECT COUNT(*) FROM maintenance_plans WHERE equipment_id=?", equipmentId);
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
