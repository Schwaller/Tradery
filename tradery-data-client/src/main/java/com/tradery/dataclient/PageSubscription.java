package com.tradery.dataclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket-based subscription manager for real-time page updates.
 */
public class PageSubscription {
    private static final Logger LOG = LoggerFactory.getLogger(PageSubscription.class);

    private final String host;
    private final int port;
    private final String consumerId;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<Consumer<PageUpdate>>> subscriptions = new ConcurrentHashMap<>();
    private final Set<Consumer<ConnectionState>> connectionListeners = ConcurrentHashMap.newKeySet();

    private WebSocketClient client;
    private volatile boolean shouldReconnect = true;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    public PageSubscription(String host, int port, String consumerId) {
        this.host = host;
        this.port = port;
        this.consumerId = consumerId;
    }

    /**
     * Connect to the data service.
     */
    public void connect() {
        if (client != null && client.isOpen()) {
            return;
        }

        try {
            URI uri = new URI(String.format("ws://%s:%d/subscribe?consumerId=%s", host, port, consumerId));
            client = createClient(uri);
            client.connectBlocking(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error("Failed to connect to data service", e);
            scheduleReconnect();
        }
    }

    /**
     * Disconnect from the data service.
     */
    public void disconnect() {
        shouldReconnect = false;
        if (client != null) {
            client.close();
        }
    }

    /**
     * Subscribe to updates for a page.
     */
    public void subscribe(String pageKey, Consumer<PageUpdate> listener) {
        subscriptions.computeIfAbsent(pageKey, k -> new CopyOnWriteArrayList<>()).add(listener);

        if (client != null && client.isOpen()) {
            sendSubscribe(pageKey);
        }
    }

    /**
     * Unsubscribe from updates for a page.
     */
    public void unsubscribe(String pageKey, Consumer<PageUpdate> listener) {
        List<Consumer<PageUpdate>> listeners = subscriptions.get(pageKey);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                subscriptions.remove(pageKey);
                if (client != null && client.isOpen()) {
                    sendUnsubscribe(pageKey);
                }
            }
        }
    }

    /**
     * Add a connection state listener.
     */
    public void addConnectionListener(Consumer<ConnectionState> listener) {
        connectionListeners.add(listener);
        listener.accept(connectionState);
    }

    /**
     * Remove a connection state listener.
     */
    public void removeConnectionListener(Consumer<ConnectionState> listener) {
        connectionListeners.remove(listener);
    }

    /**
     * Get the current connection state.
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    private WebSocketClient createClient(URI uri) {
        return new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                LOG.info("Connected to data service");
                setConnectionState(ConnectionState.CONNECTED);

                // Resubscribe to all pages
                for (String pageKey : subscriptions.keySet()) {
                    sendSubscribe(pageKey);
                }
            }

            @Override
            public void onMessage(String message) {
                handleMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                LOG.info("Disconnected from data service: {} (remote={})", reason, remote);
                setConnectionState(ConnectionState.DISCONNECTED);

                if (shouldReconnect) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onError(Exception e) {
                LOG.error("WebSocket error", e);
            }
        };
    }

    private void sendSubscribe(String pageKey) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("action", "subscribe", "pageKey", pageKey));
            client.send(json);
        } catch (Exception e) {
            LOG.warn("Failed to send subscribe", e);
        }
    }

    private void sendUnsubscribe(String pageKey) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("action", "unsubscribe", "pageKey", pageKey));
            client.send(json);
        } catch (Exception e) {
            LOG.warn("Failed to send unsubscribe", e);
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String type = node.get("type").asText();
            String pageKey = node.has("pageKey") ? node.get("pageKey").asText() : null;

            PageUpdate update = switch (type) {
                case "STATE_CHANGED" -> new PageUpdate.StateChanged(
                    pageKey,
                    node.get("state").asText(),
                    node.get("progress").asInt()
                );
                case "DATA_READY" -> new PageUpdate.DataReady(
                    pageKey,
                    node.get("recordCount").asLong()
                );
                case "ERROR" -> new PageUpdate.Error(
                    pageKey,
                    node.has("message") ? node.get("message").asText() : "Unknown error"
                );
                case "EVICTED" -> new PageUpdate.Evicted(pageKey);
                default -> {
                    LOG.warn("Unknown message type: {}", type);
                    yield null;
                }
            };

            if (update != null && pageKey != null) {
                notifyListeners(pageKey, update);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse message: {}", message, e);
        }
    }

    private void notifyListeners(String pageKey, PageUpdate update) {
        List<Consumer<PageUpdate>> listeners = subscriptions.get(pageKey);
        if (listeners != null) {
            for (Consumer<PageUpdate> listener : listeners) {
                try {
                    listener.accept(update);
                } catch (Exception e) {
                    LOG.warn("Listener error", e);
                }
            }
        }
    }

    private void setConnectionState(ConnectionState state) {
        this.connectionState = state;
        for (Consumer<ConnectionState> listener : connectionListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                LOG.warn("Connection listener error", e);
            }
        }
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (shouldReconnect) {
                    LOG.info("Attempting to reconnect...");
                    setConnectionState(ConnectionState.RECONNECTING);
                    connect();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Connection state enum.
     */
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    /**
     * Page update types.
     */
    public sealed interface PageUpdate {
        String pageKey();

        record StateChanged(String pageKey, String state, int progress) implements PageUpdate {}
        record DataReady(String pageKey, long recordCount) implements PageUpdate {}
        record Error(String pageKey, String message) implements PageUpdate {}
        record Evicted(String pageKey) implements PageUpdate {}
    }
}
