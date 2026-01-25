package com.tradery.dataservice.page;

import java.util.List;

/**
 * Status information for a data page.
 */
public record PageStatus(
    PageState state,
    int progress,           // 0-100
    long recordCount,
    Long lastSyncTime,
    List<Consumer> consumers,
    Coverage coverage,
    boolean isNew           // true if page was just created
) {
    /**
     * A consumer registered for this page.
     */
    public record Consumer(String id, String name) {}

    /**
     * Coverage information for the page.
     */
    public record Coverage(
        long requestedStart,
        long requestedEnd,
        Long actualStart,
        Long actualEnd,
        List<Gap> gaps
    ) {}

    /**
     * A gap in the data coverage.
     */
    public record Gap(long start, long end) {}

    /**
     * Create a new pending status.
     */
    public static PageStatus pending(boolean isNew) {
        return new PageStatus(PageState.PENDING, 0, 0, null, List.of(), null, isNew);
    }

    /**
     * Create a loading status with progress.
     */
    public static PageStatus loading(int progress) {
        return new PageStatus(PageState.LOADING, progress, 0, null, List.of(), null, false);
    }

    /**
     * Create a ready status.
     */
    public static PageStatus ready(long recordCount, Long lastSyncTime, Coverage coverage) {
        return new PageStatus(PageState.READY, 100, recordCount, lastSyncTime, List.of(), coverage, false);
    }

    /**
     * Create an error status.
     */
    public static PageStatus error() {
        return new PageStatus(PageState.ERROR, 0, 0, null, List.of(), null, false);
    }

    /**
     * Create an evicted status.
     */
    public static PageStatus evicted() {
        return new PageStatus(PageState.EVICTED, 0, 0, null, List.of(), null, false);
    }

    /**
     * Return a copy with updated consumers list.
     */
    public PageStatus withConsumers(List<Consumer> consumers) {
        return new PageStatus(state, progress, recordCount, lastSyncTime, consumers, coverage, isNew);
    }

    /**
     * Return a copy with updated state and progress.
     */
    public PageStatus withState(PageState state, int progress) {
        return new PageStatus(state, progress, recordCount, lastSyncTime, consumers, coverage, false);
    }
}
