package com.medmaint.ui.pages.procurement;

import com.medmaint.domain.enums.ContractStatus;
import com.medmaint.domain.model.Contract;
import com.medmaint.domain.model.Equipment;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.EquipmentRepo;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrats de maintenance : liste avec statut effectif (dérivé des dates),
 * alertes d'expiration, CRUD, couverture équipements (multi-sélection).
 */
public class ContractsPage extends VBox {

    private final DataTable<Contract> table = new DataTable<>(Messages.get("contracts.empty"),
            Session.get().has(Permission.CONTRACT_MANAGE)
                    ? PageHeader.actionButton(Messages.get("contracts.new"), true, () -> edit(null))
                    : null);

    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();

    public ContractsPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        long expiring = AppContext.get().suppliers().countExpiringContracts();

        PageHeader header = new PageHeader(Messages.get("contracts.title"));
        if (expiring > 0) {
            header.subtitle(expiring + " " + Messages.get("contracts.expiring"));
        }
        if (Session.get().has(Permission.CONTRACT_MANAGE)) {
            header.addAction(Messages.get("contracts.new"), true, () -> edit(null));
        }
        getChildren().add(header);

        if (expiring > 0) {
            Label alert = new Label("⚠  " + expiring + " contrat(s) arrivant à échéance dans les "
                    + "60 prochains jours — vérifiez le renouvellement.");
            alert.getStyleClass().add("alert-warn");
            alert.setWrapText(true);
            getChildren().add(alert);
        }

        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(240);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());
        statusFilter.getItems().addAll(Messages.get("common.all_f"), "Actif", "Expirant", "Expiré", "Suspendu");
        statusFilter.setValue(Messages.get("common.all_f"));
        statusFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        HBox filters = new HBox(10,
                new VBox(2, lbl(Messages.get("common.search")), search),
                new VBox(2, lbl(Messages.get("contracts.status")), statusFilter));
        filters.setPadding(new Insets(4, 0, 4, 0));
        getChildren().add(filters);

        table.addColumn(new DataTable.Col<>("no", Messages.get("contracts.no"),
                Contract::getContractNo, 130));
        table.addColumn(new DataTable.Col<>("title", Messages.get("contracts.title_f"),
                Contract::getTitle, 240));
        table.addColumn(new DataTable.Col<>("supplier", Messages.get("contracts.supplier"),
                c -> Ui.orDash(c.getSupplierName()), 180));
        table.addColumn(new DataTable.Col<>("type", Messages.get("contracts.type"),
                c -> Ui.orDash(c.getContractType()), 150));
        table.addColumn(new DataTable.Col<>("start", Messages.get("contracts.start"),
                c -> Ui.date(c.getStartDate()), 110));
        table.addColumn(new DataTable.Col<>("end", Messages.get("contracts.end"),
                c -> Ui.date(c.getEndDate()), 110));
        table.addColumn(new DataTable.Col<>("cost", Messages.get("contracts.annual_cost"),
                c -> Ui.money(c.getAnnualCost()), 130));
        table.addColumn(new DataTable.Col<>("equipment", Messages.get("contracts.equipment_covered"),
                c -> c.getEquipmentCount() == null ? "—" : String.valueOf(c.getEquipmentCount()), 120));
        table.addColumn(new DataTable.Col<>("status", Messages.get("contracts.status"),
                c -> status(c).label(), 140));

        List<DataTable.RowAction<Contract>> actions = new java.util.ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.view"), "ghost", this::detail));
        if (Session.get().has(Permission.CONTRACT_MANAGE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("common.edit"), "ghost", c -> edit(c)));
            actions.add(new DataTable.RowAction<>(Messages.get("common.delete"), "danger", c -> {
                try {
                    AppContext.get().suppliers().deleteContract(c.getId());
                    Toast.success(Messages.get("msg.deleted"));
                    table.refresh();
                } catch (RuntimeException ex) {
                    Toast.error(ex.getMessage());
                }
            }));
        }
        table.addActionsColumn(actions);
        table.setPageSize(15);
        table.setLoader(this::load);
        getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.resetAndRefresh();
    }

    private ContractStatus status(Contract c) {
        return AppContext.get().suppliers().effectiveStatus(c);
    }

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        return l;
    }

    private Page<Contract> load(int page) {
        String st = statusFilter.getValue();
        String code = switch (st == null ? "" : st) {
            case "Actif" -> "ACTIF";
            case "Expirant" -> "EXPIRANT";
            case "Expiré" -> "EXPIRE";
            case "Suspendu" -> "SUSPENDU";
            default -> null;
        };
        return AppContext.get().suppliers().searchContracts(search.getText(), code, null,
                page, table.pageSize(), table.sortCol(), table.sortDir());
    }

    private void detail(Contract c) {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(6);
        int r = 0;
        r = kv(g, r, Messages.get("contracts.no"), c.getContractNo());
        r = kv(g, r, Messages.get("contracts.title_f"), c.getTitle());
        r = kv(g, r, Messages.get("contracts.supplier"), c.getSupplierName());
        r = kv(g, r, Messages.get("contracts.type"), c.getContractType());
        r = kv(g, r, Messages.get("contracts.start"), Ui.date(c.getStartDate()));
        r = kv(g, r, Messages.get("contracts.end"), Ui.date(c.getEndDate()));
        r = kv(g, r, Messages.get("contracts.annual_cost"), Ui.money(c.getAnnualCost()));
        r = kv(g, r, Messages.get("contracts.sla"), c.getSla());
        r = kv(g, r, Messages.get("contracts.response_time"), c.getResponseTime());
        r = kv(g, r, Messages.get("contracts.contact"),
                c.getContactName() + " " + Ui.orDash(c.getContactPhone()) + " " + Ui.orDash(c.getContactEmail()));
        r = kv(g, r, Messages.get("contracts.status"), status(c).label());
        r = kv(g, r, Messages.get("contracts.equipment_covered"), "");

        VBox covered = new VBox(4);
        for (Object[] row : AppContext.get().suppliers().contracts().equipmentDetails(c.getId())) {
            covered.getChildren().add(new Label("• " + row[0] + " — " + row[1] + "  (" + row[2] + ")"));
        }
        if (covered.getChildren().isEmpty()) covered.getChildren().add(Ui.muted("Aucun équipement couvert."));

        VBox content = new VBox(8, g, Ui.sectionLabel("contracts.equipment_covered"), covered);
        content.setPadding(new Insets(14));
        content.setPrefSize(720, 520);

        javafx.scene.control.Dialog<Void> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle(c.getContractNo() + " — " + c.getTitle());
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getScene().getStylesheets().add(
                ContractsPage.class.getResource("/css/app.css").toExternalForm());
        dlg.getDialogPane().setPrefWidth(740);
        dlg.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dlg.showAndWait();
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

    private static void edit(Contract c) {
        FormDialog<Object> dlg = new FormDialog<>(c == null
                ? Messages.get("contracts.new") : Messages.get("contracts.edit")) {
        };
        TextField no = FormDialog.textField(c == null ? nextNo() : c.getContractNo());
        TextField title = FormDialog.textField(c == null ? null : c.getTitle());
        ComboBox<com.medmaint.domain.model.Supplier> supplier = FormDialog.combo(
                AppContext.get().suppliers().suppliers().findAll(),
                c == null || c.getSupplierId() == null ? null
                        : AppContext.get().suppliers().suppliers()
                        .findById(c.getSupplierId()).orElse(null));
        supplier.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(com.medmaint.domain.model.Supplier s) {
                return s == null ? Messages.get("common.none") : s.getCompany();
            }

            @Override
            public com.medmaint.domain.model.Supplier fromString(String s) {
                return null;
            }
        });
        ComboBox<String> type = FormDialog.combo(
                List.of("Full service", "Préventif", "Correctif", "Location", "Pièces only"),
                c == null ? "Full service" : c.getContractType());
        javafx.scene.control.DatePicker start = FormDialog.datePicker(
                c == null ? LocalDate.now() : c.getStartDate());
        javafx.scene.control.DatePicker end = FormDialog.datePicker(
                c == null ? LocalDate.now().plusYears(1) : c.getEndDate());
        TextField cost = FormDialog.textField(c == null || c.getAnnualCost() == null ? ""
                : c.getAnnualCost().toPlainString());
        TextField sla = FormDialog.textField(c == null ? null : c.getSla());
        TextField response = FormDialog.textField(c == null ? null : c.getResponseTime());
        TextField contactName = FormDialog.textField(c == null ? null : c.getContactName());
        TextField contactPhone = FormDialog.textField(c == null ? null : c.getContactPhone());
        TextField contactEmail = FormDialog.textField(c == null ? null : c.getContactEmail());
        ComboBox<String> statusBox = FormDialog.combo(
                List.of("ACTIF", "SUSPENDU"), c == null ? "ACTIF" : c.getStatus());
        TextArea notes = FormDialog.textArea(c == null ? null : c.getNotes(), 2);

        dlg.addField("contracts.no", no, true);
        dlg.addField("contracts.title_f", title, true);
        dlg.addField("contracts.supplier", supplier);
        dlg.addField("contracts.type", type);
        dlg.addField("contracts.start", start, true);
        dlg.addField("contracts.end", end, true);
        dlg.addField("contracts.annual_cost", cost);
        dlg.addField("contracts.sla", sla);
        dlg.addField("contracts.response_time", response);
        dlg.addField("contracts.contact", contactName);
        dlg.addField("Téléphone", contactPhone);
        dlg.addField("E-mail", contactEmail);
        dlg.addField("contracts.status", statusBox);
        dlg.addWideField("suppliers.notes", notes, false);

        // Couverture équipements
        List<Equipment> allEq = new EquipmentRepo().findAll(
                new EquipmentRepo.Filter(null, null, null, null, null, null, null, null, false, null),
                "code", "ASC");
        Map<Long, CheckBox> boxes = new HashMap<>();
        javafx.scene.layout.FlowPane coverage = new javafx.scene.layout.FlowPane(8, 4);
        List<Long> preselected = c == null ? List.of()
                : AppContext.get().suppliers().contracts().equipmentIds(c.getId());
        for (Equipment e : allEq) {
            CheckBox cb = new CheckBox(e.getCode() + " — " + e.getName());
            cb.setSelected(preselected.contains(e.getId()));
            boxes.put(e.getId(), cb);
            coverage.getChildren().add(cb);
        }
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(coverage);
        scroll.setPrefHeight(160);
        scroll.setFitToWidth(true);
        dlg.addWideField("contracts.equipment_covered", scroll, false);

        dlg.setOnSave(b -> {
            Contract contract = c != null ? c : new Contract();
            contract.setContractNo(no.getText().trim());
            contract.setTitle(title.getText().trim());
            contract.setSupplierId(supplier.getValue() == null ? null : supplier.getValue().getId());
            contract.setContractType(type.getValue());
            contract.setStartDate(start.getValue());
            contract.setEndDate(end.getValue());
            contract.setAnnualCost(cost.getText().isBlank() ? null
                    : new java.math.BigDecimal(cost.getText().trim().replace(",", ".")));
            contract.setSla(nullIfBlank(sla));
            contract.setResponseTime(nullIfBlank(response));
            contract.setContactName(nullIfBlank(contactName));
            contract.setContactPhone(nullIfBlank(contactPhone));
            contract.setContactEmail(nullIfBlank(contactEmail));
            contract.setStatus(statusBox.getValue());
            contract.setNotes(nullIfBlank(notes));
            try {
                if (c == null) {
                    long id = AppContext.get().suppliers().createContract(contract).getId();
                    List<Long> eqIds = selectedIds(boxes);
                    AppContext.get().suppliers().setContractEquipment(id, eqIds);
                    Toast.success(Messages.get("msg.created"));
                } else {
                    AppContext.get().suppliers().updateContract(contract);
                    AppContext.get().suppliers().setContractEquipment(contract.getId(), selectedIds(boxes));
                    Toast.success(Messages.get("msg.saved"));
                }
                dlg.getDialogPane().getScene().getWindow().hide();
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (NumberFormatException nfe) {
                dlg.showError(Messages.get("validation.number"));
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private static List<Long> selectedIds(Map<Long, CheckBox> boxes) {
        List<Long> out = new ArrayList<>();
        boxes.forEach((id, cb) -> {
            if (cb.isSelected()) out.add(id);
        });
        return out;
    }

    private static String nextNo() {
        long n = new com.medmaint.persistence.repo.ContractRepo().countActive() + 1;
        String no;
        do {
            no = String.format("CT-%04d", n++);
        } while (new com.medmaint.persistence.repo.ContractRepo().noExists(no, null));
        return no;
    }

    private static String nullIfBlank(TextField f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String nullIfBlank(TextArea f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }
}
