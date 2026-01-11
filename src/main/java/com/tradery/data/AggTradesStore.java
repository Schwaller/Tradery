package com.tradery.data;

import com.tradery.TraderyApp;
import com.tradery.model.AggTrade;
import com.tradery.model.FetchProgress;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Stores and retrieves aggregated trade data as daily CSV files.
 * Files organized by symbol/aggTrades/yyyy-MM-dd.csv (one file per day).
 *
 * Storage path: ~/.tradery/data/SYMBOL/aggTrades/
 */
public class AggTradesStore {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String AGG_TRADES_DIR = "aggTrades";

    private final File dataDir;
    private final AggTradesClient client;

    // Cancellation support
    private final AtomicBoolean fetchCancelled = new AtomicBoolean(false);
    private Consumer<FetchProgress> progressCallback;

    public AggTradesStore() {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = new AggTradesClient();

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Get the aggTrades directory for a symbol.
     * Path: ~/.tradery/data/{symbol}/aggTrades/
     */
    private File getAggTradesDir(String symbol) {
        return new File(new File(dataDir, symbol), AGG_TRADES_DIR);
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
     * Check if aggTrades data exists for a time range.
     */
    public boolean hasDataFor(String symbol, long startTime, long endTime) {
        SyncStatus status = getSyncStatus(symbol, startTime, endTime);
        return status.hasData();
    }

    /**
     * Get sync status for a time range.
     */
    public SyncStatus getSyncStatus(String symbol, long startTime, long endTime) {
        File symbolDir = getAggTradesDir(symbol);
        if (!symbolDir.exists()) {
            return new SyncStatus(false, 0, countDays(startTime, endTime), startTime, endTime);
        }

        LocalDate start = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();

        int daysComplete = 0;
        int daysTotal = 0;
        long gapStart = -1;
        long gapEnd = -1;

        LocalDate current = start;
        while (!current.isAfter(end)) {
            daysTotal++;
            String dateKey = current.format(DATE_FORMAT);
            File completeFile = new File(symbolDir, dateKey + ".csv");
            File partialFile = new File(symbolDir, dateKey + ".partial.csv");

            if (completeFile.exists()) {
                daysComplete++;
            } else if (!partialFile.exists()) {
                // This day is missing
                if (gapStart < 0) {
                    gapStart = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                }
                gapEnd = current.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
            }

            current = current.plusDays(1);
        }

        boolean hasData = daysComplete == daysTotal;
        return new SyncStatus(hasData, daysComplete, daysTotal,
            gapStart > 0 ? gapStart : startTime,
            gapEnd > 0 ? gapEnd : endTime);
    }

    private int countDays(long startTime, long endTime) {
        LocalDate start = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            count++;
            current = current.plusDays(1);
        }
        return count;
    }

    /**
     * Sync status for UI display.
     */
    public record SyncStatus(
        boolean hasData,
        int daysComplete,
        int daysTotal,
        long gapStartTime,
        long gapEndTime
    ) {
        public String getStatusMessage() {
            if (hasData) {
                return "Data synced: " + daysComplete + " days";
            } else if (daysComplete > 0) {
                return "Partial: " + daysComplete + "/" + daysTotal + " days";
            } else {
                return "Not synced";
            }
        }
    }

    /**
     * Get aggregated trades for a symbol within time range.
     * Fetches from Binance if not cached locally.
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime)
            throws IOException {

        // Reset cancellation flag at start of new fetch
        fetchCancelled.set(false);

        List<AggTrade> allTrades = new ArrayList<>();
        File symbolDir = getAggTradesDir(symbol);
        symbolDir.mkdirs();

        // Current day - may have incomplete data
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Iterate through each day in the range
        LocalDate start = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();

        // Count days to fetch for progress tracking
        int totalDays = 0;
        int daysToFetch = 0;
        LocalDate checkDate = start;
        while (!checkDate.isAfter(end)) {
            totalDays++;
            File completeFile = new File(symbolDir, checkDate.format(DATE_FORMAT) + ".csv");
            if (!completeFile.exists() || checkDate.equals(today)) {
                daysToFetch++;
            }
            checkDate = checkDate.plusDays(1);
        }

        final int[] currentDayIndex = {0};
        final int finalDaysToFetch = daysToFetch;

        // Wrapper callback for overall progress
        Consumer<FetchProgress> dayProgressCallback = progress -> {
            if (progressCallback != null && finalDaysToFetch > 0) {
                // Calculate overall percentage: completed days + current day progress
                int dayPercent = progress.percentComplete();
                int overallPercent = ((currentDayIndex[0] * 100) + dayPercent) / finalDaysToFetch;
                overallPercent = Math.min(99, Math.max(0, overallPercent));

                String msg = String.format("Day %d/%d: %s",
                    currentDayIndex[0] + 1, finalDaysToFetch, progress.message());
                progressCallback.accept(new FetchProgress(overallPercent, 100, msg));
            }
        };

        // Report starting
        if (progressCallback != null && daysToFetch > 0) {
            progressCallback.accept(new FetchProgress(0, 100,
                String.format("Fetching %d day(s) of %s aggTrades...", daysToFetch, symbol)));
        }

        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (fetchCancelled.get()) {
                System.out.println("Fetch cancelled by user");
                break;
            }

            String dateKey = current.format(DATE_FORMAT);

            // Check for both complete and partial files
            File completeFile = new File(symbolDir, dateKey + ".csv");
            File partialFile = new File(symbolDir, dateKey + ".partial.csv");
            File dayFile = completeFile.exists() ? completeFile :
                           partialFile.exists() ? partialFile : null;

            // Calculate day boundaries
            long dayStart = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            LocalDate nextDay = current.plusDays(1);
            long dayEnd = nextDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

            // Clamp to requested range
            long fetchStart = Math.max(dayStart, startTime);
            long fetchEnd = Math.min(dayEnd, endTime);

            boolean isToday = current.equals(today);
            boolean fileExists = dayFile != null;

            if (fileExists && !isToday && completeFile.exists()) {
                // Historical day with complete data - use cache
                allTrades.addAll(loadCsvFile(dayFile));
            } else if (fileExists && isToday) {
                // Today - check if we need to update
                List<AggTrade> cached = loadCsvFile(dayFile);
                long lastCachedTime = cached.isEmpty() ? 0 : cached.get(cached.size() - 1).timestamp();

                // Only fetch if last cached trade is old (more than 5 minutes)
                if (System.currentTimeMillis() - lastCachedTime > 5 * 60 * 1000) {
                    System.out.println("Updating today's " + dateKey + " aggTrades...");
                    List<AggTrade> fresh = client.fetchAllAggTrades(symbol, lastCachedTime + 1, fetchEnd,
                            fetchCancelled, dayProgressCallback);
                    currentDayIndex[0]++;
                    boolean wasCancelled = fetchCancelled.get();
                    saveToCache(symbol, fresh, wasCancelled);
                    allTrades.addAll(loadCsvFile(completeFile.exists() ? completeFile : partialFile));
                } else {
                    allTrades.addAll(cached);
                }
            } else if (fileExists && partialFile.exists()) {
                // Historical partial file - use what we have
                allTrades.addAll(loadCsvFile(partialFile));
            } else {
                // No cache - fetch from Binance
                System.out.println("Fetching " + dateKey + " aggTrades from Binance...");
                List<AggTrade> fresh = client.fetchAllAggTrades(symbol, fetchStart, fetchEnd,
                        fetchCancelled, dayProgressCallback);
                currentDayIndex[0]++;
                boolean wasCancelled = fetchCancelled.get();
                saveToCache(symbol, fresh, wasCancelled);
                allTrades.addAll(fresh);

                if (wasCancelled) {
                    break;
                }
            }

            current = nextDay;
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
     * Save trades to local CSV cache.
     */
    private void saveToCache(String symbol, List<AggTrade> trades, boolean isPartial)
            throws IOException {
        if (trades.isEmpty()) return;

        File symbolDir = getAggTradesDir(symbol);
        symbolDir.mkdirs();

        // Group trades by day
        java.util.Map<String, List<AggTrade>> byDay = new java.util.LinkedHashMap<>();

        for (AggTrade t : trades) {
            LocalDate date = Instant.ofEpochMilli(t.timestamp()).atZone(ZoneOffset.UTC).toLocalDate();
            String key = date.format(DATE_FORMAT);
            byDay.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        // Write each day file
        for (var entry : byDay.entrySet()) {
            String dateKey = entry.getKey();
            File completeFile = new File(symbolDir, dateKey + ".csv");
            File partialFile = new File(symbolDir, dateKey + ".partial.csv");

            // Determine which file to read existing data from
            File existingFile = completeFile.exists() ? completeFile :
                                partialFile.exists() ? partialFile : null;

            // Load existing data and merge
            List<AggTrade> existing = existingFile != null ? loadCsvFile(existingFile) : new ArrayList<>();
            List<AggTrade> merged = mergeTrades(existing, entry.getValue());

            // Determine if data is complete for this day
            LocalDate day = LocalDate.parse(dateKey, DATE_FORMAT);
            boolean isComplete = !isPartial && isDayComplete(merged, day);

            // Write to appropriate file
            File targetFile = isComplete ? completeFile : partialFile;
            writeCsvFile(targetFile, merged);

            // Clean up
            if (isComplete && partialFile.exists()) {
                partialFile.delete();
            }
        }
    }

    /**
     * Check if trade data is complete for a day.
     */
    private boolean isDayComplete(List<AggTrade> trades, LocalDate day) {
        if (trades.isEmpty()) return false;

        // Current day is never complete
        if (day.equals(LocalDate.now(ZoneOffset.UTC))) return false;

        // Check if we have trades near the end of the day
        long dayEndMs = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long lastTradeTs = trades.get(trades.size() - 1).timestamp();

        // Last trade should be within 5 minutes of day end
        return dayEndMs - lastTradeTs < 5 * 60 * 1000;
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
                    System.err.println("Failed to parse line: " + line);
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
        System.out.println("Saved " + trades.size() + " aggTrades to " + file.getAbsolutePath());
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
}
