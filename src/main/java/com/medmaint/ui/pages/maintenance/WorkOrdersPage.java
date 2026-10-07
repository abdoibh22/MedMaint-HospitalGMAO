package com.medmaint.ui.pages.maintenance;

import com.medmaint.domain.enums.WorkOrderStatus;
import com.medmaint.domain.model.SparePart;
import com.medmaint.domain.model.StockTransaction;
import com.medmaint.domain.model.Technician;
import com.medmaint.domain.model.WorkOrder;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.WorkOrderRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Interventions (ordres de travail) : liste filtrable + détail avec workflow
 * complet (approuver → planifier → démarrer → pièces → résoudre → valider →
 * clôturer) et timeline d'étapes.
 */
public class WorkOrdersPage extends VBox implements com.medmaint.ui.navigation.ContextAware {

    private final DataTable<WorkOrder> table = new DataTable<>(Messages.get("workorders.empty"), null);

    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> priorityFilter = new ComboBox<>();
    private final ComboBox<Technician> techFilter = new ComboBox<>();

    private final VBox detailHost = new VBox();

    public WorkOrdersPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("workorders.title"));
        getChildren().add(header);

        buildFilters();
        getChildren().add(filterBar());
        buildColumns();
        table.setPageSize(15);
        table.setLoader(this::load);
        getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        detailHost.setVisible(false);
        getChildren().add(detailHost);

        table.onDoubleClick(this::openDetail);
        table.resetAndRefresh();
    }

    @Override
    public Node openContext(String context) {
        if (context == null || !context.startsWith("detail:")) return this;
        try {
            long id = Long.parseLong(context.substring(7));
            WorkOrder w = AppContext.get().workOrders().get(id);
            if (w != null) {
                openDetail(w);
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
        for (WorkOrderStatus s : WorkOrderStatus.values()) statusFilter.getItems().add(s.name());
        statusFilter.setConverter(str(s -> s == null
                ? Messages.get("common.all_f") : WorkOrderStatus.fromCode(s).label()));
        statusFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        priorityFilter.getItems().add(null);
        for (var p : com.medmaint.domain.enums.Priority.values()) priorityFilter.getItems().add(p.name());
        priorityFilter.setConverter(str(s -> s == null
                ? Messages.get("common.all_f")
                : com.medmaint.domain.enums.Priority.fromCode(s).label()));
        priorityFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        techFilter.getItems().add(null);
        // DEPT_MANAGER / REQUESTER n'ont pas TECHNICIAN_VIEW : filtre masqué plutôt que crash
        if (Session.get().has(Permission.TECHNICIAN_VIEW)) {
            techFilter.getItems().addAll(AppContext.get().technicians().all());
        }
        techFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Technician t) {
                return t == null ? Messages.get("common.all") : t.getFullName();
            }

            @Override
            public Technician fromString(String s) {
                return null;
            }
        });
        techFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
    }

    private javafx.util.StringConverter<String> str(java.util.function.Function<String, String> f) {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(String s) {
                return f.apply(s);
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        };
    }

    private Node filterBar() {
        Button reset = new Button(Messages.get("common.reset"));
        reset.getStyleClass().add("ghost");
        reset.setOnAction(e -> {
            search.clear();
            statusFilter.setValue(null);
            priorityFilter.setValue(null);
            techFilter.setValue(null);
            table.resetAndRefresh();
        });
        HBox bar = new HBox(10, lbl(Messages.get("common.search"), search),
                lbl(Messages.get("workorders.status"), statusFilter),
                lbl(Messages.get("workorders.priority"), priorityFilter));
        if (Session.get().has(Permission.TECHNICIAN_VIEW)) {
            bar.getChildren().add(lbl(Messages.get("workorders.technician"), techFilter));
        }
        bar.getChildren().add(lbl(" ", reset));
        bar.setPadding(new Insets(4, 0, 4, 0));
        bar.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        return bar;
    }

    private Node lbl(String t, Node f) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        return new VBox(2, l, f);
    }

    private void buildColumns() {
        table.addColumn(new DataTable.Col<>("no", Messages.get("workorders.no"),
                WorkOrder::getWoNo, 100));
        table.addColumn(new DataTable.Col<>("equipment", Messages.get("workorders.equipment"),
                w -> w.getEquipmentCode() + " — " + w.getEquipmentName(), 240));
        table.addColumn(new DataTable.Col<>("request", Messages.get("workorders.request"),
                w -> Ui.orDash(w.getRequestNo()), 110));
        table.addColumn(new DataTable.Col<>("technician", Messages.get("workorders.technician"),
                w -> Ui.orDash(w.getTechnicianName()), 150));
        table.addColumn(new DataTable.Col<>("priority", Messages.get("workorders.priority"),
                w -> w.priorityEnum().label(), 100));
        table.addColumn(new DataTable.Col<>("status", Messages.get("workorders.status"),
                w -> w.statusEnum().label(), 140));
        table.addColumn(new DataTable.Col<>("opened", Messages.get("workorders.opened"),
                w -> Ui.dateTime(w.getOpenedAt()), 130));
        table.addColumn(new DataTable.Col<>("scheduled", Messages.get("workorders.scheduled"),
                w -> Ui.dateTime(w.getScheduledAt()), 130));
        table.addColumn(new DataTable.Col<>("cost", Messages.get("workorders.total_cost"),
                w -> w.getTotalCost() == null ? "—" : Ui.money(w.getTotalCost()), 110));

        List<DataTable.RowAction<WorkOrder>> actions = new java.util.ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.view"), "ghost", this::openDetail));
        if (Session.get().has(Permission.WORKORDER_EXECUTE)) {
            actions.add(new DataTable.RowAction<>("▶", "ghost", w -> transition(w, "start")));
        }
        if (Session.get().has(Permission.WORKORDER_VALIDATE)) {
            actions.add(new DataTable.RowAction<>("✓", "primary", w -> transition(w, "validate")));
        }
        table.addActionsColumn(actions);
    }

    private Page<WorkOrder> load(int page) {
        Long techScope = null;
        // Technicien : ne voit que ses propres interventions
        if (Session.get().role() == com.medmaint.security.Role.TECHNICIAN) {
            techScope = AppContext.get().technicians().all().stream()
                    .filter(t -> t.getUserId() != null && t.getUserId() == Session.get().userId())
                    .map(Technician::getId).findFirst().orElse(-1L);
        }
        var f = new WorkOrderRepo.Filter(
                search.getText(), statusFilter.getValue(), priorityFilter.getValue(),
                techFilter.getValue() != null
                        ? Long.valueOf(techFilter.getValue().getId()) : techScope,
                null, null, null, null, null);
        return AppContext.get().workOrders().search(f, page, table.pageSize(),
                table.sortCol(), table.sortDir());
    }

    // ---------- Détail ----------

    private void openDetail(WorkOrder w) {
        // Détail défilant : toutes les sections (timeline, exécution, pièces…)
        // restent accessibles même sur un écran de faible hauteur.
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(buildDetail(w));
        sp.setFitToWidth(true);
        sp.getStyleClass().add("detail-scroll");
        detailHost.getChildren().setAll(sp);
        VBox.setVgrow(sp, javafx.scene.layout.Priority.ALWAYS);
        detailHost.setVisible(true);
        VBox.setVgrow(detailHost, javafx.scene.layout.Priority.ALWAYS);
        // Le détail prend toute la place : la table est masquée jusqu'à la fermeture
        table.setVisible(false);
        table.setManaged(false);
    }

    private Node buildDetail(WorkOrder w) {
        VBox box = new VBox(12);
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

        Label no = Ui.h2(w.getWoNo() + " · " + w.getEquipmentCode() + " — " + w.getEquipmentName());
        Label status = Ui.badge(w.statusEnum().label(), w.statusEnum().tone());
        Label prio = Ui.badge(w.priorityEnum().label(), w.priorityEnum().tone());
        Label req = Ui.muted("Demande : " + Ui.orDash(w.getRequestNo()));
        HBox head = new HBox(12, no, status, prio, req);
        head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Timeline du workflow
        box.getChildren().add(timeline(w));

        // Identification / affectation
        var g = new javafx.scene.layout.GridPane();
        g.setHgap(16);
        g.setVgap(6);
        int r = 0;
        r = kv(g, r, Messages.get("workorders.technician"), w.getTechnicianName());
        r = kv(g, r, Messages.get("workorders.team"), w.getTeam());
        r = kv(g, r, Messages.get("workorders.opened"), Ui.dateTime(w.getOpenedAt()));
        r = kv(g, r, Messages.get("workorders.scheduled"), Ui.dateTime(w.getScheduledAt()));
        r = kv(g, r, Messages.get("workorders.started"), Ui.dateTime(w.getStartedAt()));
        r = kv(g, r, Messages.get("workorders.completed"), Ui.dateTime(w.getCompletedAt()));
        r = kv(g, r, Messages.get("workorders.validated"), Ui.dateTime(w.getValidatedAt()));
        r = kv(g, r, Messages.get("workorders.labor_hours"),
                w.getLaborHours() == null ? null : w.getLaborHours().toPlainString());
        r = kv(g, r, Messages.get("workorders.downtime"),
                w.getDowntimeHours() == null ? null : w.getDowntimeHours().toPlainString());
        r = kv(g, r, Messages.get("workorders.total_cost"),
                w.getTotalCost() == null ? null : Ui.money(w.getTotalCost()));
        box.getChildren().add(g);

        // Champs d'exécution (modifiables selon permission)
        box.getChildren().add(executionSection(w));

        // Pièces utilisées
        box.getChildren().add(partsSection(w));

        // Documents
        Label dl = Ui.sectionLabel("equipment.tab.documents");
        box.getChildren().addAll(dl,
                new com.medmaint.ui.pages.resources.DocumentListPane("WORK_ORDER", w.getId()));

        // Actions de workflow
        box.getChildren().add(actionsRow(w));
        box.getChildren().add(close);
        return box;
    }

    private Node timeline(WorkOrder w) {
        String[] steps = {"workorders.step.request", "workorders.step.approval",
                "workorders.step.assignment", "workorders.step.scheduling",
                "workorders.step.in_progress", "workorders.step.waiting_parts",
                "workorders.step.resolved", "workorders.step.validation",
                "workorders.step.closed"};
        WorkOrderStatus st = w.statusEnum();
        int current = switch (st) {
            case DEMANDE -> 0;
            case APPROUVEE -> 1;
            case ASSIGNEE -> 2;
            case PLANIFIEE -> 3;
            case EN_COURS -> 4;
            case EN_ATTENTE_PIECE -> 5;
            case RESOLUE -> 6;
            case VALIDEE -> 7;
            case CLOTUREE -> 8;
        };
        HBox row = new HBox(4);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        for (int i = 0; i < steps.length; i++) {
            Label dot = new Label();
            dot.getStyleClass().add("timeline-dot");
            if (i < current) dot.getStyleClass().add("done");
            if (i == current) dot.getStyleClass().add("current");
            Label txt = new Label(Messages.get(steps[i]));
            txt.setStyle("-fx-font-size: 10.5px; -fx-text-fill: "
                    + (i <= current ? "#0f4c5c" : "#9aa5b1") + "; -fx-font-weight: bold;");
            VBox step = new VBox(3, dot, txt);
            step.setAlignment(javafx.geometry.Pos.CENTER);
            step.setPrefWidth(92);
            row.getChildren().add(step);
            if (i < steps.length - 1) {
                javafx.scene.layout.Region line = new javafx.scene.layout.Region();
                line.setMinHeight(2);
                line.setPrefHeight(2);
                line.setPrefWidth(10);
                line.setStyle("-fx-background-color: " + (i < current ? "#1e7e34" : "#e1e5ea") + ";");
                HBox.setHgrow(line, Priority.ALWAYS);
                javafx.scene.layout.VBox.setVgrow(line, Priority.NEVER);
                row.getChildren().add(line);
            }
        }
        return new VBox(row);
    }

    private Node executionSection(WorkOrder w) {
        boolean canEdit = Session.get().has(Permission.WORKORDER_EXECUTE)
                && (Session.get().role() != com.medmaint.security.Role.TECHNICIAN
                || ownsTechnician(w));

        VBox box = new VBox(8);
        box.getChildren().add(Ui.sectionLabel("workorders.tab.intervention"));

        TextField failureType = FormDialog.textField(w.getFailureType());
        TextArea diagnosis = FormDialog.textArea(w.getDiagnosis(), 2);
        TextArea cause = FormDialog.textArea(w.getFailureCause(), 2);
        TextArea corrective = FormDialog.textArea(w.getCorrectiveAction(), 2);
        TextArea work = FormDialog.textArea(w.getWorkPerformed(), 3);
        TextField labor = FormDialog.textField(w.getLaborHours() == null ? "" : w.getLaborHours().toPlainString());
        TextField downtime = FormDialog.textField(w.getDowntimeHours() == null ? "" : w.getDowntimeHours().toPlainString());
        TextArea comments = FormDialog.textArea(w.getComments(), 2);

        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(12);
        g.setVgap(8);
        g.add(fieldLabel(Messages.get("workorders.failure_type")), 0, 0);
        g.add(failureType, 1, 0);
        g.add(fieldLabel(Messages.get("workorders.diagnosis")), 0, 1);
        g.add(diagnosis, 1, 1);
        g.add(fieldLabel(Messages.get("workorders.cause")), 0, 2);
        g.add(cause, 1, 2);
        g.add(fieldLabel(Messages.get("workorders.corrective_action")), 0, 3);
        g.add(corrective, 1, 3);
        g.add(fieldLabel(Messages.get("workorders.work_performed")), 0, 4);
        g.add(work, 1, 4);
        g.add(fieldLabel(Messages.get("workorders.labor_hours")), 0, 5);
        g.add(labor, 1, 5);
        g.add(fieldLabel(Messages.get("workorders.downtime")), 0, 6);
        g.add(downtime, 1, 6);
        g.add(fieldLabel(Messages.get("workorders.comments")), 0, 7);
        g.add(comments, 1, 7);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints(180);
        javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints(560);
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        for (javafx.scene.Node n : g.getChildren()) {
            n.setDisable(!canEdit);
        }

        Button save = new Button(Messages.get("common.save"));
        save.getStyleClass().add("primary");
        save.setDisable(!canEdit);
        save.setOnAction(e -> {
            try {
                AppContext.get().workOrders().saveExecution(w.getId(),
                        diagnosis.getText(), cause.getText(), corrective.getText(),
                        work.getText(), failureType.getText(),
                        parse(labor), parse(downtime), comments.getText());
                Toast.success(Messages.get("msg.saved"));
                refreshDetail(w);
            } catch (RuntimeException ex) {
                Toast.error(ex.getMessage());
            }
        });

        box.getChildren().addAll(g, save);
        return box;
    }

    private boolean ownsTechnician(WorkOrder w) {
        return AppContext.get().technicians().all().stream()
                .anyMatch(t -> t.getUserId() != null && t.getUserId() == Session.get().userId()
                        && w.getTechnicianId() != null && t.getId() == w.getTechnicianId());
    }

    private Node partsSection(WorkOrder w) {
        VBox box = new VBox(6);
        box.getChildren().add(Ui.sectionLabel("workorders.tab.parts"));

        List<StockTransaction> txs = AppContext.get().workOrders().partsOf(w.getId());
        if (txs.isEmpty()) {
            box.getChildren().add(Ui.muted("Aucune pièce consommée."));
        } else {
            for (StockTransaction t : txs) {
                Label l = new Label(t.getPartNo() + " — " + t.getPartName()
                        + "  × " + t.getQuantity() + "  ·  " + Ui.money(t.value())
                        + (t.getNote() != null ? "  ·  " + t.getNote() : ""));
                box.getChildren().add(l);
            }
        }

        if (Session.get().has(Permission.WORKORDER_EXECUTE)) {
            Button add = new Button(Messages.get("workorders.action.consume_part"));
            add.getStyleClass().add("ghost");
            add.setOnAction(e -> consumePartDialog(w));
            box.getChildren().add(add);
        }
        return box;
    }

    private void consumePartDialog(WorkOrder w) {
        List<SparePart> parts = AppContext.get().stock().all();
        if (parts.isEmpty()) {
            Toast.warning(Messages.get("parts.empty"));
            return;
        }
        FormDialog<Object> dlg = new FormDialog<>(Messages.get("workorders.action.consume_part")) {
        };
        ComboBox<SparePart> combo = FormDialog.combo(parts, null);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(SparePart p) {
                return p == null ? Messages.get("common.select_option")
                        : p.getPartNo() + " — " + p.getName() + " (stock : " + p.getQuantity() + ")";
            }

            @Override
            public SparePart fromString(String s) {
                return null;
            }
        });
        TextField qty = FormDialog.textField("1");
        CheckBox allowNeg = new CheckBox("Autoriser le stock négatif (admin)");
        allowNeg.setVisible(Session.get().isAdmin());
        allowNeg.setManaged(Session.get().isAdmin());
        dlg.addField("parts.part_no", combo, true);
        dlg.addField("parts.quantity", qty, true);
        dlg.addRaw(allowNeg);
        dlg.setOnSave(b -> {
            if (combo.getValue() == null) {
                dlg.showError(Messages.get("validation.required"));
                return;
            }
            int quantity;
            try {
                quantity = Integer.parseInt(qty.getText().trim());
            } catch (NumberFormatException nfe) {
                dlg.showError(Messages.get("validation.number"));
                return;
            }
            try {
                AppContext.get().workOrders().consumePart(w.getId(), combo.getValue().getId(),
                        quantity, allowNeg.isSelected(), null);
                Toast.success(Messages.get("msg.action_done"));
                dlg.getDialogPane().getScene().getWindow().hide();
                refreshDetail(w);
                table.refresh();
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private Node actionsRow(WorkOrder w) {
        HBox row = new HBox(8);
        WorkOrderStatus st = w.statusEnum();

        if (Session.get().has(Permission.REQUEST_APPROVE) && st == WorkOrderStatus.DEMANDE) {
            row.getChildren().add(btn(Messages.get("workorders.action.approve"), "primary",
                    () -> transition(w, "approve")));
        }
        if (Session.get().has(Permission.WORKORDER_UPDATE) && st.ordinal() <= 3) {
            row.getChildren().add(btn(Messages.get("workorders.action.schedule"), "ghost",
                    () -> scheduleDialog(w)));
        }
        if (Session.get().has(Permission.WORKORDER_EXECUTE)
                && (st == WorkOrderStatus.ASSIGNEE || st == WorkOrderStatus.PLANIFIEE
                || st == WorkOrderStatus.APPROUVEE)) {
            row.getChildren().add(btn(Messages.get("workorders.action.start"), "primary",
                    () -> transition(w, "start")));
        }
        if (Session.get().has(Permission.WORKORDER_EXECUTE)
                && (st == WorkOrderStatus.EN_COURS || st == WorkOrderStatus.EN_ATTENTE_PIECE)) {
            row.getChildren().add(btn(Messages.get("workorders.action.wait_parts"), "ghost",
                    () -> transition(w, "wait_parts")));
            row.getChildren().add(btn(Messages.get("workorders.action.resolve"), "primary",
                    () -> resolveDialog(w)));
        }
        if (Session.get().has(Permission.WORKORDER_VALIDATE) && st == WorkOrderStatus.RESOLUE) {
            row.getChildren().add(btn(Messages.get("workorders.action.validate"), "primary",
                    () -> transition(w, "validate")));
        }
        if (Session.get().has(Permission.WORKORDER_VALIDATE) && st == WorkOrderStatus.VALIDEE) {
            row.getChildren().add(btn(Messages.get("workorders.action.close"), "primary",
                    () -> transition(w, "close")));
        }
        if (row.getChildren().isEmpty()) {
            row.getChildren().add(Ui.muted("Aucune action disponible pour ce statut."));
        }
        return row;
    }

    private void transition(WorkOrder w, String action) {
        try {
            switch (action) {
                case "approve" -> AppContext.get().workOrders().approve(w.getId());
                case "start" -> AppContext.get().workOrders().start(w.getId());
                case "wait_parts" -> {
                    String reason = ConfirmDialog.prompt(Messages.get("workorders.action.wait_parts"),
                            "parts.tx_note", null);
                    if (reason != null) {
                        AppContext.get().workOrders().waitingParts(w.getId(), reason);
                    }
                }
                case "validate" -> AppContext.get().workOrders().validate(w.getId(), null);
                case "close" -> {
                    if (ConfirmDialog.confirm(Messages.get("workorders.close_blocked").contains("obligatoires")
                            ? "Clôturer cet ordre de travail ?" : "Confirmer la clôture ?")) {
                        AppContext.get().workOrders().close(w.getId());
                    }
                }
                default -> { /* rien */ }
            }
            Toast.success(Messages.get("msg.action_done"));
            refreshDetail(w);
            table.refresh();
        } catch (ValidationException ve) {
            Toast.error(ve.getMessage());
        } catch (RuntimeException ex) {
            Toast.error(Messages.get("msg.error_save"));
        }
    }

    private void scheduleDialog(WorkOrder w) {
        FormDialog<Object> dlg = new FormDialog<>(Messages.get("workorders.action.schedule")) {
        };
        javafx.scene.control.DatePicker date = FormDialog.datePicker(
                w.getScheduledAt() == null ? null : w.getScheduledAt().toLocalDate());
        javafx.scene.control.Spinner<Integer> time = new javafx.scene.control.Spinner<>(8, 20, 9);
        time.setEditable(true);
        dlg.addField("workorders.scheduled", date, true);
        dlg.addField("Heure (h)", time, true);
        dlg.setOnSave(b -> {
            if (date.getValue() == null) {
                dlg.showError(Messages.get("validation.required"));
                return;
            }
            LocalDateTime at = date.getValue().atTime(
                    time.getValue() == null ? 9 : time.getValue(), 0);
            try {
                AppContext.get().workOrders().schedule(w.getId(), at);
                Toast.success(Messages.get("msg.saved"));
                dlg.getDialogPane().getScene().getWindow().hide();
                refreshDetail(w);
                table.refresh();
            } catch (RuntimeException ex) {
                dlg.showError(ex.getMessage());
            }
        });
        dlg.showAndWait();
    }

    private void resolveDialog(WorkOrder w) {
        FormDialog<Object> dlg = new FormDialog<>(Messages.get("workorders.action.resolve")) {
        };
        TextField failureType = FormDialog.textField(w.getFailureType());
        TextArea diagnosis = FormDialog.textArea(w.getDiagnosis(), 3);
        TextArea cause = FormDialog.textArea(w.getFailureCause(), 2);
        TextArea corrective = FormDialog.textArea(w.getCorrectiveAction(), 2);
        TextArea work = FormDialog.textArea(w.getWorkPerformed(), 3);
        TextField labor = FormDialog.textField(w.getLaborHours() == null ? "" : w.getLaborHours().toPlainString());
        TextField downtime = FormDialog.textField(w.getDowntimeHours() == null ? "" : w.getDowntimeHours().toPlainString());

        dlg.addField("workorders.failure_type", failureType);
        dlg.addWideField("workorders.diagnosis", diagnosis, true);
        dlg.addWideField("workorders.cause", cause, false);
        dlg.addWideField("workorders.corrective_action", corrective, false);
        dlg.addWideField("workorders.work_performed", work, true);
        dlg.addField("workorders.labor_hours", labor);
        dlg.addField("workorders.downtime", downtime);

        dlg.setOnSave(b -> {
            try {
                AppContext.get().workOrders().resolve(w.getId(),
                        diagnosis.getText(), work.getText(), cause.getText(),
                        corrective.getText(), parse(labor), parse(downtime));
                Toast.success(Messages.get("msg.action_done"));
                dlg.getDialogPane().getScene().getWindow().hide();
                refreshDetail(w);
                table.refresh();
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private void refreshDetail(WorkOrder w) {
        try {
            WorkOrder latest = AppContext.get().workOrders().get(w.getId());
            detailHost.getChildren().setAll(buildDetail(latest));
            detailHost.setVisible(true);
        } catch (RuntimeException ex) {
            Toast.error(ex.getMessage());
        }
    }

    private static BigDecimal parse(TextField f) {
        String v = f.getText();
        if (v == null || v.isBlank()) return null;
        try {
            return new BigDecimal(v.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int kv(javafx.scene.layout.GridPane g, int row, String k, String v) {
        Label l = fieldLabel(k);
        Label val = new Label(Ui.orDash(v));
        val.setWrapText(true);
        g.add(l, 0, row);
        g.add(val, 1, row);
        return row + 1;
    }

    private Label fieldLabel(String k) {
        Label l = new Label(k);
        l.getStyleClass().add("field-label");
        return l;
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
