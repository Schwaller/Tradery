package com.tradery.desk.ui.charts;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Desk implementation of ChartDataProvider.
 * Provides a simple wrapper around candle data and IndicatorPool
 * for use with tradery-charts components.
 *
 * <p>Unlike forge's page-based system, desk uses direct IndicatorEngine
 * calculations via the pool since it deals with smaller, live datasets.</p>
 *
 * <p>Thread-safe: candle data can be updated from WebSocket threads while
 * being read from the UI thread.</p>
 */
public class DeskDataProvider implements ChartDataProvider {

    private static final int MAX_CANDLES = 500;

    private final Object lock = new Object();
    private List<Candle> candles = new ArrayList<>();
    private final IndicatorPool indicatorPool = new IndicatorPool();
    private volatile String symbol = "";
    private volatile String timeframe = "";

    // Reused indicator engine to avoid GC pressure on every update
    private IndicatorEngine engine;

    /**
     * Update the candle data.
     */
    public void setCandles(List<Candle> newCandles, String symbol, String timeframe) {
        synchronized (lock) {
            this.candles = new ArrayList<>(newCandles);
            this.symbol = symbol;
            this.timeframe = timeframe;
            updateIndicatorEngine();
        }
    }

    /**
     * Add or update a candle (for live updates).
     */
    public void updateCandle(Candle candle) {
        synchronized (lock) {
            if (candles.isEmpty()) {
                candles.add(candle);
            } else {
                Candle last = candles.get(candles.size() - 1);
                if (last.timestamp() == candle.timestamp()) {
                    candles.set(candles.size() - 1, candle);
                } else {
                    candles.add(candle);
                    // Keep max candles - trim from front
                    while (candles.size() > MAX_CANDLES) {
                        candles.remove(0);
                    }
                }
            }
            updateIndicatorEngine();
        }
    }

    /**
     * Update the indicator engine with current candle data.
     * Must be called with lock held.
     */
    private void updateIndicatorEngine() {
        if (!candles.isEmpty()) {
            if (engine == null) {
                engine = new IndicatorEngine();
            }
            // Reuse engine, just update its candle data
            engine.setCandles(candles, timeframe);
            indicatorPool.setDataContext(engine);
        } else {
            indicatorPool.setDataContext(null);
        }
    }

    @Override
    public List<Candle> getCandles() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(candles));
        }
    }

    @Override
    public IndicatorPool getIndicatorPool() {
        return indicatorPool;
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
        synchronized (lock) {
            if (!candles.isEmpty()) {
                return candles.get(0).timestamp();
            }
        }
        return 0;
    }

    @Override
    public long getEndTime() {
        synchronized (lock) {
            if (!candles.isEmpty()) {
                return candles.get(candles.size() - 1).timestamp();
            }
        }
        return 0;
    }

    // Desk doesn't have orderflow data (no aggTrades)
    @Override
    public double[] getDelta() {
        return null;
    }

    @Override
    public double[] getCumulativeDelta() {
        return null;
    }

    @Override
    public double[] getWhaleDelta(double threshold) {
        return null;
    }

    // Subscription methods - desk computes via pool, no additional background computation
    @Override
    public void subscribeIndicator(IndicatorType type, int... params) {
        // No-op: desk uses pool-based async calculations
    }

    @Override
    public void addDataListener(Runnable onDataReady) {
        // No-op: desk doesn't have async data loading
    }

    @Override
    public void removeDataListener(Runnable listener) {
        // No-op
    }
}
