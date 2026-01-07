package com.tradery.ui.coordination;

import com.tradery.ApplicationContext;
import com.tradery.data.CandleStore;
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
    private final ResultStore resultStore;

    // Current data
    private List<Candle> currentCandles;

    // Callbacks
    private BiConsumer<Integer, String> onProgress;
    private Consumer<BacktestResult> onComplete;
    private Consumer<String> onError;
    private Consumer<String> onStatus;

    public BacktestCoordinator(BacktestEngine backtestEngine, CandleStore candleStore, ResultStore resultStore) {
        this.backtestEngine = backtestEngine;
        this.candleStore = candleStore;
        this.resultStore = resultStore;
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
                // Fetch candles
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

                // Load required phases
                List<Phase> requiredPhases = new ArrayList<>();
                PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
                for (String phaseId : strategy.getRequiredPhaseIds()) {
                    Phase phase = phaseStore.load(phaseId);
                    if (phase != null) {
                        requiredPhases.add(phase);
                    }
                }

                // Run backtest with phase filtering
                return backtestEngine.run(strategy, config, currentCandles, requiredPhases, this::publish);
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

        long day = 24L * 60 * 60 * 1000;
        return switch (duration) {
            case "1 day" -> day;
            case "3 days" -> 3 * day;
            case "1 week" -> 7 * day;
            case "2 weeks" -> 14 * day;
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
