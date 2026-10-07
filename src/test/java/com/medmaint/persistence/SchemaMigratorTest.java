package com.medmaint.persistence;

import com.medmaint.security.Permission;
import com.medmaint.security.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration du schéma sur base H2 en mémoire dédiée :
 * exécution idempotente + seed de la matrice rôles/permissions.
 */
class SchemaMigratorTest {

    @BeforeAll
    static void initDb() {
        Db.override("jdbc:h2:mem:schema-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        SchemaMigrator.migrate();
    }

    @Test
    void migrateIsIdempotent() {
        SchemaMigrator.migrate(); // seconde exécution sans erreur
        long tables = Jdbc.count(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='PUBLIC'");
        assertTrue(tables >= 25, "au moins 25 tables attendues, obtenu " + tables);
    }

    @Test
    void rolesAndPermissionsAreSeeded() {
        assertEquals(Role.values().length, Jdbc.count("SELECT COUNT(*) FROM roles"));
        assertEquals(Permission.values().length, Jdbc.count("SELECT COUNT(*) FROM permissions"));
        long links = Jdbc.count("SELECT COUNT(*) FROM role_permissions");
        long expected = 0;
        for (Role r : Role.values()) {
            expected += r.permissions().size();
        }
        assertEquals(expected, links, "chaque permission de chaque rôle est persistée");
    }

    @Test
    void adminHoldsEveryPermission() {
        long adminId = Jdbc.queryOne("SELECT id FROM roles WHERE code='ADMIN'", rs -> rs.getLong(1))
                .orElseThrow();
        long perms = Jdbc.count("SELECT COUNT(*) FROM role_permissions WHERE role_id=?", adminId);
        assertEquals(Permission.values().length, perms);
    }
}
