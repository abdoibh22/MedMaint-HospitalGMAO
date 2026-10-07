package com.medmaint.ui.pages.reports;

import com.medmaint.domain.model.Department;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.EquipmentCategory;
import com.medmaint.domain.model.Technician;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.service.ReportService;
import com.medmaint.ui.components.ChartFactory;
import com.medmaint.ui.components.FormDialog;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.pages.PageHeader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rapports & Analytique : 11 rapports filtrables avec export CSV/PDF,
 * plus des métriques calculées (MTBF, MTTR, disponibilité, conformité)
 * et graphiques réactifs.
 */
public class ReportsPage extends VBox {

    private final ComboBox<String> reportPicker = new ComboBox<>();
    private final DatePicker from = FormDialog.datePicker(LocalDate.now().minusMonths(6));
    private final DatePicker to = FormDialog.datePicker(LocalDate.now());
    private final ComboBox<Department> department = new ComboBox<>();
    private final ComboBox<EquipmentCategory> category = new ComboBox<>();
    private final ComboBox<Equipment> equipment = new ComboBox<>();
    private final ComboBox<Technician> technician = new ComboBox<>();

    private final TableView<Row> resultTable = new TableView<>();
    private final Label rowInfo = Ui.muted("");

    private ReportService.ReportData current;

    /** Ligne affichée par le tableau de résultats. */
    public static class Row {
        private final List<String> cells;

        public Row(List<String> cells) {
            this.cells = cells;
        }

        public String getCell(int i) {
            return i < cells.size() ? cells.get(i) : "";
        }

        public List<String> cells() {
            return cells;
        }
    }

    public ReportsPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("reports.title"));
        getChildren().add(header);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab(Messages.get("reports.tab.list"), reportsTab()));
        tabs.getTabs().add(new Tab(Messages.get("reports.tab.analytics"), analyticsTab()));
        getChildren().add(tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
    }

    // ---------- Onglet rapports ----------

    private Node reportsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(4));

        reportPicker.getItems().setAll(ReportService.reportNames());
        reportPicker.setValue(ReportService.reportNames().get(0));
        reportPicker.setMaxWidth(360);
        reportPicker.valueProperty().addListener((o, a, b) -> runReport());

        department.getItems().add(null);
        department.getItems().addAll(AppContext.get().references().allDepartments());
        department.setConverter(strDept(d -> d == null ? Messages.get("common.all") : d.getName()));

        category.getItems().add(null);
        category.getItems().addAll(AppContext.get().references().allCategories());
        category.setConverter(strCat(c -> c == null ? Messages.get("common.all_f") : c.getName()));

        equipment.getItems().add(null);
        equipment.getItems().addAll(new EquipmentRepo().findAll(
                new EquipmentRepo.Filter(null, null, null, null, null, null, null, null, false, null),
                "code", "ASC").stream().toList());
        equipment.setConverter(strEquip(e -> e == null ? Messages.get("common.all")
                : e.getCode() + " — " + e.getName()));

        technician.getItems().add(null);
        technician.getItems().addAll(AppContext.get().technicians().all());
        technician.setConverter(strTech(t -> t == null ? Messages.get("common.all") : t.getFullName()));

        GridPane filters = new GridPane();
        filters.setHgap(12);
        filters.setVgap(8);
        filters.add(wrap(Messages.get("reports.list"), reportPicker), 0, 0, 2, 1);
        filters.add(wrap(Messages.get("reports.from"), from), 0, 1);
        filters.add(wrap(Messages.get("reports.to"), to), 1, 1);
        filters.add(wrap(Messages.get("reports.department"), department), 0, 2);
        filters.add(wrap(Messages.get("reports.category"), category), 1, 2);
        filters.add(wrap(Messages.get("reports.equipment"), equipment), 0, 3);
        filters.add(wrap(Messages.get("reports.technician"), technician), 1, 3);

        Button run = new Button(Messages.get("reports.generate"));
        run.getStyleClass().add("primary");
        run.setOnAction(e -> runReport());

        Button csv = new Button(Messages.get("reports.export_csv"));
        csv.getStyleClass().add("ghost");
        csv.setOnAction(e -> exportCsv());

        Button pdf = new Button(Messages.get("reports.export_pdf"));
        pdf.getStyleClass().add("ghost");
        pdf.setOnAction(e -> exportPdf());

        HBox actions = new HBox(8, run, csv, pdf);
        box.getChildren().add(filters);
        box.getChildren().add(actions);
        box.getChildren().add(resultTable);
        VBox.setVgrow(resultTable, Priority.ALWAYS);
        box.getChildren().add(rowInfo);

        runReport();
        return box;
    }

    private javafx.util.StringConverter<Department> strDept(java.util.function.Function<Department, String> f) {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Department d) {
                return f.apply(d);
            }

            @Override
            public Department fromString(String s) {
                return null;
            }
        };
    }

    private javafx.util.StringConverter<EquipmentCategory> strCat(java.util.function.Function<EquipmentCategory, String> f) {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(EquipmentCategory c) {
                return f.apply(c);
            }

            @Override
            public EquipmentCategory fromString(String s) {
                return null;
            }
        };
    }

    private javafx.util.StringConverter<Equipment> strEquip(java.util.function.Function<Equipment, String> f) {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Equipment e) {
                return f.apply(e);
            }

            @Override
            public Equipment fromString(String s) {
                return null;
            }
        };
    }

    private javafx.util.StringConverter<Technician> strTech(java.util.function.Function<Technician, String> f) {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Technician t) {
                return f.apply(t);
            }

            @Override
            public Technician fromString(String s) {
                return null;
            }
        };
    }

    private Node wrap(String label, Node field) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        return new VBox(3, l, field);
    }

    private ReportService.Filters filters() {
        return new ReportService.Filters(
                from.getValue(), to.getValue(),
                department.getValue() == null ? null : department.getValue().getId(),
                category.getValue() == null ? null : category.getValue().getId(),
                equipment.getValue() == null ? null : equipment.getValue().getId(),
                technician.getValue() == null ? null : technician.getValue().getId(),
                null, null);
    }

    private void runReport() {
        try {
            ReportService.ReportData data = AppContext.get().reports()
                    .build(reportPicker.getValue(), filters());
            current = data;
            showResult(data);
        } catch (RuntimeException ex) {
            Toast.error(ex.getMessage());
            resultTable.getItems().clear();
            resultTable.getColumns().clear();
            rowInfo.setText("");
        }
    }

    private void showResult(ReportService.ReportData data) {
        resultTable.getColumns().clear();
        resultTable.getItems().clear();

        List<String> headers = data.headers();
        for (int i = 0; i < headers.size(); i++) {
            final int idx = i;
            TableColumn<Row, String> col = new TableColumn<>(headers.get(i));
            col.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                    cd.getValue().getCell(idx)));
            col.setPrefWidth(Math.max(110, headers.get(i).length() * 11));
            resultTable.getColumns().add(col);
        }

        List<Row> rows = new ArrayList<>();
        for (List<String> cells : data.rows()) {
            rows.add(new Row(cells));
        }
        resultTable.getItems().setAll(rows);
        rowInfo.setText(data.isEmpty()
                ? Messages.get("reports.empty")
                : Messages.get("reports.rows", rows.size()));
    }

    private void exportCsv() {
        if (current == null || current.isEmpty()) {
            Toast.warning(Messages.get("reports.empty"));
            return;
        }
        try {
            Path out = AppContext.get().reports().exportCsv(current);
            Toast.success(Messages.get("reports.export_success", out.toString()));
        } catch (RuntimeException ex) {
            Toast.error(ex.getMessage());
        }
    }

    private void exportPdf() {
        if (current == null || current.isEmpty()) {
            Toast.warning(Messages.get("reports.empty"));
            return;
        }
        try {
            Path out = AppContext.get().reports().exportPdf(current, filters());
            Toast.success(Messages.get("reports.export_success", out.toString()));
        } catch (RuntimeException ex) {
            Toast.error(ex.getMessage());
        }
    }

    // ---------- Onglet analytique ----------

    private Node analyticsTab() {
        var analytics = AppContext.get().analytics();

        // KPI calculés
        FlowPane kpis = new FlowPane(12, 12);
        kpis.getChildren().add(metric(Ui.percent(analytics.availabilityPercent(365)),
                Messages.get("analytics.availability")));
        kpis.getChildren().add(metric(Ui.decimal(analytics.mttrHours()) + " h",
                Messages.get("analytics.mttr")));
        kpis.getChildren().add(metric(Ui.decimal(analytics.mtbfHours()) + " h",
                Messages.get("analytics.mtbf")));
        kpis.getChildren().add(metric(Ui.percent(analytics.pmCompliancePercent()),
                Messages.get("analytics.pm_compliance")));
        kpis.getChildren().add(metric(String.valueOf(analytics.failureRatePerYear()),
                Messages.get("analytics.failure_rate")));
        kpis.getChildren().add(metric(Ui.money(analytics.totalCost(365)),
                Messages.get("analytics.cost")));

        Label calcNote = Ui.muted("ℹ " + Messages.get("analytics.calculated"));

        // Graphiques
        FlowPane charts = new FlowPane(14, 14);

        // Demandes par mois
        List<String> months = new ArrayList<>();
        List<Number> reqCounts = new ArrayList<>();
        for (Object[] row : Jdbc.query("""
                SELECT TO_CHAR(reported_at, 'YYYY-MM') AS ym, COUNT(*)
                FROM intervention_requests
                WHERE reported_at >= DATEADD('MONTH', -12, CURRENT_TIMESTAMP)
                GROUP BY TO_CHAR(reported_at, 'YYYY-MM') ORDER BY ym""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getString(1), rs.getLong(2)})) {
            months.add(String.valueOf(row[0]).substring(5));
            reqCounts.add((Long) row[1]);
        }
        charts.getChildren().add(ChartFactory.box(Messages.get("analytics.requests_by_month"),
                ChartFactory.line("", Messages.get("nav.requests"), months, reqCounts)));

        // Pannes par catégorie
        charts.getChildren().add(ChartFactory.box(Messages.get("analytics.mttr_by_cat"),
                ChartFactory.barH("", "MTTR (h)", analytics.mttrByCategory())));

        // MTBF par catégorie
        charts.getChildren().add(ChartFactory.box(Messages.get("analytics.mtbf_by_cat"),
                ChartFactory.barH("", "MTBF (h)", analytics.mtbfByCategory())));

        // Coût par mois
        List<String> costMonths = new ArrayList<>();
        List<Number> costs = new ArrayList<>();
        for (Object[] row : analytics.costByMonth(12)) {
            costMonths.add(String.valueOf(row[0]).substring(5));
            costs.add((Number) row[1]);
        }
        charts.getChildren().add(ChartFactory.box(Messages.get("analytics.cost_by_month"),
                ChartFactory.line("", "Coût", costMonths, costs)));

        // Arrêts par service
        charts.getChildren().add(ChartFactory.box(Messages.get("analytics.downtime_by_dept"),
                ChartFactory.barH("", "Heures", analytics.downtimeByDepartment(12))));

        // Top équipements en panne
        List<Object[]> top = new EquipmentRepo().topFailingEquipment(8);
        if (!top.isEmpty()) {
            List<Object[]> slim = new ArrayList<>();
            for (Object[] r : top) {
                slim.add(new Object[]{r[0] + " — " + shorten((String) r[1]), r[2]});
            }
            charts.getChildren().add(ChartFactory.box(Messages.get("analytics.top_failures"),
                    ChartFactory.barH("", "Pannes", slim)));
        }

        // Consommation de pièces
        List<Object[]> consumed = new com.medmaint.persistence.repo.SparePartRepo()
                .topConsumedParts(8);
        if (!consumed.isEmpty()) {
            List<Object[]> slim = new ArrayList<>();
            for (Object[] r : consumed) {
                slim.add(new Object[]{r[0], r[2]});
            }
            charts.getChildren().add(ChartFactory.box(Messages.get("analytics.parts_consumption"),
                    ChartFactory.barH("", "Quantité", slim)));
        }

        // Correctif vs préventif (camembert)
        var cvp = analytics.correctiveVsPreventive(365);
        List<Object[]> cvpData = new ArrayList<>();
        cvp.forEach((k, v) -> cvpData.add(new Object[]{k, v}));
        charts.getChildren().add(ChartFactory.box(
                Messages.get("dashboard.chart.corrective_preventive"),
                ChartFactory.pie("", cvpData)));

        VBox box = new VBox(12, Ui.sectionLabel("analytics.title"), kpis, calcNote, charts);
        box.setPadding(new Insets(4));
        return new javafx.scene.control.ScrollPane(box) {{
            setFitToWidth(true);
            setStyle("-fx-background-color: transparent;");
        }};
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() > 22 ? s.substring(0, 22) + "…" : s;
    }

    private Node metric(String value, String label) {
        VBox box = new VBox(3);
        box.getStyleClass().add("kpi-card");
        box.setMinWidth(220);
        Label v = new Label(value);
        v.getStyleClass().add("kpi-value");
        Label l = new Label(label);
        l.getStyleClass().add("kpi-label");
        l.setWrapText(true);
        Label note = Ui.muted("calculated");
        note.setText("◈ " + Messages.get("analytics.calculated"));
        note.setStyle("-fx-font-size: 9.5px;");
        box.getChildren().addAll(v, l, note);
        return box;
    }
}
