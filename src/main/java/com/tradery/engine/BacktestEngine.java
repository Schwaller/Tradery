package com.tradery.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.data.CandleStore;
import com.tradery.dsl.AstNode;
import com.tradery.dsl.Parser;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.io.HoopPatternStore;
import com.tradery.model.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main backtesting engine that runs strategies against historical data.
 */
public class BacktestEngine {

    private final IndicatorEngine indicatorEngine;
    private final CandleStore candleStore;

    public BacktestEngine() {
        this.indicatorEngine = new IndicatorEngine();
        this.candleStore = null;
    }

    public BacktestEngine(CandleStore candleStore) {
        this.indicatorEngine = new IndicatorEngine();
        this.candleStore = candleStore;
    }

    /**
     * Run a backtest for a strategy on historical data (without phases)
     */
    public BacktestResult run(
            Strategy strategy,
            BacktestConfig config,
            List<Candle> candles,
            Consumer<Progress> onProgress
    ) {
        return run(strategy, config, candles, List.of(), onProgress);
    }

    /**
     * Run a backtest for a strategy on historical data with phase filtering
     *
     * @param requiredPhases List of Phase objects that must all be active for entry
     */
    public BacktestResult run(
            Strategy strategy,
            BacktestConfig config,
            List<Candle> candles,
            List<Phase> requiredPhases,
            Consumer<Progress> onProgress
    ) {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        // Evaluate phases upfront if any are required or excluded
        Map<String, boolean[]> phaseStates = new HashMap<>();
        List<String> requiredPhaseIds = strategy.getRequiredPhaseIds();
        List<String> excludedPhaseIds = strategy.getExcludedPhaseIds();

        if (!requiredPhases.isEmpty() && candleStore != null) {
            if (onProgress != null) {
                onProgress.accept(new Progress(0, candles.size(), 0, "Evaluating phases..."));
            }
            try {
                PhaseEvaluator phaseEvaluator = new PhaseEvaluator(candleStore);
                phaseStates = phaseEvaluator.evaluatePhases(
                    requiredPhases, candles, config.resolution()
                );
            } catch (IOException e) {
                return createErrorResult(strategy, config, startTime,
                    "Failed to evaluate phases: " + e.getMessage());
            }
        }

        // Evaluate hoop patterns upfront if any are configured
        Map<String, boolean[]> hoopPatternStates = new HashMap<>();
        HoopPatternSettings hoopSettings = strategy.getHoopPatternSettings();
        List<String> requiredEntryPatternIds = hoopSettings.getRequiredEntryPatternIds();
        List<String> excludedEntryPatternIds = hoopSettings.getExcludedEntryPatternIds();
        List<String> requiredExitPatternIds = hoopSettings.getRequiredExitPatternIds();
        List<String> excludedExitPatternIds = hoopSettings.getExcludedExitPatternIds();

        if (hoopSettings.hasAnyPatterns() && candleStore != null) {
            if (onProgress != null) {
                onProgress.accept(new Progress(0, candles.size(), 0, "Evaluating hoop patterns..."));
            }
            try {
                HoopPatternEvaluator hoopEvaluator = new HoopPatternEvaluator(candleStore);

                // Collect all needed pattern IDs
                Set<String> neededPatternIds = new HashSet<>();
                neededPatternIds.addAll(requiredEntryPatternIds);
                neededPatternIds.addAll(excludedEntryPatternIds);
                neededPatternIds.addAll(requiredExitPatternIds);
                neededPatternIds.addAll(excludedExitPatternIds);

                // Load patterns from store
                File hoopsDir = new File(System.getProperty("user.home"), ".tradery/hoops");
                HoopPatternStore hoopStore = new HoopPatternStore(hoopsDir);
                List<HoopPattern> patterns = hoopStore.loadByIds(neededPatternIds);

                hoopPatternStates = hoopEvaluator.evaluatePatterns(
                    patterns, candles, config.resolution()
                );
            } catch (IOException e) {
                return createErrorResult(strategy, config, startTime,
                    "Failed to evaluate hoop patterns: " + e.getMessage());
            }
        }

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

        // Parse exit conditions from all zones
        for (ExitZone zone : strategy.getExitZones()) {
            String exitCondition = zone.exitCondition();
            if (exitCondition != null && !exitCondition.trim().isEmpty()) {
                Parser.ParseResult exitResult = parser.parse(exitCondition);
                if (!exitResult.success()) {
                    return createErrorResult(strategy, config, startTime,
                        "Exit condition parse error in zone '" + zone.name() + "': " + exitResult.error());
                }
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
                            // Check zone phase filters
                            if (pz.zone.hasPhaseFilters()) {
                                boolean zonePhasesActive = PhaseEvaluator.allPhasesActive(
                                    phaseStates, pz.zone.requiredPhaseIds(), pz.zone.excludedPhaseIds(), i
                                );
                                if (!zonePhasesActive) {
                                    continue;  // Zone phases not satisfied, try next zone
                                }
                            }
                            ots.exitReason = "zone_exit";
                            ots.exitPrice = candle.close();
                            ots.exitZone = pz.zone.name();
                            ots.matchedZone = pz.zone;
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
                ExitZone dcaMatchedZone = null;
                if (dcaExitReason != null) {
                    // Find the zone that triggered the exit
                    for (OpenTradeState ots : openTrades) {
                        if (ots.matchedZone != null) {
                            dcaMatchedZone = ots.matchedZone;
                            break;
                        }
                    }
                    for (OpenTradeState ots : openTrades) {
                        ots.exitReason = dcaExitReason;
                        ots.exitPrice = dcaExitPrice;
                        ots.matchedZone = dcaMatchedZone;
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
                                // Check zone phase filters
                                if (pz.zone.hasPhaseFilters()) {
                                    boolean zonePhasesActive = PhaseEvaluator.allPhasesActive(
                                        phaseStates, pz.zone.requiredPhaseIds(), pz.zone.excludedPhaseIds(), i
                                    );
                                    if (!zonePhasesActive) {
                                        continue;  // Zone phases not satisfied, try next zone
                                    }
                                }
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
                            ots.matchedZone = zone;
                            if (isDcaPosition) {
                                dcaExitReason = exitReason;
                                dcaExitPrice = exitPrice;
                                dcaMatchedZone = zone;
                                break;
                            } else {
                                toClose.add(ots);
                                continue;
                            }
                        }

                        // Use zone's exit configuration
                        StopLossType slType = zone.stopLossType();
                        Double slValue = zone.stopLossValue();
                        TakeProfitType tpType = zone.takeProfitType();
                        Double tpValue = zone.takeProfitValue();
                        AstNode exitConditionAst = matchingZone.exitConditionAst;

                        // Handle CLEAR - reset trailing stop state
                        if (slType == StopLossType.CLEAR) {
                            ots.trailingStopPrice = 0;
                            ots.highestPriceSinceEntry = candle.close();
                        }

                        // Calculate stop distance based on type
                        double stopDistance = 0;
                        if (slValue != null && slType != StopLossType.NONE) {
                            if (slType.isPercent()) {
                                stopDistance = entryPrice * (slValue / 100.0);
                            } else if (slType.isAtr()) {
                                stopDistance = calculateATR(candles, i, 14) * slValue;
                            }
                        }

                        // Handle trailing stop
                        if (slType != null && slType.isTrailing() && stopDistance > 0) {
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
                        else if (slType != null && !slType.isTrailing() && slType != StopLossType.NONE && stopDistance > 0) {
                            double stopPrice = entryPrice - stopDistance;
                            if (candle.low() <= stopPrice) {
                                exitReason = "stop_loss";
                                exitPrice = stopPrice;
                            }
                        }

                        // Check take-profit
                        if (exitReason == null && tpValue != null && tpType != TakeProfitType.NONE) {
                            double tpDistance = 0;
                            if (tpType.isPercent()) {
                                tpDistance = entryPrice * (tpValue / 100.0);
                            } else if (tpType.isAtr()) {
                                tpDistance = calculateATR(candles, i, 14) * tpValue;
                            }

                            double tpPrice = entryPrice + tpDistance;
                            if (candle.high() >= tpPrice) {
                                exitReason = "take_profit";
                                exitPrice = tpPrice;
                            }
                        }

                        // Check DSL exit condition (zone's or strategy's) and hoop patterns
                        if (exitReason == null) {
                            // Evaluate DSL exit condition
                            boolean dslExitSignal = exitConditionAst != null && evaluator.evaluate(exitConditionAst, i);

                            // Evaluate hoop exit pattern
                            boolean hoopExitSignal = HoopPatternEvaluator.patternsMatch(
                                hoopPatternStates, requiredExitPatternIds, excludedExitPatternIds, i
                            );

                            // Combine based on exit mode
                            boolean shouldExit = switch (hoopSettings.getExitMode()) {
                                case DSL_ONLY -> dslExitSignal;
                                case HOOP_ONLY -> hoopExitSignal;
                                case AND -> dslExitSignal && hoopExitSignal;
                                case OR -> dslExitSignal || hoopExitSignal;
                            };

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
                                dcaMatchedZone = zone;
                                break;  // Exit the loop - we'll close all trades
                            } else {
                                // Mark for closing
                                ots.exitReason = exitReason;
                                ots.exitPrice = exitPrice;
                                ots.exitZone = exitZoneName;
                                ots.matchedZone = zone;
                                toClose.add(ots);
                            }
                        }
                    }

                    // In DCA mode, if any exit triggered, close ALL open trades
                    if (dcaExitReason != null) {
                        for (OpenTradeState ots : openTrades) {
                            ots.exitReason = dcaExitReason;
                            ots.exitPrice = dcaExitPrice;
                            ots.matchedZone = dcaMatchedZone;
                            ots.exitZone = dcaMatchedZone != null ? dcaMatchedZone.name() : null;
                            if (!toClose.contains(ots)) {
                                toClose.add(ots);
                            }
                        }
                    }
                }

                // Close marked trades (with partial exit support)
                List<OpenTradeState> fullyClosedTrades = new ArrayList<>();

                // For DCA positions, calculate proportional exits
                boolean isDcaPartialExit = strategy.isDcaEnabled() && toClose.size() > 1;

                if (isDcaPartialExit && !toClose.isEmpty()) {
                    // DCA mode: distribute exit proportionally across all entries
                    OpenTradeState firstTrade = toClose.get(0);
                    ExitZone zone = firstTrade.matchedZone;

                    if (zone != null && zone.exitPercent() != null && zone.exitPercent() < 100) {
                        // Calculate total remaining across all DCA entries
                        double totalRemaining = toClose.stream().mapToDouble(o -> o.remainingQuantity).sum();
                        double totalOriginal = toClose.stream().mapToDouble(o -> o.originalQuantity).sum();

                        // Check zone exit count (use first trade as representative)
                        String zoneName = zone.name();
                        int exitsDone = firstTrade.zoneExitCount.getOrDefault(zoneName, 0);
                        int maxExits = zone.getEffectiveMaxExits();

                        // Only proceed if max exits not reached
                        if (exitsDone < maxExits) {
                            // Calculate target exit based on zone config
                            double exitPercent = zone.getEffectiveExitPercent();
                            double toExit;
                            if (zone.exitBasis() == ExitBasis.ORIGINAL) {
                                toExit = totalOriginal * (exitPercent / 100.0);
                            } else {
                                toExit = totalRemaining * (exitPercent / 100.0);
                            }
                            toExit = Math.min(toExit, totalRemaining);

                            // Check minBarsBetweenExits constraint (using first trade as representative)
                            boolean canExit = firstTrade.canExitInZone(zone, i);

                            if (toExit > 0 && canExit) {
                                // Distribute proportionally across entries
                                for (OpenTradeState ots : toClose) {
                                    double proportion = totalRemaining > 0 ? ots.remainingQuantity / totalRemaining : 0;
                                    double exitQty = toExit * proportion;
                                    exitQty = Math.min(exitQty, ots.remainingQuantity);

                                    if (exitQty > 0.0001) {
                                        Trade partialTrade = ots.trade.partialClose(
                                            i, candle.timestamp(), ots.exitPrice, exitQty,
                                            config.commission(), ots.exitReason, ots.exitZone
                                        );
                                        trades.add(partialTrade);
                                        currentEquity += partialTrade.pnl() != null ? partialTrade.pnl() : 0;
                                        ots.recordPartialExit(zoneName, exitQty, i);
                                    }

                                    if (ots.isFullyClosed()) {
                                        fullyClosedTrades.add(ots);
                                    }
                                }
                            }
                        }
                    } else {
                        // No partial exit configured, close everything
                        for (OpenTradeState ots : toClose) {
                            Trade closedTrade = ots.trade.partialClose(
                                i, candle.timestamp(), ots.exitPrice, ots.remainingQuantity,
                                config.commission(), ots.exitReason, ots.exitZone
                            );
                            trades.add(closedTrade);
                            currentEquity += closedTrade.pnl() != null ? closedTrade.pnl() : 0;
                            ots.remainingQuantity = 0;
                            fullyClosedTrades.add(ots);
                        }
                    }
                } else {
                    // Non-DCA or single trade: handle each individually
                    for (OpenTradeState ots : toClose) {
                        ExitZone zone = ots.matchedZone;
                        double exitQty;
                        boolean isPartialExit = zone != null && zone.exitPercent() != null && zone.exitPercent() < 100;

                        if (isPartialExit) {
                            // Check minBarsBetweenExits constraint
                            if (!ots.canExitInZone(zone, i)) {
                                continue;  // Skip this partial exit, not enough bars passed
                            }
                            // Partial exit
                            exitQty = ots.calculateExitQuantity(zone);
                        } else {
                            // Full exit - no bars between constraint for full exits
                            exitQty = ots.remainingQuantity;
                        }

                        if (exitQty > 0.0001) {
                            Trade partialTrade = ots.trade.partialClose(
                                i, candle.timestamp(), ots.exitPrice, exitQty,
                                config.commission(), ots.exitReason, ots.exitZone
                            );
                            trades.add(partialTrade);
                            currentEquity += partialTrade.pnl() != null ? partialTrade.pnl() : 0;

                            if (zone != null) {
                                ots.recordPartialExit(zone.name(), exitQty, i);
                            } else {
                                ots.remainingQuantity -= exitQty;
                            }
                        }

                        if (ots.isFullyClosed()) {
                            fullyClosedTrades.add(ots);
                        }
                    }
                }

                // Remove fully closed trades
                for (OpenTradeState ots : fullyClosedTrades) {
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
                DcaMode dcaMode = strategy.getDcaMode();

                // Check if entry signal is present (DSL condition)
                boolean dslSignal = evaluator.evaluate(entryResult.ast(), i);

                // Check if hoop pattern signal is present
                boolean hoopSignal = HoopPatternEvaluator.patternsMatch(
                    hoopPatternStates, requiredEntryPatternIds, excludedEntryPatternIds, i
                );

                // Combine DSL and hoop signals based on entry mode
                boolean signalPresent = switch (hoopSettings.getEntryMode()) {
                    case DSL_ONLY -> dslSignal;
                    case HOOP_ONLY -> hoopSignal;
                    case AND -> dslSignal && hoopSignal;
                    case OR -> dslSignal || hoopSignal;
                };

                // Handle DCA abort mode - close all trades if signal lost
                if (isDcaEntry && dcaMode == DcaMode.ABORT && !signalPresent && toClose.isEmpty()) {
                    List<OpenTradeState> abortedTrades = new ArrayList<>();
                    for (OpenTradeState ots : openTrades) {
                        ots.exitReason = "signal_lost";
                        ots.exitPrice = candle.close();
                        abortedTrades.add(ots);
                    }
                    // Close the aborted trades (close remaining quantity)
                    for (OpenTradeState ots : abortedTrades) {
                        if (ots.remainingQuantity > 0.0001) {
                            Trade closedTrade = ots.trade.partialClose(
                                i, candle.timestamp(), ots.exitPrice, ots.remainingQuantity,
                                config.commission(), ots.exitReason, ots.exitZone
                            );
                            trades.add(closedTrade);
                            currentEquity += closedTrade.pnl() != null ? closedTrade.pnl() : 0;
                            ots.remainingQuantity = 0;
                        }
                        openTrades.remove(ots);
                    }
                }

                if (canOpenMore && passesMinDistance) {
                    boolean shouldEnter;
                    if (isDcaEntry && dcaMode == DcaMode.CONTINUE) {
                        // In continue mode, DCA entries don't require signal
                        shouldEnter = true;
                    } else {
                        // pause mode or first entry - require signal
                        shouldEnter = signalPresent;
                    }

                    // Check if all required phases are active and no excluded phases are active
                    if (shouldEnter && (!requiredPhaseIds.isEmpty() || !excludedPhaseIds.isEmpty())) {
                        boolean phasesActive = PhaseEvaluator.allPhasesActive(
                            phaseStates, requiredPhaseIds, excludedPhaseIds, i
                        );
                        if (!phasesActive) {
                            shouldEnter = false; // Skip entry - phase filter not met
                        }
                    }

                    if (shouldEnter) {
                        // Calculate current capital usage (based on remaining quantities)
                        double usedCapital = openTrades.stream()
                            .mapToDouble(ots -> ots.trade.entryPrice() * ots.remainingQuantity)
                            .sum();
                        double availableCapital = currentEquity - usedCapital;

                        // Calculate position size (divide by max entries when DCA enabled)
                        double quantity = calculatePositionSize(config, strategy, currentEquity, candle.close(), candles, i);
                        if (strategy.isDcaEnabled() && maxEntriesPerPosition > 1) {
                            quantity = quantity / maxEntriesPerPosition;
                        }
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

                            // Initialize trailing stop from default zone (zone matching 0% PnL)
                            ExitZone defaultZone = strategy.findMatchingZone(0.0);
                            if (defaultZone != null) {
                                StopLossType slType = defaultZone.stopLossType();
                                Double slValue = defaultZone.stopLossValue();
                                if (slType != null && slType.isTrailing() && slValue != null && slValue > 0) {
                                    double stopDistance = 0;
                                    if (slType.isPercent()) {
                                        stopDistance = candle.close() * (slValue / 100.0);
                                    } else if (slType.isAtr()) {
                                        stopDistance = calculateATR(candles, i, 14) * slValue;
                                    }
                                    ots.trailingStopPrice = candle.close() - stopDistance;
                                }
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

        // Close all open trades at the end (close remaining quantities)
        Candle lastCandle = candles.get(candles.size() - 1);
        for (OpenTradeState ots : openTrades) {
            if (ots.remainingQuantity > 0.0001) {
                Trade closedTrade = ots.trade.partialClose(
                    candles.size() - 1,
                    lastCandle.timestamp(),
                    lastCandle.close(),
                    ots.remainingQuantity,
                    config.commission(),
                    "end_of_data",
                    null
                );
                trades.add(closedTrade);
                currentEquity += closedTrade.pnl() != null ? closedTrade.pnl() : 0;
            }
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
        double positionValue = equity * 0.10; // Default to 10% of equity

        switch (config.positionSizingType()) {
            case FIXED_PERCENT -> positionValue = equity * (config.positionSizingValue() / 100.0);
            case FIXED_DOLLAR -> positionValue = config.positionSizingValue();
            case RISK_PERCENT -> {
                // Risk a percentage of equity based on stop-loss distance from default zone
                ExitZone defaultZone = strategy.findMatchingZone(0.0);
                StopLossType slType = defaultZone != null ? defaultZone.stopLossType() : StopLossType.NONE;
                Double slValue = defaultZone != null ? defaultZone.stopLossValue() : null;
                if (slValue != null && slValue > 0 && slType != StopLossType.NONE) {
                    double riskAmount = equity * (config.positionSizingValue() / 100.0);
                    double stopDistance;
                    if (slType.isPercent()) {
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
            case KELLY -> {
                // Kelly Criterion: f = (bp - q) / b
                // where b = win/loss ratio, p = win rate, q = loss rate
                // Simplified: use half-Kelly for safety
                double kellyFraction = calculateKellyFraction(config);
                positionValue = equity * Math.max(0, Math.min(kellyFraction * 0.5, 0.25)); // Cap at 25%
            }
            case VOLATILITY -> {
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
            case ALL_IN -> positionValue = equity; // Use all available capital
        }

        // Ensure we don't exceed available equity (allow 100% for ALL_IN)
        double maxAllocation = config.positionSizingType() == PositionSizingType.ALL_IN ? 1.0 : 0.95;
        positionValue = Math.min(positionValue, equity * maxAllocation);

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

        // Extract from entry condition
        periods.addAll(extractPeriods(strategy.getEntry()));

        // Extract from all exit zone conditions
        for (ExitZone zone : strategy.getExitZones()) {
            if (zone.exitCondition() != null) {
                periods.addAll(extractPeriods(zone.exitCondition()));
            }
        }

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
        ExitZone matchedZone;  // The zone that triggered the exit (for partial exit calculation)
        // DCA-out tracking
        double originalQuantity;              // Original quantity at entry
        double remainingQuantity;             // Current remaining quantity
        Map<String, Integer> zoneExitCount;   // zoneName -> number of exits done in this zone
        String lastZoneName;                  // Track zone transitions for RESET logic
        int lastExitBar;                      // Track last partial exit bar for minBarsBetweenExits

        OpenTradeState(Trade trade, double entryPrice) {
            this.trade = trade;
            this.highestPriceSinceEntry = entryPrice;
            this.trailingStopPrice = 0;
            this.originalQuantity = trade.quantity();
            this.remainingQuantity = trade.quantity();
            this.zoneExitCount = new HashMap<>();
            this.lastZoneName = null;
            this.lastExitBar = -9999;
        }

        /**
         * Calculate exit quantity for a zone based on its configuration.
         * Returns the quantity to exit (clipped to remaining), or 0 if max exits reached.
         */
        double calculateExitQuantity(ExitZone zone) {
            double exitPercent = zone.getEffectiveExitPercent();
            int maxExits = zone.getEffectiveMaxExits();
            ExitBasis basis = zone.exitBasis();
            ExitReentry reentry = zone.exitReentry();
            String zoneName = zone.name();

            // Handle zone transition - reset count if configured
            if (lastZoneName != null && !lastZoneName.equals(zoneName) && reentry == ExitReentry.RESET) {
                zoneExitCount.clear();
            }
            lastZoneName = zoneName;

            // Check if max exits reached for this zone
            int exitsDone = zoneExitCount.getOrDefault(zoneName, 0);
            if (exitsDone >= maxExits) {
                return 0;  // No more exits allowed in this zone
            }

            // Calculate target quantity based on basis
            double targetQty;
            if (basis == ExitBasis.ORIGINAL) {
                targetQty = originalQuantity * (exitPercent / 100.0);
            } else {
                // REMAINING basis
                targetQty = remainingQuantity * (exitPercent / 100.0);
            }

            // Clip to remaining
            targetQty = Math.min(targetQty, remainingQuantity);
            targetQty = Math.max(targetQty, 0); // Can't be negative

            return targetQty;
        }

        /**
         * Check if enough bars have passed since last exit for this zone.
         */
        boolean canExitInZone(ExitZone zone, int currentBar) {
            int minBarsBetween = zone.minBarsBetweenExits();
            return (currentBar - lastExitBar) >= minBarsBetween;
        }

        /**
         * Record a partial exit in a zone.
         */
        void recordPartialExit(String zoneName, double quantity, int currentBar) {
            int current = zoneExitCount.getOrDefault(zoneName, 0);
            zoneExitCount.put(zoneName, current + 1);
            remainingQuantity -= quantity;
            lastExitBar = currentBar;
        }

        /**
         * Check if this position is fully closed.
         */
        boolean isFullyClosed() {
            return remainingQuantity <= 0.0001; // Small epsilon for floating point
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
