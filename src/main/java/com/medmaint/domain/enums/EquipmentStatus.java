package com.medmaint.domain.enums;

/** Statuts de cycle de vie d'un équipement. */
public enum EquipmentStatus {
    EN_SERVICE("En service", "ok"),
    EN_MAINTENANCE("En maintenance", "warn"),
    HORS_SERVICE("Hors service", "danger"),
    EN_ATTENTE_REPARATION("En attente de réparation", "warn"),
    EN_QUARANTAINE("En quarantaine", "danger"),
    REFORME("Réformé", "muted"),
    STOCK("Stock", "info"),
    PERDU("Perdu", "danger");

    private final String label;
    private final String tone;

    EquipmentStatus(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    /** Ton visuel (ok/warn/danger/info/muted) — n'est jamais le seul indicateur : le texte est affiché. */
    public String tone() {
        return tone;
    }

    public static EquipmentStatus fromCode(String code) {
        if (code == null) {
            return EN_SERVICE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EN_SERVICE;
        }
    }
}
