package com.medmaint.persistence;

import com.medmaint.security.Permission;
import com.medmaint.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.EnumSet;

/**
 * Applique le schéma (db/schema.sql) de façon idempotente, puis seed les
 * tables de référence rôles / permissions à partir de la matrice du code.
 */
public final class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    private SchemaMigrator() {
    }

    public static void migrate() {
        String script = readSchema();
        try (Statement st = Db.get().createStatement()) {
            for (String raw : script.split(";")) {
                String sql = stripComments(raw).trim();
                if (!sql.isEmpty()) {
                    st.execute(sql);
                }
            }
        } catch (Exception e) {
            throw new DbException("Échec de la migration du schéma.", e);
        }
        seedRolesAndPermissions();
        log.info("Schéma de base appliqué.");
    }

    /** Persiste rôles / permissions / role_permissions depuis la matrice Role/Permission. */
    private static void seedRolesAndPermissions() {
        try {
            if (Jdbc.count("SELECT COUNT(*) FROM roles") == 0) {
                for (Role r : Role.values()) {
                    Jdbc.update("INSERT INTO roles(code, name, description) VALUES (?,?,?)",
                            r.name(), r.label(), r.description());
                }
            }
            if (Jdbc.count("SELECT COUNT(*) FROM permissions") == 0) {
                for (Permission p : Permission.values()) {
                    Jdbc.update("INSERT INTO permissions(code, description) VALUES (?,?)",
                            p.name(), p.label());
                }
            }
            if (Jdbc.count("SELECT COUNT(*) FROM role_permissions") == 0) {
                for (Role r : Role.values()) {
                    long roleId = Jdbc.queryOne("SELECT id FROM roles WHERE code = ?",
                            rs -> rs.getLong(1), r.name()).orElse(-1L);
                    EnumSet<Permission> perms = EnumSet.copyOf(r.permissions());
                    for (Permission p : perms) {
                        Long permId = Jdbc.queryOne("SELECT id FROM permissions WHERE code = ?",
                                rs -> rs.getLong(1), p.name()).orElse(null);
                        if (permId != null && roleId > 0) {
                            Jdbc.update("INSERT INTO role_permissions(role_id, permission_id) VALUES (?,?)",
                                    roleId, permId);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Seed rôles/permissions : {}", e.getMessage());
        }
    }

    private static String readSchema() {
        try (InputStream in = SchemaMigrator.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                throw new DbException("Ressource /db/schema.sql introuvable.", null);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (DbException e) {
            throw e;
        } catch (Exception e) {
            throw new DbException("Lecture du schéma impossible.", e);
        }
    }

    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            String t = line.trim();
            if (t.startsWith("--")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
