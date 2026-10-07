package com.medmaint.ui.pages.parts;

import com.medmaint.domain.enums.StockTxType;
import com.medmaint.domain.model.SparePart;
import com.medmaint.domain.model.StockTransaction;
import com.medmaint.persistence.Page;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pièces de rechange : inventaire avec alerte stock bas visible, transactions
 * (entrée / sortie / ajustement / transfert), historique, CRUD.
 * Stock négatif bloqué sauf override administrateur.
 */
public class PartsPage extends VBox {

    private final DataTable<SparePart> table = new DataTable<>(Messages.get("parts.empty"),
            Session.get().has(Permission.PART_MANAGE)
                    ? PageHeader.actionButton(Messages.get("parts.new"), true, () -> edit(null))
                    : null);

    private final TextField search = new TextField();
    private final ComboBox<String> categoryFilter = new ComboBox<>();
    private final CheckBox lowStockOnly = new CheckBox(Messages.get("parts.low_stock"));

    public PartsPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("parts.title"));
        header.subtitle(Messages.get("parts.stock_value") + " : "
                + Ui.money(AppContext.get().stock().all().stream()
                .map(SparePart::stockValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                + "  ·  " + AppContext.get().stock().countLowStock() + " en stock bas");
        if (Session.get().has(Permission.PART_MANAGE)) {
            header.addAction(Messages.get("parts.new"), true, () -> edit(null));
        }
        getChildren().add(header);

        buildFilters();
        getChildren().add(filterBar());
        buildColumns();
        table.setPageSize(15);
        table.setLoader(this::load);
        getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.resetAndRefresh();
    }

    private void buildFilters() {
        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(240);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());

        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(AppContext.get().stock().categories());
        categoryFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
        lowStockOnly.selectedProperty().addListener((o, a, b) -> table.resetAndRefresh());
    }

    private Node filterBar() {
        Button reset = new Button(Messages.get("common.reset"));
        reset.getStyleClass().add("ghost");
        reset.setOnAction(e -> {
            search.clear();
            categoryFilter.setValue(null);
            lowStockOnly.setSelected(false);
            table.resetAndRefresh();
        });
        HBox bar = new HBox(10, lbl(Messages.get("common.search"), search),
                lbl(Messages.get("parts.category"), categoryFilter),
                lbl(" ", lowStockOnly), lbl(" ", reset));
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
        table.addColumn(new DataTable.Col<>("partNo", Messages.get("parts.part_no"),
                SparePart::getPartNo, 130));
        table.addColumn(new DataTable.Col<>("name", Messages.get("parts.name"),
                SparePart::getName, 240));
        table.addColumn(new DataTable.Col<>("category", Messages.get("parts.category"),
                SparePart::getCategory, 130));
        table.addColumn(new DataTable.Col<>("manufacturer", Messages.get("parts.manufacturer"),
                p -> Ui.orDash(p.getManufacturer()), 130));
        table.addColumn(new DataTable.Col<>("quantity", Messages.get("parts.quantity"),
                p -> p.getQuantity() + (p.isLowStock() ? "  ⚠" : ""), 100));
        table.addColumn(new DataTable.Col<>("min", Messages.get("parts.min_quantity"),
                p -> String.valueOf(p.getMinQuantity()), 100));
        table.addColumn(new DataTable.Col<>("unitCost", Messages.get("parts.unit_cost"),
                p -> Ui.money(p.getUnitCost()), 110));
        table.addColumn(new DataTable.Col<>("supplier", Messages.get("parts.supplier"),
                p -> Ui.orDash(p.getSupplierName()), 150));
        table.addColumn(new DataTable.Col<>("location", Messages.get("parts.storage"),
                p -> Ui.orDash(p.getStorageLocation()), 130));
        table.addColumn(new DataTable.Col<>("state", "État",
                p -> p.isLowStock() ? Messages.get("parts.low_stock")
                        : (p.getExpiryDate() != null && p.getExpiryDate().isBefore(java.time.LocalDate.now())
                        ? "Expirée" : "OK"), 110));

        List<DataTable.RowAction<SparePart>> actions = new java.util.ArrayList<>();
        if (Session.get().has(Permission.PART_MANAGE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("parts.action.entry"), "ghost",
                    p -> transact(p, StockTxType.ENTREE)));
            actions.add(new DataTable.RowAction<>(Messages.get("parts.action.exit"), "ghost",
                    p -> transact(p, StockTxType.SORTIE)));
            actions.add(new DataTable.RowAction<>(Messages.get("common.edit"), "ghost", p -> edit(p)));
            actions.add(new DataTable.RowAction<>(Messages.get("parts.action.history"), "ghost",
                    this::history));
        }
        table.addActionsColumn(actions);
    }

    private Page<SparePart> load(int page) {
        Boolean low = lowStockOnly.isSelected() ? Boolean.TRUE : null;
        return AppContext.get().stock().search(search.getText(), categoryFilter.getValue(),
                low, null, false, page, table.pageSize(), table.sortCol(), table.sortDir());
    }

    // ---------- Transactions ----------

    private void transact(SparePart p, StockTxType type) {
        FormDialog<Object> dlg = new FormDialog<>(type.label() + " — " + p.getPartNo()) {
        };
        Label stock = Ui.muted(Messages.get("parts.quantity") + " actuel : " + p.getQuantity());
        TextField qty = FormDialog.textField("1");
        TextField unitCost = FormDialog.textField(
                p.getUnitCost() == null ? "" : p.getUnitCost().toPlainString());
        TextArea note = FormDialog.textArea(null, 2);
        CheckBox allowNeg = new CheckBox(Messages.get("parts.negative_blocked"));
        allowNeg.setWrapText(true);
        allowNeg.setVisible(Session.get().isAdmin());
        allowNeg.setManaged(Session.get().isAdmin());

        dlg.addRaw(stock);
        dlg.addField("parts.tx_qty", qty, true);
        if (type == StockTxType.ENTREE) {
            dlg.addField("parts.unit_cost", unitCost);
        }
        dlg.addWideField("parts.tx_note", note, false);
        dlg.addRaw(allowNeg);

        dlg.setOnSave(b -> {
            int quantity;
            try {
                quantity = Integer.parseInt(qty.getText().trim());
            } catch (NumberFormatException e) {
                dlg.showError(Messages.get("validation.number"));
                return;
            }
            BigDecimal cost = null;
            if (type == StockTxType.ENTREE && !unitCost.getText().isBlank()) {
                try {
                    cost = new BigDecimal(unitCost.getText().trim().replace(",", "."));
                } catch (NumberFormatException e) {
                    dlg.showError(Messages.get("validation.number"));
                    return;
                }
            }
            try {
                AppContext.get().stock().transact(p.getId(), type, quantity, cost,
                        note.getText(), allowNeg.isSelected());
                Toast.success(Messages.get("msg.action_done"));
                dlg.getDialogPane().getScene().getWindow().hide();
                refreshHeader();
                table.refresh();
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private void history(SparePart p) {
        List<StockTransaction> txs = AppContext.get().stock().transactionsOf(p.getId());
        StringBuilder sb = new StringBuilder(p.getPartNo() + " — " + p.getName() + "\n\n");
        if (txs.isEmpty()) {
            sb.append("Aucun mouvement.");
        } else {
            for (StockTransaction t : txs) {
                sb.append(Ui.dateTime(t.getCreatedAt()))
                        .append("  ·  ").append(labelTx(t.getTxType()))
                        .append("  ·  ").append(t.getQuantity())
                        .append("  ·  ").append(Ui.money(t.value()));
                if (t.getWorkOrderNo() != null) sb.append("  ·  ").append(t.getWorkOrderNo());
                if (t.getNote() != null) sb.append("  ·  ").append(t.getNote());
                sb.append('\n');
            }
        }
        ConfirmDialog.info(sb.toString());
    }

    private String labelTx(String code) {
        try {
            return StockTxType.fromCode(code).label();
        } catch (RuntimeException e) {
            return code;
        }
    }

    // ---------- CRUD ----------

    private static void edit(SparePart p) {
        FormDialog<Object> dlg = new FormDialog<>(p == null
                ? Messages.get("parts.new") : Messages.get("parts.edit")) {
        };
        TextField partNo = FormDialog.textField(p == null ? null : p.getPartNo());
        TextField name = FormDialog.textField(p == null ? null : p.getName());
        TextField category = FormDialog.textField(p == null ? null : p.getCategory());
        TextField manufacturer = FormDialog.textField(p == null ? null : p.getManufacturer());
        TextField compatible = FormDialog.textField(p == null ? null : p.getCompatibleEquipment());
        TextField min = FormDialog.textField(p == null ? "2" : String.valueOf(p.getMinQuantity()));
        TextField max = FormDialog.textField(p == null ? "20" : String.valueOf(p.getMaxQuantity()));
        TextField unitCost = FormDialog.textField(p == null ? null
                : (p.getUnitCost() == null ? null : p.getUnitCost().toPlainString()));
        ComboBox<com.medmaint.domain.model.Supplier> supplier = FormDialog.combo(
                AppContext.get().suppliers().suppliers().findAll(),
                p == null ? null : AppContext.get().suppliers().suppliers()
                        .findById(p.getSupplierId() == null ? -1 : p.getSupplierId()).orElse(null));
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
        TextField location = FormDialog.textField(p == null ? null : p.getStorageLocation());
        javafx.scene.control.DatePicker expiry = FormDialog.datePicker(p == null ? null : p.getExpiryDate());
        TextField quantity = FormDialog.textField(p == null ? "0" : String.valueOf(p.getQuantity()));

        dlg.addField("parts.part_no", partNo, true);
        dlg.addField("parts.name", name, true);
        dlg.addField("parts.category", category);
        dlg.addField("parts.manufacturer", manufacturer);
        dlg.addField("parts.compatible", compatible);
        dlg.addField("parts.quantity", quantity, p == null);
        dlg.addField("parts.min_quantity", min, true);
        dlg.addField("parts.max_quantity", max);
        dlg.addField("parts.unit_cost", unitCost);
        dlg.addField("parts.supplier", supplier);
        dlg.addField("parts.storage", location);
        dlg.addField("parts.expiry", expiry);

        dlg.setOnSave(b -> {
            SparePart part = p != null ? p : new SparePart();
            part.setPartNo(partNo.getText().trim());
            part.setName(name.getText().trim());
            part.setCategory(trimOrNull(category));
            part.setManufacturer(trimOrNull(manufacturer));
            part.setCompatibleEquipment(trimOrNull(compatible));
            try {
                part.setMinQuantity(Integer.parseInt(min.getText().trim()));
                part.setMaxQuantity(max.getText().isBlank() ? 0 : Integer.parseInt(max.getText().trim()));
                part.setQuantity(quantity.getText().isBlank() ? 0 : Integer.parseInt(quantity.getText().trim()));
            } catch (NumberFormatException e) {
                dlg.showError(Messages.get("validation.number"));
                return;
            }
            part.setUnitCost(unitCost.getText().isBlank() ? BigDecimal.ZERO
                    : new BigDecimal(unitCost.getText().trim().replace(",", ".")));
            part.setSupplierId(supplier.getValue() == null ? null : supplier.getValue().getId());
            part.setStorageLocation(trimOrNull(location));
            part.setExpiryDate(expiry.getValue());
            try {
                if (p == null) {
                    AppContext.get().stock().create(part);
                    Toast.success(Messages.get("msg.created"));
                } else {
                    AppContext.get().stock().update(part);
                    Toast.success(Messages.get("msg.saved"));
                }
                dlg.getDialogPane().getScene().getWindow().hide();
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (NumberFormatException e) {
                dlg.showError(Messages.get("validation.number"));
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    private static String trimOrNull(TextField f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private void refreshHeader() {
        // Recalcul de l'en-tête après mouvement de stock
        getChildren().removeIf(n -> n instanceof PageHeader);
        PageHeader header = new PageHeader(Messages.get("parts.title"));
        header.subtitle(Messages.get("parts.stock_value") + " : "
                + Ui.money(AppContext.get().stock().all().stream()
                .map(SparePart::stockValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                + "  ·  " + AppContext.get().stock().countLowStock() + " en stock bas");
        if (Session.get().has(Permission.PART_MANAGE)) {
            header.addAction(Messages.get("parts.new"), true, () -> edit(null));
        }
        getChildren().add(0, header);
    }
}
