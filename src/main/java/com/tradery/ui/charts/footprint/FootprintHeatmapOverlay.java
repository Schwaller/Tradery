package com.tradery.ui.charts.footprint;

import com.tradery.indicators.FootprintIndicator;
import com.tradery.model.AggTrade;
import com.tradery.model.Candle;
import com.tradery.model.Exchange;
import com.tradery.model.Footprint;
import com.tradery.model.FootprintResult;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Overlay for footprint heatmap on the price chart.
 * Draws colored buckets showing buy/sell volume distribution at price levels.
 */
public class FootprintHeatmapOverlay {

    private static final Logger log = LoggerFactory.getLogger(FootprintHeatmapOverlay.class);

    private final JFreeChart priceChart;

    // Configuration
    private FootprintHeatmapConfig config;

    // Current data
    private List<Candle> currentCandles;
    private List<AggTrade> currentAggTrades;
    private String currentTimeframe = "1h";

    // Cached footprint result
    private FootprintResult footprintResult;

    // Current annotation
    private FootprintHeatmapAnnotation annotation;

    // Callback for repaint
    private Runnable onDataReady;

    public FootprintHeatmapOverlay(JFreeChart priceChart) {
        this.priceChart = priceChart;
        this.config = new FootprintHeatmapConfig();
    }

    public void setOnDataReady(Runnable callback) {
        this.onDataReady = callback;
    }

    // ===== Configuration =====

    public FootprintHeatmapConfig getConfig() {
        return config;
    }

    public void setConfig(FootprintHeatmapConfig config) {
        this.config = config != null ? config : new FootprintHeatmapConfig();
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    public void setEnabled(boolean enabled) {
        if (config == null) {
            config = new FootprintHeatmapConfig();
        }
        config.setEnabled(enabled);
        if (!enabled) {
            clear();
        }
    }

    public void setTimeframe(String timeframe) {
        this.currentTimeframe = timeframe;
    }

    // ===== Data Updates =====

    /**
     * Update with new candle and aggTrade data.
     * Recalculates footprints and redraws the heatmap.
     *
     * @param candles   List of candles
     * @param aggTrades List of aggregated trades
     */
    public void update(List<Candle> candles, List<AggTrade> aggTrades) {
        this.currentCandles = candles;
        this.currentAggTrades = aggTrades;
        this.footprintResult = null; // Invalidate cache

        if (!isEnabled()) {
            clear();
            return;
        }

        redraw();
    }

    /**
     * Redraw using currently available data.
     */
    public void redraw() {
        System.out.println("[FootprintHeatmap] redraw: enabled=" + isEnabled() +
            ", hasCandles=" + (currentCandles != null && !currentCandles.isEmpty()) +
            ", hasTrades=" + (currentAggTrades != null && !currentAggTrades.isEmpty()));

        clear();

        if (!isEnabled() || currentCandles == null || currentCandles.isEmpty()) {
            System.out.println("[FootprintHeatmap] redraw EARLY RETURN: not enabled or no candles");
            return;
        }

        // Calculate footprints if needed
        if (footprintResult == null) {
            System.out.println("[FootprintHeatmap] calculating footprints...");
            footprintResult = calculateFootprints();
        }

        if (footprintResult == null || footprintResult.footprints().isEmpty()) {
            System.out.println("[FootprintHeatmap] no footprints calculated");
            return;
        }

        System.out.println("[FootprintHeatmap] got " + footprintResult.footprints().size() + " footprints");

        // Create and add annotation
        XYPlot plot = priceChart.getXYPlot();
        annotation = new FootprintHeatmapAnnotation(footprintResult.footprints(), config);

        // Add as background annotation
        var existingAnnotations = new java.util.ArrayList<>(plot.getAnnotations());
        plot.clearAnnotations();
        plot.addAnnotation(annotation);
        for (var existing : existingAnnotations) {
            plot.addAnnotation(existing);
        }

        System.out.println("[FootprintHeatmap] added annotation to plot");

        if (onDataReady != null) {
            onDataReady.run();
        }
    }

    /**
     * Calculate footprints from current data.
     */
    private FootprintResult calculateFootprints() {
        if (currentCandles == null || currentCandles.isEmpty()) {
            return null;
        }

        // Determine tick size
        Double tickSize = null;
        if (config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.FIXED) {
            tickSize = config.getFixedTickSize();
        }

        // Determine exchange filter for single exchange mode
        Set<Exchange> exchangeFilter = null;
        if (config.getDisplayMode() == FootprintDisplayMode.SINGLE_EXCHANGE) {
            exchangeFilter = EnumSet.of(config.getSelectedExchange());
        }

        return FootprintIndicator.calculate(
            currentCandles,
            currentAggTrades,
            currentTimeframe,
            config.getTargetBuckets(),
            tickSize,
            exchangeFilter
        );
    }

    /**
     * Clear the annotation from the chart.
     */
    public void clear() {
        if (annotation == null) return;

        XYPlot plot = priceChart.getXYPlot();
        plot.removeAnnotation(annotation);
        annotation = null;
    }

    /**
     * Invalidate the cached footprint result (call when config changes).
     */
    public void invalidateCache() {
        footprintResult = null;
    }

    /**
     * Check if annotation is currently displayed.
     */
    public boolean hasAnnotation() {
        return annotation != null;
    }

    /**
     * Get current annotation.
     */
    public FootprintHeatmapAnnotation getAnnotation() {
        return annotation;
    }

    /**
     * Get the cached footprint result.
     */
    public FootprintResult getFootprintResult() {
        return footprintResult;
    }
}
