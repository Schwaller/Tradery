package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

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
    public double[] compute(IndicatorEngine engine) {
        return engine.getEMA(period);
    }

    public int getPeriod() {
        return period;
    }
}
