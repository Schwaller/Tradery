package com.tradery.dataservice.page;

/**
 * Listener for page state changes.
 */
public interface PageUpdateListener {
    /**
     * Called when page state changes.
     */
    void onStateChanged(PageKey key, PageState state, int progress);

    /**
     * Called when page data is ready.
     */
    void onDataReady(PageKey key, long recordCount);

    /**
     * Called when page loading fails.
     */
    void onError(PageKey key, String message);

    /**
     * Called when page is evicted due to memory pressure.
     */
    void onEvicted(PageKey key);
}
