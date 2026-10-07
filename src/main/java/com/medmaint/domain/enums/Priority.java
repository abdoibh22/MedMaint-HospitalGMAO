package com.medmaint.domain.enums;

/** Priorités des demandes et ordres de travail. */
public enum Priority {
    FAIBLE("Faible", "low", 1),
    NORMALE("Normale", "normal", 2),
    HAUTE("Haute", "high", 3),
    URGENTE("Urgente", "urgent", 4),
    CRITIQUE("Critique", "critical", 5);

    private final String label;
    private final String tone;
    private final int weight;

    Priority(String label, String tone, int weight) {
        this.label = label;
        this.tone = tone;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    /** Poids de tri (1 = plus faible). */
    public int weight() {
        return weight;
    }

    public static Priority fromCode(String code) {
        if (code == null) {
            return NORMALE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMALE;
        }
    }
}
