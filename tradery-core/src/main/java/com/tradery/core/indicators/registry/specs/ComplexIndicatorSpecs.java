package com.tradery.core.indicators.registry.specs;

import com.tradery.core.indicators.Indicators;
import com.tradery.core.indicators.Indicators.IchimokuResult;
import com.tradery.core.indicators.RotatingRays;
import com.tradery.core.indicators.RotatingRays.RaySet;
import com.tradery.core.indicators.Supertrend;
import com.tradery.core.indicators.registry.DataDependency;
import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.indicators.registry.IndicatorRegistry;
import com.tradery.core.indicators.registry.IndicatorSpec;

import java.util.Set;

/**
 * Complex indicators: Ichimoku, Supertrend, Rotating Rays.
 *
 * NOTE: Most indicators migrated to Indicator interface - see:
 * - Ichimoku.INSTANCE
 * - Supertrend.INSTANCE
 * - RotatingRays.RESISTANCE_RAYS, RotatingRays.SUPPORT_RAYS
 */
public final class ComplexIndicatorSpecs {

    private ComplexIndicatorSpecs() {}

    public static void registerAll(IndicatorRegistry registry) {
        // All indicators migrated to new Indicator interface
        // Kept for reference - can be deleted when migration is complete
    }

    // ========== ICHIMOKU ==========
    public static final IndicatorSpec<IchimokuResult> ICHIMOKU = new IndicatorSpec<>() {
        @Override
        public String id() { return "ICHIMOKU"; }

        @Override
        public String cacheKey(Object... params) {
            if (params.length >= 4) {
                return "ichimoku:" + params[0] + ":" + params[1] + ":" + params[2] + ":" + params[3];
            }
            return "ichimoku:default";
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public IchimokuResult compute(IndicatorContext ctx, Object... params) {
            if (params.length >= 4) {
                int conversionPeriod = (int) params[0];
                int basePeriod = (int) params[1];
                int spanBPeriod = (int) params[2];
                int displacement = (int) params[3];
                return Indicators.ichimoku(ctx.candles(), conversionPeriod, basePeriod, spanBPeriod, displacement);
            }
            return Indicators.ichimoku(ctx.candles());
        }

        @Override
        public double valueAt(IchimokuResult result, int barIndex) {
            // Default to tenkan - use component accessors for others
            if (result == null || barIndex < 0 || barIndex >= result.tenkanSen().length) {
                return Double.NaN;
            }
            return result.tenkanSen()[barIndex];
        }

        @Override
        public Class<IchimokuResult> resultType() {
            return IchimokuResult.class;
        }
    };

    // ========== SUPERTREND ==========
    public static final IndicatorSpec<Supertrend.Result> SUPERTREND = new IndicatorSpec<>() {
        @Override
        public String id() { return "SUPERTREND"; }

        @Override
        public String cacheKey(Object... params) {
            return "supertrend:" + params[0] + ":" + params[1];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public Supertrend.Result compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            double multiplier = (double) params[1];
            return Supertrend.calculate(ctx.candles(), period, multiplier);
        }

        @Override
        public double valueAt(Supertrend.Result result, int barIndex) {
            // Default to trend direction - use component accessors for bands
            if (result == null || barIndex < 0 || barIndex >= result.trend().length) {
                return Double.NaN;
            }
            return result.trend()[barIndex];
        }

        @Override
        public Class<Supertrend.Result> resultType() {
            return Supertrend.Result.class;
        }
    };

    // ========== RESISTANCE_RAYS ==========
    public static final IndicatorSpec<RaySet> RESISTANCE_RAYS = new IndicatorSpec<>() {
        @Override
        public String id() { return "RESISTANCE_RAYS"; }

        @Override
        public String cacheKey(Object... params) {
            return "resistance_rays:" + params[0] + ":" + params[1];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public RaySet compute(IndicatorContext ctx, Object... params) {
            int lookback = (int) params[0];
            int skip = (int) params[1];
            return RotatingRays.calculateResistanceRays(ctx.candles(), lookback, skip);
        }

        @Override
        public double valueAt(RaySet result, int barIndex) {
            // RaySet doesn't have per-bar values - use through RayFunctions
            return Double.NaN;
        }

        @Override
        public Class<RaySet> resultType() {
            return RaySet.class;
        }
    };

    // ========== SUPPORT_RAYS ==========
    public static final IndicatorSpec<RaySet> SUPPORT_RAYS = new IndicatorSpec<>() {
        @Override
        public String id() { return "SUPPORT_RAYS"; }

        @Override
        public String cacheKey(Object... params) {
            return "support_rays:" + params[0] + ":" + params[1];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public RaySet compute(IndicatorContext ctx, Object... params) {
            int lookback = (int) params[0];
            int skip = (int) params[1];
            return RotatingRays.calculateSupportRays(ctx.candles(), lookback, skip);
        }

        @Override
        public double valueAt(RaySet result, int barIndex) {
            // RaySet doesn't have per-bar values - use through RayFunctions
            return Double.NaN;
        }

        @Override
        public Class<RaySet> resultType() {
            return RaySet.class;
        }
    };
}
