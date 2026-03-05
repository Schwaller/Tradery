package com.tradery.dataservice.data;

/**
 * Callback for notifying when background backfill work completes.
 * Allows page managers to re-trigger loading of affected pages.
 */
public interface BackfillCompletionCallback {

    /**
     * Called after background profile backfill completes for a range.
     */
    void onProfileBackfillComplete(String symbol, String marketType, long start, long end);

    /**
     * Called after background spectrum backfill completes for a range.
     */
    void onSpectrumBackfillComplete(String symbol, long start, long end);
}
