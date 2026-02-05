package com.tradery.forge.ui;

import com.tradery.charts.core.ChartInteractionManager;
import com.tradery.core.dsl.Parser;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;
import com.tradery.core.model.Phase;
import com.tradery.engine.ConditionEvaluator;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.PageState;
import com.tradery.forge.data.page.CandlePageManager;
import com.tradery.forge.data.page.DataPageListener;
import com.tradery.forge.data.page.DataPageView;
import com.tradery.forge.ui.charts.ChartConfig;
import com.tradery.forge.ui.charts.ChartStyles;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chart panel showing where a phase condition would be active over historical data.
 * Displays price with highlighted bands for periods when the phase is true.
 */
public class PhasePreviewChart extends JPanel implements DataPageListener<Candle> {

    private static final Logger log = LoggerFactory.getLogger(PhasePreviewChart.class);

    private JFreeChart chart;
    private ChartPanel chartPanel;
    private CombinedDomainXYPlot combinedPlot;
    private XYPlot pricePlot;
    private XYPlot volumePlot;
    private JLabel statusLabel;
    private Phase phase;
    private List<Candle> candles = new ArrayList<>();
    private boolean[] phaseActive;

    // Phase marker stroke (minimum 1px for visibility)
    private static final BasicStroke PHASE_OUTLINE_STROKE = new BasicStroke(1.0f);

    /** Get accent color from FlatLaf theme with fallback */
    private static Color getAccentColor() {
        Color accent = UIManager.getColor("Component.accentColor");
        return accent != null ? accent : new Color(100, 140, 180); // Fallback blue
    }

    /** Get phase fill color (accent with transparency) */
    private static Color getPhaseActiveColor() {
        Color accent = getAccentColor();
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 100);
    }

    /** Get phase outline color (stronger accent for thin markers) */
    private static Color getPhaseOutlineColor() {
        Color accent = getAccentColor();
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180);
    }

    // Data range - 4 years for a full crypto cycle
    private static final int YEARS_OF_DATA = 4;

    // Counter for unique request IDs
    private static final AtomicInteger requestCounter = new AtomicInteger(0);
    private String currentRequestId;

    // Current data page (for cleanup)
    private DataPageView<Candle> currentPage;

    // Interaction manager for zoom/pan
    private final ChartInteractionManager interactionManager;

    public PhasePreviewChart() {
        interactionManager = new ChartInteractionManager();
        interactionManager.setAxisPositionSupplier(() -> ChartConfig.getInstance().getPriceAxisPosition());
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

        // Register chart for synchronized zooming and add interaction listeners
        interactionManager.addChart(chart);
        interactionManager.attachListeners(chartPanel, true); // Y-axis on right

        // Add context menu
        chartPanel.addMouseListener(createContextMenuListener());

        add(chartPanel, BorderLayout.CENTER);
    }

    private MouseAdapter createContextMenuListener() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
        };
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

        // Release previous page if any
        if (currentPage != null) {
            ApplicationContext.getInstance().getCandlePageManager().release(currentPage, this);
            currentPage = null;
        }

        String symbol = phase.getSymbol() != null ? phase.getSymbol() : "BTCUSDT";
        String timeframe = phase.getTimeframe() != null ? phase.getTimeframe() : "1d";

        // Calculate date range (timestamps in millis)
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(YEARS_OF_DATA);
        long startTime = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long endTime = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        statusLabel.setText("Loading " + symbol + " " + timeframe + "...");

        // Request data from CandlePageManager
        log.info("PhasePreviewChart requesting: symbol={}, timeframe={}, start={}, end={}",
            symbol, timeframe, startTime, endTime);
        CandlePageManager pageManager = ApplicationContext.getInstance().getCandlePageManager();
        currentPage = pageManager.request(symbol, timeframe, startTime, endTime, this, "PhasePreviewChart");

        // If already ready, onStateChanged will be called immediately
    }

    // ========== DataPageListener Implementation ==========

    @Override
    public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
        if (page != currentPage) return; // Ignore stale pages

        log.info("PhasePreviewChart.onStateChanged: newState={}, page={}", newState, page.getKey());

        switch (newState) {
            case LOADING -> statusLabel.setText("Loading candles...");
            case READY -> onDataReady(page);
            case ERROR -> {
                clearChart();
                String error = page.getErrorMessage() != null ? page.getErrorMessage() : "Unknown error";
                statusLabel.setText("Error: " + error);
            }
            case UPDATING -> statusLabel.setText("Updating...");
            case EMPTY -> {} // Initial state, ignore
        }
    }

    @Override
    public void onDataChanged(DataPageView<Candle> page) {
        if (page != currentPage) return;
        if (page.isReady()) {
            onDataReady(page);
        }
    }

    private void onDataReady(DataPageView<Candle> page) {
        candles = new ArrayList<>(page.getData());
        log.info("PhasePreviewChart.onDataReady: candleCount={}", candles.size());

        if (candles.isEmpty()) {
            clearChart();
            String symbol = phase.getSymbol() != null ? phase.getSymbol() : "BTCUSDT";
            String timeframe = phase.getTimeframe() != null ? phase.getTimeframe() : "1d";
            statusLabel.setText("No data available for " + symbol + " " + timeframe);
            return;
        }

        statusLabel.setText("Evaluating condition...");

        // Evaluate condition in background
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String errorMessage = null;
            private int activeCount = 0;
            private int totalBars = 0;

            @Override
            protected Void doInBackground() {
                try {
                    evaluateCondition();
                } catch (Exception e) {
                    errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                }
                return null;
            }

            private void evaluateCondition() {
                String timeframe = phase.getTimeframe() != null ? phase.getTimeframe() : "1d";

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
                String condition = phase.getCondition().toUpperCase();
                int warmup = 1;
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
                        if (i == warmup) {
                            System.err.println("PhasePreviewChart evaluation error at bar " + i + ": " + e.getMessage());
                        }
                    }
                }
            }

            @Override
            protected void done() {
                if (errorMessage != null) {
                    clearChart();
                    statusLabel.setText(errorMessage);
                } else {
                    updateChart();
                    fitAll();
                    double pct = totalBars > 0 ? (activeCount * 100.0 / totalBars) : 0;
                    statusLabel.setText(String.format("Active: %.1f%% of %d bars (%d years)",
                        pct, totalBars, YEARS_OF_DATA));
                }
            }
        };
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
                marker.setPaint(getPhaseActiveColor());
                // Always show outline so thin markers (like friday-afternoon) remain visible
                marker.setOutlinePaint(getPhaseOutlineColor());
                marker.setOutlineStroke(PHASE_OUTLINE_STROKE);
                pricePlot.addDomainMarker(marker, Layer.BACKGROUND);
            } else {
                i++;
            }
        }
    }

    /**
     * Cleanup when component is removed from hierarchy.
     */
    public void dispose() {
        if (currentPage != null) {
            ApplicationContext.getInstance().getCandlePageManager().release(currentPage, this);
            currentPage = null;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 220);
    }
}
