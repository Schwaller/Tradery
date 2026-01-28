package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;

public class AdxCompute extends IndicatorCompute<Indicators.ADXResult> {

    private final int period;

    public AdxCompute(int period) {
        this.period = period;
    }

    @Override
    public String key() {
        return "adx:" + period;
    }

    @Override
    public Indicators.ADXResult compute(IndicatorEngine engine) {
        return engine.getADX(period);
    }
}
