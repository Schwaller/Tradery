package com.tradery.dataclient.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
import com.tradery.core.model.MarkPriceUpdate;
import com.tradery.core.model.OpenInterestUpdate;
import com.tradery.data.page.DataType;
import com.tradery.data.page.PageKey;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Unified WebSocket connection to data-service.
 *
 * Handles both page subscriptions (historical data with progress tracking)
 * and live candle streams in a single connection.
 *
 * Features:
 * - Single WebSocket connection for all data needs
 * - subscribe_page: Request AND subscribe to page updates
 * - subscribe_live: Real-time candle updates
 * - Automatic reconnection with re-subscription
 * - EDT-safe callback dispatch
 */
public class DataServiceConnection {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceConnection.class);
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int CONNECT_TIMEOUT_MS = 10000;

    private final String host;
    private final int port;
    private final String consumerId;
    private final String consumerName;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper msgpackMapper = new ObjectMapper(new MessagePackFactory());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DataServiceConnection-Scheduler");
        t.setDaemon(true);
        return t;
    });

    // WebSocket connection
    private volatile DataServiceWebSocket webSocket;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile boolean shouldReconnect = true;
    private volatile boolean shutdown = false;

    // Connection state listeners
    private final Set<Consumer<ConnectionState>> connectionListeners = ConcurrentHashMap.newKeySet();

    // Page subscriptions: pageKey -> listeners
    private final Map<String, Set<PageUpdateCallback>> pageCallbacks = new ConcurrentHashMap<>();
    // Track pending page requests (not yet subscribed with server)
    private final Set<PageRequest> pendingPageRequests = ConcurrentHashMap.newKeySet();
    // Track active page subscriptions
    private final Set<String> activePageSubscriptions = ConcurrentHashMap.newKeySet();

    // Live candle subscriptions: "SYMBOL:timeframe" -> listeners
    private final Map<String, Set<Consumer<Candle>>> liveUpdateListeners = new ConcurrentHashMap<>();
    private final Map<String, Set<Consumer<Candle>>> liveCloseListeners = new ConcurrentHashMap<>();
    private final Set<String> activeLiveSubscriptions = ConcurrentHashMap.newKeySet();

    // Live aggTrade subscriptions: "SYMBOL" -> listeners
    private final Map<String, Set<Consumer<AggTrade>>> aggTradeListeners = new ConcurrentHashMap<>();
    private final Set<String> activeAggTradeSubscriptions = ConcurrentHashMap.newKeySet();

    // Live markPrice subscriptions: "SYMBOL" -> listeners
    private final Map<String, Set<Consumer<MarkPriceUpdate>>> markPriceListeners = new ConcurrentHashMap<>();
    private final Set<String> activeMarkPriceSubscriptions = ConcurrentHashMap.newKeySet();

    // Live OI subscriptions: "SYMBOL" -> listeners
    private final Map<String, Set<Consumer<OpenInterestUpdate>>> oiListeners = new ConcurrentHashMap<>();
    private final Set<String> activeOiSubscriptions = ConcurrentHashMap.newKeySet();

    // Historical aggTrades streaming: requestId -> callback
    private final Map<String, AggTradesHistoryCallback> aggTradesHistoryCallbacks = new ConcurrentHashMap<>();
    // Track active streams for reconnection: requestId -> request params
    private final Map<String, AggTradesHistoryRequest> activeAggTradesHistoryStreams = new ConcurrentHashMap<>();

    // Binary page data callbacks: pageKey -> callback
    private final Map<String, PageDataCallback> pageDataCallbacks = new ConcurrentHashMap<>();


    /**
     * Create a new connection to data-service.
     *
     * @param host         Data service host
     * @param port         Data service port
     * @param consumerId   Unique consumer ID (UUID recommended)
     * @param consumerName Human-readable consumer name for debugging
     */
    public DataServiceConnection(String host, int port, String consumerId, String consumerName) {
        this.host = host;
        this.port = port;
        this.consumerId = consumerId;
        this.consumerName = consumerName;
    }

    /**
     * Connect to data-service.
     * Returns immediately; connection happens asynchronously.
     */
    public void connect() {
        if (shutdown) {
            LOG.warn("Cannot connect - connection is shutdown");
            return;
        }

        if (webSocket != null && webSocket.isOpen()) {
            LOG.debug("Already connected");
            return;
        }

        setConnectionState(ConnectionState.CONNECTING);

        try {
            String wsUrl = String.format("ws://%s:%d/subscribe?consumerId=%s&consumerName=%s", host, port, consumerId, consumerName);
            webSocket = new DataServiceWebSocket(new URI(wsUrl));
            webSocket.setConnectionLostTimeout(60);
            webSocket.connectBlocking(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOG.error("Failed to connect to data-service at {}:{} - {}", host, port, e.getMessage());
            setConnectionState(ConnectionState.DISCONNECTED);
            scheduleReconnect();
        }
    }

    /**
     * Disconnect from data-service.
     */
    public void disconnect() {
        shouldReconnect = false;
        shutdown = true;
        scheduler.shutdown();
        if (webSocket != null) {
            webSocket.close();
        }
        setConnectionState(ConnectionState.DISCONNECTED);
    }

    /**
     * Check if connected to data-service.
     */
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED && webSocket != null && webSocket.isOpen();
    }

    /**
     * Get current connection state.
     */
    public ConnectionState getConnectionState() {
        return connectionState;
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

    // ========== Page Subscription API ==========

    /**
     * Request and subscribe to a page.
     *
     * This sends a subscribe_page message to data-service which:
     * 1. Creates/gets the page (starts loading if needed)
     * 2. Subscribes for state/progress updates
     *
     * @param dataType   Type of data (CANDLES, FUNDING, etc.)
     * @param symbol     Trading symbol
     * @param timeframe  Timeframe (null for non-timeframe types)
     * @param startTime  Start time in milliseconds
     * @param endTime    End time in milliseconds
     * @param callback   Callback for page updates
     */
    public void subscribePage(DataType dataType, String symbol, String timeframe,
                              long startTime, long endTime, PageUpdateCallback callback) {
        String pageKey = makePageKey(dataType, symbol, timeframe, "perp", startTime, endTime);
        PageRequest request = new PageRequest(dataType, symbol, timeframe, startTime, endTime);

        LOG.debug("Subscribing to page: {} (connected={})", pageKey, isConnected());

        // Register callback
        pageCallbacks.computeIfAbsent(pageKey, k -> ConcurrentHashMap.newKeySet()).add(callback);

        if (isConnected()) {
            sendSubscribePage(request);
            activePageSubscriptions.add(pageKey);
        } else {
            LOG.info("Not connected, queuing page request: {}", pageKey);
            pendingPageRequests.add(request);
        }
    }

    /**
     * Subscribe to a live (sliding window) page (defaults to perp market).
     * Live pages have no fixed anchor and slide forward with current time.
     *
     * @param dataType     Type of data (CANDLES, etc.)
     * @param symbol       Trading symbol
     * @param timeframe    Timeframe (null for non-timeframe types)
     * @param duration     Window duration in milliseconds
     * @param callback     Callback for page updates
     */
    public void subscribeLivePage(DataType dataType, String symbol, String timeframe,
                                  long duration, PageUpdateCallback callback) {
        subscribeLivePage(dataType, symbol, timeframe, "perp", duration, callback);
    }

    /**
     * Subscribe to a live (sliding window) page with market type.
     * Live pages have no fixed anchor and slide forward with current time.
     *
     * @param dataType     Type of data (CANDLES, etc.)
     * @param symbol       Trading symbol
     * @param timeframe    Timeframe (null for non-timeframe types)
     * @param marketType   Market type ("spot" or "perp")
     * @param duration     Window duration in milliseconds
     * @param callback     Callback for page updates
     */
    public void subscribeLivePage(DataType dataType, String symbol, String timeframe,
                                  String marketType, long duration, PageUpdateCallback callback) {
        String pageKey = makeLivePageKey(dataType, symbol, timeframe, marketType, duration);

        LOG.debug("Subscribing to live page: {} (connected={})", pageKey, isConnected());

        // Register callback
        pageCallbacks.computeIfAbsent(pageKey, k -> ConcurrentHashMap.newKeySet()).add(callback);

        if (isConnected()) {
            sendSubscribeLivePage(dataType, symbol, timeframe, marketType, duration);
            activePageSubscriptions.add(pageKey);
        } else {
            LOG.info("Not connected, queuing live page request: {}", pageKey);
            // Store as pending - need to track live page requests separately
            pendingPageRequests.add(new PageRequest(dataType, symbol, timeframe, marketType, duration, true));
        }
    }

    /**
     * Unsubscribe from page updates.
     */
    public void unsubscribePage(DataType dataType, String symbol, String timeframe,
                                long startTime, long endTime, PageUpdateCallback callback) {
        String pageKey = makePageKey(dataType, symbol, timeframe, "perp", startTime, endTime);

        Set<PageUpdateCallback> callbacks = pageCallbacks.get(pageKey);
        if (callbacks != null) {
            callbacks.remove(callback);
            if (callbacks.isEmpty()) {
                pageCallbacks.remove(pageKey);
                activePageSubscriptions.remove(pageKey);

                if (isConnected()) {
                    sendUnsubscribePage(pageKey);
                }
            }
        }
    }

    // ========== Live Candle Subscription API ==========

    /**
     * Subscribe to live candle updates.
     *
     * @param symbol    Trading symbol
     * @param timeframe Candle timeframe
     * @param onUpdate  Called on each candle update (incomplete candle)
     * @param onClose   Called when candle closes
     */
    public void subscribeLive(String symbol, String timeframe,
                              Consumer<Candle> onUpdate, Consumer<Candle> onClose) {
        String key = makeLiveKey(symbol, timeframe);

        if (onUpdate != null) {
            liveUpdateListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(onUpdate);
        }
        if (onClose != null) {
            liveCloseListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(onClose);
        }

        if (!activeLiveSubscriptions.contains(key)) {
            activeLiveSubscriptions.add(key);
            if (isConnected()) {
                sendSubscribeLive(symbol, timeframe);
            }
        }
    }

    /**
     * Unsubscribe from live candle updates.
     */
    public void unsubscribeLive(String symbol, String timeframe,
                                Consumer<Candle> onUpdate, Consumer<Candle> onClose) {
        String key = makeLiveKey(symbol, timeframe);

        if (onUpdate != null) {
            Set<Consumer<Candle>> listeners = liveUpdateListeners.get(key);
            if (listeners != null) {
                listeners.remove(onUpdate);
            }
        }
        if (onClose != null) {
            Set<Consumer<Candle>> listeners = liveCloseListeners.get(key);
            if (listeners != null) {
                listeners.remove(onClose);
            }
        }

        // Check if should unsubscribe from server
        Set<Consumer<Candle>> updates = liveUpdateListeners.get(key);
        Set<Consumer<Candle>> closes = liveCloseListeners.get(key);
        boolean hasListeners = (updates != null && !updates.isEmpty()) ||
                               (closes != null && !closes.isEmpty());

        if (!hasListeners && activeLiveSubscriptions.remove(key)) {
            if (isConnected()) {
                sendUnsubscribeLive(symbol, timeframe);
            }
        }
    }

    // ========== Live AggTrade Subscription API ==========

    public void subscribeLiveAggTrades(String symbol, Consumer<AggTrade> listener) {
        String key = symbol.toUpperCase();
        aggTradeListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

        if (!activeAggTradeSubscriptions.contains(key)) {
            activeAggTradeSubscriptions.add(key);
            if (isConnected()) {
                sendAction("subscribe_live_aggtrades", Map.of("symbol", key));
            }
        }
    }

    public void unsubscribeLiveAggTrades(String symbol, Consumer<AggTrade> listener) {
        String key = symbol.toUpperCase();
        Set<Consumer<AggTrade>> set = aggTradeListeners.get(key);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty() && activeAggTradeSubscriptions.remove(key)) {
                if (isConnected()) {
                    sendAction("unsubscribe_live_aggtrades", Map.of("symbol", key));
                }
            }
        }
    }

    // ========== Live MarkPrice Subscription API ==========

    public void subscribeLiveMarkPrice(String symbol, Consumer<MarkPriceUpdate> listener) {
        String key = symbol.toUpperCase();
        markPriceListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

        if (!activeMarkPriceSubscriptions.contains(key)) {
            activeMarkPriceSubscriptions.add(key);
            if (isConnected()) {
                sendAction("subscribe_live_markprice", Map.of("symbol", key));
            }
        }
    }

    public void unsubscribeLiveMarkPrice(String symbol, Consumer<MarkPriceUpdate> listener) {
        String key = symbol.toUpperCase();
        Set<Consumer<MarkPriceUpdate>> set = markPriceListeners.get(key);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty() && activeMarkPriceSubscriptions.remove(key)) {
                if (isConnected()) {
                    sendAction("unsubscribe_live_markprice", Map.of("symbol", key));
                }
            }
        }
    }

    // ========== Live OI Subscription API ==========

    public void subscribeLiveOi(String symbol, Consumer<OpenInterestUpdate> listener) {
        String key = symbol.toUpperCase();
        oiListeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

        if (!activeOiSubscriptions.contains(key)) {
            activeOiSubscriptions.add(key);
            if (isConnected()) {
                sendAction("subscribe_live_oi", Map.of("symbol", key));
            }
        }
    }

    public void unsubscribeLiveOi(String symbol, Consumer<OpenInterestUpdate> listener) {
        String key = symbol.toUpperCase();
        Set<Consumer<OpenInterestUpdate>> set = oiListeners.get(key);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty() && activeOiSubscriptions.remove(key)) {
                if (isConnected()) {
                    sendAction("unsubscribe_live_oi", Map.of("symbol", key));
                }
            }
        }
    }

    // ========== Historical AggTrades Streaming API ==========

    /**
     * Subscribe to historical aggTrades streaming.
     *
     * Data is streamed in chunks as it becomes available (from cache and/or fetched from Binance).
     * Progress updates are sent periodically to keep the connection alive during long fetches.
     *
     * @param symbol    Trading symbol
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @param callback  Callback for stream events
     * @return Request ID for this stream (use to cancel or resume)
     */
    public String subscribeAggTradesHistory(String symbol, long startTime, long endTime,
                                            AggTradesHistoryCallback callback) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String upperSymbol = symbol.toUpperCase();

        AggTradesHistoryRequest request = new AggTradesHistoryRequest(
            requestId, upperSymbol, startTime, endTime, 0);

        aggTradesHistoryCallbacks.put(requestId, callback);
        activeAggTradesHistoryStreams.put(requestId, request);

        if (isConnected()) {
            sendSubscribeAggTradesHistory(upperSymbol, startTime, endTime);
        } else {
            LOG.info("Not connected, queuing aggTrades history request: {}", requestId);
        }

        return requestId;
    }

    /**
     * Cancel an active aggTrades history stream.
     *
     * @param requestId The request ID returned from subscribeAggTradesHistory
     */
    public void cancelAggTradesHistory(String requestId) {
        if (isConnected() && activeAggTradesHistoryStreams.containsKey(requestId)) {
            sendAction("cancel_aggtrades_history", Map.of("requestId", requestId));
        }
        // Cleanup happens when we receive AGGTRADES_STREAM_CANCELLED
    }

    /**
     * Resume an aggTrades history stream from a specific timestamp.
     * Use this after reconnection to continue where the previous stream left off.
     *
     * @param symbol        Trading symbol
     * @param lastTimestamp Last timestamp received (stream resumes from lastTimestamp + 1)
     * @param endTime       Original end time
     * @param callback      Callback for stream events
     * @return New request ID for the resumed stream
     */
    public String resumeAggTradesHistory(String symbol, long lastTimestamp, long endTime,
                                         AggTradesHistoryCallback callback) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String upperSymbol = symbol.toUpperCase();

        AggTradesHistoryRequest request = new AggTradesHistoryRequest(
            requestId, upperSymbol, lastTimestamp + 1, endTime, lastTimestamp);

        aggTradesHistoryCallbacks.put(requestId, callback);
        activeAggTradesHistoryStreams.put(requestId, request);

        if (isConnected()) {
            sendAction("resume_aggtrades_history", Map.of(
                "symbol", upperSymbol,
                "lastTimestamp", lastTimestamp,
                "end", endTime
            ));
        }

        return requestId;
    }

    private void sendSubscribeAggTradesHistory(String symbol, long startTime, long endTime) {
        sendAction("subscribe_aggtrades_history", Map.of(
            "symbol", symbol,
            "start", startTime,
            "end", endTime
        ));
    }

    // ========== Shared send helper ==========

    private void sendAction(String action, Map<String, Object> fields) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("action", action);
            message.putAll(fields);
            webSocket.send(objectMapper.writeValueAsString(message));
            LOG.debug("Sent {}", action);
        } catch (Exception e) {
            LOG.error("Failed to send {}", action, e);
        }
    }

    // ========== WebSocket Message Handlers ==========

    private void sendSubscribePage(PageRequest request) {
        if (request.isLive) {
            sendSubscribeLivePage(request.dataType, request.symbol, request.timeframe, request.marketType, request.windowDurationMillis);
            return;
        }
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("action", "subscribe_page");
            message.put("dataType", request.dataType.toWireFormat());
            message.put("symbol", request.symbol);
            if (request.timeframe != null) {
                message.put("timeframe", request.timeframe);
            }
            message.put("startTime", request.startTime);
            message.put("endTime", request.endTime);
            message.put("consumerName", consumerName);

            String json = objectMapper.writeValueAsString(message);
            webSocket.send(json);
            LOG.debug("Sent subscribe_page: {} {} {}", request.dataType, request.symbol, request.timeframe);
        } catch (Exception e) {
            LOG.error("Failed to send subscribe_page", e);
        }
    }

    private void sendSubscribeLivePage(DataType dataType, String symbol, String timeframe, String marketType, long duration) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("action", "subscribe_live_page");
            message.put("dataType", dataType.toWireFormat());
            message.put("symbol", symbol);
            if (timeframe != null) {
                message.put("timeframe", timeframe);
            }
            message.put("marketType", marketType != null ? marketType : "perp");
            message.put("duration", duration);
            message.put("consumerName", consumerName);

            String json = objectMapper.writeValueAsString(message);
            webSocket.send(json);
            LOG.debug("Sent subscribe_live_page: {} {} {} {} duration={}", dataType, symbol, timeframe, marketType, duration);
        } catch (Exception e) {
            LOG.error("Failed to send subscribe_live_page", e);
        }
    }

    private void sendUnsubscribePage(String pageKey) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "action", "unsubscribe",
                "pageKey", pageKey
            ));
            webSocket.send(json);
            LOG.debug("Sent unsubscribe: {}", pageKey);
        } catch (Exception e) {
            LOG.error("Failed to send unsubscribe", e);
        }
    }

    private void sendSubscribeLive(String symbol, String timeframe) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "action", "subscribe_live",
                "symbol", symbol.toUpperCase(),
                "timeframe", timeframe
            ));
            webSocket.send(json);
            LOG.debug("Sent subscribe_live: {} {}", symbol, timeframe);
        } catch (Exception e) {
            LOG.error("Failed to send subscribe_live", e);
        }
    }

    private void sendUnsubscribeLive(String symbol, String timeframe) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "action", "unsubscribe_live",
                "symbol", symbol.toUpperCase(),
                "timeframe", timeframe
            ));
            webSocket.send(json);
            LOG.debug("Sent unsubscribe_live: {} {}", symbol, timeframe);
        } catch (Exception e) {
            LOG.error("Failed to send unsubscribe_live", e);
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String type = node.has("type") ? node.get("type").asText() : null;

            if (type == null) {
                LOG.warn("Message missing type field: {}", message);
                return;
            }

            switch (type) {
                case "STATE_CHANGED" -> handlePageStateChanged(node);
                case "DATA_READY" -> handlePageDataReady(node);
                case "ERROR" -> handlePageError(node);
                case "EVICTED" -> handlePageEvicted(node);
                case "CANDLE_UPDATE" -> handleCandleUpdate(node);
                case "CANDLE_CLOSED" -> handleCandleClosed(node);
                case "AGGTRADE" -> handleAggTrade(node);
                case "MARK_PRICE_UPDATE" -> handleMarkPriceUpdate(node);
                case "OI_UPDATE" -> handleOiUpdate(node);
                // Historical aggTrades streaming
                case "AGGTRADES_STREAM_START" -> handleAggTradesStreamStart(node);
                case "AGGTRADES_STREAM_RESUMED" -> handleAggTradesStreamResumed(node);
                case "AGGTRADES_CHUNK" -> handleAggTradesChunk(node);
                case "AGGTRADES_PROGRESS" -> handleAggTradesProgress(node);
                case "AGGTRADES_STREAM_END" -> handleAggTradesStreamEnd(node);
                case "AGGTRADES_STREAM_CANCELLED" -> handleAggTradesStreamCancelled(node);
                case "AGGTRADES_STREAM_ERROR" -> handleAggTradesStreamError(node);
                // Live page updates
                case "LIVE_UPDATE" -> handleLiveUpdate(node);
                case "LIVE_APPEND" -> handleLiveAppend(node);
                default -> LOG.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse message: {} - {}", message, e.getMessage());
        }
    }

    private void handlePageStateChanged(JsonNode node) {
        String pageKey = node.get("pageKey").asText();
        String state = node.get("state").asText();
        int progress = node.has("progress") ? node.get("progress").asInt() : 0;

        notifyPageCallbacks(pageKey, callback -> callback.onStateChanged(state, progress));
    }

    private void handlePageDataReady(JsonNode node) {
        String pageKey = node.get("pageKey").asText();
        long recordCount = node.get("recordCount").asLong();

        notifyPageCallbacks(pageKey, callback -> callback.onDataReady(recordCount));
    }

    private void handlePageError(JsonNode node) {
        String pageKey = node.get("pageKey").asText();
        String errorMessage = node.has("message") ? node.get("message").asText() : "Unknown error";

        notifyPageCallbacks(pageKey, callback -> callback.onError(errorMessage));
    }

    private void handlePageEvicted(JsonNode node) {
        String pageKey = node.get("pageKey").asText();
        notifyPageCallbacks(pageKey, PageUpdateCallback::onEvicted);
    }

    private void handleCandleUpdate(JsonNode node) {
        String key = node.get("key").asText();
        Candle candle = parseCandle(node);

        Set<Consumer<Candle>> listeners = liveUpdateListeners.get(key);
        if (listeners != null) {
            for (Consumer<Candle> listener : listeners) {
                try {
                    listener.accept(candle);
                } catch (Exception e) {
                    LOG.warn("Error in candle update listener: {}", e.getMessage());
                }
            }
        }
    }

    private void handleCandleClosed(JsonNode node) {
        String key = node.get("key").asText();
        Candle candle = parseCandle(node);

        Set<Consumer<Candle>> listeners = liveCloseListeners.get(key);
        if (listeners != null) {
            for (Consumer<Candle> listener : listeners) {
                try {
                    listener.accept(candle);
                } catch (Exception e) {
                    LOG.warn("Error in candle close listener: {}", e.getMessage());
                }
            }
        }
    }

    private void handleAggTrade(JsonNode node) {
        String key = node.get("key").asText();
        AggTrade trade = new AggTrade(
            node.get("aggTradeId").asLong(),
            node.get("price").asDouble(),
            node.get("quantity").asDouble(),
            0L, 0L,
            node.get("timestamp").asLong(),
            node.get("isBuyerMaker").asBoolean()
        );
        notifyListeners(aggTradeListeners.get(key), trade);
    }

    private void handleMarkPriceUpdate(JsonNode node) {
        String key = node.get("key").asText();
        MarkPriceUpdate update = new MarkPriceUpdate(
            node.get("timestamp").asLong(), node.get("markPrice").asDouble(),
            node.get("indexPrice").asDouble(), node.get("premium").asDouble(),
            node.get("fundingRate").asDouble(), node.get("nextFundingTime").asLong()
        );
        notifyListeners(markPriceListeners.get(key), update);
    }

    private void handleOiUpdate(JsonNode node) {
        String key = node.get("key").asText();
        OpenInterestUpdate update = new OpenInterestUpdate(
            node.get("timestamp").asLong(), node.get("openInterest").asDouble(),
            node.get("oiChange").asDouble()
        );
        notifyListeners(oiListeners.get(key), update);
    }

    // ========== Historical AggTrades Streaming Handlers ==========

    private void handleAggTradesStreamStart(JsonNode node) {
        String requestId = node.get("requestId").asText();
        String symbol = node.get("symbol").asText();
        long startTime = node.get("startTime").asLong();
        long endTime = node.get("endTime").asLong();

        AggTradesHistoryCallback callback = aggTradesHistoryCallbacks.get(requestId);
        if (callback != null) {
            try {
                callback.onStreamStart(requestId, symbol, startTime, endTime);
            } catch (Exception e) {
                LOG.warn("Error in aggTrades history callback: {}", e.getMessage());
            }
        }
    }

    private void handleAggTradesStreamResumed(JsonNode node) {
        String requestId = node.get("requestId").asText();
        String symbol = node.get("symbol").asText();
        long startTime = node.get("startTime").asLong();
        long endTime = node.get("endTime").asLong();

        AggTradesHistoryCallback callback = aggTradesHistoryCallbacks.get(requestId);
        if (callback != null) {
            try {
                callback.onStreamResumed(requestId, symbol, startTime, endTime);
            } catch (Exception e) {
                LOG.warn("Error in aggTrades history callback: {}", e.getMessage());
            }
        }
    }

    private void handleAggTradesChunk(JsonNode node) {
        String requestId = node.get("requestId").asText();
        String source = node.get("source").asText();
        JsonNode tradesNode = node.get("trades");

        // Parse trades from the chunk
        List<AggTrade> trades = new ArrayList<>();
        if (tradesNode != null && tradesNode.isArray()) {
            for (JsonNode t : tradesNode) {
                trades.add(new AggTrade(
                    t.get("id").asLong(),
                    t.get("price").asDouble(),
                    t.get("qty").asDouble(),
                    0L, 0L, // firstTradeId, lastTradeId not sent in chunks
                    t.get("ts").asLong(),
                    t.get("isBuyerMaker").asBoolean()
                ));
            }
        }

        // Update last timestamp for resume capability
        if (!trades.isEmpty()) {
            AggTradesHistoryRequest request = activeAggTradesHistoryStreams.get(requestId);
            if (request != null) {
                long lastTs = trades.get(trades.size() - 1).timestamp();
                activeAggTradesHistoryStreams.put(requestId, request.withLastTimestamp(lastTs));
            }
        }

        AggTradesHistoryCallback callback = aggTradesHistoryCallbacks.get(requestId);
        if (callback != null) {
            try {
                callback.onChunk(requestId, source, trades);
            } catch (Exception e) {
                LOG.warn("Error in aggTrades history callback: {}", e.getMessage());
            }
        }
    }

    private void handleAggTradesProgress(JsonNode node) {
        String requestId = node.get("requestId").asText();
        int percent = node.get("percent").asInt();
        String message = node.has("message") ? node.get("message").asText() : "";
        long totalStreamed = node.has("totalStreamed") ? node.get("totalStreamed").asLong() : 0;

        AggTradesHistoryCallback callback = aggTradesHistoryCallbacks.get(requestId);
        if (callback != null) {
            try {
                callback.onProgress(requestId, percent, message, totalStreamed);
            } catch (Exception e) {
                LOG.warn("Error in aggTrades history callback: {}", e.getMessage());
            }
        }
    }

    private void handleAggTradesStreamEnd(JsonNode node) {
        String requestId = node.get("requestId").asText();
        long totalCount = node.get("totalCount").asLong();

        AggTradesHistoryCallback callback = aggTradesHistoryCallbacks.remove(requestId);
        activeAggTradesHistoryStreams.remove(requestId);

        if (callback != null) {
            try {
                callback.onComplete(requestId, totalCount);
            } catch (Exception e) {
                LOG.warn("Error in aggTrades history callback: {}", e.getMessage());
            }
        }
    }

    private void handleAggTradesStreamCancelled(JsonNode node) {
        String requestId = node.get("requestId").asText();
        long totalStreamed = node.has("totalStreamed") ? node.get("totalStreamed").asLong() : 0;
        long lastTimestamp = node.has("lastTimestamp") ? node.get("lastTimestamp").asLong() : 0;

        AggTradesHistoryCallback callback = aggTradesHistoryCallbacks.remove(requestId);
        activeAggTradesHistoryStreams.remove(requestId);

        if (callback != null) {
            try {
                callback.onCancelled(requestId, totalStreamed, lastTimestamp);
            } catch (Exception e) {
                LOG.warn("Error in aggTrades history callback: {}", e.getMessage());
            }
        }
    }

    private void handleAggTradesStreamError(JsonNode node) {
        String requestId = node.get("requestId").asText();
        String error = node.has("error") ? node.get("error").asText() : "Unknown error";

        AggTradesHistoryCallback callback = aggTradesHistoryCallbacks.remove(requestId);
        AggTradesHistoryRequest request = activeAggTradesHistoryStreams.remove(requestId);

        if (callback != null) {
            try {
                // Pass request info so caller can retry/resume
                callback.onError(requestId, error,
                    request != null ? request.lastTimestamp : 0);
            } catch (Exception e) {
                LOG.warn("Error in aggTrades history callback: {}", e.getMessage());
            }
        }
    }

    // ========== Live Page Update Handlers ==========

    private void handleLiveUpdate(JsonNode node) {
        String pageKey = node.get("pageKey").asText();
        JsonNode candleNode = node.get("candle");
        Candle candle = parseCandleData(candleNode);

        notifyPageCallbacks(pageKey, cb -> cb.onLiveUpdate(candle));
    }

    private void handleLiveAppend(JsonNode node) {
        String pageKey = node.get("pageKey").asText();
        JsonNode candleNode = node.get("candle");
        JsonNode removedNode = node.get("removedTimestamps");

        Candle candle = parseCandleData(candleNode);

        List<Long> removedTimestamps = new ArrayList<>();
        if (removedNode != null && removedNode.isArray()) {
            for (JsonNode ts : removedNode) {
                removedTimestamps.add(ts.asLong());
            }
        }

        notifyPageCallbacks(pageKey, cb -> cb.onLiveAppend(candle, removedTimestamps));
    }

    private Candle parseCandleData(JsonNode node) {
        return new Candle(
            node.get("timestamp").asLong(),
            node.get("open").asDouble(),
            node.get("high").asDouble(),
            node.get("low").asDouble(),
            node.get("close").asDouble(),
            node.get("volume").asDouble()
        );
    }

    private <T> void notifyListeners(Set<Consumer<T>> listeners, T value) {
        if (listeners != null) {
            for (Consumer<T> listener : listeners) {
                try {
                    listener.accept(value);
                } catch (Exception e) {
                    LOG.warn("Error in listener: {}", e.getMessage());
                }
            }
        }
    }

    private Candle parseCandle(JsonNode node) {
        return new Candle(
            node.get("timestamp").asLong(),
            node.get("open").asDouble(),
            node.get("high").asDouble(),
            node.get("low").asDouble(),
            node.get("close").asDouble(),
            node.get("volume").asDouble()
        );
    }

    private void notifyPageCallbacks(String pageKey, Consumer<PageUpdateCallback> action) {
        Set<PageUpdateCallback> callbacks = pageCallbacks.get(pageKey);
        if (callbacks != null) {
            for (PageUpdateCallback callback : callbacks) {
                try {
                    action.accept(callback);
                } catch (Exception e) {
                    LOG.warn("Error in page callback: {}", e.getMessage());
                }
            }
        }
    }

    // ========== Connection Management ==========

    private void onConnected() {
        setConnectionState(ConnectionState.CONNECTED);
        LOG.info("Connected to data-service at {}:{}", host, port);

        // Re-subscribe to all pending page requests
        for (PageRequest request : pendingPageRequests) {
            sendSubscribePage(request);
            String pageKey = request.isLive
                ? makeLivePageKey(request.dataType, request.symbol, request.timeframe, request.marketType, request.windowDurationMillis)
                : makePageKey(request.dataType, request.symbol, request.timeframe, request.marketType, request.startTime, request.endTime);
            activePageSubscriptions.add(pageKey);
        }
        pendingPageRequests.clear();

        // Re-subscribe to existing page subscriptions
        for (String pageKey : activePageSubscriptions) {
            // For active subscriptions, just re-subscribe (page should still exist on server)
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                    "action", "subscribe",
                    "pageKey", pageKey
                ));
                webSocket.send(json);
            } catch (Exception e) {
                LOG.warn("Failed to re-subscribe to page: {}", pageKey);
            }
        }

        // Re-subscribe to live candle streams
        for (String key : activeLiveSubscriptions) {
            String[] parts = key.split(":");
            if (parts.length == 2) {
                sendSubscribeLive(parts[0], parts[1]);
            }
        }

        // Re-subscribe to aggTrade streams
        for (String symbol : activeAggTradeSubscriptions) {
            sendAction("subscribe_live_aggtrades", Map.of("symbol", symbol));
        }

        // Re-subscribe to markPrice streams
        for (String symbol : activeMarkPriceSubscriptions) {
            sendAction("subscribe_live_markprice", Map.of("symbol", symbol));
        }

        // Re-subscribe to OI streams
        for (String symbol : activeOiSubscriptions) {
            sendAction("subscribe_live_oi", Map.of("symbol", symbol));
        }

        // Resume aggTrades history streams
        for (AggTradesHistoryRequest request : activeAggTradesHistoryStreams.values()) {
            if (request.lastTimestamp > 0) {
                // Resume from last received timestamp
                LOG.info("Resuming aggTrades history stream {} from timestamp {}",
                    request.requestId, request.lastTimestamp);
                sendAction("resume_aggtrades_history", Map.of(
                    "symbol", request.symbol,
                    "lastTimestamp", request.lastTimestamp,
                    "end", request.endTime
                ));
            } else {
                // No data received yet, start fresh
                LOG.info("Restarting aggTrades history stream {}", request.requestId);
                sendSubscribeAggTradesHistory(request.symbol, request.startTime, request.endTime);
            }
        }
    }

    private void onDisconnected() {
        setConnectionState(ConnectionState.DISCONNECTED);
        LOG.warn("Disconnected from data-service");

        if (shouldReconnect && !shutdown) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shutdown) return;

        setConnectionState(ConnectionState.RECONNECTING);
        scheduler.schedule(() -> {
            if (!shutdown) {
                LOG.info("Attempting to reconnect to data-service...");
                connect();
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void setConnectionState(ConnectionState state) {
        this.connectionState = state;
        for (Consumer<ConnectionState> listener : connectionListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                LOG.warn("Error in connection listener: {}", e.getMessage());
            }
        }
    }

    // ========== Binary Frame Handling ==========

    /**
     * Handle a binary WebSocket frame.
     *
     * Binary frame layout:
     * [4 bytes: header length (int32 big-endian)]
     * [N bytes: UTF-8 JSON header]
     * [remaining: msgpack payload]
     */
    private void handleBinaryMessage(ByteBuffer buffer) {
        try {
            if (buffer.remaining() < 4) {
                LOG.warn("Binary frame too small: {} bytes", buffer.remaining());
                return;
            }

            // Read header length
            int headerLength = buffer.getInt();
            if (headerLength <= 0 || headerLength > buffer.remaining()) {
                LOG.warn("Invalid header length: {} (remaining: {})", headerLength, buffer.remaining());
                return;
            }

            // Read header JSON
            byte[] headerBytes = new byte[headerLength];
            buffer.get(headerBytes);
            String headerJson = new String(headerBytes, StandardCharsets.UTF_8);
            JsonNode header = objectMapper.readTree(headerJson);

            String type = header.get("type").asText();
            String pageKey = header.get("pageKey").asText();

            if ("PAGE_DATA".equals(type)) {
                String dataType = header.get("dataType").asText();
                long recordCount = header.get("recordCount").asLong();

                // Read remaining msgpack payload
                byte[] msgpackData = new byte[buffer.remaining()];
                buffer.get(msgpackData);

                // Deliver to callback
                PageDataCallback callback = pageDataCallbacks.get(pageKey);
                if (callback != null) {
                    callback.onBinaryData(pageKey, dataType, recordCount, msgpackData);
                } else {
                    LOG.debug("No data callback for page {} (binary data discarded)", pageKey);
                }
            } else if ("PAGE_DATA_CHUNK".equals(type)) {
                String dataType = header.get("dataType").asText();
                int chunkIndex = header.get("chunkIndex").asInt();
                int totalChunks = header.get("totalChunks").asInt();
                long chunkRecordCount = header.get("recordCount").asLong();

                // Read msgpack payload for this chunk
                byte[] msgpackData = new byte[buffer.remaining()];
                buffer.get(msgpackData);

                if (chunkIndex % 100 == 0 || chunkIndex == totalChunks - 1) {
                    LOG.debug("Received chunk {}/{} for {} ({} records, {} bytes)",
                        chunkIndex + 1, totalChunks, pageKey, chunkRecordCount, msgpackData.length);
                }

                // Deliver each chunk immediately — caller deserializes inline to avoid OOM
                PageDataCallback callback = pageDataCallbacks.get(pageKey);
                if (callback != null) {
                    callback.onBinaryChunk(pageKey, dataType, chunkIndex, totalChunks,
                        chunkRecordCount, msgpackData);

                    // Signal completion after last chunk
                    if (chunkIndex == totalChunks - 1) {
                        callback.onBinaryChunksComplete(pageKey, dataType, totalChunks);
                    }
                }
            } else {
                LOG.debug("Unknown binary frame type: {}", type);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse binary frame: {}", e.getMessage());
        }
    }

    /**
     * Register a callback for receiving binary page data.
     *
     * @param pageKey  The page key to listen for
     * @param callback Callback that receives raw msgpack bytes
     */
    public void setPageDataCallback(String pageKey, PageDataCallback callback) {
        if (callback != null) {
            pageDataCallbacks.put(pageKey, callback);
        } else {
            pageDataCallbacks.remove(pageKey);
        }
    }

    /**
     * Remove a page data callback.
     */
    public void removePageDataCallback(String pageKey) {
        pageDataCallbacks.remove(pageKey);
    }

    // ========== Key Generation ==========

    private String makePageKey(DataType dataType, String symbol, String timeframe, String marketType, long startTime, long endTime) {
        return new PageKey(
            dataType.toWireFormat(), "binance", symbol.toUpperCase(), timeframe,
            marketType != null ? marketType : "perp", endTime, endTime - startTime
        ).toKeyString();
    }

    private String makeLivePageKey(DataType dataType, String symbol, String timeframe, String marketType, long duration) {
        return new PageKey(
            dataType.toWireFormat(), "binance", symbol.toUpperCase(), timeframe,
            marketType != null ? marketType : "perp", null, duration
        ).toKeyString();
    }

    private String makeLiveKey(String symbol, String timeframe) {
        return symbol.toUpperCase() + ":" + timeframe;
    }

    // ========== Inner Classes ==========

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
     * Callback for page updates.
     *
     * Pages can be anchored (fixed time range) or live (sliding with current time).
     * Live pages receive onLiveUpdate and onLiveAppend callbacks.
     */
    public interface PageUpdateCallback {
        /**
         * Called when page state changes.
         * @param state    New state (PENDING, LOADING, READY, ERROR)
         * @param progress Load progress (0-100)
         */
        void onStateChanged(String state, int progress);

        /**
         * Called when page data is ready.
         * @param recordCount Number of records loaded
         */
        void onDataReady(long recordCount);

        /**
         * Called when page load fails.
         * @param message Error message
         */
        void onError(String message);

        /**
         * Called when page is evicted from server.
         */
        void onEvicted();

        /**
         * Called when the incomplete/forming candle is updated (live pages only).
         * @param candle The updated incomplete candle
         */
        default void onLiveUpdate(Candle candle) {}

        /**
         * Called when a new completed candle is appended (live pages only).
         * @param candle            The new completed candle
         * @param removedTimestamps Timestamps of candles removed to maintain window size
         */
        default void onLiveAppend(Candle candle, List<Long> removedTimestamps) {}
    }

    /**
     * Callback for receiving binary page data pushed over WebSocket.
     * The raw msgpack bytes can be deserialized into the appropriate type.
     */
    public interface PageDataCallback {
        /**
         * Called when binary page data is received (non-chunked, single frame).
         * Used for candles, funding, OI, premium.
         */
        void onBinaryData(String pageKey, String dataType, long recordCount, byte[] msgpackData);

        /**
         * Called for each chunk of binary data as it arrives (chunked streaming).
         * Used for aggTrades which are too large for a single frame.
         * Default implementation does nothing — override for chunked data handling.
         */
        default void onBinaryChunk(String pageKey, String dataType,
                                    int chunkIndex, int totalChunks,
                                    long chunkRecordCount, byte[] msgpackData) {}

        /**
         * Called when all chunks have been received.
         * Default implementation does nothing — override for chunked data handling.
         */
        default void onBinaryChunksComplete(String pageKey, String dataType, int totalChunks) {}
    }

    /**
     * Internal page request tracking.
     */
    private record PageRequest(DataType dataType, String symbol, String timeframe, String marketType,
                               long startTime, long endTime, long windowDurationMillis, boolean isLive) {
        // Anchored page constructor (defaults to perp)
        PageRequest(DataType dataType, String symbol, String timeframe, long startTime, long endTime) {
            this(dataType, symbol, timeframe, "perp", startTime, endTime, endTime - startTime, false);
        }
        // Live page constructor with marketType
        PageRequest(DataType dataType, String symbol, String timeframe, String marketType, long windowDurationMillis, boolean isLive) {
            this(dataType, symbol, timeframe, marketType != null ? marketType : "perp", 0, 0, windowDurationMillis, isLive);
        }
    }

    /**
     * Internal aggTrades history request tracking.
     */
    private record AggTradesHistoryRequest(String requestId, String symbol, long startTime, long endTime,
                                           long lastTimestamp) {
        AggTradesHistoryRequest withLastTimestamp(long ts) {
            return new AggTradesHistoryRequest(requestId, symbol, startTime, endTime, ts);
        }
    }

    /**
     * Callback for historical aggTrades streaming events.
     */
    public interface AggTradesHistoryCallback {
        /**
         * Called when the stream starts.
         * @param requestId The stream request ID
         * @param symbol    The symbol being streamed
         * @param startTime Start time of the stream
         * @param endTime   End time of the stream
         */
        default void onStreamStart(String requestId, String symbol, long startTime, long endTime) {}

        /**
         * Called when a resumed stream starts.
         * @param requestId The new stream request ID
         * @param symbol    The symbol being streamed
         * @param startTime Start time (from resume point)
         * @param endTime   End time of the stream
         */
        default void onStreamResumed(String requestId, String symbol, long startTime, long endTime) {}

        /**
         * Called when a chunk of trades is received.
         * @param requestId The stream request ID
         * @param source    Where the data came from: "cache", "api", or "vision"
         * @param trades    The trades in this chunk
         */
        void onChunk(String requestId, String source, List<AggTrade> trades);

        /**
         * Called periodically with progress updates (keeps connection alive).
         * @param requestId     The stream request ID
         * @param percent       Progress percentage (0-100)
         * @param message       Human-readable progress message
         * @param totalStreamed Total trades streamed so far
         */
        default void onProgress(String requestId, int percent, String message, long totalStreamed) {}

        /**
         * Called when the stream completes successfully.
         * @param requestId  The stream request ID
         * @param totalCount Total number of trades streamed
         */
        void onComplete(String requestId, long totalCount);

        /**
         * Called when the stream is cancelled.
         * @param requestId     The stream request ID
         * @param totalStreamed Total trades streamed before cancellation
         * @param lastTimestamp Last timestamp received (use for resumption)
         */
        default void onCancelled(String requestId, long totalStreamed, long lastTimestamp) {}

        /**
         * Called when an error occurs.
         * @param requestId     The stream request ID
         * @param error         Error message
         * @param lastTimestamp Last timestamp received (use for resumption)
         */
        default void onError(String requestId, String error, long lastTimestamp) {}
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
        public void onMessage(ByteBuffer bytes) {
            handleBinaryMessage(bytes);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            LOG.debug("WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
            onDisconnected();
        }

        @Override
        public void onError(Exception ex) {
            LOG.error("WebSocket error: {}", ex.getMessage());
        }
    }
}
