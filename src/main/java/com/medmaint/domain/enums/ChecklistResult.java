package com.medmaint.domain.enums;

/** Résultat d'un item de checklist de maintenance. */
public enum ChecklistResult {
    CONFORME("Conforme", "ok"),
    NON_CONFORME("Non conforme", "danger"),
    NON_APPLICABLE("Non applicable", "muted");

    private final String label;
    private final String tone;

    ChecklistResult(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    public static ChecklistResult fromCode(String code) {
        if (code == null) {
            return CONFORME;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CONFORME;
        }
    }
}
