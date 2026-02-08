package com.tradery.execution.order;

import com.tradery.exchange.TradingClient;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.*;
import com.tradery.execution.journal.ExecutionJournal;
import com.tradery.execution.journal.OrderEvent;
import com.tradery.execution.position.PositionTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks orders and handles fill updates from the exchange.
 */
public class OrderManager {

    private static final Logger log = LoggerFactory.getLogger(OrderManager.class);

    private final TradingClient client;
    private final PositionTracker positionTracker;
    private final ExecutionJournal journal;

    // orderId -> LiveOrder
    private final Map<String, LiveOrder> activeOrders = new ConcurrentHashMap<>();

    public OrderManager(TradingClient client, PositionTracker positionTracker, ExecutionJournal journal) {
        this.client = client;
        this.positionTracker = positionTracker;
        this.journal = journal;

        // Subscribe to fill updates
        client.subscribeFills(this::onFill);
        client.subscribeOrderUpdates(this::onOrderUpdate);
    }

    /**
     * Submit an order to the exchange and track it.
     */
    public LiveOrder submitOrder(OrderRequest request, String strategyId) throws ExchangeException {
        OrderResponse response = client.placeOrder(request);

        LiveOrder order = new LiveOrder(response.orderId(), strategyId, request);

        // If immediately filled
        if (response.isFilled()) {
            order.updateStatus(OrderStatus.FILLED);
            for (Fill fill : response.fills()) {
                order.applyFill(fill);
            }
            positionTracker.onFill(strategyId, request.symbol(), request.side(),
                    response.filledQuantity(), response.avgFillPrice() != null ? response.avgFillPrice() : 0);
        } else {
            activeOrders.put(response.orderId(), order);
        }

        journal.log(OrderEvent.placed(order));
        log.info("Order submitted: {} {} {} {} @ {} (id={})",
                strategyId, request.side(), request.quantity(), request.symbol(),
                request.price(), response.orderId());

        return order;
    }

    /**
     * Cancel an order.
     */
    public void cancelOrder(String symbol, String orderId) throws ExchangeException {
        client.cancelOrder(symbol, orderId);
        LiveOrder order = activeOrders.remove(orderId);
        if (order != null) {
            order.updateStatus(OrderStatus.CANCELLED);
            journal.log(OrderEvent.cancelled(order));
        }
    }

    /**
     * Get all active (non-terminal) orders.
     */
    public List<LiveOrder> getActiveOrders() {
        return activeOrders.values().stream()
                .filter(o -> !o.isTerminal())
                .toList();
    }

    /**
     * Get orders for a specific strategy.
     */
    public List<LiveOrder> getOrdersForStrategy(String strategyId) {
        return activeOrders.values().stream()
                .filter(o -> strategyId.equals(o.getStrategyId()))
                .toList();
    }

    private void onFill(Fill fill) {
        LiveOrder order = activeOrders.get(fill.orderId());
        if (order == null) {
            log.debug("Fill for unknown order {}: {} {} {} @ {}",
                    fill.orderId(), fill.side(), fill.quantity(), fill.symbol(), fill.price());
            return;
        }

        order.applyFill(fill);
        positionTracker.onFill(order.getStrategyId(), fill.symbol(), fill.side(),
                fill.quantity(), fill.price());
        journal.log(OrderEvent.filled(order, fill));

        log.info("Fill: {} {} {} @ {} (order {})",
                fill.side(), fill.quantity(), fill.symbol(), fill.price(), fill.orderId());

        if (order.isTerminal()) {
            activeOrders.remove(fill.orderId());
        }
    }

    private void onOrderUpdate(OrderUpdate update) {
        LiveOrder order = activeOrders.get(update.orderId());
        if (order == null) return;

        order.updateStatus(update.status());
        if (order.isTerminal()) {
            activeOrders.remove(update.orderId());
        }
    }
}
