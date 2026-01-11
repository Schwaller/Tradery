package com.tradery.ui;

import com.tradery.model.Candle;
import com.tradery.model.Trade;
import com.tradery.ui.charts.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Chart panel showing price candlesticks and equity curve.
 * Delegates to specialized managers for overlays, indicators, zoom, and crosshairs.
 */
public class ChartsPanel extends JPanel {

    // Core chart panels
    private org.jfree.chart.ChartPanel priceChartPanel;
    private org.jfree.chart.ChartPanel equityChartPanel;
    private org.jfree.chart.ChartPanel comparisonChartPanel;
    private org.jfree.chart.ChartPanel capitalUsageChartPanel;
    private org.jfree.chart.ChartPanel tradePLChartPanel;
    private org.jfree.chart.ChartPanel volumeChartPanel;

    // Core charts
    private JFreeChart priceChart;
    private JFreeChart equityChart;
    private JFreeChart comparisonChart;
    private JFreeChart capitalUsageChart;
    private JFreeChart tradePLChart;
    private JFreeChart volumeChart;

    // Managers
    private OverlayManager overlayManager;
    private final IndicatorChartsManager indicatorManager;
    private final ChartZoomManager zoomManager;
    private final CrosshairManager crosshairManager;

    // Status callback
    private Consumer<String> onStatusUpdate;

    // Current data
    private List<Candle> currentCandles;
    private List<Trade> currentTrades;

    // Highlight state
    private List<org.jfree.chart.annotations.XYAnnotation> highlightAnnotations = new java.util.ArrayList<>();

    // UI components
    private JPanel chartsContainer;
    private JPanel mainPanel;

    public ChartsPanel() {
        setLayout(new BorderLayout());
        setBorder(null);

        // Initialize non-chart-dependent managers first
        indicatorManager = new IndicatorChartsManager();
        zoomManager = new ChartZoomManager();
        crosshairManager = new CrosshairManager();

        initializeCharts();

        // Initialize overlay manager with price chart
        overlayManager = new OverlayManager(priceChart);

        setupManagers();
        setupScrollableContainer();
    }

    public void setOnStatusUpdate(Consumer<String> callback) {
        this.onStatusUpdate = callback;
        crosshairManager.setOnStatusUpdate(callback);
    }

    private void initializeCharts() {
        // Price chart
        priceChart = ChartFactory.createTimeSeriesChart(
            null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(priceChart, "Price");
        priceChartPanel = createChartPanel(priceChart);

        // Equity chart
        equityChart = ChartFactory.createTimeSeriesChart(
            null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(equityChart, "Equity");
        equityChartPanel = createChartPanel(equityChart);

        // Comparison chart
        comparisonChart = ChartFactory.createTimeSeriesChart(
            null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(comparisonChart, "Strategy vs Buy & Hold");
        comparisonChartPanel = createChartPanel(comparisonChart);

        // Capital usage chart
        capitalUsageChart = ChartFactory.createTimeSeriesChart(
            null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(capitalUsageChart, "Capital Usage");
        capitalUsageChartPanel = createChartPanel(capitalUsageChart);

        // Trade P&L chart
        tradePLChart = ChartFactory.createTimeSeriesChart(
            null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(tradePLChart, "Trade P&L %");
        tradePLChartPanel = createChartPanel(tradePLChart);

        // Volume chart
        volumeChart = ChartFactory.createXYBarChart(
            null, null, true, null, new XYSeriesCollection(),
            org.jfree.chart.plot.PlotOrientation.VERTICAL, false, false, false);
        ChartStyles.stylizeChart(volumeChart, "Volume");
        volumeChartPanel = createChartPanel(volumeChart);
    }

    private org.jfree.chart.ChartPanel createChartPanel(JFreeChart chart) {
        org.jfree.chart.ChartPanel panel = new org.jfree.chart.ChartPanel(chart);
        panel.setMouseWheelEnabled(false);
        panel.setDomainZoomable(false);
        panel.setRangeZoomable(false);
        panel.setMinimumDrawWidth(0);
        panel.setMinimumDrawHeight(0);
        panel.setMaximumDrawWidth(Integer.MAX_VALUE);
        panel.setMaximumDrawHeight(Integer.MAX_VALUE);
        return panel;
    }

    private void setupManagers() {
        // Setup zoom manager
        zoomManager.setIndicatorManager(indicatorManager);
        zoomManager.setOnLayoutChange(this::updateChartLayout);

        org.jfree.chart.ChartPanel[] coreChartPanels = {
            priceChartPanel, volumeChartPanel, equityChartPanel,
            comparisonChartPanel, capitalUsageChartPanel, tradePLChartPanel
        };
        zoomManager.createWrappers(coreChartPanels);

        // Setup indicator manager
        indicatorManager.setOnLayoutChange(this::updateChartLayout);
        indicatorManager.createWrappers(this::toggleIndicatorZoom);

        // Setup crosshairs
        crosshairManager.setupCoreChartCrosshairs(
            priceChartPanel, equityChartPanel, comparisonChartPanel,
            capitalUsageChartPanel, tradePLChartPanel, volumeChartPanel);
        crosshairManager.setupIndicatorChartCrosshairs(
            indicatorManager.getRsiChartPanel(),
            indicatorManager.getMacdChartPanel(),
            indicatorManager.getAtrChartPanel(),
            indicatorManager.getDeltaChartPanel(),
            indicatorManager.getCvdChartPanel(),
            indicatorManager.getVolumeRatioChartPanel(),
            indicatorManager.getWhaleChartPanel(),
            indicatorManager.getRetailChartPanel(),
            indicatorManager.getFundingChartPanel());

        // Sync domain axes
        JFreeChart[] otherCharts = {
            volumeChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart,
            indicatorManager.getRsiChart(), indicatorManager.getMacdChart(), indicatorManager.getAtrChart(),
            indicatorManager.getDeltaChart(), indicatorManager.getCvdChart(), indicatorManager.getVolumeRatioChart(),
            indicatorManager.getWhaleChart(), indicatorManager.getRetailChart(), indicatorManager.getFundingChart()
        };
        crosshairManager.syncDomainAxes(priceChart, otherCharts);
    }

    private void toggleIndicatorZoom(int index) {
        zoomManager.toggleIndicatorZoom(index);
    }

    private void setupScrollableContainer() {
        chartsContainer = new JPanel(new GridBagLayout());
        updateChartLayout();

        // Time scrollbar for fixed-width mode
        JScrollBar timeScrollBar = zoomManager.createTimeScrollBar();
        timeScrollBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateVisibleTimeRange();
            }
        });

        // Mouse wheel listener for scrolling
        java.awt.event.MouseWheelListener wheelListener = e -> {
            JScrollBar scrollBar = zoomManager.getTimeScrollBar();
            if (zoomManager.isFixedWidthMode() && scrollBar.isVisible()) {
                int scrollAmount = e.getWheelRotation() * scrollBar.getUnitIncrement();
                int newValue = scrollBar.getValue() + scrollAmount;
                newValue = Math.max(scrollBar.getMinimum(),
                    Math.min(newValue, scrollBar.getMaximum() - scrollBar.getVisibleAmount()));
                scrollBar.setValue(newValue);
            }
        };

        priceChartPanel.addMouseWheelListener(wheelListener);
        equityChartPanel.addMouseWheelListener(wheelListener);
        comparisonChartPanel.addMouseWheelListener(wheelListener);
        capitalUsageChartPanel.addMouseWheelListener(wheelListener);
        tradePLChartPanel.addMouseWheelListener(wheelListener);
        volumeChartPanel.addMouseWheelListener(wheelListener);
        indicatorManager.addMouseWheelListener(wheelListener);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(chartsContainer, BorderLayout.CENTER);
        mainPanel.add(timeScrollBar, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    private void updateChartLayout() {
        JFreeChart[] allCharts = {
            priceChart, volumeChart,
            indicatorManager.getRsiChart(), indicatorManager.getMacdChart(), indicatorManager.getAtrChart(),
            indicatorManager.getDeltaChart(), indicatorManager.getFundingChart(),
            equityChart, comparisonChart, capitalUsageChart, tradePLChart
        };
        JPanel[] allWrappers = {
            zoomManager.getChartWrappers()[0], zoomManager.getChartWrappers()[1],
            indicatorManager.getRsiChartWrapper(), indicatorManager.getMacdChartWrapper(), indicatorManager.getAtrChartWrapper(),
            indicatorManager.getDeltaChartWrapper(), indicatorManager.getFundingChartWrapper(),
            zoomManager.getChartWrappers()[2], zoomManager.getChartWrappers()[3],
            zoomManager.getChartWrappers()[4], zoomManager.getChartWrappers()[5]
        };

        zoomManager.updateChartLayout(chartsContainer, allCharts, allWrappers);
    }

    // ===== Public API =====

    public void setFixedWidthMode(boolean enabled) {
        zoomManager.setFixedWidthMode(enabled);
        updateFixedWidthMode();
        if (zoomManager.isFitYAxisToVisible()) {
            updateYAxisAutoRange();
        }
    }

    public void setFitYAxisToVisibleData(boolean enabled) {
        zoomManager.setFitYAxisToVisible(enabled);
        updateYAxisAutoRange();
    }

    private void updateFixedWidthMode() {
        if (currentCandles == null || currentCandles.isEmpty()) {
            zoomManager.getTimeScrollBar().setVisible(false);
            return;
        }
        zoomManager.updateFixedWidthMode(
            chartsContainer.getWidth(),
            currentCandles.size(),
            this::updateVisibleTimeRange
        );
        if (!zoomManager.isFixedWidthMode()) {
            resetDomainAxisRange();
        }
    }

    private void updateVisibleTimeRange() {
        if (!zoomManager.isFixedWidthMode() || currentCandles == null || currentCandles.isEmpty()) return;

        JScrollBar scrollBar = zoomManager.getTimeScrollBar();
        int startIndex = scrollBar.getValue();
        int visibleCandles = scrollBar.getVisibleAmount();
        int endIndex = Math.min(startIndex + visibleCandles, currentCandles.size() - 1);
        startIndex = Math.max(0, startIndex);

        if (startIndex >= currentCandles.size() || endIndex < 0) return;

        long startTime = currentCandles.get(startIndex).timestamp();
        long endTime = currentCandles.get(endIndex).timestamp();

        long range = endTime - startTime;
        long padding = range / 50;
        startTime -= padding;
        endTime += padding;

        setDomainAxisRange(startTime, endTime);

        if (zoomManager.isFitYAxisToVisible()) {
            updateYAxisAutoRange();
        }
    }

    private void setDomainAxisRange(long startTime, long endTime) {
        JFreeChart[] charts = {priceChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart};
        for (JFreeChart chart : charts) {
            if (chart == null) continue;
            XYPlot plot = chart.getXYPlot();
            if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
                dateAxis.setAutoRange(false);
                dateAxis.setRange(new Date(startTime), new Date(endTime));
            }
        }
    }

    private void resetDomainAxisRange() {
        if (currentCandles == null || currentCandles.isEmpty()) return;

        long startTime = currentCandles.get(0).timestamp();
        long endTime = currentCandles.get(currentCandles.size() - 1).timestamp();
        JFreeChart[] charts = {
            priceChart, volumeChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart,
            indicatorManager.getRsiChart(), indicatorManager.getMacdChart(), indicatorManager.getAtrChart()
        };

        for (JFreeChart chart : charts) {
            if (chart != null) {
                DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
                axis.setAutoRange(false);
                axis.setRange(startTime, endTime);
            }
        }
    }

    private void updateYAxisAutoRange() {
        boolean fitYAxis = zoomManager.isFitYAxisToVisible();
        JFreeChart[] charts = {priceChart, volumeChart, equityChart, comparisonChart, tradePLChart};

        for (JFreeChart chart : charts) {
            if (chart == null) continue;
            XYPlot plot = chart.getXYPlot();
            ValueAxis rangeAxis = plot.getRangeAxis();

            if (fitYAxis) {
                rangeAxis.setAutoRange(true);
                plot.configureRangeAxes();
            } else {
                rangeAxis.setAutoRange(true);
                DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
                double domainLower = domainAxis.getLowerBound();
                double domainUpper = domainAxis.getUpperBound();
                domainAxis.setAutoRange(true);
                plot.configureRangeAxes();
                double fullLower = rangeAxis.getLowerBound();
                double fullUpper = rangeAxis.getUpperBound();
                domainAxis.setAutoRange(false);
                domainAxis.setRange(domainLower, domainUpper);
                rangeAxis.setAutoRange(false);
                rangeAxis.setRange(fullLower, fullUpper);
            }
        }

        // Capital usage: 0-100% when not fitting visible
        if (capitalUsageChart != null) {
            ValueAxis capitalAxis = capitalUsageChart.getXYPlot().getRangeAxis();
            if (fitYAxis) {
                capitalAxis.setAutoRange(true);
                capitalUsageChart.getXYPlot().configureRangeAxes();
            } else {
                capitalAxis.setAutoRange(false);
                capitalAxis.setRange(-5, 105);
            }
        }

        indicatorManager.updateYAxisAutoRange(fitYAxis);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (zoomManager.isFixedWidthMode()) {
            SwingUtilities.invokeLater(this::updateFixedWidthMode);
        }
    }

    // ===== Overlay Delegation =====

    public void setSmaOverlay(int period, List<Candle> candles) {
        overlayManager.setSmaOverlay(period, candles);
    }

    public void clearSmaOverlay() {
        overlayManager.clearSmaOverlay();
    }

    public boolean isSmaEnabled() {
        return overlayManager.isSmaEnabled();
    }

    public void setEmaOverlay(int period, List<Candle> candles) {
        overlayManager.setEmaOverlay(period, candles);
    }

    public void clearEmaOverlay() {
        overlayManager.clearEmaOverlay();
    }

    public boolean isEmaEnabled() {
        return overlayManager.isEmaEnabled();
    }

    public void setBollingerOverlay(int period, double stdDevMultiplier, List<Candle> candles) {
        overlayManager.setBollingerOverlay(period, stdDevMultiplier, candles);
    }

    public void clearBollingerOverlay() {
        overlayManager.clearBollingerOverlay();
    }

    public boolean isBollingerEnabled() {
        return overlayManager.isBollingerEnabled();
    }

    public void setHighLowOverlay(int period, List<Candle> candles) {
        overlayManager.setHighLowOverlay(period, candles);
    }

    public void clearHighLowOverlay() {
        overlayManager.clearHighLowOverlay();
    }

    public boolean isHighLowEnabled() {
        return overlayManager.isHighLowEnabled();
    }

    public void setMayerMultipleEnabled(boolean enabled, int period) {
        overlayManager.setMayerMultipleEnabled(enabled, period);
    }

    public boolean isMayerMultipleEnabled() {
        return overlayManager.isMayerMultipleEnabled();
    }

    // ===== Indicator Chart Delegation =====

    public void setRsiChartEnabled(boolean enabled, int period) {
        indicatorManager.setRsiChartEnabled(enabled, period);
    }

    public boolean isRsiChartEnabled() {
        return indicatorManager.isRsiChartEnabled();
    }

    public void setMacdChartEnabled(boolean enabled, int fast, int slow, int signal) {
        indicatorManager.setMacdChartEnabled(enabled, fast, slow, signal);
    }

    public boolean isMacdChartEnabled() {
        return indicatorManager.isMacdChartEnabled();
    }

    public void setAtrChartEnabled(boolean enabled, int period) {
        indicatorManager.setAtrChartEnabled(enabled, period);
    }

    public boolean isAtrChartEnabled() {
        return indicatorManager.isAtrChartEnabled();
    }

    public void setDeltaChartEnabled(boolean enabled, double threshold) {
        indicatorManager.setDeltaChartEnabled(enabled, threshold);
    }

    public boolean isDeltaChartEnabled() {
        return indicatorManager.isDeltaChartEnabled();
    }

    public void setCvdChartEnabled(boolean enabled) {
        indicatorManager.setCvdChartEnabled(enabled);
    }

    public boolean isCvdChartEnabled() {
        return indicatorManager.isCvdChartEnabled();
    }

    public void setVolumeRatioChartEnabled(boolean enabled) {
        indicatorManager.setVolumeRatioChartEnabled(enabled);
    }

    public boolean isVolumeRatioChartEnabled() {
        return indicatorManager.isVolumeRatioChartEnabled();
    }

    public void setWhaleChartEnabled(boolean enabled, double threshold) {
        indicatorManager.setWhaleChartEnabled(enabled, threshold);
    }

    public boolean isWhaleChartEnabled() {
        return indicatorManager.isWhaleChartEnabled();
    }

    public void setWhaleThreshold(double threshold) {
        indicatorManager.setWhaleThreshold(threshold);
    }

    public double getWhaleThreshold() {
        return indicatorManager.getWhaleThreshold();
    }

    public void setRetailChartEnabled(boolean enabled, double threshold) {
        indicatorManager.setRetailChartEnabled(enabled, threshold);
    }

    public boolean isRetailChartEnabled() {
        return indicatorManager.isRetailChartEnabled();
    }

    public void setFundingChartEnabled(boolean enabled) {
        indicatorManager.setFundingChartEnabled(enabled);
    }

    public boolean isFundingChartEnabled() {
        return indicatorManager.isFundingChartEnabled();
    }

    public void setIndicatorEngine(com.tradery.indicators.IndicatorEngine engine) {
        indicatorManager.setIndicatorEngine(engine);
    }

    // ===== Core Chart Toggles =====

    public void setVolumeChartEnabled(boolean enabled) {
        zoomManager.setVolumeChartEnabled(enabled);
    }

    public boolean isVolumeChartEnabled() {
        return zoomManager.isVolumeChartEnabled();
    }

    public void setEquityChartEnabled(boolean enabled) {
        zoomManager.setEquityChartEnabled(enabled);
    }

    public boolean isEquityChartEnabled() {
        return zoomManager.isEquityChartEnabled();
    }

    public void setComparisonChartEnabled(boolean enabled) {
        zoomManager.setComparisonChartEnabled(enabled);
    }

    public boolean isComparisonChartEnabled() {
        return zoomManager.isComparisonChartEnabled();
    }

    public void setCapitalUsageChartEnabled(boolean enabled) {
        zoomManager.setCapitalUsageChartEnabled(enabled);
    }

    public boolean isCapitalUsageChartEnabled() {
        return zoomManager.isCapitalUsageChartEnabled();
    }

    public void setTradePLChartEnabled(boolean enabled) {
        zoomManager.setTradePLChartEnabled(enabled);
    }

    public boolean isTradePLChartEnabled() {
        return zoomManager.isTradePLChartEnabled();
    }

    /**
     * Refresh all chart styles when theme changes.
     */
    public void refreshTheme() {
        // Re-stylize all core charts
        ChartStyles.stylizeChart(priceChart, "Price");
        ChartStyles.stylizeChart(volumeChart, "Volume");
        ChartStyles.stylizeChart(equityChart, "Equity");
        ChartStyles.stylizeChart(comparisonChart, "Strategy vs Buy & Hold");
        ChartStyles.stylizeChart(capitalUsageChart, "Capital Usage");
        ChartStyles.stylizeChart(tradePLChart, "Trade P&L");

        // Re-stylize indicator charts
        ChartStyles.stylizeChart(indicatorManager.getRsiChart(), "RSI");
        ChartStyles.stylizeChart(indicatorManager.getMacdChart(), "MACD");
        ChartStyles.stylizeChart(indicatorManager.getAtrChart(), "ATR");
        ChartStyles.stylizeChart(indicatorManager.getDeltaChart(), "Delta");
        ChartStyles.stylizeChart(indicatorManager.getCvdChart(), "CVD");
        ChartStyles.stylizeChart(indicatorManager.getVolumeRatioChart(), "Buy/Sell Volume");
        ChartStyles.stylizeChart(indicatorManager.getWhaleChart(), "Whale Delta");
        ChartStyles.stylizeChart(indicatorManager.getRetailChart(), "Retail Delta");
        ChartStyles.stylizeChart(indicatorManager.getFundingChart(), "Funding");

        // Update container background
        chartsContainer.setBackground(ChartStyles.BACKGROUND_COLOR());

        // Force repaint
        repaint();
    }

    // ===== Chart Update Methods =====

    public void updateCharts(List<Candle> candles, List<Trade> trades, double initialCapital) {
        if (candles == null || candles.isEmpty()) return;

        this.currentCandles = candles;
        this.currentTrades = trades;
        crosshairManager.setCurrentCandles(candles);
        clearTradeHighlight();

        updateDateAxisFormat(candles);

        updatePriceChart(candles, trades);
        updateEquityChart(candles, trades, initialCapital);
        updateComparisonChart(candles, trades, initialCapital);
        updateCapitalUsageChart(candles, trades, initialCapital);
        updateTradePLChart(candles, trades);
        updateVolumeChart(candles);

        indicatorManager.updateCharts(candles);

        // Set consistent domain axis range
        long startTime = candles.get(0).timestamp();
        long endTime = candles.get(candles.size() - 1).timestamp();
        JFreeChart[] allCharts = {
            priceChart, volumeChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart,
            indicatorManager.getRsiChart(), indicatorManager.getMacdChart(), indicatorManager.getAtrChart(),
            indicatorManager.getDeltaChart(), indicatorManager.getFundingChart()
        };
        for (JFreeChart chart : allCharts) {
            if (chart != null) {
                DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
                axis.setAutoRange(false);
                axis.setRange(startTime, endTime);
            }
        }

        if (zoomManager.isFixedWidthMode()) {
            updateFixedWidthMode();
        }
    }

    private void updateDateAxisFormat(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        long firstTimestamp = candles.getFirst().timestamp();
        int currentYear = Year.now().getValue();
        int dataStartYear = Year.from(java.time.Instant.ofEpochMilli(firstTimestamp)
            .atZone(java.time.ZoneId.systemDefault())).getValue();

        SimpleDateFormat format = (dataStartYear < currentYear)
            ? new SimpleDateFormat("MMM d ''yy")
            : new SimpleDateFormat("MMM d");

        JFreeChart[] charts = {priceChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart};
        for (JFreeChart chart : charts) {
            if (chart != null && chart.getXYPlot().getDomainAxis() instanceof DateAxis dateAxis) {
                dateAxis.setDateFormatOverride(format);
            }
        }
    }

    private void updatePriceChart(List<Candle> candles, List<Trade> trades) {
        XYPlot plot = priceChart.getXYPlot();

        // Clear non-title annotations
        plot.getAnnotations().stream()
            .filter(a -> !(a instanceof XYTitleAnnotation))
            .toList()
            .forEach(plot::removeAnnotation);

        // Price line
        TimeSeries priceSeries = new TimeSeries("Price");
        for (Candle c : candles) {
            priceSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), c.close());
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(priceSeries);
        plot.setDataset(dataset);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.PRICE_LINE_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.LINE_STROKE);
        plot.setRenderer(renderer);

        // Add Mayer Multiple annotations if enabled
        overlayManager.addMayerMultipleAnnotations(plot, candles);

        // Add trade lines
        addTradeAnnotations(plot, trades);
    }

    private void addTradeAnnotations(XYPlot plot, List<Trade> trades) {
        if (trades == null) return;

        java.util.List<Trade> validTrades = trades.stream()
            .filter(t -> t.exitTime() != null && t.exitPrice() != null && !"rejected".equals(t.exitReason()))
            .sorted((a, b) -> Long.compare(a.entryTime(), b.entryTime()))
            .toList();

        java.util.Map<String, java.util.List<Trade>> tradesByGroup = new java.util.LinkedHashMap<>();
        for (Trade t : validTrades) {
            String groupId = t.groupId() != null ? t.groupId() : "single-" + t.id();
            tradesByGroup.computeIfAbsent(groupId, k -> new java.util.ArrayList<>()).add(t);
        }

        for (java.util.List<Trade> group : tradesByGroup.values()) {
            if (group.size() == 1) {
                Trade t = group.get(0);
                boolean isWin = t.pnl() != null && t.pnl() > 0;
                Color color = isWin ? ChartStyles.WIN_COLOR : ChartStyles.LOSS_COLOR;

                XYLineAnnotation tradeLine = new XYLineAnnotation(
                    t.entryTime(), t.entryPrice(),
                    t.exitTime(), t.exitPrice(),
                    ChartStyles.TRADE_LINE_STROKE, color);
                plot.addAnnotation(tradeLine);
            } else {
                // DCA position
                double totalValue = 0, totalQuantity = 0, totalPnl = 0;
                long firstEntryTime = Long.MAX_VALUE, lastEntryTime = Long.MIN_VALUE;

                for (Trade t : group) {
                    totalValue += t.entryPrice() * t.quantity();
                    totalQuantity += t.quantity();
                    if (t.pnl() != null) totalPnl += t.pnl();
                    firstEntryTime = Math.min(firstEntryTime, t.entryTime());
                    lastEntryTime = Math.max(lastEntryTime, t.entryTime());
                }

                double avgEntryPrice = totalValue / totalQuantity;
                boolean isWin = totalPnl > 0;
                Color color = isWin ? ChartStyles.WIN_COLOR : ChartStyles.LOSS_COLOR;
                Trade lastTrade = group.get(0);

                Color verticalColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 89);
                for (Trade t : group) {
                    XYLineAnnotation verticalLine = new XYLineAnnotation(
                        t.entryTime(), t.entryPrice(),
                        t.entryTime(), avgEntryPrice,
                        ChartStyles.THIN_STROKE, verticalColor);
                    plot.addAnnotation(verticalLine);
                }

                XYLineAnnotation avgLine = new XYLineAnnotation(
                    firstEntryTime, avgEntryPrice,
                    lastEntryTime, avgEntryPrice,
                    ChartStyles.TRADE_LINE_STROKE, color);
                plot.addAnnotation(avgLine);

                long centerTime = (firstEntryTime + lastEntryTime) / 2;
                XYLineAnnotation exitLine = new XYLineAnnotation(
                    centerTime, avgEntryPrice,
                    lastTrade.exitTime(), lastTrade.exitPrice(),
                    ChartStyles.TRADE_LINE_STROKE, color);
                plot.addAnnotation(exitLine);

                // Dots at endpoints
                final long cTime = centerTime;
                final double avgPrice = avgEntryPrice;
                final long exitTime = lastTrade.exitTime();
                final double exitPrice = lastTrade.exitPrice();
                final Color dotColor = color;
                final double dotSize = 6.0;

                plot.addAnnotation(new org.jfree.chart.annotations.AbstractXYAnnotation() {
                    @Override
                    public void draw(java.awt.Graphics2D g2, XYPlot plot, java.awt.geom.Rectangle2D dataArea,
                            ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                            org.jfree.chart.plot.PlotRenderingInfo info) {
                        double x1 = domainAxis.valueToJava2D(cTime, dataArea, plot.getDomainAxisEdge());
                        double y1 = rangeAxis.valueToJava2D(avgPrice, dataArea, plot.getRangeAxisEdge());
                        double x2 = domainAxis.valueToJava2D(exitTime, dataArea, plot.getDomainAxisEdge());
                        double y2 = rangeAxis.valueToJava2D(exitPrice, dataArea, plot.getRangeAxisEdge());
                        g2.setColor(dotColor);
                        g2.fill(new Ellipse2D.Double(x1 - dotSize/2, y1 - dotSize/2, dotSize, dotSize));
                        g2.fill(new Ellipse2D.Double(x2 - dotSize/2, y2 - dotSize/2, dotSize, dotSize));
                    }
                });
            }
        }
    }

    /**
     * Highlight specific trades on the chart (draw highlight overlay without moving view).
     */
    public void highlightTrades(List<Trade> trades) {
        clearTradeHighlight();

        if (trades == null || trades.isEmpty()) return;

        XYPlot plot = priceChart.getXYPlot();

        // Draw highlight for each trade (no zoom - just overlay)
        BasicStroke highlightStroke = new BasicStroke(4.0f);

        for (Trade t : trades) {
            long entryTime = t.entryTime();
            long exitTime = t.exitTime() != null ? t.exitTime() : entryTime;
            double entryPrice = t.entryPrice();
            double exitPrice = t.exitPrice() != null ? t.exitPrice() : entryPrice;

            // Draw highlight line (thicker, brighter)
            XYLineAnnotation highlight = new XYLineAnnotation(
                entryTime, entryPrice,
                exitTime, exitPrice,
                highlightStroke, new Color(255, 215, 0)); // Gold
            plot.addAnnotation(highlight);
            highlightAnnotations.add(highlight);

            // Draw entry/exit markers
            double markerSize = 10.0;
            org.jfree.chart.annotations.AbstractXYAnnotation entryMarker =
                new org.jfree.chart.annotations.AbstractXYAnnotation() {
                    @Override
                    public void draw(java.awt.Graphics2D g2, XYPlot plot, java.awt.geom.Rectangle2D dataArea,
                            ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                            org.jfree.chart.plot.PlotRenderingInfo info) {
                        double x = domainAxis.valueToJava2D(entryTime, dataArea, plot.getDomainAxisEdge());
                        double y = rangeAxis.valueToJava2D(entryPrice, dataArea, plot.getRangeAxisEdge());
                        g2.setColor(new Color(255, 215, 0));
                        g2.setStroke(new BasicStroke(2.0f));
                        g2.draw(new Ellipse2D.Double(x - markerSize/2, y - markerSize/2, markerSize, markerSize));
                        g2.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
                    }
                };
            plot.addAnnotation(entryMarker);
            highlightAnnotations.add(entryMarker);

            if (t.exitTime() != null && t.exitPrice() != null) {
                org.jfree.chart.annotations.AbstractXYAnnotation exitMarker =
                    new org.jfree.chart.annotations.AbstractXYAnnotation() {
                        @Override
                        public void draw(java.awt.Graphics2D g2, XYPlot plot, java.awt.geom.Rectangle2D dataArea,
                                ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                                org.jfree.chart.plot.PlotRenderingInfo info) {
                            double x = domainAxis.valueToJava2D(exitTime, dataArea, plot.getDomainAxisEdge());
                            double y = rangeAxis.valueToJava2D(exitPrice, dataArea, plot.getRangeAxisEdge());
                            g2.setColor(new Color(255, 215, 0));
                            g2.setStroke(new BasicStroke(2.0f));
                            g2.draw(new Ellipse2D.Double(x - markerSize/2, y - markerSize/2, markerSize, markerSize));
                            g2.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
                        }
                    };
                plot.addAnnotation(exitMarker);
                highlightAnnotations.add(exitMarker);
            }
        }
    }

    /**
     * Clear any trade highlight annotations.
     */
    public void clearTradeHighlight() {
        if (highlightAnnotations.isEmpty()) return;

        XYPlot plot = priceChart.getXYPlot();
        for (org.jfree.chart.annotations.XYAnnotation ann : highlightAnnotations) {
            plot.removeAnnotation(ann);
        }
        highlightAnnotations.clear();
    }

    private void updateEquityChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries equitySeries = new TimeSeries("Equity");
        double equity = initialCapital;

        java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();
        if (trades != null) {
            for (Trade t : trades) {
                if (t.exitTime() != null && t.pnl() != null) {
                    tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                }
            }
        }

        if (!candles.isEmpty()) {
            equitySeries.addOrUpdate(new Millisecond(new Date(candles.get(0).timestamp())), equity);
        }

        for (Candle c : candles) {
            if (tradePnL.containsKey(c.timestamp())) {
                equity += tradePnL.get(c.timestamp());
            }
            equitySeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), equity);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(equitySeries);
        XYPlot plot = equityChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, ChartStyles.EQUITY_COLOR);
    }

    private void updateComparisonChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries strategySeries = new TimeSeries("Strategy");
        TimeSeries buyHoldSeries = new TimeSeries("Buy & Hold");

        double startPrice = candles.get(0).close();
        double equity = initialCapital;

        java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();
        if (trades != null) {
            for (Trade t : trades) {
                if (t.exitTime() != null && t.pnl() != null) {
                    tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                }
            }
        }

        for (Candle c : candles) {
            if (tradePnL.containsKey(c.timestamp())) {
                equity += tradePnL.get(c.timestamp());
            }
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
        plot.getRenderer().setSeriesPaint(0, ChartStyles.EQUITY_COLOR);
        plot.getRenderer().setSeriesPaint(1, ChartStyles.BUY_HOLD_COLOR);
    }

    private void updateCapitalUsageChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries usageSeries = new TimeSeries("Capital Usage");

        List<Trade> validTrades = trades == null ? List.of() : trades.stream()
            .filter(t -> t.quantity() > 0).toList();

        if (validTrades.isEmpty()) {
            for (Candle c : candles) {
                usageSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), 0.0);
            }
        } else {
            java.util.Map<Long, Double> entryValues = new java.util.HashMap<>();
            java.util.Map<Long, Double> exitValues = new java.util.HashMap<>();
            java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();

            for (Trade t : validTrades) {
                double tradeValue = t.entryPrice() * t.quantity();
                entryValues.merge(t.entryTime(), tradeValue, Double::sum);
                if (t.exitTime() != null) {
                    exitValues.merge(t.exitTime(), tradeValue, Double::sum);
                    if (t.pnl() != null) {
                        tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                    }
                }
            }

            double equity = initialCapital;
            double invested = 0;

            for (Candle c : candles) {
                if (tradePnL.containsKey(c.timestamp())) {
                    equity += tradePnL.get(c.timestamp());
                }
                if (entryValues.containsKey(c.timestamp())) {
                    invested += entryValues.get(c.timestamp());
                }
                if (exitValues.containsKey(c.timestamp())) {
                    invested -= exitValues.get(c.timestamp());
                }
                invested = Math.max(0, invested);

                double usagePercent = equity > 0 ? Math.min((invested / equity) * 100, 100.0) : 0;
                usageSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), usagePercent);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(usageSeries);
        XYPlot plot = capitalUsageChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, ChartStyles.CAPITAL_USAGE_COLOR);
        plot.getRangeAxis().setRange(-5, 105);
    }

    private void updateTradePLChart(List<Candle> candles, List<Trade> trades) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();

        if (trades != null && !trades.isEmpty()) {
            java.util.Map<Long, Integer> timestampToIndex = new java.util.HashMap<>();
            for (int i = 0; i < candles.size(); i++) {
                timestampToIndex.put(candles.get(i).timestamp(), i);
            }

            int tradeNum = 0;
            for (Trade t : trades) {
                if (t.exitTime() == null) continue;

                Integer entryIdx = timestampToIndex.get(t.entryTime());
                Integer exitIdx = timestampToIndex.get(t.exitTime());
                if (entryIdx == null || exitIdx == null) continue;

                TimeSeries series = new TimeSeries("Trade " + (tradeNum + 1));
                double entryPrice = t.entryPrice();

                for (int i = entryIdx; i <= exitIdx && i < candles.size(); i++) {
                    Candle c = candles.get(i);
                    double plPercent = ((c.close() - entryPrice) / entryPrice) * 100;
                    series.addOrUpdate(new Millisecond(new Date(c.timestamp())), plPercent);
                }

                dataset.addSeries(series);
                tradeNum++;
            }

            XYPlot plot = tradePLChart.getXYPlot();
            plot.setDataset(dataset);
            for (int i = 0; i < tradeNum; i++) {
                plot.getRenderer().setSeriesPaint(i, ChartStyles.RAINBOW_COLORS[i % ChartStyles.RAINBOW_COLORS.length]);
            }
        } else {
            tradePLChart.getXYPlot().setDataset(dataset);
        }
    }

    private void updateVolumeChart(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        int lookback = 20;
        double[] avgVolumes = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - lookback + 1); j <= i; j++) {
                sum += candles.get(j).volume();
                count++;
            }
            avgVolumes[i] = sum / count;
        }

        XYSeries[] volumeSeries = new XYSeries[7];
        String[] seriesNames = {"Ultra Low", "Very Low", "Low", "Average", "High", "Very High", "Ultra High"};
        for (int i = 0; i < 7; i++) {
            volumeSeries[i] = new XYSeries(seriesNames[i]);
        }

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double relVol = c.volume() / avgVolumes[i];

            int seriesIdx;
            if (relVol >= 2.2) seriesIdx = 6;
            else if (relVol >= 1.8) seriesIdx = 5;
            else if (relVol >= 1.2) seriesIdx = 4;
            else if (relVol >= 0.8) seriesIdx = 3;
            else if (relVol >= 0.5) seriesIdx = 2;
            else if (relVol >= 0.3) seriesIdx = 1;
            else seriesIdx = 0;

            volumeSeries[seriesIdx].add(c.timestamp(), c.volume());
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        for (XYSeries series : volumeSeries) {
            dataset.addSeries(series);
        }

        XYPlot plot = volumeChart.getXYPlot();
        plot.setDataset(dataset);

        XYBarRenderer renderer = new XYBarRenderer(0.0);
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        for (int i = 0; i < ChartStyles.VOLUME_COLORS.length; i++) {
            renderer.setSeriesPaint(i, ChartStyles.VOLUME_COLORS[i]);
        }
        plot.setRenderer(renderer);
    }

    public void clear() {
        priceChart.getXYPlot().setDataset(null);
        equityChart.getXYPlot().setDataset(new TimeSeriesCollection());
        comparisonChart.getXYPlot().setDataset(new TimeSeriesCollection());
        capitalUsageChart.getXYPlot().setDataset(new TimeSeriesCollection());
        tradePLChart.getXYPlot().setDataset(new TimeSeriesCollection());
        volumeChart.getXYPlot().setDataset(new XYSeriesCollection());
        overlayManager.clearAll();
    }
}
