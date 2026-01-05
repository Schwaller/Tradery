package com.tradery.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
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

        // Parse exit zone conditions (zones are always present now)
        List<ParsedExitZone> parsedZones = new ArrayList<>();
        List<ExitZone> exitZones = strategy.getExitZones();
        for (ExitZone zone : exitZones) {
            AstNode zoneExitAst = null;
            String zoneExitCond = zone.exitCondition();
            if (zoneExitCond != null && !zoneExitCond.trim().isEmpty()) {
                Parser.ParseResult zoneResult = parser.parse(zoneExitCond);
                if (!zoneResult.success()) {
                    return createErrorResult(strategy, config, startTime,
                        "Exit zone '" + zone.name() + "' parse error: " + zoneResult.error());
                }
                zoneExitAst = zoneResult.ast();
            }
            parsedZones.add(new ParsedExitZone(zone, zoneExitAst));
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
        String currentGroupId = null;  // Groups DCA entries together
        int groupCounter = 0;

        // maxOpenTrades limits concurrent positions (DCA groups count as one position)
        int maxPositions = strategy.getMaxOpenTrades();
        int maxEntriesPerPosition = strategy.isDcaEnabled() ? strategy.getDcaMaxEntries() : 1;
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
                // Count open positions (trades with same groupId count as one position)
                long openPositions = openTrades.stream()
                    .map(ots -> ots.trade.groupId())
                    .distinct()
                    .count();

                // Count entries in current position (for DCA)
                final String groupIdForCount = currentGroupId;
                int entriesInCurrentPosition = groupIdForCount == null ? 0 :
                    (int) openTrades.stream()
                        .filter(ots -> groupIdForCount.equals(ots.trade.groupId()))
                        .count();

                // In DCA mode, only check normal exits after current position has all entries
                boolean dcaComplete = !strategy.isDcaEnabled() || entriesInCurrentPosition >= maxEntriesPerPosition;

                // Check exit conditions for all open trades
                List<OpenTradeState> toClose = new ArrayList<>();
                String dcaExitReason = null;
                double dcaExitPrice = candle.close();

                // First, check for emergency exits (exitImmediately zones) - always checked even during DCA
                for (OpenTradeState ots : openTrades) {
                    double currentPnlPercent = calculatePnlPercent(ots.trade, candle.close());
                    for (ParsedExitZone pz : parsedZones) {
                        if (pz.zone.matches(currentPnlPercent) && pz.zone.exitImmediately()) {
                            ots.exitReason = "zone_exit";
                            ots.exitPrice = candle.close();
                            ots.exitZone = pz.zone.name();
                            if (strategy.isDcaEnabled()) {
                                dcaExitReason = "zone_exit";
                                dcaExitPrice = candle.close();
                            } else {
                                toClose.add(ots);
                            }
                            break;
                        }
                    }
                }

                // If emergency exit triggered in DCA mode, close all trades in the position
                if (dcaExitReason != null) {
                    for (OpenTradeState ots : openTrades) {
                        ots.exitReason = dcaExitReason;
                        ots.exitPrice = dcaExitPrice;
                        if (!toClose.contains(ots)) {
                            toClose.add(ots);
                        }
                    }
                }

                // Check normal exit conditions only if DCA is complete and no emergency exit
                if (dcaComplete && dcaExitReason == null) {
                    // For DCA mode, calculate exits based on weighted average entry
                    boolean isDcaPosition = strategy.isDcaEnabled() && openTrades.size() > 1;
                    double avgEntryPrice = 0;
                    OpenTradeState firstTrade = openTrades.isEmpty() ? null : openTrades.getFirst();

                    if (isDcaPosition) {
                        double totalValue = 0;
                        double totalQty = 0;
                        for (OpenTradeState ots : openTrades) {
                            totalValue += ots.trade.entryPrice() * ots.trade.quantity();
                            totalQty += ots.trade.quantity();
                        }
                        avgEntryPrice = totalQty > 0 ? totalValue / totalQty : 0;

                        // For DCA, use first trade's price tracking (it's been open longest)
                        // and sync all other trades to it
                        if (firstTrade != null) {
                            for (OpenTradeState ots : openTrades) {
                                if (ots != firstTrade) {
                                    ots.highestPriceSinceEntry = firstTrade.highestPriceSinceEntry;
                                    ots.trailingStopPrice = firstTrade.trailingStopPrice;
                                }
                            }
                        }
                    }

                    for (OpenTradeState ots : openTrades) {
                        Trade openTrade = ots.trade;
                        // Use average entry for DCA, individual entry otherwise
                        double entryPrice = (isDcaPosition && avgEntryPrice > 0) ? avgEntryPrice : openTrade.entryPrice();
                        String exitReason = null;
                        double exitPrice = candle.close();
                        String exitZoneName = null;

                        // Find matching exit zone based on current P&L
                        double currentPnlPercent = calculatePnlPercent(openTrade, candle.close());

                        ParsedExitZone matchingZone = null;
                        for (ParsedExitZone pz : parsedZones) {
                            if (pz.zone.matches(currentPnlPercent)) {
                                matchingZone = pz;
                                break;
                            }
                        }

                        // If no zone matches, use first zone as fallback (but don't apply exitImmediately)
                        boolean isZoneFallback = false;
                        if (matchingZone == null && !parsedZones.isEmpty()) {
                            matchingZone = parsedZones.getFirst();
                            isZoneFallback = true;
                        }

                        if (matchingZone == null) {
                            continue;  // No exit config available
                        }

                        ExitZone zone = matchingZone.zone;
                        exitZoneName = zone.name();

                        // Check if enough bars have passed for this zone's minBarsBeforeExit
                        if ((i - lastEntryBar) < zone.minBarsBeforeExit()) {
                            continue;  // Not enough bars passed yet
                        }

                        // Check if zone requires immediate exit (only for directly matched zones, not fallback)
                        if (!isZoneFallback && zone.exitImmediately()) {
                            exitReason = "zone_exit";
                            exitPrice = candle.close();
                            ots.exitReason = exitReason;
                            ots.exitPrice = exitPrice;
                            ots.exitZone = exitZoneName;
                            if (isDcaPosition) {
                                dcaExitReason = exitReason;
                                dcaExitPrice = exitPrice;
                                break;
                            } else {
                                toClose.add(ots);
                                continue;
                            }
                        }

                        // Use zone's exit configuration
                        String slType = zone.stopLossType();
                        Double slValue = zone.stopLossValue();
                        String tpType = zone.takeProfitType();
                        Double tpValue = zone.takeProfitValue();
                        AstNode exitConditionAst = matchingZone.exitConditionAst;

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

                        // Check DSL exit condition (zone's or strategy's)
                        if (exitReason == null && exitConditionAst != null) {
                            boolean shouldExit = evaluator.evaluate(exitConditionAst, i);
                            if (shouldExit) {
                                exitReason = "signal";
                                exitPrice = candle.close();
                            }
                        }

                        if (exitReason != null) {
                            // In DCA mode, one exit triggers all exits
                            if (isDcaPosition) {
                                dcaExitReason = exitReason;
                                dcaExitPrice = exitPrice;
                                break;  // Exit the loop - we'll close all trades
                            } else {
                                // Mark for closing
                                ots.exitReason = exitReason;
                                ots.exitPrice = exitPrice;
                                ots.exitZone = exitZoneName;
                                toClose.add(ots);
                            }
                        }
                    }

                    // In DCA mode, if any exit triggered, close ALL open trades
                    if (dcaExitReason != null) {
                        for (OpenTradeState ots : openTrades) {
                            ots.exitReason = dcaExitReason;
                            ots.exitPrice = dcaExitPrice;
                            toClose.add(ots);
                        }
                    }
                }

                // Close marked trades
                for (OpenTradeState ots : toClose) {
                    Trade closedTrade = ots.trade.close(i, candle.timestamp(), ots.exitPrice, config.commission(), ots.exitReason, ots.exitZone);
                    trades.add(closedTrade);
                    currentEquity += closedTrade.pnl() != null ? closedTrade.pnl() : 0;
                    openTrades.remove(ots);
                }

                // Reset currentGroupId if position was closed (so next signal starts new position)
                if (!toClose.isEmpty() && strategy.isDcaEnabled()) {
                    // Check if current group still has open trades
                    final String groupIdForCheck = currentGroupId;
                    boolean currentGroupStillOpen = groupIdForCheck != null && openTrades.stream()
                        .anyMatch(ots -> groupIdForCheck.equals(ots.trade.groupId()));
                    if (!currentGroupStillOpen) {
                        currentGroupId = null;
                    }
                }

                // Recalculate after closes
                long openPositionsAfterClose = openTrades.stream()
                    .map(ots -> ots.trade.groupId())
                    .distinct()
                    .count();
                final String groupIdAfterClose = currentGroupId;
                int entriesInCurrentPositionAfterClose = groupIdAfterClose == null ? 0 :
                    (int) openTrades.stream()
                        .filter(ots -> groupIdAfterClose.equals(ots.trade.groupId()))
                        .count();

                // Check entry condition if we can open more positions
                boolean canAddToCurrentPosition = strategy.isDcaEnabled() &&
                    currentGroupId != null &&
                    entriesInCurrentPositionAfterClose > 0 &&
                    entriesInCurrentPositionAfterClose < maxEntriesPerPosition;
                boolean canStartNewPosition = openPositionsAfterClose < maxPositions;
                boolean canOpenMore = canAddToCurrentPosition || canStartNewPosition;
                boolean isDcaEntry = canAddToCurrentPosition;
                int requiredDistance = isDcaEntry ? strategy.getDcaBarsBetween() : minCandlesBetween;
                boolean passesMinDistance = (i - lastEntryBar) >= requiredDistance;
                String dcaMode = strategy.getDcaMode();

                // Check if entry signal is present
                boolean signalPresent = evaluator.evaluate(entryResult.ast(), i);

                // Handle DCA abort mode - close all trades if signal lost
                if (isDcaEntry && "abort".equals(dcaMode) && !signalPresent && toClose.isEmpty()) {
                    for (OpenTradeState ots : openTrades) {
                        ots.exitReason = "signal_lost";
                        ots.exitPrice = candle.close();
                        toClose.add(ots);
                    }
                    // Close the aborted trades
                    for (OpenTradeState ots : toClose) {
                        Trade closedTrade = ots.trade.close(i, candle.timestamp(), ots.exitPrice, config.commission(), ots.exitReason, ots.exitZone);
                        trades.add(closedTrade);
                        currentEquity += closedTrade.pnl() != null ? closedTrade.pnl() : 0;
                        openTrades.remove(ots);
                    }
                    toClose.clear();
                }

                if (canOpenMore && passesMinDistance) {
                    boolean shouldEnter;
                    if (isDcaEntry && "continue".equals(dcaMode)) {
                        // In continue mode, DCA entries don't require signal
                        shouldEnter = true;
                    } else {
                        // pause mode or first entry - require signal
                        shouldEnter = signalPresent;
                    }

                    if (shouldEnter) {
                        // Calculate current capital usage
                        double usedCapital = openTrades.stream()
                            .mapToDouble(ots -> ots.trade.entryPrice() * ots.trade.quantity())
                            .sum();
                        double availableCapital = currentEquity - usedCapital;

                        // Calculate position size
                        double quantity = calculatePositionSize(config, strategy, currentEquity, candle.close(), candles, i);
                        double positionValue = quantity * candle.close();

                        // Check if we have enough capital (reject if would exceed 100% usage)
                        if (positionValue > availableCapital) {
                            quantity = 0;  // Will trigger rejection
                        }

                        if (quantity > 0) {
                            // Determine if this is a new position or adding to existing
                            boolean startingNewPosition = !isDcaEntry;

                            // Generate new groupId for first entry of a new position
                            // Every position gets a groupId (DCA or not) for consistent counting
                            if (startingNewPosition) {
                                groupCounter++;
                                currentGroupId = strategy.isDcaEnabled() ? "dca-" + groupCounter : "pos-" + groupCounter;
                            }

                            Trade newTrade = Trade.open(
                                strategy.getId(),
                                "long",
                                i,
                                candle.timestamp(),
                                candle.close(),
                                quantity,
                                config.commission(),
                                currentGroupId
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

        // Generate config hash for change detection
        String exitZonesJson = "";
        try {
            exitZonesJson = new ObjectMapper().writeValueAsString(strategy.getExitZones());
        } catch (Exception ignored) {}
        String configHash = BacktestResult.hashConfig(
            strategy.getEntry(),
            exitZonesJson,
            config.symbol(),
            config.resolution(),
            String.valueOf(config.startDate()) + "-" + config.endDate()
        );

        return new BacktestResult(
            BacktestResult.newRunId(),
            configHash,
            strategy.getId(),
            strategy.getName(),
            strategy,
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
            case "fixed_dollar", "fixed_amount" -> positionValue = config.positionSizingValue();
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
        // Generate config hash for change detection
        String exitZonesJson = "";
        try {
            exitZonesJson = new ObjectMapper().writeValueAsString(strategy.getExitZones());
        } catch (Exception ignored) {}
        String configHash = BacktestResult.hashConfig(
            strategy.getEntry(),
            exitZonesJson,
            config.symbol(),
            config.resolution(),
            String.valueOf(config.startDate()) + "-" + config.endDate()
        );

        return new BacktestResult(
            BacktestResult.newRunId(),
            configHash,
            strategy.getId(),
            strategy.getName(),
            strategy,
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
        String exitZone;

        OpenTradeState(Trade trade, double entryPrice) {
            this.trade = trade;
            this.highestPriceSinceEntry = entryPrice;
            this.trailingStopPrice = 0;
        }
    }

    /**
     * Holds parsed exit zone information
     */
    private static class ParsedExitZone {
        ExitZone zone;
        AstNode exitConditionAst;

        ParsedExitZone(ExitZone zone, AstNode exitConditionAst) {
            this.zone = zone;
            this.exitConditionAst = exitConditionAst;
        }
    }

    /**
     * Calculate current P&L percentage for an open trade at a given price
     */
    private double calculatePnlPercent(Trade trade, double currentPrice) {
        double pnl = (currentPrice - trade.entryPrice()) * trade.quantity();
        if ("short".equals(trade.side())) {
            pnl = -pnl;
        }
        return (pnl / (trade.entryPrice() * trade.quantity())) * 100;
    }
}
