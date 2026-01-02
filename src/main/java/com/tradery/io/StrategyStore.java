package com.tradery.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.model.Strategy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes Strategy JSON files.
 * Files stored in ~/.tradery/strategies/ as plain JSON.
 *
 * Claude Code can directly read/write these files.
 */
public class StrategyStore {

    private final File directory;
    private final ObjectMapper mapper;

    public StrategyStore(File directory) {
        this.directory = directory;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ensure directory exists
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * Load all strategies from the directory
     */
    public List<Strategy> loadAll() {
        List<Strategy> strategies = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));

        if (files != null) {
            for (File file : files) {
                try {
                    Strategy strategy = mapper.readValue(file, Strategy.class);
                    strategies.add(strategy);
                } catch (IOException e) {
                    System.err.println("Failed to load strategy from " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        return strategies;
    }

    /**
     * Load a single strategy by ID
     */
    public Strategy load(String id) {
        File file = new File(directory, id + ".json");
        if (!file.exists()) {
            return null;
        }

        try {
            return mapper.readValue(file, Strategy.class);
        } catch (IOException e) {
            System.err.println("Failed to load strategy " + id + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Save a strategy to disk
     */
    public void save(Strategy strategy) {
        File file = new File(directory, strategy.getId() + ".json");

        try {
            mapper.writeValue(file, strategy);
            System.out.println("Saved strategy to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save strategy " + strategy.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Delete a strategy file
     */
    public boolean delete(String id) {
        File file = new File(directory, id + ".json");
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * Check if a strategy exists
     */
    public boolean exists(String id) {
        File file = new File(directory, id + ".json");
        return file.exists();
    }

    /**
     * Get the file path for a strategy
     */
    public File getFile(String id) {
        return new File(directory, id + ".json");
    }
}
