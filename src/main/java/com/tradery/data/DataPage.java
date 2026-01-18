package com.tradery.data;

import com.tradery.model.Candle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a checked-out region of candle data.
 * Components check out pages they need, receive updates when data changes,
 * and release pages when done.
 */
public class DataPage {

    public enum State {
        EMPTY,      // No data loaded yet
        LOADING,    // Initial load in progress
        READY,      // Data loaded and up to date
        UPDATING,   // Background update in progress
        ERROR       // Load/update failed
    }

    private final String symbol;
    private final String timeframe;
    private final long startTime;
    private final long endTime;

    private List<Candle> candles = new ArrayList<>();
    private State state = State.EMPTY;
    private String errorMessage;
    private long lastSyncTime = 0;

    public DataPage(String symbol, String timeframe, long startTime, long endTime) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.startTime = startTime;
        this.endTime = endTime;
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

    public State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<Candle> getCandles() {
        return Collections.unmodifiableList(candles);
    }

    void setCandles(List<Candle> candles) {
        this.candles = new ArrayList<>(candles);
    }

    public long getLastSyncTime() {
        return lastSyncTime;
    }

    void setLastSyncTime(long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public boolean isEmpty() {
        return candles.isEmpty();
    }

    public boolean isReady() {
        return state == State.READY || state == State.UPDATING;
    }

    /**
     * Create a unique key for this page's bounds.
     */
    public String getKey() {
        return symbol + ":" + timeframe + ":" + startTime + ":" + endTime;
    }

    @Override
    public String toString() {
        return "DataPage[" + symbol + " " + timeframe + " " + state + " (" + candles.size() + " candles)]";
    }
}
