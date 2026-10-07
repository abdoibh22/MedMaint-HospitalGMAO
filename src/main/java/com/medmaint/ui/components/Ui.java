package com.medmaint.ui.components;

import com.medmaint.ui.i18n.Messages;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Aides UI réutilisables : badges de statut, formats FR, libellés, champs.
 * Les badges affichent TOUJOURS un texte : la couleur n'est jamais le seul signal.
 */
public final class Ui {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Ui() {
    }

    // ---------- Formats ----------

    public static String date(LocalDate d) {
        return d == null ? "—" : DATE.format(d);
    }

    public static String dateTime(LocalDateTime d) {
        return d == null ? "—" : DATE_TIME.format(d);
    }

    public static String money(BigDecimal v) {
        if (v == null) return "—";
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.FRANCE);
        return nf.format(v);
    }

    public static String number(BigDecimal v, int decimals) {
        if (v == null) return "—";
        return String.format(Locale.FRANCE, "%,.0f", v).replace('\u202f', ' ')
                + (decimals > 0 ? "" : "");
    }

    public static String decimal(BigDecimal v) {
        if (v == null) return "—";
        return String.format(Locale.FRANCE, "%.1f", v.doubleValue());
    }

    public static String percent(BigDecimal v) {
        if (v == null) return "—";
        return String.format(Locale.FRANCE, "%.1f %%", v.doubleValue());
    }

    public static String long0(long v) {
        return String.format(Locale.FRANCE, "%,d", v).replace('\u202f', ' ').replace(',', ' ');
    }

    public static String orDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    // ---------- Badges ----------

    /** Badge coloré avec texte (le ton vient de l'énumération). */
    public static Label badge(String text, String tone) {
        Label l = new Label(text == null ? "—" : text);
        l.getStyleClass().add("badge");
        String cls = switch (tone == null ? "" : tone) {
            case "ok" -> "badge-ok";
            case "warn" -> "badge-warn";
            case "danger" -> "badge-danger";
            case "info" -> "badge-info";
            case "active" -> "badge-active";
            case "critical" -> "badge-critical";
            case "muted" -> "badge-muted";
            case "low" -> "badge-low";
            case "normal" -> "badge-normal";
            case "high" -> "badge-high";
            case "urgent" -> "badge-urgent";
            default -> "";
        };
        if (!cls.isEmpty()) {
            l.getStyleClass().add(cls);
        }
        return l;
    }

    public static Label badge(String text, String tone, String title) {
        Label l = badge(text, tone);
        if (title != null && !title.isEmpty()) {
            l.setTooltip(new Tooltip(title));
        }
        return l;
    }

    public static HBox hrow(Node... nodes) {
        HBox b = new HBox(8, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    public static Label sectionLabel(String key) {
        Label l = new Label(Messages.get(key).toUpperCase());
        l.getStyleClass().add("section-label");
        return l;
    }

    public static Label h1(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("h1");
        return l;
    }

    public static Label h2(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("h2");
        return l;
    }

    public static Label h3(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("h3");
        return l;
    }

    public static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        return l;
    }

    /** Panneau « carte » standard. */
    public static javafx.scene.layout.VBox panel(Node header, Node body) {
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox();
        box.getStyleClass().add("panel");
        if (header != null) {
            javafx.scene.layout.HBox hd = new javafx.scene.layout.HBox(header);
            hd.getStyleClass().add("panel-header");
            box.getChildren().add(hd);
        }
        javafx.scene.layout.VBox bd = new javafx.scene.layout.VBox(body);
        bd.getStyleClass().add("panel-body");
        box.getChildren().add(bd);
        return box;
    }

    /** État vide avec message + action optionnelle. */
    public static javafx.scene.layout.VBox emptyState(String message, Node action) {
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10);
        box.getStyleClass().add("empty-state");
        Label icon = new Label("∅");
        icon.setStyle("-fx-font-size: 30px; -fx-text-fill: #b6c2cc;");
        Label msg = new Label(message);
        msg.getStyleClass().add("label");
        box.getChildren().addAll(icon, msg);
        if (action != null) {
            box.getChildren().add(action);
        }
        return box;
    }
}
