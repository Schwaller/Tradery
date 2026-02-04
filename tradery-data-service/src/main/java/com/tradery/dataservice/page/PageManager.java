package com.tradery.dataservice.page;

import com.tradery.dataservice.data.*;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.api.CoverageHandler;
import com.tradery.dataservice.config.DataServiceConfig;
import com.tradery.dataservice.live.LiveCandleManager;
import com.tradery.core.model.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Manages data pages - loading, caching, and lifecycle.
 * Adapted from DataPageManager for service-side use (no Swing).
 *
 * Supports both:
 * - Anchored pages: Fixed time range, static historical view
 * - Live pages: Sliding window that moves with current time
 */
public class PageManager {
    private static final Logger LOG = LoggerFactory.getLogger(PageManager.class);
    private static final int CLEANUP_INTERVAL_SECONDS = 10;

    private final DataServiceConfig config;
    private final SqliteDataStore dataStore;
    private final LiveCandleManager liveCandleManager;
    private final ObjectMapper msgpackMapper;
    // Use key string as map key to handle live pages correctly (PageKey has computed startTime/endTime)
    private final Map<String, Page> pages = new ConcurrentHashMap<>();
    private final List<PageUpdateListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService loadExecutor;
    private final ScheduledExecutorService cleanupExecutor;

    // Data stores for fetching
    private final FundingRateStore fundingRateStore;
    private final OpenInterestStore openInterestStore;
    private final AggTradesStore aggTradesStore;
    private final PremiumIndexStore premiumIndexStore;

    // Live subscription callbacks (for cleanup on page removal)
    private final Map<String, BiConsumer<String, Candle>> liveUpdateCallbacks = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<String, Candle>> liveCloseCallbacks = new ConcurrentHashMap<>();

    public PageManager(DataServiceConfig config, SqliteDataStore dataStore, LiveCandleManager liveCandleManager) {
        this.config = config;
        this.dataStore = dataStore;
        this.liveCandleManager = liveCandleManager;

        // Initialize data stores
        this.fundingRateStore = new FundingRateStore(new FundingRateClient(), dataStore);
        this.openInterestStore = new OpenInterestStore(new OpenInterestClient(), dataStore);
        this.aggTradesStore = new AggTradesStore(new AggTradesClient(), dataStore);
        this.premiumIndexStore = new PremiumIndexStore(new PremiumIndexClient(), dataStore);
        // Use default ObjectMapper for MessagePack - records are handled correctly
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory());
        this.loadExecutor = Executors.newFixedThreadPool(config.getMaxConcurrentDownloads());
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

        // Schedule periodic cleanup of unused pages
        cleanupExecutor.scheduleAtFixedRate(this::cleanupUnusedPages,
            CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Legacy constructor without LiveCandleManager (live pages won't receive updates).
     */
    public PageManager(DataServiceConfig config, SqliteDataStore dataStore) {
        this(config, dataStore, null);
    }

    public void shutdown() {
        loadExecutor.shutdown();
        cleanupExecutor.shutdown();
        try {
            loadExecutor.awaitTermination(10, TimeUnit.SECONDS);
            cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Unsubscribe from all live streams
        for (String pageKeyStr : liveUpdateCallbacks.keySet()) {
            unsubscribeFromLive(pageKeyStr);
        }
    }

    public void addUpdateListener(PageUpdateListener listener) {
        listeners.add(listener);
    }

    public void removeUpdateListener(PageUpdateListener listener) {
        listeners.remove(listener);
    }

    /**
     * Request a page for a consumer.
     * Creates the page if it doesn't exist, or adds the consumer if it does.
     */
    public PageStatus requestPage(PageKey key, String consumerId, String consumerName) {
        String keyStr = key.toKeyString();
        Page page = pages.computeIfAbsent(keyStr, k -> {
            Page newPage = new Page(key, config);
            loadExecutor.submit(() -> loadPage(newPage));
            return newPage;
        });

        boolean isNew = page.addConsumer(consumerId, consumerName);
        return page.getStatus().withConsumers(page.getConsumers());
    }

    /**
     * Release a consumer's hold on a page.
     */
    public boolean releasePage(PageKey key, String consumerId) {
        String keyStr = key.toKeyString();
        Page page = pages.get(keyStr);
        if (page == null) return false;

        page.removeConsumer(consumerId);

        // If no consumers left, page becomes candidate for eviction
        if (page.getConsumerCount() == 0) {
            LOG.debug("Page {} has no consumers, marking for cleanup", keyStr);
        }

        return true;
    }

    /**
     * Get the current status of a page.
     */
    public PageStatus getPageStatus(PageKey key) {
        Page page = pages.get(key.toKeyString());
        if (page == null) return null;
        return page.getStatus().withConsumers(page.getConsumers());
    }

    /**
     * Get status of all active pages.
     */
    public Map<String, PageStatus> getAllPageStatus() {
        Map<String, PageStatus> result = new HashMap<>();
        for (var entry : pages.entrySet()) {
            result.put(entry.getKey(),
                entry.getValue().getStatus().withConsumers(entry.getValue().getConsumers()));
        }
        return result;
    }

    /**
     * Get the data for a page as MessagePack binary.
     */
    public byte[] getPageData(PageKey key) {
        Page page = pages.get(key.toKeyString());
        if (page == null || page.getStatus().state() != PageState.READY) {
            return null;
        }
        return page.getData();
    }

    /**
     * Get candle data directly (for simple requests).
     */
    public byte[] getCandlesData(String symbol, String timeframe, Long start, Long end) {
        try {
            List<Candle> candles = dataStore.getCandles(symbol, timeframe, start, end);
            LOG.debug("getCandlesData: {} {} returned {} candles", symbol, timeframe, candles.size());
            return msgpackMapper.writeValueAsBytes(candles);
        } catch (Exception e) {
            LOG.error("Failed to get candles for {} {}", symbol, timeframe, e);
            return null;
        }
    }

    /**
     * Get aggregated trades data directly.
     * @deprecated Use {@link #writeAggTradesData(String, Long, Long, java.io.OutputStream)} for streaming
     */
    @Deprecated
    public byte[] getAggTradesData(String symbol, Long start, Long end) {
        try {
            List<AggTrade> trades = dataStore.getAggTrades(symbol, start, end);
            LOG.debug("getAggTradesData: {} returned {} trades", symbol, trades.size());
            return msgpackMapper.writeValueAsBytes(trades);
        } catch (Exception e) {
            LOG.error("Failed to get aggTrades for {}", symbol, e);
            return null;
        }
    }

    /**
     * Stream aggregated trades data directly to an output stream.
     * Avoids allocating a huge byte[] buffer for large datasets.
     *
     * @return number of trades written, or -1 on error
     */
    public int writeAggTradesData(String symbol, Long start, Long end, java.io.OutputStream out) {
        try {
            List<AggTrade> trades = dataStore.getAggTrades(symbol, start, end);
            LOG.debug("writeAggTradesData: {} returned {} trades", symbol, trades.size());
            msgpackMapper.writeValue(out, trades);
            return trades.size();
        } catch (Exception e) {
            LOG.error("Failed to stream aggTrades for {}", symbol, e);
            return -1;
        }
    }

    /**
     * Get funding rate data directly.
     */
    public byte[] getFundingData(String symbol, Long start, Long end) {
        try {
            List<FundingRate> rates = dataStore.getFundingRates(symbol, start, end);
            LOG.debug("getFundingData: {} returned {} rates", symbol, rates.size());
            return msgpackMapper.writeValueAsBytes(rates);
        } catch (Exception e) {
            LOG.error("Failed to get funding for {}", symbol, e);
            return null;
        }
    }

    /**
     * Get open interest data directly.
     */
    public byte[] getOpenInterestData(String symbol, Long start, Long end) {
        try {
            List<OpenInterest> oi = dataStore.getOpenInterest(symbol, start, end);
            LOG.debug("getOpenInterestData: {} returned {} records", symbol, oi.size());
            return msgpackMapper.writeValueAsBytes(oi);
        } catch (Exception e) {
            LOG.error("Failed to get OI for {}", symbol, e);
            return null;
        }
    }

    /**
     * Get premium index data directly.
     */
    public byte[] getPremiumData(String symbol, String timeframe, Long start, Long end) {
        try {
            List<PremiumIndex> premium = dataStore.getPremiumIndex(symbol, timeframe, start, end);
            LOG.debug("getPremiumData: {} {} returned {} records", symbol, timeframe, premium.size());
            return msgpackMapper.writeValueAsBytes(premium);
        } catch (Exception e) {
            LOG.error("Failed to get premium for {} {}", symbol, timeframe, e);
            return null;
        }
    }

    /**
     * Get coverage information for a symbol/data type.
     * dataType can be "candles:1h", "agg_trades", "funding_rates", "open_interest", "premium_index:1h"
     */
    public CoverageHandler.CoverageInfo getCoverage(String symbol, String dataType) {
        try {
            // Parse dataType into coverage data_type and sub_key
            String covType;
            String subKey;
            if (dataType.contains(":")) {
                String[] parts = dataType.split(":", 2);
                covType = parts[0];
                subKey = parts[1];
            } else {
                covType = dataType;
                subKey = covType.equals("klines") ? "1h" : "default";
            }

            var coverageDao = dataStore.forSymbol(symbol).coverage();
            var summary = coverageDao.getCoverageSummary(covType, subKey);

            // Find gaps across the full covered range
            List<CoverageHandler.GapInfo> gaps = List.of();
            if (summary.minStart() > 0 && summary.maxEnd() > summary.minStart()) {
                var rawGaps = coverageDao.findGaps(covType, subKey, summary.minStart(), summary.maxEnd());
                gaps = rawGaps.stream()
                    .map(g -> new CoverageHandler.GapInfo(g[0], g[1]))
                    .toList();
            }

            return new CoverageHandler.CoverageInfo(
                symbol, dataType,
                summary.minStart() > 0 ? summary.minStart() : null,
                summary.maxEnd() > 0 ? summary.maxEnd() : null,
                gaps,
                summary.totalCoveredMs()
            );
        } catch (Exception e) {
            LOG.error("Failed to get coverage for {} {}", symbol, dataType, e);
            return null;
        }
    }

    /**
     * Get list of symbols with available data by scanning database files.
     */
    public List<CoverageHandler.SymbolInfo> getAvailableSymbols() {
        try {
            List<String> symbols = dataStore.getAvailableSymbolNames();
            List<CoverageHandler.SymbolInfo> result = new ArrayList<>();

            for (String symbol : symbols) {
                try {
                    Map<String, List<String>> dataTypes = dataStore.getCoverageDataTypes(symbol);
                    result.add(new CoverageHandler.SymbolInfo(
                        symbol,
                        dataTypes.containsKey("klines"),
                        dataTypes.containsKey("agg_trades"),
                        dataTypes.containsKey("funding_rates"),
                        dataTypes.containsKey("open_interest"),
                        dataTypes.containsKey("premium_index")
                    ));
                } catch (Exception e) {
                    LOG.debug("Skipping symbol {} - {}", symbol, e.getMessage());
                }
            }

            return result;
        } catch (Exception e) {
            LOG.error("Failed to get available symbols", e);
            return List.of();
        }
    }

    /**
     * Get count of active pages.
     */
    public int getActivePageCount() {
        return pages.size();
    }

    /**
     * Get the AggTradesStore for direct streaming access.
     */
    public AggTradesStore getAggTradesStore() {
        return aggTradesStore;
    }

    /**
     * Load a page's data.
     */
    private void loadPage(Page page) {
        PageKey key = page.getKey();
        LOG.info("Loading page: {}", key.toKeyString());

        try {
            page.setState(PageState.LOADING, 0);
            notifyStateChanged(key, PageState.LOADING, 0);

            // Determine data type and load accordingly
            byte[] data;
            long recordCount;

            if (key.isCandles()) {
                data = loadCandles(key, page);
                recordCount = page.getRecordCount();
            } else if (key.isAggTrades()) {
                data = loadAggTrades(key, page);
                recordCount = page.getRecordCount();
            } else if (key.isFunding()) {
                data = loadFunding(key, page);
                recordCount = page.getRecordCount();
            } else if (key.isOpenInterest()) {
                data = loadOpenInterest(key, page);
                recordCount = page.getRecordCount();
            } else if (key.isPremium()) {
                data = loadPremium(key, page);
                recordCount = page.getRecordCount();
            } else {
                throw new IllegalArgumentException("Unknown data type: " + key.dataType());
            }

            page.setData(data);
            page.setState(PageState.READY, 100);
            notifyStateChanged(key, PageState.READY, 100);
            notifyDataReady(key, recordCount);

            LOG.info("Page ready: {} ({} records, live={})", key.toKeyString(), recordCount, key.isLive());

            // For live candle pages, subscribe to live updates
            if (key.isLive() && key.isCandles() && liveCandleManager != null) {
                subscribeToLive(page);
            }

        } catch (Exception e) {
            LOG.error("Failed to load page: {}", key.toKeyString(), e);
            page.setState(PageState.ERROR, 0);
            notifyError(key, e.getMessage());
        }
    }

    // ========== Live Subscription ==========

    private void subscribeToLive(Page page) {
        PageKey key = page.getKey();
        String pageKeyStr = key.toKeyString();

        LOG.info("Subscribing page {} to live updates", pageKeyStr);

        BiConsumer<String, Candle> onUpdate = (k, candle) -> {
            page.updateIncomplete(candle);
            notifyLiveUpdate(key, candle);
        };

        BiConsumer<String, Candle> onClose = (k, candle) -> {
            List<Candle> removed = page.appendAndTrim(candle);
            notifyLiveAppend(key, candle, removed);
        };

        liveUpdateCallbacks.put(pageKeyStr, onUpdate);
        liveCloseCallbacks.put(pageKeyStr, onClose);

        liveCandleManager.subscribe(key.symbol(), key.timeframe(), onUpdate, onClose);
    }

    private void unsubscribeFromLive(String pageKeyStr) {
        Page page = pages.get(pageKeyStr);
        if (page == null || !page.getKey().isLive()) return;

        PageKey key = page.getKey();
        BiConsumer<String, Candle> onUpdate = liveUpdateCallbacks.remove(pageKeyStr);
        BiConsumer<String, Candle> onClose = liveCloseCallbacks.remove(pageKeyStr);

        if ((onUpdate != null || onClose != null) && liveCandleManager != null) {
            liveCandleManager.unsubscribe(key.symbol(), key.timeframe(), onUpdate, onClose);
            LOG.info("Unsubscribed page {} from live updates", pageKeyStr);
        }
    }

    private void notifyLiveUpdate(PageKey key, Candle candle) {
        for (PageUpdateListener listener : listeners) {
            try {
                listener.onLiveUpdate(key, candle);
            } catch (Exception e) {
                LOG.warn("Listener error", e);
            }
        }
    }

    private void notifyLiveAppend(PageKey key, Candle candle, List<Candle> removed) {
        for (PageUpdateListener listener : listeners) {
            try {
                listener.onLiveAppend(key, candle, removed);
            } catch (Exception e) {
                LOG.warn("Listener error", e);
            }
        }
    }

    /**
     * Load candle data for a page, fetching from Binance if cache is incomplete.
     */
    private byte[] loadCandles(PageKey key, Page page) throws Exception {
        String symbol = key.symbol();
        String timeframe = key.timeframe();
        // Use effective times (handles both live and anchored pages correctly)
        long startTime = key.getEffectiveStartTime();
        long endTime = key.getEffectiveEndTime();

        // Check cache first
        List<Candle> cached = dataStore.getCandles(symbol, timeframe, startTime, endTime);
        long intervalMs = getIntervalMs(timeframe);
        long expectedBars = (endTime - startTime) / intervalMs;

        List<Candle> candles;
        // If sufficient coverage, use cached data
        if (cached.size() >= expectedBars * 0.9) {
            candles = cached;
            LOG.debug("loadCandles: {} {} cache hit ({} candles)", symbol, timeframe, cached.size());
        } else {
            // Need to fetch missing data
            LOG.info("loadCandles: {} {} fetching (cached {}/{})", symbol, timeframe, cached.size(), expectedBars);
            fetchCandles(key, page, expectedBars);

            // Read fresh data
            candles = dataStore.getCandles(symbol, timeframe, startTime, endTime);
            LOG.info("loadCandles: {} {} complete ({} candles)", symbol, timeframe, candles.size());
        }

        page.setRecordCount(candles.size());

        // For live pages, also store the candle list for live updates
        if (key.isLive()) {
            page.setLiveCandles(candles);
        }

        return msgpackMapper.writeValueAsBytes(candles);
    }

    /**
     * Fetch candles from Binance Vision + API.
     */
    private void fetchCandles(PageKey key, Page page, long expectedBars) throws Exception {
        String symbol = key.symbol();
        String timeframe = key.timeframe();
        long startTime = key.getEffectiveStartTime();
        long endTime = key.getEffectiveEndTime();

        BinanceClient apiClient = new BinanceClient();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        // For weekly/monthly timeframes, use REST API directly
        if ("1w".equals(timeframe) || "1M".equals(timeframe)) {
            List<Candle> candles = apiClient.fetchAllKlines(symbol, timeframe, startTime, endTime, cancelled,
                progress -> {
                    page.setState(PageState.LOADING, progress.percentComplete());
                    notifyStateChanged(key, PageState.LOADING, progress.percentComplete());
                });
            if (!candles.isEmpty()) {
                dataStore.saveCandles(symbol, timeframe, candles);
            }
            return;
        }

        // Use Vision for bulk historical + API for recent
        YearMonth startMonth = YearMonth.from(
            Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        BinanceVisionClient visionClient = new BinanceVisionClient(dataStore);

        visionClient.syncWithApiBackfill(symbol, timeframe, startMonth, apiClient, cancelled,
            progress -> {
                // Calculate progress based on months processed
                int pct = Math.min(95, progress.percentComplete());
                page.setState(PageState.LOADING, pct);
                notifyStateChanged(key, PageState.LOADING, pct);
            });
    }

    /**
     * Convert timeframe to interval in milliseconds.
     */
    private long getIntervalMs(String timeframe) {
        if (timeframe == null) return 3600000;
        return switch (timeframe) {
            case "1m" -> 60000L;
            case "3m" -> 180000L;
            case "5m" -> 300000L;
            case "15m" -> 900000L;
            case "30m" -> 1800000L;
            case "1h" -> 3600000L;
            case "2h" -> 7200000L;
            case "4h" -> 14400000L;
            case "6h" -> 21600000L;
            case "8h" -> 28800000L;
            case "12h" -> 43200000L;
            case "1d" -> 86400000L;
            case "3d" -> 259200000L;
            case "1w" -> 604800000L;
            case "1M" -> 2592000000L;
            default -> 3600000L;
        };
    }

    /**
     * Load aggregated trades data for a page, fetching from Binance if cache is incomplete.
     */
    private byte[] loadAggTrades(PageKey key, Page page) throws Exception {
        String symbol = key.symbol();
        long startTime = key.getEffectiveStartTime();
        long endTime = key.getEffectiveEndTime();

        // Set up progress callback
        aggTradesStore.setProgressCallback(progress -> {
            int pct = Math.min(95, progress.percentComplete());
            page.setState(PageState.LOADING, pct);
            notifyStateChanged(key, PageState.LOADING, pct);
        });

        try {
            // AggTradesStore handles cache check + fetch if needed
            long t0 = System.currentTimeMillis();
            List<AggTrade> trades = aggTradesStore.getAggTrades(symbol, startTime, endTime);
            long t1 = System.currentTimeMillis();
            page.setRecordCount(trades.size());
            byte[] data = msgpackMapper.writeValueAsBytes(trades);
            long t2 = System.currentTimeMillis();
            LOG.info("loadAggTrades: {} loaded {} trades (sqlite={}ms, msgpack={}ms, total={}ms)",
                symbol, trades.size(), t1 - t0, t2 - t1, t2 - t0);
            return data;
        } finally {
            aggTradesStore.setProgressCallback(null);
        }
    }

    /**
     * Load funding rate data for a page, fetching from Binance if cache is incomplete.
     */
    private byte[] loadFunding(PageKey key, Page page) throws Exception {
        String symbol = key.symbol();
        long startTime = key.getEffectiveStartTime();
        long endTime = key.getEffectiveEndTime();

        // FundingRateStore handles cache check + fetch if needed
        List<FundingRate> rates = fundingRateStore.getFundingRates(symbol, startTime, endTime);
        page.setRecordCount(rates.size());
        LOG.info("loadFunding: {} loaded {} rates", symbol, rates.size());
        return msgpackMapper.writeValueAsBytes(rates);
    }

    /**
     * Load open interest data for a page, fetching from Binance if cache is incomplete.
     */
    private byte[] loadOpenInterest(PageKey key, Page page) throws Exception {
        String symbol = key.symbol();
        long startTime = key.getEffectiveStartTime();
        long endTime = key.getEffectiveEndTime();

        // OpenInterestStore handles cache check + fetch if needed
        List<OpenInterest> oi = openInterestStore.getOpenInterest(symbol, startTime, endTime,
            progress -> {
                // Progress callback for OI fetching
                page.setState(PageState.LOADING, 50); // OI doesn't report detailed progress
                notifyStateChanged(key, PageState.LOADING, 50);
            });
        page.setRecordCount(oi.size());
        LOG.info("loadOpenInterest: {} loaded {} records", symbol, oi.size());
        return msgpackMapper.writeValueAsBytes(oi);
    }

    /**
     * Load premium index data for a page, fetching from Binance if cache is incomplete.
     */
    private byte[] loadPremium(PageKey key, Page page) throws Exception {
        String symbol = key.symbol();
        String timeframe = key.timeframe();
        long startTime = key.getEffectiveStartTime();
        long endTime = key.getEffectiveEndTime();

        // PremiumIndexStore handles cache check + fetch if needed
        List<PremiumIndex> premium = premiumIndexStore.getPremiumIndex(symbol, timeframe, startTime, endTime);
        page.setRecordCount(premium.size());
        LOG.info("loadPremium: {} {} loaded {} records", symbol, timeframe, premium.size());
        return msgpackMapper.writeValueAsBytes(premium);
    }

    /**
     * Clean up pages with no consumers.
     */
    private void cleanupUnusedPages() {
        List<String> toRemove = new ArrayList<>();

        for (var entry : pages.entrySet()) {
            Page page = entry.getValue();
            // Use shorter idle time (10 seconds) for quick cleanup
            if (page.getConsumerCount() == 0 && page.getIdleTimeMinutes() * 60 > CLEANUP_INTERVAL_SECONDS) {
                toRemove.add(entry.getKey());
            }
        }

        for (String pageKeyStr : toRemove) {
            Page page = pages.get(pageKeyStr);
            if (page == null) continue;

            PageKey key = page.getKey();
            LOG.info("Evicting unused page: {}", pageKeyStr);

            // Unsubscribe from live if applicable
            if (key.isLive()) {
                unsubscribeFromLive(pageKeyStr);
            }

            pages.remove(pageKeyStr);
            notifyEvicted(key);
        }
    }

    // Notification methods
    private void notifyStateChanged(PageKey key, PageState state, int progress) {
        for (PageUpdateListener listener : listeners) {
            try {
                listener.onStateChanged(key, state, progress);
            } catch (Exception e) {
                LOG.warn("Listener error", e);
            }
        }
    }

    private void notifyDataReady(PageKey key, long recordCount) {
        for (PageUpdateListener listener : listeners) {
            try {
                listener.onDataReady(key, recordCount);
            } catch (Exception e) {
                LOG.warn("Listener error", e);
            }
        }
    }

    private void notifyError(PageKey key, String message) {
        for (PageUpdateListener listener : listeners) {
            try {
                listener.onError(key, message);
            } catch (Exception e) {
                LOG.warn("Listener error", e);
            }
        }
    }

    private void notifyEvicted(PageKey key) {
        for (PageUpdateListener listener : listeners) {
            try {
                listener.onEvicted(key);
            } catch (Exception e) {
                LOG.warn("Listener error", e);
            }
        }
    }
}
