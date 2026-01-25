package com.tradery.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.model.Identifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Generic base class for YAML file stores.
 * Provides common CRUD operations for entities stored in subdirectories.
 *
 * Directory structure: {baseDir}/{id}/{filename}.yaml
 *
 * Includes backward compatibility: if .yaml file doesn't exist but .json does,
 * it will read the JSON and auto-migrate to YAML.
 *
 * @param <T> Entity type that implements Identifiable
 */
public abstract class YamlStore<T extends Identifiable> {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final File directory;
    protected final ObjectMapper yamlMapper;
    protected final ObjectMapper jsonMapper;  // For reading legacy JSON files

    /**
     * Create a new YamlStore.
     *
     * @param directory Base directory for this store
     */
    protected YamlStore(File directory) {
        this.directory = directory;
        this.yamlMapper = createYamlMapper();
        this.jsonMapper = createJsonMapper();

        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * Create and configure the YAML ObjectMapper.
     */
    protected ObjectMapper createYamlMapper() {
        YAMLFactory yamlFactory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)  // No "---" at start
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);         // Cleaner output

        ObjectMapper mapper = new ObjectMapper(yamlFactory);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * Create JSON mapper for reading legacy files.
     */
    protected ObjectMapper createJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * Get the YAML filename for entities in this store (e.g., "strategy.yaml", "phase.yaml").
     */
    protected abstract String getFileName();

    /**
     * Get the legacy JSON filename for backward compatibility.
     */
    protected String getLegacyFileName() {
        return getFileName().replace(".yaml", ".json");
    }

    /**
     * Get the entity class for deserialization.
     */
    protected abstract Class<T> getEntityClass();

    /**
     * Get a display name for this entity type (for logging).
     */
    protected abstract String getEntityName();

    /**
     * Load all entities from the directory.
     */
    public List<T> loadAll() {
        List<T> entities = new ArrayList<>();
        File[] entityDirs = directory.listFiles(File::isDirectory);

        if (entityDirs != null) {
            for (File entityDir : entityDirs) {
                T entity = loadFromDir(entityDir);
                if (entity != null) {
                    entities.add(entity);
                }
            }
        }

        return entities;
    }

    /**
     * Load entity from a directory, checking YAML first, then legacy JSON.
     */
    private T loadFromDir(File entityDir) {
        File yamlFile = new File(entityDir, getFileName());
        File jsonFile = new File(entityDir, getLegacyFileName());

        // Try YAML first
        if (yamlFile.exists()) {
            try {
                return yamlMapper.readValue(yamlFile, getEntityClass());
            } catch (IOException e) {
                log.error("Failed to load {} from {}: {}", getEntityName(), yamlFile, e.getMessage());
                return null;
            }
        }

        // Fall back to JSON and auto-migrate
        if (jsonFile.exists()) {
            try {
                T entity = jsonMapper.readValue(jsonFile, getEntityClass());
                log.info("Migrating {} from JSON to YAML: {}", getEntityName(), entityDir.getName());
                // Save as YAML
                yamlMapper.writeValue(yamlFile, entity);
                // Delete old JSON file
                jsonFile.delete();
                return entity;
            } catch (IOException e) {
                log.error("Failed to load {} from {}: {}", getEntityName(), jsonFile, e.getMessage());
                return null;
            }
        }

        return null;
    }

    /**
     * Load a single entity by ID.
     *
     * @param id Entity ID
     * @return The entity, or null if not found
     */
    public T load(String id) {
        File entityDir = new File(directory, id);
        if (!entityDir.exists()) {
            return null;
        }
        return loadFromDir(entityDir);
    }

    /**
     * Load multiple entities by their IDs.
     *
     * @param ids Collection of entity IDs
     * @return List of found entities (missing IDs are skipped)
     */
    public List<T> loadByIds(Collection<String> ids) {
        List<T> entities = new ArrayList<>();
        for (String id : ids) {
            T entity = load(id);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    /**
     * Save an entity to disk as YAML.
     *
     * @param entity Entity to save
     */
    public void save(T entity) {
        File entityDir = new File(directory, entity.getId());
        if (!entityDir.exists()) {
            entityDir.mkdirs();
        }

        File yamlFile = new File(entityDir, getFileName());
        File jsonFile = new File(entityDir, getLegacyFileName());

        try {
            yamlMapper.writeValue(yamlFile, entity);
            log.info("Saved {} to: {}", getEntityName(), yamlFile.getAbsolutePath());

            // Clean up legacy JSON if it exists
            if (jsonFile.exists()) {
                jsonFile.delete();
                log.info("Removed legacy JSON: {}", jsonFile.getName());
            }
        } catch (IOException e) {
            log.error("Failed to save {} {}: {}", getEntityName(), entity.getId(), e.getMessage());
        }
    }

    /**
     * Delete an entity and its folder.
     *
     * @param id Entity ID to delete
     * @return true if deleted successfully
     */
    public boolean delete(String id) {
        File entityDir = new File(directory, id);
        if (entityDir.exists()) {
            return deleteRecursively(entityDir);
        }
        return false;
    }

    /**
     * Recursively delete a directory and its contents.
     */
    protected boolean deleteRecursively(File file) {
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
     * Check if an entity exists (YAML or legacy JSON).
     *
     * @param id Entity ID
     * @return true if the entity's file exists
     */
    public boolean exists(String id) {
        File yamlFile = new File(directory, id + "/" + getFileName());
        File jsonFile = new File(directory, id + "/" + getLegacyFileName());
        return yamlFile.exists() || jsonFile.exists();
    }

    /**
     * Get the YAML file path for an entity.
     *
     * @param id Entity ID
     * @return File object for the entity's YAML file
     */
    public File getFile(String id) {
        return new File(directory, id + "/" + getFileName());
    }

    /**
     * Get the folder for an entity.
     *
     * @param id Entity ID
     * @return File object for the entity's folder
     */
    public File getFolder(String id) {
        return new File(directory, id);
    }

    /**
     * Get the base directory for this store.
     */
    public File getDirectory() {
        return directory;
    }

    /**
     * Get the YAML ObjectMapper used by this store.
     */
    public ObjectMapper getMapper() {
        return yamlMapper;
    }
}
