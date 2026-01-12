package com.tradery.ui.coordination;

import com.tradery.ApplicationContext;
import com.tradery.data.*;
import com.tradery.engine.BacktestEngine;
import com.tradery.data.PreloadScheduler;
import com.tradery.io.PhaseStore;
import com.tradery.io.ResultStore;
import com.tradery.model.*;
import com.tradery.ui.charts.ChartConfig;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Coordinates backtest execution, running in background with progress updates.
 * Extracted from ProjectWindow to reduce complexity.
 */
public class BacktestCoordinator {

    private final BacktestEngine backtestEngine;
    private final CandleStore candleStore;
    private final AggTradesStore aggTradesStore;
    private final FundingRateStore fundingRateStore;
    private final OpenInterestStore openInterestStore;
    private final ResultStore resultStore;

    // Data requirements tracker
    private final DataRequirementsTracker tracker = new DataRequirementsTracker();

    // Current data
    private List<Candle> currentCandles;
    private List<AggTrade> currentAggTrades;
    private List<FundingRate> currentFundingRates;
    private List<OpenInterest> currentOpenInterest;

    // Callbacks
    private BiConsumer<Integer, String> onProgress;
    private Consumer<BacktestResult> onComplete;
    private Consumer<String> onError;
    private Consumer<String> onStatus;
    private BiConsumer<String, String> onDataStatus; // (dataType, status: "loading"/"ready"/"error")
    private Consumer<String> onViewDataReady; // Callback when VIEW data (Funding/OI) becomes ready for chart refresh
    private DataLoadingProgressCallback onDataLoadingProgress; // (dataType, loaded, expected)

    /**
     * Callback for data loading progress updates.
     */
    @FunctionalInterface
    public interface DataLoadingProgressCallback {
        void onProgress(String dataType, int loaded, int expected);
    }

    public BacktestCoordinator(BacktestEngine backtestEngine, CandleStore candleStore,
                               AggTradesStore aggTradesStore, FundingRateStore fundingRateStore,
                               OpenInterestStore openInterestStore, ResultStore resultStore) {
        this.backtestEngine = backtestEngine;
        this.candleStore = candleStore;
        this.aggTradesStore = aggTradesStore;
        this.fundingRateStore = fundingRateStore;
        this.openInterestStore = openInterestStore;
        this.resultStore = resultStore;
    }

    public AggTradesStore getAggTradesStore() {
        return aggTradesStore;
    }

    public void setOnProgress(BiConsumer<Integer, String> callback) {
        this.onProgress = callback;
    }

    public void setOnComplete(Consumer<BacktestResult> callback) {
        this.onComplete = callback;
    }

    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }

    public void setOnStatus(Consumer<String> callback) {
        this.onStatus = callback;
    }

    public void setOnDataStatus(BiConsumer<String, String> callback) {
        this.onDataStatus = callback;
    }

    /**
     * Set callback for when VIEW tier data (Funding/OI) becomes ready.
     * Used to trigger chart refresh without re-running backtest.
     */
    public void setOnViewDataReady(Consumer<String> callback) {
        this.onViewDataReady = callback;
    }

    /**
     * Set callback for data loading progress updates.
     * Called periodically during data fetch with (dataType, loaded, expected).
     */
    public void setOnDataLoadingProgress(DataLoadingProgressCallback callback) {
        this.onDataLoadingProgress = callback;
    }

    /**
     * Get the data requirements tracker for status monitoring.
     */
    public DataRequirementsTracker getTracker() {
        return tracker;
    }

    public List<Candle> getCurrentCandles() {
        return currentCandles;
    }

    public List<AggTrade> getCurrentAggTrades() {
        return currentAggTrades;
    }

    public List<FundingRate> getCurrentFundingRates() {
        return currentFundingRates;
    }

    public List<OpenInterest> getCurrentOpenInterest() {
        return currentOpenInterest;
    }

    public com.tradery.indicators.IndicatorEngine getIndicatorEngine() {
        return backtestEngine.getIndicatorEngine();
    }

    /**
     * Get sync status for the given symbol and time range.
     */
    public AggTradesStore.SyncStatus getSyncStatus(String symbol, long startTime, long endTime) {
        if (aggTradesStore == null) return null;
        return aggTradesStore.getSyncStatus(symbol, startTime, endTime);
    }

    /**
     * Check if required data is available for a backtest.
     * Auto-detects requirements from DSL conditions - no manual checkbox needed.
     * Returns null if all data is available, otherwise returns an error message.
     */
    public String checkDataRequirements(Strategy strategy, String symbol, String resolution, long durationMillis) {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - durationMillis;

        int subMinuteInterval = SubMinuteCandleGenerator.parseSubMinuteInterval(resolution);
        boolean isSubMinute = subMinuteInterval > 0;
        // Auto-detect from DSL: DELTA, CUM_DELTA, WHALE_*, LARGE_TRADE_COUNT
        boolean needsAggTrades = isSubMinute || strategy.requiresAggTrades();

        if (needsAggTrades && aggTradesStore != null) {
            AggTradesStore.SyncStatus status = aggTradesStore.getSyncStatus(symbol, startTime, endTime);
            if (!status.hasData()) {
                String reason;
                if (isSubMinute) {
                    reason = "Sub-minute timeframe (" + resolution + ")";
                } else {
                    reason = "Strategy uses delta/whale indicators";
                }
                return reason + " requires aggTrades data.\n\n" +
                       "Status: " + status.getStatusMessage() + "\n\n" +
                       "Use 'Manage Data' > 'Fetch New' to sync aggTrades first.";
            }
        }

        return null; // All requirements met
    }

    /**
     * Run a backtest with the given strategy and configuration.
     * Uses two-tier data loading:
     * - TRADING tier: Blocks until ready (OHLC, AggTrades for sub-minute/delta)
     * - VIEW tier: Loads in background for charts (Funding, OI)
     *
     * @param strategy The strategy to backtest
     * @param symbol Trading symbol (e.g., "BTCUSDT")
     * @param resolution Timeframe (e.g., "1h")
     * @param durationMillis Duration to backtest in milliseconds
     * @param capital Initial capital
     */
    public void runBacktest(Strategy strategy, String symbol, String resolution,
                            long durationMillis, double capital) {

        PositionSizingType sizingType = strategy.getPositionSizingType();
        double sizingValue = strategy.getPositionSizingValue();
        double commission = strategy.getTotalCommission();

        long endTime = System.currentTimeMillis();
        long startTime = endTime - durationMillis;

        // Check if sub-minute timeframe (requires aggTrades)
        int subMinuteInterval = SubMinuteCandleGenerator.parseSubMinuteInterval(resolution);
        boolean isSubMinute = subMinuteInterval > 0;

        // Auto-detect if aggTrades needed (from DSL conditions or sub-minute timeframe)
        boolean needsAggTrades = isSubMinute || strategy.requiresAggTrades();

        // Collect data requirements
        collectDataRequirements(strategy, symbol, resolution, startTime, endTime, isSubMinute, needsAggTrades);

        BacktestConfig config = new BacktestConfig(
            symbol,
            resolution,
            startTime,
            endTime,
            capital,
            sizingType,
            sizingValue,
            commission
        );

        reportProgress(0, "Starting...");

        // Pause background preloading while we're doing a trading-tier load
        PreloadScheduler preloader = ApplicationContext.getInstance().getPreloadScheduler();
        if (preloader != null) {
            preloader.pauseForTradingLoad();
        }

        SwingWorker<BacktestResult, BacktestEngine.Progress> worker = new SwingWorker<>() {
            @Override
            protected BacktestResult doInBackground() throws Exception {
                // ===== TRADING TIER: Load data required for backtest (blocking) =====

                // For sub-minute timeframes, load aggTrades first and generate candles
                if (isSubMinute) {
                    publish(new BacktestEngine.Progress(0, 0, 0, "Loading aggTrades for " + resolution + " candles..."));
                    updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.FETCHING);

                    if (aggTradesStore == null) {
                        updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.ERROR);
                        throw new Exception("AggTrades store not available for sub-minute timeframes");
                    }

                    // Set progress callback
                    aggTradesStore.setProgressCallback(progress -> {
                        publish(new BacktestEngine.Progress(
                            progress.percentComplete(),
                            0, 0,
                            progress.message()
                        ));
                    });

                    try {
                        currentAggTrades = aggTradesStore.getAggTrades(symbol, startTime, endTime);
                    } finally {
                        aggTradesStore.setProgressCallback(null);
                    }

                    if (currentAggTrades == null || currentAggTrades.isEmpty()) {
                        updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.ERROR);
                        throw new Exception("No aggTrades data available for " + config.symbol() +
                            ". Check your internet connection or try a shorter duration.");
                    }
                    updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.READY);

                    // Generate candles from aggTrades
                    publish(new BacktestEngine.Progress(0, 0, 0, "Generating " + resolution + " candles..."));
                    updateTrackerStatus("OHLC:" + resolution, DataRequirementsTracker.Status.FETCHING);
                    SubMinuteCandleGenerator generator = new SubMinuteCandleGenerator();
                    currentCandles = generator.generate(currentAggTrades, subMinuteInterval, startTime, endTime);

                    if (currentCandles.isEmpty()) {
                        updateTrackerStatus("OHLC:" + resolution, DataRequirementsTracker.Status.ERROR);
                        throw new Exception("No candles generated from aggTrades data");
                    }
                    updateTrackerStatus("OHLC:" + resolution, DataRequirementsTracker.Status.READY);

                    System.out.println("Generated " + currentCandles.size() + " " + resolution + " candles from " +
                        currentAggTrades.size() + " aggTrades");

                } else {
                    // Standard timeframe - fetch candles from Binance
                    publish(new BacktestEngine.Progress(0, 0, 0, "Fetching data from Binance..."));
                    updateTrackerStatus("OHLC:" + resolution, DataRequirementsTracker.Status.FETCHING);

                    currentCandles = candleStore.getCandles(
                        config.symbol(),
                        config.resolution(),
                        config.startDate(),
                        config.endDate()
                    );

                    if (currentCandles.isEmpty()) {
                        updateTrackerStatus("OHLC:" + resolution, DataRequirementsTracker.Status.ERROR);
                        throw new Exception("No candle data available for " + config.symbol());
                    }
                    updateTrackerStatus("OHLC:" + resolution, DataRequirementsTracker.Status.READY);

                    // Load aggTrades if orderflow mode is enabled (TRADING tier for delta indicators)
                    currentAggTrades = null;
                    if (needsAggTrades && aggTradesStore != null) {
                        publish(new BacktestEngine.Progress(0, 0, 0, "Loading aggTrades data..."));
                        updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.FETCHING);
                        try {
                            aggTradesStore.setProgressCallback(progress -> {
                                publish(new BacktestEngine.Progress(
                                    progress.percentComplete(),
                                    0, 0,
                                    progress.message()
                                ));
                            });
                            currentAggTrades = aggTradesStore.getAggTrades(symbol, startTime, endTime);
                            aggTradesStore.setProgressCallback(null);
                            updateTrackerStatus("AggTrades",
                                currentAggTrades != null && !currentAggTrades.isEmpty()
                                    ? DataRequirementsTracker.Status.READY
                                    : DataRequirementsTracker.Status.ERROR);
                        } catch (Exception e) {
                            System.err.println("Failed to load aggTrades: " + e.getMessage());
                            aggTradesStore.setProgressCallback(null);
                            updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.ERROR);
                        }
                    }
                }

                // Load phases (always TRADING - needed for filtering)
                List<Phase> allPhases = loadPhases(strategy);

                // Load funding rates - TRADING if DSL uses FUNDING, otherwise VIEW
                boolean dslUsesFunding = strategy.requiresFunding();
                currentFundingRates = null;
                if (fundingRateStore != null) {
                    if (dslUsesFunding) {
                        // TRADING tier - load synchronously
                        publish(new BacktestEngine.Progress(0, 0, 0, "Loading funding rate data..."));
                        updateTrackerStatus("Funding", DataRequirementsTracker.Status.FETCHING);
                        try {
                            currentFundingRates = fundingRateStore.getFundingRates(symbol, startTime, endTime);
                            updateTrackerStatus("Funding",
                                currentFundingRates != null && !currentFundingRates.isEmpty()
                                    ? DataRequirementsTracker.Status.READY
                                    : DataRequirementsTracker.Status.ERROR);
                        } catch (Exception e) {
                            System.err.println("Failed to load funding rates: " + e.getMessage());
                            updateTrackerStatus("Funding", DataRequirementsTracker.Status.ERROR);
                        }
                    }
                    // VIEW tier loading happens after backtest starts
                }

                // Load OI - TRADING if DSL uses OI, otherwise VIEW
                boolean dslUsesOI = strategy.requiresOpenInterest();
                currentOpenInterest = null;
                if (openInterestStore != null && dslUsesOI) {
                    // TRADING tier - load synchronously
                    try {
                        long now = System.currentTimeMillis();
                        long maxOiHistory = 30L * 24 * 60 * 60 * 1000;
                        long oiStartTime = Math.max(startTime, now - maxOiHistory);

                        if (oiStartTime < endTime) {
                            publish(new BacktestEngine.Progress(0, 0, 0, "Loading OI data..."));
                            updateTrackerStatus("OI", DataRequirementsTracker.Status.FETCHING);
                            currentOpenInterest = openInterestStore.getOpenInterest(symbol, oiStartTime, endTime,
                                msg -> publish(new BacktestEngine.Progress(0, 0, 0, msg)));
                            System.out.println("Loaded " + (currentOpenInterest != null ? currentOpenInterest.size() : 0) + " OI records");
                            updateTrackerStatus("OI",
                                currentOpenInterest != null && !currentOpenInterest.isEmpty()
                                    ? DataRequirementsTracker.Status.READY
                                    : DataRequirementsTracker.Status.ERROR);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to load open interest: " + e.getMessage());
                        e.printStackTrace();
                        updateTrackerStatus("OI", DataRequirementsTracker.Status.ERROR);
                    }
                }

                // Pass orderflow data to engine
                backtestEngine.setAggTrades(currentAggTrades);
                backtestEngine.setFundingRates(currentFundingRates);
                backtestEngine.setOpenInterest(currentOpenInterest);

                // Run backtest with phase filtering
                BacktestResult result = backtestEngine.run(strategy, config, currentCandles, allPhases, this::publish);

                // ===== VIEW TIER: Start async loading for chart-only data =====
                loadViewDataAsync(symbol, startTime, endTime, dslUsesFunding, dslUsesOI);

                return result;
            }

            @Override
            protected void process(List<BacktestEngine.Progress> chunks) {
                BacktestEngine.Progress latest = chunks.get(chunks.size() - 1);
                reportProgress(latest.percentage(), latest.message());
                reportStatus(latest.message());
            }

            @Override
            protected void done() {
                // Resume background preloading
                PreloadScheduler scheduler = ApplicationContext.getInstance().getPreloadScheduler();
                if (scheduler != null) {
                    scheduler.resumeAfterTradingLoad();
                }

                try {
                    BacktestResult result = get();

                    // Save result to per-project storage
                    resultStore.save(result);

                    // Record coverage to inventory after successful load
                    DataInventory inventory = ApplicationContext.getInstance().getDataInventory();
                    if (inventory != null) {
                        inventory.recordCandleData(symbol, resolution, startTime, endTime);
                        if (currentAggTrades != null && !currentAggTrades.isEmpty()) {
                            inventory.recordAggTradesData(symbol, startTime, endTime);
                        }
                        if (currentFundingRates != null && !currentFundingRates.isEmpty()) {
                            inventory.recordFundingData(symbol, startTime, endTime);
                        }
                        if (currentOpenInterest != null && !currentOpenInterest.isEmpty()) {
                            inventory.recordOIData(symbol, startTime, endTime);
                        }
                        // Save inventory periodically
                        inventory.save();
                    }

                    // Report completion
                    if (onComplete != null) {
                        onComplete.accept(result);
                    }

                    reportProgress(100, "Complete");
                    reportStatus(result.getSummary());

                } catch (Exception e) {
                    reportProgress(0, "Error");
                    String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    reportStatus("Error: " + errorMsg);
                    if (onError != null) {
                        onError.accept(errorMsg);
                    }
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    /**
     * Collect data requirements for a backtest session.
     */
    private void collectDataRequirements(Strategy strategy, String symbol, String resolution,
                                          long startTime, long endTime,
                                          boolean isSubMinute, boolean needsAggTrades) {
        tracker.clear();
        ChartConfig chartConfig = ChartConfig.getInstance();

        // OHLC is always TRADING
        String ohlcDataType = "OHLC:" + resolution;
        tracker.addRequirement(new DataRequirement(
            ohlcDataType, symbol, startTime, endTime,
            DataRequirement.Tier.TRADING, "strategy"
        ));

        // AggTrades: TRADING if sub-minute or DSL uses delta
        if (needsAggTrades) {
            tracker.addRequirement(new DataRequirement(
                "AggTrades", symbol, startTime, endTime,
                DataRequirement.Tier.TRADING,
                isSubMinute ? "strategy:subminute" : "strategy:delta"
            ));
        }

        // Funding: TRADING if DSL uses it, VIEW if chart enabled
        boolean dslUsesFunding = strategy.requiresFunding();
        if (dslUsesFunding) {
            tracker.addRequirement(new DataRequirement(
                "Funding", symbol, startTime, endTime,
                DataRequirement.Tier.TRADING, "strategy:dsl"
            ));
        } else if (chartConfig.isFundingEnabled()) {
            tracker.addRequirement(new DataRequirement(
                "Funding", symbol, startTime, endTime,
                DataRequirement.Tier.VIEW, "chart:funding"
            ));
        }

        // OI: TRADING if DSL uses it, VIEW if chart enabled
        boolean dslUsesOI = strategy.requiresOpenInterest();
        long now = System.currentTimeMillis();
        long maxOiHistory = 30L * 24 * 60 * 60 * 1000;
        long oiStartTime = Math.max(startTime, now - maxOiHistory);

        if (dslUsesOI) {
            tracker.addRequirement(new DataRequirement(
                "OI", symbol, oiStartTime, endTime,
                DataRequirement.Tier.TRADING, "strategy:dsl"
            ));
        } else if (chartConfig.isOiEnabled()) {
            tracker.addRequirement(new DataRequirement(
                "OI", symbol, oiStartTime, endTime,
                DataRequirement.Tier.VIEW, "chart:oi"
            ));
        }

        // Collect phase timeframes (TRADING)
        Set<String> phaseTimeframes = collectPhaseTimeframes(strategy);
        for (String tf : phaseTimeframes) {
            if (!tf.equals(resolution)) {
                tracker.addRequirement(new DataRequirement(
                    "OHLC:" + tf, symbol, startTime, endTime,
                    DataRequirement.Tier.TRADING, "phase"
                ));
            }
        }
    }

    /**
     * Load VIEW tier data asynchronously (for charts only).
     */
    private void loadViewDataAsync(String symbol, long startTime, long endTime,
                                    boolean dslUsesFunding, boolean dslUsesOI) {
        ChartConfig chartConfig = ChartConfig.getInstance();

        // Check if any orderflow charts need aggTrades
        boolean orderflowChartsEnabled = chartConfig.isDeltaEnabled() ||
                                         chartConfig.isCvdEnabled() ||
                                         chartConfig.isVolumeRatioEnabled() ||
                                         chartConfig.isWhaleEnabled() ||
                                         chartConfig.isRetailEnabled();

        // AggTrades: load async if orderflow charts are enabled but DSL doesn't use delta functions
        // (if DSL uses delta, aggTrades would already be loaded in TRADING tier)
        if (orderflowChartsEnabled && currentAggTrades == null && aggTradesStore != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("AggTrades (async): Loading for orderflow charts...");
                    updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.FETCHING);
                    aggTradesStore.setProgressCallback(progress -> {
                        System.out.println("AggTrades (async): " + progress.message());
                    });
                    List<AggTrade> trades = aggTradesStore.getAggTrades(symbol, startTime, endTime);
                    aggTradesStore.setProgressCallback(null);

                    if (trades != null && !trades.isEmpty()) {
                        currentAggTrades = trades;
                        // Update the indicator engine with aggTrades
                        backtestEngine.setAggTrades(trades);
                        backtestEngine.getIndicatorEngine().setAggTrades(trades);
                        System.out.println("AggTrades (async): Loaded " + trades.size() + " trades for orderflow charts");
                        updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.READY);
                        notifyViewDataReady("AggTrades");
                    } else {
                        System.out.println("AggTrades (async): No data available");
                        updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.ERROR);
                    }
                } catch (Exception e) {
                    System.err.println("AggTrades (async): Failed to load - " + e.getMessage());
                    updateTrackerStatus("AggTrades", DataRequirementsTracker.Status.ERROR);
                }
            });
        }

        // Funding: load async if chart enabled but DSL doesn't use it
        if (chartConfig.isFundingEnabled() && !dslUsesFunding && fundingRateStore != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("Funding (async): Loading funding rates...");
                    updateTrackerStatus("Funding", DataRequirementsTracker.Status.FETCHING);
                    List<FundingRate> rates = fundingRateStore.getFundingRates(symbol, startTime, endTime);
                    if (rates != null && !rates.isEmpty()) {
                        currentFundingRates = rates;
                        // Update the indicator engine with funding rates
                        backtestEngine.setFundingRates(rates);
                        backtestEngine.getIndicatorEngine().setFundingRates(rates);
                        System.out.println("Funding (async): Loaded " + rates.size() + " funding rates");
                        updateTrackerStatus("Funding", DataRequirementsTracker.Status.READY);
                        notifyViewDataReady("Funding");
                    } else {
                        System.out.println("Funding (async): No data available");
                        updateTrackerStatus("Funding", DataRequirementsTracker.Status.ERROR);
                    }
                } catch (Exception e) {
                    System.err.println("Funding (async): Failed to load - " + e.getMessage());
                    updateTrackerStatus("Funding", DataRequirementsTracker.Status.ERROR);
                }
            });
        }

        // OI: load async if chart enabled but DSL doesn't use it
        if (chartConfig.isOiEnabled() && !dslUsesOI && openInterestStore != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    long now = System.currentTimeMillis();
                    long maxOiHistory = 30L * 24 * 60 * 60 * 1000;
                    long oiStartTime = Math.max(startTime, now - maxOiHistory);

                    if (oiStartTime < endTime) {
                        updateTrackerStatus("OI", DataRequirementsTracker.Status.FETCHING);
                        List<OpenInterest> oi = openInterestStore.getOpenInterest(symbol, oiStartTime, endTime,
                            msg -> {
                                System.out.println("OI (async): " + msg);
                                // Parse progress from message like "Fetching OI: 145/144 (100%)"
                                if (msg.contains("/")) {
                                    try {
                                        String[] parts = msg.split("\\s+");
                                        for (String part : parts) {
                                            if (part.contains("/")) {
                                                String[] counts = part.split("/");
                                                int loaded = Integer.parseInt(counts[0]);
                                                int expected = Integer.parseInt(counts[1]);
                                                reportDataLoadingProgress("OI", loaded, expected);
                                                break;
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                            });
                        if (oi != null && !oi.isEmpty()) {
                            currentOpenInterest = oi;
                            // Update the indicator engine with OI data
                            backtestEngine.setOpenInterest(oi);
                            backtestEngine.getIndicatorEngine().setOpenInterest(oi);
                            System.out.println("OI (async): Loaded " + oi.size() + " OI records");
                            updateTrackerStatus("OI", DataRequirementsTracker.Status.READY);
                            reportDataLoadingProgress("OI", -1, -1); // Hide progress bar
                            notifyViewDataReady("OI");
                        } else {
                            System.out.println("OI (async): No data available");
                            updateTrackerStatus("OI", DataRequirementsTracker.Status.ERROR);
                            reportDataLoadingProgress("OI", -1, -1); // Hide progress bar
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load OI (async): " + e.getMessage());
                    updateTrackerStatus("OI", DataRequirementsTracker.Status.ERROR);
                    reportDataLoadingProgress("OI", -1, -1); // Hide progress bar
                }
            });
        }
    }

    /**
     * Collect all unique timeframes from required phases.
     */
    private Set<String> collectPhaseTimeframes(Strategy strategy) {
        Set<String> timeframes = new HashSet<>();
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();

        for (String phaseId : strategy.getRequiredPhaseIds()) {
            Phase phase = phaseStore.load(phaseId);
            if (phase != null && phase.getTimeframe() != null) {
                timeframes.add(phase.getTimeframe());
            }
        }
        for (String phaseId : strategy.getExcludedPhaseIds()) {
            Phase phase = phaseStore.load(phaseId);
            if (phase != null && phase.getTimeframe() != null) {
                timeframes.add(phase.getTimeframe());
            }
        }
        // Also from exit zones
        for (ExitZone zone : strategy.getExitZones()) {
            for (String phaseId : zone.requiredPhaseIds()) {
                Phase phase = phaseStore.load(phaseId);
                if (phase != null && phase.getTimeframe() != null) {
                    timeframes.add(phase.getTimeframe());
                }
            }
            for (String phaseId : zone.excludedPhaseIds()) {
                Phase phase = phaseStore.load(phaseId);
                if (phase != null && phase.getTimeframe() != null) {
                    timeframes.add(phase.getTimeframe());
                }
            }
        }
        return timeframes;
    }

    /**
     * Load all phases for strategy filtering.
     */
    private List<Phase> loadPhases(Strategy strategy) {
        List<Phase> allPhases = new ArrayList<>();
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();

        for (String phaseId : strategy.getRequiredPhaseIds()) {
            Phase phase = phaseStore.load(phaseId);
            if (phase != null) {
                allPhases.add(phase);
            }
        }
        for (String phaseId : strategy.getExcludedPhaseIds()) {
            Phase phase = phaseStore.load(phaseId);
            if (phase != null && !allPhases.contains(phase)) {
                allPhases.add(phase);
            }
        }
        for (ExitZone zone : strategy.getExitZones()) {
            for (String phaseId : zone.requiredPhaseIds()) {
                Phase phase = phaseStore.load(phaseId);
                if (phase != null && !allPhases.contains(phase)) {
                    allPhases.add(phase);
                }
            }
            for (String phaseId : zone.excludedPhaseIds()) {
                Phase phase = phaseStore.load(phaseId);
                if (phase != null && !allPhases.contains(phase)) {
                    allPhases.add(phase);
                }
            }
        }
        return allPhases;
    }

    /**
     * Update tracker status and notify UI.
     */
    private void updateTrackerStatus(String dataType, DataRequirementsTracker.Status status) {
        tracker.updateStatus(dataType, status);

        // Also report to legacy callback for UI compatibility
        String legacyStatus = switch (status) {
            case PENDING, CHECKING -> "loading";
            case FETCHING -> "loading";
            case READY -> "ready";
            case ERROR -> "error";
        };
        reportDataStatus(dataType.split(":")[0], legacyStatus); // Strip timeframe for UI
    }

    /**
     * Notify that VIEW data is ready for chart refresh.
     */
    private void notifyViewDataReady(String dataType) {
        if (onViewDataReady != null) {
            SwingUtilities.invokeLater(() -> onViewDataReady.accept(dataType));
        }
    }

    private void reportProgress(int percentage, String message) {
        if (onProgress != null) {
            onProgress.accept(percentage, message);
        }
    }

    private void reportStatus(String message) {
        if (onStatus != null) {
            onStatus.accept(message);
        }
    }

    private void reportDataStatus(String dataType, String status) {
        if (onDataStatus != null) {
            SwingUtilities.invokeLater(() -> onDataStatus.accept(dataType, status));
        }
    }

    /**
     * Report data loading progress for UI progress bar.
     */
    private void reportDataLoadingProgress(String dataType, int loaded, int expected) {
        if (onDataLoadingProgress != null) {
            SwingUtilities.invokeLater(() -> onDataLoadingProgress.onProgress(dataType, loaded, expected));
        }
    }

    /**
     * Parse duration string to milliseconds.
     */
    public static long parseDurationMillis(String duration) {
        if (duration == null) return 365L * 24 * 60 * 60 * 1000; // Default 1 year

        long hour = 60L * 60 * 1000;
        long day = 24 * hour;
        return switch (duration) {
            case "1 hour" -> hour;
            case "3 hours" -> 3 * hour;
            case "6 hours" -> 6 * hour;
            case "12 hours" -> 12 * hour;
            case "1 day" -> day;
            case "3 days" -> 3 * day;
            case "1 week" -> 7 * day;
            case "2 weeks" -> 14 * day;
            case "4 weeks" -> 28 * day;
            case "1 month" -> 30 * day;
            case "2 months" -> 60 * day;
            case "3 months" -> 90 * day;
            case "6 months" -> 180 * day;
            case "1 year" -> 365 * day;
            case "2 years" -> 730 * day;
            case "3 years" -> 1095 * day;
            case "5 years" -> 1825 * day;
            case "10 years" -> 3650 * day;
            default -> 365 * day;
        };
    }
}
