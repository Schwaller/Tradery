package com.tradery.news.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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

    // Window settings
    private int windowWidth = 1800;
    private int windowHeight = 1000;
    private int windowX = -1;  // -1 means center
    private int windowY = -1;

    // AI settings
    public enum AiProvider { CLAUDE, CODEX, CUSTOM }
    private AiProvider aiProvider = AiProvider.CLAUDE;
    private String claudePath = "claude";
    private String codexPath = "codex";
    private String customCommand = "";  // Full command for custom AI
    private int aiTimeoutSeconds = 60;

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

    public String getCodexPath() {
        return codexPath;
    }

    public void setCodexPath(String codexPath) {
        this.codexPath = codexPath;
    }

    public String getCustomCommand() {
        return customCommand;
    }

    public void setCustomCommand(String customCommand) {
        this.customCommand = customCommand;
    }

    public int getAiTimeoutSeconds() {
        return aiTimeoutSeconds;
    }

    public void setAiTimeoutSeconds(int aiTimeoutSeconds) {
        this.aiTimeoutSeconds = aiTimeoutSeconds;
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
        if (Files.exists(CONFIG_PATH)) {
            try {
                return YAML.readValue(CONFIG_PATH.toFile(), IntelConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to load intel config: " + e.getMessage());
            }
        }
        // Return default config
        IntelConfig config = new IntelConfig();
        config.setHiddenTopics(new HashSet<>(DEFAULT_HIDDEN_TOPICS));
        config.save();
        return config;
    }
}
