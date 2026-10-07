package com.medmaint.ui.pages.dashboard;

import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.enums.Priority;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.InterventionRequest;
import com.medmaint.domain.model.MaintenancePlan;
import com.medmaint.domain.model.WorkOrder;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.persistence.repo.PlanRepo;
import com.medmaint.persistence.repo.RequestRepo;
import com.medmaint.persistence.repo.WorkOrderRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.MainShell;
import com.medmaint.ui.components.ChartFactory;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.pages.PageHeader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tableau de bord : KPI (équipements, maintenance, performance), graphiques
 * alimentés par les données réelles, activité récente, maintenance à venir
 * et demandes critiques.
 */
public class DashboardPage extends VBox {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DashboardPage.class);

    private final int PERIOD_DAYS = 30;

    public DashboardPage() {
        setSpacing(14);
        setPadding(new Insets(0, 0, 10, 0));

        Session s = Session.get();

        PageHeader header = new PageHeader(Messages.get("dashboard.title"));
        header.subtitle(Messages.get("dashboard.period") + " · " + s.fullName() + " · " + s.roleLabel());
        getChildren().add(header);

        try {
            Map<String, Object> kpi = AppContext.get().analytics().dashboardKpis(PERIOD_DAYS);

            getChildren().add(kpiSection(kpi));
            getChildren().add(new HBox(14, chartsRow()));
            getChildren().add(new HBox(14, recentActivity(), upcomingMaintenance(), criticalRequests()));
        } catch (RuntimeException ex) {
            log.error("Échec de chargement du tableau de bord", ex);
            VBox err = Ui.emptyState(Messages.get("msg.error_load"), null);
            getChildren().add(err);
        }
    }

    // ---------- KPI ----------

    private Node kpiSection(Map<String, Object> k) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        int col = 0;
        // Équipements
        grid.add(kpi(Messages.get("dashboard.kpi.equipment"), String.valueOf(k.get("equipmentTotal")),
                sub(EquipmentStatus.EN_SERVICE, k) + " · " + sub(EquipmentStatus.HORS_SERVICE, k)), 0, 0);
        grid.add(kpi(String.valueOf(k.get("equipmentInService")), Messages.get("dashboard.kpi.in_service"),
                k.get("equipmentOut") + " " + Messages.get("dashboard.kpi.out_of_service").toLowerCase()), 1, 0);
        grid.add(kpi(String.valueOf(k.get("equipmentMaintenance")), Messages.get("dashboard.kpi.in_maintenance"),
                k.get("equipmentAwaitingRepair") + " en attente"), 2, 0);

        // Maintenance
        grid.add(kpi(String.valueOf(k.get("requestsOpen")), Messages.get("dashboard.kpi.requests_open"),
                k.get("requestsUrgent") + " urgentes"), 3, 0);
        grid.add(kpi(String.valueOf(k.get("woInProgress")), Messages.get("dashboard.kpi.wo_active"),
                k.get("woCompleted") + " terminées (30 j)"), 4, 0);
        grid.add(kpi(String.valueOf(k.get("pmOverdue")), Messages.get("dashboard.kpi.pm_overdue"),
                k.get("pmUpcoming") + " à venir (7 j)"), 5, 0);

        // Performance (calculé)
        grid.add(kpi(Ui.percent((BigDecimal) k.get("availability")),
                Messages.get("dashboard.kpi.availability"), Messages.get("dashboard.calculated")), 0, 1);
        grid.add(kpi(Ui.percent((BigDecimal) k.get("pmCompliance")),
                Messages.get("dashboard.kpi.pm_compliance"), Messages.get("dashboard.calculated")), 1, 1);
        grid.add(kpi(String.valueOf(k.get("failures")), Messages.get("dashboard.kpi.failures"),
                "MTTR " + Ui.decimal((BigDecimal) k.get("mttr")) + " h"), 2, 1);
        grid.add(kpi(Ui.decimal((BigDecimal) k.get("avgIntervention")),
                Messages.get("dashboard.kpi.avg_intervention") + " (h)", Messages.get("dashboard.calculated")), 3, 1);
        grid.add(kpi(Ui.money((BigDecimal) k.get("cost")), Messages.get("dashboard.kpi.cost"),
                Messages.get("dashboard.period")), 4, 1);
        grid.add(kpi(String.valueOf(k.get("lowStock")), "Stock bas",
                k.get("contractsExpiring") + " contrats à renouveler"), 5, 1);

        for (int i = 0; i < 6; i++) {
            final int kpiCol = i;
            GridPane.setHgrow(grid.getChildren().stream()
                    .filter(n -> GridPane.getColumnIndex(n) == kpiCol && GridPane.getRowIndex(n) == 0)
                    .findFirst().orElse(new VBox()), javafx.scene.layout.Priority.ALWAYS);
        }
        return grid;
    }

    private String sub(EquipmentStatus st, Map<String, Object> k) {
        return switch (st) {
            case EN_SERVICE -> k.get("equipmentInService") + " en service";
            case HORS_SERVICE -> k.get("equipmentOut") + " hors service";
            default -> "";
        };
    }

    private Node kpi(String value, String label, String sub) {
        VBox box = new VBox(3);
        box.getStyleClass().add("kpi-card");
        box.setMinWidth(205);
        box.setMaxWidth(Double.MAX_VALUE);
        Label v = new Label(value);
        v.getStyleClass().add("kpi-value");
        Label l = new Label(label);
        l.getStyleClass().add("kpi-label");
        l.setWrapText(true);
        Label s = new Label(sub == null ? "" : sub);
        s.getStyleClass().add("dim");
        s.setWrapText(true);
        box.getChildren().addAll(v, l, s);
        HBox.setHgrow(box, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    // ---------- Graphiques ----------

    private Node chartsRow() {
        FlowPane flow = new FlowPane(14, 14);
        flow.setPadding(new Insets(0));

        EquipmentRepo eq = AppContext.get().analytics().equipment();
        WorkOrderRepo wo = AppContext.get().analytics().workOrders();

        // 1. Répartition par catégorie
        flow.getChildren().add(ChartFactory.box(
                Messages.get("dashboard.chart.by_category"),
                ChartFactory.pie("", eq.countByCategory())));

        // 2. Répartition par service
        flow.getChildren().add(ChartFactory.box(
                Messages.get("dashboard.chart.by_department"),
                ChartFactory.barH("", Messages.get("nav.equipment"), eq.countByDepartment())));

        // 3. Correctif vs préventif
        Map<String, Long> cvp = AppContext.get().analytics().correctiveVsPreventive(PERIOD_DAYS);
        List<Object[]> cvpData = new ArrayList<>();
        cvp.forEach((k2, v) -> cvpData.add(new Object[]{k2, v}));
        flow.getChildren().add(ChartFactory.box(
                Messages.get("dashboard.chart.corrective_preventive"),
                ChartFactory.pie("", cvpData)));

        // 4. Demandes par priorité
        flow.getChildren().add(ChartFactory.box(
                Messages.get("dashboard.chart.requests_priority"),
                ChartFactory.barH("", Messages.get("nav.requests"),
                        translatePriority(new RequestRepo().countByPriority()))));

        // 5. Activité mensuelle (correctif + préventif)
        var activity = AppContext.get().analytics().activityByMonth(12);
        List<String> months = new ArrayList<>();
        List<Number> corrective = new ArrayList<>();
        List<Number> preventive = new ArrayList<>();
        for (Map<String, Object> row : activity) {
            months.add(String.valueOf(row.get("month")).substring(5));
            corrective.add((Long) row.get("corrective"));
            preventive.add((Long) row.get("preventive"));
        }
        flow.getChildren().add(ChartFactory.box(
                Messages.get("dashboard.chart.activity"),
                ChartFactory.barMulti("", months,
                        "Correctif", corrective, "Préventif", preventive)));

        // 6. Coût mensuel
        List<String> costMonths = new ArrayList<>();
        List<Number> costs = new ArrayList<>();
        for (Object[] row : wo.costByMonth(12)) {
            costMonths.add(String.valueOf(row[0]).substring(5));
            costs.add((Number) row[1]);
        }
        flow.getChildren().add(ChartFactory.box(
                Messages.get("dashboard.chart.cost"),
                ChartFactory.line("", "Coût", costMonths, costs)));

        return flow;
    }

    private List<Object[]> translatePriority(List<Object[]> raw) {
        List<Object[]> out = new ArrayList<>();
        for (Object[] row : raw) {
            String key = "priority." + String.valueOf(row[0]).toLowerCase();
            String label = Messages.has(key) ? Messages.get(key) : String.valueOf(row[0]);
            out.add(new Object[]{label, row[1]});
        }
        // Ordonne par poids décroissant de priorité
        out.sort((a, b) -> Integer.compare(priorityWeight((String) b[0]), priorityWeight((String) a[0])));
        return out;
    }

    private int priorityWeight(String label) {
        for (Priority p : Priority.values()) {
            if (p.label().equalsIgnoreCase(label)) {
                return p.weight();
            }
        }
        return 0;
    }

    // ---------- Panneaux latéraux ----------

    private Node recentActivity() {
        VBox list = new VBox(8);
        List<WorkOrder> recentWos = new WorkOrderRepo().findAll(null, 5);
        List<InterventionRequest> recentReq = new RequestRepo()
                .findAll(new RequestRepo.Filter(null, null, null, null, null, null, null, null, null), 4);

        for (InterventionRequest r : recentReq) {
            list.getChildren().add(activityRow("⚠", r.getRequestNo() + " · " + r.getEquipmentCode(),
                    r.getEquipmentName() + " — " + r.priorityEnum().label(),
                    Ui.dateTime(r.getReportedAt()),
                    () -> MainShell.shellNavigate("requests", null)));
        }
        for (WorkOrder w : recentWos) {
            list.getChildren().add(activityRow("⚒", w.getWoNo() + " · " + w.getEquipmentCode(),
                    w.getEquipmentName() + " — " + w.statusEnum().label(),
                    Ui.dateTime(w.getOpenedAt()),
                    () -> MainShell.shellNavigate("workorders", "detail:" + w.getId())));
        }
        if (list.getChildren().isEmpty()) {
            list.getChildren().add(Ui.emptyState(Messages.get("common.none"), null));
        }
        return panel(Messages.get("dashboard.recent"), list, 470);
    }

    private Node upcomingMaintenance() {
        VBox list = new VBox(8);
        List<MaintenancePlan> plans = new PlanRepo().findDueWithin(30);
        if (plans.isEmpty()) {
            list.getChildren().add(Ui.emptyState(Messages.get("dashboard.upcoming_empty"), null));
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        for (MaintenancePlan p : plans) {
            String when;
            String tone;
            if (p.getNextDue() != null && p.getNextDue().isBefore(today)) {
                long d = java.time.temporal.ChronoUnit.DAYS.between(p.getNextDue(), today);
                when = d + " j de retard";
                tone = "danger";
            } else if (p.getNextDue() != null && p.getNextDue().equals(today)) {
                when = Messages.get("common.today");
                tone = "warn";
            } else if (p.getNextDue() != null && !p.getNextDue().isAfter(today.plusDays(7))) {
                long d = java.time.temporal.ChronoUnit.DAYS.between(today, p.getNextDue());
                when = "dans " + d + " j";
                tone = "info";
            } else {
                when = Ui.date(p.getNextDue());
                tone = "muted";
            }
            HBox row = new HBox(8,
                    Ui.badge(when, tone),
                    new Label(p.getEquipmentCode() + " — " + p.getMaintenanceType()));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setUserData(p);
            row.setOnMouseClicked(e -> MainShell.shellNavigate("preventive", null));
            row.setStyle("-fx-cursor: hand; -fx-padding: 4 2 4 2;");
            list.getChildren().add(row);
        }
        return panel(Messages.get("dashboard.upcoming"), list, 470);
    }

    private Node criticalRequests() {
        VBox list = new VBox(8);
        List<InterventionRequest> critical = new RequestRepo().findAll(
                new RequestRepo.Filter(null, null, "CRITIQUE", null, null, null, null, null, null), 5);
        critical.addAll(new RequestRepo().findAll(
                new RequestRepo.Filter(null, null, "URGENTE", null, null, null, null, null, null), 5));
        if (critical.isEmpty()) {
            list.getChildren().add(Ui.emptyState(Messages.get("dashboard.no_critical"), null));
        }
        for (InterventionRequest r : critical) {
            Label no = new Label(r.getRequestNo() + " · " + r.getEquipmentCode());
            no.getStyleClass().add("h3");
            Label desc = new Label(r.getEquipmentName());
            desc.getStyleClass().add("muted");
            desc.setWrapText(true);
            Label status = new Label(r.statusEnum().label());
            status.getStyleClass().add("badge");
            status.getStyleClass().add("badge-" + r.statusEnum().tone());
            VBox item = new VBox(2, no, desc, status);
            item.setStyle("-fx-padding: 4 2 4 2; -fx-cursor: hand;");
            item.setOnMouseClicked(e -> MainShell.shellNavigate("requests", "detail:" + r.getId()));
            list.getChildren().add(item);
        }
        return panel(Messages.get("dashboard.critical"), list, 470);
    }

    private Node activityRow(String icon, String title, String subtitle, String time, Runnable onClick) {
        Label ic = new Label(icon);
        ic.setStyle("-fx-font-size: 15px;");
        Label t = new Label(title);
        t.getStyleClass().add("h3");
        Label s = new Label(subtitle + "  ·  " + time);
        s.getStyleClass().add("dim");
        s.setWrapText(true);
        VBox text = new VBox(1, t, s);
        HBox row = new HBox(10, ic, text);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 4 2 4 2; -fx-cursor: hand;");
        row.setOnMouseClicked(e -> onClick.run());
        return row;
    }

    private Node panel(String title, Node body, double width) {
        Label t = Ui.h3(title);
        VBox content = new VBox(body);
        content.setPadding(new Insets(4, 0, 0, 0));
        VBox box = Ui.panel(t, content);
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        return box;
    }
}
