package com.tradery.forge.data.page;

import com.tradery.core.indicators.RotatingRays;
import com.tradery.core.indicators.RotatingRays.RaySet;
import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.indicators.registry.IndicatorRegistry;
import com.tradery.core.indicators.registry.IndicatorSpec;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
import com.tradery.data.page.PageState;
import com.tradery.forge.data.log.DownloadLogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Manager for computed indicator pages.
 *
 * Indicators depend on data pages (candles, aggTrades) and are
 * recomputed when source data changes.
 *
 * Star architecture: IndicatorPage owns its source data acquisition.
 * This manager provides:
 * - Page registry and deduplication
 * - Reference counting
 * - Computation executors
 * - Listener management
 */
public class IndicatorPageManager {

    private static final Logger log = LoggerFactory.getLogger(IndicatorPageManager.class);

    // Source data managers
    private final CandlePageManager candlePageMgr;
    private final AggTradesPageManager aggTradesPageMgr;

    // Active indicator pages
    private final Map<String, IndicatorPage<?>> pages = new ConcurrentHashMap<>();
    private final Map<String, Set<IndicatorPageListener<?>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Integer> anonymousRefs = new ConcurrentHashMap<>();
    private final Map<IndicatorPageListener<?>, String> consumerNames = new ConcurrentHashMap<>();

    // Deferred cleanup
    private static final long CLEANUP_DELAY_MS = 5000;
    private final Map<String, ScheduledFuture<?>> pendingCleanups = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler;

    // Background computation
    private final ExecutorService computeExecutor;
    private final ExecutorService aggTradesExecutor;  // Dedicated for aggTrades-based indicators

    public IndicatorPageManager(CandlePageManager candlePageMgr,
                                 AggTradesPageManager aggTradesPageMgr) {
        this.candlePageMgr = candlePageMgr;
        this.aggTradesPageMgr = aggTradesPageMgr;

        // Thread pool size: min 3, max 1/3 of CPUs
        int cpus = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(3, cpus / 3);
        log.info("Indicator compute pool size: {} (CPUs: {})", poolSize, cpus);

        // Candle-based indicators (HISTORIC_RAYS can take minutes)
        this.computeExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "IndicatorCompute");
            t.setDaemon(true);
            return t;
        });

        // AggTrades-based indicators (separate so HISTORIC_RAYS doesn't block Delta/CVD)
        this.aggTradesExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "AggTradesCompute");
            t.setDaemon(true);
            return t;
        });

        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IndicatorPageCleanup");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Request an indicator page. Returns immediately (never blocks).
     *
     * @param type         Indicator type (RSI, SMA, etc.)
     * @param params       Indicator parameters (e.g., "14" for RSI(14))
     * @param symbol       Trading symbol
     * @param timeframe    Timeframe
     * @param startTime    Start time
     * @param endTime      End time
     * @param listener     Listener for updates (can be null)
     * @param consumerName Name of the consumer (for debugging/status display)
     * @return The indicator page
     */
    @SuppressWarnings("unchecked")
    public <T> IndicatorPage<T> request(IndicatorType type, String params,
                                         String symbol, String timeframe,
                                         long startTime, long endTime,
                                         IndicatorPageListener<T> listener,
                                         String consumerName) {

        String key = makeKey(type, params, symbol, timeframe, startTime, endTime);

        // Cancel any pending deferred cleanup for this key
        ScheduledFuture<?> pendingCleanup = pendingCleanups.remove(key);
        if (pendingCleanup != null) {
            pendingCleanup.cancel(false);
            log.debug("Cancelled deferred cleanup for indicator page: {}", key);
        }

        // Get or create page
        IndicatorPage<T> page = (IndicatorPage<T>) pages.computeIfAbsent(key, k ->
            new IndicatorPage<>(type, params, symbol, timeframe, startTime, endTime));

        // Register listener with name, or track anonymous ref
        if (listener != null) {
            listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);
            consumerNames.put(listener, consumerName);
            DownloadLogStore.getInstance().logListenerAdded(key, null, consumerName);
        } else {
            anonymousRefs.merge(key, 1, Integer::sum);
        }

        // Request source data if this is a new page
        if (page.getState() == PageState.EMPTY) {
            DownloadLogStore.getInstance().logPageCreated(key, null, symbol, timeframe);
            // Create compute callback for this page
            IndicatorPage.ComputeCallback<T> callback = createComputeCallback(page);
            notifyStateChanged(page, PageState.EMPTY, PageState.LOADING);
            DownloadLogStore.getInstance().logLoadStarted(key, null, symbol, timeframe);
            page.requestSourceData(candlePageMgr, aggTradesPageMgr, callback);
        } else if (listener != null && page.isReady()) {
            // Already ready - notify immediately
            SwingUtilities.invokeLater(() -> {
                listener.onStateChanged(page, PageState.EMPTY, PageState.READY);
                listener.onDataChanged(page);
            });
        }

        return page;
    }

    /**
     * Create a compute callback for an indicator page.
     * The callback routes computation to the appropriate executor.
     */
    private <T> IndicatorPage.ComputeCallback<T> createComputeCallback(IndicatorPage<T> page) {
        return new IndicatorPage.ComputeCallback<>() {
            @Override
            public void compute(IndicatorPage<T> p, List<Candle> candles, List<AggTrade> aggTrades) {
                if (aggTrades != null && !aggTrades.isEmpty()) {
                    computeAsyncWithAggTrades(p, candles, aggTrades);
                } else {
                    computeAsync(p, candles);
                }
            }

            @Override
            public void onError(IndicatorPage<T> p, String errorMessage) {
                updateError(p, errorMessage);
            }
        };
    }

    /**
     * Release an indicator page.
     * When the last reference is released, also releases underlying source data pages.
     */
    @SuppressWarnings("unchecked")
    public <T> void release(IndicatorPage<T> page, IndicatorPageListener<T> listener) {
        if (page == null) return;

        String key = page.getKey();

        // Remove listener or decrement anonymous refs
        if (listener != null) {
            Set<IndicatorPageListener<?>> pageListeners = listeners.get(key);
            if (pageListeners != null) {
                pageListeners.remove(listener);
            }
            String name = consumerNames.remove(listener);
            if (name != null) {
                DownloadLogStore.getInstance().logListenerRemoved(key, null, name);
            }
        } else {
            anonymousRefs.compute(key, (k, count) -> {
                if (count == null || count <= 1) return null;
                return count - 1;
            });
        }

        // Check if page has no consumers remaining
        Set<IndicatorPageListener<?>> remaining = listeners.get(key);
        boolean hasListeners = remaining != null && !remaining.isEmpty();
        boolean hasAnonymous = anonymousRefs.containsKey(key);

        if (!hasListeners && !hasAnonymous) {
            // Defer cleanup to allow release-then-re-request patterns
            if (!pendingCleanups.containsKey(key)) {
                ScheduledFuture<?> future = cleanupScheduler.schedule(
                    () -> cleanupIndicatorPage(key), CLEANUP_DELAY_MS, TimeUnit.MILLISECONDS);
                pendingCleanups.put(key, future);
                log.debug("Scheduled deferred cleanup for indicator page: {}", key);
            }
        }
    }

    /**
     * Deferred cleanup: destroy indicator page if still unused after grace period.
     */
    private void cleanupIndicatorPage(String key) {
        pendingCleanups.remove(key);

        // Re-check: a new consumer may have arrived during the grace period
        Set<IndicatorPageListener<?>> remaining = listeners.get(key);
        boolean hasListeners = remaining != null && !remaining.isEmpty();
        boolean hasAnonymous = anonymousRefs.containsKey(key);

        if (hasListeners || hasAnonymous) {
            log.debug("Deferred indicator cleanup cancelled (new consumer arrived): {}", key);
            return;
        }

        IndicatorPage<?> page = pages.remove(key);
        listeners.remove(key);

        if (page != null) {
            // IndicatorPage releases its own source data pages (star architecture)
            page.releaseSourcePages(candlePageMgr, aggTradesPageMgr);
            log.debug("Deferred indicator cleanup completed: {}", key);
        }
    }

    /**
     * Compute indicator in background thread (candles only).
     */
    @SuppressWarnings("unchecked")
    private <T> void computeAsync(IndicatorPage<T> page, List<Candle> candles) {
        long t0 = System.currentTimeMillis();
        computeExecutor.submit(() -> {
            try {
                Object result = computeIndicator(page.getType(), page.getParams(), page.getTimeframe(), page.getSymbol(), candles, null);
                long elapsed = System.currentTimeMillis() - t0;

                SwingUtilities.invokeLater(() -> {
                    PageState prevState = page.getState();
                    page.setData((T) result);
                    page.setSourceCandleHash(computeHash(candles));
                    page.setComputeTime(System.currentTimeMillis());
                    page.setState(PageState.READY);
                    page.setLoadProgress(100);  // Mark complete

                    DownloadLogStore.getInstance().logIndicatorComputed(
                        page.getKey(), page.getType().getName(), page.getParams(), candles.size(), elapsed);
                    notifyStateChanged(page, prevState, PageState.READY);
                    notifyDataChanged(page);
                });

            } catch (Exception e) {
                log.warn("Indicator computation failed: {}", e.getMessage());
                DownloadLogStore.getInstance().logError(page.getKey(), null, e.getMessage());
                updateError(page, e.getMessage());
            }
        });
    }

    /**
     * Compute indicator in background thread (candles + aggTrades).
     * Uses dedicated aggTradesExecutor to not be blocked by HISTORIC_RAYS.
     */
    @SuppressWarnings("unchecked")
    private <T> void computeAsyncWithAggTrades(IndicatorPage<T> page, List<Candle> candles, List<AggTrade> aggTrades) {
        long t0 = System.currentTimeMillis();
        aggTradesExecutor.submit(() -> {
            try {
                Object result = computeIndicator(page.getType(), page.getParams(), page.getTimeframe(), page.getSymbol(), candles, aggTrades);
                long elapsed = System.currentTimeMillis() - t0;

                SwingUtilities.invokeLater(() -> {
                    PageState prevState = page.getState();
                    page.setData((T) result);
                    page.setSourceCandleHash(computeHash(candles));
                    page.setComputeTime(System.currentTimeMillis());
                    page.setState(PageState.READY);
                    page.setLoadProgress(100);  // Mark complete

                    DownloadLogStore.getInstance().logIndicatorComputed(
                        page.getKey(), page.getType().getName(), page.getParams(), candles.size(), elapsed);
                    notifyStateChanged(page, prevState, PageState.READY);
                    notifyDataChanged(page);
                });

            } catch (Exception e) {
                log.warn("Indicator computation failed for {}: {}", page.getType(), e.getMessage(), e);
                DownloadLogStore.getInstance().logError(page.getKey(), null, e.getMessage());
                updateError(page, e.getMessage());
            }
        });
    }

    /**
     * Compute the indicator value using the registry.
     * Falls back to legacy switch for special cases not in registry.
     */
    private Object computeIndicator(IndicatorType type, String params, String timeframe, String symbol, List<Candle> candles, List<AggTrade> aggTrades) {
        long t0 = System.currentTimeMillis();
        int candleCount = candles != null ? candles.size() : 0;
        int aggTradeCount = aggTrades != null ? aggTrades.size() : 0;

        Object result = doComputeIndicator(type, params, timeframe, symbol, candles, aggTrades);

        long elapsed = System.currentTimeMillis() - t0;
        if (elapsed >= 50) {
            log.info("Indicator {} [{}] computed in {}ms (candles={}, aggTrades={})",
                type, params, elapsed, candleCount, aggTradeCount);
        } else {
            log.debug("Indicator {} [{}] computed in {}ms (candles={}, aggTrades={})",
                type, params, elapsed, candleCount, aggTradeCount);
        }
        return result;
    }

    private Object doComputeIndicator(IndicatorType type, String params, String timeframe, String symbol, List<Candle> candles, List<AggTrade> aggTrades) {
        // Map IndicatorType to registry ID
        String registryId = mapTypeToRegistryId(type);

        // Try registry first
        if (registryId != null) {
            IndicatorSpec<?> spec = IndicatorRegistry.getInstance().getSpec(registryId);
            if (spec != null) {
                // Create context with both candles and aggTrades if available
                String tf = timeframe != null ? timeframe : "1h";
                IndicatorContext ctx = (aggTrades != null && !aggTrades.isEmpty())
                    ? IndicatorContext.ofCandlesAndAggTrades(candles, aggTrades, tf)
                    : IndicatorContext.ofCandles(candles, tf);
                Object[] parsedParams = spec.parseParams(params);
                return spec.compute(ctx, parsedParams);
            }
        }

        // Fall back to legacy for special cases
        return switch (type) {
            case HISTORIC_RAYS -> {
                // Compute rays at multiple bar positions for historic visualization
                String[] p = params.split(":");
                int skip = Integer.parseInt(p[0]);
                int interval = Integer.parseInt(p[1]);
                yield computeHistoricRays(candles, skip, interval);
            }
            case FOOTPRINT_HEATMAP -> {
                // Footprint heatmap is computed directly by FootprintHeatmapOverlay
                // (profile-based for COMBINED/SPLIT, aggTrades-based for exchange modes)
                yield null;
            }
            case DAILY_VOLUME_PROFILE -> {
                // Params format: numBins:valueAreaPct:maxDays
                int numBins = 24;
                double valueAreaPct = 70.0;
                int maxDays = 30;
                if (params != null && !params.isEmpty()) {
                    String[] parts = params.split(":");
                    if (parts.length >= 1) numBins = Integer.parseInt(parts[0]);
                    if (parts.length >= 2) valueAreaPct = Double.parseDouble(parts[1]);
                    if (parts.length >= 3) maxDays = Integer.parseInt(parts[2]);
                }

                if (candles == null || candles.isEmpty()) yield null;

                // Try data service first, fall back to candle computation
                var ctx = com.tradery.forge.ApplicationContext.getInstance();
                if (ctx != null && ctx.isDataServiceAvailable() && symbol != null) {
                    var result = fetchDayProfilesFromDataService(ctx.getDataServiceClient(),
                        symbol, candles, numBins, valueAreaPct, maxDays);
                    if (result != null && !result.isEmpty()) yield result;
                }
                // Fallback: always compute from candles if data service failed or returned empty
                yield calculateDayProfilesFromCandles(candles, numBins, valueAreaPct, maxDays);
            }
            default -> throw new UnsupportedOperationException("Indicator not implemented: " + type);
        };
    }

    /**
     * Map IndicatorType enum to registry ID.
     * Returns null for types not in registry or needing special handling.
     */
    private String mapTypeToRegistryId(IndicatorType type) {
        return switch (type) {
            // Simple indicators
            case RSI -> "RSI";
            case SMA -> "SMA";
            case EMA -> "EMA";
            case ATR -> "ATR";
            case ADX -> "ADX";
            case PLUS_DI -> "PLUS_DI";
            case MINUS_DI -> "MINUS_DI";
            case VWAP -> "VWAP";
            // Composite indicators
            case MACD -> "MACD";
            case BBANDS -> "BBANDS";
            case STOCHASTIC -> "STOCHASTIC";
            case ICHIMOKU -> "ICHIMOKU";
            case SUPERTREND -> "SUPERTREND";
            // Rays
            case RESISTANCE_RAYS -> "RESISTANCE_RAYS";
            case SUPPORT_RAYS -> "SUPPORT_RAYS";
            // Orderflow (aggTrades-based)
            case DELTA -> "DELTA";
            case CUM_DELTA -> "CUM_DELTA";
            case BUY_VOLUME -> "BUY_VOLUME";
            case SELL_VOLUME -> "SELL_VOLUME";
            case WHALE_DELTA -> "WHALE_DELTA";
            case RETAIL_DELTA -> "RETAIL_DELTA";
            // OHLCV-based volume indicators
            case TRADE_COUNT -> "TRADE_COUNT";
            case BUY_RATIO -> "BUY_RATIO";
            case OHLCV_DELTA -> "OHLCV_DELTA";
            case OHLCV_CVD -> "OHLCV_CVD";
            // Range analysis
            case RANGE_POSITION -> "RANGE_POSITION";
            // Volume profile - returns full result, caller extracts POC/VAH/VAL
            case POC, VAH, VAL -> "VOLUME_PROFILE";
            // Special cases handled by legacy switch (not in registry)
            case HISTORIC_RAYS -> null;
            // External data indicators - not yet in registry
            case FUNDING, FUNDING_8H, OI, OI_CHANGE, PREMIUM -> null;
            // Daily volume profile — fetched from data service, not registry
            case DAILY_VOLUME_PROFILE -> null;
            // Footprint heatmap — fetched from data service when possible, falls back to aggTrades
            case FOOTPRINT_HEATMAP -> null;
        };
    }

    /**
     * Fetch daily volume profiles from the data service.
     * Uses the batch /profile/daily-binned endpoint — single ensureCoverage for the full range,
     * returns all days in one response. Much faster than N per-day calls.
     */
    private List<com.tradery.charts.overlay.DailyVolumeProfileAnnotation.DayProfile> fetchDayProfilesFromDataService(
            com.tradery.dataclient.DataServiceClient client, String symbol,
            List<Candle> candles, int numBins, double valueAreaPct, int maxDays) {

        long rangeStart = candles.get(0).timestamp();
        long rangeEnd = candles.get(candles.size() - 1).timestamp();

        var appCtx = com.tradery.forge.ApplicationContext.getInstance();
        if (appCtx != null) {
            appCtx.setProfileStatus(com.tradery.forge.ApplicationContext.ProfileStatus.LOADING,
                "Fetching profiles for " + symbol, 0);
        }

        try {
            var dailyBinned = client.getProfileDailyBinned(symbol, rangeStart, rangeEnd, numBins, valueAreaPct);
            if (dailyBinned == null || dailyBinned.isEmpty()) {
                if (appCtx != null) {
                    appCtx.setProfileStatus(com.tradery.forge.ApplicationContext.ProfileStatus.IDLE,
                        "No profile data for range", 0);
                }
                return null;  // Let caller fall back to candles
            }

            // Limit to most recent maxDays
            if (maxDays > 0 && dailyBinned.size() > maxDays) {
                dailyBinned = dailyBinned.subList(dailyBinned.size() - maxDays, dailyBinned.size());
            }

            var profiles = new java.util.ArrayList<com.tradery.charts.overlay.DailyVolumeProfileAnnotation.DayProfile>();
            for (var day : dailyBinned) {
                if (day.bins() == null) continue;
                double[] priceLevels = day.bins().priceLevels();
                double[] buyVols = day.bins().buyVolumes();
                double[] sellVols = day.bins().sellVolumes();
                if (priceLevels == null || priceLevels.length == 0) continue;

                double[] volumes = new double[priceLevels.length];
                double[] deltas = new double[priceLevels.length];
                double maxVol = 0;
                for (int i = 0; i < priceLevels.length; i++) {
                    volumes[i] = buyVols[i] + sellVols[i];
                    deltas[i] = buyVols[i] - sellVols[i];
                    maxVol = Math.max(maxVol, volumes[i]);
                }

                long dayEnd = day.dayStart() + 86_400_000L - 1;
                double minPrice = priceLevels[0];
                double maxPrice = priceLevels[priceLevels.length - 1];

                profiles.add(new com.tradery.charts.overlay.DailyVolumeProfileAnnotation.DayProfile(
                    day.dayStart(), dayEnd, priceLevels, volumes, deltas,
                    day.poc(), day.vah(), day.val(),
                    maxVol, minPrice, maxPrice
                ));
            }
            if (appCtx != null) {
                appCtx.setProfileStatus(com.tradery.forge.ApplicationContext.ProfileStatus.READY,
                    profiles.size() + " days from data service", profiles.size());
            }
            return profiles;
        } catch (Exception e) {
            log.warn("Failed to fetch daily binned profiles for {}: {}", symbol, e.getMessage());
            if (appCtx != null) {
                appCtx.setProfileStatus(com.tradery.forge.ApplicationContext.ProfileStatus.ERROR,
                    e.getMessage(), 0);
            }
            return null;  // Let caller fall back to candles
        }
    }

    /**
     * Candle-based daily volume profile fallback.
     * Less accurate than aggTrades-based profiles but always available.
     */
    private List<com.tradery.charts.overlay.DailyVolumeProfileAnnotation.DayProfile> calculateDayProfilesFromCandles(
            List<Candle> candles, int numBins, double valueAreaPct, int maxDays) {

        // Group candles by UTC day
        java.util.Map<java.time.LocalDate, java.util.List<Candle>> byDay = new java.util.LinkedHashMap<>();
        for (Candle c : candles) {
            java.time.LocalDate day = java.time.Instant.ofEpochMilli(c.timestamp())
                .atZone(java.time.ZoneOffset.UTC).toLocalDate();
            byDay.computeIfAbsent(day, k -> new java.util.ArrayList<>()).add(c);
        }

        var days = new java.util.ArrayList<>(byDay.keySet());
        if (maxDays > 0 && days.size() > maxDays) {
            days = new java.util.ArrayList<>(days.subList(days.size() - maxDays, days.size()));
        }

        var profiles = new java.util.ArrayList<com.tradery.charts.overlay.DailyVolumeProfileAnnotation.DayProfile>();
        for (java.time.LocalDate day : days) {
            java.util.List<Candle> dayCandles = byDay.get(day);
            if (dayCandles == null || dayCandles.isEmpty()) continue;

            com.tradery.core.indicators.VolumeProfile.Result vp =
                com.tradery.core.indicators.VolumeProfile.calculate(dayCandles, dayCandles.size(), numBins, valueAreaPct);
            if (vp.priceLevels().length == 0) continue;

            long dayStart = day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            long dayEnd = day.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

            double maxVol = 0;
            for (double v : vp.volumes()) maxVol = Math.max(maxVol, v);

            double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
            for (Candle c : dayCandles) {
                minPrice = Math.min(minPrice, c.low());
                maxPrice = Math.max(maxPrice, c.high());
            }

            profiles.add(new com.tradery.charts.overlay.DailyVolumeProfileAnnotation.DayProfile(
                dayStart, dayEnd, vp.priceLevels(), vp.volumes(),
                vp.poc(), vp.vah(), vp.val(),
                maxVol, minPrice, maxPrice
            ));
        }
        return profiles;
    }

    /**
     * Result of historic ray computation - rays at multiple bar positions.
     */
    public record HistoricRays(
        List<HistoricRayEntry> entries,
        int skip,
        int interval
    ) {
        public record HistoricRayEntry(int barIndex, RaySet resistance, RaySet support) {}
    }

    /**
     * Compute rays at multiple bar positions for historic visualization.
     * Uses lookback=0 (unlimited) to get meaningful slopes.
     */
    private HistoricRays computeHistoricRays(List<Candle> candles, int skip, int interval) {
        List<HistoricRays.HistoricRayEntry> entries = new ArrayList<>();
        int startBar = Math.max(20, interval);
        int lastBar = candles.size() - 1;

        for (int barIndex = startBar; barIndex < lastBar; barIndex += Math.max(1, interval)) {
            // Compute rays at this bar position (lookback=0 for unlimited)
            List<Candle> candlesUpToBar = candles.subList(0, barIndex + 1);
            RaySet resistance = RotatingRays.calculateResistanceRays(candlesUpToBar, 0, skip);
            RaySet support = RotatingRays.calculateSupportRays(candlesUpToBar, 0, skip);
            entries.add(new HistoricRays.HistoricRayEntry(barIndex, resistance, support));
        }

        log.debug("Computed historic rays: {} entries", entries.size());
        return new HistoricRays(entries, skip, interval);
    }

    /**
     * Compute a hash of candle data for invalidation tracking.
     */
    private String computeHash(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return "";
        // Simple hash based on first, last, and count
        Candle first = candles.get(0);
        Candle last = candles.get(candles.size() - 1);
        return first.timestamp() + ":" + last.timestamp() + ":" + candles.size();
    }

    private String makeKey(IndicatorType type, String params, String symbol,
                           String timeframe, long startTime, long endTime) {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append(":");
        sb.append(params).append(":");
        sb.append(symbol).append(":");
        if (timeframe != null) {
            sb.append(timeframe).append(":");
        }
        sb.append(startTime).append(":").append(endTime);
        return sb.toString();
    }

    private <T> void updateError(IndicatorPage<T> page, String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            PageState prevState = page.getState();
            page.setState(PageState.ERROR);
            page.setErrorMessage(errorMessage);
            notifyStateChanged(page, prevState, PageState.ERROR);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void notifyStateChanged(IndicatorPage<T> page, PageState oldState, PageState newState) {
        Set<IndicatorPageListener<?>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null) return;

        if (SwingUtilities.isEventDispatchThread()) {
            for (IndicatorPageListener<?> listener : pageListeners) {
                try {
                    ((IndicatorPageListener<T>) listener).onStateChanged(page, oldState, newState);
                } catch (Exception e) {
                    log.warn("Listener error: {}", e.getMessage());
                }
            }
        } else {
            SwingUtilities.invokeLater(() -> notifyStateChanged(page, oldState, newState));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void notifyDataChanged(IndicatorPage<T> page) {
        Set<IndicatorPageListener<?>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null) return;

        if (SwingUtilities.isEventDispatchThread()) {
            for (IndicatorPageListener<?> listener : pageListeners) {
                try {
                    ((IndicatorPageListener<T>) listener).onDataChanged(page);
                } catch (Exception e) {
                    log.warn("Listener error: {}", e.getMessage());
                }
            }
        } else {
            SwingUtilities.invokeLater(() -> notifyDataChanged(page));
        }
    }

    /**
     * Get count of active indicator pages.
     */
    public int getActivePageCount() {
        return pages.size();
    }

    /**
     * Estimate memory used by all active indicator pages in bytes.
     */
    public long estimateMemoryBytes() {
        long total = 0;
        for (IndicatorPage<?> page : pages.values()) {
            total += estimatePageMemory(page);
        }
        return total;
    }

    /**
     * Estimate memory for a single indicator page.
     */
    private long estimatePageMemory(IndicatorPage<?> page) {
        Object data = page.getData();
        if (data == null) return 0;

        // double[] arrays: 8 bytes per element
        if (data instanceof double[] arr) {
            return arr.length * 8L;
        }

        // Composite results (MACD, BBANDS, etc.) - estimate 3x array size
        if (data.getClass().isRecord()) {
            // Most records have 2-5 double[] fields, estimate 1000 bars * 3 arrays * 8 bytes
            return 24000L; // Conservative estimate
        }

        // HistoricRays - can be large
        if (data instanceof HistoricRays hr) {
            // Each entry has 2 RaySets with multiple rays
            return hr.entries().size() * 500L; // Rough estimate
        }

        // Default for unknown types
        return 1000L;
    }

    /**
     * Info about an indicator page for debugging.
     */
    public record IndicatorPageInfo(
        String key,
        String type,
        String params,
        String symbol,
        String timeframe,
        long startTime,
        long endTime,
        PageState state,
        int listenerCount,
        int loadProgress,  // 0-100 percentage
        boolean hasData,
        List<String> consumers
    ) {}

    /**
     * Get info about all active indicator pages.
     */
    public List<IndicatorPageInfo> getActivePages() {
        List<IndicatorPageInfo> result = new java.util.ArrayList<>();
        for (Map.Entry<String, IndicatorPage<?>> entry : pages.entrySet()) {
            IndicatorPage<?> page = entry.getValue();
            Set<IndicatorPageListener<?>> pageListeners = (Set<IndicatorPageListener<?>>) listeners.get(entry.getKey());
            int listenerCount = pageListeners != null ? pageListeners.size() : 0;

            // Collect consumer names for this page
            List<String> pageConsumers = new java.util.ArrayList<>();
            if (pageListeners != null) {
                for (IndicatorPageListener<?> listener : pageListeners) {
                    String name = consumerNames.get(listener);
                    if (name != null) {
                        pageConsumers.add(name);
                    }
                }
            }

            result.add(new IndicatorPageInfo(
                entry.getKey(),
                page.getType().getName(),
                page.getParams(),
                page.getSymbol(),
                page.getTimeframe(),
                page.getStartTime(),
                page.getEndTime(),
                page.getState(),
                listenerCount,
                page.getLoadProgress(),
                page.hasData(),
                pageConsumers
            ));
        }
        return result;
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        log.info("Shutting down IndicatorPageManager...");
        cleanupScheduler.shutdownNow();
        pendingCleanups.clear();
        computeExecutor.shutdown();
        aggTradesExecutor.shutdown();
        try {
            if (!computeExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                computeExecutor.shutdownNow();
            }
            if (!aggTradesExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                aggTradesExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            computeExecutor.shutdownNow();
            aggTradesExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pages.clear();
        listeners.clear();
        anonymousRefs.clear();
    }
}
