package com.medmaint.ui.pages.requests;

import com.medmaint.domain.enums.Priority;
import com.medmaint.domain.enums.RequestStatus;
import com.medmaint.domain.model.InterventionRequest;
import com.medmaint.domain.model.Technician;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.RequestRepo;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Demandes d'intervention : liste filtrable (statut, priorité, service,
 * technicien, dates), actions d'approbation / rejet / affectation,
 * ouverture du détail par double-clic.
 */
public class RequestsPage extends VBox implements com.medmaint.ui.navigation.ContextAware {

    private final DataTable<InterventionRequest> table =
            new DataTable<>(Messages.get("requests.empty"),
                    Session.get().has(Permission.REQUEST_CREATE)
                            ? PageHeader.actionButton(Messages.get("requests.new"), true, this::createNew)
                            : null);

    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> priorityFilter = new ComboBox<>();
    private final ComboBox<com.medmaint.domain.model.Department> deptFilter = new ComboBox<>();

    private final VBox detailHost = new VBox();
    private InterventionRequest selected;

    public RequestsPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("requests.title"));
        if (Session.get().has(Permission.REQUEST_CREATE)) {
            header.addAction(Messages.get("requests.new"), true, this::createNew);
        }
        getChildren().add(header);

        buildFilters();
        getChildren().add(filterBar());
        buildColumns();

        table.setPageSize(15);
        table.setLoader(this::load);
        getChildren().add(table);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        // Vue détail contextuelle (context = "detail:<id>")
        detailHost.setPadding(new Insets(0));
        detailHost.setVisible(false);
        getChildren().add(detailHost);

        table.onDoubleClick(r -> showDetail(r));
        table.resetAndRefresh();
    }

    /** Appelé par le routeur avec un contexte « detail:<id> ». */
    @Override
    public Node openContext(String context) {
        if (context == null || !context.startsWith("detail:")) {
            return this;
        }
        try {
            long id = Long.parseLong(context.substring(7));
            InterventionRequest r = AppContext.get().requests().get(id);
            if (r != null) {
                showDetail(r);
            }
        } catch (RuntimeException ignored) {
            // contexte invalide → liste
        }
        return this;
    }

    private void buildFilters() {
        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(230);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());

        statusFilter.getItems().add(null);
        for (RequestStatus s : RequestStatus.values()) {
            statusFilter.getItems().add(s.name());
        }
        statusFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String c) {
                return c == null ? Messages.get("common.all_f") : RequestStatus.fromCode(c).label();
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        statusFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        priorityFilter.getItems().add(null);
        for (Priority p : Priority.values()) {
            priorityFilter.getItems().add(p.name());
        }
        priorityFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String c) {
                return c == null ? Messages.get("common.all_f") : Priority.fromCode(c).label();
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        priorityFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        deptFilter.getItems().add(null);
        // REQUESTER n'a pas DEPARTMENT_VIEW : filtre masqué plutôt que crash
        if (Session.get().has(Permission.DEPARTMENT_VIEW)) {
            deptFilter.getItems().addAll(AppContext.get().references().allDepartments());
        }
        deptFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(com.medmaint.domain.model.Department d) {
                return d == null ? Messages.get("common.all") : d.getName();
            }

            @Override
            public com.medmaint.domain.model.Department fromString(String s) {
                return null;
            }
        });
        deptFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
    }

    private Node filterBar() {
        Button reset = new Button(Messages.get("common.reset"));
        reset.getStyleClass().add("ghost");
        reset.setOnAction(e -> {
            search.clear();
            statusFilter.setValue(null);
            priorityFilter.setValue(null);
            deptFilter.setValue(null);
            table.resetAndRefresh();
        });
        HBox bar = new HBox(10, labeled(Messages.get("common.search"), search),
                labeled(Messages.get("requests.status"), statusFilter),
                labeled(Messages.get("requests.priority"), priorityFilter));
        if (Session.get().has(Permission.DEPARTMENT_VIEW)) {
            bar.getChildren().add(labeled(Messages.get("requests.department"), deptFilter));
        }
        bar.getChildren().add(labeled(" ", reset));
        bar.setPadding(new Insets(4, 0, 4, 0));
        bar.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        return bar;
    }

    private Node labeled(String label, Node field) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        return new VBox(2, l, field);
    }

    private void buildColumns() {
        table.addColumn(new DataTable.Col<>("no", Messages.get("requests.no"),
                InterventionRequest::getRequestNo, 100));
        table.addColumn(new DataTable.Col<>("equipment", Messages.get("requests.equipment"),
                r -> r.getEquipmentCode() + " — " + r.getEquipmentName(), 240));
        table.addColumn(new DataTable.Col<>("department", Messages.get("requests.department"),
                InterventionRequest::getDepartmentName, 150));
        table.addColumn(new DataTable.Col<>("requester", Messages.get("requests.requester"),
                InterventionRequest::getRequesterName, 150));
        table.addColumn(new DataTable.Col<>("date", Messages.get("requests.date"),
                r -> Ui.dateTime(r.getReportedAt()), 130));
        table.addColumn(new DataTable.Col<>("priority", Messages.get("requests.priority"),
                r -> r.priorityEnum().label(), 100));
        table.addColumn(new DataTable.Col<>("status", Messages.get("requests.status"),
                r -> r.statusEnum().label(), 130));
        table.addColumn(new DataTable.Col<>("technician", Messages.get("requests.technician"),
                r -> Ui.orDash(r.getAssignedTechnicianName()), 150));

        List<DataTable.RowAction<InterventionRequest>> actions = new java.util.ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.view"), "ghost", this::showDetail));
        if (Session.get().has(Permission.REQUEST_APPROVE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("requests.action.approve"), "ghost", r -> {
                try {
                    AppContext.get().requests().approve(r.getId(), null);
                    Toast.success(Messages.get("msg.action_done"));
                    table.refresh();
                } catch (RuntimeException ex) {
                    Toast.error(ex.getMessage());
                }
            }));
            actions.add(new DataTable.RowAction<>(Messages.get("requests.action.reject"), "danger", this::reject));
        }
        if (Session.get().has(Permission.REQUEST_ASSIGN)) {
            actions.add(new DataTable.RowAction<>(Messages.get("requests.action.assign"), "primary", this::assign));
        }
        table.addActionsColumn(actions);
    }

    private Page<InterventionRequest> load(int page) {
        return AppContext.get().requests().search(filter(), page, table.pageSize(),
                table.sortCol(), table.sortDir());
    }

    private RequestRepo.Filter filter() {
        var dept = deptFilter.getValue();
        Long scope = null;
        // Responsable de Service : ne voit que les demandes de son service
        if (Session.get().role() == com.medmaint.security.Role.DEPT_MANAGER
                && Session.get().departmentId() != null) {
            scope = Session.get().departmentId();
        }
        // Demandeur : uniquement ses propres demandes
        Long requesterScope = Session.get().role() == com.medmaint.security.Role.REQUESTER
                ? Session.get().userId() : null;
        return new RequestRepo.Filter(
                search.getText(),
                statusFilter.getValue(),
                priorityFilter.getValue(),
                dept != null ? Long.valueOf(dept.getId()) : scope,
                null, null,
                requesterScope, null, null);
    }

    private void createNew() {
        new RequestFormDialog(RequestFormDialog.Mode.CREATE, null).showAndWait();
        table.refresh();
    }

    private void showDetail(InterventionRequest r) {
        selected = r;
        // Détail défilant : la demande entière (description, symptômes,
        // historique, actions) reste accessible sur tous les écrans.
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(buildDetail(r));
        sp.setFitToWidth(true);
        sp.getStyleClass().add("detail-scroll");
        detailHost.getChildren().setAll(sp);
        javafx.scene.layout.VBox.setVgrow(sp, javafx.scene.layout.Priority.ALWAYS);
        detailHost.setVisible(true);
        // Le détail prend toute la place : la table est masquée jusqu'à la fermeture
        table.setVisible(false);
        table.setManaged(false);
    }

    private Node buildDetail(InterventionRequest r) {
        VBox box = new VBox(10);
        box.getStyleClass().add("panel");
        box.setPadding(new Insets(14));

        Button close = new Button("✕ " + Messages.get("common.close"));
        close.getStyleClass().add("ghost");
        close.setOnAction(e -> {
            detailHost.setVisible(false);
            detailHost.getChildren().clear();
            table.setVisible(true);
            table.setManaged(true);
            table.refresh();
        });

        Label no = Ui.h2(r.getRequestNo() + " · " + r.getEquipmentCode() + " — " + r.getEquipmentName());
        Label status = Ui.badge(r.statusEnum().label(), r.statusEnum().tone());
        Label prio = Ui.badge(r.priorityEnum().label(), r.priorityEnum().tone());
        HBox head = new HBox(10, no, status, prio);
        head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Bannière panne critique
        if (r.isCritical()) {
            Label banner = new Label(Messages.get("requests.critical_banner"));
            banner.getStyleClass().add("alert-danger");
            banner.setWrapText(true);
            box.getChildren().add(banner);
        }

        var g = new javafx.scene.layout.GridPane();
        g.setHgap(16);
        g.setVgap(6);
        int row = 0;
        row = kv(g, row, Messages.get("requests.requester"), r.getRequesterName());
        row = kv(g, row, Messages.get("requests.department"), r.getDepartmentName());
        row = kv(g, row, Messages.get("requests.date"), Ui.dateTime(r.getReportedAt()));
        row = kv(g, row, Messages.get("requests.availability"), r.getAvailability());
        row = kv(g, row, "Impact sécurité", r.isSafetyImpact() ? Messages.get("common.yes") : Messages.get("common.no"));
        row = kv(g, row, Messages.get("requests.technician"), r.getAssignedTechnicianName());
        row = kv(g, row, Messages.get("requests.workorder"), r.getWorkOrderNo());
        if (r.getRejectionReason() != null) {
            row = kv(g, row, Messages.get("requests.reject_reason"), r.getRejectionReason());
        }

        Label pl = new Label(Messages.get("requests.problem"));
        pl.getStyleClass().add("field-label");
        Label pv = new Label(Ui.orDash(r.getProblemDescription()));
        pv.setWrapText(true);
        Label sl = new Label(Messages.get("requests.symptoms"));
        sl.getStyleClass().add("field-label");
        Label sv = new Label(Ui.orDash(r.getSymptoms()));
        sv.setWrapText(true);

        HBox actions = new HBox(8);
        if (Session.get().has(Permission.REQUEST_APPROVE) && r.statusEnum().isOpen()) {
            actions.getChildren().add(btn(Messages.get("requests.action.approve"), "primary", () -> {
                try {
                    AppContext.get().requests().approve(r.getId(), null);
                    Toast.success(Messages.get("msg.action_done"));
                    table.refresh();
                    showDetail(AppContext.get().requests().get(r.getId()));
                } catch (RuntimeException ex) {
                    Toast.error(ex.getMessage());
                }
            }));
            actions.getChildren().add(btn(Messages.get("requests.action.reject"), "danger", () -> reject(r)));
        }
        if (Session.get().has(Permission.REQUEST_ASSIGN) && r.statusEnum().isOpen()) {
            actions.getChildren().add(btn(Messages.get("requests.action.assign"), "ghost", () -> assign(r)));
        }
        if (r.getWorkOrderId() != null) {
            actions.getChildren().add(btn(Messages.get("common.view") + " OT", "ghost",
                    () -> MainShell.shellNavigate("workorders", "detail:" + r.getWorkOrderId())));
        }

        box.getChildren().addAll(head, g, pl, pv, sl, sv, actions, close);
        return box;
    }

    private int kv(javafx.scene.layout.GridPane g, int row, String k, String v) {
        Label l = new Label(k);
        l.getStyleClass().add("field-label");
        Label val = new Label(Ui.orDash(v));
        val.setWrapText(true);
        g.add(l, 0, row);
        g.add(val, 1, row);
        return row + 1;
    }

    private void reject(InterventionRequest r) {
        FormDialog<Object> dlg = new FormDialog<>(Messages.get("requests.reject_dialog")) {
        };
        TextArea reason = FormDialog.textArea(null, 3);
        reason.setPromptText(Messages.get("requests.reject_reason"));
        dlg.addWideField(Messages.get("requests.reject_reason"), reason, true);
        dlg.setOnSave(b -> {
            try {
                AppContext.get().requests().reject(r.getId(), reason.getText());
                Toast.success(Messages.get("msg.action_done"));
                dlg.getDialogPane().getScene().getWindow().hide();
                table.refresh();
            } catch (com.medmaint.security.ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private void assign(InterventionRequest r) {
        List<Technician> techs = AppContext.get().technicians().all().stream()
                .filter(t -> "ACTIF".equalsIgnoreCase(t.getStatus()))
                .toList();
        if (techs.isEmpty()) {
            Toast.warning("Aucun technicien disponible.");
            return;
        }
        FormDialog<Object> dlg = new FormDialog<>(Messages.get("requests.assign_dialog")) {
        };
        ComboBox<Technician> combo = FormDialog.combo(techs, null);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Technician t) {
                return t == null ? Messages.get("common.select_option")
                        : t.getFullName() + " · " + Ui.orDash(t.getSpecialty());
            }

            @Override
            public Technician fromString(String s) {
                return null;
            }
        });
        dlg.addField("requests.technician", combo, true);
        dlg.setOnSave(b -> {
            if (combo.getValue() == null) {
                dlg.showError(Messages.get("validation.required"));
                return;
            }
            try {
                AppContext.get().requests().assign(r.getId(), combo.getValue().getId());
                Toast.success(Messages.get("msg.action_done"));
                dlg.getDialogPane().getScene().getWindow().hide();
                table.refresh();
                showDetail(AppContext.get().requests().get(r.getId()));
            } catch (com.medmaint.security.ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private Button btn(String label, String style, Runnable action) {
        Button b = new Button(label);
        b.getStyleClass().add(style);
        b.setOnAction(e -> action.run());
        return b;
    }

    public void refreshTable() {
        table.refresh();
    }
}
