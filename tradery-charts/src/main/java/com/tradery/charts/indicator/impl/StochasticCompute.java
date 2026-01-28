package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;

import java.util.List;

public class StochasticCompute extends IndicatorCompute<Indicators.StochasticResult> {

    private final int kPeriod;
    private final int dPeriod;

    public StochasticCompute(int kPeriod, int dPeriod) {
        this.kPeriod = kPeriod;
        this.dPeriod = dPeriod;
    }

    @Override
    public String key() {
        return "stochastic:" + kPeriod + ":" + dPeriod;
    }

    @Override
    public Indicators.StochasticResult compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        return engine.getStochastic(kPeriod, dPeriod);
    }
}
