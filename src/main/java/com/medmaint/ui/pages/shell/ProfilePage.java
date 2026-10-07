package com.medmaint.ui.pages.shell;

import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.FormDialog;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.pages.PageHeader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Mon profil : informations du compte, changement de mot de passe,
 * préférences de notification.
 */
public class ProfilePage extends VBox {

    public ProfilePage() {
        setSpacing(14);
        setPadding(new Insets(0, 0, 8, 0));

        Session s = Session.get();
        PageHeader header = new PageHeader(Messages.get("profile.title"));
        getChildren().add(header);

        // --- Compte ---
        GridPane g = new GridPane();
        g.setHgap(18);
        g.setVgap(8);
        int r = 0;
        r = kv(g, r, Messages.get("profile.account"), s.fullName());
        r = kv(g, r, "Identifiant", s.username());
        r = kv(g, r, Messages.get("profile.role"), s.roleLabel());
        r = kv(g, r, Messages.get("profile.department"), Ui.orDash(s.departmentName()));

        var user = AppContext.get().auth().findById(s.userId()).orElse(null);
        if (user != null) {
            r = kv(g, r, "E-mail", user.getEmail());
            r = kv(g, r, Messages.get("tech.phone"), Ui.orDash(user.getPhone()));
            r = kv(g, r, Messages.get("users.last_login"), Ui.dateTime(user.getLastLogin()));
            r = kv(g, r, Messages.get("users.status"), user.getStatus());
            if (user.isDemo()) {
                r = kv(g, r, "Type", Messages.get("users.demo_badge"));
            }
        }

        VBox account = Ui.panel(Ui.h3(Messages.get("profile.account")), g);

        // --- Mot de passe ---
        HBox pwd = new HBox(8);
        Button changePwd = new Button(Messages.get("profile.change_password"));
        changePwd.getStyleClass().add("primary");
        changePwd.setOnAction(e -> changePassword());
        pwd.getChildren().add(changePwd);
        VBox pwdPanel = Ui.panel(Ui.h3(Messages.get("profile.change_password")),
                new VBox(6, Ui.muted(Messages.get("users.password_policy")), pwd));

        // --- Préférences de notification ---
        javafx.scene.control.CheckBox prefs = new javafx.scene.control.CheckBox(
                "Recevoir les alertes de maintenance, de stock et de contrats dans l'application");
        String stored = user == null ? "ALL" : user.getNotificationPrefs();
        prefs.setSelected(stored == null || stored.isBlank() || stored.equals("ALL"));
        Button savePrefs = new Button(Messages.get("common.save"));
        savePrefs.getStyleClass().add("ghost");
        savePrefs.setOnAction(e -> {
            if (user != null) {
                user.setNotificationPrefs(prefs.isSelected() ? "ALL" : "NONE");
                AppContext.get().auth().update(user);
                Toast.success(Messages.get("profile.saved"));
            }
        });
        VBox notif = Ui.panel(Ui.h3(Messages.get("profile.notif_prefs")),
                new VBox(8, prefs, savePrefs));

        getChildren().addAll(account, pwdPanel, notif);
    }

    private void changePassword() {
        FormDialog<Object> dlg = new FormDialog<>(Messages.get("profile.change_password")) {
        };
        PasswordField current = new PasswordField();
        current.setPromptText(Messages.get("users.password_current"));
        PasswordField next = new PasswordField();
        next.setPromptText(Messages.get("users.password_new"));
        PasswordField confirm = new PasswordField();
        confirm.setPromptText(Messages.get("common.confirm"));

        dlg.addField("users.password_current", current, true);
        dlg.addField("users.password_new", next, true);
        dlg.addField("common.confirm", confirm, true);
        dlg.setOnSave(b -> {
            if (next.getText() == null || !next.getText().equals(confirm.getText())) {
                dlg.showError("Les deux nouveaux mots de passe ne correspondent pas.");
                return;
            }
            try {
                AppContext.get().auth().changePassword(Session.get().userId(),
                        current.getText(), next.getText());
                Toast.success(Messages.get("msg.saved"));
                dlg.getDialogPane().getScene().getWindow().hide();
            } catch (com.medmaint.security.ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private int kv(GridPane g, int row, String k, String v) {
        Label l = new Label(k);
        l.getStyleClass().add("field-label");
        Label val = new Label(Ui.orDash(v));
        val.setWrapText(true);
        g.add(l, 0, row);
        g.add(val, 1, row);
        return row + 1;
    }
}
