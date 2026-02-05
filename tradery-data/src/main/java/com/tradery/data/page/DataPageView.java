package com.tradery.data.page;

import java.util.List;

/**
 * Read-only view of a data page for consumers.
 *
 * Consumers receive this interface instead of the full DataPage,
 * preventing them from modifying page state or data.
 *
 * @param <T> The type of data records in this page
 */
public interface DataPageView<T> {

    // ========== Identity ==========

    /**
     * Get the type of data in this page.
     */
    DataType getDataType();

    /**
     * Get the trading symbol (e.g., "BTCUSDT").
     */
    String getSymbol();

    /**
     * Get the timeframe (for CANDLES and PREMIUM_INDEX).
     * Returns null for data types that don't use timeframe.
     */
    String getTimeframe();

    /**
     * Get the market type ("spot" or "perp").
     * Returns "perp" as default.
     */
    default String getMarketType() {
        return "perp";
    }

    /**
     * Get the start time of the data range in milliseconds.
     */
    long getStartTime();

    /**
     * Get the end time of the data range in milliseconds.
     */
    long getEndTime();

    /**
     * Get unique key identifying this page.
     */
    String getKey();

    // ========== State ==========

    /**
     * Get the current state of this page.
     */
    PageState getState();

    /**
     * Get error message if state is ERROR.
     */
    String getErrorMessage();

    /**
     * Get load progress as percentage (0-100).
     */
    int getLoadProgress();

    /**
     * Check if the page is ready for use (has data available).
     * True when state is READY or UPDATING.
     */
    boolean isReady();

    /**
     * Check if the page is currently loading or updating.
     */
    boolean isLoading();

    /**
     * Check if the page is in an error state.
     */
    boolean hasError();

    // ========== Data ==========

    /**
     * Get the data records.
     * Returns an unmodifiable view - changes to the underlying data
     * will be reflected but the list cannot be modified.
     */
    List<T> getData();

    /**
     * Check if the page has any data.
     */
    boolean hasData();

    /**
     * Check if the page is empty (no data).
     */
    boolean isEmpty();

    /**
     * Get the number of records in the page.
     */
    int getRecordCount();
}
