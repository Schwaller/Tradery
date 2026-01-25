package com.tradery.dataservice.data;

import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.core.model.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Binance Vision client for bulk historical data downloads.
 * Uses monthly ZIP files from data.binance.vision - 10-100x faster than REST API.
 *
 * Benefits:
 * - Single file per month vs hundreds of API calls
 * - No rate limits on Vision downloads
 * - Parallel downloads for multiple months
 * - Hybrid sync: Vision for historical, API for recent gap
 */
public class BinanceVisionClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceVisionClient.class);
    private static final String BASE_URL = "https://data.binance.vision/data/futures/um/monthly";
    private static final int DEFAULT_THREAD_COUNT = 4;
    private static final int BATCH_SIZE = 10000; // Save to DB in batches

    private final OkHttpClient client;
    private final OkHttpClient bulkClient;
    private final SqliteDataStore dataStore;
    private final int threadCount;

    /**
     * Data types available on Binance Vision.
     */
    public enum VisionDataType {
        KLINES("klines"),
        AGG_TRADES("aggTrades"),
        PREMIUM_INDEX("premiumIndexKlines"),
        FUNDING_RATE("fundingRate");

        private final String pathSegment;

        VisionDataType(String pathSegment) {
            this.pathSegment = pathSegment;
        }

        public String getPathSegment() {
            return pathSegment;
        }
    }

    /**
     * Progress information for Vision downloads.
     */
    public record VisionProgress(
        int completedMonths,
        int totalMonths,
        long recordsInserted,
        String status,
        String currentMonth
    ) {
        public int percentComplete() {
            if (totalMonths == 0) return 0;
            return Math.min(100, (completedMonths * 100) / totalMonths);
        }

        public static VisionProgress starting(int totalMonths) {
            return new VisionProgress(0, totalMonths, 0, "Starting download...", "");
        }

        public static VisionProgress inProgress(int completed, int total, long records, String month) {
            return new VisionProgress(completed, total, records,
                "Downloading " + month + "...", month);
        }

        public static VisionProgress complete(int totalMonths, long records) {
            return new VisionProgress(totalMonths, totalMonths, records, "Complete", "");
        }

        public static VisionProgress cancelled(int completed, int total, long records) {
            return new VisionProgress(completed, total, records, "Cancelled", "");
        }

        public static VisionProgress error(String message) {
            return new VisionProgress(0, 0, 0, "Error: " + message, "");
        }
    }

    public BinanceVisionClient(SqliteDataStore dataStore) {
        this(dataStore, DEFAULT_THREAD_COUNT);
    }

    public BinanceVisionClient(SqliteDataStore dataStore, int threadCount) {
        this.client = HttpClientFactory.getClient();
        this.bulkClient = HttpClientFactory.getBulkDownloadClient();
        this.dataStore = dataStore;
        this.threadCount = threadCount;
    }

    // ========== URL Building ==========

    /**
     * Build URL for a Vision data file.
     */
    public String buildUrl(VisionDataType dataType, String symbol, String interval, YearMonth month) {
        String monthStr = String.format("%d-%02d", month.getYear(), month.getMonthValue());

        return switch (dataType) {
            case KLINES -> String.format("%s/klines/%s/%s/%s-%s-%s.zip",
                BASE_URL, symbol, interval, symbol, interval, monthStr);

            case AGG_TRADES -> String.format("%s/aggTrades/%s/%s-aggTrades-%s.zip",
                BASE_URL, symbol, symbol, monthStr);

            case PREMIUM_INDEX -> String.format("%s/premiumIndexKlines/%s/%s/%s-%s-%s.zip",
                BASE_URL, symbol, interval, symbol, interval, monthStr);

            case FUNDING_RATE -> String.format("%s/fundingRate/%s/%s-fundingRate-%s.zip",
                BASE_URL, symbol, symbol, monthStr);
        };
    }

    // ========== Download Methods ==========

    /**
     * Download klines for a symbol/interval over a range of months.
     *
     * @param symbol      Trading pair (e.g., "BTCUSDT")
     * @param interval    Kline interval (e.g., "1h", "4h", "1d")
     * @param startMonth  First month to download
     * @param endMonth    Last month to download (inclusive)
     * @param cancelled   Optional cancellation flag
     * @param onProgress  Optional progress callback
     * @return Total records inserted
     */
    public long downloadKlines(String symbol, String interval, YearMonth startMonth, YearMonth endMonth,
                               AtomicBoolean cancelled, Consumer<VisionProgress> onProgress) throws IOException {

        List<YearMonth> months = getMonthsToDownload(startMonth, endMonth);
        return downloadParallel(VisionDataType.KLINES, symbol, interval, months, cancelled, onProgress,
            (records, sym, intv) -> saveKlines(records, sym, intv));
    }

    /**
     * Download aggregated trades for a symbol over a range of months.
     */
    public long downloadAggTrades(String symbol, YearMonth startMonth, YearMonth endMonth,
                                   AtomicBoolean cancelled, Consumer<VisionProgress> onProgress) throws IOException {

        List<YearMonth> months = getMonthsToDownload(startMonth, endMonth);
        return downloadParallel(VisionDataType.AGG_TRADES, symbol, null, months, cancelled, onProgress,
            (records, sym, intv) -> saveAggTrades(records, sym));
    }

    /**
     * Download funding rates for a symbol over a range of months.
     */
    public long downloadFundingRates(String symbol, YearMonth startMonth, YearMonth endMonth,
                                      AtomicBoolean cancelled, Consumer<VisionProgress> onProgress) throws IOException {

        List<YearMonth> months = getMonthsToDownload(startMonth, endMonth);
        return downloadParallel(VisionDataType.FUNDING_RATE, symbol, null, months, cancelled, onProgress,
            (records, sym, intv) -> saveFundingRates(records, sym));
    }

    /**
     * Download premium index for a symbol/interval over a range of months.
     */
    public long downloadPremiumIndex(String symbol, String interval, YearMonth startMonth, YearMonth endMonth,
                                      AtomicBoolean cancelled, Consumer<VisionProgress> onProgress) throws IOException {

        List<YearMonth> months = getMonthsToDownload(startMonth, endMonth);
        return downloadParallel(VisionDataType.PREMIUM_INDEX, symbol, interval, months, cancelled, onProgress,
            (records, sym, intv) -> savePremiumIndex(records, sym, intv));
    }

    // ========== Parallel Download Infrastructure ==========

    @FunctionalInterface
    private interface RecordSaver {
        void save(List<String> records, String symbol, String interval) throws IOException;
    }

    /**
     * Download multiple months in parallel with progress tracking.
     */
    private long downloadParallel(VisionDataType dataType, String symbol, String interval,
                                   List<YearMonth> months, AtomicBoolean cancelled,
                                   Consumer<VisionProgress> onProgress, RecordSaver saver) throws IOException {

        if (months.isEmpty()) {
            if (onProgress != null) {
                onProgress.accept(VisionProgress.complete(0, 0));
            }
            return 0;
        }

        // Filter out already-covered months
        List<YearMonth> uncoveredMonths = filterUncoveredMonths(dataType, symbol, interval, months);

        if (uncoveredMonths.isEmpty()) {
            log.info("All {} months already covered for {} {}", months.size(), symbol, interval);
            if (onProgress != null) {
                onProgress.accept(VisionProgress.complete(months.size(), 0));
            }
            return 0;
        }

        log.info("Downloading {} months of {} data for {} {} (skipping {} already covered)",
            uncoveredMonths.size(), dataType, symbol, interval,
            months.size() - uncoveredMonths.size());

        if (onProgress != null) {
            onProgress.accept(VisionProgress.starting(uncoveredMonths.size()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong totalRecords = new AtomicLong(0);
        AtomicLong completedCount = new AtomicLong(0);

        try {
            List<Future<Long>> futures = new ArrayList<>();

            for (YearMonth month : uncoveredMonths) {
                futures.add(executor.submit(() -> {
                    if (cancelled != null && cancelled.get()) {
                        return 0L;
                    }

                    try {
                        long records = downloadAndSaveMonth(dataType, symbol, interval, month, saver);
                        long completed = completedCount.incrementAndGet();
                        long total = totalRecords.addAndGet(records);

                        if (onProgress != null) {
                            onProgress.accept(VisionProgress.inProgress(
                                (int) completed, uncoveredMonths.size(), total, month.toString()));
                        }

                        return records;
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("404")) {
                            // Month not available - this is normal for recent/future months
                            log.debug("Month {} not available for {} {}", month, symbol, dataType);
                            completedCount.incrementAndGet();
                            return 0L;
                        }
                        throw e;
                    }
                }));
            }

            // Wait for all downloads to complete
            for (Future<Long> future : futures) {
                if (cancelled != null && cancelled.get()) {
                    break;
                }
                try {
                    future.get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException ioe) {
                        throw ioe;
                    }
                    throw new IOException("Download failed: " + e.getCause().getMessage(), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted", e);
                }
            }

            long total = totalRecords.get();

            if (cancelled != null && cancelled.get()) {
                if (onProgress != null) {
                    onProgress.accept(VisionProgress.cancelled(
                        (int) completedCount.get(), uncoveredMonths.size(), total));
                }
            } else {
                if (onProgress != null) {
                    onProgress.accept(VisionProgress.complete(uncoveredMonths.size(), total));
                }
            }

            return total;

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Download a single month's ZIP file and save to database.
     */
    private long downloadAndSaveMonth(VisionDataType dataType, String symbol, String interval,
                                       YearMonth month, RecordSaver saver) throws IOException {

        String url = buildUrl(dataType, symbol, interval, month);
        log.debug("Downloading: {}", url);

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        // Use bulk client with 10-minute timeout for large ZIP files
        try (Response response = bulkClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new IOException("404 Not Found: " + url);
            }
            if (!response.isSuccessful()) {
                throw new IOException("Download failed: " + response.code() + " " + response.message());
            }

            // Get content length for progress logging
            long contentLength = response.body().contentLength();
            if (contentLength > 0) {
                log.info("Downloading {} ({} MB)...", month, String.format("%.1f", contentLength / 1_000_000.0));
            }

            // Parse ZIP in memory and extract CSV records
            List<String> records = parseZipStream(response.body().byteStream());

            if (records.isEmpty()) {
                log.debug("No records in {}", url);
                return 0;
            }

            // Save records to database
            saver.save(records, symbol, interval);

            // Mark month as covered
            markMonthCovered(dataType, symbol, interval, month);

            log.debug("Saved {} records for {} {}", records.size(), symbol, month);
            return records.size();
        }
    }

    /**
     * Parse ZIP stream and extract CSV lines (excluding header).
     */
    private List<String> parseZipStream(InputStream inputStream) throws IOException {
        List<String> records = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".csv")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String line;
                    boolean isHeader = true;

                    while ((line = reader.readLine()) != null) {
                        // Skip header if it looks like one (starts with non-numeric)
                        if (isHeader) {
                            isHeader = false;
                            if (line.startsWith("open") || line.startsWith("agg") ||
                                line.startsWith("symbol") || line.startsWith("funding")) {
                                continue;
                            }
                        }
                        if (!line.isBlank()) {
                            records.add(line);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        return records;
    }

    // ========== Save Methods ==========

    private void saveKlines(List<String> records, String symbol, String interval) throws IOException {
        List<Candle> candles = new ArrayList<>();

        for (String line : records) {
            try {
                Candle candle = parseKlineCsv(line);
                candles.add(candle);

                if (candles.size() >= BATCH_SIZE) {
                    dataStore.saveCandles(symbol, interval, candles);
                    candles.clear();
                }
            } catch (Exception e) {
                log.warn("Failed to parse kline: {}", line);
            }
        }

        if (!candles.isEmpty()) {
            dataStore.saveCandles(symbol, interval, candles);
        }
    }

    private void saveAggTrades(List<String> records, String symbol) throws IOException {
        List<AggTrade> trades = new ArrayList<>();

        for (String line : records) {
            try {
                AggTrade trade = parseAggTradeCsv(line);
                trades.add(trade);

                if (trades.size() >= BATCH_SIZE) {
                    dataStore.saveAggTrades(symbol, trades);
                    trades.clear();
                }
            } catch (Exception e) {
                log.warn("Failed to parse aggTrade: {}", line);
            }
        }

        if (!trades.isEmpty()) {
            dataStore.saveAggTrades(symbol, trades);
        }
    }

    private void saveFundingRates(List<String> records, String symbol) throws IOException {
        List<FundingRate> rates = new ArrayList<>();

        for (String line : records) {
            try {
                FundingRate rate = parseFundingRateCsv(line, symbol);
                rates.add(rate);

                if (rates.size() >= BATCH_SIZE) {
                    dataStore.saveFundingRates(symbol, rates);
                    rates.clear();
                }
            } catch (Exception e) {
                log.warn("Failed to parse funding rate: {}", line);
            }
        }

        if (!rates.isEmpty()) {
            dataStore.saveFundingRates(symbol, rates);
        }
    }

    private void savePremiumIndex(List<String> records, String symbol, String interval) throws IOException {
        List<PremiumIndex> indices = new ArrayList<>();

        for (String line : records) {
            try {
                PremiumIndex index = parsePremiumIndexCsv(line);
                indices.add(index);

                if (indices.size() >= BATCH_SIZE) {
                    dataStore.savePremiumIndex(symbol, interval, indices);
                    indices.clear();
                }
            } catch (Exception e) {
                log.warn("Failed to parse premium index: {}", line);
            }
        }

        if (!indices.isEmpty()) {
            dataStore.savePremiumIndex(symbol, interval, indices);
        }
    }

    // ========== CSV Parsing ==========

    /**
     * Parse Binance Vision kline CSV line.
     * Format: open_time,open,high,low,close,volume,close_time,quote_volume,count,taker_buy_volume,taker_buy_quote_volume,ignore
     */
    private Candle parseKlineCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid kline CSV: " + line);
        }

        long timestamp = Long.parseLong(parts[0].trim());
        double open = Double.parseDouble(parts[1].trim());
        double high = Double.parseDouble(parts[2].trim());
        double low = Double.parseDouble(parts[3].trim());
        double close = Double.parseDouble(parts[4].trim());
        double volume = Double.parseDouble(parts[5].trim());

        // Extended fields (indices 7-10 in Vision CSV)
        double quoteVolume = parts.length > 7 ? Double.parseDouble(parts[7].trim()) : -1;
        int tradeCount = parts.length > 8 ? Integer.parseInt(parts[8].trim()) : -1;
        double takerBuyVolume = parts.length > 9 ? Double.parseDouble(parts[9].trim()) : -1;
        double takerBuyQuoteVolume = parts.length > 10 ? Double.parseDouble(parts[10].trim()) : -1;

        return new Candle(timestamp, open, high, low, close, volume,
            tradeCount, quoteVolume, takerBuyVolume, takerBuyQuoteVolume);
    }

    /**
     * Parse Binance Vision aggTrades CSV line.
     * Format: agg_trade_id,price,quantity,first_trade_id,last_trade_id,transact_time,is_buyer_maker
     */
    private AggTrade parseAggTradeCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid aggTrade CSV: " + line);
        }
        return new AggTrade(
            Long.parseLong(parts[0].trim()),     // agg_trade_id
            Double.parseDouble(parts[1].trim()), // price
            Double.parseDouble(parts[2].trim()), // quantity
            Long.parseLong(parts[3].trim()),     // first_trade_id
            Long.parseLong(parts[4].trim()),     // last_trade_id
            Long.parseLong(parts[5].trim()),     // transact_time
            parseBoolean(parts[6].trim())        // is_buyer_maker
        );
    }

    /**
     * Parse Binance Vision funding rate CSV line.
     * Format: calc_time,funding_interval_hours,last_funding_rate,symbol (or variations)
     * Some files: symbol,fundingRate,fundingTime,markPrice
     */
    private FundingRate parseFundingRateCsv(String line, String symbol) {
        String[] parts = line.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid funding rate CSV: " + line);
        }

        // Handle different CSV formats from Binance Vision
        // Format 1: calc_time,funding_interval_hours,last_funding_rate,...
        // Format 2: symbol,fundingRate,fundingTime,markPrice
        if (parts[0].matches("\\d+")) {
            // Format 1: timestamp first
            long fundingTime = Long.parseLong(parts[0].trim());
            double fundingRate = Double.parseDouble(parts[2].trim());
            double markPrice = parts.length > 4 ? Double.parseDouble(parts[4].trim()) : 0.0;
            return new FundingRate(symbol, fundingRate, fundingTime, markPrice);
        } else {
            // Format 2: symbol first
            double fundingRate = Double.parseDouble(parts[1].trim());
            long fundingTime = Long.parseLong(parts[2].trim());
            double markPrice = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : 0.0;
            return new FundingRate(symbol, fundingRate, fundingTime, markPrice);
        }
    }

    /**
     * Parse Binance Vision premium index kline CSV line.
     * Format: open_time,open,high,low,close,ignore,close_time,...
     */
    private PremiumIndex parsePremiumIndexCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid premium index CSV: " + line);
        }
        return new PremiumIndex(
            Long.parseLong(parts[0].trim()),     // open_time
            Double.parseDouble(parts[1].trim()), // open
            Double.parseDouble(parts[2].trim()), // high
            Double.parseDouble(parts[3].trim()), // low
            Double.parseDouble(parts[4].trim()), // close
            Long.parseLong(parts[6].trim())      // close_time
        );
    }

    private boolean parseBoolean(String s) {
        return "true".equalsIgnoreCase(s) || "True".equals(s) || "1".equals(s);
    }

    // ========== Coverage Integration ==========

    /**
     * Filter out months that are already fully covered.
     */
    private List<YearMonth> filterUncoveredMonths(VisionDataType dataType, String symbol,
                                                   String interval, List<YearMonth> months) throws IOException {
        List<YearMonth> uncovered = new ArrayList<>();
        String subKey = getSubKey(dataType, interval);

        for (YearMonth month : months) {
            long[] range = getMonthRange(month);
            if (!dataStore.isFullyCovered(symbol, dataType.name().toLowerCase(), subKey, range[0], range[1])) {
                uncovered.add(month);
            }
        }

        return uncovered;
    }

    /**
     * Mark a month as fully covered in the coverage tracker.
     */
    private void markMonthCovered(VisionDataType dataType, String symbol,
                                   String interval, YearMonth month) throws IOException {
        long[] range = getMonthRange(month);
        String subKey = getSubKey(dataType, interval);
        dataStore.addCoverage(symbol, dataType.name().toLowerCase(), subKey, range[0], range[1], true);
    }

    private String getSubKey(VisionDataType dataType, String interval) {
        return switch (dataType) {
            case KLINES, PREMIUM_INDEX -> interval != null ? interval : "1h";
            case AGG_TRADES, FUNDING_RATE -> "default";
        };
    }

    /**
     * Get start/end timestamps for a month.
     */
    private long[] getMonthRange(YearMonth month) {
        long start = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long end = month.atEndOfMonth().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1;
        return new long[]{start, end};
    }

    // ========== Utility Methods ==========

    /**
     * Get list of months between start and end (inclusive).
     */
    private List<YearMonth> getMonthsToDownload(YearMonth startMonth, YearMonth endMonth) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = startMonth;

        while (!current.isAfter(endMonth)) {
            months.add(current);
            current = current.plusMonths(1);
        }

        return months;
    }

    /**
     * Get the last complete month (current month - 1).
     * Vision data for current month may be incomplete.
     */
    public static YearMonth getLastCompleteMonth() {
        return YearMonth.now().minusMonths(1);
    }

    /**
     * Check if a month is available on Vision.
     * Returns false for 404 responses.
     */
    public boolean isMonthAvailable(VisionDataType dataType, String symbol, String interval, YearMonth month) {
        String url = buildUrl(dataType, symbol, interval, month);

        Request request = new Request.Builder()
            .url(url)
            .head()
            .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Find the earliest available month for a data type/symbol.
     * Searches backwards from a starting point.
     */
    public YearMonth findEarliestMonth(VisionDataType dataType, String symbol, String interval) {
        // Start from a known early date (Binance Futures launched ~Sep 2019)
        YearMonth searchMonth = YearMonth.of(2019, 9);
        YearMonth now = YearMonth.now();

        // Binary search would be more efficient, but linear is simpler and this is rarely called
        while (searchMonth.isBefore(now)) {
            if (isMonthAvailable(dataType, symbol, interval, searchMonth)) {
                return searchMonth;
            }
            searchMonth = searchMonth.plusMonths(1);
        }

        return null;
    }

    // ========== Hybrid Sync ==========

    /**
     * Sync using Vision for historical data and API for recent gap.
     * Vision for complete months, BinanceClient for current month.
     *
     * @param symbol      Trading pair
     * @param interval    Kline interval
     * @param startMonth  First month to sync
     * @param apiClient   BinanceClient for API backfill
     * @param cancelled   Cancellation flag
     * @param onProgress  Progress callback
     */
    public void syncWithApiBackfill(String symbol, String interval, YearMonth startMonth,
                                     BinanceClient apiClient, AtomicBoolean cancelled,
                                     Consumer<VisionProgress> onProgress) throws IOException {

        // 1. Download complete months via Vision
        YearMonth lastCompleteMonth = getLastCompleteMonth();

        if (!startMonth.isAfter(lastCompleteMonth)) {
            downloadKlines(symbol, interval, startMonth, lastCompleteMonth, cancelled, onProgress);
        }

        if (cancelled != null && cancelled.get()) {
            return;
        }

        // 2. Backfill current month via API
        YearMonth currentMonth = YearMonth.now();
        long[] currentRange = getMonthRange(currentMonth);

        if (onProgress != null) {
            onProgress.accept(new VisionProgress(0, 1, 0, "Backfilling current month via API...", currentMonth.toString()));
        }

        // Get the latest candle we have
        Candle latest = dataStore.getLatestCandle(symbol, interval);
        long apiStart = latest != null ? latest.timestamp() + 1 : currentRange[0];

        List<Candle> apiCandles = apiClient.fetchAllKlines(symbol, interval, apiStart, System.currentTimeMillis(),
            cancelled, progress -> {
                if (onProgress != null) {
                    onProgress.accept(new VisionProgress(0, 1, progress.fetchedCandles(),
                        "API backfill: " + progress.message(), currentMonth.toString()));
                }
            });

        if (!apiCandles.isEmpty()) {
            dataStore.saveCandles(symbol, interval, apiCandles);
            log.info("API backfill added {} candles for {} {}", apiCandles.size(), symbol, interval);
        }

        if (onProgress != null) {
            onProgress.accept(VisionProgress.complete(1, apiCandles.size()));
        }
    }
}
