package com.tradery.data.page;

import com.tradery.data.DataType;
import com.tradery.data.PageState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Abstract base class for data page managers.
 *
 * Each data type (Candles, Funding, OI, etc.) has its own manager.
 * The manager handles:
 * - Page deduplication (same data identity = same page)
 * - Reference counting (cleanup when no consumers)
 * - Listener management (multiple listeners per page)
 * - Async data loading (never blocks)
 * - EDT-safe callbacks
 *
 * @param <T> The type of data records managed
 */
public abstract class DataPageManager<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // Active pages keyed by data identity (NO consumer in key)
    protected final Map<String, DataPage<T>> pages = new ConcurrentHashMap<>();

    // Listeners per page (multiple consumers can listen to same page)
    protected final Map<String, Set<DataPageListener<T>>> listeners = new ConcurrentHashMap<>();

    // Consumer names for each listener (for debugging/status display)
    protected final Map<DataPageListener<T>, String> consumerNames = new ConcurrentHashMap<>();

    // Reference counting for cleanup
    protected final Map<String, Integer> refCounts = new ConcurrentHashMap<>();

    // Background executor for data loading
    protected final ExecutorService loadExecutor;

    // The data type this manager handles
    protected final DataType dataType;

    /**
     * Create a manager for the specified data type.
     */
    protected DataPageManager(DataType dataType) {
        this(dataType, 2);
    }

    /**
     * Create a manager with custom thread pool size.
     */
    protected DataPageManager(DataType dataType, int threadPoolSize) {
        this.dataType = dataType;
        this.loadExecutor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, dataType.getDisplayName() + "PageManager");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Request a data page. Returns immediately (NEVER blocks).
     *
     * If a page for this data identity already exists, it's returned with
     * the listener registered. Otherwise, a new page is created and loading
     * starts in the background.
     *
     * @param symbol    Trading symbol (e.g., "BTCUSDT")
     * @param timeframe Timeframe (required for some types, null for others)
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @param listener  Listener for state/data changes (can be null)
     * @return Read-only view of the data page (may be EMPTY/LOADING initially)
     */
    public DataPageView<T> request(String symbol, String timeframe,
                                    long startTime, long endTime,
                                    DataPageListener<T> listener) {
        return request(symbol, timeframe, startTime, endTime, listener, "Anonymous");
    }

    /**
     * Request a data page with a named consumer. Returns immediately (NEVER blocks).
     *
     * @param symbol       Trading symbol (e.g., "BTCUSDT")
     * @param timeframe    Timeframe (required for some types, null for others)
     * @param startTime    Start time in milliseconds
     * @param endTime      End time in milliseconds
     * @param listener     Listener for state/data changes (can be null)
     * @param consumerName Name of the consumer (for debugging/status display)
     * @return Read-only view of the data page (may be EMPTY/LOADING initially)
     */
    public DataPageView<T> request(String symbol, String timeframe,
                                    long startTime, long endTime,
                                    DataPageListener<T> listener,
                                    String consumerName) {

        String key = makeKey(symbol, timeframe, startTime, endTime);

        // Get or create page (deduplication)
        DataPage<T> page = pages.computeIfAbsent(key, k ->
            createPage(symbol, timeframe, startTime, endTime));

        log.info("DataPageManager.request: dataType={}, key={}, pageState={}",
            dataType, key, page.getState());

        // Register listener with name
        if (listener != null) {
            listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);
            consumerNames.put(listener, consumerName);
        }

        // Increment reference count
        refCounts.merge(key, 1, Integer::sum);

        // If empty, start loading
        if (page.getState() == PageState.EMPTY) {
            startLoad(page);
        } else if (listener != null && page.getState() == PageState.READY) {
            // Page already ready - notify listener immediately on EDT
            SwingUtilities.invokeLater(() -> {
                listener.onStateChanged(page, PageState.EMPTY, PageState.READY);
                listener.onDataChanged(page);
            });
        }

        return page;  // Returns as DataPageView (interface)
    }

    /**
     * Release a page. Decrements reference count and cleans up if zero.
     *
     * @param pageView The page to release (DataPageView obtained from request())
     * @param listener The listener to unregister (can be null)
     */
    public void release(DataPageView<T> pageView, DataPageListener<T> listener) {
        if (pageView == null) return;

        String key = pageView.getKey();

        // Remove listener and its name
        if (listener != null) {
            Set<DataPageListener<T>> pageListeners = listeners.get(key);
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
            DataPage<T> page = pages.remove(key);
            listeners.remove(key);
            if (page != null) {
                onPageReleased(page);
            }
            log.debug("Released and cleaned up page: {}", key);
        } else {
            log.debug("Released page: {} (refs remaining: {})", key, newCount);
        }
    }

    /**
     * Get an existing page without incrementing reference count.
     * Used for checking if data is already available.
     */
    public DataPageView<T> peek(String symbol, String timeframe, long startTime, long endTime) {
        String key = makeKey(symbol, timeframe, startTime, endTime);
        return pages.get(key);
    }

    /**
     * Trigger a refresh/resync of a page's data.
     */
    public void refresh(DataPageView<T> pageView) {
        if (pageView == null) return;
        // Internal lookup by key since we can't cast from interface
        DataPage<T> page = pages.get(pageView.getKey());
        if (page == null) return;
        startLoad(page);
    }

    // ========== Abstract Methods ==========

    /**
     * Load data for a page. Called from background thread.
     * Implementation should:
     * 1. Try cache first
     * 2. Fetch from API if needed
     * 3. Call updatePageData() with results
     *
     * MUST NOT be called from EDT - this method performs blocking I/O.
     */
    protected abstract void loadData(DataPage<T> page) throws Exception;

    /**
     * Assert that we're NOT on the EDT. Call at the start of blocking operations.
     */
    protected void assertNotEDT(String operation) {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                operation + " must not be called from EDT - would block UI");
        }
    }

    // ========== Template Methods (Override if needed) ==========

    /**
     * Called when a page is fully released (ref count = 0).
     * Override to perform cleanup like freeing memory.
     */
    protected void onPageReleased(DataPage<T> page) {
        // Default: do nothing
    }

    // ========== Internal Methods ==========

    /**
     * Create a new page instance.
     */
    protected DataPage<T> createPage(String symbol, String timeframe,
                                      long startTime, long endTime) {
        return new DataPage<>(dataType, symbol, timeframe, startTime, endTime);
    }

    /**
     * Generate a key for page deduplication.
     */
    protected String makeKey(String symbol, String timeframe, long startTime, long endTime) {
        StringBuilder sb = new StringBuilder();
        sb.append(dataType).append(":");
        sb.append(symbol).append(":");
        if (timeframe != null) {
            sb.append(timeframe).append(":");
        }
        sb.append(startTime).append(":").append(endTime);
        return sb.toString();
    }

    /**
     * Start async loading for a page.
     */
    protected void startLoad(DataPage<T> page) {
        PageState oldState = page.getState();
        PageState newState = page.isEmpty() ? PageState.LOADING : PageState.UPDATING;

        page.setState(newState);
        notifyStateChanged(page, oldState, newState);

        loadExecutor.submit(() -> {
            try {
                assertNotEDT("loadData");
                loadData(page);
            } catch (Exception e) {
                log.warn("Failed to load {}: {}", page.getKey(), e.getMessage());
                updatePageError(page, e.getMessage());
            }
        });
    }

    /**
     * Update page with loaded data. Call from loadData() implementation.
     * Handles state transition and listener notification.
     */
    protected void updatePageData(DataPage<T> page, java.util.List<T> data) {
        // Create copy in background thread
        java.util.List<T> dataCopy = new java.util.ArrayList<>(data);

        SwingUtilities.invokeLater(() -> {
            PageState prevState = page.getState();
            page.setDataDirect(dataCopy);
            page.setState(PageState.READY);
            page.setLastSyncTime(System.currentTimeMillis());
            page.setErrorMessage(null);

            notifyStateChanged(page, prevState, PageState.READY);
            notifyDataChanged(page);

            log.debug("Loaded {} {} records", dataCopy.size(), dataType.getDisplayName());
        });
    }

    /**
     * Update page with an error. Call from loadData() on failure.
     */
    protected void updatePageError(DataPage<T> page, String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            PageState prevState = page.getState();
            page.setState(PageState.ERROR);
            page.setErrorMessage(errorMessage);

            notifyStateChanged(page, prevState, PageState.ERROR);
        });
    }

    /**
     * Notify all listeners of state change.
     */
    protected void notifyStateChanged(DataPage<T> page, PageState oldState, PageState newState) {
        Set<DataPageListener<T>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null || pageListeners.isEmpty()) return;

        // Ensure we're on EDT
        if (SwingUtilities.isEventDispatchThread()) {
            for (DataPageListener<T> listener : pageListeners) {
                try {
                    listener.onStateChanged(page, oldState, newState);
                } catch (Exception e) {
                    log.warn("Listener error on state change: {}", e.getMessage());
                }
            }
        } else {
            SwingUtilities.invokeLater(() -> notifyStateChanged(page, oldState, newState));
        }
    }

    /**
     * Notify all listeners of data change.
     */
    protected void notifyDataChanged(DataPage<T> page) {
        Set<DataPageListener<T>> pageListeners = listeners.get(page.getKey());
        if (pageListeners == null || pageListeners.isEmpty()) return;

        // Ensure we're on EDT
        if (SwingUtilities.isEventDispatchThread()) {
            for (DataPageListener<T> listener : pageListeners) {
                try {
                    listener.onDataChanged(page);
                } catch (Exception e) {
                    log.warn("Listener error on data change: {}", e.getMessage());
                }
            }
        } else {
            SwingUtilities.invokeLater(() -> notifyDataChanged(page));
        }
    }

    // ========== Status & Lifecycle ==========

    /**
     * Information about an active page for status display.
     */
    public record PageInfo(
        String key,
        PageState state,
        DataType dataType,
        String symbol,
        String timeframe,
        int listenerCount,
        int recordCount,
        java.util.List<String> consumers
    ) {}

    /**
     * Get information about all active pages.
     * Used by status UI to display what data is being tracked.
     */
    public java.util.List<PageInfo> getActivePages() {
        java.util.List<PageInfo> result = new java.util.ArrayList<>();
        for (Map.Entry<String, DataPage<T>> entry : pages.entrySet()) {
            DataPage<T> page = entry.getValue();
            Set<DataPageListener<T>> pageListeners = listeners.get(entry.getKey());
            int listenerCount = pageListeners != null ? pageListeners.size() : 0;

            // Collect consumer names for this page
            java.util.List<String> pageConsumers = new java.util.ArrayList<>();
            if (pageListeners != null) {
                for (DataPageListener<T> listener : pageListeners) {
                    String name = consumerNames.get(listener);
                    if (name != null) {
                        pageConsumers.add(name);
                    }
                }
            }

            result.add(new PageInfo(
                page.getKey(),
                page.getState(),
                page.getDataType(),
                page.getSymbol(),
                page.getTimeframe(),
                listenerCount,
                page.getRecordCount(),
                pageConsumers
            ));
        }
        return result;
    }

    /**
     * Get count of active pages.
     */
    public int getActivePageCount() {
        return pages.size();
    }

    /**
     * Estimate memory used by all active pages in bytes.
     * Subclasses should override getRecordSizeBytes() for accurate estimates.
     */
    public long estimateMemoryBytes() {
        long total = 0;
        for (DataPage<T> page : pages.values()) {
            total += page.getRecordCount() * getRecordSizeBytes();
        }
        return total;
    }

    /**
     * Estimated size in bytes of a single record.
     * Subclasses should override for accurate estimates.
     * Default assumes 64 bytes per record.
     */
    protected int getRecordSizeBytes() {
        return 64;
    }

    /**
     * Get total record count across all pages.
     */
    public int getTotalRecordCount() {
        int total = 0;
        for (DataPage<T> page : pages.values()) {
            total += page.getRecordCount();
        }
        return total;
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        log.info("Shutting down {}...", getClass().getSimpleName());
        loadExecutor.shutdown();
        try {
            if (!loadExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                loadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            loadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pages.clear();
        listeners.clear();
        refCounts.clear();
    }
}
