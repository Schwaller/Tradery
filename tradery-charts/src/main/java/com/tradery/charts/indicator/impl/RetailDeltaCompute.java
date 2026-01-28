package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

public class RetailDeltaCompute extends IndicatorCompute<double[]> {

    private final double threshold;

    public RetailDeltaCompute(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String key() {
        return "retaildelta:" + threshold;
    }

    @Override
    public double[] compute(IndicatorEngine engine) {
        return engine.getRetailDelta(threshold);
    }
}
