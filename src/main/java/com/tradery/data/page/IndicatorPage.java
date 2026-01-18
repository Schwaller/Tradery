package com.tradery.data.page;

import com.tradery.data.PageState;

/**
 * Represents a computed indicator for the indicator page system.
 *
 * Indicators are a second layer that builds on top of data pages.
 * When the source data changes, the indicator is recomputed.
 *
 * @param <T> The type of computed data (double[], MACDResult, etc.)
 */
public class IndicatorPage<T> {

    // Identity
    private final IndicatorType type;
    private final String params;         // e.g., "14" for RSI(14), "12:26:9" for MACD
    private final String symbol;
    private final String timeframe;      // null for non-timeframe indicators
    private final long startTime;
    private final long endTime;

    // State
    private volatile PageState state = PageState.EMPTY;
    private volatile String errorMessage;

    // Computed data
    private volatile T data;

    // Source dependency tracking
    private volatile String sourceCandleHash;   // Hash of candles used for computation
    private volatile long computeTime;          // When computation finished

    public IndicatorPage(IndicatorType type, String params, String symbol,
                          String timeframe, long startTime, long endTime) {
        this.type = type;
        this.params = params;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // ========== Identity ==========

    public IndicatorType getType() {
        return type;
    }

    public String getParams() {
        return params;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    /**
     * Generate a unique key for deduplication.
     */
    public String getKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append(":");
        sb.append(params).append(":");
        sb.append(symbol).append(":");
        if (timeframe != null) {
            sb.append(timeframe).append(":");
        }
        sb.append(startTime).append(":").append(endTime);
        return sb.toString();
    }

    // ========== State ==========

    public PageState getState() {
        return state;
    }

    public void setState(PageState state) {
        this.state = state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // ========== Data ==========

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean hasData() {
        return data != null;
    }

    // ========== Source Tracking ==========

    public String getSourceCandleHash() {
        return sourceCandleHash;
    }

    public void setSourceCandleHash(String hash) {
        this.sourceCandleHash = hash;
    }

    public long getComputeTime() {
        return computeTime;
    }

    public void setComputeTime(long computeTime) {
        this.computeTime = computeTime;
    }

    /**
     * Check if the computed data is still valid (source hasn't changed).
     */
    public boolean isValid(String currentSourceHash) {
        return sourceCandleHash != null && sourceCandleHash.equals(currentSourceHash);
    }

    // ========== Convenience State Checks ==========

    public boolean isReady() {
        return state == PageState.READY;
    }

    public boolean isLoading() {
        return state == PageState.LOADING;
    }

    public boolean hasError() {
        return state == PageState.ERROR;
    }

    @Override
    public String toString() {
        String tfStr = timeframe != null ? "/" + timeframe : "";
        return "IndicatorPage[" + type.getName() + "(" + params + ") " +
               symbol + tfStr + " " + state + "]";
    }
}
