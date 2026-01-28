package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

public class EmaCompute extends IndicatorCompute<double[]> {

    private final int period;

    public EmaCompute(int period) {
        this.period = period;
    }

    @Override
    public String key() {
        return "ema:" + period;
    }

    @Override
    public double[] compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        return engine.getEMA(period);
    }

    public int getPeriod() {
        return period;
    }
}
