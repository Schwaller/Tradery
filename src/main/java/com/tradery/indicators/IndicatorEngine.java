package com.tradery.indicators;

import com.tradery.model.AggTrade;
import com.tradery.model.Candle;
import com.tradery.model.FundingRate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indicator Engine - unified access to all indicators with caching.
 * Caches calculated indicator values for performance.
 */
public class IndicatorEngine {

    private List<Candle> candles;
    private List<AggTrade> aggTrades;
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

    // ========== Holiday Functions ==========

    // Cache for US holidays by year
    private final Map<Integer, Set<LocalDate>> usHolidayCache = new HashMap<>();

    /**
     * Check if bar is on a US federal bank holiday.
     */
    public boolean isUSHolidayAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        if (timestamp == 0) return false;

        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
        int year = date.getYear();

        // Get or compute holidays for this year
        Set<LocalDate> holidays = usHolidayCache.computeIfAbsent(year, this::computeUSHolidays);

        return holidays.contains(date);
    }

    /**
     * Compute all US federal holidays for a given year.
     * These are days when the Federal Reserve is closed.
     */
    private Set<LocalDate> computeUSHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();

        // New Year's Day - January 1 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 1, 1)));

        // MLK Day - 3rd Monday in January
        holidays.add(nthDayOfWeek(year, 1, DayOfWeek.MONDAY, 3));

        // Presidents Day - 3rd Monday in February
        holidays.add(nthDayOfWeek(year, 2, DayOfWeek.MONDAY, 3));

        // Memorial Day - Last Monday in May
        holidays.add(LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)));

        // Juneteenth - June 19 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 6, 19)));

        // Independence Day - July 4 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 7, 4)));

        // Labor Day - 1st Monday in September
        holidays.add(nthDayOfWeek(year, 9, DayOfWeek.MONDAY, 1));

        // Columbus Day - 2nd Monday in October
        holidays.add(nthDayOfWeek(year, 10, DayOfWeek.MONDAY, 2));

        // Veterans Day - November 11 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 11, 11)));

        // Thanksgiving - 4th Thursday in November
        holidays.add(nthDayOfWeek(year, 11, DayOfWeek.THURSDAY, 4));

        // Christmas Day - December 25 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 12, 25)));

        return holidays;
    }

    /**
     * Get the nth occurrence of a day of week in a month.
     */
    private LocalDate nthDayOfWeek(int year, int month, DayOfWeek dayOfWeek, int n) {
        LocalDate first = LocalDate.of(year, month, 1).with(TemporalAdjusters.firstInMonth(dayOfWeek));
        return first.plusWeeks(n - 1);
    }

    /**
     * Get the observed date for a holiday (Friday if Saturday, Monday if Sunday).
     */
    private LocalDate observedDate(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) {
            return date.minusDays(1); // Friday
        } else if (dow == DayOfWeek.SUNDAY) {
            return date.plusDays(1); // Monday
        }
        return date;
    }

    // ========== FOMC Meeting Functions ==========

    // Cache for FOMC meeting dates by year
    private final Map<Integer, Set<LocalDate>> fomcMeetingCache = new HashMap<>();

    /**
     * Check if bar is on an FOMC meeting day.
     * FOMC meetings are typically 2-day events (Tuesday-Wednesday).
     */
    public boolean isFomcMeetingAt(int barIndex) {
        long timestamp = getTimestampAt(barIndex);
        if (timestamp == 0) return false;

        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
        int year = date.getYear();

        Set<LocalDate> meetings = fomcMeetingCache.computeIfAbsent(year, this::getFomcMeetingDates);
        return meetings.contains(date);
    }

    /**
     * Get FOMC meeting dates for a given year.
     * These are the actual meeting days (typically 2 consecutive days).
     */
    private Set<LocalDate> getFomcMeetingDates(int year) {
        Set<LocalDate> dates = new HashSet<>();

        // FOMC Schedule - 8 meetings per year, typically Tue-Wed
        switch (year) {
            case 2024 -> {
                addMeetingDays(dates, 2024, 1, 30, 31);   // Jan 30-31
                addMeetingDays(dates, 2024, 3, 19, 20);   // Mar 19-20
                addMeetingDays(dates, 2024, 4, 30, 0);    // Apr 30 - May 1
                dates.add(LocalDate.of(2024, 5, 1));
                addMeetingDays(dates, 2024, 6, 11, 12);   // Jun 11-12
                addMeetingDays(dates, 2024, 7, 30, 31);   // Jul 30-31
                addMeetingDays(dates, 2024, 9, 17, 18);   // Sep 17-18
                addMeetingDays(dates, 2024, 11, 6, 7);    // Nov 6-7
                addMeetingDays(dates, 2024, 12, 17, 18);  // Dec 17-18
            }
            case 2025 -> {
                addMeetingDays(dates, 2025, 1, 28, 29);   // Jan 28-29
                addMeetingDays(dates, 2025, 3, 18, 19);   // Mar 18-19
                addMeetingDays(dates, 2025, 5, 6, 7);     // May 6-7
                addMeetingDays(dates, 2025, 6, 17, 18);   // Jun 17-18
                addMeetingDays(dates, 2025, 7, 29, 30);   // Jul 29-30
                addMeetingDays(dates, 2025, 9, 16, 17);   // Sep 16-17
                addMeetingDays(dates, 2025, 11, 4, 5);    // Nov 4-5
                addMeetingDays(dates, 2025, 12, 16, 17);  // Dec 16-17
            }
            case 2026 -> {
                addMeetingDays(dates, 2026, 1, 27, 28);   // Jan 27-28
                addMeetingDays(dates, 2026, 3, 17, 18);   // Mar 17-18
                addMeetingDays(dates, 2026, 5, 5, 6);     // May 5-6
                addMeetingDays(dates, 2026, 6, 16, 17);   // Jun 16-17
                addMeetingDays(dates, 2026, 7, 28, 29);   // Jul 28-29
                addMeetingDays(dates, 2026, 9, 15, 16);   // Sep 15-16
                addMeetingDays(dates, 2026, 11, 3, 4);    // Nov 3-4
                addMeetingDays(dates, 2026, 12, 15, 16);  // Dec 15-16
            }
            default -> {
                // For years outside known schedule, estimate based on typical pattern
                // Meetings typically in: late Jan, mid Mar, early May, mid Jun, late Jul, mid Sep, early Nov, mid Dec
            }
        }

        return dates;
    }

    private void addMeetingDays(Set<LocalDate> dates, int year, int month, int day1, int day2) {
        dates.add(LocalDate.of(year, month, day1));
        if (day2 > 0) {
            dates.add(LocalDate.of(year, month, day2));
        }
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
        return Indicators.vwapAt(candles, barIndex);
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
        if (!hasAggTrades()) {
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
        if (!hasAggTrades()) {
            return Double.NaN;
        }
        return OrderflowIndicators.deltaAt(aggTrades, candles, resolution, barIndex);
    }

    public double[] getCumulativeDelta() {
        if (!hasAggTrades()) {
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
        if (!hasAggTrades()) {
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
        if (!hasAggTrades()) {
            return Double.NaN;
        }
        return OrderflowIndicators.whaleDeltaAt(aggTrades, candles, resolution, threshold, barIndex);
    }

    /**
     * Get whale buy volume at bar index - buy volume from trades above threshold only.
     */
    public double getWhaleBuyVolAt(double threshold, int barIndex) {
        if (!hasAggTrades()) {
            return Double.NaN;
        }
        return OrderflowIndicators.whaleBuyVolumeAt(aggTrades, candles, resolution, threshold, barIndex);
    }

    /**
     * Get whale sell volume at bar index - sell volume from trades above threshold only.
     */
    public double getWhaleSellVolAt(double threshold, int barIndex) {
        if (!hasAggTrades()) {
            return Double.NaN;
        }
        return OrderflowIndicators.whaleSellVolumeAt(aggTrades, candles, resolution, threshold, barIndex);
    }

    /**
     * Get large trade count at bar index - number of trades above threshold.
     */
    public double getLargeTradeCountAt(double threshold, int barIndex) {
        if (!hasAggTrades()) {
            return Double.NaN;
        }
        return OrderflowIndicators.largeTradeCountAt(aggTrades, candles, resolution, threshold, barIndex);
    }

    // ========== Orderflow Arrays for Charts ==========

    /**
     * Get whale delta array for all bars - delta from trades above threshold only.
     */
    public double[] getWhaleDelta(double threshold) {
        if (!hasAggTrades()) {
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
        if (!hasAggTrades()) {
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
        if (!hasAggTrades()) {
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
        if (!hasAggTrades()) {
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
        if (!hasAggTrades()) {
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
        if (!hasAggTrades()) {
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
}
