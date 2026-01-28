package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

public class RangePositionCompute extends IndicatorCompute<double[]> {

    private final int period;
    private final int skip;

    public RangePositionCompute(int period, int skip) {
        this.period = period;
        this.skip = skip;
    }

    @Override
    public String key() {
        return "rangeposition:" + period + ":" + skip;
    }

    @Override
    public double[] compute(IndicatorEngine engine) {
        return engine.getRangePosition(period, skip);
    }
}
