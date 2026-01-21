package com.tradery.indicators.registry.specs;

import com.tradery.indicators.OrderflowIndicators;
import com.tradery.indicators.registry.DataDependency;
import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.indicators.registry.IndicatorRegistry;
import com.tradery.indicators.registry.IndicatorSpec;
import com.tradery.model.Candle;
import com.tradery.ui.charts.DailyVolumeProfileAnnotation;

import java.util.List;
import java.util.Set;

/**
 * Orderflow indicators that require aggTrades data.
 * Falls back to OHLCV approximation when aggTrades unavailable.
 */
public final class OrderflowIndicatorSpecs {

    private OrderflowIndicatorSpecs() {}

    public static void registerAll(IndicatorRegistry registry) {
        registry.registerAll(
            DELTA, CUM_DELTA,
            WHALE_DELTA, RETAIL_DELTA,
            BUY_VOLUME, SELL_VOLUME,
            TRADE_COUNT, LARGE_TRADE_COUNT,
            WHALE_BUY_VOLUME, WHALE_SELL_VOLUME,
            DAILY_VOLUME_PROFILE
        );
    }

    // ========== DELTA ==========
    public static final IndicatorSpec<double[]> DELTA = new IndicatorSpec<>() {
        @Override
        public String id() { return "DELTA"; }

        @Override
        public String cacheKey(Object... params) {
            return "delta";
        }

        @Override
        public Set<DataDependency> dependencies() {
            // Optional AGG_TRADES - falls back to OHLCV
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            if (ctx.hasAggTrades()) {
                return OrderflowIndicators.delta(ctx.aggTrades(), ctx.candles(), ctx.resolution());
            }
            // Fallback to OHLCV delta
            return computeOhlcvDelta(ctx.candles());
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

    // ========== CUM_DELTA ==========
    public static final IndicatorSpec<double[]> CUM_DELTA = new IndicatorSpec<>() {
        @Override
        public String id() { return "CUM_DELTA"; }

        @Override
        public String cacheKey(Object... params) {
            return "cum_delta";
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            if (ctx.hasAggTrades()) {
                return OrderflowIndicators.cumulativeDelta(ctx.aggTrades(), ctx.candles(), ctx.resolution());
            }
            // Fallback to OHLCV CVD
            return computeOhlcvCvd(ctx.candles());
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

    // ========== WHALE_DELTA ==========
    public static final IndicatorSpec<double[]> WHALE_DELTA = new IndicatorSpec<>() {
        @Override
        public String id() { return "WHALE_DELTA"; }

        @Override
        public String cacheKey(Object... params) {
            return "whale_delta:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES, DataDependency.AGG_TRADES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            double threshold = (double) params[0];
            return OrderflowIndicators.whaleDelta(ctx.aggTrades(), ctx.candles(), ctx.resolution(), threshold);
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

    // ========== RETAIL_DELTA ==========
    public static final IndicatorSpec<double[]> RETAIL_DELTA = new IndicatorSpec<>() {
        @Override
        public String id() { return "RETAIL_DELTA"; }

        @Override
        public String cacheKey(Object... params) {
            return "retail_delta:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES, DataDependency.AGG_TRADES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            double threshold = (double) params[0];
            return OrderflowIndicators.retailDelta(ctx.aggTrades(), ctx.candles(), ctx.resolution(), threshold);
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

    // ========== BUY_VOLUME ==========
    public static final IndicatorSpec<double[]> BUY_VOLUME = new IndicatorSpec<>() {
        @Override
        public String id() { return "BUY_VOLUME"; }

        @Override
        public String cacheKey(Object... params) {
            return "buy_volume";
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES, DataDependency.AGG_TRADES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            return OrderflowIndicators.buyVolume(ctx.aggTrades(), ctx.candles(), ctx.resolution());
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

    // ========== SELL_VOLUME ==========
    public static final IndicatorSpec<double[]> SELL_VOLUME = new IndicatorSpec<>() {
        @Override
        public String id() { return "SELL_VOLUME"; }

        @Override
        public String cacheKey(Object... params) {
            return "sell_volume";
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES, DataDependency.AGG_TRADES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            return OrderflowIndicators.sellVolume(ctx.aggTrades(), ctx.candles(), ctx.resolution());
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

    // ========== TRADE_COUNT ==========
    public static final IndicatorSpec<double[]> TRADE_COUNT = new IndicatorSpec<>() {
        @Override
        public String id() { return "TRADE_COUNT"; }

        @Override
        public String cacheKey(Object... params) {
            return "trade_count";
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            if (ctx.hasAggTrades()) {
                return OrderflowIndicators.tradeCount(ctx.aggTrades(), ctx.candles(), ctx.resolution());
            }
            // Fallback to OHLCV trade count
            return computeOhlcvTradeCount(ctx.candles());
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

    // ========== LARGE_TRADE_COUNT ==========
    public static final IndicatorSpec<double[]> LARGE_TRADE_COUNT = new IndicatorSpec<>() {
        @Override
        public String id() { return "LARGE_TRADE_COUNT"; }

        @Override
        public String cacheKey(Object... params) {
            return "large_trade_count:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES, DataDependency.AGG_TRADES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            double threshold = (double) params[0];
            return OrderflowIndicators.largeTradeCount(ctx.aggTrades(), ctx.candles(), ctx.resolution(), threshold);
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

    // ========== WHALE_BUY_VOLUME ==========
    public static final IndicatorSpec<double[]> WHALE_BUY_VOLUME = new IndicatorSpec<>() {
        @Override
        public String id() { return "WHALE_BUY_VOLUME"; }

        @Override
        public String cacheKey(Object... params) {
            return "whale_buy_volume:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES, DataDependency.AGG_TRADES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            double threshold = (double) params[0];
            return OrderflowIndicators.whaleBuyVolume(ctx.aggTrades(), ctx.candles(), ctx.resolution(), threshold);
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

    // ========== WHALE_SELL_VOLUME ==========
    public static final IndicatorSpec<double[]> WHALE_SELL_VOLUME = new IndicatorSpec<>() {
        @Override
        public String id() { return "WHALE_SELL_VOLUME"; }

        @Override
        public String cacheKey(Object... params) {
            return "whale_sell_volume:" + params[0];
        }

        @Override
        public Set<DataDependency> dependencies() {
            return Set.of(DataDependency.CANDLES, DataDependency.AGG_TRADES);
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            double threshold = (double) params[0];
            return OrderflowIndicators.whaleSellVolume(ctx.aggTrades(), ctx.candles(), ctx.resolution(), threshold);
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

    // ========== OHLCV Fallback Helpers ==========

    private static double[] computeOhlcvDelta(List<Candle> candles) {
        double[] result = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double buyVol = c.hasExtendedVolume() ? c.takerBuyVolume() : c.volume() / 2;
            double sellVol = c.volume() - buyVol;
            result[i] = buyVol - sellVol;
        }
        return result;
    }

    private static double[] computeOhlcvCvd(List<Candle> candles) {
        double[] delta = computeOhlcvDelta(candles);
        double[] result = new double[candles.size()];
        double cumulative = 0;
        for (int i = 0; i < delta.length; i++) {
            cumulative += delta[i];
            result[i] = cumulative;
        }
        return result;
    }

    private static double[] computeOhlcvTradeCount(List<Candle> candles) {
        double[] result = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            result[i] = c.hasTradeCount() ? c.tradeCount() : 0;
        }
        return result;
    }

    // ========== DAILY_VOLUME_PROFILE ==========
    /**
     * Daily volume profile computed from aggTrades (preferred) or candles (fallback).
     * Returns List<DayProfile> for rendering by DailyVolumeProfileAnnotation.
     * Params: numBins (int), valueAreaPct (double), maxDays (int)
     */
    public static final IndicatorSpec<List<DailyVolumeProfileAnnotation.DayProfile>> DAILY_VOLUME_PROFILE =
        new IndicatorSpec<>() {

        @Override
        public String id() { return "DAILY_VOLUME_PROFILE"; }

        @Override
        public String cacheKey(Object... params) {
            int numBins = params.length > 0 ? (int) params[0] : 24;
            double valueAreaPct = params.length > 1 ? (double) params[1] : 70.0;
            int maxDays = params.length > 2 ? (int) params[2] : 30;
            return "daily_volume_profile:" + numBins + ":" + valueAreaPct + ":" + maxDays;
        }

        @Override
        public Set<DataDependency> dependencies() {
            // Prefers AGG_TRADES but can fall back to CANDLES
            return Set.of(DataDependency.CANDLES);
        }

        @Override
        public List<DailyVolumeProfileAnnotation.DayProfile> compute(IndicatorContext ctx, Object... params) {
            int numBins = params.length > 0 ? (int) params[0] : 24;
            double valueAreaPct = params.length > 1 ? (double) params[1] : 70.0;
            int maxDays = params.length > 2 ? (int) params[2] : 30;

            if (ctx.hasAggTrades()) {
                return DailyVolumeProfileAnnotation.calculateDayProfilesFromAggTrades(
                    ctx.aggTrades(), numBins, valueAreaPct, maxDays);
            }
            // Fall back to candle-based calculation
            return DailyVolumeProfileAnnotation.calculateDayProfiles(
                ctx.candles(), numBins, valueAreaPct, maxDays);
        }

        @Override
        public double valueAt(List<DailyVolumeProfileAnnotation.DayProfile> result, int barIndex) {
            // Not applicable for this indicator type
            return Double.NaN;
        }

        @Override
        public Class<List<DailyVolumeProfileAnnotation.DayProfile>> resultType() {
            @SuppressWarnings("unchecked")
            Class<List<DailyVolumeProfileAnnotation.DayProfile>> clazz =
                (Class<List<DailyVolumeProfileAnnotation.DayProfile>>) (Class<?>) List.class;
            return clazz;
        }
    };
}
