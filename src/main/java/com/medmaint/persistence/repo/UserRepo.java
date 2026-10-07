package com.medmaint.persistence.repo;

import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;
import com.medmaint.domain.model.User;

import java.util.List;
import java.util.Optional;

/** Accès aux comptes utilisateurs. */
public final class UserRepo {

    private static final String BASE = """
            SELECT u.id, u.username, u.email, u.password_hash, u.full_name, u.role,
                   u.department_id, d.name AS department_name, u.phone, u.status, u.is_demo,
                   u.notification_prefs, u.last_login, u.created_at
            FROM users u LEFT JOIN departments d ON d.id = u.department_id
            """;

    private static final Jdbc.RowMapper<User> MAP = rs -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(rs.getString("role"));
        u.setDepartmentId(Jdbc.getLongOrNull(rs, "department_id"));
        u.setDepartmentName(rs.getString("department_name"));
        u.setPhone(rs.getString("phone"));
        u.setStatus(rs.getString("status"));
        u.setDemo(Jdbc.getBool(rs, "is_demo"));
        u.setNotificationPrefs(rs.getString("notification_prefs"));
        u.setLastLogin(Jdbc.getDateTime(rs, "last_login"));
        u.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        return u;
    };

    public Optional<User> findById(long id) {
        return Jdbc.queryOne(BASE + " WHERE u.id = ?", MAP, id);
    }

    public Optional<User> findByUsername(String username) {
        return Jdbc.queryOne(BASE + " WHERE LOWER(u.username) = LOWER(?)", MAP, username);
    }

    public Optional<User> findByEmail(String email) {
        return Jdbc.queryOne(BASE + " WHERE LOWER(u.email) = LOWER(?)", MAP, email);
    }

    /** Recherche paginée avec filtres optionnels (rôle, service, statut, recherche texte). */
    public Page<User> search(String text, String role, String status, Long departmentId,
                             int page, int pageSize, String sortCol, String sortDir) {
        SqlWhere w = new SqlWhere("1=1");
        w.addLikes(List.of("u.full_name", "u.username", "u.email"), text)
                .add("u.role = ?", role)
                .add("u.status = ?", status)
                .add("u.department_id = ?", departmentId);
        return page(w, page, pageSize, sortCol, sortDir);
    }

    private Page<User> page(SqlWhere w, int page, int pageSize, String sortCol, String sortDir) {
        long total = Jdbc.count("SELECT COUNT(*) FROM users u WHERE " + w.clause(), w.params());
        String allowed = switch (sortCol == null ? "" : sortCol) {
            case "username" -> "u.username";
            case "email" -> "u.email";
            case "role" -> "u.role";
            case "status" -> "u.status";
            case "lastLogin" -> "u.last_login";
            default -> "u.full_name";
        };
        String dir = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        int offset = Math.max(0, (page - 1) * pageSize);
        List<User> items = Jdbc.query(
                BASE + " WHERE " + w.clause() + " ORDER BY " + allowed + " " + dir + " LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public long insert(User u) {
        return Jdbc.insert("""
                INSERT INTO users(username, email, password_hash, full_name, role,
                                  department_id, phone, status, is_demo, notification_prefs, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?, CURRENT_TIMESTAMP)""",
                u.getUsername(), u.getEmail(), u.getPasswordHash(), u.getFullName(), u.getRole(),
                u.getDepartmentId(), u.getPhone(), u.getStatus(), u.isDemo(), u.getNotificationPrefs());
    }

    public void update(User u) {
        Jdbc.update("""
                UPDATE users SET email=?, full_name=?, role=?, department_id=?, phone=?,
                                 status=?, notification_prefs=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?""",
                u.getEmail(), u.getFullName(), u.getRole(), u.getDepartmentId(), u.getPhone(),
                u.getStatus(), u.getNotificationPrefs(), u.getId());
    }

    public void updatePassword(long id, String hash) {
        Jdbc.update("UPDATE users SET password_hash=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", hash, id);
    }

    public void updateLastLogin(long id) {
        Jdbc.update("UPDATE users SET last_login=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM users WHERE id=? AND is_demo = FALSE", id);
    }

    public long countByRole(String role) {
        return Jdbc.count("SELECT COUNT(*) FROM users WHERE role=?", role);
    }

    public List<User> findByRole(String role) {
        return Jdbc.query(BASE + " WHERE u.role=? AND u.status='ACTIF' ORDER BY u.full_name", MAP, role);
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
