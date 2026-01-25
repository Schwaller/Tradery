package com.tradery.desk.strategy;

import com.tradery.desk.DeskConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Manages the strategy library (iCloud Drive by default).
 * Provides access to published strategies and their versions.
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
 */
public class StrategyLibrary {

    private static final Logger log = LoggerFactory.getLogger(StrategyLibrary.class);
    private static final Pattern VERSION_PATTERN = Pattern.compile("v(\\d+)\\.yaml");

    private final Path libraryDir;
    private final Path strategiesDir;

    public StrategyLibrary(DeskConfig config) {
        this.libraryDir = config.getLibraryDir();
        this.strategiesDir = config.getLibraryStrategiesDir();
    }

    public StrategyLibrary(Path libraryDir) {
        this.libraryDir = libraryDir;
        this.strategiesDir = libraryDir.resolve("strategies");
    }

    /**
     * Check if the library exists and is accessible.
     */
    public boolean isAvailable() {
        return Files.isDirectory(strategiesDir);
    }

    /**
     * Ensure the library directories exist.
     */
    public void ensureExists() throws IOException {
        Files.createDirectories(strategiesDir);
    }

    /**
     * List all strategies in the library.
     */
    public List<String> listStrategies() {
        List<String> strategies = new ArrayList<>();

        if (!Files.isDirectory(strategiesDir)) {
            return strategies;
        }

        try (Stream<Path> dirs = Files.list(strategiesDir)) {
            dirs.filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .sorted()
                .forEach(strategies::add);
        } catch (IOException e) {
            log.error("Failed to list strategies: {}", e.getMessage());
        }

        return strategies;
    }

    /**
     * List all versions of a strategy.
     */
    public List<Integer> listVersions(String strategyId) {
        List<Integer> versions = new ArrayList<>();

        Path strategyDir = strategiesDir.resolve(strategyId);
        if (!Files.isDirectory(strategyDir)) {
            return versions;
        }

        try (Stream<Path> files = Files.list(strategyDir)) {
            files.filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .map(VERSION_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(m -> Integer.parseInt(m.group(1)))
                .sorted()
                .forEach(versions::add);
        } catch (IOException e) {
            log.error("Failed to list versions for {}: {}", strategyId, e.getMessage());
        }

        return versions;
    }

    /**
     * Get the latest version number for a strategy.
     */
    public Optional<Integer> getLatestVersion(String strategyId) {
        List<Integer> versions = listVersions(strategyId);
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.get(versions.size() - 1));
    }

    /**
     * Get the path to a specific version of a strategy.
     */
    public Path getVersionPath(String strategyId, int version) {
        return strategiesDir.resolve(strategyId).resolve("v" + version + ".yaml");
    }

    /**
     * Load a specific version of a strategy.
     */
    public Optional<PublishedStrategy> loadStrategy(String strategyId, int version) {
        Path versionPath = getVersionPath(strategyId, version);

        if (!Files.exists(versionPath)) {
            log.warn("Strategy version not found: {} v{}", strategyId, version);
            return Optional.empty();
        }

        try {
            PublishedStrategy strategy = PublishedStrategy.fromYaml(versionPath);
            return Optional.of(strategy);
        } catch (IOException e) {
            log.error("Failed to load strategy {} v{}: {}", strategyId, version, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Load the latest version of a strategy.
     */
    public Optional<PublishedStrategy> loadLatest(String strategyId) {
        return getLatestVersion(strategyId)
            .flatMap(version -> loadStrategy(strategyId, version));
    }

    /**
     * Import a strategy version to Desk's active folder.
     * Returns the imported strategy if successful.
     */
    public Optional<PublishedStrategy> importToActive(String strategyId, int version) {
        Optional<PublishedStrategy> strategyOpt = loadStrategy(strategyId, version);

        if (strategyOpt.isEmpty()) {
            return Optional.empty();
        }

        PublishedStrategy strategy = strategyOpt.get();
        Path activeFile = DeskConfig.ACTIVE_DIR.resolve(strategyId + ".yaml");

        try {
            Files.createDirectories(DeskConfig.ACTIVE_DIR);
            strategy.toYaml(activeFile);
            log.info("Imported {} v{} to active folder", strategyId, version);
            return Optional.of(strategy);
        } catch (IOException e) {
            log.error("Failed to import strategy: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Remove a strategy from the active folder.
     */
    public boolean removeFromActive(String strategyId) {
        Path activeFile = DeskConfig.ACTIVE_DIR.resolve(strategyId + ".yaml");

        try {
            return Files.deleteIfExists(activeFile);
        } catch (IOException e) {
            log.error("Failed to remove strategy from active: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get a summary of all strategies in the library.
     */
    public List<StrategySummary> getLibrarySummary() {
        List<StrategySummary> summaries = new ArrayList<>();

        for (String strategyId : listStrategies()) {
            List<Integer> versions = listVersions(strategyId);
            if (!versions.isEmpty()) {
                int latestVersion = versions.get(versions.size() - 1);

                // Try to load latest to get name
                String name = strategyId;
                String symbol = "?";
                String timeframe = "?";

                Optional<PublishedStrategy> latest = loadStrategy(strategyId, latestVersion);
                if (latest.isPresent()) {
                    name = latest.get().getName();
                    symbol = latest.get().getSymbol();
                    timeframe = latest.get().getTimeframe();
                }

                summaries.add(new StrategySummary(strategyId, name, versions.size(), latestVersion, symbol, timeframe));
            }
        }

        return summaries;
    }

    public Path getLibraryDir() {
        return libraryDir;
    }

    public Path getStrategiesDir() {
        return strategiesDir;
    }

    /**
     * Summary info for a strategy in the library.
     */
    public record StrategySummary(
        String id,
        String name,
        int versionCount,
        int latestVersion,
        String symbol,
        String timeframe
    ) {}
}
