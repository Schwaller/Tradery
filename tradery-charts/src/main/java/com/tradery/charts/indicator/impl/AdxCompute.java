package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;

import java.util.List;

public class AdxCompute extends IndicatorCompute<Indicators.ADXResult> {

    private final int period;

    public AdxCompute(int period) {
        this.period = period;
    }

    @Override
    public String key() {
        return "adx:" + period;
    }

    @Override
    public Indicators.ADXResult compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        return engine.getADX(period);
    }
}
