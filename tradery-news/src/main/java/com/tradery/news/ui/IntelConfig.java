package com.tradery.news.ui;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tradery.ai.AiConfig;
import com.tradery.ai.AiProfile;
import com.tradery.ai.AiProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.UUID;

/**
 * Configuration for the Intelligence app.
 * Stored in ~/.tradery/intel-config.yaml
 */
public class IntelConfig {

    private static final Path CONFIG_PATH = Path.of(
        System.getProperty("user.home"), ".tradery", "intel-config.yaml"
    );
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static IntelConfig instance;

    // News map settings
    private Set<String> hiddenTopics = new HashSet<>();

    // Main window settings
    private int windowWidth = 1800;
    private int windowHeight = 1000;
    private int windowX = -1;  // -1 means center
    private int windowY = -1;

    // Settings window settings
    private int settingsWidth = -1;   // -1 means use default (80% of screen)
    private int settingsHeight = -1;
    private int settingsX = -1;
    private int settingsY = -1;

    // Launcher window settings
    private int launcherWidth = -1;
    private int launcherHeight = -1;
    private int launcherX = -1;
    private int launcherY = -1;

    // Last opened document
    private String lastOpenedDocId;

    // User identity for sharing
    private String userEmail;

    // Unique device ID (generated on first load, persists across sessions)
    private String deviceId;

    // Data structure window settings
    private int dataStructureWidth = -1;
    private int dataStructureHeight = -1;
    private int dataStructureX = -1;
    private int dataStructureY = -1;

    // ERD settings
    private boolean erdFlowMode = false;

    // AI settings (AiProvider enum moved to com.tradery.ai)
    private AiProvider aiProvider = AiProvider.CLAUDE;
    private String claudePath = "claude";
    private String claudeArgs = "--print --output-format text --model haiku";
    private String codexPath = "codex";
    private String codexArgs = "exec";
    private String customCommand = "";  // Full command for custom AI
    private String geminiApiKey = "";
    private String geminiModel = "gemini-2.5-flash-lite";
    private int aiTimeoutSeconds = 60;

    // AI profiles
    private List<AiProfile> aiProfiles = new ArrayList<>();
    private String defaultProfileId = null;

    // Panel configurations
    private List<PanelConfig> panels = new ArrayList<>();

    // Friends
    private List<FriendConfig> friends = new ArrayList<>();

    // News fetch settings (0 = manual only)
    private int fetchIntervalMinutes = 0;
    private Set<String> disabledFeedIds = new HashSet<>();

    // Default hidden topics
    private static final Set<String> DEFAULT_HIDDEN_TOPICS = Set.of("crypto");

    public IntelConfig() {
        // Default constructor for YAML
    }

    // ==================== News Map Settings ====================

    public Set<String> getHiddenTopics() {
        return hiddenTopics;
    }

    public void setHiddenTopics(Set<String> hiddenTopics) {
        this.hiddenTopics = hiddenTopics != null ? hiddenTopics : new HashSet<>();
    }

    public boolean isTopicHidden(String topicId) {
        return hiddenTopics.contains(topicId.toLowerCase());
    }

    public void setTopicHidden(String topicId, boolean hidden) {
        if (hidden) {
            hiddenTopics.add(topicId.toLowerCase());
        } else {
            hiddenTopics.remove(topicId.toLowerCase());
        }
    }

    // ==================== Window Settings ====================

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = windowHeight;
    }

    public int getWindowX() {
        return windowX;
    }

    public void setWindowX(int windowX) {
        this.windowX = windowX;
    }

    public int getWindowY() {
        return windowY;
    }

    public void setWindowY(int windowY) {
        this.windowY = windowY;
    }

    // Settings window
    public int getSettingsWidth() {
        return settingsWidth;
    }

    public void setSettingsWidth(int settingsWidth) {
        this.settingsWidth = settingsWidth;
    }

    public int getSettingsHeight() {
        return settingsHeight;
    }

    public void setSettingsHeight(int settingsHeight) {
        this.settingsHeight = settingsHeight;
    }

    public int getSettingsX() {
        return settingsX;
    }

    public void setSettingsX(int settingsX) {
        this.settingsX = settingsX;
    }

    public int getSettingsY() {
        return settingsY;
    }

    public void setSettingsY(int settingsY) {
        this.settingsY = settingsY;
    }

    // Launcher window
    public int getLauncherWidth() { return launcherWidth; }
    public void setLauncherWidth(int launcherWidth) { this.launcherWidth = launcherWidth; }
    public int getLauncherHeight() { return launcherHeight; }
    public void setLauncherHeight(int launcherHeight) { this.launcherHeight = launcherHeight; }
    public int getLauncherX() { return launcherX; }
    public void setLauncherX(int launcherX) { this.launcherX = launcherX; }
    public int getLauncherY() { return launcherY; }
    public void setLauncherY(int launcherY) { this.launcherY = launcherY; }

    // Last opened document
    public String getLastOpenedDocId() { return lastOpenedDocId; }
    public void setLastOpenedDocId(String lastOpenedDocId) { this.lastOpenedDocId = lastOpenedDocId; }

    // User identity
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    // Device ID
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    // Data structure window
    public int getDataStructureWidth() {
        return dataStructureWidth;
    }

    public void setDataStructureWidth(int dataStructureWidth) {
        this.dataStructureWidth = dataStructureWidth;
    }

    public int getDataStructureHeight() {
        return dataStructureHeight;
    }

    public void setDataStructureHeight(int dataStructureHeight) {
        this.dataStructureHeight = dataStructureHeight;
    }

    public int getDataStructureX() {
        return dataStructureX;
    }

    public void setDataStructureX(int dataStructureX) {
        this.dataStructureX = dataStructureX;
    }

    public int getDataStructureY() {
        return dataStructureY;
    }

    public void setDataStructureY(int dataStructureY) {
        this.dataStructureY = dataStructureY;
    }

    // ==================== ERD Settings ====================

    public boolean isErdFlowMode() {
        return erdFlowMode;
    }

    public void setErdFlowMode(boolean erdFlowMode) {
        this.erdFlowMode = erdFlowMode;
    }

    // ==================== AI Settings ====================

    public AiProvider getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getClaudePath() {
        return claudePath;
    }

    public void setClaudePath(String claudePath) {
        this.claudePath = claudePath;
    }

    public String getClaudeArgs() {
        return claudeArgs;
    }

    public void setClaudeArgs(String claudeArgs) {
        this.claudeArgs = claudeArgs;
    }

    public String getCodexPath() {
        return codexPath;
    }

    public void setCodexPath(String codexPath) {
        this.codexPath = codexPath;
    }

    public String getCodexArgs() {
        return codexArgs;
    }

    public void setCodexArgs(String codexArgs) {
        this.codexArgs = codexArgs;
    }

    public String getCustomCommand() {
        return customCommand;
    }

    public void setCustomCommand(String customCommand) {
        this.customCommand = customCommand;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    public int getAiTimeoutSeconds() {
        return aiTimeoutSeconds;
    }

    public void setAiTimeoutSeconds(int aiTimeoutSeconds) {
        this.aiTimeoutSeconds = aiTimeoutSeconds;
    }

    // ==================== AI Profile Settings ====================
    // Delegates to AiConfig for actual storage.
    // Local fields kept for YAML backward compat (deserialization from old configs).

    public List<AiProfile> getAiProfiles() {
        return aiProfiles;
    }

    public void setAiProfiles(List<AiProfile> aiProfiles) {
        this.aiProfiles = aiProfiles != null ? aiProfiles : new ArrayList<>();
    }

    public String getDefaultProfileId() {
        return defaultProfileId;
    }

    public void setDefaultProfileId(String defaultProfileId) {
        this.defaultProfileId = defaultProfileId;
    }

    @JsonIgnore
    public AiProfile getDefaultProfile() {
        return AiConfig.get().getDefaultProfile();
    }

    public AiProfile getProfile(String id) {
        return AiConfig.get().getProfile(id);
    }

    public void addProfile(AiProfile profile) {
        AiConfig.get().addProfile(profile);
    }

    public void removeProfile(String id) {
        AiConfig.get().removeProfile(id);
    }

    // ==================== Panel Settings ====================

    public List<PanelConfig> getPanels() {
        return panels;
    }

    public void setPanels(List<PanelConfig> panels) {
        this.panels = panels != null ? panels : new ArrayList<>();
    }

    public void addPanel(PanelConfig panel) {
        panels.add(panel);
    }

    public void removePanel(String id) {
        panels.removeIf(p -> id != null && id.equals(p.getId()));
    }

    @JsonIgnore
    public PanelConfig getPanelById(String id) {
        for (PanelConfig p : panels) {
            if (id != null && id.equals(p.getId())) return p;
        }
        return null;
    }

    // ==================== Friends ====================

    public List<FriendConfig> getFriends() { return friends; }

    public void setFriends(List<FriendConfig> friends) {
        this.friends = friends != null ? friends : new ArrayList<>();
    }

    public void addFriend(FriendConfig friend) {
        friends.removeIf(f -> friend.getEmail().equalsIgnoreCase(f.getEmail()));
        friends.add(friend);
    }

    public void removeFriend(String email) {
        friends.removeIf(f -> email.equalsIgnoreCase(f.getEmail()));
    }

    @JsonIgnore
    public FriendConfig getFriendByEmail(String email) {
        for (FriendConfig f : friends) {
            if (email.equalsIgnoreCase(f.getEmail())) return f;
        }
        return null;
    }

    // ==================== Fetch Settings ====================

    public int getFetchIntervalMinutes() {
        return fetchIntervalMinutes;
    }

    public void setFetchIntervalMinutes(int fetchIntervalMinutes) {
        this.fetchIntervalMinutes = fetchIntervalMinutes;
    }

    public Set<String> getDisabledFeedIds() {
        return disabledFeedIds;
    }

    public void setDisabledFeedIds(Set<String> disabledFeedIds) {
        this.disabledFeedIds = disabledFeedIds != null ? disabledFeedIds : new HashSet<>();
    }

    public boolean isFeedDisabled(String feedId) {
        return disabledFeedIds.contains(feedId);
    }

    public void setFeedDisabled(String feedId, boolean disabled) {
        if (disabled) {
            disabledFeedIds.add(feedId);
        } else {
            disabledFeedIds.remove(feedId);
        }
    }

    // ==================== Persistence ====================

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            YAML.writeValue(CONFIG_PATH.toFile(), this);
        } catch (IOException e) {
            System.err.println("Failed to save intel config: " + e.getMessage());
        }
    }

    public static IntelConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static IntelConfig load() {
        IntelConfig config;
        boolean existingConfig = Files.exists(CONFIG_PATH);
        if (existingConfig) {
            try {
                config = YAML.readValue(CONFIG_PATH.toFile(), IntelConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to load intel config: " + e.getMessage());
                config = new IntelConfig();
                config.setHiddenTopics(new HashSet<>(DEFAULT_HIDDEN_TOPICS));
                existingConfig = false;
            }
        } else {
            config = new IntelConfig();
            config.setHiddenTopics(new HashSet<>(DEFAULT_HIDDEN_TOPICS));
        }

        // Migrate old flat AI settings to AiConfig if no profiles exist anywhere.
        // Only for existing configs that had old-style AI fields — fresh installs
        // are handled by the setup dialog in IntelFrame.main()
        if (AiConfig.get().getProfiles().isEmpty() && existingConfig) {
            AiProfile profile = new AiProfile();
            profile.setProvider(config.getAiProvider());
            profile.setTimeoutSeconds(config.getAiTimeoutSeconds());

            switch (config.getAiProvider()) {
                case CLAUDE -> {
                    profile.setId("claude");
                    profile.setName("Claude");
                    profile.setPath(config.getClaudePath());
                    profile.setArgs(config.getClaudeArgs());
                }
                case CODEX -> {
                    profile.setId("codex");
                    profile.setName("Codex");
                    profile.setPath(config.getCodexPath());
                    profile.setArgs(config.getCodexArgs());
                }
                case GEMINI -> {
                    profile.setId("gemini");
                    profile.setName("Gemini");
                    profile.setApiKey(config.getGeminiApiKey());
                    profile.setModel(config.getGeminiModel());
                }
                case CUSTOM -> {
                    profile.setId("custom");
                    profile.setName("Custom");
                    profile.setCommand(config.getCustomCommand());
                }
            }

            AiConfig.get().addProfile(profile);
            AiConfig.get().setDefaultProfileId(profile.getId());
            AiConfig.get().save();
        }

        // Generate device ID on first load
        if (config.deviceId == null) {
            config.deviceId = UUID.randomUUID().toString();
            config.save();
        }

        // Seed default panels if none exist
        if (config.getPanels().isEmpty()) {
            config.setPanels(PanelConfig.defaults());
            config.save();
        }

        return config;
    }
}
