package com.tradery.core.model;

import java.util.List;
import java.util.Map;

/**
 * Unified market data interface used by both chart and backtest engine.
 * One metric = one computation path. Both chart overlays and backtest indicators
 * consume the same data through this interface.
 */
public interface MarketData {
    String symbol();
    String timeframe();
    List<Candle> candles();
    List<AggTrade> aggTrades();           // nullable
    List<FundingRate> fundingRates();      // nullable
    List<OpenInterest> openInterest();     // nullable
    List<PremiumIndex> premiumIndex();     // nullable
    List<FearGreedIndex> fearGreedIndex(); // nullable
    Map<Long, double[]> dailyProfiles();   // nullable — dayStartMs → {poc, vah, val}
    List<SpectrumWindow> spectrumWindows();  // nullable — 10s trade size distribution windows
}
