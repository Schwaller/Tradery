package com.tradery.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.model.Strategy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    // ========== Preset Management ==========

    private static final String PRESET_MANIFEST = "/strategies/manifest.json";

    /**
     * Information about a bundled preset strategy
     */
    public record PresetInfo(String id, String name, String version) {}

    /**
     * Load the list of available presets from the bundled manifest
     */
    public List<PresetInfo> getAvailablePresets() {
        List<PresetInfo> presets = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream(PRESET_MANIFEST)) {
            if (is == null) {
                System.err.println("Preset manifest not found in resources");
                return presets;
            }
            JsonNode root = mapper.readTree(is);
            JsonNode strategies = root.get("strategies");
            if (strategies != null && strategies.isArray()) {
                for (JsonNode node : strategies) {
                    presets.add(new PresetInfo(
                        node.get("id").asText(),
                        node.get("name").asText(),
                        node.get("version").asText()
                    ));
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load preset manifest: " + e.getMessage());
        }
        return presets;
    }

    /**
     * Install missing presets on first run.
     * Only installs presets that don't already exist in the user's directory.
     */
    public void installMissingPresets() {
        List<PresetInfo> presets = getAvailablePresets();
        for (PresetInfo preset : presets) {
            if (!exists(preset.id())) {
                installPreset(preset.id());
            }
        }
    }

    /**
     * Install or restore a preset strategy from bundled resources.
     * This will overwrite any existing strategy with the same ID.
     */
    public Strategy installPreset(String presetId) {
        String resourcePath = "/strategies/" + presetId + "/strategy.json";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("Preset not found: " + presetId);
                return null;
            }
            Strategy strategy = mapper.readValue(is, Strategy.class);
            save(strategy);
            System.out.println("Installed preset: " + strategy.getName());
            return strategy;
        } catch (IOException e) {
            System.err.println("Failed to install preset " + presetId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if an installed strategy has a newer version available as a preset.
     */
    public boolean hasNewerPresetVersion(Strategy strategy) {
        if (!strategy.isPreset()) {
            return false;
        }
        String installedVersion = strategy.getPresetVersion();
        if (installedVersion == null) {
            return true; // No version = needs update
        }
        for (PresetInfo preset : getAvailablePresets()) {
            if (preset.id().equals(strategy.getPresetId())) {
                return compareVersions(preset.version(), installedVersion) > 0;
            }
        }
        return false;
    }

    /**
     * Get the bundled version of a preset (or null if not found)
     */
    public String getPresetVersion(String presetId) {
        for (PresetInfo preset : getAvailablePresets()) {
            if (preset.id().equals(presetId)) {
                return preset.version();
            }
        }
        return null;
    }

    /**
     * Compare version strings (simple numeric comparison, e.g., "1.0" vs "1.1")
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }

    /**
     * Restore all presets to their original bundled versions
     */
    public void restoreAllPresets() {
        for (PresetInfo preset : getAvailablePresets()) {
            installPreset(preset.id());
        }
    }
}
