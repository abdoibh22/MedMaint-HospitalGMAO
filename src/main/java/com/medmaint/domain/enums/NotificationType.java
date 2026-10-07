package com.medmaint.domain.enums;

/** Types de notifications du centre de notifications. */
public enum NotificationType {
    NOUVELLE_DEMANDE("Nouvelle demande d'intervention", "info"),
    PANNE_CRITIQUE("Panne critique", "critical"),
    INTERVENTION_ASSIGNEE("Intervention assignée", "info"),
    PM_A_VENIR("Maintenance préventive à venir", "info"),
    PM_EN_RETARD("Maintenance préventive en retard", "warn"),
    STOCK_FAIBLE("Stock de pièces faible", "warn"),
    CONTRAT_EXPIRATION("Contrat bientôt expiré", "warn"),
    GARANTIE_EXPIRATION("Garantie bientôt expirée", "info"),
    INTERVENTION_TERMINEE("Intervention terminée", "ok"),
    VALIDATION_REQUISE("Validation nécessaire", "warn"),
    DEMANDE_MAJ("Statut de demande mis à jour", "info"),
    EQUIPEMENT_AJOUTE("Équipement ajouté", "info");

    private final String label;
    private final String tone;

    NotificationType(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    public static NotificationType fromCode(String code) {
        if (code == null) {
            return NOUVELLE_DEMANDE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NOUVELLE_DEMANDE;
        }
    }
}
