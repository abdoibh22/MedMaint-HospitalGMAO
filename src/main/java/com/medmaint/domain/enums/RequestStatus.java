package com.medmaint.domain.enums;

/** Statuts du cycle de vie d'une demande d'intervention. */
public enum RequestStatus {
    NOUVELLE("Nouvelle", "info"),
    EN_ATTENTE("En attente", "warn"),
    ACCEPTEE("Acceptée", "info"),
    ASSIGNEE("Assignée", "info"),
    EN_COURS("En cours", "active"),
    EN_ATTENTE_PIECE("En attente de pièce", "warn"),
    RESOLUE("Résolue", "ok"),
    CLOTUREE("Clôturée", "muted"),
    REJETEE("Rejetée", "danger");

    private final String label;
    private final String tone;

    RequestStatus(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    /** Statuts considérés comme « ouverts ». */
    public boolean isOpen() {
        return this != RESOLUE && this != CLOTUREE && this != REJETEE;
    }

    public static RequestStatus fromCode(String code) {
        if (code == null) {
            return NOUVELLE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NOUVELLE;
        }
    }
}
