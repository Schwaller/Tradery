package com.tradery.desk.strategy;

import com.tradery.desk.DeskConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Store for published strategies in Desk.
 * Loads strategies from ~/.tradery/desk/strategies/{id}/active.yaml
 */
public class DeskStrategyStore {

    private static final Logger log = LoggerFactory.getLogger(DeskStrategyStore.class);
    private static final String ACTIVE_FILE = "active.yaml";

    private final Path strategiesDir;
    private final Map<String, PublishedStrategy> strategies = new ConcurrentHashMap<>();

    public DeskStrategyStore() {
        this(DeskConfig.STRATEGIES_DIR);
    }

    public DeskStrategyStore(Path strategiesDir) {
        this.strategiesDir = strategiesDir;
    }

    /**
     * Load all published strategies from disk.
     */
    public void loadAll() {
        strategies.clear();

        if (!Files.exists(strategiesDir)) {
            log.info("No strategies directory found at {}", strategiesDir);
            return;
        }

        try (Stream<Path> dirs = Files.list(strategiesDir)) {
            dirs.filter(Files::isDirectory)
                .forEach(this::loadStrategy);
        } catch (IOException e) {
            log.error("Failed to list strategies: {}", e.getMessage());
        }

        log.info("Loaded {} published strategies", strategies.size());
    }

    /**
     * Load a single strategy from its directory.
     */
    private void loadStrategy(Path strategyDir) {
        Path activeFile = strategyDir.resolve(ACTIVE_FILE);
        if (!Files.exists(activeFile)) {
            return;
        }

        try {
            PublishedStrategy strategy = PublishedStrategy.fromYaml(activeFile);
            strategies.put(strategy.getId(), strategy);
            log.debug("Loaded strategy: {} v{}", strategy.getName(), strategy.getVersion());
        } catch (IOException e) {
            log.error("Failed to load strategy from {}: {}", activeFile, e.getMessage());
        }
    }

    /**
     * Reload a specific strategy by ID.
     */
    public void reloadStrategy(String strategyId) {
        Path strategyDir = strategiesDir.resolve(strategyId);
        if (Files.exists(strategyDir)) {
            loadStrategy(strategyDir);
        } else {
            strategies.remove(strategyId);
        }
    }

    /**
     * Get all loaded strategies.
     */
    public List<PublishedStrategy> getAll() {
        return new ArrayList<>(strategies.values());
    }

    /**
     * Get a strategy by ID.
     */
    public PublishedStrategy get(String id) {
        return strategies.get(id);
    }

    /**
     * Check if a strategy exists.
     */
    public boolean exists(String id) {
        return strategies.containsKey(id);
    }

    /**
     * Get count of loaded strategies.
     */
    public int size() {
        return strategies.size();
    }

    /**
     * Get the strategies directory path.
     */
    public Path getStrategiesDir() {
        return strategiesDir;
    }
}
