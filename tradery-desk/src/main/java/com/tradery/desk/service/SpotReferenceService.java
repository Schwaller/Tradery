package com.tradery.desk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Connects to Binance spot WebSocket for BTCUSDT reference price.
 * Provides a fixed reference price line for comparison with futures/other data.
 */
public class SpotReferenceService {

    private static final Logger log = LoggerFactory.getLogger(SpotReferenceService.class);
    private static final String BINANCE_SPOT_WS = "wss://stream.binance.com:9443/ws/btcusdt@trade";

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebSocket webSocket;
    private Consumer<Double> priceListener;
    private volatile double lastPrice = Double.NaN;
    private volatile boolean connected = false;
    private volatile long lastNotifyTime = 0;
    private static final long NOTIFY_INTERVAL_MS = 500; // Throttle updates to every 500ms

    public SpotReferenceService() {
        this.client = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Set the listener for price updates.
     */
    public void setPriceListener(Consumer<Double> listener) {
        this.priceListener = listener;
    }

    /**
     * Get the last known spot price.
     */
    public double getLastPrice() {
        return lastPrice;
    }

    /**
     * Check if connected to the WebSocket.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Start the WebSocket connection.
     */
    public void start() {
        if (webSocket != null) {
            return; // Already started
        }

        log.info("[SPOT-REF] Connecting to Binance spot BTCUSDT...");

        Request request = new Request.Builder()
                .url(BINANCE_SPOT_WS)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                log.info("[SPOT-REF] Connected to Binance spot BTCUSDT");
                connected = true;
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JsonNode json = mapper.readTree(text);
                    // Trade stream format: {"e":"trade","E":123456789,"s":"BTCUSDT","t":123,"p":"50000.00",...}
                    JsonNode priceNode = json.get("p");
                    if (priceNode != null) {
                        double price = Double.parseDouble(priceNode.asText());
                        lastPrice = price;
                        // Throttle notifications to avoid excessive UI updates
                        long now = System.currentTimeMillis();
                        if (priceListener != null && (now - lastNotifyTime) >= NOTIFY_INTERVAL_MS) {
                            lastNotifyTime = now;
                            priceListener.accept(price);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[SPOT-REF] Failed to parse message: {}", e.getMessage());
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                log.error("[SPOT-REF] WebSocket error: {}", t.getMessage());
                connected = false;
                // Attempt reconnect after delay
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                log.info("[SPOT-REF] WebSocket closed: {} {}", code, reason);
                connected = false;
            }
        });
    }

    /**
     * Stop the WebSocket connection.
     */
    public void stop() {
        if (webSocket != null) {
            webSocket.close(1000, "Stopping");
            webSocket = null;
        }
        connected = false;
    }

    private void scheduleReconnect() {
        // Simple reconnect after 5 seconds
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (!connected && webSocket != null) {
                    webSocket = null;
                    start();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
