package com.tradery.data;

import com.tradery.model.Candle;
import com.tradery.model.FetchProgress;
import com.tradery.model.Gap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Stores and retrieves candle data as CSV files.
 * Files organized by symbol/resolution/year-month.csv
 *
 * Claude Code can directly read these files.
 */
public class CandleStore {

    private static final Logger log = LoggerFactory.getLogger(CandleStore.class);
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String CSV_HEADER = "timestamp,open,high,low,close,volume";

    private final File dataDir;
    private final BinanceClient binanceClient;

    // Cancellation support for long-running fetches
    private final AtomicBoolean fetchCancelled = new AtomicBoolean(false);
    private Consumer<FetchProgress> progressCallback;

    public CandleStore() {
        this(new BinanceClient());
    }

    public CandleStore(BinanceClient binanceClient) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.binanceClient = binanceClient;

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Cancel any ongoing fetch operation.
     * Partial data will be saved as .partial.csv
     */
    public void cancelCurrentFetch() {
        fetchCancelled.set(true);
    }

    /**
     * Set a callback to receive fetch progress updates.
     */
    public void setProgressCallback(Consumer<FetchProgress> callback) {
        this.progressCallback = callback;
    }

    /**
     * Check if a fetch is currently cancelled.
     */
    public boolean isFetchCancelled() {
        return fetchCancelled.get();
    }

    /**
     * Get candles for a symbol and resolution between two dates.
     * Fetches from Binance if not cached locally.
     * Smart caching: only fetches missing data, skips complete historical months.
     */
    public List<Candle> getCandles(String symbol, String resolution, long startTime, long endTime)
            throws IOException {

        // Reset cancellation flag at start of new fetch
        fetchCancelled.set(false);

        List<Candle> allCandles = new ArrayList<>();
        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        symbolDir.mkdirs();

        // Current month - may have incomplete data
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String currentMonth = now.format(YEAR_MONTH);

        // Iterate through each month in the range
        LocalDate start = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        LocalDate end = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();

        LocalDate current = start;
        while (!current.isAfter(end)) {
            // Check for cancellation
            if (fetchCancelled.get()) {
                log.debug("Fetch cancelled by user");
                break;
            }

            String monthKey = current.format(YEAR_MONTH);

            // Check for both complete and partial files
            File completeFile = new File(symbolDir, monthKey + ".csv");
            File partialFile = new File(symbolDir, monthKey + ".partial.csv");
            File monthFile = completeFile.exists() ? completeFile :
                             partialFile.exists() ? partialFile : null;

            // Calculate month boundaries
            long monthStart = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            LocalDate nextMonth = current.plusMonths(1);
            long monthEnd = nextMonth.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

            // Clamp to requested range
            long fetchStart = Math.max(monthStart, startTime);
            long fetchEnd = Math.min(monthEnd, endTime);

            boolean isCurrentMonth = monthKey.equals(currentMonth);
            boolean fileExists = monthFile != null;

            if (fileExists && !isCurrentMonth && completeFile.exists()) {
                // Historical month with complete data - use cache, don't refetch
                log.debug("Using cached data for {}", monthKey);
                allCandles.addAll(loadCsvFile(monthFile));
            } else if (fileExists && isCurrentMonth) {
                // Current month - check if we need to update
                List<Candle> cached = loadCsvFile(monthFile);
                long lastCachedTime = cached.isEmpty() ? 0 : cached.get(cached.size() - 1).timestamp();

                // Only fetch if last cached candle is old (more than resolution interval)
                long resolutionMs = getResolutionMs(resolution);
                if (System.currentTimeMillis() - lastCachedTime > resolutionMs * 2) {
                    log.debug("Updating current month {} from {}", monthKey, lastCachedTime);
                    List<Candle> fresh = binanceClient.fetchAllKlines(symbol, resolution, lastCachedTime, fetchEnd,
                            fetchCancelled, progressCallback);
                    boolean wasCancelled = fetchCancelled.get();
                    saveToCache(symbol, resolution, fresh, wasCancelled);
                    allCandles.addAll(loadCsvFile(completeFile.exists() ? completeFile : partialFile)); // Reload merged data
                } else {
                    log.debug("Using recent cached data for {}", monthKey);
                    allCandles.addAll(cached);
                }
            } else if (fileExists && partialFile.exists()) {
                // Historical partial file - complete it by fetching ALL missing data
                List<Candle> cached = loadCsvFile(partialFile);
                long firstCachedTime = cached.isEmpty() ? Long.MAX_VALUE : cached.get(0).timestamp();
                long lastCachedTime = cached.isEmpty() ? 0 : cached.get(cached.size() - 1).timestamp();
                boolean needsFetch = false;

                // Fetch missing data BEFORE first cached candle (gap at start)
                if (fetchStart < firstCachedTime) {
                    log.info("Completing partial {} - fetching data before {}", monthKey, firstCachedTime);
                    List<Candle> beforeData = binanceClient.fetchAllKlines(symbol, resolution, fetchStart, firstCachedTime,
                            fetchCancelled, progressCallback);
                    if (!beforeData.isEmpty()) {
                        saveToCache(symbol, resolution, beforeData, fetchCancelled.get());
                        needsFetch = true;
                    }
                }

                // Fetch missing data AFTER last cached candle (gap at end)
                if (lastCachedTime < fetchEnd && !fetchCancelled.get()) {
                    log.info("Completing partial {} - fetching data after {}", monthKey, lastCachedTime);
                    List<Candle> afterData = binanceClient.fetchAllKlines(symbol, resolution, lastCachedTime, fetchEnd,
                            fetchCancelled, progressCallback);
                    if (!afterData.isEmpty()) {
                        saveToCache(symbol, resolution, afterData, fetchCancelled.get());
                        needsFetch = true;
                    }
                }

                // Reload merged data or use cached
                if (needsFetch) {
                    allCandles.addAll(loadCsvFile(partialFile.exists() ? partialFile : completeFile));
                } else {
                    allCandles.addAll(cached);
                }
            } else {
                // No cache - fetch from Binance
                log.info("Fetching {} from Binance...", monthKey);
                List<Candle> fresh = binanceClient.fetchAllKlines(symbol, resolution, fetchStart, fetchEnd,
                        fetchCancelled, progressCallback);
                boolean wasCancelled = fetchCancelled.get();
                saveToCache(symbol, resolution, fresh, wasCancelled);
                allCandles.addAll(fresh);

                if (wasCancelled) {
                    break;
                }
            }

            current = nextMonth;
        }

        // Filter to requested range and sort
        return allCandles.stream()
            .filter(c -> c.timestamp() >= startTime && c.timestamp() <= endTime)
            .distinct()
            .sorted((a, b) -> Long.compare(a.timestamp(), b.timestamp()))
            .toList();
    }

    /**
     * Get resolution in milliseconds
     */
    private long getResolutionMs(String resolution) {
        return switch (resolution) {
            case "1m" -> 60_000L;
            case "5m" -> 5 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "30m" -> 30 * 60_000L;
            case "1h" -> 60 * 60_000L;
            case "4h" -> 4 * 60 * 60_000L;
            case "1d" -> 24 * 60 * 60_000L;
            case "1w" -> 7 * 24 * 60 * 60_000L;
            default -> 60 * 60_000L; // Default to 1h
        };
    }

    /**
     * Load candles from local CSV cache
     */
    private List<Candle> loadFromCache(String symbol, String resolution, long startTime, long endTime) {
        List<Candle> candles = new ArrayList<>();
        File symbolDir = new File(dataDir, symbol + "/" + resolution);

        if (!symbolDir.exists()) {
            return candles;
        }

        // Determine which month files to load
        LocalDate start = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();

        LocalDate current = start.withDayOfMonth(1);
        while (!current.isAfter(end)) {
            String filename = current.format(YEAR_MONTH) + ".csv";
            File file = new File(symbolDir, filename);

            if (file.exists()) {
                try {
                    candles.addAll(loadCsvFile(file));
                } catch (IOException e) {
                    log.warn("Failed to load {}: {}", file, e.getMessage());
                }
            }

            current = current.plusMonths(1);
        }

        return candles;
    }

    /**
     * Save candles to local CSV cache (complete data).
     */
    private void saveToCache(String symbol, String resolution, List<Candle> candles) throws IOException {
        saveToCache(symbol, resolution, candles, false);
    }

    /**
     * Save candles to local CSV cache.
     *
     * @param symbol     Trading pair
     * @param resolution Time resolution
     * @param candles    Candles to save
     * @param isPartial  If true, save as .partial.csv instead of .csv
     */
    private void saveToCache(String symbol, String resolution, List<Candle> candles, boolean isPartial)
            throws IOException {
        if (candles.isEmpty()) return;

        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        symbolDir.mkdirs();

        // Group candles by year-month
        java.util.Map<String, List<Candle>> byMonth = new java.util.LinkedHashMap<>();

        for (Candle c : candles) {
            LocalDate date = Instant.ofEpochMilli(c.timestamp()).atZone(ZoneOffset.UTC).toLocalDate();
            String key = date.format(YEAR_MONTH);
            byMonth.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }

        // Write each month file
        for (var entry : byMonth.entrySet()) {
            String monthKey = entry.getKey();
            File completeFile = new File(symbolDir, monthKey + ".csv");
            File partialFile = new File(symbolDir, monthKey + ".partial.csv");

            // Determine which file to read existing data from
            File existingFile = completeFile.exists() ? completeFile :
                                partialFile.exists() ? partialFile : null;

            // Load existing data and merge
            List<Candle> existing = existingFile != null ? loadCsvFile(existingFile) : new ArrayList<>();
            List<Candle> merged = new ArrayList<>(mergeCandles(existing, entry.getValue(), 0, Long.MAX_VALUE));

            // Sort by timestamp
            merged.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));

            // Remove duplicates
            List<Candle> deduplicated = new ArrayList<>();
            long lastTs = -1;
            for (Candle c : merged) {
                if (c.timestamp() != lastTs) {
                    deduplicated.add(c);
                    lastTs = c.timestamp();
                }
            }

            // Determine if data is complete for this month
            YearMonth ym = YearMonth.parse(monthKey, YEAR_MONTH);
            boolean isComplete = !isPartial && isMonthComplete(deduplicated, resolution, ym);

            // Write to appropriate file
            File targetFile = isComplete ? completeFile : partialFile;
            writeCsvFile(targetFile, deduplicated);

            // Clean up: if we wrote to complete, remove partial; if partial, leave complete alone
            if (isComplete && partialFile.exists()) {
                partialFile.delete();
                log.info("Upgraded {} from partial to complete", monthKey);
            }
        }
    }

    /**
     * Check if candle data is complete for a month.
     * A month is complete when:
     * 1. It's not the current month
     * 2. We have the last candle of the month (within tolerance)
     * 3. We have at least 95% of expected candles
     */
    private boolean isMonthComplete(List<Candle> candles, String resolution, YearMonth month) {
        if (candles.isEmpty()) return false;

        // Current month is never considered complete
        if (month.equals(YearMonth.now(ZoneOffset.UTC))) return false;

        // Check if we have the LAST candle of the month
        long monthEndMs = month.plusMonths(1).atDay(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long lastCandleTs = candles.get(candles.size() - 1).timestamp();
        long resolutionMs = getResolutionMs(resolution);

        // Last candle should be within two resolution periods of month end
        // This handles the case where data was fetched mid-month and is incomplete
        if (monthEndMs - lastCandleTs > resolutionMs * 2) {
            return false;  // Missing end-of-month data
        }

        // Also check expected count (with tolerance)
        int expectedCandles = calculateExpectedCandles(resolution, month);
        return candles.size() >= expectedCandles * 0.95;
    }

    /**
     * Calculate expected number of candles for a month.
     */
    private int calculateExpectedCandles(String resolution, YearMonth month) {
        int daysInMonth = month.lengthOfMonth();
        long resolutionMs = getResolutionMs(resolution);
        long minutesInResolution = resolutionMs / 60_000;
        long minutesInMonth = daysInMonth * 24L * 60L;
        return (int) (minutesInMonth / minutesInResolution);
    }

    /**
     * Load candles from a CSV file
     */
    private List<Candle> loadCsvFile(File file) throws IOException {
        List<Candle> candles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Skip header line
                if (firstLine && line.startsWith("timestamp")) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                try {
                    candles.add(Candle.fromCsv(line));
                } catch (Exception e) {
                    log.warn("Failed to parse CSV line: {}", line);
                }
            }
        }

        return candles;
    }

    /**
     * Write candles to a CSV file
     */
    private void writeCsvFile(File file, List<Candle> candles) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("timestamp,open,high,low,close,volume");
            for (Candle c : candles) {
                writer.println(c.toCsv());
            }
        }
        log.debug("Saved {} candles to {}", candles.size(), file.getAbsolutePath());
    }

    /**
     * Merge two candle lists and filter to range
     */
    private List<Candle> mergeCandles(List<Candle> a, List<Candle> b, long startTime, long endTime) {
        java.util.Map<Long, Candle> map = new java.util.LinkedHashMap<>();

        for (Candle c : a) {
            map.put(c.timestamp(), c);
        }
        for (Candle c : b) {
            map.put(c.timestamp(), c);
        }

        return map.values().stream()
            .filter(c -> c.timestamp() >= startTime && c.timestamp() <= endTime)
            .sorted((x, y) -> Long.compare(x.timestamp(), y.timestamp()))
            .toList();
    }

    /**
     * Get the data directory path for a symbol
     */
    public Path getDataPath(String symbol, String resolution) {
        return new File(dataDir, symbol + "/" + resolution).toPath();
    }

    /**
     * Clear cached data for a symbol
     */
    public void clearCache(String symbol) throws IOException {
        File symbolDir = new File(dataDir, symbol);
        if (symbolDir.exists()) {
            Files.walk(symbolDir.toPath())
                .sorted((a, b) -> -a.compareTo(b))
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    /**
     * Repair a specific month by re-fetching all data.
     * Merges with existing data and upgrades partial to complete if possible.
     */
    public void repairMonth(String symbol, String resolution, YearMonth month) throws IOException {
        fetchCancelled.set(false);

        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        symbolDir.mkdirs();

        // Calculate month boundaries
        long monthStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

        log.info("Repairing {} for {} {}", month, symbol, resolution);

        // Fetch entire month
        List<Candle> fresh = binanceClient.fetchAllKlines(symbol, resolution, monthStart, monthEnd,
                fetchCancelled, progressCallback);

        boolean wasCancelled = fetchCancelled.get();
        saveToCache(symbol, resolution, fresh, wasCancelled);

        log.info("Repair complete for {}: {} candles", month, fresh.size());
    }

    /**
     * Repair specific gaps within a month.
     */
    public void repairGaps(String symbol, String resolution, YearMonth month, List<Gap> gaps) throws IOException {
        if (gaps.isEmpty()) return;

        fetchCancelled.set(false);
        List<Candle> allFresh = new ArrayList<>();

        for (Gap gap : gaps) {
            if (fetchCancelled.get()) break;

            log.debug("Repairing gap: {} - {}", gap.startTimestamp(), gap.endTimestamp());
            List<Candle> fresh = binanceClient.fetchAllKlines(symbol, resolution,
                    gap.startTimestamp(), gap.endTimestamp(),
                    fetchCancelled, progressCallback);
            allFresh.addAll(fresh);
        }

        boolean wasCancelled = fetchCancelled.get();
        saveToCache(symbol, resolution, allFresh, wasCancelled);

        log.info("Gap repair complete: {} candles fetched", allFresh.size());
    }

    /**
     * Delete cached data for a specific month.
     */
    public void deleteMonth(String symbol, String resolution, YearMonth month) {
        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        String monthKey = month.format(YEAR_MONTH);

        File completeFile = new File(symbolDir, monthKey + ".csv");
        File partialFile = new File(symbolDir, monthKey + ".partial.csv");

        if (completeFile.exists()) {
            completeFile.delete();
            log.debug("Deleted {}", completeFile.getAbsolutePath());
        }
        if (partialFile.exists()) {
            partialFile.delete();
            log.debug("Deleted {}", partialFile.getAbsolutePath());
        }
    }

    /**
     * Get the BinanceClient for external use.
     */
    public BinanceClient getBinanceClient() {
        return binanceClient;
    }
}
