package com.tradery.exchange;

import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.exception.InsufficientBalanceException;
import com.tradery.exchange.exception.OrderRejectedException;
import com.tradery.exchange.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Simulated trading client for paper trading mode.
 * Fills market orders immediately at requested price.
 * Does not simulate limit order matching or slippage.
 */
public class PaperTradingClient implements TradingClient {

    private static final Logger log = LoggerFactory.getLogger(PaperTradingClient.class);

    private final TradingConfig config;
    private double balance;
    private boolean connected;
    private final Map<String, PaperPosition> positions = new LinkedHashMap<>();
    private final List<Fill> fillHistory = new ArrayList<>();
    private final AtomicLong orderIdSeq = new AtomicLong(1);

    private final List<Consumer<OrderUpdate>> orderListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ExchangePosition>> positionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Fill>> fillListeners = new CopyOnWriteArrayList<>();

    public PaperTradingClient(TradingConfig config) {
        this.config = config;
        this.balance = config.getPaperTrading().getInitialBalance();
    }

    @Override
    public String getVenueName() {
        return "paper";
    }

    @Override
    public void connect() {
        connected = true;
        log.info("Paper trading connected with balance: {}", balance);
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public AccountState getAccountState() {
        double unrealizedPnl = positions.values().stream()
                .mapToDouble(p -> p.unrealizedPnl)
                .sum();
        double equity = balance + unrealizedPnl;
        return new AccountState(balance, equity, equity, 0, unrealizedPnl, balance, "USD", Instant.now());
    }

    @Override
    public List<ExchangePosition> getPositions() {
        return positions.values().stream()
                .map(PaperPosition::toExchangePosition)
                .toList();
    }

    @Override
    public List<AssetInfo> getAssets() {
        // Paper mode returns a basic set of common assets
        return List.of(
                new AssetInfo("BTC", "BTC", "USD", 4, 1, 0.001, 50, true, 0),
                new AssetInfo("ETH", "ETH", "USD", 3, 2, 0.01, 50, true, 1),
                new AssetInfo("SOL", "SOL", "USD", 1, 3, 0.1, 20, true, 2)
        );
    }

    @Override
    public OrderResponse placeOrder(OrderRequest request) throws ExchangeException {
        String orderId = "paper-" + orderIdSeq.getAndIncrement();
        Instant now = Instant.now();

        // For market orders, simulate immediate fill
        if (request.type() == OrderType.MARKET) {
            double fillPrice = request.price() != null ? request.price() : 0;
            if (fillPrice <= 0) {
                throw new OrderRejectedException("Market order requires a reference price in paper mode", "NO_PRICE");
            }

            double cost = fillPrice * request.quantity();
            if (!request.reduceOnly() && cost > balance) {
                throw new InsufficientBalanceException(
                        String.format("Insufficient balance: need %.2f, have %.2f", cost, balance));
            }

            Fill fill = new Fill(orderId, orderId, request.symbol(), request.side(),
                    fillPrice, request.quantity(), 0, "USD", now);
            fillHistory.add(fill);

            // Update position
            updatePosition(request.symbol(), request.side(), request.quantity(), fillPrice);

            // Notify listeners
            fillListeners.forEach(l -> l.accept(fill));

            OrderUpdate update = new OrderUpdate(orderId, request.symbol(), request.side(),
                    request.type(), OrderStatus.FILLED, request.quantity(), request.quantity(),
                    fillPrice, null, now);
            orderListeners.forEach(l -> l.accept(update));

            log.info("Paper {} {} {} @ {} (order {})",
                    request.side(), request.quantity(), request.symbol(), fillPrice, orderId);

            return new OrderResponse(orderId, request.clientOrderId(), request.symbol(),
                    request.side(), request.type(), OrderStatus.FILLED,
                    request.quantity(), request.quantity(), fillPrice, List.of(fill), now, now);
        }

        // Limit/stop orders are acknowledged but not filled (would need price simulation)
        log.info("Paper limit order placed: {} {} {} @ {} (order {})",
                request.side(), request.quantity(), request.symbol(), request.price(), orderId);

        return new OrderResponse(orderId, request.clientOrderId(), request.symbol(),
                request.side(), request.type(), OrderStatus.OPEN,
                request.quantity(), 0, null, List.of(), now, now);
    }

    @Override
    public OrderResponse cancelOrder(String symbol, String orderId) {
        Instant now = Instant.now();
        return new OrderResponse(orderId, null, symbol, null, null, OrderStatus.CANCELLED,
                0, 0, null, List.of(), now, now);
    }

    @Override
    public List<OrderResponse> getOpenOrders(String symbol) {
        return List.of();
    }

    @Override
    public List<Fill> getFillHistory(String symbol, Instant from, Instant to) {
        return fillHistory.stream()
                .filter(f -> symbol == null || f.symbol().equals(symbol))
                .filter(f -> !f.timestamp().isBefore(from) && !f.timestamp().isAfter(to))
                .toList();
    }

    @Override
    public void setLeverage(String symbol, int leverage, MarginMode mode) {
        log.info("Paper set leverage: {} {}x {}", symbol, leverage, mode);
    }

    @Override
    public void subscribeOrderUpdates(Consumer<OrderUpdate> listener) {
        orderListeners.add(listener);
    }

    @Override
    public void subscribePositionUpdates(Consumer<ExchangePosition> listener) {
        positionListeners.add(listener);
    }

    @Override
    public void subscribeFills(Consumer<Fill> listener) {
        fillListeners.add(listener);
    }

    private void updatePosition(String symbol, OrderSide side, double qty, double price) {
        PaperPosition pos = positions.get(symbol);
        if (pos == null) {
            pos = new PaperPosition(symbol, side, qty, price);
            positions.put(symbol, pos);
        } else {
            if (pos.side == side) {
                // Adding to position
                double totalCost = pos.entryPrice * pos.quantity + price * qty;
                pos.quantity += qty;
                pos.entryPrice = totalCost / pos.quantity;
            } else {
                // Reducing or flipping
                if (qty >= pos.quantity) {
                    double pnl = (price - pos.entryPrice) * pos.quantity * (pos.side == OrderSide.BUY ? 1 : -1);
                    balance += pnl;
                    double remaining = qty - pos.quantity;
                    if (remaining > 0) {
                        pos.side = side;
                        pos.quantity = remaining;
                        pos.entryPrice = price;
                    } else {
                        positions.remove(symbol);
                        return;
                    }
                } else {
                    double pnl = (price - pos.entryPrice) * qty * (pos.side == OrderSide.BUY ? 1 : -1);
                    balance += pnl;
                    pos.quantity -= qty;
                }
            }
        }

        // Notify position listeners
        if (positions.containsKey(symbol)) {
            ExchangePosition ep = positions.get(symbol).toExchangePosition();
            positionListeners.forEach(l -> l.accept(ep));
        }
    }

    private static class PaperPosition {
        String symbol;
        OrderSide side;
        double quantity;
        double entryPrice;
        double unrealizedPnl;

        PaperPosition(String symbol, OrderSide side, double quantity, double entryPrice) {
            this.symbol = symbol;
            this.side = side;
            this.quantity = quantity;
            this.entryPrice = entryPrice;
        }

        ExchangePosition toExchangePosition() {
            return new ExchangePosition(symbol, side, quantity, entryPrice, entryPrice,
                    unrealizedPnl, 0, 1, MarginMode.CROSS, 0, 0, Instant.now());
        }
    }
}
