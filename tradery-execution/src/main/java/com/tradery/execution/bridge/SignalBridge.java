package com.tradery.execution.bridge;

import com.tradery.exchange.model.OrderSide;
import com.tradery.exchange.model.OrderType;
import com.tradery.execution.ExecutionEngine;
import com.tradery.execution.order.OrderIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts external signal events into OrderIntents for the execution engine.
 * Designed to be called from the desk's AlertOutput or runner's signal pipeline.
 */
public class SignalBridge {

    private static final Logger log = LoggerFactory.getLogger(SignalBridge.class);

    private final ExecutionEngine engine;

    public SignalBridge(ExecutionEngine engine) {
        this.engine = engine;
    }

    /**
     * Handle an entry signal.
     */
    public void onEntrySignal(String strategyId, String symbol, double price) {
        OrderIntent intent = OrderIntent.entry(strategyId, symbol, OrderSide.BUY, price);
        log.info("Signal bridge: ENTRY {} {} @ {}", strategyId, symbol, price);
        engine.executeIntent(intent);
    }

    /**
     * Handle an exit signal.
     */
    public void onExitSignal(String strategyId, String symbol, double price, OrderSide positionSide) {
        // Exit side is opposite of position side
        OrderSide exitSide = positionSide == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
        OrderIntent intent = OrderIntent.exit(strategyId, symbol, exitSide, price);
        log.info("Signal bridge: EXIT {} {} @ {}", strategyId, symbol, price);
        engine.executeIntent(intent);
    }
}
