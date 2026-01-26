package com.tradery.charts.core;

/**
 * Enum representing all indicator chart types.
 * Used for Map-based access to chart components.
 */
public enum IndicatorType {
    RSI("RSI", new double[]{0, 100}),
    MACD("MACD", null),
    ATR("ATR", null),
    DELTA("Delta", null),
    CVD("CVD", null),
    VOLUME_RATIO("Buy/Sell Volume", null),
    WHALE("Whale Delta", null),
    RETAIL("Retail Delta", null),
    FUNDING("Funding", null),
    OI("Open Interest", null),
    STOCHASTIC("Stochastic", new double[]{0, 100}),
    RANGE_POSITION("Range Position", new double[]{-2, 2}),
    ADX("ADX", new double[]{0, 100}),
    TRADE_COUNT("Trade Count", null),
    PREMIUM("Premium Index", null);

    private final String title;
    private final double[] yAxisRange;

    IndicatorType(String title, double[] yAxisRange) {
        this.title = title;
        this.yAxisRange = yAxisRange;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Returns fixed Y-axis range for this indicator, or null if auto-range should be used.
     */
    public double[] getYAxisRange() {
        return yAxisRange;
    }
}
