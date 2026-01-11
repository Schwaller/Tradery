package com.tradery.data;

import com.tradery.model.AggTrade;
import com.tradery.model.FetchProgress;

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

/**
 * Stores and retrieves aggregated trade data as hourly CSV files in daily folders.
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

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH");
    private static final String AGG_TRADES_DIR = "aggTrades";
    private static final long ONE_HOUR_MS = 60 * 60 * 1000;

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
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime)
            throws IOException {

        // Reset cancellation flag at start of new fetch
        fetchCancelled.set(false);

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
                System.out.println("Fetch cancelled by user");
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
                    System.out.println("Updating current hour " + current.format(DATE_FORMAT) + " " + current.format(HOUR_FORMAT) + ":xx...");
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
                    System.out.println("Completing hour " + current.format(DATE_FORMAT) + " " + current.format(HOUR_FORMAT) + ":xx...");
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
                System.out.println("Fetching hour " + current.format(DATE_FORMAT) + " " + current.format(HOUR_FORMAT) + ":xx...");
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
        System.out.println("Saved " + formatCount(trades.size()) + " aggTrades to " + file.getPath());
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
