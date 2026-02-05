package com.tradery.dataservice.data;

import com.tradery.core.model.FundingRate;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Stores and retrieves funding rate data as monthly CSV files.
 * Files organized by symbol/yyyy-MM.csv (one file per month).
 *
 * Storage path: ~/.tradery/funding/SYMBOL/
 *
 * Funding rates occur every 8 hours (~3 per day, ~90 per month), so
 * monthly files are efficient.
 */
public class FundingRateStore {

    private static final Logger log = LoggerFactory.getLogger(FundingRateStore.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String FUNDING_DIR = "funding";
    private static final String CSV_HEADER = "symbol,fundingRate,fundingTime,markPrice";

    // Vision bulk download settings
    private static final String VISION_BASE_URL = "https://data.binance.vision/data/futures/um/monthly/fundingRate";
    private static final int VISION_THRESHOLD_DAYS = 60; // Use Vision for >= 2 months of data

    private final File dataDir;
    private final FundingRateClient client;
    private final OkHttpClient httpClient;
    private final SqliteDataStore sqliteStore;

    public FundingRateStore() {
        this(new FundingRateClient(), new SqliteDataStore());
    }

    public FundingRateStore(FundingRateClient client, SqliteDataStore sqliteStore) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = client;
        this.sqliteStore = sqliteStore;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Get the funding directory for a symbol.
     * Path: ~/.tradery/data/{symbol}/funding/
     */
    private File getFundingDir(String symbol) {
        return new File(new File(dataDir, symbol), FUNDING_DIR);
    }

    /**
     * Get funding rates for a time range, fetching from API if needed.
     * Uses SQLite for fast cached reads.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of funding rates sorted by time ascending
     */
    public List<FundingRate> getFundingRates(String symbol, long startTime, long endTime) throws IOException {
        // Check SQLite for cached data and find gaps
        List<long[]> gaps = findGapsInSqlite(symbol, startTime, endTime);

        if (gaps.isEmpty()) {
            // All data is cached - fast path!
            log.debug("SQLite cache hit for {} funding rates", symbol);
            return sqliteStore.getFundingRates(symbol, startTime, endTime);
        }

        // Calculate uncached duration
        long uncachedMs = gaps.stream().mapToLong(g -> g[1] - g[0]).sum();

        // Use Vision for large uncached ranges (>1 month)
        if (shouldUseVision(startTime, endTime) && uncachedMs > 30L * 24 * 60 * 60 * 1000) {
            fetchViaVision(symbol, startTime, endTime);
            // After Vision download, check again
            gaps = findGapsInSqlite(symbol, startTime, endTime);
        }

        // Fetch remaining gaps from API
        for (long[] gap : gaps) {
            List<FundingRate> fetched = client.fetchFundingRates(symbol, gap[0], gap[1]);
            if (!fetched.isEmpty()) {
                saveToSqlite(symbol, fetched);
                markCoverage(symbol, gap[0], gap[1], true);
            }
        }

        // Return all data from SQLite (includes lookback automatically via DAO)
        return sqliteStore.getFundingRates(symbol, startTime, endTime);
    }

    /**
     * Find gaps in SQLite coverage.
     */
    private List<long[]> findGapsInSqlite(String symbol, long startTime, long endTime) {
        try {
            return sqliteStore.findGaps(symbol, "funding_rates", "default", startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to check SQLite coverage: {}", e.getMessage());
            List<long[]> gaps = new ArrayList<>();
            gaps.add(new long[]{startTime, endTime});
            return gaps;
        }
    }

    /**
     * Save funding rates to SQLite.
     */
    private void saveToSqlite(String symbol, List<FundingRate> rates) {
        if (rates.isEmpty()) return;
        try {
            sqliteStore.saveFundingRates(symbol, rates);
            log.debug("Saved {} funding rates to SQLite for {}", rates.size(), symbol);
        } catch (IOException e) {
            log.warn("Failed to save funding rates to SQLite: {}", e.getMessage());
        }
    }

    /**
     * Mark coverage in SQLite.
     */
    private void markCoverage(String symbol, long start, long end, boolean isComplete) {
        try {
            sqliteStore.addCoverage(symbol, "funding_rates", "default", start, end, isComplete);
        } catch (IOException e) {
            log.warn("Failed to mark coverage: {}", e.getMessage());
        }
    }

    /**
     * Get funding rates from cache only (no API calls).
     * Returns immediately with whatever is available in SQLite cache.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of funding rates sorted by time ascending (may be incomplete)
     */
    public List<FundingRate> getFundingRatesCacheOnly(String symbol, long startTime, long endTime) {
        try {
            return sqliteStore.getFundingRates(symbol, startTime, endTime);
        } catch (IOException e) {
            log.warn("Failed to load funding rates from SQLite: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Clear cache for a symbol.
     */
    public void clearCache(String symbol) {
        File symbolDir = getFundingDir(symbol);
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

    // ========== Vision Bulk Download Methods ==========

    /**
     * Determine if Vision bulk download should be used based on data volume.
     * Funding data is small (~90 records/month), so we use Vision for >= 2 months.
     */
    private boolean shouldUseVision(long startTime, long endTime) {
        long durationDays = (endTime - startTime) / (24 * 60 * 60 * 1000);
        return durationDays >= VISION_THRESHOLD_DAYS;
    }

    /**
     * Check if a month is fully cached in SQLite.
     */
    private boolean isMonthFullyCached(String symbol, YearMonth month) {
        long monthStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

        try {
            return sqliteStore.isFullyCovered(symbol, "funding_rates", "default", monthStart, monthEnd);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Fetch funding rate data via Vision bulk download.
     */
    private void fetchViaVision(String symbol, long startTime, long endTime) {
        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC));

        // Don't try to download future months or current month (incomplete)
        YearMonth lastCompleteMonth = YearMonth.now().minusMonths(1);
        if (endMonth.isAfter(lastCompleteMonth)) {
            endMonth = lastCompleteMonth;
        }

        log.info("Using Vision bulk download for {} funding rates ({} to {})",
            symbol, startMonth, endMonth);

        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            if (!isMonthFullyCached(symbol, current)) {
                try {
                    downloadVisionMonth(symbol, current);
                } catch (Exception e) {
                    log.warn("Vision download failed for {} {}: {}", symbol, current, e.getMessage());
                }
            }
            current = current.plusMonths(1);
        }
    }

    /**
     * Download a single month of funding rate data from Vision.
     */
    private void downloadVisionMonth(String symbol, YearMonth month) throws IOException {
        // URL: https://data.binance.vision/data/futures/um/monthly/fundingRate/BTCUSDT/BTCUSDT-fundingRate-2024-01.zip
        String url = String.format("%s/%s/%s-fundingRate-%s.zip",
            VISION_BASE_URL, symbol, symbol, month.format(MONTH_FORMAT));

        log.info("Vision: Downloading funding rates {}", month);

        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    log.debug("Vision: No data available for {} {}", symbol, month);
                    return;
                }
                throw new IOException("HTTP " + response.code() + " for " + url);
            }

            List<FundingRate> rates = parseVisionZip(response.body().byteStream(), symbol);
            if (!rates.isEmpty()) {
                saveToSqlite(symbol, rates);
                // Mark month as covered
                long monthStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                long monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
                markCoverage(symbol, monthStart, monthEnd, true);
                log.info("Vision: Saved {} funding rates for {}", rates.size(), month);
            }
        }
    }

    /**
     * Parse Vision ZIP file containing funding rate CSV data.
     * Vision format: symbol,fundingTime,fundingRate
     */
    private List<FundingRate> parseVisionZip(InputStream inputStream, String symbol) throws IOException {
        List<FundingRate> rates = new ArrayList<>();

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
                        if (line.startsWith("symbol") || line.startsWith("calc_time")) {
                            continue;
                        }
                    }

                    try {
                        // Vision CSV format: symbol,calc_time,funding_interval_hours,last_funding_rate
                        // or: symbol,fundingTime,fundingRate
                        String[] parts = line.split(",");
                        if (parts.length >= 3) {
                            long fundingTime = Long.parseLong(parts[1].trim());
                            double fundingRate = Double.parseDouble(parts[parts.length - 1].trim());
                            // Mark price not available in Vision data, use 0
                            rates.add(new FundingRate(symbol, fundingRate, fundingTime, 0.0));
                        }
                    } catch (Exception e) {
                        // Skip malformed lines
                    }
                }
            }
        }

        return rates;
    }
}
