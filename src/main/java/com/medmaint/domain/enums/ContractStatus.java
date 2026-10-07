package com.medmaint.domain.enums;

import java.time.LocalDate;

/** Statut d'un contrat de maintenance (dérivé des dates + statut stocké). */
public enum ContractStatus {
    ACTIF("Actif", "ok"),
    EXPIRANT("Expirant bientôt", "warn"),
    EXPIRE("Expiré", "danger"),
    SUSPENDU("Suspendu", "muted");

    private final String label;
    private final String tone;

    ContractStatus(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    /** Statut effectif : tient compte des dates de validité. */
    public static ContractStatus effective(ContractStatus stored, LocalDate start, LocalDate end, LocalDate today) {
        if (stored == SUSPENDU) {
            return SUSPENDU;
        }
        if (start != null && today.isBefore(start)) {
            return ACTIF; // en attente de démarrage : affiché actif
        }
        if (end == null) {
            return ACTIF;
        }
        if (today.isAfter(end)) {
            return EXPIRE;
        }
        if (!today.plusDays(60).isBefore(end)) {
            return EXPIRANT;
        }
        return ACTIF;
    }

    public static ContractStatus fromCode(String code) {
        if (code == null) {
            return ACTIF;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ACTIF;
        }
    }
}
