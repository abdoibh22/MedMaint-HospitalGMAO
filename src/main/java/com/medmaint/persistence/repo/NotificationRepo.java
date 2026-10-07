package com.medmaint.persistence.repo;

import com.medmaint.domain.model.Notification;
import com.medmaint.persistence.Jdbc;

import java.util.List;
import java.util.Optional;

/** Notifications en-app. */
public final class NotificationRepo {

    private static final Jdbc.RowMapper<Notification> MAP = rs -> {
        Notification n = new Notification();
        n.setId(rs.getLong("id"));
        n.setUserId(Jdbc.getLongOrNull(rs, "user_id"));
        n.setRoleCode(rs.getString("role_code"));
        n.setType(rs.getString("type"));
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setSeverity(rs.getString("severity"));
        n.setEntityType(rs.getString("entity_type"));
        n.setEntityId(Jdbc.getLongOrNull(rs, "entity_id"));
        n.setRead(Jdbc.getBool(rs, "is_read"));
        n.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        return n;
    };

    public long insert(Notification n) {
        return Jdbc.insert("""
                INSERT INTO notifications(user_id, role_code, type, title, message, severity,
                    entity_type, entity_id, is_read, created_at)
                VALUES (?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP)""",
                n.getUserId(), n.getRoleCode(), n.getType(), n.getTitle(), n.getMessage(),
                n.getSeverity(), n.getEntityType(), n.getEntityId());
    }

    /** Notifications visibles par un utilisateur (personnelles ou diffusées à son rôle). */
    public List<Notification> findForUser(long userId, String roleCode, int limit) {
        return Jdbc.query("""
                SELECT * FROM notifications
                WHERE user_id = ? OR (user_id IS NULL AND role_code = ?)
                ORDER BY created_at DESC LIMIT ?""", MAP, userId, roleCode, limit);
    }

    public long countUnread(long userId, String roleCode) {
        return Jdbc.count("""
                SELECT COUNT(*) FROM notifications
                WHERE is_read = FALSE AND (user_id = ? OR (user_id IS NULL AND role_code = ?))""",
                userId, roleCode);
    }

    public void markRead(long id) {
        Jdbc.update("UPDATE notifications SET is_read=TRUE WHERE id=?", id);
    }

    public void markAllRead(long userId, String roleCode) {
        Jdbc.update("""
                UPDATE notifications SET is_read = TRUE
                WHERE (user_id = ? OR (user_id IS NULL AND role_code = ?)) AND is_read = FALSE""",
                userId, roleCode);
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM notifications WHERE id=?", id);
    }

    public Optional<Notification> findById(long id) {
        return Jdbc.queryOne("SELECT * FROM notifications WHERE id=?", MAP, id);
    }

    /** Supprime les notifications de plus de 90 jours (maintenance). */
    public void purgeOlderThan(int days) {
        Jdbc.update("DELETE FROM notifications WHERE created_at < DATEADD('DAY', ?, CURRENT_TIMESTAMP)",
                -days);
    }
}
