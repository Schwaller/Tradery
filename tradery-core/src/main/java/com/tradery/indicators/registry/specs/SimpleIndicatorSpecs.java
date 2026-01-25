package com.tradery.indicators.registry.specs;

import com.tradery.indicators.Indicators;
import com.tradery.indicators.registry.DataDependency;
import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.indicators.registry.IndicatorRegistry;
import com.tradery.indicators.registry.SimpleIndicatorSpec;

/**
 * Simple indicators that return double[] with a single period parameter.
 *
 * NOTE: RSI, SMA, EMA, ATR migrated to Indicator interface.
 * Only HIGH_OF, LOW_OF, AVG_VOLUME, VWAP remain here.
 */
public final class SimpleIndicatorSpecs {

    private SimpleIndicatorSpecs() {}

    public static void registerAll(IndicatorRegistry registry) {
        // Only non-migrated indicators
        registry.registerAll(
            HIGH_OF, LOW_OF, AVG_VOLUME, VWAP
        );
    }

    // ========== RSI ==========
    public static final SimpleIndicatorSpec RSI = new SimpleIndicatorSpec("RSI") {
        @Override
        public String cacheKey(Object... params) {
            return "rsi:" + params[0];
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.rsi(ctx.candles(), period);
        }
    };

    // ========== SMA ==========
    public static final SimpleIndicatorSpec SMA = new SimpleIndicatorSpec("SMA") {
        @Override
        public String cacheKey(Object... params) {
            return "sma:" + params[0];
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.sma(ctx.candles(), period);
        }
    };

    // ========== EMA ==========
    public static final SimpleIndicatorSpec EMA = new SimpleIndicatorSpec("EMA") {
        @Override
        public String cacheKey(Object... params) {
            return "ema:" + params[0];
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.ema(ctx.candles(), period);
        }
    };

    // ========== ATR ==========
    public static final SimpleIndicatorSpec ATR = new SimpleIndicatorSpec("ATR") {
        @Override
        public String cacheKey(Object... params) {
            return "atr:" + params[0];
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.atr(ctx.candles(), period);
        }
    };

    // ========== HIGH_OF ==========
    public static final SimpleIndicatorSpec HIGH_OF = new SimpleIndicatorSpec("HIGH_OF") {
        @Override
        public String cacheKey(Object... params) {
            return "high_of:" + params[0];
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.highOf(ctx.candles(), period);
        }
    };

    // ========== LOW_OF ==========
    public static final SimpleIndicatorSpec LOW_OF = new SimpleIndicatorSpec("LOW_OF") {
        @Override
        public String cacheKey(Object... params) {
            return "low_of:" + params[0];
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.lowOf(ctx.candles(), period);
        }
    };

    // ========== AVG_VOLUME ==========
    public static final SimpleIndicatorSpec AVG_VOLUME = new SimpleIndicatorSpec("AVG_VOLUME") {
        @Override
        public String cacheKey(Object... params) {
            return "avg_volume:" + params[0];
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            int period = (int) params[0];
            return Indicators.avgVolume(ctx.candles(), period);
        }
    };

    // ========== VWAP ==========
    public static final SimpleIndicatorSpec VWAP = new SimpleIndicatorSpec("VWAP") {
        @Override
        public String cacheKey(Object... params) {
            return "vwap";
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            return Indicators.vwap(ctx.candles());
        }
    };
}
