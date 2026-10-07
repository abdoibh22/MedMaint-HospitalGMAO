package com.medmaint.ui.shell;

import com.medmaint.security.Session;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.navigation.Router;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu latéral rétractable, groupé par sections, filtré par permission.
 * Chaque item déclenche la navigation via le routeur.
 */
public final class Sidebar extends VBox {

    private final Router router;
    private boolean collapsed = false;
    private String activeRouteId;
    private final Runnable onNavigate;

    /** Menu défilable à la molette : le contenu dépasse parfois la hauteur utile. */
    private final javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane();
    private final VBox content = new VBox();

    public Sidebar(Router router, Runnable onNavigate) {
        this.router = router;
        this.onNavigate = onNavigate;
        getStyleClass().add("sidebar");

        content.setFillWidth(true);
        scroll.setContent(content);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        rebuild();
    }

    public void setActive(String routeId) {
        this.activeRouteId = routeId;
        rebuild();
    }

    public void toggle() {
        collapsed = !collapsed;
        getStyleClass().setAll(collapsed ? "sidebar" : "sidebar", "sidebar");
        if (collapsed) {
            if (!getStyleClass().contains("collapsed")) {
                getStyleClass().add("collapsed");
            }
        } else {
            getStyleClass().remove("collapsed");
        }
        rebuild();
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    /** Regroupe les routes visibles par section (ordre d'inscription du routeur). */
    public void rebuild() {
        getChildren().clear();

        VBox brand = new VBox(2);
        brand.getStyleClass().add("brand-box");
        Label name = new Label(Messages.get("app.name"));
        name.getStyleClass().add("brand-name");
        Label sub = new Label(collapsed ? "GMAO" : Messages.get("app.subtitle"));
        sub.getStyleClass().add("brand-sub");
        brand.getChildren().addAll(name, sub);
        if (collapsed) {
            brand.setAlignment(Pos.CENTER);
            brand.setStyle("-fx-padding: 18 4 14 4;");
        }
        getChildren().add(brand);

        // Sections + items construits dans le conteneur défilable (molette)
        content.getChildren().clear();
        List<Router.Route> routes = router.visibleRoutes();
        Map<String, List<Router.Route>> bySection = new LinkedHashMap<>();
        for (Router.Route r : routes) {
            bySection.computeIfAbsent(r.sectionKey(), k -> new java.util.ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<Router.Route>> entry : bySection.entrySet()) {
            if (!collapsed) {
                Label section = new Label(Messages.get(entry.getKey()).toUpperCase());
                section.getStyleClass().add("section-label");
                section.setStyle("-fx-padding: 14 18 6 18; -fx-text-fill: #7ea6b0;");
                content.getChildren().add(section);
            } else {
                javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
                sep.setStyle("-fx-padding: 8 10 0 10;");
                content.getChildren().add(sep);
            }
            for (Router.Route r : entry.getValue()) {
                content.getChildren().add(navButton(r));
            }
        }

        getChildren().add(scroll);
    }

    private javafx.scene.Node navButton(Router.Route r) {
        Label icon = new Label(r.icon());
        icon.setStyle("-fx-font-size: 14px; -fx-text-fill: inherit; -fx-min-width: 20px; -fx-alignment: center;");
        Label text = new Label(collapsed ? "" : Messages.get(r.titleKey()));
        text.getStyleClass().add("label");

        javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(12, icon, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("nav-item");
        box.setMinHeight(38);
        if (r.id().equals(activeRouteId)) {
            box.getStyleClass().add("active");
        }
        if (collapsed) {
            Tooltip.install(box, new Tooltip(Messages.get(r.titleKey())));
            box.setOnMouseClicked(e -> {
                if (onNavigate != null) {
                    onNavigate.run();
                }
                // Navigation directe malgré la giclure
                com.medmaint.ui.MainShell.shellNavigate(r.id(), null);
            });
        } else {
            box.setOnMouseClicked(e -> com.medmaint.ui.MainShell.shellNavigate(r.id(), null));
        }
        box.setStyle("-fx-cursor: hand;");
        return box;
    }

    /** Routes visibles pour la session courante (exposé pour tests). */
    public static List<String> visibleRouteIds(Router router) {
        List<String> out = new java.util.ArrayList<>();
        for (Router.Route r : router.visibleRoutes()) {
            out.add(r.id());
        }
        return out;
    }

    public static boolean canSee(Router.Route r) {
        return r.visibility() == null || Session.get() == null || Session.get().has(r.visibility());
    }
}
