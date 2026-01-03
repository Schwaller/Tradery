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
            true,  // legend
            true,
            false
        );
        stylizeChart(priceChart, "Price");

        priceChartPanel = new org.jfree.chart.ChartPanel(priceChart);
        priceChartPanel.setMouseWheelEnabled(false);
        priceChartPanel.setDomainZoomable(false);
        priceChartPanel.setRangeZoomable(false);

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
        equityChartPanel.setMouseWheelEnabled(false);
        equityChartPanel.setDomainZoomable(false);
        equityChartPanel.setRangeZoomable(false);

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
        comparisonChartPanel.setMouseWheelEnabled(false);
        comparisonChartPanel.setDomainZoomable(false);
        comparisonChartPanel.setRangeZoomable(false);

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
        capitalUsageChartPanel.setMouseWheelEnabled(false);
        capitalUsageChartPanel.setDomainZoomable(false);
        capitalUsageChartPanel.setRangeZoomable(false);

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
    }

    private void setupScrollableContainer() {
        // Create wrappers with zoom buttons for each chart
        org.jfree.chart.ChartPanel[] chartPanels = {
            priceChartPanel, equityChartPanel, comparisonChartPanel,
            capitalUsageChartPanel, tradePLChartPanel
        };
        chartWrappers = new JPanel[5];
        zoomButtons = new JButton[5];

        for (int i = 0; i < chartPanels.length; i++) {
            chartWrappers[i] = createChartWrapper(chartPanels[i], i);
        }

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

        // Main panel with charts and scrollbar
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(chartsContainer, BorderLayout.CENTER);
        mainPanel.add(timeScrollBar, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

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
        }
        updateChartLayout();
        updateZoomButtonStates();
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

        for (int i = 0; i < chartWrappers.length; i++) {
            gbc.gridy = i;
            if (zoomedChartIndex == i) {
                gbc.weighty = 0.65; // 65% for zoomed chart
            } else if (zoomedChartIndex >= 0) {
                gbc.weighty = 0.0875; // 8.75% each for 4 non-zoomed charts
            } else {
                gbc.weighty = 0.2; // 20% each when none zoomed
            }
            chartsContainer.add(chartWrappers[i], gbc);
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
                capitalAxis.setRange(0, 100);
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
        JFreeChart[] charts = {priceChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart};

        for (JFreeChart chart : charts) {
            if (chart == null) continue;
            XYPlot plot = chart.getXYPlot();
            if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
                dateAxis.setAutoRange(true);
            }
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

            JFreeChart[] otherCharts = {equityChart, comparisonChart, capitalUsageChart, tradePLChart};
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

        // Set thin line stroke with rounded joins for all series
        if (plot.getRenderer() != null) {
            plot.getRenderer().setDefaultStroke(ChartStyles.LINE_STROKE);
        }

        // Add title as annotation only if chart has no legend
        if (chart.getLegend() == null) {
            TextTitle textTitle = new TextTitle(title, new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            textTitle.setPaint(new Color(150, 150, 150));
            textTitle.setBackgroundPaint(null);
            XYTitleAnnotation titleAnnotation = new XYTitleAnnotation(0.01, 0.98, textTitle, RectangleAnchor.TOP_LEFT);
            plot.addAnnotation(titleAnnotation);
        }

        // Make legend background transparent if present
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(new Color(0, 0, 0, 0));
            chart.getLegend().setFrame(BlockBorder.NONE);
            chart.getLegend().setItemPaint(Color.LIGHT_GRAY);
            chart.getLegend().setPosition(org.jfree.chart.ui.RectangleEdge.TOP);
            chart.getLegend().setItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            chart.getLegend().setPadding(new org.jfree.chart.ui.RectangleInsets(8, 2, 0, 2));
        }
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

        // Update fixed-width mode scrollbar if active
        if (fixedWidthMode) {
            updateFixedWidthMode();
        }
    }

    private void updatePriceChart(List<Candle> candles, List<Trade> trades) {
        TimeSeries priceSeries = new TimeSeries("Price");

        for (Candle c : candles) {
            priceSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), c.close());
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(priceSeries);
        XYPlot plot = priceChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, new Color(255, 255, 255, 180)); // Slightly transparent white

        // Clear existing trade annotations (keep title annotation)
        plot.getAnnotations().stream()
            .filter(a -> !(a instanceof XYTitleAnnotation))
            .toList()
            .forEach(plot::removeAnnotation);

        // Add trade lines - green for wins, red for losses
        if (trades != null) {
            Color winColor = new Color(76, 175, 80, 180);   // Green with transparency
            Color lossColor = new Color(244, 67, 54, 180);  // Red with transparency
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
                    Color color = isWin ? winColor : lossColor;

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
                    Color color = isWin ? winColor : lossColor;
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
        plot.getRenderer().setSeriesPaint(0, new Color(77, 77, 255)); // Neon blue
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
        plot.getRenderer().setSeriesPaint(0, new Color(77, 77, 255));   // Strategy - same blue as equity
        plot.getRenderer().setSeriesPaint(1, new Color(255, 193, 7));   // Buy & Hold - amber
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
        plot.getRenderer().setSeriesPaint(0, new Color(57, 255, 20)); // Neon green
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
