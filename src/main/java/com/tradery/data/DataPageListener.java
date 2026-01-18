package com.tradery.data;

/**
 * Listener for data page updates.
 * Components implement this to receive notifications when their checked-out pages change.
 */
public interface DataPageListener {

    /**
     * Called when the page's data has been updated.
     * This is called on the Swing EDT for safe UI updates.
     */
    void onPageDataUpdated(DataPage page);

    /**
     * Called when the page's state changes (e.g., LOADING -> READY).
     * This is called on the Swing EDT for safe UI updates.
     */
    default void onPageStateChanged(DataPage page, DataPage.State oldState, DataPage.State newState) {
        // Optional - default does nothing
    }
}
