package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.data.DataConsumer;
import com.tradery.data.DataRequirement;
import com.tradery.data.DataRequirementsTracker;
import com.tradery.dsl.Parser;
import com.tradery.engine.ConditionEvaluator;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.model.Candle;
import com.tradery.model.Phase;
import com.tradery.ui.charts.ChartStyles;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chart panel showing where a phase condition would be active over historical data.
 * Displays price with highlighted bands for periods when the phase is true.
 */
public class PhasePreviewChart extends JPanel {

    private JFreeChart chart;
    private ChartPanel chartPanel;
    private CombinedDomainXYPlot combinedPlot;
    private XYPlot pricePlot;
    private XYPlot volumePlot;
    private JLabel statusLabel;
    private Phase phase;
    private List<Candle> candles = new ArrayList<>();
    private boolean[] phaseActive;

    // Phase active highlight color - orange for visibility
    private static final Color PHASE_ACTIVE_COLOR = new Color(255, 152, 0, 100);  // Orange with good visibility

    // Data range - 4 years for a full crypto cycle
    private static final int YEARS_OF_DATA = 4;

    // Counter for unique requirement IDs
    private static final AtomicInteger requestCounter = new AtomicInteger(0);
    private String currentRequestId;

    // Pan state
    private Point panStart = null;
    private double panStartDomainMin, panStartDomainMax;
    private double panStartRangeMin, panStartRangeMax;

    // Y-axis drag state
    private boolean draggingYAxis = false;
    private int yAxisDragStartY;
    private double yAxisDragStartRangeMin, yAxisDragStartRangeMax;

    public PhasePreviewChart() {
        setLayout(new BorderLayout());
        initializeChart();
        initializeStatusLabel();
    }

    private void initializeChart() {
        // Shared date axis
        DateAxis dateAxis = new DateAxis();
        dateAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        dateAxis.setAxisLineVisible(false);

        // Price subplot (80% weight) - Y axis on right side
        NumberAxis priceAxis = new NumberAxis();
        priceAxis.setAutoRangeIncludesZero(false);
        priceAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        priceAxis.setAxisLineVisible(false);

        pricePlot = new XYPlot(new TimeSeriesCollection(), null, priceAxis, createPriceRenderer());
        pricePlot.setRangeAxisLocation(org.jfree.chart.axis.AxisLocation.TOP_OR_RIGHT);
        pricePlot.setBackgroundPaint(ChartStyles.PLOT_BACKGROUND_COLOR);
        pricePlot.setDomainGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        pricePlot.setRangeGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        pricePlot.setOutlineVisible(false);

        // Volume subplot (20% weight)
        NumberAxis volumeAxis = new NumberAxis();
        volumeAxis.setAutoRangeIncludesZero(true);
        volumeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        volumeAxis.setAxisLineVisible(false);
        volumeAxis.setTickLabelsVisible(false);

        volumePlot = new XYPlot(new TimeSeriesCollection(), null, volumeAxis, createVolumeRenderer());
        volumePlot.setBackgroundPaint(ChartStyles.PLOT_BACKGROUND_COLOR);
        volumePlot.setDomainGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        volumePlot.setRangeGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        volumePlot.setOutlineVisible(false);

        // Combined plot: price (80%) + volume (20%)
        combinedPlot = new CombinedDomainXYPlot(dateAxis);
        combinedPlot.setGap(0);
        combinedPlot.add(pricePlot, 4);   // 80%
        combinedPlot.add(volumePlot, 1);  // 20%
        combinedPlot.setBackgroundPaint(ChartStyles.BACKGROUND_COLOR);
        combinedPlot.setOutlineVisible(false);

        // Create chart
        chart = new JFreeChart(null, null, combinedPlot, false);
        chart.setBackgroundPaint(ChartStyles.BACKGROUND_COLOR);

        // Create chart panel with custom controls
        chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(false);  // We handle it ourselves
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setPreferredSize(new Dimension(400, 200));

        // Add mouse listeners for pan, zoom, and Y-axis scaling
        chartPanel.addMouseListener(createMouseListener());
        chartPanel.addMouseMotionListener(createMouseMotionListener());
        chartPanel.addMouseWheelListener(createMouseWheelListener());

        add(chartPanel, BorderLayout.CENTER);
    }

    private XYLineAndShapeRenderer createPriceRenderer() {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.PRICE_LINE_COLOR);
        renderer.setSeriesStroke(0, new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        return renderer;
    }

    private XYAreaRenderer createVolumeRenderer() {
        XYAreaRenderer renderer = new XYAreaRenderer(XYAreaRenderer.AREA);
        renderer.setSeriesPaint(0, new Color(100, 100, 100, 150));
        renderer.setOutline(false);
        return renderer;
    }

    private MouseAdapter createMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }

                Point screenPoint = e.getPoint();

                // Check if on Y axis for scaling
                if (isOnYAxis(screenPoint)) {
                    startYAxisDrag(screenPoint.y);
                    return;
                }

                // Start panning
                startPan(screenPoint);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }
                panStart = null;
                draggingYAxis = false;
            }
        };
    }

    private MouseMotionAdapter createMouseMotionListener() {
        return new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point screenPoint = e.getPoint();

                if (draggingYAxis) {
                    handleYAxisDrag(screenPoint.y);
                    return;
                }

                if (panStart != null) {
                    handlePan(screenPoint);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // Show resize cursor on Y axis
                if (isOnYAxis(e.getPoint())) {
                    chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                } else {
                    chartPanel.setCursor(Cursor.getDefaultCursor());
                }
            }
        };
    }

    private MouseWheelListener createMouseWheelListener() {
        return e -> {
            Rectangle2D dataArea = getPriceSubplotDataArea();
            if (dataArea == null) return;

            double zoomFactor = e.getWheelRotation() < 0 ? 0.9 : 1.1;

            double mouseX = combinedPlot.getDomainAxis().java2DToValue(
                e.getPoint().getX(), dataArea, combinedPlot.getDomainAxisEdge()
            );

            double domainMin = combinedPlot.getDomainAxis().getLowerBound();
            double domainMax = combinedPlot.getDomainAxis().getUpperBound();
            double domainRange = domainMax - domainMin;
            double newRange = domainRange * zoomFactor;

            double mouseRatio = (mouseX - domainMin) / domainRange;
            double newMin = mouseX - mouseRatio * newRange;
            double newMax = newMin + newRange;

            combinedPlot.getDomainAxis().setRange(newMin, newMax);
        };
    }

    private boolean isOnYAxis(Point point) {
        Rectangle2D dataArea = getPriceSubplotDataArea();
        if (dataArea == null) return false;
        return point.x > dataArea.getMaxX() && point.x < dataArea.getMaxX() + 60;
    }

    private void startYAxisDrag(int y) {
        draggingYAxis = true;
        yAxisDragStartY = y;
        yAxisDragStartRangeMin = pricePlot.getRangeAxis().getLowerBound();
        yAxisDragStartRangeMax = pricePlot.getRangeAxis().getUpperBound();
    }

    private void startPan(Point point) {
        panStart = point;
        panStartDomainMin = combinedPlot.getDomainAxis().getLowerBound();
        panStartDomainMax = combinedPlot.getDomainAxis().getUpperBound();
        panStartRangeMin = pricePlot.getRangeAxis().getLowerBound();
        panStartRangeMax = pricePlot.getRangeAxis().getUpperBound();
    }

    /**
     * Gets the data area for the price subplot (first subplot in combined plot).
     */
    private Rectangle2D getPriceSubplotDataArea() {
        if (chartPanel.getChartRenderingInfo() == null) return null;
        PlotRenderingInfo plotInfo = chartPanel.getChartRenderingInfo().getPlotInfo();
        if (plotInfo == null || plotInfo.getSubplotCount() == 0) return null;
        return plotInfo.getSubplotInfo(0).getDataArea();
    }

    private void handleYAxisDrag(int currentY) {
        int dy = currentY - yAxisDragStartY;
        double scaleFactor = Math.pow(1.01, dy);

        double originalRange = yAxisDragStartRangeMax - yAxisDragStartRangeMin;
        double originalCenter = (yAxisDragStartRangeMax + yAxisDragStartRangeMin) / 2.0;

        double newRange = originalRange * scaleFactor;
        double newMin = originalCenter - newRange / 2.0;
        double newMax = originalCenter + newRange / 2.0;

        pricePlot.getRangeAxis().setRange(newMin, newMax);
    }

    private void handlePan(Point currentPoint) {
        Rectangle2D dataArea = getPriceSubplotDataArea();
        if (dataArea == null) return;

        int dx = currentPoint.x - panStart.x;
        int dy = currentPoint.y - panStart.y;

        double domainRange = panStartDomainMax - panStartDomainMin;
        double rangeRange = panStartRangeMax - panStartRangeMin;

        double domainDelta = -dx * domainRange / dataArea.getWidth();
        double rangeDelta = dy * rangeRange / dataArea.getHeight();

        combinedPlot.getDomainAxis().setRange(panStartDomainMin + domainDelta, panStartDomainMax + domainDelta);
        pricePlot.getRangeAxis().setRange(panStartRangeMin + rangeDelta, panStartRangeMax + rangeDelta);
    }

    private void showContextMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem fitAllItem = new JMenuItem("Fit All");
        fitAllItem.addActionListener(evt -> fitAll());
        menu.add(fitAllItem);

        JMenuItem fitXItem = new JMenuItem("Fit X Axis");
        fitXItem.addActionListener(evt -> fitXAxis());
        menu.add(fitXItem);

        JMenuItem fitYItem = new JMenuItem("Fit Y Axis");
        fitYItem.addActionListener(evt -> fitYAxis());
        menu.add(fitYItem);

        menu.show(chartPanel, e.getX(), e.getY());
    }

    private void fitAll() {
        fitXAxis();
        fitYAxis();
    }

    private void fitXAxis() {
        if (candles.isEmpty()) return;
        long minTime = candles.get(0).timestamp();
        long maxTime = candles.get(candles.size() - 1).timestamp();
        long padding = (maxTime - minTime) / 20;
        combinedPlot.getDomainAxis().setRange(minTime - padding, maxTime + padding);
    }

    private void fitYAxis() {
        if (candles.isEmpty()) return;
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (Candle c : candles) {
            minPrice = Math.min(minPrice, c.low());
            maxPrice = Math.max(maxPrice, c.high());
        }
        double padding = (maxPrice - minPrice) * 0.05;
        pricePlot.getRangeAxis().setRange(minPrice - padding, maxPrice + padding);
    }

    private void initializeStatusLabel() {
        statusLabel = new JLabel("Select a phase to preview", SwingConstants.CENTER);
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(statusLabel, BorderLayout.SOUTH);
    }

    /**
     * Update the chart for a new phase. Loads data and evaluates condition.
     */
    public void setPhase(Phase phase) {
        this.phase = phase;

        if (phase == null || phase.getCondition() == null || phase.getCondition().isBlank()) {
            clearChart();
            statusLabel.setText("No condition defined");
            return;
        }

        // Generate unique request ID for tracking
        currentRequestId = "phase-preview-" + requestCounter.incrementAndGet();

        // Load data in background
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String errorMessage = null;
            private int activeCount = 0;
            private int totalBars = 0;
            private final String requestId = currentRequestId;

            @Override
            protected Void doInBackground() {
                try {
                    loadAndEvaluate();
                } catch (Exception e) {
                    errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    System.err.println("PhasePreviewChart error: " + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }

            private void loadAndEvaluate() throws IOException {
                String symbol = phase.getSymbol() != null ? phase.getSymbol() : "BTCUSDT";
                String timeframe = phase.getTimeframe() != null ? phase.getTimeframe() : "1d";

                // Calculate date range (timestamps in millis)
                LocalDate endDate = LocalDate.now();
                LocalDate startDate = endDate.minusYears(YEARS_OF_DATA);
                long startTime = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                long endTime = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

                // Register data requirement with tracker
                DataRequirementsTracker tracker = ApplicationContext.getInstance().getPreviewTracker();
                String dataType = "OHLC:" + timeframe;
                DataRequirement requirement = new DataRequirement(
                    dataType,
                    symbol,
                    startTime,
                    endTime,
                    DataRequirement.Tier.TRADING,
                    "phase:" + phase.getId(),
                    DataConsumer.PHASE_PREVIEW
                );
                tracker.addRequirement(requirement);
                tracker.updateStatus(dataType, DataRequirementsTracker.Status.FETCHING);

                // Update status on EDT
                SwingUtilities.invokeLater(() -> statusLabel.setText("Fetching " + symbol + " " + timeframe + "..."));

                // Load candles from SQLite
                SqliteDataStore dataStore = ApplicationContext.getInstance().getSqliteDataStore();
                try {
                    candles = dataStore.getCandles(symbol, timeframe, startTime, endTime);
                } catch (Exception e) {
                    tracker.updateStatus(dataType, DataRequirementsTracker.Status.ERROR, 0, 0, e.getMessage());
                    throw e;
                }

                if (candles.isEmpty()) {
                    tracker.updateStatus(dataType, DataRequirementsTracker.Status.ERROR, 0, 0, "No data available");
                    errorMessage = "No data available for " + symbol + " " + timeframe;
                    return;
                }

                // Mark data as ready
                tracker.updateStatus(dataType, DataRequirementsTracker.Status.READY, candles.size(), candles.size());

                // Update status on EDT
                SwingUtilities.invokeLater(() -> statusLabel.setText("Evaluating condition..."));

                // Parse condition
                Parser parser = new Parser();
                Parser.ParseResult result = parser.parse(phase.getCondition());

                if (!result.success()) {
                    errorMessage = "Parse error: " + result.error();
                    return;
                }

                // Evaluate condition at each bar
                IndicatorEngine indicatorEngine = new IndicatorEngine();
                indicatorEngine.setCandles(candles, timeframe);
                ConditionEvaluator evaluator = new ConditionEvaluator(indicatorEngine);

                phaseActive = new boolean[candles.size()];
                totalBars = candles.size();

                // Calculate warmup based on condition complexity
                // Simple conditions (DAYOFWEEK, HOUR, etc.) don't need warmup
                String condition = phase.getCondition().toUpperCase();
                int warmup = 1; // Default minimal warmup
                if (condition.contains("SMA") || condition.contains("EMA") ||
                    condition.contains("RSI") || condition.contains("MACD") ||
                    condition.contains("ATR") || condition.contains("ADX") ||
                    condition.contains("BBANDS") || condition.contains("HIGH_OF") ||
                    condition.contains("LOW_OF") || condition.contains("AVG_VOLUME")) {
                    warmup = Math.min(50, candles.size() / 4);
                }

                for (int i = warmup; i < candles.size(); i++) {
                    try {
                        phaseActive[i] = evaluator.evaluate(result.ast(), i);
                        if (phaseActive[i]) activeCount++;
                    } catch (Exception e) {
                        // Evaluation error at this bar - log but continue
                        if (i == warmup) {
                            System.err.println("PhasePreviewChart evaluation error at bar " + i + ": " + e.getMessage());
                        }
                    }
                }
            }

            @Override
            protected void done() {
                // Only update if this is still the current request
                if (!requestId.equals(currentRequestId)) {
                    return;
                }

                if (errorMessage != null) {
                    clearChart();
                    statusLabel.setText(errorMessage);
                } else {
                    updateChart();
                    // Fit the chart to show all data
                    fitAll();
                    double pct = totalBars > 0 ? (activeCount * 100.0 / totalBars) : 0;
                    statusLabel.setText(String.format("Active: %.1f%% of %d bars (%d years)",
                        pct, totalBars, YEARS_OF_DATA));
                }
            }
        };

        statusLabel.setText("Loading data...");
        worker.execute();
    }

    /**
     * Refresh the chart with current phase (re-evaluate condition).
     */
    public void refresh() {
        if (phase != null) {
            setPhase(phase);
        }
    }

    private void clearChart() {
        pricePlot.setDataset(new TimeSeriesCollection());
        volumePlot.setDataset(new TimeSeriesCollection());
        pricePlot.clearDomainMarkers();
        chart.fireChartChanged();
    }

    private void updateChart() {
        if (candles.isEmpty()) {
            clearChart();
            return;
        }

        // Create price and volume series using TimeSeries for combined plot
        TimeSeries priceSeries = new TimeSeries("Price");
        TimeSeries volumeSeries = new TimeSeries("Volume");

        for (Candle candle : candles) {
            FixedMillisecond time = new FixedMillisecond(candle.timestamp());
            priceSeries.addOrUpdate(time, candle.close());
            volumeSeries.addOrUpdate(time, candle.volume());
        }

        pricePlot.setDataset(new TimeSeriesCollection(priceSeries));
        volumePlot.setDataset(new TimeSeriesCollection(volumeSeries));

        // Clear existing markers
        pricePlot.clearDomainMarkers();

        // Add phase active/inactive markers as background bands
        addPhaseMarkers();

        chart.fireChartChanged();
    }

    private void addPhaseMarkers() {
        if (phaseActive == null || candles.isEmpty()) return;

        // Calculate bar width for single-bar markers (use gap between candles)
        long barWidth = 24 * 60 * 60 * 1000L; // Default to 1 day in millis
        if (candles.size() >= 2) {
            barWidth = candles.get(1).timestamp() - candles.get(0).timestamp();
        }

        // Find contiguous regions where phase is active
        int i = 0;
        while (i < phaseActive.length) {
            if (phaseActive[i]) {
                // Start of active region
                int start = i;
                while (i < phaseActive.length && phaseActive[i]) {
                    i++;
                }
                int end = i - 1;

                // Add marker for this region
                long startTime = candles.get(start).timestamp();
                long endTime = candles.get(end).timestamp() + barWidth; // Extend to cover the full bar

                IntervalMarker marker = new IntervalMarker(startTime, endTime);
                marker.setPaint(PHASE_ACTIVE_COLOR);
                marker.setOutlinePaint(null);
                pricePlot.addDomainMarker(marker, Layer.BACKGROUND);
            } else {
                i++;
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 220);
    }
}
