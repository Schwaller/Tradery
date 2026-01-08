package com.tradery.model;

/**
 * Result of a hoop pattern match at a specific bar.
 * Used for debugging, visualization, and analysis.
 */
public record HoopMatchResult(
    String patternId,        // ID of the pattern that matched
    int anchorBar,           // Bar where pattern started (anchor candle)
    int completionBar,       // Bar where pattern completed (last hoop hit)
    double anchorPrice,      // Initial anchor price (close of anchor candle)
    double[] hoopHitPrices,  // Price where each hoop was hit
    int[] hoopHitBars        // Bar index where each hoop was hit
) {
    /**
     * Get the total duration of the pattern in bars.
     */
    public int getDuration() {
        return completionBar - anchorBar;
    }

    /**
     * Get the absolute price change from anchor to final hoop.
     */
    public double getPriceChange() {
        if (hoopHitPrices == null || hoopHitPrices.length == 0) return 0;
        return hoopHitPrices[hoopHitPrices.length - 1] - anchorPrice;
    }

    /**
     * Get the percentage price change from anchor to final hoop.
     */
    public double getPriceChangePercent() {
        if (anchorPrice == 0) return 0;
        return (getPriceChange() / anchorPrice) * 100;
    }

    /**
     * Get the number of hoops that were matched.
     */
    public int getHoopCount() {
        return hoopHitBars != null ? hoopHitBars.length : 0;
    }

    /**
     * Get the timestamp (in bar index) when a specific hoop was hit.
     * Returns -1 if hoop index is out of bounds.
     */
    public int getHoopHitBar(int hoopIndex) {
        if (hoopHitBars == null || hoopIndex < 0 || hoopIndex >= hoopHitBars.length) {
            return -1;
        }
        return hoopHitBars[hoopIndex];
    }

    /**
     * Get the price where a specific hoop was hit.
     * Returns NaN if hoop index is out of bounds.
     */
    public double getHoopHitPrice(int hoopIndex) {
        if (hoopHitPrices == null || hoopIndex < 0 || hoopIndex >= hoopHitPrices.length) {
            return Double.NaN;
        }
        return hoopHitPrices[hoopIndex];
    }

    @Override
    public String toString() {
        return String.format("HoopMatchResult[pattern=%s, bars=%d-%d, duration=%d, change=%.2f%%]",
            patternId, anchorBar, completionBar, getDuration(), getPriceChangePercent());
    }
}
