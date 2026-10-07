package com.medmaint.ui.pages.shell;

import com.medmaint.domain.model.Notification;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.MainShell;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.pages.PageHeader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Centre de notifications : liste avec état lu/non-lu, type, horodatage
 * et enregistrement lié (navigation au clic).
 */
public class NotificationsPage extends VBox {

    private final VBox list = new VBox(8);

    public NotificationsPage() {
        setSpacing(10);
        setPadding(new Insets(0, 0, 8, 0));

        Session s = Session.get();
        long unread = AppContext.get().notifications().unreadCount(s.userId(), s.role().name());

        PageHeader header = new PageHeader(Messages.get("notifications.title"));
        header.subtitle(unread + " " + Messages.get("notifications.unread"));
        header.addAction(Messages.get("notifications.mark_all_read"), false, () -> {
            AppContext.get().notifications().markAllRead(s.userId(), s.role().name());
            rebuild();
        });
        getChildren().add(header);

        rebuild();
        // Liste défilante : toutes les notifications restent accessibles
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
    }

    private void rebuild() {
        Session s = Session.get();
        List<Notification> all = AppContext.get().notifications().forUser(
                s.userId(), s.role().name(), 10_000);
        list.getChildren().clear();

        if (all.isEmpty()) {
            list.getChildren().add(Ui.emptyState(Messages.get("notifications.empty"),
                    new Label(Messages.get("notifications.empty_action"))));
            return;
        }
        for (Notification n : all) {
            list.getChildren().add(row(n));
        }
    }

    private Node row(Notification n) {
        Label dot = new Label(n.isRead() ? "○" : "●");
        dot.setStyle("-fx-font-size: 14px; -fx-text-fill: "
                + (n.isRead() ? "#b6c2cc" : severityColor(n.getSeverity())) + ";");

        Label title = new Label(n.getTitle());
        title.getStyleClass().add(n.isRead() ? "muted" : "h3");
        title.setWrapText(true);

        Label meta = Ui.muted(severityLabel(n.getSeverity()) + "  ·  "
                + Ui.dateTime(n.getCreatedAt())
                + (n.getEntityType() != null ? "  ·  " + n.getEntityType() : ""));
        meta.setStyle("-fx-font-size: 11px;");

        Label message = new Label(Ui.orDash(n.getMessage()));
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #52606d;");

        VBox body = new VBox(2, title, meta, message);
        HBox row = new HBox(12, dot, body);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.getStyleClass().add("surface");
        row.setStyle("-fx-cursor: hand;");
        row.setOnMouseClicked(e -> {
            if (!n.isRead()) {
                AppContext.get().notifications().markRead(n.getId());
            }
            if (n.getEntityType() != null) {
                switch (n.getEntityType()) {
                    case "REQUEST" -> MainShell.shellNavigate("requests", null);
                    case "WORK_ORDER" -> MainShell.shellNavigate("workorders",
                            n.getEntityId() != null ? "detail:" + n.getEntityId() : null);
                    case "MAINTENANCE_PLAN" -> MainShell.shellNavigate("preventive", null);
                    case "SPARE_PART" -> MainShell.shellNavigate("parts", null);
                    case "CONTRACT" -> MainShell.shellNavigate("contracts", null);
                    case "EQUIPMENT" -> MainShell.shellNavigate("equipment",
                            n.getEntityId() != null ? "detail:" + n.getEntityId() : null);
                    default -> rebuild();
                }
            } else {
                rebuild();
            }
        });
        return row;
    }

    private String severityColor(String severity) {
        if (severity == null) return "#1b8a9c";
        return switch (severity) {
            case "CRITICAL" -> "#b3261e";
            case "WARN" -> "#d97706";
            case "SUCCESS" -> "#1e7e34";
            default -> "#1b8a9c";
        };
    }

    private String severityLabel(String severity) {
        if (severity == null) return "Info";
        return switch (severity) {
            case "CRITICAL" -> "Critique";
            case "WARN" -> "Avertissement";
            case "SUCCESS" -> "Succès";
            default -> "Information";
        };
    }
}
