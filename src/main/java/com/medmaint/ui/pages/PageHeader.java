package com.medmaint.ui.pages;

import com.medmaint.ui.components.Ui;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * En-tête standard des pages : titre + sous-titre + actions alignées à droite.
 */
public final class PageHeader extends HBox {

    private final Label title = new Label();
    private final Label subtitle = new Label();

    public PageHeader(String titleText) {
        setSpacing(10);
        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        setPadding(new javafx.geometry.Insets(2, 0, 6, 0));

        title.setText(titleText);
        title.getStyleClass().add("h1");
        subtitle.getStyleClass().add("muted");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(title, subtitle, spacer);
    }

    public PageHeader subtitle(String text) {
        subtitle.setText(text == null || text.isBlank() ? "" : "  ·  " + text);
        return this;
    }

    public void addAction(Node button) {
        getChildren().add(button);
    }

    public void addAction(String label, boolean primary, Runnable action) {
        Button b = new Button(label);
        b.getStyleClass().add(primary ? "primary" : "ghost");
        b.setOnAction(e -> action.run());
        getChildren().add(b);
    }

    public Label titleLabel() {
        return title;
    }

    public static Button actionButton(String label, boolean primary, Runnable action) {
        Button b = new Button(label);
        b.getStyleClass().add(primary ? "primary" : "ghost");
        b.setOnAction(e -> action.run());
        return b;
    }

    public static HBox toolbar(Node... nodes) {
        HBox box = new HBox(8, nodes);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return box;
    }
}
