package com.tradery.desk.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.model.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * A strategy published to Desk with version metadata.
 * Published strategies are immutable copies stored in ~/.tradery/desk/strategies/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublishedStrategy extends Strategy {

    private static final Logger log = LoggerFactory.getLogger(PublishedStrategy.class);
    private static final ObjectMapper YAML;

    static {
        YAML = new ObjectMapper(new YAMLFactory());
        YAML.registerModule(new JavaTimeModule());
    }

    // Version metadata
    private int version = 1;
    private Instant publishedAt;
    private String publishedFrom = "forge";

    public PublishedStrategy() {
        super();
    }

    /**
     * Create published strategy from a regular strategy.
     */
    public static PublishedStrategy fromStrategy(Strategy strategy, int version) {
        PublishedStrategy pub = new PublishedStrategy();
        // Copy all strategy fields
        pub.setId(strategy.getId());
        pub.setName(strategy.getName());
        pub.setDescription(strategy.getDescription());
        pub.setNotes(strategy.getNotes());
        pub.setEntrySettings(strategy.getEntrySettings());
        pub.setExitSettings(strategy.getExitSettings());
        pub.setBacktestSettings(strategy.getBacktestSettings());
        pub.setPhaseSettings(strategy.getPhaseSettings());
        pub.setHoopPatternSettings(strategy.getHoopPatternSettings());
        pub.setOrderflowSettings(strategy.getOrderflowSettings());
        pub.setEnabled(strategy.isEnabled());
        pub.setCreated(strategy.getCreated());
        pub.setUpdated(strategy.getUpdated());

        // Set version metadata
        pub.version = version;
        pub.publishedAt = Instant.now();
        pub.publishedFrom = "forge";

        return pub;
    }

    /**
     * Load published strategy from YAML file.
     */
    public static PublishedStrategy fromYaml(Path yamlPath) throws IOException {
        return YAML.readValue(yamlPath.toFile(), PublishedStrategy.class);
    }

    /**
     * Save published strategy to YAML file.
     */
    public void toYaml(Path yamlPath) throws IOException {
        Files.createDirectories(yamlPath.getParent());
        YAML.writeValue(yamlPath.toFile(), this);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPublishedFrom() {
        return publishedFrom;
    }

    public void setPublishedFrom(String publishedFrom) {
        this.publishedFrom = publishedFrom;
    }

    @Override
    public String toString() {
        return getName() + " v" + version;
    }
}
