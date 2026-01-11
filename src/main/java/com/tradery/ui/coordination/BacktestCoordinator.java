package com.tradery.ui.coordination;

import com.tradery.ApplicationContext;
import com.tradery.data.AggTradesStore;
import com.tradery.data.CandleStore;
import com.tradery.data.FundingRateStore;
import com.tradery.data.SubMinuteCandleGenerator;
import com.tradery.engine.BacktestEngine;
import com.tradery.io.PhaseStore;
import com.tradery.io.ResultStore;
import com.tradery.model.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
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
    private final ResultStore resultStore;

    // Current data
    private List<Candle> currentCandles;
    private List<AggTrade> currentAggTrades;
    private List<FundingRate> currentFundingRates;

    // Callbacks
    private BiConsumer<Integer, String> onProgress;
    private Consumer<BacktestResult> onComplete;
    private Consumer<String> onError;
    private Consumer<String> onStatus;

    public BacktestCoordinator(BacktestEngine backtestEngine, CandleStore candleStore,
                               AggTradesStore aggTradesStore, FundingRateStore fundingRateStore,
                               ResultStore resultStore) {
        this.backtestEngine = backtestEngine;
        this.candleStore = candleStore;
        this.aggTradesStore = aggTradesStore;
        this.fundingRateStore = fundingRateStore;
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

    public List<Candle> getCurrentCandles() {
        return currentCandles;
    }

    public List<AggTrade> getCurrentAggTrades() {
        return currentAggTrades;
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
     * Run a backtest with the given strategy and configuration.
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

        // Check if strategy has orderflow enabled (for loading aggTrades if available)
        // Sub-minute timeframes always need aggTrades
        boolean needsAggTrades = isSubMinute || strategy.getOrderflowMode() == OrderflowSettings.Mode.ENABLED;

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

        SwingWorker<BacktestResult, BacktestEngine.Progress> worker = new SwingWorker<>() {
            @Override
            protected BacktestResult doInBackground() throws Exception {
                // For sub-minute timeframes, load aggTrades first and generate candles
                if (isSubMinute) {
                    publish(new BacktestEngine.Progress(0, 0, 0, "Loading aggTrades for " + resolution + " candles..."));

                    if (aggTradesStore == null) {
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
                        throw new Exception("No aggTrades data available for " + config.symbol() +
                            ". Enable orderflow and sync data first.");
                    }

                    // Generate candles from aggTrades
                    publish(new BacktestEngine.Progress(0, 0, 0, "Generating " + resolution + " candles..."));
                    SubMinuteCandleGenerator generator = new SubMinuteCandleGenerator();
                    currentCandles = generator.generate(currentAggTrades, subMinuteInterval, startTime, endTime);

                    if (currentCandles.isEmpty()) {
                        throw new Exception("No candles generated from aggTrades data");
                    }

                    System.out.println("Generated " + currentCandles.size() + " " + resolution + " candles from " +
                        currentAggTrades.size() + " aggTrades");

                } else {
                    // Standard timeframe - fetch candles from Binance
                    publish(new BacktestEngine.Progress(0, 0, 0, "Fetching data from Binance..."));

                    currentCandles = candleStore.getCandles(
                        config.symbol(),
                        config.resolution(),
                        config.startDate(),
                        config.endDate()
                    );

                    if (currentCandles.isEmpty()) {
                        throw new Exception("No candle data available for " + config.symbol());
                    }

                    // Load aggTrades if orderflow mode is enabled
                    currentAggTrades = null;
                    if (needsAggTrades && aggTradesStore != null) {
                        publish(new BacktestEngine.Progress(0, 0, 0, "Loading aggTrades data..."));
                        try {
                            // Set progress callback to show aggTrades loading in status bar
                            aggTradesStore.setProgressCallback(progress -> {
                                publish(new BacktestEngine.Progress(
                                    progress.percentComplete(),
                                    0, 0,
                                    progress.message()
                                ));
                            });
                            currentAggTrades = aggTradesStore.getAggTrades(symbol, startTime, endTime);
                            aggTradesStore.setProgressCallback(null);
                        } catch (Exception e) {
                            // Log warning but continue - delta indicators won't work
                            System.err.println("Failed to load aggTrades: " + e.getMessage());
                            aggTradesStore.setProgressCallback(null);
                        }
                    }
                }

                // Always load funding rates (needed for FUNDING DSL function and chart)
                currentFundingRates = null;
                if (fundingRateStore != null) {
                    publish(new BacktestEngine.Progress(0, 0, 0, "Loading funding rate data..."));
                    try {
                        currentFundingRates = fundingRateStore.getFundingRates(symbol, startTime, endTime);
                    } catch (Exception e) {
                        System.err.println("Failed to load funding rates: " + e.getMessage());
                    }
                }

                // Load required and excluded phases (strategy-level)
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
                // Also load phases referenced by exit zones
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

                // Pass orderflow data to engine if available
                backtestEngine.setAggTrades(currentAggTrades);
                backtestEngine.setFundingRates(currentFundingRates);

                // Run backtest with phase filtering
                return backtestEngine.run(strategy, config, currentCandles, allPhases, this::publish);
            }

            @Override
            protected void process(List<BacktestEngine.Progress> chunks) {
                BacktestEngine.Progress latest = chunks.get(chunks.size() - 1);
                reportProgress(latest.percentage(), latest.message());
                reportStatus(latest.message());
            }

            @Override
            protected void done() {
                try {
                    BacktestResult result = get();

                    // Save result to per-project storage
                    resultStore.save(result);

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
