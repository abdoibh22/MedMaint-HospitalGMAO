package com.medmaint.ui.pages.resources;

import com.medmaint.domain.model.Document;
import com.medmaint.persistence.Page;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.ConfirmDialog;
import com.medmaint.ui.components.DataTable;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.pages.PageHeader;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Bibliothèque de documents : liste globale filtrée
 * (type d'entité, catégorie), téléchargement, suppression.
 * Le téléversement reste attaché à son entité (équipement, OT, contrat…).
 */
public class DocumentsPage extends VBox {

    private final DataTable<Document> table = new DataTable<>(Messages.get("documents.empty"), null);
    private final TextField search = new TextField();
    private final ComboBox<String> entityTypeFilter = new ComboBox<>();
    private final ComboBox<String> categoryFilter = new ComboBox<>();

    public DocumentsPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("documents.title"));
        header.subtitle("Téléversez des documents depuis la fiche d'un équipement, "
                + "d'un ordre de travail ou d'un contrat.");
        getChildren().add(header);

        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(260);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());

        entityTypeFilter.getItems().addAll(Messages.get("common.all_f"),
                "EQUIPMENT", "WORK_ORDER", "REQUEST", "MAINTENANCE_PLAN",
                "SUPPLIER", "CONTRACT", "USER");
        entityTypeFilter.setValue(Messages.get("common.all_f"));
        entityTypeFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        categoryFilter.getItems().addAll(Messages.get("common.all_f"));
        for (com.medmaint.domain.enums.DocumentCategory c
                : com.medmaint.domain.enums.DocumentCategory.values()) {
            categoryFilter.getItems().add(c.name());
        }
        categoryFilter.setValue(Messages.get("common.all_f"));
        categoryFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        HBox filters = new HBox(10,
                new VBox(2, lbl(Messages.get("common.search")), search),
                new VBox(2, lbl(Messages.get("documents.entity")), entityTypeFilter),
                new VBox(2, lbl(Messages.get("documents.category")), categoryFilter));
        filters.setPadding(new Insets(4, 0, 4, 0));
        getChildren().add(filters);

        table.addColumn(new DataTable.Col<>("file", Messages.get("documents.filename"),
                Document::getFilename, 300));
        table.addColumn(new DataTable.Col<>("entity", Messages.get("documents.entity"),
                d -> d.getEntityType() + " #" + d.getEntityId(), 170));
        table.addColumn(new DataTable.Col<>("category", Messages.get("documents.category"),
                d -> {
                    try {
                        return com.medmaint.domain.enums.DocumentCategory.fromCode(d.getCategory()).label();
                    } catch (RuntimeException e) {
                        return d.getCategory();
                    }
                }, 180));
        table.addColumn(new DataTable.Col<>("size", Messages.get("documents.size"),
                Document::humanSize, 100));
        table.addColumn(new DataTable.Col<>("by", Messages.get("documents.uploaded_by"),
                Document::getUploadedByName, 170));
        table.addColumn(new DataTable.Col<>("at", Messages.get("documents.uploaded_at"),
                d -> Ui.dateTime(d.getUploadedAt()), 150));

        List<DataTable.RowAction<Document>> actions = new java.util.ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.download"), "ghost", this::download));
        if (Session.get().has(Permission.DOCUMENT_DELETE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("common.delete"), "danger", d -> {
                if (ConfirmDialog.confirm(Messages.get("documents.delete_confirm"))) {
                    try {
                        AppContext.get().documents().delete(d.getId());
                        Toast.success(Messages.get("msg.deleted"));
                        table.refresh();
                    } catch (RuntimeException ex) {
                        Toast.error(ex.getMessage());
                    }
                }
            }));
        }
        table.addActionsColumn(actions);
        table.setPageSize(20);
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

    private Page<Document> load(int page) {
        String entity = entityTypeFilter.getValue();
        if (entity != null && entity.equals(Messages.get("common.all_f"))) entity = null;
        String category = categoryFilter.getValue();
        if (category != null && category.equals(Messages.get("common.all_f"))) category = null;
        return AppContext.get().documents().search(
                search.getText(), entity, category, page, table.pageSize()).page();
    }

    private void download(Document d) {
        try {
            Path p = AppContext.get().documents().resolveFile(d);
            if (!Files.exists(p)) {
                Toast.error("Fichier introuvable sur le disque.");
                return;
            }
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle(Messages.get("common.download"));
            chooser.setInitialFileName(d.getFilename());
            java.io.File target = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
            if (target != null) {
                Files.copy(p, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Toast.success(Messages.get("msg.action_done"));
            }
        } catch (IOException ioe) {
            Toast.error("Téléchargement impossible.");
        } catch (RuntimeException ex) {
            Toast.error(ex.getMessage());
        }
    }
}
