package com.tradery.dataclient.page;

import com.tradery.core.model.Candle;
import com.tradery.dataclient.DataServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Remote page manager for candle data.
 *
 * Sources data from data-service instead of directly from Binance.
 * Follows the same patterns as tradery-forge's CandlePageManager:
 * - Reference counting for cleanup
 * - Listener notification (state + data changes)
 * - EDT-safe callbacks for Swing
 * - Progress tracking (0-100%)
 * - Async loading (never blocks)
 *
 * Key differences:
 * - Data sourced from data-service via HTTP/WebSocket
 * - Live updates can be enabled per-page
 */
public class RemoteCandlePageManager {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteCandlePageManager.class);

    private final DataServiceConnection connection;
    private final DataServiceClient httpClient;
    private final String consumerName;

    // Page storage: pageKey -> page
    private final Map<String, DataPage<Candle>> pages = new ConcurrentHashMap<>();

    // Listener management: pageKey -> listeners
    private final Map<String, Set<DataPageListener<Candle>>> listeners = new ConcurrentHashMap<>();

    // Consumer tracking for reference counting: pageKey -> consumer names
    private final Map<String, Set<String>> consumers = new ConcurrentHashMap<>();

    // Reference counting: pageKey -> count
    private final Map<String, Integer> refCounts = new ConcurrentHashMap<>();

    // Executor for data fetching
    private final ExecutorService fetchExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "RemoteCandlePageManager-Fetch");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean shutdown = false;

    /**
     * Create a new remote candle page manager.
     *
     * @param connection   WebSocket connection to data-service
     * @param httpClient   HTTP client for fetching data
     * @param consumerName Default consumer name for pages
     */
    public RemoteCandlePageManager(DataServiceConnection connection, DataServiceClient httpClient,
                                   String consumerName) {
        this.connection = connection;
        this.httpClient = httpClient;
        this.consumerName = consumerName;
    }

    /**
     * Request a candle page. Returns immediately (never blocks).
     * Same interface as tradery-forge's CandlePageManager.
     *
     * @param symbol     Trading symbol
     * @param timeframe  Candle timeframe
     * @param startTime  Start time in milliseconds
     * @param endTime    End time in milliseconds
     * @param listener   Listener for state/data changes (can be null)
     * @return Read-only view of the page
     */
    public DataPageView<Candle> request(String symbol, String timeframe,
                                         long startTime, long endTime,
                                         DataPageListener<Candle> listener) {
        return request(symbol, timeframe, startTime, endTime, listener, consumerName);
    }

    /**
     * Request a candle page with explicit consumer name.
     *
     * @param symbol       Trading symbol
     * @param timeframe    Candle timeframe
     * @param startTime    Start time in milliseconds
     * @param endTime      End time in milliseconds
     * @param listener     Listener for state/data changes (can be null)
     * @param consumerName Name for tracking/debugging
     * @return Read-only view of the page
     */
    public DataPageView<Candle> request(String symbol, String timeframe,
                                         long startTime, long endTime,
                                         DataPageListener<Candle> listener,
                                         String consumerName) {
        String key = makeKey(symbol, timeframe, "perp", startTime, endTime);

        // Get or create page
        DataPage<Candle> page = pages.computeIfAbsent(key, k -> {
            DataPage<Candle> newPage = new DataPage<>(DataType.CANDLES, symbol, timeframe, startTime, endTime);
            // Subscribe to updates from data-service
            connection.subscribePage(DataType.CANDLES, symbol, timeframe, startTime, endTime,
                createPageCallback(key));
            return newPage;
        });

        // Register listener
        if (listener != null) {
            listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

            // Immediately notify of current state if not EMPTY
            // Only notify READY if data is actually present (avoids race with HTTP fetch)
            if (page.getState() != PageState.EMPTY) {
                if (page.getState() == PageState.READY && page.getData().isEmpty()) {
                    // READY but no data yet — HTTP fetch still in progress, skip notification
                    // fetchPageData() will notify when data arrives
                } else {
                    notifyStateChangedOnEDT(page, PageState.EMPTY, page.getState(), listener);
                }
            }
        }

        // Track consumer
        consumers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(consumerName);

        // Increment reference count
        refCounts.merge(key, 1, Integer::sum);

        LOG.debug("Page requested: {} (refCount={})", key, refCounts.get(key));
        return page;
    }

    /**
     * Request a live (sliding window) candle page (defaults to perp market).
     * Live pages slide forward with current time.
     *
     * @param symbol       Trading symbol
     * @param timeframe    Candle timeframe
     * @param duration     Window duration in milliseconds
     * @param listener     Listener for state/data changes (can be null)
     * @param consumerName Name for tracking/debugging
     * @return Read-only view of the page
     */
    public DataPageView<Candle> requestLive(String symbol, String timeframe, long duration,
                                             DataPageListener<Candle> listener, String consumerName) {
        return requestLive(symbol, timeframe, "perp", duration, listener, consumerName);
    }

    /**
     * Request a live (sliding window) candle page with market type.
     * Live pages slide forward with current time.
     *
     * @param symbol       Trading symbol
     * @param timeframe    Candle timeframe
     * @param marketType   Market type ("spot" or "perp")
     * @param duration     Window duration in milliseconds
     * @param listener     Listener for state/data changes (can be null)
     * @param consumerName Name for tracking/debugging
     * @return Read-only view of the page
     */
    public DataPageView<Candle> requestLive(String symbol, String timeframe, String marketType, long duration,
                                             DataPageListener<Candle> listener, String consumerName) {
        String key = makeLiveKey(symbol, timeframe, marketType, duration);

        // Get or create page
        DataPage<Candle> page = pages.computeIfAbsent(key, k -> {
            long now = System.currentTimeMillis();
            DataPage<Candle> newPage = DataPage.live(DataType.CANDLES, symbol, timeframe, marketType, now - duration, now, duration);
            // Subscribe to live page updates from data-service
            connection.subscribeLivePage(DataType.CANDLES, symbol, timeframe, marketType, duration,
                createPageCallback(key));
            return newPage;
        });

        // Register listener
        if (listener != null) {
            listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

            if (page.getState() != PageState.EMPTY) {
                if (page.getState() == PageState.READY && page.getData().isEmpty()) {
                    // READY but no data yet — skip notification
                } else {
                    notifyStateChangedOnEDT(page, PageState.EMPTY, page.getState(), listener);
                }
            }
        }

        // Track consumer
        consumers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(consumerName);

        // Increment reference count
        refCounts.merge(key, 1, Integer::sum);

        LOG.debug("Live page requested: {} (refCount={})", key, refCounts.get(key));
        return page;
    }

    /**
     * Release a page. Decrements reference count and cleans up when zero.
     *
     * @param pageView The page view to release
     * @param listener The listener that was registered (can be null)
     */
    public void release(DataPageView<Candle> pageView, DataPageListener<Candle> listener) {
        String key = pageView.getKey();

        // Remove listener
        if (listener != null) {
            Set<DataPageListener<Candle>> pageListeners = listeners.get(key);
            if (pageListeners != null) {
                pageListeners.remove(listener);
            }
        }

        // Decrement reference count
        int newCount = refCounts.compute(key, (k, v) -> {
            if (v == null || v <= 1) return null;
            return v - 1;
        }) == null ? 0 : refCounts.getOrDefault(key, 0);

        LOG.debug("Page released: {} (refCount={})", key, newCount);

        // Cleanup when no references
        if (newCount == 0) {
            cleanupPage(key);
        }
    }

    /**
     * Peek at a page without incrementing reference count.
     *
     * @return Page view if exists, null otherwise
     */
    public DataPageView<Candle> peek(String symbol, String timeframe, long startTime, long endTime) {
        String key = makeKey(symbol, timeframe, "perp", startTime, endTime);
        return pages.get(key);
    }

    /**
     * Refresh a page's data.
     *
     * @param pageView The page to refresh
     */
    public void refresh(DataPageView<Candle> pageView) {
        String key = pageView.getKey();
        DataPage<Candle> page = pages.get(key);
        if (page != null && page.isReady()) {
            fetchPageData(key, page);
        }
    }

    /**
     * Get list of active pages for monitoring.
     */
    public List<PageInfo> getActivePages() {
        List<PageInfo> result = new ArrayList<>();
        for (Map.Entry<String, DataPage<Candle>> entry : pages.entrySet()) {
            String key = entry.getKey();
            DataPage<Candle> page = entry.getValue();
            Set<DataPageListener<Candle>> pageListeners = listeners.getOrDefault(key, Set.of());
            Set<String> pageConsumers = consumers.getOrDefault(key, Set.of());

            result.add(new PageInfo(
                key,
                page.getState(),
                page.getDataType(),
                page.getSymbol(),
                page.getTimeframe(),
                page.getMarketType(),
                page.getStartTime(),
                page.getEndTime(),
                pageListeners.size(),
                page.getRecordCount(),
                page.getLoadProgress(),
                new ArrayList<>(pageConsumers),
                page.isLiveEnabled()
            ));
        }
        return result;
    }

    /**
     * Get number of active pages.
     */
    public int getActivePageCount() {
        return pages.size();
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        shutdown = true;
        fetchExecutor.shutdown();
        try {
            if (!fetchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                fetchExecutor.shutdownNow();
                if (!fetchExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOG.warn("FetchExecutor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            fetchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clean up all pages
        for (String key : new ArrayList<>(pages.keySet())) {
            cleanupPage(key);
        }
    }

    // ========== Internal Methods ==========

    private DataServiceConnection.PageUpdateCallback createPageCallback(String pageKey) {
        return new DataServiceConnection.PageUpdateCallback() {
            @Override
            public void onStateChanged(String state, int progress) {
                DataPage<Candle> page = pages.get(pageKey);
                if (page == null) return;

                PageState oldState = page.getState();
                PageState newState = parseState(state);
                page.setLoadProgress(progress);

                // Don't propagate READY until data is actually fetched via HTTP.
                // The fetchPageData() method handles the READY transition after data arrives.
                if (newState == PageState.READY) {
                    notifyProgressOnEDT(page, progress);
                    return;
                }

                page.setState(newState);

                // Notify listeners
                if (oldState != newState) {
                    notifyStateChangedOnEDT(page, oldState, newState);
                }
                notifyProgressOnEDT(page, progress);
            }

            @Override
            public void onDataReady(long recordCount) {
                DataPage<Candle> page = pages.get(pageKey);
                if (page == null) return;

                // Fetch the actual data
                fetchPageData(pageKey, page);
            }

            @Override
            public void onError(String message) {
                DataPage<Candle> page = pages.get(pageKey);
                if (page == null) return;

                PageState oldState = page.getState();
                page.setState(PageState.ERROR);
                page.setErrorMessage(message);

                notifyStateChangedOnEDT(page, oldState, PageState.ERROR);
            }

            @Override
            public void onEvicted() {
                DataPage<Candle> page = pages.get(pageKey);
                if (page == null) return;

                LOG.info("Page evicted by server: {}", pageKey);
                // The page data is still valid locally, but server no longer has it
                // Re-subscribe if still needed
                if (refCounts.getOrDefault(pageKey, 0) > 0) {
                    LOG.debug("Re-subscribing to evicted page: {}", pageKey);
                    connection.subscribePage(DataType.CANDLES, page.getSymbol(), page.getTimeframe(),
                        page.getStartTime(), page.getEndTime(), this);
                }
            }

            @Override
            public void onLiveUpdate(Candle candle) {
                // Update of incomplete/forming candle from server
                DataPage<Candle> page = pages.get(pageKey);
                if (page == null || !page.isLiveEnabled()) return;

                List<Candle> data = page.getData();
                if (!data.isEmpty()) {
                    Candle lastCandle = data.get(data.size() - 1);
                    if (lastCandle.timestamp() == candle.timestamp()) {
                        // Same candle, just update values
                        page.updateLastRecord(candle);
                        notifyLiveUpdateOnEDT(page, candle);
                    } else if (candle.timestamp() > lastCandle.timestamp()) {
                        // New candle started - add it as incomplete
                        LOG.debug("Live update: new candle started ts={}", candle.timestamp());
                        page.appendData(candle);
                        notifyLiveUpdateOnEDT(page, candle);
                    }
                } else {
                    // No candles yet - add this one
                    page.appendData(candle);
                    notifyLiveUpdateOnEDT(page, candle);
                }
            }

            @Override
            public void onLiveAppend(Candle candle, List<Long> removedTimestamps) {
                // New completed candle from server (with optional removal of old candles)
                DataPage<Candle> page = pages.get(pageKey);
                if (page == null || !page.isLiveEnabled()) return;

                LOG.debug("Live append: {} ts={}", pageKey, candle.timestamp());

                // Remove old candles if any
                if (removedTimestamps != null && !removedTimestamps.isEmpty()) {
                    page.removeByTimestamps(removedTimestamps);
                }

                // Append the new candle
                List<Candle> data = page.getData();
                if (data.isEmpty() || data.get(data.size() - 1).timestamp() != candle.timestamp()) {
                    page.appendData(candle);
                } else {
                    // Update the last candle if it matches (transition from incomplete to complete)
                    page.updateLastRecord(candle);
                }

                notifyLiveAppendOnEDT(page, candle);
            }
        };
    }

    private void fetchPageData(String pageKey, DataPage<Candle> page) {
        fetchExecutor.submit(() -> {
            try {
                List<Candle> candles = httpClient.getCandles(pageKey);
                if (candles != null && !candles.isEmpty()) {
                    page.setData(candles);
                    page.setLastSyncTime(System.currentTimeMillis());

                    PageState oldState = page.getState();
                    if (oldState != PageState.READY) {
                        page.setState(PageState.READY);
                        notifyStateChangedOnEDT(page, oldState, PageState.READY);
                    }
                    notifyDataChangedOnEDT(page);
                }
            } catch (IOException e) {
                LOG.error("Failed to fetch page data: {}", pageKey, e);
                PageState oldState = page.getState();
                page.setState(PageState.ERROR);
                page.setErrorMessage("Failed to fetch data: " + e.getMessage());
                notifyStateChangedOnEDT(page, oldState, PageState.ERROR);
            }
        });
    }

    private void cleanupPage(String key) {
        DataPage<Candle> page = pages.remove(key);
        listeners.remove(key);
        consumers.remove(key);
        refCounts.remove(key);

        // Unsubscribe from data-service
        if (page != null) {
            connection.unsubscribePage(DataType.CANDLES, page.getSymbol(), page.getTimeframe(),
                page.getStartTime(), page.getEndTime(), null);
        }

        LOG.debug("Page cleaned up: {}", key);
    }

    private PageState parseState(String state) {
        return switch (state.toUpperCase()) {
            case "PENDING" -> PageState.EMPTY;
            case "LOADING" -> PageState.LOADING;
            case "READY" -> PageState.READY;
            case "ERROR" -> PageState.ERROR;
            case "UPDATING" -> PageState.UPDATING;
            default -> PageState.EMPTY;
        };
    }

    private void notifyStateChangedOnEDT(DataPage<Candle> page, PageState oldState, PageState newState) {
        Set<DataPageListener<Candle>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null || pageListeners.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            for (DataPageListener<Candle> listener : pageListeners) {
                try {
                    listener.onStateChanged(page, oldState, newState);
                } catch (Exception e) {
                    LOG.warn("Error in page listener: {}", e.getMessage());
                }
            }
        });
    }

    private void notifyStateChangedOnEDT(DataPage<Candle> page, PageState oldState, PageState newState,
                                          DataPageListener<Candle> listener) {
        SwingUtilities.invokeLater(() -> {
            try {
                listener.onStateChanged(page, oldState, newState);
            } catch (Exception e) {
                LOG.warn("Error in page listener: {}", e.getMessage());
            }
        });
    }

    private void notifyDataChangedOnEDT(DataPage<Candle> page) {
        Set<DataPageListener<Candle>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null || pageListeners.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            for (DataPageListener<Candle> listener : pageListeners) {
                try {
                    listener.onDataChanged(page);
                } catch (Exception e) {
                    LOG.warn("Error in page listener: {}", e.getMessage());
                }
            }
        });
    }

    private void notifyProgressOnEDT(DataPage<Candle> page, int progress) {
        Set<DataPageListener<Candle>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null || pageListeners.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            for (DataPageListener<Candle> listener : pageListeners) {
                try {
                    listener.onProgress(page, progress);
                } catch (Exception e) {
                    LOG.warn("Error in page listener: {}", e.getMessage());
                }
            }
        });
    }

    private void notifyLiveUpdateOnEDT(DataPage<Candle> page, Candle candle) {
        Set<DataPageListener<Candle>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null || pageListeners.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            for (DataPageListener<Candle> listener : pageListeners) {
                try {
                    listener.onLiveUpdate(page, candle);
                } catch (Exception e) {
                    LOG.warn("Error in page listener: {}", e.getMessage());
                }
            }
        });
    }

    private void notifyLiveAppendOnEDT(DataPage<Candle> page, Candle candle) {
        Set<DataPageListener<Candle>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null || pageListeners.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            for (DataPageListener<Candle> listener : pageListeners) {
                try {
                    listener.onLiveAppend(page, candle);
                } catch (Exception e) {
                    LOG.warn("Error in page listener: {}", e.getMessage());
                }
            }
        });
    }

    private String makeKey(String symbol, String timeframe, String marketType, long startTime, long endTime) {
        // Must match server's PageKey.toKeyString() format: CANDLES:symbol:timeframe:marketType:anchor:duration
        // For anchored pages: anchor = endTime, duration = endTime - startTime
        String mt = marketType != null ? marketType : "perp";
        return "CANDLES:" + symbol.toUpperCase() + ":" + timeframe + ":" + mt + ":" + endTime + ":" + (endTime - startTime);
    }

    private String makeLiveKey(String symbol, String timeframe, String marketType, long duration) {
        // Must match server's PageKey.toKeyString() format: CANDLES:symbol:timeframe:marketType:LIVE:duration
        String mt = marketType != null ? marketType : "perp";
        return "CANDLES:" + symbol.toUpperCase() + ":" + timeframe + ":" + mt + ":LIVE:" + duration;
    }

    // ========== Info Record ==========

    /**
     * Information about an active page.
     */
    public record PageInfo(
        String key,
        PageState state,
        DataType dataType,
        String symbol,
        String timeframe,
        String marketType,
        long startTime,
        long endTime,
        int listenerCount,
        int recordCount,
        int loadProgress,
        List<String> consumers,
        boolean liveEnabled
    ) {}
}
