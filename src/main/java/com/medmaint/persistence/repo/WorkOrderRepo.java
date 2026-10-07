package com.medmaint.persistence.repo;

import com.medmaint.domain.model.WorkOrder;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Ordres de travail (interventions correctives). */
public final class WorkOrderRepo {

    private static final String BASE = """
            SELECT w.*, e.code AS equipment_code, e.name AS equipment_name, e.serial_number,
                   d.name AS department_name, t.full_name AS technician_name,
                   r.request_no, cu.full_name AS created_by_name, vu.full_name AS validated_by_name
            FROM work_orders w
            JOIN equipment e ON e.id = w.equipment_id
            LEFT JOIN departments d ON d.id = e.department_id
            LEFT JOIN technicians t ON t.id = w.technician_id
            LEFT JOIN intervention_requests r ON r.id = w.request_id
            LEFT JOIN users cu ON cu.id = w.created_by
            LEFT JOIN users vu ON vu.id = w.validated_by
            """;

    public static final Jdbc.RowMapper<WorkOrder> MAP = rs -> {
        WorkOrder w = new WorkOrder();
        w.setId(rs.getLong("id"));
        w.setWoNo(rs.getString("wo_no"));
        w.setRequestId(Jdbc.getLongOrNull(rs, "request_id"));
        w.setRequestNo(rs.getString("request_no"));
        w.setEquipmentId(rs.getLong("equipment_id"));
        w.setEquipmentCode(rs.getString("equipment_code"));
        w.setEquipmentName(rs.getString("equipment_name"));
        w.setEquipmentSerial(rs.getString("serial_number"));
        w.setDepartmentName(rs.getString("department_name"));
        w.setTechnicianId(Jdbc.getLongOrNull(rs, "technician_id"));
        w.setTechnicianName(rs.getString("technician_name"));
        w.setTeam(rs.getString("team"));
        w.setPriority(rs.getString("priority"));
        w.setStatus(rs.getString("status"));
        w.setOpenedAt(Jdbc.getDateTime(rs, "opened_at"));
        w.setScheduledAt(Jdbc.getDateTime(rs, "scheduled_at"));
        w.setStartedAt(Jdbc.getDateTime(rs, "started_at"));
        w.setCompletedAt(Jdbc.getDateTime(rs, "completed_at"));
        w.setValidatedAt(Jdbc.getDateTime(rs, "validated_at"));
        w.setClosedAt(Jdbc.getDateTime(rs, "closed_at"));
        w.setDiagnosis(rs.getString("diagnosis"));
        w.setFailureCause(rs.getString("failure_cause"));
        w.setCorrectiveAction(rs.getString("corrective_action"));
        w.setWorkPerformed(rs.getString("work_performed"));
        w.setFailureType(rs.getString("failure_type"));
        w.setLaborHours(rs.getBigDecimal("labor_hours"));
        w.setDowntimeHours(rs.getBigDecimal("downtime_hours"));
        w.setLaborCost(rs.getBigDecimal("labor_cost"));
        w.setPartsCost(rs.getBigDecimal("parts_cost"));
        w.setTotalCost(rs.getBigDecimal("total_cost"));
        w.setComments(rs.getString("comments"));
        w.setCreatedBy(Jdbc.getLongOrNull(rs, "created_by"));
        w.setCreatedByName(rs.getString("created_by_name"));
        w.setValidatedBy(Jdbc.getLongOrNull(rs, "validated_by"));
        w.setValidatedByName(rs.getString("validated_by_name"));
        w.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        w.setUpdatedAt(Jdbc.getDateTime(rs, "updated_at"));
        return w;
    };

    public record Filter(String text, String status, String priority, Long technicianId,
                         Long equipmentId, Long departmentId, Long requestId,
                         java.time.LocalDate from, java.time.LocalDate to) {
    }

    public Page<WorkOrder> search(Filter f, int page, int pageSize, String sortCol, String sortDir) {
        SqlWhere w = where(f);
        long total = Jdbc.count("SELECT COUNT(*) FROM work_orders w " + joins() + " WHERE " + w.clause(), w.params());
        String order = allowedSort(sortCol) + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        int offset = Math.max(0, (page - 1) * pageSize);
        List<WorkOrder> items = Jdbc.query(
                BASE + " WHERE " + w.clause() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<WorkOrder> findAll(Filter f, int limit) {
        SqlWhere w = where(f);
        return Jdbc.query(BASE + " WHERE " + w.clause() + " ORDER BY w.opened_at DESC LIMIT ?",
                MAP, concat(w.params(), limit));
    }

    private SqlWhere where(Filter f) {
        SqlWhere w = new SqlWhere("1=1");
        if (f == null) return w;
        w.addLikes(List.of("w.wo_no", "e.name", "e.code"), f.text())
                .add("w.status = ?", f.status())
                .add("w.priority = ?", f.priority())
                .add("w.technician_id = ?", f.technicianId())
                .add("w.equipment_id = ?", f.equipmentId())
                .add("e.department_id = ?", f.departmentId())
                .add("w.request_id = ?", f.requestId());
        if (f.from() != null) w.add("w.opened_at >= ?", f.from().atStartOfDay());
        if (f.to() != null) w.add("w.opened_at < ?", f.to().plusDays(1).atStartOfDay());
        return w;
    }

    private static String joins() {
        return """
                JOIN equipment e ON e.id = w.equipment_id
                LEFT JOIN departments d ON d.id = e.department_id
                LEFT JOIN technicians t ON t.id = w.technician_id
                """;
    }

    private static String allowedSort(String col) {
        return switch (col == null ? "" : col) {
            case "priority" -> "CASE w.priority WHEN 'CRITIQUE' THEN 5 WHEN 'URGENTE' THEN 4 WHEN 'HAUTE' THEN 3 WHEN 'NORMALE' THEN 2 ELSE 1 END";
            case "status" -> "w.status";
            case "scheduled" -> "w.scheduled_at";
            case "completed" -> "w.completed_at";
            case "cost" -> "w.total_cost";
            default -> "w.opened_at";
        };
    }

    public Optional<WorkOrder> findById(long id) {
        return Jdbc.queryOne(BASE + " WHERE w.id = ?", MAP, id);
    }

    public Optional<WorkOrder> findByNo(String no) {
        return Jdbc.queryOne(BASE + " WHERE w.wo_no = ?", MAP, no);
    }

    public String nextWoNo() {
        long n = Jdbc.count("SELECT COUNT(*) FROM work_orders") + 1;
        String no;
        do {
            no = String.format("OT-%05d", n);
            n++;
        } while (findByNo(no).isPresent());
        return no;
    }

    public long insert(WorkOrder w) {
        return Jdbc.insert("""
                INSERT INTO work_orders(wo_no, request_id, equipment_id, technician_id, team, priority,
                    status, opened_at, scheduled_at, diagnosis, failure_cause, corrective_action,
                    work_performed, failure_type, labor_hours, downtime_hours, labor_cost, parts_cost,
                    total_cost, comments, created_by, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)""",
                w.getWoNo(), w.getRequestId(), w.getEquipmentId(), w.getTechnicianId(), w.getTeam(),
                w.getPriority(), w.getStatus(), w.getOpenedAt(), w.getScheduledAt(), w.getDiagnosis(),
                w.getFailureCause(), w.getCorrectiveAction(), w.getWorkPerformed(), w.getFailureType(),
                w.getLaborHours(), w.getDowntimeHours(), w.getLaborCost(), w.getPartsCost(),
                w.getTotalCost(), w.getComments(), w.getCreatedBy());
    }

    public void update(WorkOrder w) {
        Jdbc.update("""
                UPDATE work_orders SET technician_id=?, team=?, priority=?, status=?, scheduled_at=?,
                    started_at=?, completed_at=?, validated_at=?, closed_at=?, diagnosis=?, failure_cause=?,
                    corrective_action=?, work_performed=?, failure_type=?, labor_hours=?, downtime_hours=?,
                    labor_cost=?, parts_cost=?, total_cost=?, comments=?, validated_by=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?""",
                w.getTechnicianId(), w.getTeam(), w.getPriority(), w.getStatus(), w.getScheduledAt(),
                w.getStartedAt(), w.getCompletedAt(), w.getValidatedAt(), w.getClosedAt(), w.getDiagnosis(),
                w.getFailureCause(), w.getCorrectiveAction(), w.getWorkPerformed(), w.getFailureType(),
                w.getLaborHours(), w.getDowntimeHours(), w.getLaborCost(), w.getPartsCost(),
                w.getTotalCost(), w.getComments(), w.getValidatedBy(), w.getId());
    }

    public void updateStatus(long id, String status) {
        Jdbc.update("UPDATE work_orders SET status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id);
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM work_orders WHERE id=?", id);
    }

    /** Interventions liées à un équipement (garde-fou de suppression). */
    public long countOpenForEquipment(long equipmentId) {
        return Jdbc.count("SELECT COUNT(*) FROM work_orders WHERE equipment_id=?", equipmentId);
    }

    // ---------- Statistiques ----------

    public long countOpen() {
        return Jdbc.count("""
                SELECT COUNT(*) FROM work_orders
                WHERE status NOT IN ('RESOLUE','VALIDEE','CLOTUREE')""");
    }

    public long countByStatus(String status) {
        return Jdbc.count("SELECT COUNT(*) FROM work_orders WHERE status=?", status);
    }

    public long countCompletedSince(int days) {
        return Jdbc.count("""
                SELECT COUNT(*) FROM work_orders
                WHERE completed_at IS NOT NULL AND completed_at >= DATEADD('DAY', ?, CURRENT_TIMESTAMP)""",
                -days);
    }

    /** MTTR moyen (heures) des interventions clôturées. */
    public BigDecimal avgRepairHours() {
        return Jdbc.queryOne("""
                SELECT AVG(DATEDIFF('MINUTE', started_at, completed_at) / 60.0)
                FROM work_orders
                WHERE started_at IS NOT NULL AND completed_at IS NOT NULL""",
                rs -> {
                    BigDecimal v = rs.getBigDecimal(1);
                    return v == null ? BigDecimal.ZERO : v.setScale(1, java.math.RoundingMode.HALF_UP);
                }).orElse(BigDecimal.ZERO);
    }

    /** Temps moyen d'intervention (délai d'affectation → clôture), en heures. */
    public BigDecimal avgInterventionHours() {
        return Jdbc.queryOne("""
                SELECT AVG(DATEDIFF('MINUTE', opened_at, completed_at) / 60.0)
                FROM work_orders WHERE completed_at IS NOT NULL""",
                rs -> {
                    BigDecimal v = rs.getBigDecimal(1);
                    return v == null ? BigDecimal.ZERO : v.setScale(1, java.math.RoundingMode.HALF_UP);
                }).orElse(BigDecimal.ZERO);
    }

    public BigDecimal totalCostSince(int days) {
        return Jdbc.queryOne("""
                SELECT COALESCE(SUM(total_cost), 0) FROM work_orders
                WHERE completed_at >= DATEADD('DAY', ?, CURRENT_TIMESTAMP)""",
                rs -> rs.getBigDecimal(1), -days).orElse(BigDecimal.ZERO);
    }

    public List<Object[]> costByMonth(int months) {
        return Jdbc.query("""
                SELECT TO_CHAR(COALESCE(completed_at, opened_at), 'YYYY-MM') AS ym,
                       COALESCE(SUM(total_cost), 0)
                FROM work_orders
                WHERE COALESCE(completed_at, opened_at) >= DATEADD('MONTH', ?, CURRENT_TIMESTAMP)
                GROUP BY TO_CHAR(COALESCE(completed_at, opened_at), 'YYYY-MM')
                ORDER BY ym""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getString(1), rs.getBigDecimal(2)}, -months);
    }

    /** Correctif vs préventif : nombre d'interventions correctives par mois. */
    public List<Object[]> correctiveByMonth(int months) {
        return Jdbc.query("""
                SELECT TO_CHAR(opened_at, 'YYYY-MM') AS ym, COUNT(*)
                FROM work_orders
                WHERE opened_at >= DATEADD('MONTH', ?, CURRENT_TIMESTAMP)
                GROUP BY TO_CHAR(opened_at, 'YYYY-MM') ORDER BY ym""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getString(1), rs.getLong(2)}, -months);
    }

    public List<Object[]> countByMonth(int months) {
        return correctiveByMonth(months);
    }

    /** Arrêts (downtime) par service sur N mois. */
    public List<Object[]> downtimeByDepartment(int months) {
        return Jdbc.query("""
                SELECT COALESCE(d.name, 'Non renseigné'), COALESCE(SUM(w.downtime_hours), 0)
                FROM work_orders w
                JOIN equipment e ON e.id = w.equipment_id
                LEFT JOIN departments d ON d.id = e.department_id
                WHERE w.opened_at >= DATEADD('MONTH', ?, CURRENT_TIMESTAMP)
                GROUP BY COALESCE(d.name, 'Non renseigné')
                ORDER BY 2 DESC""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getString(1), rs.getBigDecimal(2)}, -months);
    }

    public List<Object[]> failuresByCategory(int months) {
        return Jdbc.query("""
                SELECT c.name, COUNT(*)
                FROM work_orders w
                JOIN equipment e ON e.id = w.equipment_id
                JOIN equipment_categories c ON c.id = e.category_id
                WHERE w.opened_at >= DATEADD('MONTH', ?, CURRENT_TIMESTAMP)
                GROUP BY c.name ORDER BY COUNT(*) DESC""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getString(1), rs.getLong(2)}, -months);
    }

    /** MTTR moyen par catégorie d'équipement (heures). */
    public List<Object[]> mttrByCategory() {
        return Jdbc.query("""
                SELECT c.name, AVG(DATEDIFF('MINUTE', w.started_at, w.completed_at) / 60.0)
                FROM work_orders w
                JOIN equipment e ON e.id = w.equipment_id
                JOIN equipment_categories c ON c.id = e.category_id
                WHERE w.started_at IS NOT NULL AND w.completed_at IS NOT NULL
                GROUP BY c.name ORDER BY 2""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getString(1), rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2)});
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
