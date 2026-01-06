package com.tradery.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.model.Phase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes Phase JSON files.
 * Each phase has its own folder: ~/.tradery/phases/{id}/phase.json
 *
 * Claude Code can directly read/write these files.
 */
public class PhaseStore {

    private final File directory;
    private final ObjectMapper mapper;

    public PhaseStore(File directory) {
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
     * Load all phases from the directory
     */
    public List<Phase> loadAll() {
        List<Phase> phases = new ArrayList<>();
        File[] phaseDirs = directory.listFiles(File::isDirectory);

        if (phaseDirs != null) {
            for (File phaseDir : phaseDirs) {
                File phaseFile = new File(phaseDir, "phase.json");
                if (phaseFile.exists()) {
                    try {
                        Phase phase = mapper.readValue(phaseFile, Phase.class);
                        phases.add(phase);
                    } catch (IOException e) {
                        System.err.println("Failed to load phase from " + phaseFile + ": " + e.getMessage());
                    }
                }
            }
        }

        return phases;
    }

    /**
     * Load a single phase by ID
     */
    public Phase load(String id) {
        File file = new File(directory, id + "/phase.json");
        if (!file.exists()) {
            return null;
        }

        try {
            return mapper.readValue(file, Phase.class);
        } catch (IOException e) {
            System.err.println("Failed to load phase " + id + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Save a phase to disk
     */
    public void save(Phase phase) {
        File phaseDir = new File(directory, phase.getId());
        if (!phaseDir.exists()) {
            phaseDir.mkdirs();
        }

        File file = new File(phaseDir, "phase.json");

        try {
            mapper.writeValue(file, phase);
            System.out.println("Saved phase to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save phase " + phase.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Delete a phase folder and all its contents
     */
    public boolean delete(String id) {
        File phaseDir = new File(directory, id);
        if (phaseDir.exists()) {
            return deleteRecursively(phaseDir);
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
     * Check if a phase exists
     */
    public boolean exists(String id) {
        File file = new File(directory, id + "/phase.json");
        return file.exists();
    }

    /**
     * Get the phase.json file path for a phase
     */
    public File getFile(String id) {
        return new File(directory, id + "/phase.json");
    }

    /**
     * Get the phase folder for a phase
     */
    public File getFolder(String id) {
        return new File(directory, id);
    }

    /**
     * Get the base directory for phases
     */
    public File getDirectory() {
        return directory;
    }
}
