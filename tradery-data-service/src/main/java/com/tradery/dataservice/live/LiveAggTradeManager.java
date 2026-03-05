package com.tradery.dataservice.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.AggTrade;
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
 * Manages live aggTrade streams from Binance WebSocket.
 * Forwards each trade immediately to listeners — no batching.
 * Supports both spot and futures (perp) market types.
 */
public class LiveAggTradeManager {
    private static final Logger LOG = LoggerFactory.getLogger(LiveAggTradeManager.class);
    private static final String FUTURES_WS_BASE = "wss://fstream.binance.com/ws/";
    private static final String SPOT_WS_BASE = "wss://stream.binance.com:9443/ws/";
    private static final int RECONNECT_DELAY_MS = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    // Keyed by "SYMBOL:marketType" (e.g., "BTCUSDT:perp" or "BTCUSDT:spot")
    private final Map<String, BinanceAggTradeClient> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<BiConsumer<String, AggTrade>>> listeners = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LiveAggTradeManager-Scheduler");
        t.setDaemon(true);
        return t;
    });

    /**
     * Get the WebSocket base URL for a market type.
     */
    private static String getWsBase(String marketType) {
        return "spot".equalsIgnoreCase(marketType) ? SPOT_WS_BASE : FUTURES_WS_BASE;
    }

    /**
     * Build the connection key from symbol and market type.
     */
    private static String connectionKey(String symbol, String marketType) {
        return symbol.toUpperCase() + ":" + (marketType != null ? marketType : "perp");
    }

    /**
     * Subscribe to live aggTrades (defaults to futures/perp).
     */
    public void subscribe(String symbol, BiConsumer<String, AggTrade> listener) {
        subscribe(symbol, "perp", listener);
    }

    /**
     * Subscribe to live aggTrades for a specific market type.
     */
    public void subscribe(String symbol, String marketType, BiConsumer<String, AggTrade> listener) {
        String key = connectionKey(symbol, marketType);
        listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

        if (!connections.containsKey(key)) {
            startConnection(symbol.toUpperCase(), marketType != null ? marketType : "perp");
        }
    }

    /**
     * Unsubscribe from live aggTrades (defaults to futures/perp).
     */
    public void unsubscribe(String symbol, BiConsumer<String, AggTrade> listener) {
        unsubscribe(symbol, "perp", listener);
    }

    /**
     * Unsubscribe from live aggTrades for a specific market type.
     */
    public void unsubscribe(String symbol, String marketType, BiConsumer<String, AggTrade> listener) {
        String key = connectionKey(symbol, marketType);

        Set<BiConsumer<String, AggTrade>> set = listeners.get(key);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                stopConnection(key);
            }
        }
    }

    private void startConnection(String symbol, String marketType) {
        String key = connectionKey(symbol, marketType);
        String streamName = symbol.toLowerCase() + "@aggTrade";
        String wsUrl = getWsBase(marketType) + streamName;

        LOG.info("Starting {} aggTrade stream for {} ({})", marketType, symbol, wsUrl);

        try {
            BinanceAggTradeClient client = new BinanceAggTradeClient(new URI(wsUrl), key);
            connections.put(key, client);
            client.connect();
        } catch (Exception e) {
            LOG.error("Failed to start aggTrade WebSocket for {} {}: {}", symbol, marketType, e.getMessage());
        }
    }

    private void stopConnection(String key) {
        BinanceAggTradeClient client = connections.remove(key);
        if (client != null) {
            LOG.info("Stopping aggTrade stream for {}", key);
            client.close();
        }
        listeners.remove(key);
    }

    private void scheduleReconnect(String connectionKey, String symbol, String marketType) {
        scheduler.schedule(() -> {
            if (connections.containsKey(connectionKey)) {
                LOG.info("Reconnecting aggTrade stream for {}", connectionKey);
                connections.remove(connectionKey);
                startConnection(symbol, marketType);
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void handleAggTradeMessage(String connectionKey, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!root.has("e") || !"aggTrade".equals(root.get("e").asText())) {
                return;
            }

            AggTrade trade = new AggTrade(
                root.get("a").asLong(),
                root.get("p").asDouble(),
                root.get("q").asDouble(),
                root.get("f").asLong(),
                root.get("l").asLong(),
                root.get("T").asLong(),
                root.get("m").asBoolean()
            );

            Set<BiConsumer<String, AggTrade>> set = listeners.get(connectionKey);
            if (set != null) {
                // Extract symbol from connection key for the callback
                String symbol = connectionKey.contains(":") ? connectionKey.substring(0, connectionKey.indexOf(':')) : connectionKey;
                for (BiConsumer<String, AggTrade> listener : set) {
                    try {
                        listener.accept(symbol, trade);
                    } catch (Exception e) {
                        LOG.warn("Error in aggTrade listener: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse aggTrade message: {}", e.getMessage());
        }
    }

    public int getConnectionCount() {
        return connections.size();
    }

    public void shutdown() {
        LOG.info("Shutting down LiveAggTradeManager");
        scheduler.shutdown();
        for (String key : new java.util.ArrayList<>(connections.keySet())) {
            stopConnection(key);
        }
    }

    private class BinanceAggTradeClient extends WebSocketClient {
        private final String connectionKey;

        public BinanceAggTradeClient(URI serverUri, String connectionKey) {
            super(serverUri);
            this.connectionKey = connectionKey;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            LOG.info("AggTrade stream connected: {}", connectionKey);
        }

        @Override
        public void onMessage(String message) {
            handleAggTradeMessage(connectionKey, message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            LOG.warn("AggTrade stream closed: {} (code={}, reason={})", connectionKey, code, reason);
            // Parse symbol and market type from connection key for reconnect
            String[] parts = connectionKey.split(":", 2);
            String symbol = parts[0];
            String marketType = parts.length > 1 ? parts[1] : "perp";
            scheduleReconnect(connectionKey, symbol, marketType);
        }

        @Override
        public void onError(Exception ex) {
            LOG.error("AggTrade stream error: {} - {}", connectionKey, ex.getMessage());
        }
    }
}
