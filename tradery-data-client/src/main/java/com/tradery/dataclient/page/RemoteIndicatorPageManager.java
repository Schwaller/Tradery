package com.tradery.dataclient.page;

import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.indicators.registry.IndicatorRegistry;
import com.tradery.indicators.registry.IndicatorSpec;
import com.tradery.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manager for computed indicator pages using remote data.
 *
 * Sources candle data from RemoteCandlePageManager and computes
 * indicators using tradery-core's IndicatorRegistry.
 *
 * Star architecture: IndicatorPage owns its source data acquisition.
 * This manager provides:
 * - Page registry and deduplication
 * - Reference counting
 * - Computation executors
 * - Listener management
 */
public class RemoteIndicatorPageManager {

    private static final Logger log = LoggerFactory.getLogger(RemoteIndicatorPageManager.class);

    // Source data manager
    private final RemoteCandlePageManager candlePageMgr;

    // Active indicator pages
    private final Map<String, IndicatorPage<?>> pages = new ConcurrentHashMap<>();
    private final Map<String, Set<IndicatorPageListener<?>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Integer> refCounts = new ConcurrentHashMap<>();
    private final Map<IndicatorPageListener<?>, String> consumerNames = new ConcurrentHashMap<>();

    // Background computation
    private final ExecutorService computeExecutor;

    private volatile boolean shutdown = false;

    public RemoteIndicatorPageManager(RemoteCandlePageManager candlePageMgr) {
        this.candlePageMgr = candlePageMgr;

        // Thread pool size: min 3, max 1/3 of CPUs
        int cpus = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(3, cpus / 3);
        log.info("Remote indicator compute pool size: {} (CPUs: {})", poolSize, cpus);

        this.computeExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "RemoteIndicatorCompute");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Request an indicator page. Returns immediately (never blocks).
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

        // Request source data if this is a new page
        if (page.getState() == PageState.EMPTY) {
            // Create compute callback for this page
            IndicatorPage.ComputeCallback<T> callback = createComputeCallback(page);
            notifyStateChanged(page, PageState.EMPTY, PageState.LOADING);
            page.requestSourceData(candlePageMgr, callback);
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
     */
    private <T> IndicatorPage.ComputeCallback<T> createComputeCallback(IndicatorPage<T> page) {
        return new IndicatorPage.ComputeCallback<>() {
            @Override
            public void compute(IndicatorPage<T> p, List<Candle> candles) {
                computeAsync(p, candles);
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

            // IndicatorPage releases its own source data page (star architecture)
            page.releaseSourcePage(candlePageMgr);

            log.debug("Released indicator page: {}", key);
        }
    }

    /**
     * Compute indicator in background thread.
     */
    @SuppressWarnings("unchecked")
    private <T> void computeAsync(IndicatorPage<T> page, List<Candle> candles) {
        computeExecutor.submit(() -> {
            try {
                Object result = computeIndicator(page.getType(), page.getParams(),
                                                  page.getTimeframe(), candles);

                SwingUtilities.invokeLater(() -> {
                    PageState prevState = page.getState();
                    page.setData((T) result);
                    page.setSourceCandleHash(computeHash(candles));
                    page.setComputeTime(System.currentTimeMillis());
                    page.setState(PageState.READY);
                    page.setLoadProgress(100);  // Mark complete

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
     * Compute the indicator value using the registry.
     */
    private Object computeIndicator(IndicatorType type, String params,
                                     String timeframe, List<Candle> candles) {
        // Map IndicatorType to registry ID
        String registryId = mapTypeToRegistryId(type);

        if (registryId != null) {
            IndicatorSpec<?> spec = IndicatorRegistry.getInstance().getSpec(registryId);
            if (spec != null) {
                String tf = timeframe != null ? timeframe : "1h";
                IndicatorContext ctx = IndicatorContext.ofCandles(candles, tf);
                Object[] parsedParams = spec.parseParams(params);
                return spec.compute(ctx, parsedParams);
            }
        }

        throw new UnsupportedOperationException("Indicator not implemented: " + type);
    }

    /**
     * Map IndicatorType enum to registry ID.
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
            // OHLCV-based volume indicators
            case TRADE_COUNT -> "TRADE_COUNT";
            case BUY_RATIO -> "BUY_RATIO";
            case OHLCV_DELTA -> "OHLCV_DELTA";
            case OHLCV_CVD -> "OHLCV_CVD";
            // Range analysis
            case RANGE_POSITION -> "RANGE_POSITION";
            // Volume profile - returns full result
            case POC, VAH, VAL -> "VOLUME_PROFILE";
        };
    }

    /**
     * Compute a hash of candle data for invalidation tracking.
     */
    private String computeHash(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return "";
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
        long startTime,
        long endTime,
        PageState state,
        int listenerCount,
        int loadProgress,
        boolean hasData,
        List<String> consumers
    ) {}

    /**
     * Get info about all active indicator pages.
     */
    public List<IndicatorPageInfo> getActivePages() {
        List<IndicatorPageInfo> result = new ArrayList<>();
        for (Map.Entry<String, IndicatorPage<?>> entry : pages.entrySet()) {
            IndicatorPage<?> page = entry.getValue();
            Set<IndicatorPageListener<?>> pageListeners = listeners.get(entry.getKey());
            int listenerCount = pageListeners != null ? pageListeners.size() : 0;

            // Collect consumer names for this page
            List<String> pageConsumers = new ArrayList<>();
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
        shutdown = true;
        log.info("Shutting down RemoteIndicatorPageManager...");
        computeExecutor.shutdown();
        try {
            if (!computeExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                computeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            computeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pages.clear();
        listeners.clear();
        refCounts.clear();
    }
}
