package com.medmaint.ui.components;

import com.medmaint.persistence.Page;
import com.medmaint.ui.i18n.Messages;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Table de données générique : recherche, tri par colonne, pagination serveur,
 * sélection multiple, visibilité de colonnes, état vide avec action.
 *
 * @param <T> type de ligne
 */
public final class DataTable<T> extends VBox {

    /** Définition d'une colonne. */
    public static final class Col<T> {
        final String key;
        final String title;
        final Function<T, String> value;
        final double width;
        boolean sortable = true;
        boolean visible = true;

        public Col(String key, String title, Function<T, String> value, double width) {
            this.key = key;
            this.title = title;
            this.value = value;
            this.width = width;
        }

        public Col<T> sortable(boolean s) {
            this.sortable = s;
            return this;
        }

        public Col<T> hideByDefault() {
            this.visible = false;
            return this;
        }
    }

    private final List<Col<T>> columns = new ArrayList<>();
    private final TableView<T> table = new TableView<>();
    private final Label infoLabel = new Label();
    private final Label emptyLabel = new Label();
    private final Pagination pagination = new Pagination();
    private final Map<String, CheckBox> columnToggles = new LinkedHashMap<>();

    private int page = 1;
    private int pageSize = 20;
    private String sortCol;
    private String sortDir = "ASC";
    private long total;

    private Function<Integer, Page<T>> loader;
    private final ObservableList<T> rows = FXCollections.observableArrayList();

    public DataTable(String emptyMessage, Node emptyAction) {
        setSpacing(8);
        setVgrow(table, Priority.ALWAYS);

        emptyLabel.setText(emptyMessage);
        VBox empty = Ui.emptyState(emptyMessage, emptyAction);
        empty.setAlignment(Pos.CENTER);
        table.setPlaceholder(empty);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setFixedCellSize(34);

        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);
        infoLabel.getStyleClass().add("pager-label");
        footer.getChildren().add(infoLabel);

        pagination.setPageFactory(this::buildPageControls);
        HBox.setHgrow(pagination, Priority.ALWAYS);

        HBox right = new HBox(6);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.getChildren().add(pagination);

        HBox bottom = new HBox(12, footer, right);
        bottom.setAlignment(Pos.CENTER);
        getChildren().addAll(table, bottom);
    }

    public void addColumn(Col<T> col) {
        columns.add(col);
        TableColumn<T, String> tc = new TableColumn<>(col.title);
        tc.setPrefWidth(col.width);
        tc.setSortable(col.sortable);
        tc.setCellValueFactory(data -> new SimpleStringProperty(
                col.value.apply(data.getValue()) == null ? "" : col.value.apply(data.getValue())));
        tc.setCellFactory(v -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                }
            }
        });
        if (col.sortable) {
            tc.setSortType(TableColumn.SortType.ASCENDING);
        }
        table.getColumns().add(tc);
        if (!col.visible) {
            tc.setVisible(false);
        }
        // Repère la colonne triée pour piloter la pagination côté serveur
        table.getSortOrder().addListener((javafx.collections.ListChangeListener<TableColumn<T, ?>>) change -> {
            if (!change.wasAdded()) {
                return;
            }
            for (TableColumn<T, ?> sorted : change.getAddedSubList()) {
                for (Col<T> c : columns) {
                    if (c.title.equals(sorted.getText())) {
                        sortCol = c.key;
                    }
                }
                sortDir = sorted.getSortType() == TableColumn.SortType.DESCENDING ? "DESC" : "ASC";
            }
            refresh();
        });
    }

    /** Menu de visibilité de colonnes (cases à cocher). */
    public Node columnMenu() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 0, 4, 0));
        Label title = new Label(Messages.get("common.actions"));
        title.getStyleClass().add("section-label");
        for (TableColumn<T, ?> tc : table.getColumns()) {
            CheckBox cb = new CheckBox(tc.getText());
            cb.setSelected(tc.isVisible());
            tc.visibleProperty().bind(cb.selectedProperty());
            columnToggles.put(tc.getText(), cb);
            box.getChildren().add(cb);
        }
        return box;
    }

    public void setLoader(Function<Integer, Page<T>> loader) {
        this.loader = loader;
    }

    public void setPageSize(int size) {
        this.pageSize = size;
        this.page = 1;
    }

    public void setSort(String col, String dir) {
        this.sortCol = col;
        this.sortDir = dir;
    }

    public String sortCol() {
        return sortCol;
    }

    public String sortDir() {
        return sortDir;
    }

    /** Recharge la page courante. */
    public void refresh() {
        if (loader == null) {
            return;
        }
        Page<T> result = loader.apply(page);
        total = result.total();
        rows.setAll(result.items());
        table.setItems(rows);
        int pages = result.totalPages();
        if (page > pages) {
            page = pages;
            result = loader.apply(page);
            rows.setAll(result.items());
        }
        infoLabel.setText(Messages.get("common.page", page, Math.max(1, (int) Math.ceil((double) total / pageSize)))
                + " · " + total + " " + Messages.get("common.total"));
        updatePagination((int) Math.max(1, (long) Math.ceil((double) total / pageSize)));
    }

    public void resetAndRefresh() {
        page = 1;
        refresh();
    }

    private void updatePagination(int pageCount) {
        pagination.setPageCount(Math.min(pageCount, 50));
        if (pagination.getCurrentPageIndex() != page - 1 && page - 1 < pagination.getPageCount()) {
            pagination.setCurrentPageIndex(page - 1);
        }
    }

    private Node buildPageControls(int index) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6));

        Button prev = new Button("‹");
        prev.getStyleClass().add("pager-btn");
        prev.setOnAction(e -> {
            if (page > 1) {
                page--;
                refresh();
            }
        });
        prev.setDisable(page <= 1);

        Label lbl = new Label(page + " / " + Math.max(1, (int) Math.ceil((double) total / pageSize)));
        lbl.getStyleClass().add("pager-label");

        Button next = new Button("›");
        next.getStyleClass().add("pager-btn");
        next.setOnAction(e -> {
            if (page * pageSize < total) {
                page++;
                refresh();
            }
        });
        next.setDisable(page * pageSize >= total);

        // Boutons de page autour de la page courante
        box.getChildren().add(prev);
        int pages = (int) Math.max(1, (int) Math.ceil((double) total / pageSize));
        int start = Math.max(1, page - 2);
        int end = Math.min(pages, start + 4);
        start = Math.max(1, end - 4);
        for (int i = start; i <= end; i++) {
            final int p = i;
            Button b = new Button(String.valueOf(i));
            b.getStyleClass().add("pager-btn");
            if (p == page) {
                b.getStyleClass().add("active");
            }
            b.setOnAction(e -> {
                page = p;
                refresh();
            });
            box.getChildren().add(b);
        }
        box.getChildren().addAll(lbl, next);
        return box;
    }

    // ---------- Accès ----------

    public TableView<T> table() {
        return table;
    }

    public List<T> selectedItems() {
        return new ArrayList<>(table.getSelectionModel().getSelectedItems());
    }

    public T selectedItem() {
        return table.getSelectionModel().getSelectedItem();
    }

    public void onDoubleClick(Consumer<T> handler) {
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                handler.accept(table.getSelectionModel().getSelectedItem());
            }
        });
    }

    public void onSelectionChange(BiConsumer<T, Boolean> handler) {
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) ->
                handler.accept(nv, nv != null));
    }

    public int currentPage() {
        return page;
    }

    public int pageSize() {
        return pageSize;
    }

    public long total() {
        return total;
    }

    /** Ajoute une colonne d'actions (boutons en fin de ligne). */
    public void addActionsColumn(List<RowAction<T>> actions) {
        TableColumn<T, Void> tc = new TableColumn<>(Messages.get("common.actions"));
        tc.setSortable(false);
        tc.setPrefWidth(40 + actions.size() * 84.0);
        tc.setCellFactory(col -> new TableCell<T, Void>() {
            private final HBox box = new HBox(6);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                box.getChildren().clear();
                T rowData = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || rowData == null) {
                    setGraphic(null);
                    return;
                }
                for (RowAction<T> a : actions) {
                    Button b = new Button(a.label());
                    b.getStyleClass().add(a.styleClass());
                    b.setTooltip(new Tooltip(a.label()));
                    b.setOnAction(e -> a.handler().accept(rowData));
                    box.getChildren().add(b);
                }
                setGraphic(box);
            }
        });
        table.getColumns().add(tc);
    }

    /** Action de ligne. */
    public record RowAction<T>(String label, String styleClass, Consumer<T> handler) {
    }
}
