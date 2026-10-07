package com.medmaint.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Toasts de feedback (succès / erreur / avertissement).
 * Fenêtre non bloquante, auto-disparition après 3 s.
 */
public final class Toast {

    private Toast() {
    }

    public static void success(String message) {
        show(message, "toast-success");
    }

    public static void error(String message) {
        show(message, "toast-error");
    }

    public static void warning(String message) {
        show(message, "toast-warning");
    }

    public static void info(String message) {
        show(message, "");
    }

    private static void show(String message, String styleClass) {
        try {
            Stage stage = new Stage();
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setAlwaysOnTop(true);

            Label label = new Label(message);
            label.setWrapText(true);
            label.setMaxWidth(420);

            VBox box = new VBox(label);
            box.getStyleClass().add("toast");
            if (!styleClass.isEmpty()) {
                box.getStyleClass().add(styleClass);
            }
            StackPane root = new StackPane(box);
            root.setStyle("-fx-background-color: transparent; -fx-padding: 8;");

            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    Toast.class.getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
            stage.sizeToScene();

            // En haut à droite de l'écran
            javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setX(screen.getMaxX() - stage.getWidth() - 24);
            stage.setY(screen.getMinY() + 24);

            stage.show();

            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(300), root);
                fade.setFromValue(1);
                fade.setToValue(0);
                fade.setOnFinished(ev -> stage.close());
                fade.play();
            });
            pause.play();
        } catch (RuntimeException ignored) {
            // Les toasts ne doivent jamais faire échouer l'application.
            System.err.println("[toast] " + message);
        }
    }
}
