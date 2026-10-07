package com.medmaint.persistence.repo;

import com.medmaint.persistence.Db;
import com.medmaint.persistence.SchemaMigrator;
import com.medmaint.seed.DemoDataSeeder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Régression : chaque recherche paginée doit s'exécuter sans erreur de binding
 * (bug 90008 H2 « invalid value for parameterIndex » quand une condition
 * littérale liait un paramètre sans marqueur {@code ?}).
 * Toutes les facettes à conditions littérales sont couvertes.
 */
class RepoSearchTest {

    @BeforeAll
    static void seedDatabase() {
        Db.override("jdbc:h2:mem:repos-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        SchemaMigrator.migrate();
        assertTrue(DemoDataSeeder.runIfNeeded());
    }

    @Test
    void equipmentSearchDefaultAndFacets() {
        EquipmentRepo repo = new EquipmentRepo();
        var all = repo.search(eqFilter(null, null, null), 1, 20, "code", "ASC");
        assertEquals(40, all.total());
        assertEquals(20, all.items().size());

        var text = repo.search(eqFilter("resp", null, null), 1, 20, "name", "ASC");
        assertTrue(text.total() > 0, "recherche « resp » doit trouver des respirateurs");

        // facettes à conditions littérales (ancien bug 90008)
        var warranty = repo.search(eqFilter(null, "Sous garantie", null), 1, 20, "code", "ASC");
        assertTrue(warranty.total() > 0);
        var outWarranty = repo.search(eqFilter(null, "Hors garantie", null), 1, 20, "code", "ASC");
        assertTrue(outWarranty.total() > 0);
        var late = repo.search(eqFilter(null, null, "En retard"), 1, 20, "code", "ASC");
        assertTrue(late.total() >= 4, "plans en retard visibles depuis l'équipement");
        var upcoming = repo.search(eqFilter(null, null, "À venir"), 1, 20, "code", "ASC");
        assertTrue(upcoming.total() > 0);
        var noPlan = repo.search(eqFilter(null, null, "Sans plan"), 1, 20, "code", "ASC");
        assertTrue(noPlan.total() > 0);

        assertEquals(40, repo.findAll(eqFilter(null, null, null), "name", "ASC").size());
        assertTrue(repo.findById(all.items().get(0).getId()).isPresent());
    }

    private static EquipmentRepo.Filter eqFilter(String text, String warranty, String maint) {
        return new EquipmentRepo.Filter(text, null, null, null, null, null, warranty, maint, false, null);
    }

    @Test
    void sparePartSearchWithLowStockFacet() {
        SparePartRepo repo = new SparePartRepo();
        var all = repo.search(null, null, false, null, false, 1, 50, "name", "ASC");
        assertEquals(35, all.total());
        var low = repo.search(null, null, true, null, false, 1, 50, "name", "ASC");
        assertTrue(low.total() >= 3, "stock bas détecté");
        var text = repo.search("filtre", null, false, null, false, 1, 50, "name", "ASC");
        assertTrue(text.total() > 0);
    }

    @Test
    void planSearchBuckets() {
        PlanRepo repo = new PlanRepo();
        var all = repo.search(new PlanRepo.Filter(null, null, null, null, null, null, false),
                1, 50, "next_due", "ASC");
        assertEquals(28, all.total());
        var late = repo.search(new PlanRepo.Filter(null, null, "EN_RETARD", null, null, null, false),
                1, 50, "next_due", "ASC");
        assertTrue(late.total() >= 4);
        var month = repo.search(new PlanRepo.Filter(null, "MENSUELLE", null, null, null, null, false),
                1, 50, "next_due", "ASC");
        assertTrue(month.total() > 0);
    }

    @Test
    void supplierSearchExcludesArchivedByDefault() {
        var page = new SupplierRepo().search(null, null, false, 1, 20, "company", "ASC");
        assertEquals(12, page.total());
        var text = new SupplierRepo().search("medtech", null, false, 1, 20, "company", "ASC");
        assertEquals(1, text.total());
    }

    @Test
    void requestAndWorkOrderSearch() {
        var req = new RequestRepo().search(new RequestRepo.Filter(null, null, null, null, null,
                null, null, null, null), 1, 20, "reported_at", "DESC");
        assertEquals(30, req.total());
        var crit = new RequestRepo().search(new RequestRepo.Filter(null, null, "CRITIQUE", null, null,
                null, null, null, null), 1, 20, "reported_at", "DESC");
        assertTrue(crit.total() >= 1);

        var wo = new WorkOrderRepo().search(new WorkOrderRepo.Filter(null, null, null, null, null,
                null, null, null, null), 1, 20, "opened_at", "DESC");
        assertTrue(wo.total() >= 40);
        var closed = new WorkOrderRepo().search(new WorkOrderRepo.Filter(null, "CLOTUREE", null, null, null,
                null, null, null, null), 1, 20, "opened_at", "DESC");
        assertTrue(closed.total() >= 15);
        assertFalse(wo.items().isEmpty());
    }

    @Test
    void userContractAuditTechnicianDocumentSearch() {
        assertEquals(5, new UserRepo().search(null, null, null, null, 1, 20, "fullName", "ASC").total());
        assertEquals(12, new ContractRepo().search(null, null, null, 1, 20, "contract_no", "ASC").total());
        var audit = new AuditRepo().search(null, null, null, null, null, null, 1, 50);
        assertTrue(audit.total() >= 30);
        assertEquals(8, new TechnicianRepo().search(null, null, null, 1, 20, "full_name", "ASC").total());
        assertTrue(new DocumentRepo().search(null, null, null, 1, 20).total() >= 4);
        assertEquals(12, new DepartmentRepo().search(null, 1, 20).total());
    }
}
