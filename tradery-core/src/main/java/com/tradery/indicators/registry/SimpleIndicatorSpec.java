package com.tradery.indicators.registry;

import java.util.Set;

/**
 * Base class for simple indicators that return double[].
 * Handles common boilerplate - subclasses just implement compute().
 */
public abstract class SimpleIndicatorSpec implements IndicatorSpec<double[]> {

    private final String id;
    private final Set<DataDependency> dependencies;

    protected SimpleIndicatorSpec(String id, DataDependency... deps) {
        this.id = id;
        this.dependencies = deps.length > 0 ? Set.of(deps) : Set.of(DataDependency.CANDLES);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<DataDependency> dependencies() {
        return dependencies;
    }

    @Override
    public Class<double[]> resultType() {
        return double[].class;
    }

    @Override
    public double valueAt(double[] result, int barIndex) {
        if (result == null || barIndex < 0 || barIndex >= result.length) {
            return Double.NaN;
        }
        return result[barIndex];
    }
}
