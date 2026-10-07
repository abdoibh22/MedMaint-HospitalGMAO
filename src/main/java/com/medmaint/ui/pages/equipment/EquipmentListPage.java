package com.medmaint.ui.pages.equipment;

import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.EquipmentCategory;
import com.medmaint.domain.model.Department;
import com.medmaint.domain.model.Manufacturer;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.EquipmentRepo;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventaire des équipements : recherche, filtres (catégorie, service, statut,
 * fabricant, criticité, garantie, état maintenance), tri, pagination,
 * sélection multiple, export et actions (fiche, modifier, dupliquer,
 * archiver, signaler une panne).
 */
public class EquipmentListPage extends VBox implements com.medmaint.ui.navigation.ContextAware {

    private final DataTable<Equipment> table = new DataTable<>(Messages.get("equipment.empty"),
            Session.get().has(Permission.EQUIPMENT_CREATE)
                    ? PageHeader.actionButton(Messages.get("equipment.new"), true, this::createNew)
                    : null);

    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<EquipmentCategory> categoryFilter = new ComboBox<>();
    private final ComboBox<Department> departmentFilter = new ComboBox<>();
    private final ComboBox<String> criticalityFilter = new ComboBox<>();
    private final ComboBox<String> warrantyFilter = new ComboBox<>();
    private final ComboBox<String> pmStatusFilter = new ComboBox<>();

    private Long serviceScope; // filtrage par service pour les rôles restreints

    public EquipmentListPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        boolean canCreate = Session.get().has(Permission.EQUIPMENT_CREATE);
        PageHeader header = new PageHeader(Messages.get("equipment.title"));
        if (canCreate) {
            header.addAction(Messages.get("equipment.new"), true, this::createNew);
        }
        if (Session.get().has(Permission.EXPORT)) {
            header.addAction(Messages.get("common.export"), false, this::exportCsv);
        }
        getChildren().add(header);

        buildFilters();
        getChildren().add(filterBar());

        buildColumns();
        table.setPageSize(15);
        table.setLoader(this::load);
        getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        // Responsable de Service : ne voit que son service
        if (Session.get().role() == com.medmaint.security.Role.DEPT_MANAGER
                && Session.get().departmentId() != null) {
            serviceScope = Session.get().departmentId();
        }

        table.onDoubleClick(eq -> openDetail(eq));
        table.resetAndRefresh();
    }

    private void buildFilters() {
        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(240);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());

        statusFilter.getItems().add(null);
        for (EquipmentStatus s : EquipmentStatus.values()) {
            statusFilter.getItems().add(s.name());
        }
        statusFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String code) {
                if (code == null) return Messages.get("common.all_f");
                return EquipmentStatus.fromCode(code).label();
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        });
        statusFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(AppContext.get().references().allCategories());
        categoryFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(EquipmentCategory c) {
                return c == null ? Messages.get("common.all_f") : c.getName();
            }

            @Override
            public EquipmentCategory fromString(String string) {
                return null;
            }
        });
        categoryFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        departmentFilter.getItems().add(null);
        // REQUESTER n'a pas DEPARTMENT_VIEW : filtre masqué plutôt que crash
        if (Session.get().has(Permission.DEPARTMENT_VIEW)) {
            departmentFilter.getItems().addAll(AppContext.get().references().allDepartments());
        }
        departmentFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Department d) {
                return d == null ? Messages.get("common.all") : d.getName();
            }

            @Override
            public Department fromString(String string) {
                return null;
            }
        });
        departmentFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
        if (serviceScope != null) {
            departmentFilter.getItems().stream()
                    .filter(d -> d != null && d.getId() == serviceScope)
                    .findFirst().ifPresent(departmentFilter::setValue);
            departmentFilter.setDisable(true);
        }

        criticalityFilter.getItems().setAll(Messages.get("common.all"),
                "Faible", "Moyenne", "Haute", "Vitale");
        criticalityFilter.setValue(Messages.get("common.all"));
        criticalityFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        warrantyFilter.getItems().setAll(Messages.get("common.all"), "Sous garantie", "Hors garantie");
        warrantyFilter.setValue(Messages.get("common.all"));
        warrantyFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        pmStatusFilter.getItems().setAll(Messages.get("common.all"), "En retard", "À venir", "Sans plan");
        pmStatusFilter.setValue(Messages.get("common.all"));
        pmStatusFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
    }

    private Node filterBar() {
        Label l1 = Ui.muted(Messages.get("common.search"));
        Label l2 = Ui.muted(Messages.get("equipment.status"));
        Label l3 = Ui.muted(Messages.get("equipment.category"));
        Label l4 = Ui.muted(Messages.get("equipment.service"));
        Label l5 = Ui.muted(Messages.get("equipment.criticality"));
        Label l6 = Ui.muted("Garantie");
        Label l7 = Ui.muted(Messages.get("equipment.next_maintenance"));

        for (Label l : new Label[]{l1, l2, l3, l4, l5, l6, l7}) {
            l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        }

        VBox f1 = new VBox(2, l1, search);
        VBox f2 = new VBox(2, l2, statusFilter);
        VBox f3 = new VBox(2, l3, categoryFilter);
        VBox f4 = new VBox(2, l4, departmentFilter);
        VBox f5 = new VBox(2, l5, criticalityFilter);
        VBox f6 = new VBox(2, l6, warrantyFilter);
        VBox f7 = new VBox(2, l7, pmStatusFilter);

        Button reset = new Button(Messages.get("common.reset"));
        reset.getStyleClass().add("ghost");
        reset.setOnAction(e -> {
            search.clear();
            statusFilter.setValue(null);
            categoryFilter.setValue(null);
            if (!departmentFilter.isDisable()) departmentFilter.setValue(null);
            criticalityFilter.setValue(Messages.get("common.all"));
            warrantyFilter.setValue(Messages.get("common.all"));
            pmStatusFilter.setValue(Messages.get("common.all"));
            table.resetAndRefresh();
        });
        VBox f8 = new VBox(2, Ui.muted(" "), reset);

        HBox bar = new HBox(10, f1, f2, f3, f5, f6, f7, f8);
        if (Session.get().has(Permission.DEPARTMENT_VIEW)) {
            bar.getChildren().add(3, f4);
        }
        bar.setPadding(new Insets(4, 0, 4, 0));
        bar.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        return bar;
    }

    private void buildColumns() {
        table.addColumn(new DataTable.Col<>("code", Messages.get("equipment.code"),
                Equipment::getCode, 110));
        table.addColumn(new DataTable.Col<>("name", Messages.get("equipment.name"),
                Equipment::getName, 220));
        table.addColumn(new DataTable.Col<>("category", Messages.get("equipment.category"),
                Equipment::getCategoryName, 140));
        table.addColumn(new DataTable.Col<>("manufacturer", Messages.get("equipment.manufacturer"),
                Equipment::getManufacturerName, 120));
        table.addColumn(new DataTable.Col<>("model", Messages.get("equipment.model"),
                Equipment::getModel, 110));
        table.addColumn(new DataTable.Col<>("department", Messages.get("equipment.service"),
                Equipment::getDepartmentName, 140));
        table.addColumn(new DataTable.Col<>("location", Messages.get("equipment.location"),
                e -> Ui.orDash(e.getLocationName()), 130));
        table.addColumn(new DataTable.Col<>("status", Messages.get("equipment.status"),
                e -> e.statusEnum().label(), 140));
        table.addColumn(new DataTable.Col<>("criticality", Messages.get("equipment.criticality"),
                e -> e.criticalityEnum().label(), 100));
        table.addColumn(new DataTable.Col<>("lastMaintenance", Messages.get("equipment.last_maintenance"),
                e -> Ui.date(e.getLastMaintenanceDate()), 120));
        table.addColumn(new DataTable.Col<>("nextMaintenance", Messages.get("equipment.next_maintenance"),
                e -> Ui.date(e.getNextMaintenanceDate()), 120));

        List<DataTable.RowAction<Equipment>> actions = new ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.view"), "ghost",
                e -> openDetail(e)));
        if (Session.get().has(Permission.EQUIPMENT_UPDATE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("common.edit"), "ghost",
                    e -> edit(e)));
        }
        if (Session.get().has(Permission.REQUEST_CREATE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("equipment.action.new_request"), "danger",
                    e -> new com.medmaint.ui.pages.requests.RequestFormDialog(
                            com.medmaint.ui.pages.requests.RequestFormDialog.Mode.FROM_EQUIPMENT, e)
                    .showAndWait()));
        }
        table.addActionsColumn(actions);
    }

    private Page<Equipment> load(int page) {
        EquipmentRepo.Filter f = buildFilter();
        return AppContext.get().equipment().search(f, page, table.pageSize(),
                table.sortCol(), table.sortDir(), Session.get().isAdmin());
    }

    private EquipmentRepo.Filter buildFilter() {
        String status = statusFilter.getValue();
        EquipmentCategory cat = categoryFilter.getValue();
        Department dep = departmentFilter.getValue();
        String crit = criticalityFilter.getValue();
        String critCode = null;
        if (crit != null && !crit.equals(Messages.get("common.all"))) {
            critCode = switch (crit) {
                case "Faible" -> "FAIBLE";
                case "Moyenne" -> "MOYENNE";
                case "Haute" -> "HAUTE";
                case "Vitale" -> "VITALE";
                default -> null;
            };
        }
        String warranty = warrantyFilter.getValue();
        if (warranty != null && warranty.equals(Messages.get("common.all"))) {
            warranty = null;
        }
        String pm = pmStatusFilter.getValue();
        if (pm != null && pm.equals(Messages.get("common.all"))) {
            pm = null;
        }
        Long scope = serviceScope;
        return new EquipmentRepo.Filter(
                search.getText(),
                cat != null ? cat.getId() : null,
                dep != null ? dep.getId() : null,
                status,
                null,
                critCode,
                warranty,
                pm,
                false,
                scope);
    }

    private void openDetail(Equipment e) {
        MainShell.shellNavigate("equipment", "detail:" + e.getId());
    }

    /** Contexte « detail:<id> » : affiche la fiche complète de l'équipement. */
    @Override
    public javafx.scene.Node openContext(String context) {
        if (context == null || !context.startsWith("detail:")) {
            return this;
        }
        return new EquipmentDetailPage(context);
    }

    private void createNew() {
        EquipmentFormDialog dlg = new EquipmentFormDialog(null);
        dlg.showAndWait();
        table.refresh();
    }

    private void edit(Equipment e) {
        EquipmentFormDialog dlg = new EquipmentFormDialog(e);
        dlg.showAndWait();
        table.refresh();
    }

    public void refreshTable() {
        table.refresh();
    }

    private void exportCsv() {
        try {
            List<Equipment> rows = AppContext.get().equipment()
                    .search(buildFilter(), 1, 10000, table.sortCol(), table.sortDir(), false).items();
            java.util.List<List<String>> data = new ArrayList<>();
            for (Equipment e : rows) {
                data.add(List.of(e.getCode(), e.getName(), Ui.orDash(e.getCategoryName()),
                        Ui.orDash(e.getManufacturerName()), Ui.orDash(e.getModel()),
                        Ui.orDash(e.getDepartmentName()), e.statusEnum().label(),
                        e.criticalityEnum().label(), Ui.date(e.getNextMaintenanceDate())));
            }
            com.medmaint.service.ReportService.ReportData rd =
                    new com.medmaint.service.ReportService.ReportData(
                            Messages.get("equipment.title"),
                            List.of("Code", "Équipement", "Catégorie", "Fabricant", "Modèle",
                                    "Service", "Statut", "Criticité", "Proch. maintenance"),
                            data);
            java.nio.file.Path out = AppContext.get().reports().exportCsv(rd);
            Toast.success(Messages.get("reports.export_success", out.toString()));
        } catch (RuntimeException ex) {
            Toast.error(ex.getMessage());
        }
    }

    /** Confirme puis supprime (admin uniquement). */
    public static void confirmDelete(Equipment e, Runnable onDone) {
        if (ConfirmDialog.confirm(Messages.get("equipment.delete_confirm"))) {
            try {
                AppContext.get().equipment().delete(e.getId());
                Toast.success(Messages.get("msg.deleted"));
                if (onDone != null) onDone.run();
            } catch (RuntimeException ex) {
                Toast.error(ex.getMessage());
            }
        }
    }

    /** Utilisé par les dialogs pour rafraîchir après création. */
    public static void toastCreated() {
        Toast.success(Messages.get("msg.created"));
    }
}
