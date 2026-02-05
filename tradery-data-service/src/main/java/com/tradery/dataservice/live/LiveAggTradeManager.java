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
 */
public class LiveAggTradeManager {
    private static final Logger LOG = LoggerFactory.getLogger(LiveAggTradeManager.class);
    private static final String BINANCE_WS_BASE = "wss://fstream.binance.com/ws/";
    private static final int RECONNECT_DELAY_MS = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, BinanceAggTradeClient> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<BiConsumer<String, AggTrade>>> listeners = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LiveAggTradeManager-Scheduler");
        t.setDaemon(true);
        return t;
    });

    public void subscribe(String symbol, BiConsumer<String, AggTrade> listener) {
        String key = symbol.toUpperCase();
        listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

        if (!connections.containsKey(key)) {
            startConnection(key);
        }
    }

    public void unsubscribe(String symbol, BiConsumer<String, AggTrade> listener) {
        String key = symbol.toUpperCase();

        Set<BiConsumer<String, AggTrade>> set = listeners.get(key);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                stopConnection(key);
            }
        }
    }

    private void startConnection(String symbol) {
        String streamName = symbol.toLowerCase() + "@aggTrade";
        String wsUrl = BINANCE_WS_BASE + streamName;

        LOG.info("Starting aggTrade stream for {}", symbol);

        try {
            BinanceAggTradeClient client = new BinanceAggTradeClient(new URI(wsUrl), symbol);
            connections.put(symbol, client);
            client.connect();
        } catch (Exception e) {
            LOG.error("Failed to start aggTrade WebSocket for {}: {}", symbol, e.getMessage());
        }
    }

    private void stopConnection(String symbol) {
        BinanceAggTradeClient client = connections.remove(symbol);
        if (client != null) {
            LOG.info("Stopping aggTrade stream for {}", symbol);
            client.close();
        }
        listeners.remove(symbol);
    }

    private void scheduleReconnect(String symbol) {
        scheduler.schedule(() -> {
            if (connections.containsKey(symbol)) {
                LOG.info("Reconnecting aggTrade stream for {}", symbol);
                connections.remove(symbol);
                startConnection(symbol);
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void handleAggTradeMessage(String symbol, String message) {
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

            Set<BiConsumer<String, AggTrade>> set = listeners.get(symbol);
            if (set != null) {
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
        for (String key : connections.keySet()) {
            stopConnection(key);
        }
    }

    private class BinanceAggTradeClient extends WebSocketClient {
        private final String symbol;

        public BinanceAggTradeClient(URI serverUri, String symbol) {
            super(serverUri);
            this.symbol = symbol;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            LOG.info("AggTrade stream connected: {}", symbol);
        }

        @Override
        public void onMessage(String message) {
            handleAggTradeMessage(symbol, message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            LOG.warn("AggTrade stream closed: {} (code={}, reason={})", symbol, code, reason);
            scheduleReconnect(symbol);
        }

        @Override
        public void onError(Exception ex) {
            LOG.error("AggTrade stream error: {} - {}", symbol, ex.getMessage());
        }
    }
}
