package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

public class PocCompute extends IndicatorCompute<PocCompute.Result> {

    private final int period;
    private final boolean showValueArea;

    public PocCompute(int period, boolean showValueArea) {
        this.period = period;
        this.showValueArea = showValueArea;
    }

    @Override
    public String key() {
        return "poc:" + period + ":" + showValueArea;
    }

    @Override
    public Result compute(IndicatorEngine engine) {
        List<Candle> candles = engine.getCandles();
        if (candles == null || candles.isEmpty()) return null;

        int len = candles.size();
        double[] poc = new double[len];
        double[] vah = showValueArea ? new double[len] : null;
        double[] val = showValueArea ? new double[len] : null;

        for (int i = 0; i < len; i++) {
            if (i < period - 1) {
                poc[i] = Double.NaN;
                if (showValueArea) {
                    vah[i] = Double.NaN;
                    val[i] = Double.NaN;
                }
            } else {
                poc[i] = engine.getPOCAt(period, i);
                if (showValueArea) {
                    vah[i] = engine.getVAHAt(period, i);
                    val[i] = engine.getVALAt(period, i);
                }
            }
        }
        return new Result(poc, vah, val, period);
    }

    public int getPeriod() {
        return period;
    }

    public record Result(double[] poc, double[] vah, double[] val, int warmup) {}
}
