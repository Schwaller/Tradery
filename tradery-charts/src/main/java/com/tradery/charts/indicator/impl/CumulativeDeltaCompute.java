package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

public class CumulativeDeltaCompute extends IndicatorCompute<double[]> {

    @Override
    public String key() {
        return "cvd";
    }

    @Override
    public double[] compute(IndicatorEngine engine) {
        return engine.getCumulativeDelta();
    }
}
