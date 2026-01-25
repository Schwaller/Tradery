package com.tradery.forge.data.page;

import com.tradery.forge.data.PageState;

/**
 * Listener for data page state and data changes.
 * All callbacks are guaranteed to be called on the Swing EDT.
 *
 * Callbacks receive DataPageView (read-only interface) to prevent
 * consumers from accidentally modifying page state.
 *
 * @param <T> The type of data records in the page
 */
public interface DataPageListener<T> {

    /**
     * Called when the page state changes (EMPTY -> LOADING -> READY, etc.).
     * Always called on the Swing EDT.
     *
     * @param page     The page whose state changed (read-only view)
     * @param oldState Previous state
     * @param newState New state
     */
    void onStateChanged(DataPageView<T> page, PageState oldState, PageState newState);

    /**
     * Called when the data in the page changes (new data loaded, updated, etc.).
     * Always called on the Swing EDT.
     *
     * Default implementation does nothing - override if you need data change notifications.
     *
     * @param page The page whose data changed (read-only view)
     */
    default void onDataChanged(DataPageView<T> page) {
        // Default: do nothing
    }
}
