package com.tradery.ui;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Manages status bar updates with priority-based handling to prevent flickering
 * when multiple concurrent operations are running.
 *
 * Each status source has a priority level. Higher priority sources override lower ones.
 * When a higher priority source clears its status, the next highest active status is shown.
 */
public class StatusManager {

    /**
     * Priority levels for status sources.
     * Higher values = higher priority (shown over lower priority messages)
     */
    public enum Priority {
        IDLE(0),           // "Ready" - default state
        HOVER(10),         // Crosshair/chart hover info
        INFO(20),          // Informational messages (auto-save, file change)
        LOADING(30),       // Background data loading (VIEW tier)
        OPERATION(40),     // Active operations (backtest, indicator calc)
        ERROR(50);         // Error messages

        private final int level;

        Priority(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }
    }

    /**
     * Status source identifiers
     */
    public static final String SOURCE_IDLE = "idle";
    public static final String SOURCE_HOVER = "hover";
    public static final String SOURCE_AUTOSAVE = "autosave";
    public static final String SOURCE_FILE_CHANGE = "file_change";
    public static final String SOURCE_CANDLE_FETCH = "candle_fetch";
    public static final String SOURCE_OI_LOADING = "oi_loading";
    public static final String SOURCE_FUNDING_LOADING = "funding_loading";
    public static final String SOURCE_AGGTRADES_LOADING = "aggtrades_loading";
    public static final String SOURCE_BACKTEST = "backtest";
    public static final String SOURCE_INDICATORS = "indicators";
    public static final String SOURCE_PHASES = "phases";
    public static final String SOURCE_ERROR = "error";

    // Active statuses by source
    private final Map<String, StatusEntry> activeStatuses = new ConcurrentHashMap<>();

    // Callback to update the UI
    private BiConsumer<String, Integer> statusCallback;  // (message, progressPercent or -1)
    private Runnable hideProgressCallback;

    /**
     * Entry tracking a status message and its priority
     */
    private record StatusEntry(String message, Priority priority, int progressPercent, long timestamp) {}

    public StatusManager() {
        // Start with idle status
        activeStatuses.put(SOURCE_IDLE, new StatusEntry("Ready", Priority.IDLE, -1, System.currentTimeMillis()));
    }

    /**
     * Set callbacks for status updates
     */
    public void setCallbacks(BiConsumer<String, Integer> statusCallback, Runnable hideProgressCallback) {
        this.statusCallback = statusCallback;
        this.hideProgressCallback = hideProgressCallback;
    }

    /**
     * Set a status message from a source with given priority.
     * Will only display if this is the highest priority active status.
     *
     * @param source Unique identifier for the status source
     * @param message Status message to display
     * @param priority Priority level for this source
     */
    public void setStatus(String source, String message, Priority priority) {
        setStatus(source, message, priority, -1);
    }

    /**
     * Set a status message with progress from a source.
     *
     * @param source Unique identifier for the status source
     * @param message Status message to display
     * @param priority Priority level for this source
     * @param progressPercent Progress percentage (0-100) or -1 for no progress bar
     */
    public void setStatus(String source, String message, Priority priority, int progressPercent) {
        activeStatuses.put(source, new StatusEntry(message, priority, progressPercent, System.currentTimeMillis()));
        updateDisplay();
    }

    /**
     * Clear a status source. If it was the highest priority, shows the next highest.
     */
    public void clearStatus(String source) {
        activeStatuses.remove(source);
        updateDisplay();
    }

    /**
     * Clear all statuses and return to idle.
     */
    public void clearAll() {
        activeStatuses.clear();
        activeStatuses.put(SOURCE_IDLE, new StatusEntry("Ready", Priority.IDLE, -1, System.currentTimeMillis()));
        updateDisplay();
    }

    /**
     * Set idle message (replaces default "Ready")
     */
    public void setIdleMessage(String message) {
        activeStatuses.put(SOURCE_IDLE, new StatusEntry(message, Priority.IDLE, -1, System.currentTimeMillis()));
        updateDisplay();
    }

    /**
     * Find and display the highest priority status
     */
    private void updateDisplay() {
        if (statusCallback == null) return;

        StatusEntry highest = null;
        for (StatusEntry entry : activeStatuses.values()) {
            if (highest == null || entry.priority.level() > highest.priority.level() ||
                (entry.priority.level() == highest.priority.level() && entry.timestamp > highest.timestamp)) {
                highest = entry;
            }
        }

        if (highest != null) {
            final StatusEntry displayEntry = highest;
            SwingUtilities.invokeLater(() -> {
                statusCallback.accept(displayEntry.message, displayEntry.progressPercent);
                if (displayEntry.progressPercent < 0 && hideProgressCallback != null) {
                    hideProgressCallback.run();
                }
            });
        }
    }

    /**
     * Check if a source is currently active
     */
    public boolean isActive(String source) {
        return activeStatuses.containsKey(source);
    }

    /**
     * Get current active status count (excluding idle)
     */
    public int getActiveCount() {
        return (int) activeStatuses.entrySet().stream()
            .filter(e -> !e.getKey().equals(SOURCE_IDLE))
            .count();
    }

    // Convenience methods for common operations

    /**
     * Start a backtest operation status
     */
    public void startBacktest(String message) {
        setStatus(SOURCE_BACKTEST, message, Priority.OPERATION);
    }

    /**
     * Update backtest progress
     */
    public void updateBacktest(String message, int progressPercent) {
        setStatus(SOURCE_BACKTEST, message, Priority.OPERATION, progressPercent);
    }

    /**
     * Complete backtest and show summary
     */
    public void completeBacktest(String summary) {
        clearStatus(SOURCE_BACKTEST);
        clearStatus(SOURCE_INDICATORS);
        clearStatus(SOURCE_PHASES);
        setIdleMessage(summary);
    }

    /**
     * Start indicator calculation status
     */
    public void startIndicators(String indicatorName) {
        setStatus(SOURCE_INDICATORS, "Calculating " + indicatorName + "...", Priority.OPERATION);
    }

    /**
     * Complete indicator calculation
     */
    public void completeIndicators() {
        clearStatus(SOURCE_INDICATORS);
    }

    /**
     * Start phase evaluation status
     */
    public void startPhases(String phaseName) {
        setStatus(SOURCE_PHASES, "Evaluating phase: " + phaseName + "...", Priority.OPERATION);
    }

    /**
     * Complete phase evaluation
     */
    public void completePhases() {
        clearStatus(SOURCE_PHASES);
    }

    /**
     * Set hover status (from chart crosshair)
     */
    public void setHoverStatus(String ohlcInfo) {
        setStatus(SOURCE_HOVER, ohlcInfo, Priority.HOVER);
    }

    /**
     * Clear hover status
     */
    public void clearHoverStatus() {
        clearStatus(SOURCE_HOVER);
    }

    /**
     * Set data loading status
     */
    public void setDataLoadingStatus(String dataType, String message, int progressPercent) {
        String source = switch (dataType) {
            case "OI" -> SOURCE_OI_LOADING;
            case "Funding" -> SOURCE_FUNDING_LOADING;
            case "AggTrades" -> SOURCE_AGGTRADES_LOADING;
            default -> SOURCE_CANDLE_FETCH;
        };
        if (progressPercent >= 100 || message.contains("Complete") || message.contains("Ready")) {
            clearStatus(source);
        } else {
            setStatus(source, message, Priority.LOADING, progressPercent);
        }
    }

    /**
     * Set info status (auto-save, file change, etc.)
     */
    public void setInfoStatus(String source, String message) {
        setStatus(source, message, Priority.INFO);
        // Auto-clear info messages after 3 seconds
        Timer timer = new Timer(3000, e -> clearStatus(source));
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Set error status
     */
    public void setErrorStatus(String message) {
        setStatus(SOURCE_ERROR, "Error: " + message, Priority.ERROR);
    }

    /**
     * Clear error status
     */
    public void clearErrorStatus() {
        clearStatus(SOURCE_ERROR);
    }
}
