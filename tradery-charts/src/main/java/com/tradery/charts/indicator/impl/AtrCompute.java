package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

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
    public double[] compute(IndicatorEngine engine) {
        return engine.getATR(period);
    }

    public int getPeriod() {
        return period;
    }
}
