package com.medmaint.domain.model;

import java.time.LocalDateTime;

/** Entrée du journal d'audit (traçabilité des mutations). */
public class AuditLog {

    private long id;
    private Long userId;
    private String userName;
    private String action;       // CREATE | UPDATE | DELETE | LOGIN | ASSIGN | CONSUME…
    private String entityType;
    private String entityId;
    private String entityLabel;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getEntityLabel() { return entityLabel; }
    public void setEntityLabel(String entityLabel) { this.entityLabel = entityLabel; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Résumé d'audit : « Équipement mis à jour — EQ-001 ». */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (action != null) sb.append(action);
        if (entityType != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(entityType);
        }
        if (entityLabel != null) {
            sb.append(" — ").append(entityLabel);
        }
        return sb.toString();
    }
}
