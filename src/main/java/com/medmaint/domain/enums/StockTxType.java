package com.medmaint.domain.enums;

/** Types de transactions de stock de pièces de rechange. */
public enum StockTxType {
    ENTREE("Entrée de stock", 1),
    SORTIE("Sortie de stock", -1),
    TRANSFERT("Transfert", 0),
    AJUSTEMENT("Ajustement d'inventaire", 0),
    USAGE("Consommé sur intervention", -1);

    private final String label;
    private final int sign;

    StockTxType(String label, int sign) {
        this.label = label;
        this.sign = sign;
    }

    public String label() {
        return label;
    }

    public int sign() {
        return sign;
    }

    public static StockTxType fromCode(String code) {
        if (code == null) {
            return ENTREE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ENTREE;
        }
    }
}
