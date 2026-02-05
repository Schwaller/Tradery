package com.tradery.core.indicators.registry.specs;

import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.indicators.registry.IndicatorRegistry;
import com.tradery.core.indicators.registry.SimpleIndicatorSpec;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * OHLCV volume indicators - use extended kline data (instant, no aggTrades needed).
 *
 * NOTE: All indicators migrated to Indicator interface - see OhlcvVolumeIndicators class.
 */
public final class OhlcvVolumeIndicatorSpecs {

    private OhlcvVolumeIndicatorSpecs() {}

    public static void registerAll(IndicatorRegistry registry) {
        // All indicators migrated to new Indicator interface
        // Kept for reference - can be deleted when migration is complete
    }

    // ========== QUOTE_VOLUME ==========
    public static final SimpleIndicatorSpec QUOTE_VOLUME = new SimpleIndicatorSpec("QUOTE_VOLUME") {
        @Override
        public String cacheKey(Object... params) {
            return "quote_volume";
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            List<Candle> candles = ctx.candles();
            double[] result = new double[candles.size()];
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                result[i] = c.quoteVolume() >= 0 ? c.quoteVolume() : c.volume() * c.close();
            }
            return result;
        }
    };

    // ========== TAKER_BUY_VOLUME ==========
    public static final SimpleIndicatorSpec TAKER_BUY_VOLUME = new SimpleIndicatorSpec("TAKER_BUY_VOLUME") {
        @Override
        public String cacheKey(Object... params) {
            return "taker_buy_volume";
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            List<Candle> candles = ctx.candles();
            double[] result = new double[candles.size()];
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                result[i] = c.hasExtendedVolume() ? c.takerBuyVolume() : c.volume() / 2;
            }
            return result;
        }
    };

    // ========== TAKER_SELL_VOLUME ==========
    public static final SimpleIndicatorSpec TAKER_SELL_VOLUME = new SimpleIndicatorSpec("TAKER_SELL_VOLUME") {
        @Override
        public String cacheKey(Object... params) {
            return "taker_sell_volume";
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            List<Candle> candles = ctx.candles();
            double[] result = new double[candles.size()];
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                double buyVol = c.hasExtendedVolume() ? c.takerBuyVolume() : c.volume() / 2;
                result[i] = c.volume() - buyVol;
            }
            return result;
        }
    };

    // ========== OHLCV_DELTA ==========
    public static final SimpleIndicatorSpec OHLCV_DELTA = new SimpleIndicatorSpec("OHLCV_DELTA") {
        @Override
        public String cacheKey(Object... params) {
            return "ohlcv_delta";
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            List<Candle> candles = ctx.candles();
            double[] result = new double[candles.size()];
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                double buyVol = c.hasExtendedVolume() ? c.takerBuyVolume() : c.volume() / 2;
                double sellVol = c.volume() - buyVol;
                result[i] = buyVol - sellVol;
            }
            return result;
        }
    };

    // ========== OHLCV_CVD ==========
    public static final SimpleIndicatorSpec OHLCV_CVD = new SimpleIndicatorSpec("OHLCV_CVD") {
        @Override
        public String cacheKey(Object... params) {
            return "ohlcv_cvd";
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            List<Candle> candles = ctx.candles();
            double[] result = new double[candles.size()];
            double cumulative = 0;
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                double buyVol = c.hasExtendedVolume() ? c.takerBuyVolume() : c.volume() / 2;
                double sellVol = c.volume() - buyVol;
                cumulative += (buyVol - sellVol);
                result[i] = cumulative;
            }
            return result;
        }
    };

    // ========== BUY_RATIO ==========
    public static final SimpleIndicatorSpec BUY_RATIO = new SimpleIndicatorSpec("BUY_RATIO") {
        @Override
        public String cacheKey(Object... params) {
            return "buy_ratio";
        }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            List<Candle> candles = ctx.candles();
            double[] result = new double[candles.size()];
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                if (c.volume() == 0) {
                    result[i] = 0.5;
                } else {
                    double buyVol = c.hasExtendedVolume() ? c.takerBuyVolume() : c.volume() / 2;
                    result[i] = buyVol / c.volume();
                }
            }
            return result;
        }
    };
}
