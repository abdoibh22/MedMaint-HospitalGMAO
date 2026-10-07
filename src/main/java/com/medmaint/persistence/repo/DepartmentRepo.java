package com.medmaint.persistence.repo;

import com.medmaint.domain.model.Department;
import com.medmaint.domain.model.Location;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.util.List;
import java.util.Optional;

/** Services hospitaliers et localisations. */
public final class DepartmentRepo {

    private static final Jdbc.RowMapper<Department> MAP = rs -> {
        Department d = new Department();
        d.setId(rs.getLong("id"));
        d.setCode(rs.getString("code"));
        d.setName(rs.getString("name"));
        d.setHeadName(rs.getString("head_name"));
        d.setLocation(rs.getString("location"));
        d.setPhone(rs.getString("phone"));
        d.setEmail(rs.getString("email"));
        return d;
    };

    private static final Jdbc.RowMapper<Location> LOC_MAP = rs -> {
        Location l = new Location();
        l.setId(rs.getLong("id"));
        l.setDepartmentId(rs.getLong("department_id"));
        l.setCode(rs.getString("code"));
        l.setName(rs.getString("name"));
        l.setFloor(rs.getString("floor"));
        return l;
    };

    public List<Department> findAll() {
        return Jdbc.query("SELECT * FROM departments ORDER BY name", MAP);
    }

    public Optional<Department> findById(long id) {
        return Jdbc.queryOne("SELECT * FROM departments WHERE id=?", MAP, id);
    }

    public Optional<Department> findByCode(String code) {
        return Jdbc.queryOne("SELECT * FROM departments WHERE LOWER(code)=LOWER(?)", MAP, code);
    }

    public Page<Department> search(String text, int page, int pageSize) {
        SqlWhere w = new SqlWhere("1=1");
        w.addLikes(List.of("name", "code"), text);
        long total = Jdbc.count("SELECT COUNT(*) FROM departments WHERE " + w.clause(), w.params());
        int offset = Math.max(0, (page - 1) * pageSize);
        List<Department> items = Jdbc.query(
                "SELECT * FROM departments WHERE " + w.clause() + " ORDER BY name LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    /** Compteur d'équipements par service (pour la liste et le dashboard). */
    public List<Object[]> equipmentCountByDepartment() {
        return Jdbc.query("""
                SELECT d.id, d.name, COUNT(e.id)
                FROM departments d LEFT JOIN equipment e ON e.department_id = d.id AND e.archived = FALSE
                GROUP BY d.id, d.name ORDER BY d.name""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getLong(1), rs.getString(2), rs.getLong(3)});
    }

    public long insert(Department d) {
        return Jdbc.insert(
                "INSERT INTO departments(code, name, head_name, location, phone, email) VALUES (?,?,?,?,?,?)",
                d.getCode(), d.getName(), d.getHeadName(), d.getLocation(), d.getPhone(), d.getEmail());
    }

    public void update(Department d) {
        Jdbc.update("UPDATE departments SET code=?, name=?, head_name=?, location=?, phone=?, email=? WHERE id=?",
                d.getCode(), d.getName(), d.getHeadName(), d.getLocation(), d.getPhone(), d.getEmail(), d.getId());
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM departments WHERE id=?", id);
    }

    public boolean hasEquipment(long departmentId) {
        return Jdbc.count("SELECT COUNT(*) FROM equipment WHERE department_id=?", departmentId) > 0;
    }

    // ---------- Localisations ----------

    public List<Location> findLocations(Long departmentId) {
        if (departmentId == null) {
            return Jdbc.query("SELECT * FROM locations ORDER BY name", LOC_MAP);
        }
        return Jdbc.query("SELECT * FROM locations WHERE department_id=? ORDER BY name", LOC_MAP, departmentId);
    }

    public Optional<Location> findLocationById(long id) {
        return Jdbc.queryOne("SELECT * FROM locations WHERE id=?", LOC_MAP, id);
    }

    public long insertLocation(Location l) {
        return Jdbc.insert("INSERT INTO locations(department_id, code, name, floor) VALUES (?,?,?,?)",
                l.getDepartmentId(), l.getCode(), l.getName(), l.getFloor());
    }

    public void updateLocation(Location l) {
        Jdbc.update("UPDATE locations SET department_id=?, code=?, name=?, floor=? WHERE id=?",
                l.getDepartmentId(), l.getCode(), l.getName(), l.getFloor(), l.getId());
    }

    public void deleteLocation(long id) {
        Jdbc.update("DELETE FROM locations WHERE id=?", id);
    }

    public boolean locationInUse(long locationId) {
        return Jdbc.count("SELECT COUNT(*) FROM equipment WHERE location_id=?", locationId) > 0;
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
