package com.tradery.core.indicators;

import com.tradery.core.indicators.registry.DataDependency;

import java.util.Set;

/**
 * Base class for simple indicators that return double[].
 * Handles common boilerplate - subclasses just implement compute().
 */
public abstract class SimpleIndicator implements Indicator<double[]> {

    @Override
    public int warmupBars(Object... params) {
        return 0; // Most simple indicators don't need warmup
    }

    @Override
    public double valueAt(double[] result, int barIndex) {
        if (result == null || barIndex < 0 || barIndex >= result.length) {
            return Double.NaN;
        }
        return result[barIndex];
    }

    @Override
    public Class<double[]> resultType() {
        return double[].class;
    }

    @Override
    public Set<DataDependency> dependencies() {
        return Set.of(DataDependency.CANDLES);
    }
}
