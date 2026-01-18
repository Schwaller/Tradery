package com.tradery.data.page;

import com.tradery.data.AggTradesStore;
import com.tradery.data.DataType;
import com.tradery.model.AggTrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Page manager for aggregated trade data.
 *
 * AggTrades are tick-level trades used for orderflow analysis and sub-minute candles.
 * This data can be very large, so the manager implements LRU eviction.
 */
public class AggTradesPageManager extends DataPageManager<AggTrade> {

    private final AggTradesStore aggTradesStore;

    // Memory management: max records across all pages
    private static final long MAX_RECORDS = 100_000_000; // ~4GB

    // Current record count (AtomicLong for thread-safe compound operations)
    private final AtomicLong currentRecordCount = new AtomicLong(0);

    public AggTradesPageManager(AggTradesStore aggTradesStore) {
        super(DataType.AGG_TRADES, 2);
        this.aggTradesStore = aggTradesStore;
    }

    @Override
    public DataPageView<AggTrade> request(String symbol, String timeframe,
                                           long startTime, long endTime,
                                           DataPageListener<AggTrade> listener) {
        // Check memory before loading large data
        evictIfNeeded();
        return super.request(symbol, timeframe, startTime, endTime, listener);
    }

    @Override
    protected void loadData(DataPage<AggTrade> page) throws Exception {
        assertNotEDT("AggTradesPageManager.loadData");

        if (aggTradesStore == null) {
            updatePageData(page, Collections.emptyList());
            return;
        }

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        // AggTradesStore handles caching + API fetch internally (blocking I/O)
        int oldCount = page.getRecordCount();
        List<AggTrade> trades = aggTradesStore.getAggTrades(symbol, startTime, endTime);

        // Track memory (atomic operation)
        long newTotal = currentRecordCount.addAndGet(trades.size() - oldCount);

        log.debug("Loaded {} aggTrades for {} (total in memory: {})",
            trades.size(), symbol, newTotal);
        updatePageData(page, trades);
    }

    @Override
    protected void onPageReleased(DataPage<AggTrade> page) {
        // Decrement record count (atomic, with floor at 0)
        currentRecordCount.updateAndGet(current ->
            Math.max(0, current - page.getRecordCount()));
    }

    /**
     * Load only from cache (no API fetch).
     */
    public List<AggTrade> loadFromCacheOnly(String symbol, long startTime, long endTime) {
        if (aggTradesStore == null) {
            return Collections.emptyList();
        }
        try {
            return aggTradesStore.getAggTradesCacheOnly(symbol, startTime, endTime);
        } catch (Exception e) {
            log.debug("Cache read failed: {}", e.getMessage());
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
            Integer count = refCounts.get(entry.getKey());
            if (count == null || count == 0) {
                evictionCandidates.add(entry.getKey());
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
                refCounts.remove(key);
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
