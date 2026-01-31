package com.tradery.forge.data.page;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.DataType;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.core.model.AggTrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Page manager for aggregated trade data.
 *
 * AggTrades are tick-level trades used for orderflow analysis and sub-minute candles.
 * This data can be very large, so the manager implements LRU eviction.
 * Delegates all data loading to the Data Service which handles
 * caching and fetching from Binance.
 */
public class AggTradesPageManager extends DataPageManager<AggTrade> {

    // Memory management: max records across all pages
    private static final long MAX_RECORDS = 100_000_000; // ~4GB

    // Current record count (AtomicLong for thread-safe compound operations)
    private final AtomicLong currentRecordCount = new AtomicLong(0);

    public AggTradesPageManager() {
        super(DataType.AGG_TRADES, 2);
    }

    @Override
    public DataPageView<AggTrade> request(String symbol, String timeframe,
                                           long startTime, long endTime,
                                           DataPageListener<AggTrade> listener,
                                           String consumerName) {
        // Check memory before loading large data
        evictIfNeeded();
        // AggTrades are tick-level data — timeframe is irrelevant for deduplication.
        // Always use null to ensure all consumers share the same page.
        return super.request(symbol, null, startTime, endTime, listener, consumerName);
    }

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 5_000;  // 5s, 10s, 20s, 40s, 80s
    private static final int MAX_STATUS_NOT_FOUND = 10;  // Allow transient 404s during polling

    @Override
    protected void loadData(DataPage<AggTrade> page) throws Exception {
        assertNotEDT("AggTradesPageManager.loadData");

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            log.info("AggTradesPageManager.loadData: {} attempt {}/{}", symbol, attempt, MAX_RETRIES);

            ApplicationContext ctx = ApplicationContext.getInstance();
            if (ctx == null || !ctx.isDataServiceAvailable()) {
                log.error("Data service not available");
                if (attempt < MAX_RETRIES) {
                    long delay = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
                    log.info("Retrying in {}ms...", delay);
                    Thread.sleep(delay);
                    continue;
                }
                updatePageError(page, "Data service not available after " + MAX_RETRIES + " attempts");
                return;
            }

            DataServiceClient client = ctx.getDataServiceClient();

            try {
                // Request page - data service will fetch if cache is incomplete
                var response = client.requestPage(
                    new DataServiceClient.PageRequest("AGG_TRADES", symbol, null, startTime, endTime),
                    "app-" + System.currentTimeMillis(),
                    "AggTradesPageManager"
                );

                // Poll for completion (max 10 minutes, detect stalls after 60s of no progress)
                String pageKey = response.pageKey();
                int lastProgress = 0;
                int statusNotFoundCount = 0;
                boolean pageReady = false;
                boolean pageError = false;
                long pollStartTime = System.currentTimeMillis();
                long lastProgressTime = pollStartTime;
                long maxPollMs = 10 * 60 * 1000;  // 10 minutes absolute max
                long stallTimeoutMs = 60 * 1000;   // 60s with no progress change = stall

                while (true) {
                    long now = System.currentTimeMillis();
                    if (now - pollStartTime > maxPollMs) {
                        log.warn("Polling timeout after {}s for {}", (now - pollStartTime) / 1000, pageKey);
                        break;
                    }
                    if (now - lastProgressTime > stallTimeoutMs) {
                        log.warn("No progress for {}s (stuck at {}%), treating as stall for {}",
                            (now - lastProgressTime) / 1000, lastProgress, pageKey);
                        break;
                    }

                    var status = client.getPageStatus(pageKey);
                    if (status == null) {
                        statusNotFoundCount++;
                        if (statusNotFoundCount >= MAX_STATUS_NOT_FOUND) {
                            log.warn("Page status not found {} times for {}, will retry", statusNotFoundCount, pageKey);
                            break;
                        }
                        Thread.sleep(500);
                        continue;
                    }
                    statusNotFoundCount = 0;

                    // Update progress
                    if (status.progress() > lastProgress) {
                        lastProgress = status.progress();
                        lastProgressTime = now;
                        updatePageProgress(page, Math.min(lastProgress, 95));
                    }

                    // Check if ready
                    if ("READY".equals(status.state())) {
                        pageReady = true;
                        break;
                    } else if ("ERROR".equals(status.state())) {
                        log.error("Data service reported error for page: {}", pageKey);
                        pageError = true;
                        break;
                    }

                    Thread.sleep(100);
                }

                if (!pageReady && !pageError) {
                    // Status polling failed — retry the whole request
                    if (attempt < MAX_RETRIES) {
                        long delay = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
                        log.info("Polling failed for {}, retrying in {}ms...", symbol, delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    updatePageError(page, "Page status polling failed after " + MAX_RETRIES + " attempts");
                    return;
                }

                if (pageError) {
                    if (attempt < MAX_RETRIES) {
                        long delay = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
                        log.info("Data service error for {}, retrying in {}ms...", symbol, delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    updatePageError(page, "Data service error after " + MAX_RETRIES + " attempts");
                    return;
                }

                // Fetch final data
                int oldCount = page.getRecordCount();
                long fetchStart = System.currentTimeMillis();
                List<AggTrade> trades = client.getAggTrades(symbol, startTime, endTime);
                long fetchMs = System.currentTimeMillis() - fetchStart;

                // Track memory (atomic operation)
                long newTotal = currentRecordCount.addAndGet(trades.size() - oldCount);

                log.info("AggTradesPageManager.loadData: {} got {} trades in {}ms (total in memory: {})",
                    symbol, trades.size(), fetchMs, newTotal);
                updatePageData(page, trades);
                return;  // Success

            } catch (Exception e) {
                log.error("Failed to load aggTrades (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    long delay = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
                    log.info("Retrying in {}ms...", delay);
                    Thread.sleep(delay);
                } else {
                    updatePageError(page, "Failed after " + MAX_RETRIES + " attempts: " + e.getMessage());
                }
            }
        }
    }

    @Override
    protected void onPageReleased(DataPage<AggTrade> page) {
        // Decrement record count (atomic, with floor at 0)
        currentRecordCount.updateAndGet(current ->
            Math.max(0, current - page.getRecordCount()));
    }

    /**
     * Load from cache via data service.
     */
    public List<AggTrade> loadFromCacheOnly(String symbol, long startTime, long endTime) {
        try {
            ApplicationContext ctx = ApplicationContext.getInstance();
            if (ctx != null && ctx.isDataServiceAvailable()) {
                DataServiceClient client = ctx.getDataServiceClient();
                List<AggTrade> trades = client.getAggTrades(symbol, startTime, endTime);
                log.debug("Loaded {} aggTrades from data service for {}", trades.size(), symbol);
                return trades;
            }
            log.warn("Data service not available");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to load aggTrades from data service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Evict least-recently-used pages if memory threshold exceeded.
     */
    private void evictIfNeeded() {
        long current = currentRecordCount.get();
        if (current <= MAX_RECORDS) return;

        log.info("AggTrades memory threshold exceeded ({} records), evicting...", current);

        // Find pages with refCount == 0 (not currently in use)
        // and evict oldest first until under 80% threshold
        long target = (long) (MAX_RECORDS * 0.8);

        // Collect candidates first to avoid stream mutation during iteration
        List<String> evictionCandidates = new ArrayList<>();
        for (Map.Entry<String, DataPage<AggTrade>> entry : pages.entrySet()) {
            String k = entry.getKey();
            Set<DataPageListener<AggTrade>> pageListeners = listeners.get(k);
            boolean hasListeners = pageListeners != null && !pageListeners.isEmpty();
            boolean hasAnonymous = anonymousRefs.containsKey(k);
            if (!hasListeners && !hasAnonymous) {
                evictionCandidates.add(k);
            }
        }

        // Sort by last sync time (oldest first)
        evictionCandidates.sort((a, b) -> {
            DataPage<AggTrade> pageA = pages.get(a);
            DataPage<AggTrade> pageB = pages.get(b);
            if (pageA == null || pageB == null) return 0;
            return Long.compare(pageA.getLastSyncTime(), pageB.getLastSyncTime());
        });

        // Evict until under target
        for (String key : evictionCandidates) {
            if (currentRecordCount.get() <= target) break;

            DataPage<AggTrade> page = pages.remove(key);
            if (page != null) {
                currentRecordCount.addAndGet(-page.getRecordCount());
                listeners.remove(key);
                anonymousRefs.remove(key);
                log.debug("Evicted aggTrades page: {} ({} records)",
                    key, page.getRecordCount());
            }
        }
    }

    /**
     * Get current memory usage (record count).
     */
    public long getCurrentRecordCount() {
        return currentRecordCount.get();
    }
}
