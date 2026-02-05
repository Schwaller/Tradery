package com.tradery.engine;

import com.tradery.core.dsl.AstNode;
import com.tradery.core.indicators.IndicatorEngine;

import java.util.List;

/**
 * Evaluates DSL AST nodes against indicator data at a specific bar.
 */
public class ConditionEvaluator {

    private final IndicatorEngine engine;

    public ConditionEvaluator(IndicatorEngine engine) {
        this.engine = engine;
    }

    /**
     * Evaluate an AST node at a specific bar index.
     * Returns true if the condition is met, false otherwise.
     */
    public boolean evaluate(AstNode node, int barIndex) {
        Object result = evaluateNode(node, barIndex);
        if (result instanceof Boolean b) {
            return b;
        }
        throw new EvaluationException("Expected boolean result, got " + result.getClass().getSimpleName());
    }

    /**
     * Evaluate a node and return the result (boolean or double)
     */
    private Object evaluateNode(AstNode node, int barIndex) {
        return switch (node) {
            case AstNode.Comparison c -> evaluateComparison(c, barIndex);
            case AstNode.CrossComparison c -> evaluateCrossComparison(c, barIndex);
            case AstNode.LogicalExpression l -> evaluateLogical(l, barIndex);
            case AstNode.ArithmeticExpression a -> evaluateArithmetic(a, barIndex);
            case AstNode.IndicatorCall i -> evaluateIndicator(i, barIndex);
            case AstNode.PropertyAccess p -> evaluateProperty(p, barIndex);
            case AstNode.RangeFunctionCall r -> evaluateRangeFunction(r, barIndex);
            case AstNode.VolumeFunctionCall v -> evaluateVolumeFunction(v, barIndex);
            case AstNode.TimeFunctionCall t -> evaluateTimeFunction(t, barIndex);
            case AstNode.MoonFunctionCall m -> evaluateMoonFunction(m, barIndex);
            case AstNode.HolidayFunctionCall h -> evaluateHolidayFunction(h, barIndex);
            case AstNode.FomcFunctionCall f -> evaluateFomcFunction(f, barIndex);
            case AstNode.OrderflowFunctionCall o -> evaluateOrderflowFunction(o, barIndex);
            case AstNode.FundingFunctionCall f -> evaluateFundingFunction(f, barIndex);
            case AstNode.PremiumFunctionCall p -> evaluatePremiumFunction(p, barIndex);
            case AstNode.SessionOrderflowFunctionCall s -> evaluateSessionOrderflowFunction(s, barIndex);
            case AstNode.OhlcvVolumeFunctionCall o -> evaluateOhlcvVolumeFunction(o, barIndex);
            case AstNode.OIFunctionCall o -> evaluateOIFunction(o, barIndex);
            case AstNode.RayFunctionCall r -> evaluateRayFunction(r, barIndex);
            case AstNode.AggregateFunctionCall a -> evaluateAggregateFunction(a, barIndex);
            case AstNode.MathFunctionCall m -> evaluateMathFunction(m, barIndex);
            case AstNode.CandlePatternCall c -> evaluateCandlePattern(c, barIndex);
            case AstNode.CandlePropCall c -> evaluateCandleProp(c, barIndex);
            case AstNode.FootprintFunctionCall f -> evaluateFootprintFunction(f, barIndex);
            case AstNode.ExchangeFunctionCall e -> evaluateExchangeFunction(e, barIndex);
            case AstNode.LookbackAccess l -> evaluateLookback(l, barIndex);
            case AstNode.PriceReference p -> evaluatePrice(p, barIndex);
            case AstNode.NumberLiteral n -> n.value();
            case AstNode.BooleanLiteral b -> b.value();
        };
    }

    private boolean evaluateComparison(AstNode.Comparison node, int barIndex) {
        double left = toDouble(evaluateNode(node.left(), barIndex));
        double right = toDouble(evaluateNode(node.right(), barIndex));

        if (Double.isNaN(left) || Double.isNaN(right)) {
            return false;
        }

        return switch (node.operator()) {
            case ">" -> left > right;
            case "<" -> left < right;
            case ">=" -> left >= right;
            case "<=" -> left <= right;
            case "==" -> Math.abs(left - right) < 0.0000001;
            default -> throw new EvaluationException("Unknown operator: " + node.operator());
        };
    }

    private boolean evaluateCrossComparison(AstNode.CrossComparison node, int barIndex) {
        if (barIndex < 1) {
            return false;
        }

        double leftCurrent = toDouble(evaluateNode(node.left(), barIndex));
        double leftPrev = toDouble(evaluateNode(node.left(), barIndex - 1));
        double rightCurrent = toDouble(evaluateNode(node.right(), barIndex));
        double rightPrev = toDouble(evaluateNode(node.right(), barIndex - 1));

        if (Double.isNaN(leftCurrent) || Double.isNaN(leftPrev) ||
            Double.isNaN(rightCurrent) || Double.isNaN(rightPrev)) {
            return false;
        }

        return switch (node.operator()) {
            case "crosses_above" -> leftPrev <= rightPrev && leftCurrent > rightCurrent;
            case "crosses_below" -> leftPrev >= rightPrev && leftCurrent < rightCurrent;
            default -> throw new EvaluationException("Unknown cross operator: " + node.operator());
        };
    }

    private boolean evaluateLogical(AstNode.LogicalExpression node, int barIndex) {
        boolean left = toBoolean(evaluateNode(node.left(), barIndex));

        // Short-circuit evaluation
        if ("AND".equals(node.operator()) && !left) {
            return false;
        }
        if ("OR".equals(node.operator()) && left) {
            return true;
        }

        boolean right = toBoolean(evaluateNode(node.right(), barIndex));

        return switch (node.operator()) {
            case "AND" -> left && right;
            case "OR" -> left || right;
            default -> throw new EvaluationException("Unknown logical operator: " + node.operator());
        };
    }

    private double evaluateArithmetic(AstNode.ArithmeticExpression node, int barIndex) {
        double left = toDouble(evaluateNode(node.left(), barIndex));
        double right = toDouble(evaluateNode(node.right(), barIndex));

        return switch (node.operator()) {
            case "*" -> left * right;
            case "/" -> right != 0 ? left / right : Double.NaN;
            case "+" -> left + right;
            case "-" -> left - right;
            default -> throw new EvaluationException("Unknown arithmetic operator: " + node.operator());
        };
    }

    private double evaluateIndicator(AstNode.IndicatorCall node, int barIndex) {
        List<Double> params = node.params();

        return switch (node.indicator()) {
            case "SMA" -> engine.getSMAAt(params.get(0).intValue(), barIndex);
            case "EMA" -> engine.getEMAAt(params.get(0).intValue(), barIndex);
            case "RSI" -> engine.getRSIAt(params.get(0).intValue(), barIndex);
            case "ATR" -> engine.getATRAt(params.get(0).intValue(), barIndex);
            case "ADX" -> engine.getADXAt(params.get(0).intValue(), barIndex);
            case "PLUS_DI" -> engine.getPlusDIAt(params.get(0).intValue(), barIndex);
            case "MINUS_DI" -> engine.getMinusDIAt(params.get(0).intValue(), barIndex);
            case "MACD" -> engine.getMACDLineAt(
                params.get(0).intValue(),
                params.get(1).intValue(),
                params.get(2).intValue(),
                barIndex
            );
            case "BBANDS" -> engine.getBollingerMiddleAt(
                params.get(0).intValue(),
                params.get(1),
                barIndex
            );
            case "STOCHASTIC" -> {
                int kPeriod = params.get(0).intValue();
                int dPeriod = params.size() > 1 ? params.get(1).intValue() : 3;
                yield engine.getStochasticKAt(kPeriod, barIndex);
            }
            case "SUPERTREND" -> {
                // Without property access, return the trend direction (1 = up, -1 = down)
                int period = params.get(0).intValue();
                double multiplier = params.get(1);
                yield engine.getSupertrendTrendAt(period, multiplier, barIndex);
            }
            case "ICHIMOKU" -> {
                // Without property access, return the tenkan-sen (conversion line) by default
                int conversionPeriod = params.size() > 0 ? params.get(0).intValue() : 9;
                yield engine.getIchimokuTenkanAt(conversionPeriod, barIndex);
            }
            default -> throw new EvaluationException("Unknown indicator: " + node.indicator());
        };
    }

    private double evaluateProperty(AstNode.PropertyAccess node, int barIndex) {
        AstNode.IndicatorCall indicator = node.object();
        String property = node.property();
        List<Double> params = indicator.params();

        return switch (indicator.indicator()) {
            case "MACD" -> {
                int fast = params.get(0).intValue();
                int slow = params.get(1).intValue();
                int signal = params.get(2).intValue();
                yield switch (property) {
                    case "line" -> engine.getMACDLineAt(fast, slow, signal, barIndex);
                    case "signal" -> engine.getMACDSignalAt(fast, slow, signal, barIndex);
                    case "histogram" -> engine.getMACDHistogramAt(fast, slow, signal, barIndex);
                    default -> throw new EvaluationException("Unknown MACD property: " + property);
                };
            }
            case "BBANDS" -> {
                int period = params.get(0).intValue();
                double stdDev = params.get(1);
                yield switch (property) {
                    case "upper" -> engine.getBollingerUpperAt(period, stdDev, barIndex);
                    case "middle" -> engine.getBollingerMiddleAt(period, stdDev, barIndex);
                    case "lower" -> engine.getBollingerLowerAt(period, stdDev, barIndex);
                    case "width" -> engine.getBollingerUpperAt(period, stdDev, barIndex) -
                                    engine.getBollingerLowerAt(period, stdDev, barIndex);
                    default -> throw new EvaluationException("Unknown BBANDS property: " + property);
                };
            }
            case "STOCHASTIC" -> {
                int kPeriod = params.get(0).intValue();
                int dPeriod = params.size() > 1 ? params.get(1).intValue() : 3;
                yield switch (property) {
                    case "k" -> engine.getStochasticKAt(kPeriod, barIndex);
                    case "d" -> engine.getStochasticDAt(kPeriod, dPeriod, barIndex);
                    default -> throw new EvaluationException("Unknown STOCHASTIC property: " + property);
                };
            }
            case "SUPERTREND" -> {
                int period = params.get(0).intValue();
                double multiplier = params.get(1);
                yield switch (property) {
                    case "trend" -> engine.getSupertrendTrendAt(period, multiplier, barIndex);
                    case "upper" -> engine.getSupertrendUpperAt(period, multiplier, barIndex);
                    case "lower" -> engine.getSupertrendLowerAt(period, multiplier, barIndex);
                    default -> throw new EvaluationException("Unknown SUPERTREND property: " + property);
                };
            }
            case "ICHIMOKU" -> {
                // Default Ichimoku parameters: 9, 26, 52, 26
                int conversionPeriod = params.size() > 0 ? params.get(0).intValue() : 9;
                int basePeriod = params.size() > 1 ? params.get(1).intValue() : 26;
                int spanBPeriod = params.size() > 2 ? params.get(2).intValue() : 52;
                int displacement = params.size() > 3 ? params.get(3).intValue() : 26;

                yield switch (property) {
                    case "tenkan" -> engine.getIchimokuTenkanAt(conversionPeriod, barIndex);
                    case "kijun" -> engine.getIchimokuKijunAt(basePeriod, barIndex);
                    case "senkou_a" -> engine.getIchimokuSenkouAAt(conversionPeriod, basePeriod, displacement, barIndex);
                    case "senkou_b" -> engine.getIchimokuSenkouBAt(spanBPeriod, displacement, barIndex);
                    case "chikou" -> engine.getIchimokuChikouAt(displacement, barIndex);
                    default -> throw new EvaluationException("Unknown ICHIMOKU property: " + property);
                };
            }
            default -> throw new EvaluationException("Unknown indicator for property access: " + indicator.indicator());
        };
    }

    private double evaluateRangeFunction(AstNode.RangeFunctionCall node, int barIndex) {
        int period = node.period();

        return switch (node.func()) {
            case "HIGH_OF" -> engine.getHighOfAt(period, barIndex);
            case "LOW_OF" -> engine.getLowOfAt(period, barIndex);
            case "RANGE_POSITION" -> {
                int skip = node.skip() != null ? node.skip() : 0;
                yield engine.getRangePositionAt(period, skip, barIndex);
            }
            default -> throw new EvaluationException("Unknown range function: " + node.func());
        };
    }

    private double evaluateVolumeFunction(AstNode.VolumeFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "AVG_VOLUME" -> engine.getAvgVolumeAt(node.period(), barIndex);
            default -> throw new EvaluationException("Unknown volume function: " + node.func());
        };
    }

    private double evaluateTimeFunction(AstNode.TimeFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "DAYOFWEEK" -> engine.getDayOfWeekAt(barIndex);
            case "HOUR" -> engine.getHourAt(barIndex);
            case "DAY" -> engine.getDayAt(barIndex);
            case "MONTH" -> engine.getMonthAt(barIndex);
            default -> throw new EvaluationException("Unknown time function: " + node.func());
        };
    }

    private double evaluateMoonFunction(AstNode.MoonFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "MOON_PHASE" -> engine.getMoonPhaseAt(barIndex);
            default -> throw new EvaluationException("Unknown moon function: " + node.func());
        };
    }

    private double evaluateHolidayFunction(AstNode.HolidayFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "IS_US_HOLIDAY" -> engine.isUSHolidayAt(barIndex) ? 1.0 : 0.0;
            default -> throw new EvaluationException("Unknown holiday function: " + node.func());
        };
    }

    private double evaluateFomcFunction(AstNode.FomcFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "IS_FOMC_MEETING" -> engine.isFomcMeetingAt(barIndex) ? 1.0 : 0.0;
            default -> throw new EvaluationException("Unknown FOMC function: " + node.func());
        };
    }

    private double evaluateOrderflowFunction(AstNode.OrderflowFunctionCall node, int barIndex) {
        Integer period = node.period();

        return switch (node.func()) {
            case "VWAP" -> engine.getVWAPAt(barIndex);
            case "POC" -> engine.getPOCAt(period != null ? period : 20, barIndex);
            case "VAH" -> engine.getVAHAt(period != null ? period : 20, barIndex);
            case "VAL" -> engine.getVALAt(period != null ? period : 20, barIndex);
            case "DELTA" -> engine.getDeltaAt(barIndex);
            case "CUM_DELTA" -> engine.getCumulativeDeltaAt(barIndex);
            // Whale / Large Trade Detection (threshold is passed as period)
            case "WHALE_DELTA" -> engine.getWhaleDeltaAt(period != null ? period.doubleValue() : 0, barIndex);
            case "WHALE_BUY_VOL" -> engine.getWhaleBuyVolAt(period != null ? period.doubleValue() : 0, barIndex);
            case "WHALE_SELL_VOL" -> engine.getWhaleSellVolAt(period != null ? period.doubleValue() : 0, barIndex);
            case "LARGE_TRADE_COUNT" -> engine.getLargeTradeCountAt(period != null ? period.doubleValue() : 0, barIndex);
            default -> throw new EvaluationException("Unknown orderflow function: " + node.func());
        };
    }

    private double evaluateFundingFunction(AstNode.FundingFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "FUNDING" -> engine.getFundingAt(barIndex);
            case "FUNDING_8H" -> engine.getFunding8HAvgAt(barIndex);
            default -> throw new EvaluationException("Unknown funding function: " + node.func());
        };
    }

    private double evaluatePremiumFunction(AstNode.PremiumFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "PREMIUM" -> engine.getPremiumAt(barIndex);
            case "PREMIUM_AVG" -> engine.getPremiumAvgAt(node.period(), barIndex);
            default -> throw new EvaluationException("Unknown premium function: " + node.func());
        };
    }

    private double evaluateSessionOrderflowFunction(AstNode.SessionOrderflowFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "PREV_DAY_POC" -> engine.getPrevDayPOCAt(barIndex);
            case "PREV_DAY_VAH" -> engine.getPrevDayVAHAt(barIndex);
            case "PREV_DAY_VAL" -> engine.getPrevDayVALAt(barIndex);
            case "TODAY_POC" -> engine.getTodayPOCAt(barIndex);
            case "TODAY_VAH" -> engine.getTodayVAHAt(barIndex);
            case "TODAY_VAL" -> engine.getTodayVALAt(barIndex);
            default -> throw new EvaluationException("Unknown session orderflow function: " + node.func());
        };
    }

    private double evaluateOhlcvVolumeFunction(AstNode.OhlcvVolumeFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "QUOTE_VOLUME" -> engine.getQuoteVolumeAt(barIndex);
            case "BUY_VOLUME" -> engine.getTakerBuyVolumeAt(barIndex);
            case "SELL_VOLUME" -> engine.getTakerSellVolumeAt(barIndex);
            case "OHLCV_DELTA" -> engine.getOhlcvDeltaAt(barIndex);
            case "OHLCV_CVD" -> engine.getOhlcvCvdAt(barIndex);
            case "BUY_RATIO" -> engine.getBuyRatioAt(barIndex);
            case "TRADE_COUNT" -> engine.getTradeCountAt(barIndex);
            default -> throw new EvaluationException("Unknown OHLCV volume function: " + node.func());
        };
    }

    private double evaluateOIFunction(AstNode.OIFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "OI" -> engine.getOIAt(barIndex);
            case "OI_CHANGE" -> engine.getOIChangeAt(barIndex);
            case "OI_DELTA" -> engine.getOIDeltaAt(node.period(), barIndex);
            default -> throw new EvaluationException("Unknown OI function: " + node.func());
        };
    }

    /**
     * Evaluate rotating ray functions for resistance/support trendline detection.
     * Returns boolean (1.0/0.0) for _BROKEN/_CROSSED, double for _DISTANCE, int for _COUNT.
     */
    private double evaluateRayFunction(AstNode.RayFunctionCall node, int barIndex) {
        String func = node.func();
        int lookback = node.lookback();
        int skip = node.skip();
        Integer rayNum = node.rayNum();

        return switch (func) {
            // Resistance ray functions
            case "RESISTANCE_RAY_BROKEN" ->
                engine.isResistanceRayBroken(rayNum, lookback, skip, barIndex) ? 1.0 : 0.0;
            case "RESISTANCE_RAY_CROSSED" ->
                engine.didResistanceRayCross(rayNum, lookback, skip, barIndex) ? 1.0 : 0.0;
            case "RESISTANCE_RAY_DISTANCE" ->
                engine.getResistanceRayDistance(rayNum, lookback, skip, barIndex);
            case "RESISTANCE_RAYS_BROKEN" ->
                engine.getResistanceRaysBroken(lookback, skip, barIndex);
            case "RESISTANCE_RAY_COUNT" ->
                engine.getResistanceRayCount(lookback, skip, barIndex);

            // Support ray functions
            case "SUPPORT_RAY_BROKEN" ->
                engine.isSupportRayBroken(rayNum, lookback, skip, barIndex) ? 1.0 : 0.0;
            case "SUPPORT_RAY_CROSSED" ->
                engine.didSupportRayCross(rayNum, lookback, skip, barIndex) ? 1.0 : 0.0;
            case "SUPPORT_RAY_DISTANCE" ->
                engine.getSupportRayDistance(rayNum, lookback, skip, barIndex);
            case "SUPPORT_RAYS_BROKEN" ->
                engine.getSupportRaysBroken(lookback, skip, barIndex);
            case "SUPPORT_RAY_COUNT" ->
                engine.getSupportRayCount(lookback, skip, barIndex);

            default -> throw new EvaluationException("Unknown ray function: " + func);
        };
    }

    /**
     * Evaluate aggregate functions: LOWEST, HIGHEST, PERCENTILE
     * These evaluate an expression over a lookback period and return an aggregate value.
     */
    private double evaluateAggregateFunction(AstNode.AggregateFunctionCall node, int barIndex) {
        int period = node.period();

        // Need enough bars for the lookback
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        return switch (node.func()) {
            case "LOWEST" -> {
                double lowest = Double.MAX_VALUE;
                for (int i = 0; i < period; i++) {
                    double value = toDouble(evaluateNode(node.expression(), barIndex - i));
                    if (!Double.isNaN(value) && value < lowest) {
                        lowest = value;
                    }
                }
                yield lowest == Double.MAX_VALUE ? Double.NaN : lowest;
            }
            case "HIGHEST" -> {
                double highest = -Double.MAX_VALUE;
                for (int i = 0; i < period; i++) {
                    double value = toDouble(evaluateNode(node.expression(), barIndex - i));
                    if (!Double.isNaN(value) && value > highest) {
                        highest = value;
                    }
                }
                yield highest == -Double.MAX_VALUE ? Double.NaN : highest;
            }
            case "PERCENTILE" -> {
                // Calculate what percentile the current value is at within the lookback period
                double currentValue = toDouble(evaluateNode(node.expression(), barIndex));
                if (Double.isNaN(currentValue)) {
                    yield Double.NaN;
                }

                int belowCount = 0;
                int validCount = 0;
                for (int i = 0; i < period; i++) {
                    double value = toDouble(evaluateNode(node.expression(), barIndex - i));
                    if (!Double.isNaN(value)) {
                        validCount++;
                        if (value < currentValue) {
                            belowCount++;
                        }
                    }
                }

                if (validCount == 0) {
                    yield Double.NaN;
                }

                // Return percentile (0-100)
                yield (belowCount * 100.0) / validCount;
            }
            default -> throw new EvaluationException("Unknown aggregate function: " + node.func());
        };
    }

    /**
     * Evaluate math utility functions: abs, min, max
     */
    private double evaluateMathFunction(AstNode.MathFunctionCall node, int barIndex) {
        return switch (node.func()) {
            case "abs" -> {
                double value = toDouble(evaluateNode(node.args().get(0), barIndex));
                yield Math.abs(value);
            }
            case "min" -> {
                double val1 = toDouble(evaluateNode(node.args().get(0), barIndex));
                double val2 = toDouble(evaluateNode(node.args().get(1), barIndex));
                yield Math.min(val1, val2);
            }
            case "max" -> {
                double val1 = toDouble(evaluateNode(node.args().get(0), barIndex));
                double val2 = toDouble(evaluateNode(node.args().get(1), barIndex));
                yield Math.max(val1, val2);
            }
            default -> throw new EvaluationException("Unknown math function: " + node.func());
        };
    }

    /**
     * Evaluate candlestick pattern functions: HAMMER, SHOOTING_STAR, DOJI
     * Returns 1.0 if pattern detected, 0.0 otherwise.
     */
    private double evaluateCandlePattern(AstNode.CandlePatternCall node, int barIndex) {
        double open = engine.getOpenAt(barIndex);
        double high = engine.getHighAt(barIndex);
        double low = engine.getLowAt(barIndex);
        double close = engine.getCloseAt(barIndex);

        double body = Math.abs(close - open);
        double range = high - low;
        double upperWick = high - Math.max(open, close);
        double lowerWick = Math.min(open, close) - low;

        // Avoid division by zero
        if (range < 0.0000001 || body < 0.0000001) {
            // For DOJI, a tiny body relative to range is actually the pattern
            if ("DOJI".equals(node.func())) {
                double maxBodyRatio = node.ratio() != null ? node.ratio() : 0.1;
                return (body / range) <= maxBodyRatio ? 1.0 : 0.0;
            }
            return 0.0;
        }

        return switch (node.func()) {
            case "HAMMER" -> {
                // Hammer: long lower wick, small upper wick
                double minWickRatio = node.ratio() != null ? node.ratio() : 2.0;
                boolean longLowerWick = lowerWick >= body * minWickRatio;
                boolean smallUpperWick = upperWick <= body * 0.5;
                yield (longLowerWick && smallUpperWick) ? 1.0 : 0.0;
            }
            case "SHOOTING_STAR" -> {
                // Shooting star: long upper wick, small lower wick
                double minWickRatio = node.ratio() != null ? node.ratio() : 2.0;
                boolean longUpperWick = upperWick >= body * minWickRatio;
                boolean smallLowerWick = lowerWick <= body * 0.5;
                yield (longUpperWick && smallLowerWick) ? 1.0 : 0.0;
            }
            case "DOJI" -> {
                // Doji: tiny body relative to range
                double maxBodyRatio = node.ratio() != null ? node.ratio() : 0.1;
                yield (body / range) <= maxBodyRatio ? 1.0 : 0.0;
            }
            default -> throw new EvaluationException("Unknown candle pattern: " + node.func());
        };
    }

    /**
     * Evaluate candlestick property functions: BODY_SIZE, BODY_RATIO, IS_BULLISH, IS_BEARISH
     */
    private double evaluateCandleProp(AstNode.CandlePropCall node, int barIndex) {
        double open = engine.getOpenAt(barIndex);
        double high = engine.getHighAt(barIndex);
        double low = engine.getLowAt(barIndex);
        double close = engine.getCloseAt(barIndex);

        return switch (node.func()) {
            case "BODY_SIZE" -> Math.abs(close - open);
            case "BODY_RATIO" -> {
                double range = high - low;
                yield range > 0.0000001 ? Math.abs(close - open) / range : 0.0;
            }
            case "IS_BULLISH" -> close > open ? 1.0 : 0.0;
            case "IS_BEARISH" -> close < open ? 1.0 : 0.0;
            default -> throw new EvaluationException("Unknown candle property: " + node.func());
        };
    }

    /**
     * Evaluate footprint functions for orderflow analysis.
     * These analyze trade flow at price levels within candles.
     */
    private double evaluateFootprintFunction(AstNode.FootprintFunctionCall node, int barIndex) {
        List<Double> params = node.params();

        return switch (node.func()) {
            // Imbalance functions - return ratio of buy/sell at price level
            case "IMBALANCE_AT_POC" -> engine.getImbalanceAtPOC(barIndex);
            case "IMBALANCE_AT_VAH" -> engine.getImbalanceAtVAH(barIndex);
            case "IMBALANCE_AT_VAL" -> engine.getImbalanceAtVAL(barIndex);

            // Stacked imbalance detection - returns 1 if n consecutive imbalances found
            case "STACKED_BUY_IMBALANCES" -> {
                int n = params.isEmpty() ? 3 : params.get(0).intValue();
                yield engine.hasStackedBuyImbalances(n, barIndex) ? 1.0 : 0.0;
            }
            case "STACKED_SELL_IMBALANCES" -> {
                int n = params.isEmpty() ? 3 : params.get(0).intValue();
                yield engine.hasStackedSellImbalances(n, barIndex) ? 1.0 : 0.0;
            }

            // Absorption detection - high volume + small price movement
            case "ABSORPTION" -> {
                double volumeThreshold = params.size() > 0 ? params.get(0) : 100000;
                double maxMovement = params.size() > 1 ? params.get(1) : 0.5;
                yield engine.hasAbsorption(volumeThreshold, maxMovement, barIndex) ? 1.0 : 0.0;
            }

            // High volume node counting
            case "HIGH_VOLUME_NODE_COUNT" -> {
                double threshold = params.isEmpty() ? 1.5 : params.get(0);
                yield engine.getHighVolumeNodeCount(threshold, barIndex);
            }

            // Volume distribution around POC
            case "VOLUME_ABOVE_POC_RATIO" -> engine.getVolumeAbovePOCRatio(barIndex);
            case "VOLUME_BELOW_POC_RATIO" -> engine.getVolumeBelowPOCRatio(barIndex);

            // Footprint aggregates
            case "FOOTPRINT_DELTA" -> engine.getFootprintDelta(barIndex);
            case "FOOTPRINT_POC" -> engine.getFootprintPOC(barIndex);

            default -> throw new EvaluationException("Unknown footprint function: " + node.func());
        };
    }

    /**
     * Evaluate cross-exchange functions for multi-exchange analysis.
     * These compare orderflow across different exchanges.
     */
    private double evaluateExchangeFunction(AstNode.ExchangeFunctionCall node, int barIndex) {
        List<Double> params = node.params();

        return switch (node.func()) {
            // Per-exchange delta
            case "BINANCE_DELTA" -> engine.getExchangeDelta("BINANCE", barIndex);
            case "BYBIT_DELTA" -> engine.getExchangeDelta("BYBIT", barIndex);
            case "OKX_DELTA" -> engine.getExchangeDelta("OKX", barIndex);

            // Combined cross-exchange metrics
            case "COMBINED_DELTA" -> engine.getCombinedDelta(barIndex);
            case "EXCHANGE_DELTA_SPREAD" -> engine.getExchangeDeltaSpread(barIndex);
            case "EXCHANGE_DIVERGENCE" -> engine.hasExchangeDivergence(barIndex) ? 1.0 : 0.0;

            // Combined imbalance analysis
            case "COMBINED_IMBALANCE_AT_POC" -> engine.getCombinedImbalanceAtPOC(barIndex);
            case "EXCHANGES_WITH_BUY_IMBALANCE" -> engine.getExchangesWithBuyImbalance(barIndex);
            case "EXCHANGES_WITH_SELL_IMBALANCE" -> engine.getExchangesWithSellImbalance(barIndex);

            // Cross-exchange whale detection
            case "WHALE_DELTA_COMBINED" -> {
                double threshold = params.isEmpty() ? 100000 : params.get(0);
                yield engine.getWhaleDeltaCombined(threshold, barIndex);
            }

            // Dominant exchange (returns enum ordinal)
            case "DOMINANT_EXCHANGE" -> engine.getDominantExchange(barIndex);

            // Spot vs Futures market-type functions
            case "SPOT_DELTA" -> engine.getSpotDelta(barIndex);
            case "FUTURES_DELTA" -> engine.getFuturesDelta(barIndex);
            case "SPOT_VOLUME" -> engine.getSpotVolume(barIndex);
            case "FUTURES_VOLUME" -> engine.getFuturesVolume(barIndex);
            case "SPOT_FUTURES_DIVERGENCE" -> engine.getSpotFuturesDivergence(barIndex);
            case "SPOT_FUTURES_DELTA_SPREAD" -> engine.getSpotFuturesDeltaSpread(barIndex);

            default -> throw new EvaluationException("Unknown exchange function: " + node.func());
        };
    }

    /**
     * Evaluate lookback access: expr[n] returns value n bars ago
     */
    private double evaluateLookback(AstNode.LookbackAccess node, int barIndex) {
        int targetBar = barIndex - node.barsAgo();
        if (targetBar < 0) {
            return Double.NaN;
        }
        return toDouble(evaluateNode(node.expression(), targetBar));
    }

    private double evaluatePrice(AstNode.PriceReference node, int barIndex) {
        return switch (node.field()) {
            case "price", "close" -> engine.getCloseAt(barIndex);
            case "open" -> engine.getOpenAt(barIndex);
            case "high" -> engine.getHighAt(barIndex);
            case "low" -> engine.getLowAt(barIndex);
            case "volume" -> engine.getVolumeAt(barIndex);
            default -> throw new EvaluationException("Unknown price field: " + node.field());
        };
    }

    private double toDouble(Object value) {
        if (value instanceof Double d) {
            return d;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new EvaluationException("Cannot convert " + value.getClass().getSimpleName() + " to double");
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new EvaluationException("Cannot convert " + value.getClass().getSimpleName() + " to boolean");
    }

    public static class EvaluationException extends RuntimeException {
        public EvaluationException(String message) {
            super(message);
        }
    }
}
