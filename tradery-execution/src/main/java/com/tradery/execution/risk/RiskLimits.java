package com.tradery.execution.risk;

import com.tradery.exchange.model.TradingConfig;

import java.util.List;

/**
 * Configurable risk limits loaded from TradingConfig.
 */
public record RiskLimits(
    double maxPositionSizeUsd,
    int maxOpenPositions,
    double maxDailyLossPercent,
    double maxDrawdownPercent,
    int maxOrdersPerMinute,
    List<String> allowedSymbols
) {
    public static RiskLimits fromConfig(TradingConfig.RiskConfig config) {
        return new RiskLimits(
                config.getMaxPositionSizeUsd(),
                config.getMaxOpenPositions(),
                config.getMaxDailyLossPercent(),
                config.getMaxDrawdownPercent(),
                config.getMaxOrdersPerMinute(),
                config.getAllowedSymbols()
        );
    }

    public static RiskLimits defaults() {
        return new RiskLimits(10000, 5, 5.0, 15.0, 10, List.of());
    }
}
