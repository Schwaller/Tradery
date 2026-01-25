package com.tradery.desk.strategy;

import com.tradery.desk.DeskConfig;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Watches the active strategies directory for changes and triggers reloads.
 * Monitors for new/modified/deleted .yaml files in ~/.tradery/desk/active/
 */
public class StrategyWatcher {

    private static final Logger log = LoggerFactory.getLogger(StrategyWatcher.class);

    private final Path activeDir;
    private final Consumer<String> onStrategyChanged;
    private DirectoryWatcher watcher;
    private volatile boolean running = false;

    public StrategyWatcher(Consumer<String> onStrategyChanged) {
        this(DeskConfig.ACTIVE_DIR, onStrategyChanged);
    }

    public StrategyWatcher(Path activeDir, Consumer<String> onStrategyChanged) {
        this.activeDir = activeDir;
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
            Files.createDirectories(activeDir);

            watcher = DirectoryWatcher.builder()
                .path(activeDir)
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
            log.info("Started watching {}", activeDir);
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

        // Only care about .yaml files
        if (!path.toString().endsWith(".yaml")) {
            return;
        }

        // Extract strategy ID from filename (remove .yaml extension)
        String filename = path.getFileName().toString();
        String strategyId = filename.substring(0, filename.length() - 5);

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
