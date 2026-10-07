package com.medmaint.ui.components;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fabrique de graphiques cohérents (JavaFX charts) avec style uniforme.
 * Toutes les séries proviennent de données réelles calculées par les services.
 */
public final class ChartFactory {

    /** Palette sobre (bleu-vert clinique + neutres). */
    public static final String[] PALETTE = {
            "#0f4c5c", "#1b8a9c", "#5aa9b8", "#8fc7d1",
            "#364152", "#6b7a8f", "#c6d2d9", "#d97706", "#1e7e34", "#b3261e"
    };

    private ChartFactory() {
    }

    public static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("chart-title");
        return l;
    }

    /** Camembert de répartition. */
    public static PieChart pie(String title, List<Object[]> data /* [label, value] */) {
        ObservableList<PieChart.Data> items = FXCollections.observableArrayList();
        for (Object[] row : data) {
            items.add(new PieChart.Data(String.valueOf(row[0]), ((Number) row[1]).doubleValue()));
        }
        PieChart chart = new PieChart(items);
        chart.setTitle(title);
        chart.setLabelsVisible(true);
        chart.setLegendVisible(true);
        chart.setLegendSide(javafx.geometry.Side.BOTTOM);
        chart.setPrefHeight(260);
        chart.setMinHeight(220);
        chart.getStyleClass().add("chart");
        chart.setClockwise(true);
        int i = 0;
        for (PieChart.Data d : chart.getData()) {
            String color = PALETTE[i++ % PALETTE.length];
            d.getNode().setStyle("-fx-pie-color: " + color + ";");
        }
        return chart;
    }

    /** Barres horizontales (facilement lisibles pour des libellés longs). */
    public static BarChart<String, Number> barH(String title, String seriesName, List<Object[]> data) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(true);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setPrefHeight(Math.max(240, Math.min(420, 70 + data.size() * 34)));
        chart.setCategoryGap(0.4);
        chart.setBarGap(0.3);
        chart.getStyleClass().add("chart");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(seriesName);
        for (Object[] row : data) {
            series.getData().add(new XYChart.Data<>(String.valueOf(row[0]), ((Number) row[1]).doubleValue()));
        }
        chart.getData().add(series);
        styleBars(chart, seriesName != null && seriesName.contains("Coût") ? "#1b8a9c" : "#0f4c5c");
        return chart;
    }

    /** Série principale + secondaire (correctif/préventif). */
    public static BarChart<String, Number> barMulti(String title, List<String> categories,
                                                    String series1, List<Number> values1,
                                                    String series2, List<Number> values2) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(true);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle(title);
        chart.setPrefHeight(260);
        chart.setCategoryGap(0.35);
        chart.setBarGap(0.25);
        chart.getStyleClass().add("chart");

        XYChart.Series<String, Number> s1 = new XYChart.Series<>(series1, FXCollections.observableArrayList());
        XYChart.Series<String, Number> s2 = new XYChart.Series<>(series2, FXCollections.observableArrayList());
        for (int i = 0; i < categories.size(); i++) {
            s1.getData().add(new XYChart.Data<>(categories.get(i), values1.get(i).doubleValue()));
            if (values2 != null) {
                s2.getData().add(new XYChart.Data<>(categories.get(i), values2.get(i).doubleValue()));
            }
        }
        chart.getData().add(s1);
        if (values2 != null) {
            chart.getData().add(s2);
        }
        styleBars(chart, "#0f4c5c");
        // getNode() est null tant que le graphe n'a pas été rendu : style protégé
        if (values2 != null && s2.getNode() != null) {
            s2.getNode().setStyle("-fx-bar-fill: #8fc7d1;");
        }
        return chart;
    }

    /** Ligne (tendance mensuelle : demandes, coûts, pannes). */
    public static LineChart<String, Number> line(String title, String seriesName,
                                                 List<String> categories, List<Number> values) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(true);
        LineChart<String, Number> chart = new LineChart<>(x, y);
        chart.setTitle(title);
        chart.setPrefHeight(260);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.getStyleClass().add("chart");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(seriesName);
        for (int i = 0; i < categories.size(); i++) {
            series.getData().add(new XYChart.Data<>(categories.get(i), values.get(i).doubleValue()));
        }
        chart.getData().add(series);
        if (series.getNode() != null) {
            series.getNode().setStyle(
                    "-fx-stroke: #0f4c5c; -fx-stroke-width: 2.5;");
        }
        chart.setAnimated(false);
        return chart;
    }

    /** Double ligne. */
    public static LineChart<String, Number> lineMulti(String title, String name1, String name2,
                                                      List<String> categories,
                                                      List<Number> v1, List<Number> v2) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(true);
        LineChart<String, Number> chart = new LineChart<>(x, y);
        chart.setTitle(title);
        chart.setPrefHeight(260);
        chart.setLegendVisible(true);
        chart.setLegendSide(javafx.geometry.Side.BOTTOM);
        chart.getStyleClass().add("chart");

        XYChart.Series<String, Number> s1 = new XYChart.Series<>(name1, FXCollections.observableArrayList());
        XYChart.Series<String, Number> s2 = new XYChart.Series<>(name2, FXCollections.observableArrayList());
        for (int i = 0; i < categories.size(); i++) {
            s1.getData().add(new XYChart.Data<>(categories.get(i), v1.get(i).doubleValue()));
            s2.getData().add(new XYChart.Data<>(categories.get(i), v2.get(i).doubleValue()));
        }
        chart.getData().addAll(s1, s2);
        if (s1.getNode() != null) s1.getNode().setStyle("-fx-stroke: #0f4c5c; -fx-stroke-width: 2.5;");
        if (s2.getNode() != null) s2.getNode().setStyle("-fx-stroke: #d97706; -fx-stroke-width: 2.5;");
        chart.setAnimated(false);
        return chart;
    }

    private static void styleBars(BarChart<String, Number> chart, String color) {
        chart.setAnimated(false);
        chart.getData().forEach(s -> {
            if (s.getNode() != null) {
                s.getNode().setStyle("-fx-bar-fill: " + color + ";");
            }
        });
    }

    /** Boîte graphique standard (titre + contenu). */
    public static Node box(String title, Node chart) {
        Label t = title(title);
        javafx.scene.layout.VBox body = new javafx.scene.layout.VBox(chart);
        body.setPadding(new javafx.geometry.Insets(6, 10, 10, 10));
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(6, t, body);
        box.getStyleClass().add("chart-panel");
        box.setPadding(new javafx.geometry.Insets(12));
        return box;
    }
}
