package com.medmaint.domain.enums;

/**
 * Workflow d'un ordre de travail :
 * Demande → Approbation → Affectation → Planification → En cours
 * → En attente de pièce → Résolue → Validation → Clôturée
 */
public enum WorkOrderStatus {
    DEMANDE("Demandée", "info"),
    APPROUVEE("Approuvée", "info"),
    ASSIGNEE("Assignée", "info"),
    PLANIFIEE("Planifiée", "info"),
    EN_COURS("En cours", "active"),
    EN_ATTENTE_PIECE("En attente de pièce", "warn"),
    RESOLUE("Résolue", "ok"),
    VALIDEE("Validée", "ok"),
    CLOTUREE("Clôturée", "muted");

    private final String label;
    private final String tone;

    WorkOrderStatus(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    public boolean isOpen() {
        return this != RESOLUE && this != VALIDEE && this != CLOTUREE;
    }

    public static WorkOrderStatus fromCode(String code) {
        if (code == null) {
            return DEMANDE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEMANDE;
        }
    }
}
