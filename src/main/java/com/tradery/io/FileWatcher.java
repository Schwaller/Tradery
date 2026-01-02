package com.tradery.io;

import io.methvin.watcher.DirectoryWatcher;
import io.methvin.watcher.hashing.FileHash;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Watches ~/.tradery/strategies/ for file changes.
 * When a strategy file is modified (by Claude Code or externally),
 * notifies listeners with a debounced callback.
 */
public class FileWatcher {

    private static final long DEBOUNCE_MS = 500;

    private final Path watchPath;
    private final Consumer<Path> onFileChanged;
    private DirectoryWatcher watcher;
    private Thread watchThread;

    private final ScheduledExecutorService debouncer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingNotification;
    private Path lastChangedPath;

    public FileWatcher(Path watchPath, Consumer<Path> onFileChanged) {
        this.watchPath = watchPath;
        this.onFileChanged = onFileChanged;
    }

    /**
     * Start watching for file changes
     */
    public void start() throws IOException {
        if (watcher != null) {
            return; // Already watching
        }

        System.out.println("Watching for changes in: " + watchPath);

        watcher = DirectoryWatcher.builder()
            .path(watchPath)
            .listener(event -> {
                Path path = event.path();

                // Only watch JSON files
                if (!path.toString().endsWith(".json")) {
                    return;
                }

                switch (event.eventType()) {
                    case CREATE, MODIFY -> debounceNotification(path);
                    case DELETE -> System.out.println("File deleted: " + path);
                    default -> {}
                }
            })
            .build();

        watchThread = new Thread(() -> {
            try {
                watcher.watch();
            } catch (Exception e) {
                System.err.println("File watcher error: " + e.getMessage());
            }
        }, "FileWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * Debounce notifications to avoid multiple rapid updates
     */
    private void debounceNotification(Path path) {
        synchronized (this) {
            lastChangedPath = path;

            if (pendingNotification != null) {
                pendingNotification.cancel(false);
            }

            pendingNotification = debouncer.schedule(() -> {
                Path pathToNotify;
                synchronized (this) {
                    pathToNotify = lastChangedPath;
                    pendingNotification = null;
                }

                if (pathToNotify != null) {
                    System.out.println("File changed: " + pathToNotify);
                    onFileChanged.accept(pathToNotify);
                }
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop watching
     */
    public void stop() {
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException e) {
                System.err.println("Error closing file watcher: " + e.getMessage());
            }
            watcher = null;
        }

        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }

        debouncer.shutdownNow();
    }

    /**
     * Check if watching
     */
    public boolean isWatching() {
        return watcher != null && watchThread != null && watchThread.isAlive();
    }
}
