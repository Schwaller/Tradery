package com.tradery.exchange.hyperliquid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.exchange.model.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Hyperliquid WebSocket client for real-time updates.
 * Subscribes to orderUpdates, userFills, and clearinghouseState channels.
 */
public class HyperliquidWebSocket {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidWebSocket.class);

    private final String wsUrl;
    private final String userAddress;
    private final ObjectMapper mapper;
    private volatile HlWebSocketClient wsClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hl-ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    private final List<Consumer<OrderUpdate>> orderListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ExchangePosition>> positionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Fill>> fillListeners = new CopyOnWriteArrayList<>();

    private volatile boolean shouldReconnect = true;

    public HyperliquidWebSocket(String userAddress, boolean testnet) {
        this.userAddress = userAddress;
        this.wsUrl = testnet
                ? "wss://api.hyperliquid-testnet.xyz/ws"
                : "wss://api.hyperliquid.xyz/ws";
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public void connect() {
        shouldReconnect = true;
        doConnect();
    }

    public void disconnect() {
        shouldReconnect = false;
        scheduler.shutdownNow();
        if (wsClient != null) {
            wsClient.close();
        }
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }

    public void subscribeOrderUpdates(Consumer<OrderUpdate> listener) {
        orderListeners.add(listener);
    }

    public void subscribePositionUpdates(Consumer<ExchangePosition> listener) {
        positionListeners.add(listener);
    }

    public void subscribeFills(Consumer<Fill> listener) {
        fillListeners.add(listener);
    }

    private void doConnect() {
        try {
            wsClient = new HlWebSocketClient(new URI(wsUrl));
            wsClient.connect();
        } catch (Exception e) {
            log.error("WebSocket connection failed: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        scheduler.schedule(this::doConnect, 5, TimeUnit.SECONDS);
    }

    private void subscribe() {
        try {
            // Subscribe to order updates
            ObjectNode orderSub = mapper.createObjectNode();
            orderSub.put("method", "subscribe");
            ObjectNode orderSubParams = orderSub.putObject("subscription");
            orderSubParams.put("type", "orderUpdates");
            orderSubParams.put("user", userAddress);
            wsClient.send(mapper.writeValueAsString(orderSub));

            // Subscribe to user fills
            ObjectNode fillSub = mapper.createObjectNode();
            fillSub.put("method", "subscribe");
            ObjectNode fillSubParams = fillSub.putObject("subscription");
            fillSubParams.put("type", "userFills");
            fillSubParams.put("user", userAddress);
            wsClient.send(mapper.writeValueAsString(fillSub));

            // Subscribe to user events (includes position updates)
            ObjectNode eventSub = mapper.createObjectNode();
            eventSub.put("method", "subscribe");
            ObjectNode eventSubParams = eventSub.putObject("subscription");
            eventSubParams.put("type", "userEvents");
            eventSubParams.put("user", userAddress);
            wsClient.send(mapper.writeValueAsString(eventSub));

            log.info("Subscribed to Hyperliquid WebSocket channels for {}", userAddress);
        } catch (Exception e) {
            log.error("Failed to subscribe: {}", e.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            String channel = root.path("channel").asText("");

            switch (channel) {
                case "orderUpdates" -> handleOrderUpdates(root.get("data"));
                case "userFills" -> handleUserFills(root.get("data"));
                case "userEvents" -> handleUserEvents(root.get("data"));
                default -> {
                    // Ping/pong or subscription confirmation — ignore
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket message: {}", e.getMessage());
        }
    }

    private void handleOrderUpdates(JsonNode data) {
        if (data == null || !data.isArray()) return;

        for (JsonNode node : data) {
            try {
                JsonNode order = node.has("order") ? node.get("order") : node;
                String oid = order.get("oid").asText();
                String coin = order.get("coin").asText();
                String side = order.get("side").asText();
                double sz = order.get("sz").asDouble();
                double limitPx = order.get("limitPx").asDouble();
                String statusStr = node.has("status") ? node.get("status").asText() : "open";
                long timestamp = order.has("timestamp") ? order.get("timestamp").asLong() : System.currentTimeMillis();

                OrderSide orderSide = "B".equals(side) ? OrderSide.BUY : OrderSide.SELL;
                OrderStatus status = mapOrderStatus(statusStr);

                OrderUpdate update = new OrderUpdate(oid, coin, orderSide, OrderType.LIMIT,
                        status, sz, 0, limitPx, null, Instant.ofEpochMilli(timestamp));

                orderListeners.forEach(l -> {
                    try { l.accept(update); } catch (Exception e) { log.warn("Order listener error", e); }
                });
            } catch (Exception e) {
                log.warn("Failed to parse order update: {}", e.getMessage());
            }
        }
    }

    private void handleUserFills(JsonNode data) {
        if (data == null || !data.isArray()) return;

        for (JsonNode node : data) {
            try {
                String tid = node.has("tid") ? node.get("tid").asText() : "";
                String oid = node.has("oid") ? node.get("oid").asText() : "";
                String coin = node.get("coin").asText();
                String side = node.get("side").asText();
                double px = node.get("px").asDouble();
                double sz = node.get("sz").asDouble();
                double fee = node.has("fee") ? node.get("fee").asDouble() : 0;
                long time = node.get("time").asLong();

                OrderSide orderSide = "B".equals(side) ? OrderSide.BUY : OrderSide.SELL;
                Fill fill = new Fill(tid, oid, coin, orderSide, px, sz, fee, "USD",
                        Instant.ofEpochMilli(time));

                fillListeners.forEach(l -> {
                    try { l.accept(fill); } catch (Exception e) { log.warn("Fill listener error", e); }
                });
            } catch (Exception e) {
                log.warn("Failed to parse fill: {}", e.getMessage());
            }
        }
    }

    private void handleUserEvents(JsonNode data) {
        if (data == null || !data.isArray()) return;

        for (JsonNode event : data) {
            // User events can contain position updates within "fills" or "liquidation" events
            if (event.has("fills")) {
                handleUserFills(event.get("fills"));
            }
        }
    }

    private OrderStatus mapOrderStatus(String status) {
        return switch (status.toLowerCase()) {
            case "open" -> OrderStatus.OPEN;
            case "filled" -> OrderStatus.FILLED;
            case "canceled", "cancelled" -> OrderStatus.CANCELLED;
            case "triggered" -> OrderStatus.OPEN;
            case "rejected" -> OrderStatus.REJECTED;
            default -> OrderStatus.OPEN;
        };
    }

    private class HlWebSocketClient extends WebSocketClient {

        HlWebSocketClient(URI serverUri) {
            super(serverUri);
            this.setConnectionLostTimeout(30);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            log.info("Hyperliquid WebSocket connected");
            subscribe();
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.info("Hyperliquid WebSocket closed: {} (code {})", reason, code);
            if (shouldReconnect) {
                scheduleReconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            log.error("Hyperliquid WebSocket error: {}", ex.getMessage());
        }
    }
}
