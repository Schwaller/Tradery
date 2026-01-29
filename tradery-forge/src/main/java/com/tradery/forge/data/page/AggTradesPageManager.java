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
        return super.request(symbol, timeframe, startTime, endTime, listener, consumerName);
    }

    @Override
    protected void loadData(DataPage<AggTrade> page) throws Exception {
        assertNotEDT("AggTradesPageManager.loadData");

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        log.info("AggTradesPageManager.loadData: {} requesting from data service", symbol);

        // Request page from data service (handles fetching if needed)
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            log.error("Data service not available");
            updatePageData(page, Collections.emptyList());
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

            // Poll for completion
            String pageKey = response.pageKey();
            int lastProgress = 0;

            while (true) {
                var status = client.getPageStatus(pageKey);
                if (status == null) {
                    log.error("Page status not found: {}", pageKey);
                    break;
                }

                // Update progress
                if (status.progress() > lastProgress) {
                    lastProgress = status.progress();
                    updatePageProgress(page, Math.min(lastProgress, 95));
                }

                // Check if ready
                if ("READY".equals(status.state())) {
                    break;
                } else if ("ERROR".equals(status.state())) {
                    log.error("Page load error: {}", pageKey);
                    break;
                }

                Thread.sleep(100); // Poll interval
            }

            // Fetch final data
            int oldCount = page.getRecordCount();
            List<AggTrade> trades = client.getAggTrades(symbol, startTime, endTime);

            // Track memory (atomic operation)
            long newTotal = currentRecordCount.addAndGet(trades.size() - oldCount);

            log.info("AggTradesPageManager.loadData: {} got {} trades (total in memory: {})",
                symbol, trades.size(), newTotal);
            updatePageData(page, trades);

        } catch (Exception e) {
            log.error("Failed to load aggTrades from data service: {}", e.getMessage());
            updatePageData(page, Collections.emptyList());
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
