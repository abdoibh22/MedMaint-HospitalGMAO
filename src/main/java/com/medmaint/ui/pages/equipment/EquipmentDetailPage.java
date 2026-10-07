package com.medmaint.ui.pages.equipment;

import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.model.AuditLog;
import com.medmaint.domain.model.Document;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.MaintenancePlan;
import com.medmaint.domain.model.MaintenanceRecord;
import com.medmaint.domain.model.StockTransaction;
import com.medmaint.domain.model.WorkOrder;
import com.medmaint.persistence.repo.WorkOrderRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.MainShell;
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
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Fiche détaillée d'un équipement : en-tête (code, statut, criticité) + 8 onglets
 * (Informations générales, Maintenance, Interventions, Préventif, Pièces,
 * Documents, Coûts, Activité).
 */
public class EquipmentDetailPage extends VBox {

    private final long equipmentId;
    private Equipment equipment;

    public EquipmentDetailPage(String context) {
        long id = parseId(context);
        this.equipmentId = id;
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        Optional<Equipment> found = AppContext.get().equipment().get(id);
        if (found.isEmpty()) {
            getChildren().add(Ui.emptyState(Messages.get("msg.not_found"), backBtn()));
            return;
        }
        equipment = found.get();
        render();
    }

    private void render() {
        getChildren().clear();

        // --- En-tête ---
        Button back = new Button("‹ " + Messages.get("common.back"));
        back.getStyleClass().add("ghost");
        back.setOnAction(e -> MainShell.shellNavigate("equipment", null));

        Label code = Ui.h1(equipment.getCode() + " — " + equipment.getName());
        Label status = Ui.badge(equipment.statusEnum().label(), equipment.statusEnum().tone());
        Label crit = Ui.badge("Criticité : " + equipment.criticalityEnum().label(),
                equipment.criticalityEnum().tone());
        Label serial = Ui.muted("N° série : " + Ui.orDash(equipment.getSerialNumber()));

        HBox head = new HBox(12, code, status, crit, serial);
        head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        PageHeader header = new PageHeader("");
        header.setVisible(false);

        HBox toolbar = new HBox(8);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        toolbar.getChildren().add(back);
        if (Session.get().has(Permission.EQUIPMENT_UPDATE)) {
            toolbar.getChildren().add(action(Messages.get("common.edit"),
                    new com.medmaint.ui.pages.equipment.EquipmentFormDialog(equipment)::showAndWait,
                    () -> refreshSelf(), true));
        }
        if (Session.get().has(Permission.REQUEST_CREATE)) {
            toolbar.getChildren().add(action(Messages.get("equipment.action.new_request"),
                    () -> new com.medmaint.ui.pages.requests.RequestFormDialog(
                            com.medmaint.ui.pages.requests.RequestFormDialog.Mode.FROM_EQUIPMENT,
                            equipment).showAndWait(),
                    null, false));
        }
        if (Session.get().has(Permission.PM_MANAGE)) {
            toolbar.getChildren().add(action(Messages.get("equipment.action.new_pm"),
                    () -> new com.medmaint.ui.pages.maintenance.PlanFormDialog(equipment).showAndWait(),
                    () -> refreshSelf(), false));
        }
        if (Session.get().has(Permission.EQUIPMENT_DELETE)) {
            toolbar.getChildren().add(action(
                    equipment.isArchived() ? Messages.get("equipment.action.unarchive")
                            : Messages.get("equipment.action.archive"),
                    () -> {
                        boolean arch = !equipment.isArchived();
                        if (arch || ConfirmDialog.confirm(Messages.get("equipment.archive_confirm"))) {
                            AppContext.get().equipment().archive(equipment.getId(), arch);
                            Toast.success(Messages.get("msg.action_done"));
                            refreshSelf();
                        }
                    },
                    null, false));
        }
        if (Session.get().has(Permission.EQUIPMENT_DELETE) && Session.get().isAdmin()) {
            toolbar.getChildren().add(action(Messages.get("common.delete"), () -> {
                if (ConfirmDialog.confirm(Messages.get("equipment.delete_confirm"))) {
                    try {
                        AppContext.get().equipment().delete(equipment.getId());
                        Toast.success(Messages.get("msg.deleted"));
                        MainShell.shellNavigate("equipment", null);
                    } catch (RuntimeException ex) {
                        Toast.error(ex.getMessage());
                    }
                }
            }, null, false));
        }

        VBox sub = new VBox(6, Ui.muted(fullLocation() + " · " + Ui.orDash(equipment.getCategoryName())
                + " · " + Ui.orDash(equipment.getManufacturerName()) + " " + Ui.orDash(equipment.getModel())));

        getChildren().addAll(head, toolbar, sub);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab(Messages.get("equipment.tab.general"), generalTab()),
                tab(Messages.get("equipment.tab.maintenance"), maintenanceTab()),
                tab(Messages.get("equipment.tab.interventions"), interventionsTab()),
                tab(Messages.get("equipment.tab.preventive"), preventiveTab()),
                tab(Messages.get("equipment.tab.parts"), partsTab()),
                tab(Messages.get("equipment.tab.documents"), documentsTab()),
                tab(Messages.get("equipment.tab.costs"), costsTab()),
                tab(Messages.get("equipment.tab.activity"), activityTab()));
        getChildren().add(tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
    }

    private void refreshSelf() {
        Optional<Equipment> found = AppContext.get().equipment().get(equipmentId);
        found.ifPresent(e -> {
            equipment = e;
            render();
        });
    }

    private String fullLocation() {
        return equipment.getFullLocation();
    }

    private Tab tab(String title, Node content) {
        Tab t = new Tab(title);
        VBox wrap = new VBox(content);
        wrap.setPadding(new Insets(14));
        t.setContent(wrap);
        return t;
    }

    // ---------- Informations générales ----------

    private Node generalTab() {
        GridPane g = grid();
        int r = 0;
        r = row(g, r, Messages.get("equipment.code"), equipment.getCode());
        r = row(g, r, Messages.get("equipment.name"), equipment.getName());
        r = row(g, r, Messages.get("equipment.category"), equipment.getCategoryName());
        r = row(g, r, Messages.get("equipment.subcategory"), equipment.getSubcategory());
        r = row(g, r, Messages.get("equipment.manufacturer"), equipment.getManufacturerName());
        r = row(g, r, Messages.get("equipment.model"), equipment.getModel());
        r = row(g, r, Messages.get("equipment.serial"), equipment.getSerialNumber());
        r = row(g, r, Messages.get("equipment.reference"), equipment.getReferenceNumber());
        r = row(g, r, Messages.get("equipment.status"), equipment.statusEnum().label());
        r = row(g, r, Messages.get("equipment.criticality"), equipment.criticalityEnum().label());
        r = row(g, r, Messages.get("equipment.risk"), equipment.riskLevelEnum().shortLabel());
        r = row(g, r, Messages.get("equipment.device_class"), equipment.getDeviceClass());
        r = row(g, r, Messages.get("equipment.service"), equipment.getDepartmentName());
        r = row(g, r, Messages.get("equipment.location"), equipment.getLocationName());
        r = row(g, r, Messages.get("equipment.location_details"), equipment.getLocationDetails());
        r = row(g, r, Messages.get("equipment.supplier"), equipment.getSupplierName());
        r = row(g, r, Messages.get("equipment.purchase_date"), Ui.date(equipment.getPurchaseDate()));
        r = row(g, r, Messages.get("equipment.reception_date"), Ui.date(equipment.getReceptionDate()));
        r = row(g, r, Messages.get("equipment.commissioning"), Ui.date(equipment.getCommissioningDate()));
        r = row(g, r, Messages.get("equipment.price"),
                equipment.getPurchasePrice() == null ? null : Ui.money(equipment.getPurchasePrice()));
        r = row(g, r, Messages.get("equipment.warranty"),
                equipment.getWarrantyMonths() == null ? null : equipment.getWarrantyMonths() + " mois");
        r = row(g, r, Messages.get("equipment.warranty_end"), warrantyText());
        r = row(g, r, Messages.get("equipment.power_supply"), equipment.getPowerSupply());
        r = row(g, r, Messages.get("equipment.power"), equipment.getPowerRating());
        r = row(g, r, Messages.get("equipment.dimensions"), equipment.getDimensions());
        r = row(g, r, Messages.get("equipment.weight"),
                equipment.getWeightKg() == null ? null : equipment.getWeightKg() + " kg");
        r = row(g, r, Messages.get("equipment.year"),
                equipment.getFabricationYear() == null ? null : String.valueOf(equipment.getFabricationYear()));
        r = row(g, r, Messages.get("equipment.notes"), equipment.getNotes());
        r = row(g, r, Messages.get("equipment.contract"), equipment.getContractNo());
        return g;
    }

    private String warrantyText() {
        if (equipment.getWarrantyEndDate() == null) return null;
        boolean ok = equipment.isUnderWarranty(java.time.LocalDate.now());
        return Ui.date(equipment.getWarrantyEndDate()) + (ok ? "  (sous garantie)" : "  (hors garantie)");
    }

    // ---------- Maintenance / historique ----------

    private Node maintenanceTab() {
        List<MaintenanceRecord> records = AppContext.get().preventive()
                .historyForEquipment(equipmentId, 100);
        if (records.isEmpty()) {
            return Ui.emptyState("Aucun historique de maintenance pour cet équipement.", null);
        }
        VBox list = new VBox(8);
        for (MaintenanceRecord r : records) {
            Label when = new Label(Ui.dateTime(r.getPerformedAt()));
            Label type = Ui.badge("PREVENTIVE".equals(r.getRecordType())
                    ? Messages.get("nav.preventive") : Messages.get("nav.workorders"),
                    "PREVENTIVE".equals(r.getRecordType()) ? "info" : "warn");
            Label result = Ui.badge(resultLabel(r.getResult()), resultTone(r.getResult()));
            Label who = Ui.muted(Ui.orDash(r.getPerformedByName()));
            Label obs = new Label(Ui.orDash(r.getObservations()));
            obs.setWrapText(true);
            Label cost = Ui.muted(r.getCost() == null ? "" : Ui.money(r.getCost()));

            VBox main = new VBox(3, new HBox(10, when, type, result, who, cost), obs);
            if (!r.getChecklistResults().isEmpty()) {
                VBox items = new VBox(2);
                r.getChecklistResults().forEach(e -> {
                    Label item = new Label("• " + e.getItemLabel() + " — "
                            + resultLabel(e.getResult())
                            + (e.getNotes() != null && !e.getNotes().isBlank() ? " (" + e.getNotes() + ")" : ""));
                    item.getStyleClass().add("muted");
                    items.getChildren().add(item);
                });
                main.getChildren().add(items);
            }
            VBox card = Ui.panel(null, main);
            list.getChildren().add(card);
        }
        return list;
    }

    private String resultLabel(String code) {
        if (code == null) return "—";
        return switch (code) {
            case "CONFORME" -> "Conforme";
            case "NON_CONFORME" -> "Non conforme";
            case "NON_APPLICABLE" -> "Non applicable";
            default -> code;
        };
    }

    private String resultTone(String code) {
        if (code == null) return "muted";
        return switch (code) {
            case "CONFORME" -> "ok";
            case "NON_CONFORME" -> "danger";
            default -> "muted";
        };
    }

    // ---------- Interventions ----------

    private Node interventionsTab() {
        List<WorkOrder> wos = new WorkOrderRepo().findAll(
                new WorkOrderRepo.Filter(null, null, null, null, equipmentId, null, null, null, null), 200);
        if (wos.isEmpty()) {
            return Ui.emptyState(Messages.get("workorders.empty"), null);
        }
        VBox list = new VBox(8);
        for (WorkOrder w : wos) {
            Label no = Ui.h3(w.getWoNo() + " · " + w.statusEnum().label());
            Label meta = Ui.muted(Ui.dateTime(w.getOpenedAt())
                    + " · " + w.priorityEnum().label()
                    + " · " + Ui.orDash(w.getTechnicianName())
                    + " · " + (w.getTotalCost() == null ? "—" : Ui.money(w.getTotalCost())));
            Label diag = new Label("Diagnostic : " + Ui.orDash(w.getDiagnosis()));
            diag.setWrapText(true);
            VBox body = new VBox(3, no, meta, diag);
            VBox card = Ui.panel(null, body);
            card.setOnMouseClicked(e -> MainShell.shellNavigate("workorders", "detail:" + w.getId()));
            card.setStyle("-fx-cursor: hand;");
            list.getChildren().add(card);
        }
        return list;
    }

    // ---------- Préventif ----------

    private Node preventiveTab() {
        List<MaintenancePlan> plans = new com.medmaint.persistence.repo.PlanRepo()
                .findByEquipment(equipmentId);
        if (plans.isEmpty()) {
            Node action = Session.get().has(Permission.PM_MANAGE)
                    ? PageHeader.actionButton(Messages.get("pm.new"), true,
                    () -> new com.medmaint.ui.pages.maintenance.PlanFormDialog(equipment).showAndWait()) : null;
            return Ui.emptyState(Messages.get("pm.empty"), action);
        }
        VBox list = new VBox(8);
        for (MaintenancePlan p : plans) {
            Label type = Ui.h3(p.getMaintenanceType());
            Label freq = Ui.badge(p.getFrequency() == null ? "" : freqLabel(p), "info");
            Label next = Ui.badge("Prochaine : " + Ui.date(p.getNextDue()),
                    dueTone(p));
            Label last = Ui.muted("Dernière : " + Ui.date(p.getLastPerformed()));
            Label resp = Ui.muted("Responsable : " + Ui.orDash(p.getResponsibleName()));
            list.getChildren().add(Ui.panel(null, new VBox(4, new HBox(10, type, freq, next, last, resp),
                    new Label(Ui.orDash(p.getInstructions())))));
        }
        return list;
    }

    private String freqLabel(MaintenancePlan p) {
        return com.medmaint.domain.enums.MaintenanceFrequency.fromCode(p.getFrequency()).label();
    }

    private String dueTone(MaintenancePlan p) {
        if (p.getNextDue() == null) return "muted";
        if (p.getNextDue().isBefore(java.time.LocalDate.now())) return "danger";
        if (!p.getNextDue().isAfter(java.time.LocalDate.now().plusDays(7))) return "warn";
        return "ok";
    }

    // ---------- Pièces ----------

    private Node partsTab() {
        List<StockTransaction> txs = new com.medmaint.persistence.repo.SparePartRepo()
                .findRecentTransactions(500).stream()
                .filter(t -> t.getEquipmentId() != null && t.getEquipmentId() == equipmentId)
                .toList();
        if (txs.isEmpty()) {
            return Ui.emptyState("Aucune pièce consommée sur cet équipement.", null);
        }
        DataTable<StockTransaction> table = new DataTable<>("—", null);
        table.setPageSize(50);
        table.addColumn(new DataTable.Col<StockTransaction>("date", "Date",
                t -> Ui.dateTime(t.getCreatedAt()), 130).sortable(false));
        table.addColumn(new DataTable.Col<>("part", Messages.get("parts.part_no"),
                StockTransaction::getPartNo, 130));
        table.addColumn(new DataTable.Col<>("name", Messages.get("parts.name"),
                StockTransaction::getPartName, 220));
        table.addColumn(new DataTable.Col<>("type", Messages.get("parts.tx_type"),
                t -> txLabel(t.getTxType()), 150));
        table.addColumn(new DataTable.Col<>("qty", Messages.get("parts.quantity"),
                t -> String.valueOf(t.getQuantity()), 90));
        table.addColumn(new DataTable.Col<>("cost", Messages.get("workorders.parts_cost"),
                t -> Ui.money(t.value()), 110));
        table.addColumn(new DataTable.Col<>("wo", Messages.get("workorders.no"),
                t -> Ui.orDash(t.getWorkOrderNo()), 120));
        table.setLoader(p -> new com.medmaint.persistence.Page<>(txs, txs.size(), p, 50));
        table.refresh();
        return table;
    }

    private String txLabel(String code) {
        return switch (code) {
            case "ENTREE" -> "Entrée";
            case "SORTIE" -> "Sortie";
            case "USAGE" -> "Consommé";
            case "AJUSTEMENT" -> "Ajustement";
            case "TRANSFERT" -> "Transfert";
            default -> code;
        };
    }

    // ---------- Documents ----------

    private Node documentsTab() {
        return new com.medmaint.ui.pages.resources.DocumentListPane("EQUIPMENT", equipmentId);
    }

    // ---------- Coûts ----------

    private Node costsTab() {
        List<WorkOrder> wos = new WorkOrderRepo().findAll(
                new WorkOrderRepo.Filter(null, null, null, null, equipmentId, null, null, null, null), 500);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal labor = BigDecimal.ZERO;
        BigDecimal parts = BigDecimal.ZERO;
        int count = wos.size();
        for (WorkOrder w : wos) {
            total = total.add(nz(w.getTotalCost()));
            labor = labor.add(nz(w.getLaborCost()));
            parts = parts.add(nz(w.getPartsCost()));
        }

        GridPane g = grid();
        int r = 0;
        r = row(g, r, "Nombre d'interventions", String.valueOf(count));
        r = row(g, r, "Coût total main-d'œuvre", Ui.money(labor));
        r = row(g, r, "Coût total pièces", Ui.money(parts));
        r = row(g, r, "Coût total de maintenance", Ui.money(total));
        r = row(g, r, "Coût moyen par intervention",
                count == 0 ? "—" : Ui.money(total.divide(BigDecimal.valueOf(count), 2,
                        java.math.RoundingMode.HALF_UP)));

        Label cal = new Label(Messages.get("dashboard.calculated"));
        cal.getStyleClass().add("dim");
        VBox box = new VBox(10, g, cal);

        // Détail des coûts par intervention
        if (!wos.isEmpty()) {
            DataTable<CostRow> detail = new DataTable<>(Messages.get("common.none"), null);
            detail.setPageSize(25);
            detail.addColumn(new DataTable.Col<>("wo", Messages.get("workorders.no"),
                    CostRow::woNo, 110));
            detail.addColumn(new DataTable.Col<>("date", Messages.get("workorders.completed"),
                    CostRow::date, 130));
            detail.addColumn(new DataTable.Col<>("labor", Messages.get("workorders.labor_cost"),
                    CostRow::labor, 120));
            detail.addColumn(new DataTable.Col<>("parts", Messages.get("workorders.parts_cost"),
                    CostRow::parts, 120));
            detail.addColumn(new DataTable.Col<>("total", Messages.get("workorders.total_cost"),
                    CostRow::total, 130));
            List<CostRow> rows = wos.stream().map(w -> new CostRow(
                    w.getWoNo(), Ui.dateTime(w.getCompletedAt() != null ? w.getCompletedAt() : w.getOpenedAt()),
                    Ui.money(nz(w.getLaborCost())), Ui.money(nz(w.getPartsCost())),
                    Ui.money(nz(w.getTotalCost())))).toList();
            detail.setLoader(p -> new com.medmaint.persistence.Page<>(rows, rows.size(), p, 25));
            detail.refresh();
            box.getChildren().addAll(Ui.sectionLabel("common.total"), detail);
        }
        return box;
    }

    private record CostRow(String woNo, String date, String labor, String parts, String total) {
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---------- Activité ----------

    private Node activityTab() {
        List<AuditLog> logs = AppContext.get().audit()
                .forEntity("Équipement", String.valueOf(equipmentId));
        if (logs.isEmpty()) {
            return Ui.emptyState("Aucune activité enregistrée pour cet équipement.", null);
        }
        VBox list = new VBox(6);
        for (AuditLog a : logs) {
            Label action = Ui.h3(a.getAction() + " · " + Ui.orDash(a.getUserName()));
            Label when = Ui.muted(Ui.dateTime(a.getCreatedAt()) + " · " + Ui.orDash(a.getEntityLabel()));
            VBox item = new VBox(2, action, when);
            if (a.getOldValue() != null || a.getNewValue() != null) {
                Label chg = new Label("→ " + Ui.orDash(a.getOldValue()) + "  ⇒  " + Ui.orDash(a.getNewValue()));
                chg.getStyleClass().add("dim");
                chg.setWrapText(true);
                item.getChildren().add(chg);
            }
            list.getChildren().add(item);
        }
        return list;
    }

    // ---------- Helpers ----------

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(18);
        g.setVgap(7);
        ColumnConstraints c1 = new ColumnConstraints(230);
        ColumnConstraints c2 = new ColumnConstraints(520);
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    private int row(GridPane g, int r, String labelKey, String value) {
        Label l = new Label(labelKey);
        l.getStyleClass().add("field-label");
        Label v = new Label(Ui.orDash(value));
        v.setWrapText(true);
        g.add(l, 0, r);
        g.add(v, 1, r);
        return r + 1;
    }

    private Node backBtn() {
        Button b = new Button("‹ " + Messages.get("common.back"));
        b.getStyleClass().add("ghost");
        b.setOnAction(e -> MainShell.shellNavigate("equipment", null));
        return b;
    }

    private Button action(String label, Runnable onClick, Runnable after, boolean primary) {
        Button b = new Button(label);
        b.getStyleClass().add(primary ? "primary" : "ghost");
        b.setOnAction(e -> {
            onClick.run();
            if (after != null) after.run();
        });
        return b;
    }

    private static long parseId(String context) {
        if (context == null || context.isBlank()) return -1;
        String c = context.startsWith("detail:") ? context.substring(7) : context;
        try {
            return Long.parseLong(c);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
