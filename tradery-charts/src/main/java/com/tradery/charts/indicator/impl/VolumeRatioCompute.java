package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

public class VolumeRatioCompute extends IndicatorCompute<VolumeRatioCompute.Result> {

    @Override
    public String key() {
        return "volumeratio";
    }

    @Override
    public Result compute(IndicatorEngine engine) {
        return new Result(engine.getBuyVolume(), engine.getSellVolume());
    }

    public record Result(double[] buyVolume, double[] sellVolume) {}
}
