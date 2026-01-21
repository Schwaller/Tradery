package com.tradery.indicators.registry.specs;

import com.tradery.indicators.Indicators;
import com.tradery.indicators.Indicators.BollingerResult;
import com.tradery.indicators.Indicators.MACDResult;
import com.tradery.indicators.Indicators.StochasticResult;
import com.tradery.indicators.Indicators.VolumeProfileResult;
import com.tradery.indicators.registry.DataDependency;
import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.indicators.registry.IndicatorRegistry;
import com.tradery.indicators.registry.IndicatorSpec;

import java.util.Set;

/**
 * Composite indicators that return multi-array result types.
 * These need special handling for component access.
 */
public final class CompositeIndicatorSpecs {

    private CompositeIndicatorSpecs() {}

    public static void registerAll(IndicatorRegistry registry) {
        registry.registerAll(
            MACD, BBANDS, ADX, PLUS_DI, MINUS_DI, STOCHASTIC,
            RANGE_POSITION, VOLUME_PROFILE
        );
    }

    // ========== MACD ==========
    public static final IndicatorSpec<MACDResult> MACD = new IndicatorSpec<>() {
        @Override
        public String id() { return "MACD"; }

        @Override
        public String cacheKey(Object... params) {
            return "macd:" + params[0] + ":" + params[1] + ":" + params[2];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public MACDResult compute(IndicatorContext ctx, Object... params) {
            int fast = (int) params[0];
            int slow = (int) params[1];
            int signal = (int) params[2];
            return Indicators.macd(ctx.candles(), fast, slow, signal);
        }

        @Override
        public double valueAt(MACDResult result, int barIndex) {
            // Default to line - use component accessors for signal/histogram
            if (result == null || barIndex < 0 || barIndex >= result.line().length) {
                return Double.NaN;
            }
            return result.line()[barIndex];
        }

        @Override
        public Class<MACDResult> resultType() {
            return MACDResult.class;
        }
    };

    // ========== BBANDS ==========
    public static final IndicatorSpec<BollingerResult> BBANDS = new IndicatorSpec<>() {
        @Override
        public String id() { return "BBANDS"; }

        @Override
        public String cacheKey(Object... params) {
            return "bbands:" + params[0] + ":" + params[1];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public BollingerResult compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            double stdDev = (double) params[1];
            return Indicators.bollingerBands(ctx.candles(), period, stdDev);
        }

        @Override
        public double valueAt(BollingerResult result, int barIndex) {
            // Default to middle - use component accessors for upper/lower
            if (result == null || barIndex < 0 || barIndex >= result.middle().length) {
                return Double.NaN;
            }
            return result.middle()[barIndex];
        }

        @Override
        public Class<BollingerResult> resultType() {
            return BollingerResult.class;
        }
    };

    // ========== ADX (returns just adx[] for chart compatibility) ==========
    public static final IndicatorSpec<double[]> ADX = new IndicatorSpec<>() {
        @Override
        public String id() { return "ADX"; }

        @Override
        public String cacheKey(Object... params) {
            return "adx:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.adx(ctx.candles(), period).adx();
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
    };

    // ========== PLUS_DI ==========
    public static final IndicatorSpec<double[]> PLUS_DI = new IndicatorSpec<>() {
        @Override
        public String id() { return "PLUS_DI"; }

        @Override
        public String cacheKey(Object... params) {
            return "plus_di:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.adx(ctx.candles(), period).plusDI();
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
    };

    // ========== MINUS_DI ==========
    public static final IndicatorSpec<double[]> MINUS_DI = new IndicatorSpec<>() {
        @Override
        public String id() { return "MINUS_DI"; }

        @Override
        public String cacheKey(Object... params) {
            return "minus_di:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.adx(ctx.candles(), period).minusDI();
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
    };

    // ========== STOCHASTIC ==========
    public static final IndicatorSpec<StochasticResult> STOCHASTIC = new IndicatorSpec<>() {
        @Override
        public String id() { return "STOCHASTIC"; }

        @Override
        public String cacheKey(Object... params) {
            return "stochastic:" + params[0] + ":" + params[1];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public StochasticResult compute(IndicatorContext ctx, Object... params) {
            int kPeriod = (int) params[0];
            int dPeriod = (int) params[1];
            return Indicators.stochastic(ctx.candles(), kPeriod, dPeriod);
        }

        @Override
        public double valueAt(StochasticResult result, int barIndex) {
            // Default to K - use component accessor for D
            if (result == null || barIndex < 0 || barIndex >= result.k().length) {
                return Double.NaN;
            }
            return result.k()[barIndex];
        }

        @Override
        public Class<StochasticResult> resultType() {
            return StochasticResult.class;
        }
    };

    // ========== RANGE_POSITION ==========
    public static final IndicatorSpec<double[]> RANGE_POSITION = new IndicatorSpec<>() {
        @Override
        public String id() { return "RANGE_POSITION"; }

        @Override
        public String cacheKey(Object... params) {
            return "range_position:" + params[0] + ":" + params[1];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            int skip = params.length > 1 ? (int) params[1] : 0;
            return Indicators.rangePosition(ctx.candles(), period, skip);
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
    };

    // ========== VOLUME_PROFILE ==========
    public static final IndicatorSpec<VolumeProfileResult> VOLUME_PROFILE = new IndicatorSpec<>() {
        @Override
        public String id() { return "VOLUME_PROFILE"; }

        @Override
        public String cacheKey(Object... params) {
            return "volume_profile:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public VolumeProfileResult compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.volumeProfile(ctx.candles(), period, 24, 70.0);
        }

        @Override
        public double valueAt(VolumeProfileResult result, int barIndex) {
            // Return POC as default
            if (result == null) return Double.NaN;
            return result.poc();
        }

        @Override
        public Class<VolumeProfileResult> resultType() {
            return VolumeProfileResult.class;
        }
    };
}
