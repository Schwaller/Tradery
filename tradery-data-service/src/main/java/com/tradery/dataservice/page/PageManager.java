package com.tradery.dataservice.page;

import com.tradery.dataservice.api.CoverageHandler;
import com.tradery.dataservice.config.DataServiceConfig;
import com.tradery.model.Candle;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages data pages - loading, caching, and lifecycle.
 * Adapted from DataPageManager for service-side use (no Swing).
 */
public class PageManager {
    private static final Logger LOG = LoggerFactory.getLogger(PageManager.class);

    private final DataServiceConfig config;
    private final ObjectMapper msgpackMapper;
    private final Map<PageKey, Page> pages = new ConcurrentHashMap<>();
    private final List<PageUpdateListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService loadExecutor;
    private final ScheduledExecutorService cleanupExecutor;

    public PageManager(DataServiceConfig config) {
        this.config = config;
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory());
        this.loadExecutor = Executors.newFixedThreadPool(config.getMaxConcurrentDownloads());
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

        // Schedule periodic cleanup of unused pages
        cleanupExecutor.scheduleAtFixedRate(this::cleanupUnusedPages, 5, 5, TimeUnit.MINUTES);
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
        Page page = pages.computeIfAbsent(key, k -> {
            Page newPage = new Page(k, config);
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
        Page page = pages.get(key);
        if (page == null) return false;

        page.removeConsumer(consumerId);

        // If no consumers left, page becomes candidate for eviction
        if (page.getConsumerCount() == 0) {
            LOG.debug("Page {} has no consumers, marking for cleanup", key.toKeyString());
        }

        return true;
    }

    /**
     * Get the current status of a page.
     */
    public PageStatus getPageStatus(PageKey key) {
        Page page = pages.get(key);
        if (page == null) return null;
        return page.getStatus().withConsumers(page.getConsumers());
    }

    /**
     * Get status of all active pages.
     */
    public Map<String, PageStatus> getAllPageStatus() {
        Map<String, PageStatus> result = new HashMap<>();
        for (var entry : pages.entrySet()) {
            result.put(entry.getKey().toKeyString(),
                entry.getValue().getStatus().withConsumers(entry.getValue().getConsumers()));
        }
        return result;
    }

    /**
     * Get the data for a page as MessagePack binary.
     */
    public byte[] getPageData(PageKey key) {
        Page page = pages.get(key);
        if (page == null || page.getStatus().state() != PageState.READY) {
            return null;
        }
        return page.getData();
    }

    /**
     * Get candle data directly (for simple requests).
     */
    public byte[] getCandlesData(String symbol, String timeframe, Long start, Long end) {
        // For direct access, we query the SQLite database
        // Implementation will use SqliteDataStore
        // TODO: Implement direct data access
        return null;
    }

    /**
     * Get aggregated trades data directly.
     */
    public byte[] getAggTradesData(String symbol, Long start, Long end) {
        // TODO: Implement direct data access
        return null;
    }

    /**
     * Get funding rate data directly.
     */
    public byte[] getFundingData(String symbol, Long start, Long end) {
        // TODO: Implement direct data access
        return null;
    }

    /**
     * Get open interest data directly.
     */
    public byte[] getOpenInterestData(String symbol, Long start, Long end) {
        // TODO: Implement direct data access
        return null;
    }

    /**
     * Get premium index data directly.
     */
    public byte[] getPremiumData(String symbol, String timeframe, Long start, Long end) {
        // TODO: Implement direct data access
        return null;
    }

    /**
     * Get coverage information for a symbol/data type.
     */
    public CoverageHandler.CoverageInfo getCoverage(String symbol, String dataType) {
        // TODO: Implement coverage query from SQLite
        return null;
    }

    /**
     * Get list of symbols with available data.
     */
    public List<CoverageHandler.SymbolInfo> getAvailableSymbols() {
        // TODO: Implement symbol discovery from SQLite
        return List.of();
    }

    /**
     * Get count of active pages.
     */
    public int getActivePageCount() {
        return pages.size();
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

            LOG.info("Page ready: {} ({} records)", key.toKeyString(), recordCount);

        } catch (Exception e) {
            LOG.error("Failed to load page: {}", key.toKeyString(), e);
            page.setState(PageState.ERROR, 0);
            notifyError(key, e.getMessage());
        }
    }

    /**
     * Load candle data for a page.
     */
    private byte[] loadCandles(PageKey key, Page page) throws Exception {
        // TODO: Implement candle loading from SQLite/Binance
        // For now, return empty data
        List<Candle> candles = List.of();
        page.setRecordCount(candles.size());
        return msgpackMapper.writeValueAsBytes(candles);
    }

    /**
     * Load aggregated trades data for a page.
     */
    private byte[] loadAggTrades(PageKey key, Page page) throws Exception {
        // TODO: Implement aggTrades loading
        return msgpackMapper.writeValueAsBytes(List.of());
    }

    /**
     * Load funding rate data for a page.
     */
    private byte[] loadFunding(PageKey key, Page page) throws Exception {
        // TODO: Implement funding loading
        return msgpackMapper.writeValueAsBytes(List.of());
    }

    /**
     * Load open interest data for a page.
     */
    private byte[] loadOpenInterest(PageKey key, Page page) throws Exception {
        // TODO: Implement OI loading
        return msgpackMapper.writeValueAsBytes(List.of());
    }

    /**
     * Load premium index data for a page.
     */
    private byte[] loadPremium(PageKey key, Page page) throws Exception {
        // TODO: Implement premium loading
        return msgpackMapper.writeValueAsBytes(List.of());
    }

    /**
     * Clean up pages with no consumers.
     */
    private void cleanupUnusedPages() {
        List<PageKey> toRemove = new ArrayList<>();

        for (var entry : pages.entrySet()) {
            Page page = entry.getValue();
            if (page.getConsumerCount() == 0 && page.getIdleTimeMinutes() > 5) {
                toRemove.add(entry.getKey());
            }
        }

        for (PageKey key : toRemove) {
            LOG.info("Evicting unused page: {}", key.toKeyString());
            pages.remove(key);
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
