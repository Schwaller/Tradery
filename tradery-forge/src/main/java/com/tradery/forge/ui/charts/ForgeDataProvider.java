package com.tradery.forge.ui.charts;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;
import com.tradery.core.model.MarketDataSnapshot;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Forge implementation of ChartDataProvider.
 * Wraps IndicatorDataService for async indicator data and provides
 * access to IndicatorPool for async computations.
 *
 * <p>This bridges the tradery-charts module with forge's page-based
 * indicator management system.</p>
 */
public class ForgeDataProvider implements ChartDataProvider {

    private static final Logger log = LoggerFactory.getLogger(ForgeDataProvider.class);

    private final IndicatorDataService indicatorDataService;
    private final IndicatorPool indicatorPool = new IndicatorPool();
    private final ExecutorService profileFetcher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DailyProfile-Fetch");
        t.setDaemon(true);
        return t;
    });
    private String symbol = "";
    private String timeframe = "";
    private long startTime;
    private long endTime;

    public ForgeDataProvider(IndicatorDataService indicatorDataService) {
        this.indicatorDataService = indicatorDataService;
    }

    /**
     * Set the full data context.
     * Call this when candles, symbol, or timeframe change.
     */
    public void setDataContext(List<Candle> candles, String symbol, String timeframe,
                                long startTime, long endTime) {
        this.symbol = symbol != null ? symbol : "";
        this.timeframe = timeframe != null ? timeframe : "";
        this.startTime = startTime;
        this.endTime = endTime;

        // Create indicator engine via MarketDataSnapshot and pass to pool
        if (candles != null && !candles.isEmpty()) {
            MarketDataSnapshot md = MarketDataSnapshot.builder(
                this.symbol, this.timeframe, candles).build();

            IndicatorEngine engine = new IndicatorEngine();
            engine.setMarketData(md);

            // Async fetch of daily profiles from data service (updates engine when ready)
            populateDailyProfiles(engine, symbol, candles);

            indicatorPool.setDataContext(engine);
        } else {
            indicatorPool.setDataContext(null);
        }

        // Update the indicator data service context too
        indicatorDataService.setDataContext(candles, symbol, timeframe, startTime, endTime);
    }

    @Override
    public List<Candle> getCandles() {
        return indicatorDataService.getCandles();
    }

    @Override
    public IndicatorPool getIndicatorPool() {
        return indicatorPool;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public String getTimeframe() {
        return timeframe;
    }

    @Override
    public long getStartTime() {
        if (startTime > 0) {
            return startTime;
        }
        List<Candle> candles = getCandles();
        if (candles != null && !candles.isEmpty()) {
            return candles.get(0).timestamp();
        }
        return 0;
    }

    @Override
    public long getEndTime() {
        if (endTime > 0) {
            return endTime;
        }
        List<Candle> candles = getCandles();
        if (candles != null && !candles.isEmpty()) {
            return candles.get(candles.size() - 1).timestamp();
        }
        return 0;
    }

    // ===== Async indicator subscription (delegates to IndicatorDataService) =====

    @Override
    public void subscribeIndicator(IndicatorType type, int... params) {
        switch (type) {
            case RSI -> {
                if (params.length >= 1) indicatorDataService.subscribeRSI(params[0]);
            }
            case MACD -> {
                if (params.length >= 3) indicatorDataService.subscribeMACD(params[0], params[1], params[2]);
            }
            case ATR -> {
                if (params.length >= 1) indicatorDataService.subscribeATR(params[0]);
            }
            case STOCHASTIC -> {
                if (params.length >= 2) indicatorDataService.subscribeStochastic(params[0], params[1]);
            }
            case ADX -> {
                if (params.length >= 1) indicatorDataService.subscribeADX(params[0]);
            }
            case DELTA -> indicatorDataService.subscribeDelta();
            case CVD -> indicatorDataService.subscribeCumDelta();
            case TRADE_COUNT -> indicatorDataService.subscribeTradeCount();
            default -> {
                // Other types not yet mapped
            }
        }
    }

    @Override
    public void addDataListener(Runnable onDataReady) {
        indicatorDataService.addDataListener(onDataReady);
    }

    @Override
    public void removeDataListener(Runnable listener) {
        indicatorDataService.removeDataListener(listener);
    }

    // ===== Orderflow data (from IndicatorDataService) =====

    @Override
    public double[] getDelta() {
        return indicatorDataService.getDelta();
    }

    @Override
    public double[] getCumulativeDelta() {
        return indicatorDataService.getCumDelta();
    }

    @Override
    public double[] getWhaleDelta(double threshold) {
        return indicatorDataService.getWhaleDelta(threshold);
    }

    /**
     * Populate the IndicatorEngine with precomputed daily profiles from the data service.
     * Runs on a background thread to avoid blocking the UI — the engine falls back to
     * candle-based computation until profiles arrive, then subsequent calls use precomputed data.
     */
    private void populateDailyProfiles(IndicatorEngine engine, String symbol, List<Candle> candles) {
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable() || symbol == null || symbol.isEmpty()) {
            return;
        }

        long start = candles.get(0).timestamp();
        long end = candles.get(candles.size() - 1).timestamp();

        ctx.setProfileStatus(ApplicationContext.ProfileStatus.LOADING,
            "Fetching daily levels for " + symbol, 0);

        profileFetcher.submit(() -> {
            try {
                DataServiceClient client = ctx.getDataServiceClient();
                List<DataServiceClient.DailyLevelsPoint> levels = client.getProfileDailyLevels(symbol, start, end);
                if (levels == null || levels.isEmpty()) {
                    ctx.setProfileStatus(ApplicationContext.ProfileStatus.IDLE,
                        "No daily levels for range", 0);
                    return;
                }

                Map<Long, double[]> profileMap = new HashMap<>();
                for (DataServiceClient.DailyLevelsPoint p : levels) {
                    profileMap.put(p.dayStart(), new double[]{p.poc(), p.vah(), p.val()});
                }

                engine.setPrecomputedDailyProfiles(profileMap);
                ctx.setProfileStatus(ApplicationContext.ProfileStatus.READY,
                    profileMap.size() + " daily levels loaded", profileMap.size());
                log.info("Populated {} precomputed daily profiles for {}", profileMap.size(), symbol);
            } catch (Exception e) {
                ctx.setProfileStatus(ApplicationContext.ProfileStatus.ERROR,
                    e.getMessage(), 0);
                log.warn("Failed to fetch daily profiles from data service for {}, using candle fallback: {}",
                    symbol, e.getMessage());
            }
        });
    }

    /**
     * Update the IndicatorPool with a fully-configured IndicatorEngine.
     * Use this when the backtest engine's IndicatorEngine has additional data
     * (funding, OI, premium, aggTrades) that the pool should use for
     * sourceable chart computations.
     */
    public void setIndicatorEngine(IndicatorEngine engine) {
        indicatorPool.setDataContext(engine);
    }

    /**
     * Get the underlying IndicatorDataService.
     * Useful for accessing forge-specific features.
     */
    public IndicatorDataService getIndicatorDataService() {
        return indicatorDataService;
    }
}
