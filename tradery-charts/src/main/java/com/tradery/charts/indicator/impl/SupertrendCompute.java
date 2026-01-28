package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Supertrend;

public class SupertrendCompute extends IndicatorCompute<Supertrend.Result> {

    private final int period;
    private final double multiplier;

    public SupertrendCompute(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public String key() {
        return "supertrend:" + period + ":" + multiplier;
    }

    @Override
    public Supertrend.Result compute(IndicatorEngine engine) {
        return engine.getSupertrend(period, multiplier);
    }

    public int getPeriod() {
        return period;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
