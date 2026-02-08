package com.tradery.exchange;

import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.*;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public interface TradingClient {

    String getVenueName();

    // Connection lifecycle
    void connect() throws ExchangeException;
    void disconnect();
    boolean isConnected();

    // Account info
    AccountState getAccountState() throws ExchangeException;
    List<ExchangePosition> getPositions() throws ExchangeException;
    List<AssetInfo> getAssets() throws ExchangeException;

    // Orders
    OrderResponse placeOrder(OrderRequest request) throws ExchangeException;
    OrderResponse cancelOrder(String symbol, String orderId) throws ExchangeException;
    List<OrderResponse> getOpenOrders(String symbol) throws ExchangeException;
    List<Fill> getFillHistory(String symbol, Instant from, Instant to) throws ExchangeException;

    // Margin
    void setLeverage(String symbol, int leverage, MarginMode mode) throws ExchangeException;

    // Streaming (WebSocket)
    void subscribeOrderUpdates(Consumer<OrderUpdate> listener);
    void subscribePositionUpdates(Consumer<ExchangePosition> listener);
    void subscribeFills(Consumer<Fill> listener);
}
