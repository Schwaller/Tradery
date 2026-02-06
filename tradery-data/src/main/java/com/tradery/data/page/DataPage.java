package com.tradery.data.page;

import com.tradery.core.model.Candle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Generic data page for market data.
 *
 * Supports both anchored (fixed time range) and live (sliding window) modes.
 * This class implements DataPageView for consumer access and adds
 * internal mutation methods for use by page managers.
 *
 * @param <T> The type of data records stored in this page
 */
public class DataPage<T> implements DataPageView<T> {

    // Identity (determines deduplication - NOT including consumer)
    private final DataType dataType;
    private final String symbol;
    private final String timeframe;    // null for non-timeframe types (Funding, OI, AggTrades)
    private final String marketType;   // "spot" or "perp" (default: "perp")
    private final String exchange;     // exchange config key (e.g., "binance", "bybit")
    private final long startTime;
    private final long endTime;
    private final long windowDurationMillis;  // Window duration (> 0 for live pages, computed for anchored)
    private final boolean liveEnabled; // True for live pages that receive real-time updates

    // State
    private volatile PageState state = PageState.EMPTY;
    private volatile String errorMessage;
    private volatile long lastSyncTime;
    private volatile long loadStartTime;  // For duration tracking
    private volatile int loadProgress = 0;  // 0-100 percentage

    // Data (volatile for visibility across threads)
    private volatile List<T> data = new ArrayList<>();

    /**
     * Create a new anchored data page (defaults to perp market, binance exchange).
     */
    public DataPage(DataType dataType, String symbol, String timeframe,
                    long startTime, long endTime) {
        this(dataType, symbol, timeframe, "perp", "binance", startTime, endTime, endTime - startTime);
    }

    /**
     * Create a new anchored data page with market type and exchange.
     */
    public DataPage(DataType dataType, String symbol, String timeframe,
                    String marketType, String exchange,
                    long startTime, long endTime) {
        this(dataType, symbol, timeframe, marketType, exchange, startTime, endTime, endTime - startTime);
    }

    /**
     * Create a new live (sliding window) data page (defaults to perp market, binance).
     */
    public static <T> DataPage<T> live(DataType dataType, String symbol, String timeframe,
                                        long startTime, long endTime, long windowDurationMillis) {
        return new DataPage<>(dataType, symbol, timeframe, "perp", "binance", startTime, endTime, windowDurationMillis, true);
    }

    /**
     * Create a new live (sliding window) data page with market type.
     */
    public static <T> DataPage<T> live(DataType dataType, String symbol, String timeframe, String marketType,
                                        long startTime, long endTime, long windowDurationMillis) {
        return new DataPage<>(dataType, symbol, timeframe, marketType, "binance", startTime, endTime, windowDurationMillis, true);
    }

    /**
     * Create a new live (sliding window) data page with market type and exchange.
     */
    public static <T> DataPage<T> live(DataType dataType, String symbol, String timeframe,
                                        String marketType, String exchange,
                                        long startTime, long endTime, long windowDurationMillis) {
        return new DataPage<>(dataType, symbol, timeframe, marketType, exchange, startTime, endTime, windowDurationMillis, true);
    }

    private DataPage(DataType dataType, String symbol, String timeframe,
                     String marketType, String exchange,
                     long startTime, long endTime, long windowDurationMillis) {
        this(dataType, symbol, timeframe, marketType, exchange, startTime, endTime, windowDurationMillis, false);
    }

    private DataPage(DataType dataType, String symbol, String timeframe,
                     String marketType, String exchange,
                     long startTime, long endTime, long windowDurationMillis, boolean liveEnabled) {
        this.dataType = dataType;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.marketType = marketType != null ? marketType : "perp";
        this.exchange = exchange != null ? exchange : "binance";
        this.startTime = startTime;
        this.endTime = endTime;
        this.windowDurationMillis = windowDurationMillis;
        this.liveEnabled = liveEnabled;
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
    public String getMarketType() {
        return marketType;
    }

    @Override
    public String getExchange() {
        return exchange;
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
        sb.append(exchange).append(":");
        sb.append(symbol).append(":");
        if (timeframe != null) {
            sb.append(timeframe).append(":");
        }
        sb.append(marketType).append(":");
        if (liveEnabled) {
            sb.append("LIVE:").append(windowDurationMillis);
        } else {
            sb.append(endTime).append(":").append(windowDurationMillis);
        }
        return sb.toString();
    }

    // ========== State (DataPageView interface) ==========

    @Override
    public PageState getState() {
        return state;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public int getLoadProgress() {
        return loadProgress;
    }

    // ========== State (internal mutation) ==========

    public void setState(PageState state) {
        this.state = state;
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

    /**
     * Append a single record to the data list.
     * Used for live updates.
     */
    public void appendData(T record) {
        List<T> newData = new ArrayList<>(this.data);
        newData.add(record);
        this.data = newData;
    }

    /**
     * Update the last record in the data list.
     * Used for live candle updates (incomplete candle).
     */
    public void updateLastRecord(T record) {
        if (!data.isEmpty()) {
            List<T> newData = new ArrayList<>(this.data);
            newData.set(newData.size() - 1, record);
            this.data = newData;
        }
    }

    /**
     * Remove records by their timestamps.
     * Used for live pages to maintain sliding window size.
     * Only works for Candle data type.
     */
    public void removeByTimestamps(List<Long> timestamps) {
        if (timestamps == null || timestamps.isEmpty() || data.isEmpty()) {
            return;
        }

        // Only works for Candle type
        if (dataType != DataType.CANDLES) {
            return;
        }

        HashSet<Long> toRemove = new HashSet<>(timestamps);
        List<T> newData = new ArrayList<>();
        for (T record : data) {
            if (record instanceof Candle candle) {
                if (!toRemove.contains(candle.timestamp())) {
                    newData.add(record);
                }
            } else {
                newData.add(record);
            }
        }
        this.data = newData;
    }

    // ========== Live Updates ==========

    public boolean isLiveEnabled() {
        return liveEnabled;
    }

    public long getWindowDurationMillis() {
        return windowDurationMillis;
    }

    /**
     * True if this is a live (sliding window) page rather than anchored.
     */
    public boolean isLivePage() {
        return liveEnabled;
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
        String liveStr = liveEnabled ? " [LIVE]" : "";
        return "DataPage[" + exchange + " " + dataType.getDisplayName() + " " + symbol + tfStr +
               " " + marketType + " " + state + " (" + data.size() + " records)" + liveStr + "]";
    }
}
