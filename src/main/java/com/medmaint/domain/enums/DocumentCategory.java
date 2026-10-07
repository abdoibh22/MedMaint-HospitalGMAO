package com.medmaint.domain.enums;

/** Catégories de documents gérées par le module Document. */
public enum DocumentCategory {
    MANUEL_UTILISATEUR("Manuel utilisateur"),
    MANUEL_TECHNIQUE("Manuel technique"),
    MANUEL_MAINTENANCE("Manuel de maintenance"),
    GARANTIE("Attestation de garantie"),
    FACTURE("Facture d'achat"),
    CERTIFICAT("Certificat / conformité"),
    RAPPORT_INSPECTION("Rapport d'inspection"),
    RAPPORT_INTERVENTION("Rapport d'intervention"),
    PHOTO("Photo"),
    CONTRAT("Contrat"),
    AUTRE("Autre");

    private final String label;

    DocumentCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static DocumentCategory fromCode(String code) {
        if (code == null) {
            return AUTRE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTRE;
        }
    }
}
