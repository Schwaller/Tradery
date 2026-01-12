package com.tradery.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
 * Generic base class for JSON file stores.
 * Provides common CRUD operations for entities stored in subdirectories.
 *
 * Directory structure: {baseDir}/{id}/{filename}.json
 *
 * @param <T> Entity type that implements Identifiable
 */
public abstract class JsonStore<T extends Identifiable> {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final File directory;
    protected final ObjectMapper mapper;

    /**
     * Create a new JsonStore.
     *
     * @param directory Base directory for this store
     */
    protected JsonStore(File directory) {
        this.directory = directory;
        this.mapper = createMapper();

        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * Create and configure the ObjectMapper.
     * Subclasses can override to customize serialization.
     */
    protected ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * Get the filename for entities in this store (e.g., "strategy.json", "phase.json").
     */
    protected abstract String getFileName();

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
                File file = new File(entityDir, getFileName());
                if (file.exists()) {
                    try {
                        T entity = mapper.readValue(file, getEntityClass());
                        entities.add(entity);
                    } catch (IOException e) {
                        log.error("Failed to load {} from {}: {}", getEntityName(), file, e.getMessage());
                    }
                }
            }
        }

        return entities;
    }

    /**
     * Load a single entity by ID.
     *
     * @param id Entity ID
     * @return The entity, or null if not found
     */
    public T load(String id) {
        File file = new File(directory, id + "/" + getFileName());
        if (!file.exists()) {
            return null;
        }

        try {
            return mapper.readValue(file, getEntityClass());
        } catch (IOException e) {
            log.error("Failed to load {} {}: {}", getEntityName(), id, e.getMessage());
            return null;
        }
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
     * Save an entity to disk.
     *
     * @param entity Entity to save
     */
    public void save(T entity) {
        File entityDir = new File(directory, entity.getId());
        if (!entityDir.exists()) {
            entityDir.mkdirs();
        }

        File file = new File(entityDir, getFileName());

        try {
            mapper.writeValue(file, entity);
            log.info("Saved {} to: {}", getEntityName(), file.getAbsolutePath());
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
     * Check if an entity exists.
     *
     * @param id Entity ID
     * @return true if the entity's JSON file exists
     */
    public boolean exists(String id) {
        File file = new File(directory, id + "/" + getFileName());
        return file.exists();
    }

    /**
     * Get the JSON file path for an entity.
     *
     * @param id Entity ID
     * @return File object for the entity's JSON file
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
     * Get the ObjectMapper used by this store.
     */
    public ObjectMapper getMapper() {
        return mapper;
    }
}
