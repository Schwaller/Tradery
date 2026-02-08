package com.tradery.news.ui;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tradery.news.ai.AiProfile;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

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

    // Data structure window settings
    private int dataStructureWidth = -1;
    private int dataStructureHeight = -1;
    private int dataStructureX = -1;
    private int dataStructureY = -1;

    // ERD settings
    private boolean erdFlowMode = false;

    // AI settings
    public enum AiProvider { CLAUDE, CODEX, CUSTOM, GEMINI }
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

    // News fetch settings (0 = manual only)
    private int fetchIntervalMinutes = 0;
    private Set<String> disabledFeedIds = new HashSet<>();

    // Theme settings (shared with forge via theme.txt)
    private static final Path THEME_PATH = Path.of(
        System.getProperty("user.home"), ".tradery", "theme.txt"
    );
    private static final Map<String, String> THEMES = new LinkedHashMap<>();
    static {
        THEMES.put("Hiberbee Dark", "com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme");
        THEMES.put("Flat Dark", "com.formdev.flatlaf.FlatDarkLaf");
        THEMES.put("Flat Light", "com.formdev.flatlaf.FlatLightLaf");
        THEMES.put("Flat Darcula", "com.formdev.flatlaf.FlatDarculaLaf");
        THEMES.put("macOS Dark", "com.formdev.flatlaf.themes.FlatMacDarkLaf");
        THEMES.put("macOS Light", "com.formdev.flatlaf.themes.FlatMacLightLaf");
        THEMES.put("Arc Dark", "com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme");
        THEMES.put("Dracula", "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme");
        THEMES.put("Nord", "com.formdev.flatlaf.intellijthemes.FlatNordIJTheme");
        THEMES.put("One Dark", "com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme");
        THEMES.put("Monokai Pro", "com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme");
        THEMES.put("Solarized Dark", "com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme");
        THEMES.put("Solarized Light", "com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme");
        THEMES.put("Gruvbox Dark", "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme");
        THEMES.put("Material Oceanic", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMaterialOceanicIJTheme");
        THEMES.put("Carbon", "com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme");
    }

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
        if (aiProfiles.isEmpty()) return null;
        if (defaultProfileId != null) {
            for (AiProfile p : aiProfiles) {
                if (defaultProfileId.equals(p.getId())) return p;
            }
        }
        return aiProfiles.get(0);
    }

    public AiProfile getProfile(String id) {
        for (AiProfile p : aiProfiles) {
            if (id != null && id.equals(p.getId())) return p;
        }
        return null;
    }

    public void addProfile(AiProfile profile) {
        aiProfiles.add(profile);
    }

    public void removeProfile(String id) {
        if (aiProfiles.size() <= 1) return;
        aiProfiles.removeIf(p -> id != null && id.equals(p.getId()));
        if (id != null && id.equals(defaultProfileId)) {
            defaultProfileId = aiProfiles.isEmpty() ? null : aiProfiles.get(0).getId();
        }
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

    // ==================== Theme Settings ====================

    public static List<String> getAvailableThemes() {
        return new ArrayList<>(THEMES.keySet());
    }

    public static String getCurrentTheme() {
        try {
            if (Files.exists(THEME_PATH)) {
                String theme = Files.readString(THEME_PATH).trim();
                if (THEMES.containsKey(theme)) {
                    return theme;
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return "Hiberbee Dark";
    }

    public static void setTheme(String themeName) {
        if (!THEMES.containsKey(themeName)) return;

        try {
            Files.createDirectories(THEME_PATH.getParent());
            Files.writeString(THEME_PATH, themeName);
        } catch (IOException e) {
            System.err.println("Failed to save theme: " + e.getMessage());
        }

        applyTheme(themeName);
    }

    public static void applyTheme(String themeName) {
        String className = THEMES.get(themeName);
        if (className == null) return;

        try {
            UIManager.setLookAndFeel(className);
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
        } catch (Exception e) {
            System.err.println("Failed to apply theme: " + e.getMessage());
        }
    }

    public static void applyCurrentTheme() {
        applyTheme(getCurrentTheme());
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

        // Migrate old flat AI settings to profile if no profiles exist
        // Only for existing configs that had old-style AI fields — fresh installs
        // are handled by the setup dialog in IntelFrame.main()
        if (config.getAiProfiles().isEmpty() && existingConfig) {
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

            config.getAiProfiles().add(profile);
            config.setDefaultProfileId(profile.getId());
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
