package com.tradery.core.model;

/**
 * Status of data completeness for a time period.
 */
public enum DataStatus {
    /** All expected candles are present */
    COMPLETE,

    /** Some candles exist but there are gaps */
    PARTIAL,

    /** No data file exists */
    MISSING,

    /** Status could not be determined */
    UNKNOWN
}
