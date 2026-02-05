package com.tradery.dataservice.data;

import com.tradery.core.model.AggTrade;
import com.tradery.core.model.FetchProgress;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
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
    private final SqliteDataStore sqliteStore;

    // Cancellation support
    private final AtomicBoolean fetchCancelled = new AtomicBoolean(false);
    private Consumer<FetchProgress> progressCallback;

    public AggTradesStore() {
        this(new AggTradesClient(), new SqliteDataStore());
    }

    public AggTradesStore(AggTradesClient client, SqliteDataStore sqliteStore) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = client;
        this.httpClient = HttpClientFactory.getClient();
        this.bulkDownloadClient = HttpClientFactory.getBulkDownloadClient();
        this.sqliteStore = sqliteStore;

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
     * Estimate uncached duration for aggTrades using SQLite coverage.
     */
    private long estimateUncachedDuration(String symbol, long startTime, long endTime) {
        List<long[]> gaps = findGapsInSqlite(symbol, startTime, endTime);
        return gaps.stream().mapToLong(g -> g[1] - g[0]).sum();
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

        // Handle edge case: entire range is in the future
        if (startDate.isAfter(yesterday)) {
            log.info("Vision: Entire time range is in the future, no data available");
            if (progressCallback != null) {
                progressCallback.accept(new FetchProgress(100, 100, "No data available (future dates)"));
            }
            return;
        }

        // Count days for progress
        int totalDays = 0;
        LocalDate temp = startDate;
        while (!temp.isAfter(endDate)) {
            totalDays++;
            temp = temp.plusDays(1);
        }

        // Handle edge case: no days to process after capping
        if (totalDays == 0) {
            log.info("Vision: No valid days in range after capping at yesterday");
            if (progressCallback != null) {
                progressCallback.accept(new FetchProgress(100, 100, "No data available"));
            }
            return;
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

            // Delete incomplete day folder before re-downloading
            File dayDir = getDayDir(symbol, current);
            if (dayDir.exists()) {
                log.debug("Vision: Cleaning incomplete day folder {}", dateKey);
                deleteDirectory(dayDir);
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

        // Final progress update - ensure we reach 100% even if all days failed
        if (progressCallback != null) {
            progressCallback.accept(new FetchProgress(100, 100, "Download complete"));
        }
    }

    /**
     * Check if a day is fully cached in SQLite.
     */
    private boolean isDayFullyCached(String symbol, LocalDate date) {
        long dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

        try {
            return sqliteStore.isFullyCovered(symbol, "agg_trades", "default", dayStart, dayEnd);
        } catch (IOException e) {
            log.debug("Failed to check day coverage: {}", e.getMessage());
            return false;
        }
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

            // Estimated file size when content length unknown (typical daily aggTrades: 50-200MB)
            long estimatedSize = contentLength > 0 ? contentLength : 100_000_000L; // 100MB estimate

            ProgressInputStream progressStream = new ProgressInputStream(rawStream, contentLength, bytesRead -> {
                if (progressCallback != null) {
                    int overallPercent;
                    String progressMsg;

                    if (contentLength > 0) {
                        // File progress within current day (0-100)
                        int filePercent = (int) ((bytesRead * 100) / contentLength);
                        // Overall progress: completed days + fraction of current day
                        overallPercent = totalDays > 0
                            ? (currentDayIndex * 100 + filePercent) / totalDays
                            : filePercent;
                        String size = formatBytes(contentLength);
                        String downloaded = formatBytes(bytesRead);
                        progressMsg = String.format("Day %d/%d: %s %s / %s",
                            currentDayIndex + 1, totalDays, dateKey, downloaded, size);
                    } else {
                        // Content length unknown - estimate progress based on typical file size
                        // Cap file progress at 95% since we don't know actual size
                        int filePercent = (int) Math.min(95, (bytesRead * 100) / estimatedSize);
                        overallPercent = totalDays > 0
                            ? (currentDayIndex * 100 + filePercent) / totalDays
                            : filePercent;
                        progressMsg = String.format("Day %d/%d: %s %s downloaded",
                            currentDayIndex + 1, totalDays, dateKey, formatBytes(bytesRead));
                    }
                    overallPercent = Math.min(99, Math.max(0, overallPercent));
                    progressCallback.accept(new FetchProgress(overallPercent, 100, progressMsg));
                }
            });

            return streamZipToSqlite(symbol, progressStream, date);
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
     * Stream ZIP contents directly to SQLite.
     * Batches trades for efficient insertion.
     */
    private int streamZipToSqlite(String symbol, InputStream inputStream, LocalDate date) throws IOException {
        List<AggTrade> batch = new ArrayList<>(10000);
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
                                AggTrade trade = parseVisionAggTrade(line);
                                batch.add(trade);
                                totalCount++;

                                // Flush batch periodically
                                if (batch.size() >= 10000) {
                                    saveToSqlite(symbol, batch);
                                    batch.clear();
                                }
                            } catch (Exception e) {
                                // Skip malformed lines
                            }
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        // Flush remaining
        if (!batch.isEmpty()) {
            saveToSqlite(symbol, batch);
        }

        // Mark day as covered
        if (totalCount > 0 && !fetchCancelled.get()) {
            long dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
            markCoverage(symbol, dayStart, dayEnd, true);
            log.debug("Saved {} aggTrades to SQLite for {}", formatCount(totalCount), date.format(DATE_FORMAT));
        }

        return totalCount;
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
     * Check if aggTrades data exists for a time range.
     */
    public boolean hasDataFor(String symbol, long startTime, long endTime) {
        try {
            return sqliteStore.isFullyCovered(symbol, "agg_trades", "default", startTime, endTime);
        } catch (IOException e) {
            log.debug("Failed to check coverage: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get sync status for a time range.
     */
    public SyncStatus getSyncStatus(String symbol, long startTime, long endTime) {
        List<long[]> gaps = findGapsInSqlite(symbol, startTime, endTime);

        long totalDuration = endTime - startTime;
        long gapDuration = gaps.stream().mapToLong(g -> g[1] - g[0]).sum();
        long coveredDuration = totalDuration - gapDuration;

        // Convert to approximate hours
        int hoursTotal = (int) (totalDuration / ONE_HOUR_MS) + 1;
        int hoursComplete = (int) (coveredDuration / ONE_HOUR_MS);

        boolean hasData = gaps.isEmpty();
        long gapStart = gaps.isEmpty() ? startTime : gaps.get(0)[0];
        long gapEnd = gaps.isEmpty() ? endTime : gaps.get(gaps.size() - 1)[1];

        return new SyncStatus(hasData, hoursComplete, hoursTotal, gapStart, gapEnd);
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
     * Uses SQLite for fast cached reads. Fetches from Binance if not cached locally.
     *
     * Automatically uses Binance Vision (bulk ZIP downloads) for large date ranges
     * (>= 3 days). AggTrades are massive - a single day can have 500K+ trades.
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime)
            throws IOException {

        // Reset cancellation flag at start of new fetch
        fetchCancelled.set(false);

        // First, check SQLite for cached data and find gaps
        List<long[]> gaps = findGapsInSqlite(symbol, startTime, endTime);

        if (gaps.isEmpty()) {
            // All data is cached in SQLite - fast path!
            log.info("SQLite cache hit for {} aggTrades [{} - {}]", symbol, startTime, endTime);
            return sqliteStore.getAggTrades(symbol, startTime, endTime);
        }

        // Calculate total uncached duration
        long uncachedMs = gaps.stream().mapToLong(g -> g[1] - g[0]).sum();
        long uncachedDays = uncachedMs / (24 * 60 * 60 * 1000);

        log.info("SQLite cache miss for {} aggTrades: {} gaps, {} uncached days", symbol, gaps.size(), uncachedDays);

        // Use Vision for bulk download if large uncached range
        if (shouldUseVision(startTime, endTime) && uncachedDays >= VISION_THRESHOLD_DAYS) {
            log.info("Using Vision bulk download for {} aggTrades (large uncached range: {} days)", symbol, uncachedDays);
            try {
                fetchViaVision(symbol, startTime, endTime);
                // After Vision download, data should be in SQLite
                return sqliteStore.getAggTrades(symbol, startTime, endTime);
            } catch (Exception e) {
                log.warn("Vision download failed, falling back to API: {}", e.getMessage());
            }
        }

        // Fetch missing data via API for each gap
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final int totalGaps = gaps.size();
        final int[] completedGaps = {0};

        for (long[] gap : gaps) {
            if (fetchCancelled.get()) {
                log.debug("Fetch cancelled by user");
                break;
            }

            long gapStart = gap[0];
            long gapEnd = gap[1];

            // Progress reporting
            if (progressCallback != null) {
                int pct = totalGaps > 0 ? (completedGaps[0] * 100) / totalGaps : 0;
                progressCallback.accept(new FetchProgress(pct, 100,
                    String.format("Fetching gap %d/%d...", completedGaps[0] + 1, totalGaps)));
            }

            // Check if this gap includes the current hour (needs special handling)
            LocalDateTime gapEndTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(gapEnd), ZoneOffset.UTC);
            boolean includesCurrentHour = gapEndTime.getHour() == now.getHour() &&
                                          gapEndTime.toLocalDate().equals(now.toLocalDate());

            log.info("Fetching aggTrades gap: {} - {}", gapStart, gapEnd);

            List<AggTrade> fresh = client.fetchAllAggTrades(symbol, gapStart, gapEnd,
                fetchCancelled, progress -> {
                    if (progressCallback != null) {
                        int basePct = totalGaps > 0 ? (completedGaps[0] * 100) / totalGaps : 0;
                        int gapPct = progress.percentComplete() / totalGaps;
                        progressCallback.accept(new FetchProgress(basePct + gapPct, 100, progress.message()));
                    }
                });

            if (!fresh.isEmpty()) {
                // Save to SQLite
                saveToSqlite(symbol, fresh);

                // Mark coverage (not complete if includes current hour)
                if (!includesCurrentHour && !fetchCancelled.get()) {
                    markCoverage(symbol, gapStart, gapEnd, true);
                }
            }

            completedGaps[0]++;
        }

        // Report completion
        if (progressCallback != null) {
            progressCallback.accept(new FetchProgress(100, 100, "AggTrades fetch complete"));
        }

        // Return all data from SQLite
        return sqliteStore.getAggTrades(symbol, startTime, endTime);
    }

    /**
     * Stream aggregated trades to a consumer, fetching from Binance if needed.
     * This avoids loading all trades into memory - chunks are streamed as they become available.
     *
     * Data flows:
     * 1. Cached data from SQLite is streamed in chunks
     * 2. For gaps, data is fetched and streamed as it arrives (tee'd to SQLite for persistence)
     * 3. Progress updates are sent periodically to keep connection alive
     *
     * @param symbol        Trading symbol
     * @param startTime     Start time in milliseconds
     * @param endTime       End time in milliseconds
     * @param chunkSize     Number of trades per chunk (recommended: 5000-10000)
     * @param chunkConsumer Called with each chunk of trades and its source ("cache", "api", "vision")
     * @param onProgress    Called periodically with progress updates (keeps connection alive)
     * @return Total number of trades streamed
     */
    public int streamAggTrades(String symbol, long startTime, long endTime, int chunkSize,
                               StreamChunkConsumer chunkConsumer,
                               Consumer<FetchProgress> onProgress) throws IOException {

        // Reset cancellation flag at start of new stream
        fetchCancelled.set(false);

        int totalStreamed = 0;

        // Find gaps in SQLite coverage
        List<long[]> gaps = findGapsInSqlite(symbol, startTime, endTime);
        List<long[]> cachedRanges = computeCachedRanges(startTime, endTime, gaps);

        log.info("Streaming {} aggTrades: {} cached ranges, {} gaps", symbol, cachedRanges.size(), gaps.size());

        // Interleave cached data and gap fetches in chronological order
        int gapIndex = 0;
        int cacheIndex = 0;

        while (gapIndex < gaps.size() || cacheIndex < cachedRanges.size()) {
            if (fetchCancelled.get()) {
                log.debug("Stream cancelled after {} trades", totalStreamed);
                return totalStreamed;
            }

            // Determine what comes next chronologically
            long nextGapStart = gapIndex < gaps.size() ? gaps.get(gapIndex)[0] : Long.MAX_VALUE;
            long nextCacheStart = cacheIndex < cachedRanges.size() ? cachedRanges.get(cacheIndex)[0] : Long.MAX_VALUE;

            if (nextCacheStart <= nextGapStart) {
                // Stream cached data
                long[] range = cachedRanges.get(cacheIndex++);
                int streamed = streamCachedRange(symbol, range[0], range[1], chunkSize, chunkConsumer, onProgress);
                totalStreamed += streamed;
            } else {
                // Fetch and stream gap
                long[] gap = gaps.get(gapIndex++);
                int streamed = streamGap(symbol, gap[0], gap[1], chunkSize, chunkConsumer, onProgress,
                    gapIndex, gaps.size());
                totalStreamed += streamed;
            }
        }

        // Final progress update
        if (onProgress != null) {
            onProgress.accept(FetchProgress.complete(totalStreamed));
        }

        log.info("Stream complete for {} aggTrades: {} total trades", symbol, formatCount(totalStreamed));
        return totalStreamed;
    }

    /**
     * Compute the cached ranges (inverse of gaps) within the requested time range.
     */
    private List<long[]> computeCachedRanges(long startTime, long endTime, List<long[]> gaps) {
        List<long[]> cached = new ArrayList<>();
        long current = startTime;

        for (long[] gap : gaps) {
            if (gap[0] > current) {
                // There's cached data before this gap
                cached.add(new long[]{current, gap[0] - 1});
            }
            current = gap[1] + 1;
        }

        // Check if there's cached data after the last gap
        if (current < endTime) {
            cached.add(new long[]{current, endTime});
        }

        return cached;
    }

    /**
     * Stream cached data from SQLite in chunks.
     */
    private int streamCachedRange(String symbol, long start, long end, int chunkSize,
                                  StreamChunkConsumer chunkConsumer,
                                  Consumer<FetchProgress> onProgress) throws IOException {
        log.debug("Streaming cached range [{} - {}]", start, end);

        if (onProgress != null) {
            onProgress.accept(new FetchProgress(0, 100, "Streaming cached data..."));
        }

        try {
            return sqliteStore.streamAggTrades(symbol, start, end, chunkSize,
                chunk -> chunkConsumer.accept(chunk, "cache"));
        } catch (IOException e) {
            log.warn("Failed to stream cached data: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Fetch a gap and stream chunks as they arrive, also persisting to SQLite.
     */
    private int streamGap(String symbol, long gapStart, long gapEnd, int chunkSize,
                          StreamChunkConsumer chunkConsumer,
                          Consumer<FetchProgress> onProgress,
                          int gapIndex, int totalGaps) throws IOException {

        long gapDuration = gapEnd - gapStart;
        long gapDays = gapDuration / (24 * 60 * 60 * 1000);

        log.info("Streaming gap [{} - {}] ({} days)", gapStart, gapEnd, gapDays);

        // Use Vision for large gaps
        if (gapDays >= VISION_THRESHOLD_DAYS) {
            return streamGapViaVision(symbol, gapStart, gapEnd, chunkSize, chunkConsumer, onProgress);
        }

        // Use API for smaller gaps
        return streamGapViaApi(symbol, gapStart, gapEnd, chunkConsumer, onProgress, gapIndex, totalGaps);
    }

    /**
     * Stream gap via Binance API, tee'ing to SQLite.
     */
    private int streamGapViaApi(String symbol, long gapStart, long gapEnd,
                                StreamChunkConsumer chunkConsumer,
                                Consumer<FetchProgress> onProgress,
                                int gapIndex, int totalGaps) throws IOException {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime gapEndTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(gapEnd), ZoneOffset.UTC);
        boolean includesCurrentHour = gapEndTime.getHour() == now.getHour() &&
                                      gapEndTime.toLocalDate().equals(now.toLocalDate());

        final int[] totalFetched = {0};

        int fetched = client.streamAggTrades(symbol, gapStart, gapEnd, fetchCancelled,
            progress -> {
                if (onProgress != null) {
                    String msg = String.format("Gap %d/%d: %s", gapIndex, totalGaps, progress.message());
                    onProgress.accept(new FetchProgress(progress.fetchedCandles(), progress.estimatedTotal(), msg));
                }
            },
            batch -> {
                // Tee: stream to client AND save to SQLite
                chunkConsumer.accept(batch, "api");
                saveToSqlite(symbol, batch);
                totalFetched[0] += batch.size();
            });

        // Mark coverage (not complete if includes current hour)
        if (!includesCurrentHour && !fetchCancelled.get() && fetched > 0) {
            markCoverage(symbol, gapStart, gapEnd, true);
        }

        return fetched;
    }

    /**
     * Stream gap via Vision bulk download, tee'ing chunks to consumer as they're parsed.
     */
    private int streamGapViaVision(String symbol, long gapStart, long gapEnd, int chunkSize,
                                   StreamChunkConsumer chunkConsumer,
                                   Consumer<FetchProgress> onProgress) throws IOException {

        LocalDate startDate = Instant.ofEpochMilli(gapStart).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = Instant.ofEpochMilli(gapEnd).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        if (endDate.isAfter(yesterday)) {
            endDate = yesterday;
        }

        if (startDate.isAfter(yesterday)) {
            log.info("Vision: Gap is in the future, no data available");
            return 0;
        }

        int totalDays = 0;
        LocalDate temp = startDate;
        while (!temp.isAfter(endDate)) {
            totalDays++;
            temp = temp.plusDays(1);
        }

        if (totalDays == 0) {
            return 0;
        }

        int completedDays = 0;
        int totalStreamed = 0;
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            if (fetchCancelled.get()) {
                break;
            }

            String dateKey = current.format(DATE_FORMAT);

            // Check if day is already cached
            if (isDayFullyCached(symbol, current)) {
                log.trace("Vision: Skipping {} (already cached)", dateKey);
                // Stream from cache instead
                long dayStart = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                long dayEnd = current.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
                int cached = streamCachedRange(symbol, dayStart, dayEnd, chunkSize, chunkConsumer, onProgress);
                totalStreamed += cached;
                completedDays++;
                current = current.plusDays(1);
                continue;
            }

            // Build Vision URL
            String url = String.format("%s/%s/%s-aggTrades-%s.zip",
                VISION_BASE_URL, symbol, symbol, dateKey);

            log.info("Vision: Downloading and streaming {}", dateKey);

            final int dayIndex = completedDays;
            final int finalTotalDays = totalDays;

            if (onProgress != null) {
                int pct = totalDays > 0 ? (completedDays * 100) / totalDays : 0;
                onProgress.accept(new FetchProgress(pct, 100,
                    String.format("Day %d/%d: %s downloading...", completedDays + 1, totalDays, dateKey)));
            }

            try {
                int dayStreamed = downloadAndStreamVisionDay(symbol, url, current, chunkSize,
                    chunkConsumer, onProgress, dayIndex, finalTotalDays);
                totalStreamed += dayStreamed;
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

        return totalStreamed;
    }

    /**
     * Download a Vision day file and stream chunks to consumer while also saving to SQLite.
     */
    private int downloadAndStreamVisionDay(String symbol, String url, LocalDate date, int chunkSize,
                                           StreamChunkConsumer chunkConsumer,
                                           Consumer<FetchProgress> onProgress,
                                           int dayIndex, int totalDays) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = bulkDownloadClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new IOException("404 Not Found");
            }
            if (!response.isSuccessful()) {
                throw new IOException("Download failed: " + response.code());
            }

            long contentLength = response.body().contentLength();
            String dateKey = date.format(DATE_FORMAT);

            InputStream rawStream = response.body().byteStream();
            long estimatedSize = contentLength > 0 ? contentLength : 100_000_000L;

            ProgressInputStream progressStream = new ProgressInputStream(rawStream, contentLength, bytesRead -> {
                if (onProgress != null) {
                    int overallPercent;
                    String progressMsg;

                    if (contentLength > 0) {
                        int filePercent = (int) ((bytesRead * 100) / contentLength);
                        overallPercent = totalDays > 0
                            ? (dayIndex * 100 + filePercent) / totalDays
                            : filePercent;
                        progressMsg = String.format("Day %d/%d: %s %s / %s",
                            dayIndex + 1, totalDays, dateKey, formatBytes(bytesRead), formatBytes(contentLength));
                    } else {
                        int filePercent = (int) Math.min(95, (bytesRead * 100) / estimatedSize);
                        overallPercent = totalDays > 0
                            ? (dayIndex * 100 + filePercent) / totalDays
                            : filePercent;
                        progressMsg = String.format("Day %d/%d: %s %s downloaded",
                            dayIndex + 1, totalDays, dateKey, formatBytes(bytesRead));
                    }
                    overallPercent = Math.min(99, Math.max(0, overallPercent));
                    onProgress.accept(new FetchProgress(overallPercent, 100, progressMsg));
                }
            });

            return streamZipToConsumerAndSqlite(symbol, progressStream, date, chunkSize, chunkConsumer);
        }
    }

    /**
     * Stream ZIP contents to both consumer and SQLite.
     */
    private int streamZipToConsumerAndSqlite(String symbol, InputStream inputStream, LocalDate date,
                                             int chunkSize, StreamChunkConsumer chunkConsumer) throws IOException {
        List<AggTrade> batch = new ArrayList<>(chunkSize);
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
                                AggTrade trade = parseVisionAggTrade(line);
                                batch.add(trade);
                                totalCount++;

                                // Flush batch periodically - tee to both consumer and SQLite
                                if (batch.size() >= chunkSize) {
                                    chunkConsumer.accept(batch, "vision");
                                    saveToSqlite(symbol, batch);
                                    batch = new ArrayList<>(chunkSize);
                                }
                            } catch (Exception e) {
                                // Skip malformed lines
                            }
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        // Flush remaining
        if (!batch.isEmpty()) {
            chunkConsumer.accept(batch, "vision");
            saveToSqlite(symbol, batch);
        }

        // Mark day as covered
        if (totalCount > 0 && !fetchCancelled.get()) {
            long dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
            markCoverage(symbol, dayStart, dayEnd, true);
        }

        return totalCount;
    }

    /**
     * Consumer for streamed chunks that includes the source of the data.
     */
    @FunctionalInterface
    public interface StreamChunkConsumer {
        /**
         * Accept a chunk of trades.
         * @param trades The trades in this chunk
         * @param source Where the data came from: "cache", "api", or "vision"
         */
        void accept(List<AggTrade> trades, String source);
    }

    /**
     * Find gaps in SQLite coverage for a time range.
     */
    private List<long[]> findGapsInSqlite(String symbol, long startTime, long endTime) {
        try {
            return sqliteStore.findGaps(symbol, "agg_trades", "default", startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to check SQLite coverage: {}", e.getMessage());
            // Return entire range as a gap
            List<long[]> gaps = new ArrayList<>();
            gaps.add(new long[]{startTime, endTime});
            return gaps;
        }
    }

    /**
     * Save trades to SQLite.
     */
    private void saveToSqlite(String symbol, List<AggTrade> trades) {
        if (trades.isEmpty()) return;
        try {
            sqliteStore.saveAggTrades(symbol, trades);
            log.debug("Saved {} aggTrades to SQLite for {}", formatCount(trades.size()), symbol);
        } catch (IOException e) {
            log.warn("Failed to save aggTrades to SQLite: {}", e.getMessage());
        }
    }

    /**
     * Mark coverage in SQLite.
     */
    private void markCoverage(String symbol, long start, long end, boolean isComplete) {
        try {
            sqliteStore.addCoverage(symbol, "agg_trades", "default", start, end, isComplete);
        } catch (IOException e) {
            log.warn("Failed to mark coverage: {}", e.getMessage());
        }
    }

    /**
     * Get aggregated trades from cache only (no API calls).
     * Returns immediately with whatever is available in SQLite cache.
     *
     * @param symbol    Trading symbol
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of AggTrades sorted by time ascending (may be incomplete)
     */
    public List<AggTrade> getAggTradesCacheOnly(String symbol, long startTime, long endTime) {
        try {
            return sqliteStore.getAggTrades(symbol, startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to load aggTrades from SQLite: {}", e.getMessage());
            return new ArrayList<>();
        }
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

    /**
     * Recursively delete a directory and all its contents.
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}
