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
 * Store for active strategies in Desk.
 * Loads strategies from ~/.tradery/desk/active/
 */
public class DeskStrategyStore {

    private static final Logger log = LoggerFactory.getLogger(DeskStrategyStore.class);

    private final Path activeDir;
    private final Map<String, PublishedStrategy> strategies = new ConcurrentHashMap<>();

    public DeskStrategyStore() {
        this(DeskConfig.ACTIVE_DIR);
    }

    public DeskStrategyStore(Path activeDir) {
        this.activeDir = activeDir;
    }

    /**
     * Load all active strategies from disk.
     */
    public void loadAll() {
        strategies.clear();

        if (!Files.exists(activeDir)) {
            log.info("No active strategies directory found at {}", activeDir);
            return;
        }

        try (Stream<Path> files = Files.list(activeDir)) {
            files.filter(p -> p.toString().endsWith(".yaml"))
                .forEach(this::loadStrategy);
        } catch (IOException e) {
            log.error("Failed to list active strategies: {}", e.getMessage());
        }

        log.info("Loaded {} active strategies", strategies.size());
    }

    /**
     * Load a single strategy from a file.
     */
    private void loadStrategy(Path yamlFile) {
        try {
            PublishedStrategy strategy = PublishedStrategy.fromYaml(yamlFile);
            strategies.put(strategy.getId(), strategy);
            log.debug("Loaded strategy: {} v{}", strategy.getName(), strategy.getVersion());
        } catch (IOException e) {
            log.error("Failed to load strategy from {}: {}", yamlFile, e.getMessage());
        }
    }

    /**
     * Reload a specific strategy by ID.
     */
    public void reloadStrategy(String strategyId) {
        Path yamlFile = activeDir.resolve(strategyId + ".yaml");
        if (Files.exists(yamlFile)) {
            loadStrategy(yamlFile);
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
     * Get the active strategies directory path.
     */
    public Path getActiveDir() {
        return activeDir;
    }
}
