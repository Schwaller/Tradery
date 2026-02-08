package com.tradery.desk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Trading Desk.
 * Stored in ~/.tradery/desk/desk-config.yaml
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeskConfig {

    private static final Logger log = LoggerFactory.getLogger(DeskConfig.class);
    private static final ObjectMapper YAML;

    static {
        YAMLFactory factory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        YAML = new ObjectMapper(factory);
    }

    // Desk directories
    public static final Path DESK_DIR = Path.of(System.getProperty("user.home"), ".tradery", "desk");
    public static final Path CONFIG_FILE = DESK_DIR.resolve("desk-config.yaml");
    public static final Path ACTIVE_DIR = DESK_DIR.resolve("active");

    // Default library path on iCloud Drive
    public static final Path DEFAULT_LIBRARY_PATH = Path.of(
        System.getProperty("user.home"),
        "Library", "Mobile Documents", "com~apple~CloudDocs", "Plaiiin"
    );

    // Library path (where Forge publishes strategies)
    private String libraryPath = DEFAULT_LIBRARY_PATH.toString();

    // Activated strategies (imported from library)
    private List<ActivatedStrategy> activatedStrategies = new ArrayList<>();

    // Alert settings
    private AlertSettings alerts = new AlertSettings();

    // History bars to keep for indicator calculation
    private int historyBars = 300;

    // Auto-reload strategies when files change
    private boolean autoReload = true;

    public DeskConfig() {
    }

    public String getLibraryPath() {
        return libraryPath;
    }

    public void setLibraryPath(String libraryPath) {
        this.libraryPath = libraryPath;
    }

    public Path getLibraryDir() {
        // Expand ~ if present
        String path = libraryPath;
        if (path.startsWith("~")) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        return Path.of(path);
    }

    public Path getLibraryStrategiesDir() {
        return getLibraryDir().resolve("strategies");
    }

    public List<ActivatedStrategy> getActivatedStrategies() {
        return activatedStrategies;
    }

    public void setActivatedStrategies(List<ActivatedStrategy> activatedStrategies) {
        this.activatedStrategies = activatedStrategies;
    }

    public AlertSettings getAlerts() {
        return alerts;
    }

    public void setAlerts(AlertSettings alerts) {
        this.alerts = alerts;
    }

    public int getHistoryBars() {
        return historyBars;
    }

    public void setHistoryBars(int historyBars) {
        this.historyBars = historyBars;
    }

    public boolean isAutoReload() {
        return autoReload;
    }

    public void setAutoReload(boolean autoReload) {
        this.autoReload = autoReload;
    }

    /**
     * Load config from file or create default.
     */
    public static DeskConfig load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                return YAML.readValue(CONFIG_FILE.toFile(), DeskConfig.class);
            }
        } catch (IOException e) {
            log.warn("Failed to load config, using defaults: {}", e.getMessage());
        }
        return createDefault();
    }

    /**
     * Save config to file.
     */
    public void save() {
        try {
            Files.createDirectories(DESK_DIR);
            YAML.writeValue(CONFIG_FILE.toFile(), this);
        } catch (IOException e) {
            log.error("Failed to save config: {}", e.getMessage());
        }
    }

    /**
     * Create and save default config.
     */
    public static DeskConfig createDefault() {
        DeskConfig config = new DeskConfig();
        try {
            Files.createDirectories(DESK_DIR);
            Files.createDirectories(ACTIVE_DIR);
            config.save();
        } catch (IOException e) {
            log.error("Failed to create default config: {}", e.getMessage());
        }
        return config;
    }

    /**
     * Activate a strategy from the library.
     */
    public void activateStrategy(String strategyId, int version) {
        // Remove any existing activation for this strategy
        activatedStrategies.removeIf(s -> s.getId().equals(strategyId));
        // Add new activation
        activatedStrategies.add(new ActivatedStrategy(strategyId, version));
        save();
    }

    /**
     * Deactivate a strategy.
     */
    public void deactivateStrategy(String strategyId) {
        activatedStrategies.removeIf(s -> s.getId().equals(strategyId));
        save();
    }

    /**
     * Get activation info for a strategy.
     */
    public ActivatedStrategy getActivation(String strategyId) {
        return activatedStrategies.stream()
            .filter(s -> s.getId().equals(strategyId))
            .findFirst()
            .orElse(null);
    }

    /**
     * An activated strategy reference.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActivatedStrategy {
        private String id;
        private int version;

        public ActivatedStrategy() {
        }

        public ActivatedStrategy(String id, int version) {
            this.id = id;
            this.version = version;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }
    }

    /**
     * Alert configuration settings.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AlertSettings {
        private boolean desktop = true;
        private boolean audio = true;
        private boolean console = true;
        private WebhookSettings webhook = new WebhookSettings();

        public boolean isDesktop() {
            return desktop;
        }

        public void setDesktop(boolean desktop) {
            this.desktop = desktop;
        }

        public boolean isAudio() {
            return audio;
        }

        public void setAudio(boolean audio) {
            this.audio = audio;
        }

        public boolean isConsole() {
            return console;
        }

        public void setConsole(boolean console) {
            this.console = console;
        }

        public WebhookSettings getWebhook() {
            return webhook;
        }

        public void setWebhook(WebhookSettings webhook) {
            this.webhook = webhook;
        }
    }

    /**
     * Webhook configuration.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookSettings {
        private boolean enabled = false;
        private String url = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
