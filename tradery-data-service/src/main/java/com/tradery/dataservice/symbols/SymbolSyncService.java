package com.tradery.dataservice.symbols;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.dataservice.coingecko.CoinGeckoClient;
import com.tradery.dataservice.coingecko.CoinInfo;
import com.tradery.dataservice.data.HttpClientFactory;
import com.tradery.dataservice.data.sqlite.SymbolsConnection;
import com.tradery.dataservice.data.sqlite.dao.SymbolDao;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for syncing exchange symbols from CoinGecko.
 * Fetches both spot and perp markets for each exchange.
 */
public class SymbolSyncService {

    private static final Logger log = LoggerFactory.getLogger(SymbolSyncService.class);

    // CoinGecko exchange IDs for spot and perp markets
    private static final Map<String, ExchangeMapping> EXCHANGE_MAPPINGS = Map.of(
        "binance", new ExchangeMapping("binance", "binance_futures"),
        "bybit", new ExchangeMapping("bybit_spot", "bybit"),
        "okx", new ExchangeMapping("okex", "okx_swap"),
        "coinbase", new ExchangeMapping("gdax", null), // No perp
        "kraken", new ExchangeMapping("kraken", "kraken_futures")
    );

    // Default quote currencies to look for
    private static final Set<String> COMMON_QUOTES = Set.of("USDT", "USDC", "USD", "BUSD", "BTC", "ETH");

    private final CoinGeckoClient coingeckoClient;
    private final SymbolsConnection connection;
    private final SymbolDao symbolDao;
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    // Cache coins list for ID lookups
    private Map<String, String> symbolToCoingeckoId = new HashMap<>();
    private Instant coinsListLastUpdated = null;
    private static final Duration COINS_LIST_CACHE_DURATION = Duration.ofHours(24);

    public SymbolSyncService(CoinGeckoClient coingeckoClient, SymbolsConnection connection) {
        this.coingeckoClient = coingeckoClient;
        this.connection = connection;
        this.symbolDao = new SymbolDao(connection);
    }

    /**
     * Sync all enabled exchanges (both spot and perp).
     *
     * @return Summary of sync results
     */
    public SyncResult syncAll() throws IOException, SQLException {
        if (!syncing.compareAndSet(false, true)) {
            throw new IllegalStateException("Sync already in progress");
        }

        try {
            log.info("Starting full symbol sync...");
            Instant start = Instant.now();

            // Refresh coins list if stale
            refreshCoinsListIfNeeded();

            List<ExchangeSyncResult> results = new ArrayList<>();

            // Sync each exchange
            for (Map.Entry<String, ExchangeMapping> entry : EXCHANGE_MAPPINGS.entrySet()) {
                String exchange = entry.getKey();
                ExchangeMapping mapping = entry.getValue();

                // Sync spot market
                if (mapping.spotId() != null) {
                    try {
                        ExchangeSyncResult result = syncExchange(exchange, MarketType.SPOT);
                        results.add(result);
                    } catch (Exception e) {
                        log.error("Failed to sync {} SPOT: {}", exchange, e.getMessage());
                        results.add(new ExchangeSyncResult(exchange, MarketType.SPOT, 0, "ERROR", e.getMessage()));
                    }
                }

                // Sync perp market
                if (mapping.perpId() != null) {
                    try {
                        ExchangeSyncResult result = syncExchange(exchange, MarketType.PERP);
                        results.add(result);
                    } catch (Exception e) {
                        log.error("Failed to sync {} PERP: {}", exchange, e.getMessage());
                        results.add(new ExchangeSyncResult(exchange, MarketType.PERP, 0, "ERROR", e.getMessage()));
                    }
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            int totalPairs = results.stream().mapToInt(ExchangeSyncResult::pairCount).sum();
            log.info("Symbol sync complete: {} pairs across {} exchanges in {}s",
                totalPairs, results.size(), elapsed.getSeconds());

            return new SyncResult(results, elapsed);
        } finally {
            syncing.set(false);
        }
    }

    /**
     * Sync a single exchange and market type.
     */
    public ExchangeSyncResult syncExchange(String exchange, MarketType marketType) throws IOException, SQLException {
        ExchangeMapping mapping = EXCHANGE_MAPPINGS.get(exchange);
        if (mapping == null) {
            throw new IllegalArgumentException("Unknown exchange: " + exchange);
        }

        String coingeckoId = marketType == MarketType.SPOT ? mapping.spotId() : mapping.perpId();
        if (coingeckoId == null) {
            return new ExchangeSyncResult(exchange, marketType, 0, "SKIPPED", "No CoinGecko ID for this market type");
        }

        log.info("Syncing {} {} (CoinGecko ID: {})...", exchange, marketType, coingeckoId);

        // Fetch tickers from CoinGecko
        List<JsonNode> tickers = coingeckoClient.fetchExchangeTickers(coingeckoId);

        // Parse tickers into trading pairs
        List<TradingPair> pairs = parseTickers(exchange, marketType, tickers);

        // Fallback: if CoinGecko returned nothing for perp, try exchange API directly
        if (pairs.isEmpty() && marketType == MarketType.PERP) {
            log.info("CoinGecko returned 0 perp tickers for {}, falling back to exchange API...", exchange);
            pairs = fetchPerpSymbolsFromExchangeApi(exchange);
        }

        // Upsert pairs
        int count = symbolDao.upsertPairsBatch(pairs);

        // Update sync metadata
        symbolDao.updateSyncMetadata(exchange, marketType, count, "SUCCESS", null);

        log.info("Synced {} pairs for {} {}", count, exchange, marketType);
        return new ExchangeSyncResult(exchange, marketType, count, "SUCCESS", null);
    }

    /**
     * Parse CoinGecko tickers into TradingPair objects.
     */
    private List<TradingPair> parseTickers(String exchange, MarketType marketType, List<JsonNode> tickers) {
        List<TradingPair> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (JsonNode ticker : tickers) {
            try {
                String base = ticker.get("base").asText();
                String target = ticker.get("target").asText();

                // Skip non-USD-like quote currencies for perps
                if (marketType == MarketType.PERP && !isCommonQuote(target)) {
                    continue;
                }

                // Build the full symbol
                String symbol = buildSymbol(exchange, marketType, base, target);

                // Skip duplicates
                String key = exchange + ":" + marketType.getValue() + ":" + symbol;
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);

                // Get CoinGecko IDs
                JsonNode coinIdNode = ticker.get("coin_id");
                String coingeckoBaseId = coinIdNode != null && !coinIdNode.isNull()
                    ? coinIdNode.asText()
                    : lookupCoingeckoId(base);

                JsonNode targetCoinIdNode = ticker.get("target_coin_id");
                String coingeckoQuoteId = targetCoinIdNode != null && !targetCoinIdNode.isNull()
                    ? targetCoinIdNode.asText()
                    : lookupCoingeckoId(target);

                TradingPair pair = TradingPair.create(
                    exchange,
                    marketType,
                    symbol,
                    base,
                    target,
                    coingeckoBaseId,
                    coingeckoQuoteId
                );

                pairs.add(pair);
            } catch (Exception e) {
                log.debug("Failed to parse ticker: {}", e.getMessage());
            }
        }

        return pairs;
    }

    /**
     * Build the full symbol for an exchange.
     * Different exchanges have different naming conventions.
     */
    private String buildSymbol(String exchange, MarketType marketType, String base, String quote) {
        return switch (exchange) {
            case "okx" -> {
                if (marketType == MarketType.PERP) {
                    yield base + "-" + quote + "-SWAP";
                } else {
                    yield base + "-" + quote;
                }
            }
            case "bybit" -> {
                if (marketType == MarketType.PERP) {
                    yield base + quote; // BTCUSDT
                } else {
                    yield base + quote;
                }
            }
            case "kraken" -> {
                // Kraken uses XBT for Bitcoin
                String adjustedBase = "BTC".equals(base) ? "XBT" : base;
                if (marketType == MarketType.PERP) {
                    yield "PF_" + adjustedBase + quote;
                } else {
                    yield adjustedBase + quote;
                }
            }
            default -> base + quote; // Binance, Coinbase, etc.
        };
    }

    /**
     * Check if a quote currency is a common one.
     */
    private boolean isCommonQuote(String quote) {
        return COMMON_QUOTES.contains(quote.toUpperCase());
    }

    /**
     * Look up CoinGecko ID from our cached coins list.
     */
    private String lookupCoingeckoId(String symbol) {
        return symbolToCoingeckoId.get(symbol.toLowerCase());
    }

    /**
     * Refresh the coins list cache if stale.
     */
    private void refreshCoinsListIfNeeded() throws IOException, SQLException {
        if (coinsListLastUpdated != null &&
            Instant.now().isBefore(coinsListLastUpdated.plus(COINS_LIST_CACHE_DURATION))) {
            // Try to load from database
            int count = symbolDao.countCoins();
            if (count > 0) {
                loadCoinsFromDatabase();
                return;
            }
        }

        refreshCoinsList();
    }

    /**
     * Refresh the coins list from CoinGecko.
     */
    public void refreshCoinsList() throws IOException, SQLException {
        log.info("Fetching coins list from CoinGecko...");
        List<CoinInfo> coins = coingeckoClient.fetchCoinsList();

        // Store in database
        symbolDao.upsertCoinsBatch(coins);

        // Build lookup map
        symbolToCoingeckoId.clear();
        for (CoinInfo coin : coins) {
            // Map symbol to ID (prefer first/most common)
            symbolToCoingeckoId.putIfAbsent(coin.symbol().toLowerCase(), coin.id());
        }

        coinsListLastUpdated = Instant.now();
        log.info("Cached {} coins from CoinGecko", coins.size());
    }

    /**
     * Load coins from database into memory cache.
     */
    private void loadCoinsFromDatabase() {
        // This is a lazy implementation - in production you'd query the database
        // For now, we just mark it as loaded
        coinsListLastUpdated = Instant.now();
        log.debug("Coins cache marked as loaded from database");
    }

    /**
     * Check if sync is needed based on last sync time.
     */
    public boolean isSyncNeeded(Duration maxAge) throws SQLException {
        for (String exchange : EXCHANGE_MAPPINGS.keySet()) {
            Optional<Instant> lastSync = symbolDao.getLastSyncTime(exchange, MarketType.PERP);
            if (lastSync.isEmpty() || Instant.now().isAfter(lastSync.get().plus(maxAge))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if currently syncing.
     */
    public boolean isSyncing() {
        return syncing.get();
    }

    /**
     * Get supported exchanges.
     */
    public Set<String> getSupportedExchanges() {
        return EXCHANGE_MAPPINGS.keySet();
    }

    // ========== Direct Exchange API Fallback ==========

    private static final Map<String, String> EXCHANGE_INFO_URLS = Map.of(
        "binance", "https://fapi.binance.com/fapi/v1/exchangeInfo",
        "bybit", "https://api.bybit.com/v5/market/instruments-info?category=linear&limit=1000"
    );

    /**
     * Fetch perpetual symbols directly from exchange API when CoinGecko fails.
     */
    private List<TradingPair> fetchPerpSymbolsFromExchangeApi(String exchange) {
        String url = EXCHANGE_INFO_URLS.get(exchange);
        if (url == null) {
            log.debug("No direct API fallback for exchange: {}", exchange);
            return List.of();
        }

        try {
            OkHttpClient client = HttpClientFactory.getClient();
            ObjectMapper mapper = HttpClientFactory.getMapper();

            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Exchange API returned {}: {}", response.code(), url);
                    return List.of();
                }

                JsonNode root = mapper.readTree(response.body().string());
                return switch (exchange) {
                    case "binance" -> parseBinanceFuturesExchangeInfo(root);
                    case "bybit" -> parseBybitLinearInstruments(root);
                    default -> List.of();
                };
            }
        } catch (Exception e) {
            log.error("Failed to fetch perp symbols from {} API: {}", exchange, e.getMessage());
            return List.of();
        }
    }

    private List<TradingPair> parseBinanceFuturesExchangeInfo(JsonNode root) {
        List<TradingPair> pairs = new ArrayList<>();
        JsonNode symbols = root.get("symbols");
        if (symbols == null) return pairs;

        for (JsonNode sym : symbols) {
            String status = sym.has("status") ? sym.get("status").asText() : "";
            String contractType = sym.has("contractType") ? sym.get("contractType").asText() : "";
            if (!"TRADING".equals(status) || !"PERPETUAL".equals(contractType)) continue;

            String symbol = sym.get("symbol").asText();
            String base = sym.get("baseAsset").asText();
            String quote = sym.get("quoteAsset").asText();
            if (!isCommonQuote(quote)) continue;

            String coingeckoId = lookupCoingeckoId(base);
            String quoteCoingeckoId = lookupCoingeckoId(quote);

            pairs.add(TradingPair.create("binance", MarketType.PERP, symbol, base, quote,
                coingeckoId, quoteCoingeckoId));
        }

        log.info("Parsed {} Binance perpetual pairs from exchangeInfo", pairs.size());
        return pairs;
    }

    private List<TradingPair> parseBybitLinearInstruments(JsonNode root) {
        List<TradingPair> pairs = new ArrayList<>();
        JsonNode result = root.get("result");
        if (result == null) return pairs;
        JsonNode list = result.get("list");
        if (list == null) return pairs;

        for (JsonNode inst : list) {
            String status = inst.has("status") ? inst.get("status").asText() : "";
            if (!"Trading".equals(status)) continue;

            String symbol = inst.get("symbol").asText();
            String base = inst.get("baseCoin").asText();
            String quote = inst.get("quoteCoin").asText();
            if (!isCommonQuote(quote)) continue;

            String coingeckoId = lookupCoingeckoId(base);
            String quoteCoingeckoId = lookupCoingeckoId(quote);

            pairs.add(TradingPair.create("bybit", MarketType.PERP, symbol, base, quote,
                coingeckoId, quoteCoingeckoId));
        }

        log.info("Parsed {} Bybit linear perpetual pairs from API", pairs.size());
        return pairs;
    }

    /**
     * Mapping of exchange to CoinGecko IDs.
     */
    private record ExchangeMapping(String spotId, String perpId) {}

    /**
     * Result of syncing a single exchange/market type.
     */
    public record ExchangeSyncResult(
        String exchange,
        MarketType marketType,
        int pairCount,
        String status,
        String errorMessage
    ) {}

    /**
     * Result of a full sync operation.
     */
    public record SyncResult(
        List<ExchangeSyncResult> exchangeResults,
        Duration elapsed
    ) {
        public int totalPairs() {
            return exchangeResults.stream().mapToInt(ExchangeSyncResult::pairCount).sum();
        }

        public int successCount() {
            return (int) exchangeResults.stream().filter(r -> "SUCCESS".equals(r.status())).count();
        }

        public int errorCount() {
            return (int) exchangeResults.stream().filter(r -> "ERROR".equals(r.status())).count();
        }
    }
}
