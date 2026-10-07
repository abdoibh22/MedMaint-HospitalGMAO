package com.medmaint.ui.navigation;

import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Routeur applicatif : chaque entrée de menu correspond à une route
 * avec sa permission de visibilité et une fabrique de page (instanciation
 * paresseuse — une page n'est créée que si l'utilisateur y accède).
 */
public final class Router {

    /** Une route du menu. */
    public record Route(String id, String titleKey, String icon, Permission visibility,
                        String sectionKey, Supplier<Node> factory) {
    }

    private final Map<String, Route> routes = new LinkedHashMap<>();
    private final List<RouteListener> listeners = new ArrayList<>();

    /** Observée par le shell (titre, fil d'Ariane, page active). */
    @FunctionalInterface
    public interface RouteListener {
        void onNavigate(Route route, Node page, String context);
    }

    public void register(Route route) {
        routes.put(route.id(), route);
    }

    public void addListener(RouteListener l) {
        listeners.add(l);
    }

    /**
     * La coquille applicative est unique : remplacer les écouteurs évite
     * d'accumuler des listeners obsolètes à chaque reconnexion
     * (ou double-clic rapide sur « Se connecter »).
     */
    public void clearListeners() {
        listeners.clear();
    }

    public List<Route> visibleRoutes() {
        List<Route> out = new ArrayList<>();
        for (Route r : routes.values()) {
            if (r.visibility() == null || Session.get() == null || Session.get().has(r.visibility())) {
                out.add(r);
            }
        }
        return out;
    }

    public Route route(String id) {
        return routes.get(id);
    }

    /**
     * Navigue vers une route.
     * @param context paramètre de contexte (ex. identifiant d'un détail) ou null
     * @return la page construite, ou null si l'accès est refusé / route inconnue
     */
    public Node navigate(String id, String context) {
        Route r = routes.get(id);
        if (r == null) {
            return null;
        }
        if (r.visibility() != null && Session.get() != null && !Session.get().has(r.visibility())) {
            return null;
        }
        Node page = r.factory().get();
        // Contexte « detail:<id> » : la page ouvre sa vue détail si elle le sait
        // (sinon elle renvoie une page de détail distincte, ex. fiche équipement).
        if (context != null && context.startsWith("detail:") && page instanceof ContextAware ca) {
            Node resolved = ca.openContext(context);
            if (resolved != null) {
                page = resolved;
            }
        }
        for (RouteListener l : listeners) {
            l.onNavigate(r, page, context);
        }
        return page;
    }

    /** Construit uniquement une page de détail (sans notification de route). */
    public Node build(String id, String context) {
        Route r = routes.get(id);
        return r == null ? null : r.factory().get();
    }
}
