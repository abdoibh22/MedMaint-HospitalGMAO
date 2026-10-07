package com.medmaint.ui.components;

import com.medmaint.ui.i18n.Messages;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Dialogues de confirmation (actions destructives) et d'information. */
public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    /** @return true si l'utilisateur confirme. */
    public static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(Messages.get("common.confirm"));
        alert.setHeaderText(null);
        Label l = new Label(message);
        l.setWrapText(true);
        l.setMaxWidth(460);
        VBox content = new VBox(l);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().getScene().getStylesheets().add(
                ConfirmDialog.class.getResource("/css/app.css").toExternalForm());
        var result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(Messages.get("app.name"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getScene().getStylesheets().add(
                ConfirmDialog.class.getResource("/css/app.css").toExternalForm());
        alert.showAndWait();
    }

    public static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(Messages.get("app.name"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getScene().getStylesheets().add(
                ConfirmDialog.class.getResource("/css/app.css").toExternalForm());
        alert.showAndWait();
    }

    /** Saisie simple (motif de rejet, note…). @return texte saisi ou null. */
    public static String prompt(String title, String labelKey, String initial) {
        javafx.scene.control.TextInputDialog dlg =
                new javafx.scene.control.TextInputDialog(initial == null ? "" : initial);
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.setContentText(Messages.get(labelKey));
        dlg.getDialogPane().getScene().getStylesheets().add(
                ConfirmDialog.class.getResource("/css/app.css").toExternalForm());
        return dlg.showAndWait().orElse(null);
    }
}
