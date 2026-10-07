package com.medmaint.ui.pages.resources;

import com.medmaint.domain.model.Document;
import com.medmaint.domain.enums.DocumentCategory;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.MainShell;
import com.medmaint.ui.components.ConfirmDialog;
import com.medmaint.ui.components.DataTable;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Panneau de liste de documents rattachés à une entité
 * (équipement, OT, plan, fournisseur, contrat…).
 * Téléversement (type + taille validés), téléchargement, suppression.
 */
public class DocumentListPane extends VBox {

    private final String entityType;
    private final long entityId;
    private final DataTable<Document> table;

    public DocumentListPane(String entityType, long entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
        setSpacing(8);

        table = new DataTable<>(Messages.get("documents.empty"), null);

        buildToolbar();
    }

    private void buildToolbar() {
        Button uploadBtn = new Button(Messages.get("documents.new"));
        uploadBtn.getStyleClass().add("primary");
        uploadBtn.setVisible(Session.get().has(Permission.DOCUMENT_UPLOAD));
        uploadBtn.setOnAction(e -> openUpload());
        getChildren().add(uploadBtn);

        table.addColumn(new DataTable.Col<>("file", Messages.get("documents.filename"),
                Document::getFilename, 260));
        table.addColumn(new DataTable.Col<>("category", Messages.get("documents.category"),
                d -> docCat(d.getCategory()), 160));
        table.addColumn(new DataTable.Col<>("size", Messages.get("documents.size"),
                Document::humanSize, 90));
        table.addColumn(new DataTable.Col<>("by", Messages.get("documents.uploaded_by"),
                Document::getUploadedByName, 150));
        table.addColumn(new DataTable.Col<>("at", Messages.get("documents.uploaded_at"),
                d -> Ui.dateTime(d.getUploadedAt()), 140));

        List<DataTable.RowAction<Document>> actions = new java.util.ArrayList<>();
        actions.add(new DataTable.RowAction<>(Messages.get("common.download"), "ghost", this::download));
        if (Session.get().has(Permission.DOCUMENT_DELETE)) {
            actions.add(new DataTable.RowAction<>(Messages.get("common.delete"), "danger", this::delete));
        }
        table.addActionsColumn(actions);
        table.setPageSize(20);
        table.setLoader(p -> {
            List<Document> all = AppContext.get().documents().forEntity(entityType, entityId);
            int from = Math.min(all.size(), (p - 1) * table.pageSize());
            int to = Math.min(all.size(), from + table.pageSize());
            return new com.medmaint.persistence.Page<>(all.subList(from, to), all.size(), p, table.pageSize());
        });
        javafx.scene.layout.VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(table);
        table.refresh();
    }

    private String docCat(String code) {
        if (code == null) return "—";
        try {
            return DocumentCategory.fromCode(code).label();
        } catch (RuntimeException e) {
            return code;
        }
    }

    private void openUpload() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(Messages.get("documents.new"));
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter(
                        "Fichiers autorisés", "*.pdf", "*.jpg", "*.jpeg", "*.png", "*.docx", "*.xlsx"),
                new javafx.stage.FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));
        java.io.File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }

        TextInputDialog catDlg = new TextInputDialog(DocumentCategory.AUTRE.name());
        catDlg.setTitle(Messages.get("documents.new"));
        catDlg.setHeaderText(Messages.get("documents.category") + " : "
                + java.util.Arrays.toString(DocumentCategory.values()));
        String category = catDlg.showAndWait().orElse(DocumentCategory.AUTRE.name());

        try {
            byte[] content = Files.readAllBytes(file.toPath());
            AppContext.get().documents().store(entityType, entityId, file.getName(), category, content);
            Toast.success(Messages.get("documents.upload_success"));
            table.refresh();
        } catch (IOException ioe) {
            Toast.error(Messages.get("documents.upload_error"));
        } catch (com.medmaint.security.ValidationException ve) {
            Toast.error(ve.getMessage());
        }
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

    private void delete(Document d) {
        if (ConfirmDialog.confirm(Messages.get("documents.delete_confirm"))) {
            try {
                AppContext.get().documents().delete(d.getId());
                Toast.success(Messages.get("msg.deleted"));
                table.refresh();
            } catch (RuntimeException ex) {
                Toast.error(ex.getMessage());
            }
        }
    }

    public void refresh() {
        table.refresh();
    }
}
