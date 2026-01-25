package com.tradery.forge.publish;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.core.model.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Publishes strategies from Forge to the Strategy Library (iCloud Drive by default).
 *
 * Library structure:
 * ~/Library/Mobile Documents/com~apple~CloudDocs/Tradery/
 * └── strategies/
 *     ├── rsi-reversal/
 *     │   ├── v1.yaml
 *     │   ├── v2.yaml
 *     │   └── v3.yaml
 *     └── ema-crossover/
 *         └── v1.yaml
 *
 * Desk then imports/activates specific versions from this library.
 */
public class DeskPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeskPublisher.class);

    // Default library path on iCloud Drive
    public static final Path DEFAULT_LIBRARY_PATH = Path.of(
        System.getProperty("user.home"),
        "Library", "Mobile Documents", "com~apple~CloudDocs", "Tradery"
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

    private final Path libraryPath;

    public DeskPublisher() {
        this(DEFAULT_LIBRARY_PATH);
    }

    public DeskPublisher(Path libraryPath) {
        this.libraryPath = libraryPath;
    }

    /**
     * Get the strategies directory in the library.
     */
    public Path getStrategiesDir() {
        return libraryPath.resolve("strategies");
    }

    /**
     * Publish a strategy to the library.
     *
     * @param strategy The strategy to publish
     * @return The version number that was published
     * @throws IOException If publishing fails
     */
    public int publish(Strategy strategy) throws IOException {
        Path strategiesDir = getStrategiesDir();
        Path strategyDir = strategiesDir.resolve(strategy.getId());
        Files.createDirectories(strategyDir);

        // Determine next version
        int nextVersion = getNextVersion(strategyDir);

        // Create versioned copy
        Path versionFile = strategyDir.resolve("v" + nextVersion + ".yaml");
        writeWithMetadata(strategy, versionFile, nextVersion);

        log.info("Published {} v{} to library: {}", strategy.getName(), nextVersion, versionFile);
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
     * Get the current published version for a strategy.
     * Returns 0 if not published.
     */
    public int getPublishedVersion(String strategyId) {
        Path strategyDir = getStrategiesDir().resolve(strategyId);
        if (!Files.exists(strategyDir)) {
            return 0;
        }
        return getNextVersion(strategyDir) - 1;
    }

    /**
     * List all versions for a strategy.
     */
    public List<Integer> listVersions(String strategyId) {
        Path strategyDir = getStrategiesDir().resolve(strategyId);
        if (!Files.exists(strategyDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(strategyDir)) {
            return files
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .map(VERSION_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(m -> Integer.parseInt(m.group(1)))
                .sorted()
                .toList();
        } catch (IOException e) {
            log.debug("Error listing versions: {}", e.getMessage());
            return List.of();
        }
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
        Path strategyDir = getStrategiesDir().resolve(strategyId);
        return Files.exists(strategyDir) && getPublishedVersion(strategyId) > 0;
    }

    /**
     * Delete all versions of a strategy from the library.
     */
    public void unpublish(String strategyId) throws IOException {
        Path strategyDir = getStrategiesDir().resolve(strategyId);
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
     * Delete a specific version of a strategy.
     */
    public void deleteVersion(String strategyId, int version) throws IOException {
        Path versionFile = getStrategiesDir().resolve(strategyId).resolve("v" + version + ".yaml");
        if (Files.deleteIfExists(versionFile)) {
            log.info("Deleted {} v{}", strategyId, version);
        }
    }

    /**
     * Get the library path.
     */
    public Path getLibraryPath() {
        return libraryPath;
    }

    /**
     * Ensure the library directory exists (creates iCloud Tradery folder if needed).
     */
    public void ensureLibraryExists() throws IOException {
        Files.createDirectories(getStrategiesDir());
    }
}
