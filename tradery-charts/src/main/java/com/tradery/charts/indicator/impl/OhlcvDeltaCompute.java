package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

public class OhlcvDeltaCompute extends IndicatorCompute<OhlcvDeltaCompute.Result> {

    @Override
    public String key() {
        return "ohlcvdelta";
    }

    @Override
    public Result compute(IndicatorEngine engine) {
        return new Result(engine.getOhlcvDelta(), engine.getOhlcvCvd());
    }

    public record Result(double[] delta, double[] cvd) {}
}
