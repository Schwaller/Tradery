package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Donchian Channel: highest high and lowest low over N bars.
 */
public class DonchianCompute extends IndicatorCompute<DonchianCompute.Result> {

    private final int period;

    public DonchianCompute(int period) {
        this.period = period;
    }

    @Override
    public String key() {
        return "donchian:" + period;
    }

    @Override
    public Result compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        double[] highOf = engine.getHighOf(period);
        double[] lowOf = engine.getLowOf(period);
        if (highOf == null || lowOf == null) return null;
        return new Result(highOf, lowOf, period);
    }

    public record Result(double[] highOf, double[] lowOf, int warmup) {}
}
