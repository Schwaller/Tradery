package com.tradery.engine;

import com.tradery.model.*;

import java.util.List;

/**
 * Handles position sizing calculations for backtests.
 * Supports multiple sizing strategies: fixed percent, fixed dollar, risk-based, Kelly criterion, and volatility-based.
 */
public class PositionSizer {

    /**
     * Calculate position size based on config and strategy settings.
     *
     * @param config The backtest configuration
     * @param strategy The strategy (for stop-loss info in risk-based sizing)
     * @param equity Current account equity
     * @param price Current price for the asset
     * @param candles Historical candles (for ATR calculation)
     * @param barIndex Current bar index
     * @return Position quantity (units of the asset)
     */
    public double calculate(BacktestConfig config, Strategy strategy, double equity,
                            double price, List<Candle> candles, int barIndex) {
        double positionValue = equity * 0.10; // Default to 10% of equity

        switch (config.positionSizingType()) {
            case FIXED_PERCENT -> positionValue = equity * (config.positionSizingValue() / 100.0);
            case FIXED_DOLLAR -> positionValue = config.positionSizingValue();
            case RISK_PERCENT -> positionValue = calculateRiskBased(config, strategy, equity, price, candles, barIndex);
            case KELLY -> positionValue = calculateKellyBased(config, equity);
            case VOLATILITY -> positionValue = calculateVolatilityBased(equity, price, candles, barIndex);
            case ALL_IN -> positionValue = equity;
        }

        // Ensure we don't exceed available equity (allow 100% for ALL_IN)
        double maxAllocation = config.positionSizingType() == PositionSizingType.ALL_IN ? 1.0 : 0.95;
        positionValue = Math.min(positionValue, equity * maxAllocation);

        return positionValue / price;
    }

    /**
     * Risk-based position sizing: risk a percentage of equity based on stop-loss distance.
     */
    private double calculateRiskBased(BacktestConfig config, Strategy strategy, double equity,
                                      double price, List<Candle> candles, int barIndex) {
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
                return (riskAmount / stopDistance) * price;
            }
        }
        // No SL defined, fall back to fixed percent
        return equity * (config.positionSizingValue() / 100.0);
    }

    /**
     * Kelly Criterion position sizing: f = (bp - q) / b
     * Uses half-Kelly for safety and caps at 25%.
     */
    private double calculateKellyBased(BacktestConfig config, double equity) {
        double kellyFraction = calculateKellyFraction();
        return equity * Math.max(0, Math.min(kellyFraction * 0.5, 0.25));
    }

    /**
     * Calculate Kelly fraction based on estimated win rate and payoff ratio.
     * In a real implementation, this would use historical trade data.
     */
    private double calculateKellyFraction() {
        // Conservative estimates
        double winRate = 0.55;  // Assume 55% win rate
        double avgWin = 1.5;    // Avg win is 1.5x avg loss
        double avgLoss = 1.0;

        double b = avgWin / avgLoss;
        double p = winRate;
        double q = 1 - p;

        return (b * p - q) / b;
    }

    /**
     * Volatility-based sizing: size inversely proportional to ATR.
     * Lower volatility = larger position, higher volatility = smaller position.
     */
    private double calculateVolatilityBased(double equity, double price, List<Candle> candles, int barIndex) {
        double atr = calculateATR(candles, barIndex, 14);
        if (atr > 0) {
            double targetRisk = equity * 0.02; // Risk 2% of equity
            double atrMultiple = 2.0; // Stop at 2x ATR
            return (targetRisk / (atr * atrMultiple)) * price;
        }
        return equity * 0.10; // Default fallback
    }

    /**
     * Calculate Average True Range for a given period.
     *
     * @param candles List of candles
     * @param barIndex Current bar index
     * @param period ATR period
     * @return ATR value
     */
    public double calculateATR(List<Candle> candles, int barIndex, int period) {
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
}
