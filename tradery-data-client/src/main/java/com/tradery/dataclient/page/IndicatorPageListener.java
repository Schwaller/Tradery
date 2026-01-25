package com.tradery.dataclient.page;

/**
 * Listener for indicator page state and data changes.
 * All callbacks are guaranteed to be called on the Swing EDT.
 *
 * @param <T> The type of computed indicator data
 */
public interface IndicatorPageListener<T> {

    /**
     * Called when the indicator page state changes.
     * Always called on the Swing EDT.
     */
    void onStateChanged(IndicatorPage<T> page, PageState oldState, PageState newState);

    /**
     * Called when the indicator data changes (recomputed).
     * Always called on the Swing EDT.
     */
    default void onDataChanged(IndicatorPage<T> page) {
        // Default: do nothing
    }

    /**
     * Called when progress is updated during computation.
     * Always called on the Swing EDT.
     *
     * @param page     The page being computed
     * @param progress Progress percentage (0-100)
     */
    default void onProgress(IndicatorPage<T> page, int progress) {
        // Default: do nothing
    }
}
