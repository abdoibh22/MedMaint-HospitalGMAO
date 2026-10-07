package com.medmaint;

import com.medmaint.persistence.Db;
import com.medmaint.persistence.SchemaMigrator;
import com.medmaint.seed.DemoDataSeeder;
import com.medmaint.service.AppContext;
import com.medmaint.ui.LoginPage;
import com.medmaint.ui.MainShell;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.navigation.Router;
import com.medmaint.ui.pages.UiKit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Point d'entrée MedMaint — GMAO hospitalier.
 * Séquence : migration du schéma → seed des données de démonstration →
 * connexion → coquille applicative.
 */
public class MedMaintApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MedMaintApp.class);

    static {
        Locale.setDefault(Locale.FRENCH);
        Messages.setLocale(Locale.FRENCH);
    }

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("Exception non interceptée dans {}", t.getName(), e);
        });

        // Écran de démarrage pendant la migration + seed
        Label title = new Label(Messages.get("app.name"));
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #0f4c5c;");
        Label sub = new Label(Messages.get("app.tagline"));
        sub.setStyle("-fx-text-fill: #616e7c; -fx-font-size: 13px;");
        sub.setWrapText(true);
        sub.setMaxWidth(420);
        sub.setAlignment(Pos.CENTER);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(36, 36);
        VBox boot = new VBox(14, title, sub, spinner);
        boot.setAlignment(Pos.CENTER);
        boot.setStyle("-fx-background-color: #f4f6f8;");
        boot.setPrefSize(680, 420);

        Scene scene = new Scene(boot);
        stage.setScene(scene);
        stage.setTitle(Messages.get("app.name"));
        stage.show();

        Thread bootstrap = new Thread(() -> {
            try {
                Db.get();
                SchemaMigrator.migrate();
                boolean seeded = DemoDataSeeder.runIfNeeded();
                AppContext.get().bootstrap();
                log.info("Démarrage terminé (données de démo : {}).", seeded ? "oui" : "déjà présentes");
                Platform.runLater(() -> {
                    Router router = UiKit.buildRouter();
                    LoginPage.show(stage, router);
                });
            } catch (Throwable t) {
                log.error("Échec du démarrage", t);
                Platform.runLater(() -> {
                    boot.getChildren().setAll(new Label(
                            "Échec du démarrage : " + t.getMessage()
                                    + "\nConsultez logs/medmaint.log pour le détail."));
                });
            }
        }, "medmaint-bootstrap");
        bootstrap.setDaemon(true);
        bootstrap.start();
    }

    @Override
    public void stop() {
        try {
            Db.close();
        } catch (RuntimeException ignored) {
            // fermeture propre
        }
        log.info("Application arrêtée.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
