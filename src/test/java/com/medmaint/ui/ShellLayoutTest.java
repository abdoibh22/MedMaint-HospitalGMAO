package com.medmaint.ui;

import com.medmaint.persistence.Db;
import com.medmaint.persistence.SchemaMigrator;
import com.medmaint.security.Role;
import com.medmaint.security.Session;
import com.medmaint.seed.DemoDataSeeder;
import com.medmaint.ui.components.FormDialog;
import com.medmaint.ui.pages.UiKit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dimensions d'interface imposées par l'utilisateur :
 * — fenêtre principale 1400×900 par défaut, redimensionnable
 *   avec bornes min (contenu lisible) et max (écran) ;
 * — navigation latérale défilable à la molette (ScrollPane) ;
 * — pop-up de formulaire 800×600 (champs toujours visibles/cliquables).
 */
class ShellLayoutTest {

    @BeforeAll
    static void initFx() {
        // Base de démo : nécessaire pour les deep-links détail et les pages qui
        // interrogent les services à la construction.
        Db.override("jdbc:h2:mem:layout-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        SchemaMigrator.migrate();
        DemoDataSeeder.runIfNeeded();
        try {
            javafx.application.Platform.startup(() -> { });
            javafx.application.Platform.setImplicitExit(false);
        } catch (IllegalStateException alreadyStarted) {
            // toolkit déjà démarré par une autre classe de test
        } catch (Throwable ignored) {
            // sans toolkit, certains environnements de test restent utilisables
        }
    }

    @AfterAll
    static void cleanup() {
        Session.clear();
    }

    private static void runOnFx(Runnable r) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            assertTrue(done.await(15, TimeUnit.SECONDS), "le thread JavaFX n'a pas répondu");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            fail("interrompu en attente du thread JavaFX");
        }
        if (err.get() != null) {
            throw new AssertionError("échec sur le thread JavaFX : " + err.get(), err.get());
        }
    }

    @Test
    void mainWindowDefaults1400x900WithSensibleBounds() {
        Session.clear(); // Topbar ne touche pas à la base sans session
        runOnFx(() -> {
            Stage stage = new Stage();
            new MainShell(stage, UiKit.buildRouter());
            Scene scene = stage.getScene();
            assertEquals(1400, (int) scene.getWidth(), "scène par défaut en 1400 de large");
            assertEquals(900, (int) scene.getHeight(), "scène par défaut en 900 de haut");
            assertTrue(stage.isResizable(), "la fenêtre doit être redimensionnable");
            assertEquals(1024, (int) stage.getMinWidth(), "largeur minimale");
            assertEquals(680, (int) stage.getMinHeight(), "hauteur minimale");
            javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
            if (vb.getWidth() >= 1400) {
                assertTrue(stage.getMaxWidth() >= 1400 && stage.getMaxWidth() <= vb.getWidth(),
                        "largeur maximale bornée à l'écran sans descendre sous le défaut");
            }
            if (vb.getHeight() >= 900) {
                assertTrue(stage.getMaxHeight() >= 900 && stage.getMaxHeight() <= vb.getHeight(),
                        "hauteur maximale bornée à l'écran sans descendre sous le défaut");
            }
            stage.hide();
        });
    }

    @Test
    void sidebarNavigationIsScrollable() {
        Session.clear();
        runOnFx(() -> {
            Stage stage = new Stage();
            MainShell shell = new MainShell(stage, UiKit.buildRouter());
            boolean scrollable = shell.sidebar().getChildren().stream()
                    .anyMatch(c -> c instanceof ScrollPane);
            assertTrue(scrollable, "la sidebar doit contenir un ScrollPane pour la molette");
            stage.hide();
        });
    }

    @Test
    void formDialogsAre800x600() {
        Session.clear();
        runOnFx(() -> {
            FormDialog<Object> dlg = new FormDialog<>("Test") { };
            assertEquals(800, (int) dlg.getDialogPane().getPrefWidth(), "pop-up formulaire en 800 de large");
            assertEquals(600, (int) dlg.getDialogPane().getPrefHeight(), "pop-up formulaire en 600 de haut");
            assertTrue(dlg.getDialogPane().getContent() instanceof ScrollPane,
                    "le corps du formulaire doit être défilant (ScrollPane)");
        });
    }

    /**
     * Régression deep-link : le routeur doit désormais dispatcher le contexte
     * « detail:<id> » (tableau de bord, recherche, notifications). Avant la correction,
     * la fiche équipement n'était jamais construite — l'ingénieur ne voyait
     * donc jamais le détail.
     */
    @Test
    void detailContextOpensTheRightPage() {
        runOnFx(() -> {
            Stage stage = new Stage();
            MainShell shell = new MainShell(stage, UiKit.buildRouter());
            Session.create(1L, "engineer", "Test Ingénieur", Role.ENGINEER, null, null);
            try {
                javafx.scene.Node eq = shell.navigate("equipment", "detail:1");
                assertTrue(eq instanceof com.medmaint.ui.pages.equipment.EquipmentDetailPage,
                        "le deep-link détail équipement doit ouvrir la fiche complète (ENGINEER)");
                javafx.scene.Node wo = shell.navigate("workorders", "detail:1");
                assertTrue(wo instanceof com.medmaint.ui.pages.maintenance.WorkOrdersPage,
                        "le deep-link détail OT doit construire la page Interventions sans erreur");
                javafx.scene.Node rq = shell.navigate("requests", "detail:1");
                assertTrue(rq instanceof com.medmaint.ui.pages.requests.RequestsPage,
                        "le deep-link détail demande doit construire la page Demandes sans erreur");
            } finally {
                Session.clear();
            }
            stage.hide();
        });
    }

    /** Vues longues (notifications, détail de demande) défilantes. */
    @Test
    void longViewsAreScrollable() {
        runOnFx(() -> {
            Session.create(1L, "admin", "Test Admin", Role.ADMIN, null, null);
            try {
                assertTrue(containsDetailScroll(new com.medmaint.ui.pages.shell.NotificationsPage()),
                        "la liste des notifications doit être défilante");
                com.medmaint.ui.pages.requests.RequestsPage rq =
                        new com.medmaint.ui.pages.requests.RequestsPage();
                rq.openContext("detail:1");
                assertTrue(containsDetailScroll(rq),
                        "le détail d'une intervention doit être défilant");
            } finally {
                Session.clear();
            }
        });
    }

    /** true si l'arbre contient le ScrollPane « detail-scroll » (pas celui de la table). */
    private static boolean containsDetailScroll(javafx.scene.Node n) {
        if (n instanceof ScrollPane sp && sp.getStyleClass().contains("detail-scroll")) {
            return true;
        }
        if (n instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node c : p.getChildrenUnmodifiable()) {
                if (containsDetailScroll(c)) {
                    return true;
                }
            }
        }
        return false;
    }
}
