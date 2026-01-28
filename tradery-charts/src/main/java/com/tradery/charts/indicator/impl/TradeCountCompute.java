package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;

public class TradeCountCompute extends IndicatorCompute<double[]> {

    @Override
    public String key() {
        return "tradecount";
    }

    @Override
    public double[] compute(IndicatorEngine engine) {
        return engine.getTradeCount();
    }
}
