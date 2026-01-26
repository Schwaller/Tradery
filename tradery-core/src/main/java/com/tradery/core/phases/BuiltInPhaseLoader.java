package com.tradery.core.phases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.tradery.core.model.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads built-in phase templates from classpath resources.
 * Phase templates are bundled in /phases/ directory.
 *
 * <p>This class provides read-only access to built-in phases.
 * Applications can use these as templates or install them to user directories.</p>
 */
public class BuiltInPhaseLoader {

    private static final Logger log = LoggerFactory.getLogger(BuiltInPhaseLoader.class);
    private static final String PHASES_ROOT = "/phases/";
    private static final String MANIFEST_PATH = PHASES_ROOT + "manifest.json";

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();

    /**
     * Information about a built-in phase preset.
     */
    public record PhasePreset(String id, String name, String category, String version) {}

    /**
     * Get list of all available built-in phase presets.
     */
    public List<PhasePreset> getAvailablePresets() {
        List<PhasePreset> presets = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream(MANIFEST_PATH)) {
            if (is == null) {
                log.warn("Phase manifest not found at {}", MANIFEST_PATH);
                return presets;
            }
            JsonNode root = jsonMapper.readTree(is);
            JsonNode phases = root.get("phases");
            if (phases != null && phases.isArray()) {
                for (JsonNode node : phases) {
                    presets.add(new PhasePreset(
                        node.get("id").asText(),
                        node.has("name") ? node.get("name").asText() : node.get("id").asText(),
                        node.has("category") ? node.get("category").asText() : "Custom",
                        node.has("version") ? node.get("version").asText() : "1.0"
                    ));
                }
            }
        } catch (IOException e) {
            log.error("Failed to load phase manifest: {}", e.getMessage());
        }
        return presets;
    }

    /**
     * Load a built-in phase by ID.
     * Tries YAML first, then falls back to JSON for legacy compatibility.
     *
     * @param phaseId The phase ID (e.g., "uptrend", "asian-session")
     * @return The loaded Phase, or empty if not found
     */
    public Optional<Phase> loadPhase(String phaseId) {
        // Try YAML first (preferred format)
        String yamlPath = PHASES_ROOT + phaseId + "/phase.yaml";
        try (InputStream is = getClass().getResourceAsStream(yamlPath)) {
            if (is != null) {
                Phase phase = yamlMapper.readValue(is, Phase.class);
                phase.setBuiltIn(true);
                return Optional.of(phase);
            }
        } catch (IOException e) {
            log.debug("Failed to load phase {} from YAML: {}", phaseId, e.getMessage());
        }

        // Fall back to JSON (legacy format)
        String jsonPath = PHASES_ROOT + phaseId + "/phase.json";
        try (InputStream is = getClass().getResourceAsStream(jsonPath)) {
            if (is != null) {
                Phase phase = jsonMapper.readValue(is, Phase.class);
                phase.setBuiltIn(true);
                return Optional.of(phase);
            }
        } catch (IOException e) {
            log.debug("Failed to load phase {} from JSON: {}", phaseId, e.getMessage());
        }

        log.warn("Built-in phase not found: {}", phaseId);
        return Optional.empty();
    }

    /**
     * Load all built-in phases.
     */
    public List<Phase> loadAllPhases() {
        List<Phase> phases = new ArrayList<>();
        for (PhasePreset preset : getAvailablePresets()) {
            loadPhase(preset.id()).ifPresent(phase -> {
                phase.setVersion(preset.version());
                phases.add(phase);
            });
        }
        return phases;
    }

    /**
     * Check if a phase ID corresponds to a built-in preset.
     */
    public boolean isBuiltIn(String phaseId) {
        return getAvailablePresets().stream()
            .anyMatch(p -> p.id().equals(phaseId));
    }

    /**
     * Get phases grouped by category.
     */
    public List<PhasePreset> getPresetsByCategory(String category) {
        return getAvailablePresets().stream()
            .filter(p -> p.category().equalsIgnoreCase(category))
            .toList();
    }

    /**
     * Compare version strings. Returns true if v1 is newer than v2.
     */
    public static boolean isNewerVersion(String v1, String v2) {
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
            return v1.compareTo(v2) > 0;
        }
    }
}
