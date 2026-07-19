package com.druvu.lib.fx.example.pages;

import com.druvu.lib.fx.bus.Delivery;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.example.market.Tick;
import java.util.HashMap;
import java.util.Map;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

/**
 * Live charts fed by bus {@link Tick}s: % change since feed start per symbol (line) and cumulative traded lots per
 * symbol (bar). The FX-mode subscription is the whole threading story - ticks originate on the feed's virtual thread,
 * handlers land on the FX thread, so chart mutation below needs no locks and no runLater.
 */
public final class DashboardPage {

    private static final int WINDOW = 150;

    private final LineChart<Number, Number> priceChart;
    private final XYChart.Series<String, Number> volumeSeries = new XYChart.Series<>();
    private final Map<String, XYChart.Series<Number, Number>> priceSeries = new HashMap<>();
    private final Map<String, Double> firstPrice = new HashMap<>();
    private final Map<String, XYChart.Data<String, Number>> volumeBars = new HashMap<>();
    private final Node node;
    private long tickIndex;

    public DashboardPage(FxBus bus) {
        priceChart = buildPriceChart();
        final BarChart<String, Number> volumeChart = buildVolumeChart();

        final javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(priceChart, volumeChart);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.65);
        this.node = split;

        // page lives as long as the app: subscription intentionally not closed
        bus.subscribe(Tick.class, Delivery.FX, this::onTick);
    }

    public Node node() {
        return node;
    }

    private void onTick(Tick tick) {
        final double base = firstPrice.computeIfAbsent(tick.symbol(), s -> tick.price());
        final double pctChange = (tick.price() / base - 1) * 100.0;

        final XYChart.Series<Number, Number> series = priceSeries.computeIfAbsent(tick.symbol(), this::newPriceSeries);
        series.getData().add(new XYChart.Data<>(tickIndex++, pctChange));
        if (series.getData().size() > WINDOW) {
            series.getData().remove(0);
        }

        final XYChart.Data<String, Number> bar = volumeBars.computeIfAbsent(tick.symbol(), this::newVolumeBar);
        bar.setYValue(bar.getYValue().longValue() + tick.qty());
    }

    private XYChart.Series<Number, Number> newPriceSeries(String symbol) {
        final XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(symbol);
        priceChart.getData().add(series);
        return series;
    }

    private XYChart.Data<String, Number> newVolumeBar(String symbol) {
        final XYChart.Data<String, Number> data = new XYChart.Data<>(symbol, 0L);
        volumeSeries.getData().add(data);
        return data;
    }

    private LineChart<Number, Number> buildPriceChart() {
        final NumberAxis x = new NumberAxis();
        x.setLabel("tick");
        x.setForceZeroInRange(false);
        final NumberAxis y = new NumberAxis();
        y.setLabel("% change since start");
        y.setForceZeroInRange(false);
        final LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setTitle("Prices - % change");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        return chart;
    }

    private BarChart<String, Number> buildVolumeChart() {
        final CategoryAxis x = new CategoryAxis();
        x.setLabel("symbol");
        final NumberAxis y = new NumberAxis();
        y.setLabel("lots");
        final BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle("Cumulative volume");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.getData().add(volumeSeries);
        return chart;
    }
}
