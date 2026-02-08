package com.tradery.news.source;

import com.tradery.news.ui.coin.*;

import java.time.Duration;
import java.util.*;

/**
 * Data source that fetches coin entities from CoinGecko API.
 * Produces coins, L2s, ETFs, ETPs, and exchange entities plus their relationships.
 */
public class CoinGeckoSource implements DataSource {

    private static final Duration CACHE_TTL = Duration.ofHours(6);

    @Override
    public String id() { return "coingecko"; }

    @Override
    public String name() { return "CoinGecko"; }

    @Override
    public List<String> producedEntityTypes() {
        return List.of("coin", "l2", "etf", "etp", "exchange");
    }

    @Override
    public List<String> producedRelationshipTypes() {
        return List.of("l2_of", "etf_tracks", "ecosystem", "fork_of");
    }

    @Override
    public Duration cacheTTL() { return CACHE_TTL; }

    @Override
    public FetchResult fetch(FetchContext ctx) {
        EntityStore store = ctx.entityStore();
        ProgressCallback progress = ctx.progress();

        try {
            CoinGeckoClient client = new CoinGeckoClient();
            boolean fromCache = false;
            List<CoinEntity> entities;

            // Try cache first
            if (store.isSourceCacheValid("coingecko", CACHE_TTL)) {
                progress.update("Loading from cache...", 10);
                entities = store.loadEntitiesBySource("coingecko");
                fromCache = !entities.isEmpty();
            } else {
                entities = null;
            }

            if (entities == null || entities.isEmpty()) {
                progress.update("Fetching from CoinGecko...", 20);
                List<CoinEntity> cgEntities = client.fetchTopCoins(200);
                store.replaceEntitiesBySource("coingecko", cgEntities);
                entities = new ArrayList<>(cgEntities);
            }

            // Load manual entities for relationship building
            progress.update("Loading manual entities...", 50);
            List<CoinEntity> manualEntities = store.loadEntitiesBySource("manual");
            List<CoinEntity> allEntities = new ArrayList<>(entities);
            allEntities.addAll(manualEntities);

            // Build and save relationships
            progress.update("Building relationships...", 60);
            List<CoinRelationship> autoRels = client.buildRelationships(allEntities);
            store.replaceRelationshipsBySource("auto", autoRels);

            // Seed default manual entities if none exist
            if (manualEntities.isEmpty()) {
                progress.update("Seeding defaults...", 70);
                seedDefaultManualEntities(store);
            }

            int entityCount = entities.size();
            int relCount = autoRels.size();

            // Background category enrichment (only on fresh fetch)
            if (!fromCache) {
                fetchCategories(client, entities, store, progress);
            }

            progress.update("Done", 100);
            return new FetchResult(entityCount, relCount,
                "Loaded " + entityCount + " entities, " + relCount + " relationships");

        } catch (Exception e) {
            return new FetchResult(0, 0, "Error: " + e.getMessage());
        }
    }

    private void fetchCategories(CoinGeckoClient client, List<CoinEntity> entities,
                                  EntityStore store, ProgressCallback progress) {
        List<String> cgIds = entities.stream()
            .filter(e -> e.type() == CoinEntity.Type.COIN)
            .map(CoinEntity::id).toList();
        int total = cgIds.size();
        int count = 0;

        for (String coinId : cgIds) {
            count++;
            int pct = 70 + (count * 30) / total;
            progress.update("Categories: " + count + "/" + total, pct);

            try {
                Map<String, List<String>> catMap = client.fetchCoinCategories(List.of(coinId));
                List<String> cats = catMap.get(coinId);
                if (cats != null && !cats.isEmpty()) {
                    for (CoinEntity entity : entities) {
                        if (entity.id().equals(coinId)) {
                            entity.setCategories(cats);
                            store.saveEntity(entity, "coingecko");
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void seedDefaultManualEntities(EntityStore store) {
        // ETFs
        saveManual(store, createETF("ibit", "iShares Bitcoin Trust", "IBIT"));
        saveManual(store, createETF("fbtc", "Fidelity Wise Origin Bitcoin", "FBTC"));
        saveManual(store, createETF("gbtc", "Grayscale Bitcoin Trust", "GBTC"));
        saveManual(store, createETF("etha", "iShares Ethereum Trust", "ETHA"));

        Set<String> existingIds = new HashSet<>();
        for (CoinEntity e : store.loadAllEntities()) existingIds.add(e.id());

        if (existingIds.contains("bitcoin")) {
            for (String etf : List.of("ibit", "fbtc", "gbtc"))
                saveManualRel(store, new CoinRelationship(etf, "bitcoin", CoinRelationship.Type.ETF_TRACKS));
        }
        if (existingIds.contains("ethereum")) {
            saveManualRel(store, new CoinRelationship("etha", "ethereum", CoinRelationship.Type.ETF_TRACKS));
        }

        // VCs
        saveManual(store, createVC("a16z", "Andreessen Horowitz"));
        saveManual(store, createVC("paradigm", "Paradigm"));
        saveManual(store, createVC("multicoin", "Multicoin Capital"));

        seedInvestments(store, existingIds, "a16z", "solana", "ethereum", "optimism", "uniswap");
        seedInvestments(store, existingIds, "paradigm", "ethereum", "optimism", "uniswap");
        seedInvestments(store, existingIds, "multicoin", "solana", "helium");

        // Exchanges
        saveManual(store, createExchange("binance-ex", "Binance"));
        saveManual(store, createExchange("coinbase-ex", "Coinbase"));

        if (existingIds.contains("binancecoin"))
            saveManualRel(store, new CoinRelationship("binance-ex", "binancecoin", CoinRelationship.Type.FOUNDED_BY));
    }

    private void seedInvestments(EntityStore store, Set<String> existingIds, String vcId, String... coinIds) {
        for (String coinId : coinIds) {
            if (existingIds.contains(coinId))
                saveManualRel(store, new CoinRelationship(vcId, coinId, CoinRelationship.Type.INVESTED_IN));
        }
    }

    private void saveManual(EntityStore store, CoinEntity entity) {
        store.saveEntity(entity, "manual");
    }

    private void saveManualRel(EntityStore store, CoinRelationship rel) {
        store.saveRelationship(rel, "manual");
    }

    private CoinEntity createETF(String id, String name, String symbol) {
        return new CoinEntity(id, name, symbol, CoinEntity.Type.ETF);
    }

    private CoinEntity createVC(String id, String name) {
        return new CoinEntity(id, name, null, CoinEntity.Type.VC);
    }

    private CoinEntity createExchange(String id, String name) {
        return new CoinEntity(id, name, null, CoinEntity.Type.EXCHANGE);
    }
}
