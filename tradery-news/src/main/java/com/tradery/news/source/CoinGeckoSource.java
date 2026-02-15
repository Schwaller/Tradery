package com.tradery.news.source;

import com.tradery.news.ui.IntelConfig;
import com.tradery.news.ui.coin.AttributeValue;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.CoinGeckoClient;
import com.tradery.news.ui.coin.CoinRelationship;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaAttribute;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;

import java.awt.Color;
import java.time.Duration;
import java.util.*;

/**
 * Data source that fetches coin entities from CoinGecko API.
 * Produces coins, L2s, ETFs, ETPs, and exchange entities plus their relationships.
 */
public class CoinGeckoSource implements DataSource {

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
    public Duration cacheTTL() {
        return Duration.ofHours(IntelConfig.get().getCoinGeckoCacheHours());
    }

    @Override
    public void seedSchemaTypes(SchemaRegistry registry) {
        int order = 0;

        // Entity types
        seedEntityType(registry, "coin", "Coin", new Color(100, 180, 255), order++, true);
        seedEntityType(registry, "l2", "L2", new Color(150, 130, 255), order++, true);
        seedEntityType(registry, "etf", "Etf", new Color(80, 200, 120), order++, true);
        seedEntityType(registry, "etp", "Etp", new Color(100, 200, 150), order++, true);
        seedEntityType(registry, "dat", "Dat", new Color(180, 180, 120), order++, true);
        seedEntityType(registry, "exchange", "Exchange", new Color(200, 160, 100), order++, false);
        seedEntityType(registry, "news_source", "News Source", new Color(200, 180, 140), order++, false);

        // Relationship types
        order = 0;

        if (registry.getType("l2_of") == null) {
            SchemaType st = new SchemaType("l2_of", "L2 Of", new Color(150, 130, 255), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("L2 of"); st.setFromTypeId("l2"); st.setToTypeId("coin");
            st.setInverseLabel("L1 for"); st.setPluralLabel("L1"); st.setInversePluralLabel("L2s");
            st.setSearchDescription("The L1 blockchain that %s is built on");
            st.setInverseSearchDescription("Layer 2 networks built on %s");
            st.setSearchHints(List.of("%s Layer 1 blockchain built on"));
            st.setInverseSearchHints(List.of("%s Layer 2 networks rollups", "%s L2 scaling solutions"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("etf_tracks") == null) {
            SchemaType st = new SchemaType("etf_tracks", "Etf Tracks", new Color(80, 200, 120), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("tracks"); st.setFromTypeId("etf"); st.setToTypeId("coin");
            st.setInverseLabel("tracked by"); st.setPluralLabel("Tracks"); st.setInversePluralLabel("ETFs");
            st.setSearchDescription("Cryptocurrencies that %s tracks");
            st.setInverseSearchDescription("ETFs (Exchange-Traded Funds) that track %s");
            st.setSearchHints(List.of("%s ETF holdings cryptocurrency"));
            st.setInverseSearchHints(List.of("%s cryptocurrency ETF list spot", "%s ETF approved SEC"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("etp_tracks") == null) {
            SchemaType st = new SchemaType("etp_tracks", "Etp Tracks", new Color(100, 200, 150), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("tracks"); st.setFromTypeId("etp"); st.setToTypeId("coin");
            st.setInverseLabel("tracked by"); st.setPluralLabel("Tracks"); st.setInversePluralLabel("ETPs");
            st.setSearchDescription("Cryptocurrencies that %s tracks");
            st.setInverseSearchDescription("ETPs (Exchange-Traded Products) that track %s");
            st.setSearchHints(List.of("%s ETP holdings"));
            st.setInverseSearchHints(List.of("%s cryptocurrency ETP exchange traded product", "%s ETP Europe"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("ecosystem") == null) {
            SchemaType st = new SchemaType("ecosystem", "Ecosystem", new Color(180, 180, 150), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("ecosystem"); st.setFromTypeId("coin"); st.setToTypeId("coin");
            st.setInverseLabel("ecosystem:"); st.setPluralLabel("Ecosystem"); st.setInversePluralLabel("Ecosystem");
            st.setSearchDescription("Projects and tokens in the %s ecosystem");
            st.setInverseSearchDescription("Projects and tokens in the %s ecosystem");
            st.setSearchHints(List.of("%s ecosystem projects tokens DeFi", "%s blockchain ecosystem dApps protocols"));
            st.setInverseSearchHints(List.of("%s ecosystem projects tokens DeFi"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("fork_of") == null) {
            SchemaType st = new SchemaType("fork_of", "Fork Of", new Color(200, 150, 150), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("fork of"); st.setFromTypeId("coin"); st.setToTypeId("coin");
            st.setInverseLabel("forked to"); st.setPluralLabel("Forks"); st.setInversePluralLabel("Forks");
            st.setSearchDescription("Projects that forked from %s or that %s forked from");
            st.setInverseSearchDescription("Projects that forked from %s or that %s forked from");
            st.setSearchHints(List.of("%s fork forked from blockchain", "%s hard fork code fork crypto"));
            st.setInverseSearchHints(List.of("%s fork forked from blockchain"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("hosts_pair") == null) {
            SchemaType st = new SchemaType("hosts_pair", "Hosts Pair", new Color(200, 160, 100), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("hosts"); st.setFromTypeId("exchange"); st.setToTypeId("coin");
            st.setInverseLabel("traded on"); st.setPluralLabel("Pairs"); st.setInversePluralLabel("Exchanges");
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }
    }

    private void seedEntityType(SchemaRegistry registry, String id, String name, Color color, int order, boolean hasMarketCap) {
        if (registry.getType(id) != null) return;
        SchemaType st = new SchemaType(id, name, color, SchemaType.KIND_ENTITY);
        st.setDisplayOrder(order);
        st.setHasMarketCap(hasMarketCap);
        st.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0, null, null, SchemaAttribute.Mutability.SOURCE));
        st.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1, null, null, SchemaAttribute.Mutability.SOURCE));
        if (hasMarketCap) {
            st.addAttribute(new SchemaAttribute("market_cap", SchemaAttribute.CURRENCY, false, 2,
                java.util.Map.of("en", "Market Cap"),
                java.util.Map.of("currencyCode", "USD", "currencySymbol", "$", "symbolPosition", "prefix", "decimalPlaces", 0),
                SchemaAttribute.Mutability.SOURCE));
        }
        registry.save(st);
    }

    @Override
    public FetchResult fetch(FetchContext ctx) {
        EntityStore store = ctx.entityStore();
        ProgressCallback progress = ctx.progress();

        IntelConfig config = IntelConfig.get();
        if (!config.isCoinGeckoEnabled()) {
            progress.update("CoinGecko disabled", 100);
            return new FetchResult(0, 0, "CoinGecko disabled");
        }

        try {
            CoinGeckoClient client = new CoinGeckoClient();
            boolean fromCache = false;
            List<CoinEntity> entities;
            Duration cacheTtl = cacheTTL();

            // Try cache first
            if (store.isSourceCacheValid("coingecko", cacheTtl)) {
                progress.update("Loading from cache...", 10);
                entities = store.loadEntitiesBySource("coingecko");
                fromCache = !entities.isEmpty();
            } else {
                entities = null;
            }

            if (entities == null || entities.isEmpty()) {
                progress.update("Fetching from CoinGecko...", 20);
                long delayMs = config.getCoinGeckoRequestDelayMs();
                List<CoinEntity> cgEntities = client.fetchTopCoins(config.getCoinGeckoLimit(), delayMs);
                store.replaceEntitiesBySource("coingecko", cgEntities);
                entities = new ArrayList<>(cgEntities);

                // Write attribute values with SOURCE origin for provenance tracking
                writeSourceAttributeValues(store, cgEntities);
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

            // Background category enrichment (only on fresh fetch, if enabled)
            if (!fromCache && config.isCoinGeckoFetchCategories()) {
                fetchCategories(client, entities, store, progress, config.getCoinGeckoRequestDelayMs());
            }

            progress.update("Done", 100);
            return new FetchResult(entityCount, relCount,
                "Loaded " + entityCount + " entities, " + relCount + " relationships");

        } catch (Exception e) {
            return new FetchResult(0, 0, "Error: " + e.getMessage());
        }
    }

    private void writeSourceAttributeValues(EntityStore store, List<CoinEntity> entities) {
        AttributeValue.Origin source = AttributeValue.Origin.SOURCE;
        for (CoinEntity entity : entities) {
            String typeId = entity.type().name().toLowerCase();
            if (entity.name() != null) {
                store.saveAttributeValue(entity.id(), typeId, "name", entity.name(), source);
            }
            if (entity.symbol() != null) {
                store.saveAttributeValue(entity.id(), typeId, "symbol", entity.symbol(), source);
            }
            if (entity.marketCap() > 0) {
                store.saveAttributeValue(entity.id(), typeId, "market_cap",
                    String.valueOf((long) entity.marketCap()), source);
            }
        }
    }

    private void fetchCategories(CoinGeckoClient client, List<CoinEntity> entities,
                                  EntityStore store, ProgressCallback progress, long delayMs) {
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
                Map<String, List<String>> catMap = client.fetchCoinCategories(List.of(coinId), delayMs);
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
                saveManualRel(store, new CoinRelationship(etf, "bitcoin", "etf_tracks"));
        }
        if (existingIds.contains("ethereum")) {
            saveManualRel(store, new CoinRelationship("etha", "ethereum", "etf_tracks"));
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
            saveManualRel(store, new CoinRelationship("binance-ex", "binancecoin", "founded_by"));
    }

    private void seedInvestments(EntityStore store, Set<String> existingIds, String vcId, String... coinIds) {
        for (String coinId : coinIds) {
            if (existingIds.contains(coinId))
                saveManualRel(store, new CoinRelationship(vcId, coinId, "invested_in"));
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
