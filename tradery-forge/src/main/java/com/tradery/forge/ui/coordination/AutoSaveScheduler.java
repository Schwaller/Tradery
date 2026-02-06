package com.tradery.forge.ui.coordination;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages auto-save and auto-backtest timers with debouncing.
 * Extracted from ProjectWindow to reduce complexity.
 */
public class AutoSaveScheduler {

    private static final int AUTO_SAVE_DELAY_MS = 500;
    private static final int AUTO_BACKTEST_DELAY_MS = 800;

    private Timer autoSaveTimer;
    private Timer autoBacktestTimer;

    // Flag to suppress auto-save during load operations (e.g., reloading strategy from disk)
    private volatile boolean ignoringFileChanges = false;

    // Counter for pending self-writes: incremented on save, decremented when the
    // corresponding file-watcher event arrives. Replaces the old timer-based approach
    // which could race with the FileWatcher debounce delay.
    private final AtomicInteger pendingSaveCount = new AtomicInteger(0);

    // Callbacks
    private Runnable onSave;
    private Runnable onBacktest;

    public AutoSaveScheduler() {
        autoSaveTimer = new Timer(AUTO_SAVE_DELAY_MS, e -> performSave());
        autoSaveTimer.setRepeats(false);

        autoBacktestTimer = new Timer(AUTO_BACKTEST_DELAY_MS, e -> performBacktest());
        autoBacktestTimer.setRepeats(false);
    }

    /**
     * Set the callback to run when auto-save triggers.
     */
    public void setOnSave(Runnable callback) {
        this.onSave = callback;
    }

    /**
     * Set the callback to run when auto-backtest triggers.
     */
    public void setOnBacktest(Runnable callback) {
        this.onBacktest = callback;
    }

    /**
     * Schedule an auto-save and auto-backtest with debouncing.
     * Call this when user makes changes in the UI.
     */
    public void scheduleUpdate() {
        if (ignoringFileChanges) return;
        autoSaveTimer.restart();
        autoBacktestTimer.restart();
    }

    /**
     * Check if file changes should be ignored (during save operations).
     */
    public boolean isIgnoringFileChanges() {
        return ignoringFileChanges;
    }

    /**
     * Set the flag to ignore file changes.
     * Used when loading data to prevent triggering saves.
     */
    public void setIgnoringFileChanges(boolean ignoring) {
        this.ignoringFileChanges = ignoring;
    }

    /**
     * Begin ignoring file changes, run the action, then stop ignoring.
     * Useful for wrapping load operations.
     */
    public void withIgnoredFileChanges(Runnable action) {
        ignoringFileChanges = true;
        try {
            action.run();
        } finally {
            ignoringFileChanges = false;
        }
    }

    /**
     * Mark that a save just occurred. The next file-watcher event will be
     * suppressed via {@link #tryConsumeSaveEvent()}.
     */
    public void markSaveOccurred() {
        pendingSaveCount.incrementAndGet();
    }

    /**
     * Called from file-watcher callbacks. If a save is pending, consume it
     * (decrement counter) and return true — the caller should skip the reload.
     * If no save is pending, return false — it's a genuine external change.
     */
    public boolean tryConsumeSaveEvent() {
        return pendingSaveCount.getAndUpdate(c -> c > 0 ? c - 1 : 0) > 0;
    }

    private void performSave() {
        if (onSave != null) {
            onSave.run();
        }
    }

    private void performBacktest() {
        if (onBacktest != null) {
            onBacktest.run();
        }
    }

    /**
     * Stop all timers (for cleanup on window close).
     */
    public void stop() {
        if (autoSaveTimer != null) {
            autoSaveTimer.stop();
        }
        if (autoBacktestTimer != null) {
            autoBacktestTimer.stop();
        }
    }
}
