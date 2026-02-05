package com.tradery.dataservice.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.Candle;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Manages live candle streams from Binance WebSocket.
 * Maintains current candle state and broadcasts updates to subscribers.
 */
public class LiveCandleManager {
    private static final Logger LOG = LoggerFactory.getLogger(LiveCandleManager.class);
    private static final String BINANCE_FUTURES_WS = "wss://fstream.binance.com/ws/";
    private static final String BINANCE_SPOT_WS = "wss://stream.binance.com:9443/ws/";
    private static final int RECONNECT_DELAY_MS = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, BinanceKlineClient> connections = new ConcurrentHashMap<>();
    private final Map<String, Candle> currentCandles = new ConcurrentHashMap<>();
    private final Map<String, List<Candle>> recentClosedCandles = new ConcurrentHashMap<>();
    private final Map<String, Set<BiConsumer<String, Candle>>> updateListeners = new ConcurrentHashMap<>();
    private final Map<String, Set<BiConsumer<String, Candle>>> closeListeners = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Subscribe to live candles for a symbol/timeframe (defaults to perp).
     * Returns the current candle if available.
     */
    public Candle subscribe(String symbol, String timeframe,
                            BiConsumer<String, Candle> onUpdate,
                            BiConsumer<String, Candle> onClose) {
        return subscribe(symbol, timeframe, "perp", onUpdate, onClose);
    }

    /**
     * Subscribe to live candles for a symbol/timeframe/marketType.
     * Returns the current candle if available.
     */
    public Candle subscribe(String symbol, String timeframe, String marketType,
                            BiConsumer<String, Candle> onUpdate,
                            BiConsumer<String, Candle> onClose) {
        String key = makeKey(symbol, timeframe, marketType);

        // Register listeners
        if (onUpdate != null) {
            updateListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(onUpdate);
        }
        if (onClose != null) {
            closeListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(onClose);
        }

        // Start connection if not exists
        if (!connections.containsKey(key)) {
            startConnection(symbol, timeframe, marketType);
        }

        return currentCandles.get(key);
    }

    /**
     * Unsubscribe from live candles (defaults to perp).
     */
    public void unsubscribe(String symbol, String timeframe,
                            BiConsumer<String, Candle> onUpdate,
                            BiConsumer<String, Candle> onClose) {
        unsubscribe(symbol, timeframe, "perp", onUpdate, onClose);
    }

    /**
     * Unsubscribe from live candles with market type.
     */
    public void unsubscribe(String symbol, String timeframe, String marketType,
                            BiConsumer<String, Candle> onUpdate,
                            BiConsumer<String, Candle> onClose) {
        String key = makeKey(symbol, timeframe, marketType);

        if (onUpdate != null) {
            Set<BiConsumer<String, Candle>> listeners = updateListeners.get(key);
            if (listeners != null) {
                listeners.remove(onUpdate);
            }
        }
        if (onClose != null) {
            Set<BiConsumer<String, Candle>> listeners = closeListeners.get(key);
            if (listeners != null) {
                listeners.remove(onClose);
            }
        }

        // Stop connection if no more listeners
        Set<BiConsumer<String, Candle>> updates = updateListeners.get(key);
        Set<BiConsumer<String, Candle>> closes = closeListeners.get(key);
        boolean hasListeners = (updates != null && !updates.isEmpty()) ||
                               (closes != null && !closes.isEmpty());

        if (!hasListeners) {
            stopConnection(key);
        }
    }

    /**
     * Get the current (incomplete) candle for a symbol/timeframe.
     */
    public Candle getCurrentCandle(String symbol, String timeframe) {
        return getCurrentCandle(symbol, timeframe, "perp");
    }

    /**
     * Get the current (incomplete) candle for a symbol/timeframe/marketType.
     */
    public Candle getCurrentCandle(String symbol, String timeframe, String marketType) {
        return currentCandles.get(makeKey(symbol, timeframe, marketType));
    }

    /**
     * Get recent closed candles (up to 10).
     */
    public List<Candle> getRecentClosedCandles(String symbol, String timeframe) {
        return getRecentClosedCandles(symbol, timeframe, "perp");
    }

    /**
     * Get recent closed candles (up to 10) with market type.
     */
    public List<Candle> getRecentClosedCandles(String symbol, String timeframe, String marketType) {
        List<Candle> candles = recentClosedCandles.get(makeKey(symbol, timeframe, marketType));
        return candles != null ? List.copyOf(candles) : List.of();
    }

    /**
     * Check if a stream is active for symbol/timeframe.
     */
    public boolean isActive(String symbol, String timeframe) {
        return isActive(symbol, timeframe, "perp");
    }

    /**
     * Check if a stream is active for symbol/timeframe/marketType.
     */
    public boolean isActive(String symbol, String timeframe, String marketType) {
        BinanceKlineClient client = connections.get(makeKey(symbol, timeframe, marketType));
        return client != null && client.isOpen();
    }

    private void startConnection(String symbol, String timeframe, String marketType) {
        String key = makeKey(symbol, timeframe, marketType);
        String streamName = symbol.toLowerCase() + "@kline_" + timeframe;
        boolean isSpot = "spot".equalsIgnoreCase(marketType);
        String wsUrl = (isSpot ? BINANCE_SPOT_WS : BINANCE_FUTURES_WS) + streamName;

        LOG.info("Starting live stream for {} {} {} ({})", symbol, timeframe, marketType, isSpot ? "SPOT" : "PERP");

        try {
            BinanceKlineClient client = new BinanceKlineClient(new URI(wsUrl), key, symbol, timeframe, marketType);
            connections.put(key, client);
            client.connect();
        } catch (Exception e) {
            LOG.error("Failed to start WebSocket for {}: {}", key, e.getMessage());
        }
    }

    private void stopConnection(String key) {
        BinanceKlineClient client = connections.remove(key);
        if (client != null) {
            LOG.info("Stopping live stream for {}", key);
            client.close();
        }
        currentCandles.remove(key);
        recentClosedCandles.remove(key);
        updateListeners.remove(key);
        closeListeners.remove(key);
    }

    private void scheduleReconnect(String key, String symbol, String timeframe, String marketType) {
        scheduler.schedule(() -> {
            if (connections.containsKey(key)) {
                // Still have listeners, reconnect
                LOG.info("Reconnecting to {} {} {}", symbol, timeframe, marketType);
                connections.remove(key);
                startConnection(symbol, timeframe, marketType);
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void handleKlineMessage(String key, String message) {
        long receiveTime = System.currentTimeMillis();
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!root.has("e") || !"kline".equals(root.get("e").asText())) {
                return;
            }

            JsonNode k = root.get("k");
            if (k == null) return;

            // Extract Binance event time for latency tracking
            long eventTime = root.has("E") ? root.get("E").asLong() : 0;
            long latencyMs = eventTime > 0 ? receiveTime - eventTime : -1;

            Candle candle = new Candle(
                k.get("t").asLong(),
                k.get("o").asDouble(),
                k.get("h").asDouble(),
                k.get("l").asDouble(),
                k.get("c").asDouble(),
                k.get("v").asDouble()
            );

            boolean isClosed = k.get("x").asBoolean();

            if (isClosed) {
                // Candle closed
                LOG.info("[LIVE] {} candle CLOSED at {} (Binance latency: {}ms)",
                    key, candle.timestamp(), latencyMs);
                addClosedCandle(key, candle);
                currentCandles.remove(key);
                notifyCloseListeners(key, candle);
            } else {
                // Update current candle
                LOG.debug("[LIVE] {} candle UPDATE close={} (Binance latency: {}ms)",
                    key, candle.close(), latencyMs);
                currentCandles.put(key, candle);
                notifyUpdateListeners(key, candle);
            }

        } catch (Exception e) {
            LOG.warn("Failed to parse kline message: {}", e.getMessage());
        }
    }

    private void addClosedCandle(String key, Candle candle) {
        recentClosedCandles.compute(key, (k, list) -> {
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
            }
            // Check if already have this timestamp
            if (!list.isEmpty() && list.get(list.size() - 1).timestamp() == candle.timestamp()) {
                list.set(list.size() - 1, candle);
            } else {
                list.add(candle);
                // Keep only last 10
                while (list.size() > 10) {
                    list.remove(0);
                }
            }
            return list;
        });
    }

    private void notifyUpdateListeners(String key, Candle candle) {
        Set<BiConsumer<String, Candle>> listeners = updateListeners.get(key);
        if (listeners != null) {
            for (BiConsumer<String, Candle> listener : listeners) {
                try {
                    listener.accept(key, candle);
                } catch (Exception e) {
                    LOG.warn("Error in update listener: {}", e.getMessage());
                }
            }
        }
    }

    private void notifyCloseListeners(String key, Candle candle) {
        Set<BiConsumer<String, Candle>> listeners = closeListeners.get(key);
        if (listeners != null) {
            for (BiConsumer<String, Candle> listener : listeners) {
                try {
                    listener.accept(key, candle);
                } catch (Exception e) {
                    LOG.warn("Error in close listener: {}", e.getMessage());
                }
            }
        }
    }

    private String makeKey(String symbol, String timeframe, String marketType) {
        return symbol.toUpperCase() + ":" + timeframe + ":" + (marketType != null ? marketType : "perp");
    }

    public int getConnectionCount() {
        return connections.size();
    }

    public void shutdown() {
        LOG.info("Shutting down LiveCandleManager");
        scheduler.shutdown();
        for (String key : connections.keySet()) {
            stopConnection(key);
        }
    }

    /**
     * WebSocket client for Binance kline stream.
     */
    private class BinanceKlineClient extends WebSocketClient {
        private final String key;
        private final String symbol;
        private final String timeframe;
        private final String marketType;
        private final long connectStartTime;

        public BinanceKlineClient(URI serverUri, String key, String symbol, String timeframe, String marketType) {
            super(serverUri);
            this.key = key;
            this.symbol = symbol;
            this.timeframe = timeframe;
            this.marketType = marketType;
            this.connectStartTime = System.currentTimeMillis();
            LOG.info("[WS-BINANCE] Connecting to {} for key={}", serverUri, key);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            long connectDuration = System.currentTimeMillis() - connectStartTime;
            LOG.info("[WS-BINANCE] Connected: {} (connect took {}ms, status={})",
                key, connectDuration, handshake.getHttpStatus());
        }

        @Override
        public void onMessage(String message) {
            handleKlineMessage(key, message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            LOG.warn("[WS-BINANCE] Closed: {} (code={}, reason='{}', remote={})",
                key, code, reason, remote);
            scheduleReconnect(key, symbol, timeframe, marketType);
        }

        @Override
        public void onError(Exception ex) {
            LOG.error("[WS-BINANCE] Error: {} - {}", key, ex.getMessage(), ex);
        }
    }
}
