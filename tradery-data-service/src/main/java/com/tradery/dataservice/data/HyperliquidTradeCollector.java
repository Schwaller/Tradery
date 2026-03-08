package com.tradery.dataservice.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.Exchange;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects real-time trades from Hyperliquid WebSocket and stores them in SQLite.
 *
 * Hyperliquid's REST recentTrades returns only ~10 trades (useless for orderflow).
 * The WebSocket trades subscription streams every trade in real-time.
 * We collect these into SQLite using the existing aggTrades infrastructure.
 *
 * Limitation: No historical backfill. Data accumulates from first subscription.
 * Persists across restarts in SQLite.
 *
 * Single WebSocket connection manages all coin subscriptions.
 * Trades are buffered in memory and flushed every 1000 trades or 5 seconds.
 */
public class HyperliquidTradeCollector {

    private static final Logger LOG = LoggerFactory.getLogger(HyperliquidTradeCollector.class);
    private static final String WS_URL = "wss://api.hyperliquid.xyz/ws";
    private static final int FLUSH_THRESHOLD = 1000;
    private static final int FLUSH_INTERVAL_SECONDS = 5;
    private static final int RECONNECT_DELAY_SECONDS = 5;

    private static final Path CONFIG_FILE = Path.of(System.getProperty("user.home"), ".tradery", "hl-trade-collection.json");

    private final SqliteDataStore dataStore;
    private final ObjectMapper mapper = new ObjectMapper();

    // Coin subscriptions: coin → (exchange, symbol) for SQLite routing
    private final Map<String, CoinSubscription> subscriptions = new ConcurrentHashMap<>();
    // Active coins already subscribed on the current WS connection
    private final Set<String> activeWsSubscriptions = ConcurrentHashMap.newKeySet();

    // Per-coin stats tracking
    private final Map<String, AtomicLong> tradeCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> firstTradeTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastTradeTimes = new ConcurrentHashMap<>();

    // Trade buffer: symbol → list of trades (keyed by normalized symbol for SQLite)
    private final Map<String, List<AggTrade>> tradeBuffers = new ConcurrentHashMap<>();
    private final Object bufferLock = new Object();

    private volatile HlTradesWebSocketClient wsClient;
    private volatile boolean shouldReconnect = false;
    private volatile boolean connected = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hl-trades-collector");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> flushTask;

    record CoinSubscription(String coin, String exchange, String symbol) {}

    /**
     * Subscription info with live stats, returned by getSubscriptions().
     */
    public record SubscriptionInfo(String coin, String exchange, String symbol, long tradeCount, long firstTradeTime, long lastTradeTime) {}

    public HyperliquidTradeCollector(SqliteDataStore dataStore) {
        this.dataStore = dataStore;
        loadConfig();
    }

    /**
     * Add a persistent subscription. Starts collecting and saves to config file.
     * Called from the HTTP API.
     */
    public void addSubscription(String coin, String exchange, String symbol) {
        startCollecting(coin, exchange, symbol);
        saveConfig();
    }

    /**
     * Remove a subscription. Stops collecting and removes from config file.
     */
    public void removeSubscription(String coin) {
        String key = coin.toUpperCase();
        CoinSubscription removed = subscriptions.remove(key);
        if (removed == null) return;

        activeWsSubscriptions.remove(key);
        tradeCounts.remove(key);
        firstTradeTimes.remove(key);
        lastTradeTimes.remove(key);

        // Unsubscribe from WS if connected
        if (connected && wsClient != null && wsClient.isOpen()) {
            try {
                ObjectNode unsub = mapper.createObjectNode();
                unsub.put("method", "unsubscribe");
                ObjectNode subscription = unsub.putObject("subscription");
                subscription.put("type", "trades");
                subscription.put("coin", removed.coin());
                wsClient.send(mapper.writeValueAsString(unsub));
                LOG.info("Unsubscribed from Hyperliquid trades for coin={}", coin);
            } catch (Exception e) {
                LOG.warn("Failed to unsubscribe from coin={}: {}", coin, e.getMessage());
            }
        }

        // If no more subscriptions, disconnect
        if (subscriptions.isEmpty()) {
            shouldReconnect = false;
            if (flushTask != null) {
                flushTask.cancel(false);
                flushTask = null;
            }
            flushAllBuffers();
            if (wsClient != null) {
                wsClient.close();
            }
            connected = false;
        }

        saveConfig();
        LOG.info("Removed trade collection for coin={}", coin);
    }

    /**
     * Get all current subscriptions with live stats.
     */
    public List<SubscriptionInfo> getSubscriptions() {
        List<SubscriptionInfo> result = new ArrayList<>();
        for (var entry : subscriptions.entrySet()) {
            CoinSubscription sub = entry.getValue();
            long count = tradeCounts.getOrDefault(entry.getKey(), new AtomicLong(0)).get();
            long firstTime = firstTradeTimes.getOrDefault(entry.getKey(), new AtomicLong(0)).get();
            long lastTime = lastTradeTimes.getOrDefault(entry.getKey(), new AtomicLong(0)).get();
            result.add(new SubscriptionInfo(sub.coin(), sub.exchange(), sub.symbol(), count, firstTime, lastTime));
        }
        return result;
    }

    /**
     * Start collecting trades for a Hyperliquid coin.
     * Connects WebSocket on first call, subscribes to the coin's trades channel.
     *
     * @param coin     Hyperliquid coin name (e.g., "BTC", "km:US500")
     * @param exchange Exchange config key (e.g., "hyperliquid", "hl-km")
     * @param symbol   Normalized symbol for SQLite storage (e.g., "BTC", "KM:US500")
     */
    public void startCollecting(String coin, String exchange, String symbol) {
        String key = coin.toUpperCase();
        if (subscriptions.containsKey(key)) {
            return; // Already subscribed
        }

        subscriptions.put(key, new CoinSubscription(coin, exchange, symbol));
        tradeCounts.putIfAbsent(key, new AtomicLong(0));
        firstTradeTimes.putIfAbsent(key, new AtomicLong(0));
        lastTradeTimes.putIfAbsent(key, new AtomicLong(0));
        LOG.info("Registered trade collection for coin={} exchange={} symbol={}", coin, exchange, symbol);

        // Connect WS if not connected
        if (!connected && !shouldReconnect) {
            shouldReconnect = true;
            doConnect();

            // Start periodic flush
            flushTask = scheduler.scheduleAtFixedRate(this::flushAllBuffers,
                FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }

        // If already connected, subscribe immediately
        if (connected && wsClient != null && wsClient.isOpen()) {
            subscribeCoin(coin);
        }
    }

    /**
     * Shutdown the collector, flushing remaining trades and closing the WebSocket.
     */
    public void shutdown() {
        shouldReconnect = false;
        if (flushTask != null) {
            flushTask.cancel(false);
        }

        // Flush remaining trades
        flushAllBuffers();

        if (wsClient != null) {
            wsClient.close();
        }
        scheduler.shutdownNow();
        LOG.info("HyperliquidTradeCollector shut down");
    }

    private void doConnect() {
        try {
            wsClient = new HlTradesWebSocketClient(new URI(WS_URL));
            wsClient.setConnectionLostTimeout(30);
            wsClient.connect();
        } catch (Exception e) {
            LOG.error("Failed to connect Hyperliquid trades WebSocket: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        LOG.info("Scheduling Hyperliquid trades WebSocket reconnect in {}s", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(this::doConnect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void onConnected() {
        connected = true;
        activeWsSubscriptions.clear();

        // Subscribe to all registered coins
        for (var entry : subscriptions.entrySet()) {
            subscribeCoin(entry.getValue().coin());
        }
    }

    private void subscribeCoin(String coin) {
        if (activeWsSubscriptions.contains(coin.toUpperCase())) return;

        try {
            ObjectNode sub = mapper.createObjectNode();
            sub.put("method", "subscribe");
            ObjectNode subscription = sub.putObject("subscription");
            subscription.put("type", "trades");
            subscription.put("coin", coin);
            wsClient.send(mapper.writeValueAsString(sub));
            activeWsSubscriptions.add(coin.toUpperCase());
            LOG.info("Subscribed to Hyperliquid trades for coin={}", coin);
        } catch (Exception e) {
            LOG.error("Failed to subscribe to trades for coin={}: {}", coin, e.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            String channel = root.path("channel").asText("");

            if (!"trades".equals(channel)) return;

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) return;

            for (JsonNode tradeNode : data) {
                processTrade(tradeNode);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse Hyperliquid trades message: {}", e.getMessage());
        }
    }

    private void processTrade(JsonNode node) {
        try {
            String coin = node.get("coin").asText();
            String key = coin.toUpperCase();
            CoinSubscription sub = subscriptions.get(key);
            if (sub == null) return;

            long tid = node.get("tid").asLong();
            double price = Double.parseDouble(node.get("px").asText());
            double size = Double.parseDouble(node.get("sz").asText());
            long timestamp = node.get("time").asLong();
            String side = node.get("side").asText();

            // HL side: "A" = ask (sell aggressor / taker buy), "B" = bid (buy aggressor / taker sell)
            // isBuyerMaker: true when the BUYER is the maker (i.e., sell aggressor hit the bid)
            // So "A" (ask side lifted) = buyer is taker = isBuyerMaker=false
            //    "B" (bid side hit) = seller is taker = isBuyerMaker=true
            boolean isBuyerMaker = "B".equals(side);

            AggTrade trade = AggTrade.withExchange(
                tid, price, size,
                tid, tid,  // firstTradeId = lastTradeId = tid (individual trades)
                timestamp, isBuyerMaker,
                Exchange.HYPERLIQUID, DataMarketType.FUTURES_PERP,
                coin
            );

            // Update stats
            tradeCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
            firstTradeTimes.computeIfAbsent(key, k -> new AtomicLong(0)).compareAndSet(0, timestamp);
            lastTradeTimes.computeIfAbsent(key, k -> new AtomicLong(0)).set(timestamp);

            // Buffer the trade
            synchronized (bufferLock) {
                tradeBuffers
                    .computeIfAbsent(sub.symbol(), k -> new ArrayList<>())
                    .add(trade);

                // Check if any buffer exceeds threshold
                List<AggTrade> buffer = tradeBuffers.get(sub.symbol());
                if (buffer.size() >= FLUSH_THRESHOLD) {
                    flushBuffer(sub.symbol(), sub.exchange(), buffer);
                    tradeBuffers.put(sub.symbol(), new ArrayList<>());
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to process Hyperliquid trade: {}", e.getMessage());
        }
    }

    private void flushAllBuffers() {
        synchronized (bufferLock) {
            for (var entry : tradeBuffers.entrySet()) {
                String symbol = entry.getKey();
                List<AggTrade> buffer = entry.getValue();
                if (!buffer.isEmpty()) {
                    // Look up exchange from subscriptions
                    String exchange = subscriptions.values().stream()
                        .filter(s -> s.symbol().equals(symbol))
                        .map(CoinSubscription::exchange)
                        .findFirst()
                        .orElse("hyperliquid");
                    flushBuffer(symbol, exchange, buffer);
                    tradeBuffers.put(symbol, new ArrayList<>());
                }
            }
        }
    }

    private void flushBuffer(String symbol, String exchange, List<AggTrade> trades) {
        if (trades.isEmpty()) return;
        try {
            dataStore.saveAggTrades(symbol, exchange, "perp", trades);
            LOG.debug("Flushed {} Hyperliquid trades for {} (exchange={})", trades.size(), symbol, exchange);
        } catch (Exception e) {
            LOG.error("Failed to flush {} Hyperliquid trades for {}: {}", trades.size(), symbol, e.getMessage());
        }
    }

    // ========== Config Persistence ==========

    private void loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            LOG.info("No HL trade collection config found, starting fresh");
            return;
        }
        try {
            JsonNode arr = mapper.readTree(CONFIG_FILE.toFile());
            if (!arr.isArray()) return;
            int count = 0;
            for (JsonNode node : arr) {
                String coin = node.get("coin").asText();
                String exchange = node.has("exchange") ? node.get("exchange").asText() : "hyperliquid";
                String symbol = node.has("symbol") ? node.get("symbol").asText() : coin;
                startCollecting(coin, exchange, symbol);
                count++;
            }
            if (count > 0) {
                LOG.info("Loaded {} HL trade collection subscriptions from config", count);
            }
        } catch (IOException e) {
            LOG.error("Failed to load HL trade collection config: {}", e.getMessage());
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            var arr = mapper.createArrayNode();
            for (CoinSubscription sub : subscriptions.values()) {
                var node = mapper.createObjectNode();
                node.put("coin", sub.coin());
                node.put("exchange", sub.exchange());
                node.put("symbol", sub.symbol());
                arr.add(node);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE.toFile(), arr);
        } catch (IOException e) {
            LOG.error("Failed to save HL trade collection config: {}", e.getMessage());
        }
    }

    private class HlTradesWebSocketClient extends WebSocketClient {

        HlTradesWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            LOG.info("Hyperliquid trades WebSocket connected");
            onConnected();
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            LOG.info("Hyperliquid trades WebSocket closed: {} (code {})", reason, code);
            connected = false;
            activeWsSubscriptions.clear();
            if (shouldReconnect) {
                scheduleReconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            LOG.error("Hyperliquid trades WebSocket error: {}", ex.getMessage());
        }
    }
}
