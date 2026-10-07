package com.medmaint.ui.pages.procurement;

import com.medmaint.domain.model.Supplier;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Fournisseurs : liste, CRUD, fiche détaillée avec historique
 * (équipements fournis, pièces, contrats).
 */
public class SuppliersPage extends VBox {

    private static final List<String> CATEGORIES = List.of(
            "Fabricant", "Distributeur", "Sous-traitant maintenance", "Pièces détachées",
            "Installation / pose", "Autre");

    private final DataTable<Supplier> table = new DataTable<>(Messages.get("suppliers.empty"),
            Session.get().has(Permission.SUPPLIER_MANAGE)
                    ? PageHeader.actionButton(Messages.get("suppliers.new"), true, () -> edit(null))
                    : null);

    private final TextField search = new TextField();
    private final ComboBox<String> categoryFilter = new ComboBox<>();

    public SuppliersPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("suppliers.title"));
        if (Session.get().has(Permission.SUPPLIER_MANAGE)) {
            header.addAction(Messages.get("suppliers.new"), true, () -> edit(null));
        }
        getChildren().add(header);

        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(240);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());
        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(CATEGORIES);
        categoryFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        Label l1 = lbl(Messages.get("common.search"));
        Label l2 = lbl(Messages.get("suppliers.category"));
        HBox filters = new HBox(10, new VBox(2, l1, search), new VBox(2, l2, categoryFilter));
        filters.setPadding(new Insets(4, 0, 4, 0));
        getChildren().add(filters);

        table.addColumn(new DataTable.Col<>("company", Messages.get("suppliers.company"),
                Supplier::getCompany, 230));
        table.addColumn(new DataTable.Col<>("contact", Messages.get("suppliers.contact"),
                s -> Ui.orDash(s.getContactPerson()), 170));
        table.addColumn(new DataTable.Col<>("category", Messages.get("suppliers.category"),
                s -> Ui.orDash(s.getCategory()), 170));
        table.addColumn(new DataTable.Col<>("phone", Messages.get("suppliers.phone"),
                s -> Ui.orDash(s.getPhone()), 140));
        table.addColumn(new DataTable.Col<>("email", Messages.get("suppliers.email"),
                s -> Ui.orDash(s.getEmail()), 200));
        table.addColumn(new DataTable.Col<>("contract", Messages.get("suppliers.contract_status"),
                s -> Ui.orDash(s.getContractStatus()), 130));

        List<DataTable.RowAction<Supplier>> actions = new java.util.ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.view"), "ghost", this::detail));
        if (Session.get().has(Permission.SUPPLIER_MANAGE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("common.edit"), "ghost", s -> edit(s)));
            actions.add(new DataTable.RowAction<>(Messages.get("common.delete"), "danger", s -> {
                try {
                    AppContext.get().suppliers().archiveSupplier(s.getId(), true);
                    Toast.success(Messages.get("msg.action_done"));
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

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        return l;
    }

    private Page<Supplier> load(int page) {
        return AppContext.get().suppliers().searchSuppliers(search.getText(),
                categoryFilter.getValue(), false, page, table.pageSize(),
                table.sortCol(), table.sortDir());
    }

    private void detail(Supplier s) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Informations
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(6);
        int r = 0;
        r = kv(g, r, Messages.get("suppliers.company"), s.getCompany());
        r = kv(g, r, Messages.get("suppliers.contact"), s.getContactPerson());
        r = kv(g, r, Messages.get("suppliers.address"), s.getAddress());
        r = kv(g, r, Messages.get("suppliers.phone"), s.getPhone());
        r = kv(g, r, Messages.get("suppliers.email"), s.getEmail());
        r = kv(g, r, Messages.get("suppliers.website"), s.getWebsite());
        r = kv(g, r, Messages.get("suppliers.category"), s.getCategory());
        r = kv(g, r, Messages.get("suppliers.products"), s.getProducts());
        r = kv(g, r, Messages.get("suppliers.contract_status"), s.getContractStatus());
        r = kv(g, r, Messages.get("suppliers.notes"), s.getNotes());
        tabs.getTabs().add(tab(Messages.get("suppliers.tab.info"), g));

        // Équipements fournis
        VBox eq = new VBox(6);
        for (Object[] row : AppContext.get().suppliers().suppliers().equipmentBySupplier(s.getId())) {
            eq.getChildren().add(new Label(row[0] + " — " + row[1] + "  (" + row[2] + ")"));
        }
        if (eq.getChildren().isEmpty()) eq.getChildren().add(Ui.muted("Aucun équipement."));
        tabs.getTabs().add(tab(Messages.get("suppliers.tab.equipment"), eq));

        // Pièces
        VBox parts = new VBox(6);
        for (Object[] row : AppContext.get().suppliers().suppliers().partsBySupplier(s.getId())) {
            parts.getChildren().add(new Label(row[0] + " — " + row[1] + "  (stock : " + row[2] + ")"));
        }
        if (parts.getChildren().isEmpty()) parts.getChildren().add(Ui.muted("Aucune pièce."));
        tabs.getTabs().add(tab(Messages.get("suppliers.tab.parts"), parts));

        // Contrats
        VBox contracts = new VBox(6);
        for (Object[] row : AppContext.get().suppliers().suppliers().contractsBySupplier(s.getId())) {
            contracts.getChildren().add(new Label(row[0] + " — " + row[1] + "  ("
                    + row[2] + " → " + row[3] + ")  " + row[4]));
        }
        if (contracts.getChildren().isEmpty()) contracts.getChildren().add(Ui.muted("Aucun contrat."));
        tabs.getTabs().add(tab(Messages.get("suppliers.tab.contracts"), contracts));

        javafx.scene.control.Dialog<Void> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle(s.getCompany());
        VBox content = new VBox(tabs);
        content.setPadding(new Insets(12));
        content.setPrefSize(720, 480);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getScene().getStylesheets().add(
                SuppliersPage.class.getResource("/css/app.css").toExternalForm());
        dlg.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private Tab tab(String title, Node content) {
        Tab t = new Tab(title);
        VBox wrap = new VBox(content);
        wrap.setPadding(new Insets(14));
        t.setContent(wrap);
        return t;
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

    private static void edit(Supplier s) {
        FormDialog<Object> dlg = new FormDialog<>(s == null
                ? Messages.get("suppliers.new") : Messages.get("suppliers.edit")) {
        };
        TextField company = FormDialog.textField(s == null ? null : s.getCompany());
        TextField contact = FormDialog.textField(s == null ? null : s.getContactPerson());
        TextField address = FormDialog.textField(s == null ? null : s.getAddress());
        TextField phone = FormDialog.textField(s == null ? null : s.getPhone());
        TextField email = FormDialog.textField(s == null ? null : s.getEmail());
        TextField website = FormDialog.textField(s == null ? null : s.getWebsite());
        ComboBox<String> category = FormDialog.combo(CATEGORIES,
                s == null ? null : s.getCategory());
        TextArea products = FormDialog.textArea(s == null ? null : s.getProducts(), 2);
        ComboBox<String> contractStatus = FormDialog.combo(
                List.of("Actif", "En attente", "Suspendu", "Expiré"),
                s == null ? "Actif" : s.getContractStatus());
        TextArea notes = FormDialog.textArea(s == null ? null : s.getNotes(), 3);

        dlg.addField("suppliers.company", company, true);
        dlg.addField("suppliers.contact", contact);
        dlg.addField("suppliers.address", address);
        dlg.addField("suppliers.phone", phone);
        dlg.addField("suppliers.email", email);
        dlg.addField("suppliers.website", website);
        dlg.addField("suppliers.category", category);
        dlg.addWideField("suppliers.products", products, false);
        dlg.addField("suppliers.contract_status", contractStatus);
        dlg.addWideField("suppliers.notes", notes, false);

        dlg.setOnSave(b -> {
            Supplier sup = s != null ? s : new Supplier();
            sup.setCompany(company.getText().trim());
            sup.setContactPerson(nullIfBlank(contact));
            sup.setAddress(nullIfBlank(address));
            sup.setPhone(nullIfBlank(phone));
            sup.setEmail(nullIfBlank(email));
            sup.setWebsite(nullIfBlank(website));
            sup.setCategory(category.getValue());
            sup.setProducts(nullIfBlank(products));
            sup.setContractStatus(contractStatus.getValue());
            sup.setNotes(nullIfBlank(notes));
            try {
                if (s == null) {
                    AppContext.get().suppliers().createSupplier(sup);
                    Toast.success(Messages.get("msg.created"));
                } else {
                    AppContext.get().suppliers().updateSupplier(sup);
                    Toast.success(Messages.get("msg.saved"));
                }
                dlg.getDialogPane().getScene().getWindow().hide();
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
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
