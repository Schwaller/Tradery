package com.tradery.engine;

import com.tradery.dsl.AstNode;
import com.tradery.dsl.Parser;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main backtesting engine that runs strategies against historical data.
 */
public class BacktestEngine {

    private final IndicatorEngine indicatorEngine;

    public BacktestEngine() {
        this.indicatorEngine = new IndicatorEngine();
    }

    /**
     * Run a backtest for a strategy on historical data
     */
    public BacktestResult run(
            Strategy strategy,
            BacktestConfig config,
            List<Candle> candles,
            Consumer<Progress> onProgress
    ) {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        if (onProgress != null) {
            onProgress.accept(new Progress(0, candles.size(), 0, "Parsing strategy..."));
        }

        // Parse entry and exit conditions
        Parser parser = new Parser();

        Parser.ParseResult entryResult = parser.parse(strategy.getEntry());
        if (!entryResult.success()) {
            return createErrorResult(strategy, config, startTime,
                "Entry condition parse error: " + entryResult.error());
        }

        // Exit condition is optional - if empty, only SL/TP will trigger exits
        String exitCondition = strategy.getExit();
        boolean hasExitCondition = exitCondition != null && !exitCondition.trim().isEmpty();
        Parser.ParseResult exitResult = null;
        if (hasExitCondition) {
            exitResult = parser.parse(exitCondition);
            if (!exitResult.success()) {
                return createErrorResult(strategy, config, startTime,
                    "Exit condition parse error: " + exitResult.error());
            }
        }

        // Initialize indicator engine
        if (onProgress != null) {
            onProgress.accept(new Progress(0, candles.size(), 0, "Calculating indicators..."));
        }

        indicatorEngine.setCandles(candles, config.resolution());

        // Create evaluator
        ConditionEvaluator evaluator = new ConditionEvaluator(indicatorEngine);

        // Calculate warmup period
        int warmupBars = calculateWarmupPeriod(strategy, config);

        // Run simulation
        List<Trade> trades = new ArrayList<>();
        List<OpenTradeState> openTrades = new ArrayList<>();
        double currentEquity = config.initialCapital();
        int lastEntryBar = -9999;  // Track last entry bar for min candle distance

        int maxOpenTrades = strategy.getMaxOpenTrades();
        int minCandlesBetween = strategy.getMinCandlesBetweenTrades();

        if (onProgress != null) {
            onProgress.accept(new Progress(0, candles.size(), 0, "Running backtest..."));
        }

        for (int i = warmupBars; i < candles.size(); i++) {
            Candle candle = candles.get(i);

            // Report progress
            if (i % 500 == 0 || i == candles.size() - 1) {
                int percentage = (int) (((double)(i - warmupBars) / (candles.size() - warmupBars)) * 100);
                if (onProgress != null) {
                    onProgress.accept(new Progress(i, candles.size(), percentage,
                        "Processing bar " + i + " of " + candles.size()));
                }
            }

            try {
                // Check exit conditions for all open trades
                List<OpenTradeState> toClose = new ArrayList<>();
                for (OpenTradeState ots : openTrades) {
                    Trade openTrade = ots.trade;
                    double entryPrice = openTrade.entryPrice();
                    String exitReason = null;
                    double exitPrice = candle.close();

                    String slType = strategy.getStopLossType();
                    Double slValue = strategy.getStopLossValue();
                    String tpType = strategy.getTakeProfitType();
                    Double tpValue = strategy.getTakeProfitValue();

                    // Calculate stop distance based on type
                    double stopDistance = 0;
                    if (slValue != null && !"none".equals(slType)) {
                        if (slType.contains("percent")) {
                            stopDistance = entryPrice * (slValue / 100.0);
                        } else if (slType.contains("atr")) {
                            stopDistance = calculateATR(candles, i, 14) * slValue;
                        }
                    }

                    // Handle trailing stop
                    if (slType != null && slType.startsWith("trailing") && stopDistance > 0) {
                        if (candle.high() > ots.highestPriceSinceEntry) {
                            ots.highestPriceSinceEntry = candle.high();
                            ots.trailingStopPrice = ots.highestPriceSinceEntry - stopDistance;
                        }
                        if (candle.low() <= ots.trailingStopPrice) {
                            exitReason = "trailing_stop";
                            exitPrice = ots.trailingStopPrice;
                        }
                    }
                    // Handle fixed stop-loss
                    else if (slType != null && slType.startsWith("fixed") && stopDistance > 0) {
                        double stopPrice = entryPrice - stopDistance;
                        if (candle.low() <= stopPrice) {
                            exitReason = "stop_loss";
                            exitPrice = stopPrice;
                        }
                    }

                    // Check take-profit
                    if (exitReason == null && tpValue != null && !"none".equals(tpType)) {
                        double tpDistance = 0;
                        if (tpType.contains("percent")) {
                            tpDistance = entryPrice * (tpValue / 100.0);
                        } else if (tpType.contains("atr")) {
                            tpDistance = calculateATR(candles, i, 14) * tpValue;
                        }

                        double tpPrice = entryPrice + tpDistance;
                        if (candle.high() >= tpPrice) {
                            exitReason = "take_profit";
                            exitPrice = tpPrice;
                        }
                    }

                    // Check DSL exit condition (only if one was specified)
                    if (exitReason == null && hasExitCondition) {
                        boolean shouldExit = evaluator.evaluate(exitResult.ast(), i);
                        if (shouldExit) {
                            exitReason = "signal";
                            exitPrice = candle.close();
                        }
                    }

                    if (exitReason != null) {
                        // Mark for closing
                        ots.exitReason = exitReason;
                        ots.exitPrice = exitPrice;
                        toClose.add(ots);
                    }
                }

                // Close marked trades
                for (OpenTradeState ots : toClose) {
                    Trade closedTrade = ots.trade.close(i, candle.timestamp(), ots.exitPrice, config.commission(), ots.exitReason);
                    trades.add(closedTrade);
                    currentEquity += closedTrade.pnl() != null ? closedTrade.pnl() : 0;
                    openTrades.remove(ots);
                }

                // Check entry condition if we can open more trades
                boolean canOpenMore = openTrades.size() < maxOpenTrades;
                boolean passesMinDistance = (i - lastEntryBar) >= minCandlesBetween;

                if (canOpenMore && passesMinDistance) {
                    boolean shouldEnter = evaluator.evaluate(entryResult.ast(), i);

                    if (shouldEnter) {
                        // Calculate position size
                        double quantity = calculatePositionSize(config, strategy, currentEquity, candle.close(), candles, i);

                        if (quantity > 0) {
                            Trade newTrade = Trade.open(
                                strategy.getId(),
                                "long",
                                i,
                                candle.timestamp(),
                                candle.close(),
                                quantity,
                                config.commission()
                            );

                            OpenTradeState ots = new OpenTradeState(newTrade, candle.close());

                            // Initialize trailing stop if enabled
                            String slType = strategy.getStopLossType();
                            Double slValue = strategy.getStopLossValue();
                            if (slType != null && slType.startsWith("trailing") && slValue != null) {
                                double stopDistance = 0;
                                if (slType.contains("percent")) {
                                    stopDistance = candle.close() * (slValue / 100.0);
                                } else if (slType.contains("atr")) {
                                    stopDistance = calculateATR(candles, i, 14) * slValue;
                                }
                                ots.trailingStopPrice = candle.close() - stopDistance;
                            }

                            openTrades.add(ots);
                            lastEntryBar = i;
                        } else {
                            // Record rejected trade (no capital available)
                            Trade rejectedTrade = Trade.rejected(
                                strategy.getId(),
                                "long",
                                i,
                                candle.timestamp(),
                                candle.close()
                            );
                            trades.add(rejectedTrade);
                        }
                    }
                }
            } catch (Exception e) {
                errors.add("Error at bar " + i + ": " + e.getMessage());
            }
        }

        // Close all open trades at the end
        Candle lastCandle = candles.get(candles.size() - 1);
        for (OpenTradeState ots : openTrades) {
            Trade closedTrade = ots.trade.close(
                candles.size() - 1,
                lastCandle.timestamp(),
                lastCandle.close(),
                config.commission()
            );
            trades.add(closedTrade);
            currentEquity += closedTrade.pnl() != null ? closedTrade.pnl() : 0;
        }

        // Calculate metrics
        if (onProgress != null) {
            onProgress.accept(new Progress(candles.size(), candles.size(), 100, "Calculating metrics..."));
        }

        PerformanceMetrics metrics = PerformanceMetrics.calculate(trades, config.initialCapital());

        long endTime = System.currentTimeMillis();

        return new BacktestResult(
            strategy.getId(),
            strategy.getName(),
            config,
            trades,
            metrics,
            startTime,
            endTime,
            candles.size() - warmupBars,
            endTime - startTime,
            errors.size() > 100 ? errors.subList(0, 100) : errors
        );
    }

    /**
     * Calculate position size based on config and strategy
     */
    private double calculatePositionSize(BacktestConfig config, Strategy strategy, double equity,
                                         double price, List<Candle> candles, int barIndex) {
        double positionValue;

        switch (config.positionSizingType()) {
            case "fixed_percent" -> positionValue = equity * (config.positionSizingValue() / 100.0);
            case "fixed_amount" -> positionValue = config.positionSizingValue();
            case "risk_percent" -> {
                // Risk a percentage of equity based on stop-loss distance
                String slType = strategy.getStopLossType();
                Double slValue = strategy.getStopLossValue();
                if (slValue != null && slValue > 0 && !"none".equals(slType)) {
                    double riskAmount = equity * (config.positionSizingValue() / 100.0);
                    double stopDistance;
                    if (slType.contains("percent")) {
                        stopDistance = price * (slValue / 100.0);
                    } else {
                        // ATR-based
                        stopDistance = calculateATR(candles, barIndex, 14) * slValue;
                    }
                    if (stopDistance > 0) {
                        positionValue = (riskAmount / stopDistance) * price;
                    } else {
                        positionValue = equity * (config.positionSizingValue() / 100.0);
                    }
                } else {
                    // No SL defined, fall back to fixed percent
                    positionValue = equity * (config.positionSizingValue() / 100.0);
                }
            }
            case "kelly" -> {
                // Kelly Criterion: f = (bp - q) / b
                // where b = win/loss ratio, p = win rate, q = loss rate
                // Simplified: use half-Kelly for safety
                double kellyFraction = calculateKellyFraction(config);
                positionValue = equity * Math.max(0, Math.min(kellyFraction * 0.5, 0.25)); // Cap at 25%
            }
            case "volatility" -> {
                // Volatility-based: size inversely proportional to ATR
                double atr = calculateATR(candles, barIndex, 14);
                if (atr > 0) {
                    double targetRisk = equity * 0.02; // Risk 2% of equity
                    double atrMultiple = 2.0; // Stop at 2x ATR
                    positionValue = (targetRisk / (atr * atrMultiple)) * price;
                } else {
                    positionValue = equity * 0.10;
                }
            }
            default -> positionValue = equity * 0.10; // Default 10%
        }

        // Ensure we don't exceed available equity
        positionValue = Math.min(positionValue, equity * 0.95);

        return positionValue / price;
    }

    /**
     * Calculate Kelly fraction based on recent trades (simplified)
     */
    private double calculateKellyFraction(BacktestConfig config) {
        // In a real implementation, this would use historical trade data
        // For now, use conservative estimates
        double winRate = 0.55;  // Assume 55% win rate
        double avgWin = 1.5;    // Avg win is 1.5x avg loss
        double avgLoss = 1.0;

        double b = avgWin / avgLoss;
        double p = winRate;
        double q = 1 - p;

        return (b * p - q) / b;
    }

    /**
     * Calculate Average True Range
     */
    private double calculateATR(List<Candle> candles, int barIndex, int period) {
        if (barIndex < period) return 0;

        double sum = 0;
        for (int i = barIndex - period + 1; i <= barIndex; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            double tr = Math.max(
                curr.high() - curr.low(),
                Math.max(
                    Math.abs(curr.high() - prev.close()),
                    Math.abs(curr.low() - prev.close())
                )
            );
            sum += tr;
        }

        return sum / period;
    }

    /**
     * Calculate the warmup period needed for indicators
     */
    private int calculateWarmupPeriod(Strategy strategy, BacktestConfig config) {
        List<Integer> periods = new ArrayList<>();

        // Extract from entry/exit conditions
        periods.addAll(extractPeriods(strategy.getEntry()));
        periods.addAll(extractPeriods(strategy.getExit()));

        if (periods.isEmpty()) {
            return 50; // Default warmup
        }

        // Return max period + buffer
        return periods.stream().max(Integer::compareTo).orElse(50) + 10;
    }

    /**
     * Extract periods from a DSL expression
     */
    private List<Integer> extractPeriods(String expression) {
        List<Integer> periods = new ArrayList<>();

        Pattern pattern = Pattern.compile("(SMA|EMA|RSI|MACD|BBANDS|HIGH_OF|LOW_OF|AVG_VOLUME|ATR)\\((\\d+(?:,\\s*\\d+)*)\\)");
        Matcher matcher = pattern.matcher(expression);

        while (matcher.find()) {
            String params = matcher.group(2);
            for (String p : params.split(",")) {
                try {
                    periods.add(Integer.parseInt(p.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        return periods;
    }

    /**
     * Create an error result
     */
    private BacktestResult createErrorResult(Strategy strategy, BacktestConfig config, long startTime, String error) {
        return new BacktestResult(
            strategy.getId(),
            strategy.getName(),
            config,
            List.of(),
            PerformanceMetrics.empty(config.initialCapital()),
            startTime,
            System.currentTimeMillis(),
            0,
            System.currentTimeMillis() - startTime,
            List.of(error)
        );
    }

    /**
     * Progress update record
     */
    public record Progress(int current, int total, int percentage, String message) {}

    /**
     * Helper class to track state for each open trade
     */
    private static class OpenTradeState {
        Trade trade;
        double highestPriceSinceEntry;
        double trailingStopPrice;
        String exitReason;
        double exitPrice;

        OpenTradeState(Trade trade, double entryPrice) {
            this.trade = trade;
            this.highestPriceSinceEntry = entryPrice;
            this.trailingStopPrice = 0;
        }
    }
}
