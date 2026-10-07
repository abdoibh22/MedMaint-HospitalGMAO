package com.medmaint.ui;

import com.medmaint.security.Session;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.navigation.Router;
import com.medmaint.security.UnauthorizedException;
import com.medmaint.service.AppContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.List;

/**
 * Écran de connexion : logo, MedMaint, identifiant, mot de passe (afficher/
 * masquer), se souvenir de moi, gestion d'erreurs et comptes de démo.
 */
public final class LoginPage {

    /** Comptes de démonstration (mots de passe documentés dans le README). */
    public static final List<String[]> DEMO_ACCOUNTS = List.of(
            new String[]{"admin@medmaint.demo", "Administrateur"},
            new String[]{"manager@medmaint.demo", "Responsable Maintenance"},
            new String[]{"engineer@medmaint.demo", "Ingénieur Biomédical"},
            new String[]{"technician@medmaint.demo", "Technicien"},
            new String[]{"service@medmaint.demo", "Responsable de Service"});

    public static final String DEMO_PASSWORD = "demo2026";

    private LoginPage() {
    }

    public static void show(Stage stage, Router router) {
        TextField username = new TextField();
        username.setPromptText(Messages.get("login.username"));
        username.getStyleClass().add("search-field");
        username.setPrefWidth(320);

        PasswordField password = new PasswordField();
        password.setPromptText(Messages.get("login.password"));
        password.getStyleClass().add("search-field");
        password.setPrefWidth(320);

        TextField plainPassword = new TextField();
        plainPassword.setPromptText(Messages.get("login.password"));
        plainPassword.getStyleClass().add("search-field");
        plainPassword.setPrefWidth(320);
        password.textProperty().bindBidirectional(plainPassword.textProperty());

        CheckBox showPwd = new CheckBox(Messages.get("login.show_password"));
        showPwd.setWrapText(true);
        showPwd.setOnAction(e -> {
            boolean show = showPwd.isSelected();
            password.setVisible(!show);
            password.setManaged(!show);
            plainPassword.setVisible(show);
            plainPassword.setManaged(show);
            (show ? plainPassword : password).requestFocus();
        });
        plainPassword.setVisible(false);
        plainPassword.setManaged(false);

        CheckBox remember = new CheckBox(Messages.get("login.remember"));
        remember.setSelected(true);

        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        error.setMaxWidth(320);
        error.setVisible(false);

        Button submit = new Button(Messages.get("login.submit"));
        submit.getStyleClass().add("primary");
        submit.setPrefWidth(320);
        submit.setDefaultButton(true);
        submit.setPrefHeight(38);

        Runnable doLogin = () -> {
            error.setVisible(false);
            String user = username.getText();
            String pwd = showPwd.isSelected() ? plainPassword.getText() : password.getText();
            try {
                Session s = AppContext.get().auth().login(user, pwd);
                Toast.success(Messages.get("login.welcome") + ", " + s.fullName());
                MainShell.boot(stage, router, homeFor(s));
            } catch (UnauthorizedException ue) {
                error.setText(ue.getMessage());
                error.setVisible(true);
                password.clear();
                plainPassword.clear();
            } catch (RuntimeException ex) {
                error.setText(Messages.get("msg.error_load"));
                error.setVisible(true);
            }
        };
        submit.setOnAction(e -> doLogin.run());
        password.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) doLogin.run();
        });
        plainPassword.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) doLogin.run();
        });

        // Raccourcis de comptes démo
        VBox demoBox = new VBox(6);
        Label demoTitle = new Label(Messages.get("login.demo_title"));
        demoTitle.getStyleClass().add("section-label");
        Label demoHint = new Label(Messages.get("login.demo_hint"));
        demoHint.getStyleClass().add("dim");
        demoHint.setWrapText(true);
        demoBox.getChildren().addAll(demoTitle, demoHint);
        for (String[] acc : DEMO_ACCOUNTS) {
            Button b = new Button(acc[0] + "  ·  " + acc[1]);
            b.getStyleClass().add("ghost");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setOnAction(e -> {
                username.setText(acc[0]);
                password.setText(DEMO_PASSWORD);
                plainPassword.setText(DEMO_PASSWORD);
                password.requestFocus();
            });
            demoBox.getChildren().add(b);
        }

        VBox form = new VBox(10);
        form.setPadding(new Insets(28, 30, 28, 30));
        form.setAlignment(Pos.TOP_CENTER);
        form.setMaxWidth(420);

        VBox brand = new VBox(4);
        brand.setAlignment(Pos.CENTER);
        Label logo = new Label("+");
        logo.setStyle("-fx-font-size: 44px; -fx-font-weight: bold; -fx-text-fill: #0f4c5c;");
        logo.setEffect(new DropShadow(10, Color.rgb(15, 76, 92, 0.25)));
        Label app = new Label(Messages.get("app.name"));
        app.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #102027;");
        Label sub = new Label(Messages.get("app.subtitle"));
        sub.getStyleClass().add("muted");
        Label welcome = new Label(Messages.get("login.subtitle_signin"));
        welcome.getStyleClass().add("dim");
        brand.getChildren().addAll(logo, app, sub, welcome);

        form.getChildren().addAll(
                brand,
                new Region() {{ setPrefHeight(8); }},
                username, password, plainPassword,
                new HBox(16, showPwd, remember),
                error,
                submit,
                new Region() {{ setPrefHeight(6); }},
                demoBox);

        VBox card = new VBox(form);
        card.getStyleClass().add("panel");
        card.setEffect(new DropShadow(24, Color.rgb(16, 32, 39, 0.12)));
        card.setMaxWidth(460);

        VBox outer = new VBox(card);
        outer.setAlignment(Pos.CENTER);
        outer.getStyleClass().add("app-bg");
        outer.setPadding(new Insets(30));
        VBox.setVgrow(card, Priority.ALWAYS);

        Scene scene = new Scene(outer, 1400, 900);
        scene.getStylesheets().add(LoginPage.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle(Messages.get("app.name") + " — " + Messages.get("login.title"));
        stage.show();
        username.requestFocus();
    }

    /** Page d'accueil selon le rôle (le technicien voit son travail en premier). */
    public static String homeFor(Session s) {
        return switch (s.role()) {
            case TECHNICIAN -> "workorders";
            default -> "dashboard";
        };
    }
}
