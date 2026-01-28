package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.RotatingRays;
import com.tradery.core.indicators.RotatingRays.RaySet;
import com.tradery.core.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes rays at multiple bar positions for historic visualization.
 * Shows how rays evolved over time by computing snapshots at regular intervals.
 */
public class HistoricRaysCompute extends IndicatorCompute<HistoricRaysCompute.Result> {

    private final int skip;
    private final int interval;

    public HistoricRaysCompute(int skip, int interval) {
        this.skip = skip;
        this.interval = Math.max(1, interval);
    }

    @Override
    public String key() {
        return "historic-rays:" + skip + ":" + interval;
    }

    @Override
    public Result compute(IndicatorEngine engine) {
        List<Candle> candles = engine.getCandles();
        if (candles == null || candles.size() < 20) return null;

        List<Entry> entries = new ArrayList<>();
        int startBar = Math.max(20, interval);
        int lastBar = candles.size() - 1;

        for (int barIndex = startBar; barIndex < lastBar; barIndex += Math.max(1, interval)) {
            List<Candle> candlesUpToBar = candles.subList(0, barIndex + 1);
            RaySet resistance = RotatingRays.calculateResistanceRays(candlesUpToBar, 0, skip);
            RaySet support = RotatingRays.calculateSupportRays(candlesUpToBar, 0, skip);
            entries.add(new Entry(barIndex, resistance, support));
        }

        return new Result(entries);
    }

    public record Result(List<Entry> entries) {}

    public record Entry(int barIndex, RaySet resistance, RaySet support) {}
}
