package com.tradery.data;

import com.tradery.TraderyApp;
import com.tradery.model.FundingRate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String FUNDING_DIR = "funding";
    private static final String CSV_HEADER = "symbol,fundingRate,fundingTime,markPrice";

    private final File dataDir;
    private final FundingRateClient client;

    public FundingRateStore() {
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.client = new FundingRateClient();

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
        // Load cached data
        Map<Long, FundingRate> ratesMap = new TreeMap<>();
        loadCachedRates(symbol, startTime, endTime, ratesMap);

        // Find gaps in data and fetch from API
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
                    System.err.println("Error loading funding cache: " + cacheFile + " - " + e.getMessage());
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
}
