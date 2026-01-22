package com.tradery.data;

import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.model.PremiumIndex;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Stores and retrieves premium index kline data.
 * Uses SQLite for fast cached reads with coverage tracking.
 *
 * Premium index klines are stored at the same resolution as strategy timeframes
 * (e.g., 1h, 5m, 1d) allowing per-bar premium evaluation.
 */
public class PremiumIndexStore {

    private static final Logger log = LoggerFactory.getLogger(PremiumIndexStore.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    // Vision bulk download settings
    private static final String VISION_BASE_URL = "https://data.binance.vision/data/futures/um/monthly/premiumIndexKlines";
    private static final int VISION_THRESHOLD_API_CALLS = 10;

    private final File dataDir;
    private final PremiumIndexClient client;
    private final OkHttpClient httpClient;
    private final SqliteDataStore sqliteStore;

    public PremiumIndexStore() {
        this(new PremiumIndexClient(), new SqliteDataStore());
    }

    public PremiumIndexStore(PremiumIndexClient client, SqliteDataStore sqliteStore) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = client;
        this.sqliteStore = sqliteStore;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Get premium index klines for a time range, fetching from API if needed.
     * Uses SQLite for fast cached reads.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param interval  Kline interval (e.g., "1h", "5m")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of premium index klines sorted by time ascending
     */
    public List<PremiumIndex> getPremiumIndex(String symbol, String interval,
                                               long startTime, long endTime) throws IOException {
        // Check SQLite for cached data and find gaps
        List<long[]> gaps = findGapsInSqlite(symbol, interval, startTime, endTime);

        if (gaps.isEmpty()) {
            log.debug("SQLite cache hit for {} {} premium index", symbol, interval);
            return sqliteStore.getPremiumIndex(symbol, interval, startTime, endTime);
        }

        // Calculate uncached duration
        long uncachedMs = gaps.stream().mapToLong(g -> g[1] - g[0]).sum();

        // Use Vision for large uncached ranges (>1 month)
        if (shouldUseVision(interval, startTime, endTime) && uncachedMs > 28L * 24 * 60 * 60 * 1000) {
            fetchViaVision(symbol, interval, startTime, endTime);
            // After Vision download, check again
            gaps = findGapsInSqlite(symbol, interval, startTime, endTime);
        }

        // Fetch remaining gaps from API
        for (long[] gap : gaps) {
            log.info("Fetching {} {} premium index from {} ...",
                symbol, interval, Instant.ofEpochMilli(gap[0]).atZone(ZoneOffset.UTC));

            List<PremiumIndex> fetched = client.fetchPremiumIndexKlines(symbol, interval, gap[0], gap[1]);
            if (!fetched.isEmpty()) {
                saveToSqlite(symbol, interval, fetched);
                markCoverage(symbol, interval, gap[0], gap[1], true);
            }
        }

        return sqliteStore.getPremiumIndex(symbol, interval, startTime, endTime);
    }

    /**
     * Find gaps in SQLite coverage.
     */
    private List<long[]> findGapsInSqlite(String symbol, String interval, long startTime, long endTime) {
        try {
            return sqliteStore.findGaps(symbol, "premium_index", interval, startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to check SQLite coverage: {}", e.getMessage());
            List<long[]> gaps = new ArrayList<>();
            gaps.add(new long[]{startTime, endTime});
            return gaps;
        }
    }

    /**
     * Save premium index to SQLite.
     */
    private void saveToSqlite(String symbol, String interval, List<PremiumIndex> data) {
        if (data.isEmpty()) return;
        try {
            sqliteStore.savePremiumIndex(symbol, interval, data);
            log.debug("Saved {} premium records to SQLite for {} {}", data.size(), symbol, interval);
        } catch (IOException e) {
            log.warn("Failed to save premium to SQLite: {}", e.getMessage());
        }
    }

    /**
     * Mark coverage in SQLite.
     */
    private void markCoverage(String symbol, String interval, long start, long end, boolean isComplete) {
        try {
            sqliteStore.addCoverage(symbol, "premium_index", interval, start, end, isComplete);
        } catch (IOException e) {
            log.warn("Failed to mark coverage: {}", e.getMessage());
        }
    }

    /**
     * Check if a month is fully cached in SQLite.
     */
    private boolean isMonthFullyCached(String symbol, String interval, YearMonth month) {
        long monthStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

        try {
            return sqliteStore.isFullyCovered(symbol, "premium_index", interval, monthStart, monthEnd);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get premium index klines from cache only (no API calls).
     * Returns immediately with whatever is available in SQLite cache.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param interval  Kline interval (e.g., "1h", "5m")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of premium index klines sorted by time ascending (may be incomplete)
     */
    public List<PremiumIndex> getPremiumIndexCacheOnly(String symbol, String interval,
                                                        long startTime, long endTime) {
        try {
            return sqliteStore.getPremiumIndex(symbol, interval, startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to load premium from SQLite: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Clear cache for a symbol (no-op for SQLite, kept for API compatibility).
     */
    public void clearCache(String symbol, String interval) {
        // SQLite data is managed by SqliteDataStore
        log.debug("clearCache called for {} {} - SQLite data persists", symbol, interval);
    }

    // ========== Vision Bulk Download Methods ==========

    /**
     * Determine if Vision bulk download should be used based on estimated API calls.
     */
    private boolean shouldUseVision(String interval, long startTime, long endTime) {
        long durationMs = endTime - startTime;
        long durationHours = durationMs / (1000 * 60 * 60);

        // Estimate candles based on interval
        long estimatedCandles = switch (interval) {
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

        // Use Vision if we'd need more than threshold API calls AND >= 1 month
        boolean exceedsThreshold = estimatedApiCalls > VISION_THRESHOLD_API_CALLS;
        boolean hasCompleteMonth = durationMs >= 28L * 24 * 60 * 60 * 1000;

        return exceedsThreshold && hasCompleteMonth;
    }

    /**
     * Fetch premium index data via Vision bulk download.
     */
    private void fetchViaVision(String symbol, String interval, long startTime, long endTime) {
        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC));

        // Don't try to download future months or current month (incomplete)
        YearMonth lastCompleteMonth = YearMonth.now().minusMonths(1);
        if (endMonth.isAfter(lastCompleteMonth)) {
            endMonth = lastCompleteMonth;
        }

        log.info("Using Vision bulk download for {} {} premium index ({} to {})",
            symbol, interval, startMonth, endMonth);

        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            if (!isMonthFullyCached(symbol, interval, current)) {
                try {
                    downloadVisionMonth(symbol, interval, current);
                } catch (Exception e) {
                    log.warn("Vision download failed for {} {} {}: {}", symbol, interval, current, e.getMessage());
                }
            }
            current = current.plusMonths(1);
        }
    }

    /**
     * Download a single month of premium index data from Vision.
     */
    private void downloadVisionMonth(String symbol, String interval, YearMonth month) throws IOException {
        // URL: https://data.binance.vision/data/futures/um/monthly/premiumIndexKlines/BTCUSDT/1h/BTCUSDT-1h-2024-01.zip
        String url = String.format("%s/%s/%s/%s-%s-%s.zip",
            VISION_BASE_URL, symbol, interval, symbol, interval, month.format(MONTH_FORMAT));

        log.info("Vision: Downloading premium index {}", month);

        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    log.debug("Vision: No premium data available for {} {} {}", symbol, interval, month);
                    return;
                }
                throw new IOException("HTTP " + response.code() + " for " + url);
            }

            List<PremiumIndex> premiums = parseVisionZip(response.body().byteStream());
            if (!premiums.isEmpty()) {
                saveToSqlite(symbol, interval, premiums);
                // Mark month as covered
                long monthStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                long monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
                markCoverage(symbol, interval, monthStart, monthEnd, true);
                log.info("Vision: Saved {} premium index records for {}", premiums.size(), month);
            }
        }
    }

    /**
     * Parse Vision ZIP file containing premium index CSV data.
     * Vision format: open_time,open,high,low,close,close_time,ignore,num_trades,volume,taker_buy_volume,taker_buy_quote_volume,ignore
     */
    private List<PremiumIndex> parseVisionZip(InputStream inputStream) throws IOException {
        List<PremiumIndex> premiums = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry = zis.getNextEntry();
            if (entry != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                String line;
                boolean firstLine = true;

                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        // Skip header if present
                        if (line.startsWith("open_time") || line.startsWith("openTime")) {
                            continue;
                        }
                    }

                    try {
                        // Vision CSV format: open_time,open,high,low,close,close_time,...
                        String[] parts = line.split(",");
                        if (parts.length >= 6) {
                            long openTime = Long.parseLong(parts[0].trim());
                            double open = Double.parseDouble(parts[1].trim());
                            double high = Double.parseDouble(parts[2].trim());
                            double low = Double.parseDouble(parts[3].trim());
                            double close = Double.parseDouble(parts[4].trim());
                            long closeTime = Long.parseLong(parts[5].trim());

                            premiums.add(new PremiumIndex(openTime, open, high, low, close, closeTime));
                        }
                    } catch (Exception e) {
                        // Skip malformed lines
                    }
                }
            }
        }

        return premiums;
    }
}
