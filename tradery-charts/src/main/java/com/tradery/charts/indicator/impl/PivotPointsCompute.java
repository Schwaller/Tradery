package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Classic Pivot Points from previous day's HLC.
 * Computes P, R1, S1, R2, S2, R3, S3.
 */
public class PivotPointsCompute extends IndicatorCompute<PivotPointsCompute.Result> {

    @Override
    public String key() {
        return "pivotpoints";
    }

    @Override
    public Result compute(List<Candle> candles, String timeframe) {
        if (candles == null || candles.size() < 2) return null;

        int midPoint = candles.size() / 2;
        double prevHigh = Double.MIN_VALUE;
        double prevLow = Double.MAX_VALUE;
        double prevClose = candles.get(midPoint - 1).close();

        for (int i = 0; i < midPoint; i++) {
            Candle c = candles.get(i);
            prevHigh = Math.max(prevHigh, c.high());
            prevLow = Math.min(prevLow, c.low());
        }

        double pivot = (prevHigh + prevLow + prevClose) / 3.0;
        double range = prevHigh - prevLow;

        return new Result(
                pivot,
                2 * pivot - prevLow,      // R1
                2 * pivot - prevHigh,      // S1
                pivot + range,             // R2
                pivot - range,             // S2
                prevHigh + 2 * (pivot - prevLow),   // R3
                prevLow - 2 * (prevHigh - pivot),   // S3
                midPoint
        );
    }

    public record Result(double pivot, double r1, double s1, double r2, double s2,
                          double r3, double s3, int startIndex) {}
}
