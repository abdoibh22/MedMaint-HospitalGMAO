package com.medmaint.ui.pages.maintenance;

import com.medmaint.domain.enums.MaintenanceFrequency;
import com.medmaint.domain.enums.PlanStatus;
import com.medmaint.domain.model.Checklist;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.MaintenancePlan;
import com.medmaint.domain.model.MaintenanceRecord;
import com.medmaint.domain.model.Technician;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.persistence.repo.PlanRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.ConfirmDialog;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance préventive : onglets Plans / Calendrier / En retard / À venir,
 * exécution de checklist (scénario C) et création de plans.
 */
public class PreventivePage extends VBox {

    private final DataTable<MaintenancePlan> table = new DataTable<>(Messages.get("pm.empty"),
            Session.get().has(Permission.PM_MANAGE)
                    ? PageHeader.actionButton(Messages.get("pm.new"), true, () -> new PlanFormDialog(null).showAndWait())
                    : null);

    private final TextField search = new TextField();
    private final ComboBox<String> frequencyFilter = new ComboBox<>();
    private final ComboBox<String> bucketFilter = new ComboBox<>();

    public PreventivePage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("pm.title"));
        if (Session.get().has(Permission.PM_MANAGE)) {
            header.addAction(Messages.get("pm.new"), true, () -> new PlanFormDialog(null).showAndWait());
        }
        getChildren().add(header);

        buildFilters();
        getChildren().add(filterBar());
        buildColumns();
        table.setPageSize(15);
        table.setLoader(this::load);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(tab(Messages.get("pm.tab.plans"), planListWithFilters()));
        tabs.getTabs().add(tab(Messages.get("pm.tab.overdue"), bucketList("EN_RETARD")));
        tabs.getTabs().add(tab(Messages.get("pm.tab.upcoming"), upcomingNode()));
        tabs.getTabs().add(tab(Messages.get("pm.tab.calendar"), calendarNode()));
        getChildren().add(tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        table.resetAndRefresh();
    }

    private Tab tab(String title, Node content) {
        Tab t = new Tab(title);
        VBox wrap = new VBox(content);
        wrap.setPadding(new Insets(12));
        VBox.setVgrow(content, Priority.ALWAYS);
        t.setContent(wrap);
        return t;
    }

    private Node planListWithFilters() {
        VBox box = new VBox(filterBar(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void buildFilters() {
        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(230);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());

        frequencyFilter.getItems().add(null);
        for (MaintenanceFrequency f : MaintenanceFrequency.values()) {
            frequencyFilter.getItems().add(f.name());
        }
        frequencyFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String c) {
                return c == null ? Messages.get("common.all_f") : MaintenanceFrequency.fromCode(c).label();
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        frequencyFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        bucketFilter.getItems().addAll(Messages.get("common.all_f"),
                "En retard", "Aujourd'hui", "Cette semaine", "À venir");
        bucketFilter.setValue(Messages.get("common.all_f"));
        bucketFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
    }

    private Node filterBar() {
        Button reset = new Button(Messages.get("common.reset"));
        reset.getStyleClass().add("ghost");
        reset.setOnAction(e -> {
            search.clear();
            frequencyFilter.setValue(null);
            bucketFilter.setValue(Messages.get("common.all_f"));
            table.resetAndRefresh();
        });
        HBox bar = new HBox(10,
                wrap(Messages.get("common.search"), search),
                wrap(Messages.get("pm.frequency"), frequencyFilter),
                wrap(Messages.get("pm.status"), bucketFilter),
                wrap(" ", reset));
        bar.setPadding(new Insets(4, 0, 4, 0));
        bar.setAlignment(javavfxPos());
        return bar;
    }

    private javafx.geometry.Pos javavfxPos() {
        return javafx.geometry.Pos.BOTTOM_LEFT;
    }

    private Node wrap(String label, Node field) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        return new VBox(2, l, field);
    }

    private void buildColumns() {
        table.addColumn(new DataTable.Col<>("equipment", Messages.get("pm.equipment"),
                p -> p.getEquipmentCode() + " — " + p.getEquipmentName(), 250));
        table.addColumn(new DataTable.Col<>("type", Messages.get("pm.type"),
                MaintenancePlan::getMaintenanceType, 170));
        table.addColumn(new DataTable.Col<>("frequency", Messages.get("pm.frequency"),
                p -> MaintenanceFrequency.fromCode(p.getFrequency()).label(), 120));
        table.addColumn(new DataTable.Col<>("last", Messages.get("pm.last_performed"),
                p -> Ui.date(p.getLastPerformed()), 130));
        table.addColumn(new DataTable.Col<>("next", Messages.get("pm.next_due"),
                p -> Ui.date(p.getNextDue()), 130));
        table.addColumn(new DataTable.Col<>("responsible", Messages.get("pm.responsible"),
                p -> Ui.orDash(p.getResponsibleName()), 150));
        table.addColumn(new DataTable.Col<>("status", Messages.get("pm.status"),
                p -> AppContext.get().preventive().statusOf(p).label(), 130));
        table.addColumn(new DataTable.Col<>("checklist", Messages.get("pm.checklist"),
                p -> Ui.orDash(p.getChecklistName()), 170));

        List<DataTable.RowAction<MaintenancePlan>> actions = new ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.view"), "ghost", p -> {
            showPlanDetail(p);
        }));
        if (Session.get().has(Permission.PM_EXECUTE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("pm.action.execute"), "primary",
                    this::execute));
        }
        if (Session.get().has(Permission.PM_MANAGE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("common.edit"), "ghost",
                    p -> new PlanFormDialog(p, null).showAndWait()));
            actions.add(new DataTable.RowAction<>(Messages.get("common.delete"), "danger", p -> {
                if (ConfirmDialog.confirm(Messages.get("msg.confirm_delete", p.getMaintenanceType()))) {
                    try {
                        AppContext.get().preventive().delete(p.getId());
                        Toast.success(Messages.get("msg.deleted"));
                        table.refresh();
                    } catch (RuntimeException ex) {
                        Toast.error(ex.getMessage());
                    }
                }
            }));
        }
        table.addActionsColumn(actions);
    }

    private Page<MaintenancePlan> load(int page) {
        String bucket = switch (bucketFilter.getValue() == null ? "" : bucketFilter.getValue()) {
            case "En retard" -> "EN_RETARD";
            case "Aujourd'hui" -> "AUJOURD_HUI";
            case "Cette semaine" -> "CETTE_SEMAINE";
            case "À venir" -> "A_VENIR";
            default -> null;
        };
        var f = new PlanRepo.Filter(search.getText(), frequencyFilter.getValue(), bucket,
                null, null, null, false);
        return AppContext.get().preventive().search(f, page, table.pageSize(),
                table.sortCol(), table.sortDir());
    }

    // ---------- Vue retard ----------

    private Node bucketList(String bucket) {
        DataTable<MaintenancePlan> t = new DataTable<>(Messages.get("pm.empty"), null);
        t.setPageSize(50);
        t.addColumn(new DataTable.Col<>("equipment", Messages.get("pm.equipment"),
                p -> p.getEquipmentCode() + " — " + p.getEquipmentName(), 280));
        t.addColumn(new DataTable.Col<>("type", Messages.get("pm.type"),
                MaintenancePlan::getMaintenanceType, 180));
        t.addColumn(new DataTable.Col<>("next", Messages.get("pm.next_due"),
                p -> Ui.date(p.getNextDue()), 130));
        t.addColumn(new DataTable.Col<>("status", Messages.get("pm.status"), p -> {
            LocalDate due = p.getNextDue();
            if (due == null) return "—";
            long days = java.time.temporal.ChronoUnit.DAYS.between(due, LocalDate.now());
            return days > 0 ? days + " j de retard" : "à échéance";
        }, 150));
        if (Session.get().has(Permission.PM_EXECUTE)) {
            t.addActionsColumn(List.of(new DataTable.RowAction<>(Messages.get("pm.action.execute"),
                    "primary", this::execute)));
        }
        t.setLoader(p -> AppContext.get().preventive().search(
                new PlanRepo.Filter(null, null, bucket, null, null, null, false),
                p, t.pageSize(), t.sortCol(), t.sortDir()));
        t.resetAndRefresh();
        return t;
    }

    // ---------- À venir ----------

    private Node upcomingNode() {
        VBox box = new VBox(8);
        List<MaintenancePlan> due30 = AppContext.get().preventive().dueWithin(30);
        if (due30.isEmpty()) {
            box.getChildren().add(Ui.emptyState(Messages.get("pm.empty"), null));
            return box;
        }
        LocalDate today = LocalDate.now();
        for (MaintenancePlan p : due30) {
            String when;
            String tone;
            if (p.getNextDue().isBefore(today)) {
                long d = java.time.temporal.ChronoUnit.DAYS.between(p.getNextDue(), today);
                when = d + " j de retard";
                tone = "danger";
            } else if (p.getNextDue().equals(today)) {
                when = Messages.get("common.today");
                tone = "warn";
            } else {
                long d = java.time.temporal.ChronoUnit.DAYS.between(today, p.getNextDue());
                when = "dans " + d + " j";
                tone = d <= 7 ? "info" : "muted";
            }
            HBox row = new HBox(10,
                    Ui.badge(when, tone),
                    Ui.h3(p.getEquipmentCode() + " — " + p.getEquipmentName()),
                    Ui.muted(p.getMaintenanceType() + " · "
                            + MaintenanceFrequency.fromCode(p.getFrequency()).label()));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            if (Session.get().has(Permission.PM_EXECUTE)) {
                Button run = new Button(Messages.get("pm.action.execute"));
                run.getStyleClass().add("primary");
                run.setOnAction(e -> execute(p));
                row.getChildren().add(run);
            }
            box.getChildren().add(row);
        }
        return box;
    }

    // ---------- Calendrier mensuel ----------

    private Node calendarNode() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        int daysInMonth = first.lengthOfMonth();
        String[] dayNames = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.getStyleClass().add("panel");

        for (int i = 0; i < 7; i++) {
            Label d = new Label(dayNames[i]);
            d.getStyleClass().add("section-label");
            d.setPrefWidth(118);
            d.setAlignment(javafx.geometry.Pos.CENTER);
            grid.add(d, i, 0);
        }

        // Plans du mois courant
        List<MaintenancePlan> monthPlans = AppContext.get().preventive().search(
                new PlanRepo.Filter(null, null, null, null, null, null, false),
                1, 500, "next", "ASC").items();

        int startCol = first.getDayOfWeek().getValue() - 1;
        int row = 1;
        int col = startCol;
        LocalDate today = LocalDate.now();

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = first.withDayOfMonth(day);
            VBox cell = new VBox(3);
            cell.setPrefWidth(118);
            cell.setMinHeight(92);
            cell.setPadding(new Insets(4));
            cell.getStyleClass().add("surface");

            Label dayLbl = new Label(String.valueOf(day));
            dayLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;"
                    + (date.equals(today) ? " -fx-text-fill: #b3261e;" : " -fx-text-fill: #52606d;"));
            cell.getChildren().add(dayLbl);

            for (MaintenancePlan p : monthPlans) {
                if (p.getNextDue() != null && p.getNextDue().equals(date)) {
                    Label chip = new Label(p.getEquipmentCode());
                    boolean late = date.isBefore(today);
                    chip.setStyle("-fx-font-size: 9.5px; -fx-padding: 1 4 1 4; -fx-background-radius: 3;"
                            + (late ? " -fx-background-color: #fbdcd9; -fx-text-fill: #7f1d1d;"
                            : " -fx-background-color: #ddeff7; -fx-text-fill: #0b4a63;"));
                    chip.setTooltip(new javafx.scene.control.Tooltip(p.getMaintenanceType()));
                    cell.getChildren().add(chip);
                }
            }
            grid.add(cell, col, row);
            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }
        return new VBox(6, Ui.muted(first.getMonth().name() + " " + first.getYear()), grid);
    }

    // ---------- Détail ----------

    private void showPlanDetail(MaintenancePlan p) {
        VBox box = new VBox(8);
        var g = new GridPane();
        g.setHgap(16);
        g.setVgap(6);
        int r = 0;
        r = kv(g, r, Messages.get("pm.equipment"), p.getEquipmentCode() + " — " + p.getEquipmentName());
        r = kv(g, r, Messages.get("pm.type"), p.getMaintenanceType());
        r = kv(g, r, Messages.get("pm.frequency"),
                MaintenanceFrequency.fromCode(p.getFrequency()).label());
        r = kv(g, r, Messages.get("pm.checklist"), p.getChecklistName());
        r = kv(g, r, Messages.get("pm.responsible"), p.getResponsibleName());
        r = kv(g, r, Messages.get("pm.last_performed"), Ui.date(p.getLastPerformed()));
        r = kv(g, r, Messages.get("pm.next_due"), Ui.date(p.getNextDue()));
        r = kv(g, r, Messages.get("pm.duration"),
                p.getEstimatedDurationMin() == null ? null : p.getEstimatedDurationMin() + " min");
        r = kv(g, r, Messages.get("pm.required_parts"), p.getRequiredParts());
        r = kv(g, r, Messages.get("pm.required_tools"), p.getRequiredTools());
        r = kv(g, r, Messages.get("pm.instructions"), p.getInstructions());
        box.getChildren().addAll(g);

        List<MaintenanceRecord> history = AppContext.get().preventive()
                .checklistRepo().withResults(
                        AppContext.get().preventive().checklistRepo().findRecordsByEquipment(
                                p.getEquipmentId(), 20));
        box.getChildren().add(Ui.sectionLabel("pm.list"));
        if (history.isEmpty()) {
            box.getChildren().add(Ui.muted("Aucune réalisation."));
        } else {
            for (MaintenanceRecord rec : history) {
                box.getChildren().add(new Label(Ui.dateTime(rec.getPerformedAt()) + " — "
                        + rec.getResult() + " · " + Ui.orDash(rec.getObservations())));
            }
        }

        javafx.scene.control.Dialog<Void> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle(p.getMaintenanceType());
        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getScene().getStylesheets().add(
                PreventivePage.class.getResource("/css/app.css").toExternalForm());
        dlg.getDialogPane().setPrefWidth(700);
        dlg.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dlg.showAndWait();
    }

    // ---------- Exécution (scénario C) ----------

    private void execute(MaintenancePlan p) {
        Checklist checklist = p.getChecklistId() == null ? null
                : AppContext.get().preventive().checklist(p.getChecklistId()).orElse(null);
        new ChecklistRunDialog(p, checklist, table::refresh).showAndWait();
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
