package com.tradery.ui;

import com.tradery.model.Candle;
import com.tradery.model.Trade;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.panel.CrosshairOverlay;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
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
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.BasicStroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Chart panel showing price candlesticks and equity curve.
 * Uses JFreeChart for rendering.
 */
public class ChartsPanel extends JPanel {

    // Color constants
    private static final Color BACKGROUND_COLOR = new Color(30, 30, 35);
    private static final Color PLOT_BACKGROUND_COLOR = new Color(20, 20, 25);
    private static final Color GRIDLINE_COLOR = new Color(60, 60, 65);
    private static final Color TEXT_COLOR = new Color(150, 150, 150);
    private static final Color CROSSHAIR_COLOR = new Color(150, 150, 150, 180);
    private static final Color PRICE_LINE_COLOR = new Color(255, 255, 255, 180);
    private static final Color WIN_COLOR = new Color(76, 175, 80, 180);
    private static final Color LOSS_COLOR = new Color(244, 67, 54, 180);
    private static final Color EQUITY_COLOR = new Color(77, 77, 255);
    private static final Color BUY_HOLD_COLOR = new Color(255, 193, 7);
    private static final Color CAPITAL_USAGE_COLOR = new Color(57, 255, 20);
    private static final Color SMA_COLOR = new Color(255, 193, 7, 200);
    private static final Color EMA_COLOR = new Color(0, 200, 255, 200);
    private static final Color BB_COLOR = new Color(180, 100, 255, 180);
    private static final Color BB_MIDDLE_COLOR = new Color(180, 100, 255, 120);

    // Mayer Multiple zone colors (rainbow spectrum)
    private static final Color MAYER_DEEP_UNDERVALUED = new Color(0, 200, 255);    // Cyan - deeply undervalued
    private static final Color MAYER_UNDERVALUED = new Color(0, 255, 100);         // Green - undervalued
    private static final Color MAYER_NEUTRAL = new Color(255, 255, 0);             // Yellow - fair value
    private static final Color MAYER_OVERVALUED = new Color(255, 140, 0);          // Orange - overvalued
    private static final Color MAYER_DEEP_OVERVALUED = new Color(255, 0, 100);     // Magenta - deeply overvalued

    // RSI chart colors
    private static final Color RSI_COLOR = new Color(255, 193, 7);
    private static final Color RSI_OVERBOUGHT = new Color(255, 80, 80, 50);
    private static final Color RSI_OVERSOLD = new Color(80, 255, 80, 50);

    // MACD chart colors
    private static final Color MACD_LINE_COLOR = new Color(0, 150, 255);
    private static final Color MACD_SIGNAL_COLOR = new Color(255, 140, 0);
    private static final Color MACD_HIST_POS = new Color(76, 175, 80);
    private static final Color MACD_HIST_NEG = new Color(244, 67, 54);

    // ATR chart color
    private static final Color ATR_COLOR = new Color(180, 100, 255);

    // Volume colors (Wyckoff-style: cool to warm)
    private static final Color[] VOLUME_COLORS = {
        new Color(100, 100, 100),  // Ultra Low - grey
        new Color(0, 100, 255),    // Very Low - blue
        new Color(0, 200, 200),    // Low - cyan
        new Color(100, 200, 100),  // Average - green
        new Color(255, 180, 0),    // High - orange
        new Color(255, 80, 80),    // Very High - red
        new Color(255, 0, 200)     // Ultra High - magenta
    };

    private org.jfree.chart.ChartPanel priceChartPanel;
    private org.jfree.chart.ChartPanel equityChartPanel;
    private org.jfree.chart.ChartPanel comparisonChartPanel;
    private org.jfree.chart.ChartPanel capitalUsageChartPanel;
    private org.jfree.chart.ChartPanel tradePLChartPanel;
    private org.jfree.chart.ChartPanel volumeChartPanel;

    private JFreeChart priceChart;
    private JFreeChart equityChart;
    private JFreeChart comparisonChart;
    private JFreeChart capitalUsageChart;
    private JFreeChart tradePLChart;
    private JFreeChart volumeChart;

    private Crosshair priceCrosshair;
    private Crosshair equityCrosshair;
    private Crosshair comparisonCrosshair;
    private Crosshair capitalUsageCrosshair;
    private Crosshair tradePLCrosshair;
    private Crosshair volumeCrosshair;

    private Consumer<String> onStatusUpdate;
    private List<Candle> currentCandles;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm");

    // Fixed width mode support
    private JPanel chartsContainer;
    private boolean fixedWidthMode = false;
    private boolean fitYAxisToVisible = true;
    private static final int PIXELS_PER_CANDLE = 4;
    private JScrollBar timeScrollBar;
    private JPanel mainPanel;

    // Zoom support
    private int zoomedChartIndex = -1; // -1 = none zoomed
    private JPanel[] chartWrappers;
    private JButton[] zoomButtons;

    // Indicator overlays
    private TimeSeries smaSeries;
    private int smaDatasetIndex = -1;
    private TimeSeries emaSeries;
    private int emaDatasetIndex = -1;
    private int bbDatasetIndex = -1;
    private int hlDatasetIndex = -1;

    // Mayer Multiple mode
    private boolean mayerMultipleEnabled = false;
    private int mayerPeriod = 200;

    // Indicator chart panels (optional, toggled)
    private org.jfree.chart.ChartPanel rsiChartPanel;
    private org.jfree.chart.ChartPanel macdChartPanel;
    private org.jfree.chart.ChartPanel atrChartPanel;
    private JFreeChart rsiChart;
    private JFreeChart macdChart;
    private JFreeChart atrChart;
    private Crosshair rsiCrosshair;
    private Crosshair macdCrosshair;
    private Crosshair atrCrosshair;
    private JPanel rsiChartWrapper;
    private JPanel macdChartWrapper;
    private JPanel atrChartWrapper;
    private JButton rsiZoomBtn;
    private JButton macdZoomBtn;
    private JButton atrZoomBtn;

    // Zoom state for indicator charts (-1 = none, 0=RSI, 1=MACD, 2=ATR)
    private int indicatorZoomedIndex = -1;

    // Indicator chart toggle state
    private boolean rsiChartEnabled = false;
    private boolean macdChartEnabled = false;
    private boolean atrChartEnabled = false;
    private int rsiPeriod = 14;
    private int macdFast = 12;
    private int macdSlow = 26;
    private int macdSignal = 9;
    private int atrPeriod = 14;

    public ChartsPanel() {
        setLayout(new BorderLayout());
        setBorder(null);

        initializeCharts();
        setupCrosshairs();
        setupScrollableContainer();
        syncDomainAxes();
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
            false,  // no JFreeChart legend - using custom
            true,
            false
        );
        stylizeChart(priceChart, "Price");

        priceChartPanel = new org.jfree.chart.ChartPanel(priceChart);
        priceChartPanel.setMouseWheelEnabled(false);
        priceChartPanel.setDomainZoomable(false);
        priceChartPanel.setRangeZoomable(false);
        priceChartPanel.setMinimumDrawWidth(0);
        priceChartPanel.setMinimumDrawHeight(0);
        priceChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        priceChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

        // Equity chart (placeholder)
        equityChart = ChartFactory.createTimeSeriesChart(
            null,
            null,
            null,
            new TimeSeriesCollection(),
            false,  // no JFreeChart legend - using custom
            true,
            false
        );
        stylizeChart(equityChart, "Equity");

        equityChartPanel = new org.jfree.chart.ChartPanel(equityChart);
        equityChartPanel.setMouseWheelEnabled(false);
        equityChartPanel.setDomainZoomable(false);
        equityChartPanel.setRangeZoomable(false);
        equityChartPanel.setMinimumDrawWidth(0);
        equityChartPanel.setMinimumDrawHeight(0);
        equityChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        equityChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

        // Comparison chart (Strategy vs Buy & Hold)
        comparisonChart = ChartFactory.createTimeSeriesChart(
            null,
            null,
            null,
            new TimeSeriesCollection(),
            false,  // no JFreeChart legend - using custom
            true,
            false
        );
        stylizeChart(comparisonChart, "Strategy vs Buy & Hold");

        comparisonChartPanel = new org.jfree.chart.ChartPanel(comparisonChart);
        comparisonChartPanel.setMouseWheelEnabled(false);
        comparisonChartPanel.setDomainZoomable(false);
        comparisonChartPanel.setRangeZoomable(false);
        comparisonChartPanel.setMinimumDrawWidth(0);
        comparisonChartPanel.setMinimumDrawHeight(0);
        comparisonChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        comparisonChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

        // Capital usage chart
        capitalUsageChart = ChartFactory.createTimeSeriesChart(
            null,
            null,
            null,
            new TimeSeriesCollection(),
            false,  // no JFreeChart legend - using custom
            true,
            false
        );
        stylizeChart(capitalUsageChart, "Capital Usage");

        capitalUsageChartPanel = new org.jfree.chart.ChartPanel(capitalUsageChart);
        capitalUsageChartPanel.setMouseWheelEnabled(false);
        capitalUsageChartPanel.setDomainZoomable(false);
        capitalUsageChartPanel.setRangeZoomable(false);
        capitalUsageChartPanel.setMinimumDrawWidth(0);
        capitalUsageChartPanel.setMinimumDrawHeight(0);
        capitalUsageChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        capitalUsageChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

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
        tradePLChartPanel.setMouseWheelEnabled(false);
        tradePLChartPanel.setDomainZoomable(false);
        tradePLChartPanel.setRangeZoomable(false);
        tradePLChartPanel.setMinimumDrawWidth(0);
        tradePLChartPanel.setMinimumDrawHeight(0);
        tradePLChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        tradePLChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

        // Volume chart with colored bars
        volumeChart = ChartFactory.createXYBarChart(
            null,
            null,
            true,  // dateAxis
            null,
            new XYSeriesCollection(),
            org.jfree.chart.plot.PlotOrientation.VERTICAL,
            false,  // no JFreeChart legend - using custom
            false, // tooltips
            false  // urls
        );
        stylizeChart(volumeChart, "Volume");

        volumeChartPanel = new org.jfree.chart.ChartPanel(volumeChart);
        volumeChartPanel.setMouseWheelEnabled(false);
        volumeChartPanel.setDomainZoomable(false);
        volumeChartPanel.setRangeZoomable(false);
        volumeChartPanel.setMinimumDrawWidth(0);
        volumeChartPanel.setMinimumDrawHeight(0);
        volumeChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        volumeChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

        // RSI chart (optional, toggled)
        rsiChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        stylizeChart(rsiChart, "RSI");
        // Set fixed Y range 0-100 for RSI
        rsiChart.getXYPlot().getRangeAxis().setRange(0, 100);

        rsiChartPanel = new org.jfree.chart.ChartPanel(rsiChart);
        rsiChartPanel.setMouseWheelEnabled(false);
        rsiChartPanel.setDomainZoomable(false);
        rsiChartPanel.setRangeZoomable(false);
        rsiChartPanel.setMinimumDrawWidth(0);
        rsiChartPanel.setMinimumDrawHeight(0);
        rsiChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        rsiChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

        // MACD chart (optional, toggled)
        macdChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        stylizeChart(macdChart, "MACD");

        macdChartPanel = new org.jfree.chart.ChartPanel(macdChart);
        macdChartPanel.setMouseWheelEnabled(false);
        macdChartPanel.setDomainZoomable(false);
        macdChartPanel.setRangeZoomable(false);
        macdChartPanel.setMinimumDrawWidth(0);
        macdChartPanel.setMinimumDrawHeight(0);
        macdChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        macdChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

        // ATR chart (optional, toggled)
        atrChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        stylizeChart(atrChart, "ATR");

        atrChartPanel = new org.jfree.chart.ChartPanel(atrChart);
        atrChartPanel.setMouseWheelEnabled(false);
        atrChartPanel.setDomainZoomable(false);
        atrChartPanel.setRangeZoomable(false);
        atrChartPanel.setMinimumDrawWidth(0);
        atrChartPanel.setMinimumDrawHeight(0);
        atrChartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        atrChartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);
    }

    private void setupScrollableContainer() {
        // Create wrappers with zoom buttons for each chart
        // Volume is placed right after price for better correlation viewing
        org.jfree.chart.ChartPanel[] chartPanels = {
            priceChartPanel, volumeChartPanel, equityChartPanel,
            comparisonChartPanel, capitalUsageChartPanel, tradePLChartPanel
        };
        chartWrappers = new JPanel[6];
        zoomButtons = new JButton[6];

        for (int i = 0; i < chartPanels.length; i++) {
            chartWrappers[i] = createChartWrapper(chartPanels[i], i);
        }

        // Create wrappers for optional indicator charts with zoom buttons
        rsiZoomBtn = new JButton("⤢");
        macdZoomBtn = new JButton("⤢");
        atrZoomBtn = new JButton("⤢");
        rsiChartWrapper = createIndicatorChartWrapper(rsiChartPanel, rsiZoomBtn, 0);
        macdChartWrapper = createIndicatorChartWrapper(macdChartPanel, macdZoomBtn, 1);
        atrChartWrapper = createIndicatorChartWrapper(atrChartPanel, atrZoomBtn, 2);

        // Create container for all charts using GridBagLayout for variable sizing
        chartsContainer = new JPanel(new GridBagLayout());
        updateChartLayout();

        // Time scrollbar for fixed-width mode (hidden by default)
        timeScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
        timeScrollBar.setVisible(false);
        timeScrollBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateVisibleTimeRange();
            }
        });

        // Add mouse wheel listener to all chart panels for scrolling
        java.awt.event.MouseWheelListener wheelListener = e -> {
            if (fixedWidthMode && timeScrollBar.isVisible()) {
                int scrollAmount = e.getWheelRotation() * timeScrollBar.getUnitIncrement();
                int newValue = timeScrollBar.getValue() + scrollAmount;
                newValue = Math.max(timeScrollBar.getMinimum(),
                           Math.min(newValue, timeScrollBar.getMaximum() - timeScrollBar.getVisibleAmount()));
                timeScrollBar.setValue(newValue);
            }
        };
        priceChartPanel.addMouseWheelListener(wheelListener);
        equityChartPanel.addMouseWheelListener(wheelListener);
        comparisonChartPanel.addMouseWheelListener(wheelListener);
        capitalUsageChartPanel.addMouseWheelListener(wheelListener);
        tradePLChartPanel.addMouseWheelListener(wheelListener);
        volumeChartPanel.addMouseWheelListener(wheelListener);
        rsiChartPanel.addMouseWheelListener(wheelListener);
        macdChartPanel.addMouseWheelListener(wheelListener);
        atrChartPanel.addMouseWheelListener(wheelListener);

        // Main panel with charts and scrollbar
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(chartsContainer, BorderLayout.CENTER);
        mainPanel.add(timeScrollBar, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Create a wrapper for indicator charts with zoom button
     */
    private JPanel createIndicatorChartWrapper(org.jfree.chart.ChartPanel chartPanel, JButton zoomBtn, int indicatorIndex) {
        zoomBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        zoomBtn.setMargin(new Insets(2, 4, 1, 4));
        zoomBtn.setFocusPainted(false);
        zoomBtn.setToolTipText("Zoom chart");
        zoomBtn.addActionListener(e -> toggleIndicatorZoom(indicatorIndex));

        JLayeredPane layeredPane = new JLayeredPane();
        chartPanel.setBounds(0, 0, 100, 100);
        layeredPane.add(chartPanel, JLayeredPane.DEFAULT_LAYER);

        Dimension btnSize = zoomBtn.getPreferredSize();
        zoomBtn.setBounds(0, 5, btnSize.width, btnSize.height);
        layeredPane.add(zoomBtn, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                chartPanel.setBounds(0, 0, w, h);
                Dimension bs = zoomBtn.getPreferredSize();
                zoomBtn.setBounds(w - bs.width - 12, 8, bs.width, bs.height);
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(layeredPane, BorderLayout.CENTER);
        wrapper.setMinimumSize(new Dimension(100, MIN_CHART_HEIGHT));
        return wrapper;
    }

    private void toggleIndicatorZoom(int index) {
        if (indicatorZoomedIndex == index) {
            indicatorZoomedIndex = -1; // Unzoom
        } else {
            indicatorZoomedIndex = index; // Zoom this indicator
            zoomedChartIndex = -1; // Reset core chart zoom
        }
        updateChartLayout();
        updateIndicatorZoomButtonStates();
    }

    private void updateIndicatorZoomButtonStates() {
        JButton[] btns = {rsiZoomBtn, macdZoomBtn, atrZoomBtn};
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] != null) {
                if (indicatorZoomedIndex == i) {
                    btns[i].setText("⤡");
                    btns[i].setToolTipText("Restore chart size");
                } else {
                    btns[i].setText("⤢");
                    btns[i].setToolTipText("Zoom chart");
                }
            }
        }
    }

    private static final int MIN_CHART_HEIGHT = 60;

    /**
     * Create a wrapper panel for a chart with a zoom button overlay
     */
    private JPanel createChartWrapper(org.jfree.chart.ChartPanel chartPanel, int chartIndex) {
        // Create zoom button
        JButton zoomBtn = new JButton("⤢");
        zoomBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        zoomBtn.setMargin(new Insets(2, 4, 1, 4));
        zoomBtn.setFocusPainted(false);
        zoomBtn.setToolTipText("Zoom chart");
        zoomBtn.addActionListener(e -> toggleZoom(chartIndex));
        zoomButtons[chartIndex] = zoomBtn;

        // Use JLayeredPane with manual positioning for the button
        JLayeredPane layeredPane = new JLayeredPane();

        // Add chart panel to fill the space
        chartPanel.setBounds(0, 0, 100, 100); // Will be updated on resize
        layeredPane.add(chartPanel, JLayeredPane.DEFAULT_LAYER);

        // Add button in upper right (position updated on resize)
        Dimension btnSize = zoomBtn.getPreferredSize();
        zoomBtn.setBounds(0, 5, btnSize.width, btnSize.height);
        layeredPane.add(zoomBtn, JLayeredPane.PALETTE_LAYER);

        // Update positions when resized
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                chartPanel.setBounds(0, 0, w, h);
                Dimension btnSize = zoomBtn.getPreferredSize();
                zoomBtn.setBounds(w - btnSize.width - 12, 8, btnSize.width, btnSize.height);
            }
        });

        // Wrapper to give layeredPane proper layout behavior
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(layeredPane, BorderLayout.CENTER);
        wrapper.setMinimumSize(new Dimension(100, MIN_CHART_HEIGHT));
        return wrapper;
    }

    /**
     * Toggle zoom state for a chart
     */
    private void toggleZoom(int chartIndex) {
        if (zoomedChartIndex == chartIndex) {
            // Unzoom
            zoomedChartIndex = -1;
        } else {
            // Zoom this chart (and unzoom any other)
            zoomedChartIndex = chartIndex;
            indicatorZoomedIndex = -1; // Reset indicator zoom
        }
        updateChartLayout();
        updateZoomButtonStates();
        updateIndicatorZoomButtonStates();
    }

    /**
     * Update chart layout based on zoom state
     */
    private void updateChartLayout() {
        chartsContainer.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // Build list of visible charts in order:
        // Price, Volume, [RSI], [MACD], [ATR], Equity, Comparison, CapitalUsage, TradeP&L
        java.util.List<JPanel> visibleCharts = new java.util.ArrayList<>();
        visibleCharts.add(chartWrappers[0]); // Price
        visibleCharts.add(chartWrappers[1]); // Volume
        if (rsiChartEnabled) visibleCharts.add(rsiChartWrapper);
        if (macdChartEnabled) visibleCharts.add(macdChartWrapper);
        if (atrChartEnabled) visibleCharts.add(atrChartWrapper);
        visibleCharts.add(chartWrappers[2]); // Equity
        visibleCharts.add(chartWrappers[3]); // Comparison
        visibleCharts.add(chartWrappers[4]); // Capital Usage
        visibleCharts.add(chartWrappers[5]); // Trade P&L

        // Map indicator wrappers to their zoom indices
        JPanel[] indicatorWrappers = {rsiChartWrapper, macdChartWrapper, atrChartWrapper};

        int totalCharts = visibleCharts.size();
        for (int i = 0; i < totalCharts; i++) {
            gbc.gridy = i;
            JPanel panel = visibleCharts.get(i);

            // Check if this is a zoomed chart
            boolean isZoomed = false;

            // Check core chart zoom
            if (zoomedChartIndex >= 0 && zoomedChartIndex < 6) {
                for (int j = 0; j < 6; j++) {
                    if (chartWrappers[j] == panel && j == zoomedChartIndex) {
                        isZoomed = true;
                        break;
                    }
                }
            }

            // Check indicator chart zoom
            if (indicatorZoomedIndex >= 0) {
                for (int j = 0; j < 3; j++) {
                    if (indicatorWrappers[j] == panel && j == indicatorZoomedIndex) {
                        isZoomed = true;
                        break;
                    }
                }
            }

            // Set weight based on zoom state
            if (isZoomed) {
                gbc.weighty = 0.65;
            } else if (zoomedChartIndex >= 0 || indicatorZoomedIndex >= 0) {
                gbc.weighty = 0.35 / (totalCharts - 1);
            } else {
                gbc.weighty = 1.0 / totalCharts;
            }
            chartsContainer.add(panel, gbc);
        }
        chartsContainer.setBackground(BACKGROUND_COLOR);

        // Show time labels only on first (price) and last chart
        // Price chart labels on top, last chart labels on bottom
        JFreeChart[] allCharts = {priceChart, volumeChart, rsiChart, macdChart, atrChart,
                                  equityChart, comparisonChart, capitalUsageChart, tradePLChart};
        JPanel[] allWrappers = {chartWrappers[0], chartWrappers[1], rsiChartWrapper, macdChartWrapper, atrChartWrapper,
                                chartWrappers[2], chartWrappers[3], chartWrappers[4], chartWrappers[5]};

        for (int i = 0; i < allCharts.length; i++) {
            if (allCharts[i] != null) {
                XYPlot plot = allCharts[i].getXYPlot();
                DateAxis axis = (DateAxis) plot.getDomainAxis();
                boolean isFirst = (allWrappers[i] == visibleCharts.get(0));
                boolean isLast = (allWrappers[i] == visibleCharts.get(visibleCharts.size() - 1));
                boolean showLabels = isFirst || isLast;
                axis.setTickLabelsVisible(showLabels);
                axis.setTickMarksVisible(showLabels);
                // Keep all axes at bottom for alignment
                plot.setDomainAxisLocation(org.jfree.chart.axis.AxisLocation.BOTTOM_OR_LEFT);
            }
        }

        chartsContainer.revalidate();
        chartsContainer.repaint();
    }

    /**
     * Update zoom button appearance based on zoom state
     */
    private void updateZoomButtonStates() {
        for (int i = 0; i < zoomButtons.length; i++) {
            if (zoomButtons[i] != null) {
                if (zoomedChartIndex == i) {
                    zoomButtons[i].setText("⤡"); // Different icon when zoomed
                    zoomButtons[i].setToolTipText("Restore chart size");
                } else {
                    zoomButtons[i].setText("⤢");
                    zoomButtons[i].setToolTipText("Zoom chart");
                }
            }
        }
    }

    /**
     * Toggle between fit-to-width and fixed-width modes.
     * In fixed-width mode, each candle takes PIXELS_PER_CANDLE pixels.
     */
    public void setFixedWidthMode(boolean enabled) {
        this.fixedWidthMode = enabled;
        updateFixedWidthMode();

        // Update Y-axis when switching modes
        if (fitYAxisToVisible) {
            updateYAxisAutoRange();
        }
    }

    /**
     * Toggle Y-axis auto-fitting to visible data range.
     */
    public void setFitYAxisToVisibleData(boolean enabled) {
        this.fitYAxisToVisible = enabled;
        updateYAxisAutoRange();
    }

    private void updateYAxisAutoRange() {
        JFreeChart[] charts = {priceChart, equityChart, comparisonChart, tradePLChart};

        for (JFreeChart chart : charts) {
            if (chart == null) continue;
            XYPlot plot = chart.getXYPlot();
            ValueAxis rangeAxis = plot.getRangeAxis();

            if (fitYAxisToVisible) {
                // Fit to visible data only
                rangeAxis.setAutoRange(true);
                plot.configureRangeAxes();
            } else {
                // Show full data range - temporarily reset domain to get full range
                rangeAxis.setAutoRange(true);

                // Save current domain range
                DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
                double domainLower = domainAxis.getLowerBound();
                double domainUpper = domainAxis.getUpperBound();

                // Temporarily show all data to calculate full range
                domainAxis.setAutoRange(true);
                plot.configureRangeAxes();

                // Capture the full Y range
                double fullLower = rangeAxis.getLowerBound();
                double fullUpper = rangeAxis.getUpperBound();

                // Restore domain range
                domainAxis.setAutoRange(false);
                domainAxis.setRange(domainLower, domainUpper);

                // Set Y to full range
                rangeAxis.setAutoRange(false);
                rangeAxis.setRange(fullLower, fullUpper);
            }
        }

        // Capital usage: 0-100% when not fitting visible, auto when fitting
        if (capitalUsageChart != null) {
            ValueAxis capitalAxis = capitalUsageChart.getXYPlot().getRangeAxis();
            if (fitYAxisToVisible) {
                capitalAxis.setAutoRange(true);
                capitalUsageChart.getXYPlot().configureRangeAxes();
            } else {
                capitalAxis.setAutoRange(false);
                capitalAxis.setRange(-5, 105);
            }
        }
    }

    private void updateFixedWidthMode() {
        if (currentCandles == null || currentCandles.isEmpty()) {
            timeScrollBar.setVisible(false);
            return;
        }

        if (fixedWidthMode) {
            // Calculate how many candles fit in the viewport at 4px each
            int viewportWidth = chartsContainer.getWidth();
            if (viewportWidth <= 0) viewportWidth = getWidth() - 50;
            int visibleCandles = Math.max(1, viewportWidth / PIXELS_PER_CANDLE);
            int totalCandles = currentCandles.size();

            if (visibleCandles < totalCandles) {
                // Need scrolling
                boolean wasVisible = timeScrollBar.isVisible();
                int oldValue = timeScrollBar.getValue();
                int oldMax = timeScrollBar.getMaximum();

                timeScrollBar.setMinimum(0);
                timeScrollBar.setMaximum(totalCandles);
                timeScrollBar.setVisibleAmount(visibleCandles);
                timeScrollBar.setBlockIncrement(visibleCandles / 2);
                timeScrollBar.setUnitIncrement(10);

                // Only set to end if scrollbar is newly shown or data changed
                if (!wasVisible || oldMax != totalCandles) {
                    timeScrollBar.setValue(totalCandles - visibleCandles);
                } else {
                    // Keep current position (adjusted for new visible amount)
                    timeScrollBar.setValue(Math.min(oldValue, totalCandles - visibleCandles));
                }

                timeScrollBar.setVisible(true);
                updateVisibleTimeRange();
            } else {
                timeScrollBar.setVisible(false);
                resetDomainAxisRange();
            }
        } else {
            timeScrollBar.setVisible(false);
            resetDomainAxisRange();
        }
    }

    private void updateVisibleTimeRange() {
        if (!fixedWidthMode || currentCandles == null || currentCandles.isEmpty()) return;

        int startIndex = timeScrollBar.getValue();
        int visibleCandles = timeScrollBar.getVisibleAmount();
        int endIndex = Math.min(startIndex + visibleCandles, currentCandles.size() - 1);
        startIndex = Math.max(0, startIndex);

        if (startIndex >= currentCandles.size() || endIndex < 0) return;

        long startTime = currentCandles.get(startIndex).timestamp();
        long endTime = currentCandles.get(endIndex).timestamp();

        // Add small padding
        long range = endTime - startTime;
        long padding = range / 50;
        startTime -= padding;
        endTime += padding;

        setDomainAxisRange(startTime, endTime);

        // Update Y-axis to fit visible data if enabled
        if (fitYAxisToVisible) {
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

        // Set consistent domain axis range for ALL charts (same as updateCharts)
        long startTime = currentCandles.get(0).timestamp();
        long endTime = currentCandles.get(currentCandles.size() - 1).timestamp();
        JFreeChart[] charts = {priceChart, volumeChart, equityChart, comparisonChart,
                               capitalUsageChart, tradePLChart, rsiChart, macdChart, atrChart};

        for (JFreeChart chart : charts) {
            if (chart == null) continue;
            DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
            axis.setAutoRange(false);
            axis.setRange(startTime, endTime);
        }
    }

    private void syncDomainAxes() {
        // Use price chart as the master - sync all others to it
        DateAxis masterAxis = (DateAxis) priceChart.getXYPlot().getDomainAxis();

        // When master axis changes, update all other axes
        masterAxis.addChangeListener(event -> {
            if (masterAxis.getRange() == null) return;

            double lower = masterAxis.getLowerBound();
            double upper = masterAxis.getUpperBound();

            JFreeChart[] otherCharts = {equityChart, comparisonChart, capitalUsageChart, tradePLChart, volumeChart,
                                        rsiChart, macdChart, atrChart};
            for (JFreeChart chart : otherCharts) {
                if (chart == null) continue;
                DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
                if (axis.getLowerBound() != lower || axis.getUpperBound() != upper) {
                    axis.setRange(lower, upper);
                }
            }
        });
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        // Update visible range when panel resizes
        if (fixedWidthMode) {
            SwingUtilities.invokeLater(this::updateFixedWidthMode);
        }
    }

    private void setupCrosshairs() {
        Color crosshairColor = CROSSHAIR_COLOR;

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

        // Volume chart crosshair
        volumeCrosshair = new Crosshair(Double.NaN);
        volumeCrosshair.setPaint(crosshairColor);
        CrosshairOverlay volumeOverlay = new CrosshairOverlay();
        volumeOverlay.addDomainCrosshair(volumeCrosshair);
        volumeChartPanel.addOverlay(volumeOverlay);

        // RSI chart crosshair
        rsiCrosshair = new Crosshair(Double.NaN);
        rsiCrosshair.setPaint(crosshairColor);
        CrosshairOverlay rsiOverlay = new CrosshairOverlay();
        rsiOverlay.addDomainCrosshair(rsiCrosshair);
        rsiChartPanel.addOverlay(rsiOverlay);

        // MACD chart crosshair
        macdCrosshair = new Crosshair(Double.NaN);
        macdCrosshair.setPaint(crosshairColor);
        CrosshairOverlay macdOverlay = new CrosshairOverlay();
        macdOverlay.addDomainCrosshair(macdCrosshair);
        macdChartPanel.addOverlay(macdOverlay);

        // ATR chart crosshair
        atrCrosshair = new Crosshair(Double.NaN);
        atrCrosshair.setPaint(crosshairColor);
        CrosshairOverlay atrOverlay = new CrosshairOverlay();
        atrOverlay.addDomainCrosshair(atrCrosshair);
        atrChartPanel.addOverlay(atrOverlay);

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
        volumeChartPanel.addChartMouseListener(listener);
        rsiChartPanel.addChartMouseListener(listener);
        macdChartPanel.addChartMouseListener(listener);
        atrChartPanel.addChartMouseListener(listener);
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
        volumeCrosshair.setValue(domainValue);
        rsiCrosshair.setValue(domainValue);
        macdCrosshair.setValue(domainValue);
        atrCrosshair.setValue(domainValue);

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
        chart.setBackgroundPaint(BACKGROUND_COLOR);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(PLOT_BACKGROUND_COLOR);
        plot.setDomainGridlinePaint(GRIDLINE_COLOR);
        plot.setRangeGridlinePaint(GRIDLINE_COLOR);
        plot.setOutlineVisible(false);

        // Date axis formatting
        if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
            dateAxis.setDateFormatOverride(new SimpleDateFormat("MMM d"));
            dateAxis.setTickLabelPaint(Color.LIGHT_GRAY);
            dateAxis.setAxisLineVisible(false);
        }

        plot.getRangeAxis().setTickLabelPaint(Color.LIGHT_GRAY);
        plot.getRangeAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setFixedDimension(60);  // Fixed width for alignment

        // Set thin line stroke with rounded joins for all series
        if (plot.getRenderer() != null) {
            plot.getRenderer().setDefaultStroke(ChartStyles.LINE_STROKE);
        }

        // Add title as annotation only if chart has no legend
        if (chart.getLegend() == null) {
            TextTitle textTitle = new TextTitle(title, new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            textTitle.setPaint(TEXT_COLOR);
            textTitle.setBackgroundPaint(null);
            XYTitleAnnotation titleAnnotation = new XYTitleAnnotation(0.01, 0.98, textTitle, RectangleAnchor.TOP_LEFT);
            plot.addAnnotation(titleAnnotation);
        }

        // Legends are handled by custom legend panel for consistent alignment
    }

    /**
     * Add title annotation to chart plot (for re-adding after clearAnnotations)
     */
    private void addChartTitleAnnotation(XYPlot plot, String title) {
        TextTitle textTitle = new TextTitle(title, new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        textTitle.setPaint(TEXT_COLOR);
        textTitle.setBackgroundPaint(null);
        XYTitleAnnotation titleAnnotation = new XYTitleAnnotation(0.01, 0.98, textTitle, RectangleAnchor.TOP_LEFT);
        plot.addAnnotation(titleAnnotation);
    }

    /**
     * Update date axis format on all charts based on data range.
     * Shows year when data spans beyond current year.
     */
    private void updateDateAxisFormat(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        long firstTimestamp = candles.getFirst().timestamp();
        int currentYear = Year.now().getValue();
        int dataStartYear = Year.from(java.time.Instant.ofEpochMilli(firstTimestamp)
            .atZone(java.time.ZoneId.systemDefault())).getValue();

        // Use format with year if data spans beyond current year
        SimpleDateFormat format = (dataStartYear < currentYear)
            ? new SimpleDateFormat("MMM d ''yy")  // e.g., "Jan 1 '24"
            : new SimpleDateFormat("MMM d");      // e.g., "Jan 1"

        // Apply to all charts
        JFreeChart[] charts = {priceChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart};
        for (JFreeChart chart : charts) {
            if (chart != null && chart.getXYPlot().getDomainAxis() instanceof DateAxis dateAxis) {
                dateAxis.setDateFormatOverride(format);
            }
        }
    }

    /**
     * Update charts with new candle data and trades
     */
    public void updateCharts(List<Candle> candles, List<Trade> trades, double initialCapital) {
        if (candles == null || candles.isEmpty()) return;

        this.currentCandles = candles;

        // Update date format based on data range
        updateDateAxisFormat(candles);

        updatePriceChart(candles, trades);
        updateEquityChart(candles, trades, initialCapital);
        updateComparisonChart(candles, trades, initialCapital);
        updateCapitalUsageChart(candles, trades, initialCapital);
        updateTradePLChart(candles, trades);
        updateVolumeChart(candles);

        // Update optional indicator charts
        updateRsiChart(candles);
        updateMacdChart(candles);
        updateAtrChart(candles);

        // Set consistent domain axis range for ALL charts
        long startTime = candles.get(0).timestamp();
        long endTime = candles.get(candles.size() - 1).timestamp();
        JFreeChart[] allCharts = {priceChart, volumeChart, equityChart, comparisonChart,
                                  capitalUsageChart, tradePLChart, rsiChart, macdChart, atrChart};
        for (JFreeChart chart : allCharts) {
            if (chart != null) {
                DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
                axis.setAutoRange(false);
                axis.setRange(startTime, endTime);
            }
        }

        // Update fixed-width mode scrollbar if active
        if (fixedWidthMode) {
            updateFixedWidthMode();
        }
    }

    private void updatePriceChart(List<Candle> candles, List<Trade> trades) {
        XYPlot plot = priceChart.getXYPlot();

        // Clear existing annotations first (keep title annotation)
        plot.getAnnotations().stream()
            .filter(a -> !(a instanceof XYTitleAnnotation))
            .toList()
            .forEach(plot::removeAnnotation);

        // Standard price line
        TimeSeries priceSeries = new TimeSeries("Price");
        for (Candle c : candles) {
            priceSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), c.close());
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(priceSeries);
        plot.setDataset(dataset);

        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer renderer =
            new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, PRICE_LINE_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.LINE_STROKE);
        plot.setRenderer(renderer);

        // Add Mayer Multiple threshold lines if enabled
        if (mayerMultipleEnabled && candles.size() >= mayerPeriod) {
            // Calculate SMA for Mayer Multiple
            double[] sma = new double[candles.size()];
            for (int i = mayerPeriod - 1; i < candles.size(); i++) {
                double sum = 0;
                for (int j = 0; j < mayerPeriod; j++) {
                    sum += candles.get(i - j).close();
                }
                sma[i] = sum / mayerPeriod;
            }

            // Draw threshold lines at Mayer levels (tighter for short timeframes)
            double[] thresholds = {0.95, 0.98, 1.0, 1.02, 1.05};
            Color[] colors = {MAYER_DEEP_UNDERVALUED, MAYER_UNDERVALUED, MAYER_NEUTRAL, MAYER_OVERVALUED, MAYER_DEEP_OVERVALUED};
            BasicStroke solidStroke = new BasicStroke(1.5f);
            BasicStroke dashedStroke = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10.0f, new float[]{6.0f, 4.0f}, 0.0f);

            for (int t = 0; t < thresholds.length; t++) {
                double mult = thresholds[t];
                Color color = colors[t];

                // Draw line segments for this threshold
                for (int i = mayerPeriod; i < candles.size(); i++) {
                    Candle prev = candles.get(i - 1);
                    Candle curr = candles.get(i);

                    double prevPrice = sma[i - 1] * mult;
                    double currPrice = sma[i] * mult;

                    XYLineAnnotation segment = new XYLineAnnotation(
                        prev.timestamp(), prevPrice,
                        curr.timestamp(), currPrice,
                        dashedStroke, color
                    );
                    plot.addAnnotation(segment);
                }
            }
        }

        // Add trade lines - green for wins, red for losses
        if (trades != null) {
            BasicStroke thinStroke = new BasicStroke(1.0f);

            // Group trades by groupId
            java.util.List<Trade> validTrades = trades.stream()
                .filter(t -> t.exitTime() != null && t.exitPrice() != null && !"rejected".equals(t.exitReason()))
                .sorted((a, b) -> Long.compare(a.entryTime(), b.entryTime()))
                .toList();

            java.util.Map<String, java.util.List<Trade>> tradesByGroup = new java.util.LinkedHashMap<>();
            for (Trade t : validTrades) {
                String groupId = t.groupId() != null ? t.groupId() : "single-" + t.id();
                tradesByGroup.computeIfAbsent(groupId, k -> new java.util.ArrayList<>()).add(t);
            }
            java.util.List<java.util.List<Trade>> tradeGroups = new java.util.ArrayList<>(tradesByGroup.values());

            for (java.util.List<Trade> group : tradeGroups) {
                if (group.size() == 1) {
                    // Single trade - draw simple diagonal line
                    Trade t = group.get(0);
                    boolean isWin = t.pnl() != null && t.pnl() > 0;
                    Color color = isWin ? WIN_COLOR : LOSS_COLOR;

                    XYLineAnnotation tradeLine = new XYLineAnnotation(
                        t.entryTime(), t.entryPrice(),
                        t.exitTime(), t.exitPrice(),
                        ChartStyles.TRADE_LINE_STROKE, color
                    );
                    plot.addAnnotation(tradeLine);
                } else {
                    // DCA position - multiple entries with same exit
                    // Calculate weighted average entry price
                    double totalValue = 0;
                    double totalQuantity = 0;
                    double totalPnl = 0;
                    long firstEntryTime = Long.MAX_VALUE;
                    long lastEntryTime = Long.MIN_VALUE;

                    for (Trade t : group) {
                        totalValue += t.entryPrice() * t.quantity();
                        totalQuantity += t.quantity();
                        if (t.pnl() != null) totalPnl += t.pnl();
                        firstEntryTime = Math.min(firstEntryTime, t.entryTime());
                        lastEntryTime = Math.max(lastEntryTime, t.entryTime());
                    }

                    double avgEntryPrice = totalValue / totalQuantity;
                    boolean isWin = totalPnl > 0;
                    Color color = isWin ? WIN_COLOR : LOSS_COLOR;
                    Trade lastTrade = group.get(0); // All have same exit time/price

                    // Draw vertical lines from each entry to the average line (35% alpha)
                    Color verticalColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 89);
                    for (Trade t : group) {
                        XYLineAnnotation verticalLine = new XYLineAnnotation(
                            t.entryTime(), t.entryPrice(),
                            t.entryTime(), avgEntryPrice,
                            thinStroke, verticalColor
                        );
                        plot.addAnnotation(verticalLine);
                    }

                    // Draw horizontal line at average entry price (from first entry to last entry)
                    XYLineAnnotation avgLine = new XYLineAnnotation(
                        firstEntryTime, avgEntryPrice,
                        lastEntryTime, avgEntryPrice,
                        ChartStyles.TRADE_LINE_STROKE, color
                    );
                    plot.addAnnotation(avgLine);

                    // Draw diagonal line from center of avg line to exit point
                    long centerTime = (firstEntryTime + lastEntryTime) / 2;
                    XYLineAnnotation exitLine = new XYLineAnnotation(
                        centerTime, avgEntryPrice,
                        lastTrade.exitTime(), lastTrade.exitPrice(),
                        ChartStyles.TRADE_LINE_STROKE, color
                    );
                    plot.addAnnotation(exitLine);

                    // Draw 6px dots at start and end of diagonal line
                    double dotSize = 6.0;
                    final long cTime = centerTime;
                    final double avgPrice = avgEntryPrice;
                    final long exitTime = lastTrade.exitTime();
                    final double exitPrice = lastTrade.exitPrice();
                    final Color dotColor = color;

                    plot.addAnnotation(new org.jfree.chart.annotations.AbstractXYAnnotation() {
                        @Override public void draw(java.awt.Graphics2D g2, XYPlot plot, java.awt.geom.Rectangle2D dataArea,
                                ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex, org.jfree.chart.plot.PlotRenderingInfo info) {
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
        plot.getRenderer().setSeriesPaint(0, EQUITY_COLOR);
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
        plot.getRenderer().setSeriesPaint(0, EQUITY_COLOR);
        plot.getRenderer().setSeriesPaint(1, BUY_HOLD_COLOR);
    }

    private void updateCapitalUsageChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries usageSeries = new TimeSeries("Capital Usage");

        // Filter out rejected trades (quantity = 0)
        List<Trade> validTrades = trades == null ? List.of() : trades.stream()
            .filter(t -> t.quantity() > 0)
            .toList();

        if (validTrades.isEmpty()) {
            // No trades, show 0% usage
            for (Candle c : candles) {
                usageSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), 0.0);
            }
        } else {
            // Build maps of entry/exit timestamps
            java.util.Map<Long, Double> entryValues = new java.util.HashMap<>();
            java.util.Map<Long, Double> exitValues = new java.util.HashMap<>();

            for (Trade t : validTrades) {
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
            for (Trade t : validTrades) {
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

                // Calculate usage percentage (cap at 100% - can appear higher if equity dropped)
                double usagePercent = equity > 0 ? Math.min((invested / equity) * 100, 100.0) : 0;
                usageSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), usagePercent);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(usageSeries);
        XYPlot plot = capitalUsageChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, CAPITAL_USAGE_COLOR);
        plot.getRangeAxis().setRange(-5, 105);
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

            // Set rainbow colors for each series
            XYPlot plot = tradePLChart.getXYPlot();
            plot.setDataset(dataset);
            for (int i = 0; i < tradeNum; i++) {
                plot.getRenderer().setSeriesPaint(i, ChartStyles.RAINBOW_COLORS[i % ChartStyles.RAINBOW_COLORS.length]);
            }
        } else {
            tradePLChart.getXYPlot().setDataset(dataset);
        }
    }

    /**
     * Update volume chart with colored bars based on relative volume (Wyckoff-style)
     */
    private void updateVolumeChart(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        // Calculate average volume over lookback period
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

        // Create series for different volume levels (will be colored differently)
        XYSeries[] volumeSeries = new XYSeries[7];
        String[] seriesNames = {"Ultra Low", "Very Low", "Low", "Average", "High", "Very High", "Ultra High"};
        for (int i = 0; i < 7; i++) {
            volumeSeries[i] = new XYSeries(seriesNames[i]);
        }

        // Classify each bar by relative volume
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double relVol = c.volume() / avgVolumes[i];

            // Determine which series this bar belongs to
            int seriesIdx;
            if (relVol >= 2.2) seriesIdx = 6;       // Ultra High (magenta)
            else if (relVol >= 1.8) seriesIdx = 5;  // Very High (red)
            else if (relVol >= 1.2) seriesIdx = 4;  // High (orange)
            else if (relVol >= 0.8) seriesIdx = 3;  // Average (yellow/green)
            else if (relVol >= 0.5) seriesIdx = 2;  // Low (cyan)
            else if (relVol >= 0.3) seriesIdx = 1;  // Very Low (blue)
            else seriesIdx = 0;                      // Ultra Low (purple)

            volumeSeries[seriesIdx].add(c.timestamp(), c.volume());
        }

        // Create dataset and set colors
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (XYSeries series : volumeSeries) {
            dataset.addSeries(series);
        }

        XYPlot plot = volumeChart.getXYPlot();
        plot.setDataset(dataset);

        // Create custom renderer with colors
        XYBarRenderer renderer = new XYBarRenderer(0.0);
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);

        for (int i = 0; i < VOLUME_COLORS.length; i++) {
            renderer.setSeriesPaint(i, VOLUME_COLORS[i]);
        }

        plot.setRenderer(renderer);
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
        volumeChart.getXYPlot().setDataset(new XYSeriesCollection());
        clearSmaOverlay();
        clearEmaOverlay();
        clearBollingerOverlay();
        clearHighLowOverlay();
    }

    /**
     * Set SMA overlay on price chart
     */
    public void setSmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearSmaOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Calculate SMA values
        smaSeries = new TimeSeries("SMA(" + period + ")");

        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += candles.get(i - j).close();
            }
            double sma = sum / period;
            smaSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), sma);
        }

        // Add as secondary dataset
        TimeSeriesCollection smaDataset = new TimeSeriesCollection(smaSeries);

        if (smaDatasetIndex < 0) {
            // Find next available index
            smaDatasetIndex = 1;
            while (plot.getDataset(smaDatasetIndex) != null) {
                smaDatasetIndex++;
            }
        }

        plot.setDataset(smaDatasetIndex, smaDataset);

        // Style the SMA line
        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer smaRenderer =
            new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(true, false);
        smaRenderer.setSeriesPaint(0, SMA_COLOR);
        smaRenderer.setSeriesStroke(0, new BasicStroke(1.5f));
        plot.setRenderer(smaDatasetIndex, smaRenderer);
    }

    /**
     * Clear SMA overlay from price chart
     */
    public void clearSmaOverlay() {
        if (smaDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(smaDatasetIndex, null);
        }
        smaSeries = null;
    }

    /**
     * Set EMA overlay on price chart
     */
    public void setEmaOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearEmaOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Calculate EMA values
        emaSeries = new TimeSeries("EMA(" + period + ")");
        double multiplier = 2.0 / (period + 1);
        double ema = 0;

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                // Build up initial SMA
                continue;
            } else if (i == period - 1) {
                // First EMA is SMA of first 'period' values
                double sum = 0;
                for (int j = 0; j < period; j++) {
                    sum += candles.get(j).close();
                }
                ema = sum / period;
            } else {
                // EMA = (close - prevEMA) * multiplier + prevEMA
                ema = (candles.get(i).close() - ema) * multiplier + ema;
            }
            emaSeries.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), ema);
        }

        // Add as secondary dataset
        TimeSeriesCollection emaDataset = new TimeSeriesCollection(emaSeries);

        if (emaDatasetIndex < 0) {
            // Find next available index (after SMA if present)
            emaDatasetIndex = Math.max(2, smaDatasetIndex + 1);
            while (plot.getDataset(emaDatasetIndex) != null) {
                emaDatasetIndex++;
            }
        }

        plot.setDataset(emaDatasetIndex, emaDataset);

        // Style the EMA line - cyan color to distinguish from SMA
        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer emaRenderer =
            new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(true, false);
        emaRenderer.setSeriesPaint(0, EMA_COLOR);
        emaRenderer.setSeriesStroke(0, new BasicStroke(1.5f));
        plot.setRenderer(emaDatasetIndex, emaRenderer);
    }

    /**
     * Clear EMA overlay from price chart
     */
    public void clearEmaOverlay() {
        if (emaDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(emaDatasetIndex, null);
        }
        emaSeries = null;
    }

    /**
     * Set Bollinger Bands overlay on price chart
     */
    public void setBollingerOverlay(int period, double stdDevMultiplier, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearBollingerOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Calculate Bollinger Bands
        TimeSeries upperSeries = new TimeSeries("BB Upper");
        TimeSeries middleSeries = new TimeSeries("BB Middle");
        TimeSeries lowerSeries = new TimeSeries("BB Lower");

        for (int i = period - 1; i < candles.size(); i++) {
            // Calculate SMA (middle band)
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += candles.get(i - j).close();
            }
            double sma = sum / period;

            // Calculate standard deviation
            double sumSquaredDiff = 0;
            for (int j = 0; j < period; j++) {
                double diff = candles.get(i - j).close() - sma;
                sumSquaredDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSquaredDiff / period);

            // Calculate bands
            double upper = sma + (stdDevMultiplier * stdDev);
            double lower = sma - (stdDevMultiplier * stdDev);

            Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
            upperSeries.addOrUpdate(time, upper);
            middleSeries.addOrUpdate(time, sma);
            lowerSeries.addOrUpdate(time, lower);
        }

        // Add as dataset with 3 series
        TimeSeriesCollection bbDataset = new TimeSeriesCollection();
        bbDataset.addSeries(upperSeries);
        bbDataset.addSeries(middleSeries);
        bbDataset.addSeries(lowerSeries);

        if (bbDatasetIndex < 0) {
            bbDatasetIndex = Math.max(3, Math.max(smaDatasetIndex, emaDatasetIndex) + 1);
            while (plot.getDataset(bbDatasetIndex) != null) {
                bbDatasetIndex++;
            }
        }

        plot.setDataset(bbDatasetIndex, bbDataset);

        // Style: purple bands with semi-transparent fill effect
        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer bbRenderer =
            new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(true, false);
        bbRenderer.setSeriesPaint(0, BB_COLOR); // Upper
        bbRenderer.setSeriesPaint(1, BB_MIDDLE_COLOR); // Middle (lighter)
        bbRenderer.setSeriesPaint(2, BB_COLOR); // Lower
        bbRenderer.setSeriesStroke(0, new BasicStroke(1.0f));
        bbRenderer.setSeriesStroke(1, new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f}, 0.0f)); // Dashed middle
        bbRenderer.setSeriesStroke(2, new BasicStroke(1.0f));
        plot.setRenderer(bbDatasetIndex, bbRenderer);
    }

    /**
     * Clear Bollinger Bands overlay from price chart
     */
    public void clearBollingerOverlay() {
        if (bbDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(bbDatasetIndex, null);
        }
    }

    /**
     * Set High/Low overlay on price chart (support/resistance levels)
     */
    public void setHighLowOverlay(int period, List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            clearHighLowOverlay();
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Calculate rolling high/low
        TimeSeries highSeries = new TimeSeries("High(" + period + ")");
        TimeSeries lowSeries = new TimeSeries("Low(" + period + ")");

        for (int i = period - 1; i < candles.size(); i++) {
            double high = Double.MIN_VALUE;
            double low = Double.MAX_VALUE;

            for (int j = 0; j < period; j++) {
                Candle c = candles.get(i - j);
                high = Math.max(high, c.high());
                low = Math.min(low, c.low());
            }

            Millisecond time = new Millisecond(new Date(candles.get(i).timestamp()));
            highSeries.addOrUpdate(time, high);
            lowSeries.addOrUpdate(time, low);
        }

        // Add as dataset
        TimeSeriesCollection hlDataset = new TimeSeriesCollection();
        hlDataset.addSeries(highSeries);
        hlDataset.addSeries(lowSeries);

        if (hlDatasetIndex < 0) {
            hlDatasetIndex = Math.max(4, Math.max(Math.max(smaDatasetIndex, emaDatasetIndex), bbDatasetIndex) + 1);
            while (plot.getDataset(hlDatasetIndex) != null) {
                hlDatasetIndex++;
            }
        }

        plot.setDataset(hlDatasetIndex, hlDataset);

        // Style: green for high (resistance), red for low (support)
        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer hlRenderer =
            new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(true, false);
        hlRenderer.setSeriesPaint(0, WIN_COLOR);  // Green - resistance
        hlRenderer.setSeriesPaint(1, LOSS_COLOR);  // Red - support
        hlRenderer.setSeriesStroke(0, new BasicStroke(1.2f));
        hlRenderer.setSeriesStroke(1, new BasicStroke(1.2f));
        plot.setRenderer(hlDatasetIndex, hlRenderer);
    }

    /**
     * Clear High/Low overlay from price chart
     */
    public void clearHighLowOverlay() {
        if (hlDatasetIndex >= 0 && priceChart != null) {
            XYPlot plot = priceChart.getXYPlot();
            plot.setDataset(hlDatasetIndex, null);
        }
    }

    /**
     * Enable or disable Mayer Multiple color-coded price display
     */
    public void setMayerMultipleEnabled(boolean enabled, int period) {
        this.mayerMultipleEnabled = enabled;
        this.mayerPeriod = period;
    }

    public boolean isMayerMultipleEnabled() {
        return mayerMultipleEnabled;
    }

    /**
     * Get color for Mayer Multiple value using smooth gradient
     */
    private Color getMayerColor(double mayer) {
        // Smooth gradient from green (undervalued) through white (fair) to red (overvalued)
        if (mayer < 0.6) {
            return MAYER_DEEP_UNDERVALUED;
        } else if (mayer < 0.8) {
            // Gradient from deep green to light green
            float t = (float) ((mayer - 0.6) / 0.2);
            return interpolateColor(MAYER_DEEP_UNDERVALUED, MAYER_UNDERVALUED, t);
        } else if (mayer < 1.0) {
            // Gradient from light green to neutral
            float t = (float) ((mayer - 0.8) / 0.2);
            return interpolateColor(MAYER_UNDERVALUED, MAYER_NEUTRAL, t);
        } else if (mayer < 1.5) {
            // Gradient from neutral to orange
            float t = (float) ((mayer - 1.0) / 0.5);
            return interpolateColor(MAYER_NEUTRAL, MAYER_OVERVALUED, t);
        } else if (mayer < 2.4) {
            // Gradient from orange to red
            float t = (float) ((mayer - 1.5) / 0.9);
            return interpolateColor(MAYER_OVERVALUED, MAYER_DEEP_OVERVALUED, t);
        } else {
            return MAYER_DEEP_OVERVALUED;
        }
    }

    private Color interpolateColor(Color c1, Color c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (c1.getRed() + t * (c2.getRed() - c1.getRed()));
        int g = (int) (c1.getGreen() + t * (c2.getGreen() - c1.getGreen()));
        int b = (int) (c1.getBlue() + t * (c2.getBlue() - c1.getBlue()));
        return new Color(r, g, b);
    }

    // ===== RSI Chart Methods =====

    public void setRsiChartEnabled(boolean enabled, int period) {
        this.rsiChartEnabled = enabled;
        this.rsiPeriod = period;
        updateChartLayout();
    }

    public boolean isRsiChartEnabled() {
        return rsiChartEnabled;
    }

    private void updateRsiChart(List<Candle> candles) {
        if (!rsiChartEnabled || candles == null || candles.size() < rsiPeriod + 1) {
            return;
        }

        XYPlot plot = rsiChart.getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries rsiSeries = new TimeSeries("RSI(" + rsiPeriod + ")");

        // Calculate RSI
        double[] gains = new double[candles.size()];
        double[] losses = new double[candles.size()];

        for (int i = 1; i < candles.size(); i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            gains[i] = change > 0 ? change : 0;
            losses[i] = change < 0 ? -change : 0;
        }

        // Calculate initial averages
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= rsiPeriod; i++) {
            avgGain += gains[i];
            avgLoss += losses[i];
        }
        avgGain /= rsiPeriod;
        avgLoss /= rsiPeriod;

        // Calculate RSI values
        for (int i = rsiPeriod; i < candles.size(); i++) {
            if (i > rsiPeriod) {
                avgGain = (avgGain * (rsiPeriod - 1) + gains[i]) / rsiPeriod;
                avgLoss = (avgLoss * (rsiPeriod - 1) + losses[i]) / rsiPeriod;
            }

            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsi = 100 - (100 / (1 + rs));

            Candle c = candles.get(i);
            rsiSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), rsi);
        }

        dataset.addSeries(rsiSeries);
        plot.setDataset(dataset);

        // Style the RSI line
        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer renderer =
            new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, RSI_COLOR);
        renderer.setSeriesStroke(0, new BasicStroke(1.5f));
        plot.setRenderer(renderer);

        // Add reference lines at 30, 50, 70
        plot.clearAnnotations();
        addChartTitleAnnotation(plot, "RSI");
        BasicStroke dashedStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{4.0f, 4.0f}, 0.0f);

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();

            // Oversold line at 30
            plot.addAnnotation(new XYLineAnnotation(startTime, 30, endTime, 30, dashedStroke, RSI_OVERSOLD));
            // Neutral line at 50
            plot.addAnnotation(new XYLineAnnotation(startTime, 50, endTime, 50, dashedStroke, TEXT_COLOR));
            // Overbought line at 70
            plot.addAnnotation(new XYLineAnnotation(startTime, 70, endTime, 70, dashedStroke, RSI_OVERBOUGHT));
        }
    }

    // ===== MACD Chart Methods =====

    public void setMacdChartEnabled(boolean enabled, int fast, int slow, int signal) {
        this.macdChartEnabled = enabled;
        this.macdFast = fast;
        this.macdSlow = slow;
        this.macdSignal = signal;
        updateChartLayout();
    }

    public boolean isMacdChartEnabled() {
        return macdChartEnabled;
    }

    private void updateMacdChart(List<Candle> candles) {
        if (!macdChartEnabled || candles == null || candles.size() < macdSlow + macdSignal) {
            return;
        }

        XYPlot plot = macdChart.getXYPlot();
        TimeSeriesCollection lineDataset = new TimeSeriesCollection();
        TimeSeries macdLine = new TimeSeries("MACD");
        TimeSeries signalLine = new TimeSeries("Signal");

        // Calculate EMAs
        double[] fastEma = calculateEMA(candles, macdFast);
        double[] slowEma = calculateEMA(candles, macdSlow);
        double[] macdValues = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            macdValues[i] = fastEma[i] - slowEma[i];
        }

        // Calculate signal line (EMA of MACD)
        double[] signalValues = new double[candles.size()];
        double multiplier = 2.0 / (macdSignal + 1);
        signalValues[macdSlow - 1] = macdValues[macdSlow - 1];
        for (int i = macdSlow; i < candles.size(); i++) {
            signalValues[i] = (macdValues[i] - signalValues[i - 1]) * multiplier + signalValues[i - 1];
        }

        // Build series and histogram data
        XYSeriesCollection histogramDataset = new XYSeriesCollection();
        XYSeries histogramSeries = new XYSeries("Histogram");

        for (int i = macdSlow + macdSignal - 1; i < candles.size(); i++) {
            Candle c = candles.get(i);
            Millisecond ms = new Millisecond(new Date(c.timestamp()));
            macdLine.addOrUpdate(ms, macdValues[i]);
            signalLine.addOrUpdate(ms, signalValues[i]);
            histogramSeries.add(c.timestamp(), macdValues[i] - signalValues[i]);
        }

        lineDataset.addSeries(macdLine);
        lineDataset.addSeries(signalLine);
        histogramDataset.addSeries(histogramSeries);

        // Set up renderers
        plot.setDataset(0, histogramDataset);
        plot.setDataset(1, lineDataset);

        // Histogram renderer with color based on value
        XYBarRenderer histRenderer = new XYBarRenderer() {
            @Override
            public java.awt.Paint getItemPaint(int series, int item) {
                double value = histogramDataset.getYValue(series, item);
                return value >= 0 ? MACD_HIST_POS : MACD_HIST_NEG;
            }
        };
        histRenderer.setShadowVisible(false);
        histRenderer.setBarPainter(new org.jfree.chart.renderer.xy.StandardXYBarPainter());
        plot.setRenderer(0, histRenderer);

        // Line renderer for MACD and signal
        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer lineRenderer =
            new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, MACD_LINE_COLOR);
        lineRenderer.setSeriesPaint(1, MACD_SIGNAL_COLOR);
        lineRenderer.setSeriesStroke(0, new BasicStroke(1.5f));
        lineRenderer.setSeriesStroke(1, new BasicStroke(1.5f));
        plot.setRenderer(1, lineRenderer);

        // Add zero line
        plot.clearAnnotations();
        addChartTitleAnnotation(plot, "MACD");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            BasicStroke dashedStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10.0f, new float[]{4.0f, 4.0f}, 0.0f);
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0, dashedStroke, TEXT_COLOR));
        }
    }

    private double[] calculateEMA(List<Candle> candles, int period) {
        double[] ema = new double[candles.size()];
        double multiplier = 2.0 / (period + 1);

        // Start with SMA for first value
        double sum = 0;
        for (int i = 0; i < period && i < candles.size(); i++) {
            sum += candles.get(i).close();
        }
        ema[period - 1] = sum / period;

        // Calculate EMA
        for (int i = period; i < candles.size(); i++) {
            ema[i] = (candles.get(i).close() - ema[i - 1]) * multiplier + ema[i - 1];
        }

        return ema;
    }

    // ===== ATR Chart Methods =====

    public void setAtrChartEnabled(boolean enabled, int period) {
        this.atrChartEnabled = enabled;
        this.atrPeriod = period;
        updateChartLayout();
    }

    public boolean isAtrChartEnabled() {
        return atrChartEnabled;
    }

    private void updateAtrChart(List<Candle> candles) {
        if (!atrChartEnabled || candles == null || candles.size() < atrPeriod + 1) {
            return;
        }

        XYPlot plot = atrChart.getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries atrSeries = new TimeSeries("ATR(" + atrPeriod + ")");

        // Calculate True Range and ATR
        double[] tr = new double[candles.size()];
        tr[0] = candles.get(0).high() - candles.get(0).low();

        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            double highLow = curr.high() - curr.low();
            double highClose = Math.abs(curr.high() - prev.close());
            double lowClose = Math.abs(curr.low() - prev.close());
            tr[i] = Math.max(highLow, Math.max(highClose, lowClose));
        }

        // Calculate ATR using Wilder's smoothing
        double atr = 0;
        for (int i = 0; i < atrPeriod; i++) {
            atr += tr[i];
        }
        atr /= atrPeriod;

        for (int i = atrPeriod; i < candles.size(); i++) {
            if (i > atrPeriod) {
                atr = (atr * (atrPeriod - 1) + tr[i]) / atrPeriod;
            }
            Candle c = candles.get(i);
            atrSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), atr);
        }

        dataset.addSeries(atrSeries);
        plot.setDataset(dataset);

        // Style the ATR line
        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer renderer =
            new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ATR_COLOR);
        renderer.setSeriesStroke(0, new BasicStroke(1.5f));
        plot.setRenderer(renderer);

        // Clear any annotations and re-add title
        plot.clearAnnotations();
        addChartTitleAnnotation(plot, "ATR");
    }
}
