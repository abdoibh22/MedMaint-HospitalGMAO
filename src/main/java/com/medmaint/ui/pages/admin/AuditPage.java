package com.medmaint.ui.pages.admin;

import com.medmaint.domain.model.AuditLog;
import com.medmaint.persistence.Page;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.DataTable;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.pages.PageHeader;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Journal d'audit : recherche par utilisateur / action / entité / période.
 * Visible uniquement avec la permission AUDIT_VIEW (administrateurs).
 */
public class AuditPage extends VBox {

    private final DataTable<AuditLog> table =
            new DataTable<>(Messages.get("audit.empty"), null);

    private final TextField search = new TextField();
    private final ComboBox<String> actionFilter = new ComboBox<>();
    private final ComboBox<String> entityFilter = new ComboBox<>();
    private final DatePicker from = com.medmaint.ui.components.FormDialog.datePicker(null);
    private final DatePicker to = com.medmaint.ui.components.FormDialog.datePicker(null);

    public AuditPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        PageHeader header = new PageHeader(Messages.get("audit.title"));
        header.subtitle(AppContext.get().audit().repo().count() + " entrée(s)");
        getChildren().add(header);

        Label restricted = new Label("🔒 " + Messages.get("audit.restricted"));
        restricted.getStyleClass().add("alert-info");
        restricted.setWrapText(true);
        getChildren().add(restricted);

        search.setPromptText(Messages.get("search.placeholder"));
        search.setPrefWidth(240);
        search.textProperty().addListener((o, a, b) -> table.resetAndRefresh());

        actionFilter.getItems().add(null);
        actionFilter.getItems().addAll(AppContext.get().audit().repo().distinctActions());
        actionFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
        actionFilter.setPromptText(Messages.get("audit.action"));

        entityFilter.getItems().add(null);
        entityFilter.getItems().addAll(AppContext.get().audit().repo().distinctEntityTypes());
        entityFilter.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
        entityFilter.setPromptText(Messages.get("audit.entity"));

        from.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());
        to.valueProperty().addListener((o, a, b) -> table.resetAndRefresh());

        HBox filters = new HBox(10,
                wrap(Messages.get("common.search"), search),
                wrap(Messages.get("audit.action"), actionFilter),
                wrap(Messages.get("audit.entity"), entityFilter),
                wrap(Messages.get("reports.from"), from),
                wrap(Messages.get("reports.to"), to));
        filters.setPadding(new Insets(4, 0, 4, 0));
        getChildren().add(filters);

        table.addColumn(new DataTable.Col<>("date", Messages.get("audit.date"),
                a -> Ui.dateTime(a.getCreatedAt()), 150));
        table.addColumn(new DataTable.Col<>("user", Messages.get("audit.user"),
                a -> Ui.orDash(a.getUserName()), 170));
        table.addColumn(new DataTable.Col<>("action", Messages.get("audit.action"),
                AuditLog::getAction, 130));
        table.addColumn(new DataTable.Col<>("entity", Messages.get("audit.entity"),
                AuditLog::getEntityType, 170));
        table.addColumn(new DataTable.Col<>("entityId", Messages.get("audit.entity_id"),
                a -> Ui.orDash(a.getEntityId()), 110));
        table.addColumn(new DataTable.Col<>("label", "Libellé",
                a -> Ui.orDash(a.getEntityLabel()), 220));
        table.addColumn(new DataTable.Col<>("changes", Messages.get("audit.changes"),
                a -> (a.getOldValue() == null && a.getNewValue() == null) ? "—"
                        : Ui.orDash(a.getOldValue()) + " ⇒ " + Ui.orDash(a.getNewValue()), 280));

        table.setPageSize(25);
        table.setLoader(this::load);
        getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.resetAndRefresh();
    }

    private VBox wrap(String label, javafx.scene.Node field) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #52606d;");
        return new VBox(2, l, field);
    }

    private Page<AuditLog> load(int page) {
        return AppContext.get().audit().repo().search(
                search.getText(), actionFilter.getValue(), entityFilter.getValue(),
                Session.get() != null ? null : null,
                from.getValue(), to.getValue(),
                page, table.pageSize());
    }
}
