package com.medmaint.ui.pages.resources;

import com.medmaint.domain.model.Technician;
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Techniciens : profils, statistiques (assignées / terminées / ouvertes,
 * durée moyenne), compétences, vue de charge.
 */
public class TechniciansPage extends VBox {

    private final DataTable<Technician> table = new DataTable<>(Messages.get("tech.empty"),
            Session.get().has(Permission.TECHNICIAN_MANAGE)
                    ? PageHeader.actionButton(Messages.get("tech.new"), true, () -> edit(null))
                    : null);

    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();

    public TechniciansPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("tech.title"));
        if (Session.get().has(Permission.TECHNICIAN_MANAGE)) {
            header.addAction(Messages.get("tech.new"), true, () -> edit(null));
        }
        header.addAction(Messages.get("tech.workload"), false, this::showWorkload);
        getChildren().add(header);

        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(260);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());
        statusFilter.getItems().addAll(Messages.get("common.all_f"), "ACTIF", "EN_CONGE", "INACTIF");
        statusFilter.setValue(Messages.get("common.all_f"));
        statusFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        HBox filters = new HBox(10,
                new VBox(2, lbl(Messages.get("common.search")), search),
                new VBox(2, lbl(Messages.get("tech.status")), statusFilter));
        filters.setPadding(new Insets(4, 0, 4, 0));
        getChildren().add(filters);

        table.addColumn(new DataTable.Col<>("employeeNo", Messages.get("tech.employee_no"),
                Technician::getEmployeeNo, 110));
        table.addColumn(new DataTable.Col<>("name", Messages.get("tech.name"),
                Technician::getFullName, 200));
        table.addColumn(new DataTable.Col<>("department", Messages.get("tech.department"),
                t -> Ui.orDash(t.getDepartmentName()), 160));
        table.addColumn(new DataTable.Col<>("specialty", Messages.get("tech.specialty"),
                t -> Ui.orDash(t.getSpecialty()), 180));
        table.addColumn(new DataTable.Col<>("phone", Messages.get("tech.phone"),
                t -> Ui.orDash(t.getPhone()), 130));
        table.addColumn(new DataTable.Col<>("status", Messages.get("tech.status"),
                Technician::getStatus, 110));
        table.addColumn(new DataTable.Col<>("availability", Messages.get("tech.availability"),
                Technician::getAvailability, 120));
        table.addColumn(new DataTable.Col<>("open", Messages.get("tech.stat.open"),
                t -> String.valueOf(t.getOpenCount()), 110));
        table.addColumn(new DataTable.Col<>("done", Messages.get("tech.stat.completed"),
                t -> String.valueOf(t.getCompletedCount()), 110));
        table.addColumn(new DataTable.Col<>("avg", Messages.get("tech.stat.avg"),
                t -> Ui.decimal(t.getAvgDurationHours()) + " h", 120));

        List<DataTable.RowAction<Technician>> actions = new ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.view"), "ghost", this::profile));
        if (Session.get().has(Permission.TECHNICIAN_MANAGE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("common.edit"), "ghost", t -> edit(t)));
        }
        table.addActionsColumn(actions);
        table.setPageSize(15);
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

    private Page<Technician> load(int page) {
        String status = statusFilter.getValue();
        if (status != null && status.equals(Messages.get("common.all_f"))) {
            status = null;
        }
        return AppContext.get().technicians().search(search.getText(), status, null,
                page, table.pageSize(), table.sortCol(), table.sortDir());
    }

    private void profile(Technician t) {
        Technician full = AppContext.get().technicians().profile(t.getId());

        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(6);
        int r = 0;
        r = kv(g, r, Messages.get("tech.employee_no"), full.getEmployeeNo());
        r = kv(g, r, Messages.get("tech.name"), full.getFullName());
        r = kv(g, r, Messages.get("tech.department"), full.getDepartmentName());
        r = kv(g, r, Messages.get("tech.specialty"), full.getSpecialty());
        r = kv(g, r, Messages.get("tech.phone"), full.getPhone());
        r = kv(g, r, Messages.get("tech.email"), full.getEmail());
        r = kv(g, r, Messages.get("tech.certifications"), full.getCertifications());
        r = kv(g, r, Messages.get("tech.status"), full.getStatus());
        r = kv(g, r, Messages.get("tech.availability"), full.getAvailability());
        r = kv(g, r, Messages.get("tech.skills"), full.getSkillsCsv());

        GridPane stats = new GridPane();
        stats.setHgap(24);
        stats.setVgap(8);
        stats.add(statBox(String.valueOf(full.getAssignedCount()), Messages.get("tech.stat.assigned")), 0, 0);
        stats.add(statBox(String.valueOf(full.getCompletedCount()), Messages.get("tech.stat.completed")), 1, 0);
        stats.add(statBox(String.valueOf(full.getOpenCount()), Messages.get("tech.stat.open")), 2, 0);
        stats.add(statBox(Ui.decimal(full.getAvgDurationHours()) + " h", Messages.get("tech.stat.avg")), 3, 0);

        VBox content = new VBox(12,
                Ui.sectionLabel("profile.account"), g,
                Ui.sectionLabel("tech.workload"), stats);
        content.setPadding(new Insets(14));
        content.setPrefSize(760, 480);

        javafx.scene.control.Dialog<Void> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle(full.getFullName());
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getScene().getStylesheets().add(
                TechniciansPage.class.getResource("/css/app.css").toExternalForm());
        dlg.getDialogPane().setPrefWidth(780);
        dlg.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private void showWorkload() {
        var data = AppContext.get().technicians().workload();
        if (data.isEmpty()) {
            Ui.emptyState(Messages.get("common.none"), null);
            return;
        }
        VBox list = new VBox(8);
        long max = data.stream().mapToLong(r -> (Long) r[1]).max().orElse(1);
        for (Object[] row : data) {
            Label name = new Label((String) row[0]);
            name.setPrefWidth(220);
            Label open = Ui.badge(row[1] + " ouvertes", ((Long) row[1]) > 3 ? "warn" : "info");
            Label total = Ui.muted(row[2] + " au total");
            javafx.scene.layout.Region bar = new javafx.scene.layout.Region();
            double w = max == 0 ? 0 : ((Long) row[1]) * 240.0 / max;
            bar.setPrefWidth(Math.max(6, w));
            bar.setPrefHeight(10);
            bar.setStyle("-fx-background-color: #1b8a9c; -fx-background-radius: 3;");
            HBox rowBox = new HBox(10, name, open, bar, total);
            rowBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            list.getChildren().add(rowBox);
        }
        javafx.scene.control.Dialog<Void> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle(Messages.get("tech.workload"));
        VBox content = new VBox(list);
        content.setPadding(new Insets(16));
        content.setPrefSize(720, 420);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getScene().getStylesheets().add(
                TechniciansPage.class.getResource("/css/app.css").toExternalForm());
        dlg.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private Node statBox(String value, String label) {
        VBox box = new VBox(2);
        box.getStyleClass().add("kpi-card");
        Label v = new Label(value);
        v.getStyleClass().add("kpi-value");
        Label l = new Label(label);
        l.getStyleClass().add("kpi-label");
        box.getChildren().addAll(v, l);
        return box;
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

    private void edit(Technician t) {
        FormDialog<Object> dlg = new FormDialog<>(t == null
                ? Messages.get("tech.new") : Messages.get("tech.edit")) {
        };
        TextField employeeNo = FormDialog.textField(t == null ? null : t.getEmployeeNo());
        TextField fullName = FormDialog.textField(t == null ? null : t.getFullName());
        ComboBox<com.medmaint.domain.model.Department> dept = FormDialog.combo(
                AppContext.get().references().allDepartments(),
                t == null || t.getDepartmentId() == null ? null
                        : AppContext.get().references().getDepartment(t.getDepartmentId()).orElse(null));
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
        TextField specialty = FormDialog.textField(t == null ? null : t.getSpecialty());
        TextField phone = FormDialog.textField(t == null ? null : t.getPhone());
        TextField email = FormDialog.textField(t == null ? null : t.getEmail());
        TextArea certifications = FormDialog.textArea(t == null ? null : t.getCertifications(), 2);
        ComboBox<String> statusBox = FormDialog.combo(
                List.of("ACTIF", "EN_CONGE", "INACTIF"), t == null ? "ACTIF" : t.getStatus());
        ComboBox<String> availability = FormDialog.combo(
                List.of("DISPONIBLE", "OCCUPE", "ABSENT"),
                t == null ? "DISPONIBLE" : t.getAvailability());
        TextField skills = FormDialog.textField(t == null ? "" : Ui.orDash(t.getSkillsCsv()));

        dlg.addField("tech.employee_no", employeeNo, true);
        dlg.addField("tech.name", fullName, true);
        dlg.addField("tech.department", dept);
        dlg.addField("tech.specialty", specialty);
        dlg.addField("tech.phone", phone);
        dlg.addField("tech.email", email);
        dlg.addWideField("tech.certifications", certifications, false);
        dlg.addField("tech.status", statusBox);
        dlg.addField("tech.availability", availability);
        dlg.addField("tech.skills", skills);

        dlg.setOnSave(b -> {
            Technician tech = t != null ? t : new Technician();
            tech.setEmployeeNo(employeeNo.getText().trim());
            tech.setFullName(fullName.getText().trim());
            tech.setDepartmentId(dept.getValue() == null ? null : dept.getValue().getId());
            tech.setSpecialty(emptyToNull(specialty));
            tech.setPhone(emptyToNull(phone));
            tech.setEmail(emptyToNull(email));
            tech.setCertifications(emptyToNull(certifications));
            tech.setStatus(statusBox.getValue());
            tech.setAvailability(availability.getValue());
            List<String> skillList = skills.getText() == null ? List.of()
                    : List.of(skills.getText().split(","));
            try {
                if (t == null) {
                    AppContext.get().technicians().create(tech, skillList);
                    Toast.success(Messages.get("msg.created"));
                } else {
                    AppContext.get().technicians().update(tech, skillList);
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

    private static String emptyToNull(TextArea f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }
}
