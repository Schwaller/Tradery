package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;

public class IchimokuCompute extends IndicatorCompute<Indicators.IchimokuResult> {

    private final int conversionPeriod;
    private final int basePeriod;
    private final int spanBPeriod;
    private final int displacement;

    public IchimokuCompute(int conversionPeriod, int basePeriod, int spanBPeriod, int displacement) {
        this.conversionPeriod = conversionPeriod;
        this.basePeriod = basePeriod;
        this.spanBPeriod = spanBPeriod;
        this.displacement = displacement;
    }

    @Override
    public String key() {
        return "ichimoku:" + conversionPeriod + ":" + basePeriod + ":" + spanBPeriod + ":" + displacement;
    }

    @Override
    public Indicators.IchimokuResult compute(IndicatorEngine engine) {
        return engine.getIchimoku(conversionPeriod, basePeriod, spanBPeriod, displacement);
    }

    public int getWarmup() {
        return Math.max(Math.max(conversionPeriod, basePeriod), spanBPeriod) - 1;
    }
}
