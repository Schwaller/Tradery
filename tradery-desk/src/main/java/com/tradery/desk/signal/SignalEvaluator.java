package com.tradery.desk.signal;

import com.tradery.core.dsl.AstNode;
import com.tradery.core.dsl.Parser;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;
import com.tradery.core.model.ExitZone;
import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.engine.ConditionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates strategy conditions against candle data.
 * Uses ConditionEvaluator and IndicatorEngine from tradery-core.
 */
public class SignalEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SignalEvaluator.class);
    private static final Parser parser = new Parser();

    private final PublishedStrategy strategy;
    private final IndicatorEngine engine;
    private final ConditionEvaluator evaluator;
    private final AstNode entryCondition;
    private final List<ParsedExitZone> exitZones;

    private boolean inPosition = false; // Track if we're in a simulated position
    private volatile Instant lastEvalTime;

    /**
     * Create evaluator for a strategy.
     */
    public SignalEvaluator(PublishedStrategy strategy) {
        this.strategy = strategy;
        this.engine = new IndicatorEngine();
        this.evaluator = new ConditionEvaluator(engine);

        // Parse entry condition once
        String entryDsl = strategy.getEntry();
        if (entryDsl != null && !entryDsl.isBlank()) {
            Parser.ParseResult result = parser.parse(entryDsl);
            if (result.success()) {
                this.entryCondition = result.ast();
            } else {
                log.error("Failed to parse entry condition for {}: {}",
                    strategy.getId(), result.error());
                this.entryCondition = null;
            }
        } else {
            this.entryCondition = null;
        }

        // Parse exit zone conditions
        this.exitZones = new ArrayList<>();
        for (ExitZone zone : strategy.getExitZones()) {
            String exitCondition = zone.exitCondition();
            if (exitCondition != null && !exitCondition.isBlank()) {
                Parser.ParseResult result = parser.parse(exitCondition);
                if (result.success()) {
                    exitZones.add(new ParsedExitZone(zone.name(), exitCondition, result.ast()));
                } else {
                    log.error("Failed to parse exit condition for {}/{}: {}",
                        strategy.getId(), zone.name(), result.error());
                }
            }
        }

        log.debug("Created evaluator for {} with {} exit zones",
            strategy.getId(), exitZones.size());
    }

    /**
     * Update the engine with candle data.
     */
    public void setCandles(List<Candle> candles) {
        engine.setCandles(candles, strategy.getTimeframe());
        lastEvalTime = Instant.now();
    }

    /**
     * Evaluate conditions on the most recent closed candle.
     * Returns entry or exit signal if condition is met.
     */
    public Optional<SignalEvent> evaluateOnClose(Candle closedCandle, List<Candle> allCandles) {
        // Update engine with all candles
        setCandles(allCandles);

        int barIndex = engine.getBarCount() - 1;
        if (barIndex < 0) {
            return Optional.empty();
        }

        // If not in position, check entry
        if (!inPosition && entryCondition != null) {
            try {
                boolean entryMet = evaluator.evaluate(entryCondition, barIndex);
                if (entryMet) {
                    inPosition = true; // Simulate entering position
                    return Optional.of(SignalEvent.entry(strategy, closedCandle));
                }
            } catch (Exception e) {
                log.debug("Entry evaluation error for {}: {}", strategy.getId(), e.getMessage());
            }
        }

        // If in position, check exit conditions
        if (inPosition && !exitZones.isEmpty()) {
            for (ParsedExitZone exitZone : exitZones) {
                try {
                    boolean exitMet = evaluator.evaluate(exitZone.ast(), barIndex);
                    if (exitMet) {
                        inPosition = false; // Simulate exiting position
                        return Optional.of(SignalEvent.exit(strategy, closedCandle, exitZone.condition()));
                    }
                } catch (Exception e) {
                    log.debug("Exit evaluation error for {}/{}: {}",
                        strategy.getId(), exitZone.name(), e.getMessage());
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Evaluate entry condition only (ignore position state).
     * Useful for signal-only mode without position tracking.
     */
    public Optional<SignalEvent> evaluateEntry(Candle closedCandle, List<Candle> allCandles) {
        if (entryCondition == null) {
            return Optional.empty();
        }

        setCandles(allCandles);
        int barIndex = engine.getBarCount() - 1;
        if (barIndex < 0) {
            return Optional.empty();
        }

        try {
            boolean entryMet = evaluator.evaluate(entryCondition, barIndex);
            if (entryMet) {
                return Optional.of(SignalEvent.entry(strategy, closedCandle));
            }
        } catch (Exception e) {
            log.debug("Entry evaluation error for {}: {}", strategy.getId(), e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Reset position state.
     */
    public void resetPosition() {
        inPosition = false;
    }

    /**
     * Check if currently in simulated position.
     */
    public boolean isInPosition() {
        return inPosition;
    }

    public PublishedStrategy getStrategy() {
        return strategy;
    }

    public IndicatorEngine getEngine() {
        return engine;
    }

    public Instant getLastEvalTime() {
        return lastEvalTime;
    }

    /**
     * Parsed exit zone with AST.
     */
    private record ParsedExitZone(String name, String condition, AstNode ast) {}
}
