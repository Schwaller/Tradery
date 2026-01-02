package com.tradery.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.TraderyApp;
import com.tradery.model.BacktestResult;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores backtest results as JSON files.
 * - Per-project: ~/.tradery/results/{strategyId}/latest.json and history/
 * - Global: ~/.tradery/results/latest.json (for backward compatibility)
 *
 * Claude Code can directly read these files.
 */
public class ResultStore {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final File resultsDir;
    private final File historyDir;
    private final ObjectMapper mapper;
    private final String strategyId;

    /**
     * Create a global result store (backward compatible)
     */
    public ResultStore() {
        this(null);
    }

    /**
     * Create a per-project result store
     * @param strategyId The strategy ID for per-project storage, or null for global storage
     */
    public ResultStore(String strategyId) {
        this.strategyId = strategyId;

        if (strategyId != null && !strategyId.isEmpty()) {
            // Per-project storage: ~/.tradery/results/{strategyId}/
            this.resultsDir = new File(TraderyApp.USER_DIR, "results/" + strategyId);
        } else {
            // Global storage: ~/.tradery/results/
            this.resultsDir = new File(TraderyApp.USER_DIR, "results");
        }

        this.historyDir = new File(resultsDir, "history");
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ensure directories exist
        resultsDir.mkdirs();
        historyDir.mkdirs();
    }

    /**
     * Get the strategy ID this store is for (null if global)
     */
    public String getStrategyId() {
        return strategyId;
    }

    /**
     * Save a backtest result as the latest and add to history
     */
    public void save(BacktestResult result) throws IOException {
        // Save as latest
        File latestFile = new File(resultsDir, "latest.json");
        mapper.writeValue(latestFile, result);
        System.out.println("Saved result to: " + latestFile.getAbsolutePath());

        // Save to history
        String timestamp = Instant.ofEpochMilli(result.endTime())
            .atZone(ZoneOffset.UTC)
            .format(DATE_FORMAT);
        String historyFilename = timestamp + "_" + result.strategyId() + ".json";
        File historyFile = new File(historyDir, historyFilename);
        mapper.writeValue(historyFile, result);
    }

    /**
     * Load the latest result
     */
    public BacktestResult loadLatest() {
        File latestFile = new File(resultsDir, "latest.json");
        if (!latestFile.exists()) {
            return null;
        }

        try {
            return mapper.readValue(latestFile, BacktestResult.class);
        } catch (IOException e) {
            System.err.println("Failed to load latest result: " + e.getMessage());
            return null;
        }
    }

    /**
     * List all historical results
     */
    public List<BacktestResult> loadHistory() {
        List<BacktestResult> results = new ArrayList<>();
        File[] files = historyDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (files != null) {
            for (File file : files) {
                try {
                    results.add(mapper.readValue(file, BacktestResult.class));
                } catch (IOException e) {
                    System.err.println("Failed to load " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        // Sort by end time, newest first
        results.sort((a, b) -> Long.compare(b.endTime(), a.endTime()));

        return results;
    }

    /**
     * Clear all results
     */
    public void clearAll() {
        File latestFile = new File(resultsDir, "latest.json");
        if (latestFile.exists()) {
            latestFile.delete();
        }

        File[] historyFiles = historyDir.listFiles();
        if (historyFiles != null) {
            for (File f : historyFiles) {
                f.delete();
            }
        }
    }
}
