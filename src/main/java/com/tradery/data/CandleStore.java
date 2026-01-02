package com.tradery.data;

import com.tradery.TraderyApp;
import com.tradery.model.Candle;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores and retrieves candle data as CSV files.
 * Files organized by symbol/resolution/year-month.csv
 *
 * Claude Code can directly read these files.
 */
public class CandleStore {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final File dataDir;
    private final BinanceClient binanceClient;

    public CandleStore() {
        this.dataDir = new File(TraderyApp.USER_DIR, "data");
        this.binanceClient = new BinanceClient();

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Get candles for a symbol and resolution between two dates.
     * Fetches from Binance if not cached locally.
     * Smart caching: only fetches missing data, skips complete historical months.
     */
    public List<Candle> getCandles(String symbol, String resolution, long startTime, long endTime)
            throws IOException {

        List<Candle> allCandles = new ArrayList<>();
        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        symbolDir.mkdirs();

        // Current month - may have incomplete data
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String currentMonth = now.format(YEAR_MONTH);

        // Iterate through each month in the range
        LocalDate start = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        LocalDate end = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();

        LocalDate current = start;
        while (!current.isAfter(end)) {
            String monthKey = current.format(YEAR_MONTH);
            File monthFile = new File(symbolDir, monthKey + ".csv");

            // Calculate month boundaries
            long monthStart = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            LocalDate nextMonth = current.plusMonths(1);
            long monthEnd = nextMonth.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

            // Clamp to requested range
            long fetchStart = Math.max(monthStart, startTime);
            long fetchEnd = Math.min(monthEnd, endTime);

            boolean isCurrentMonth = monthKey.equals(currentMonth);
            boolean fileExists = monthFile.exists();

            if (fileExists && !isCurrentMonth) {
                // Historical month with data - use cache, don't refetch
                System.out.println("Using cached data for " + monthKey);
                allCandles.addAll(loadCsvFile(monthFile));
            } else if (fileExists && isCurrentMonth) {
                // Current month - check if we need to update
                List<Candle> cached = loadCsvFile(monthFile);
                long lastCachedTime = cached.isEmpty() ? 0 : cached.get(cached.size() - 1).timestamp();

                // Only fetch if last cached candle is old (more than resolution interval)
                long resolutionMs = getResolutionMs(resolution);
                if (System.currentTimeMillis() - lastCachedTime > resolutionMs * 2) {
                    System.out.println("Updating current month " + monthKey + " from " + lastCachedTime);
                    List<Candle> fresh = binanceClient.fetchAllKlines(symbol, resolution, lastCachedTime, fetchEnd);
                    saveToCache(symbol, resolution, fresh);
                    allCandles.addAll(loadCsvFile(monthFile)); // Reload merged data
                } else {
                    System.out.println("Using recent cached data for " + monthKey);
                    allCandles.addAll(cached);
                }
            } else {
                // No cache - fetch from Binance
                System.out.println("Fetching " + monthKey + " from Binance...");
                List<Candle> fresh = binanceClient.fetchAllKlines(symbol, resolution, fetchStart, fetchEnd);
                saveToCache(symbol, resolution, fresh);
                allCandles.addAll(fresh);
            }

            current = nextMonth;
        }

        // Filter to requested range and sort
        return allCandles.stream()
            .filter(c -> c.timestamp() >= startTime && c.timestamp() <= endTime)
            .distinct()
            .sorted((a, b) -> Long.compare(a.timestamp(), b.timestamp()))
            .toList();
    }

    /**
     * Get resolution in milliseconds
     */
    private long getResolutionMs(String resolution) {
        return switch (resolution) {
            case "1m" -> 60_000L;
            case "5m" -> 5 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "30m" -> 30 * 60_000L;
            case "1h" -> 60 * 60_000L;
            case "4h" -> 4 * 60 * 60_000L;
            case "1d" -> 24 * 60 * 60_000L;
            case "1w" -> 7 * 24 * 60 * 60_000L;
            default -> 60 * 60_000L; // Default to 1h
        };
    }

    /**
     * Load candles from local CSV cache
     */
    private List<Candle> loadFromCache(String symbol, String resolution, long startTime, long endTime) {
        List<Candle> candles = new ArrayList<>();
        File symbolDir = new File(dataDir, symbol + "/" + resolution);

        if (!symbolDir.exists()) {
            return candles;
        }

        // Determine which month files to load
        LocalDate start = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();

        LocalDate current = start.withDayOfMonth(1);
        while (!current.isAfter(end)) {
            String filename = current.format(YEAR_MONTH) + ".csv";
            File file = new File(symbolDir, filename);

            if (file.exists()) {
                try {
                    candles.addAll(loadCsvFile(file));
                } catch (IOException e) {
                    System.err.println("Failed to load " + file + ": " + e.getMessage());
                }
            }

            current = current.plusMonths(1);
        }

        return candles;
    }

    /**
     * Save candles to local CSV cache
     */
    private void saveToCache(String symbol, String resolution, List<Candle> candles) throws IOException {
        if (candles.isEmpty()) return;

        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        symbolDir.mkdirs();

        // Group candles by year-month
        java.util.Map<String, List<Candle>> byMonth = new java.util.LinkedHashMap<>();

        for (Candle c : candles) {
            LocalDate date = Instant.ofEpochMilli(c.timestamp()).atZone(ZoneOffset.UTC).toLocalDate();
            String key = date.format(YEAR_MONTH);
            byMonth.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }

        // Write each month file
        for (var entry : byMonth.entrySet()) {
            File file = new File(symbolDir, entry.getKey() + ".csv");

            // Load existing data and merge
            List<Candle> existing = file.exists() ? loadCsvFile(file) : new ArrayList<>();
            List<Candle> merged = new ArrayList<>(mergeCandles(existing, entry.getValue(), 0, Long.MAX_VALUE));

            // Sort by timestamp
            merged.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));

            // Remove duplicates
            List<Candle> deduplicated = new ArrayList<>();
            long lastTs = -1;
            for (Candle c : merged) {
                if (c.timestamp() != lastTs) {
                    deduplicated.add(c);
                    lastTs = c.timestamp();
                }
            }

            // Write to file
            writeCsvFile(file, deduplicated);
        }
    }

    /**
     * Load candles from a CSV file
     */
    private List<Candle> loadCsvFile(File file) throws IOException {
        List<Candle> candles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Skip header line
                if (firstLine && line.startsWith("timestamp")) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                try {
                    candles.add(Candle.fromCsv(line));
                } catch (Exception e) {
                    System.err.println("Failed to parse line: " + line);
                }
            }
        }

        return candles;
    }

    /**
     * Write candles to a CSV file
     */
    private void writeCsvFile(File file, List<Candle> candles) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("timestamp,open,high,low,close,volume");
            for (Candle c : candles) {
                writer.println(c.toCsv());
            }
        }
        System.out.println("Saved " + candles.size() + " candles to " + file.getAbsolutePath());
    }

    /**
     * Merge two candle lists and filter to range
     */
    private List<Candle> mergeCandles(List<Candle> a, List<Candle> b, long startTime, long endTime) {
        java.util.Map<Long, Candle> map = new java.util.LinkedHashMap<>();

        for (Candle c : a) {
            map.put(c.timestamp(), c);
        }
        for (Candle c : b) {
            map.put(c.timestamp(), c);
        }

        return map.values().stream()
            .filter(c -> c.timestamp() >= startTime && c.timestamp() <= endTime)
            .sorted((x, y) -> Long.compare(x.timestamp(), y.timestamp()))
            .toList();
    }

    /**
     * Get the data directory path for a symbol
     */
    public Path getDataPath(String symbol, String resolution) {
        return new File(dataDir, symbol + "/" + resolution).toPath();
    }

    /**
     * Clear cached data for a symbol
     */
    public void clearCache(String symbol) throws IOException {
        File symbolDir = new File(dataDir, symbol);
        if (symbolDir.exists()) {
            Files.walk(symbolDir.toPath())
                .sorted((a, b) -> -a.compareTo(b))
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
}
