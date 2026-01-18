package com.tradery.data;

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
 * Stores and retrieves premium index kline data as monthly CSV files.
 * Files organized by symbol/premium/{interval}/yyyy-MM.csv
 *
 * Storage path: ~/.tradery/data/{symbol}/premium/{interval}/
 *
 * Premium index klines are stored at the same resolution as strategy timeframes
 * (e.g., 1h, 5m, 1d) allowing per-bar premium evaluation.
 */
public class PremiumIndexStore {

    private static final Logger log = LoggerFactory.getLogger(PremiumIndexStore.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String PREMIUM_DIR = "premium";
    private static final String CSV_HEADER = "openTime,open,high,low,close,closeTime";

    // Vision bulk download settings
    private static final String VISION_BASE_URL = "https://data.binance.vision/data/futures/um/monthly/premiumIndexKlines";
    private static final int VISION_THRESHOLD_API_CALLS = 10;

    private final File dataDir;
    private final PremiumIndexClient client;
    private final OkHttpClient httpClient;

    public PremiumIndexStore() {
        this(new PremiumIndexClient());
    }

    public PremiumIndexStore(PremiumIndexClient client) {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = client;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Get the premium directory for a symbol and interval.
     * Path: ~/.tradery/data/{symbol}/premium/{interval}/
     */
    private File getPremiumDir(String symbol, String interval) {
        return new File(new File(new File(dataDir, symbol), PREMIUM_DIR), interval);
    }

    /**
     * Get premium index klines for a time range, fetching from API if needed.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param interval  Kline interval (e.g., "1h", "5m")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of premium index klines sorted by time ascending
     */
    public List<PremiumIndex> getPremiumIndex(String symbol, String interval,
                                               long startTime, long endTime) throws IOException {
        // Check if Vision bulk download should be used for large uncached ranges
        long uncachedMs = estimateUncachedDuration(symbol, interval, startTime, endTime);
        if (shouldUseVision(interval, startTime, startTime + uncachedMs) && uncachedMs > 28L * 24 * 60 * 60 * 1000) {
            // Use Vision for historical data (more than 1 month uncached)
            fetchViaVision(symbol, interval, startTime, endTime);
        }

        // Load cached data
        Map<Long, PremiumIndex> premiumMap = new TreeMap<>();
        loadCachedPremium(symbol, interval, startTime, endTime, premiumMap);

        // Find gaps in data and fetch from API (for recent data or small gaps)
        long intervalMs = getIntervalMs(interval);
        long gapStart = findFirstGap(premiumMap, startTime, endTime, intervalMs);

        if (gapStart > 0) {
            log.info("Fetching {} {} premium index from {} ...",
                symbol, interval, Instant.ofEpochMilli(gapStart).atZone(ZoneOffset.UTC));

            List<PremiumIndex> fetched = client.fetchPremiumIndexKlines(symbol, interval, gapStart, endTime);

            for (PremiumIndex pi : fetched) {
                premiumMap.put(pi.openTime(), pi);
            }

            // Save to cache
            saveToCache(symbol, interval, new ArrayList<>(premiumMap.values()));
        }

        // Filter to requested range
        List<PremiumIndex> result = new ArrayList<>();
        for (PremiumIndex pi : premiumMap.values()) {
            if (pi.openTime() >= startTime && pi.openTime() <= endTime) {
                result.add(pi);
            }
        }

        return result;
    }

    /**
     * Convert interval string to milliseconds.
     */
    private long getIntervalMs(String interval) {
        return switch (interval) {
            case "1m" -> 60 * 1000L;
            case "3m" -> 3 * 60 * 1000L;
            case "5m" -> 5 * 60 * 1000L;
            case "15m" -> 15 * 60 * 1000L;
            case "30m" -> 30 * 60 * 1000L;
            case "1h" -> 60 * 60 * 1000L;
            case "2h" -> 2 * 60 * 60 * 1000L;
            case "4h" -> 4 * 60 * 60 * 1000L;
            case "6h" -> 6 * 60 * 60 * 1000L;
            case "8h" -> 8 * 60 * 60 * 1000L;
            case "12h" -> 12 * 60 * 60 * 1000L;
            case "1d" -> 24 * 60 * 60 * 1000L;
            case "3d" -> 3 * 24 * 60 * 60 * 1000L;
            case "1w" -> 7 * 24 * 60 * 60 * 1000L;
            default -> 60 * 60 * 1000L; // Default to 1h
        };
    }

    /**
     * Load cached premium index data from monthly CSV files.
     */
    private void loadCachedPremium(String symbol, String interval, long startTime, long endTime,
                                    Map<Long, PremiumIndex> premiumMap) {
        File premiumDir = getPremiumDir(symbol, interval);
        if (!premiumDir.exists()) {
            return;
        }

        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC));

        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            String monthKey = current.format(MONTH_FORMAT);
            File cacheFile = new File(premiumDir, monthKey + ".csv");

            if (cacheFile.exists()) {
                try {
                    loadCsvFile(cacheFile, premiumMap);
                } catch (IOException e) {
                    log.warn("Error loading premium cache: {} - {}", cacheFile, e.getMessage());
                }
            }

            current = current.plusMonths(1);
        }
    }

    /**
     * Load premium index data from a CSV file.
     */
    private void loadCsvFile(File file, Map<Long, PremiumIndex> premiumMap) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("openTime")) continue; // Skip header
                }
                if (line.isBlank()) continue;

                try {
                    PremiumIndex pi = PremiumIndex.fromCsv(line);
                    premiumMap.put(pi.openTime(), pi);
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }
    }

    /**
     * Find the first gap in premium data.
     * Returns 0 if no gap found.
     */
    private long findFirstGap(Map<Long, PremiumIndex> premiumMap, long startTime,
                               long endTime, long intervalMs) {
        if (premiumMap.isEmpty()) {
            return startTime;
        }

        // Allow 10% tolerance for interval gaps
        long maxGap = intervalMs + (intervalMs / 10);

        List<Long> times = new ArrayList<>(premiumMap.keySet());

        // Check if we have data from the start
        if (times.get(0) > startTime + maxGap) {
            return startTime;
        }

        // Check for gaps between klines
        for (int i = 1; i < times.size(); i++) {
            long gap = times.get(i) - times.get(i - 1);
            if (gap > maxGap) {
                return times.get(i - 1) + intervalMs;
            }
        }

        // Check if we have data up to the end
        long lastTime = times.get(times.size() - 1);
        long now = System.currentTimeMillis();
        if (lastTime < endTime - maxGap && lastTime < now - maxGap) {
            return lastTime + intervalMs;
        }

        return 0; // No gap
    }

    /**
     * Save premium index data to monthly CSV files.
     */
    private void saveToCache(String symbol, String interval, List<PremiumIndex> premiums) throws IOException {
        if (premiums.isEmpty()) return;

        File premiumDir = getPremiumDir(symbol, interval);
        if (!premiumDir.exists()) {
            premiumDir.mkdirs();
        }

        // Group by month
        Map<YearMonth, List<PremiumIndex>> byMonth = new TreeMap<>();
        for (PremiumIndex pi : premiums) {
            YearMonth month = YearMonth.from(
                Instant.ofEpochMilli(pi.openTime()).atZone(ZoneOffset.UTC)
            );
            byMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(pi);
        }

        // Write each month's file
        for (Map.Entry<YearMonth, List<PremiumIndex>> entry : byMonth.entrySet()) {
            String monthKey = entry.getKey().format(MONTH_FORMAT);
            File cacheFile = new File(premiumDir, monthKey + ".csv");

            // Load existing data and merge
            Map<Long, PremiumIndex> merged = new TreeMap<>();
            if (cacheFile.exists()) {
                loadCsvFile(cacheFile, merged);
            }
            for (PremiumIndex pi : entry.getValue()) {
                merged.put(pi.openTime(), pi);
            }

            // Write merged data
            try (PrintWriter writer = new PrintWriter(new FileWriter(cacheFile))) {
                writer.println(CSV_HEADER);
                for (PremiumIndex pi : merged.values()) {
                    writer.println(pi.toCsv());
                }
            }
        }
    }

    /**
     * Get premium index klines from cache only (no API calls).
     * Returns immediately with whatever is available in local cache.
     *
     * @param symbol    Trading pair (e.g., "BTCUSDT")
     * @param interval  Kline interval (e.g., "1h", "5m")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @return List of premium index klines sorted by time ascending (may be incomplete)
     */
    public List<PremiumIndex> getPremiumIndexCacheOnly(String symbol, String interval,
                                                        long startTime, long endTime) {
        Map<Long, PremiumIndex> premiumMap = new TreeMap<>();
        loadCachedPremium(symbol, interval, startTime, endTime, premiumMap);

        List<PremiumIndex> result = new ArrayList<>();
        for (PremiumIndex pi : premiumMap.values()) {
            if (pi.openTime() >= startTime && pi.openTime() <= endTime) {
                result.add(pi);
            }
        }
        return result;
    }

    /**
     * Clear cache for a symbol and interval.
     */
    public void clearCache(String symbol, String interval) {
        File premiumDir = getPremiumDir(symbol, interval);
        if (premiumDir.exists()) {
            File[] files = premiumDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            premiumDir.delete();
        }
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
     * Estimate the duration of uncached data within the requested range.
     */
    private long estimateUncachedDuration(String symbol, String interval, long startTime, long endTime) {
        File premiumDir = getPremiumDir(symbol, interval);
        if (!premiumDir.exists()) {
            return endTime - startTime;
        }

        YearMonth startMonth = YearMonth.from(Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC));
        YearMonth endMonth = YearMonth.from(Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC));

        long uncachedMonths = 0;
        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            if (!isMonthFullyCached(symbol, interval, current)) {
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
    private boolean isMonthFullyCached(String symbol, String interval, YearMonth month) {
        File cacheFile = new File(getPremiumDir(symbol, interval), month.format(MONTH_FORMAT) + ".csv");
        if (!cacheFile.exists()) {
            return false;
        }

        // Check if file has reasonable amount of data based on interval
        long intervalMs = getIntervalMs(interval);
        long expectedRecords = (30L * 24 * 60 * 60 * 1000) / intervalMs * 80 / 100; // 80% threshold

        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            int lineCount = 0;
            while (reader.readLine() != null) {
                lineCount++;
            }
            return lineCount >= Math.min(expectedRecords, 100); // At least 100 records or 80% expected
        } catch (IOException e) {
            return false;
        }
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
                saveToCache(symbol, interval, premiums);
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
