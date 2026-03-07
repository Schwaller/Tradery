package com.tradery.charts.overlay;

import java.util.List;
import java.util.Map;

/**
 * Provider interface for fetching raw tick-level volume profiles from a data service.
 * Implementations bridge app-specific data clients (forge ApplicationContext, desk DeskAppContext)
 * to the shared footprint overlay in tradery-charts.
 */
public interface FootprintProfileProvider {

    /**
     * Raw profile data for a single candle window.
     *
     * @param windowStart  Candle start timestamp (ms)
     * @param tickSize     Tick size used for profile computation
     * @param totalBuyVolume  Total buy volume across all levels
     * @param totalSellVolume Total sell volume across all levels
     * @param levels       Tick index → [buyVol, sellVol] map
     */
    record RawProfile(long windowStart, double tickSize,
                      double totalBuyVolume, double totalSellVolume,
                      Map<String, double[]> levels) {}

    /**
     * Fetch raw profiles for a time range.
     *
     * @param symbol     Trading symbol (e.g., BTCUSDT)
     * @param timeframe  Candle timeframe (e.g., 1h)
     * @param start      Start timestamp (ms)
     * @param end        End timestamp (ms)
     * @param marketType Market type (e.g., "perp", "spot")
     * @return List of raw profiles, or empty list if none available
     */
    List<RawProfile> getProfiles(String symbol, String timeframe,
                                  long start, long end, String marketType) throws Exception;

    /**
     * Check if the data service is available for profile fetching.
     */
    boolean isAvailable();
}
