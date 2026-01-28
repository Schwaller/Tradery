package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;

import java.util.List;

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
    public Indicators.IchimokuResult compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        return engine.getIchimoku(conversionPeriod, basePeriod, spanBPeriod, displacement);
    }

    public int getWarmup() {
        return Math.max(Math.max(conversionPeriod, basePeriod), spanBPeriod) - 1;
    }
}
