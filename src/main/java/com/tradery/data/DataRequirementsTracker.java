package com.tradery.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Central state holder for all data requirements during a backtest session.
 *
 * Tracks:
 * - What data is required (OHLC, AggTrades, Funding, OI)
 * - Time windows for each requirement
 * - Loading status and progress
 * - Tier classification (TRADING vs VIEW)
 *
 * Provides callbacks:
 * - onStatusChange: UI updates for progress display
 * - onTradingReady: Trigger to start backtest
 * - onViewReady: Trigger to refresh specific chart
 */
public class DataRequirementsTracker {

    /**
     * Status of a data requirement.
     */
    public enum Status {
        /** Not yet started */
        PENDING,
        /** Checking cache for existing data */
        CHECKING,
        /** Fetching from API */
        FETCHING,
        /** Data is available and ready */
        READY,
        /** Failed to load (network error, no data available, etc.) */
        ERROR
    }

    /**
     * State of a single requirement including progress.
     */
    public record RequirementState(
        DataRequirement requirement,
        Status status,
        int loaded,
        int expected,
        String message
    ) {
        /**
         * Get progress as percentage (0-100).
         */
        public int progressPercent() {
            if (expected <= 0) return 0;
            return Math.min(100, (loaded * 100) / expected);
        }

        /**
         * Get a display-friendly status string.
         */
        public String statusText() {
            return switch (status) {
                case PENDING -> requirement.dataType() + ": pending";
                case CHECKING -> requirement.dataType() + ": checking...";
                case FETCHING -> {
                    if (expected > 0) {
                        yield String.format("%s: %d/%d (%d%%)",
                            requirement.dataType(), loaded, expected, progressPercent());
                    } else {
                        yield requirement.dataType() + ": fetching...";
                    }
                }
                case READY -> requirement.dataType() + ": ready";
                case ERROR -> requirement.dataType() + ": " + (message != null ? message : "error");
            };
        }
    }

    // State storage
    private final Map<String, RequirementState> requirements = new ConcurrentHashMap<>();

    // Callbacks
    private Consumer<RequirementState> onStatusChange;
    private Runnable onTradingReady;
    private Consumer<String> onViewReady; // dataType

    /**
     * Set callback for status changes (for UI updates).
     */
    public void setOnStatusChange(Consumer<RequirementState> callback) {
        this.onStatusChange = callback;
    }

    /**
     * Set callback when all TRADING requirements are ready.
     */
    public void setOnTradingReady(Runnable callback) {
        this.onTradingReady = callback;
    }

    /**
     * Set callback when a VIEW requirement becomes ready (for chart refresh).
     */
    public void setOnViewReady(Consumer<String> callback) {
        this.onViewReady = callback;
    }

    /**
     * Clear all requirements (call before starting a new backtest).
     */
    public void clear() {
        requirements.clear();
    }

    /**
     * Add a new requirement.
     */
    public void addRequirement(DataRequirement req) {
        RequirementState state = new RequirementState(req, Status.PENDING, 0, 0, null);
        requirements.put(req.dataType(), state);
        notifyStatusChange(state);
    }

    /**
     * Update status of a requirement.
     */
    public void updateStatus(String dataType, Status status) {
        updateStatus(dataType, status, 0, 0, null);
    }

    /**
     * Update status with progress information.
     */
    public void updateStatus(String dataType, Status status, int loaded, int expected) {
        updateStatus(dataType, status, loaded, expected, null);
    }

    /**
     * Update status with progress and message.
     */
    public void updateStatus(String dataType, Status status, int loaded, int expected, String message) {
        RequirementState current = requirements.get(dataType);
        if (current == null) return;

        RequirementState newState = new RequirementState(
            current.requirement(), status, loaded, expected, message
        );
        requirements.put(dataType, newState);
        notifyStatusChange(newState);

        // Check if this completion triggers callbacks
        if (status == Status.READY) {
            if (current.requirement().tier() == DataRequirement.Tier.TRADING) {
                checkTradingReady();
            } else {
                notifyViewReady(dataType);
            }
        }
    }

    /**
     * Get current state of a requirement.
     */
    public RequirementState getState(String dataType) {
        return requirements.get(dataType);
    }

    /**
     * Get all requirement states.
     */
    public Collection<RequirementState> getAllStates() {
        return Collections.unmodifiableCollection(requirements.values());
    }

    /**
     * Get all TRADING tier requirements.
     */
    public Set<DataRequirement> getTradingRequirements() {
        Set<DataRequirement> result = new HashSet<>();
        for (RequirementState state : requirements.values()) {
            if (state.requirement().tier() == DataRequirement.Tier.TRADING) {
                result.add(state.requirement());
            }
        }
        return result;
    }

    /**
     * Get all VIEW tier requirements.
     */
    public Set<DataRequirement> getViewRequirements() {
        Set<DataRequirement> result = new HashSet<>();
        for (RequirementState state : requirements.values()) {
            if (state.requirement().tier() == DataRequirement.Tier.VIEW) {
                result.add(state.requirement());
            }
        }
        return result;
    }

    /**
     * Check if all TRADING requirements are ready.
     */
    public boolean isTradingReady() {
        for (RequirementState state : requirements.values()) {
            if (state.requirement().tier() == DataRequirement.Tier.TRADING) {
                if (state.status() != Status.READY) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if all VIEW requirements are ready.
     */
    public boolean isViewReady() {
        for (RequirementState state : requirements.values()) {
            if (state.requirement().tier() == DataRequirement.Tier.VIEW) {
                if (state.status() != Status.READY) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if a specific data type has any pending or loading work.
     */
    public boolean isLoading(String dataType) {
        RequirementState state = requirements.get(dataType);
        if (state == null) return false;
        return state.status() == Status.CHECKING || state.status() == Status.FETCHING;
    }

    /**
     * Check if a specific data type is ready.
     */
    public boolean isReady(String dataType) {
        RequirementState state = requirements.get(dataType);
        if (state == null) return false;
        return state.status() == Status.READY;
    }

    /**
     * Check if a specific data type has an error.
     */
    public boolean hasError(String dataType) {
        RequirementState state = requirements.get(dataType);
        if (state == null) return false;
        return state.status() == Status.ERROR;
    }

    /**
     * Get count of requirements by tier.
     */
    public int countByTier(DataRequirement.Tier tier) {
        int count = 0;
        for (RequirementState state : requirements.values()) {
            if (state.requirement().tier() == tier) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get count of ready requirements by tier.
     */
    public int countReadyByTier(DataRequirement.Tier tier) {
        int count = 0;
        for (RequirementState state : requirements.values()) {
            if (state.requirement().tier() == tier && state.status() == Status.READY) {
                count++;
            }
        }
        return count;
    }

    // Internal: notify status change callback
    private void notifyStatusChange(RequirementState state) {
        if (onStatusChange != null) {
            onStatusChange.accept(state);
        }
    }

    // Internal: check if all trading requirements are ready and fire callback
    private void checkTradingReady() {
        if (isTradingReady() && onTradingReady != null) {
            onTradingReady.run();
        }
    }

    // Internal: notify view ready callback
    private void notifyViewReady(String dataType) {
        if (onViewReady != null) {
            onViewReady.accept(dataType);
        }
    }
}
