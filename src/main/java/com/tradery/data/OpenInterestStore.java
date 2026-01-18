package com.tradery.data;

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

    public OpenInterestStore() {
        this(new OpenInterestClient());
    }

    public OpenInterestStore(OpenInterestClient client) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = client;

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
     * Fetches missing data from API if cache is incomplete.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @param progress  Progress callback for status updates (can be null)
     * @return List of open interest data sorted by time ascending
     */
    public List<OpenInterest> getOpenInterest(String symbol, long startTime, long endTime,
                                               Consumer<String> progress) throws IOException {
        // Load cached data
        if (progress != null) {
            progress.accept("OI: Loading cached data...");
        }

        Map<Long, OpenInterest> oiMap = new TreeMap<>();
        loadCachedData(symbol, startTime, endTime, oiMap);

        if (progress != null && !oiMap.isEmpty()) {
            progress.accept("OI: Loaded " + oiMap.size() + " cached records");
        }

        // Calculate total missing records based on all gaps
        int missingRecords = calculateMissingRecords(oiMap, startTime, endTime);

        // Find gaps and fetch missing data
        long gapStart = findFirstGap(oiMap, startTime, endTime);
        if (gapStart > 0 && missingRecords > 0) {
            // Cap to Binance's 30-day limit
            long now = System.currentTimeMillis();
            long maxHistoryMs = MAX_HISTORICAL_DAYS * 24 * 60 * 60 * 1000;
            long fetchStart = Math.max(gapStart, now - maxHistoryMs);

            if (fetchStart < endTime) {
                if (progress != null) {
                    progress.accept("OI: Fetching " + missingRecords + " missing records...");
                }
                try {
                    // Pass the calculated expected count to the client
                    List<OpenInterest> fetched = client.fetchOpenInterest(
                        symbol, fetchStart, endTime, "5m", missingRecords, progress);
                    for (OpenInterest oi : fetched) {
                        oiMap.put(oi.timestamp(), oi);
                    }

                    // Save to cache
                    if (!fetched.isEmpty()) {
                        saveToCache(symbol, new ArrayList<>(oiMap.values()));
                    }
                } catch (Exception e) {
                    log.warn("OI: Failed to fetch: {}", e.getMessage());
                    // Continue with cached data
                }
            }
        }

        // Filter to requested range
        List<OpenInterest> result = new ArrayList<>();
        for (OpenInterest oi : oiMap.values()) {
            if (oi.timestamp() >= startTime && oi.timestamp() <= endTime) {
                result.add(oi);
            }
        }

        if (progress != null) {
            progress.accept("OI: Ready (" + result.size() + " records)");
        }

        return result;
    }

    /**
     * Load cached open interest data from monthly CSV files.
     */
    private void loadCachedData(String symbol, long startTime, long endTime,
                                 Map<Long, OpenInterest> oiMap) {
        File symbolDir = getOIDir(symbol);
        if (!symbolDir.exists()) {
            return;
        }

        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC));

        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            String monthKey = current.format(MONTH_FORMAT);
            File cacheFile = new File(symbolDir, monthKey + ".csv");

            if (cacheFile.exists()) {
                try {
                    loadCsvFile(cacheFile, oiMap);
                } catch (IOException e) {
                    log.warn("Error loading OI cache: {} - {}", cacheFile, e.getMessage());
                }
            }

            current = current.plusMonths(1);
        }
    }

    /**
     * Load open interest data from a CSV file.
     */
    private void loadCsvFile(File file, Map<Long, OpenInterest> oiMap) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("symbol")) continue; // Skip header
                }
                if (line.isBlank()) continue;

                try {
                    OpenInterest oi = OpenInterest.fromCsv(line);
                    oiMap.put(oi.timestamp(), oi);
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }
    }

    /**
     * Find the first gap in OI data (5 minutes between records expected).
     * Returns 0 if no gap found.
     */
    private long findFirstGap(Map<Long, OpenInterest> oiMap, long startTime, long endTime) {
        if (oiMap.isEmpty()) {
            return startTime;
        }

        long maxGap = OI_INTERVAL + GAP_TOLERANCE;

        List<Long> times = new ArrayList<>(oiMap.keySet());

        // Check if we have data from the start
        if (times.get(0) > startTime + maxGap) {
            return startTime;
        }

        // Check for gaps between records
        for (int i = 1; i < times.size(); i++) {
            long gap = times.get(i) - times.get(i - 1);
            if (gap > maxGap) {
                return times.get(i - 1) + 1;
            }
        }

        // Check if we have data up to the end
        long lastTime = times.get(times.size() - 1);
        long now = System.currentTimeMillis();
        if (lastTime < endTime - maxGap && lastTime < now - maxGap) {
            return lastTime + 1;
        }

        return 0; // No gap
    }

    /**
     * Calculate total missing records by analyzing all gaps in the data.
     * This accounts for partial caches where data exists in some ranges but not others.
     *
     * @return Number of 5-minute records that need to be fetched
     */
    private int calculateMissingRecords(Map<Long, OpenInterest> oiMap, long startTime, long endTime) {
        long now = System.currentTimeMillis();
        // Cap to Binance's 30-day limit
        long maxHistoryMs = MAX_HISTORICAL_DAYS * 24 * 60 * 60 * 1000;
        long effectiveStart = Math.max(startTime, now - maxHistoryMs);

        if (effectiveStart >= endTime) {
            return 0; // Nothing fetchable
        }

        if (oiMap.isEmpty()) {
            // No cached data - full range needs fetching
            return (int) ((endTime - effectiveStart) / OI_INTERVAL);
        }

        long maxGap = OI_INTERVAL + GAP_TOLERANCE;
        List<Long> times = new ArrayList<>(oiMap.keySet());
        long totalMissingTime = 0;

        // Gap at the beginning: from effectiveStart to first cached record
        long firstCached = times.get(0);
        if (firstCached > effectiveStart + maxGap) {
            totalMissingTime += (firstCached - effectiveStart);
        }

        // Gaps between cached records
        for (int i = 1; i < times.size(); i++) {
            long gap = times.get(i) - times.get(i - 1);
            if (gap > maxGap) {
                totalMissingTime += gap;
            }
        }

        // Gap at the end: from last cached record to endTime
        long lastCached = times.get(times.size() - 1);
        if (lastCached < endTime - maxGap && lastCached < now - maxGap) {
            long gapEnd = Math.min(endTime, now);
            totalMissingTime += (gapEnd - lastCached);
        }

        return (int) (totalMissingTime / OI_INTERVAL);
    }

    /**
     * Save open interest data to monthly CSV files.
     */
    private void saveToCache(String symbol, List<OpenInterest> data) throws IOException {
        if (data.isEmpty()) {
            log.debug("OI Cache: No data to save");
            return;
        }

        File symbolDir = getOIDir(symbol);
        log.debug("OI Cache: Saving {} records to {}", data.size(), symbolDir.getAbsolutePath());
        if (!symbolDir.exists()) {
            boolean created = symbolDir.mkdirs();
            log.debug("OI Cache: Created directory: {}", created);
        }

        // Group data by month
        Map<YearMonth, List<OpenInterest>> byMonth = new TreeMap<>();
        for (OpenInterest oi : data) {
            YearMonth month = YearMonth.from(
                Instant.ofEpochMilli(oi.timestamp()).atZone(ZoneOffset.UTC)
            );
            byMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(oi);
        }

        // Write each month's file
        for (Map.Entry<YearMonth, List<OpenInterest>> entry : byMonth.entrySet()) {
            String monthKey = entry.getKey().format(MONTH_FORMAT);
            File cacheFile = new File(symbolDir, monthKey + ".csv");

            // Load existing data and merge
            Map<Long, OpenInterest> merged = new TreeMap<>();
            if (cacheFile.exists()) {
                loadCsvFile(cacheFile, merged);
            }
            for (OpenInterest oi : entry.getValue()) {
                merged.put(oi.timestamp(), oi);
            }

            // Write merged data
            try (PrintWriter writer = new PrintWriter(new FileWriter(cacheFile))) {
                writer.println(CSV_HEADER);
                for (OpenInterest oi : merged.values()) {
                    writer.println(oi.toCsv());
                }
            }
        }
    }

    /**
     * Schedule a background update to fetch new OI data without blocking.
     */
    private void scheduleBackgroundUpdate(String symbol, long startTime, long endTime) {
        new Thread(() -> {
            try {
                long durationHours = (endTime - startTime) / (60 * 60 * 1000);
                log.info("OI: Background fetch starting for {} ({} hours of data)", symbol, durationHours);
                List<OpenInterest> fetched = client.fetchOpenInterest(symbol, startTime, endTime,
                    msg -> log.debug("OI: {}", msg));
                if (fetched.isEmpty()) {
                    log.info("OI: Background fetch returned 0 records (Binance may not have data for this range)");
                } else {
                    saveToCache(symbol, fetched);
                    log.info("OI: Background fetch saved {} records", fetched.size());
                }
            } catch (Exception e) {
                log.error("OI: Background fetch failed: {}", e.getMessage(), e);
            }
        }, "OI-Background-Update").start();
    }

    /**
     * Get open interest data from cache only (no API calls).
     * Returns immediately with whatever is available in local cache.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of open interest data sorted by time ascending (may be incomplete)
     */
    public List<OpenInterest> getOpenInterestCacheOnly(String symbol, long startTime, long endTime) {
        Map<Long, OpenInterest> oiMap = new TreeMap<>();
        loadCachedData(symbol, startTime, endTime, oiMap);

        List<OpenInterest> result = new ArrayList<>();
        for (OpenInterest oi : oiMap.values()) {
            if (oi.timestamp() >= startTime && oi.timestamp() <= endTime) {
                result.add(oi);
            }
        }
        return result;
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
        Map<Long, OpenInterest> oiMap = new TreeMap<>();
        loadCachedData(symbol, startTime, endTime, oiMap);

        if (oiMap.isEmpty()) {
            long durationHours = (endTime - startTime) / (60 * 60 * 1000);
            int expectedRecords = (int) (durationHours * 12); // 12 per hour at 5m intervals
            return new SyncStatus(false, 0, expectedRecords, startTime, endTime);
        }

        // Calculate expected records (12 per hour for 5m data)
        long durationHours = (endTime - startTime) / (60 * 60 * 1000);
        int expectedRecords = (int) (durationHours * 12);
        int actualRecords = oiMap.size();

        // Find first gap
        long gapStart = findFirstGap(oiMap, startTime, endTime);
        boolean hasData = gapStart <= 0;

        return new SyncStatus(hasData, actualRecords, expectedRecords,
            gapStart > 0 ? gapStart : startTime,
            gapStart > 0 ? endTime : endTime);
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
