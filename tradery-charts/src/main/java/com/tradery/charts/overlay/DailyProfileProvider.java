package com.tradery.charts.overlay;

import java.util.List;

/**
 * Provider interface for fetching daily binned volume profiles from a data service.
 * Implementations bridge app-specific data clients to the shared DVP overlay in tradery-charts.
 */
public interface DailyProfileProvider {

    /**
     * Binned volume profile data for a single day.
     *
     * @param dayStart     UTC midnight timestamp (ms)
     * @param poc          Point of Control price
     * @param vah          Value Area High price
     * @param val          Value Area Low price
     * @param priceLevels  Bin center prices
     * @param buyVolumes   Buy volume at each bin
     * @param sellVolumes  Sell volume at each bin
     */
    record DailyBinnedResult(long dayStart, double poc, double vah, double val,
                              double[] priceLevels, double[] buyVolumes, double[] sellVolumes) {}

    /**
     * Fetch daily binned profiles for a time range.
     *
     * @param symbol        Trading symbol
     * @param start         Start timestamp (ms)
     * @param end           End timestamp (ms)
     * @param binCount      Number of price bins per day
     * @param valueAreaPct  Value area percentage (e.g., 70.0)
     * @return List of daily binned results, or empty list if none available
     */
    List<DailyBinnedResult> getDailyBinned(String symbol, long start, long end,
                                            int binCount, double valueAreaPct) throws Exception;

    /**
     * Check if the data service is available for profile fetching.
     */
    boolean isAvailable();
}
