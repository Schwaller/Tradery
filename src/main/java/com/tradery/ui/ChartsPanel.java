package com.tradery.ui;

import com.tradery.model.Candle;
import com.tradery.model.Trade;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.panel.CrosshairOverlay;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.Millisecond;

import javax.swing.*;
import java.awt.*;
import java.awt.BasicStroke;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Chart panel showing price candlesticks and equity curve.
 * Uses JFreeChart for rendering.
 */
public class ChartsPanel extends JPanel {

    private org.jfree.chart.ChartPanel priceChartPanel;
    private org.jfree.chart.ChartPanel equityChartPanel;
    private org.jfree.chart.ChartPanel comparisonChartPanel;
    private org.jfree.chart.ChartPanel capitalUsageChartPanel;
    private org.jfree.chart.ChartPanel tradePLChartPanel;

    private JFreeChart priceChart;
    private JFreeChart equityChart;
    private JFreeChart comparisonChart;
    private JFreeChart capitalUsageChart;
    private JFreeChart tradePLChart;

    private Crosshair priceCrosshair;
    private Crosshair equityCrosshair;
    private Crosshair comparisonCrosshair;
    private Crosshair capitalUsageCrosshair;
    private Crosshair tradePLCrosshair;

    private Consumer<String> onStatusUpdate;
    private List<Candle> currentCandles;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm");

    public ChartsPanel() {
        setLayout(new GridLayout(5, 1, 0, 1));
        setBorder(null);

        initializeCharts();
        setupCrosshairs();
    }

    public void setOnStatusUpdate(Consumer<String> callback) {
        this.onStatusUpdate = callback;
    }

    private void initializeCharts() {
        // Price chart (placeholder)
        priceChart = ChartFactory.createTimeSeriesChart(
            null,
            null,
            null,
            new TimeSeriesCollection(),
            true,  // legend
            true,
            false
        );
        stylizeChart(priceChart, "Price");

        priceChartPanel = new org.jfree.chart.ChartPanel(priceChart);
        priceChartPanel.setMouseWheelEnabled(true);
        priceChartPanel.setDomainZoomable(true);
        priceChartPanel.setRangeZoomable(true);

        // Equity chart (placeholder)
        equityChart = ChartFactory.createTimeSeriesChart(
            null,
            null,
            null,
            new TimeSeriesCollection(),
            true,  // legend
            true,
            false
        );
        stylizeChart(equityChart, "Equity");

        equityChartPanel = new org.jfree.chart.ChartPanel(equityChart);
        equityChartPanel.setMouseWheelEnabled(true);

        // Comparison chart (Strategy vs Buy & Hold)
        comparisonChart = ChartFactory.createTimeSeriesChart(
            null,
            null,
            null,
            new TimeSeriesCollection(),
            true,  // legend
            true,
            false
        );
        stylizeChart(comparisonChart, "Strategy vs Buy & Hold");

        comparisonChartPanel = new org.jfree.chart.ChartPanel(comparisonChart);
        comparisonChartPanel.setMouseWheelEnabled(true);

        // Capital usage chart
        capitalUsageChart = ChartFactory.createTimeSeriesChart(
            null,
            null,
            null,
            new TimeSeriesCollection(),
            true,  // legend
            true,
            false
        );
        stylizeChart(capitalUsageChart, "Capital Usage");

        capitalUsageChartPanel = new org.jfree.chart.ChartPanel(capitalUsageChart);
        capitalUsageChartPanel.setMouseWheelEnabled(true);

        // Trade P&L chart
        tradePLChart = ChartFactory.createTimeSeriesChart(
            null,
            null,
            null,
            new TimeSeriesCollection(),
            false,
            true,
            false
        );
        stylizeChart(tradePLChart, "Trade P&L %");

        tradePLChartPanel = new org.jfree.chart.ChartPanel(tradePLChart);
        tradePLChartPanel.setMouseWheelEnabled(true);

        add(priceChartPanel);
        add(equityChartPanel);
        add(comparisonChartPanel);
        add(capitalUsageChartPanel);
        add(tradePLChartPanel);
    }

    private void setupCrosshairs() {
        Color crosshairColor = new Color(150, 150, 150, 180);

        // Price chart crosshair
        priceCrosshair = new Crosshair(Double.NaN);
        priceCrosshair.setPaint(crosshairColor);
        CrosshairOverlay priceOverlay = new CrosshairOverlay();
        priceOverlay.addDomainCrosshair(priceCrosshair);
        priceChartPanel.addOverlay(priceOverlay);

        // Equity chart crosshair
        equityCrosshair = new Crosshair(Double.NaN);
        equityCrosshair.setPaint(crosshairColor);
        CrosshairOverlay equityOverlay = new CrosshairOverlay();
        equityOverlay.addDomainCrosshair(equityCrosshair);
        equityChartPanel.addOverlay(equityOverlay);

        // Comparison chart crosshair
        comparisonCrosshair = new Crosshair(Double.NaN);
        comparisonCrosshair.setPaint(crosshairColor);
        CrosshairOverlay comparisonOverlay = new CrosshairOverlay();
        comparisonOverlay.addDomainCrosshair(comparisonCrosshair);
        comparisonChartPanel.addOverlay(comparisonOverlay);

        // Capital usage chart crosshair
        capitalUsageCrosshair = new Crosshair(Double.NaN);
        capitalUsageCrosshair.setPaint(crosshairColor);
        CrosshairOverlay capitalUsageOverlay = new CrosshairOverlay();
        capitalUsageOverlay.addDomainCrosshair(capitalUsageCrosshair);
        capitalUsageChartPanel.addOverlay(capitalUsageOverlay);

        // Trade P&L chart crosshair
        tradePLCrosshair = new Crosshair(Double.NaN);
        tradePLCrosshair.setPaint(crosshairColor);
        CrosshairOverlay tradePLOverlay = new CrosshairOverlay();
        tradePLOverlay.addDomainCrosshair(tradePLCrosshair);
        tradePLChartPanel.addOverlay(tradePLOverlay);

        // Add mouse listeners to sync crosshairs
        ChartMouseListener listener = new ChartMouseListener() {
            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                updateCrosshairs(event);
            }

            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
            }
        };

        priceChartPanel.addChartMouseListener(listener);
        equityChartPanel.addChartMouseListener(listener);
        comparisonChartPanel.addChartMouseListener(listener);
        capitalUsageChartPanel.addChartMouseListener(listener);
        tradePLChartPanel.addChartMouseListener(listener);
    }

    private void updateCrosshairs(ChartMouseEvent event) {
        JFreeChart chart = event.getChart();
        XYPlot plot = chart.getXYPlot();
        Rectangle2D dataArea = priceChartPanel.getScreenDataArea();

        if (dataArea == null) return;

        double x = event.getTrigger().getX();
        double y = event.getTrigger().getY();

        // Convert to data coordinates
        ValueAxis domainAxis = plot.getDomainAxis();
        double domainValue = domainAxis.java2DToValue(x, dataArea, plot.getDomainAxisEdge());

        // Update all crosshairs
        priceCrosshair.setValue(domainValue);
        equityCrosshair.setValue(domainValue);
        comparisonCrosshair.setValue(domainValue);
        capitalUsageCrosshair.setValue(domainValue);
        tradePLCrosshair.setValue(domainValue);

        // Update status bar
        updateStatus(domainValue);
    }

    private void updateStatus(double timestamp) {
        if (onStatusUpdate == null || currentCandles == null || currentCandles.isEmpty()) return;

        // Find the candle closest to this timestamp
        long ts = (long) timestamp;
        Candle closest = null;
        long minDiff = Long.MAX_VALUE;

        for (Candle c : currentCandles) {
            long diff = Math.abs(c.timestamp() - ts);
            if (diff < minDiff) {
                minDiff = diff;
                closest = c;
            }
        }

        if (closest != null) {
            String status = String.format("%s  O: %.2f  H: %.2f  L: %.2f  C: %.2f  Vol: %.0f",
                dateFormat.format(new Date(closest.timestamp())),
                closest.open(), closest.high(), closest.low(), closest.close(), closest.volume());
            onStatusUpdate.accept(status);
        }
    }

    private void stylizeChart(JFreeChart chart, String title) {
        chart.setBackgroundPaint(new Color(30, 30, 35));

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(20, 20, 25));
        plot.setDomainGridlinePaint(new Color(60, 60, 65));
        plot.setRangeGridlinePaint(new Color(60, 60, 65));
        plot.setOutlinePaint(new Color(60, 60, 65));

        // Date axis formatting
        if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
            dateAxis.setDateFormatOverride(new SimpleDateFormat("MMM d"));
            dateAxis.setTickLabelPaint(Color.LIGHT_GRAY);
            dateAxis.setAxisLinePaint(Color.GRAY);
        }

        plot.getRangeAxis().setTickLabelPaint(Color.LIGHT_GRAY);
        plot.getRangeAxis().setAxisLinePaint(Color.GRAY);
        plot.getRangeAxis().setFixedDimension(60);  // Fixed width for alignment

        // Add title as annotation in top-left corner
        TextTitle textTitle = new TextTitle(title, new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        textTitle.setPaint(new Color(150, 150, 150));
        textTitle.setBackgroundPaint(null);
        XYTitleAnnotation titleAnnotation = new XYTitleAnnotation(0.01, 0.98, textTitle, RectangleAnchor.TOP_LEFT);
        plot.addAnnotation(titleAnnotation);

        // Make legend background transparent if present
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(new Color(0, 0, 0, 0));
            chart.getLegend().setFrame(BlockBorder.NONE);
            chart.getLegend().setItemPaint(Color.LIGHT_GRAY);
            chart.getLegend().setPosition(org.jfree.chart.ui.RectangleEdge.TOP);
            chart.getLegend().setItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            chart.getLegend().setPadding(new org.jfree.chart.ui.RectangleInsets(2, 2, 2, 2));
        }
    }

    /**
     * Update charts with new candle data and trades
     */
    public void updateCharts(List<Candle> candles, List<Trade> trades, double initialCapital) {
        if (candles == null || candles.isEmpty()) return;

        this.currentCandles = candles;
        updatePriceChart(candles, trades);
        updateEquityChart(candles, trades, initialCapital);
        updateComparisonChart(candles, trades, initialCapital);
        updateCapitalUsageChart(candles, trades, initialCapital);
        updateTradePLChart(candles, trades);
    }

    private void updatePriceChart(List<Candle> candles, List<Trade> trades) {
        int n = candles.size();
        Date[] dates = new Date[n];
        double[] highs = new double[n];
        double[] lows = new double[n];
        double[] opens = new double[n];
        double[] closes = new double[n];
        double[] volumes = new double[n];

        for (int i = 0; i < n; i++) {
            Candle c = candles.get(i);
            dates[i] = new Date(c.timestamp());
            highs[i] = c.high();
            lows[i] = c.low();
            opens[i] = c.open();
            closes[i] = c.close();
            volumes[i] = c.volume();
        }

        OHLCDataset dataset = new DefaultHighLowDataset(
            "Price", dates, highs, lows, opens, closes, volumes
        );

        XYPlot plot = priceChart.getXYPlot();
        plot.setDataset(dataset);

        CandlestickRenderer renderer = new CandlestickRenderer();
        renderer.setUpPaint(new Color(76, 175, 80));      // Green
        renderer.setDownPaint(new Color(244, 67, 54));    // Red
        renderer.setSeriesPaint(0, Color.LIGHT_GRAY);
        plot.setRenderer(renderer);

        // Clear existing trade annotations (keep title annotation)
        plot.getAnnotations().stream()
            .filter(a -> a instanceof XYLineAnnotation)
            .toList()
            .forEach(plot::removeAnnotation);

        // Add trade lines
        if (trades != null) {
            BasicStroke tradeStroke = new BasicStroke(2.0f);
            Color winColor = new Color(76, 175, 80, 180);   // Green with transparency
            Color lossColor = new Color(244, 67, 54, 180);  // Red with transparency

            for (Trade t : trades) {
                if (t.exitTime() != null && t.exitPrice() != null) {
                    boolean isWin = t.pnl() != null && t.pnl() > 0;
                    Color color = isWin ? winColor : lossColor;

                    XYLineAnnotation tradeLine = new XYLineAnnotation(
                        t.entryTime(), t.entryPrice(),
                        t.exitTime(), t.exitPrice(),
                        tradeStroke, color
                    );
                    plot.addAnnotation(tradeLine);
                }
            }
        }
    }

    private void updateEquityChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries equitySeries = new TimeSeries("Equity");

        // Simple equity curve: start with initial capital, add P&L from trades
        double equity = initialCapital;

        // Build a map of exit timestamps to P&L
        java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();
        if (trades != null) {
            for (Trade t : trades) {
                if (t.exitTime() != null && t.pnl() != null) {
                    tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                }
            }
        }

        // Add initial point
        if (!candles.isEmpty()) {
            equitySeries.addOrUpdate(
                new Millisecond(new Date(candles.get(0).timestamp())),
                equity
            );
        }

        // Update equity at each trade exit
        for (Candle c : candles) {
            if (tradePnL.containsKey(c.timestamp())) {
                equity += tradePnL.get(c.timestamp());
            }
            equitySeries.addOrUpdate(
                new Millisecond(new Date(c.timestamp())),
                equity
            );
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(equitySeries);
        XYPlot plot = equityChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, new Color(100, 149, 237)); // Cornflower blue
    }

    private void updateComparisonChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries strategySeries = new TimeSeries("Strategy");
        TimeSeries buyHoldSeries = new TimeSeries("Buy & Hold");

        double startPrice = candles.get(0).close();
        double equity = initialCapital;

        // Build a map of exit timestamps to P&L
        java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();
        if (trades != null) {
            for (Trade t : trades) {
                if (t.exitTime() != null && t.pnl() != null) {
                    tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                }
            }
        }

        for (Candle c : candles) {
            // Update strategy equity
            if (tradePnL.containsKey(c.timestamp())) {
                equity += tradePnL.get(c.timestamp());
            }

            // Calculate returns as percentage
            double strategyReturn = ((equity - initialCapital) / initialCapital) * 100;
            double buyHoldReturn = ((c.close() - startPrice) / startPrice) * 100;

            Millisecond time = new Millisecond(new Date(c.timestamp()));
            strategySeries.addOrUpdate(time, strategyReturn);
            buyHoldSeries.addOrUpdate(time, buyHoldReturn);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(strategySeries);
        dataset.addSeries(buyHoldSeries);

        XYPlot plot = comparisonChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, new Color(100, 149, 237)); // Strategy - blue
        plot.getRenderer().setSeriesPaint(1, new Color(255, 193, 7));   // Buy & Hold - amber
    }

    private void updateCapitalUsageChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries usageSeries = new TimeSeries("Capital Usage");

        if (trades == null || trades.isEmpty()) {
            // No trades, show 0% usage
            for (Candle c : candles) {
                usageSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), 0.0);
            }
        } else {
            // Build maps of entry/exit timestamps
            java.util.Map<Long, Double> entryValues = new java.util.HashMap<>();
            java.util.Map<Long, Double> exitValues = new java.util.HashMap<>();

            for (Trade t : trades) {
                double tradeValue = t.entryPrice() * t.quantity();
                entryValues.merge(t.entryTime(), tradeValue, Double::sum);
                if (t.exitTime() != null) {
                    exitValues.merge(t.exitTime(), tradeValue, Double::sum);
                }
            }

            double equity = initialCapital;
            double invested = 0;

            // Build P&L map for equity tracking
            java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();
            for (Trade t : trades) {
                if (t.exitTime() != null && t.pnl() != null) {
                    tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                }
            }

            for (Candle c : candles) {
                // Update equity from closed trades
                if (tradePnL.containsKey(c.timestamp())) {
                    equity += tradePnL.get(c.timestamp());
                }

                // Update invested amount
                if (entryValues.containsKey(c.timestamp())) {
                    invested += entryValues.get(c.timestamp());
                }
                if (exitValues.containsKey(c.timestamp())) {
                    invested -= exitValues.get(c.timestamp());
                }
                invested = Math.max(0, invested);

                // Calculate usage percentage
                double usagePercent = equity > 0 ? (invested / equity) * 100 : 0;
                usageSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), usagePercent);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(usageSeries);
        XYPlot plot = capitalUsageChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, new Color(156, 39, 176)); // Purple
        plot.getRangeAxis().setRange(0, 100);
    }

    private void updateTradePLChart(List<Candle> candles, List<Trade> trades) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();

        if (trades != null && !trades.isEmpty()) {
            // Build a map from timestamp to candle index for quick lookup
            java.util.Map<Long, Integer> timestampToIndex = new java.util.HashMap<>();
            for (int i = 0; i < candles.size(); i++) {
                timestampToIndex.put(candles.get(i).timestamp(), i);
            }

            int tradeNum = 0;
            Color[] colors = {
                new Color(100, 149, 237),  // Cornflower blue
                new Color(255, 193, 7),    // Amber
                new Color(156, 39, 176),   // Purple
                new Color(0, 188, 212),    // Cyan
                new Color(255, 87, 34),    // Deep orange
                new Color(139, 195, 74),   // Light green
                new Color(233, 30, 99),    // Pink
                new Color(63, 81, 181)     // Indigo
            };

            for (Trade t : trades) {
                if (t.exitTime() == null) continue;

                Integer entryIdx = timestampToIndex.get(t.entryTime());
                Integer exitIdx = timestampToIndex.get(t.exitTime());

                if (entryIdx == null || exitIdx == null) continue;

                TimeSeries series = new TimeSeries("Trade " + (tradeNum + 1));
                double entryPrice = t.entryPrice();

                // Add P&L % for each candle from entry to exit
                for (int i = entryIdx; i <= exitIdx && i < candles.size(); i++) {
                    Candle c = candles.get(i);
                    double plPercent = ((c.close() - entryPrice) / entryPrice) * 100;
                    series.addOrUpdate(new Millisecond(new Date(c.timestamp())), plPercent);
                }

                dataset.addSeries(series);
                tradeNum++;
            }

            // Set colors for each series
            XYPlot plot = tradePLChart.getXYPlot();
            plot.setDataset(dataset);
            for (int i = 0; i < Math.min(tradeNum, colors.length); i++) {
                plot.getRenderer().setSeriesPaint(i, colors[i % colors.length]);
            }
        } else {
            tradePLChart.getXYPlot().setDataset(dataset);
        }
    }

    /**
     * Clear charts
     */
    public void clear() {
        priceChart.getXYPlot().setDataset(null);
        equityChart.getXYPlot().setDataset(new TimeSeriesCollection());
        comparisonChart.getXYPlot().setDataset(new TimeSeriesCollection());
        capitalUsageChart.getXYPlot().setDataset(new TimeSeriesCollection());
        tradePLChart.getXYPlot().setDataset(new TimeSeriesCollection());
    }
}
