package com.tradery.desk.feed;

import com.tradery.core.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Aggregates WebSocket kline messages into a rolling window of candles.
 * Maintains historical candles + current incomplete candle.
 *
 * Thread-safe: history and currentCandle can be accessed from WebSocket
 * callback threads and UI/evaluation threads concurrently.
 */
public class CandleAggregator {

    private static final Logger log = LoggerFactory.getLogger(CandleAggregator.class);

    private final String symbol;
    private final String timeframe;
    private final int maxHistory;

    // Thread-safe: synchronized access via lock object
    private final Object lock = new Object();
    private final List<Candle> history = new ArrayList<>();
    private volatile Candle currentCandle;

    private volatile Consumer<Candle> onCandleClose;
    private volatile Consumer<Candle> onCandleUpdate;

    public CandleAggregator(String symbol, String timeframe, int maxHistory) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.maxHistory = maxHistory;
    }

    /**
     * Set callback for when a candle closes.
     */
    public void setOnCandleClose(Consumer<Candle> callback) {
        this.onCandleClose = callback;
    }

    /**
     * Set callback for live candle updates (incomplete candles).
     */
    public void setOnCandleUpdate(Consumer<Candle> callback) {
        this.onCandleUpdate = callback;
    }

    /**
     * Initialize with historical candles (for indicator warmup).
     */
    public void setHistory(List<Candle> candles) {
        synchronized (lock) {
            history.clear();
            if (candles != null) {
                // Keep only up to maxHistory candles
                int start = Math.max(0, candles.size() - maxHistory);
                for (int i = start; i < candles.size(); i++) {
                    history.add(candles.get(i));
                }
            }
            log.info("Initialized {} with {} historical candles", symbol, history.size());
        }
    }

    /**
     * Process a kline message from WebSocket.
     */
    public void processKline(KlineMessage message) {
        if (!message.isKlineEvent()) {
            return;
        }

        Candle candle = message.toCandle();
        if (candle == null) {
            return;
        }

        Consumer<Candle> closeCallback = null;
        Consumer<Candle> updateCallback = null;

        if (message.isClosed()) {
            // Candle closed - add to history
            synchronized (lock) {
                addClosedCandleInternal(candle);
            }
            currentCandle = null;
            closeCallback = onCandleClose;
        } else {
            // Update current (incomplete) candle
            currentCandle = candle;
            updateCallback = onCandleUpdate;
        }

        // Notify outside synchronized block to avoid potential deadlocks
        if (closeCallback != null) {
            closeCallback.accept(candle);
        }
        if (updateCallback != null) {
            updateCallback.accept(candle);
        }
    }

    /**
     * Add a closed candle to history (public method for external use).
     * Note: Does NOT call onCandleClose callback - caller is responsible for handling.
     */
    public void addClosedCandle(Candle candle) {
        synchronized (lock) {
            addClosedCandleInternal(candle);
        }
    }

    /**
     * Add a closed candle to history (internal, must be called with lock held).
     */
    private void addClosedCandleInternal(Candle candle) {
        // Check if this candle timestamp already exists
        if (!history.isEmpty()) {
            Candle last = history.get(history.size() - 1);
            if (last.timestamp() == candle.timestamp()) {
                // Update existing candle
                history.set(history.size() - 1, candle);
                return;
            } else if (last.timestamp() > candle.timestamp()) {
                // Old candle, ignore
                return;
            }
        }

        history.add(candle);

        // Trim history if needed - remove from front efficiently
        // ArrayList.remove(0) is O(n), but this happens infrequently (once per candle close)
        // and maxHistory is typically large enough that we rarely trim
        while (history.size() > maxHistory) {
            history.remove(0);
        }
    }

    /**
     * Get all candles (history + current if exists).
     * Returns an immutable snapshot.
     */
    public List<Candle> getAllCandles() {
        synchronized (lock) {
            List<Candle> all = new ArrayList<>(history);
            Candle current = currentCandle;
            if (current != null) {
                all.add(current);
            }
            return Collections.unmodifiableList(all);
        }
    }

    /**
     * Get historical candles only (closed candles).
     * Returns an immutable snapshot.
     */
    public List<Candle> getHistory() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    /**
     * Get the current incomplete candle.
     */
    public Candle getCurrentCandle() {
        return currentCandle;
    }

    /**
     * Get the last closed candle.
     */
    public Candle getLastClosedCandle() {
        synchronized (lock) {
            return history.isEmpty() ? null : history.get(history.size() - 1);
        }
    }

    /**
     * Get the current price (close of current or last candle).
     */
    public double getCurrentPrice() {
        Candle current = currentCandle;
        if (current != null) {
            return current.close();
        }
        synchronized (lock) {
            if (!history.isEmpty()) {
                return history.get(history.size() - 1).close();
            }
        }
        return Double.NaN;
    }

    /**
     * Get the number of candles available for evaluation.
     */
    public int getCandleCount() {
        synchronized (lock) {
            return history.size() + (currentCandle != null ? 1 : 0);
        }
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }
}
