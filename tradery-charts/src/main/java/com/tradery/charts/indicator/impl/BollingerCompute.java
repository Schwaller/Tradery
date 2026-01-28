package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;

import java.util.List;

public class BollingerCompute extends IndicatorCompute<Indicators.BollingerResult> {

    private final int period;
    private final double stdDev;

    public BollingerCompute(int period, double stdDev) {
        this.period = period;
        this.stdDev = stdDev;
    }

    @Override
    public String key() {
        return "bollinger:" + period + ":" + stdDev;
    }

    @Override
    public Indicators.BollingerResult compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        return engine.getBollingerBands(period, stdDev);
    }

    public int getPeriod() {
        return period;
    }
}
