package com.medmaint.domain.enums;

import java.time.LocalDate;

/** Fréquence des plans de maintenance préventive. */
public enum MaintenanceFrequency {
    JOURNALIERE("Quotidienne", 1),
    HEBDOMADAIRE("Hebdomadaire", 7),
    MENSUELLE("Mensuelle", 30),
    TRIMESTRIELLE("Trimestrielle", 91),
    SEMESTRIELLE("Semestrielle", 182),
    ANNUELLE("Annuelle", 365),
    PERSONNALISEE("Personnalisée", -1);

    private final String label;
    private final int days;

    MaintenanceFrequency(String label, int days) {
        this.label = label;
        this.days = days;
    }

    public String label() {
        return label;
    }

    public int days() {
        return days;
    }

    /** Calcule la prochaine échéance à partir d'une date de référence. */
    public LocalDate nextDate(LocalDate from, Integer customDays) {
        int d = (this == PERSONNALISEE)
                ? (customDays == null || customDays <= 0 ? 365 : customDays)
                : days;
        return from.plusDays(d);
    }

    public static MaintenanceFrequency fromCode(String code) {
        if (code == null) {
            return MENSUELLE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MENSUELLE;
        }
    }
}
