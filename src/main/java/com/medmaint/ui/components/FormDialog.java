package com.medmaint.ui.components;

import com.medmaint.ui.i18n.Messages;
import com.medmaint.security.ValidationException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialogue de formulaire réutilisable :
 * grille de champs labelisés, messages d'erreur en français,
 * soumission protégée (les ValidationException deviennent des messages d'erreur).
 */
public class FormDialog<T> extends Dialog<T> {

    protected final GridPane grid = new GridPane();
    private final VBox root = new VBox(12);
    private final Label errorLabel = new Label();
    private final List<TextField> requiredTextFields = new ArrayList<>();
    private int row = 0;

    protected FormDialog(String title) {
        setTitle(title);
        getDialogPane().getStyleClass().add("dialog-pane");
        getDialogPane().getScene().getStylesheets().add(
                FormDialog.class.getResource("/css/app.css").toExternalForm());
        // Fenêtre de formulaire fixée en 800×600 (champs toujours visibles)
        getDialogPane().setPrefWidth(800);
        getDialogPane().setPrefHeight(600);
        getDialogPane().setMinWidth(800);
        getDialogPane().setMinHeight(600);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");

        grid.setHgap(14);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 8, 4, 8));
        ColumnConstraints c1 = new ColumnConstraints(190);
        ColumnConstraints c2 = new ColumnConstraints(460);
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c1, c2);

        errorLabel.getStyleClass().add("field-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);

        VBox body = new VBox(10, titleLabel, errorLabel, grid);
        body.setPadding(new Insets(6, 10, 6, 10));
        root.getChildren().add(body);
        // Défilement du contenu : les longs formuliers restent accessibles,
        // les boutons Enregistrer/Annuler sont toujours visibles en bas
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(root);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        getDialogPane().setContent(scroll);

        ButtonType saveType = new ButtonType(Messages.get("common.save"), javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(Messages.get("common.cancel"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(cancelType, saveType);

        // Validation à la volée des champs obligatoires
        setOnShown(e -> grid.requestFocus());
    }

    /** Ajoute une ligne label + champ. */
    public Node addField(String labelKey, Node field) {
        Label lbl = new Label(Messages.get(labelKey));
        lbl.getStyleClass().add("field-label");
        lbl.setWrapText(true);
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
        row++;
        if (field instanceof TextField tf && lbl.getText().contains("*")) {
            requiredTextFields.add(tf);
        }
        return field;
    }

    public Node addField(String labelKey, Node field, boolean required) {
        Label lbl = new Label(Messages.get(labelKey) + (required ? " *" : ""));
        lbl.getStyleClass().add("field-label");
        lbl.setWrapText(true);
        if (required) {
            lbl.getStyleClass().add("required-mark");
        }
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
        row++;
        if (required && field instanceof TextField tf) {
            requiredTextFields.add(tf);
        }
        return field;
    }

    /** Ajoute un champ pleine largeur (description…). */
    public Node addWideField(String labelKey, Node field, boolean required) {
        Label lbl = new Label(Messages.get(labelKey) + (required ? " *" : ""));
        lbl.getStyleClass().add("field-label");
        if (required) {
            lbl.getStyleClass().add("required-mark");
        }
        VBox box = new VBox(4, lbl, field);
        box.setPadding(new Insets(6, 0, 6, 0));
        grid.add(box, 0, row, 2, 1);
        row++;
        return field;
    }

    public void addRaw(Node node) {
        grid.add(node, 0, row, 2, 1);
        row++;
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    public void clearError() {
        errorLabel.setVisible(false);
    }

    /** Exécute une action de soumission et convertit les exceptions métier en messages. */
    public void submit(Consumer<T> action) {
        clearError();
        try {
            action.accept(null);
        } catch (ValidationException ve) {
            showError(ve.getMessage());
        } catch (RuntimeException ex) {
            showError(Messages.get("msg.error_save"));
        }
    }

    public void setOnSave(Consumer<T> handler) {
        Node saveBtn = getDialogPane().lookupButton(
                getDialogPane().getButtonTypes().stream()
                        .filter(b -> b.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OK_DONE)
                        .findFirst().orElse(ButtonType.OK));
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            clearError();
            try {
                // contrôle des champs obligatoires
                for (TextField tf : requiredTextFields) {
                    if (tf.getText() == null || tf.getText().isBlank()) {
                        showError(Messages.get("validation.required"));
                        tf.requestFocus();
                        return;
                    }
                }
                handler.accept(null);
            } catch (ValidationException ve) {
                showError(ve.getMessage());
            } catch (RuntimeException ex) {
                showError(Messages.get("msg.error_save"));
            }
        });
    }

    // ---------- Fabriques de champs ----------

    public static TextField textField(String value) {
        TextField tf = new TextField(value == null ? "" : value);
        tf.setPromptText("");
        return tf;
    }

    public static TextArea textArea(String value, int rows) {
        TextArea ta = new TextArea(value == null ? "" : value);
        ta.setWrapText(true);
        ta.setPrefRowCount(rows);
        return ta;
    }

    public static <E> ComboBox<E> combo(List<E> items, E selected) {
        ComboBox<E> cb = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(items));
        cb.setValue(selected);
        cb.setMaxWidth(Double.MAX_VALUE);
        return cb;
    }

    public static DatePicker datePicker(LocalDate value) {
        DatePicker dp = new DatePicker(value);
        dp.setConverter(new FrDateConverter());
        dp.setPromptText("jj/mm/aaaa");
        return dp;
    }

    /** Bouton secondaire aligné. */
    public HBox footerRow(Node... nodes) {
        HBox box = new HBox(8, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Vérifie qu'une date n'est pas antérieure (message FR). */
    public static void checkOrder(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException(Messages.get("validation.date_order"));
        }
    }
}
