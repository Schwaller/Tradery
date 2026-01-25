package com.tradery.core.indicators;

import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * OHLCV volume indicators - use extended kline data (instant, no aggTrades needed).
 */
public final class OhlcvVolumeIndicators {

    private OhlcvVolumeIndicators() {} // Utility class

    // ===== Indicator instances for registry =====
    public static final Indicator<double[]> QUOTE_VOLUME = new QuoteVolumeIndicator();
    public static final Indicator<double[]> TAKER_BUY_VOLUME = new TakerBuyVolumeIndicator();
    public static final Indicator<double[]> TAKER_SELL_VOLUME = new TakerSellVolumeIndicator();
    public static final Indicator<double[]> OHLCV_DELTA = new OhlcvDeltaIndicator();
    public static final Indicator<double[]> OHLCV_CVD = new OhlcvCvdIndicator();
    public static final Indicator<double[]> BUY_RATIO = new BuyRatioIndicator();

    // ===== Indicator Implementations =====

    private static class QuoteVolumeIndicator extends SimpleIndicator {
        @Override public String id() { return "QUOTE_VOLUME"; }
        @Override public String name() { return "Quote Volume"; }
        @Override public String description() { return "Volume in quote currency (USD for BTCUSDT)"; }
        @Override public String cacheKey(Object... params) { return "quote_volume"; }

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
    }

    private static class TakerBuyVolumeIndicator extends SimpleIndicator {
        @Override public String id() { return "TAKER_BUY_VOLUME"; }
        @Override public String name() { return "Taker Buy Volume"; }
        @Override public String description() { return "Aggressive buy volume from klines"; }
        @Override public String cacheKey(Object... params) { return "taker_buy_volume"; }

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
    }

    private static class TakerSellVolumeIndicator extends SimpleIndicator {
        @Override public String id() { return "TAKER_SELL_VOLUME"; }
        @Override public String name() { return "Taker Sell Volume"; }
        @Override public String description() { return "Aggressive sell volume from klines"; }
        @Override public String cacheKey(Object... params) { return "taker_sell_volume"; }

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
    }

    private static class OhlcvDeltaIndicator extends SimpleIndicator {
        @Override public String id() { return "OHLCV_DELTA"; }
        @Override public String name() { return "OHLCV Delta"; }
        @Override public String description() { return "Buy - Sell volume from klines"; }
        @Override public String cacheKey(Object... params) { return "ohlcv_delta"; }

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
    }

    private static class OhlcvCvdIndicator extends SimpleIndicator {
        @Override public String id() { return "OHLCV_CVD"; }
        @Override public String name() { return "OHLCV CVD"; }
        @Override public String description() { return "Cumulative delta from klines"; }
        @Override public String cacheKey(Object... params) { return "ohlcv_cvd"; }

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
    }

    private static class BuyRatioIndicator extends SimpleIndicator {
        @Override public String id() { return "BUY_RATIO"; }
        @Override public String name() { return "Buy Ratio"; }
        @Override public String description() { return "Buy volume / total volume (0-1)"; }
        @Override public String cacheKey(Object... params) { return "buy_ratio"; }

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
    }
}
