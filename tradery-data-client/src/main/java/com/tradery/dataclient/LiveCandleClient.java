package com.tradery.dataclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.model.Candle;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * WebSocket client for subscribing to live candle updates from data-service.
 */
public class LiveCandleClient {
    private static final Logger LOG = LoggerFactory.getLogger(LiveCandleClient.class);
    private static final int RECONNECT_DELAY_MS = 5000;

    private final String wsUrl;
    private final String consumerId;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private DataServiceWebSocket webSocket;
    private final Map<String, Set<Consumer<Candle>>> updateListeners = new ConcurrentHashMap<>();
    private final Map<String, Set<Consumer<Candle>>> closeListeners = new ConcurrentHashMap<>();
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();

    private volatile boolean connected = false;
    private volatile boolean shutdown = false;

    public LiveCandleClient(String host, int port, String consumerId) {
        this.wsUrl = String.format("ws://%s:%d/subscribe?consumerId=%s", host, port, consumerId);
        this.consumerId = consumerId;
    }

    /**
     * Connect to the data service WebSocket.
     */
    public void connect() {
        if (shutdown) return;

        try {
            webSocket = new DataServiceWebSocket(new URI(wsUrl));
            webSocket.connect();
        } catch (Exception e) {
            LOG.error("Failed to connect to data service WebSocket: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * Subscribe to live candle updates for a symbol/timeframe.
     */
    public void subscribe(String symbol, String timeframe,
                          Consumer<Candle> onUpdate,
                          Consumer<Candle> onClose) {
        String key = makeKey(symbol, timeframe);

        if (onUpdate != null) {
            updateListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(onUpdate);
        }
        if (onClose != null) {
            closeListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(onClose);
        }

        if (!subscriptions.contains(key)) {
            subscriptions.add(key);
            if (connected) {
                sendSubscribe(symbol, timeframe);
            }
        }
    }

    /**
     * Unsubscribe from live candle updates.
     */
    public void unsubscribe(String symbol, String timeframe,
                            Consumer<Candle> onUpdate,
                            Consumer<Candle> onClose) {
        String key = makeKey(symbol, timeframe);

        if (onUpdate != null) {
            Set<Consumer<Candle>> listeners = updateListeners.get(key);
            if (listeners != null) {
                listeners.remove(onUpdate);
            }
        }
        if (onClose != null) {
            Set<Consumer<Candle>> listeners = closeListeners.get(key);
            if (listeners != null) {
                listeners.remove(onClose);
            }
        }

        // Check if should unsubscribe
        Set<Consumer<Candle>> updates = updateListeners.get(key);
        Set<Consumer<Candle>> closes = closeListeners.get(key);
        boolean hasListeners = (updates != null && !updates.isEmpty()) ||
                               (closes != null && !closes.isEmpty());

        if (!hasListeners && subscriptions.remove(key)) {
            if (connected) {
                sendUnsubscribe(symbol, timeframe);
            }
        }
    }

    /**
     * Check if connected to data service.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Close the client.
     */
    public void close() {
        shutdown = true;
        scheduler.shutdown();
        if (webSocket != null) {
            webSocket.close();
        }
    }

    private void sendSubscribe(String symbol, String timeframe) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                "action", "subscribe_live",
                "symbol", symbol.toUpperCase(),
                "timeframe", timeframe
            ));
            webSocket.send(message);
            LOG.debug("Sent subscribe_live for {} {}", symbol, timeframe);
        } catch (Exception e) {
            LOG.error("Failed to send subscribe: {}", e.getMessage());
        }
    }

    private void sendUnsubscribe(String symbol, String timeframe) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                "action", "unsubscribe_live",
                "symbol", symbol.toUpperCase(),
                "timeframe", timeframe
            ));
            webSocket.send(message);
            LOG.debug("Sent unsubscribe_live for {} {}", symbol, timeframe);
        } catch (Exception e) {
            LOG.error("Failed to send unsubscribe: {}", e.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String type = root.get("type").asText();

            if ("CANDLE_UPDATE".equals(type) || "CANDLE_CLOSED".equals(type)) {
                String key = root.get("key").asText();
                Candle candle = new Candle(
                    root.get("timestamp").asLong(),
                    root.get("open").asDouble(),
                    root.get("high").asDouble(),
                    root.get("low").asDouble(),
                    root.get("close").asDouble(),
                    root.get("volume").asDouble()
                );

                if ("CANDLE_UPDATE".equals(type)) {
                    notifyUpdateListeners(key, candle);
                } else {
                    notifyCloseListeners(key, candle);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to handle message: {}", e.getMessage());
        }
    }

    private void notifyUpdateListeners(String key, Candle candle) {
        Set<Consumer<Candle>> listeners = updateListeners.get(key);
        if (listeners != null) {
            for (Consumer<Candle> listener : listeners) {
                try {
                    listener.accept(candle);
                } catch (Exception e) {
                    LOG.warn("Error in update listener: {}", e.getMessage());
                }
            }
        }
    }

    private void notifyCloseListeners(String key, Candle candle) {
        Set<Consumer<Candle>> listeners = closeListeners.get(key);
        if (listeners != null) {
            for (Consumer<Candle> listener : listeners) {
                try {
                    listener.accept(candle);
                } catch (Exception e) {
                    LOG.warn("Error in close listener: {}", e.getMessage());
                }
            }
        }
    }

    private void onConnected() {
        connected = true;
        LOG.info("Connected to data service WebSocket");

        // Re-subscribe to all subscriptions
        for (String key : subscriptions) {
            String[] parts = key.split(":");
            if (parts.length == 2) {
                sendSubscribe(parts[0], parts[1]);
            }
        }
    }

    private void onDisconnected() {
        connected = false;
        LOG.warn("Disconnected from data service WebSocket");
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (shutdown) return;

        scheduler.schedule(() -> {
            LOG.info("Attempting to reconnect to data service...");
            connect();
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private String makeKey(String symbol, String timeframe) {
        return symbol.toUpperCase() + ":" + timeframe;
    }

    /**
     * WebSocket client implementation.
     */
    private class DataServiceWebSocket extends WebSocketClient {

        public DataServiceWebSocket(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            onConnected();
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            onDisconnected();
        }

        @Override
        public void onError(Exception ex) {
            LOG.error("WebSocket error: {}", ex.getMessage());
        }
    }
}
