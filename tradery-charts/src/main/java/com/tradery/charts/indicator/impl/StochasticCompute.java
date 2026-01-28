package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;

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
    public Indicators.StochasticResult compute(IndicatorEngine engine) {
        return engine.getStochastic(kPeriod, dPeriod);
    }
}
