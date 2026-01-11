package com.tradery.data;

import com.tradery.TraderyApp;
import com.tradery.model.Candle;
import com.tradery.model.DataHealth;
import com.tradery.model.DataStatus;
import com.tradery.model.Gap;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Analyzes cached candle data for completeness and gaps.
 * Provides data health information for visualization and repair.
 */
public class DataIntegrityChecker {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final File dataDir;

    public DataIntegrityChecker() {
        this.dataDir = DataConfig.getInstance().getDataDir();
    }

    /**
     * Analyze data health for a single month.
     */
    public DataHealth analyze(String symbol, String resolution, YearMonth month) {
        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        String monthKey = month.format(YEAR_MONTH);

        // Check for both complete and partial files
        File completeFile = new File(symbolDir, monthKey + ".csv");
        File partialFile = new File(symbolDir, monthKey + ".partial.csv");

        File dataFile = completeFile.exists() ? completeFile :
                        partialFile.exists() ? partialFile : null;

        // No file exists
        if (dataFile == null) {
            int expected = calculateExpectedCandles(resolution, month);
            return new DataHealth(symbol, resolution, month, expected, 0,
                    Collections.emptyList(), DataStatus.MISSING);
        }

        try {
            List<Candle> candles = loadCsvFile(dataFile);
            return analyzeCandles(symbol, resolution, month, candles);
        } catch (IOException e) {
            int expected = calculateExpectedCandles(resolution, month);
            return new DataHealth(symbol, resolution, month, expected, 0,
                    Collections.emptyList(), DataStatus.UNKNOWN);
        }
    }

    /**
     * Analyze data health for a range of months.
     */
    public List<DataHealth> analyzeRange(String symbol, String resolution,
                                          YearMonth start, YearMonth end) {
        List<DataHealth> results = new ArrayList<>();
        YearMonth current = start;

        while (!current.isAfter(end)) {
            results.add(analyze(symbol, resolution, current));
            current = current.plusMonths(1);
        }

        return results;
    }

    /**
     * Get all available symbols (directories under data/).
     */
    public List<String> getAvailableSymbols() {
        List<String> symbols = new ArrayList<>();
        if (!dataDir.exists()) return symbols;

        File[] dirs = dataDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                symbols.add(dir.getName());
            }
        }
        Collections.sort(symbols);
        return symbols;
    }

    /**
     * Get available resolutions for a symbol.
     */
    public List<String> getAvailableResolutions(String symbol) {
        List<String> resolutions = new ArrayList<>();
        File symbolDir = new File(dataDir, symbol);
        if (!symbolDir.exists()) return resolutions;

        File[] dirs = symbolDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                resolutions.add(dir.getName());
            }
        }
        // Sort by resolution size
        resolutions.sort(this::compareResolutions);
        return resolutions;
    }

    /**
     * Get the range of months that have data for a symbol/resolution.
     */
    public Optional<YearMonth[]> getDataRange(String symbol, String resolution) {
        File symbolDir = new File(dataDir, symbol + "/" + resolution);
        if (!symbolDir.exists()) return Optional.empty();

        File[] files = symbolDir.listFiles((dir, name) ->
                name.endsWith(".csv") || name.endsWith(".partial.csv"));
        if (files == null || files.length == 0) return Optional.empty();

        YearMonth min = null;
        YearMonth max = null;

        for (File f : files) {
            String name = f.getName()
                    .replace(".partial.csv", "")
                    .replace(".csv", "");
            try {
                YearMonth ym = YearMonth.parse(name, YEAR_MONTH);
                if (min == null || ym.isBefore(min)) min = ym;
                if (max == null || ym.isAfter(max)) max = ym;
            } catch (Exception e) {
                // Skip invalid filenames
            }
        }

        if (min != null && max != null) {
            return Optional.of(new YearMonth[]{min, max});
        }
        return Optional.empty();
    }

    /**
     * Analyze a list of candles for a month to detect gaps.
     */
    private DataHealth analyzeCandles(String symbol, String resolution,
                                       YearMonth month, List<Candle> candles) {
        int expectedCandles = calculateExpectedCandles(resolution, month);
        int actualCandles = candles.size();

        if (candles.isEmpty()) {
            return new DataHealth(symbol, resolution, month, expectedCandles, 0,
                    Collections.emptyList(), DataStatus.MISSING);
        }

        // Sort candles by timestamp
        List<Candle> sorted = candles.stream()
                .sorted(Comparator.comparingLong(Candle::timestamp))
                .toList();

        // Detect gaps
        List<Gap> gaps = detectGaps(sorted, resolution, month);

        // Determine status
        DataStatus status;
        if (gaps.isEmpty() && actualCandles >= expectedCandles * 0.99) {
            // Allow 1% tolerance for edge cases (DST, etc.)
            status = DataStatus.COMPLETE;
        } else if (actualCandles > 0) {
            status = DataStatus.PARTIAL;
        } else {
            status = DataStatus.MISSING;
        }

        return new DataHealth(symbol, resolution, month, expectedCandles,
                actualCandles, gaps, status);
    }

    /**
     * Detect gaps in a sorted list of candles.
     */
    private List<Gap> detectGaps(List<Candle> candles, String resolution, YearMonth month) {
        List<Gap> gaps = new ArrayList<>();
        long resolutionMs = getResolutionMs(resolution);

        // Month boundaries
        long monthStart = month.atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        long monthEnd = month.plusMonths(1).atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

        // Check gap at start of month
        if (!candles.isEmpty()) {
            long firstCandle = candles.get(0).timestamp();
            if (firstCandle - monthStart > resolutionMs) {
                int missing = (int) ((firstCandle - monthStart) / resolutionMs);
                if (missing > 0) {
                    gaps.add(new Gap(monthStart, firstCandle - resolutionMs, missing));
                }
            }
        }

        // Check gaps between candles
        for (int i = 0; i < candles.size() - 1; i++) {
            long current = candles.get(i).timestamp();
            long next = candles.get(i + 1).timestamp();
            long diff = next - current;

            // Allow some tolerance (1.5x resolution) for minor variations
            if (diff > resolutionMs * 1.5) {
                int missing = (int) (diff / resolutionMs) - 1;
                if (missing > 0) {
                    gaps.add(new Gap(current + resolutionMs, next - resolutionMs, missing));
                }
            }
        }

        // Check gap at end of month (only for historical months)
        if (!candles.isEmpty() && !month.equals(YearMonth.now())) {
            long lastCandle = candles.get(candles.size() - 1).timestamp();
            if (monthEnd - lastCandle > resolutionMs * 2) {
                int missing = (int) ((monthEnd - lastCandle) / resolutionMs) - 1;
                if (missing > 0) {
                    gaps.add(new Gap(lastCandle + resolutionMs, monthEnd - resolutionMs, missing));
                }
            }
        }

        return gaps;
    }

    /**
     * Calculate expected number of candles for a month.
     * For the current month, only counts up to the current time.
     */
    private int calculateExpectedCandles(String resolution, YearMonth month) {
        long resolutionMs = getResolutionMs(resolution);

        // Month start timestamp
        long monthStart = month.atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

        // Month end timestamp - for current month, use current time
        long monthEnd;
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        if (month.equals(now)) {
            // Current month: only expect candles up to now
            monthEnd = Instant.now().toEpochMilli();
        } else if (month.isAfter(now)) {
            // Future month: no candles expected
            return 0;
        } else {
            // Historical month: full month
            monthEnd = month.plusMonths(1).atDay(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli();
        }

        long durationMs = monthEnd - monthStart;
        return (int) (durationMs / resolutionMs);
    }

    /**
     * Get resolution in milliseconds.
     */
    private long getResolutionMs(String resolution) {
        return switch (resolution) {
            case "1m" -> 60_000L;
            case "3m" -> 3 * 60_000L;
            case "5m" -> 5 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "30m" -> 30 * 60_000L;
            case "1h" -> 60 * 60_000L;
            case "2h" -> 2 * 60 * 60_000L;
            case "4h" -> 4 * 60 * 60_000L;
            case "6h" -> 6 * 60 * 60_000L;
            case "8h" -> 8 * 60 * 60_000L;
            case "12h" -> 12 * 60 * 60_000L;
            case "1d" -> 24 * 60 * 60_000L;
            case "3d" -> 3 * 24 * 60 * 60_000L;
            case "1w" -> 7 * 24 * 60 * 60_000L;
            case "1M" -> 30 * 24 * 60 * 60_000L; // Approximate
            default -> 60 * 60_000L; // Default to 1h
        };
    }

    /**
     * Compare resolutions for sorting (smaller to larger).
     */
    private int compareResolutions(String a, String b) {
        return Long.compare(getResolutionMs(a), getResolutionMs(b));
    }

    /**
     * Load candles from a CSV file.
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
                    // Skip invalid lines
                }
            }
        }

        return candles;
    }
}
