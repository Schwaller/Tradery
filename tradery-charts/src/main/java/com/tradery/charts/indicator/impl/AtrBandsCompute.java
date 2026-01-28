package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * ATR Bands: Close +/- ATR * multiplier.
 */
public class AtrBandsCompute extends IndicatorCompute<AtrBandsCompute.Result> {

    private final int period;
    private final double multiplier;

    public AtrBandsCompute(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public String key() {
        return "atrbands:" + period + ":" + multiplier;
    }

    @Override
    public Result compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        double[] atr = engine.getATR(period);
        if (atr == null) return null;

        int len = candles.size();
        double[] upper = new double[len];
        double[] lower = new double[len];

        for (int i = 0; i < len && i < atr.length; i++) {
            if (!Double.isNaN(atr[i])) {
                double close = candles.get(i).close();
                double offset = atr[i] * multiplier;
                upper[i] = close + offset;
                lower[i] = close - offset;
            } else {
                upper[i] = Double.NaN;
                lower[i] = Double.NaN;
            }
        }
        return new Result(upper, lower, period);
    }

    public record Result(double[] upper, double[] lower, int warmup) {}
}
