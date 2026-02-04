package com.tradery.ui.dashboard;

import java.util.List;

/**
 * Common page info for the dashboard. Both forge and desk map their
 * page manager types to this common record.
 */
public record DashboardPageInfo(
    String key,
    String displayName,
    String category,
    State state,
    int listenerCount,
    int recordCount,
    int loadProgress,
    boolean liveEnabled,
    List<String> consumers
) {
    public enum State {
        EMPTY, LOADING, READY, UPDATING, ERROR
    }

    /** Convenience constructor without consumers. */
    public DashboardPageInfo(String key, String displayName, String category,
                             State state, int listenerCount, int recordCount,
                             int loadProgress, boolean liveEnabled) {
        this(key, displayName, category, state, listenerCount, recordCount,
             loadProgress, liveEnabled, List.of());
    }
}
