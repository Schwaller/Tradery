package com.tradery.execution.order;

import com.tradery.exchange.model.OrderSide;
import com.tradery.exchange.model.OrderType;

/**
 * Pre-validation order intent from a signal, before risk checks and position sizing.
 */
public record OrderIntent(
    String strategyId,
    String symbol,
    OrderSide side,
    OrderType type,
    double referencePrice,
    Double stopLossPrice,
    Double takeProfitPrice,
    boolean reduceOnly
) {
    public static OrderIntent entry(String strategyId, String symbol, OrderSide side, double price) {
        return new OrderIntent(strategyId, symbol, side, OrderType.MARKET, price, null, null, false);
    }

    public static OrderIntent exit(String strategyId, String symbol, OrderSide side, double price) {
        return new OrderIntent(strategyId, symbol, side, OrderType.MARKET, price, null, null, true);
    }
}
