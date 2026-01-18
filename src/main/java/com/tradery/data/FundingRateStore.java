package com.tradery.data;

import com.tradery.model.FundingRate;
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

    public FundingRateStore() {
        this(new FundingRateClient());
    }

    public FundingRateStore(FundingRateClient client) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = client;
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
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of funding rates sorted by time ascending
     */
    public List<FundingRate> getFundingRates(String symbol, long startTime, long endTime) throws IOException {
        // Check if Vision bulk download should be used for large uncached ranges
        long uncachedMs = estimateUncachedDuration(symbol, startTime, endTime);
        if (shouldUseVision(startTime, startTime + uncachedMs) && uncachedMs > 30L * 24 * 60 * 60 * 1000) {
            // Use Vision for historical data (more than 1 month uncached)
            fetchViaVision(symbol, startTime, endTime);
        }

        // Load cached data
        Map<Long, FundingRate> ratesMap = new TreeMap<>();
        loadCachedRates(symbol, startTime, endTime, ratesMap);

        // Find gaps in data and fetch from API (for recent data or small gaps)
        long gapStart = findFirstGap(ratesMap, startTime, endTime);
        if (gapStart > 0) {
            // Fetch missing data from API
            List<FundingRate> fetched = client.fetchFundingRates(symbol, gapStart, endTime);
            for (FundingRate fr : fetched) {
                ratesMap.put(fr.fundingTime(), fr);
            }

            // Save to cache
            saveToCache(symbol, new ArrayList<>(ratesMap.values()));
        }

        // Filter to requested range, but include one rate before startTime for lookback
        // (funding rates come every 8 hours, so a 1-hour window might have no rates)
        List<FundingRate> result = new ArrayList<>();
        FundingRate latestBeforeStart = null;

        for (FundingRate fr : ratesMap.values()) {
            if (fr.fundingTime() < startTime) {
                // Track the most recent rate before our window
                latestBeforeStart = fr;
            } else if (fr.fundingTime() <= endTime) {
                // Include rates within the window
                result.add(fr);
            }
        }

        // Add the lookback rate at the beginning if we have one
        if (latestBeforeStart != null) {
            result.add(0, latestBeforeStart);
        }

        return result;
    }

    /**
     * Load cached funding rates from monthly CSV files.
     */
    private void loadCachedRates(String symbol, long startTime, long endTime,
                                  Map<Long, FundingRate> ratesMap) {
        File symbolDir = getFundingDir(symbol);
        if (!symbolDir.exists()) {
            return;
        }

        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC));

        // Load one month before startMonth for lookback (funding rates come every 8h)
        YearMonth current = startMonth.minusMonths(1);
        while (!current.isAfter(endMonth)) {
            String monthKey = current.format(MONTH_FORMAT);
            File cacheFile = new File(symbolDir, monthKey + ".csv");

            if (cacheFile.exists()) {
                try {
                    loadCsvFile(cacheFile, ratesMap);
                } catch (IOException e) {
                    // Ignore corrupt cache files
                    log.warn("Error loading funding cache: {} - {}", cacheFile, e.getMessage());
                }
            }

            current = current.plusMonths(1);
        }
    }

    /**
     * Load funding rates from a CSV file.
     */
    private void loadCsvFile(File file, Map<Long, FundingRate> ratesMap) throws IOException {
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
                    FundingRate fr = FundingRate.fromCsv(line);
                    ratesMap.put(fr.fundingTime(), fr);
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }
    }

    /**
     * Find the first gap in funding data (8 hours between rates expected).
     * Returns 0 if no gap found.
     */
    private long findFirstGap(Map<Long, FundingRate> ratesMap, long startTime, long endTime) {
        if (ratesMap.isEmpty()) {
            return startTime;
        }

        // Funding happens every 8 hours
        long fundingInterval = 8 * 60 * 60 * 1000;
        long maxGap = fundingInterval + 60 * 60 * 1000; // Allow 1 hour tolerance

        List<Long> times = new ArrayList<>(ratesMap.keySet());

        // Check if we have data from the start
        if (times.get(0) > startTime + maxGap) {
            return startTime;
        }

        // Check for gaps between rates
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
     * Save funding rates to monthly CSV files.
     */
    private void saveToCache(String symbol, List<FundingRate> rates) throws IOException {
        if (rates.isEmpty()) return;

        File symbolDir = getFundingDir(symbol);
        if (!symbolDir.exists()) {
            symbolDir.mkdirs();
        }

        // Group rates by month
        Map<YearMonth, List<FundingRate>> byMonth = new TreeMap<>();
        for (FundingRate fr : rates) {
            YearMonth month = YearMonth.from(
                Instant.ofEpochMilli(fr.fundingTime()).atZone(ZoneOffset.UTC)
            );
            byMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(fr);
        }

        // Write each month's file
        for (Map.Entry<YearMonth, List<FundingRate>> entry : byMonth.entrySet()) {
            String monthKey = entry.getKey().format(MONTH_FORMAT);
            File cacheFile = new File(symbolDir, monthKey + ".csv");

            // Load existing data and merge
            Map<Long, FundingRate> merged = new TreeMap<>();
            if (cacheFile.exists()) {
                loadCsvFile(cacheFile, merged);
            }
            for (FundingRate fr : entry.getValue()) {
                merged.put(fr.fundingTime(), fr);
            }

            // Write merged data
            try (PrintWriter writer = new PrintWriter(new FileWriter(cacheFile))) {
                writer.println(CSV_HEADER);
                for (FundingRate fr : merged.values()) {
                    writer.println(fr.toCsv());
                }
            }
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
     * Estimate the duration of uncached data within the requested range.
     */
    private long estimateUncachedDuration(String symbol, long startTime, long endTime) {
        File symbolDir = getFundingDir(symbol);
        if (!symbolDir.exists()) {
            return endTime - startTime;
        }

        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC));

        long uncachedMonths = 0;
        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            if (!isMonthFullyCached(symbol, current)) {
                uncachedMonths++;
            }
            current = current.plusMonths(1);
        }

        // Each month is approximately 30 days
        return uncachedMonths * 30L * 24 * 60 * 60 * 1000;
    }

    /**
     * Check if a month is fully cached.
     */
    private boolean isMonthFullyCached(String symbol, YearMonth month) {
        File cacheFile = new File(getFundingDir(symbol), month.format(MONTH_FORMAT) + ".csv");
        if (!cacheFile.exists()) {
            return false;
        }

        // Check if file has reasonable data (at least 80 records = ~27 days of funding)
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            int lineCount = 0;
            while (reader.readLine() != null) {
                lineCount++;
            }
            return lineCount >= 80; // ~90 funding events per month
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
                saveToCache(symbol, rates);
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
