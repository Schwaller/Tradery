package com.tradery.forge.io;

import com.tradery.core.model.Phase;
import com.tradery.core.phases.BuiltInPhaseLoader;

import java.io.File;
import java.util.List;

/**
 * Reads and writes Phase YAML files.
 * Each phase has its own folder: ~/.tradery/phases/{id}/phase.yaml
 *
 * Backward compatible: auto-migrates legacy phase.json to phase.yaml
 *
 * Uses BuiltInPhaseLoader from tradery-core for built-in phase templates.
 * Claude Code can directly read/write these files.
 */
public class PhaseStore extends YamlStore<Phase> {

    private final BuiltInPhaseLoader builtInLoader = new BuiltInPhaseLoader();

    public PhaseStore(File directory) {
        super(directory);
    }

    @Override
    protected String getFileName() {
        return "phase.yaml";
    }

    @Override
    protected Class<Phase> getEntityClass() {
        return Phase.class;
    }

    @Override
    protected String getEntityName() {
        return "phase";
    }

    // ========== Preset Management (delegates to BuiltInPhaseLoader) ==========

    public record PresetInfo(String id, String name, String version) {}

    /**
     * Load the list of available presets from the bundled manifest
     */
    public List<PresetInfo> getAvailablePresets() {
        return builtInLoader.getAvailablePresets().stream()
            .map(p -> new PresetInfo(p.id(), p.name(), p.version()))
            .toList();
    }

    /**
     * Install/update all built-in presets on startup.
     * Only updates if the resource version is newer than the installed version.
     */
    public void installBuiltInPresets() {
        for (var preset : builtInLoader.getAvailablePresets()) {
            Phase existing = load(preset.id());
            if (existing == null || BuiltInPhaseLoader.isNewerVersion(preset.version(), existing.getVersion())) {
                installPreset(preset.id(), preset.version());
            }
        }
    }

    /**
     * Install or restore a preset phase from bundled resources.
     * Marks the phase as builtIn so it can't be edited.
     */
    public Phase installPreset(String presetId, String version) {
        return builtInLoader.loadPhase(presetId)
            .map(phase -> {
                phase.setVersion(version);
                save(phase);
                log.info("Installed built-in phase: {} v{}", phase.getName(), version);
                return phase;
            })
            .orElseGet(() -> {
                log.warn("Phase preset not found: {}", presetId);
                return null;
            });
    }

    /**
     * Check if a phase ID is a built-in preset
     */
    public boolean isBuiltInPreset(String id) {
        return builtInLoader.isBuiltIn(id);
    }
}
