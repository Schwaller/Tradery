package com.tradery.forge.ui.charts.footprint;

import com.tradery.core.indicators.FootprintIndicator;
import com.tradery.core.model.*;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import com.tradery.data.page.PageState;
import com.tradery.forge.data.page.AggTradesPageManager;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

    // Market type for profile-based computation
    private String marketType = "perp";

    // AggTrades page (only need aggTrades, candles come from ChartsPanel)
    private DataPageView<AggTrade> aggTradesPage;
    private final AggTradesListener aggTradesListener = new AggTradesListener();

    // External aggTrades (from BacktestCoordinator, avoids duplicate page load)
    private List<AggTrade> externalAggTrades;

    // When true, the overlay waits for setAggTrades() instead of requesting its own page
    private boolean waitForExternalAggTrades;

    // Computed result
    private FootprintResult footprintResult;

    // Current annotation
    private FootprintHeatmapAnnotation annotation;

    // Callback for repaint
    private Runnable onDataReady;

    // Background computation
    private final ExecutorService computeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Footprint-Compute");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger computeGeneration = new AtomicInteger(0);

    public FootprintHeatmapOverlay(JFreeChart priceChart) {
        this.priceChart = priceChart;
        this.config = new FootprintHeatmapConfig();
    }

    public void setOnDataReady(Runnable callback) {
        this.onDataReady = callback;
    }

    /**
     * Check if a display mode requires raw aggTrades (per-exchange breakdown).
     * COMBINED and SPLIT can use precomputed volume profiles.
     * SINGLE_EXCHANGE, STACKED, and DIVERGENCE need raw aggTrades for per-exchange data.
     */
    public static boolean requiresAggTrades(FootprintDisplayMode mode) {
        return switch (mode) {
            case COMBINED, SPLIT -> false;
            case SINGLE_EXCHANGE, STACKED, DIVERGENCE -> true;
        };
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
            computeGeneration.incrementAndGet(); // cancel any in-flight compute
            clear();
            releasePage();
            externalAggTrades = null;
            updateStatus(ApplicationContext.ProfileStatus.IDLE, "Disabled", 0);
        }
    }

    public void setTimeframe(String timeframe) {
        this.currentTimeframe = timeframe;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType != null ? marketType : "perp";
    }

    // ===== Data Request =====

    /**
     * Set aggTrades from an external source (e.g. BacktestCoordinator) to avoid
     * loading a duplicate page. When set, requestData() uses these directly
     * instead of requesting its own aggTrades page from the page manager.
     */
    public void setAggTrades(List<AggTrade> aggTrades) {
        this.externalAggTrades = aggTrades;
        // Coordinator delivered — stop waiting
        this.waitForExternalAggTrades = false;
        // Release any existing page since we no longer need it
        releasePage();
        // If candles are already available (requestData was called while waiting), compute now
        if (isEnabled() && currentCandles != null && !currentCandles.isEmpty()
                && aggTrades != null && !aggTrades.isEmpty()) {
            computeAndRedraw();
        }
    }

    /**
     * Tell the overlay to wait for setAggTrades() instead of requesting its own page.
     * Call this when a BacktestCoordinator is loading aggTrades for the same data range.
     */
    public void setWaitForExternalAggTrades(boolean wait) {
        this.waitForExternalAggTrades = wait;
    }

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

        // Route based on display mode:
        // Profile path: COMBINED/SPLIT (profiles have total buy/sell)
        // AggTrades path: exchange-specific modes (SINGLE_EXCHANGE, STACKED, DIVERGENCE)
        if (!requiresAggTrades(config.getDisplayMode())) {
            // Profile path: release any aggTrades page we no longer need
            releasePage();
            computeFromProfiles();
            return;
        }

        // AggTrades path: if we have aggTrades from BacktestCoordinator, use them directly
        if (externalAggTrades != null && !externalAggTrades.isEmpty()) {
            log.debug("Using external aggTrades ({}) for footprint, skipping page request", externalAggTrades.size());
            computeAndRedraw();
            return;
        }

        // If coordinator is loading aggTrades, wait for setAggTrades() callback
        if (waitForExternalAggTrades) {
            log.debug("Waiting for external aggTrades from BacktestCoordinator, skipping page request");
            return;
        }

        // Otherwise fall back to requesting our own aggTrades page
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
     * Compute footprint from precomputed volume profiles in the data service.
     * Runs on background thread. Falls back to aggTrades path if profiles are unavailable.
     */
    private void computeFromProfiles() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            log.debug("Data service unavailable, falling back to aggTrades for footprint");
            requestAggTradesPage();
            return;
        }

        List<Candle> candles = List.copyOf(currentCandles);
        String symbol = currentSymbol;
        String timeframe = currentTimeframe;
        int targetBuckets = config.getTargetBuckets();
        Double fixedTickSize = config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.FIXED
            ? config.getFixedTickSize() : null;
        boolean perCandle = config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.PER_CANDLE;

        int generation = computeGeneration.incrementAndGet();
        updateStatus(ApplicationContext.ProfileStatus.LOADING,
            "Fetching profiles for " + candles.size() + " candles", 0);

        DataServiceClient client = ctx.getDataServiceClient();

        computeExecutor.submit(() -> {
            try {
                long start = candles.get(0).timestamp();
                long end = candles.get(candles.size() - 1).timestamp();

                var rawProfiles = client.getProfiles(symbol, timeframe, start, end, marketType);
                if (rawProfiles == null || rawProfiles.isEmpty()) {
                    log.debug("No profiles returned from data service, falling back to aggTrades");
                    if (computeGeneration.get() == generation) {
                        SwingUtilities.invokeLater(this::requestAggTradesPage);
                    }
                    return;
                }

                // Index profiles by window start timestamp
                Map<Long, DataServiceClient.RawProfileResponse> profileByTimestamp = new HashMap<>();
                for (var p : rawProfiles) {
                    profileByTimestamp.put(p.windowStart(), p);
                }

                // Calculate tick size
                double atr = calculateATR(candles, 14);
                double tickSize = fixedTickSize != null ? fixedTickSize
                    : FootprintIndicator.calculateTickSize(atr, targetBuckets);

                var resultBuilder = new FootprintResult.Builder()
                    .tickSize(tickSize)
                    .symbol(symbol)
                    .timeframe(timeframe);

                for (int i = 0; i < candles.size(); i++) {
                    Candle candle = candles.get(i);
                    var profile = profileByTimestamp.get(candle.timestamp());

                    Footprint.Builder fpBuilder;
                    if (perCandle) {
                        double candleTickSize = FootprintIndicator.calculateTickSize(
                            candle.high() - candle.low(), targetBuckets);
                        fpBuilder = new Footprint.Builder()
                            .timestamp(candle.timestamp())
                            .barIndex(i)
                            .high(candle.high())
                            .low(candle.low())
                            .tickSize(candleTickSize);
                        buildFootprintFromProfile(fpBuilder, profile, candleTickSize, candle);
                    } else {
                        fpBuilder = new Footprint.Builder()
                            .timestamp(candle.timestamp())
                            .barIndex(i)
                            .high(candle.high())
                            .low(candle.low())
                            .tickSize(tickSize);
                        buildFootprintFromProfile(fpBuilder, profile, tickSize, candle);
                    }

                    resultBuilder.addFootprint(fpBuilder.build());
                }

                FootprintResult result = resultBuilder.build();

                if (computeGeneration.get() == generation) {
                    SwingUtilities.invokeLater(() -> {
                        if (computeGeneration.get() == generation) {
                            footprintResult = result;
                            redraw();
                            int fpCount = result.footprints().size();
                            updateStatus(ApplicationContext.ProfileStatus.READY,
                                fpCount + " footprints (profiles)", fpCount);
                            log.info("Footprint computed from precomputed profiles: {} candles, {} profiles matched",
                                candles.size(), rawProfiles.size());
                            if (onDataReady != null) {
                                onDataReady.run();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("Failed to fetch footprint from data service, falling back to aggTrades: {}", e.getMessage());
                if (computeGeneration.get() == generation) {
                    SwingUtilities.invokeLater(this::requestAggTradesPage);
                }
            }
        });
    }

    /**
     * Build footprint buckets from a precomputed profile, or empty buckets if profile is null.
     * Accumulates volumes from multiple profile ticks that snap to the same footprint price level.
     */
    private static void buildFootprintFromProfile(Footprint.Builder fpBuilder,
            DataServiceClient.RawProfileResponse profile, double tickSize, Candle candle) {
        if (profile != null && profile.levels() != null && !profile.levels().isEmpty()) {
            // Accumulate buy/sell volumes at each snapped price level
            // (multiple fine-grained profile ticks may map to the same footprint bucket)
            TreeMap<Long, double[]> accumulated = new TreeMap<>();
            double profileTickSize = profile.tickSize();

            for (var entry : profile.levels().entrySet()) {
                int tickIndex = Integer.parseInt(entry.getKey());
                double[] buySell = entry.getValue();
                double priceLevel = tickIndex * profileTickSize;
                double buyVol = buySell.length > 0 ? buySell[0] : 0;
                double sellVol = buySell.length > 1 ? buySell[1] : 0;

                long snappedKey = Math.round(priceLevel / tickSize);
                double[] acc = accumulated.computeIfAbsent(snappedKey, k -> new double[2]);
                acc[0] += buyVol;
                acc[1] += sellVol;
            }

            for (var entry : accumulated.entrySet()) {
                double snappedPrice = entry.getKey() * tickSize;
                double[] vols = entry.getValue();
                var bucketBuilder = new FootprintBucket.Builder(snappedPrice);
                bucketBuilder.addBuyVolume(Exchange.BINANCE, vols[0]);
                bucketBuilder.addSellVolume(Exchange.BINANCE, vols[1]);
                fpBuilder.addBucket(bucketBuilder.build());
            }
        } else {
            // Empty footprint covering candle range
            double snapLow = Math.round(candle.low() / tickSize) * tickSize;
            double snapHigh = Math.round(candle.high() / tickSize) * tickSize;
            for (double price = snapLow; price <= snapHigh + tickSize / 2; price += tickSize) {
                fpBuilder.addBucket(FootprintBucket.empty(price));
            }
        }
    }

    /**
     * Request aggTrades page as fallback when profiles are unavailable.
     * Must be called on EDT.
     */
    private void requestAggTradesPage() {
        if (currentCandles == null || currentCandles.isEmpty() || !isEnabled()) return;

        AggTradesPageManager aggTradesMgr = ApplicationContext.getInstance().getAggTradesPageManager();
        if (aggTradesMgr == null) {
            log.warn("AggTradesPageManager not available for fallback");
            return;
        }

        boolean needNewPage = aggTradesPage == null ||
            !currentSymbol.equals(aggTradesPage.getSymbol()) ||
            !currentTimeframe.equals(aggTradesPage.getTimeframe()) ||
            currentStartTime != aggTradesPage.getStartTime() ||
            currentEndTime != aggTradesPage.getEndTime();

        if (needNewPage) {
            if (aggTradesPage != null) {
                aggTradesMgr.release(aggTradesPage, aggTradesListener);
            }
            aggTradesPage = aggTradesMgr.request(currentSymbol, null, currentStartTime, currentEndTime,
                aggTradesListener, "FootprintHeatmap");
            log.debug("Requested aggTrades page as profile fallback: {} {}-{}", currentSymbol, currentStartTime, currentEndTime);
        } else if (aggTradesPage.isReady()) {
            computeAndRedraw();
        }
    }

    /**
     * Calculate ATR for tick size computation.
     */
    private static double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < 2) {
            Candle last = candles.get(candles.size() - 1);
            return last.high() - last.low();
        }
        double sum = 0;
        int count = 0;
        int start = Math.max(1, candles.size() - period);
        for (int i = start; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle prev = candles.get(i - 1);
            double tr = Math.max(current.high() - current.low(),
                Math.max(Math.abs(current.high() - prev.close()), Math.abs(current.low() - prev.close())));
            sum += tr;
            count++;
        }
        return count > 0 ? sum / count : 1.0;
    }

    /**
     * Compute footprint using stored candles and aggTrades from page.
     * Heavy calculation runs on a background thread to avoid blocking the EDT.
     */
    private void computeAndRedraw() {
        if (currentCandles == null || currentCandles.isEmpty()) {
            log.debug("computeAndRedraw: no candles");
            return;
        }

        // Snapshot inputs for the background thread
        List<Candle> candles = List.copyOf(currentCandles);
        List<AggTrade> aggTrades = externalAggTrades != null ? externalAggTrades
            : (aggTradesPage != null ? aggTradesPage.getData() : null);
        String timeframe = currentTimeframe;

        int buckets = config.getTargetBuckets();
        boolean perCandle = config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.PER_CANDLE;
        Double tickSize = config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.FIXED
            ? config.getFixedTickSize() : null;

        Set<Exchange> exchangeFilter = null;
        if (config.getDisplayMode() == FootprintDisplayMode.SINGLE_EXCHANGE) {
            exchangeFilter = EnumSet.of(config.getSelectedExchange());
        }

        log.debug("Computing footprint: candles={}, aggTrades={}, buckets={}, tickSize={}, perCandle={}",
            candles.size(), aggTrades != null ? aggTrades.size() : 0, buckets, tickSize, perCandle);

        // Bump generation so stale results are discarded
        int generation = computeGeneration.incrementAndGet();

        updateStatus(ApplicationContext.ProfileStatus.LOADING,
            "Computing " + candles.size() + " candles", 0);

        Set<Exchange> finalExchangeFilter = exchangeFilter;
        computeExecutor.submit(() -> {
            try {
                FootprintResult result = FootprintIndicator.calculate(
                    candles, aggTrades, timeframe, buckets, tickSize, finalExchangeFilter, perCandle);

                // Only apply if this is still the latest request
                if (computeGeneration.get() == generation) {
                    SwingUtilities.invokeLater(() -> {
                        if (computeGeneration.get() == generation) {
                            footprintResult = result;
                            redraw();
                            int fpCount = result != null ? result.footprints().size() : 0;
                            updateStatus(ApplicationContext.ProfileStatus.READY,
                                fpCount + " footprints", fpCount);
                            if (onDataReady != null) {
                                onDataReady.run();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Footprint computation failed: {}", e.getMessage(), e);
                updateStatus(ApplicationContext.ProfileStatus.ERROR, e.getMessage(), 0);
            }
        });
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
        if (currentCandles == null || currentCandles.isEmpty() || !isEnabled()) return;

        if (!requiresAggTrades(config.getDisplayMode())) {
            // Profile path: re-fetch from data service
            releasePage();
            computeFromProfiles();
        } else if (aggTradesPage != null && aggTradesPage.isReady()) {
            computeAndRedraw();
        } else if (externalAggTrades != null && !externalAggTrades.isEmpty()) {
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

    // ===== Status Reporting =====

    private void updateStatus(ApplicationContext.ProfileStatus status, String detail, int count) {
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx != null) {
            ctx.setFootprintStatus(status, detail, count);
        }
    }

    // ===== AggTrades Listener =====

    private class AggTradesListener implements DataPageListener<AggTrade> {
        @Override
        public void onStateChanged(DataPageView<AggTrade> page, PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                computeAndRedraw();
            }
        }

        @Override
        public void onDataChanged(DataPageView<AggTrade> page) {
            computeAndRedraw();
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
