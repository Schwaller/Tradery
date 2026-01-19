package com.tradery.data;

import com.tradery.model.AggTrade;
import com.tradery.model.FetchProgress;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Stores and retrieves aggregated trade data as hourly CSV files in daily folders.
 *
 * Automatically uses Binance Vision (bulk ZIP downloads) for large date ranges
 * (>= 3 days). AggTrades are massive - a single day can have 500K+ trades.
 *
 * Storage structure:
 * ~/.tradery/data/BTCUSDT/aggTrades/
 * ├── 2026-01-11/
 * │   ├── 00.csv  (complete hour)
 * │   ├── 01.csv
 * │   ├── ...
 * │   └── 23.partial.csv  (current hour, incomplete)
 * └── 2026-01-12/
 *     └── ...
 */
public class AggTradesStore {

    private static final Logger log = LoggerFactory.getLogger(AggTradesStore.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH");
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String AGG_TRADES_DIR = "aggTrades";
    private static final String CSV_HEADER = "aggTradeId,price,quantity,firstTradeId,lastTradeId,timestamp,isBuyerMaker";
    private static final long ONE_HOUR_MS = 60 * 60 * 1000;

    // Use Vision for >= 3 days (aggTrades are massive - 500K+ trades/day)
    private static final int VISION_THRESHOLD_DAYS = 3;
    // Use DAILY Vision files - monthly files are too large (10-50GB each)
    private static final String VISION_BASE_URL = "https://data.binance.vision/data/futures/um/daily/aggTrades";

    private final File dataDir;
    private final AggTradesClient client;
    private final OkHttpClient httpClient;
    private final OkHttpClient bulkDownloadClient;

    // Cancellation support
    private final AtomicBoolean fetchCancelled = new AtomicBoolean(false);
    private Consumer<FetchProgress> progressCallback;

    public AggTradesStore() {
        this(new AggTradesClient());
    }

    public AggTradesStore(AggTradesClient client) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = client;
        this.httpClient = HttpClientFactory.getClient();
        this.bulkDownloadClient = HttpClientFactory.getBulkDownloadClient();

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Get the aggTrades base directory for a symbol.
     * Path: ~/.tradery/data/{symbol}/aggTrades/
     */
    private File getAggTradesDir(String symbol) {
        return new File(new File(dataDir, symbol), AGG_TRADES_DIR);
    }

    /**
     * Get the daily folder for a specific date.
     * Path: ~/.tradery/data/{symbol}/aggTrades/{yyyy-MM-dd}/
     */
    private File getDayDir(String symbol, LocalDate date) {
        return new File(getAggTradesDir(symbol), date.format(DATE_FORMAT));
    }

    /**
     * Get the hourly file path.
     * @param complete true for complete file (.csv), false for partial (.partial.csv)
     */
    private File getHourFile(String symbol, LocalDateTime dateTime, boolean complete) {
        File dayDir = getDayDir(symbol, dateTime.toLocalDate());
        String hourStr = dateTime.format(HOUR_FORMAT);
        String filename = complete ? hourStr + ".csv" : hourStr + ".partial.csv";
        return new File(dayDir, filename);
    }

    /**
     * Cancel any ongoing fetch operation.
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
     * Check if Vision bulk download should be used.
     * AggTrades are massive - use Vision for >= 3 days.
     */
    private boolean shouldUseVision(long startTime, long endTime) {
        long durationMs = endTime - startTime;
        long durationDays = durationMs / (24 * 60 * 60 * 1000);
        return durationDays >= VISION_THRESHOLD_DAYS;
    }

    /**
     * Estimate uncached duration for aggTrades.
     */
    private long estimateUncachedDuration(String symbol, long startTime, long endTime) {
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC)
            .withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneOffset.UTC);

        long uncachedMs = 0;
        LocalDateTime current = start;

        while (!current.isAfter(end)) {
            File completeFile = getHourFile(symbol, current, true);
            if (!completeFile.exists()) {
                uncachedMs += ONE_HOUR_MS;
            }
            current = current.plusHours(1);
        }

        return uncachedMs;
    }

    /**
     * Fetch aggTrades via Binance Vision bulk download.
     * Uses DAILY ZIP files (not monthly - those are 10-50GB each).
     * Streams directly to hourly cache files to avoid memory issues.
     */
    private void fetchViaVision(String symbol, long startTime, long endTime) throws IOException {
        LocalDate startDate = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        // Cap at yesterday (today's data is incomplete)
        if (endDate.isAfter(yesterday)) {
            endDate = yesterday;
        }

        // Count days for progress
        int totalDays = 0;
        LocalDate temp = startDate;
        while (!temp.isAfter(endDate)) {
            totalDays++;
            temp = temp.plusDays(1);
        }

        int completedDays = 0;
        LocalDate current = startDate;

        // Track progress state for combining day + file progress
        final int finalTotalDays = totalDays;
        final int[] currentDayIndex = {0};

        while (!current.isAfter(endDate)) {
            if (fetchCancelled.get()) {
                log.debug("Vision fetch cancelled");
                break;
            }

            String dateKey = current.format(DATE_FORMAT);

            // Check if day is already fully cached (all 24 hours)
            if (isDayFullyCached(symbol, current)) {
                log.trace("Vision: Skipping {} (already cached)", dateKey);
                completedDays++;
                currentDayIndex[0] = completedDays;
                current = current.plusDays(1);
                continue;
            }

            // Build Vision URL for daily file
            String url = String.format("%s/%s/%s-aggTrades-%s.zip",
                VISION_BASE_URL, symbol, symbol, dateKey);

            log.info("Vision: Downloading aggTrades {}", dateKey);
            currentDayIndex[0] = completedDays;

            // Report progress - starting this day
            if (progressCallback != null) {
                int pct = finalTotalDays > 0 ? (completedDays * 100) / finalTotalDays : 0;
                progressCallback.accept(new FetchProgress(pct, 100,
                    String.format("Day %d/%d: %s connecting...", completedDays + 1, finalTotalDays, dateKey)));
            }

            try {
                // Stream directly to hourly files without loading all into memory
                // Pass progress info so file download can report combined progress
                int tradesWritten = downloadAndStreamToHourlyFiles(symbol, url, current,
                    currentDayIndex[0], finalTotalDays);
                if (tradesWritten > 0) {
                    log.info("Vision: Saved {} aggTrades for {}", formatCount(tradesWritten), dateKey);
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    log.debug("Vision: Day {} not available", dateKey);
                } else {
                    log.warn("Vision: Failed to download {}: {}", dateKey, e.getMessage());
                }
            }

            completedDays++;
            current = current.plusDays(1);
        }
    }

    /**
     * Check if a day is fully cached (all 24 hours have complete files).
     */
    private boolean isDayFullyCached(String symbol, LocalDate date) {
        for (int hour = 0; hour < 24; hour++) {
            LocalDateTime hourTime = date.atTime(hour, 0);
            File completeFile = getHourFile(symbol, hourTime, true);
            if (!completeFile.exists()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Download Vision ZIP and stream directly to hourly files.
     * This avoids loading millions of trades into memory.
     *
     * @param symbol Trading symbol
     * @param url Vision download URL
     * @param date Date being downloaded
     * @param currentDayIndex 0-based index of current day in the batch
     * @param totalDays Total days in the batch
     */
    private int downloadAndStreamToHourlyFiles(String symbol, String url, LocalDate date,
                                                int currentDayIndex, int totalDays) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        // Use bulk download client with 10-minute timeout for large Vision files
        try (Response response = bulkDownloadClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new IOException("404 Not Found");
            }
            if (!response.isSuccessful()) {
                throw new IOException("Download failed: " + response.code());
            }

            // Get content length for progress tracking
            long contentLength = response.body().contentLength();
            String dateKey = date.format(DATE_FORMAT);

            // Wrap input stream with progress tracking
            // Calculate combined progress: days completed + current file progress
            InputStream rawStream = response.body().byteStream();
            ProgressInputStream progressStream = new ProgressInputStream(rawStream, contentLength, bytesRead -> {
                if (progressCallback != null && contentLength > 0) {
                    // File progress within current day (0-100)
                    int filePercent = (int) ((bytesRead * 100) / contentLength);
                    // Overall progress: completed days + fraction of current day
                    int overallPercent = totalDays > 0
                        ? (currentDayIndex * 100 + filePercent) / totalDays
                        : filePercent;
                    overallPercent = Math.min(99, Math.max(0, overallPercent));

                    String size = formatBytes(contentLength);
                    String downloaded = formatBytes(bytesRead);
                    progressCallback.accept(new FetchProgress(overallPercent, 100,
                        String.format("Day %d/%d: %s %s / %s",
                            currentDayIndex + 1, totalDays, dateKey, downloaded, size)));
                }
            });

            return streamZipToHourlyFiles(symbol, progressStream, date);
        }
    }

    /**
     * Format bytes as human-readable string.
     */
    private String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000) {
            return String.format("%.1f GB", bytes / 1_000_000_000.0);
        } else if (bytes >= 1_000_000) {
            return String.format("%.1f MB", bytes / 1_000_000.0);
        } else if (bytes >= 1_000) {
            return String.format("%.1f KB", bytes / 1_000.0);
        }
        return bytes + " B";
    }

    /**
     * Stream ZIP contents directly to hourly files.
     * Groups trades by hour and writes incrementally.
     */
    private int streamZipToHourlyFiles(String symbol, InputStream inputStream, LocalDate date) throws IOException {
        // Prepare day directory
        File dayDir = getDayDir(symbol, date);
        dayDir.mkdirs();

        // Writers for each hour (lazy init)
        @SuppressWarnings("unchecked")
        PrintWriter[] hourWriters = new PrintWriter[24];
        int[] hourCounts = new int[24];
        int totalCount = 0;

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".csv")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String line;
                    boolean isHeader = true;

                    while ((line = reader.readLine()) != null) {
                        if (fetchCancelled.get()) {
                            break;
                        }

                        if (isHeader) {
                            isHeader = false;
                            if (line.startsWith("agg") || !Character.isDigit(line.charAt(0))) {
                                continue;
                            }
                        }
                        if (!line.isBlank()) {
                            try {
                                // Parse just enough to get timestamp and hour
                                String[] parts = line.split(",", 7);
                                if (parts.length >= 6) {
                                    long timestamp = Long.parseLong(parts[5].trim());
                                    int hour = LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(timestamp), ZoneOffset.UTC).getHour();

                                    // Get or create writer for this hour
                                    if (hourWriters[hour] == null) {
                                        File hourFile = new File(dayDir, String.format("%02d.csv", hour));
                                        hourWriters[hour] = new PrintWriter(new FileWriter(hourFile));
                                        hourWriters[hour].println(CSV_HEADER);
                                    }

                                    // Write the original line (already in CSV format)
                                    hourWriters[hour].println(formatAggTradeLine(parts));
                                    hourCounts[hour]++;
                                    totalCount++;
                                }
                            } catch (Exception e) {
                                // Skip malformed lines
                            }
                        }
                    }
                }
                zis.closeEntry();
            }
        } finally {
            // Close all writers
            for (int i = 0; i < 24; i++) {
                if (hourWriters[i] != null) {
                    hourWriters[i].close();
                    log.debug("Saved {} aggTrades to {}/{}.csv", formatCount(hourCounts[i]), date.format(DATE_FORMAT), String.format("%02d", i));
                }
            }
        }

        return totalCount;
    }

    /**
     * Format aggTrade parts to our CSV format.
     * Vision format: agg_trade_id,price,quantity,first_trade_id,last_trade_id,transact_time,is_buyer_maker
     * Our format: aggTradeId,price,quantity,firstTradeId,lastTradeId,timestamp,isBuyerMaker
     */
    private String formatAggTradeLine(String[] parts) {
        // Parts are already in the right order, just need to trim and rejoin
        return String.join(",",
            parts[0].trim(),  // aggTradeId
            parts[1].trim(),  // price
            parts[2].trim(),  // quantity
            parts[3].trim(),  // firstTradeId
            parts[4].trim(),  // lastTradeId
            parts[5].trim(),  // timestamp
            parts[6].trim()   // isBuyerMaker
        );
    }

    /**
     * Check if a month is fully cached (all hours have complete files).
     */
    private boolean isMonthFullyCached(String symbol, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        LocalDateTime current = start;
        while (current.isBefore(end)) {
            File completeFile = getHourFile(symbol, current, true);
            if (!completeFile.exists()) {
                return false;
            }
            current = current.plusHours(1);
        }
        return true;
    }

    /**
     * Download and parse a single month's aggTrades ZIP from Vision.
     */
    private List<AggTrade> downloadVisionMonth(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        // Use bulk download client with 10-minute timeout for large Vision files
        try (Response response = bulkDownloadClient.newCall(request).execute()) {
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
     * Parse Vision ZIP file and extract aggTrades.
     */
    private List<AggTrade> parseVisionZip(InputStream inputStream) throws IOException {
        List<AggTrade> trades = new ArrayList<>();

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
                            if (line.startsWith("agg") || !Character.isDigit(line.charAt(0))) {
                                continue;
                            }
                        }
                        if (!line.isBlank()) {
                            try {
                                trades.add(parseVisionAggTrade(line));
                            } catch (Exception e) {
                                // Skip malformed lines
                            }
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        return trades;
    }

    /**
     * Parse Vision aggTrade CSV line.
     * Format: agg_trade_id,price,quantity,first_trade_id,last_trade_id,transact_time,is_buyer_maker
     */
    private AggTrade parseVisionAggTrade(String line) {
        String[] parts = line.split(",");
        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid aggTrade: " + line);
        }
        return new AggTrade(
            Long.parseLong(parts[0].trim()),
            Double.parseDouble(parts[1].trim()),
            Double.parseDouble(parts[2].trim()),
            Long.parseLong(parts[3].trim()),
            Long.parseLong(parts[4].trim()),
            Long.parseLong(parts[5].trim()),
            parseBoolean(parts[6].trim())
        );
    }

    private boolean parseBoolean(String s) {
        return "true".equalsIgnoreCase(s) || "True".equals(s) || "1".equals(s);
    }

    /**
     * Distribute trades from a monthly download to hourly cache files.
     */
    private void distributeToHourlyFiles(String symbol, List<AggTrade> trades) throws IOException {
        // Group by hour
        java.util.Map<LocalDateTime, List<AggTrade>> byHour = new java.util.LinkedHashMap<>();

        for (AggTrade trade : trades) {
            LocalDateTime hour = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(trade.timestamp()), ZoneOffset.UTC)
                .withMinute(0).withSecond(0).withNano(0);
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(trade);
        }

        // Write each hour
        for (var entry : byHour.entrySet()) {
            LocalDateTime hour = entry.getKey();
            List<AggTrade> hourTrades = entry.getValue();

            // Sort by timestamp
            hourTrades.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));

            // Save as complete (Vision data is complete for historical months)
            saveHourFile(symbol, hour, hourTrades, false);
        }
    }

    /**
     * Check if aggTrades data exists for a time range.
     */
    public boolean hasDataFor(String symbol, long startTime, long endTime) {
        SyncStatus status = getSyncStatus(symbol, startTime, endTime);
        return status.hasData();
    }

    /**
     * Get sync status for a time range (hourly granularity).
     */
    public SyncStatus getSyncStatus(String symbol, long startTime, long endTime) {
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC)
            .withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        int hoursComplete = 0;
        int hoursTotal = 0;
        long gapStart = -1;
        long gapEnd = -1;

        LocalDateTime current = start;
        while (!current.isAfter(end)) {
            hoursTotal++;

            File completeFile = getHourFile(symbol, current, true);
            File partialFile = getHourFile(symbol, current, false);
            boolean isCurrentHour = current.getHour() == now.getHour() &&
                                    current.toLocalDate().equals(now.toLocalDate());

            if (completeFile.exists() || (isCurrentHour && partialFile.exists())) {
                hoursComplete++;
            } else if (!partialFile.exists()) {
                // This hour is missing
                if (gapStart < 0) {
                    gapStart = current.toInstant(ZoneOffset.UTC).toEpochMilli();
                }
                gapEnd = current.plusHours(1).toInstant(ZoneOffset.UTC).toEpochMilli() - 1;
            }

            current = current.plusHours(1);
        }

        boolean hasData = hoursComplete == hoursTotal;
        return new SyncStatus(hasData, hoursComplete, hoursTotal,
            gapStart > 0 ? gapStart : startTime,
            gapEnd > 0 ? gapEnd : endTime);
    }

    /**
     * Sync status for UI display.
     */
    public record SyncStatus(
        boolean hasData,
        int hoursComplete,
        int hoursTotal,
        long gapStartTime,
        long gapEndTime
    ) {
        public String getStatusMessage() {
            if (hasData) {
                return "Data synced: " + hoursComplete + " hours";
            } else if (hoursComplete > 0) {
                return "Partial: " + hoursComplete + "/" + hoursTotal + " hours";
            } else {
                return "Not synced";
            }
        }
    }

    /**
     * Get aggregated trades for a symbol within time range.
     * Fetches from Binance if not cached locally.
     *
     * Automatically uses Binance Vision (bulk ZIP downloads) for large date ranges
     * (>= 3 days). AggTrades are massive - a single day can have 500K+ trades.
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime)
            throws IOException {

        // Reset cancellation flag at start of new fetch
        fetchCancelled.set(false);

        // Check if we should use Vision for bulk download
        long uncachedMs = estimateUncachedDuration(symbol, startTime, endTime);
        long uncachedDays = uncachedMs / (24 * 60 * 60 * 1000);

        if (shouldUseVision(startTime, endTime) && uncachedDays >= VISION_THRESHOLD_DAYS) {
            log.info("Using Vision bulk download for {} aggTrades (large uncached range: {} days)", symbol, uncachedDays);
            try {
                fetchViaVision(symbol, startTime, endTime);
            } catch (Exception e) {
                log.warn("Vision download failed, falling back to API: {}", e.getMessage());
            }
        }

        List<AggTrade> allTrades = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Round start down to hour boundary
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC)
            .withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneOffset.UTC);

        // Count hours to fetch for progress tracking
        int hoursToFetch = 0;
        LocalDateTime checkTime = start;
        while (!checkTime.isAfter(end)) {
            File completeFile = getHourFile(symbol, checkTime, true);
            boolean isCurrentHour = checkTime.getHour() == now.getHour() &&
                                    checkTime.toLocalDate().equals(now.toLocalDate());

            if (!completeFile.exists() || isCurrentHour) {
                hoursToFetch++;
            }
            checkTime = checkTime.plusHours(1);
        }

        final int[] currentHourIndex = {0};
        final int finalHoursToFetch = hoursToFetch;

        // Progress callback wrapper
        Consumer<FetchProgress> hourProgressCallback = progress -> {
            if (progressCallback != null && finalHoursToFetch > 0) {
                int hourPercent = progress.percentComplete();
                int overallPercent = ((currentHourIndex[0] * 100) + hourPercent) / finalHoursToFetch;
                overallPercent = Math.min(99, Math.max(0, overallPercent));

                String msg = String.format("Hour %d/%d: %s",
                    currentHourIndex[0] + 1, finalHoursToFetch, progress.message());
                progressCallback.accept(new FetchProgress(overallPercent, 100, msg));
            }
        };

        // Report starting
        if (progressCallback != null && hoursToFetch > 0) {
            progressCallback.accept(new FetchProgress(0, 100,
                String.format("Fetching %d hour(s) of %s aggTrades...", hoursToFetch, symbol)));
        }

        LocalDateTime current = start;
        while (!current.isAfter(end)) {
            if (fetchCancelled.get()) {
                log.debug("Fetch cancelled by user");
                break;
            }

            // Hour boundaries
            long hourStart = current.toInstant(ZoneOffset.UTC).toEpochMilli();
            long hourEnd = current.plusHours(1).toInstant(ZoneOffset.UTC).toEpochMilli() - 1;

            // Clamp to requested range
            long fetchStart = Math.max(hourStart, startTime);
            long fetchEnd = Math.min(hourEnd, endTime);

            File completeFile = getHourFile(symbol, current, true);
            File partialFile = getHourFile(symbol, current, false);

            boolean isCurrentHour = current.getHour() == now.getHour() &&
                                    current.toLocalDate().equals(now.toLocalDate());

            if (completeFile.exists() && !isCurrentHour) {
                // Historical hour with complete data - use cache
                allTrades.addAll(loadCsvFile(completeFile));
            } else if (isCurrentHour) {
                // Current hour - check if we need to update
                File existingFile = completeFile.exists() ? completeFile :
                                    partialFile.exists() ? partialFile : null;
                List<AggTrade> cached = existingFile != null ? loadCsvFile(existingFile) : new ArrayList<>();
                long lastCachedTime = cached.isEmpty() ? hourStart : cached.get(cached.size() - 1).timestamp();

                // Only fetch if last cached trade is old (more than 1 minute for current hour)
                if (System.currentTimeMillis() - lastCachedTime > 60 * 1000) {
                    log.debug("Updating current hour {} {}:xx...", current.format(DATE_FORMAT), current.format(HOUR_FORMAT));
                    List<AggTrade> fresh = client.fetchAllAggTrades(symbol, lastCachedTime + 1, fetchEnd,
                            fetchCancelled, hourProgressCallback);
                    currentHourIndex[0]++;
                    boolean wasCancelled = fetchCancelled.get();

                    // Merge and save
                    List<AggTrade> merged = mergeTrades(cached, fresh);
                    saveHourFile(symbol, current, merged, true); // Current hour is always partial
                    allTrades.addAll(merged);
                } else {
                    allTrades.addAll(cached);
                }
            } else if (partialFile.exists()) {
                // Historical partial file - need to complete it
                List<AggTrade> cached = loadCsvFile(partialFile);
                long lastCachedTime = cached.isEmpty() ? hourStart : cached.get(cached.size() - 1).timestamp();

                // Check if actually complete (within 1 min of hour end)
                if (hourEnd - lastCachedTime < 60 * 1000) {
                    // Actually complete - promote to complete file
                    saveHourFile(symbol, current, cached, false);
                    partialFile.delete();
                    allTrades.addAll(cached);
                } else {
                    // Need to fetch remaining data
                    log.debug("Completing hour {} {}:xx...", current.format(DATE_FORMAT), current.format(HOUR_FORMAT));
                    List<AggTrade> fresh = client.fetchAllAggTrades(symbol, lastCachedTime + 1, hourEnd,
                            fetchCancelled, hourProgressCallback);
                    currentHourIndex[0]++;
                    boolean wasCancelled = fetchCancelled.get();

                    List<AggTrade> merged = mergeTrades(cached, fresh);
                    saveHourFile(symbol, current, merged, wasCancelled);
                    allTrades.addAll(merged);

                    if (wasCancelled) {
                        break;
                    }
                }
            } else {
                // No cache - fetch from Binance
                log.info("Fetching hour {} {}:xx...", current.format(DATE_FORMAT), current.format(HOUR_FORMAT));
                List<AggTrade> fresh = client.fetchAllAggTrades(symbol, fetchStart, fetchEnd,
                        fetchCancelled, hourProgressCallback);
                currentHourIndex[0]++;
                boolean wasCancelled = fetchCancelled.get();

                // For historical hours, mark as complete if we got all the way to hour end
                boolean isPartial = wasCancelled || isCurrentHour;
                saveHourFile(symbol, current, fresh, isPartial);
                allTrades.addAll(fresh);

                if (wasCancelled) {
                    break;
                }
            }

            current = current.plusHours(1);
        }

        // Report completion
        if (progressCallback != null) {
            progressCallback.accept(new FetchProgress(100, 100, "AggTrades fetch complete"));
        }

        // Filter to requested range and sort
        return allTrades.stream()
            .filter(t -> t.timestamp() >= startTime && t.timestamp() <= endTime)
            .distinct()
            .sorted((a, b) -> Long.compare(a.timestamp(), b.timestamp()))
            .toList();
    }

    /**
     * Save trades to an hourly file.
     */
    private void saveHourFile(String symbol, LocalDateTime hour, List<AggTrade> trades, boolean isPartial)
            throws IOException {
        if (trades.isEmpty()) return;

        File dayDir = getDayDir(symbol, hour.toLocalDate());
        dayDir.mkdirs();

        File targetFile = getHourFile(symbol, hour, !isPartial);
        File otherFile = getHourFile(symbol, hour, isPartial);

        writeCsvFile(targetFile, trades);

        // Clean up the other file type if it exists
        if (otherFile.exists()) {
            otherFile.delete();
        }
    }

    /**
     * Merge two trade lists, removing duplicates by aggTradeId.
     */
    private List<AggTrade> mergeTrades(List<AggTrade> a, List<AggTrade> b) {
        java.util.Map<Long, AggTrade> map = new java.util.LinkedHashMap<>();

        for (AggTrade t : a) {
            map.put(t.aggTradeId(), t);
        }
        for (AggTrade t : b) {
            map.put(t.aggTradeId(), t);
        }

        return map.values().stream()
            .sorted((x, y) -> Long.compare(x.timestamp(), y.timestamp()))
            .toList();
    }

    /**
     * Load trades from a CSV file.
     */
    private List<AggTrade> loadCsvFile(File file) throws IOException {
        List<AggTrade> trades = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Skip header line
                if (firstLine && line.startsWith("aggTradeId")) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                try {
                    trades.add(AggTrade.fromCsv(line));
                } catch (Exception e) {
                    log.warn("Failed to parse CSV line: {}", line);
                }
            }
        }

        return trades;
    }

    /**
     * Write trades to a CSV file.
     */
    private void writeCsvFile(File file, List<AggTrade> trades) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("aggTradeId,price,quantity,firstTradeId,lastTradeId,timestamp,isBuyerMaker");
            for (AggTrade t : trades) {
                writer.println(t.toCsv());
            }
        }
        log.debug("Saved {} aggTrades to {}", formatCount(trades.size()), file.getPath());
    }

    /**
     * Get aggregated trades from cache only (no API calls).
     * Returns immediately with whatever is available in local cache.
     *
     * @param symbol    Trading symbol
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of AggTrades sorted by time ascending (may be incomplete)
     */
    public List<AggTrade> getAggTradesCacheOnly(String symbol, long startTime, long endTime) {
        List<AggTrade> allTrades = new ArrayList<>();

        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC)
            .withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneOffset.UTC);

        LocalDateTime current = start;
        while (!current.isAfter(end)) {
            File completeFile = getHourFile(symbol, current, true);
            File partialFile = getHourFile(symbol, current, false);

            // Prefer complete file, fall back to partial
            File fileToLoad = completeFile.exists() ? completeFile :
                              partialFile.exists() ? partialFile : null;

            if (fileToLoad != null) {
                try {
                    allTrades.addAll(loadCsvFile(fileToLoad));
                } catch (IOException e) {
                    log.warn("Failed to load cache file {}: {}", fileToLoad, e.getMessage());
                }
            }

            current = current.plusHours(1);
        }

        // Filter to requested range and sort
        return allTrades.stream()
            .filter(t -> t.timestamp() >= startTime && t.timestamp() <= endTime)
            .distinct()
            .sorted((a, b) -> Long.compare(a.timestamp(), b.timestamp()))
            .toList();
    }

    /**
     * Get the data directory path for a symbol's aggTrades.
     */
    public Path getDataPath(String symbol) {
        return getAggTradesDir(symbol).toPath();
    }

    /**
     * Clear cached data for a symbol.
     */
    public void clearCache(String symbol) throws IOException {
        File symbolDir = getAggTradesDir(symbol);
        if (symbolDir.exists()) {
            Files.walk(symbolDir.toPath())
                .sorted((a, b) -> -a.compareTo(b))
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    /**
     * Get the AggTradesClient for external use.
     */
    public AggTradesClient getClient() {
        return client;
    }

    private String formatCount(int count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        } else if (count >= 1_000) {
            return String.format("%.1fK", count / 1_000.0);
        }
        return String.valueOf(count);
    }
}
