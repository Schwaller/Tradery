package com.tradery.data.page;

import com.tradery.data.AggTradesStore;
import com.tradery.data.DataType;
import com.tradery.model.AggTrade;

import java.util.Collections;
import java.util.List;

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

    // Current record count
    private volatile long currentRecordCount = 0;

    public AggTradesPageManager(AggTradesStore aggTradesStore) {
        super(DataType.AGG_TRADES, 2);
        this.aggTradesStore = aggTradesStore;
    }

    @Override
    public DataPage<AggTrade> request(String symbol, String timeframe,
                                       long startTime, long endTime,
                                       DataPageListener<AggTrade> listener) {
        // Check memory before loading large data
        evictIfNeeded();
        return super.request(symbol, timeframe, startTime, endTime, listener);
    }

    @Override
    protected void loadData(DataPage<AggTrade> page) throws Exception {
        if (aggTradesStore == null) {
            updatePageData(page, Collections.emptyList());
            return;
        }

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        // AggTradesStore handles caching + API fetch internally
        int oldCount = page.getRecordCount();
        List<AggTrade> trades = aggTradesStore.getAggTrades(symbol, startTime, endTime);

        // Track memory
        currentRecordCount += (trades.size() - oldCount);

        log.debug("Loaded {} aggTrades for {} (total in memory: {})",
            trades.size(), symbol, currentRecordCount);
        updatePageData(page, trades);
    }

    @Override
    protected void onPageReleased(DataPage<AggTrade> page) {
        // Decrement record count
        currentRecordCount -= page.getRecordCount();
        if (currentRecordCount < 0) currentRecordCount = 0;
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
        if (currentRecordCount <= MAX_RECORDS) return;

        log.info("AggTrades memory threshold exceeded ({} records), evicting...",
            currentRecordCount);

        // Find pages with refCount == 0 (not currently in use)
        // and evict oldest first until under 80% threshold
        long target = (long) (MAX_RECORDS * 0.8);

        pages.entrySet().stream()
            .filter(e -> {
                Integer count = refCounts.get(e.getKey());
                return count == null || count == 0;
            })
            .sorted((a, b) -> Long.compare(
                a.getValue().getLastSyncTime(),
                b.getValue().getLastSyncTime()))
            .forEach(entry -> {
                if (currentRecordCount > target) {
                    String key = entry.getKey();
                    DataPage<AggTrade> page = pages.remove(key);
                    if (page != null) {
                        currentRecordCount -= page.getRecordCount();
                        listeners.remove(key);
                        refCounts.remove(key);
                        log.debug("Evicted aggTrades page: {} ({} records)",
                            key, page.getRecordCount());
                    }
                }
            });
    }

    /**
     * Get current memory usage (record count).
     */
    public long getCurrentRecordCount() {
        return currentRecordCount;
    }
}
