package com.tradery.engine;

import com.tradery.dsl.AstNode;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.indicators.Indicators;

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
                    default -> throw new EvaluationException("Unknown BBANDS property: " + property);
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
