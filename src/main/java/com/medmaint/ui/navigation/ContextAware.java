package com.medmaint.ui.navigation;

import javafx.scene.Node;

/**
 * Page capable d'ouvrir une vue de détail à partir d'un contexte de route
 * « detail:&lt;id&gt; » (clic depuis le tableau de bord, la recherche globale,
 * les notifications ou une liste).
 */
@FunctionalInterface
public interface ContextAware {

    /**
     * Ouvre le détail correspondant au contexte.
     *
     * @return le nœud à afficher : la page elle-même, ou une page de détail
     *         distincte (ex. {@code EquipmentDetailPage}) ; null pour conserver
     *         la page d'origine.
     */
    Node openContext(String context);
}
