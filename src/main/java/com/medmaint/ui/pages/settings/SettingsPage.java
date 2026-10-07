package com.medmaint.ui.pages.settings;

import com.medmaint.domain.model.EquipmentCategory;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Paramètres : informations hôpital, catégories d'équipements, services,
 * checklists, notifications, stock, à propos.
 */
public class SettingsPage extends VBox {

    public SettingsPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("settings.title"));
        getChildren().add(header);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab(Messages.get("settings.tab.hospital"), hospitalTab()),
                tab(Messages.get("settings.tab.categories"), categoriesTab()),
                tab(Messages.get("settings.tab.departments"), departmentsTab()),
                tab(Messages.get("settings.tab.checklists"), checklistsTab()),
                tab(Messages.get("settings.tab.notifications"), notificationsTab()),
                tab(Messages.get("settings.tab.stock"), stockTab()),
                tab(Messages.get("settings.tab.about"), aboutTab()));
        getChildren().add(tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
    }

    private Tab tab(String title, Node content) {
        Tab t = new Tab(title);
        VBox wrap = new VBox(content);
        wrap.setPadding(new Insets(16));
        t.setContent(wrap);
        return t;
    }

    // ---------- Informations hôpital ----------

    private Node hospitalTab() {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(12);

        TextField name = field("hospital.name");
        TextField address = field("hospital.address");
        TextField phone = field("hospital.phone");
        TextField email = field("hospital.email");
        TextField logo = field("hospital.logo_text");

        addRow(g, 0, Messages.get("settings.hospital_name"), name);
        addRow(g, 1, Messages.get("settings.hospital_address"), address);
        addRow(g, 2, Messages.get("settings.hospital_phone"), phone);
        addRow(g, 3, Messages.get("settings.hospital_email"), email);
        addRow(g, 4, Messages.get("settings.hospital_logo"), logo);

        Button save = new Button(Messages.get("common.save"));
        save.getStyleClass().add("primary");
        save.setOnAction(e -> {
            try {
                saveSetting("hospital.name", name);
                saveSetting("hospital.address", address);
                saveSetting("hospital.phone", phone);
                saveSetting("hospital.email", email);
                saveSetting("hospital.logo_text", logo);
                Toast.success(Messages.get("msg.saved"));
            } catch (RuntimeException ex) {
                Toast.error(ex.getMessage());
            }
        });
        VBox box = new VBox(14, g, save);
        return box;
    }

    // ---------- Catégories ----------

    private Node categoriesTab() {
        DataTable<EquipmentCategory> table = new DataTable<>(
                Messages.get("equipment.empty"), null);
        table.addColumn(new DataTable.Col<>("code", Messages.get("dept.code"),
                EquipmentCategory::getCode, 110));
        table.addColumn(new DataTable.Col<>("name", Messages.get("equipment.category"),
                EquipmentCategory::getName, 240));
        table.addColumn(new DataTable.Col<>("freq", Messages.get("equipment.frequency"),
                EquipmentCategory::getDefaultFrequency, 160));
        table.addColumn(new DataTable.Col<>("desc", "Description",
                c -> Ui.orDash(c.getDescription()), 340));
        if (Session.get().has(com.medmaint.security.Permission.SETTINGS_MANAGE)) {
            table.addActionsColumn(List.of(
                    new DataTable.RowAction<>(Messages.get("common.edit"), "ghost",
                            c -> editCategory(c)),
                    new DataTable.RowAction<>(Messages.get("common.delete"), "danger", c -> {
                        try {
                            AppContext.get().references().deleteCategory(c.getId());
                            Toast.success(Messages.get("msg.deleted"));
                            table.refresh();
                        } catch (ValidationException ve) {
                            Toast.error(ve.getMessage());
                        } catch (RuntimeException ex) {
                            Toast.error(Messages.get("msg.error_save"));
                        }
                    })));
        }
        table.setLoader(p -> {
            List<EquipmentCategory> all = AppContext.get().references().allCategories();
            int from = Math.min(all.size(), (p - 1) * table.pageSize());
            int to = Math.min(all.size(), from + table.pageSize());
            return new com.medmaint.persistence.Page<>(all.subList(from, to), all.size(), p, table.pageSize());
        });

        Button add = new Button("+ " + Messages.get("common.create"));
        add.getStyleClass().add("primary");
        add.setVisible(Session.get().has(com.medmaint.security.Permission.SETTINGS_MANAGE));
        add.setOnAction(e -> editCategory(null));
        table.setPageSize(50);
        table.resetAndRefresh();

        return new VBox(10, add, table);
    }

    private void editCategory(EquipmentCategory c) {
        FormDialog<Object> dlg = new FormDialog<>(c == null
                ? Messages.get("common.create") : Messages.get("common.edit")) {
        };
        TextField code = FormDialog.textField(c == null ? null : c.getCode());
        TextField name = FormDialog.textField(c == null ? null : c.getName());
        ComboBox<String> freq = FormDialog.combo(List.of(
                "JOURNALIERE", "HEBDOMADAIRE", "MENSUELLE", "TRIMESTRIELLE",
                "SEMESTRIELLE", "ANNUELLE"),
                c == null ? "MENSUELLE" : c.getDefaultFrequency());
        freq.setEditable(true);
        TextField desc = FormDialog.textField(c == null ? null : c.getDescription());

        dlg.addField("dept.code", code, true);
        dlg.addField("dept.name", name, true);
        dlg.addField("equipment.frequency", freq);
        dlg.addField("Description", desc);

        dlg.setOnSave(b -> {
            EquipmentCategory cat = c != null ? c : new EquipmentCategory();
            cat.setCode(code.getText().trim());
            cat.setName(name.getText().trim());
            String freqValue = freq.getEditor() == null || freq.getEditor().getText() == null
                    || freq.getEditor().getText().isBlank() ? freq.getValue() : freq.getEditor().getText();
            cat.setDefaultFrequency(freqValue == null || freqValue.isBlank() ? null : freqValue.trim());
            cat.setDescription(desc.getText() == null || desc.getText().isBlank()
                    ? null : desc.getText().trim());
            try {
                if (c == null) {
                    AppContext.get().references().createCategory(cat);
                    Toast.success(Messages.get("msg.created"));
                } else {
                    AppContext.get().references().updateCategory(cat);
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

    // ---------- Services ----------

    private Node departmentsTab() {
        Label hint = Ui.muted("La gestion détaillée des services se fait depuis la page "
                + "« " + Messages.get("nav.departements") + " » (création, édition, statistiques).");
        Button go = new Button(Messages.get("nav.departements"));
        go.getStyleClass().add("ghost");
        go.setOnAction(e -> com.medmaint.ui.MainShell.shellNavigate("departments", null));
        return new VBox(10, hint, go);
    }

    // ---------- Checklists ----------

    private Node checklistsTab() {
        VBox list = new VBox(8);
        Button add = new Button("+ " + Messages.get("common.create"));
        add.getStyleClass().add("primary");
        add.setVisible(Session.get().has(com.medmaint.security.Permission.PM_MANAGE));
        add.setOnAction(e -> editChecklist(null));
        list.getChildren().add(add);

        for (var c : AppContext.get().preventive().checklists()) {
            int count = AppContext.get().preventive().checklistItems(c.getId()).size();
            Label title = Ui.h3(c.getName());
            Label meta = Ui.muted(Ui.orDash(c.getCategoryName()) + " · " + count + " item(s)");
            Button edit = new Button(Messages.get("common.edit"));
            edit.getStyleClass().add("ghost");
            edit.setVisible(Session.get().has(com.medmaint.security.Permission.PM_MANAGE));
            edit.setOnAction(e -> editChecklist(c));
            Button del = new Button(Messages.get("common.delete"));
            del.getStyleClass().add("danger");
            del.setVisible(Session.get().has(com.medmaint.security.Permission.PM_MANAGE));
            del.setOnAction(e -> {
                try {
                    AppContext.get().preventive().deleteChecklist(c.getId());
                    Toast.success(Messages.get("msg.deleted"));
                    rebuildTab(list, add);
                } catch (ValidationException ve) {
                    Toast.error(ve.getMessage());
                } catch (RuntimeException ex) {
                    Toast.error(Messages.get("msg.error_save"));
                }
            });
            VBox body = new VBox(3, title, meta);
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(10, body,
                    new javafx.scene.layout.Region(), edit, del);
            javafx.scene.layout.HBox.setHgrow((javafx.scene.layout.Region) row.getChildren().get(1),
                    Priority.ALWAYS);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("surface");
            row.setPadding(new Insets(8, 12, 8, 12));
            list.getChildren().add(row);
        }
        return list;
    }

    private void rebuildTab(VBox list, Button add) {
        list.getChildren().clear();
        // Reconstruction simple : on rouvre l'onglet via un refresh global
        getChildren().clear();
        getChildren().add(new SettingsPage());
    }

    private void editChecklist(com.medmaint.domain.model.Checklist c) {
        FormDialog<Object> dlg = new FormDialog<>(c == null
                ? Messages.get("common.create") : Messages.get("common.edit")) {
        };
        TextField name = FormDialog.textField(c == null ? null : c.getName());
        var categories = AppContext.get().references().allCategories();
        TextField catName = FormDialog.textField(c == null ? null : c.getCategoryName());

        javafx.scene.control.TextArea items = FormDialog.textArea(
                c == null ? "" : String.join("\n",
                        AppContext.get().preventive().checklistItems(c.getId()).stream()
                                .map(i -> i.getLabel()).toList()), 8);
        items.setPromptText("Un item par ligne");

        dlg.addField("pm.checklist", name, true);
        dlg.addWideField("equipment.category", items, true);
        dlg.addRaw(Ui.muted("Chaque ligne devient un point de contrôle de la checklist."));

        dlg.setOnSave(b -> {
            com.medmaint.domain.model.Checklist chk =
                    c != null ? c : new com.medmaint.domain.model.Checklist();
            chk.setName(name.getText().trim());
            List<com.medmaint.domain.model.Checklist.ChecklistItem> entries = new java.util.ArrayList<>();
            int pos = 1;
            for (String line : items.getText().split("\n")) {
                if (!line.isBlank()) {
                    var it = new com.medmaint.domain.model.Checklist.ChecklistItem();
                    it.setPosition(pos++);
                    it.setLabel(line.trim());
                    entries.add(it);
                }
            }
            try {
                AppContext.get().preventive().saveChecklist(chk, entries);
                Toast.success(Messages.get("msg.saved"));
                dlg.getDialogPane().getScene().getWindow().hide();
                getChildren().setAll(new SettingsPage());
            } catch (ValidationException ve) {
                dlg.showError(ve.getMessage());
            } catch (RuntimeException ex) {
                dlg.showError(Messages.get("msg.error_save"));
            }
        });
        dlg.showAndWait();
    }

    // ---------- Notifications ----------

    private Node notificationsTab() {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(12);

        TextField lead = field("notif.pm_lead_days");
        TextField contractLead = field("notif.contract_lead_days");
        CheckBox lowStock = new CheckBox(Messages.get("settings.notif_low_stock"));
        lowStock.setSelected(AppContext.get().settings().getBool("notif.low_stock_enabled", true));

        addRow(g, 0, Messages.get("settings.notif_lead"), lead);
        addRow(g, 1, Messages.get("settings.notif_contract_lead"), contractLead);
        g.add(lowStock, 0, 2, 2, 1);

        Button save = new Button(Messages.get("common.save"));
        save.getStyleClass().add("primary");
        save.setOnAction(e -> {
            try {
                saveSetting("notif.pm_lead_days", lead);
                saveSetting("notif.contract_lead_days", contractLead);
                AppContext.get().settings().set("notif.low_stock_enabled",
                        String.valueOf(lowStock.isSelected()));
                Toast.success(Messages.get("settings.saved"));
            } catch (RuntimeException ex) {
                Toast.error(ex.getMessage());
            }
        });
        return new VBox(14, g, save);
    }

    // ---------- Stock ----------

    private Node stockTab() {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(12);

        CheckBox allowNeg = new CheckBox(Messages.get("settings.stock_negative"));
        allowNeg.setSelected(AppContext.get().settings().getBool("stock.allow_negative", false));
        allowNeg.setDisable(!Session.get().isAdmin());

        TextField rate = field("maintenance.hourly_rate");
        TextField upload = field("doc.max_upload_mb");

        g.add(allowNeg, 0, 0, 2, 1);
        addRow(g, 1, "Coût horaire main-d'œuvre (DA)", rate);
        addRow(g, 2, "Taille max. upload (Mo)", upload);

        Button save = new Button(Messages.get("common.save"));
        save.getStyleClass().add("primary");
        save.setOnAction(e -> {
            try {
                AppContext.get().settings().set("stock.allow_negative",
                        String.valueOf(allowNeg.isSelected()));
                saveSetting("maintenance.hourly_rate", rate);
                saveSetting("doc.max_upload_mb", upload);
                Toast.success(Messages.get("settings.saved"));
            } catch (RuntimeException ex) {
                Toast.error(ex.getMessage());
            }
        });
        return new VBox(14, g, save);
    }

    // ---------- À propos ----------

    private Node aboutTab() {
        VBox box = new VBox(10);
        box.getChildren().add(Ui.h2(AppConfigNames()));
        box.getChildren().add(Ui.muted("Version 1.0.0"));
        Label text = new Label(Messages.get("settings.about_text"));
        text.setWrapText(true);
        text.setMaxWidth(700);
        box.getChildren().add(text);
        box.getChildren().add(Ui.sectionLabel("Database"));
        box.getChildren().add(Ui.muted("H2 embarqué (fichier local) — "
                + com.medmaint.config.AppConfig.get().dbUrl().replaceAll(";.*", "")));
        box.getChildren().add(Ui.muted("Exports : " + com.medmaint.config.AppConfig.get().exportDir()
                + "  ·  Documents : " + com.medmaint.config.AppConfig.get().uploadDir()
                + "  ·  Journaux : " + com.medmaint.config.AppConfig.get().logDir()));
        return box;
    }

    private String AppConfigNames() {
        return com.medmaint.config.AppConfig.APP_NAME + " — " + com.medmaint.config.AppConfig.APP_SUBTITLE;
    }

    // ---------- Helpers ----------

    private TextField field(String key) {
        TextField tf = new TextField(AppContext.get().settings().get(key));
        tf.setPrefWidth(380);
        return tf;
    }

    private void saveSetting(String key, TextField field) {
        AppContext.get().settings().set(key, field.getText() == null ? "" : field.getText().trim());
    }

    private void addRow(GridPane g, int row, String label, Node field) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        g.add(l, 0, row);
        g.add(field, 1, row);
    }
}
