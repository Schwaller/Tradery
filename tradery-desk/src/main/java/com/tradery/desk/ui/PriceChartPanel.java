package com.tradery.desk.ui;

import com.tradery.charts.chart.CandlestickChart;
import com.tradery.charts.core.ChartCoordinator;
import com.tradery.core.model.Candle;
import com.tradery.desk.ui.charts.DeskDataProvider;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Price chart panel for Desk using tradery-charts CandlestickChart.
 * Displays candlestick chart with live updates.
 */
public class PriceChartPanel extends JPanel {

    private static final Color BACKGROUND_COLOR = new Color(30, 30, 30);

    private final DeskDataProvider dataProvider;
    private final CandlestickChart candlestickChart;

    public PriceChartPanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);

        // Create data provider
        dataProvider = new DeskDataProvider();

        // Create candlestick chart (no coordinator needed for single chart)
        candlestickChart = new CandlestickChart(null, "");
        candlestickChart.initialize();
        candlestickChart.setCandlestickMode(true);

        // Add chart panel
        add(candlestickChart.getChartPanel(), BorderLayout.CENTER);
    }

    /**
     * Set historical candles.
     */
    public void setCandles(List<Candle> historicalCandles, String symbol, String timeframe) {
        dataProvider.setCandles(historicalCandles, symbol, timeframe);
        SwingUtilities.invokeLater(() -> candlestickChart.updateData(dataProvider));
    }

    /**
     * Update the current (incomplete) candle.
     */
    public void updateCurrentCandle(Candle candle) {
        dataProvider.updateCandle(candle);
        SwingUtilities.invokeLater(() -> candlestickChart.updateData(dataProvider));
    }

    /**
     * Add a completed candle.
     */
    public void addCandle(Candle candle) {
        dataProvider.updateCandle(candle);
        SwingUtilities.invokeLater(() -> candlestickChart.updateData(dataProvider));
    }

    /**
     * Clear the chart.
     */
    public void clear() {
        dataProvider.setCandles(List.of(), "", "");
        SwingUtilities.invokeLater(() -> candlestickChart.updateData(dataProvider));
    }

    /**
     * Get the data provider for adding overlays or indicators.
     */
    public DeskDataProvider getDataProvider() {
        return dataProvider;
    }

    /**
     * Get the underlying candlestick chart for customization.
     */
    public CandlestickChart getCandlestickChart() {
        return candlestickChart;
    }
}
