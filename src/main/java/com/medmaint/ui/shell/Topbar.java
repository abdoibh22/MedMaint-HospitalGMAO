package com.medmaint.ui.shell;

import com.medmaint.domain.model.Notification;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import com.medmaint.ui.navigation.Router;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Barre supérieure : titre + fil d'Ariane, recherche globale,
 * notifications (compteur + popup), menu utilisateur avec rôle.
 */
public final class Topbar extends HBox {

    private final Router router;
    private final TextField searchField = new TextField();
    private final Label titleLabel = new Label();
    private final Label breadcrumb = new Label();
    private final Label unreadBadge = new Label();
    private final Label userName = new Label();
    private final Label roleChip = new Label();
    private final Label avatar = new Label();
    private Button bellBtn;

    private Runnable openSidebar;
    private Runnable searchAction;

    public Topbar(Router router) {
        this.router = router;
        setAlignment(Pos.CENTER);
        setSpacing(14);
        setPadding(new Insets(8, 18, 8, 18));
        getStyleClass().add("topbar");

        Button menuBtn = new Button("☰");
        menuBtn.getStyleClass().add("icon-btn");
        menuBtn.setTooltip(new Tooltip("Afficher / masquer le menu"));
        menuBtn.setOnAction(e -> {
            if (openSidebar != null) openSidebar.run();
        });

        VBox titles = new VBox(1);
        titleLabel.getStyleClass().add("h2");
        breadcrumb.getStyleClass().add("dim");
        titles.getChildren().addAll(titleLabel, breadcrumb);

        searchField.setPromptText(Messages.get("search.placeholder"));
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(340);
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && searchAction != null) {
                searchAction.run();
            }
        });

        Button searchBtn = new Button("⌕");
        searchBtn.getStyleClass().add("icon-btn");
        searchBtn.setTooltip(new Tooltip(Messages.get("search.title") + " (Entrée)"));
        searchBtn.setOnAction(e -> {
            if (searchAction != null) searchAction.run();
        });

        HBox searchBox = new HBox(4, searchField, searchBtn);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        bellBtn = new Button("🔔");
        bellBtn.getStyleClass().add("icon-btn");
        bellBtn.setTooltip(new Tooltip(Messages.get("notifications.title")));
        unreadBadge.getStyleClass().add("badge-count");
        unreadBadge.setText("0");
        javafx.scene.layout.StackPane bellWrap = new javafx.scene.layout.StackPane(bellBtn, unreadBadge);
        javafx.scene.layout.StackPane.setAlignment(unreadBadge, javafx.geometry.Pos.TOP_RIGHT);
        bellBtn.setOnAction(e -> openNotifications());

        avatar.getStyleClass().add("avatar");
        avatar.setMinWidth(34);
        avatar.setMinHeight(34);
        avatar.setPrefWidth(34);
        avatar.setPrefHeight(34);

        userName.getStyleClass().add("h3");
        roleChip.getStyleClass().add("role-chip");

        VBox userBox = new VBox(1, userName, roleChip);
        userBox.setAlignment(Pos.CENTER_LEFT);

        Button userBtn = new Button();
        userBtn.setGraphic(new HBox(10, avatar, userBox));
        userBtn.getStyleClass().add("icon-btn");
        userBtn.setOnAction(e -> openUserMenu(userBtn));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(menuBtn, titles, spacer, searchBox, bellWrap, userBtn);
        refreshUser();
    }

    public void bind(Runnable openSidebar, Runnable searchAction) {
        this.openSidebar = openSidebar;
        this.searchAction = searchAction;
    }

    public void setContext(String titleKey, String context) {
        titleLabel.setText(Messages.get(titleKey));
        breadcrumb.setText(context == null || context.isBlank() ? "" : context);
    }

    public TextField searchField() {
        return searchField;
    }

    public void refreshUser() {
        Session s = Session.get();
        if (s == null) {
            return;
        }
        userName.setText(s.fullName());
        roleChip.setText(s.roleLabel());
        avatar.setText(s.fullName() != null && s.fullName().length() >= 2
                ? s.fullName().substring(0, 2).toUpperCase() : "?");
        refreshUnread();
    }

    public void refreshUnread() {
        Session s = Session.get();
        if (s == null) return;
        long unread = AppContext.get().notifications().unreadCount(s.userId(), s.role().name());
        unreadBadge.setText(String.valueOf(unread));
        unreadBadge.setVisible(unread > 0);
    }

    private void openNotifications() {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        Session s = Session.get();
        List<Notification> list = AppContext.get().notifications().forUser(s.userId(), s.role().name(), 12);
        if (list.isEmpty()) {
            Label empty = new Label(Messages.get("notifications.empty"));
            empty.getStyleClass().add("muted");
            empty.setPadding(new Insets(10));
            menu.getItems().add(new javafx.scene.control.CustomMenuItem(empty));
        } else {
            for (Notification n : list) {
                Label title = new Label((n.isRead() ? "" : "● ") + n.getTitle());
                title.setWrapText(true);
                title.setMaxWidth(340);
                Label time = new Label(n.getCreatedAt() == null ? "" : Ui.dateTime(n.getCreatedAt()));
                time.getStyleClass().add("dim");
                VBox item = new VBox(2, title, time);
                item.setPadding(new Insets(6));
                javafx.scene.control.CustomMenuItem mi = new javafx.scene.control.CustomMenuItem(item);
                mi.setOnAction(e -> {
                    AppContext.get().notifications().markRead(n.getId());
                    refreshUnread();
                    if (n.getEntityType() != null) {
                        routeToNotification(n);
                    }
                });
                menu.getItems().add(mi);
            }
            menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            javafx.scene.control.MenuItem all = new javafx.scene.control.MenuItem(
                    Messages.get("notifications.mark_all_read"));
            all.setOnAction(e -> {
                AppContext.get().notifications().markAllRead(s.userId(), s.role().name());
                refreshUnread();
            });
            menu.getItems().add(all);
        }
        menu.show(bellBtn, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void routeToNotification(Notification n) {
        switch (n.getEntityType() == null ? "" : n.getEntityType()) {
            case "REQUEST" -> com.medmaint.ui.MainShell.shellNavigate("requests", null);
            case "WORK_ORDER" -> com.medmaint.ui.MainShell.shellNavigate("workorders", null);
            case "MAINTENANCE_PLAN" -> com.medmaint.ui.MainShell.shellNavigate("preventive", null);
            case "SPARE_PART" -> com.medmaint.ui.MainShell.shellNavigate("parts", null);
            case "CONTRACT" -> com.medmaint.ui.MainShell.shellNavigate("contracts", null);
            case "EQUIPMENT" -> com.medmaint.ui.MainShell.shellNavigate("equipment", null);
            default -> { /* rien */ }
        }
    }

    private void openUserMenu(javafx.scene.Node anchor) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem profile = new javafx.scene.control.MenuItem(
                Messages.get("nav.profile"));
        profile.setOnAction(e -> com.medmaint.ui.MainShell.shellNavigate("profile", null));
        javafx.scene.control.MenuItem logout = new javafx.scene.control.MenuItem(
                Messages.get("nav.logout"));
        logout.setOnAction(e -> com.medmaint.ui.MainShell.logout());
        menu.getItems().addAll(profile, new javafx.scene.control.SeparatorMenuItem(), logout);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }
}
