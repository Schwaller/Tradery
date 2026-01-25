package com.tradery.desk.feed;

import com.tradery.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Aggregates WebSocket kline messages into a rolling window of candles.
 * Maintains historical candles + current incomplete candle.
 */
public class CandleAggregator {

    private static final Logger log = LoggerFactory.getLogger(CandleAggregator.class);

    private final String symbol;
    private final String timeframe;
    private final int maxHistory;
    private final List<Candle> history = new ArrayList<>();
    private Candle currentCandle;
    private Consumer<Candle> onCandleClose;
    private Consumer<Candle> onCandleUpdate;

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

        if (message.isClosed()) {
            // Candle closed - add to history and notify
            addClosedCandleInternal(candle);

            if (onCandleClose != null) {
                onCandleClose.accept(candle);
            }

            // Clear current candle
            currentCandle = null;
        } else {
            // Update current (incomplete) candle
            currentCandle = candle;

            if (onCandleUpdate != null) {
                onCandleUpdate.accept(candle);
            }
        }
    }

    /**
     * Add a closed candle to history (public method for external use).
     * Note: Does NOT call onCandleClose callback - caller is responsible for handling.
     */
    public void addClosedCandle(Candle candle) {
        addClosedCandleInternal(candle);
    }

    /**
     * Add a closed candle to history (internal).
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

        // Trim history if needed
        while (history.size() > maxHistory) {
            history.remove(0);
        }
    }

    /**
     * Get all candles (history + current if exists).
     */
    public List<Candle> getAllCandles() {
        List<Candle> all = new ArrayList<>(history);
        if (currentCandle != null) {
            all.add(currentCandle);
        }
        return all;
    }

    /**
     * Get historical candles only (closed candles).
     */
    public List<Candle> getHistory() {
        return new ArrayList<>(history);
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
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    /**
     * Get the current price (close of current or last candle).
     */
    public double getCurrentPrice() {
        if (currentCandle != null) {
            return currentCandle.close();
        }
        if (!history.isEmpty()) {
            return history.get(history.size() - 1).close();
        }
        return Double.NaN;
    }

    /**
     * Get the number of candles available for evaluation.
     */
    public int getCandleCount() {
        return history.size() + (currentCandle != null ? 1 : 0);
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }
}
