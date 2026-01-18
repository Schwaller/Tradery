package com.tradery.data;

import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages checked-out data pages.
 *
 * Components check out pages they need, receive updates via listeners,
 * and release pages when done. Background sync runs for active pages.
 *
 * Features:
 * - Immediate return with cached data (from SQLite)
 * - Background sync for checked-out pages
 * - Event notifications on data updates
 * - Automatic cleanup when pages are released
 * - Deduplication of identical page requests
 */
public class DataPageManager {

    private static final Logger log = LoggerFactory.getLogger(DataPageManager.class);

    // How often to check if pages need syncing (ms)
    private static final long SYNC_CHECK_INTERVAL = 5000;

    // Minimum time between syncs for the same page (ms)
    private static final long MIN_SYNC_INTERVAL = 30000;

    private final SqliteDataStore dataStore;

    // Active pages keyed by their bounds
    private final Map<String, DataPage> pages = new ConcurrentHashMap<>();

    // Listeners for each page
    private final Map<String, Set<DataPageListener>> listeners = new ConcurrentHashMap<>();

    // Reference counts for each page (for cleanup)
    private final Map<String, Integer> refCounts = new ConcurrentHashMap<>();

    // Background sync executor
    private final ScheduledExecutorService syncExecutor;
    private final ExecutorService fetchExecutor;

    public DataPageManager(SqliteDataStore dataStore) {
        this.dataStore = dataStore;

        // Single thread for scheduling sync checks
        this.syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DataPageManager-Sync");
            t.setDaemon(true);
            return t;
        });

        // Thread pool for actual fetch operations
        this.fetchExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DataPageManager-Fetch");
            t.setDaemon(true);
            return t;
        });

        // Start periodic sync check
        syncExecutor.scheduleAtFixedRate(this::syncCheckedOutPages,
            SYNC_CHECK_INTERVAL, SYNC_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Check out a data page. Returns immediately with cached data if available,
     * then syncs in background and notifies listener when updates arrive.
     *
     * @param symbol    Trading symbol
     * @param timeframe Candle timeframe
     * @param startTime Start time in ms
     * @param endTime   End time in ms
     * @param listener  Listener for updates (can be null)
     * @return The data page (may be empty initially)
     */
    public DataPage checkout(String symbol, String timeframe, long startTime, long endTime,
                              DataPageListener listener) {

        String key = makeKey(symbol, timeframe, startTime, endTime);

        // Get or create the page
        DataPage page = pages.computeIfAbsent(key, k ->
            new DataPage(symbol, timeframe, startTime, endTime));

        // Register listener
        if (listener != null) {
            listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);
        }

        // Increment ref count
        refCounts.merge(key, 1, Integer::sum);

        // If page is empty, load from cache immediately
        if (page.getState() == DataPage.State.EMPTY) {
            loadFromCache(page);
        }

        // Trigger background sync if needed
        if (needsSync(page)) {
            triggerSync(page);
        }

        log.debug("Checked out page: {} (refs: {})", key, refCounts.get(key));
        return page;
    }

    /**
     * Release a checked-out page. When ref count reaches 0, the page is cleaned up.
     */
    public void release(DataPage page, DataPageListener listener) {
        String key = page.getKey();

        // Remove listener
        if (listener != null) {
            Set<DataPageListener> pageListeners = listeners.get(key);
            if (pageListeners != null) {
                pageListeners.remove(listener);
            }
        }

        // Decrement ref count
        Integer newCount = refCounts.compute(key, (k, count) -> {
            if (count == null || count <= 1) return null;
            return count - 1;
        });

        // Clean up if no more references
        if (newCount == null) {
            pages.remove(key);
            listeners.remove(key);
            log.debug("Released and cleaned up page: {}", key);
        } else {
            log.debug("Released page: {} (refs remaining: {})", key, newCount);
        }
    }

    /**
     * Load data from SQLite cache into the page (synchronous, fast).
     */
    private void loadFromCache(DataPage page) {
        try {
            List<Candle> cached = dataStore.getCandles(
                page.getSymbol(), page.getTimeframe(), page.getStartTime(), page.getEndTime());

            if (!cached.isEmpty()) {
                page.setCandles(cached);
                page.setState(DataPage.State.READY);
                log.debug("Loaded {} candles from cache for {}", cached.size(), page.getKey());
            }
        } catch (Exception e) {
            log.warn("Failed to load from SQLite cache: {}", e.getMessage());
        }
    }

    /**
     * Check if a page needs syncing.
     */
    private boolean needsSync(DataPage page) {
        // Always sync if empty
        if (page.isEmpty()) return true;

        // Don't sync if already syncing
        if (page.getState() == DataPage.State.LOADING ||
            page.getState() == DataPage.State.UPDATING) {
            return false;
        }

        // Sync if enough time has passed
        return System.currentTimeMillis() - page.getLastSyncTime() > MIN_SYNC_INTERVAL;
    }

    /**
     * Trigger a background sync for a page.
     */
    private void triggerSync(DataPage page) {
        String key = page.getKey();
        DataPage.State oldState = page.getState();
        DataPage.State newState = page.isEmpty() ? DataPage.State.LOADING : DataPage.State.UPDATING;

        page.setState(newState);
        notifyStateChanged(key, page, oldState, newState);

        fetchExecutor.submit(() -> {
            try {
                // Read from SQLite (data is populated by BinanceVisionClient separately)
                List<Candle> fresh = dataStore.getCandles(
                    page.getSymbol(), page.getTimeframe(), page.getStartTime(), page.getEndTime());

                // Update page on EDT
                SwingUtilities.invokeLater(() -> {
                    DataPage.State prevState = page.getState();
                    page.setCandles(fresh);
                    page.setState(DataPage.State.READY);
                    page.setLastSyncTime(System.currentTimeMillis());
                    page.setErrorMessage(null);

                    notifyStateChanged(key, page, prevState, DataPage.State.READY);
                    notifyDataUpdated(key, page);

                    log.debug("Synced {} candles for {}", fresh.size(), key);
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    DataPage.State prevState = page.getState();
                    page.setState(DataPage.State.ERROR);
                    page.setErrorMessage(e.getMessage());
                    notifyStateChanged(key, page, prevState, DataPage.State.ERROR);

                    log.warn("Sync failed for {}: {}", key, e.getMessage());
                });
            }
        });
    }

    /**
     * Periodic check to sync all checked-out pages that need it.
     */
    private void syncCheckedOutPages() {
        for (DataPage page : pages.values()) {
            if (needsSync(page)) {
                triggerSync(page);
            }
        }
    }

    /**
     * Notify listeners of data update.
     */
    private void notifyDataUpdated(String key, DataPage page) {
        Set<DataPageListener> pageListeners = listeners.get(key);
        if (pageListeners != null) {
            for (DataPageListener listener : pageListeners) {
                try {
                    listener.onPageDataUpdated(page);
                } catch (Exception e) {
                    log.warn("Listener error: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Notify listeners of state change.
     */
    private void notifyStateChanged(String key, DataPage page, DataPage.State oldState, DataPage.State newState) {
        Set<DataPageListener> pageListeners = listeners.get(key);
        if (pageListeners != null) {
            for (DataPageListener listener : pageListeners) {
                try {
                    listener.onPageStateChanged(page, oldState, newState);
                } catch (Exception e) {
                    log.warn("Listener error: {}", e.getMessage());
                }
            }
        }
    }

    private String makeKey(String symbol, String timeframe, long startTime, long endTime) {
        return symbol + ":" + timeframe + ":" + startTime + ":" + endTime;
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        syncExecutor.shutdown();
        fetchExecutor.shutdown();
        try {
            syncExecutor.awaitTermination(2, TimeUnit.SECONDS);
            fetchExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get count of active pages (for debugging).
     */
    public int getActivePageCount() {
        return pages.size();
    }
}
