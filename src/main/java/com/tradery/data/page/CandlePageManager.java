package com.tradery.data.page;

import com.tradery.data.BinanceClient;
import com.tradery.data.BinanceVisionClient;
import com.tradery.data.DataType;
import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.model.Candle;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

        // First try cache
        List<Candle> cached = loadFromCache(symbol, timeframe, startTime, endTime);

        // Check if we have sufficient coverage
        if (hasSufficientCoverage(cached, timeframe, startTime, endTime)) {
            log.debug("Cache hit for {} {} ({} candles)", symbol, timeframe, cached.size());
            updatePageData(page, cached);
            return;
        }

        // Need to fetch missing data
        log.info("Fetching candles for {} {} ({} to {})",
            symbol, timeframe, startTime, endTime);

        // Sync via Vision + API
        fetchAndSync(symbol, timeframe, startTime, endTime);

        // Read fresh data from SQLite
        List<Candle> fresh = loadFromCache(symbol, timeframe, startTime, endTime);
        updatePageData(page, fresh);
    }

    /**
     * Load candles from SQLite cache.
     */
    private List<Candle> loadFromCache(String symbol, String timeframe,
                                        long startTime, long endTime) {
        try {
            return dataStore.getCandles(symbol, timeframe, startTime, endTime);
        } catch (Exception e) {
            log.debug("Cache read failed: {}", e.getMessage());
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
     */
    private void fetchAndSync(String symbol, String timeframe,
                               long startTime, long endTime) throws Exception {
        // Convert timestamps to YearMonth for Vision client
        YearMonth startMonth = YearMonth.from(
            Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));

        // Use BinanceVisionClient for bulk download + API backfill
        BinanceVisionClient visionClient = new BinanceVisionClient(dataStore);
        BinanceClient apiClient = new BinanceClient();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        visionClient.syncWithApiBackfill(symbol, timeframe, startMonth, apiClient, cancelled,
            progress -> log.debug("Vision sync: {} - {}", progress.currentMonth(), progress.status()));
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
