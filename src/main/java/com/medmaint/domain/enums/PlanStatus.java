package com.medmaint.domain.enums;

import java.time.LocalDate;

/** État d'avancement d'un plan de maintenance préventive par rapport à sa date d'échéance. */
public enum PlanStatus {
    A_VENIR("À venir", "info"),
    AUJOURD_HUI("À faire aujourd'hui", "warn"),
    CETTE_SEMAINE("Cette semaine", "warn"),
    EN_RETARD("En retard", "danger"),
    EN_COURS("En cours", "active"),
    TERMINEE("Terminée", "ok"),
    ANNULEE("Annulée", "muted");

    private final String label;
    private final String tone;

    PlanStatus(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    /**
     * Calcule l'état d'un plan actif à partir de sa prochaine échéance.
     * @param inProgress true si une maintenance est en cours d'exécution
     */
    public static PlanStatus forDue(LocalDate nextDue, boolean inProgress, boolean active, boolean done) {
        if (!active) {
            return ANNULEE;
        }
        if (inProgress) {
            return EN_COURS;
        }
        if (done && nextDue == null) {
            return TERMINEE;
        }
        if (nextDue == null) {
            return A_VENIR;
        }
        LocalDate today = LocalDate.now();
        if (nextDue.isBefore(today)) {
            return EN_RETARD;
        }
        if (nextDue.equals(today)) {
            return AUJOURD_HUI;
        }
        if (!nextDue.isAfter(today.plusDays(7))) {
            return CETTE_SEMAINE;
        }
        return A_VENIR;
    }

    public static PlanStatus fromCode(String code) {
        if (code == null) {
            return A_VENIR;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return A_VENIR;
        }
    }
}
