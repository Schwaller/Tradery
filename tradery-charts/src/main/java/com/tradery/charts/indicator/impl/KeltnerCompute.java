package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Keltner Channel: EMA +/- ATR * multiplier.
 * Returns a KeltnerResult with upper, middle (EMA), and lower arrays.
 */
public class KeltnerCompute extends IndicatorCompute<KeltnerCompute.Result> {

    private final int emaPeriod;
    private final int atrPeriod;
    private final double multiplier;

    public KeltnerCompute(int emaPeriod, int atrPeriod, double multiplier) {
        this.emaPeriod = emaPeriod;
        this.atrPeriod = atrPeriod;
        this.multiplier = multiplier;
    }

    @Override
    public String key() {
        return "keltner:" + emaPeriod + ":" + atrPeriod + ":" + multiplier;
    }

    @Override
    public Result compute(List<Candle> candles, String timeframe) {
        var engine = new IndicatorEngine();
        engine.setCandles(candles, timeframe);
        double[] ema = engine.getEMA(emaPeriod);
        double[] atr = engine.getATR(atrPeriod);
        if (ema == null || atr == null) return null;

        int len = Math.min(ema.length, atr.length);
        double[] upper = new double[len];
        double[] middle = new double[len];
        double[] lower = new double[len];

        for (int i = 0; i < len; i++) {
            middle[i] = ema[i];
            if (!Double.isNaN(ema[i]) && !Double.isNaN(atr[i])) {
                double offset = atr[i] * multiplier;
                upper[i] = ema[i] + offset;
                lower[i] = ema[i] - offset;
            } else {
                upper[i] = Double.NaN;
                lower[i] = Double.NaN;
            }
        }
        return new Result(upper, middle, lower, Math.max(emaPeriod, atrPeriod));
    }

    public record Result(double[] upper, double[] middle, double[] lower, int warmup) {}
}
