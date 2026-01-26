package com.tradery.forge.ui.charts;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.core.IndicatorType;
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
    private IndicatorEngine indicatorEngine;

    public ForgeDataProvider(IndicatorDataService indicatorDataService) {
        this.indicatorDataService = indicatorDataService;
    }

    /**
     * Set the indicator engine for sync calculations.
     * Call this when the data context changes.
     */
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
    public String getSymbol() {
        // Get from candles if available
        List<Candle> candles = getCandles();
        if (candles != null && !candles.isEmpty()) {
            // Symbol is stored in context, not candles - return empty for now
            return "";
        }
        return "";
    }

    @Override
    public String getTimeframe() {
        // Timeframe is stored in context, not candles - return empty for now
        return "";
    }

    @Override
    public long getStartTime() {
        List<Candle> candles = getCandles();
        if (candles != null && !candles.isEmpty()) {
            return candles.get(0).timestamp();
        }
        return 0;
    }

    @Override
    public long getEndTime() {
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
