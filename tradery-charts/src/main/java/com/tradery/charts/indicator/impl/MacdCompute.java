package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.model.Candle;

import java.util.List;

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
    public Indicators.MACDResult compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        return engine.getMACD(fast, slow, signal);
    }
}
