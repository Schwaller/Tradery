package com.tradery.data;

/**
 * Represents a data requirement for backtesting or charting.
 *
 * Requirements are categorized into two tiers:
 * - TRADING: Required for backtest engine to run (blocks until ready)
 * - VIEW: Required for charts only (loads in background, doesn't block)
 *
 * @param dataType  Type identifier (e.g., "OHLC:15s", "OHLC:1d", "AggTrades", "Funding", "OI")
 * @param symbol    Trading symbol (e.g., "BTCUSDT")
 * @param startTime Start time in milliseconds
 * @param endTime   End time in milliseconds
 * @param tier      TRADING (blocks backtest) or VIEW (background for charts)
 * @param source    Origin of requirement (e.g., "strategy", "phase:uptrend", "chart:funding")
 * @param consumer  The consumer requesting this data (for grouping in status UI)
 */
public record DataRequirement(
    String dataType,
    String symbol,
    long startTime,
    long endTime,
    Tier tier,
    String source,
    DataConsumer consumer
) {
    /**
     * Constructor for backward compatibility - defaults to BACKTEST consumer.
     */
    public DataRequirement(String dataType, String symbol, long startTime, long endTime, Tier tier, String source) {
        this(dataType, symbol, startTime, endTime, tier, source,
             tier == Tier.TRADING ? DataConsumer.BACKTEST : DataConsumer.CHART_VIEW);
    }
    /**
     * Data requirement tier determines loading behavior.
     */
    public enum Tier {
        /**
         * Required for backtest engine to run.
         * Backtest blocks until all TRADING requirements are ready.
         * Examples: OHLC for strategy timeframe, AggTrades for sub-minute or delta indicators
         */
        TRADING,

        /**
         * Required for charts only, not for trade execution.
         * Loads in background without blocking backtest.
         * Charts refresh when data arrives.
         * Examples: Funding chart data, OI chart data
         */
        VIEW
    }

    /**
     * Get the base data type without timeframe suffix.
     * E.g., "OHLC:15s" returns "OHLC", "AggTrades" returns "AggTrades"
     */
    public String baseType() {
        int colonIndex = dataType.indexOf(':');
        return colonIndex > 0 ? dataType.substring(0, colonIndex) : dataType;
    }

    /**
     * Get the timeframe suffix if present.
     * E.g., "OHLC:15s" returns "15s", "AggTrades" returns null
     */
    public String timeframe() {
        int colonIndex = dataType.indexOf(':');
        return colonIndex > 0 ? dataType.substring(colonIndex + 1) : null;
    }

    /**
     * Check if this is an OHLC requirement.
     */
    public boolean isOHLC() {
        return dataType.startsWith("OHLC");
    }

    /**
     * Check if this is a sub-minute OHLC requirement (generated from AggTrades).
     */
    public boolean isSubMinuteOHLC() {
        String tf = timeframe();
        if (tf == null) return false;
        return tf.endsWith("s") && !tf.equals("1m");
    }
}
