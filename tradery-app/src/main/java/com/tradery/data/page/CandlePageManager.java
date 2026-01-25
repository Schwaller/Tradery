package com.tradery.data.page;

import com.tradery.ApplicationContext;
import com.tradery.data.BinanceClient;
import com.tradery.data.BinanceVisionClient;
import com.tradery.data.DataType;
import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.model.Candle;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Page manager for OHLCV candle data.
 *
 * Uses SQLite as the primary cache and fetches from Binance API/Vision
 * when data is missing or incomplete.
 */
public class CandlePageManager extends DataPageManager<Candle> {

    private final SqliteDataStore dataStore;

    public CandlePageManager(SqliteDataStore dataStore) {
        super(DataType.CANDLES, 4); // 4 threads for parallel loading
        this.dataStore = dataStore;
    }

    @Override
    protected void loadData(DataPage<Candle> page) throws Exception {
        assertNotEDT("CandlePageManager.loadData");

        String symbol = page.getSymbol();
        String timeframe = page.getTimeframe();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        // Calculate expected bars for progress tracking
        long intervalMs = getIntervalMs(timeframe);
        int expectedBars = (int) ((endTime - startTime) / intervalMs);

        // First try cache and report initial progress
        List<Candle> cached = loadFromCache(symbol, timeframe, startTime, endTime);
        int initialProgress = expectedBars > 0 ? (cached.size() * 100) / expectedBars : 0;
        updatePageProgress(page, Math.min(initialProgress, 95));  // Cap at 95% until verified complete

        log.info("CandlePageManager.loadData: symbol={}, timeframe={}, cached={}/{} ({}%)",
            symbol, timeframe, cached.size(), expectedBars, initialProgress);

        // Check if we have sufficient coverage
        if (hasSufficientCoverage(cached, timeframe, startTime, endTime)) {
            log.debug("Cache hit for {} {} ({} candles)", symbol, timeframe, cached.size());
            updatePageData(page, cached);
            return;
        }

        // Need to fetch missing data
        log.info("Fetching candles for {} {} ({} to {})",
            symbol, timeframe, startTime, endTime);

        // Sync via Vision + API with progress reporting based on overall coverage
        fetchAndSync(page, symbol, timeframe, startTime, endTime, expectedBars);

        // Read fresh data from SQLite
        List<Candle> fresh = loadFromCache(symbol, timeframe, startTime, endTime);
        updatePageData(page, fresh);
    }

    /**
     * Load candles from cache via data service.
     */
    private List<Candle> loadFromCache(String symbol, String timeframe,
                                        long startTime, long endTime) {
        try {
            ApplicationContext ctx = ApplicationContext.getInstance();
            if (ctx != null && ctx.isDataServiceAvailable()) {
                DataServiceClient client = ctx.getDataServiceClient();
                List<Candle> candles = client.getCandles(symbol, timeframe, startTime, endTime);
                log.debug("Loaded {} candles from data service for {} {}",
                    candles.size(), symbol, timeframe);
                return candles;
            }
            log.warn("Data service not available");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to load candles from data service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Check if cached data provides sufficient coverage.
     */
    private boolean hasSufficientCoverage(List<Candle> cached, String timeframe,
                                           long startTime, long endTime) {
        if (cached.isEmpty()) return false;

        long intervalMs = getIntervalMs(timeframe);
        long expectedBars = (endTime - startTime) / intervalMs;

        // Consider sufficient if we have at least 90% of expected bars
        return cached.size() >= expectedBars * 0.9;
    }

    /**
     * Fetch data from Binance Vision (for bulk historical) and API (for recent).
     * Reports progress based on overall page coverage (cached + fetched vs expected).
     */
    private void fetchAndSync(DataPage<Candle> page, String symbol, String timeframe,
                               long startTime, long endTime, int expectedBars) throws Exception {
        BinanceClient apiClient = new BinanceClient();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        // For weekly timeframe, use REST API directly (Vision has gaps at month boundaries)
        if ("1w".equals(timeframe) || "1M".equals(timeframe)) {
            log.info("Using REST API for {} {} (Vision has gaps for large timeframes)", symbol, timeframe);
            List<Candle> candles = apiClient.fetchAllKlines(symbol, timeframe, startTime, endTime, cancelled,
                progress -> {
                    // Re-check cache to get current coverage
                    List<Candle> current = loadFromCache(symbol, timeframe, startTime, endTime);
                    int percent = expectedBars > 0 ? (current.size() * 100) / expectedBars : progress.percentComplete();
                    updatePageProgress(page, Math.min(95, percent));  // Cap at 95% until complete
                });
            if (!candles.isEmpty()) {
                dataStore.saveCandles(symbol, timeframe, candles);
                log.info("Fetched {} {} candles via REST API", candles.size(), timeframe);
            }
            return;
        }

        // Convert timestamps to YearMonth for Vision client
        YearMonth startMonth = YearMonth.from(
            Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));

        // Use BinanceVisionClient for bulk download + API backfill
        BinanceVisionClient visionClient = new BinanceVisionClient(dataStore);

        visionClient.syncWithApiBackfill(symbol, timeframe, startMonth, apiClient, cancelled,
            progress -> {
                // Re-check cache to get current coverage after each month is processed
                List<Candle> current = loadFromCache(symbol, timeframe, startTime, endTime);
                int percent = expectedBars > 0 ? (current.size() * 100) / expectedBars : 0;
                updatePageProgress(page, Math.min(95, percent));  // Cap at 95% until complete
                log.debug("Vision sync: {} - {} ({}/{} bars, {}%)",
                    progress.currentMonth(), progress.status(), current.size(), expectedBars, percent);
            });
    }

    @Override
    protected int getRecordSizeBytes() {
        // Candle: 1 long (8) + 7 doubles (56) + 1 int (4) + object overhead (~20) = ~88 bytes
        return 88;
    }

    /**
     * Convert timeframe string to interval in milliseconds.
     */
    private long getIntervalMs(String timeframe) {
        if (timeframe == null) return 3600000; // Default 1h

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
}
