package com.medmaint.ui;

import com.medmaint.security.Session;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.navigation.Router;
import com.medmaint.ui.shell.Sidebar;
import com.medmaint.ui.shell.Topbar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Coquille applicative : sidebar + topbar + zone de contenu.
 * Instance statique unique pour les navigations déclenchées depuis n'importe
 * quel composant (menu, notifications, recherche, boutons d'actions).
 */
public final class MainShell {

    private static MainShell instance;

    private final Stage stage;
    private final Router router;
    private final BorderPane root = new BorderPane();
    private final Sidebar sidebar;
    private final Topbar topbar;
    private final VBox content = new VBox();
    private final Label contextCrumb = new Label();

    public static void install(MainShell shell) {
        instance = shell;
    }

    /** Navigation statique (appelée par Sidebar / Topbar / pages). */
    public static Node shellNavigate(String routeId, String context) {
        return instance != null ? instance.navigate(routeId, context) : null;
    }

    public static void logout() {
        if (instance != null) {
            instance.doLogout();
        }
    }

    public MainShell(Stage stage, Router router) {
        this.stage = stage;
        this.router = router;
        instance = this;

        sidebar = new Sidebar(router, this::toggleSidebar);
        topbar = new Topbar(router);
        topbar.bind(this::toggleSidebar, this::openGlobalSearch);

        content.setPadding(new Insets(16, 20, 20, 20));
        content.setSpacing(10);
        VBox.setVgrow(content, Priority.ALWAYS);

        root.setLeft(sidebar);
        root.setCenter(wrapCenter());

        router.clearListeners(); // reconnexion / double-clic : pas de listener fantôme
        router.addListener((route, page, context) -> {
            showPage(page);
            topbar.setContext(route.titleKey(), buildCrumb(route, context));
            sidebar.setActive(route.id());
        });

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(
                MainShell.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        // Fenêtre principale : 1400×900 par défaut, redimensionnable
        // avec des bornes raisonnables (min : contenu lisible ; max : écran)
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        if (screen.getWidth() >= 1400) {
            stage.setMaxWidth(screen.getWidth());
        }
        if (screen.getHeight() >= 900) {
            stage.setMaxHeight(screen.getHeight());
        }
        stage.setTitle(Messages.get("app.name") + " — " + Messages.get("app.subtitle"));
    }

    private Node wrapCenter() {
        VBox center = new VBox(topbar, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return center;
    }

    private String buildCrumb(Router.Route route, String context) {
        if (context == null || context.isBlank()) {
            return Messages.get(route.sectionKey());
        }
        return Messages.get(route.sectionKey()) + "  ›  " + context;
    }

    public void start() {
        navigate("dashboard", null);
        stage.show();
    }

    public Node navigate(String routeId, String context) {
        Node page = router.navigate(routeId, context);
        if (page == null) {
            showPage(accessDenied());
        }
        return page;
    }

    /** Navigue vers une route en passant un callback de page construite. */
    public Node navigateWith(String routeId, String context,
                             java.util.function.BiConsumer<String, Node> onPage) {
        Node page = router.navigate(routeId, context);
        if (page == null) {
            showPage(accessDenied());
            return null;
        }
        onPage.accept(routeId, page);
        return page;
    }

    public void showPage(Node page) {
        content.getChildren().setAll(page);
        VBox.setVgrow(page, Priority.ALWAYS);
    }

    public Router router() {
        return router;
    }

    public Stage stage() {
        return stage;
    }

    private void toggleSidebar() {
        sidebar.toggle();
    }

    private void openGlobalSearch() {
        com.medmaint.ui.dialogs.GlobalSearchDialog.show(stage);
    }

    private void doLogout() {
        com.medmaint.service.AppContext.get().auth().logout();
        content.getChildren().clear();
        instance = null;
        com.medmaint.ui.LoginPage.show(stage, router);
    }

    private Node accessDenied() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60));
        Label title = new Label(Messages.get("msg.unauthorized"));
        title.getStyleClass().add("h2");
        title.setWrapText(true);
        box.getChildren().add(title);
        return box;
    }

    /** Construit la coquille et navigue vers la page d'accueil. */
    public static MainShell boot(Stage stage, Router router, String homeRoute) {
        MainShell shell = new MainShell(stage, router);
        shell.start();
        shell.navigate(homeRoute, null);
        return shell;
    }

    public Sidebar sidebar() {
        return sidebar;
    }

    public Topbar topbar() {
        return topbar;
    }

    public static MainShell current() {
        return instance;
    }

    public HBox toolbarSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return new HBox(r);
    }
}
