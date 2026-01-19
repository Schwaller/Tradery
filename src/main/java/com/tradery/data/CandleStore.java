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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Stores and retrieves candle data as CSV files.
 * Files organized by symbol/resolution/year-month.csv
 *
 * Automatically uses Binance Vision (bulk ZIP downloads) for large date ranges
 * and REST API for small ranges. Vision is 10-100x faster for historical data.
 *
 * Claude Code can directly read these files.
 */
public class CandleStore {

    private static final Logger log = LoggerFactory.getLogger(CandleStore.class);
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String CSV_HEADER = "timestamp,open,high,low,close,volume";

    // Use Vision when estimated API calls exceed this threshold
    private static final int VISION_THRESHOLD_API_CALLS = 10;
    private static final String VISION_BASE_URL = "https://data.binance.vision/data/futures/um/monthly/klines";

    // Binance Futures started in September 2019 - no data exists before this
    private static final YearMonth BINANCE_FUTURES_START = YearMonth.of(2019, 9);

    private final File dataDir;
    private final BinanceClient binanceClient;
    private final okhttp3.OkHttpClient httpClient;

    // Cancellation support for long-running fetches
    private final AtomicBoolean fetchCancelled = new AtomicBoolean(false);
    private Consumer<FetchProgress> progressCallback;

    public CandleStore() {
        this(new BinanceClient());
    }

    public CandleStore(BinanceClient binanceClient) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.binanceClient = binanceClient;
        this.httpClient = HttpClientFactory.getClient();

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
     * Estimate how much of the requested range is not in cache.
     * Returns duration in milliseconds.
     */
    private long estimateUncachedDuration(String symbol, String resolution, long startTime, long endTime) {
        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        if (!symbolDir.exists()) {
            return endTime - startTime; // Nothing cached
        }

        long uncachedMs = 0;
        LocalDate start = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        LocalDate end = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            String monthKey = current.format(YEAR_MONTH);
            File completeFile = new File(symbolDir, monthKey + ".csv");
            File partialFile = new File(symbolDir, monthKey + ".partial.csv");

            if (!completeFile.exists() && !partialFile.exists()) {
                // This month is completely uncached
                long monthStart = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                LocalDate nextMonth = current.plusMonths(1);
                long monthEnd = nextMonth.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                uncachedMs += Math.min(monthEnd, endTime) - Math.max(monthStart, startTime);
            }

            current = current.plusMonths(1);
        }

        return uncachedMs;
    }

    /**
     * Check if Vision bulk download should be used based on estimated data volume.
     */
    private boolean shouldUseVision(String resolution, long startTime, long endTime) {
        long durationMs = endTime - startTime;
        long durationHours = durationMs / (1000 * 60 * 60);
        long durationDays = durationHours / 24;

        // Estimate candles based on timeframe
        long estimatedCandles = switch (resolution) {
            case "1m" -> durationHours * 60;
            case "3m" -> durationHours * 20;
            case "5m" -> durationHours * 12;
            case "15m" -> durationHours * 4;
            case "30m" -> durationHours * 2;
            case "1h" -> durationHours;
            case "2h" -> durationHours / 2;
            case "4h" -> durationHours / 4;
            case "6h" -> durationHours / 6;
            case "8h" -> durationHours / 8;
            case "12h" -> durationHours / 12;
            case "1d" -> durationHours / 24;
            case "3d" -> durationHours / 72;
            case "1w" -> durationHours / 168;
            default -> durationHours;
        };

        // API returns max 1000 records per request
        long estimatedApiCalls = (estimatedCandles + 999) / 1000;

        // Use Vision if we'd need more than threshold API calls
        // Also require at least 1 complete month for Vision to be worthwhile
        return estimatedApiCalls > VISION_THRESHOLD_API_CALLS && durationDays >= 28;
    }

    /**
     * Fetch candles via Binance Vision bulk download.
     * Downloads monthly ZIP files directly to CSV cache.
     */
    private List<Candle> fetchViaVision(String symbol, String resolution, long startTime, long endTime)
            throws IOException {

        List<Candle> allCandles = new ArrayList<>();
        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        symbolDir.mkdirs();

        // Get month range
        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC));
        YearMonth lastCompleteMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1);

        // Cap at last complete month (current month via API)
        if (endMonth.isAfter(lastCompleteMonth)) {
            endMonth = lastCompleteMonth;
        }

        // Cap at Binance Futures start (no data before Sept 2019)
        if (startMonth.isBefore(BINANCE_FUTURES_START)) {
            startMonth = BINANCE_FUTURES_START;
        }

        // Count months for progress
        int totalMonths = 0;
        YearMonth temp = startMonth;
        while (!temp.isAfter(endMonth)) {
            totalMonths++;
            temp = temp.plusMonths(1);
        }

        int completedMonths = 0;
        YearMonth current = startMonth;

        while (!current.isAfter(endMonth)) {
            if (fetchCancelled.get()) {
                log.debug("Vision fetch cancelled");
                break;
            }

            String monthKey = current.format(YEAR_MONTH);
            File completeFile = new File(symbolDir, monthKey + ".csv");

            // Skip if we already have complete data for this month
            if (completeFile.exists()) {
                log.debug("Vision: Skipping {} (already cached)", monthKey);
                allCandles.addAll(loadCsvFile(completeFile));
                completedMonths++;
                current = current.plusMonths(1);
                continue;
            }

            // Build Vision URL
            String url = String.format("%s/%s/%s/%s-%s-%s.zip",
                VISION_BASE_URL, symbol, resolution, symbol, resolution, monthKey);

            log.info("Vision: Downloading {}", monthKey);

            // Report progress
            if (progressCallback != null) {
                int pct = totalMonths > 0 ? (completedMonths * 100) / totalMonths : 0;
                progressCallback.accept(new FetchProgress(completedMonths, totalMonths,
                    "Vision: Downloading " + monthKey + "..."));
            }

            try {
                List<Candle> monthCandles = downloadVisionMonth(url);

                if (!monthCandles.isEmpty()) {
                    // Save to CSV cache
                    writeCsvFile(completeFile, monthCandles);
                    allCandles.addAll(monthCandles);
                    log.info("Vision: Saved {} candles for {}", monthCandles.size(), monthKey);
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    log.debug("Vision: Month {} not available", monthKey);
                } else {
                    log.warn("Vision: Failed to download {}: {}", monthKey, e.getMessage());
                }
            }

            completedMonths++;
            current = current.plusMonths(1);
        }

        return allCandles;
    }

    /**
     * Download and parse a single month's ZIP from Vision.
     */
    private List<Candle> downloadVisionMonth(String url) throws IOException {
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url(url)
            .get()
            .build();

        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new IOException("404 Not Found");
            }
            if (!response.isSuccessful()) {
                throw new IOException("Download failed: " + response.code());
            }

            return parseVisionZip(response.body().byteStream());
        }
    }

    /**
     * Parse Vision ZIP file and extract candles.
     */
    private List<Candle> parseVisionZip(InputStream inputStream) throws IOException {
        List<Candle> candles = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".csv")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String line;
                    boolean isHeader = true;

                    while ((line = reader.readLine()) != null) {
                        if (isHeader) {
                            isHeader = false;
                            // Skip header if it looks like one
                            if (line.startsWith("open") || !Character.isDigit(line.charAt(0))) {
                                continue;
                            }
                        }
                        if (!line.isBlank()) {
                            try {
                                candles.add(parseVisionKline(line));
                            } catch (Exception e) {
                                log.warn("Failed to parse Vision kline: {}", line);
                            }
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        // Sort by timestamp
        candles.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));
        return candles;
    }

    /**
     * Parse Vision kline CSV line.
     * Format: open_time,open,high,low,close,volume,close_time,...
     */
    private Candle parseVisionKline(String line) {
        String[] parts = line.split(",");
        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid kline: " + line);
        }
        return new Candle(
            Long.parseLong(parts[0].trim()),
            Double.parseDouble(parts[1].trim()),
            Double.parseDouble(parts[2].trim()),
            Double.parseDouble(parts[3].trim()),
            Double.parseDouble(parts[4].trim()),
            Double.parseDouble(parts[5].trim())
        );
    }

    /**
     * Get candles for a symbol and resolution between two dates.
     * Fetches from Binance if not cached locally.
     * Smart caching: only fetches missing data, skips complete historical months.
     *
     * Automatically uses Binance Vision (bulk ZIP downloads) for large date ranges
     * when the estimated API calls exceed the threshold.
     */
    public List<Candle> getCandles(String symbol, String resolution, long startTime, long endTime)
            throws IOException {

        // Reset cancellation flag at start of new fetch
        fetchCancelled.set(false);

        log.info("getCandles {} {} from {} to {}", symbol, resolution,
            Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate(),
            Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate());

        List<Candle> allCandles = new ArrayList<>();
        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        symbolDir.mkdirs();

        // Check if we should use Vision for bulk download
        // Only use Vision if we have significant uncached data (>= 1 month)
        long uncachedMs = estimateUncachedDuration(symbol, resolution, startTime, endTime);
        if (shouldUseVision(resolution, startTime, startTime + uncachedMs) && uncachedMs > 28L * 24 * 60 * 60 * 1000) {
            log.info("Using Vision bulk download for {} {} (large uncached range)", symbol, resolution);
            try {
                // Vision downloads and caches data - we don't add to allCandles here
                // The regular iteration below will read from cache
                fetchViaVision(symbol, resolution, startTime, endTime);
            } catch (Exception e) {
                log.warn("Vision download failed, falling back to API: {}", e.getMessage());
                // Continue with API-based fetch below
            }
        }

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
            YearMonth currentYearMonth = YearMonth.from(current);

            // Skip months before Binance Futures existed (Sept 2019)
            if (currentYearMonth.isBefore(BINANCE_FUTURES_START)) {
                log.trace("Skipping {} - before Binance Futures start", monthKey);
                current = current.plusMonths(1).withDayOfMonth(1);
                continue;
            }

            // Check for empty marker (month has no data available)
            if (hasEmptyMarker(symbolDir, monthKey)) {
                log.trace("Skipping {} - empty marker (no data available)", monthKey);
                current = current.plusMonths(1).withDayOfMonth(1);
                continue;
            }

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
                log.trace("Cache hit: {}", monthKey);
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
                // Historical partial file - check if it covers the requested range
                List<Candle> cached = loadCsvFile(partialFile);
                long firstCachedTime = cached.isEmpty() ? Long.MAX_VALUE : cached.get(0).timestamp();
                long lastCachedTime = cached.isEmpty() ? 0 : cached.get(cached.size() - 1).timestamp();
                long resolutionMs = getResolutionMs(resolution);

                // If cached data covers the requested range (within tolerance), just use it
                boolean coversStart = firstCachedTime <= fetchStart + resolutionMs;
                boolean coversEnd = lastCachedTime >= fetchEnd - resolutionMs;

                if (coversStart && coversEnd) {
                    // Cached data covers requested range - no need to fetch
                    log.trace("Partial {} covers requested range, using cache", monthKey);
                    allCandles.addAll(cached);
                } else {
                    // Need to fill gaps
                    boolean needsFetch = false;

                    // Fetch missing data BEFORE first cached candle (gap at start)
                    if (!coversStart) {
                        log.info("Completing partial {} - fetching data before {}", monthKey, firstCachedTime);
                        List<Candle> beforeData = binanceClient.fetchAllKlines(symbol, resolution, fetchStart, firstCachedTime,
                                fetchCancelled, progressCallback);
                        if (!beforeData.isEmpty()) {
                            saveToCache(symbol, resolution, beforeData, fetchCancelled.get());
                            needsFetch = true;
                        }
                    }

                    // Fetch missing data AFTER last cached candle (gap at end)
                    if (!coversEnd && !fetchCancelled.get()) {
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
                }
            } else {
                // No cache - fetch from Binance
                log.info("Fetching {} from Binance...", monthKey);
                List<Candle> fresh = binanceClient.fetchAllKlines(symbol, resolution, fetchStart, fetchEnd,
                        fetchCancelled, progressCallback);
                boolean wasCancelled = fetchCancelled.get();

                if (fresh.isEmpty() && !wasCancelled && !isCurrentMonth) {
                    // API returned no data for a historical month - save empty marker
                    // to prevent repeated fetches (e.g., months before data existed)
                    log.debug("No data from API for {} - saving empty marker", monthKey);
                    saveEmptyMarker(symbolDir, monthKey);
                } else {
                    saveToCache(symbol, resolution, fresh, wasCancelled);
                    allCandles.addAll(fresh);
                }

                if (wasCancelled) {
                    break;
                }
            }

            current = nextMonth;
        }

        // Filter to requested range and sort
        List<Candle> result = allCandles.stream()
            .filter(c -> c.timestamp() >= startTime && c.timestamp() <= endTime)
            .distinct()
            .sorted((a, b) -> Long.compare(a.timestamp(), b.timestamp()))
            .toList();

        log.info("getCandles complete: {} candles loaded", result.size());
        return result;
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
     * Get candles from cache only, without fetching from API.
     * Use this for overview displays where speed matters more than completeness.
     * Returns whatever is available in cache, may be empty or incomplete.
     */
    public List<Candle> getCandlesCacheOnly(String symbol, String resolution, long startTime, long endTime) {
        return loadFromCache(symbol, resolution, startTime, endTime).stream()
            .filter(c -> c.timestamp() >= startTime && c.timestamp() <= endTime)
            .sorted((a, b) -> Long.compare(a.timestamp(), b.timestamp()))
            .toList();
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
     * Load candles from a CSV file.
     * Returns null if file contains old-format candles (missing extended volume fields)
     * to trigger a refetch with the new format.
     */
    private List<Candle> loadCsvFile(File file) throws IOException {
        List<Candle> candles = new ArrayList<>();
        boolean hasExtendedFields = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Skip header line - but check if it has extended fields
                if (firstLine && line.startsWith("timestamp")) {
                    // New format has 10 columns, old format has 6
                    hasExtendedFields = line.split(",").length >= 10;
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                try {
                    Candle c = Candle.fromCsv(line);
                    candles.add(c);
                    // Also check first data row for extended fields
                    if (candles.size() == 1 && c.hasExtendedVolume()) {
                        hasExtendedFields = true;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse CSV line: {}", line);
                }
            }
        }

        // If file has old format without extended volume fields, return null to trigger refetch
        if (!candles.isEmpty() && !hasExtendedFields) {
            log.info("CSV file {} has old format (missing extended volume fields), will refetch", file.getName());
            // Delete the old file so it gets refetched
            if (file.delete()) {
                log.debug("Deleted old-format file: {}", file.getName());
            }
            return null;
        }

        return candles;
    }

    /**
     * Write candles to a CSV file
     */
    private void writeCsvFile(File file, List<Candle> candles) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Use extended header if candles have extended fields
            boolean hasExtended = !candles.isEmpty() && candles.get(0).hasExtendedVolume();
            if (hasExtended) {
                writer.println("timestamp,open,high,low,close,volume,tradeCount,quoteVolume,takerBuyVolume,takerBuyQuoteVolume");
            } else {
                writer.println("timestamp,open,high,low,close,volume");
            }
            for (Candle c : candles) {
                writer.println(c.toCsv());
            }
        }
        log.debug("Saved {} candles to {}", candles.size(), file.getAbsolutePath());
    }

    /**
     * Save an empty marker file to indicate a month has no data.
     * This prevents repeated API calls for months that don't have data.
     */
    private void saveEmptyMarker(File symbolDir, String monthKey) {
        File emptyFile = new File(symbolDir, monthKey + ".empty");
        try {
            emptyFile.createNewFile();
        } catch (IOException e) {
            log.warn("Failed to create empty marker for {}: {}", monthKey, e.getMessage());
        }
    }

    /**
     * Check if a month has an empty marker (no data available from API).
     */
    private boolean hasEmptyMarker(File symbolDir, String monthKey) {
        return new File(symbolDir, monthKey + ".empty").exists();
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
