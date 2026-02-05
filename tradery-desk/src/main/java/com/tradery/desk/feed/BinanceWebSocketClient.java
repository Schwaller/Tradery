package com.tradery.desk.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * WebSocket client for Binance Futures kline streams.
 * Handles connection, reconnection, and message parsing.
 */
public class BinanceWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);
    private static final String FUTURES_WS_URL = "wss://fstream.binance.com/ws/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String symbol;
    private final String timeframe;
    private final String streamUrl;

    private WebSocketClient client;
    private Consumer<KlineMessage> onMessage;
    private Consumer<ConnectionState> onStateChange;
    private BiConsumer<String, Exception> onError;

    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BinanceWS-Reconnect");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean shouldReconnect = true;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_DELAY = 60; // seconds

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    public BinanceWebSocketClient(String symbol, String timeframe) {
        this.symbol = symbol.toLowerCase();
        this.timeframe = timeframe;
        // Stream format: btcusdt@kline_1h
        this.streamUrl = FUTURES_WS_URL + this.symbol + "@kline_" + timeframe;
    }

    /**
     * Set callback for incoming kline messages.
     */
    public void setOnMessage(Consumer<KlineMessage> callback) {
        this.onMessage = callback;
    }

    /**
     * Set callback for connection state changes.
     */
    public void setOnStateChange(Consumer<ConnectionState> callback) {
        this.onStateChange = callback;
    }

    /**
     * Set callback for errors.
     */
    public void setOnError(BiConsumer<String, Exception> callback) {
        this.onError = callback;
    }

    /**
     * Connect to the WebSocket stream.
     */
    public void connect() {
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            return;
        }

        shouldReconnect = true;
        doConnect();
    }

    /**
     * Internal connect implementation.
     */
    private void doConnect() {
        try {
            updateState(ConnectionState.CONNECTING);

            URI uri = new URI(streamUrl);
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("Connected to {} stream", symbol);
                    reconnectAttempts = 0;
                    updateState(ConnectionState.CONNECTED);
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("WebSocket closed: code={}, reason={}, remote={}",
                        code, reason, remote);
                    updateState(ConnectionState.DISCONNECTED);

                    if (shouldReconnect) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket error: {}", ex.getMessage());
                    if (onError != null) {
                        onError.accept(ex.getMessage(), ex);
                    }
                }
            };

            client.connect();
        } catch (Exception e) {
            log.error("Failed to connect: {}", e.getMessage());
            updateState(ConnectionState.DISCONNECTED);

            if (shouldReconnect) {
                scheduleReconnect();
            }
        }
    }

    /**
     * Schedule a reconnection attempt with exponential backoff.
     */
    private void scheduleReconnect() {
        if (!shouldReconnect) {
            return;
        }

        reconnectAttempts++;
        int delay = Math.min((int) Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY);

        log.info("Reconnecting in {} seconds (attempt {})", delay, reconnectAttempts);
        updateState(ConnectionState.RECONNECTING);

        reconnectExecutor.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    /**
     * Handle incoming WebSocket message.
     */
    private void handleMessage(String message) {
        try {
            KlineMessage kline = JSON.readValue(message, KlineMessage.class);
            if (kline.isKlineEvent() && onMessage != null) {
                onMessage.accept(kline);
            }
        } catch (Exception e) {
            log.debug("Failed to parse message: {}", e.getMessage());
        }
    }

    /**
     * Disconnect from the WebSocket.
     */
    public void disconnect() {
        shouldReconnect = false;

        if (client != null) {
            client.close();
        }

        updateState(ConnectionState.DISCONNECTED);
    }

    /**
     * Shutdown the client and executor.
     */
    public void shutdown() {
        disconnect();
        reconnectExecutor.shutdownNow();
        try {
            if (!reconnectExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Reconnect executor did not terminate cleanly for {}", symbol);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Update connection state and notify listeners.
     */
    private void updateState(ConnectionState newState) {
        if (this.state != newState) {
            this.state = newState;
            if (onStateChange != null) {
                onStateChange.accept(newState);
            }
        }
    }

    public ConnectionState getState() {
        return state;
    }

    public boolean isConnected() {
        return state == ConnectionState.CONNECTED;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }
}
