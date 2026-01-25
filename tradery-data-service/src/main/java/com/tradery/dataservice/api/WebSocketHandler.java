package com.tradery.dataservice.api;

import com.tradery.dataservice.live.LiveCandleManager;
import com.tradery.dataservice.page.PageManager;
import com.tradery.dataservice.page.PageKey;
import com.tradery.dataservice.page.PageState;
import com.tradery.dataservice.page.PageStatus;
import com.tradery.dataservice.page.PageUpdateListener;
import com.tradery.model.Candle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;

/**
 * WebSocket handler for real-time page status updates and live candle streams.
 */
public class WebSocketHandler implements PageUpdateListener {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketHandler.class);

    private final PageManager pageManager;
    private final LiveCandleManager liveCandleManager;
    private final ObjectMapper objectMapper;
    private final Map<String, WsConnectContext> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>(); // consumerId -> pageKeys
    private final Map<String, Set<String>> pageSubscribers = new ConcurrentHashMap<>(); // pageKey -> consumerIds

    // Live candle subscriptions
    private final Map<String, Set<String>> liveSubscriptions = new ConcurrentHashMap<>(); // consumerId -> liveKeys
    private final Map<String, Set<String>> liveSubscribers = new ConcurrentHashMap<>(); // liveKey -> consumerIds
    private final Map<String, BiConsumer<String, Candle>> updateCallbacks = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<String, Candle>> closeCallbacks = new ConcurrentHashMap<>();

    public WebSocketHandler(PageManager pageManager, LiveCandleManager liveCandleManager, ObjectMapper objectMapper) {
        this.pageManager = pageManager;
        this.liveCandleManager = liveCandleManager;
        this.objectMapper = objectMapper;
        pageManager.addUpdateListener(this);
    }

    public void onConnect(WsConnectContext ctx) {
        String consumerId = ctx.queryParam("consumerId");
        if (consumerId == null) {
            consumerId = ctx.sessionId();
        }

        LOG.info("WebSocket connected: {}", consumerId);
        connections.put(consumerId, ctx);
        subscriptions.put(consumerId, new CopyOnWriteArraySet<>());
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
                case "subscribe_live" -> handleSubscribeLive(consumerId, message);
                case "unsubscribe_live" -> handleUnsubscribeLive(consumerId, message);
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

        DataReadyMessage message = new DataReadyMessage("DATA_READY", pageKey, recordCount);
        broadcast(subscribers, message);
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

    private void sendError(WsMessageContext ctx, String error) {
        try {
            ctx.send(objectMapper.writeValueAsString(new ErrorMessage("ERROR", null, error)));
        } catch (Exception e) {
            LOG.warn("Failed to send error message", e);
        }
    }

    // Message records
    public record StateChangedMessage(String type, String pageKey, String state, int progress) {}
    public record DataReadyMessage(String type, String pageKey, long recordCount) {}
    public record ErrorMessage(String type, String pageKey, String message) {}
    public record EvictedMessage(String type, String pageKey) {}
    public record LiveCandleMessage(String type, String key, long timestamp, double open, double high,
                                    double low, double close, double volume) {}
}
