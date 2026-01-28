package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

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
    public double[] compute(IndicatorEngine engine) {
        return engine.getSMA(period);
    }

    public int getPeriod() {
        return period;
    }
}
