package com.tradery.desk.ui;

import com.tradery.core.model.Candle;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Simple price chart panel for Desk.
 * Displays candlestick chart with live updates.
 */
public class PriceChartPanel extends JPanel {

    private static final Color BACKGROUND_COLOR = new Color(30, 30, 30);
    private static final Color GRID_COLOR = new Color(60, 60, 60);
    private static final Color UP_COLOR = new Color(38, 166, 154);
    private static final Color DOWN_COLOR = new Color(239, 83, 80);
    private static final Color TEXT_COLOR = new Color(200, 200, 200);

    private final List<Candle> candles = new ArrayList<>();
    private DefaultOHLCDataset dataset;
    private JFreeChart chart;
    private ChartPanel chartPanel;
    private String symbol = "";
    private String timeframe = "";

    public PriceChartPanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);
        createChart();
    }

    private void createChart() {
        // Create empty dataset
        dataset = new DefaultOHLCDataset("Price", new OHLCDataItem[0]);

        // Date axis
        DateAxis dateAxis = new DateAxis();
        dateAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));
        dateAxis.setTickLabelPaint(TEXT_COLOR);
        dateAxis.setAxisLinePaint(GRID_COLOR);

        // Price axis
        NumberAxis priceAxis = new NumberAxis();
        priceAxis.setAutoRangeIncludesZero(false);
        priceAxis.setTickLabelPaint(TEXT_COLOR);
        priceAxis.setAxisLinePaint(GRID_COLOR);

        // Candlestick renderer
        CandlestickRenderer renderer = new CandlestickRenderer();
        renderer.setUpPaint(UP_COLOR);
        renderer.setDownPaint(DOWN_COLOR);
        renderer.setSeriesPaint(0, TEXT_COLOR);
        renderer.setUseOutlinePaint(false);
        renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);

        // Create plot
        XYPlot plot = new XYPlot(dataset, dateAxis, priceAxis, renderer);
        plot.setBackgroundPaint(BACKGROUND_COLOR);
        plot.setDomainGridlinePaint(GRID_COLOR);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setOutlinePaint(GRID_COLOR);

        // Create chart
        chart = new JFreeChart(null, null, plot, false);
        chart.setBackgroundPaint(BACKGROUND_COLOR);

        // Create chart panel
        chartPanel = new ChartPanel(chart);
        chartPanel.setBackground(BACKGROUND_COLOR);
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setMouseWheelEnabled(true);

        add(chartPanel, BorderLayout.CENTER);
    }

    /**
     * Set historical candles.
     */
    public void setCandles(List<Candle> historicalCandles, String symbol, String timeframe) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.candles.clear();
        this.candles.addAll(historicalCandles);
        updateDataset();
    }

    /**
     * Update the current (incomplete) candle.
     */
    public void updateCurrentCandle(Candle candle) {
        if (candles.isEmpty()) {
            candles.add(candle);
        } else {
            // Replace last candle if same timestamp, otherwise add
            Candle last = candles.get(candles.size() - 1);
            if (last.timestamp() == candle.timestamp()) {
                candles.set(candles.size() - 1, candle);
            } else {
                candles.add(candle);
                // Keep max 500 candles
                while (candles.size() > 500) {
                    candles.remove(0);
                }
            }
        }
        updateDataset();
    }

    /**
     * Add a completed candle.
     */
    public void addCandle(Candle candle) {
        candles.add(candle);
        // Keep max 500 candles
        while (candles.size() > 500) {
            candles.remove(0);
        }
        updateDataset();
    }

    private void updateDataset() {
        if (candles.isEmpty()) {
            return;
        }

        OHLCDataItem[] items = new OHLCDataItem[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            items[i] = new OHLCDataItem(
                new Date(c.timestamp()),
                c.open(),
                c.high(),
                c.low(),
                c.close(),
                c.volume()
            );
        }

        SwingUtilities.invokeLater(() -> {
            dataset = new DefaultOHLCDataset(symbol + " " + timeframe, items);
            XYPlot plot = (XYPlot) chart.getPlot();
            plot.setDataset(dataset);

            // Auto-range Y axis to visible data
            NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
            yAxis.setAutoRange(true);
        });
    }

    /**
     * Clear the chart.
     */
    public void clear() {
        candles.clear();
        dataset = new DefaultOHLCDataset("Price", new OHLCDataItem[0]);
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setDataset(dataset);
    }
}
