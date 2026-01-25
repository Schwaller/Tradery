package com.tradery.desk.strategy;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Watches the strategies directory for changes and triggers reloads.
 * Monitors for new/modified active.yaml files.
 */
public class StrategyWatcher {

    private static final Logger log = LoggerFactory.getLogger(StrategyWatcher.class);
    private static final String ACTIVE_FILE = "active.yaml";

    private final Path strategiesDir;
    private final Consumer<String> onStrategyChanged;
    private DirectoryWatcher watcher;
    private volatile boolean running = false;

    public StrategyWatcher(Path strategiesDir, Consumer<String> onStrategyChanged) {
        this.strategiesDir = strategiesDir;
        this.onStrategyChanged = onStrategyChanged;
    }

    /**
     * Start watching for changes.
     */
    public void start() {
        if (running) {
            return;
        }

        try {
            Files.createDirectories(strategiesDir);

            watcher = DirectoryWatcher.builder()
                .path(strategiesDir)
                .listener(this::onEvent)
                .build();

            Thread watchThread = new Thread(() -> {
                try {
                    watcher.watch();
                } catch (Exception e) {
                    if (running) {
                        log.error("Watcher error: {}", e.getMessage());
                    }
                }
            }, "StrategyWatcher");
            watchThread.setDaemon(true);
            watchThread.start();

            running = true;
            log.info("Started watching {}", strategiesDir);
        } catch (IOException e) {
            log.error("Failed to start watcher: {}", e.getMessage());
        }
    }

    /**
     * Stop watching.
     */
    public void stop() {
        running = false;
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException e) {
                log.debug("Error closing watcher: {}", e.getMessage());
            }
        }
    }

    /**
     * Handle file system events.
     */
    private void onEvent(DirectoryChangeEvent event) {
        Path path = event.path();

        // Only care about active.yaml files
        if (!path.getFileName().toString().equals(ACTIVE_FILE)) {
            return;
        }

        // Extract strategy ID from parent directory
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        String strategyId = parent.getFileName().toString();

        switch (event.eventType()) {
            case CREATE, MODIFY -> {
                log.info("Strategy {} changed, reloading", strategyId);
                onStrategyChanged.accept(strategyId);
            }
            case DELETE -> {
                log.info("Strategy {} deleted", strategyId);
                onStrategyChanged.accept(strategyId);
            }
            default -> {
                // Ignore OVERFLOW events
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}
