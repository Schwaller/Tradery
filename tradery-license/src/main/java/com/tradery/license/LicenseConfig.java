package com.tradery.license;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * License configuration stored in ~/.tradery/license.yaml.
 * Follows the DeskConfig pattern: @JsonIgnoreProperties, static load()/save(), YAML ObjectMapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LicenseConfig {

    private static final ObjectMapper YAML;
    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".tradery");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("license.yaml");

    static {
        YAMLFactory factory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        YAML = new ObjectMapper(factory);
    }

    private String licenseKey;

    public LicenseConfig() {}

    public String getLicenseKey() {
        return licenseKey;
    }

    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey;
    }

    /**
     * Load config from file or return empty config.
     */
    public static LicenseConfig load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                return YAML.readValue(CONFIG_FILE.toFile(), LicenseConfig.class);
            }
        } catch (IOException e) {
            System.err.println("Failed to load license config: " + e.getMessage());
        }
        return new LicenseConfig();
    }

    /**
     * Save config to file.
     */
    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            YAML.writeValue(CONFIG_FILE.toFile(), this);
        } catch (IOException e) {
            System.err.println("Failed to save license config: " + e.getMessage());
        }
    }

    /**
     * Get the config file path.
     */
    public static Path getConfigFile() {
        return CONFIG_FILE;
    }
}
