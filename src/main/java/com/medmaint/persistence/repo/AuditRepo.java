package com.medmaint.persistence.repo;

import com.medmaint.domain.model.AuditLog;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.util.List;

/** Journal d'audit (traçabilité). */
public final class AuditRepo {

    private static final Jdbc.RowMapper<AuditLog> MAP = rs -> {
        AuditLog a = new AuditLog();
        a.setId(rs.getLong("id"));
        a.setUserId(Jdbc.getLongOrNull(rs, "user_id"));
        a.setUserName(rs.getString("user_name"));
        a.setAction(rs.getString("action"));
        a.setEntityType(rs.getString("entity_type"));
        a.setEntityId(rs.getString("entity_id"));
        a.setEntityLabel(rs.getString("entity_label"));
        a.setOldValue(rs.getString("old_value"));
        a.setNewValue(rs.getString("new_value"));
        a.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        return a;
    };

    public void insert(AuditLog a) {
        Jdbc.insert("""
                INSERT INTO audit_logs(user_id, user_name, action, entity_type, entity_id, entity_label,
                    old_value, new_value, created_at)
                VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)""",
                a.getUserId(), a.getUserName(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getEntityLabel(), a.getOldValue(), a.getNewValue());
    }

    public Page<AuditLog> search(String text, String action, String entityType, Long userId,
                                 java.time.LocalDate from, java.time.LocalDate to,
                                 int page, int pageSize) {
        SqlWhere w = new SqlWhere("1=1");
        w.addLikes(List.of("a.user_name", "a.entity_label", "a.entity_id"), text)
                .add("a.action = ?", action)
                .add("a.entity_type = ?", entityType)
                .add("a.user_id = ?", userId);
        if (from != null) w.add("a.created_at >= ?", from.atStartOfDay());
        if (to != null) w.add("a.created_at < ?", to.plusDays(1).atStartOfDay());

        long total = Jdbc.count("SELECT COUNT(*) FROM audit_logs a WHERE " + w.clause(), w.params());
        int offset = Math.max(0, (page - 1) * pageSize);
        List<AuditLog> items = Jdbc.query(
                "SELECT a.*, u.full_name AS user_name FROM audit_logs a LEFT JOIN users u ON u.id=a.user_id WHERE "
                        + w.clause() + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?",
                rs -> {
                    AuditLog x = MAP.map(rs);
                    if (x.getUserName() == null) {
                        x.setUserName(rs.getString("user_name"));
                    }
                    return x;
                },
                concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public List<AuditLog> findForEntity(String entityType, String entityId, int limit) {
        return Jdbc.query("""
                SELECT a.*, u.full_name AS user_name FROM audit_logs a
                LEFT JOIN users u ON u.id = a.user_id
                WHERE a.entity_type=? AND a.entity_id=?
                ORDER BY a.created_at DESC LIMIT ?""",
                rs -> {
                    AuditLog x = MAP.map(rs);
                    if (x.getUserName() == null) {
                        x.setUserName(rs.getString("user_name"));
                    }
                    return x;
                }, entityType, entityId, limit);
    }

    public List<String> distinctActions() {
        return Jdbc.query("SELECT DISTINCT action FROM audit_logs ORDER BY action", rs -> rs.getString(1));
    }

    public List<String> distinctEntityTypes() {
        return Jdbc.query("SELECT DISTINCT entity_type FROM audit_logs ORDER BY entity_type",
                rs -> rs.getString(1));
    }

    public long count() {
        return Jdbc.count("SELECT COUNT(*) FROM audit_logs");
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
