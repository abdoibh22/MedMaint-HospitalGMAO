package com.medmaint.ui.pages.admin;

import com.medmaint.domain.model.User;
import com.medmaint.persistence.Page;
import com.medmaint.security.Permission;
import com.medmaint.security.Role;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.DataTable;
import com.medmaint.ui.components.FormDialog;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.pages.PageHeader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Utilisateurs : comptes, rôles, activation/suspension, création,
 * réinitialisation de mot de passe. Réservé aux administrateurs (permission).
 */
public class UsersPage extends VBox {

    private final DataTable<User> table = new DataTable<>(Messages.get("users.empty"),
            PageHeader.actionButton(Messages.get("users.new"), true, () -> edit(null)));

    private final TextField search = new TextField();
    private final ComboBox<String> roleFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();

    public UsersPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("users.title"));
        header.addAction(Messages.get("users.new"), true, () -> edit(null));
        getChildren().add(header);

        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(240);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());

        roleFilter.getItems().add(null);
        for (Role r : Role.values()) {
            roleFilter.getItems().add(r.name());
        }
        roleFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String c) {
                return c == null ? Messages.get("common.all_f") : Role.fromCode(c).label();
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        roleFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        statusFilter.getItems().addAll(Messages.get("common.all_f"), "ACTIF", "SUSPENDU");
        statusFilter.setValue(Messages.get("common.all_f"));
        statusFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        HBox filters = new HBox(10,
                wrap(Messages.get("common.search"), search),
                wrap(Messages.get("users.role"), roleFilter),
                wrap(Messages.get("users.status"), statusFilter));
        filters.setPadding(new Insets(4, 0, 4, 0));
        getChildren().add(filters);

        table.addColumn(new DataTable.Col<>("name", Messages.get("users.full_name"),
                User::getFullName, 220));
        table.addColumn(new DataTable.Col<>("username", Messages.get("users.username"),
                User::getUsername, 170));
        table.addColumn(new DataTable.Col<>("email", Messages.get("users.email"),
                User::getEmail, 240));
        table.addColumn(new DataTable.Col<>("role", Messages.get("users.role"),
                u -> u.roleEnum().label(), 190));
        table.addColumn(new DataTable.Col<>("department", Messages.get("users.department"),
                u -> Ui.orDash(u.getDepartmentName()), 170));
        table.addColumn(new DataTable.Col<>("status", Messages.get("users.status"),
                u -> (u.isActive() ? "Actif" : "Suspendu")
                        + (u.isDemo() ? "  ·  démo" : ""), 130));
        table.addColumn(new DataTable.Col<>("lastLogin", Messages.get("users.last_login"),
                u -> Ui.dateTime(u.getLastLogin()), 150));

        table.addActionsColumn(List.of(
                new DataTable.RowAction<>(Messages.get("common.edit"), "ghost", this::edit),
                new DataTable.RowAction<>(Messages.get("users.reset_password"), "ghost", u -> resetPwd(u)),
                new DataTable.RowAction<>(Messages.get("common.delete"), "danger", u -> {
                    try {
                        AppContext.get().auth().delete(u.getId());
                        Toast.success(Messages.get("msg.deleted"));
                        table.refresh();
                    } catch (ValidationException ve) {
                        Toast.error(ve.getMessage());
                    } catch (RuntimeException ex) {
                        Toast.error(Messages.get("msg.error_save"));
                    }
                })));

        table.setPageSize(15);
        table.setLoader(this::load);
        getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.resetAndRefresh();
    }

    private VBox wrap(String label, Node field) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        return new VBox(2, l, field);
    }

    private Page<User> load(int page) {
        String status = statusFilter.getValue();
        if (status != null && status.equals(Messages.get("common.all_f"))) {
            status = null;
        }
        return AppContext.get().auth().repo().search(search.getText(), roleFilter.getValue(),
                status, null, page, table.pageSize(), table.sortCol(), table.sortDir());
    }

    private void edit(User u) {
        FormDialog<Object> dlg = new FormDialog<>(u == null
                ? Messages.get("users.new") : Messages.get("users.edit")) {
        };
        TextField fullName = FormDialog.textField(u == null ? null : u.getFullName());
        TextField username = FormDialog.textField(u == null ? null : u.getUsername());
        TextField email = FormDialog.textField(u == null ? null : u.getEmail());
        ComboBox<Role> role = FormDialog.combo(List.of(Role.values()),
                u == null ? Role.REQUESTER : u.roleEnum());
        role.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Role r) {
                return r == null ? "" : r.label();
            }

            @Override
            public Role fromString(String s) {
                return null;
            }
        });
        ComboBox<com.medmaint.domain.model.Department> dept = FormDialog.combo(
                AppContext.get().references().allDepartments(),
                u == null || u.getDepartmentId() == null ? null
                        : AppContext.get().references().getDepartment(u.getDepartmentId()).orElse(null));
        dept.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(com.medmaint.domain.model.Department d) {
                return d == null ? Messages.get("common.none") : d.getName();
            }

            @Override
            public com.medmaint.domain.model.Department fromString(String s) {
                return null;
            }
        });
        TextField phone = FormDialog.textField(u == null ? null : u.getPhone());
        ComboBox<String> status = FormDialog.combo(List.of("ACTIF", "SUSPENDU"),
                u == null || u.getStatus() == null ? "ACTIF" : u.getStatus());
        TextField password = FormDialog.textField(null);

        dlg.addField("users.full_name", fullName, true);
        dlg.addField("users.username", username, true);
        dlg.addField("users.email", email, true);
        dlg.addField("users.role", role, true);
        dlg.addField("users.department", dept);
        dlg.addField("users.phone", phone);
        dlg.addField("users.status", status);
        if (u == null) {
            dlg.addField("users.password", password, true);
        } else {
            Label hint = Ui.muted(Messages.get("users.password_policy"));
            dlg.addRaw(hint);
        }

        dlg.setOnSave(b -> {
            User user = u != null ? u : new User();
            user.setFullName(fullName.getText().trim());
            user.setUsername(username.getText().trim());
            user.setEmail(email.getText().trim());
            user.setRole(role.getValue().name());
            user.setDepartmentId(dept.getValue() == null ? null : dept.getValue().getId());
            user.setPhone(emptyToNull(phone));
            user.setStatus(status.getValue());
            try {
                if (u == null) {
                    AppContext.get().auth().create(user, password.getText());
                    Toast.success(Messages.get("msg.created"));
                } else {
                    AppContext.get().auth().update(user);
                    Toast.success(Messages.get("msg.saved"));
                }
                dlg.getDialogPane().getScene().getWindow().hide();
                table.refresh();
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private void resetPwd(User u) {
        FormDialog<Object> dlg = new FormDialog<>(Messages.get("users.reset_password")) {
        };
        TextField next = FormDialog.textField(null);
        dlg.addField("users.password_new", next, true);
        dlg.addRaw(Ui.muted(Messages.get("users.password_policy")));
        dlg.setOnSave(b -> {
            try {
                AppContext.get().auth().resetPassword(u.getId(), next.getText());
                Toast.success(Messages.get("msg.saved"));
                dlg.getDialogPane().getScene().getWindow().hide();
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private static String emptyToNull(TextField f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }
}
