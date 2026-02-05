package com.tradery.news.ui.coin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Client for fetching coin data from CoinGecko API.
 */
public class CoinGeckoClient {

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final OkHttpClient client;

    // Known L2s and their parent chains
    private static final Map<String, String> L2_PARENTS = Map.ofEntries(
        Map.entry("arbitrum", "ethereum"),
        Map.entry("optimism", "ethereum"),
        Map.entry("polygon-pos", "ethereum"),
        Map.entry("base", "ethereum"),
        Map.entry("zksync", "ethereum"),
        Map.entry("starknet", "ethereum"),
        Map.entry("linea", "ethereum"),
        Map.entry("scroll", "ethereum"),
        Map.entry("manta-network", "ethereum"),
        Map.entry("mantle", "ethereum"),
        Map.entry("blast", "ethereum"),
        Map.entry("mode", "ethereum"),
        Map.entry("immutable-x", "ethereum"),
        Map.entry("loopring", "ethereum"),
        Map.entry("metis", "ethereum"),
        Map.entry("boba-network", "ethereum"),
        Map.entry("lightning-network", "bitcoin")
    );

    // Known categories for classification
    private static final Set<String> ETF_KEYWORDS = Set.of("etf", "trust", "fund", "grayscale");
    private static final Set<String> EXCHANGE_TOKENS = Set.of(
        "binancecoin", "crypto-com-chain", "okb", "kucoin-shares",
        "ftx-token", "huobi-token", "gate-token", "mx-token"
    );

    public CoinGeckoClient() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Fetch top coins by market cap.
     */
    public List<CoinEntity> fetchTopCoins(int limit) throws IOException {
        List<CoinEntity> entities = new ArrayList<>();
        int perPage = Math.min(limit, 250);  // CoinGecko max per page
        int pages = (limit + perPage - 1) / perPage;

        for (int page = 1; page <= pages && entities.size() < limit; page++) {
            String url = String.format(
                "%s/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=%d&page=%d&sparkline=false",
                BASE_URL, perPage, page
            );

            Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("CoinGecko API error: " + response.code());
                }

                String json = response.body().string();
                JsonNode coins = mapper.readTree(json);

                for (JsonNode coin : coins) {
                    if (entities.size() >= limit) break;

                    String id = coin.path("id").asText();
                    String name = coin.path("name").asText();
                    String symbol = coin.path("symbol").asText().toUpperCase();
                    double marketCap = coin.path("market_cap").asDouble(0);

                    CoinEntity.Type type = determineType(id, name);
                    String parentId = L2_PARENTS.get(id);

                    CoinEntity entity;
                    if (parentId != null) {
                        entity = new CoinEntity(id, name, symbol, CoinEntity.Type.L2, parentId);
                    } else {
                        entity = new CoinEntity(id, name, symbol, type);
                    }
                    entity.setMarketCap(marketCap);
                    entities.add(entity);
                }
            }

            // Rate limiting - CoinGecko free tier is 10-30 calls/min
            if (page < pages) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return entities;
    }

    /**
     * Fetch coin categories from CoinGecko.
     */
    public Map<String, List<String>> fetchCoinCategories(List<String> coinIds) throws IOException {
        Map<String, List<String>> categoryMap = new HashMap<>();

        for (String coinId : coinIds) {
            try {
                String url = String.format("%s/coins/%s?localization=false&tickers=false&market_data=false&community_data=false&developer_data=false", BASE_URL, coinId);

                Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String json = response.body().string();
                        JsonNode data = mapper.readTree(json);
                        JsonNode categories = data.path("categories");

                        List<String> catList = new ArrayList<>();
                        if (categories.isArray()) {
                            for (JsonNode cat : categories) {
                                catList.add(cat.asText());
                            }
                        }
                        categoryMap.put(coinId, catList);
                    }
                }

                // Rate limiting
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Skip failed coins
            }
        }

        return categoryMap;
    }

    /**
     * Build relationships based on categories and known data.
     */
    public List<CoinRelationship> buildRelationships(List<CoinEntity> entities) {
        List<CoinRelationship> relationships = new ArrayList<>();
        Map<String, CoinEntity> entityMap = new HashMap<>();
        for (CoinEntity e : entities) {
            entityMap.put(e.id(), e);
        }

        // L2 relationships
        for (CoinEntity entity : entities) {
            if (entity.parentId() != null && entityMap.containsKey(entity.parentId())) {
                relationships.add(new CoinRelationship(entity.id(), entity.parentId(), CoinRelationship.Type.L2_OF));
            }
        }

        // Ecosystem relationships (stablecoins, oracles, etc.)
        addEcosystemRelationships(entities, entityMap, relationships);

        return relationships;
    }

    private void addEcosystemRelationships(List<CoinEntity> entities, Map<String, CoinEntity> entityMap, List<CoinRelationship> relationships) {
        // Chainlink ecosystem
        if (entityMap.containsKey("chainlink")) {
            for (String chain : List.of("ethereum", "polygon-pos", "avalanche-2", "arbitrum", "optimism", "solana")) {
                if (entityMap.containsKey(chain)) {
                    relationships.add(new CoinRelationship("chainlink", chain, CoinRelationship.Type.ECOSYSTEM));
                }
            }
        }

        // Major stablecoins on Ethereum
        for (String stablecoin : List.of("tether", "usd-coin", "dai", "frax")) {
            if (entityMap.containsKey(stablecoin) && entityMap.containsKey("ethereum")) {
                relationships.add(new CoinRelationship(stablecoin, "ethereum", CoinRelationship.Type.ECOSYSTEM));
            }
        }

        // Cosmos ecosystem
        if (entityMap.containsKey("cosmos")) {
            for (String cosmosChain : List.of("osmosis", "injective-protocol", "celestia", "dymension", "sei-network", "kava")) {
                if (entityMap.containsKey(cosmosChain)) {
                    relationships.add(new CoinRelationship(cosmosChain, "cosmos", CoinRelationship.Type.ECOSYSTEM));
                }
            }
        }

        // Polkadot parachains
        if (entityMap.containsKey("polkadot")) {
            for (String parachain : List.of("moonbeam", "acala", "astar", "phala-network")) {
                if (entityMap.containsKey(parachain)) {
                    relationships.add(new CoinRelationship(parachain, "polkadot", CoinRelationship.Type.ECOSYSTEM));
                }
            }
        }

        // Forks
        if (entityMap.containsKey("bitcoin")) {
            for (String fork : List.of("bitcoin-cash", "litecoin", "bitcoin-sv")) {
                if (entityMap.containsKey(fork)) {
                    relationships.add(new CoinRelationship(fork, "bitcoin", CoinRelationship.Type.FORK_OF));
                }
            }
        }
        if (entityMap.containsKey("ethereum")) {
            for (String fork : List.of("ethereum-classic")) {
                if (entityMap.containsKey(fork)) {
                    relationships.add(new CoinRelationship(fork, "ethereum", CoinRelationship.Type.FORK_OF));
                }
            }
        }
    }

    private CoinEntity.Type determineType(String id, String name) {
        String lower = name.toLowerCase();

        if (EXCHANGE_TOKENS.contains(id)) {
            return CoinEntity.Type.EXCHANGE;
        }
        if (L2_PARENTS.containsKey(id)) {
            return CoinEntity.Type.L2;
        }
        for (String keyword : ETF_KEYWORDS) {
            if (lower.contains(keyword)) {
                return CoinEntity.Type.ETF;
            }
        }
        return CoinEntity.Type.COIN;
    }
}
