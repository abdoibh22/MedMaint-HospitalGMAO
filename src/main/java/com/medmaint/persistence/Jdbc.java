package com.medmaint.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mini-couche JDBC : requêtes paramétrées, mapping vers objets, requêtes préparées
 * avec retours de clés générées. Toutes les requêtes passent par des paramètres
 * (aucune concaténation de valeurs = pas d'injection SQL).
 */
public final class Jdbc {

    /** Mapper ligne → objet. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** Callback avec résultat (requêtes de mise à jour). */
    @FunctionalInterface
    public interface SqlConsumer<T> {
        void accept(PreparedStatement ps) throws SQLException;
    }

    private Jdbc() {
    }

    public static <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = Db.get().prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new DbException("Échec de lecture : " + abbreviate(sql), e);
        }
    }

    public static <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> list = query(sql, mapper, params);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public static long count(String sql, Object... params) {
        return queryOne(sql, rs -> rs.getLong(1), params).orElse(0L);
    }

    /** Exécute une mise à jour / insertion / suppression, retourne le nombre de lignes affectées. */
    public static int update(String sql, Object... params) {
        try (PreparedStatement ps = Db.get().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Échec d'écriture : " + abbreviate(sql), e);
        }
    }

    /** Insert avec retour de la clé générée. */
    public static long insert(String sql, Object... params) {
        try (PreparedStatement ps = Db.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new DbException("Aucune clé générée pour : " + abbreviate(sql), null);
            }
        } catch (SQLException e) {
            throw new DbException("Échec d'insertion : " + abbreviate(sql), e);
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            int idx = i + 1;
            if (v == null) {
                ps.setNull(idx, Types.NULL);
            } else if (v instanceof LocalDate d) {
                ps.setObject(idx, d);
            } else if (v instanceof LocalDateTime dt) {
                ps.setObject(idx, dt);
            } else if (v instanceof Enum<?> e) {
                ps.setString(idx, e.name());
            } else if (v instanceof Boolean b) {
                ps.setBoolean(idx, b);
            } else {
                ps.setObject(idx, v);
            }
        }
    }

    /** Raccourcit une requête pour les messages d'erreur / logs. */
    private static String abbreviate(String sql) {
        String flat = sql.replaceAll("\\s+", " ").trim();
        return flat.length() > 140 ? flat.substring(0, 140) + "…" : flat;
    }

    // ---- Helpers de lecture sûrs ----

    public static String getString(ResultSet rs, String col) throws SQLException {
        return rs.getString(col);
    }

    public static Long getLongOrNull(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    public static LocalDate getDate(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDate.class);
    }

    public static LocalDateTime getDateTime(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDateTime.class);
    }

    public static boolean getBool(ResultSet rs, String col) throws SQLException {
        boolean v = rs.getBoolean(col);
        return !rs.wasNull() && v;
    }
}
