package com.medmaint.domain.enums;

/** Niveau de risque lié à l'utilisation de l'équipement. */
public enum RiskLevel {
    BAS("Risque bas — dispositif de classe I"),
    MOYEN("Risque moyen — dispositif de classe IIa"),
    HAUT("Risque élevé — dispositif de classe IIb / III");

    private final String label;

    RiskLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static RiskLevel fromCode(String code) {
        if (code == null) {
            return MOYEN;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MOYEN;
        }
    }
}
