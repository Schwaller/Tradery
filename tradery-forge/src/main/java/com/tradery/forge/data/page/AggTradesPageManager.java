package com.tradery.forge.data.page;

import com.tradery.core.model.AggTrade;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import com.tradery.data.page.DataPage;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.DataType;
import com.tradery.forge.data.log.DownloadLogStore;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Page manager for aggregated trade data.
 *
 * AggTrades are tick-level trades used for orderflow analysis and sub-minute candles.
 * Uses WebSocket binary push with chunked delivery (aggTrades can be millions of records).
 */
public class AggTradesPageManager extends DataPageManager<AggTrade> {

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
        // AggTrades are tick-level data — timeframe is irrelevant for deduplication.
        // Always use null to ensure all consumers share the same page.
        return super.request(symbol, null, startTime, endTime, listener, consumerName);
    }

    @Override
    protected void loadData(DataPage<AggTrade> page) throws Exception {
        assertNotEDT("AggTradesPageManager.loadData");

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            updatePageError(page, "Data service not available");
            return;
        }

        DataServiceClient client = ctx.getDataServiceClient();
        DownloadLogStore logStore = DownloadLogStore.getInstance();

        if (!client.hasActiveConnection()) {
            updatePageError(page, "No WebSocket connection available");
            return;
        }

        loadDataViaWs(page, client, logStore);
    }

    /**
     * Load aggTrades via WebSocket binary push (chunked msgpack frames).
     * Server streams data in 10K-trade chunks after ensuring cache is populated.
     * Each chunk is deserialized immediately to avoid holding raw bytes in memory.
     */
    private void loadDataViaWs(DataPage<AggTrade> page, DataServiceClient client,
                                DownloadLogStore logStore) throws Exception {
        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();
        String forgePageKey = page.getKey();

        logStore.logApiRequestStarted(forgePageKey, DataType.AGG_TRADES,
            "data-service/ws-subscribe",
            String.format("%s %d-%d", symbol, startTime, endTime));

        long requestStart = System.currentTimeMillis();

        // Accumulate deserialized trades as chunks arrive (not raw bytes)
        var msgpackMapper = client.getMsgpackMapper();
        var tradeType = msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, AggTrade.class);
        List<AggTrade> allTrades = Collections.synchronizedList(new ArrayList<>());

        try {
            CompletableFuture<byte[]> future = client.subscribePage(
                DataType.AGG_TRADES, symbol, null, startTime, endTime,
                new DataServiceClient.DataPageCallback() {
                    @Override
                    public void onStateChanged(String state, int progress) {
                        if ("LOADING".equals(state)) {
                            updatePageProgress(page, Math.min(progress, 95));
                        }
                    }

                    @Override
                    public void onData(byte[] msgpackData, long recordCount) {
                        // Not used for chunked delivery
                    }

                    @Override
                    public void onChunk(byte[] msgpackData, int chunkIndex, int totalChunks) {
                        // Deserialize each chunk immediately — avoids holding raw bytes in memory
                        try {
                            List<AggTrade> chunkTrades = msgpackMapper.readValue(msgpackData, tradeType);
                            allTrades.addAll(chunkTrades);
                        } catch (Exception e) {
                            log.error("Failed to deserialize aggTrades chunk {}: {}", chunkIndex, e.getMessage());
                        }

                        // Update progress based on chunk delivery (after server-side loading is done)
                        int progress = 95 + (int) (chunkIndex * 5.0 / totalChunks);
                        updatePageProgress(page, Math.min(progress, 99));
                    }

                    @Override
                    public void onError(String message) {
                        log.error("WS page error for {}: {}", forgePageKey, message);
                    }
                });

            // Wait for all chunks to complete (10 minute timeout)
            future.get(10, TimeUnit.MINUTES);

            long totalDuration = System.currentTimeMillis() - requestStart;

            // Track memory
            int oldCount = page.getRecordCount();
            long newTotal = currentRecordCount.addAndGet(allTrades.size() - oldCount);

            log.info("AggTradesPageManager.loadData (WS): {} got {} trades in {}ms (total in memory: {})",
                symbol, allTrades.size(), totalDuration, newTotal);

            logStore.logApiRequestCompleted(forgePageKey, DataType.AGG_TRADES,
                "data-service/aggtrades", allTrades.size(), totalDuration);

            updatePageData(page, allTrades);

        } catch (Exception e) {
            log.error("AggTradesPageManager.loadData (WS) failed: {}", e.getMessage());
            client.unsubscribePage(DataType.AGG_TRADES, symbol, null, startTime, endTime);
            logStore.logError(forgePageKey, DataType.AGG_TRADES,
                "WS data load failed: " + e.getMessage());
            updatePageError(page, "Failed to load aggTrades: " + e.getMessage());
        }
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
     * Get current memory usage (record count).
     */
    public long getCurrentRecordCount() {
        return currentRecordCount.get();
    }
}
