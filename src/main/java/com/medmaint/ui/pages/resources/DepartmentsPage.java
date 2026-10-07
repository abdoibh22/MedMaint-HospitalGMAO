package com.medmaint.ui.pages.resources;

import com.medmaint.domain.model.Department;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.security.Permission;
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
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Services hospitaliers : liste avec statistiques (équipements, demandes
 * ouvertes), CRUD complet.
 */
public class DepartmentsPage extends VBox {

    private final DataTable<Department> table = new DataTable<>(Messages.get("dept.empty"),
            Session.get().has(Permission.DEPARTMENT_MANAGE)
                    ? PageHeader.actionButton(Messages.get("dept.new"), true, () -> edit(null))
                    : null);

    private final TextField search = new TextField();
    private Map<Long, Long> equipmentCounts = new HashMap<>();
    private Map<Long, Long> openRequests = new HashMap<>();

    public DepartmentsPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("dept.title"));
        if (Session.get().has(Permission.DEPARTMENT_MANAGE)) {
            header.addAction(Messages.get("dept.new"), true, () -> edit(null));
        }
        getChildren().add(header);

        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(280);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());
        HBox filters = new HBox(10, new VBox(2, lbl(Messages.get("common.search")), search));
        filters.setPadding(new Insets(4, 0, 4, 0));
        getChildren().add(filters);

        table.addColumn(new DataTable.Col<>("code", Messages.get("dept.code"),
                Department::getCode, 90));
        table.addColumn(new DataTable.Col<>("name", Messages.get("dept.name"),
                Department::getName, 220));
        table.addColumn(new DataTable.Col<>("head", Messages.get("dept.head"),
                d -> Ui.orDash(d.getHeadName()), 190));
        table.addColumn(new DataTable.Col<>("location", Messages.get("dept.location"),
                d -> Ui.orDash(d.getLocation()), 200));
        table.addColumn(new DataTable.Col<>("phone", Messages.get("dept.phone"),
                d -> Ui.orDash(d.getPhone()), 140));
        table.addColumn(new DataTable.Col<>("email", Messages.get("dept.email"),
                d -> Ui.orDash(d.getEmail()), 220));
        table.addColumn(new DataTable.Col<>("equipment", Messages.get("dept.equipment_count"),
                d -> String.valueOf(equipmentCounts.getOrDefault(d.getId(), 0L)), 120));
        table.addColumn(new DataTable.Col<>("open", Messages.get("dept.open_requests"),
                d -> String.valueOf(openRequests.getOrDefault(d.getId(), 0L)), 130));

        if (Session.get().has(Permission.DEPARTMENT_MANAGE)) {
            table.addActionsColumn(List.of(
                    new DataTable.RowAction<>(Messages.get("common.edit"), "ghost", this::edit),
                    new DataTable.RowAction<>(Messages.get("common.delete"), "danger", d -> {
                        try {
                            AppContext.get().references().deleteDepartment(d.getId());
                            Toast.success(Messages.get("msg.deleted"));
                            table.refresh();
                        } catch (ValidationException ve) {
                            Toast.error(ve.getMessage());
                        } catch (RuntimeException ex) {
                            Toast.error(Messages.get("msg.error_save"));
                        }
                    })));
        }
        table.setPageSize(20);
        table.setLoader(this::load);
        getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.resetAndRefresh();
    }

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        return l;
    }

    private Page<Department> load(int page) {
        refreshStats();
        return AppContext.get().references().searchDepartments(search.getText(), page, table.pageSize());
    }

    private void refreshStats() {
        equipmentCounts = new HashMap<>();
        for (Object[] row : AppContext.get().references().departmentEquipmentCounts()) {
            equipmentCounts.put((Long) row[0], (Long) row[2]);
        }
        openRequests = new HashMap<>();
        try {
            for (Object[] row : Jdbc.query("""
                    SELECT department_id, COUNT(*) FROM intervention_requests
                    WHERE status NOT IN ('RESOLUE','CLOTUREE','REJETEE')
                    GROUP BY department_id""",
                    (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getLong(1), rs.getLong(2)})) {
                openRequests.put((Long) row[0], (Long) row[1]);
            }
        } catch (RuntimeException ignored) {
            // pas de demandes
        }
    }

    private void edit(Department d) {
        FormDialog<Object> dlg = new FormDialog<>(d == null
                ? Messages.get("dept.new") : Messages.get("dept.edit")) {
        };
        TextField code = FormDialog.textField(d == null ? null : d.getCode());
        TextField name = FormDialog.textField(d == null ? null : d.getName());
        TextField head = FormDialog.textField(d == null ? null : d.getHeadName());
        TextField location = FormDialog.textField(d == null ? null : d.getLocation());
        TextField phone = FormDialog.textField(d == null ? null : d.getPhone());
        TextField email = FormDialog.textField(d == null ? null : d.getEmail());

        dlg.addField("dept.code", code, true);
        dlg.addField("dept.name", name, true);
        dlg.addField("dept.head", head);
        dlg.addField("dept.location", location);
        dlg.addField("dept.phone", phone);
        dlg.addField("dept.email", email);

        dlg.setOnSave(b -> {
            Department dep = d != null ? d : new Department();
            dep.setCode(code.getText().trim());
            dep.setName(name.getText().trim());
            dep.setHeadName(emptyToNull(head));
            dep.setLocation(emptyToNull(location));
            dep.setPhone(emptyToNull(phone));
            dep.setEmail(emptyToNull(email));
            try {
                if (d == null) {
                    AppContext.get().references().createDepartment(dep);
                    Toast.success(Messages.get("msg.created"));
                } else {
                    AppContext.get().references().updateDepartment(dep);
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

    private static String emptyToNull(TextField f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }
}
