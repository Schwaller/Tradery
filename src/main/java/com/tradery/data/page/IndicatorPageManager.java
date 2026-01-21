package com.tradery.data.page;

import com.tradery.data.PageState;
import com.tradery.indicators.Indicators;
import com.tradery.indicators.RotatingRays;
import com.tradery.indicators.RotatingRays.RaySet;
import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.indicators.registry.IndicatorRegistry;
import com.tradery.indicators.registry.IndicatorSpec;
import com.tradery.model.AggTrade;
import com.tradery.model.Candle;
import com.tradery.model.FundingRate;
import com.tradery.model.OpenInterest;
import com.tradery.model.PremiumIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Manager for computed indicator pages.
 *
 * Indicators depend on data pages (candles, funding, etc.) and are
 * recomputed when source data changes.
 *
 * This is a second layer on top of the data page system.
 */
public class IndicatorPageManager {

    private static final Logger log = LoggerFactory.getLogger(IndicatorPageManager.class);

    // Source data managers
    private final CandlePageManager candlePageMgr;
    private final FundingPageManager fundingPageMgr;
    private final OIPageManager oiPageMgr;
    private final AggTradesPageManager aggTradesPageMgr;
    private final PremiumPageManager premiumPageMgr;

    // Active indicator pages
    private final Map<String, IndicatorPage<?>> pages = new ConcurrentHashMap<>();
    private final Map<String, Set<IndicatorPageListener<?>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Integer> refCounts = new ConcurrentHashMap<>();
    private final Map<IndicatorPageListener<?>, String> consumerNames = new ConcurrentHashMap<>();

    // Background computation
    private final ExecutorService computeExecutor;
    private final ExecutorService aggTradesExecutor;  // Dedicated for aggTrades-based indicators

    public IndicatorPageManager(CandlePageManager candlePageMgr,
                                 FundingPageManager fundingPageMgr,
                                 OIPageManager oiPageMgr,
                                 AggTradesPageManager aggTradesPageMgr,
                                 PremiumPageManager premiumPageMgr) {
        this.candlePageMgr = candlePageMgr;
        this.fundingPageMgr = fundingPageMgr;
        this.oiPageMgr = oiPageMgr;
        this.aggTradesPageMgr = aggTradesPageMgr;
        this.premiumPageMgr = premiumPageMgr;

        // Use 4 threads for candle-based indicators (HISTORIC_RAYS can take minutes)
        this.computeExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "IndicatorPageManager");
            t.setDaemon(true);
            return t;
        });

        // Dedicated executor for aggTrades-based indicators (not blocked by HISTORIC_RAYS)
        this.aggTradesExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AggTradesIndicator");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Request an indicator page. Returns immediately (never blocks).
     *
     * @param type      Indicator type (RSI, SMA, etc.)
     * @param params    Indicator parameters (e.g., "14" for RSI(14))
     * @param symbol    Trading symbol
     * @param timeframe Timeframe
     * @param startTime Start time
     * @param endTime   End time
     * @param listener  Listener for updates (can be null)
     * @return The indicator page
     */
    public <T> IndicatorPage<T> request(IndicatorType type, String params,
                                         String symbol, String timeframe,
                                         long startTime, long endTime,
                                         IndicatorPageListener<T> listener) {
        return request(type, params, symbol, timeframe, startTime, endTime, listener, "Anonymous");
    }

    /**
     * Request an indicator page with a named consumer. Returns immediately (never blocks).
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

        // Get or create page
        IndicatorPage<T> page = (IndicatorPage<T>) pages.computeIfAbsent(key, k ->
            new IndicatorPage<>(type, params, symbol, timeframe, startTime, endTime));

        // Register listener with name
        if (listener != null) {
            listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);
            consumerNames.put(listener, consumerName);
        }

        // Increment ref count
        refCounts.merge(key, 1, Integer::sum);

        // Request source data and set up computation
        if (page.getState() == PageState.EMPTY) {
            requestSourceAndCompute(page);
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
     * Release an indicator page.
     */
    @SuppressWarnings("unchecked")
    public <T> void release(IndicatorPage<T> page, IndicatorPageListener<T> listener) {
        if (page == null) return;

        String key = page.getKey();

        // Remove listener and its name
        if (listener != null) {
            Set<IndicatorPageListener<?>> pageListeners = listeners.get(key);
            if (pageListeners != null) {
                pageListeners.remove(listener);
            }
            consumerNames.remove(listener);
        }

        // Decrement ref count
        Integer newCount = refCounts.compute(key, (k, count) -> {
            if (count == null || count <= 1) return null;
            return count - 1;
        });

        // Cleanup if no more references
        if (newCount == null) {
            pages.remove(key);
            listeners.remove(key);
            log.debug("Released indicator page: {}", key);
        }
    }

    /**
     * Request source data and trigger computation when ready.
     */
    private <T> void requestSourceAndCompute(IndicatorPage<T> page) {
        page.setState(PageState.LOADING);
        notifyStateChanged(page, PageState.EMPTY, PageState.LOADING);

        IndicatorType type = page.getType();

        switch (type.getDependency()) {
            case CANDLES -> {
                // Request candle data
                candlePageMgr.request(
                    page.getSymbol(), page.getTimeframe(),
                    page.getStartTime(), page.getEndTime(),
                    new CandleDataListener<>(page),
                    "IndicatorPageManager");
            }
            case AGG_TRADES -> {
                // Request both candles and aggTrades for orderflow indicators
                new AggTradesDataCoordinator<>(page).start();
            }
            case OPEN_INTEREST, FUNDING, PREMIUM -> {
                // These indicators use direct data access via IndicatorEngine
                log.debug("Indicator {} uses direct data access, not page manager computation",
                    type);
                updateError(page, "Use direct data access for " + type.getDependency());
            }
            default -> {
                log.warn("Indicator {} dependency {} not yet implemented",
                    type, type.getDependency());
                updateError(page, "Dependency not implemented: " + type.getDependency());
            }
        }
    }

    /**
     * Listener that triggers indicator computation when source candles are ready.
     */
    private class CandleDataListener<T> implements DataPageListener<Candle> {
        private final IndicatorPage<T> indicatorPage;

        CandleDataListener(IndicatorPage<T> indicatorPage) {
            this.indicatorPage = indicatorPage;
        }

        @Override
        public void onStateChanged(DataPageView<Candle> candlePage, PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                computeAsync(indicatorPage, candlePage.getData());
            } else if (newState == PageState.ERROR) {
                updateError(indicatorPage, candlePage.getErrorMessage());
            }
        }

        @Override
        public void onDataChanged(DataPageView<Candle> candlePage) {
            // Source data changed - recompute
            if (candlePage.isReady()) {
                computeAsync(indicatorPage, candlePage.getData());
            }
        }
    }

    /**
     * Coordinator that requests both candles and aggTrades, then computes when both ready.
     */
    private class AggTradesDataCoordinator<T> {
        private final IndicatorPage<T> indicatorPage;
        private volatile List<Candle> candles;
        private volatile List<AggTrade> aggTrades;
        private volatile boolean candlesReady = false;
        private volatile boolean aggTradesReady = false;

        AggTradesDataCoordinator(IndicatorPage<T> indicatorPage) {
            this.indicatorPage = indicatorPage;
        }

        void start() {
            // Request candles
            candlePageMgr.request(
                indicatorPage.getSymbol(), indicatorPage.getTimeframe(),
                indicatorPage.getStartTime(), indicatorPage.getEndTime(),
                new DataPageListener<Candle>() {
                    @Override
                    public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
                        if (newState == PageState.READY) {
                            candles = page.getData();
                            candlesReady = true;
                            checkAndCompute();
                        } else if (newState == PageState.ERROR) {
                            updateError(indicatorPage, page.getErrorMessage());
                        }
                    }
                    @Override
                    public void onDataChanged(DataPageView<Candle> page) {
                        if (page.isReady()) {
                            candles = page.getData();
                            checkAndCompute();
                        }
                    }
                },
                "IndicatorPageManager-AggTrades");

            // Request aggTrades
            aggTradesPageMgr.request(
                indicatorPage.getSymbol(), indicatorPage.getTimeframe(),
                indicatorPage.getStartTime(), indicatorPage.getEndTime(),
                new DataPageListener<AggTrade>() {
                    @Override
                    public void onStateChanged(DataPageView<AggTrade> page, PageState oldState, PageState newState) {
                        if (newState == PageState.READY) {
                            aggTrades = page.getData();
                            aggTradesReady = true;
                            checkAndCompute();
                        } else if (newState == PageState.ERROR) {
                            // AggTrades error is not fatal - can fall back to candles
                            log.debug("AggTrades not available, will use candle fallback");
                            aggTradesReady = true;
                            checkAndCompute();
                        }
                    }
                    @Override
                    public void onDataChanged(DataPageView<AggTrade> page) {
                        if (page.isReady()) {
                            aggTrades = page.getData();
                            checkAndCompute();
                        }
                    }
                });
        }

        private synchronized void checkAndCompute() {
            if (candlesReady && aggTradesReady && candles != null) {
                computeAsyncWithAggTrades(indicatorPage, candles, aggTrades);
            }
        }
    }

    /**
     * Compute indicator in background thread (candles only).
     */
    @SuppressWarnings("unchecked")
    private <T> void computeAsync(IndicatorPage<T> page, List<Candle> candles) {
        computeExecutor.submit(() -> {
            try {
                Object result = computeIndicator(page.getType(), page.getParams(), candles, null);

                SwingUtilities.invokeLater(() -> {
                    PageState prevState = page.getState();
                    page.setData((T) result);
                    page.setSourceCandleHash(computeHash(candles));
                    page.setComputeTime(System.currentTimeMillis());
                    page.setState(PageState.READY);

                    notifyStateChanged(page, prevState, PageState.READY);
                    notifyDataChanged(page);
                });

            } catch (Exception e) {
                log.warn("Indicator computation failed: {}", e.getMessage());
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
        // Use dedicated executor for aggTrades indicators (not blocked by candle-only indicators)
        aggTradesExecutor.submit(() -> {
            try {
                Object result = computeIndicator(page.getType(), page.getParams(), candles, aggTrades);

                SwingUtilities.invokeLater(() -> {
                    PageState prevState = page.getState();
                    page.setData((T) result);
                    page.setSourceCandleHash(computeHash(candles));
                    page.setComputeTime(System.currentTimeMillis());
                    page.setState(PageState.READY);

                    notifyStateChanged(page, prevState, PageState.READY);
                    notifyDataChanged(page);
                });

            } catch (Exception e) {
                log.warn("Indicator computation failed for {}: {}", page.getType(), e.getMessage(), e);
                updateError(page, e.getMessage());
            }
        });
    }

    /**
     * Compute the indicator value using the registry.
     * Falls back to legacy switch for special cases not in registry.
     */
    private Object computeIndicator(IndicatorType type, String params, List<Candle> candles, List<AggTrade> aggTrades) {
        // Map IndicatorType to registry ID
        String registryId = mapTypeToRegistryId(type);

        // Try registry first
        if (registryId != null) {
            IndicatorSpec<?> spec = IndicatorRegistry.getInstance().get(registryId);
            if (spec != null) {
                // Create context with both candles and aggTrades if available
                IndicatorContext ctx = (aggTrades != null && !aggTrades.isEmpty())
                    ? IndicatorContext.ofCandlesAndAggTrades(candles, aggTrades, "1h")
                    : IndicatorContext.ofCandles(candles, "1h");
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
            // Orderflow
            case DELTA -> "DELTA";
            case CUM_DELTA -> "CUM_DELTA";
            // Volume profile - returns full result, caller extracts POC/VAH/VAL
            case POC, VAH, VAL -> "VOLUME_PROFILE";
            // Special cases handled by legacy switch (not in registry)
            case HISTORIC_RAYS -> null;
            // External data indicators - not yet in registry
            case FUNDING, FUNDING_8H, OI, OI_CHANGE, PREMIUM -> null;
            // Daily volume profile
            case DAILY_VOLUME_PROFILE -> "DAILY_VOLUME_PROFILE";
        };
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
     * Info about an indicator page for debugging.
     */
    public record IndicatorPageInfo(
        String key,
        String type,
        String params,
        String symbol,
        String timeframe,
        PageState state,
        int listenerCount,
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
                page.getState(),
                listenerCount,
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
        refCounts.clear();
    }
}
