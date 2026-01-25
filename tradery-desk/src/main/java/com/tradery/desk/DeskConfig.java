package com.tradery.desk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for Tradery Desk.
 * Stored in ~/.tradery/desk/desk-config.yaml
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeskConfig {

    private static final Logger log = LoggerFactory.getLogger(DeskConfig.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public static final Path DESK_DIR = Path.of(System.getProperty("user.home"), ".tradery", "desk");
    public static final Path CONFIG_FILE = DESK_DIR.resolve("desk-config.yaml");
    public static final Path STRATEGIES_DIR = DESK_DIR.resolve("strategies");

    // Alert settings
    private AlertSettings alerts = new AlertSettings();

    // History bars to keep for indicator calculation
    private int historyBars = 300;

    // Auto-reload strategies when published
    private boolean autoReload = true;

    public DeskConfig() {
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
            Files.createDirectories(STRATEGIES_DIR);
            config.save();
        } catch (IOException e) {
            log.error("Failed to create default config: {}", e.getMessage());
        }
        return config;
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
