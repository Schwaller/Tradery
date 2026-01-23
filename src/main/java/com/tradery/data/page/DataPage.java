package com.tradery.data.page;

import com.tradery.data.DataType;
import com.tradery.data.PageState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic data page for the event-driven data architecture.
 *
 * This class implements DataPageView for consumer access and adds
 * internal mutation methods for use by DataPageManager.
 *
 * Key difference from the old system: The page key does NOT include consumer name.
 * This enables proper deduplication - multiple consumers can share the same page.
 *
 * @param <T> The type of data records stored in this page
 */
public class DataPage<T> implements DataPageView<T> {

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
    private volatile long loadStartTime;  // For duration tracking
    private volatile int loadProgress = 0;  // 0-100 percentage

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

    // ========== Identity (DataPageView interface) ==========

    @Override
    public DataType getDataType() {
        return dataType;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public String getTimeframe() {
        return timeframe;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
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

    // ========== State (DataPageView interface) ==========

    @Override
    public PageState getState() {
        return state;
    }

    // ========== State (internal mutation) ==========

    public void setState(PageState state) {
        this.state = state;
    }

    @Override
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

    public long getLoadStartTime() {
        return loadStartTime;
    }

    public void setLoadStartTime(long loadStartTime) {
        this.loadStartTime = loadStartTime;
    }

    public int getLoadProgress() {
        return loadProgress;
    }

    public void setLoadProgress(int loadProgress) {
        this.loadProgress = Math.max(0, Math.min(100, loadProgress));
    }

    // ========== Data (DataPageView interface) ==========

    @Override
    public List<T> getData() {
        return Collections.unmodifiableList(data);
    }

    @Override
    public boolean hasData() {
        return !data.isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public int getRecordCount() {
        return data.size();
    }

    // ========== Data (internal mutation) ==========

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

    // ========== State Checks (DataPageView interface) ==========

    @Override
    public boolean isReady() {
        return state == PageState.READY || state == PageState.UPDATING;
    }

    @Override
    public boolean isLoading() {
        return state == PageState.LOADING || state == PageState.UPDATING;
    }

    @Override
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
