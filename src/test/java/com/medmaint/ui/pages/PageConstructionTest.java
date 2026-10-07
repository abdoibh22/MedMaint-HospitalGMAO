package com.medmaint.ui.pages;

import com.medmaint.persistence.Db;
import com.medmaint.persistence.SchemaMigrator;
import com.medmaint.security.Role;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.seed.DemoDataSeeder;
import com.medmaint.ui.navigation.Router;
import com.medmaint.ui.pages.dashboard.DashboardPage;
import com.medmaint.ui.pages.maintenance.WorkOrdersPage;
import com.medmaint.ui.pages.requests.RequestsPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Construction des écrans (chargement SQL à la création).
 * Couvre deux classes de bugs réels :
 * — NPE d'unboxing des filtres « dept != null ? dept.getId() : scope » (Long) ;
 * — UnauthorizedException quand un rôle sans TECHNICIAN_VIEW ouvre une page
 *   dont les filtres chargent des référentiels protégés (ex. DEPT_MANAGER ↔
 *   Ordres de travail, cf. journal applicatif).
 */
class PageConstructionTest {

    @BeforeAll
    static void init() {
        Db.override("jdbc:h2:mem:pages-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        SchemaMigrator.migrate();
        DemoDataSeeder.runIfNeeded();
        try {
            javafx.application.Platform.startup(() -> { });
            javafx.application.Platform.setImplicitExit(false);
        } catch (IllegalStateException alreadyStarted) {
            // toolkit JavaFX déjà initialisé dans ce JVM de test
        } catch (Throwable ignored) {
            // sans toolkit, la construction de nœuds hors scène reste possible
        }
    }

    @BeforeEach
    void adminSession() {
        login(Role.ADMIN);
    }

    @AfterAll
    static void cleanup() {
        Session.clear();
    }

    private static void login(Role role) {
        Session.create(1L, "test-" + role.name().toLowerCase(), "Test " + role.label(), role, null, null);
    }

    @Test
    void requestsPageLoadsDataOnConstruction() {
        assertDoesNotThrow(RequestsPage::new, "RequestsPage.filter() ne doit pas planer (scope Long)");
    }

    @Test
    void workOrdersPageLoadsDataOnConstruction() {
        assertDoesNotThrow(WorkOrdersPage::new, "WorkOrdersPage.load() ne doit pas planer (techScope Long)");
    }

    @Test
    void dashboardAndEquipmentListConstruct() {
        assertDoesNotThrow(DashboardPage::new);
        assertDoesNotThrow(com.medmaint.ui.pages.equipment.EquipmentListPage::new);
    }

    /**
     * Balayage complet : chaque route visible par un rôle doit pouvoir être
     * construite sans exception (permission manquante sur un filtre, ternaire
     * d'unboxing, requête SQL…). Toutes les défaillances sont listées d'un coup.
     */
    @Test
    void everyVisibleRouteConstructsForEveryRole() {
        Router router = UiKit.buildRouter();
        List<String> failures = new ArrayList<>();
        int constructed = 0;
        try {
            for (Role role : Role.values()) {
                login(role);
                for (Router.Route r : router.visibleRoutes()) {
                    try {
                        router.navigate(r.id(), null);
                        constructed++;
                    } catch (Throwable t) {
                        failures.add(role + " / " + r.id() + " : " + t);
                    }
                }
            }
        } finally {
            login(Role.ADMIN);
        }
        assertEquals(List.of(), failures,
                "Chaque route visible doit se construire pour chaque rôle ("
                        + constructed + " pages OK)");
    }

    /**
     * Régression tableau de bord : dashboardKpis exige DASHBOARD_VIEW mais
     * appelait des métriques internes gated REPORT_VIEW → UnauthorizedException
     * attrapée par le catch de la page → état d'erreur pour TECHNICIAN,
     * DEPT_MANAGER et REQUESTER. Teste la cause au niveau service.
     */
    @Test
    void dashboardKpisLoadForEveryRole() {
        List<String> failures = new ArrayList<>();
        for (Role role : Role.values()) {
            login(role);
            try {
                AppContext.get().analytics().dashboardKpis(30);
            } catch (RuntimeException e) {
                failures.add(role + " : " + e);
            }
        }
        login(Role.ADMIN);
        assertEquals(List.of(), failures,
                "Les KPI du tableau de bord doivent charger pour chaque rôle");
    }

    /**
     * La page tableau de bord ne doit jamais tomber dans son catch global
     * (état d'erreur « empty-state » en enfant direct) pour un rôle quelconque :
     * c'était le symptôme visible pour TECHNICIAN et DEPT_MANAGER.
     */
    @Test
    void dashboardPageRendersForEveryRole() {
        List<String> failures = new ArrayList<>();
        for (Role role : Role.values()) {
            login(role);
            try {
                DashboardPage page = new DashboardPage();
                boolean errorState = page.getChildren().stream()
                        .anyMatch(n -> n.getStyleClass().contains("empty-state"));
                if (errorState) {
                    failures.add(role + " : état d'erreur affiché");
                }
            } catch (RuntimeException e) {
                failures.add(role + " : " + e);
            }
        }
        login(Role.ADMIN);
        assertEquals(List.of(), failures,
                "Le tableau de bord doit s'afficher pour chaque rôle");
    }
}
