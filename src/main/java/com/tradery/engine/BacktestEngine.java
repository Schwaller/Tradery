package com.tradery.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.dsl.AstNode;
import com.tradery.dsl.Parser;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.io.HoopPatternStore;
import com.tradery.model.*;
import com.tradery.model.AggTrade;
import com.tradery.model.FundingRate;
import com.tradery.model.OpenInterest;
import com.tradery.model.PremiumIndex;
import com.tradery.model.TradeDirection;

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
    private final SqliteDataStore dataStore;
    private final PositionSizer positionSizer;
    private final TradeAnalytics tradeAnalytics;
    private List<AggTrade> aggTrades;
    private List<FundingRate> fundingRates;
    private List<OpenInterest> openInterestData;
    private List<PremiumIndex> premiumIndexData;

    public BacktestEngine() {
        this.indicatorEngine = new IndicatorEngine();
        this.dataStore = null;
        this.positionSizer = new PositionSizer();
        this.tradeAnalytics = new TradeAnalytics(indicatorEngine);
    }

    public BacktestEngine(SqliteDataStore dataStore) {
        this.indicatorEngine = new IndicatorEngine();
        this.dataStore = dataStore;
        this.positionSizer = new PositionSizer();
        this.tradeAnalytics = new TradeAnalytics(indicatorEngine);
    }

    /**
     * Get the indicator engine for chart access.
     */
    public IndicatorEngine getIndicatorEngine() {
        return indicatorEngine;
    }

    /**
     * Set aggregated trades data for orderflow indicators (Delta, CumDelta).
     * Must be called before run() for Tier 2 orderflow indicators to work.
     */
    public void setAggTrades(List<AggTrade> aggTrades) {
        this.aggTrades = aggTrades;
    }

    /**
     * Set funding rate data for funding indicators (FUNDING, FUNDING_8H).
     * Must be called before run() for funding indicators to work.
     */
    public void setFundingRates(List<FundingRate> fundingRates) {
        this.fundingRates = fundingRates;
    }

    /**
     * Set open interest data for OI indicators (OI, OI_CHANGE, OI_DELTA).
     * Must be called before run() for OI indicators to work.
     */
    public void setOpenInterest(List<OpenInterest> openInterestData) {
        this.openInterestData = openInterestData;
    }

    /**
     * Set premium index data for premium indicators (PREMIUM, PREMIUM_AVG).
     * Must be called before run() for premium indicators to work.
     */
    public void setPremiumIndex(List<PremiumIndex> premiumIndexData) {
        this.premiumIndexData = premiumIndexData;
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
        return run(strategy, config, candles, requiredPhases, null, onProgress);
    }

    /**
     * Run a backtest for a strategy on historical data with phase filtering and pre-fetched phase candles
     *
     * @param requiredPhases List of Phase objects that must all be active for entry
     * @param phaseCandles   Pre-fetched candles for phases keyed by "symbol:timeframe" (can be null)
     */
    public BacktestResult run(
            Strategy strategy,
            BacktestConfig config,
            List<Candle> candles,
            List<Phase> requiredPhases,
            Map<String, List<Candle>> phaseCandles,
            Consumer<Progress> onProgress
    ) {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        // Initialize indicator engine FIRST so charts can display data even if parsing fails
        if (onProgress != null) {
            onProgress.accept(new Progress(0, candles.size(), 0, "Initializing indicators..."));
        }
        initializeIndicatorEngine(candles, config.resolution(), onProgress);

        // Evaluate phases upfront if any are required or excluded
        Map<String, boolean[]> phaseStates = new HashMap<>();
        List<String> requiredPhaseIds = strategy.getRequiredPhaseIds();
        List<String> excludedPhaseIds = strategy.getExcludedPhaseIds();

        if (!requiredPhases.isEmpty() && dataStore != null) {
            if (onProgress != null) {
                onProgress.accept(new Progress(0, candles.size(), 0, "Evaluating phases..."));
            }
            try {
                PhaseEvaluator phaseEvaluator = new PhaseEvaluator(dataStore);
                phaseStates = phaseEvaluator.evaluatePhases(
                    requiredPhases, candles, config.resolution(),
                    phaseName -> {
                        if (onProgress != null) {
                            onProgress.accept(new Progress(0, candles.size(), 0, "Evaluating phase: " + phaseName + "..."));
                        }
                    },
                    phaseCandles
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

        if (hoopSettings.hasAnyPatterns() && dataStore != null) {
            if (onProgress != null) {
                onProgress.accept(new Progress(0, candles.size(), 0, "Evaluating hoop patterns..."));
            }
            try {
                HoopPatternEvaluator hoopEvaluator = new HoopPatternEvaluator(dataStore);

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

        // Check for overlapping exit zones (warning, not error)
        List<String> warnings = new ArrayList<>();
        ExitSettings exitSettings = strategy.getExitSettings();
        if (exitSettings != null) {
            warnings.addAll(exitSettings.findOverlappingZones());
        }

        // Create evaluator (indicator engine was already initialized above)
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
        PendingOrder pendingOrder = null;  // Tracks pending entry order (LIMIT/STOP/TRAILING)
        List<Trade> expiredOrders = new ArrayList<>();  // Track expired pending orders

        // maxOpenTrades limits concurrent positions (DCA groups count as one position)
        int maxPositions = strategy.getMaxOpenTrades();
        int maxEntriesPerPosition = strategy.isDcaEnabled() ? strategy.getDcaMaxEntries() : 1;
        int minCandlesBetween = strategy.getMinCandlesBetweenTrades();

        if (onProgress != null) {
            onProgress.accept(new Progress(0, candles.size(), 0, "Running backtest..."));
        }

        for (int i = warmupBars; i < candles.size(); i++) {
            Candle candle = candles.get(i);

            // Update MFE/MAE tracking for all open trades
            for (OpenTradeState ots : openTrades) {
                boolean isLong = "long".equalsIgnoreCase(ots.trade.side());
                ots.updateExcursions(candle.high(), candle.low(), i, isLong);
            }

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

                // If DCA exit triggered, close all trades in the position
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

                        // Use zone's exit configuration
                        AstNode exitConditionAst = matchingZone.exitConditionAst;
                        boolean isMarketExit = !isZoneFallback && zone.exitImmediately();

                        // For Market Exit zones, skip SL/TP - only use DSL condition
                        StopLossType slType = isMarketExit ? StopLossType.NONE : zone.stopLossType();
                        Double slValue = isMarketExit ? null : zone.stopLossValue();
                        TakeProfitType tpType = isMarketExit ? TakeProfitType.NONE : zone.takeProfitType();
                        Double tpValue = isMarketExit ? null : zone.takeProfitValue();

                        // Determine if this is a long or short trade
                        boolean isLong = "long".equalsIgnoreCase(openTrade.side());

                        // Handle CLEAR - reset trailing stop state
                        if (slType == StopLossType.CLEAR) {
                            ots.trailingStopPrice = 0;
                            if (isLong) {
                                ots.highestPriceSinceEntry = candle.close();
                            } else {
                                ots.lowestPriceSinceEntry = candle.close();
                            }
                        }

                        // Calculate stop distance based on type
                        double stopDistance = 0;
                        if (slValue != null && slType != StopLossType.NONE) {
                            if (slType.isPercent()) {
                                stopDistance = entryPrice * (slValue / 100.0);
                            } else if (slType.isAtr()) {
                                stopDistance = positionSizer.calculateATR(candles, i, 14) * slValue;
                            }
                        }

                        // Handle trailing stop
                        if (slType != null && slType.isTrailing() && stopDistance > 0) {
                            if (isLong) {
                                // Long: track highest price, stop below
                                if (candle.high() > ots.highestPriceSinceEntry) {
                                    ots.highestPriceSinceEntry = candle.high();
                                    ots.trailingStopPrice = ots.highestPriceSinceEntry - stopDistance;
                                }
                                if (candle.low() <= ots.trailingStopPrice) {
                                    exitReason = "trailing_stop";
                                    exitPrice = ots.trailingStopPrice;
                                }
                            } else {
                                // Short: track lowest price, stop above
                                if (candle.low() < ots.lowestPriceSinceEntry) {
                                    ots.lowestPriceSinceEntry = candle.low();
                                    ots.trailingStopPrice = ots.lowestPriceSinceEntry + stopDistance;
                                }
                                if (candle.high() >= ots.trailingStopPrice) {
                                    exitReason = "trailing_stop";
                                    exitPrice = ots.trailingStopPrice;
                                }
                            }
                        }
                        // Handle fixed stop-loss
                        else if (slType != null && !slType.isTrailing() && slType != StopLossType.NONE && stopDistance > 0) {
                            if (isLong) {
                                // Long: stop below entry
                                double stopPrice = entryPrice - stopDistance;
                                if (candle.low() <= stopPrice) {
                                    exitReason = "stop_loss";
                                    exitPrice = stopPrice;
                                }
                            } else {
                                // Short: stop above entry
                                double stopPrice = entryPrice + stopDistance;
                                if (candle.high() >= stopPrice) {
                                    exitReason = "stop_loss";
                                    exitPrice = stopPrice;
                                }
                            }
                        }

                        // Check take-profit
                        if (exitReason == null && tpValue != null && tpType != TakeProfitType.NONE) {
                            double tpDistance = 0;
                            if (tpType.isPercent()) {
                                tpDistance = entryPrice * (tpValue / 100.0);
                            } else if (tpType.isAtr()) {
                                tpDistance = positionSizer.calculateATR(candles, i, 14) * tpValue;
                            }

                            if (isLong) {
                                // Long: TP above entry
                                double tpPrice = entryPrice + tpDistance;
                                if (candle.high() >= tpPrice) {
                                    exitReason = "take_profit";
                                    exitPrice = tpPrice;
                                }
                            } else {
                                // Short: TP below entry
                                double tpPrice = entryPrice - tpDistance;
                                if (candle.low() <= tpPrice) {
                                    exitReason = "take_profit";
                                    exitPrice = tpPrice;
                                }
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

                            // Hoops are always AND'ed with DSL (like phases)
                            // DSL must trigger AND all required hoops must match AND no excluded hoops active
                            boolean shouldExit = dslExitSignal && hoopExitSignal;

                            if (shouldExit) {
                                exitReason = isMarketExit ? "market_exit" : "signal";
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
                                // Capture phases and indicators once for this bar
                                List<String> exitPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, i);
                                Map<String, Double> exitIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, i);

                                // Distribute proportionally across entries
                                for (OpenTradeState ots : toClose) {
                                    double proportion = totalRemaining > 0 ? ots.remainingQuantity / totalRemaining : 0;
                                    double exitQty = toExit * proportion;
                                    exitQty = Math.min(exitQty, ots.remainingQuantity);

                                    if (exitQty > 0.0001) {
                                        // Capture indicators at MFE/MAE points
                                        Map<String, Double> mfeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.mfeBar);
                                        Map<String, Double> maeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.maeBar);
                                        Trade partialTrade = ots.trade.partialCloseWithAnalytics(
                                            i, candle.timestamp(), ots.exitPrice, exitQty,
                                            config.commission(), ots.exitReason, ots.exitZone,
                                            ots.mfePercent, ots.maePercent, ots.mfeBar, ots.maeBar,
                                            exitPhases, exitIndicators, mfeIndicators, maeIndicators
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
                        // Capture phases and indicators at exit once for this bar
                        List<String> exitPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, i);
                        Map<String, Double> exitIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, i);
                        for (OpenTradeState ots : toClose) {
                            // Capture indicators at MFE/MAE points
                            Map<String, Double> mfeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.mfeBar);
                            Map<String, Double> maeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.maeBar);
                            Trade closedTrade = ots.trade.partialCloseWithAnalytics(
                                i, candle.timestamp(), ots.exitPrice, ots.remainingQuantity,
                                config.commission(), ots.exitReason, ots.exitZone,
                                ots.mfePercent, ots.maePercent, ots.mfeBar, ots.maeBar,
                                exitPhases, exitIndicators, mfeIndicators, maeIndicators
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
                            // Capture phases and indicators at exit
                            List<String> exitPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, i);
                            Map<String, Double> exitIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, i);
                            // Capture indicators at MFE/MAE points
                            Map<String, Double> mfeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.mfeBar);
                            Map<String, Double> maeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.maeBar);
                            Trade partialTrade = ots.trade.partialCloseWithAnalytics(
                                i, candle.timestamp(), ots.exitPrice, exitQty,
                                config.commission(), ots.exitReason, ots.exitZone,
                                ots.mfePercent, ots.maePercent, ots.mfeBar, ots.maeBar,
                                exitPhases, exitIndicators, mfeIndicators, maeIndicators
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

                // Process pending entry order (LIMIT/STOP/TRAILING)
                boolean pendingOrderFilled = false;
                if (pendingOrder != null) {
                    // Check expiration first
                    if (pendingOrder.isExpired(i)) {
                        // Record expired order
                        String side = strategy.getDirection().getValue();
                        Trade expiredTrade = Trade.expired(
                            strategy.getId(),
                            side,
                            pendingOrder.signalBar,
                            candles.get(pendingOrder.signalBar).timestamp(),
                            pendingOrder.signalPrice,
                            i  // Expiration bar
                        );
                        expiredOrders.add(expiredTrade);
                        trades.add(expiredTrade);
                        pendingOrder = null;
                    } else {
                        // Check fill conditions based on order type
                        Double fillPrice = null;

                        if (pendingOrder.shouldFillLimit(candle.high(), candle.low())) {
                            fillPrice = pendingOrder.getFillPrice();
                        } else if (pendingOrder.shouldFillStop(candle.high(), candle.low())) {
                            fillPrice = pendingOrder.getFillPrice();
                        } else if (pendingOrder.orderType == EntryOrderType.TRAILING) {
                            fillPrice = pendingOrder.updateTrailingAndCheckFill(
                                candle.high(), candle.low(), candle.close()
                            );
                        }

                        if (fillPrice != null) {
                            // Calculate position size at fill time
                            double usedCapital = openTrades.stream()
                                .mapToDouble(ots -> ots.trade.entryPrice() * ots.remainingQuantity)
                                .sum();
                            double availableCapital = currentEquity - usedCapital;

                            double quantity = positionSizer.calculate(config, strategy, currentEquity, fillPrice, candles, i);
                            if (strategy.isDcaEnabled() && maxEntriesPerPosition > 1) {
                                quantity = quantity / maxEntriesPerPosition;
                            }
                            double positionValue = quantity * fillPrice;

                            if (positionValue > availableCapital) {
                                quantity = 0;
                            }

                            if (quantity > 0) {
                                // Execute the fill
                                groupCounter++;
                                currentGroupId = strategy.isDcaEnabled() ? "dca-" + groupCounter : "pos-" + groupCounter;

                                // Capture active phases and indicators at entry
                                List<String> entryPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, i);
                                Map<String, Double> entryIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, i);

                                String side = strategy.getDirection().getValue();
                                Trade newTrade = Trade.open(
                                    strategy.getId(),
                                    side,
                                    i,
                                    candle.timestamp(),
                                    fillPrice,
                                    quantity,
                                    config.commission(),
                                    currentGroupId,
                                    entryPhases,
                                    entryIndicators
                                );

                                OpenTradeState ots = new OpenTradeState(newTrade, fillPrice, entryPhases);

                                // Initialize trailing stop from default zone
                                ExitZone defaultZone = strategy.findMatchingZone(0.0);
                                if (defaultZone != null) {
                                    StopLossType slType = defaultZone.stopLossType();
                                    Double slValue = defaultZone.stopLossValue();
                                    if (slType != null && slType.isTrailing() && slValue != null && slValue > 0) {
                                        double stopDistance = 0;
                                        if (slType.isPercent()) {
                                            stopDistance = fillPrice * (slValue / 100.0);
                                        } else if (slType.isAtr()) {
                                            stopDistance = positionSizer.calculateATR(candles, i, 14) * slValue;
                                        }
                                        // Long: stop below entry; Short: stop above entry
                                        boolean isLong = strategy.isLong();
                                        ots.trailingStopPrice = isLong ? (fillPrice - stopDistance) : (fillPrice + stopDistance);
                                    }
                                }

                                openTrades.add(ots);
                                lastEntryBar = i;
                                pendingOrderFilled = true;
                            } else {
                                // Rejected due to no capital
                                List<String> rejectedPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, i);
                                Map<String, Double> rejectedIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, i);
                                String side = strategy.getDirection().getValue();
                                Trade rejectedTrade = Trade.rejected(
                                    strategy.getId(),
                                    side,
                                    i,
                                    candle.timestamp(),
                                    fillPrice,
                                    rejectedPhases,
                                    rejectedIndicators
                                );
                                trades.add(rejectedTrade);
                            }
                            pendingOrder = null;
                        }
                    }
                }

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

                // Hoops are always AND'ed with DSL (like phases)
                // DSL must trigger AND all required hoops must match AND no excluded hoops active
                boolean signalPresent = dslSignal && hoopSignal;

                // Handle DCA abort mode - close all trades if signal lost
                if (isDcaEntry && dcaMode == DcaMode.ABORT && !signalPresent && toClose.isEmpty()) {
                    List<OpenTradeState> abortedTrades = new ArrayList<>();
                    for (OpenTradeState ots : openTrades) {
                        ots.exitReason = "signal_lost";
                        ots.exitPrice = candle.close();
                        abortedTrades.add(ots);
                    }
                    // Close the aborted trades (close remaining quantity)
                    // Capture phases and indicators at exit once for this bar
                    List<String> abortExitPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, i);
                    Map<String, Double> abortExitIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, i);
                    for (OpenTradeState ots : abortedTrades) {
                        if (ots.remainingQuantity > 0.0001) {
                            // Capture indicators at MFE/MAE points
                            Map<String, Double> mfeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.mfeBar);
                            Map<String, Double> maeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.maeBar);
                            Trade closedTrade = ots.trade.partialCloseWithAnalytics(
                                i, candle.timestamp(), ots.exitPrice, ots.remainingQuantity,
                                config.commission(), ots.exitReason, ots.exitZone,
                                ots.mfePercent, ots.maePercent, ots.mfeBar, ots.maeBar,
                                abortExitPhases, abortExitIndicators, mfeIndicators, maeIndicators
                            );
                            trades.add(closedTrade);
                            currentEquity += closedTrade.pnl() != null ? closedTrade.pnl() : 0;
                            ots.remainingQuantity = 0;
                        }
                        openTrades.remove(ots);
                    }
                }

                // Skip new entry signals if a pending order was just filled this bar
                if (!pendingOrderFilled && canOpenMore && passesMinDistance) {
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
                        // Get entry order type from strategy
                        EntryOrderType orderType = strategy.getEntrySettings().getOrderType();

                        if (orderType == EntryOrderType.MARKET) {
                            // MARKET order: immediate entry (original behavior)
                            double usedCapital = openTrades.stream()
                                .mapToDouble(ots -> ots.trade.entryPrice() * ots.remainingQuantity)
                                .sum();
                            double availableCapital = currentEquity - usedCapital;

                            double quantity = positionSizer.calculate(config, strategy, currentEquity, candle.close(), candles, i);
                            if (strategy.isDcaEnabled() && maxEntriesPerPosition > 1) {
                                quantity = quantity / maxEntriesPerPosition;
                            }
                            double positionValue = quantity * candle.close();

                            if (positionValue > availableCapital) {
                                quantity = 0;
                            }

                            if (quantity > 0) {
                                boolean startingNewPosition = !isDcaEntry;

                                if (startingNewPosition) {
                                    groupCounter++;
                                    currentGroupId = strategy.isDcaEnabled() ? "dca-" + groupCounter : "pos-" + groupCounter;
                                }

                                // Capture active phases and indicators at entry
                                List<String> entryPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, i);
                                Map<String, Double> entryIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, i);

                                String side = strategy.getDirection().getValue();
                                Trade newTrade = Trade.open(
                                    strategy.getId(),
                                    side,
                                    i,
                                    candle.timestamp(),
                                    candle.close(),
                                    quantity,
                                    config.commission(),
                                    currentGroupId,
                                    entryPhases,
                                    entryIndicators
                                );

                                OpenTradeState ots = new OpenTradeState(newTrade, candle.close(), entryPhases);

                                ExitZone defaultZone = strategy.findMatchingZone(0.0);
                                if (defaultZone != null) {
                                    StopLossType slType = defaultZone.stopLossType();
                                    Double slValue = defaultZone.stopLossValue();
                                    if (slType != null && slType.isTrailing() && slValue != null && slValue > 0) {
                                        double stopDistance = 0;
                                        if (slType.isPercent()) {
                                            stopDistance = candle.close() * (slValue / 100.0);
                                        } else if (slType.isAtr()) {
                                            stopDistance = positionSizer.calculateATR(candles, i, 14) * slValue;
                                        }
                                        // Long: stop below entry; Short: stop above entry
                                        boolean isLong = strategy.isLong();
                                        ots.trailingStopPrice = isLong ? (candle.close() - stopDistance) : (candle.close() + stopDistance);
                                    }
                                }

                                openTrades.add(ots);
                                lastEntryBar = i;
                            } else {
                                // Capture active phases and indicators for rejected trade too
                                List<String> rejectedPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, i);
                                Map<String, Double> rejectedIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, i);
                                String side = strategy.getDirection().getValue();
                                Trade rejectedTrade = Trade.rejected(
                                    strategy.getId(),
                                    side,
                                    i,
                                    candle.timestamp(),
                                    candle.close(),
                                    rejectedPhases,
                                    rejectedIndicators
                                );
                                trades.add(rejectedTrade);
                            }
                        } else {
                            // LIMIT, STOP, or TRAILING order: create pending order
                            // New signal replaces any existing pending order
                            OffsetUnit offsetUnit = strategy.getEntrySettings().getOrderOffsetUnit();
                            Double atr = offsetUnit == OffsetUnit.ATR ? indicatorEngine.getATRAt(14, i) : null;
                            pendingOrder = new PendingOrder(
                                i,
                                candle.close(),
                                orderType,
                                offsetUnit,
                                strategy.getEntrySettings().getOrderOffsetValue(),
                                atr,
                                strategy.getEntrySettings().getTrailingReversePercent(),
                                strategy.getEntrySettings().getExpirationBars(),
                                strategy.isLong()
                            );
                        }
                    }
                }
            } catch (Exception e) {
                errors.add("Error at bar " + i + ": " + e.getMessage());
            }
        }

        // Close all open trades at the end (close remaining quantities)
        Candle lastCandle = candles.get(candles.size() - 1);
        int lastBar = candles.size() - 1;
        // Capture phases and indicators at end of data
        List<String> endPhases = tradeAnalytics.getActivePhasesAtBar(phaseStates, lastBar);
        Map<String, Double> endIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, lastBar);
        for (OpenTradeState ots : openTrades) {
            // Final MFE/MAE update for last candle
            boolean isLong = "long".equalsIgnoreCase(ots.trade.side());
            ots.updateExcursions(lastCandle.high(), lastCandle.low(), lastBar, isLong);
            if (ots.remainingQuantity > 0.0001) {
                // Capture indicators at MFE/MAE points
                Map<String, Double> mfeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.mfeBar);
                Map<String, Double> maeIndicators = tradeAnalytics.getIndicatorValuesAtBar(strategy, ots.maeBar);
                Trade closedTrade = ots.trade.partialCloseWithAnalytics(
                    lastBar,
                    lastCandle.timestamp(),
                    lastCandle.close(),
                    ots.remainingQuantity,
                    config.commission(),
                    "end_of_data",
                    null,
                    ots.mfePercent, ots.maePercent, ots.mfeBar, ots.maeBar,
                    endPhases, endIndicators, mfeIndicators, maeIndicators
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
            errors.size() > 100 ? errors.subList(0, 100) : errors,
            warnings
        );
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

        Pattern pattern = Pattern.compile("(SMA|EMA|RSI|MACD|BBANDS|HIGH_OF|LOW_OF|AVG_VOLUME|ATR|RANGE_POSITION)\\((\\d+(?:,\\s*\\d+)*)\\)");
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
     * Initialize the indicator engine with candle data and optional orderflow/funding/OI data.
     */
    private void initializeIndicatorEngine(List<Candle> candles, String resolution, Consumer<Progress> onProgress) {
        if (onProgress != null) {
            onProgress.accept(new Progress(0, 4, 0, "Loading candle data..."));
        }
        indicatorEngine.setCandles(candles, resolution);

        if (aggTrades != null && !aggTrades.isEmpty()) {
            if (onProgress != null) {
                onProgress.accept(new Progress(1, 4, 25, "Processing orderflow data..."));
            }
            indicatorEngine.setAggTrades(aggTrades);
        }
        if (fundingRates != null && !fundingRates.isEmpty()) {
            if (onProgress != null) {
                onProgress.accept(new Progress(2, 4, 50, "Processing funding rates..."));
            }
            indicatorEngine.setFundingRates(fundingRates);
        }
        if (openInterestData != null && !openInterestData.isEmpty()) {
            if (onProgress != null) {
                onProgress.accept(new Progress(3, 4, 75, "Processing open interest..."));
            }
            indicatorEngine.setOpenInterest(openInterestData);
        }
        if (premiumIndexData != null && !premiumIndexData.isEmpty()) {
            if (onProgress != null) {
                onProgress.accept(new Progress(4, 5, 80, "Processing premium index..."));
            }
            indicatorEngine.setPremiumIndex(premiumIndexData);
        }
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
            List.of(error),
            List.of()  // No warnings on error result
        );
    }

    /**
     * Progress update record
     */
    public record Progress(int current, int total, int percentage, String message) {}

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
