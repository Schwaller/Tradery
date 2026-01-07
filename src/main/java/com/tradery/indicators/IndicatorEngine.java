package com.tradery.indicators;

import com.tradery.model.Candle;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indicator Engine - unified access to all indicators with caching.
 * Caches calculated indicator values for performance.
 */
public class IndicatorEngine {

    private List<Candle> candles;
    private String resolution = "1h";
    private final Map<String, Object> cache = new HashMap<>();

    /**
     * Initialize with candle data
     */
    public void setCandles(List<Candle> candles, String resolution) {
        this.candles = candles;
        this.resolution = resolution;
        clearCache();
    }

    /**
     * Clear the indicator cache
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Get candle count
     */
    public int getBarCount() {
        return candles != null ? candles.size() : 0;
    }

    /**
     * Get candle at index
     */
    public Candle getCandleAt(int barIndex) {
        if (candles == null || barIndex < 0 || barIndex >= candles.size()) {
            return null;
        }
        return candles.get(barIndex);
    }

    // ========== SMA ==========

    public double[] getSMA(int period) {
        String key = "sma:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.sma(candles, period));
        }
        return (double[]) cache.get(key);
    }

    public double getSMAAt(int period, int barIndex) {
        return Indicators.smaAt(candles, period, barIndex);
    }

    // ========== EMA ==========

    public double[] getEMA(int period) {
        String key = "ema:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.ema(candles, period));
        }
        return (double[]) cache.get(key);
    }

    public double getEMAAt(int period, int barIndex) {
        return Indicators.emaAt(candles, period, barIndex);
    }

    // ========== RSI ==========

    public double[] getRSI(int period) {
        String key = "rsi:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.rsi(candles, period));
        }
        return (double[]) cache.get(key);
    }

    public double getRSIAt(int period, int barIndex) {
        return Indicators.rsiAt(candles, period, barIndex);
    }

    // ========== MACD ==========

    public Indicators.MACDResult getMACD(int fast, int slow, int signal) {
        String key = "macd:" + fast + ":" + slow + ":" + signal;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.macd(candles, fast, slow, signal));
        }
        return (Indicators.MACDResult) cache.get(key);
    }

    public double getMACDLineAt(int fast, int slow, int signal, int barIndex) {
        Indicators.MACDResult result = getMACD(fast, slow, signal);
        return barIndex < result.line().length ? result.line()[barIndex] : Double.NaN;
    }

    public double getMACDSignalAt(int fast, int slow, int signal, int barIndex) {
        Indicators.MACDResult result = getMACD(fast, slow, signal);
        return barIndex < result.signal().length ? result.signal()[barIndex] : Double.NaN;
    }

    public double getMACDHistogramAt(int fast, int slow, int signal, int barIndex) {
        Indicators.MACDResult result = getMACD(fast, slow, signal);
        return barIndex < result.histogram().length ? result.histogram()[barIndex] : Double.NaN;
    }

    // ========== Bollinger Bands ==========

    public Indicators.BollingerResult getBollingerBands(int period, double stdDev) {
        String key = "bbands:" + period + ":" + stdDev;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.bollingerBands(candles, period, stdDev));
        }
        return (Indicators.BollingerResult) cache.get(key);
    }

    public double getBollingerUpperAt(int period, double stdDev, int barIndex) {
        Indicators.BollingerResult result = getBollingerBands(period, stdDev);
        return barIndex < result.upper().length ? result.upper()[barIndex] : Double.NaN;
    }

    public double getBollingerMiddleAt(int period, double stdDev, int barIndex) {
        Indicators.BollingerResult result = getBollingerBands(period, stdDev);
        return barIndex < result.middle().length ? result.middle()[barIndex] : Double.NaN;
    }

    public double getBollingerLowerAt(int period, double stdDev, int barIndex) {
        Indicators.BollingerResult result = getBollingerBands(period, stdDev);
        return barIndex < result.lower().length ? result.lower()[barIndex] : Double.NaN;
    }

    // ========== ATR ==========

    public double[] getATR(int period) {
        String key = "atr:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.atr(candles, period));
        }
        return (double[]) cache.get(key);
    }

    public double getATRAt(int period, int barIndex) {
        return Indicators.atrAt(candles, period, barIndex);
    }

    // ========== Range Functions ==========

    public double[] getHighOf(int period) {
        String key = "high_of:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.highOf(candles, period));
        }
        return (double[]) cache.get(key);
    }

    public double getHighOfAt(int period, int barIndex) {
        return Indicators.highOfAt(candles, period, barIndex);
    }

    public double[] getLowOf(int period) {
        String key = "low_of:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.lowOf(candles, period));
        }
        return (double[]) cache.get(key);
    }

    public double getLowOfAt(int period, int barIndex) {
        return Indicators.lowOfAt(candles, period, barIndex);
    }

    // ========== Volume ==========

    public double[] getAvgVolume(int period) {
        String key = "avg_volume:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.avgVolume(candles, period));
        }
        return (double[]) cache.get(key);
    }

    public double getAvgVolumeAt(int period, int barIndex) {
        return Indicators.avgVolumeAt(candles, period, barIndex);
    }

    // ========== Price Access ==========

    public double getCloseAt(int barIndex) {
        Candle c = getCandleAt(barIndex);
        return c != null ? c.close() : Double.NaN;
    }

    public double getOpenAt(int barIndex) {
        Candle c = getCandleAt(barIndex);
        return c != null ? c.open() : Double.NaN;
    }

    public double getHighAt(int barIndex) {
        Candle c = getCandleAt(barIndex);
        return c != null ? c.high() : Double.NaN;
    }

    public double getLowAt(int barIndex) {
        Candle c = getCandleAt(barIndex);
        return c != null ? c.low() : Double.NaN;
    }

    public double getVolumeAt(int barIndex) {
        Candle c = getCandleAt(barIndex);
        return c != null ? c.volume() : Double.NaN;
    }

    // ========== Time Functions ==========

    /**
     * Get timestamp at bar index
     */
    public long getTimestampAt(int barIndex) {
        Candle c = getCandleAt(barIndex);
        return c != null ? c.timestamp() : 0;
    }

    /**
     * Get day of week at bar index (1=Monday, 7=Sunday)
     */
    public double getDayOfWeekAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        if (timestamp == 0) return Double.NaN;
        ZonedDateTime dt = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC);
        return dt.getDayOfWeek().getValue(); // 1=Monday to 7=Sunday
    }

    /**
     * Get hour at bar index (0-23)
     */
    public double getHourAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        if (timestamp == 0) return Double.NaN;
        ZonedDateTime dt = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC);
        return dt.getHour();
    }

    /**
     * Get day of month at bar index (1-31)
     */
    public double getDayAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        if (timestamp == 0) return Double.NaN;
        ZonedDateTime dt = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC);
        return dt.getDayOfMonth();
    }

    /**
     * Get month at bar index (1-12)
     */
    public double getMonthAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        if (timestamp == 0) return Double.NaN;
        ZonedDateTime dt = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC);
        return dt.getMonthValue();
    }

    // ========== Moon Functions ==========

    // Known new moon reference: January 6, 2000 at 18:14 UTC
    private static final long NEW_MOON_REFERENCE = 947182440000L;
    // Synodic month (moon cycle) in milliseconds: 29.53059 days
    private static final double SYNODIC_MONTH_MS = 29.53059 * 24 * 60 * 60 * 1000;

    /**
     * Get moon phase at bar index.
     * Returns a value from 0 to 1 where:
     * - 0.0 = new moon
     * - 0.25 = first quarter
     * - 0.5 = full moon
     * - 0.75 = last quarter
     * - 1.0 = new moon (wraps around)
     */
    public double getMoonPhaseAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        if (timestamp == 0) return Double.NaN;

        // Calculate how many synodic months since reference new moon
        double timeSinceRef = timestamp - NEW_MOON_REFERENCE;
        double phase = (timeSinceRef % SYNODIC_MONTH_MS) / SYNODIC_MONTH_MS;

        // Handle negative timestamps (before reference)
        if (phase < 0) phase += 1.0;

        return phase;
    }
}
