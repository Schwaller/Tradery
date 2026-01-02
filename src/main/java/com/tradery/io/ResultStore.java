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
 * Stores backtest results as JSON files within the strategy folder.
 * - ~/.tradery/strategies/{strategyId}/latest.json
 * - ~/.tradery/strategies/{strategyId}/history/
 *
 * Claude Code can directly read these files.
 */
public class ResultStore {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final File strategyDir;
    private final File historyDir;
    private final ObjectMapper mapper;
    private final String strategyId;

    /**
     * Create a result store for a specific strategy
     * @param strategyId The strategy ID
     */
    public ResultStore(String strategyId) {
        this.strategyId = strategyId;

        // Store results in strategy folder: ~/.tradery/strategies/{strategyId}/
        this.strategyDir = new File(TraderyApp.USER_DIR, "strategies/" + strategyId);
        this.historyDir = new File(strategyDir, "history");

        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ensure directories exist
        strategyDir.mkdirs();
        historyDir.mkdirs();
    }

    /**
     * Get the strategy ID this store is for
     */
    public String getStrategyId() {
        return strategyId;
    }

    /**
     * Save a backtest result as the latest and add to history
     */
    public void save(BacktestResult result) throws IOException {
        // Save as latest
        File latestFile = new File(strategyDir, "latest.json");
        mapper.writeValue(latestFile, result);
        System.out.println("Saved result to: " + latestFile.getAbsolutePath());

        // Save to history
        String timestamp = Instant.ofEpochMilli(result.endTime())
            .atZone(ZoneOffset.UTC)
            .format(DATE_FORMAT);
        String historyFilename = timestamp + ".json";
        File historyFile = new File(historyDir, historyFilename);
        mapper.writeValue(historyFile, result);
    }

    /**
     * Load the latest result
     */
    public BacktestResult loadLatest() {
        File latestFile = new File(strategyDir, "latest.json");
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
        File latestFile = new File(strategyDir, "latest.json");
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
