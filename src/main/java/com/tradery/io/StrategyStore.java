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
 * Each strategy has its own folder: ~/.tradery/strategies/{id}/strategy.json
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
        File[] strategyDirs = directory.listFiles(File::isDirectory);

        if (strategyDirs != null) {
            for (File strategyDir : strategyDirs) {
                File strategyFile = new File(strategyDir, "strategy.json");
                if (strategyFile.exists()) {
                    try {
                        Strategy strategy = mapper.readValue(strategyFile, Strategy.class);
                        strategies.add(strategy);
                    } catch (IOException e) {
                        System.err.println("Failed to load strategy from " + strategyFile + ": " + e.getMessage());
                    }
                }
            }
        }

        return strategies;
    }

    /**
     * Load a single strategy by ID
     */
    public Strategy load(String id) {
        File file = new File(directory, id + "/strategy.json");
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
        File strategyDir = new File(directory, strategy.getId());
        if (!strategyDir.exists()) {
            strategyDir.mkdirs();
        }

        File file = new File(strategyDir, "strategy.json");

        try {
            mapper.writeValue(file, strategy);
            System.out.println("Saved strategy to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save strategy " + strategy.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Delete a strategy folder and all its contents
     */
    public boolean delete(String id) {
        File strategyDir = new File(directory, id);
        if (strategyDir.exists()) {
            return deleteRecursively(strategyDir);
        }
        return false;
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    /**
     * Check if a strategy exists
     */
    public boolean exists(String id) {
        File file = new File(directory, id + "/strategy.json");
        return file.exists();
    }

    /**
     * Get the strategy.json file path for a strategy
     */
    public File getFile(String id) {
        return new File(directory, id + "/strategy.json");
    }

    /**
     * Get the strategy folder for a strategy
     */
    public File getFolder(String id) {
        return new File(directory, id);
    }
}
