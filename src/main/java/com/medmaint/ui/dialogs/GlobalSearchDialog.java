package com.medmaint.ui.dialogs;

import com.medmaint.service.AppContext;
import com.medmaint.service.SearchService;
import com.medmaint.ui.MainShell;
import com.medmaint.ui.i18n.Messages;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recherche globale (Ctrl/Cmd+M ou bouton ⌕) :
 * résultats groupés par type, navigation au double-clic / Entrée.
 */
public final class GlobalSearchDialog {

    private GlobalSearchDialog() {
    }

    public static void show(Window owner) {
        Dialog<SearchService.Result> dialog = new Dialog<>();
        dialog.setTitle(Messages.get("search.title"));
        dialog.initOwner(owner);
        dialog.initModality(Modality.NONE);
        dialog.getDialogPane().getScene().getStylesheets().add(
                GlobalSearchDialog.class.getResource("/css/app.css").toExternalForm());
        dialog.getDialogPane().setPrefWidth(800);
        dialog.getDialogPane().setPrefHeight(600);
        dialog.getDialogPane().setMinWidth(800);
        dialog.getDialogPane().setMinHeight(600);

        TextField input = new TextField();
        input.setPromptText(Messages.get("search.placeholder"));
        input.getStyleClass().add("search-field");
        input.requestFocus();

        ListView<SearchService.Result> list = new ListView<>();
        list.setPrefHeight(400);
        javafx.scene.layout.VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
        Label hint = new Label(Messages.get("search.hint"));
        hint.getStyleClass().add("dim");

        List<SearchService.Result> all = new ArrayList<>();

        Runnable search = () -> {
            String q = input.getText();
            all.clear();
            Map<String, List<SearchService.Result>> grouped =
                    AppContext.get().search().globalSearch(q, 6);
            grouped.forEach((group, results) -> {
                Label header = new Label(group.toUpperCase());
                header.getStyleClass().add("section-label");
                header.setPadding(new Insets(6, 8, 2, 8));
                results.forEach(r -> all.add(r));
            });
            // Marqueurs de groupe via un libellé enrichi
            list.getItems().setAll(prefixGroups(grouped));
            hint.setVisible(q.trim().length() < 2);
        };

        input.textProperty().addListener((obs, o, n) -> search.run());

        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(SearchService.Result item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox box = new VBox(1);
                    Label t = new Label(item.title());
                    t.getStyleClass().add("h3");
                    Label s = new Label((item.group() + (item.subtitle() != null ? " · " + item.subtitle() : "")));
                    s.getStyleClass().add("dim");
                    box.getChildren().addAll(t, s);
                    setGraphic(box);
                }
            }
        });

        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openSelected(list.getSelectionModel().getSelectedItem(), dialog);
            }
        });
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                list.requestFocus();
                if (!list.getItems().isEmpty()) {
                    list.getSelectionModel().select(0);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                openSelected(list.getSelectionModel().getSelectedItem() != null
                        ? list.getSelectionModel().getSelectedItem()
                        : (all.isEmpty() ? null : all.get(0)), dialog);
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelected(list.getSelectionModel().getSelectedItem(), dialog);
            }
        });

        VBox body = new VBox(8, input, hint, list);
        body.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.show();
    }

    private static List<SearchService.Result> prefixGroups(Map<String, List<SearchService.Result>> grouped) {
        List<SearchService.Result> out = new ArrayList<>();
        grouped.forEach((g, items) -> items.forEach(out::add));
        return out;
    }

    private static void openSelected(SearchService.Result r, Dialog<?> dialog) {
        if (r == null) {
            return;
        }
        dialog.close();
        switch (r.route()) {
            case "equipment.detail" -> MainShell.shellNavigate("equipment", "detail:" + r.entityId());
            case "workorders.detail" -> MainShell.shellNavigate("workorders", "detail:" + r.entityId());
            default -> MainShell.shellNavigate(r.route(), null);
        }
    }

    public static void show(Stage stage) {
        show((Window) stage);
    }

    public static Node searchButton() {
        javafx.scene.control.Button b = new javafx.scene.control.Button("⌕");
        b.getStyleClass().add("icon-btn");
        b.setOnAction(e -> show(null));
        return b;
    }
}
