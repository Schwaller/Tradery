package com.tradery.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradery.model.Phase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes Phase JSON files.
 * Each phase has its own folder: ~/.tradery/phases/{id}/phase.json
 *
 * Claude Code can directly read/write these files.
 */
public class PhaseStore extends JsonStore<Phase> {

    public PhaseStore(File directory) {
        super(directory);
    }

    @Override
    protected String getFileName() {
        return "phase.json";
    }

    @Override
    protected Class<Phase> getEntityClass() {
        return Phase.class;
    }

    @Override
    protected String getEntityName() {
        return "phase";
    }

    // ========== Preset Management ==========

    private static final String PRESET_MANIFEST = "/phases/manifest.json";

    public record PresetInfo(String id, String name, String version) {}

    /**
     * Load the list of available presets from the bundled manifest
     */
    public List<PresetInfo> getAvailablePresets() {
        List<PresetInfo> presets = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream(PRESET_MANIFEST)) {
            if (is == null) {
                log.warn("Phase preset manifest not found in resources");
                return presets;
            }
            JsonNode root = mapper.readTree(is);
            JsonNode phases = root.get("phases");
            if (phases != null && phases.isArray()) {
                for (JsonNode node : phases) {
                    presets.add(new PresetInfo(
                        node.get("id").asText(),
                        node.get("name").asText(),
                        node.get("version").asText()
                    ));
                }
            }
        } catch (IOException e) {
            log.error("Failed to load phase preset manifest: {}", e.getMessage());
        }
        return presets;
    }

    /**
     * Install/update all built-in presets on startup.
     * Only updates if the resource version is newer than the installed version.
     */
    public void installBuiltInPresets() {
        List<PresetInfo> presets = getAvailablePresets();
        for (PresetInfo preset : presets) {
            Phase existing = load(preset.id());
            if (existing == null || isNewerVersion(preset.version(), existing.getVersion())) {
                installPreset(preset.id(), preset.version());
            }
        }
    }

    /**
     * Compare version strings. Returns true if v1 is newer than v2.
     * Supports formats like "1.0", "1.1", "2.0", etc.
     */
    private boolean isNewerVersion(String v1, String v2) {
        if (v2 == null || v2.isEmpty()) return true;
        if (v1 == null || v1.isEmpty()) return false;
        try {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            int len = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < len; i++) {
                int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                if (p1 > p2) return true;
                if (p1 < p2) return false;
            }
            return false;  // Equal versions
        } catch (NumberFormatException e) {
            return v1.compareTo(v2) > 0;  // Fall back to string comparison
        }
    }

    /**
     * Install or restore a preset phase from bundled resources.
     * Marks the phase as builtIn so it can't be edited.
     */
    public Phase installPreset(String presetId, String version) {
        String resourcePath = "/phases/" + presetId + "/phase.json";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Phase preset not found: {}", presetId);
                return null;
            }
            Phase phase = mapper.readValue(is, Phase.class);
            phase.setBuiltIn(true);  // Mark as built-in
            phase.setVersion(version);  // Set version from manifest
            save(phase);
            log.info("Installed built-in phase: {} v{}", phase.getName(), version);
            return phase;
        } catch (IOException e) {
            log.error("Failed to install phase preset {}: {}", presetId, e.getMessage());
            return null;
        }
    }

    /**
     * Check if a phase ID is a built-in preset
     */
    public boolean isBuiltInPreset(String id) {
        return getAvailablePresets().stream().anyMatch(p -> p.id().equals(id));
    }
}
