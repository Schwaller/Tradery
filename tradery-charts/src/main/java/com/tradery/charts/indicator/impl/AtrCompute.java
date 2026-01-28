package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

public class AtrCompute extends IndicatorCompute<double[]> {

    private final int period;

    public AtrCompute(int period) {
        this.period = period;
    }

    @Override
    public String key() {
        return "atr:" + period;
    }

    @Override
    public double[] compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        return engine.getATR(period);
    }

    public int getPeriod() {
        return period;
    }
}
