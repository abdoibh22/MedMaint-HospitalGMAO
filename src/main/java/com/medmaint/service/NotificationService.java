package com.medmaint.service;

import com.medmaint.domain.enums.NotificationType;
import com.medmaint.domain.model.Notification;
import com.medmaint.persistence.repo.NotificationRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Centre de notifications en-app.
 * Les notifications sont générées par les événements métier réels
 * (nouvelle demande, affectation, maintenance due, stock faible…).
 * Aucune prétention de « temps réel » côté réseau : l'UI rafraîchit le compteur
 * au fil des actions et au démarrage.
 */
public final class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepo repo = new NotificationRepo();

    public void notifyUser(long userId, NotificationType type, String title, String message,
                           String severity, String entityType, Long entityId) {
        Notification n = base(type, title, message, severity, entityType, entityId);
        n.setUserId(userId);
        repo.insert(n);
        log.debug("Notification utilisateur {} : {}", userId, title);
    }

    public void notifyRole(String roleCode, NotificationType type, String title, String message,
                           String severity, String entityType, Long entityId) {
        Notification n = base(type, title, message, severity, entityType, entityId);
        n.setRoleCode(roleCode);
        repo.insert(n);
        log.debug("Notification rôle {} : {}", roleCode, title);
    }

    /** Diffusion à plusieurs rôles (ex. panne critique → responsable + ingénieur). */
    public void notifyRoles(List<String> roleCodes, NotificationType type, String title,
                            String message, String severity, String entityType, Long entityId) {
        for (String role : roleCodes) {
            notifyRole(role, type, title, message, severity, entityType, entityId);
        }
    }

    private Notification base(NotificationType type, String title, String message,
                              String severity, String entityType, Long entityId) {
        Notification n = new Notification();
        n.setType(type.name());
        n.setTitle(title);
        n.setMessage(message);
        n.setSeverity(severity != null ? severity : "INFO");
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        return n;
    }

    public List<Notification> forUser(long userId, String roleCode, int limit) {
        return repo.findForUser(userId, roleCode, limit);
    }

    public long unreadCount(long userId, String roleCode) {
        return repo.countUnread(userId, roleCode);
    }

    public void markRead(long id) {
        repo.markRead(id);
    }

    public void markAllRead(long userId, String roleCode) {
        repo.markAllRead(userId, roleCode);
    }

    public void delete(long id) {
        repo.delete(id);
    }
}
