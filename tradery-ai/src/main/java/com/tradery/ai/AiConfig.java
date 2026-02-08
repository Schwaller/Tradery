package com.tradery.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone AI configuration stored in ~/.tradery/ai-config.yaml.
 * Manages AI profiles independently of any specific application's config.
 */
public class AiConfig {

    private static final Path CONFIG_PATH = Path.of(
        System.getProperty("user.home"), ".tradery", "ai-config.yaml"
    );
    private static final Path INTEL_CONFIG_PATH = Path.of(
        System.getProperty("user.home"), ".tradery", "intel-config.yaml"
    );
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static AiConfig instance;

    private List<AiProfile> profiles = new ArrayList<>();
    private String defaultProfileId = null;

    public AiConfig() {
    }

    // ==================== Profile Management ====================

    public List<AiProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<AiProfile> profiles) {
        this.profiles = profiles != null ? profiles : new ArrayList<>();
    }

    public String getDefaultProfileId() {
        return defaultProfileId;
    }

    public void setDefaultProfileId(String defaultProfileId) {
        this.defaultProfileId = defaultProfileId;
    }

    @JsonIgnore
    public AiProfile getDefaultProfile() {
        if (profiles.isEmpty()) return null;
        if (defaultProfileId != null) {
            for (AiProfile p : profiles) {
                if (defaultProfileId.equals(p.getId())) return p;
            }
        }
        return profiles.get(0);
    }

    public AiProfile getProfile(String id) {
        for (AiProfile p : profiles) {
            if (id != null && id.equals(p.getId())) return p;
        }
        return null;
    }

    public void addProfile(AiProfile profile) {
        profiles.add(profile);
    }

    public void removeProfile(String id) {
        if (profiles.size() <= 1) return;
        profiles.removeIf(p -> id != null && id.equals(p.getId()));
        if (id != null && id.equals(defaultProfileId)) {
            defaultProfileId = profiles.isEmpty() ? null : profiles.get(0).getId();
        }
    }

    // ==================== Persistence ====================

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            YAML.writeValue(CONFIG_PATH.toFile(), this);
        } catch (IOException e) {
            System.err.println("Failed to save AI config: " + e.getMessage());
        }
    }

    public static synchronized AiConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AiConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                return YAML.readValue(CONFIG_PATH.toFile(), AiConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to load AI config: " + e.getMessage());
                return new AiConfig();
            }
        }

        // Migration: try to import profiles from intel-config.yaml
        AiConfig config = migrateFromIntelConfig();
        if (config != null) {
            config.save();
            return config;
        }

        return new AiConfig();
    }

    /**
     * One-time migration from intel-config.yaml to ai-config.yaml.
     * Reads the aiProfiles and defaultProfileId fields from the old config.
     */
    private static AiConfig migrateFromIntelConfig() {
        if (!Files.exists(INTEL_CONFIG_PATH)) return null;

        try {
            // Read as a tree to extract just the AI profile fields
            var tree = YAML.readTree(INTEL_CONFIG_PATH.toFile());
            var profilesNode = tree.get("aiProfiles");
            if (profilesNode == null || !profilesNode.isArray() || profilesNode.isEmpty()) {
                return null;
            }

            AiConfig config = new AiConfig();
            for (var node : profilesNode) {
                AiProfile profile = YAML.treeToValue(node, AiProfile.class);
                config.addProfile(profile);
            }

            var defaultIdNode = tree.get("defaultProfileId");
            if (defaultIdNode != null && !defaultIdNode.isNull()) {
                config.setDefaultProfileId(defaultIdNode.asText());
            }

            System.out.println("Migrated " + config.getProfiles().size() + " AI profiles from intel-config.yaml to ai-config.yaml");
            return config;
        } catch (Exception e) {
            System.err.println("Failed to migrate AI profiles from intel-config: " + e.getMessage());
            return null;
        }
    }
}
