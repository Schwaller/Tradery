package com.tradery.forge.ui.charts.footprint;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.PageState;
import com.tradery.forge.data.page.AggTradesPageManager;
import com.tradery.forge.data.page.DataPageListener;
import com.tradery.forge.data.page.DataPageView;
import com.tradery.core.indicators.FootprintIndicator;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
import com.tradery.core.model.Exchange;
import com.tradery.core.model.FootprintResult;
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
 *
 * Uses the SAME candles as the price chart (passed via requestData) to ensure
 * footprint buckets align with rendered candles. Only aggTrades are loaded
 * from the page manager.
 */
public class FootprintHeatmapOverlay {

    private static final Logger log = LoggerFactory.getLogger(FootprintHeatmapOverlay.class);

    private final JFreeChart priceChart;

    // Configuration
    private FootprintHeatmapConfig config;
    private boolean enabled;

    // Current data context - candles from ChartsPanel (same as rendered)
    private List<Candle> currentCandles;
    private String currentSymbol;
    private String currentTimeframe;
    private long currentStartTime;
    private long currentEndTime;

    // AggTrades page (only need aggTrades, candles come from ChartsPanel)
    private DataPageView<AggTrade> aggTradesPage;
    private final AggTradesListener aggTradesListener = new AggTradesListener();

    // Computed result
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
     * IMPORTANT: Uses the passed candles directly (same as rendered on chart)
     * to ensure footprint buckets align with visible candles.
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

        // Store candles from ChartsPanel - these are the SAME candles rendered on the price chart
        this.currentCandles = candles;
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;
        this.currentStartTime = startTime;
        this.currentEndTime = endTime;

        // Request aggTrades only (we use the passed candles, not loaded ones)
        AggTradesPageManager aggTradesMgr = ApplicationContext.getInstance().getAggTradesPageManager();
        if (aggTradesMgr == null) {
            log.warn("AggTradesPageManager not available");
            return;
        }

        // Check if we need a new aggTrades page
        boolean needNewPage = aggTradesPage == null ||
            !symbol.equals(aggTradesPage.getSymbol()) ||
            !timeframe.equals(aggTradesPage.getTimeframe()) ||
            startTime != aggTradesPage.getStartTime() ||
            endTime != aggTradesPage.getEndTime();

        if (needNewPage) {
            // Release old page
            if (aggTradesPage != null) {
                aggTradesMgr.release(aggTradesPage, aggTradesListener);
            }

            // Request new aggTrades page
            aggTradesPage = aggTradesMgr.request(symbol, null, startTime, endTime, aggTradesListener, "FootprintHeatmap");
            log.debug("Requested new aggTrades page for footprint: {} {} {}-{}", symbol, timeframe, startTime, endTime);
        } else if (aggTradesPage.isReady()) {
            // Same page, already ready - recompute with new candles
            computeAndRedraw();
        }
    }

    /**
     * Compute footprint using stored candles and aggTrades from page.
     */
    private void computeAndRedraw() {
        if (currentCandles == null || currentCandles.isEmpty()) {
            log.debug("computeAndRedraw: no candles");
            return;
        }

        List<AggTrade> aggTrades = aggTradesPage != null ? aggTradesPage.getData() : null;

        // Compute footprint using ChartsPanel candles (ensures alignment)
        int buckets = config.getTargetBuckets();
        Double tickSize = config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.FIXED
            ? config.getFixedTickSize() : null;

        Set<Exchange> exchangeFilter = null;
        if (config.getDisplayMode() == FootprintDisplayMode.SINGLE_EXCHANGE) {
            exchangeFilter = EnumSet.of(config.getSelectedExchange());
        }

        log.debug("Computing footprint: candles={}, aggTrades={}, buckets={}, tickSize={}",
            currentCandles.size(), aggTrades != null ? aggTrades.size() : 0, buckets, tickSize);

        // Log first candle for debugging
        if (!currentCandles.isEmpty()) {
            Candle first = currentCandles.get(0);
            log.debug("First candle: ts={}, high={}, low={}, close={}",
                first.timestamp(), first.high(), first.low(), first.close());
        }

        footprintResult = FootprintIndicator.calculate(
            currentCandles, aggTrades, currentTimeframe, buckets, tickSize, exchangeFilter);

        redraw();
    }

    /**
     * Release aggTrades page when no longer needed.
     */
    public void releasePage() {
        if (aggTradesPage == null) return;

        AggTradesPageManager aggTradesMgr = ApplicationContext.getInstance().getAggTradesPageManager();
        if (aggTradesMgr != null) {
            aggTradesMgr.release(aggTradesPage, aggTradesListener);
        }
        aggTradesPage = null;
        footprintResult = null;
    }

    // ===== Drawing =====

    /**
     * Redraw using computed footprint result.
     */
    public void redraw() {
        log.debug("FootprintHeatmapOverlay.redraw: enabled={}, hasResult={}",
            isEnabled(), footprintResult != null);

        clear();

        if (!isEnabled() || footprintResult == null || footprintResult.footprints().isEmpty()) {
            return;
        }

        // Create and add annotation
        XYPlot plot = priceChart.getXYPlot();
        annotation = new FootprintHeatmapAnnotation(footprintResult.footprints(), config);

        // Add as background annotation (before other annotations)
        var existingAnnotations = new java.util.ArrayList<>(plot.getAnnotations());
        plot.clearAnnotations();
        plot.addAnnotation(annotation);
        for (var existing : existingAnnotations) {
            plot.addAnnotation(existing);
        }

        log.debug("FootprintHeatmapOverlay.redraw: ADDED annotation with {} footprints",
            footprintResult.footprints().size());
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
        if (aggTradesPage != null && aggTradesPage.isReady() && currentCandles != null) {
            computeAndRedraw();
        }
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

    // ===== AggTrades Listener =====

    private class AggTradesListener implements DataPageListener<AggTrade> {
        @Override
        public void onStateChanged(DataPageView<AggTrade> page, PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                computeAndRedraw();
                if (onDataReady != null) {
                    onDataReady.run();
                }
            }
        }

        @Override
        public void onDataChanged(DataPageView<AggTrade> page) {
            computeAndRedraw();
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
