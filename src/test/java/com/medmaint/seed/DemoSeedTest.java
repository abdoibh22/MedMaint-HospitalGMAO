package com.medmaint.seed;

import com.medmaint.config.AppConfig;
import com.medmaint.persistence.Db;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.SchemaMigrator;
import com.medmaint.security.Session;
import com.medmaint.security.UnauthorizedException;
import com.medmaint.service.AppContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jeu de démonstration complet sur H2 en mémoire :
 * volumes attendus, idempotence, intégrité référentielle,
 * authentification des comptes de démonstration, documents PDF.
 */
class DemoSeedTest {

    @BeforeAll
    static void seedDatabase() {
        Db.override("jdbc:h2:mem:seed-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        SchemaMigrator.migrate();
        assertTrue(DemoDataSeeder.runIfNeeded(), "premier appel : insertion effective");
        assertFalse(DemoDataSeeder.runIfNeeded(), "second appel : déjà peuplé, rien à faire");
    }

    @AfterAll
    static void clearSession() {
        Session.clear();
    }

    @Test
    void referenceDataVolumes() {
        assertEquals(12, Jdbc.count("SELECT COUNT(*) FROM departments"));
        assertEquals(12, Jdbc.count("SELECT COUNT(*) FROM equipment_categories"));
        assertEquals(8, Jdbc.count("SELECT COUNT(*) FROM manufacturers"));
        assertEquals(12, Jdbc.count("SELECT COUNT(*) FROM suppliers"));
        assertEquals(12, Jdbc.count("SELECT COUNT(*) FROM contracts"));
        assertEquals(24, Jdbc.count("SELECT COUNT(*) FROM locations"));
        assertEquals(5, Jdbc.count("SELECT COUNT(*) FROM users"));
        assertEquals(8, Jdbc.count("SELECT COUNT(*) FROM technicians"));
    }

    @Test
    void operationalDataVolumes() {
        assertEquals(40, Jdbc.count("SELECT COUNT(*) FROM equipment"));
        assertEquals(30, Jdbc.count("SELECT COUNT(*) FROM intervention_requests"));
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM work_orders") >= 40,
                "41 ordres de travail attendus au minimum");
        assertEquals(28, Jdbc.count("SELECT COUNT(*) FROM maintenance_plans"));
        assertEquals(35, Jdbc.count("SELECT COUNT(*) FROM spare_parts"));
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM maintenance_records") >= 60);
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM checklist_results") > 100);
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM notifications") >= 15);
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM audit_logs") >= 30);
        assertEquals(6, Jdbc.count("SELECT COUNT(*) FROM maintenance_checklists"));
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM checklist_items") >= 30);
    }

    @Test
    void referentialIntegrityHolds() {
        assertEquals(0, Jdbc.count(
                "SELECT COUNT(*) FROM equipment e WHERE NOT EXISTS (SELECT 1 FROM equipment_categories c WHERE c.id=e.category_id)"));
        assertEquals(0, Jdbc.count(
                "SELECT COUNT(*) FROM equipment e WHERE NOT EXISTS (SELECT 1 FROM departments d WHERE d.id=e.department_id)"));
        assertEquals(0, Jdbc.count(
                "SELECT COUNT(*) FROM work_orders w WHERE NOT EXISTS (SELECT 1 FROM equipment e WHERE e.id=w.equipment_id)"));
        assertEquals(0, Jdbc.count(
                "SELECT COUNT(*) FROM intervention_requests r WHERE NOT EXISTS (SELECT 1 FROM equipment e WHERE e.id=r.equipment_id)"));
        assertEquals(0, Jdbc.count(
                "SELECT COUNT(*) FROM maintenance_plans p WHERE NOT EXISTS (SELECT 1 FROM equipment e WHERE e.id=p.equipment_id)"));
        assertEquals(0, Jdbc.count(
                "SELECT COUNT(*) FROM stock_transactions t WHERE NOT EXISTS (SELECT 1 FROM spare_parts s WHERE s.id=t.part_id)"));
    }

    @Test
    void businessScenarioCoverage() {
        // couverture 12 mois d'historique
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM work_orders WHERE opened_at >= DATEADD('MONTH', -12, NOW())") >= 30);
        // statuts variés
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM intervention_requests WHERE status='NOUVELLE'") >= 3);
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM intervention_requests WHERE status='CLOTUREE'") >= 8);
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM intervention_requests WHERE status='REJETEE'") >= 2);
        // plans : retards + échéances à venir (alimente les vues PreventivePage)
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM maintenance_plans WHERE status='EN_RETARD'") >= 4);
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM maintenance_plans WHERE status IN ('CETTE_SEMAINE','AUJOURD_HUI')") >= 4);
        // stock bas pour le KPI
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM spare_parts WHERE quantity < min_quantity") >= 3);
        // contrats expirants (< 60 j)
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM contracts WHERE status='EXPIRANT'") >= 2);
        // notifications non lues pour le badge du topbar
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM notifications WHERE is_read=FALSE") >= 10);
        // audit récent
        assertTrue(Jdbc.count("SELECT COUNT(*) FROM audit_logs WHERE action='LOGIN'") >= 5);
    }

    @Test
    void demoLoginWorksWithDemoPassword() {
        Session s = AppContext.get().auth().login("admin", "demo2026");
        assertNotNull(s);
        assertEquals("ADMIN", s.role().name());
        assertEquals("Karim Benali", s.fullName());
        Session.clear();

        // connexion par e-mail, autre compte
        Session s2 = AppContext.get().auth().login("technician@medmaint.demo", "demo2026");
        assertEquals("TECHNICIAN", s2.role().name());
        Session.clear();

        assertThrows(UnauthorizedException.class,
                () -> AppContext.get().auth().login("admin", "wrong-password"));
        assertThrows(UnauthorizedException.class,
                () -> AppContext.get().auth().login("unknown", "demo2026"));
        Session.clear();
    }

    @Test
    void demoDocumentsWereWrittenToUploads() throws Exception {
        assertEquals(5, Jdbc.count("SELECT COUNT(*) FROM documents"));
        var docs = Jdbc.query("SELECT stored_name, size_bytes FROM documents", rs ->
                new Object[]{rs.getString(1), rs.getLong(2)});
        assertFalse(docs.isEmpty());
        for (Object[] d : docs) {
            var path = AppConfig.get().uploadDir().resolve((String) d[0]);
            assertTrue(Files.exists(path), "fichier manquant : " + path);
            assertTrue(Files.size(path) > 100, "PDF trop petit : " + path);
            assertTrue(((Long) d[1]) > 100, "taille en base incohérente");
        }
    }

    @Test
    void seededPlansHaveChecklistsAndResponsibles() {
        long withChecklist = Jdbc.count("SELECT COUNT(*) FROM maintenance_plans WHERE checklist_id IS NOT NULL");
        long withTech = Jdbc.count("SELECT COUNT(*) FROM maintenance_plans WHERE responsible_id IS NOT NULL");
        assertTrue(withChecklist >= 12, "plans rattachés à une checklist : " + withChecklist);
        assertTrue(withTech >= 25, "plans avec responsable : " + withTech);
    }
}
