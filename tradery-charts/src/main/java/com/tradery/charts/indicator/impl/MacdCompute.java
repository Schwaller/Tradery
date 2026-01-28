package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;

public class MacdCompute extends IndicatorCompute<Indicators.MACDResult> {

    private final int fast;
    private final int slow;
    private final int signal;

    public MacdCompute(int fast, int slow, int signal) {
        this.fast = fast;
        this.slow = slow;
        this.signal = signal;
    }

    @Override
    public String key() {
        return "macd:" + fast + ":" + slow + ":" + signal;
    }

    @Override
    public Indicators.MACDResult compute(IndicatorEngine engine) {
        return engine.getMACD(fast, slow, signal);
    }
}
