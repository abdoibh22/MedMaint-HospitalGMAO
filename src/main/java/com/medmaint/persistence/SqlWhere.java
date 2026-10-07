package com.medmaint.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit dynamiquement une clause WHERE paramétrée (jamais de concaténation
 * de valeurs) pour les listes filtrées / paginées.
 *
 * <pre>
 * SqlWhere w = new SqlWhere("1=1");
 * w.add("e.status = ?", status);
 * w.addLike("e.name", search);
 * sql = "SELECT * FROM equipment e WHERE " + w.clause() + " ORDER BY ...";
 * </pre>
 */
public final class SqlWhere {

    private final StringBuilder clause;
    private final List<Object> params = new ArrayList<>();

    public SqlWhere(String base) {
        this.clause = new StringBuilder(base == null || base.isBlank() ? "1=1" : base);
    }

    /**
     * Ajoute une condition si la valeur est non nulle / non vide.
     * Seules les conditions contenant un {@code ?} attendent un paramètre :
     * les conditions littérales ({@code CURRENT_DATE}, {@code IS NULL}…) sont
     * ajoutées sans lier de valeur, sinon le nombre de paramètres ne correspond
     * plus aux marqueurs de la requête (erreur H2 90008).
     */
    public SqlWhere add(String condition, Object value) {
        if (value == null) {
            return this;
        }
        if (value instanceof String s && s.isBlank()) {
            return this;
        }
        clause.append(" AND ").append(condition);
        if (condition.indexOf('?') >= 0) {
            params.add(value);
        }
        return this;
    }

    /** Condition LIKE « %value% » sur une colonne (échappement des caractères spéciaux). */
    public SqlWhere addLike(String column, String value) {
        return addLikes(java.util.List.of(column), value);
    }

    /**
     * Recherche textuelle sur plusieurs colonnes avec sémantique OU :
     * {@code AND (LOWER(c1) LIKE ? OR LOWER(c2) LIKE ? …)}. Une même valeur
     * recherchée doit matcher n'importe quelle colonne (jamais toutes à la fois).
     */
    public SqlWhere addLikes(java.util.List<String> columns, String value) {
        if (value == null || value.isBlank() || columns == null || columns.isEmpty()) {
            return this;
        }
        String pattern = "%" + value.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        clause.append(" AND (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                clause.append(" OR ");
            }
            clause.append("LOWER(").append(columns.get(i)).append(") LIKE ? ESCAPE '\\'");
            params.add(pattern);
        }
        clause.append(')');
        return this;
    }

    /** Condition IN sur une liste potentiellement vide (aucune restriction si vide). */
    public SqlWhere addIn(String column, List<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) placeholders.append(',');
            placeholders.append('?');
        }
        clause.append(" AND ").append(column).append(" IN (").append(placeholders).append(')');
        params.addAll(values);
        return this;
    }

    public String clause() {
        return clause.toString();
    }

    public Object[] params() {
        return params.toArray();
    }

    public int count() {
        return params.size();
    }
}
