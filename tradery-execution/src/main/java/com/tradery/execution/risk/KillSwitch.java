package com.tradery.execution.risk;

import com.tradery.exchange.TradingClient;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.ExchangePosition;
import com.tradery.exchange.model.OrderRequest;
import com.tradery.exchange.model.OrderSide;
import com.tradery.exchange.model.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Emergency close-all + halt mechanism.
 */
public class KillSwitch {

    private static final Logger log = LoggerFactory.getLogger(KillSwitch.class);

    private final TradingClient client;
    private final RiskManager riskManager;

    public KillSwitch(TradingClient client, RiskManager riskManager) {
        this.client = client;
        this.riskManager = riskManager;
    }

    /**
     * Activate kill switch: cancel all open orders, close all positions, halt trading.
     */
    public void activate() {
        log.error("KILL SWITCH ACTIVATED — closing all positions and halting trading");
        riskManager.setKilled(true);

        try {
            // Cancel all open orders
            var openOrders = client.getOpenOrders(null);
            for (var order : openOrders) {
                try {
                    client.cancelOrder(order.symbol(), order.orderId());
                    log.info("Cancelled order: {} {}", order.symbol(), order.orderId());
                } catch (ExchangeException e) {
                    log.error("Failed to cancel order {}: {}", order.orderId(), e.getMessage());
                }
            }

            // Close all positions with market orders
            List<ExchangePosition> positions = client.getPositions();
            for (ExchangePosition pos : positions) {
                try {
                    OrderSide closeSide = pos.isLong() ? OrderSide.SELL : OrderSide.BUY;
                    OrderRequest closeRequest = OrderRequest.builder()
                            .symbol(pos.symbol())
                            .side(closeSide)
                            .type(OrderType.MARKET)
                            .quantity(pos.quantity())
                            .price(pos.markPrice() > 0 ? pos.markPrice() : pos.entryPrice())
                            .reduceOnly(true)
                            .build();

                    client.placeOrder(closeRequest);
                    log.info("Closed position: {} {} {}", pos.symbol(), pos.side(), pos.quantity());
                } catch (ExchangeException e) {
                    log.error("Failed to close position {}: {}", pos.symbol(), e.getMessage());
                }
            }

            log.error("Kill switch complete — {} orders cancelled, {} positions closed",
                    openOrders.size(), positions.size());
        } catch (ExchangeException e) {
            log.error("Kill switch error: {}", e.getMessage());
        }
    }

    /**
     * Reset kill switch (re-enable trading).
     */
    public void reset() {
        riskManager.setKilled(false);
        log.info("Kill switch reset — trading re-enabled");
    }

    public boolean isActivated() {
        return riskManager.isKilled();
    }
}
