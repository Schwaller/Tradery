package com.tradery.desk.ui.charts;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Desk implementation of ChartDataProvider.
 * Provides a simple wrapper around candle data and IndicatorEngine
 * for use with tradery-charts components.
 *
 * <p>Unlike forge's page-based system, desk uses direct IndicatorEngine
 * calculations since it deals with smaller, live datasets.</p>
 */
public class DeskDataProvider implements ChartDataProvider {

    private List<Candle> candles = new ArrayList<>();
    private final IndicatorPool indicatorPool = new IndicatorPool();
    private IndicatorEngine indicatorEngine;
    private String symbol = "";
    private String timeframe = "";

    /**
     * Update the candle data.
     */
    public void setCandles(List<Candle> candles, String symbol, String timeframe) {
        this.candles = new ArrayList<>(candles);
        this.symbol = symbol;
        this.timeframe = timeframe;
        // Create fresh indicator engine with new candles
        if (!candles.isEmpty()) {
            this.indicatorEngine = new IndicatorEngine();
            this.indicatorEngine.setCandles(candles, timeframe);
            indicatorPool.setDataContext(this.candles, symbol, timeframe,
                    candles.get(0).timestamp(), candles.get(candles.size() - 1).timestamp());
        } else {
            this.indicatorEngine = null;
        }
    }

    /**
     * Add or update a candle (for live updates).
     */
    public void updateCandle(Candle candle) {
        if (candles.isEmpty()) {
            candles.add(candle);
        } else {
            Candle last = candles.get(candles.size() - 1);
            if (last.timestamp() == candle.timestamp()) {
                candles.set(candles.size() - 1, candle);
            } else {
                candles.add(candle);
                // Keep max 500 candles
                while (candles.size() > 500) {
                    candles.remove(0);
                }
            }
        }
        // Refresh indicator engine
        if (!candles.isEmpty()) {
            this.indicatorEngine = new IndicatorEngine();
            this.indicatorEngine.setCandles(candles, timeframe);
            indicatorPool.setDataContext(candles, symbol, timeframe,
                    candles.get(0).timestamp(), candles.get(candles.size() - 1).timestamp());
        } else {
            this.indicatorEngine = null;
        }
    }

    @Override
    public List<Candle> getCandles() {
        return candles;
    }

    @Override
    public IndicatorEngine getIndicatorEngine() {
        return indicatorEngine;
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
        if (candles != null && !candles.isEmpty()) {
            return candles.get(0).timestamp();
        }
        return 0;
    }

    @Override
    public long getEndTime() {
        if (candles != null && !candles.isEmpty()) {
            return candles.get(candles.size() - 1).timestamp();
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

    // Subscription methods - desk computes on-demand, no background computation
    @Override
    public void subscribeIndicator(IndicatorType type, int... params) {
        // No-op: desk uses synchronous IndicatorEngine calculations
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
