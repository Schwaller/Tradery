package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.RotatingRays;
import com.tradery.core.model.Candle;

import java.util.List;

public class RaysCompute extends IndicatorCompute<RaysCompute.Result> {

    private final int lookback;
    private final int skip;
    private final boolean showResistance;
    private final boolean showSupport;

    public RaysCompute(int lookback, int skip, boolean showResistance, boolean showSupport) {
        this.lookback = lookback;
        this.skip = skip;
        this.showResistance = showResistance;
        this.showSupport = showSupport;
    }

    @Override
    public String key() {
        return "rays:" + lookback + ":" + skip + ":" + showResistance + ":" + showSupport;
    }

    @Override
    public Result compute(IndicatorEngine engine) {
        List<Candle> candles = engine.getCandles();
        if (candles == null || candles.size() < lookback) return null;

        RotatingRays.RaySet resistance = showResistance
                ? RotatingRays.calculateResistanceRays(candles, lookback, skip)
                : null;
        RotatingRays.RaySet support = showSupport
                ? RotatingRays.calculateSupportRays(candles, lookback, skip)
                : null;

        return new Result(resistance, support);
    }

    public record Result(RotatingRays.RaySet resistance, RotatingRays.RaySet support) {}
}
