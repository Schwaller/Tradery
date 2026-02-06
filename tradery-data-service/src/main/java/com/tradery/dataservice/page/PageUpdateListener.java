package com.tradery.dataservice.page;

import com.tradery.core.model.Candle;
import com.tradery.data.page.PageKey;

import java.util.List;

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

    /**
     * Called when the incomplete/forming candle is updated (live pages only).
     */
    default void onLiveUpdate(PageKey key, Candle candle) {}

    /**
     * Called when a new completed candle is appended (live pages only).
     * @param removed Candles that were removed to maintain window size
     */
    default void onLiveAppend(PageKey key, Candle candle, List<Candle> removed) {}
}
