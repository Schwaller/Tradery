package com.tradery.indicators;

import com.tradery.model.AggTrade;
import com.tradery.model.Candle;
import com.tradery.model.FundingRate;
import com.tradery.model.OpenInterest;
import com.tradery.model.PremiumIndex;
import com.tradery.indicators.RotatingRays.RaySet;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Indicator Engine - unified access to all indicators with caching.
 * Caches calculated indicator values for performance.
 */
public class IndicatorEngine {

    private List<Candle> candles;
    private List<AggTrade> aggTrades;
    private String resolution = "1h";
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    /**
     * Initialize with candle data
     */
    public void setCandles(List<Candle> candles, String resolution) {
        this.candles = candles;
        this.resolution = resolution;
        clearCache();
    }

    /**
     * Set aggregated trades data for orderflow indicators.
     * Must be called before using delta indicators.
     */
    public void setAggTrades(List<AggTrade> aggTrades) {
        this.aggTrades = aggTrades;
        // Clear orderflow-related cache entries
        cache.remove("delta");
        cache.remove("cumDelta");
    }

    /**
     * Check if aggregated trades data is available.
     */
    public boolean hasAggTrades() {
        return aggTrades != null && !aggTrades.isEmpty();
    }

    /**
     * Check if candle data is available.
     */
    public boolean hasCandles() {
        return candles != null && !candles.isEmpty();
    }

    /**
     * Clear the indicator cache
     */
    public void clearCache() {
        cache.clear();
        dailyProfileCache.clear();
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
        double[] sma = getSMA(period);  // Uses cache
        return barIndex < sma.length ? sma[barIndex] : Double.NaN;
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
        double[] ema = getEMA(period);  // Uses cache
        return barIndex < ema.length ? ema[barIndex] : Double.NaN;
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
        double[] rsi = getRSI(period);  // Uses cache
        return barIndex < rsi.length ? rsi[barIndex] : Double.NaN;
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

    // Aliases for BBANDS syntax
    public double getBBandsUpperAt(int period, double stdDev, int barIndex) {
        return getBollingerUpperAt(period, stdDev, barIndex);
    }

    public double getBBandsMiddleAt(int period, double stdDev, int barIndex) {
        return getBollingerMiddleAt(period, stdDev, barIndex);
    }

    public double getBBandsLowerAt(int period, double stdDev, int barIndex) {
        return getBollingerLowerAt(period, stdDev, barIndex);
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
        double[] atr = getATR(period);  // Uses cache
        return barIndex < atr.length ? atr[barIndex] : Double.NaN;
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
        double[] highOf = getHighOf(period);  // Uses cache
        return barIndex < highOf.length ? highOf[barIndex] : Double.NaN;
    }

    public double[] getLowOf(int period) {
        String key = "low_of:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.lowOf(candles, period));
        }
        return (double[]) cache.get(key);
    }

    public double getLowOfAt(int period, int barIndex) {
        double[] lowOf = getLowOf(period);  // Uses cache
        return barIndex < lowOf.length ? lowOf[barIndex] : Double.NaN;
    }

    // ========== Range Position ==========

    public double[] getRangePosition(int period, int skip) {
        String key = "range_position:" + period + ":" + skip;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.rangePosition(candles, period, skip));
        }
        return (double[]) cache.get(key);
    }

    public double getRangePositionAt(int period, int skip, int barIndex) {
        double[] rangePos = getRangePosition(period, skip);  // Uses cache
        return barIndex < rangePos.length ? rangePos[barIndex] : Double.NaN;
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
        double[] avgVol = getAvgVolume(period);  // Uses cache
        return barIndex < avgVol.length ? avgVol[barIndex] : Double.NaN;
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

    // ========== ADX / DMI ==========

    public Indicators.ADXResult getADX(int period) {
        String key = "adx:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.adx(candles, period));
        }
        return (Indicators.ADXResult) cache.get(key);
    }

    public double getADXAt(int period, int barIndex) {
        Indicators.ADXResult result = getADX(period);
        return barIndex < result.adx().length ? result.adx()[barIndex] : Double.NaN;
    }

    public double getPlusDIAt(int period, int barIndex) {
        Indicators.ADXResult result = getADX(period);
        return barIndex < result.plusDI().length ? result.plusDI()[barIndex] : Double.NaN;
    }

    public double getMinusDIAt(int period, int barIndex) {
        Indicators.ADXResult result = getADX(period);
        return barIndex < result.minusDI().length ? result.minusDI()[barIndex] : Double.NaN;
    }

    // ========== Stochastic ==========

    public Indicators.StochasticResult getStochastic(int kPeriod, int dPeriod) {
        String key = "stochastic:" + kPeriod + ":" + dPeriod;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.stochastic(candles, kPeriod, dPeriod));
        }
        return (Indicators.StochasticResult) cache.get(key);
    }

    public double getStochasticKAt(int kPeriod, int barIndex) {
        Indicators.StochasticResult result = getStochastic(kPeriod, 3);  // Uses cache with default dPeriod=3
        return barIndex < result.k().length ? result.k()[barIndex] : Double.NaN;
    }

    public double getStochasticDAt(int kPeriod, int dPeriod, int barIndex) {
        Indicators.StochasticResult result = getStochastic(kPeriod, dPeriod);  // Uses cache
        return barIndex < result.d().length ? result.d()[barIndex] : Double.NaN;
    }

    // ========== Moon Functions ==========

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
        return CalendarIndicators.getMoonPhase(timestamp);
    }

    // ========== Holiday Functions ==========

    /**
     * Check if bar is on a US federal bank holiday.
     */
    public boolean isUSHolidayAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        return CalendarIndicators.isUSHoliday(timestamp);
    }

    // ========== FOMC Meeting Functions ==========

    /**
     * Check if bar is on an FOMC meeting day.
     * FOMC meetings are typically 2-day events (Tuesday-Wednesday).
     */
    public boolean isFomcMeetingAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        return CalendarIndicators.isFomcMeeting(timestamp);
    }

    // ========== VWAP (Tier 1 - Orderflow) ==========

    public double[] getVWAP() {
        String key = "vwap";
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.vwap(candles));
        }
        return (double[]) cache.get(key);
    }

    public double getVWAPAt(int barIndex) {
        double[] vwap = getVWAP();  // Uses cache
        return barIndex < vwap.length ? vwap[barIndex] : Double.NaN;
    }

    // ========== Volume Profile / POC / VAH / VAL (Tier 1 - Orderflow) ==========

    public Indicators.VolumeProfileResult getVolumeProfile(int period) {
        String key = "volumeProfile:" + period;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.volumeProfile(candles, period, 24, 70.0));
        }
        return (Indicators.VolumeProfileResult) cache.get(key);
    }

    public double getPOCAt(int period, int barIndex) {
        return Indicators.pocAt(candles, period, barIndex);
    }

    public double getVAHAt(int period, int barIndex) {
        return Indicators.vahAt(candles, period, barIndex);
    }

    public double getVALAt(int period, int barIndex) {
        return Indicators.valAt(candles, period, barIndex);
    }

    // ========== Delta / Cumulative Delta (Tier 2 - Orderflow, requires aggTrades) ==========

    public double[] getDelta() {
        if (!hasAggTrades() || !hasCandles()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "delta";
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.delta(aggTrades, candles, resolution));
        }
        return (double[]) cache.get(key);
    }

    public double getDeltaAt(int barIndex) {
        if (!hasAggTrades() || !hasCandles()) {
            return Double.NaN;
        }
        return OrderflowIndicators.deltaAt(aggTrades, candles, resolution, barIndex);
    }

    public double[] getCumulativeDelta() {
        if (!hasAggTrades() || !hasCandles()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "cumDelta";
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.cumulativeDelta(aggTrades, candles, resolution));
        }
        return (double[]) cache.get(key);
    }

    public double getCumulativeDeltaAt(int barIndex) {
        if (!hasAggTrades() || !hasCandles()) {
            return Double.NaN;
        }
        return OrderflowIndicators.cumulativeDeltaAt(aggTrades, candles, resolution, barIndex);
    }

    // ========== Whale / Large Trade Detection (Tier 2 - requires aggTrades) ==========

    /**
     * Get whale delta at bar index - delta from trades above threshold only.
     * @param threshold Minimum notional value in USD (e.g., 50000 for $50K)
     */
    public double getWhaleDeltaAt(double threshold, int barIndex) {
        if (!hasAggTrades() || !hasCandles()) {
            return Double.NaN;
        }
        return OrderflowIndicators.whaleDeltaAt(aggTrades, candles, resolution, threshold, barIndex);
    }

    /**
     * Get whale buy volume at bar index - buy volume from trades above threshold only.
     */
    public double getWhaleBuyVolAt(double threshold, int barIndex) {
        if (!hasAggTrades() || !hasCandles()) {
            return Double.NaN;
        }
        return OrderflowIndicators.whaleBuyVolumeAt(aggTrades, candles, resolution, threshold, barIndex);
    }

    /**
     * Get whale sell volume at bar index - sell volume from trades above threshold only.
     */
    public double getWhaleSellVolAt(double threshold, int barIndex) {
        if (!hasAggTrades() || !hasCandles()) {
            return Double.NaN;
        }
        return OrderflowIndicators.whaleSellVolumeAt(aggTrades, candles, resolution, threshold, barIndex);
    }

    /**
     * Get large trade count at bar index - number of trades above threshold.
     */
    public double getLargeTradeCountAt(double threshold, int barIndex) {
        if (!hasAggTrades() || !hasCandles()) {
            return Double.NaN;
        }
        return OrderflowIndicators.largeTradeCountAt(aggTrades, candles, resolution, threshold, barIndex);
    }

    // ========== Orderflow Arrays for Charts ==========

    /**
     * Get whale delta array for all bars - delta from trades above threshold only.
     */
    public double[] getWhaleDelta(double threshold) {
        if (!hasAggTrades() || !hasCandles()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "whaleDelta:" + threshold;
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.whaleDelta(aggTrades, candles, resolution, threshold));
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get retail delta array for all bars - delta from trades below threshold only.
     */
    public double[] getRetailDelta(double threshold) {
        if (!hasAggTrades() || !hasCandles()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "retailDelta:" + threshold;
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.retailDelta(aggTrades, candles, resolution, threshold));
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get buy volume array for all bars.
     */
    public double[] getBuyVolume() {
        if (!hasAggTrades() || !hasCandles()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "buyVolume";
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.buyVolume(aggTrades, candles, resolution));
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get sell volume array for all bars.
     */
    public double[] getSellVolume() {
        if (!hasAggTrades() || !hasCandles()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "sellVolume";
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.sellVolume(aggTrades, candles, resolution));
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get trade count array for all bars.
     */
    public double[] getTradeCount() {
        if (!hasAggTrades() || !hasCandles()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "tradeCount";
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.tradeCount(aggTrades, candles, resolution));
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get large trade count array for all bars.
     */
    public double[] getLargeTradeCount(double threshold) {
        if (!hasAggTrades() || !hasCandles()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "largeTradeCount:" + threshold;
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.largeTradeCount(aggTrades, candles, resolution, threshold));
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get whale buy volume array for all bars.
     */
    public double[] getWhaleBuyVolume(double threshold) {
        if (!hasAggTrades()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "whaleBuyVol:" + threshold;
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.whaleBuyVolume(aggTrades, candles, resolution, threshold));
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get whale sell volume array for all bars.
     */
    public double[] getWhaleSellVolume(double threshold) {
        if (!hasAggTrades()) {
            double[] result = new double[candles != null ? candles.size() : 0];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "whaleSellVol:" + threshold;
        if (!cache.containsKey(key)) {
            cache.put(key, OrderflowIndicators.whaleSellVolume(aggTrades, candles, resolution, threshold));
        }
        return (double[]) cache.get(key);
    }

    // ========== Funding Rate (requires funding data to be loaded) ==========

    private List<FundingRate> fundingRates;

    /**
     * Set funding rate data for funding indicators.
     */
    public void setFundingRates(List<FundingRate> fundingRates) {
        this.fundingRates = fundingRates;
        // Clear funding-related cache entries
        cache.remove("fundingArray");
        cache.remove("funding8HArray");
    }

    /**
     * Check if funding rate data is available.
     */
    public boolean hasFundingRates() {
        return fundingRates != null && !fundingRates.isEmpty();
    }

    /**
     * Get the current funding rate at bar index.
     * Returns the most recent funding rate before or at the candle timestamp.
     * Funding rate is returned as percentage (e.g., 0.01 = 0.01%)
     */
    public double getFundingAt(int barIndex) {
        if (!hasFundingRates()) {
            return Double.NaN;
        }

        long candleTime = getTimestampAt(barIndex);
        if (candleTime == 0) return Double.NaN;

        // Find the most recent funding rate at or before this candle
        FundingRate latest = null;
        for (FundingRate fr : fundingRates) {
            if (fr.fundingTime() <= candleTime) {
                latest = fr;
            } else {
                break; // Funding rates are sorted by time
            }
        }

        if (latest == null) return Double.NaN;

        // Convert to percentage (Binance returns as decimal, e.g., 0.0001 = 0.01%)
        return latest.fundingRate() * 100;
    }

    /**
     * Get the 8-hour average funding rate at bar index.
     * Averages the last 3 funding rates (24 hours of data).
     */
    public double getFunding8HAvgAt(int barIndex) {
        if (!hasFundingRates()) {
            return Double.NaN;
        }

        long candleTime = getTimestampAt(barIndex);
        if (candleTime == 0) return Double.NaN;

        // Find funding rates in the last 24 hours (3 x 8h intervals)
        long twentyFourHoursAgo = candleTime - (24 * 60 * 60 * 1000);
        double sum = 0;
        int count = 0;

        for (FundingRate fr : fundingRates) {
            if (fr.fundingTime() > candleTime) break;
            if (fr.fundingTime() >= twentyFourHoursAgo && fr.fundingTime() <= candleTime) {
                sum += fr.fundingRate();
                count++;
            }
        }

        if (count == 0) return Double.NaN;

        // Convert to percentage
        return (sum / count) * 100;
    }

    // ========== Funding Arrays for Charts ==========

    /**
     * Get funding rate array for all bars.
     */
    public double[] getFunding() {
        int size = candles != null ? candles.size() : 0;
        if (!hasFundingRates() || size == 0) {
            double[] result = new double[size];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "fundingArray";
        if (!cache.containsKey(key)) {
            double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                result[i] = getFundingAt(i);
            }
            cache.put(key, result);
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get 8-hour average funding rate array for all bars.
     */
    public double[] getFunding8H() {
        int size = candles != null ? candles.size() : 0;
        if (!hasFundingRates() || size == 0) {
            double[] result = new double[size];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "funding8HArray";
        if (!cache.containsKey(key)) {
            double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                result[i] = getFunding8HAvgAt(i);
            }
            cache.put(key, result);
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get candles list for chart access.
     */
    public List<Candle> getCandles() {
        return candles;
    }

    // ========== Premium Index (requires premium data to be loaded) ==========

    private List<PremiumIndex> premiumIndexData;

    /**
     * Set premium index data for premium indicators.
     */
    public void setPremiumIndex(List<PremiumIndex> premiumIndexData) {
        this.premiumIndexData = premiumIndexData;
        // Clear premium-related cache entries
        cache.remove("premiumArray");
        cache.remove("premiumAvgArray");
    }

    /**
     * Check if premium index data is available.
     */
    public boolean hasPremiumIndex() {
        return premiumIndexData != null && !premiumIndexData.isEmpty();
    }

    /**
     * Get the premium index value at bar index.
     * Returns the premium value for the kline that matches or precedes the candle timestamp.
     * Premium is returned as percentage (e.g., 0.0001 decimal -> 0.01%)
     */
    public double getPremiumAt(int barIndex) {
        if (!hasPremiumIndex()) {
            return Double.NaN;
        }

        long candleTime = getTimestampAt(barIndex);
        if (candleTime == 0) return Double.NaN;

        // Find the premium kline that matches or precedes this candle
        PremiumIndex match = null;
        for (PremiumIndex pi : premiumIndexData) {
            if (pi.openTime() <= candleTime && pi.closeTime() >= candleTime) {
                // Exact match - candle falls within this premium kline
                match = pi;
                break;
            } else if (pi.openTime() <= candleTime) {
                // Track the most recent premium before this candle
                match = pi;
            } else {
                break; // Premium data is sorted by time
            }
        }

        if (match == null) return Double.NaN;

        // Return as percentage
        return match.closePercent();
    }

    /**
     * Get the average premium index over N bars.
     * @param period Number of bars to average
     * @param barIndex Current bar index
     */
    public double getPremiumAvgAt(int period, int barIndex) {
        if (!hasPremiumIndex() || barIndex < period - 1) {
            return Double.NaN;
        }

        double sum = 0;
        int count = 0;

        for (int i = barIndex - period + 1; i <= barIndex; i++) {
            double premium = getPremiumAt(i);
            if (!Double.isNaN(premium)) {
                sum += premium;
                count++;
            }
        }

        if (count == 0) return Double.NaN;
        return sum / count;
    }

    /**
     * Get the premium high at bar index.
     * Returns the high premium value for the matching kline.
     */
    public double getPremiumHighAt(int barIndex) {
        if (!hasPremiumIndex()) {
            return Double.NaN;
        }

        long candleTime = getTimestampAt(barIndex);
        if (candleTime == 0) return Double.NaN;

        for (PremiumIndex pi : premiumIndexData) {
            if (pi.openTime() <= candleTime && pi.closeTime() >= candleTime) {
                return pi.highPercent();
            } else if (pi.openTime() > candleTime) {
                break;
            }
        }

        return Double.NaN;
    }

    /**
     * Get the premium low at bar index.
     * Returns the low premium value for the matching kline.
     */
    public double getPremiumLowAt(int barIndex) {
        if (!hasPremiumIndex()) {
            return Double.NaN;
        }

        long candleTime = getTimestampAt(barIndex);
        if (candleTime == 0) return Double.NaN;

        for (PremiumIndex pi : premiumIndexData) {
            if (pi.openTime() <= candleTime && pi.closeTime() >= candleTime) {
                return pi.lowPercent();
            } else if (pi.openTime() > candleTime) {
                break;
            }
        }

        return Double.NaN;
    }

    // ========== Premium Index Arrays for Charts ==========

    /**
     * Get premium index array for all bars.
     */
    public double[] getPremium() {
        int size = candles != null ? candles.size() : 0;
        if (!hasPremiumIndex() || size == 0) {
            double[] result = new double[size];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "premiumArray";
        if (!cache.containsKey(key)) {
            double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                result[i] = getPremiumAt(i);
            }
            cache.put(key, result);
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get premium average array for all bars.
     */
    public double[] getPremiumAvg(int period) {
        int size = candles != null ? candles.size() : 0;
        if (!hasPremiumIndex() || size == 0) {
            double[] result = new double[size];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "premiumAvg:" + period;
        if (!cache.containsKey(key)) {
            double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                result[i] = getPremiumAvgAt(period, i);
            }
            cache.put(key, result);
        }
        return (double[]) cache.get(key);
    }

    // ========== Open Interest (requires OI data to be loaded) ==========

    private List<OpenInterest> openInterestData;

    /**
     * Set open interest data for OI indicators.
     */
    public void setOpenInterest(List<OpenInterest> openInterestData) {
        this.openInterestData = openInterestData;
        // Clear OI-related cache entries
        cache.remove("oiArray");
        cache.remove("oiChangeArray");
    }

    /**
     * Check if open interest data is available.
     */
    public boolean hasOpenInterest() {
        return openInterestData != null && !openInterestData.isEmpty();
    }

    /**
     * Get the open interest value at bar index.
     * Returns the most recent OI data before or at the candle timestamp.
     */
    public double getOIAt(int barIndex) {
        if (!hasOpenInterest()) {
            return Double.NaN;
        }

        long candleTime = getTimestampAt(barIndex);
        if (candleTime == 0) return Double.NaN;

        // Find the most recent OI at or before this candle
        OpenInterest latest = null;
        for (OpenInterest oi : openInterestData) {
            if (oi.timestamp() <= candleTime) {
                latest = oi;
            } else {
                break; // OI data is sorted by time
            }
        }

        if (latest == null) return Double.NaN;

        // Return OI value in billions for readability
        return latest.openInterestValueBillions();
    }

    /**
     * Get the OI change from previous bar at bar index.
     */
    public double getOIChangeAt(int barIndex) {
        if (!hasOpenInterest() || barIndex < 1) {
            return Double.NaN;
        }

        double current = getOIAt(barIndex);
        double previous = getOIAt(barIndex - 1);

        if (Double.isNaN(current) || Double.isNaN(previous)) {
            return Double.NaN;
        }

        return current - previous;
    }

    /**
     * Get the OI change over N bars (delta).
     */
    public double getOIDeltaAt(int period, int barIndex) {
        if (!hasOpenInterest() || barIndex < period) {
            return Double.NaN;
        }

        double current = getOIAt(barIndex);
        double past = getOIAt(barIndex - period);

        if (Double.isNaN(current) || Double.isNaN(past)) {
            return Double.NaN;
        }

        return current - past;
    }

    // ========== Open Interest Arrays for Charts ==========

    /**
     * Get OI array for all bars.
     */
    public double[] getOI() {
        int size = candles != null ? candles.size() : 0;
        if (!hasOpenInterest() || size == 0) {
            double[] result = new double[size];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "oiArray";
        if (!cache.containsKey(key)) {
            double[] result = new double[size];
            for (int i = 0; i < size; i++) {
                result[i] = getOIAt(i);
            }
            cache.put(key, result);
        }
        return (double[]) cache.get(key);
    }

    /**
     * Get OI change array for all bars.
     */
    public double[] getOIChange() {
        int size = candles != null ? candles.size() : 0;
        if (!hasOpenInterest() || size == 0) {
            double[] result = new double[size];
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }
        String key = "oiChangeArray";
        if (!cache.containsKey(key)) {
            double[] result = new double[size];
            result[0] = Double.NaN; // No change for first bar
            for (int i = 1; i < size; i++) {
                result[i] = getOIChangeAt(i);
            }
            cache.put(key, result);
        }
        return (double[]) cache.get(key);
    }

    // ========== Supertrend ==========

    public Supertrend.Result getSupertrend(int period, double multiplier) {
        String key = "supertrend:" + period + ":" + multiplier;
        if (!cache.containsKey(key)) {
            cache.put(key, Supertrend.calculate(candles, period, multiplier));
        }
        return (Supertrend.Result) cache.get(key);
    }

    /**
     * Get Supertrend trend direction at bar index.
     * Returns 1 for uptrend, -1 for downtrend.
     */
    public double getSupertrendTrendAt(int period, double multiplier, int barIndex) {
        Supertrend.Result result = getSupertrend(period, multiplier);
        return barIndex < result.trend().length ? result.trend()[barIndex] : Double.NaN;
    }

    /**
     * Get Supertrend upper band at bar index.
     */
    public double getSupertrendUpperAt(int period, double multiplier, int barIndex) {
        Supertrend.Result result = getSupertrend(period, multiplier);
        return barIndex < result.upperBand().length ? result.upperBand()[barIndex] : Double.NaN;
    }

    /**
     * Get Supertrend lower band at bar index.
     */
    public double getSupertrendLowerAt(int period, double multiplier, int barIndex) {
        Supertrend.Result result = getSupertrend(period, multiplier);
        return barIndex < result.lowerBand().length ? result.lowerBand()[barIndex] : Double.NaN;
    }

    // ========== Daily Session Volume Profile (PREV_DAY / TODAY POC/VAH/VAL) ==========

    // Cache for daily volume profiles: key = "date:YYYY-MM-DD", value = VolumeProfileResult
    private final Map<String, Indicators.VolumeProfileResult> dailyProfileCache = new ConcurrentHashMap<>();

    /**
     * Get the UTC date for a bar index.
     */
    private LocalDate getDateAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        if (timestamp == 0) return null;
        return Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Find the start bar index for a given UTC date.
     * Returns -1 if no bars exist for that date.
     */
    private int findDayStartIndex(LocalDate targetDate) {
        if (candles == null || candles.isEmpty()) return -1;

        for (int i = 0; i < candles.size(); i++) {
            LocalDate barDate = getDateAt(i);
            if (barDate != null && barDate.equals(targetDate)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find the end bar index (inclusive) for a given UTC date.
     * Returns -1 if no bars exist for that date.
     */
    private int findDayEndIndex(LocalDate targetDate) {
        if (candles == null || candles.isEmpty()) return -1;

        int lastIdx = -1;
        for (int i = 0; i < candles.size(); i++) {
            LocalDate barDate = getDateAt(i);
            if (barDate != null && barDate.equals(targetDate)) {
                lastIdx = i;
            } else if (lastIdx >= 0) {
                // We've moved past the target date
                break;
            }
        }
        return lastIdx;
    }

    /**
     * Calculate volume profile for a specific day's candles.
     */
    private Indicators.VolumeProfileResult calculateDayProfile(LocalDate date) {
        String cacheKey = "day:" + date.toString();
        if (dailyProfileCache.containsKey(cacheKey)) {
            return dailyProfileCache.get(cacheKey);
        }

        int startIdx = findDayStartIndex(date);
        int endIdx = findDayEndIndex(date);

        if (startIdx < 0 || endIdx < 0) {
            return new Indicators.VolumeProfileResult(Double.NaN, Double.NaN, Double.NaN, new double[0], new double[0]);
        }

        // Extract candles for this day
        List<Candle> dayCandles = candles.subList(startIdx, endIdx + 1);
        Indicators.VolumeProfileResult result = Indicators.volumeProfile(dayCandles, dayCandles.size(), 24, 70.0);

        dailyProfileCache.put(cacheKey, result);
        return result;
    }

    /**
     * Calculate volume profile for today's candles up to and including barIndex.
     * This is the "developing" profile that updates throughout the day.
     */
    private Indicators.VolumeProfileResult calculateTodayProfile(int barIndex) {
        LocalDate today = getDateAt(barIndex);
        if (today == null) {
            return new Indicators.VolumeProfileResult(Double.NaN, Double.NaN, Double.NaN, new double[0], new double[0]);
        }

        int startIdx = findDayStartIndex(today);
        if (startIdx < 0 || startIdx > barIndex) {
            return new Indicators.VolumeProfileResult(Double.NaN, Double.NaN, Double.NaN, new double[0], new double[0]);
        }

        // Extract candles from day start to current bar
        List<Candle> todayCandles = candles.subList(startIdx, barIndex + 1);
        return Indicators.volumeProfile(todayCandles, todayCandles.size(), 24, 70.0);
    }

    /**
     * Get the previous day's POC at a bar index.
     */
    public double getPrevDayPOCAt(int barIndex) {
        LocalDate today = getDateAt(barIndex);
        if (today == null) return Double.NaN;

        LocalDate prevDay = today.minusDays(1);
        Indicators.VolumeProfileResult profile = calculateDayProfile(prevDay);
        return profile.poc();
    }

    /**
     * Get the previous day's VAH at a bar index.
     */
    public double getPrevDayVAHAt(int barIndex) {
        LocalDate today = getDateAt(barIndex);
        if (today == null) return Double.NaN;

        LocalDate prevDay = today.minusDays(1);
        Indicators.VolumeProfileResult profile = calculateDayProfile(prevDay);
        return profile.vah();
    }

    /**
     * Get the previous day's VAL at a bar index.
     */
    public double getPrevDayVALAt(int barIndex) {
        LocalDate today = getDateAt(barIndex);
        if (today == null) return Double.NaN;

        LocalDate prevDay = today.minusDays(1);
        Indicators.VolumeProfileResult profile = calculateDayProfile(prevDay);
        return profile.val();
    }

    /**
     * Get today's developing POC at a bar index.
     */
    public double getTodayPOCAt(int barIndex) {
        Indicators.VolumeProfileResult profile = calculateTodayProfile(barIndex);
        return profile.poc();
    }

    /**
     * Get today's developing VAH at a bar index.
     */
    public double getTodayVAHAt(int barIndex) {
        Indicators.VolumeProfileResult profile = calculateTodayProfile(barIndex);
        return profile.vah();
    }

    /**
     * Get today's developing VAL at a bar index.
     */
    public double getTodayVALAt(int barIndex) {
        Indicators.VolumeProfileResult profile = calculateTodayProfile(barIndex);
        return profile.val();
    }

    // ========== Rotating Ray Trendlines ==========

    /**
     * Get resistance rays using full dataset - for chart visualization only.
     * Cached for performance since ray calculation is expensive.
     * WARNING: Do NOT use for backtest/phase evaluation (causes look-ahead bias).
     */
    private RaySet getResistanceRaysFull(int lookback, int skip) {
        String key = "resistanceRays:" + lookback + ":" + skip;
        if (!cache.containsKey(key)) {
            cache.put(key, RotatingRays.calculateResistanceRays(candles, lookback, skip));
        }
        return (RaySet) cache.get(key);
    }

    /**
     * Get support rays using full dataset - for chart visualization only.
     * Cached for performance since ray calculation is expensive.
     * WARNING: Do NOT use for backtest/phase evaluation (causes look-ahead bias).
     */
    private RaySet getSupportRaysFull(int lookback, int skip) {
        String key = "supportRays:" + lookback + ":" + skip;
        if (!cache.containsKey(key)) {
            cache.put(key, RotatingRays.calculateSupportRays(candles, lookback, skip));
        }
        return (RaySet) cache.get(key);
    }

    /**
     * Get resistance rays at a specific bar index, using only data available up to that bar.
     * This avoids look-ahead bias for backtest/phase evaluation.
     * @param lookback Number of bars to look back for ATH
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index (rays computed using candles 0..barIndex)
     * @return RaySet computed with historical data only
     */
    private RaySet getResistanceRaysAt(int lookback, int skip, int barIndex) {
        // Slice candles to only include data available at barIndex
        int endIndex = Math.min(barIndex + 1, candles.size());
        List<Candle> availableCandles = candles.subList(0, endIndex);
        return RotatingRays.calculateResistanceRays(availableCandles, lookback, skip);
    }

    /**
     * Get support rays at a specific bar index, using only data available up to that bar.
     * This avoids look-ahead bias for backtest/phase evaluation.
     * @param lookback Number of bars to look back for ATL
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index (rays computed using candles 0..barIndex)
     * @return RaySet computed with historical data only
     */
    private RaySet getSupportRaysAt(int lookback, int skip, int barIndex) {
        // Slice candles to only include data available at barIndex
        int endIndex = Math.min(barIndex + 1, candles.size());
        List<Candle> availableCandles = candles.subList(0, endIndex);
        return RotatingRays.calculateSupportRays(availableCandles, lookback, skip);
    }

    // ===== Resistance Ray Functions =====

    /**
     * Check if price is above a specific resistance ray (ray is broken).
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * @param rayNum Ray number (1-indexed, ray 1 = ATH ray)
     * @param lookback Number of bars to look back for ATH
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return true if price is above the ray
     */
    public boolean isResistanceRayBroken(int rayNum, int lookback, int skip, int barIndex) {
        RaySet raySet = getResistanceRaysAt(lookback, skip, barIndex);
        // Use sliced candles for evaluation
        List<Candle> availableCandles = candles.subList(0, Math.min(barIndex + 1, candles.size()));
        return RotatingRays.isRayBroken(raySet, rayNum, availableCandles, barIndex);
    }

    /**
     * Check if price crossed above a resistance ray this bar.
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * @param rayNum Ray number (1-indexed, ray 1 = ATH ray)
     * @param lookback Number of bars to look back for ATH
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return true if price crossed above the ray this bar
     */
    public boolean didResistanceRayCross(int rayNum, int lookback, int skip, int barIndex) {
        RaySet raySet = getResistanceRaysAt(lookback, skip, barIndex);
        List<Candle> availableCandles = candles.subList(0, Math.min(barIndex + 1, candles.size()));
        return RotatingRays.didRayCross(raySet, rayNum, availableCandles, barIndex);
    }

    /**
     * Get percentage distance from price to a resistance ray.
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * Positive = above ray, Negative = below ray.
     * @param rayNum Ray number (1-indexed, ray 1 = ATH ray)
     * @param lookback Number of bars to look back for ATH
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return Distance as percentage
     */
    public double getResistanceRayDistance(int rayNum, int lookback, int skip, int barIndex) {
        RaySet raySet = getResistanceRaysAt(lookback, skip, barIndex);
        List<Candle> availableCandles = candles.subList(0, Math.min(barIndex + 1, candles.size()));
        return RotatingRays.getRayDistance(raySet, rayNum, availableCandles, barIndex);
    }

    /**
     * Count how many resistance rays are currently broken (price above).
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * @param lookback Number of bars to look back for ATH
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return Count of broken rays
     */
    public int getResistanceRaysBroken(int lookback, int skip, int barIndex) {
        RaySet raySet = getResistanceRaysAt(lookback, skip, barIndex);
        List<Candle> availableCandles = candles.subList(0, Math.min(barIndex + 1, candles.size()));
        return RotatingRays.countBrokenRays(raySet, availableCandles, barIndex);
    }

    /**
     * Get total number of resistance rays.
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * @param lookback Number of bars to look back for ATH
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return Total ray count
     */
    public int getResistanceRayCount(int lookback, int skip, int barIndex) {
        RaySet raySet = getResistanceRaysAt(lookback, skip, barIndex);
        return raySet.count();
    }

    // ===== Support Ray Functions =====

    /**
     * Check if price is below a specific support ray (ray is broken).
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * @param rayNum Ray number (1-indexed, ray 1 = ATL ray)
     * @param lookback Number of bars to look back for ATL
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return true if price is below the ray
     */
    public boolean isSupportRayBroken(int rayNum, int lookback, int skip, int barIndex) {
        RaySet raySet = getSupportRaysAt(lookback, skip, barIndex);
        List<Candle> availableCandles = candles.subList(0, Math.min(barIndex + 1, candles.size()));
        return RotatingRays.isRayBroken(raySet, rayNum, availableCandles, barIndex);
    }

    /**
     * Check if price crossed below a support ray this bar.
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * @param rayNum Ray number (1-indexed, ray 1 = ATL ray)
     * @param lookback Number of bars to look back for ATL
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return true if price crossed below the ray this bar
     */
    public boolean didSupportRayCross(int rayNum, int lookback, int skip, int barIndex) {
        RaySet raySet = getSupportRaysAt(lookback, skip, barIndex);
        List<Candle> availableCandles = candles.subList(0, Math.min(barIndex + 1, candles.size()));
        return RotatingRays.didRayCross(raySet, rayNum, availableCandles, barIndex);
    }

    /**
     * Get percentage distance from price to a support ray.
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * Positive = above ray, Negative = below ray.
     * @param rayNum Ray number (1-indexed, ray 1 = ATL ray)
     * @param lookback Number of bars to look back for ATL
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return Distance as percentage
     */
    public double getSupportRayDistance(int rayNum, int lookback, int skip, int barIndex) {
        RaySet raySet = getSupportRaysAt(lookback, skip, barIndex);
        List<Candle> availableCandles = candles.subList(0, Math.min(barIndex + 1, candles.size()));
        return RotatingRays.getRayDistance(raySet, rayNum, availableCandles, barIndex);
    }

    /**
     * Count how many support rays are currently broken (price below).
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * @param lookback Number of bars to look back for ATL
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return Count of broken rays
     */
    public int getSupportRaysBroken(int lookback, int skip, int barIndex) {
        RaySet raySet = getSupportRaysAt(lookback, skip, barIndex);
        List<Candle> availableCandles = candles.subList(0, Math.min(barIndex + 1, candles.size()));
        return RotatingRays.countBrokenRays(raySet, availableCandles, barIndex);
    }

    /**
     * Count total number of support rays.
     * Uses only data available up to barIndex to avoid look-ahead bias.
     * @param lookback Number of bars to look back for ATL
     * @param skip Number of recent bars to skip
     * @param barIndex Current bar index
     * @return Total ray count
     */
    public int getSupportRayCount(int lookback, int skip, int barIndex) {
        RaySet raySet = getSupportRaysAt(lookback, skip, barIndex);
        return raySet.count();
    }

    // ===== Ray Data Access for Charts =====

    /**
     * Get the resistance ray set for chart visualization.
     * Uses full dataset (appropriate for showing current state of rays on chart).
     */
    public RaySet getResistanceRaySet(int lookback, int skip) {
        return getResistanceRaysFull(lookback, skip);
    }

    /**
     * Get the support ray set for chart visualization.
     * Uses full dataset (appropriate for showing current state of rays on chart).
     */
    public RaySet getSupportRaySet(int lookback, int skip) {
        return getSupportRaysFull(lookback, skip);
    }

    /**
     * Get resistance rays at a specific bar for historic ray visualization.
     * Useful for showing how rays evolved over time.
     */
    public RaySet getResistanceRaySetAt(int lookback, int skip, int barIndex) {
        return getResistanceRaysAt(lookback, skip, barIndex);
    }

    /**
     * Get support rays at a specific bar for historic ray visualization.
     * Useful for showing how rays evolved over time.
     */
    public RaySet getSupportRaySetAt(int lookback, int skip, int barIndex) {
        return getSupportRaysAt(lookback, skip, barIndex);
    }

    // ========== Ichimoku Cloud ==========

    /**
     * Get Ichimoku Cloud result with default parameters (9, 26, 52, 26).
     */
    public Indicators.IchimokuResult getIchimoku() {
        String key = "ichimoku:9:26:52:26";
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.ichimoku(candles));
        }
        return (Indicators.IchimokuResult) cache.get(key);
    }

    /**
     * Get Ichimoku Cloud result with custom parameters.
     */
    public Indicators.IchimokuResult getIchimoku(int conversionPeriod, int basePeriod,
                                                  int spanBPeriod, int displacement) {
        String key = "ichimoku:" + conversionPeriod + ":" + basePeriod + ":" + spanBPeriod + ":" + displacement;
        if (!cache.containsKey(key)) {
            cache.put(key, Indicators.ichimoku(candles, conversionPeriod, basePeriod, spanBPeriod, displacement));
        }
        return (Indicators.IchimokuResult) cache.get(key);
    }

    /**
     * Get Ichimoku Tenkan-sen (Conversion Line) at bar index.
     */
    public double getIchimokuTenkanAt(int conversionPeriod, int barIndex) {
        return Indicators.ichimokuTenkanAt(candles, conversionPeriod, barIndex);
    }

    /**
     * Get Ichimoku Kijun-sen (Base Line) at bar index.
     */
    public double getIchimokuKijunAt(int basePeriod, int barIndex) {
        return Indicators.ichimokuKijunAt(candles, basePeriod, barIndex);
    }

    /**
     * Get Ichimoku Senkou Span A at bar index (with displacement applied).
     */
    public double getIchimokuSenkouAAt(int conversionPeriod, int basePeriod, int displacement, int barIndex) {
        return Indicators.ichimokuSenkouAAt(candles, conversionPeriod, basePeriod, displacement, barIndex);
    }

    /**
     * Get Ichimoku Senkou Span B at bar index (with displacement applied).
     */
    public double getIchimokuSenkouBAt(int spanBPeriod, int displacement, int barIndex) {
        return Indicators.ichimokuSenkouBAt(candles, spanBPeriod, displacement, barIndex);
    }

    /**
     * Get Ichimoku Chikou Span at bar index.
     */
    public double getIchimokuChikouAt(int displacement, int barIndex) {
        return Indicators.ichimokuChikouAt(candles, displacement, barIndex);
    }
}
