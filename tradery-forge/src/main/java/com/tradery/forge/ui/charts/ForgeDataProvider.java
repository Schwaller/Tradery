package com.tradery.forge.ui.charts;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Forge implementation of ChartDataProvider.
 * Wraps IndicatorDataService for async indicator data and provides
 * access to IndicatorEngine for sync calculations.
 *
 * <p>This bridges the tradery-charts module with forge's page-based
 * indicator management system.</p>
 */
public class ForgeDataProvider implements ChartDataProvider {

    private final IndicatorDataService indicatorDataService;
    private final IndicatorPool indicatorPool = new IndicatorPool();
    private IndicatorEngine indicatorEngine;
    private String symbol = "";
    private String timeframe = "";
    private long startTime;
    private long endTime;

    public ForgeDataProvider(IndicatorDataService indicatorDataService) {
        this.indicatorDataService = indicatorDataService;
    }

    /**
     * Set the full data context.
     * Call this when candles, symbol, or timeframe change.
     */
    public void setDataContext(List<Candle> candles, String symbol, String timeframe,
                                long startTime, long endTime) {
        this.symbol = symbol != null ? symbol : "";
        this.timeframe = timeframe != null ? timeframe : "";
        this.startTime = startTime;
        this.endTime = endTime;

        // Recreate indicator engine with new candles
        if (candles != null && !candles.isEmpty()) {
            this.indicatorEngine = new IndicatorEngine();
            this.indicatorEngine.setCandles(candles, timeframe != null ? timeframe : "1h");
        } else {
            this.indicatorEngine = null;
        }

        // Update the indicator data service context too
        indicatorDataService.setDataContext(candles, symbol, timeframe, startTime, endTime);

        // Update indicator pool context
        indicatorPool.setDataContext(candles, symbol, timeframe, startTime, endTime);
    }

    /**
     * Set the indicator engine for sync calculations.
     * @deprecated Use setDataContext() instead
     */
    @Deprecated
    public void setIndicatorEngine(IndicatorEngine engine) {
        this.indicatorEngine = engine;
    }

    @Override
    public List<Candle> getCandles() {
        return indicatorDataService.getCandles();
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
        if (startTime > 0) {
            return startTime;
        }
        List<Candle> candles = getCandles();
        if (candles != null && !candles.isEmpty()) {
            return candles.get(0).timestamp();
        }
        return 0;
    }

    @Override
    public long getEndTime() {
        if (endTime > 0) {
            return endTime;
        }
        List<Candle> candles = getCandles();
        if (candles != null && !candles.isEmpty()) {
            return candles.get(candles.size() - 1).timestamp();
        }
        return 0;
    }

    // ===== Async indicator subscription (delegates to IndicatorDataService) =====

    @Override
    public void subscribeIndicator(IndicatorType type, int... params) {
        switch (type) {
            case RSI -> {
                if (params.length >= 1) indicatorDataService.subscribeRSI(params[0]);
            }
            case MACD -> {
                if (params.length >= 3) indicatorDataService.subscribeMACD(params[0], params[1], params[2]);
            }
            case ATR -> {
                if (params.length >= 1) indicatorDataService.subscribeATR(params[0]);
            }
            case STOCHASTIC -> {
                if (params.length >= 2) indicatorDataService.subscribeStochastic(params[0], params[1]);
            }
            case ADX -> {
                if (params.length >= 1) indicatorDataService.subscribeADX(params[0]);
            }
            case DELTA -> indicatorDataService.subscribeDelta();
            case CVD -> indicatorDataService.subscribeCumDelta();
            case TRADE_COUNT -> indicatorDataService.subscribeTradeCount();
            default -> {
                // Other types not yet mapped
            }
        }
    }

    @Override
    public void addDataListener(Runnable onDataReady) {
        indicatorDataService.addDataListener(onDataReady);
    }

    @Override
    public void removeDataListener(Runnable listener) {
        indicatorDataService.removeDataListener(listener);
    }

    // ===== Orderflow data (from IndicatorDataService) =====

    @Override
    public double[] getDelta() {
        return indicatorDataService.getDelta();
    }

    @Override
    public double[] getCumulativeDelta() {
        return indicatorDataService.getCumDelta();
    }

    @Override
    public double[] getWhaleDelta(double threshold) {
        return indicatorDataService.getWhaleDelta(threshold);
    }

    /**
     * Get the underlying IndicatorDataService.
     * Useful for accessing forge-specific features.
     */
    public IndicatorDataService getIndicatorDataService() {
        return indicatorDataService;
    }
}
