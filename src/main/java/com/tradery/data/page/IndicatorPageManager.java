package com.tradery.data.page;

import com.tradery.data.PageState;
import com.tradery.indicators.Indicators;
import com.tradery.indicators.RotatingRays;
import com.tradery.indicators.RotatingRays.RaySet;
import com.tradery.model.Candle;
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

        this.computeExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "IndicatorPageManager");
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
                // Request candle data (internal dependency)
                DataPageView<Candle> candlePage = candlePageMgr.request(
                    page.getSymbol(), page.getTimeframe(),
                    page.getStartTime(), page.getEndTime(),
                    new CandleDataListener<>(page),
                    "IndicatorPageManager");
            }
            // Other dependencies can be added similarly
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
     * Compute indicator in background thread.
     */
    @SuppressWarnings("unchecked")
    private <T> void computeAsync(IndicatorPage<T> page, List<Candle> candles) {
        computeExecutor.submit(() -> {
            try {
                Object result = computeIndicator(page.getType(), page.getParams(), candles);

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
     * Compute the indicator value.
     */
    private Object computeIndicator(IndicatorType type, String params, List<Candle> candles) {
        return switch (type) {
            case RSI -> Indicators.rsi(candles, Integer.parseInt(params));
            case SMA -> Indicators.sma(candles, Integer.parseInt(params));
            case EMA -> Indicators.ema(candles, Integer.parseInt(params));
            case ATR -> Indicators.atr(candles, Integer.parseInt(params));
            case ADX -> Indicators.adx(candles, Integer.parseInt(params)).adx();
            case PLUS_DI -> Indicators.adx(candles, Integer.parseInt(params)).plusDI();
            case MINUS_DI -> Indicators.adx(candles, Integer.parseInt(params)).minusDI();
            case MACD -> {
                String[] p = params.split(":");
                yield Indicators.macd(candles,
                    Integer.parseInt(p[0]),
                    Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]));
            }
            case BBANDS -> {
                String[] p = params.split(":");
                yield Indicators.bollingerBands(candles,
                    Integer.parseInt(p[0]),
                    Double.parseDouble(p[1]));
            }
            case RESISTANCE_RAYS -> {
                String[] p = params.split(":");
                int lookback = Integer.parseInt(p[0]);
                int skip = Integer.parseInt(p[1]);
                yield RotatingRays.calculateResistanceRays(candles, lookback, skip);
            }
            case SUPPORT_RAYS -> {
                String[] p = params.split(":");
                int lookback = Integer.parseInt(p[0]);
                int skip = Integer.parseInt(p[1]);
                yield RotatingRays.calculateSupportRays(candles, lookback, skip);
            }
            case HISTORIC_RAYS -> {
                // Compute rays at multiple bar positions for historic visualization
                String[] p = params.split(":");
                int skip = Integer.parseInt(p[0]);
                int interval = Integer.parseInt(p[1]);
                yield computeHistoricRays(candles, skip, interval);
            }
            case STOCHASTIC -> {
                String[] p = params.split(":");
                int kPeriod = Integer.parseInt(p[0]);
                int dPeriod = Integer.parseInt(p[1]);
                yield Indicators.stochastic(candles, kPeriod, dPeriod);
            }
            default -> throw new UnsupportedOperationException("Indicator not implemented: " + type);
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
