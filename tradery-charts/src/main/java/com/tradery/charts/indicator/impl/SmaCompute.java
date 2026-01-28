package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

public class SmaCompute extends IndicatorCompute<double[]> {

    private final int period;

    public SmaCompute(int period) {
        this.period = period;
    }

    @Override
    public String key() {
        return "sma:" + period;
    }

    @Override
    public double[] compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        return engine.getSMA(period);
    }

    public int getPeriod() {
        return period;
    }
}
