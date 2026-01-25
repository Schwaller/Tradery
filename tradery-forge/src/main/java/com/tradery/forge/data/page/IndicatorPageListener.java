package com.tradery.forge.data.page;

import com.tradery.forge.data.PageState;

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
}
