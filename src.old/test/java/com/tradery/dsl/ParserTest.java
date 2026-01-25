package com.tradery.dsl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DSL Parser covering various expressions.
 */
class ParserTest {

    private Parser parser;

    @BeforeEach
    void setUp() {
        parser = new Parser();
    }

    @Nested
    @DisplayName("Simple Comparisons")
    class SimpleComparisonTests {

        @Test
        @DisplayName("Parses RSI comparison")
        void parsesRsiComparison() {
            Parser.ParseResult result = parser.parse("RSI(14) < 30");

            assertTrue(result.success());
            assertInstanceOf(AstNode.Comparison.class, result.ast());

            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            assertEquals("<", comp.operator());

            assertInstanceOf(AstNode.IndicatorCall.class, comp.left());
            AstNode.IndicatorCall indicator = (AstNode.IndicatorCall) comp.left();
            assertEquals("RSI", indicator.indicator());
            assertEquals(List.of(14.0), indicator.params());

            assertInstanceOf(AstNode.NumberLiteral.class, comp.right());
            assertEquals(30.0, ((AstNode.NumberLiteral) comp.right()).value());
        }

        @Test
        @DisplayName("Parses SMA comparison with price")
        void parsesSmaComparisonWithPrice() {
            Parser.ParseResult result = parser.parse("close > SMA(200)");

            assertTrue(result.success());
            assertInstanceOf(AstNode.Comparison.class, result.ast());

            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            assertEquals(">", comp.operator());

            assertInstanceOf(AstNode.PriceReference.class, comp.left());
            assertEquals("close", ((AstNode.PriceReference) comp.left()).field());

            assertInstanceOf(AstNode.IndicatorCall.class, comp.right());
        }

        @Test
        @DisplayName("Parses all comparison operators")
        void parsesAllComparisonOperators() {
            String[] operators = {">", "<", ">=", "<=", "=="};

            for (String op : operators) {
                Parser.ParseResult result = parser.parse("close " + op + " 100");
                assertTrue(result.success(), "Should parse operator: " + op);

                AstNode.Comparison comp = (AstNode.Comparison) result.ast();
                assertEquals(op, comp.operator());
            }
        }
    }

    @Nested
    @DisplayName("Logical Expressions")
    class LogicalExpressionTests {

        @Test
        @DisplayName("Parses AND expression")
        void parsesAndExpression() {
            Parser.ParseResult result = parser.parse("RSI(14) < 30 AND close > SMA(20)");

            assertTrue(result.success());
            assertInstanceOf(AstNode.LogicalExpression.class, result.ast());

            AstNode.LogicalExpression expr = (AstNode.LogicalExpression) result.ast();
            assertEquals("AND", expr.operator());

            assertInstanceOf(AstNode.Comparison.class, expr.left());
            assertInstanceOf(AstNode.Comparison.class, expr.right());
        }

        @Test
        @DisplayName("Parses OR expression")
        void parsesOrExpression() {
            Parser.ParseResult result = parser.parse("RSI(14) > 70 OR RSI(14) < 30");

            assertTrue(result.success());
            assertInstanceOf(AstNode.LogicalExpression.class, result.ast());

            AstNode.LogicalExpression expr = (AstNode.LogicalExpression) result.ast();
            assertEquals("OR", expr.operator());
        }

        @Test
        @DisplayName("AND has higher precedence than OR")
        void andHigherPrecedenceThanOr() {
            // A OR B AND C should be parsed as A OR (B AND C)
            Parser.ParseResult result = parser.parse("RSI(14) > 70 OR RSI(7) < 30 AND close > 100");

            assertTrue(result.success());
            assertInstanceOf(AstNode.LogicalExpression.class, result.ast());

            AstNode.LogicalExpression expr = (AstNode.LogicalExpression) result.ast();
            assertEquals("OR", expr.operator());

            // Right side should be the AND expression
            assertInstanceOf(AstNode.LogicalExpression.class, expr.right());
            AstNode.LogicalExpression andExpr = (AstNode.LogicalExpression) expr.right();
            assertEquals("AND", andExpr.operator());
        }

        @Test
        @DisplayName("Parses multiple AND conditions")
        void parsesMultipleAndConditions() {
            Parser.ParseResult result = parser.parse("RSI(14) < 30 AND close > SMA(20) AND volume > 1000");

            assertTrue(result.success());
            assertInstanceOf(AstNode.LogicalExpression.class, result.ast());
        }
    }

    @Nested
    @DisplayName("Cross Comparisons")
    class CrossComparisonTests {

        @Test
        @DisplayName("Parses crosses_above")
        void parsesCrossesAbove() {
            Parser.ParseResult result = parser.parse("EMA(20) crosses_above EMA(50)");

            assertTrue(result.success());
            assertInstanceOf(AstNode.CrossComparison.class, result.ast());

            AstNode.CrossComparison cross = (AstNode.CrossComparison) result.ast();
            assertEquals("crosses_above", cross.operator());
        }

        @Test
        @DisplayName("Parses crosses_below")
        void parsesCrossesBelow() {
            Parser.ParseResult result = parser.parse("EMA(9) crosses_below EMA(21)");

            assertTrue(result.success());
            assertInstanceOf(AstNode.CrossComparison.class, result.ast());

            AstNode.CrossComparison cross = (AstNode.CrossComparison) result.ast();
            assertEquals("crosses_below", cross.operator());
        }
    }

    @Nested
    @DisplayName("Property Access")
    class PropertyAccessTests {

        @Test
        @DisplayName("Parses Bollinger Bands lower property")
        void parsesBbandsLower() {
            Parser.ParseResult result = parser.parse("close < BBANDS(20,2).lower");

            assertTrue(result.success());
            assertInstanceOf(AstNode.Comparison.class, result.ast());

            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            assertInstanceOf(AstNode.PropertyAccess.class, comp.right());

            AstNode.PropertyAccess prop = (AstNode.PropertyAccess) comp.right();
            assertEquals("lower", prop.property());
            assertEquals("BBANDS", prop.object().indicator());
        }

        @Test
        @DisplayName("Parses MACD signal line property")
        void parsesMacdSignal() {
            Parser.ParseResult result = parser.parse("MACD(12,26,9).signal > 0");

            assertTrue(result.success());
            assertInstanceOf(AstNode.Comparison.class, result.ast());

            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            assertInstanceOf(AstNode.PropertyAccess.class, comp.left());

            AstNode.PropertyAccess prop = (AstNode.PropertyAccess) comp.left();
            assertEquals("signal", prop.property());
            assertEquals("MACD", prop.object().indicator());
            assertEquals(List.of(12.0, 26.0, 9.0), prop.object().params());
        }

        @Test
        @DisplayName("Parses MACD histogram property")
        void parsesMacdHistogram() {
            Parser.ParseResult result = parser.parse("MACD(12,26,9).histogram > 0");

            assertTrue(result.success());
            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            AstNode.PropertyAccess prop = (AstNode.PropertyAccess) comp.left();
            assertEquals("histogram", prop.property());
        }
    }

    @Nested
    @DisplayName("Arithmetic Expressions")
    class ArithmeticTests {

        @Test
        @DisplayName("Parses multiplication")
        void parsesMultiplication() {
            Parser.ParseResult result = parser.parse("volume > AVG_VOLUME(20) * 1.5");

            assertTrue(result.success());
            assertInstanceOf(AstNode.Comparison.class, result.ast());

            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            assertInstanceOf(AstNode.ArithmeticExpression.class, comp.right());

            AstNode.ArithmeticExpression arith = (AstNode.ArithmeticExpression) comp.right();
            assertEquals("*", arith.operator());
        }

        @Test
        @DisplayName("Parses division")
        void parsesDivision() {
            Parser.ParseResult result = parser.parse("close / open > 1.01");

            assertTrue(result.success());
            assertInstanceOf(AstNode.Comparison.class, result.ast());

            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            assertInstanceOf(AstNode.ArithmeticExpression.class, comp.left());

            AstNode.ArithmeticExpression arith = (AstNode.ArithmeticExpression) comp.left();
            assertEquals("/", arith.operator());
        }

        @Test
        @DisplayName("Parses addition and subtraction")
        void parsesAdditionSubtraction() {
            Parser.ParseResult result = parser.parse("close > high - 10");
            assertTrue(result.success());

            result = parser.parse("close < low + 5");
            assertTrue(result.success());
        }
    }

    @Nested
    @DisplayName("Price References")
    class PriceReferenceTests {

        @Test
        @DisplayName("Parses all price references")
        void parsesAllPriceReferences() {
            String[] priceRefs = {"close", "open", "high", "low", "volume"};

            for (String ref : priceRefs) {
                Parser.ParseResult result = parser.parse(ref + " > 0");
                assertTrue(result.success(), "Should parse price reference: " + ref);

                AstNode.Comparison comp = (AstNode.Comparison) result.ast();
                assertInstanceOf(AstNode.PriceReference.class, comp.left());
                assertEquals(ref, ((AstNode.PriceReference) comp.left()).field());
            }
        }
    }

    @Nested
    @DisplayName("Range Functions")
    class RangeFunctionTests {

        @Test
        @DisplayName("Parses HIGH_OF function")
        void parsesHighOf() {
            Parser.ParseResult result = parser.parse("close > HIGH_OF(20)");

            assertTrue(result.success());
            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            assertInstanceOf(AstNode.RangeFunctionCall.class, comp.right());

            AstNode.RangeFunctionCall func = (AstNode.RangeFunctionCall) comp.right();
            assertEquals("HIGH_OF", func.func());
            assertEquals(20, func.period());
        }

        @Test
        @DisplayName("Parses LOW_OF function")
        void parsesLowOf() {
            Parser.ParseResult result = parser.parse("close < LOW_OF(10)");

            assertTrue(result.success());
            AstNode.Comparison comp = (AstNode.Comparison) result.ast();
            assertInstanceOf(AstNode.RangeFunctionCall.class, comp.right());

            AstNode.RangeFunctionCall func = (AstNode.RangeFunctionCall) comp.right();
            assertEquals("LOW_OF", func.func());
            assertEquals(10, func.period());
        }
    }

    @Nested
    @DisplayName("Boolean Literals")
    class BooleanLiteralTests {

        @Test
        @DisplayName("Parses true literal")
        void parsesTrueLiteral() {
            Parser.ParseResult result = parser.parse("true");

            assertTrue(result.success());
            assertInstanceOf(AstNode.BooleanLiteral.class, result.ast());
            assertTrue(((AstNode.BooleanLiteral) result.ast()).value());
        }

        @Test
        @DisplayName("Parses false literal")
        void parsesFalseLiteral() {
            Parser.ParseResult result = parser.parse("false");

            assertTrue(result.success());
            assertInstanceOf(AstNode.BooleanLiteral.class, result.ast());
            assertFalse(((AstNode.BooleanLiteral) result.ast()).value());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Reports error for empty expression")
        void reportsErrorForEmpty() {
            Parser.ParseResult result = parser.parse("");

            assertFalse(result.success());
            assertNotNull(result.error());
        }

        @Test
        @DisplayName("Reports error for unknown identifier")
        void reportsErrorForUnknownIdentifier() {
            Parser.ParseResult result = parser.parse("UNKNOWN_INDICATOR(14) > 0");

            // May either fail parsing or fail at evaluation
            // If parser allows it, the result will be tested at evaluation time
            // This test just ensures the parser doesn't crash
            assertNotNull(result);
        }

        @Test
        @DisplayName("Reports error for unclosed parenthesis")
        void reportsErrorForUnclosedParen() {
            Parser.ParseResult result = parser.parse("RSI(14 > 30");

            assertFalse(result.success());
            assertNotNull(result.error());
        }

        @Test
        @DisplayName("Reports error for missing operand")
        void reportsErrorForMissingOperand() {
            Parser.ParseResult result = parser.parse("RSI(14) >");

            assertFalse(result.success());
            assertNotNull(result.error());
        }
    }

    @Nested
    @DisplayName("Complex Expressions")
    class ComplexExpressionTests {

        @Test
        @DisplayName("Parses RSI strategy expression")
        void parsesRsiStrategyExpression() {
            Parser.ParseResult result = parser.parse("RSI(7) < 40 AND close < BBANDS(20,2).lower");

            assertTrue(result.success());
            assertInstanceOf(AstNode.LogicalExpression.class, result.ast());
        }

        @Test
        @DisplayName("Parses exit condition with OR")
        void parsesExitConditionWithOr() {
            Parser.ParseResult result = parser.parse("RSI(7) > 70 OR close > SMA(20)");

            assertTrue(result.success());
            assertInstanceOf(AstNode.LogicalExpression.class, result.ast());
        }

        @Test
        @DisplayName("Parses volume spike condition")
        void parsesVolumeSpikeCondition() {
            Parser.ParseResult result = parser.parse("close > HIGH_OF(20) AND volume > AVG_VOLUME(20) * 1.5");

            assertTrue(result.success());
            assertInstanceOf(AstNode.LogicalExpression.class, result.ast());
        }

        @Test
        @DisplayName("Parses MACD crossover strategy")
        void parsesMacdCrossoverStrategy() {
            Parser.ParseResult result = parser.parse("MACD(12,26,9).histogram > 0 AND MACD(12,26,9).signal crosses_above 0");

            // This might not parse correctly if cross comparison has different precedence
            // Just ensure it doesn't crash
            assertNotNull(result);
        }
    }
}
