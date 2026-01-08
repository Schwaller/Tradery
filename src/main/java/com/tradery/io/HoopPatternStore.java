package com.tradery.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.model.HoopPattern;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reads and writes HoopPattern JSON files.
 * Each pattern has its own folder: ~/.tradery/hoops/{id}/hoop.json
 *
 * Claude Code can directly read/write these files.
 */
public class HoopPatternStore {

    private final File directory;
    private final ObjectMapper mapper;

    public HoopPatternStore(File directory) {
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
     * Load all hoop patterns from the directory
     */
    public List<HoopPattern> loadAll() {
        List<HoopPattern> patterns = new ArrayList<>();
        File[] patternDirs = directory.listFiles(File::isDirectory);

        if (patternDirs != null) {
            for (File patternDir : patternDirs) {
                File patternFile = new File(patternDir, "hoop.json");
                if (patternFile.exists()) {
                    try {
                        HoopPattern pattern = mapper.readValue(patternFile, HoopPattern.class);
                        patterns.add(pattern);
                    } catch (IOException e) {
                        System.err.println("Failed to load hoop pattern from " + patternFile + ": " + e.getMessage());
                    }
                }
            }
        }

        return patterns;
    }

    /**
     * Load multiple patterns by their IDs
     */
    public List<HoopPattern> loadByIds(Collection<String> ids) {
        List<HoopPattern> patterns = new ArrayList<>();
        for (String id : ids) {
            HoopPattern pattern = load(id);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        return patterns;
    }

    /**
     * Load a single hoop pattern by ID
     */
    public HoopPattern load(String id) {
        File file = new File(directory, id + "/hoop.json");
        if (!file.exists()) {
            return null;
        }

        try {
            return mapper.readValue(file, HoopPattern.class);
        } catch (IOException e) {
            System.err.println("Failed to load hoop pattern " + id + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Save a hoop pattern to disk
     */
    public void save(HoopPattern pattern) {
        File patternDir = new File(directory, pattern.getId());
        if (!patternDir.exists()) {
            patternDir.mkdirs();
        }

        File file = new File(patternDir, "hoop.json");

        try {
            mapper.writeValue(file, pattern);
            System.out.println("Saved hoop pattern to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save hoop pattern " + pattern.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Delete a hoop pattern folder and all its contents
     */
    public boolean delete(String id) {
        File patternDir = new File(directory, id);
        if (patternDir.exists()) {
            return deleteRecursively(patternDir);
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
     * Check if a hoop pattern exists
     */
    public boolean exists(String id) {
        File file = new File(directory, id + "/hoop.json");
        return file.exists();
    }

    /**
     * Get the hoop.json file path for a pattern
     */
    public File getFile(String id) {
        return new File(directory, id + "/hoop.json");
    }

    /**
     * Get the pattern folder for a pattern
     */
    public File getFolder(String id) {
        return new File(directory, id);
    }

    /**
     * Get the base directory for hoop patterns
     */
    public File getDirectory() {
        return directory;
    }
}
