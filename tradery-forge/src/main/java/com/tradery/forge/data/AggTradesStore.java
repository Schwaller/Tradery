package com.tradery.forge.data;

import com.tradery.core.model.AggTrade;
import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.Exchange;
import com.tradery.core.model.FetchProgress;
import com.tradery.forge.data.sqlite.SqliteDataStore;
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
    private static final String VISION_SPOT_BASE_URL = "https://data.binance.vision/data/spot/daily/aggTrades";

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
     * Get the Vision base URL for a market type.
     */
    private String getVisionBaseUrl(DataMarketType marketType) {
        return marketType == DataMarketType.SPOT ? VISION_SPOT_BASE_URL : VISION_BASE_URL;
    }

    /**
     * Get the coverage sub_key for a market type.
     * Futures uses "default" for backward compatibility with existing data.
     */
    private String getCoverageSubKey(DataMarketType marketType) {
        return marketType == DataMarketType.SPOT ? "spot" : "default";
    }

    /**
     * Get the coverage sub_key for an exchange and market type.
     * Binance non-spot uses "default" for backward compatibility with existing data.
     * Other exchanges use "{exchange}_{marketType}" format.
     */
    private String getCoverageSubKey(Exchange exchange, DataMarketType marketType) {
        if (exchange == Exchange.BINANCE && marketType != DataMarketType.SPOT) {
            return "default";
        }
        if (exchange == Exchange.BINANCE && marketType == DataMarketType.SPOT) {
            return "spot";
        }
        return exchange.getConfigKey() + "_" + marketType.getConfigKey();
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
        fetchViaVision(symbol, startTime, endTime, DataMarketType.FUTURES_PERP);
    }

    /**
     * Fetch aggTrades via Binance Vision bulk download for a specific market type.
     */
    private void fetchViaVision(String symbol, long startTime, long endTime, DataMarketType marketType) throws IOException {
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
            if (isDayFullyCached(symbol, current, marketType)) {
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
            String visionBase = getVisionBaseUrl(marketType);
            String url = String.format("%s/%s/%s-aggTrades-%s.zip",
                visionBase, symbol, symbol, dateKey);

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
                    currentDayIndex[0], finalTotalDays, marketType);
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
        return isDayFullyCached(symbol, date, DataMarketType.FUTURES_PERP);
    }

    private boolean isDayFullyCached(String symbol, LocalDate date, DataMarketType marketType) {
        long dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

        try {
            return sqliteStore.isFullyCovered(symbol, "agg_trades", getCoverageSubKey(marketType), dayStart, dayEnd);
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
                                                int currentDayIndex, int totalDays, DataMarketType marketType) throws IOException {
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

            return streamZipToSqlite(symbol, progressStream, date, marketType);
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
        return streamZipToSqlite(symbol, inputStream, date, DataMarketType.FUTURES_PERP);
    }

    /**
     * Stream ZIP contents directly to SQLite with market type tagging.
     */
    private int streamZipToSqlite(String symbol, InputStream inputStream, LocalDate date, DataMarketType marketType) throws IOException {
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
                                AggTrade trade = parseVisionAggTrade(line, marketType);
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
            markCoverage(symbol, getCoverageSubKey(marketType), dayStart, dayEnd, true);
            log.debug("Saved {} {} aggTrades to SQLite for {}", formatCount(totalCount), marketType.getShortName(), date.format(DATE_FORMAT));
        }

        return totalCount;
    }

    /**
     * Parse Vision aggTrade CSV line.
     * Format: agg_trade_id,price,quantity,first_trade_id,last_trade_id,transact_time,is_buyer_maker
     */
    private AggTrade parseVisionAggTrade(String line) {
        return parseVisionAggTrade(line, DataMarketType.FUTURES_PERP);
    }

    /**
     * Parse Vision aggTrade CSV line with market type tagging.
     */
    private AggTrade parseVisionAggTrade(String line, DataMarketType marketType) {
        String[] parts = line.split(",");
        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid aggTrade: " + line);
        }
        double price = Double.parseDouble(parts[1].trim());
        return AggTrade.withExchange(
            Long.parseLong(parts[0].trim()),
            price,
            Double.parseDouble(parts[2].trim()),
            Long.parseLong(parts[3].trim()),
            Long.parseLong(parts[4].trim()),
            Long.parseLong(parts[5].trim()),
            parseBoolean(parts[6].trim()),
            Exchange.BINANCE,
            marketType,
            null
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
     * Get aggregated trades for a symbol and market type within time range.
     * Fetches from Binance Vision (spot or futures) if not cached locally.
     */
    public List<AggTrade> getAggTrades(String symbol, DataMarketType marketType, long startTime, long endTime)
            throws IOException {

        // Reset cancellation flag at start of new fetch
        fetchCancelled.set(false);

        String subKey = getCoverageSubKey(marketType);

        // Check SQLite for cached data and find gaps
        List<long[]> gaps = findGapsInSqlite(symbol, subKey, startTime, endTime);

        if (gaps.isEmpty()) {
            log.debug("SQLite cache hit for {} {} aggTrades [{} - {}]", symbol, marketType.getShortName(), startTime, endTime);
            return sqliteStore.getAggTrades(symbol, startTime, endTime,
                java.util.Set.of(marketType));
        }

        long uncachedMs = gaps.stream().mapToLong(g -> g[1] - g[0]).sum();
        long uncachedDays = uncachedMs / (24 * 60 * 60 * 1000);

        log.debug("SQLite cache miss for {} {} aggTrades: {} gaps, {} uncached days",
            symbol, marketType.getShortName(), gaps.size(), uncachedDays);

        if (shouldUseVision(startTime, endTime) && uncachedDays >= VISION_THRESHOLD_DAYS) {
            log.info("Using Vision bulk download for {} {} aggTrades ({} uncached days)",
                symbol, marketType.getShortName(), uncachedDays);
            try {
                fetchViaVision(symbol, startTime, endTime, marketType);
                return sqliteStore.getAggTrades(symbol, startTime, endTime,
                    java.util.Set.of(marketType));
            } catch (Exception e) {
                log.warn("Vision download failed for {} {}: {}", symbol, marketType.getShortName(), e.getMessage());
            }
        }

        // For spot API fetching, create a spot-specific client
        if (marketType == DataMarketType.SPOT) {
            log.info("Spot API fetch for small gaps not yet implemented; use Vision for larger ranges");
        }

        return sqliteStore.getAggTrades(symbol, startTime, endTime,
            java.util.Set.of(marketType));
    }

    /**
     * Get aggregated trades for a symbol, exchange, and market type within time range.
     * For Binance, delegates to existing getAggTrades with market type.
     * For Bybit/OKX, fetches via ExchangeClient and caches to SQLite.
     */
    public List<AggTrade> getAggTrades(String symbol, Exchange exchange, DataMarketType marketType,
                                        long startTime, long endTime) throws IOException {
        // Binance uses existing optimized path (Vision bulk downloads, etc.)
        if (exchange == Exchange.BINANCE) {
            return getAggTrades(symbol, marketType, startTime, endTime);
        }

        // Reset cancellation flag
        fetchCancelled.set(false);

        String subKey = getCoverageSubKey(exchange, marketType);

        // Check SQLite cache first
        List<long[]> gaps = findGapsInSqlite(symbol, subKey, startTime, endTime);

        if (gaps.isEmpty()) {
            log.debug("SQLite cache hit for {} {} {} aggTrades", symbol, exchange.getShortName(), marketType.getShortName());
            return sqliteStore.getAggTrades(symbol, startTime, endTime,
                java.util.Set.of(marketType));
        }

        // Fetch via exchange client
        ExchangeClient exchangeClient = ExchangeClientFactory.getInstance().getClient(exchange);
        if (exchangeClient == null) {
            log.warn("No client available for {}", exchange.getDisplayName());
            return sqliteStore.getAggTrades(symbol, startTime, endTime,
                java.util.Set.of(marketType));
        }

        String exchangeSymbol = exchangeClient.normalizeSymbol(
            extractBase(symbol), extractQuote(symbol), marketType);

        log.info("Fetching {} aggTrades from {} (symbol: {})...",
            symbol, exchange.getDisplayName(), exchangeSymbol);

        List<AggTrade> trades = exchangeClient.fetchAllAggTrades(
            exchangeSymbol, startTime, endTime, fetchCancelled, progressCallback);

        if (!trades.isEmpty()) {
            saveToSqlite(symbol, trades);

            // Mark coverage for the range actually fetched (may be limited for non-Binance)
            long actualStart = trades.get(0).timestamp();
            long actualEnd = trades.get(trades.size() - 1).timestamp();
            if (!fetchCancelled.get()) {
                markCoverage(symbol, subKey, actualStart, actualEnd, true);
            }
        }

        return sqliteStore.getAggTrades(symbol, startTime, endTime,
            java.util.Set.of(marketType));
    }

    /**
     * Extract base symbol from a combined symbol (e.g., "BTCUSDT" -> "BTC").
     */
    private String extractBase(String symbol) {
        // Common quote currencies to strip
        for (String quote : new String[]{"USDT", "USDC", "USD", "BUSD", "BTC", "ETH"}) {
            if (symbol.endsWith(quote) && symbol.length() > quote.length()) {
                return symbol.substring(0, symbol.length() - quote.length());
            }
        }
        return symbol;
    }

    /**
     * Extract quote symbol from a combined symbol (e.g., "BTCUSDT" -> "USDT").
     */
    private String extractQuote(String symbol) {
        for (String quote : new String[]{"USDT", "USDC", "USD", "BUSD", "BTC", "ETH"}) {
            if (symbol.endsWith(quote) && symbol.length() > quote.length()) {
                return quote;
            }
        }
        return "USDT";
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
            log.debug("SQLite cache hit for {} aggTrades [{} - {}]", symbol, startTime, endTime);
            return sqliteStore.getAggTrades(symbol, startTime, endTime);
        }

        // Calculate total uncached duration
        long uncachedMs = gaps.stream().mapToLong(g -> g[1] - g[0]).sum();
        long uncachedDays = uncachedMs / (24 * 60 * 60 * 1000);

        log.debug("SQLite cache miss for {} aggTrades: {} gaps, {} uncached days", symbol, gaps.size(), uncachedDays);

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
     * Find gaps in SQLite coverage for a time range.
     */
    private List<long[]> findGapsInSqlite(String symbol, long startTime, long endTime) {
        return findGapsInSqlite(symbol, "default", startTime, endTime);
    }

    private List<long[]> findGapsInSqlite(String symbol, String subKey, long startTime, long endTime) {
        try {
            return sqliteStore.findGaps(symbol, "agg_trades", subKey, startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to check SQLite coverage: {}", e.getMessage());
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
        markCoverage(symbol, "default", start, end, isComplete);
    }

    private void markCoverage(String symbol, String subKey, long start, long end, boolean isComplete) {
        try {
            sqliteStore.addCoverage(symbol, "agg_trades", subKey, start, end, isComplete);
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
