package com.tradery.io;

import io.methvin.watcher.DirectoryWatcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Watches files or directories for changes.
 * Supports two modes:
 * - Directory watching: Notifies on any file changes in directory
 * - Single-file watching: Notifies only for a specific file
 *
 * When a file is modified (by Claude Code or externally),
 * notifies listeners with a debounced callback.
 */
public class FileWatcher {

    private static final long DEBOUNCE_MS = 500;

    private final Path watchPath;
    private final Path targetFile;  // null for directory mode, specific file for single-file mode
    private final Consumer<Path> onFileChanged;
    private final Consumer<Path> onFileDeleted;
    private final Consumer<Path> onFileCreated;
    private DirectoryWatcher watcher;
    private Thread watchThread;

    private final ScheduledExecutorService debouncer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingNotification;
    private Path lastChangedPath;
    private String lastEventType;

    /**
     * Create a directory watcher (original behavior)
     * @param watchPath Directory to watch
     * @param onFileChanged Called when files are created or modified
     */
    public FileWatcher(Path watchPath, Consumer<Path> onFileChanged) {
        this(watchPath, null, onFileChanged, null, null);
    }

    /**
     * Create a directory watcher with deletion support
     * @param watchPath Directory to watch
     * @param onFileChanged Called when files are modified
     * @param onFileDeleted Called when files are deleted
     * @param onFileCreated Called when files are created
     */
    public FileWatcher(Path watchPath, Consumer<Path> onFileChanged, Consumer<Path> onFileDeleted, Consumer<Path> onFileCreated) {
        this(watchPath, null, onFileChanged, onFileDeleted, onFileCreated);
    }

    /**
     * Create a single-file watcher
     * @param watchPath Directory containing the file
     * @param targetFile Specific file to watch (only this file triggers callbacks)
     * @param onFileChanged Called when the file is modified
     * @param onFileDeleted Called when the file is deleted
     */
    public FileWatcher(Path watchPath, Path targetFile, Consumer<Path> onFileChanged, Consumer<Path> onFileDeleted) {
        this(watchPath, targetFile, onFileChanged, onFileDeleted, null);
    }

    /**
     * Full constructor
     */
    private FileWatcher(Path watchPath, Path targetFile, Consumer<Path> onFileChanged,
                        Consumer<Path> onFileDeleted, Consumer<Path> onFileCreated) {
        this.watchPath = watchPath;
        this.targetFile = targetFile;
        this.onFileChanged = onFileChanged;
        this.onFileDeleted = onFileDeleted;
        this.onFileCreated = onFileCreated;
    }

    /**
     * Factory method for single-file watching
     */
    public static FileWatcher forFile(Path file, Consumer<Path> onModified, Consumer<Path> onDeleted) {
        return new FileWatcher(file.getParent(), file, onModified, onDeleted);
    }

    /**
     * Factory method for directory watching with all events
     */
    public static FileWatcher forDirectory(Path directory, Consumer<Path> onModified,
                                           Consumer<Path> onDeleted, Consumer<Path> onCreated) {
        return new FileWatcher(directory, onModified, onDeleted, onCreated);
    }

    /**
     * Start watching for file changes
     */
    public void start() throws IOException {
        if (watcher != null) {
            return; // Already watching
        }

        String mode = targetFile != null ? "single-file" : "directory";
        System.out.println("FileWatcher (" + mode + "): watching " +
                          (targetFile != null ? targetFile : watchPath));

        watcher = DirectoryWatcher.builder()
            .path(watchPath)
            .listener(event -> {
                Path path = event.path();

                // Only watch config files (YAML preferred, JSON for backward compat)
                String pathStr = path.toString();
                if (!pathStr.endsWith(".yaml") && !pathStr.endsWith(".json")) {
                    return;
                }

                // In single-file mode, only process events for the target file
                if (targetFile != null && !path.equals(targetFile)) {
                    return;
                }

                switch (event.eventType()) {
                    case CREATE -> {
                        if (targetFile == null && onFileCreated != null) {
                            // Directory mode: notify about new files
                            debounceNotification(path, "CREATE");
                        } else if (targetFile != null) {
                            // Single-file mode: treat recreate as modify
                            debounceNotification(path, "MODIFY");
                        }
                    }
                    case MODIFY -> debounceNotification(path, "MODIFY");
                    case DELETE -> {
                        if (onFileDeleted != null) {
                            // Don't debounce deletes - notify immediately
                            System.out.println("File deleted: " + path);
                            onFileDeleted.accept(path);
                        }
                    }
                }
            })
            .build();

        watchThread = new Thread(() -> {
            try {
                watcher.watch();
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("File watcher error: " + e.getMessage());
                }
            }
        }, "FileWatcher-" + (targetFile != null ? targetFile.getFileName() : watchPath.getFileName()));
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * Debounce notifications to avoid multiple rapid updates
     */
    private void debounceNotification(Path path, String eventType) {
        synchronized (this) {
            lastChangedPath = path;
            lastEventType = eventType;

            if (pendingNotification != null) {
                pendingNotification.cancel(false);
            }

            pendingNotification = debouncer.schedule(() -> {
                Path pathToNotify;
                String typeToNotify;
                synchronized (this) {
                    pathToNotify = lastChangedPath;
                    typeToNotify = lastEventType;
                    pendingNotification = null;
                }

                if (pathToNotify != null) {
                    System.out.println("File " + typeToNotify.toLowerCase() + ": " + pathToNotify);
                    if ("CREATE".equals(typeToNotify) && onFileCreated != null) {
                        onFileCreated.accept(pathToNotify);
                    } else if (onFileChanged != null) {
                        onFileChanged.accept(pathToNotify);
                    }
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

    /**
     * Get the path being watched
     */
    public Path getWatchPath() {
        return watchPath;
    }

    /**
     * Get the target file (null for directory mode)
     */
    public Path getTargetFile() {
        return targetFile;
    }
}
