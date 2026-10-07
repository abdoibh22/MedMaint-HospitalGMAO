package com.medmaint.persistence.repo;

import com.medmaint.domain.model.Technician;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Techniciens, compétences et statistiques de charge. */
public final class TechnicianRepo {

    private static final String BASE = """
            SELECT t.*, d.name AS department_name
            FROM technicians t LEFT JOIN departments d ON d.id = t.department_id
            """;

    public static final Jdbc.RowMapper<Technician> MAP = rs -> {
        Technician t = new Technician();
        t.setId(rs.getLong("id"));
        t.setUserId(Jdbc.getLongOrNull(rs, "user_id"));
        t.setEmployeeNo(rs.getString("employee_no"));
        t.setFullName(rs.getString("full_name"));
        t.setDepartmentId(Jdbc.getLongOrNull(rs, "department_id"));
        t.setDepartmentName(rs.getString("department_name"));
        t.setSpecialty(rs.getString("specialty"));
        t.setPhone(rs.getString("phone"));
        t.setEmail(rs.getString("email"));
        t.setCertifications(rs.getString("certifications"));
        t.setStatus(rs.getString("status"));
        t.setAvailability(rs.getString("availability"));
        t.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        return t;
    };

    public Page<Technician> search(String text, String status, Long departmentId,
                                   int page, int pageSize, String sortCol, String sortDir) {
        SqlWhere w = new SqlWhere("1=1");
        w.addLikes(List.of("t.full_name", "t.employee_no", "t.specialty"), text)
                .add("t.status = ?", status)
                .add("t.department_id = ?", departmentId);
        long total = Jdbc.count("SELECT COUNT(*) FROM technicians t LEFT JOIN departments d ON d.id=t.department_id WHERE "
                + w.clause(), w.params());
        String order = switch (sortCol == null ? "" : sortCol) {
            case "employeeNo" -> "t.employee_no";
            case "specialty" -> "t.specialty";
            case "status" -> "t.status";
            case "availability" -> "t.availability";
            default -> "t.full_name";
        } + ("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        int offset = Math.max(0, (page - 1) * pageSize);
        List<Technician> items = Jdbc.query(
                BASE + " WHERE " + w.clause() + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<Technician> findAll() {
        return Jdbc.query(BASE + " ORDER BY t.full_name", MAP);
    }

    public List<Technician> findAvailable() {
        return Jdbc.query(BASE + " WHERE t.status='ACTIF' ORDER BY t.full_name", MAP);
    }

    public Optional<Technician> findById(long id) {
        return Jdbc.queryOne(BASE + " WHERE t.id=?", MAP, id);
    }

    public Optional<Technician> findByUserId(long userId) {
        return Jdbc.queryOne(BASE + " WHERE t.user_id=?", MAP, userId);
    }

    public boolean employeeNoExists(String no, Long excludeId) {
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM technicians WHERE LOWER(employee_no)=LOWER(?)"
                : "SELECT COUNT(*) FROM technicians WHERE LOWER(employee_no)=LOWER(?) AND id<>?";
        return (excludeId == null ? Jdbc.count(sql, no) : Jdbc.count(sql, no, excludeId)) > 0;
    }

    public long insert(Technician t) {
        return Jdbc.insert("""
                INSERT INTO technicians(user_id, employee_no, full_name, department_id, specialty,
                    phone, email, certifications, status, availability, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)""",
                t.getUserId(), t.getEmployeeNo(), t.getFullName(), t.getDepartmentId(), t.getSpecialty(),
                t.getPhone(), t.getEmail(), t.getCertifications(), t.getStatus(), t.getAvailability());
    }

    public void update(Technician t) {
        Jdbc.update("""
                UPDATE technicians SET user_id=?, employee_no=?, full_name=?, department_id=?, specialty=?,
                    phone=?, email=?, certifications=?, status=?, availability=? WHERE id=?""",
                t.getUserId(), t.getEmployeeNo(), t.getFullName(), t.getDepartmentId(), t.getSpecialty(),
                t.getPhone(), t.getEmail(), t.getCertifications(), t.getStatus(), t.getAvailability(), t.getId());
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM technicians WHERE id=?", id);
    }

    public boolean hasOpenWork(long id) {
        return Jdbc.count("""
                SELECT COUNT(*) FROM work_orders
                WHERE technician_id=? AND status NOT IN ('RESOLUE','VALIDEE','CLOTUREE')""", id) > 0;
    }

    // ---------- Compétences ----------

    public List<String> findSkills(long technicianId) {
        return Jdbc.query("SELECT skill FROM technician_skills WHERE technician_id=? ORDER BY skill",
                rs -> rs.getString(1), technicianId);
    }

    public void replaceSkills(long technicianId, List<String> skills) {
        Jdbc.update("DELETE FROM technician_skills WHERE technician_id=?", technicianId);
        if (skills == null) return;
        for (String s : skills) {
            if (s != null && !s.isBlank()) {
                Jdbc.update("INSERT INTO technician_skills(technician_id, skill) VALUES (?,?)",
                        technicianId, s.trim());
            }
        }
    }

    // ---------- Statistiques ----------

    /** Remplit les compteurs / statistiques d'un technicien. */
    public void fillStats(Technician t) {
        t.setAssignedCount(Jdbc.count(
                "SELECT COUNT(*) FROM work_orders WHERE technician_id=?", t.getId()));
        t.setCompletedCount(Jdbc.count(
                "SELECT COUNT(*) FROM work_orders WHERE technician_id=? AND status IN ('VALIDEE','CLOTUREE')",
                t.getId()));
        t.setOpenCount(Jdbc.count(
                "SELECT COUNT(*) FROM work_orders WHERE technician_id=? AND status NOT IN ('RESOLUE','VALIDEE','CLOTUREE')",
                t.getId()));
        BigDecimal avg = Jdbc.queryOne("""
                SELECT AVG(DATEDIFF('MINUTE', started_at, completed_at) / 60.0) FROM work_orders
                WHERE technician_id=? AND started_at IS NOT NULL AND completed_at IS NOT NULL""",
                rs -> rs.getBigDecimal(1), t.getId()).orElse(null);
        t.setAvgDurationHours(avg == null ? BigDecimal.ZERO : avg.setScale(1, java.math.RoundingMode.HALF_UP));
        List<String> skills = findSkills(t.getId());
        t.setSkillsCsv(String.join(", ", skills));
    }

    /** Charge de travail : interventions ouvertes par technicien. */
    public List<Object[]> workload() {
        return Jdbc.query("""
                SELECT t.full_name,
                       SUM(CASE WHEN w.status NOT IN ('RESOLUE','VALIDEE','CLOTUREE') THEN 1 ELSE 0 END) AS open_wos,
                       COUNT(w.id) AS total_wos
                FROM technicians t
                LEFT JOIN work_orders w ON w.technician_id = t.id
                WHERE t.status = 'ACTIF'
                GROUP BY t.full_name ORDER BY open_wos DESC, t.full_name""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getLong(2), rs.getLong(3)});
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
