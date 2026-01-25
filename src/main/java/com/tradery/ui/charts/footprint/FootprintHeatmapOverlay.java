package com.tradery.ui.charts.footprint;

import com.tradery.ApplicationContext;
import com.tradery.data.PageState;
import com.tradery.data.page.IndicatorPage;
import com.tradery.data.page.IndicatorPageListener;
import com.tradery.data.page.IndicatorPageManager;
import com.tradery.data.page.IndicatorType;
import com.tradery.model.Candle;
import com.tradery.model.FootprintResult;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Overlay for footprint heatmap on the price chart.
 * Draws colored buckets showing buy/sell volume distribution at price levels.
 * Uses IndicatorPageManager for background computation - never blocks EDT.
 */
public class FootprintHeatmapOverlay {

    private static final Logger log = LoggerFactory.getLogger(FootprintHeatmapOverlay.class);

    private final JFreeChart priceChart;

    // Configuration
    private FootprintHeatmapConfig config;
    private boolean enabled;  // Local enabled state - NOT shared with ChartConfig

    // Current data context
    private String currentSymbol;
    private String currentTimeframe;
    private long currentStartTime;
    private long currentEndTime;

    // Indicator page (background computed)
    private IndicatorPage<FootprintResult> footprintPage;
    private final FootprintPageListener pageListener = new FootprintPageListener();

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
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
            releasePage();
        }
    }

    public void setTimeframe(String timeframe) {
        this.currentTimeframe = timeframe;
    }

    // ===== Data Request =====

    /**
     * Request footprint computation for given candles.
     * Non-blocking - footprints computed in background.
     */
    public void requestData(List<Candle> candles, String symbol, String timeframe,
                            long startTime, long endTime) {
        log.debug("FootprintHeatmapOverlay.requestData: candles={}, symbol={}, enabled={}",
            candles != null ? candles.size() : 0, symbol, isEnabled());

        if (candles == null || candles.isEmpty()) {
            releasePage();
            clear();
            return;
        }

        if (!isEnabled()) {
            log.debug("FootprintHeatmapOverlay: not enabled, skipping");
            releasePage();
            clear();
            return;
        }

        IndicatorPageManager pageMgr = ApplicationContext.getInstance().getIndicatorPageManager();
        if (pageMgr == null) {
            log.warn("IndicatorPageManager not available");
            return;
        }

        // Build params string: buckets:tickSize:displayMode:selectedExchange
        String params = buildParams();

        // Request page - PageManager handles caching, returns same page if key matches
        IndicatorPage<FootprintResult> newPage = pageMgr.request(
            IndicatorType.FOOTPRINT_HEATMAP, params,
            symbol, timeframe, startTime, endTime,
            pageListener,
            "FootprintHeatmapOverlay");

        // Only release old page if we got a different one (different cache key)
        if (footprintPage != null && footprintPage != newPage) {
            log.debug("FootprintHeatmapOverlay: switching to new page (params or time changed)");
            pageMgr.release(footprintPage, pageListener);
        } else if (footprintPage == newPage) {
            log.debug("FootprintHeatmapOverlay: reusing cached page");
        }

        footprintPage = newPage;
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;
        this.currentStartTime = startTime;
        this.currentEndTime = endTime;

        log.debug("Requested footprint heatmap: {} {} {}-{}", symbol, timeframe, startTime, endTime);
    }

    /**
     * Build params string from current config.
     */
    private String buildParams() {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getTargetBuckets());
        sb.append(":");
        if (config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.FIXED) {
            sb.append(config.getFixedTickSize());
        }
        sb.append(":");
        sb.append(config.getDisplayMode().name());
        sb.append(":");
        if (config.getDisplayMode() == FootprintDisplayMode.SINGLE_EXCHANGE) {
            sb.append(config.getSelectedExchange().name());
        }
        return sb.toString();
    }

    /**
     * Release indicator page when no longer needed.
     */
    public void releasePage() {
        if (footprintPage == null) return;

        IndicatorPageManager pageMgr = ApplicationContext.getInstance().getIndicatorPageManager();
        if (pageMgr != null) {
            pageMgr.release(footprintPage, pageListener);
        }
        footprintPage = null;
    }

    // ===== Drawing =====

    /**
     * Redraw using currently available data.
     */
    public void redraw() {
        log.debug("FootprintHeatmapOverlay.redraw: enabled={}, hasPage={}, hasData={}",
            isEnabled(), footprintPage != null, footprintPage != null && footprintPage.hasData());

        clear();

        if (!isEnabled() || footprintPage == null || !footprintPage.hasData()) {
            return;
        }

        FootprintResult result = footprintPage.getData();
        if (result == null || result.footprints().isEmpty()) {
            log.debug("FootprintHeatmapOverlay.redraw: footprints empty");
            return;
        }

        // Create and add annotation
        XYPlot plot = priceChart.getXYPlot();
        annotation = new FootprintHeatmapAnnotation(result.footprints(), config);

        // Add as background annotation (before other annotations)
        var existingAnnotations = new java.util.ArrayList<>(plot.getAnnotations());
        plot.clearAnnotations();
        plot.addAnnotation(annotation);
        for (var existing : existingAnnotations) {
            plot.addAnnotation(existing);
        }

        log.debug("FootprintHeatmapOverlay.redraw: ADDED annotation with {} footprints", result.footprints().size());
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
     * This releases the current page so a new one will be requested.
     */
    public void invalidateCache() {
        releasePage();
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
        return footprintPage != null ? footprintPage.getData() : null;
    }

    // ===== Listener =====

    private class FootprintPageListener implements IndicatorPageListener<FootprintResult> {
        @Override
        public void onStateChanged(IndicatorPage<FootprintResult> page,
                                   PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                redraw();
                if (onDataReady != null) {
                    onDataReady.run();
                }
            }
        }

        @Override
        public void onDataChanged(IndicatorPage<FootprintResult> page) {
            redraw();
            if (onDataReady != null) {
                onDataReady.run();
            }
        }
    }

    // ===== Legacy API (for compatibility) =====

    /**
     * Update with candles and aggTrades.
     * @deprecated Use requestData() + redraw() pattern instead
     */
    @Deprecated
    public void update(List<Candle> candles, @SuppressWarnings("unused") Object aggTrades) {
        if (candles == null || candles.isEmpty()) {
            clear();
            return;
        }
        requestData(candles, currentSymbol, currentTimeframe,
            candles.get(0).timestamp(),
            candles.get(candles.size() - 1).timestamp());
    }
}
