package com.tradery.dataservice.api;

import com.tradery.dataservice.live.LiveAggTradeManager;
import com.tradery.dataservice.live.LiveCandleManager;
import com.tradery.dataservice.live.LiveMarkPriceManager;
import com.tradery.dataservice.live.LiveOpenInterestPoller;
import com.tradery.dataservice.page.PageManager;
import com.tradery.dataservice.page.PageKey;
import com.tradery.dataservice.page.PageState;
import com.tradery.dataservice.page.PageStatus;
import com.tradery.dataservice.page.PageUpdateListener;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
import com.tradery.core.model.MarkPriceUpdate;
import com.tradery.core.model.OpenInterestUpdate;
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
    private final LiveAggTradeManager liveAggTradeManager;
    private final LiveMarkPriceManager liveMarkPriceManager;
    private final LiveOpenInterestPoller liveOpenInterestPoller;
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

    public WebSocketHandler(PageManager pageManager, LiveCandleManager liveCandleManager,
                            LiveAggTradeManager liveAggTradeManager, LiveMarkPriceManager liveMarkPriceManager,
                            LiveOpenInterestPoller liveOpenInterestPoller, ObjectMapper objectMapper) {
        this.pageManager = pageManager;
        this.liveCandleManager = liveCandleManager;
        this.liveAggTradeManager = liveAggTradeManager;
        this.liveMarkPriceManager = liveMarkPriceManager;
        this.liveOpenInterestPoller = liveOpenInterestPoller;
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
                case "subscribe_page" -> handleSubscribePage(consumerId, message);
                case "subscribe_live" -> handleSubscribeLive(consumerId, message);
                case "unsubscribe_live" -> handleUnsubscribeLive(consumerId, message);
                case "subscribe_live_aggtrades" -> handleSubscribeLiveAggTrades(consumerId, message);
                case "unsubscribe_live_aggtrades" -> handleUnsubscribeLiveAggTrades(consumerId, message);
                case "subscribe_live_markprice" -> handleSubscribeLiveMarkPrice(consumerId, message);
                case "unsubscribe_live_markprice" -> handleUnsubscribeLiveMarkPrice(consumerId, message);
                case "subscribe_live_oi" -> handleSubscribeLiveOi(consumerId, message);
                case "unsubscribe_live_oi" -> handleUnsubscribeLiveOi(consumerId, message);
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
            long startTime = message.get("startTime").asLong();
            long endTime = message.get("endTime").asLong();
            String consumerName = message.has("consumerName")
                ? message.get("consumerName").asText() : "WebSocket-" + consumerId;

            // Create PageKey
            PageKey key = new PageKey(dataType.toUpperCase(), symbol.toUpperCase(), timeframe, startTime, endTime);
            String pageKeyStr = key.toKeyString();

            LOG.info("Consumer {} requesting page {}", consumerId, pageKeyStr);

            // Request page from manager (creates if needed, adds consumer)
            PageStatus status = pageManager.requestPage(key, consumerId, consumerName);

            // Add to subscription maps for future updates
            subscriptions.computeIfAbsent(consumerId, k -> new CopyOnWriteArraySet<>()).add(pageKeyStr);
            pageSubscribers.computeIfAbsent(pageKeyStr, k -> new CopyOnWriteArraySet<>()).add(consumerId);

            // Send current status immediately
            sendStatusUpdate(consumerId, pageKeyStr, status);

            // If already ready, also send data ready message
            if (status.state() == PageState.READY) {
                DataReadyMessage dataReady = new DataReadyMessage("DATA_READY", pageKeyStr, status.recordCount());
                WsConnectContext ctx = connections.get(consumerId);
                if (ctx != null) {
                    ctx.send(objectMapper.writeValueAsString(dataReady));
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to handle subscribe_page", e);
            WsConnectContext ctx = connections.get(consumerId);
            if (ctx != null) {
                sendError(ctx, "Failed to subscribe to page: " + e.getMessage());
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
}
