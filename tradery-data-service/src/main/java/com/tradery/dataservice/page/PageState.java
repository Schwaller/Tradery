package com.tradery.dataservice.page;

/**
 * State of a data page.
 */
public enum PageState {
    /** Page requested but not yet started loading */
    PENDING,

    /** Page is currently being loaded from cache or downloaded */
    LOADING,

    /** Page is ready with all data available */
    READY,

    /** Page load failed with an error */
    ERROR,

    /** Page was evicted due to memory pressure */
    EVICTED
}
