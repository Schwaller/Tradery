package com.tradery.data;

import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.model.OpenInterest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Stores and retrieves open interest data as monthly CSV files.
 * Files organized by symbol/openinterest/yyyy-MM.csv (one file per month).
 *
 * Storage path: ~/.tradery/data/SYMBOL/openinterest/
 *
 * Open interest at 5-minute resolution: ~288 records per day, ~8,640 per month.
 * Binance limits historical data to 30 days, but cache persists indefinitely.
 */
public class OpenInterestStore {

    private static final Logger log = LoggerFactory.getLogger(OpenInterestStore.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String OI_DIR = "openinterest";
    private static final String CSV_HEADER = "symbol,timestamp,openInterest,openInterestValue";

    // 5-minute interval in milliseconds
    private static final long OI_INTERVAL = 5 * 60 * 1000;
    // Allow 1 minute tolerance for gap detection
    private static final long GAP_TOLERANCE = 60 * 1000;
    // Binance historical limit: 30 days
    private static final long MAX_HISTORICAL_DAYS = 30;

    private final File dataDir;
    private final OpenInterestClient client;
    private final SqliteDataStore sqliteStore;

    public OpenInterestStore() {
        this(new OpenInterestClient(), new SqliteDataStore());
    }

    public OpenInterestStore(OpenInterestClient client, SqliteDataStore sqliteStore) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = client;
        this.sqliteStore = sqliteStore;

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Get the open interest directory for a symbol.
     * Path: ~/.tradery/data/{symbol}/openinterest/
     */
    private File getOIDir(String symbol) {
        return new File(new File(dataDir, symbol), OI_DIR);
    }

    /**
     * Get open interest data for a time range, fetching from API if needed.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of open interest data sorted by time ascending
     */
    public List<OpenInterest> getOpenInterest(String symbol, long startTime, long endTime) throws IOException {
        return getOpenInterest(symbol, startTime, endTime, null);
    }

    /**
     * Get open interest data for a time range with progress callback.
     * Uses SQLite for fast cached reads. Fetches missing data from API if needed.
     */
    public List<OpenInterest> getOpenInterest(String symbol, long startTime, long endTime,
                                               Consumer<String> progress) throws IOException {
        // Check SQLite for cached data and find gaps
        List<long[]> gaps = findGapsInSqlite(symbol, startTime, endTime);

        if (gaps.isEmpty()) {
            log.debug("SQLite cache hit for {} open interest", symbol);
            return sqliteStore.getOpenInterest(symbol, startTime, endTime);
        }

        // Cap to Binance's 30-day limit
        long now = System.currentTimeMillis();
        long maxHistoryMs = MAX_HISTORICAL_DAYS * 24 * 60 * 60 * 1000;

        // Fetch each gap
        for (long[] gap : gaps) {
            long fetchStart = Math.max(gap[0], now - maxHistoryMs);
            long fetchEnd = gap[1];

            if (fetchStart >= fetchEnd) continue;

            int expectedRecords = (int) ((fetchEnd - fetchStart) / OI_INTERVAL);
            if (progress != null) {
                progress.accept("OI: Fetching " + expectedRecords + " records...");
            }

            try {
                List<OpenInterest> fetched = client.fetchOpenInterest(
                    symbol, fetchStart, fetchEnd, "5m", expectedRecords, progress);
                if (!fetched.isEmpty()) {
                    saveToSqlite(symbol, fetched);
                    markCoverage(symbol, fetchStart, fetchEnd, true);
                }
            } catch (Exception e) {
                log.warn("OI: Failed to fetch: {}", e.getMessage());
            }
        }

        if (progress != null) {
            progress.accept("OI: Ready");
        }

        return sqliteStore.getOpenInterest(symbol, startTime, endTime);
    }

    /**
     * Find gaps in SQLite coverage.
     */
    private List<long[]> findGapsInSqlite(String symbol, long startTime, long endTime) {
        try {
            return sqliteStore.findGaps(symbol, "open_interest", "default", startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to check SQLite coverage: {}", e.getMessage());
            List<long[]> gaps = new ArrayList<>();
            gaps.add(new long[]{startTime, endTime});
            return gaps;
        }
    }

    /**
     * Save open interest to SQLite.
     */
    private void saveToSqlite(String symbol, List<OpenInterest> data) {
        if (data.isEmpty()) return;
        try {
            sqliteStore.saveOpenInterest(symbol, data);
            log.debug("Saved {} OI records to SQLite for {}", data.size(), symbol);
        } catch (IOException e) {
            log.warn("Failed to save OI to SQLite: {}", e.getMessage());
        }
    }

    /**
     * Mark coverage in SQLite.
     */
    private void markCoverage(String symbol, long start, long end, boolean isComplete) {
        try {
            sqliteStore.addCoverage(symbol, "open_interest", "default", start, end, isComplete);
        } catch (IOException e) {
            log.warn("Failed to mark coverage: {}", e.getMessage());
        }
    }

    /**
     * Get open interest data from cache only (no API calls).
     * Returns immediately with whatever is available in SQLite cache.
     */
    public List<OpenInterest> getOpenInterestCacheOnly(String symbol, long startTime, long endTime) {
        try {
            return sqliteStore.getOpenInterest(symbol, startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to load OI from SQLite: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Clear cache for a symbol.
     */
    public void clearCache(String symbol) {
        File symbolDir = getOIDir(symbol);
        if (symbolDir.exists()) {
            File[] files = symbolDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            symbolDir.delete();
        }
    }

    /**
     * Get sync status for a time range.
     */
    public SyncStatus getSyncStatus(String symbol, long startTime, long endTime) {
        List<long[]> gaps = findGapsInSqlite(symbol, startTime, endTime);

        long durationHours = (endTime - startTime) / (60 * 60 * 1000);
        int expectedRecords = (int) (durationHours * 12); // 12 per hour at 5m intervals

        if (gaps.isEmpty()) {
            return new SyncStatus(true, expectedRecords, expectedRecords, startTime, endTime);
        }

        long gapDuration = gaps.stream().mapToLong(g -> g[1] - g[0]).sum();
        int missingRecords = (int) (gapDuration / OI_INTERVAL);
        int actualRecords = expectedRecords - missingRecords;

        return new SyncStatus(false, actualRecords, expectedRecords,
            gaps.get(0)[0], gaps.get(gaps.size() - 1)[1]);
    }

    /**
     * Sync status for UI display.
     */
    public record SyncStatus(
        boolean hasData,
        int recordsComplete,
        int recordsExpected,
        long gapStartTime,
        long gapEndTime
    ) {
        public String getStatusMessage() {
            if (hasData) {
                return "Data synced: " + recordsComplete + " records";
            } else if (recordsComplete > 0) {
                int pct = recordsExpected > 0 ? (recordsComplete * 100 / recordsExpected) : 0;
                return "Partial: " + recordsComplete + "/" + recordsExpected + " (" + pct + "%)";
            } else {
                return "Not synced";
            }
        }
    }
}
