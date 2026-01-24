package com.tradery.ui.charts.heatmap;

import com.tradery.model.AggTrade;
import com.tradery.model.Candle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Overlay for volume heatmap on the price chart.
 * Draws colored boxes behind candles showing volume distribution at price levels.
 */
public class VolumeHeatmapOverlay {

    private static final Logger log = LoggerFactory.getLogger(VolumeHeatmapOverlay.class);

    private final JFreeChart priceChart;

    // Configuration
    private VolumeHeatmapConfig config;

    // Current data
    private List<Candle> currentCandles;
    private List<AggTrade> currentAggTrades;

    // Current annotation
    private VolumeHeatmapAnnotation annotation;

    // Callback for repaint
    private Runnable onDataReady;

    public VolumeHeatmapOverlay(JFreeChart priceChart) {
        this.priceChart = priceChart;
        this.config = new VolumeHeatmapConfig();
    }

    public void setOnDataReady(Runnable callback) {
        this.onDataReady = callback;
    }

    // ===== Configuration =====

    public VolumeHeatmapConfig getConfig() {
        return config;
    }

    public void setConfig(VolumeHeatmapConfig config) {
        this.config = config != null ? config : new VolumeHeatmapConfig();
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    public void setEnabled(boolean enabled) {
        if (config == null) {
            config = new VolumeHeatmapConfig();
        }
        config.setEnabled(enabled);
        if (!enabled) {
            clear();
        }
    }

    // ===== Data Updates =====

    /**
     * Update with new candle and aggTrade data.
     * Recalculates and redraws the heatmap.
     *
     * @param candles   List of candles
     * @param aggTrades List of aggregated trades (may be null)
     */
    public void update(List<Candle> candles, List<AggTrade> aggTrades) {
        this.currentCandles = candles;
        this.currentAggTrades = aggTrades;

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
        log.debug("VolumeHeatmapOverlay.redraw: enabled={}, hasCandles={}",
            isEnabled(), currentCandles != null && !currentCandles.isEmpty());

        clear();

        if (!isEnabled() || currentCandles == null || currentCandles.isEmpty()) {
            return;
        }

        // Calculate heatmaps
        List<VolumeHeatmapAnnotation.CandleHeatmap> heatmaps =
            VolumeHeatmapAnnotation.calculate(currentCandles, currentAggTrades, config);

        if (heatmaps.isEmpty()) {
            log.debug("VolumeHeatmapOverlay: no heatmaps calculated");
            return;
        }

        // Create and add annotation
        XYPlot plot = priceChart.getXYPlot();
        annotation = new VolumeHeatmapAnnotation(heatmaps, config);

        // Add as background (first in list)
        if (config.isBehindCandles()) {
            // Clear and re-add all annotations with heatmap first
            var existingAnnotations = new java.util.ArrayList<>(plot.getAnnotations());
            plot.clearAnnotations();
            plot.addAnnotation(annotation);
            for (var existing : existingAnnotations) {
                plot.addAnnotation(existing);
            }
        } else {
            plot.addAnnotation(annotation);
        }

        log.debug("VolumeHeatmapOverlay: added annotation with {} candle heatmaps", heatmaps.size());

        if (onDataReady != null) {
            onDataReady.run();
        }
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
     * Check if annotation is currently displayed.
     */
    public boolean hasAnnotation() {
        return annotation != null;
    }

    /**
     * Get current annotation.
     */
    public VolumeHeatmapAnnotation getAnnotation() {
        return annotation;
    }
}
