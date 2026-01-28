package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;

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
    public Indicators.BollingerResult compute(IndicatorEngine engine) {
        return engine.getBollingerBands(period, stdDev);
    }

    public int getPeriod() {
        return period;
    }
}
