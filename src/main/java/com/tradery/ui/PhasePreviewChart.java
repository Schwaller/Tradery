package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.data.CandleStore;
import com.tradery.dsl.Parser;
import com.tradery.engine.ConditionEvaluator;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.model.Candle;
import com.tradery.model.Phase;
import com.tradery.ui.charts.ChartStyles;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Chart panel showing where a phase condition would be active over historical data.
 * Displays price with highlighted bands for periods when the phase is true.
 */
public class PhasePreviewChart extends JPanel {

    private JFreeChart chart;
    private ChartPanel chartPanel;
    private JLabel statusLabel;
    private Phase phase;
    private List<Candle> candles = new ArrayList<>();
    private boolean[] phaseActive;

    // Phase active highlight color - orange for visibility
    private static final Color PHASE_ACTIVE_COLOR = new Color(255, 152, 0, 100);  // Orange with good visibility

    // Data range - 4 years for a full crypto cycle
    private static final int YEARS_OF_DATA = 4;

    public PhasePreviewChart() {
        setLayout(new BorderLayout());
        initializeChart();
        initializeStatusLabel();
    }

    private void initializeChart() {
        // Create empty XY chart
        XYSeriesCollection dataset = new XYSeriesCollection();
        chart = ChartFactory.createTimeSeriesChart(
            null, null, null, dataset, false, false, false
        );

        ChartStyles.stylizeChart(chart, "Phase Preview");

        XYPlot plot = chart.getXYPlot();

        // Configure line renderer
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.PRICE_LINE_COLOR);
        renderer.setSeriesStroke(0, new BasicStroke(1.0f));
        plot.setRenderer(renderer);

        // Configure axes
        DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
        dateAxis.setTickLabelPaint(Color.LIGHT_GRAY);

        NumberAxis priceAxis = (NumberAxis) plot.getRangeAxis();
        priceAxis.setAutoRangeIncludesZero(false);
        priceAxis.setTickLabelPaint(Color.LIGHT_GRAY);

        // Create chart panel
        chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setPreferredSize(new Dimension(400, 200));

        add(chartPanel, BorderLayout.CENTER);
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

        // Load data in background
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String errorMessage = null;
            private int activeCount = 0;
            private int totalBars = 0;

            @Override
            protected Void doInBackground() {
                try {
                    loadAndEvaluate();
                } catch (Exception e) {
                    errorMessage = e.getMessage();
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

                // Load candles
                CandleStore candleStore = ApplicationContext.getInstance().getCandleStore();
                candles = candleStore.getCandles(symbol, timeframe, startTime, endTime);

                if (candles.isEmpty()) {
                    errorMessage = "No data available";
                    return;
                }

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
                        // Evaluation error at this bar - skip
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
        XYPlot plot = chart.getXYPlot();
        plot.setDataset(new XYSeriesCollection());
        plot.clearDomainMarkers();
        chart.fireChartChanged();
    }

    private void updateChart() {
        if (candles.isEmpty()) {
            clearChart();
            return;
        }

        XYPlot plot = chart.getXYPlot();

        // Create price series using raw timestamps
        XYSeries priceSeries = new XYSeries("Price", false, true);

        for (Candle candle : candles) {
            priceSeries.add(candle.timestamp(), candle.close());
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(priceSeries);
        plot.setDataset(dataset);

        // Clear existing markers
        plot.clearDomainMarkers();

        // Add phase active/inactive markers as background bands
        addPhaseMarkers(plot);

        chart.fireChartChanged();
    }

    private void addPhaseMarkers(XYPlot plot) {
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
                plot.addDomainMarker(marker, Layer.BACKGROUND);
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
