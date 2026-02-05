package com.tradery.dataservice.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.MarkPriceUpdate;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Manages live mark price streams from Binance WebSocket.
 * Provides funding rate and premium (mark - index) data at 1s intervals.
 */
public class LiveMarkPriceManager {
    private static final Logger LOG = LoggerFactory.getLogger(LiveMarkPriceManager.class);
    private static final String BINANCE_WS_BASE = "wss://fstream.binance.com/ws/";
    private static final int RECONNECT_DELAY_MS = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, BinanceMarkPriceClient> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<BiConsumer<String, MarkPriceUpdate>>> listeners = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LiveMarkPriceManager-Scheduler");
        t.setDaemon(true);
        return t;
    });

    public void subscribe(String symbol, BiConsumer<String, MarkPriceUpdate> listener) {
        String key = symbol.toUpperCase();

        listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

        if (!connections.containsKey(key)) {
            startConnection(key);
        }
    }

    public void unsubscribe(String symbol, BiConsumer<String, MarkPriceUpdate> listener) {
        String key = symbol.toUpperCase();

        Set<BiConsumer<String, MarkPriceUpdate>> set = listeners.get(key);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                stopConnection(key);
            }
        }
    }

    private void startConnection(String symbol) {
        String streamName = symbol.toLowerCase() + "@markPrice@1s";
        String wsUrl = BINANCE_WS_BASE + streamName;

        LOG.info("Starting markPrice stream for {}", symbol);

        try {
            BinanceMarkPriceClient client = new BinanceMarkPriceClient(new URI(wsUrl), symbol);
            connections.put(symbol, client);
            client.connect();
        } catch (Exception e) {
            LOG.error("Failed to start markPrice WebSocket for {}: {}", symbol, e.getMessage());
        }
    }

    private void stopConnection(String symbol) {
        BinanceMarkPriceClient client = connections.remove(symbol);
        if (client != null) {
            LOG.info("Stopping markPrice stream for {}", symbol);
            client.close();
        }
        listeners.remove(symbol);
    }

    private void scheduleReconnect(String symbol) {
        scheduler.schedule(() -> {
            if (connections.containsKey(symbol)) {
                LOG.info("Reconnecting markPrice stream for {}", symbol);
                connections.remove(symbol);
                startConnection(symbol);
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void handleMarkPriceMessage(String symbol, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!root.has("e") || !"markPriceUpdate".equals(root.get("e").asText())) {
                return;
            }

            double markPrice = root.get("p").asDouble();
            double indexPrice = root.get("i").asDouble();
            double fundingRate = root.get("r").asDouble();
            long nextFundingTime = root.get("T").asLong();
            double premium = markPrice - indexPrice;

            MarkPriceUpdate update = new MarkPriceUpdate(
                root.get("E").asLong(), markPrice, indexPrice, premium, fundingRate, nextFundingTime
            );

            Set<BiConsumer<String, MarkPriceUpdate>> set = listeners.get(symbol);
            if (set != null) {
                for (BiConsumer<String, MarkPriceUpdate> listener : set) {
                    try {
                        listener.accept(symbol, update);
                    } catch (Exception e) {
                        LOG.warn("Error in markPrice listener: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse markPrice message: {}", e.getMessage());
        }
    }

    public void shutdown() {
        LOG.info("Shutting down LiveMarkPriceManager");
        scheduler.shutdown();
        for (String key : connections.keySet()) {
            stopConnection(key);
        }
    }

    private class BinanceMarkPriceClient extends WebSocketClient {
        private final String symbol;

        public BinanceMarkPriceClient(URI serverUri, String symbol) {
            super(serverUri);
            this.symbol = symbol;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            LOG.info("MarkPrice stream connected: {}", symbol);
        }

        @Override
        public void onMessage(String message) {
            handleMarkPriceMessage(symbol, message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            LOG.warn("MarkPrice stream closed: {} (code={}, reason={})", symbol, code, reason);
            scheduleReconnect(symbol);
        }

        @Override
        public void onError(Exception ex) {
            LOG.error("MarkPrice stream error: {} - {}", symbol, ex.getMessage());
        }
    }
}
