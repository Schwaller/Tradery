package com.tradery.charts.overlay.footprint;

import com.tradery.charts.overlay.FootprintProfileProvider;
import com.tradery.core.indicators.FootprintIndicator;
import com.tradery.core.model.*;
import com.tradery.ui.controls.indicators.FootprintHeatmapConfig;
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
 * Uses precomputed volume profiles from a {@link FootprintProfileProvider}.
 * Candles are passed via requestData() (same as rendered on chart) to ensure alignment.
 */
public class FootprintHeatmapOverlay {

    private static final Logger log = LoggerFactory.getLogger(FootprintHeatmapOverlay.class);

    private final JFreeChart priceChart;
    private final FootprintProfileProvider profileProvider;

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

    // Computed result
    private FootprintResult footprintResult;

    // Current annotation
    private FootprintHeatmapAnnotation annotation;

    // Callback for repaint
    private Runnable onDataReady;

    // Optional status listener (set by forge for UI status updates)
    private StatusListener statusListener;

    // Background computation
    private final ExecutorService computeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Footprint-Compute");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger computeGeneration = new AtomicInteger(0);

    /**
     * Callback for status changes (optional — used by forge for UI updates).
     */
    @FunctionalInterface
    public interface StatusListener {
        void onStatusChanged(String status, String detail, int count);
    }

    public FootprintHeatmapOverlay(JFreeChart priceChart, FootprintProfileProvider profileProvider) {
        this.priceChart = priceChart;
        this.profileProvider = profileProvider;
        this.config = new FootprintHeatmapConfig();
    }

    public void setOnDataReady(Runnable callback) {
        this.onDataReady = callback;
    }

    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
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
            updateStatus("IDLE", "Disabled", 0);
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
     * Request footprint computation for given candles.
     * IMPORTANT: Uses the passed candles directly (same as rendered on chart)
     * to ensure footprint buckets align with visible candles.
     */
    public void requestData(List<Candle> candles, String symbol, String timeframe,
                            long startTime, long endTime) {
        log.debug("FootprintHeatmapOverlay.requestData: candles={}, symbol={}, enabled={}, marketType={}",
            candles != null ? candles.size() : 0, symbol, isEnabled(), marketType);

        if (candles == null || candles.isEmpty()) {
            clear();
            return;
        }

        if (!isEnabled()) {
            log.debug("FootprintHeatmapOverlay: not enabled, skipping");
            clear();
            return;
        }

        // Store candles from ChartsPanel - these are the SAME candles rendered on the price chart
        this.currentCandles = candles;
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;
        this.currentStartTime = startTime;
        this.currentEndTime = endTime;

        computeFromProfiles();
    }

    /**
     * Compute footprint from precomputed volume profiles via the provider.
     * Runs on background thread.
     */
    private void computeFromProfiles() {
        if (profileProvider == null || !profileProvider.isAvailable()) {
            log.debug("Profile provider unavailable for footprint profiles");
            updateStatus("ERROR", "Data service unavailable", 0);
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
        updateStatus("LOADING",
            "Fetching profiles for " + candles.size() + " candles", 0);

        computeExecutor.submit(() -> {
            try {
                long start = candles.get(0).timestamp();
                long candleMs = estimateCandleIntervalMs(candles);
                // Extend end to cover profiles within the last candle's full window
                long end = candles.get(candles.size() - 1).timestamp() + candleMs - 1;

                var rawProfiles = profileProvider.getProfiles(symbol, timeframe, start, end, marketType);
                if (rawProfiles == null || rawProfiles.isEmpty()) {
                    log.debug("No profiles returned from provider");
                    if (computeGeneration.get() == generation) {
                        SwingUtilities.invokeLater(() ->
                            updateStatus("IDLE", "No profile data", 0));
                    }
                    return;
                }

                // Index profiles by window start in a TreeMap for range queries
                TreeMap<Long, FootprintProfileProvider.RawProfile> profileByTimestamp = new TreeMap<>();
                for (var p : rawProfiles) {
                    profileByTimestamp.put(p.windowStart(), p);
                }

                // Calculate candle interval for range-based profile matching
                long candleIntervalMs = estimateCandleIntervalMs(candles);

                // Calculate tick size
                double atr = calculateATR(candles, 14);
                double tickSize = fixedTickSize != null ? fixedTickSize
                    : FootprintIndicator.calculateTickSize(atr, targetBuckets);

                var resultBuilder = new FootprintResult.Builder()
                    .tickSize(tickSize)
                    .symbol(symbol)
                    .timeframe(timeframe);

                int totalProfilesMatched = 0;
                for (int i = 0; i < candles.size(); i++) {
                    Candle candle = candles.get(i);

                    // Find all profiles within this candle's time window
                    long candleStart = candle.timestamp();
                    long candleEnd = (i + 1 < candles.size())
                        ? candles.get(i + 1).timestamp()
                        : candleStart + candleIntervalMs;
                    var matchingProfiles = profileByTimestamp.subMap(candleStart, candleEnd).values();
                    totalProfilesMatched += matchingProfiles.size();
                    var profile = mergeProfiles(matchingProfiles);

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
                int matchedCount = totalProfilesMatched;

                if (computeGeneration.get() == generation) {
                    SwingUtilities.invokeLater(() -> {
                        if (computeGeneration.get() == generation) {
                            footprintResult = result;
                            redraw();
                            int fpCount = result.footprints().size();
                            updateStatus("READY",
                                fpCount + " footprints (profiles)", fpCount);
                            log.info("Footprint computed from profiles: {} candles, {} raw profiles, {} matched to candles",
                                candles.size(), rawProfiles.size(), matchedCount);
                            if (onDataReady != null) {
                                onDataReady.run();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Failed to compute footprint from profiles: {}", e.getMessage(), e);
                if (computeGeneration.get() == generation) {
                    SwingUtilities.invokeLater(() ->
                        updateStatus("ERROR", e.getMessage(), 0));
                }
            }
        });
    }

    /**
     * Estimate the candle interval in milliseconds from the first few candles.
     */
    static long estimateCandleIntervalMs(List<Candle> candles) {
        if (candles.size() < 2) return 900_000L; // 15m default
        long minInterval = Long.MAX_VALUE;
        for (int i = 1; i < Math.min(10, candles.size()); i++) {
            long diff = candles.get(i).timestamp() - candles.get(i - 1).timestamp();
            if (diff > 0) {
                minInterval = Math.min(minInterval, diff);
            }
        }
        return minInterval == Long.MAX_VALUE ? 900_000L : minInterval;
    }

    /**
     * Merge multiple raw profiles into one by summing volumes at each tick index.
     * Returns null if the collection is empty.
     */
    static FootprintProfileProvider.RawProfile mergeProfiles(
            Collection<FootprintProfileProvider.RawProfile> profiles) {
        if (profiles.isEmpty()) return null;
        if (profiles.size() == 1) return profiles.iterator().next();

        var iter = profiles.iterator();
        var first = iter.next();
        double tickSize = first.tickSize();
        double totalBuy = first.totalBuyVolume();
        double totalSell = first.totalSellVolume();
        Map<String, double[]> merged = new HashMap<>();
        if (first.levels() != null) {
            for (var e : first.levels().entrySet()) {
                merged.put(e.getKey(), new double[]{
                    e.getValue().length > 0 ? e.getValue()[0] : 0,
                    e.getValue().length > 1 ? e.getValue()[1] : 0
                });
            }
        }

        while (iter.hasNext()) {
            var p = iter.next();
            totalBuy += p.totalBuyVolume();
            totalSell += p.totalSellVolume();
            if (p.levels() != null) {
                for (var e : p.levels().entrySet()) {
                    double buy = e.getValue().length > 0 ? e.getValue()[0] : 0;
                    double sell = e.getValue().length > 1 ? e.getValue()[1] : 0;
                    double[] existing = merged.get(e.getKey());
                    if (existing == null) {
                        merged.put(e.getKey(), new double[]{buy, sell});
                    } else {
                        existing[0] += buy;
                        existing[1] += sell;
                    }
                }
            }
        }

        return new FootprintProfileProvider.RawProfile(
            first.windowStart(), tickSize, totalBuy, totalSell, merged);
    }

    /**
     * Build footprint buckets from a precomputed profile, or empty buckets if profile is null.
     * Accumulates volumes from multiple profile ticks that snap to the same footprint price level.
     */
    static void buildFootprintFromProfile(Footprint.Builder fpBuilder,
            FootprintProfileProvider.RawProfile profile, double tickSize, Candle candle) {
        if (profile != null && profile.levels() != null && !profile.levels().isEmpty()) {
            // Accumulate buy/sell volumes at each snapped price level
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
                bucketBuilder.addBuyVolume(vols[0]);
                bucketBuilder.addSellVolume(vols[1]);
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
     * Calculate ATR for tick size computation.
     */
    static double calculateATR(List<Candle> candles, int period) {
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

    // ===== Drawing =====

    /**
     * Redraw using computed footprint result.
     */
    public void redraw() {
        log.debug("FootprintHeatmapOverlay.redraw: enabled={}, hasResult={}, fpCount={}",
            isEnabled(), footprintResult != null,
            footprintResult != null ? footprintResult.footprints().size() : 0);

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
     * Compute footprint from pre-fetched profiles (pushed by page system).
     * Skips the provider HTTP call — uses profiles already received via WebSocket.
     */
    public void computeWithProfiles(List<FootprintProfileProvider.RawProfile> rawProfiles) {
        if (currentCandles == null || currentCandles.isEmpty()) return;
        if (rawProfiles == null || rawProfiles.isEmpty()) return;

        List<Candle> candles = List.copyOf(currentCandles);
        String symbol = currentSymbol;
        String timeframe = currentTimeframe;
        int targetBuckets = config.getTargetBuckets();
        Double fixedTickSize = config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.FIXED
            ? config.getFixedTickSize() : null;
        boolean perCandle = config.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.PER_CANDLE;

        int generation = computeGeneration.incrementAndGet();

        computeExecutor.submit(() -> {
            try {
                TreeMap<Long, FootprintProfileProvider.RawProfile> profileByTimestamp = new TreeMap<>();
                for (var p : rawProfiles) {
                    profileByTimestamp.put(p.windowStart(), p);
                }

                long candleIntervalMs = estimateCandleIntervalMs(candles);
                double atr = calculateATR(candles, 14);
                double tickSize = fixedTickSize != null ? fixedTickSize
                    : FootprintIndicator.calculateTickSize(atr, targetBuckets);

                var resultBuilder = new FootprintResult.Builder()
                    .tickSize(tickSize)
                    .symbol(symbol)
                    .timeframe(timeframe);

                int totalProfilesMatched = 0;
                for (int i = 0; i < candles.size(); i++) {
                    Candle candle = candles.get(i);
                    long candleStart = candle.timestamp();
                    long candleEnd = (i + 1 < candles.size())
                        ? candles.get(i + 1).timestamp()
                        : candleStart + candleIntervalMs;
                    var matchingProfiles = profileByTimestamp.subMap(candleStart, candleEnd).values();
                    totalProfilesMatched += matchingProfiles.size();
                    var profile = mergeProfiles(matchingProfiles);

                    Footprint.Builder fpBuilder;
                    if (perCandle) {
                        double candleTickSize = FootprintIndicator.calculateTickSize(
                            candle.high() - candle.low(), targetBuckets);
                        fpBuilder = new Footprint.Builder()
                            .timestamp(candle.timestamp()).barIndex(i)
                            .high(candle.high()).low(candle.low()).tickSize(candleTickSize);
                        buildFootprintFromProfile(fpBuilder, profile, candleTickSize, candle);
                    } else {
                        fpBuilder = new Footprint.Builder()
                            .timestamp(candle.timestamp()).barIndex(i)
                            .high(candle.high()).low(candle.low()).tickSize(tickSize);
                        buildFootprintFromProfile(fpBuilder, profile, tickSize, candle);
                    }
                    resultBuilder.addFootprint(fpBuilder.build());
                }

                FootprintResult result = resultBuilder.build();
                int matchedCount = totalProfilesMatched;

                if (computeGeneration.get() == generation) {
                    SwingUtilities.invokeLater(() -> {
                        if (computeGeneration.get() == generation) {
                            footprintResult = result;
                            redraw();
                            log.debug("Footprint updated from live profiles: {} candles, {} matched",
                                candles.size(), matchedCount);
                            if (onDataReady != null) {
                                onDataReady.run();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Failed to compute footprint from live profiles: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Invalidate the cached footprint result (call when config changes).
     */
    public void invalidateCache() {
        footprintResult = null;
        if (currentCandles == null || currentCandles.isEmpty() || !isEnabled()) return;
        computeFromProfiles();
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

    private void updateStatus(String status, String detail, int count) {
        if (statusListener != null) {
            statusListener.onStatusChanged(status, detail, count);
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
