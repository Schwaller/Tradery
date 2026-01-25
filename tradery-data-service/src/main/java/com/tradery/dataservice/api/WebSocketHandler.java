package com.tradery.dataservice.api;

import com.tradery.dataservice.page.PageManager;
import com.tradery.dataservice.page.PageKey;
import com.tradery.dataservice.page.PageState;
import com.tradery.dataservice.page.PageStatus;
import com.tradery.dataservice.page.PageUpdateListener;
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

/**
 * WebSocket handler for real-time page status updates.
 */
public class WebSocketHandler implements PageUpdateListener {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketHandler.class);

    private final PageManager pageManager;
    private final ObjectMapper objectMapper;
    private final Map<String, WsConnectContext> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>(); // consumerId -> pageKeys
    private final Map<String, Set<String>> pageSubscribers = new ConcurrentHashMap<>(); // pageKey -> consumerIds

    public WebSocketHandler(PageManager pageManager, ObjectMapper objectMapper) {
        this.pageManager = pageManager;
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

        // Clean up subscriptions
        Set<String> subs = subscriptions.remove(consumerId);
        if (subs != null) {
            for (String pageKey : subs) {
                Set<String> subscribers = pageSubscribers.get(pageKey);
                if (subscribers != null) {
                    subscribers.remove(consumerId);
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
}
