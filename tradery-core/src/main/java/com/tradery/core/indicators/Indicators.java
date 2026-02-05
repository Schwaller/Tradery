package com.tradery.core.indicators;

import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Facade for technical indicator calculations.
 * Delegates to individual indicator classes for cleaner organization.
 * All methods return arrays where index corresponds to bar index.
 * Invalid values (warmup period) are Double.NaN.
 */
public final class Indicators {

    private Indicators() {} // Utility class

    // ========== Type aliases for backward compatibility ==========

    /** MACD result containing line, signal, and histogram */
    public record MACDResult(double[] line, double[] signal, double[] histogram) {}

    /** Bollinger Bands result */
    public record BollingerResult(double[] upper, double[] middle, double[] lower, double[] width) {}

    /** ADX result containing ADX, +DI, and -DI */
    public record ADXResult(double[] adx, double[] plusDI, double[] minusDI) {}

    /** Stochastic result containing %K and %D */
    public record StochasticResult(double[] k, double[] d) {}

    /** Volume Profile result */
    public record VolumeProfileResult(
        double poc, double vah, double val,
        double[] priceLevels, double[] volumes
    ) {}

    /** Ichimoku Cloud result containing all 5 components */
    public record IchimokuResult(
        double[] tenkanSen,    // Conversion Line
        double[] kijunSen,     // Base Line
        double[] senkouSpanA,  // Leading Span A
        double[] senkouSpanB,  // Leading Span B
        double[] chikouSpan    // Lagging Span
    ) {}

    // ========== SMA ==========

    public static double[] sma(List<Candle> candles, int period) {
        return SMA.calculate(candles, period);
    }

    public static double smaAt(List<Candle> candles, int period, int barIndex) {
        return SMA.calculateAt(candles, period, barIndex);
    }

    // ========== EMA ==========

    public static double[] ema(List<Candle> candles, int period) {
        return EMA.calculate(candles, period);
    }

    public static double emaAt(List<Candle> candles, int period, int barIndex) {
        return EMA.calculateAt(candles, period, barIndex);
    }

    // ========== RSI ==========

    public static double[] rsi(List<Candle> candles, int period) {
        return RSI.calculate(candles, period);
    }

    public static double rsiAt(List<Candle> candles, int period, int barIndex) {
        return RSI.calculateAt(candles, period, barIndex);
    }

    // ========== MACD ==========

    public static MACDResult macd(List<Candle> candles, int fastPeriod, int slowPeriod, int signalPeriod) {
        MACD.Result result = MACD.calculate(candles, fastPeriod, slowPeriod, signalPeriod);
        return new MACDResult(result.line(), result.signal(), result.histogram());
    }

    // ========== Bollinger Bands ==========

    public static BollingerResult bollingerBands(List<Candle> candles, int period, double stdDevMultiplier) {
        BollingerBands.Result result = BollingerBands.calculate(candles, period, stdDevMultiplier);
        return new BollingerResult(result.upper(), result.middle(), result.lower(), result.width());
    }

    public static double bbandsUpperAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        return BollingerBands.upperAt(candles, period, stdDevMultiplier, barIndex);
    }

    public static double bbandsMiddleAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        return BollingerBands.middleAt(candles, period, stdDevMultiplier, barIndex);
    }

    public static double bbandsLowerAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        return BollingerBands.lowerAt(candles, period, stdDevMultiplier, barIndex);
    }

    // ========== ATR ==========

    public static double[] atr(List<Candle> candles, int period) {
        return ATR.calculate(candles, period);
    }

    public static double atrAt(List<Candle> candles, int period, int barIndex) {
        return ATR.calculateAt(candles, period, barIndex);
    }

    // ========== Range Functions ==========

    public static double[] highOf(List<Candle> candles, int period) {
        return RangeFunctions.highOf(candles, period);
    }

    public static double highOfAt(List<Candle> candles, int period, int barIndex) {
        return RangeFunctions.highOfAt(candles, period, barIndex);
    }

    public static double[] lowOf(List<Candle> candles, int period) {
        return RangeFunctions.lowOf(candles, period);
    }

    public static double lowOfAt(List<Candle> candles, int period, int barIndex) {
        return RangeFunctions.lowOfAt(candles, period, barIndex);
    }

    // ========== Volume ==========

    public static double[] avgVolume(List<Candle> candles, int period) {
        return RangeFunctions.avgVolume(candles, period);
    }

    public static double avgVolumeAt(List<Candle> candles, int period, int barIndex) {
        return RangeFunctions.avgVolumeAt(candles, period, barIndex);
    }

    // ========== ADX / DMI ==========

    public static ADXResult adx(List<Candle> candles, int period) {
        ADX.Result result = ADX.calculate(candles, period);
        return new ADXResult(result.adx(), result.plusDI(), result.minusDI());
    }

    public static double adxAt(List<Candle> candles, int period, int barIndex) {
        return ADX.adxAt(candles, period, barIndex);
    }

    public static double plusDIAt(List<Candle> candles, int period, int barIndex) {
        return ADX.plusDIAt(candles, period, barIndex);
    }

    public static double minusDIAt(List<Candle> candles, int period, int barIndex) {
        return ADX.minusDIAt(candles, period, barIndex);
    }

    // ========== VWAP ==========

    public static double[] vwap(List<Candle> candles) {
        return VWAP.calculate(candles);
    }

    public static double vwapAt(List<Candle> candles, int barIndex) {
        return VWAP.calculateAt(candles, barIndex);
    }

    // ========== Volume Profile / POC / VAH / VAL ==========

    public static VolumeProfileResult volumeProfile(List<Candle> candles, int period, int numBins, double valueAreaPct) {
        VolumeProfile.Result result = VolumeProfile.calculate(candles, period, numBins, valueAreaPct);
        return new VolumeProfileResult(result.poc(), result.vah(), result.val(), result.priceLevels(), result.volumes());
    }

    public static double pocAt(List<Candle> candles, int period, int barIndex) {
        return VolumeProfile.pocAt(candles, period, barIndex);
    }

    public static double vahAt(List<Candle> candles, int period, int barIndex) {
        return VolumeProfile.vahAt(candles, period, barIndex);
    }

    public static double valAt(List<Candle> candles, int period, int barIndex) {
        return VolumeProfile.valAt(candles, period, barIndex);
    }

    // ========== Stochastic ==========

    public static StochasticResult stochastic(List<Candle> candles, int kPeriod, int dPeriod) {
        Stochastic.Result result = Stochastic.calculate(candles, kPeriod, dPeriod);
        return new StochasticResult(result.k(), result.d());
    }

    public static double stochasticKAt(List<Candle> candles, int period, int barIndex) {
        return Stochastic.calculateKAt(candles, period, barIndex);
    }

    public static double stochasticDAt(List<Candle> candles, int kPeriod, int dPeriod, int barIndex) {
        return Stochastic.calculateDAt(candles, kPeriod, dPeriod, barIndex);
    }

    // ========== Range Position ==========

    public static double[] rangePosition(List<Candle> candles, int period, int skip) {
        return RangePosition.calculate(candles, period, skip);
    }

    public static double rangePositionAt(List<Candle> candles, int period, int skip, int barIndex) {
        return RangePosition.calculateAt(candles, period, skip, barIndex);
    }

    public static double rangeHighAt(List<Candle> candles, int period, int skip, int barIndex) {
        return RangePosition.getRangeHighAt(candles, period, skip, barIndex);
    }

    public static double rangeLowAt(List<Candle> candles, int period, int skip, int barIndex) {
        return RangePosition.getRangeLowAt(candles, period, skip, barIndex);
    }

    // ========== Ichimoku Cloud ==========

    public static IchimokuResult ichimoku(List<Candle> candles) {
        Ichimoku.Result result = Ichimoku.calculate(candles);
        return new IchimokuResult(result.tenkanSen(), result.kijunSen(),
                                  result.senkouSpanA(), result.senkouSpanB(), result.chikouSpan());
    }

    public static IchimokuResult ichimoku(List<Candle> candles, int conversionPeriod, int basePeriod,
                                          int spanBPeriod, int displacement) {
        Ichimoku.Result result = Ichimoku.calculate(candles, conversionPeriod, basePeriod,
                                                    spanBPeriod, displacement);
        return new IchimokuResult(result.tenkanSen(), result.kijunSen(),
                                  result.senkouSpanA(), result.senkouSpanB(), result.chikouSpan());
    }

    public static double ichimokuTenkanAt(List<Candle> candles, int conversionPeriod, int barIndex) {
        return Ichimoku.tenkanSenAt(candles, conversionPeriod, barIndex);
    }

    public static double ichimokuKijunAt(List<Candle> candles, int basePeriod, int barIndex) {
        return Ichimoku.kijunSenAt(candles, basePeriod, barIndex);
    }

    public static double ichimokuSenkouAAt(List<Candle> candles, int conversionPeriod, int basePeriod,
                                            int displacement, int barIndex) {
        return Ichimoku.senkouSpanAAt(candles, conversionPeriod, basePeriod, displacement, barIndex);
    }

    public static double ichimokuSenkouBAt(List<Candle> candles, int spanBPeriod, int displacement, int barIndex) {
        return Ichimoku.senkouSpanBAt(candles, spanBPeriod, displacement, barIndex);
    }

    public static double ichimokuChikouAt(List<Candle> candles, int displacement, int barIndex) {
        return Ichimoku.chikouSpanAt(candles, displacement, barIndex);
    }
}
