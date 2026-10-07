package com.medmaint;

import com.medmaint.persistence.Db;
import com.medmaint.persistence.SchemaMigrator;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.persistence.repo.RequestRepo;
import com.medmaint.persistence.repo.WorkOrderRepo;
import com.medmaint.security.Role;
import com.medmaint.security.Session;
import com.medmaint.security.UnauthorizedException;
import com.medmaint.security.ValidationException;
import com.medmaint.seed.DemoDataSeeder;
import com.medmaint.service.AppContext;
import com.medmaint.ui.pages.requests.RequestFormDialog;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Playtest : parcours d'un nouveau client « pressé » à travers les vraies
 * surfaces (authentification, recherche, formulaire vide, double-clic sur une
 * transition). Chaque flux doit rester courtois : message français, pas
 * d'exception propagée, pas de donnée corrompue.
 */
class UserFlowsTest {

    @BeforeAll
    static void init() {
        Db.override("jdbc:h2:mem:playtest-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        SchemaMigrator.migrate();
        DemoDataSeeder.runIfNeeded();
        try {
            Platform.startup(() -> { });
            Platform.setImplicitExit(false);
        } catch (IllegalStateException alreadyStarted) {
            // toolkit déjà démarré par une autre classe
        } catch (Throwable ignored) {
            // sans toolkit
        }
    }

    @AfterAll
    static void cleanup() {
        Session.clear();
    }

    // ---------- 1. Connexion : mauvais inputs ----------

    @Test
    void loginWithBadInputsShowsFrenchMessages() {
        UnauthorizedException empty = assertThrows(UnauthorizedException.class,
                () -> AppContext.get().auth().login("", ""));
        assertTrue(empty.getMessage().contains("identifiant"),
                "champ vide → message FR : " + empty.getMessage());

        UnauthorizedException wrong = assertThrows(UnauthorizedException.class,
                () -> AppContext.get().auth().login("admin", "faux-mot-de-passe"));
        assertTrue(wrong.getMessage().contains("incorrect"),
                "mauvais mot de passe → message FR : " + wrong.getMessage());

        assertNotNull(AppContext.get().auth().login("admin", "demo2026"),
                "identifiant valide → connexion");
        Session.clear();
        assertNotNull(AppContext.get().auth().login("service@medmaint.demo", "demo2026"),
                "connexion par e-mail → OK");
        Session.clear();
    }

    // ---------- 2. Recherche : chaînes hostiles ----------

    @Test
    void junkSearchNeverBreaksListsNorData() {
        String[] junk = {"'; DROP TABLE equipment; --", "%", "_", "O'Brien", "\"", "\uD83C\uDFA5", "resp %_"};
        for (String j : junk) {
            assertDoesNotThrow(() -> new EquipmentRepo()
                            .search(new EquipmentRepo.Filter(j, null, null, null, null, null,
                                    null, null, false, null), 1, 10, "code", "ASC"),
                    "recherche équipement invalide : " + j);
            assertDoesNotThrow(() -> new RequestRepo()
                            .search(new RequestRepo.Filter(j, null, null, null, null, null,
                                    null, null, null), 1, 10, "reported_at", "DESC"),
                    "recherche demande invalide : " + j);
            assertDoesNotThrow(() -> new WorkOrderRepo()
                            .search(new WorkOrderRepo.Filter(j, null, null, null, null, null,
                                    null, null, null), 1, 10, "opened_at", "DESC"),
                    "recherche OT invalide : " + j);
        }
        // Les données sont intactes après la tentative d'injection
        assertEquals(40, new EquipmentRepo()
                        .search(new EquipmentRepo.Filter(null, null, null, null, null, null,
                                null, null, false, null), 1, 1, "code", "ASC").total(),
                "la table équipements doit survivre aux recherches hostiles");
    }

    // ---------- 3. Formulaire de demande soumis vide ----------

    @Test
    void emptyRequestFormSubmitIsBlockedWithoutSideEffect() throws Exception {
        Session.create(1L, "admin", "Admin", Role.ADMIN, null, null);
        RequestRepo.Filter empty = new RequestRepo.Filter(null, null, null, null, null,
                null, null, null, null);
        long before = new RequestRepo().search(empty, 1, 1, "reported_at", "DESC").total();

        runOnFx(() -> {
            RequestFormDialog dlg = new RequestFormDialog(RequestFormDialog.Mode.CREATE, null);
            Button save = (Button) dlg.getDialogPane().getButtonTypes().stream()
                    .filter(b -> b.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OK_DONE)
                    .map(dlg.getDialogPane()::lookupButton)
                    .findFirst()
                    .orElseThrow();
            save.fire(); // clic « Enregistrer » sur un formulaire vide
        });

        long after = new RequestRepo().search(empty, 1, 1, "reported_at", "DESC").total();
        assertEquals(before, after, "un formulaire vide ne doit rien créer");
        Session.clear();
    }

    // ---------- 4. Double-clic sur « Démarrer » un ordre de travail ----------

    @Test
    void doubleStartTransitionIsRejectedGracefully() {
        Session.create(1L, "admin", "Admin", Role.ADMIN, null, null);
        var startable = new WorkOrderRepo().search(
                new WorkOrderRepo.Filter(null, "ASSIGNEE", null, null, null, null, null, null, null),
                1, 5, "opened_at", "DESC");
        assertTrue(startable.total() > 0, "le seed contient des OT assignés");
        long id = startable.items().get(0).getId();

        AppContext.get().workOrders().start(id); // premier clic
        ValidationException second = assertThrows(ValidationException.class,
                () -> AppContext.get().workOrders().start(id), // double-clic
                "le second démarrage doit être refusé proprement");
        assertTrue(second.getMessage().contains("Impossible de démarrer"),
                "message FR attendu : " + second.getMessage());
        Session.clear();
    }

    // ---------- 5. Double-clic sur « Approuver » / rejeter sans motif ----------

    @Test
    void doubleApproveIsIdempotentAndClosedRequestsAreGuarded() {
        Session.create(1L, "manager", "Manager", Role.MANAGER, null, null);
        RequestRepo.Filter open = new RequestRepo.Filter(null, "NOUVELLE", null, null, null,
                null, null, null, null);
        var candidates = new RequestRepo().search(open, 1, 2, "reported_at", "DESC");
        assertTrue(candidates.total() > 0, "le seed contient des demandes nouvelles");
        long id = candidates.items().get(0).getId();

        // Double-clic « Approuver » : second appel idempotent, aucun crash ni corruption
        AppContext.get().requests().approve(id, null);
        assertDoesNotThrow(() -> AppContext.get().requests().approve(id, null),
                "le second approuver ne doit pas planer");
        assertEquals("ACCEPTEE", new RequestRepo().findById(id).orElseThrow().getStatus(),
                "le statut reste ACCEPTEE après double-clic");

        // Garde-fou : une demande déjà terminée ne peut plus être approuvée
        var closed = new RequestRepo().search(new RequestRepo.Filter(null, "RESOLUE", null, null,
                null, null, null, null, null), 1, 1, "reported_at", "DESC");
        assertTrue(closed.total() > 0, "le seed contient des demandes résolues");
        ValidationException guarded = assertThrows(ValidationException.class,
                () -> AppContext.get().requests().approve(closed.items().get(0).getId(), null),
                "approuver une demande résolue doit être refusé");
        assertTrue(guarded.getMessage().contains("déjà"),
                "message FR attendu : " + guarded.getMessage());

        // Rejet sans motif : refus courtois
        long other = candidates.items().get(0).getId() == id && candidates.items().size() > 1
                ? candidates.items().get(1).getId() : -1L;
        if (other > 0) {
            ValidationException noReason = assertThrows(ValidationException.class,
                    () -> AppContext.get().requests().reject(other, "   "),
                    "rejet sans motif doit être refusé");
            assertTrue(noReason.getMessage().contains("motif"),
                    "message FR attendu : " + noReason.getMessage());
        }
        Session.clear();
    }

    // ---------- 6. Deep-links avec ids corrompus ----------

    @Test
    void garbageDeepLinksFallBackSafely() throws Exception {
        Session.create(1L, "admin", "Admin", Role.ADMIN, null, null);
        runOnFx(() -> {
            com.medmaint.ui.MainShell shell = new com.medmaint.ui.MainShell(
                    new javafx.stage.Stage(), com.medmaint.ui.pages.UiKit.buildRouter());
            javafx.scene.Node eq = shell.navigate("equipment", "detail:pas-un-nombre");
            assertNotNull(eq, "deep-link équipement corrompu → page d'accueil de la fiche");
            assertTrue(eq instanceof com.medmaint.ui.pages.equipment.EquipmentDetailPage,
                    "l'id corrompu doit afficher l'état « introuvable » de la fiche");
            javafx.scene.Node wo = shell.navigate("workorders", "detail:99999999");
            assertNotNull(wo, "deep-link OT inexistant → page liste sans crash");
            javafx.scene.Node empty = shell.navigate("requests", "detail:");
            assertNotNull(empty, "deep-link vide → page liste sans crash");
        });
        Session.clear();
    }

    // ---------- 7. Double-clic rapide sur « Se connecter » ----------

    @Test
    void rapidLoginDoubleClickLeavesExactlyOneRouteListener() throws Exception {
        com.medmaint.ui.navigation.Router router = com.medmaint.ui.pages.UiKit.buildRouter();
        runOnFx(() -> {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            com.medmaint.ui.LoginPage.show(stage, router);
            javafx.scene.Parent root = stage.getScene().getRoot();
            javafx.scene.Node submit = root.lookup(".primary");
            assertTrue(submit instanceof javafx.scene.control.Button,
                    "bouton de connexion trouvable dans la page");
            java.util.Set<javafx.scene.Node> fields = root.lookupAll(".search-field");
            javafx.scene.Node[] ordered = fields.toArray(javafx.scene.Node[]::new);
            ((javafx.scene.control.TextField) ordered[0]).setText("admin");
            ((javafx.scene.control.TextField) ordered[1]).setText("demo2026");
            javafx.scene.control.Button btn = (javafx.scene.control.Button) submit;
            btn.fire(); // premier clic → connexion + coquille
            btn.fire(); // double-clic impatient → ne doit pas empiler les listeners
            stage.hide();
        });

        java.lang.reflect.Field f =
                com.medmaint.ui.navigation.Router.class.getDeclaredField("listeners");
        f.setAccessible(true);
        int listeners = ((java.util.List<?>) f.get(router)).size();
        assertEquals(1, listeners,
                "un seul écouteur de route après double-clic sur « Se connecter »");
        Session.clear();
    }

    // ---------- Outils ----------

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
}
