package com.tradery.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.model.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Publishes strategies from Forge to Desk.
 *
 * Desk uses its own folder with versioned, published strategies:
 * ~/.tradery/desk/strategies/{id}/
 *   - v1.yaml, v2.yaml, ... (archived versions)
 *   - active.yaml (currently active version, copy of latest)
 */
public class DeskPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeskPublisher.class);
    private static final Path DESK_DIR = Path.of(
        System.getProperty("user.home"), ".tradery", "desk", "strategies"
    );
    private static final ObjectMapper YAML;
    private static final Pattern VERSION_PATTERN = Pattern.compile("v(\\d+)\\.yaml");

    static {
        YAMLFactory yamlFactory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        YAML = new ObjectMapper(yamlFactory);
        YAML.registerModule(new JavaTimeModule());
        YAML.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Publish a strategy to Desk.
     *
     * @param strategy The strategy to publish
     * @return The version number that was published
     * @throws IOException If publishing fails
     */
    public int publish(Strategy strategy) throws IOException {
        Path strategyDir = DESK_DIR.resolve(strategy.getId());
        Files.createDirectories(strategyDir);

        // Determine next version
        int nextVersion = getNextVersion(strategyDir);

        // Create versioned copy
        Path versionFile = strategyDir.resolve("v" + nextVersion + ".yaml");
        writeWithMetadata(strategy, versionFile, nextVersion);

        // Update active.yaml (what Desk runs)
        Path activeFile = strategyDir.resolve("active.yaml");
        Files.copy(versionFile, activeFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        log.info("Published {} v{} to {}", strategy.getName(), nextVersion, activeFile);
        return nextVersion;
    }

    /**
     * Get the next version number for a strategy.
     */
    private int getNextVersion(Path strategyDir) {
        int maxVersion = 0;

        try (Stream<Path> files = Files.list(strategyDir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                Matcher matcher = VERSION_PATTERN.matcher(name);
                if (matcher.matches()) {
                    int version = Integer.parseInt(matcher.group(1));
                    maxVersion = Math.max(maxVersion, version);
                }
            }
        } catch (IOException e) {
            log.debug("Error reading versions: {}", e.getMessage());
        }

        return maxVersion + 1;
    }

    /**
     * Write strategy with publishing metadata.
     */
    private void writeWithMetadata(Strategy strategy, Path file, int version) throws IOException {
        // Build a map with version metadata first, then strategy fields
        Map<String, Object> data = new LinkedHashMap<>();

        // Add all strategy fields
        Map<String, Object> strategyMap = YAML.convertValue(strategy,
            new TypeReference<LinkedHashMap<String, Object>>() {});
        data.putAll(strategyMap);

        // Add/override version metadata
        data.put("version", version);
        data.put("publishedAt", Instant.now().toString());
        data.put("publishedFrom", "forge");

        YAML.writeValue(file.toFile(), data);
    }

    /**
     * Check if a strategy has been published.
     */
    public boolean isPublished(String strategyId) {
        Path activeFile = DESK_DIR.resolve(strategyId).resolve("active.yaml");
        return Files.exists(activeFile);
    }

    /**
     * Get the current published version for a strategy.
     * Returns 0 if not published.
     */
    public int getPublishedVersion(String strategyId) {
        Path strategyDir = DESK_DIR.resolve(strategyId);
        if (!Files.exists(strategyDir)) {
            return 0;
        }
        return getNextVersion(strategyDir) - 1;
    }

    /**
     * Unpublish a strategy (remove from Desk).
     */
    public void unpublish(String strategyId) throws IOException {
        Path strategyDir = DESK_DIR.resolve(strategyId);
        if (Files.exists(strategyDir)) {
            // Delete all files in the directory
            try (Stream<Path> files = Files.list(strategyDir)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(strategyDir);
            log.info("Unpublished strategy: {}", strategyId);
        }
    }

    /**
     * Get the Desk strategies directory.
     */
    public Path getDeskDir() {
        return DESK_DIR;
    }
}
