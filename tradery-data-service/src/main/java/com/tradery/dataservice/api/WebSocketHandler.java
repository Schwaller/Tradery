package com.tradery.dataservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
import com.tradery.core.model.MarkPriceUpdate;
import com.tradery.core.model.OpenInterestUpdate;
import com.tradery.dataservice.ConsumerRegistry;
import com.tradery.dataservice.data.AggTradesStore;
import com.tradery.dataservice.live.LiveAggTradeManager;
import com.tradery.dataservice.live.LiveCandleManager;
import com.tradery.dataservice.live.LiveMarkPriceManager;
import com.tradery.dataservice.live.LiveOpenInterestPoller;
import com.tradery.data.page.PageKey;
import com.tradery.dataservice.page.*;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * WebSocket handler for real-time page status updates and live candle streams.
 */
public class WebSocketHandler implements PageUpdateListener {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketHandler.class);

    // Streaming configuration
    private static final int AGGTRADES_CHUNK_SIZE = 5000;
    private static final long PROGRESS_HEARTBEAT_INTERVAL_MS = 5000; // Send heartbeat every 5 seconds

    private final PageManager pageManager;
    private final ConsumerRegistry consumerRegistry;
    private final LiveCandleManager liveCandleManager;
    private final LiveAggTradeManager liveAggTradeManager;
    private final LiveMarkPriceManager liveMarkPriceManager;
    private final LiveOpenInterestPoller liveOpenInterestPoller;
    private final AggTradesStore aggTradesStore;
    private final ObjectMapper objectMapper;
    private final Map<String, WsConnectContext> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>(); // consumerId -> pageKeys
    private final Map<String, Set<String>> pageSubscribers = new ConcurrentHashMap<>(); // pageKey -> consumerIds

    // Live candle subscriptions
    private final Map<String, Set<String>> liveSubscriptions = new ConcurrentHashMap<>(); // consumerId -> liveKeys
    private final Map<String, Set<String>> liveSubscribers = new ConcurrentHashMap<>(); // liveKey -> consumerIds
    private final Map<String, BiConsumer<String, Candle>> updateCallbacks = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<String, Candle>> closeCallbacks = new ConcurrentHashMap<>();

    // Live aggTrade subscriptions
    private final Map<String, Set<String>> aggTradeSubscriptions = new ConcurrentHashMap<>(); // consumerId -> symbols
    private final Map<String, Set<String>> aggTradeSubscribers = new ConcurrentHashMap<>(); // symbol -> consumerIds
    private final Map<String, BiConsumer<String, AggTrade>> aggTradeCallbacks = new ConcurrentHashMap<>();

    // Live markPrice subscriptions
    private final Map<String, Set<String>> markPriceSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> markPriceSubscribers = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<String, MarkPriceUpdate>> markPriceCallbacks = new ConcurrentHashMap<>();

    // Live OI subscriptions
    private final Map<String, Set<String>> oiSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> oiSubscribers = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<String, OpenInterestUpdate>> oiCallbacks = new ConcurrentHashMap<>();

    // Historical aggTrades streaming
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, AggTradesStreamState> activeStreams = new ConcurrentHashMap<>(); // requestId -> state
    private final Map<String, Set<String>> consumerStreams = new ConcurrentHashMap<>(); // consumerId -> requestIds

    public WebSocketHandler(PageManager pageManager, ConsumerRegistry consumerRegistry,
                            LiveCandleManager liveCandleManager,
                            LiveAggTradeManager liveAggTradeManager, LiveMarkPriceManager liveMarkPriceManager,
                            LiveOpenInterestPoller liveOpenInterestPoller, AggTradesStore aggTradesStore,
                            ObjectMapper objectMapper) {
        this.pageManager = pageManager;
        this.consumerRegistry = consumerRegistry;
        this.liveCandleManager = liveCandleManager;
        this.liveAggTradeManager = liveAggTradeManager;
        this.liveMarkPriceManager = liveMarkPriceManager;
        this.liveOpenInterestPoller = liveOpenInterestPoller;
        this.aggTradesStore = aggTradesStore;
        this.objectMapper = objectMapper;
        pageManager.addUpdateListener(this);
    }

    /**
     * State for an active aggTrades history stream.
     */
    private static class AggTradesStreamState {
        final String requestId;
        final String consumerId;
        final String symbol;
        final long startTime;
        final long endTime;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicLong lastChunkTimestamp = new AtomicLong(0);
        final AtomicLong totalStreamed = new AtomicLong(0);
        volatile ScheduledFuture<?> heartbeatTask;
        volatile String lastProgressMessage = "";
        volatile int lastProgressPercent = 0;

        AggTradesStreamState(String requestId, String consumerId, String symbol, long startTime, long endTime) {
            this.requestId = requestId;
            this.consumerId = consumerId;
            this.symbol = symbol;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    public void onConnect(WsConnectContext ctx) {
        String consumerId = ctx.queryParam("consumerId");
        if (consumerId == null) {
            consumerId = ctx.sessionId();
        }

        // Set long idle timeout (5 minutes) to prevent premature connection drops
        // Client sends pings every 60s, so this gives plenty of headroom
        ctx.session.setIdleTimeout(java.time.Duration.ofMinutes(5));

        LOG.info("WebSocket connected: {} (idleTimeout=5min)", consumerId);
        connections.put(consumerId, ctx);
        subscriptions.put(consumerId, new CopyOnWriteArraySet<>());

        // Auto-register consumer via WS lifecycle (replaces HTTP register endpoint)
        String consumerName = ctx.queryParam("consumerName");
        if (consumerName == null) consumerName = "ws-" + consumerId.substring(0, 8);
        consumerRegistry.register(consumerId, consumerName, 0);
        // Mark as WS-connected so heartbeat timeout is skipped (WS ping/pong handles liveness)
        consumerRegistry.setWsConnected(consumerId, true);
    }

    public void onMessage(WsMessageContext ctx) {
        String consumerId = ctx.queryParam("consumerId");
        if (consumerId == null) {
            consumerId = ctx.sessionId();
        }

        try {
            JsonNode message = objectMapper.readTree(ctx.message());
            String action = message.get("action").asText();

            switch (action) {
                case "subscribe" -> handleSubscribe(consumerId, message);
                case "unsubscribe" -> handleUnsubscribe(consumerId, message);
                case "subscribe_page" -> handleSubscribePage(consumerId, message);
                case "subscribe_live_page" -> handleSubscribeLivePage(consumerId, message);
                case "subscribe_live" -> handleSubscribeLive(consumerId, message);
                case "unsubscribe_live" -> handleUnsubscribeLive(consumerId, message);
                case "subscribe_live_aggtrades" -> handleSubscribeLiveAggTrades(consumerId, message);
                case "unsubscribe_live_aggtrades" -> handleUnsubscribeLiveAggTrades(consumerId, message);
                case "subscribe_live_markprice" -> handleSubscribeLiveMarkPrice(consumerId, message);
                case "unsubscribe_live_markprice" -> handleUnsubscribeLiveMarkPrice(consumerId, message);
                case "subscribe_live_oi" -> handleSubscribeLiveOi(consumerId, message);
                case "unsubscribe_live_oi" -> handleUnsubscribeLiveOi(consumerId, message);
                // Historical aggTrades streaming
                case "subscribe_aggtrades_history" -> handleSubscribeAggTradesHistory(consumerId, message);
                case "cancel_aggtrades_history" -> handleCancelAggTradesHistory(consumerId, message);
                case "resume_aggtrades_history" -> handleResumeAggTradesHistory(consumerId, message);
                default -> LOG.warn("Unknown WebSocket action: {}", action);
            }
        } catch (Exception e) {
            LOG.error("Failed to process WebSocket message", e);
            sendError(ctx, e.getMessage());
        }
    }

    public void onClose(WsCloseContext ctx) {
        String consumerId = ctx.queryParam("consumerId");
        if (consumerId == null) {
            consumerId = ctx.sessionId();
        }

        LOG.info("WebSocket disconnected: {}", consumerId);
        connections.remove(consumerId);

        // Clean up page subscriptions
        Set<String> subs = subscriptions.remove(consumerId);
        if (subs != null) {
            for (String pageKey : subs) {
                Set<String> subscribers = pageSubscribers.get(pageKey);
                if (subscribers != null) {
                    subscribers.remove(consumerId);
                }
            }
        }

        // Clean up live subscriptions
        Set<String> liveSubs = liveSubscriptions.remove(consumerId);
        if (liveSubs != null) {
            for (String liveKey : liveSubs) {
                Set<String> subscribers = liveSubscribers.get(liveKey);
                if (subscribers != null) {
                    subscribers.remove(consumerId);
                }

                // Unsubscribe from LiveCandleManager if no more subscribers
                if (subscribers == null || subscribers.isEmpty()) {
                    String[] parts = liveKey.split(":");
                    if (parts.length == 2) {
                        BiConsumer<String, Candle> updateCb = updateCallbacks.remove(liveKey);
                        BiConsumer<String, Candle> closeCb = closeCallbacks.remove(liveKey);
                        liveCandleManager.unsubscribe(parts[0], parts[1], updateCb, closeCb);
                    }
                }
            }
        }

        // Clean up aggTrade subscriptions
        cleanupStreamSubscriptions(consumerId, aggTradeSubscriptions, aggTradeSubscribers,
            symbol -> {
                BiConsumer<String, AggTrade> cb = aggTradeCallbacks.remove(symbol);
                if (cb != null) liveAggTradeManager.unsubscribe(symbol, cb);
            });

        // Clean up markPrice subscriptions
        cleanupStreamSubscriptions(consumerId, markPriceSubscriptions, markPriceSubscribers,
            symbol -> {
                BiConsumer<String, MarkPriceUpdate> cb = markPriceCallbacks.remove(symbol);
                if (cb != null) liveMarkPriceManager.unsubscribe(symbol, cb);
            });

        // Clean up OI subscriptions
        cleanupStreamSubscriptions(consumerId, oiSubscriptions, oiSubscribers,
            symbol -> {
                BiConsumer<String, OpenInterestUpdate> cb = oiCallbacks.remove(symbol);
                if (cb != null) liveOpenInterestPoller.unsubscribe(symbol, cb);
            });

        // Clean up active aggTrades history streams
        Set<String> streams = consumerStreams.remove(consumerId);
        if (streams != null) {
            for (String requestId : streams) {
                AggTradesStreamState state = activeStreams.remove(requestId);
                if (state != null) {
                    state.cancelled.set(true);
                    if (state.heartbeatTask != null) {
                        state.heartbeatTask.cancel(false);
                    }
                    LOG.info("Cancelled aggTrades stream {} on disconnect", requestId);
                }
            }
        }

        // Auto-unregister consumer via WS lifecycle (replaces HTTP unregister endpoint)
        consumerRegistry.unregister(consumerId);
    }

    public void onError(WsErrorContext ctx) {
        LOG.error("WebSocket error", ctx.error());
    }

    private void handleSubscribe(String consumerId, JsonNode message) {
        String pageKey = message.get("pageKey").asText();
        LOG.debug("Consumer {} subscribing to {}", consumerId, pageKey);

        subscriptions.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(pageKey);
        pageSubscribers.computeIfAbsent(pageKey, k -> new CopyOnWriteArraySet<>()).add(consumerId);

        // Send current status immediately
        try {
            PageKey key = PageKey.fromKeyString(pageKey);
            PageStatus status = pageManager.getPageStatus(key);
            if (status != null) {
                sendStatusUpdate(consumerId, pageKey, status);
            }
        } catch (Exception e) {
            LOG.warn("Failed to send initial status for {}", pageKey, e);
        }
    }

    private void handleUnsubscribe(String consumerId, JsonNode message) {
        String pageKey = message.get("pageKey").asText();
        LOG.debug("Consumer {} unsubscribing from {}", consumerId, pageKey);

        Set<String> subs = subscriptions.get(consumerId);
        if (subs != null) {
            subs.remove(pageKey);
        }

        Set<String> subscribers = pageSubscribers.get(pageKey);
        if (subscribers != null) {
            subscribers.remove(consumerId);
        }
    }

    /**
     * Handle subscribe_page action: request AND subscribe to a page in one step.
     * This creates the page if needed, starts loading, and subscribes for updates.
     */
    private void handleSubscribePage(String consumerId, JsonNode message) {
        try {
            // Extract page parameters
            String dataType = message.get("dataType").asText();
            String symbol = message.get("symbol").asText();
            String timeframe = message.has("timeframe") && !message.get("timeframe").isNull()
                ? message.get("timeframe").asText() : null;
            String marketType = message.has("marketType") && !message.get("marketType").isNull()
                ? message.get("marketType").asText() : "perp";
            long startTime = message.get("startTime").asLong();
            long endTime = message.get("endTime").asLong();
            String consumerName = message.has("consumerName")
                ? message.get("consumerName").asText() : "WebSocket-" + consumerId;

            // Create PageKey (convert startTime/endTime to endTime/windowDurationMillis)
            PageKey key = new PageKey(dataType.toUpperCase(), "binance", symbol.toUpperCase(), timeframe, marketType, endTime, endTime - startTime);
            String pageKeyStr = key.toKeyString();

            LOG.info("Consumer {} requesting page {}", consumerId, pageKeyStr);

            // Request page from manager (creates if needed, adds consumer)
            PageStatus status = pageManager.requestPage(key, consumerId, consumerName);

            // Add to subscription maps for future updates
            subscriptions.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(pageKeyStr);
            pageSubscribers.computeIfAbsent(pageKeyStr, k -> new CopyOnWriteArraySet<>()).add(consumerId);

            // Send current status immediately
            sendStatusUpdate(consumerId, pageKeyStr, status);

            // If already ready, send data ready + binary data
            if (status.state() == PageState.READY) {
                DataReadyMessage dataReady = new DataReadyMessage("DATA_READY", pageKeyStr, status.recordCount());
                WsConnectContext ctx = connections.get(consumerId);
                if (ctx != null) {
                    ctx.send(objectMapper.writeValueAsString(dataReady));
                }
                // Also push binary data
                sendBinaryPageData(key, pageKeyStr, status.recordCount(),
                    Set.of(consumerId));
            }
        } catch (Exception e) {
            LOG.error("Failed to handle subscribe_page", e);
            WsConnectContext ctx = connections.get(consumerId);
            if (ctx != null) {
                sendError(ctx, "Failed to subscribe to page: " + e.getMessage());
            }
        }
    }

    /**
     * Handle subscribe_live_page action: request a live (sliding window) page.
     * Live pages have no fixed anchor and slide forward with current time.
     */
    private void handleSubscribeLivePage(String consumerId, JsonNode message) {
        try {
            String dataType = message.get("dataType").asText();
            String symbol = message.get("symbol").asText();
            String timeframe = message.has("timeframe") && !message.get("timeframe").isNull()
                ? message.get("timeframe").asText() : null;
            String marketType = message.has("marketType") && !message.get("marketType").isNull()
                ? message.get("marketType").asText() : "perp";
            long duration = message.get("duration").asLong();
            String consumerName = message.has("consumerName")
                ? message.get("consumerName").asText() : "WebSocket-" + consumerId;

            // Create live PageKey (anchor = null)
            PageKey key = PageKey.liveCandles(symbol, timeframe, marketType, duration);
            String pageKeyStr = key.toKeyString();

            LOG.info("Consumer {} requesting live page {}", consumerId, pageKeyStr);

            // Request page from manager
            PageStatus status = pageManager.requestPage(key, consumerId, consumerName);

            // Add to subscription maps
            subscriptions.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(pageKeyStr);
            pageSubscribers.computeIfAbsent(pageKeyStr, k -> new CopyOnWriteArraySet<>()).add(consumerId);

            // Send current status
            sendStatusUpdate(consumerId, pageKeyStr, status);

            if (status.state() == PageState.READY) {
                DataReadyMessage dataReady = new DataReadyMessage("DATA_READY", pageKeyStr, status.recordCount());
                WsConnectContext ctx = connections.get(consumerId);
                if (ctx != null) {
                    ctx.send(objectMapper.writeValueAsString(dataReady));
                }
                // Also push binary data
                sendBinaryPageData(key, pageKeyStr, status.recordCount(),
                    Set.of(consumerId));
            }
        } catch (Exception e) {
            LOG.error("Failed to handle subscribe_live_page", e);
            WsConnectContext ctx = connections.get(consumerId);
            if (ctx != null) {
                sendError(ctx, "Failed to subscribe to live page: " + e.getMessage());
            }
        }
    }

    private void sendError(WsConnectContext ctx, String error) {
        try {
            ctx.send(objectMapper.writeValueAsString(new ErrorMessage("ERROR", null, error)));
        } catch (Exception e) {
            LOG.warn("Failed to send error message", e);
        }
    }

    private void handleSubscribeLive(String consumerId, JsonNode message) {
        String symbol = message.get("symbol").asText();
        String timeframe = message.get("timeframe").asText();
        String liveKey = symbol.toUpperCase() + ":" + timeframe;

        LOG.info("Consumer {} subscribing to live {}", consumerId, liveKey);

        liveSubscriptions.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(liveKey);
        liveSubscribers.computeIfAbsent(liveKey, k -> new CopyOnWriteArraySet<>()).add(consumerId);

        // Create callbacks if this is first subscriber for this key
        if (!updateCallbacks.containsKey(liveKey)) {
            BiConsumer<String, Candle> updateCb = (key, candle) -> broadcastLiveUpdate(key, candle, false);
            BiConsumer<String, Candle> closeCb = (key, candle) -> broadcastLiveUpdate(key, candle, true);
            updateCallbacks.put(liveKey, updateCb);
            closeCallbacks.put(liveKey, closeCb);

            // Subscribe to LiveCandleManager
            Candle current = liveCandleManager.subscribe(symbol, timeframe, updateCb, closeCb);

            // Send current candle immediately if available
            if (current != null) {
                sendLiveCandle(consumerId, liveKey, current, false);
            }
        } else {
            // Already subscribed, just send current candle
            Candle current = liveCandleManager.getCurrentCandle(symbol, timeframe);
            if (current != null) {
                sendLiveCandle(consumerId, liveKey, current, false);
            }
        }
    }

    private void handleUnsubscribeLive(String consumerId, JsonNode message) {
        String symbol = message.get("symbol").asText();
        String timeframe = message.get("timeframe").asText();
        String liveKey = symbol.toUpperCase() + ":" + timeframe;

        LOG.debug("Consumer {} unsubscribing from live {}", consumerId, liveKey);

        Set<String> subs = liveSubscriptions.get(consumerId);
        if (subs != null) {
            subs.remove(liveKey);
        }

        Set<String> subscribers = liveSubscribers.get(liveKey);
        if (subscribers != null) {
            subscribers.remove(consumerId);

            // Unsubscribe from LiveCandleManager if no more subscribers
            if (subscribers.isEmpty()) {
                BiConsumer<String, Candle> updateCb = updateCallbacks.remove(liveKey);
                BiConsumer<String, Candle> closeCb = closeCallbacks.remove(liveKey);
                liveCandleManager.unsubscribe(symbol, timeframe, updateCb, closeCb);
            }
        }
    }

    private void broadcastLiveUpdate(String liveKey, Candle candle, boolean isClosed) {
        Set<String> subscribers = liveSubscribers.get(liveKey);
        if (subscribers == null || subscribers.isEmpty()) return;

        for (String consumerId : subscribers) {
            sendLiveCandle(consumerId, liveKey, candle, isClosed);
        }
    }

    private void sendLiveCandle(String consumerId, String liveKey, Candle candle, boolean isClosed) {
        WsConnectContext ctx = connections.get(consumerId);
        if (ctx == null) return;

        try {
            LiveCandleMessage message = new LiveCandleMessage(
                isClosed ? "CANDLE_CLOSED" : "CANDLE_UPDATE",
                liveKey,
                candle.timestamp(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
            );
            ctx.send(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            LOG.warn("Failed to send live candle to {}: {}", consumerId, e.getMessage());
        }
    }

    // PageUpdateListener implementation
    @Override
    public void onStateChanged(PageKey key, PageState state, int progress) {
        String pageKey = key.toKeyString();
        Set<String> subscribers = pageSubscribers.get(pageKey);
        if (subscribers == null || subscribers.isEmpty()) return;

        StateChangedMessage message = new StateChangedMessage("STATE_CHANGED", pageKey, state.name(), progress);
        broadcast(subscribers, message);
    }

    @Override
    public void onDataReady(PageKey key, long recordCount) {
        String pageKey = key.toKeyString();
        Set<String> subscribers = pageSubscribers.get(pageKey);
        if (subscribers == null || subscribers.isEmpty()) return;

        // Send text DATA_READY notification (backward compatibility)
        DataReadyMessage message = new DataReadyMessage("DATA_READY", pageKey, recordCount);
        broadcast(subscribers, message);

        // Send binary page data frame to all subscribers
        sendBinaryPageData(key, pageKey, recordCount, subscribers);
    }

    /**
     * Send page data as a binary WebSocket frame.
     *
     * Binary frame layout:
     * [4 bytes: header length (int32 big-endian)]
     * [N bytes: UTF-8 JSON header {"pageKey","type","dataType","recordCount"}]
     * [remaining: msgpack payload]
     */
    private void sendBinaryPageData(PageKey key, String pageKey, long recordCount, Set<String> subscribers) {
        // AggTrades pages don't hold data in memory (null page data) — skip binary push
        if (key.isAggTrades()) return;

        byte[] msgpackData = pageManager.getPageData(key);
        if (msgpackData == null) return;

        try {
            // Build header JSON
            String headerJson = objectMapper.writeValueAsString(new BinaryFrameHeader(
                pageKey, "PAGE_DATA", key.dataType(), recordCount));
            byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);

            // Assemble binary frame: [4-byte header length][header JSON][msgpack payload]
            ByteBuffer frame = ByteBuffer.allocate(4 + headerBytes.length + msgpackData.length);
            frame.putInt(headerBytes.length);
            frame.put(headerBytes);
            frame.put(msgpackData);
            frame.flip();

            // Send to all subscribers
            for (String consumerId : subscribers) {
                WsConnectContext ctx = connections.get(consumerId);
                if (ctx != null) {
                    try {
                        // Each send needs its own buffer position (duplicate shares content, not position)
                        ctx.send(frame.duplicate());
                    } catch (Exception e) {
                        LOG.warn("Failed to send binary page data to {}: {}", consumerId, e.getMessage());
                    }
                }
            }

            LOG.debug("Sent binary page data for {} ({} bytes header + {} bytes payload) to {} subscribers",
                pageKey, headerBytes.length, msgpackData.length, subscribers.size());
        } catch (Exception e) {
            LOG.error("Failed to build binary page data frame for {}", pageKey, e);
        }
    }

    @Override
    public void onError(PageKey key, String errorMessage) {
        String pageKey = key.toKeyString();
        Set<String> subscribers = pageSubscribers.get(pageKey);
        if (subscribers == null || subscribers.isEmpty()) return;

        ErrorMessage message = new ErrorMessage("ERROR", pageKey, errorMessage);
        broadcast(subscribers, message);
    }

    @Override
    public void onEvicted(PageKey key) {
        String pageKey = key.toKeyString();
        Set<String> subscribers = pageSubscribers.get(pageKey);
        if (subscribers == null || subscribers.isEmpty()) return;

        EvictedMessage message = new EvictedMessage("EVICTED", pageKey);
        broadcast(subscribers, message);
    }

    // ========== Live Page Update Handlers (PageUpdateListener) ==========

    @Override
    public void onLiveUpdate(PageKey key, Candle candle) {
        String pageKey = key.toKeyString();
        Set<String> subscribers = pageSubscribers.get(pageKey);
        if (subscribers == null || subscribers.isEmpty()) {
            LOG.trace("No subscribers for live update: {}", pageKey);
            return;
        }

        // Update of incomplete/forming candle
        LOG.debug("[LIVE_UPDATE] {} close={} to {} subscribers", pageKey, candle.close(), subscribers.size());
        LiveUpdateMessage message = new LiveUpdateMessage("LIVE_UPDATE", pageKey,
            new CandleData(candle.timestamp(), candle.open(), candle.high(), candle.low(), candle.close(), candle.volume()));
        broadcast(subscribers, message);
    }

    @Override
    public void onLiveAppend(PageKey key, Candle candle, List<Candle> removed) {
        String pageKey = key.toKeyString();
        Set<String> subscribers = pageSubscribers.get(pageKey);
        if (subscribers == null || subscribers.isEmpty()) return;

        // New completed candle, with optional removal of old candles
        List<Long> removedTimestamps = removed.stream().map(Candle::timestamp).toList();
        LiveAppendMessage message = new LiveAppendMessage("LIVE_APPEND", pageKey,
            new CandleData(candle.timestamp(), candle.open(), candle.high(), candle.low(), candle.close(), candle.volume()),
            removedTimestamps);
        broadcast(subscribers, message);
    }

    private void sendStatusUpdate(String consumerId, String pageKey, PageStatus status) {
        WsConnectContext ctx = connections.get(consumerId);
        if (ctx == null) return;

        try {
            StateChangedMessage message = new StateChangedMessage(
                "STATE_CHANGED", pageKey, status.state().name(), status.progress()
            );
            ctx.send(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            LOG.warn("Failed to send status update to {}", consumerId, e);
        }
    }

    private void broadcast(Set<String> consumerIds, Object message) {
        for (String consumerId : consumerIds) {
            WsConnectContext ctx = connections.get(consumerId);
            if (ctx != null) {
                try {
                    ctx.send(objectMapper.writeValueAsString(message));
                } catch (Exception e) {
                    LOG.warn("Failed to broadcast to {}", consumerId, e);
                }
            }
        }
    }

    // ========== AggTrade subscription handlers ==========

    private void handleSubscribeLiveAggTrades(String consumerId, JsonNode message) {
        String symbol = message.get("symbol").asText().toUpperCase();
        LOG.info("Consumer {} subscribing to live aggTrades {}", consumerId, symbol);

        aggTradeSubscriptions.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(symbol);
        aggTradeSubscribers.computeIfAbsent(symbol, k -> new CopyOnWriteArraySet<>()).add(consumerId);

        if (!aggTradeCallbacks.containsKey(symbol)) {
            BiConsumer<String, AggTrade> cb = (sym, trade) -> broadcastAggTrade(sym, trade);
            aggTradeCallbacks.put(symbol, cb);
            liveAggTradeManager.subscribe(symbol, cb);
        }
    }

    private void handleUnsubscribeLiveAggTrades(String consumerId, JsonNode message) {
        String symbol = message.get("symbol").asText().toUpperCase();
        LOG.debug("Consumer {} unsubscribing from live aggTrades {}", consumerId, symbol);

        removeStreamSubscriber(consumerId, symbol, aggTradeSubscriptions, aggTradeSubscribers,
            () -> {
                BiConsumer<String, AggTrade> cb = aggTradeCallbacks.remove(symbol);
                if (cb != null) liveAggTradeManager.unsubscribe(symbol, cb);
            });
    }

    private void broadcastAggTrade(String symbol, AggTrade trade) {
        Set<String> subscribers = aggTradeSubscribers.get(symbol);
        if (subscribers == null || subscribers.isEmpty()) return;

        AggTradeMessage msg = new AggTradeMessage("AGGTRADE", symbol,
            trade.aggTradeId(), trade.price(), trade.quantity(),
            trade.timestamp(), trade.isBuyerMaker());
        broadcast(subscribers, msg);
    }

    // ========== MarkPrice subscription handlers ==========

    private void handleSubscribeLiveMarkPrice(String consumerId, JsonNode message) {
        String symbol = message.get("symbol").asText().toUpperCase();
        LOG.info("Consumer {} subscribing to live markPrice {}", consumerId, symbol);

        markPriceSubscriptions.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(symbol);
        markPriceSubscribers.computeIfAbsent(symbol, k -> new CopyOnWriteArraySet<>()).add(consumerId);

        if (!markPriceCallbacks.containsKey(symbol)) {
            BiConsumer<String, MarkPriceUpdate> cb = (sym, update) -> broadcastMarkPriceUpdate(sym, update);
            markPriceCallbacks.put(symbol, cb);
            liveMarkPriceManager.subscribe(symbol, cb);
        }
    }

    private void handleUnsubscribeLiveMarkPrice(String consumerId, JsonNode message) {
        String symbol = message.get("symbol").asText().toUpperCase();
        LOG.debug("Consumer {} unsubscribing from live markPrice {}", consumerId, symbol);

        removeStreamSubscriber(consumerId, symbol, markPriceSubscriptions, markPriceSubscribers,
            () -> {
                BiConsumer<String, MarkPriceUpdate> cb = markPriceCallbacks.remove(symbol);
                if (cb != null) liveMarkPriceManager.unsubscribe(symbol, cb);
            });
    }

    private void broadcastMarkPriceUpdate(String symbol, MarkPriceUpdate update) {
        Set<String> subscribers = markPriceSubscribers.get(symbol);
        if (subscribers == null || subscribers.isEmpty()) return;

        MarkPriceMessage msg = new MarkPriceMessage("MARK_PRICE_UPDATE", symbol,
            update.timestamp(), update.markPrice(), update.indexPrice(),
            update.premium(), update.fundingRate(), update.nextFundingTime());
        broadcast(subscribers, msg);
    }

    // ========== OI subscription handlers ==========

    private void handleSubscribeLiveOi(String consumerId, JsonNode message) {
        String symbol = message.get("symbol").asText().toUpperCase();
        LOG.info("Consumer {} subscribing to live OI {}", consumerId, symbol);

        oiSubscriptions.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(symbol);
        oiSubscribers.computeIfAbsent(symbol, k -> new CopyOnWriteArraySet<>()).add(consumerId);

        if (!oiCallbacks.containsKey(symbol)) {
            BiConsumer<String, OpenInterestUpdate> cb = (sym, update) -> broadcastOiUpdate(sym, update);
            oiCallbacks.put(symbol, cb);
            liveOpenInterestPoller.subscribe(symbol, cb);
        }
    }

    private void handleUnsubscribeLiveOi(String consumerId, JsonNode message) {
        String symbol = message.get("symbol").asText().toUpperCase();
        LOG.debug("Consumer {} unsubscribing from live OI {}", consumerId, symbol);

        removeStreamSubscriber(consumerId, symbol, oiSubscriptions, oiSubscribers,
            () -> {
                BiConsumer<String, OpenInterestUpdate> cb = oiCallbacks.remove(symbol);
                if (cb != null) liveOpenInterestPoller.unsubscribe(symbol, cb);
            });
    }

    private void broadcastOiUpdate(String symbol, OpenInterestUpdate update) {
        Set<String> subscribers = oiSubscribers.get(symbol);
        if (subscribers == null || subscribers.isEmpty()) return;

        OiUpdateMessage msg = new OiUpdateMessage("OI_UPDATE", symbol,
            update.timestamp(), update.openInterest(), update.oiChange());
        broadcast(subscribers, msg);
    }

    // ========== Historical AggTrades Streaming ==========

    /**
     * Handle subscribe_aggtrades_history: Start streaming historical aggTrades to the client.
     * Data is streamed in chunks as it becomes available (from cache and/or fetched from Binance).
     */
    private void handleSubscribeAggTradesHistory(String consumerId, JsonNode message) {
        try {
            String symbol = message.get("symbol").asText().toUpperCase();
            long startTime = message.get("start").asLong();
            long endTime = message.get("end").asLong();

            // Generate unique request ID
            String requestId = UUID.randomUUID().toString().substring(0, 8);

            LOG.info("Consumer {} starting aggTrades history stream {} for {} [{} - {}]",
                consumerId, requestId, symbol, startTime, endTime);

            // Create stream state
            AggTradesStreamState state = new AggTradesStreamState(requestId, consumerId, symbol, startTime, endTime);
            activeStreams.put(requestId, state);
            consumerStreams.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(requestId);

            // Send stream start message
            WsConnectContext ctx = connections.get(consumerId);
            if (ctx == null) {
                LOG.warn("Consumer {} disconnected before stream could start", consumerId);
                activeStreams.remove(requestId);
                return;
            }

            sendMessage(ctx, new AggTradesStreamStartMessage("AGGTRADES_STREAM_START", requestId, symbol, startTime, endTime));

            // Start heartbeat task to keep connection alive during long fetches
            state.heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
                sendHeartbeat(state);
            }, PROGRESS_HEARTBEAT_INTERVAL_MS, PROGRESS_HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

            // Start streaming in background
            streamExecutor.submit(() -> executeAggTradesStream(state));

        } catch (Exception e) {
            LOG.error("Failed to start aggTrades history stream", e);
            WsConnectContext ctx = connections.get(consumerId);
            if (ctx != null) {
                sendError(ctx, "Failed to start stream: " + e.getMessage());
            }
        }
    }

    /**
     * Execute the aggTrades stream, sending chunks to the client as they become available.
     */
    private void executeAggTradesStream(AggTradesStreamState state) {
        try {
            int totalStreamed = aggTradesStore.streamAggTrades(
                state.symbol,
                state.startTime,
                state.endTime,
                AGGTRADES_CHUNK_SIZE,
                (chunk, source) -> {
                    // Stream chunk to client
                    if (!state.cancelled.get()) {
                        sendChunk(state, chunk, source);
                    }
                },
                progress -> {
                    // Update progress state (heartbeat task will send it)
                    state.lastProgressMessage = progress.message();
                    state.lastProgressPercent = progress.percentComplete();
                }
            );

            // Stop heartbeat
            if (state.heartbeatTask != null) {
                state.heartbeatTask.cancel(false);
            }

            // Send completion message
            if (!state.cancelled.get()) {
                WsConnectContext ctx = connections.get(state.consumerId);
                if (ctx != null) {
                    sendMessage(ctx, new AggTradesStreamEndMessage(
                        "AGGTRADES_STREAM_END", state.requestId, state.totalStreamed.get()));
                }
            }

            LOG.info("AggTrades stream {} completed: {} trades", state.requestId, state.totalStreamed.get());

        } catch (Exception e) {
            LOG.error("AggTrades stream {} failed: {}", state.requestId, e.getMessage(), e);
            if (state.heartbeatTask != null) {
                state.heartbeatTask.cancel(false);
            }
            if (!state.cancelled.get()) {
                WsConnectContext ctx = connections.get(state.consumerId);
                if (ctx != null) {
                    sendMessage(ctx, new AggTradesStreamErrorMessage(
                        "AGGTRADES_STREAM_ERROR", state.requestId, e.getMessage()));
                }
            }
        } finally {
            // Cleanup
            activeStreams.remove(state.requestId);
            Set<String> streams = consumerStreams.get(state.consumerId);
            if (streams != null) {
                streams.remove(state.requestId);
            }
        }
    }

    /**
     * Send a chunk of aggTrades to the client.
     */
    private void sendChunk(AggTradesStreamState state, List<AggTrade> chunk, String source) {
        WsConnectContext ctx = connections.get(state.consumerId);
        if (ctx == null || state.cancelled.get()) return;

        // Track last timestamp for resume capability
        if (!chunk.isEmpty()) {
            state.lastChunkTimestamp.set(chunk.get(chunk.size() - 1).timestamp());
            state.totalStreamed.addAndGet(chunk.size());
        }

        // Convert to lightweight format for transmission
        List<AggTradeData> data = chunk.stream()
            .map(t -> new AggTradeData(t.aggTradeId(), t.price(), t.quantity(), t.timestamp(), t.isBuyerMaker()))
            .toList();

        sendMessage(ctx, new AggTradesChunkMessage("AGGTRADES_CHUNK", state.requestId, source, data));
    }

    /**
     * Send a heartbeat/progress message to keep the connection alive.
     */
    private void sendHeartbeat(AggTradesStreamState state) {
        if (state.cancelled.get()) return;

        WsConnectContext ctx = connections.get(state.consumerId);
        if (ctx == null) {
            state.cancelled.set(true);
            return;
        }

        sendMessage(ctx, new AggTradesProgressMessage(
            "AGGTRADES_PROGRESS", state.requestId, state.lastProgressPercent,
            state.lastProgressMessage, state.totalStreamed.get()));
    }

    /**
     * Handle cancel_aggtrades_history: Cancel an active stream.
     */
    private void handleCancelAggTradesHistory(String consumerId, JsonNode message) {
        String requestId = message.get("requestId").asText();

        AggTradesStreamState state = activeStreams.get(requestId);
        if (state == null) {
            LOG.debug("Cancel request for unknown stream {}", requestId);
            return;
        }

        if (!state.consumerId.equals(consumerId)) {
            LOG.warn("Consumer {} tried to cancel stream {} owned by {}", consumerId, requestId, state.consumerId);
            return;
        }

        LOG.info("Cancelling aggTrades stream {} at consumer request", requestId);
        state.cancelled.set(true);
        aggTradesStore.cancelCurrentFetch();

        if (state.heartbeatTask != null) {
            state.heartbeatTask.cancel(false);
        }

        WsConnectContext ctx = connections.get(consumerId);
        if (ctx != null) {
            sendMessage(ctx, new AggTradesStreamCancelledMessage(
                "AGGTRADES_STREAM_CANCELLED", requestId, state.totalStreamed.get(), state.lastChunkTimestamp.get()));
        }
    }

    /**
     * Handle resume_aggtrades_history: Resume a stream from a specific timestamp.
     * This allows clients to reconnect and continue where they left off.
     */
    private void handleResumeAggTradesHistory(String consumerId, JsonNode message) {
        try {
            String symbol = message.get("symbol").asText().toUpperCase();
            long startTime = message.get("lastTimestamp").asLong() + 1; // Resume from after last received
            long endTime = message.get("end").asLong();

            // Treat as a new stream starting from the resume point
            LOG.info("Consumer {} resuming aggTrades stream for {} from timestamp {}", consumerId, symbol, startTime);

            // Generate new request ID for the resumed stream
            String requestId = UUID.randomUUID().toString().substring(0, 8);

            // Create stream state
            AggTradesStreamState state = new AggTradesStreamState(requestId, consumerId, symbol, startTime, endTime);
            activeStreams.put(requestId, state);
            consumerStreams.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(requestId);

            WsConnectContext ctx = connections.get(consumerId);
            if (ctx == null) {
                activeStreams.remove(requestId);
                return;
            }

            // Send stream start (with isResume flag)
            sendMessage(ctx, new AggTradesStreamResumedMessage(
                "AGGTRADES_STREAM_RESUMED", requestId, symbol, startTime, endTime));

            // Start heartbeat
            state.heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
                sendHeartbeat(state);
            }, PROGRESS_HEARTBEAT_INTERVAL_MS, PROGRESS_HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

            // Start streaming
            streamExecutor.submit(() -> executeAggTradesStream(state));

        } catch (Exception e) {
            LOG.error("Failed to resume aggTrades history stream", e);
            WsConnectContext ctx = connections.get(consumerId);
            if (ctx != null) {
                sendError(ctx, "Failed to resume stream: " + e.getMessage());
            }
        }
    }

    /**
     * Helper to send a message with JSON serialization.
     */
    private void sendMessage(WsConnectContext ctx, Object message) {
        try {
            ctx.send(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            LOG.warn("Failed to send message: {}", e.getMessage());
        }
    }

    // ========== Shared helpers for stream subscriptions ==========

    private void removeStreamSubscriber(String consumerId, String key,
                                        Map<String, Set<String>> perConsumer,
                                        Map<String, Set<String>> perKey,
                                        Runnable onLastUnsubscribe) {
        Set<String> subs = perConsumer.get(consumerId);
        if (subs != null) subs.remove(key);

        Set<String> subscribers = perKey.get(key);
        if (subscribers != null) {
            subscribers.remove(consumerId);
            if (subscribers.isEmpty()) {
                onLastUnsubscribe.run();
            }
        }
    }

    private void cleanupStreamSubscriptions(String consumerId,
                                            Map<String, Set<String>> perConsumer,
                                            Map<String, Set<String>> perKey,
                                            java.util.function.Consumer<String> unsubscribeAction) {
        Set<String> subs = perConsumer.remove(consumerId);
        if (subs != null) {
            for (String key : subs) {
                Set<String> subscribers = perKey.get(key);
                if (subscribers != null) {
                    subscribers.remove(consumerId);
                    if (subscribers.isEmpty()) {
                        unsubscribeAction.accept(key);
                    }
                }
            }
        }
    }

    private void sendError(WsMessageContext ctx, String error) {
        try {
            ctx.send(objectMapper.writeValueAsString(new ErrorMessage("ERROR", null, error)));
        } catch (Exception e) {
            LOG.warn("Failed to send error message", e);
        }
    }

    // Binary frame header (serialized as JSON inside binary WS frames)
    public record BinaryFrameHeader(String pageKey, String type, String dataType, long recordCount) {}

    // Message records
    public record StateChangedMessage(String type, String pageKey, String state, int progress) {}
    public record DataReadyMessage(String type, String pageKey, long recordCount) {}
    public record ErrorMessage(String type, String pageKey, String message) {}
    public record EvictedMessage(String type, String pageKey) {}
    public record LiveCandleMessage(String type, String key, long timestamp, double open, double high,
                                    double low, double close, double volume) {}
    public record AggTradeMessage(String type, String key, long aggTradeId, double price, double quantity,
                                   long timestamp, boolean isBuyerMaker) {}
    public record MarkPriceMessage(String type, String key, long timestamp, double markPrice, double indexPrice,
                                   double premium, double fundingRate, long nextFundingTime) {}
    public record OiUpdateMessage(String type, String key, long timestamp, double openInterest, double oiChange) {}

    // Historical aggTrades streaming message records
    public record AggTradesStreamStartMessage(String type, String requestId, String symbol, long startTime, long endTime) {}
    public record AggTradesStreamResumedMessage(String type, String requestId, String symbol, long startTime, long endTime) {}
    public record AggTradesChunkMessage(String type, String requestId, String source, List<AggTradeData> trades) {}
    public record AggTradesProgressMessage(String type, String requestId, int percent, String message, long totalStreamed) {}
    public record AggTradesStreamEndMessage(String type, String requestId, long totalCount) {}
    public record AggTradesStreamCancelledMessage(String type, String requestId, long totalStreamed, long lastTimestamp) {}
    public record AggTradesStreamErrorMessage(String type, String requestId, String error) {}

    // Lightweight aggTrade data for chunk transmission (omits fields not needed by client)
    public record AggTradeData(long id, double price, double qty, long ts, boolean isBuyerMaker) {}

    // Live page update message records
    public record LiveUpdateMessage(String type, String pageKey, CandleData candle) {}
    public record LiveAppendMessage(String type, String pageKey, CandleData candle, List<Long> removedTimestamps) {}

    // Candle data for live page messages
    public record CandleData(long timestamp, double open, double high, double low, double close, double volume) {}
}
