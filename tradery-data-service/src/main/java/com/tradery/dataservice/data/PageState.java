package com.tradery.dataservice.data;

/**
 * State of a data page in the unified data page system.
 */
public enum PageState {
    /**
     * No data loaded yet - page is empty.
     */
    EMPTY,

    /**
     * Initial data load is in progress.
     */
    LOADING,

    /**
     * Data is loaded and ready for use.
     */
    READY,

    /**
     * Background update is in progress (data is still available).
     */
    UPDATING,

    /**
     * Load or update failed. Check errorMessage for details.
     */
    ERROR
}
