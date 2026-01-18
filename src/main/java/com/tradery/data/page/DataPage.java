package com.tradery.data.page;

import com.tradery.data.DataType;
import com.tradery.data.PageState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic data page for the event-driven data architecture.
 *
 * Key difference from the old system: The page key does NOT include consumer name.
 * This enables proper deduplication - multiple consumers can share the same page.
 *
 * @param <T> The type of data records stored in this page
 */
public class DataPage<T> {

    // Identity (determines deduplication - NOT including consumer)
    private final DataType dataType;
    private final String symbol;
    private final String timeframe;    // null for non-timeframe types (Funding, OI, AggTrades)
    private final long startTime;
    private final long endTime;

    // State
    private volatile PageState state = PageState.EMPTY;
    private volatile String errorMessage;
    private volatile long lastSyncTime;

    // Data (volatile for visibility across threads)
    private volatile List<T> data = new ArrayList<>();

    /**
     * Create a new data page.
     *
     * @param dataType  Type of data (CANDLES, FUNDING, etc.)
     * @param symbol    Trading symbol (e.g., "BTCUSDT")
     * @param timeframe Timeframe for candles/premium, null for other types
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     */
    public DataPage(DataType dataType, String symbol, String timeframe,
                    long startTime, long endTime) {
        this.dataType = dataType;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // ========== Identity ==========

    public DataType getDataType() {
        return dataType;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Get the timeframe (for CANDLES and PREMIUM_INDEX).
     * Returns null for data types that don't use timeframe.
     */
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
     * Generate a unique key for this page based on data identity only.
     * This key is used for deduplication - NO consumer name included.
     */
    public String getKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(dataType).append(":");
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

    public long getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    // ========== Data ==========

    /**
     * Get the current data (may be empty during initial load).
     * Returns an unmodifiable view of the data.
     */
    public List<T> getData() {
        return Collections.unmodifiableList(data);
    }

    /**
     * Set the data, creating a defensive copy.
     */
    public void setData(List<T> data) {
        this.data = new ArrayList<>(data);
    }

    /**
     * Set data directly without copying.
     * Use only when the caller has created an independent copy.
     */
    public void setDataDirect(List<T> data) {
        this.data = data;
    }

    public boolean hasData() {
        return !data.isEmpty();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public int getRecordCount() {
        return data.size();
    }

    // ========== Convenience State Checks ==========

    /**
     * Check if the page is ready for use (has data available).
     */
    public boolean isReady() {
        return state == PageState.READY || state == PageState.UPDATING;
    }

    /**
     * Check if the page is currently loading or updating.
     */
    public boolean isLoading() {
        return state == PageState.LOADING || state == PageState.UPDATING;
    }

    /**
     * Check if the page is in an error state.
     */
    public boolean hasError() {
        return state == PageState.ERROR;
    }

    @Override
    public String toString() {
        String tfStr = timeframe != null ? "/" + timeframe : "";
        return "DataPage[" + dataType.getDisplayName() + " " + symbol + tfStr +
               " " + state + " (" + data.size() + " records)]";
    }
}
