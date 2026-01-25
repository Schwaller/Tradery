package com.tradery.dataservice.api;

import com.tradery.dataservice.coingecko.CoinGeckoClient;
import com.tradery.dataservice.data.sqlite.SymbolsConnection;
import com.tradery.dataservice.data.sqlite.dao.SymbolDao;
import com.tradery.dataservice.symbols.MarketType;
import com.tradery.dataservice.symbols.SymbolSyncService;
import com.tradery.dataservice.symbols.TradingPair;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * HTTP handler for symbol resolution endpoints.
 */
public class SymbolHandler {

    private static final Logger log = LoggerFactory.getLogger(SymbolHandler.class);

    private final SymbolSyncService syncService;
    private final SymbolDao symbolDao;
    private final CoinGeckoClient coingeckoClient;

    public SymbolHandler(SymbolSyncService syncService, SymbolsConnection connection, CoinGeckoClient coingeckoClient) {
        this.syncService = syncService;
        this.symbolDao = new SymbolDao(connection);
        this.coingeckoClient = coingeckoClient;
    }

    /**
     * GET /symbols/resolve
     * Resolve canonical symbol to exchange-specific symbol.
     *
     * Query params:
     * - canonical: Canonical symbol or CoinGecko ID (required)
     * - exchange: Target exchange (required)
     * - market: Market type (spot/perp) - default: perp
     * - quote: Quote currency - default: USDT
     */
    public void resolve(Context ctx) {
        try {
            String canonical = ctx.queryParam("canonical");
            String exchange = ctx.queryParam("exchange");
            String marketParam = ctx.queryParamAsClass("market", String.class).getOrDefault("perp");
            String quote = ctx.queryParamAsClass("quote", String.class).getOrDefault("USDT");

            if (canonical == null || canonical.isBlank()) {
                ctx.status(400).json(new ErrorResponse("canonical parameter is required"));
                return;
            }
            if (exchange == null || exchange.isBlank()) {
                ctx.status(400).json(new ErrorResponse("exchange parameter is required"));
                return;
            }

            MarketType marketType = MarketType.fromString(marketParam);

            Optional<String> symbol = symbolDao.resolvePairSymbol(canonical, exchange.toLowerCase(), marketType, quote.toUpperCase());

            if (symbol.isEmpty()) {
                ctx.status(404).json(new ErrorResponse(
                    "Symbol not found: " + canonical + " on " + exchange + " " + marketType.getValue() + " with quote " + quote
                ));
                return;
            }

            ctx.json(new ResolveResponse(canonical, exchange, marketType.getValue(), quote, symbol.get()));
        } catch (Exception e) {
            log.error("Failed to resolve symbol", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /symbols/reverse
     * Reverse resolve exchange symbol to canonical info.
     *
     * Query params:
     * - symbol: Exchange-specific symbol (required)
     * - exchange: Exchange name (required)
     */
    public void reverse(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String exchange = ctx.queryParam("exchange");

            if (symbol == null || symbol.isBlank()) {
                ctx.status(400).json(new ErrorResponse("symbol parameter is required"));
                return;
            }
            if (exchange == null || exchange.isBlank()) {
                ctx.status(400).json(new ErrorResponse("exchange parameter is required"));
                return;
            }

            Optional<TradingPair> pair = symbolDao.reverseResolve(symbol, exchange.toLowerCase());

            if (pair.isEmpty()) {
                ctx.status(404).json(new ErrorResponse("Symbol not found: " + symbol + " on " + exchange));
                return;
            }

            TradingPair p = pair.get();
            ctx.json(new ReverseResponse(
                symbol,
                exchange,
                p.marketType().getValue(),
                p.baseSymbol(),
                p.quoteSymbol(),
                p.coingeckoBaseId(),
                p.coingeckoQuoteId()
            ));
        } catch (Exception e) {
            log.error("Failed to reverse resolve symbol", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /symbols/search
     * Search for symbols.
     *
     * Query params:
     * - q: Search query (required)
     * - exchange: Optional exchange filter
     * - limit: Max results - default: 50
     */
    public void search(Context ctx) {
        try {
            String query = ctx.queryParam("q");
            String exchange = ctx.queryParam("exchange");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

            if (query == null || query.isBlank()) {
                ctx.status(400).json(new ErrorResponse("q parameter is required"));
                return;
            }

            List<TradingPair> pairs = symbolDao.searchPairs(query, exchange, Math.min(limit, 100));

            List<SearchResult> results = pairs.stream()
                .map(p -> new SearchResult(
                    p.symbol(),
                    p.exchange(),
                    p.marketType().getValue(),
                    p.baseSymbol(),
                    p.quoteSymbol(),
                    p.coingeckoBaseId()
                ))
                .collect(Collectors.toList());

            ctx.json(new SearchResponse(query, results.size(), results));
        } catch (Exception e) {
            log.error("Failed to search symbols", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * POST /symbols/sync
     * Trigger a manual sync of all exchanges.
     */
    public void sync(Context ctx) {
        try {
            if (syncService.isSyncing()) {
                ctx.status(409).json(new ErrorResponse("Sync already in progress"));
                return;
            }

            // Run sync in background
            Thread.ofVirtual().start(() -> {
                try {
                    syncService.syncAll();
                } catch (Exception e) {
                    log.error("Background sync failed", e);
                }
            });

            ctx.status(202).json(Map.of(
                "status", "STARTED",
                "message", "Sync started in background"
            ));
        } catch (Exception e) {
            log.error("Failed to start sync", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /symbols/stats
     * Get coverage statistics.
     */
    public void stats(Context ctx) {
        try {
            int pairCount = symbolDao.countPairs();
            int assetCount = symbolDao.countAssets();
            int coinCount = symbolDao.countCoins();
            List<SymbolDao.ExchangeStats> exchangeStats = symbolDao.getExchangeStats();
            List<SymbolDao.SyncMetadata> syncMetadata = symbolDao.getAllSyncMetadata();

            // Group exchange stats by exchange
            Map<String, List<ExchangeMarketStats>> byExchange = exchangeStats.stream()
                .collect(Collectors.groupingBy(
                    SymbolDao.ExchangeStats::exchange,
                    Collectors.mapping(
                        s -> new ExchangeMarketStats(s.marketType().getValue(), s.pairCount()),
                        Collectors.toList()
                    )
                ));

            // Build sync status
            List<SyncStatus> syncStatuses = syncMetadata.stream()
                .map(m -> new SyncStatus(
                    m.exchange(),
                    m.marketType().getValue(),
                    m.lastSync() != null ? m.lastSync().toString() : null,
                    m.pairCount(),
                    m.status()
                ))
                .collect(Collectors.toList());

            // Circuit breaker status
            var cbStatus = coingeckoClient.getCircuitBreakerStatus();

            ctx.json(new StatsResponse(
                pairCount,
                assetCount,
                coinCount,
                byExchange,
                syncStatuses,
                syncService.isSyncing(),
                new CircuitBreakerInfo(
                    cbStatus.isOpen(),
                    cbStatus.consecutiveFailures(),
                    cbStatus.openedAt() != null ? cbStatus.openedAt().toString() : null,
                    cbStatus.resetsAt() != null ? cbStatus.resetsAt().toString() : null
                )
            ));
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /symbols/exchanges
     * Get list of supported exchanges.
     */
    public void exchanges(Context ctx) {
        ctx.json(new ExchangesResponse(syncService.getSupportedExchanges().stream().sorted().toList()));
    }

    // Response records

    public record ErrorResponse(String error) {}

    public record ResolveResponse(
        String canonical,
        String exchange,
        String marketType,
        String quote,
        String symbol
    ) {}

    public record ReverseResponse(
        String symbol,
        String exchange,
        String marketType,
        String base,
        String quote,
        String coingeckoBaseId,
        String coingeckoQuoteId
    ) {}

    public record SearchResult(
        String symbol,
        String exchange,
        String marketType,
        String base,
        String quote,
        String coingeckoId
    ) {}

    public record SearchResponse(
        String query,
        int count,
        List<SearchResult> results
    ) {}

    public record ExchangeMarketStats(String marketType, int pairCount) {}

    public record SyncStatus(
        String exchange,
        String marketType,
        String lastSync,
        int pairCount,
        String status
    ) {}

    public record CircuitBreakerInfo(
        boolean isOpen,
        int consecutiveFailures,
        String openedAt,
        String resetsAt
    ) {}

    public record StatsResponse(
        int totalPairs,
        int totalAssets,
        int totalCoins,
        Map<String, List<ExchangeMarketStats>> byExchange,
        List<SyncStatus> syncStatus,
        boolean syncInProgress,
        CircuitBreakerInfo circuitBreaker
    ) {}

    public record ExchangesResponse(List<String> exchanges) {}
}
