package com.tradery.engine;

import com.tradery.indicators.IndicatorEngine;
import com.tradery.model.Candle;
import com.tradery.model.ExitZone;
import com.tradery.model.Strategy;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts indicator values at specific bars for trade analytics.
 * Captures DSL-referenced indicators plus standard context indicators for AI analysis.
 */
public class TradeAnalytics {

    private final IndicatorEngine indicatorEngine;

    public TradeAnalytics(IndicatorEngine indicatorEngine) {
        this.indicatorEngine = indicatorEngine;
    }

    /**
     * Get list of active phase IDs at a given bar index.
     *
     * @param phaseStates Map of phase ID to boolean array of states
     * @param barIndex Bar index to check
     * @return List of active phase IDs
     */
    public List<String> getActivePhasesAtBar(Map<String, boolean[]> phaseStates, int barIndex) {
        List<String> activePhases = new ArrayList<>();
        for (Map.Entry<String, boolean[]> entry : phaseStates.entrySet()) {
            boolean[] states = entry.getValue();
            if (states != null && barIndex < states.length && states[barIndex]) {
                activePhases.add(entry.getKey());
            }
        }
        return activePhases;
    }

    /**
     * Extract indicator values at a given bar based on indicators used in DSL expressions.
     * Parses the entry/exit conditions to find indicator references and captures their values.
     *
     * @param strategy Strategy containing DSL expressions
     * @param barIndex Bar index to extract values at
     * @return Map of indicator name to value
     */
    public Map<String, Double> getIndicatorValuesAtBar(Strategy strategy, int barIndex) {
        Map<String, Double> values = new HashMap<>();

        // Collect all DSL expressions to analyze
        StringBuilder allExpressions = new StringBuilder();
        if (strategy.getEntry() != null) {
            allExpressions.append(strategy.getEntry()).append(" ");
        }
        for (ExitZone zone : strategy.getExitZones()) {
            if (zone.exitCondition() != null) {
                allExpressions.append(zone.exitCondition()).append(" ");
            }
        }

        String expr = allExpressions.toString();

        // Extract single-parameter indicators
        extractSimpleIndicator(expr, barIndex, values, "SMA", indicatorEngine::getSMAAt);
        extractSimpleIndicator(expr, barIndex, values, "EMA", indicatorEngine::getEMAAt);
        extractSimpleIndicator(expr, barIndex, values, "RSI", indicatorEngine::getRSIAt);
        extractSimpleIndicator(expr, barIndex, values, "ATR", indicatorEngine::getATRAt);

        // Extract multi-value indicators
        extractADXIndicators(expr, barIndex, values);
        extractMACDIndicators(expr, barIndex, values);
        extractBBandsIndicators(expr, barIndex, values);

        // Always add context indicators for AI analysis
        addContextIndicators(barIndex, values);

        // Always include price for context
        Candle candle = indicatorEngine.getCandleAt(barIndex);
        if (candle != null) {
            values.put("price", candle.close());
            values.put("volume", candle.volume());
        }

        return values;
    }

    /**
     * Add standard context indicators for AI analysis.
     * These are always captured regardless of what's in the DSL conditions.
     */
    private void addContextIndicators(int barIndex, Map<String, Double> values) {
        // Volatility context
        double atr14 = indicatorEngine.getATRAt(14, barIndex);
        if (!Double.isNaN(atr14)) values.putIfAbsent("ATR(14)", atr14);

        // Trend context - short and long term
        double sma50 = indicatorEngine.getSMAAt(50, barIndex);
        double sma200 = indicatorEngine.getSMAAt(200, barIndex);
        if (!Double.isNaN(sma50)) values.putIfAbsent("SMA(50)", sma50);
        if (!Double.isNaN(sma200)) values.putIfAbsent("SMA(200)", sma200);

        // Momentum context
        double rsi14 = indicatorEngine.getRSIAt(14, barIndex);
        if (!Double.isNaN(rsi14)) values.putIfAbsent("RSI(14)", rsi14);

        // Trend strength
        double adx14 = indicatorEngine.getADXAt(14, barIndex);
        if (!Double.isNaN(adx14)) values.putIfAbsent("ADX(14)", adx14);

        // Volume context
        double avgVol20 = indicatorEngine.getAvgVolumeAt(20, barIndex);
        if (!Double.isNaN(avgVol20)) values.putIfAbsent("AVG_VOLUME(20)", avgVol20);
    }

    /**
     * Generic extraction for single-parameter indicators (SMA, EMA, RSI, ATR).
     */
    private void extractSimpleIndicator(String expr, int barIndex, Map<String, Double> values,
                                        String indicatorName, java.util.function.BiFunction<Integer, Integer, Double> evaluator) {
        Pattern pattern = Pattern.compile(indicatorName + "\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(expr);
        while (matcher.find()) {
            int period = Integer.parseInt(matcher.group(1));
            String key = indicatorName + "(" + period + ")";
            if (!values.containsKey(key)) {
                double value = evaluator.apply(period, barIndex);
                if (!Double.isNaN(value)) {
                    values.put(key, value);
                }
            }
        }
    }

    private void extractADXIndicators(String expr, int barIndex, Map<String, Double> values) {
        Pattern pattern = Pattern.compile("(ADX|PLUS_DI|MINUS_DI)\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(expr);
        Set<Integer> periods = new HashSet<>();
        while (matcher.find()) {
            periods.add(Integer.parseInt(matcher.group(2)));
        }
        for (int period : periods) {
            double adx = indicatorEngine.getADXAt(period, barIndex);
            double plusDi = indicatorEngine.getPlusDIAt(period, barIndex);
            double minusDi = indicatorEngine.getMinusDIAt(period, barIndex);
            if (!Double.isNaN(adx)) values.put("ADX(" + period + ")", adx);
            if (!Double.isNaN(plusDi)) values.put("PLUS_DI(" + period + ")", plusDi);
            if (!Double.isNaN(minusDi)) values.put("MINUS_DI(" + period + ")", minusDi);
        }
    }

    private void extractMACDIndicators(String expr, int barIndex, Map<String, Double> values) {
        Pattern pattern = Pattern.compile("MACD\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\)");
        Matcher matcher = pattern.matcher(expr);
        while (matcher.find()) {
            int fast = Integer.parseInt(matcher.group(1));
            int slow = Integer.parseInt(matcher.group(2));
            int signal = Integer.parseInt(matcher.group(3));
            String prefix = "MACD(" + fast + "," + slow + "," + signal + ")";
            if (!values.containsKey(prefix + ".line")) {
                double line = indicatorEngine.getMACDLineAt(fast, slow, signal, barIndex);
                double sig = indicatorEngine.getMACDSignalAt(fast, slow, signal, barIndex);
                double hist = indicatorEngine.getMACDHistogramAt(fast, slow, signal, barIndex);
                if (!Double.isNaN(line)) values.put(prefix + ".line", line);
                if (!Double.isNaN(sig)) values.put(prefix + ".signal", sig);
                if (!Double.isNaN(hist)) values.put(prefix + ".histogram", hist);
            }
        }
    }

    private void extractBBandsIndicators(String expr, int barIndex, Map<String, Double> values) {
        Pattern pattern = Pattern.compile("BBANDS\\((\\d+)\\s*,\\s*([\\d.]+)\\)");
        Matcher matcher = pattern.matcher(expr);
        while (matcher.find()) {
            int period = Integer.parseInt(matcher.group(1));
            double stdDev = Double.parseDouble(matcher.group(2));
            String prefix = "BBANDS(" + period + "," + (int) stdDev + ")";
            if (!values.containsKey(prefix + ".upper")) {
                double upper = indicatorEngine.getBollingerUpperAt(period, stdDev, barIndex);
                double middle = indicatorEngine.getBollingerMiddleAt(period, stdDev, barIndex);
                double lower = indicatorEngine.getBollingerLowerAt(period, stdDev, barIndex);
                if (!Double.isNaN(upper)) values.put(prefix + ".upper", upper);
                if (!Double.isNaN(middle)) values.put(prefix + ".middle", middle);
                if (!Double.isNaN(lower)) values.put(prefix + ".lower", lower);
            }
        }
    }
}
