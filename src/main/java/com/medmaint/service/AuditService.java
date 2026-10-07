package com.medmaint.service;

import com.medmaint.domain.model.AuditLog;
import com.medmaint.persistence.repo.AuditRepo;
import com.medmaint.security.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Journal d'audit : trace toute mutation importante
 * (utilisateur, action, entité, identifiant, valeurs avant/après).
 * L'accès à la consultation est réservé aux administrateurs (contrôlé dans l'UI
 * et par la permission {@code AUDIT_VIEW}).
 */
public final class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepo repo = new AuditRepo();

    public void record(String action, String entityType, String entityId, String entityLabel,
                       String oldValue, String newValue) {
        try {
            AuditLog a = new AuditLog();
            Session s = Session.get();
            if (s != null) {
                a.setUserId(s.userId());
                a.setUserName(s.fullName());
            } else {
                a.setUserName("système");
            }
            a.setAction(action);
            a.setEntityType(entityType);
            a.setEntityId(entityId);
            a.setEntityLabel(entityLabel);
            a.setOldValue(truncate(oldValue, 2000));
            a.setNewValue(truncate(newValue, 2000));
            repo.insert(a);
        } catch (RuntimeException e) {
            // L'audit ne doit jamais faire échouer l'opération métier.
            log.warn("Écriture audit impossible : {}", e.getMessage());
        }
    }

    public void created(String entityType, String entityId, String label) {
        record("CREATE", entityType, entityId, label, null, label);
    }

    public void updated(String entityType, String entityId, String label, String oldV, String newV) {
        record("UPDATE", entityType, entityId, label, oldV, newV);
    }

    public void deleted(String entityType, String entityId, String label, String oldV) {
        record("DELETE", entityType, entityId, label, oldV, null);
    }

    public void action(String action, String entityType, String entityId, String label) {
        record(action, entityType, entityId, label, null, null);
    }

    public void login() {
        record("LOGIN", "Session", null, null, null, null);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public List<AuditLog> forEntity(String entityType, String entityId) {
        return repo.findForEntity(entityType, entityId, 50);
    }

    public AuditRepo repo() {
        return repo;
    }
}
