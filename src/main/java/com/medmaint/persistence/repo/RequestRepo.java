package com.medmaint.persistence.repo;

import com.medmaint.domain.model.InterventionRequest;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.util.List;
import java.util.Optional;

/** Demandes d'intervention (signalements de panne). */
public final class RequestRepo {

    private static final String BASE = """
            SELECT r.*, e.code AS equipment_code, e.name AS equipment_name, e.criticality,
                   d.name AS department_name, u.full_name AS requester_name,
                   t.full_name AS technician_name, w.wo_no, w.id AS work_order_id
            FROM intervention_requests r
            JOIN equipment e ON e.id = r.equipment_id
            JOIN departments d ON d.id = r.department_id
            JOIN users u ON u.id = r.requester_id
            LEFT JOIN technicians t ON t.id = r.assigned_technician_id
            LEFT JOIN work_orders w ON w.request_id = r.id
            """;

    public static final Jdbc.RowMapper<InterventionRequest> MAP = rs -> {
        InterventionRequest r = new InterventionRequest();
        r.setId(rs.getLong("id"));
        r.setRequestNo(rs.getString("request_no"));
        r.setEquipmentId(rs.getLong("equipment_id"));
        r.setEquipmentCode(rs.getString("equipment_code"));
        r.setEquipmentName(rs.getString("equipment_name"));
        r.setDepartmentId(rs.getLong("department_id"));
        r.setDepartmentName(rs.getString("department_name"));
        r.setRequesterId(rs.getLong("requester_id"));
        r.setRequesterName(rs.getString("requester_name"));
        r.setReportedAt(Jdbc.getDateTime(rs, "reported_at"));
        r.setProblemDescription(rs.getString("problem_description"));
        r.setSymptoms(rs.getString("symptoms"));
        r.setPriority(rs.getString("priority"));
        r.setSafetyImpact(Jdbc.getBool(rs, "safety_impact"));
        r.setAvailability(rs.getString("availability"));
        r.setStatus(rs.getString("status"));
        r.setAssignedTechnicianId(Jdbc.getLongOrNull(rs, "assigned_technician_id"));
        r.setAssignedTechnicianName(rs.getString("technician_name"));
        r.setWorkOrderId(Jdbc.getLongOrNull(rs, "work_order_id"));
        r.setWorkOrderNo(rs.getString("wo_no"));
        r.setRejectionReason(rs.getString("rejection_reason"));
        r.setResolutionNote(rs.getString("resolution_note"));
        r.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        r.setUpdatedAt(Jdbc.getDateTime(rs, "updated_at"));
        return r;
    };

    public record Filter(String text, String status, String priority, Long departmentId,
                         Long equipmentId, Long technicianId, Long requesterId,
                         java.time.LocalDate from, java.time.LocalDate to) {
    }

    public Page<InterventionRequest> search(Filter f, int page, int pageSize, String sortCol, String sortDir) {
        SqlWhere w = where(f);
        long total = Jdbc.count("SELECT COUNT(*) FROM intervention_requests r " + joins() + " WHERE " + w.clause(),
                w.params());
        String order = allowedSort(sortCol) + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        int offset = Math.max(0, (page - 1) * pageSize);
        List<InterventionRequest> items = Jdbc.query(
                BASE + " WHERE " + w.clause() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<InterventionRequest> findAll(Filter f, int limit) {
        SqlWhere w = where(f);
        return Jdbc.query(BASE + " WHERE " + w.clause() + " ORDER BY r.reported_at DESC LIMIT ?",
                MAP, concat(w.params(), limit));
    }

    private SqlWhere where(Filter f) {
        SqlWhere w = new SqlWhere("1=1");
        if (f == null) return w;
        w.addLikes(List.of("r.request_no", "e.name", "e.code"), f.text())
                .add("r.status = ?", f.status())
                .add("r.priority = ?", f.priority())
                .add("r.department_id = ?", f.departmentId())
                .add("r.equipment_id = ?", f.equipmentId())
                .add("r.assigned_technician_id = ?", f.technicianId())
                .add("r.requester_id = ?", f.requesterId());
        if (f.from() != null) w.add("r.reported_at >= ?", f.from().atStartOfDay());
        if (f.to() != null) w.add("r.reported_at < ?", f.to().plusDays(1).atStartOfDay());
        return w;
    }

    private static String joins() {
        return """
                JOIN equipment e ON e.id = r.equipment_id
                JOIN departments d ON d.id = r.department_id
                JOIN users u ON u.id = r.requester_id
                LEFT JOIN technicians t ON t.id = r.assigned_technician_id
                """;
    }

    private static String allowedSort(String col) {
        return switch (col == null ? "" : col) {
            case "priority" -> "CASE r.priority WHEN 'CRITIQUE' THEN 5 WHEN 'URGENTE' THEN 4 WHEN 'HAUTE' THEN 3 WHEN 'NORMALE' THEN 2 ELSE 1 END";
            case "status" -> "r.status";
            case "department" -> "d.name";
            case "equipment" -> "e.name";
            default -> "r.reported_at";
        };
    }

    public Optional<InterventionRequest> findById(long id) {
        return Jdbc.queryOne(BASE + " WHERE r.id = ?", MAP, id);
    }

    public Optional<InterventionRequest> findByNo(String no) {
        return Jdbc.queryOne(BASE + " WHERE r.request_no = ?", MAP, no);
    }

    /** Prochain numéro de demande séquence : DEM-00001. */
    public String nextRequestNo() {
        long n = Jdbc.count("SELECT COUNT(*) FROM intervention_requests") + 1;
        String no;
        do {
            no = String.format("DEM-%05d", n);
            n++;
        } while (findByNo(no).isPresent());
        return no;
    }

    public long insert(InterventionRequest r) {
        return Jdbc.insert("""
                INSERT INTO intervention_requests(request_no, equipment_id, department_id, requester_id,
                    reported_at, problem_description, symptoms, priority, safety_impact, availability,
                    status, assigned_technician_id, resolution_note, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)""",
                r.getRequestNo(), r.getEquipmentId(), r.getDepartmentId(), r.getRequesterId(),
                r.getReportedAt(), r.getProblemDescription(), r.getSymptoms(), r.getPriority(),
                r.isSafetyImpact(), r.getAvailability(), r.getStatus(), r.getAssignedTechnicianId(),
                r.getResolutionNote());
    }

    public void update(InterventionRequest r) {
        Jdbc.update("""
                UPDATE intervention_requests SET problem_description=?, symptoms=?, priority=?,
                    safety_impact=?, availability=?, status=?, assigned_technician_id=?,
                    rejection_reason=?, resolution_note=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?""",
                r.getProblemDescription(), r.getSymptoms(), r.getPriority(), r.isSafetyImpact(),
                r.getAvailability(), r.getStatus(), r.getAssignedTechnicianId(),
                r.getRejectionReason(), r.getResolutionNote(), r.getId());
    }

    public void updateStatus(long id, String status) {
        Jdbc.update("UPDATE intervention_requests SET status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                status, id);
    }

    /** Le lien demande ↔ OT vit dans work_orders.request_id (colonne de la table). */
    public void linkWorkOrder(long requestId, long workOrderId) {
        Jdbc.update("UPDATE work_orders SET request_id=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                requestId, workOrderId);
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM intervention_requests WHERE id=?", id);
    }

    public long countOpen() {
        return Jdbc.count("""
                SELECT COUNT(*) FROM intervention_requests
                WHERE status NOT IN ('RESOLUE','CLOTUREE','REJETEE')""");
    }

    public long countByStatus(String status) {
        return Jdbc.count("SELECT COUNT(*) FROM intervention_requests WHERE status=?", status);
    }

    public long countUrgentOpen() {
        return Jdbc.count("""
                SELECT COUNT(*) FROM intervention_requests
                WHERE status NOT IN ('RESOLUE','CLOTUREE','REJETEE')
                  AND priority IN ('URGENTE','CRITIQUE')""");
    }

    public List<Object[]> countByPriority() {
        return Jdbc.query("""
                SELECT priority, COUNT(*) FROM intervention_requests
                GROUP BY priority""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getString(1), rs.getLong(2)});
    }

    /** Nombre de demandes par mois sur N derniers mois (graphique). */
    public List<Object[]> countByMonth(int months) {
        return Jdbc.query("""
                SELECT TO_CHAR(reported_at, 'YYYY-MM') AS ym, COUNT(*)
                FROM intervention_requests
                WHERE reported_at >= DATEADD('MONTH', ?, CURRENT_TIMESTAMP)
                GROUP BY TO_CHAR(reported_at, 'YYYY-MM')
                ORDER BY ym""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getString(1), rs.getLong(2)}, -months);
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
