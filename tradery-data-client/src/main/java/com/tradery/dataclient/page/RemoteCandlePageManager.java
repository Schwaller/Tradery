package com.tradery.dataclient.page;

import com.tradery.dataclient.DataServiceClient;
import com.tradery.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

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

    // Live update integration: pages with live updates enabled
    private final Set<String> liveEnabledPages = ConcurrentHashMap.newKeySet();

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
        String key = makeKey(symbol, timeframe, startTime, endTime);

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
            if (page.getState() != PageState.EMPTY) {
                notifyStateChangedOnEDT(page, PageState.EMPTY, page.getState(), listener);
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
        String key = makeKey(symbol, timeframe, startTime, endTime);
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
     * Enable live updates for a page.
     * When candles close, they are appended to the page data.
     *
     * @param pageView The page to enable live updates for
     */
    public void enableLiveUpdates(DataPageView<Candle> pageView) {
        if (!(pageView instanceof DataPage<Candle> page)) {
            LOG.warn("Cannot enable live updates - not a DataPage");
            return;
        }

        String key = pageView.getKey();
        if (liveEnabledPages.add(key)) {
            page.setLiveEnabled(true);
            connection.subscribeLive(page.getSymbol(), page.getTimeframe(),
                candle -> handleLiveUpdate(key, candle),
                candle -> handleLiveClose(key, candle));
            LOG.debug("Live updates enabled for: {}", key);
        }
    }

    /**
     * Disable live updates for a page.
     *
     * @param pageView The page to disable live updates for
     */
    public void disableLiveUpdates(DataPageView<Candle> pageView) {
        if (!(pageView instanceof DataPage<Candle> page)) {
            return;
        }

        String key = pageView.getKey();
        if (liveEnabledPages.remove(key)) {
            page.setLiveEnabled(false);
            connection.unsubscribeLive(page.getSymbol(), page.getTimeframe(),
                candle -> handleLiveUpdate(key, candle),
                candle -> handleLiveClose(key, candle));
            LOG.debug("Live updates disabled for: {}", key);
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
                page.setState(newState);
                page.setLoadProgress(progress);

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

    private void handleLiveUpdate(String pageKey, Candle candle) {
        DataPage<Candle> page = pages.get(pageKey);
        if (page == null || !page.isLiveEnabled()) return;

        // Update the last candle (incomplete candle update)
        List<Candle> data = page.getData();
        if (!data.isEmpty()) {
            Candle lastCandle = data.get(data.size() - 1);
            if (lastCandle.timestamp() == candle.timestamp()) {
                page.updateLastRecord(candle);
                notifyDataChangedOnEDT(page);
            }
        }
    }

    private void handleLiveClose(String pageKey, Candle candle) {
        DataPage<Candle> page = pages.get(pageKey);
        if (page == null || !page.isLiveEnabled()) return;

        // Check if this candle is within our time range
        if (candle.timestamp() >= page.getStartTime()) {
            // Append the closed candle
            List<Candle> data = page.getData();
            if (data.isEmpty() || data.get(data.size() - 1).timestamp() != candle.timestamp()) {
                page.appendData(candle);
                notifyDataChangedOnEDT(page);
            } else {
                // Update the last candle if it matches
                page.updateLastRecord(candle);
                notifyDataChangedOnEDT(page);
            }
        }
    }

    private void cleanupPage(String key) {
        DataPage<Candle> page = pages.remove(key);
        listeners.remove(key);
        consumers.remove(key);
        refCounts.remove(key);

        // Disable live updates if enabled
        if (liveEnabledPages.remove(key) && page != null) {
            connection.unsubscribeLive(page.getSymbol(), page.getTimeframe(), null, null);
        }

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

    private String makeKey(String symbol, String timeframe, long startTime, long endTime) {
        return "CANDLES:" + symbol.toUpperCase() + ":" + timeframe + ":" + startTime + ":" + endTime;
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
        long startTime,
        long endTime,
        int listenerCount,
        int recordCount,
        int loadProgress,
        List<String> consumers,
        boolean liveEnabled
    ) {}
}
