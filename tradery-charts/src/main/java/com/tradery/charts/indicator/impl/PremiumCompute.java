package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

public class PremiumCompute extends IndicatorCompute<double[]> {

    @Override
    public String key() {
        return "premium";
    }

    @Override
    public double[] compute(IndicatorEngine engine) {
        return engine.getPremium();
    }
}
