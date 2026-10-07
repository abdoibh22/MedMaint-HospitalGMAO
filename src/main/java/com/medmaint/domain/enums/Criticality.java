package com.medmaint.domain.enums;

/** Criticité d'un équipement (impact clinique en cas de panne). */
public enum Criticality {
    FAIBLE("Faible", "low"),
    MOYENNE("Moyenne", "normal"),
    HAUTE("Haute", "high"),
    VITALE("Vitale", "critical");

    private final String label;
    private final String tone;

    Criticality(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    public static Criticality fromCode(String code) {
        if (code == null) {
            return MOYENNE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MOYENNE;
        }
    }
}
