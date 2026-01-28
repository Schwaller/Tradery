package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

public class WhaleDeltaCompute extends IndicatorCompute<double[]> {

    private final double threshold;

    public WhaleDeltaCompute(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String key() {
        return "whaledelta:" + threshold;
    }

    @Override
    public double[] compute(IndicatorEngine engine) {
        return engine.getWhaleDelta(threshold);
    }
}
