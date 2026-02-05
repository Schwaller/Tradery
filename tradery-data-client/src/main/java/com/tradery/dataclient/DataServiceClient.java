package com.tradery.dataclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.*;
import okhttp3.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the Tradery Data Service.
 * Provides synchronous methods to request pages and fetch data.
 */
public class DataServiceClient {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceClient.class);

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper msgpackMapper;

    public DataServiceClient(String host, int port) {
        this.baseUrl = String.format("http://%s:%d", host, port);
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.jsonMapper = new ObjectMapper();
        // Use default ObjectMapper for MessagePack - records are handled correctly
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory());
    }

    /**
     * Check if the data service is healthy.
     */
    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                .url(baseUrl + "/health")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Request a page from the data service.
     */
    public PageResponse requestPage(PageRequest request, String consumerId, String consumerName) throws IOException {
        String json = jsonMapper.writeValueAsString(new PageRequestBody(
            request.dataType(),
            request.symbol(),
            request.timeframe(),
            request.startTime(),
            request.endTime(),
            consumerId,
            consumerName
        ));

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request httpRequest = new Request.Builder()
            .url(baseUrl + "/pages/request")
            .post(body)
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to request page: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), PageResponse.class);
        }
    }

    /**
     * Release a page.
     */
    public boolean releasePage(String pageKey, String consumerId) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/pages/" + pageKey + "?consumerId=" + consumerId)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    /**
     * Get page status.
     */
    public PageStatus getPageStatus(String pageKey) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/pages/" + pageKey + "/status")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            return jsonMapper.readValue(response.body().string(), PageStatus.class);
        }
    }

    /**
     * Get page data as raw bytes (MessagePack).
     */
    public byte[] getPageData(String pageKey) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/pages/" + pageKey + "/data")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            return response.body().bytes();
        }
    }

    /**
     * Fetch candles from a page.
     */
    public List<Candle> getCandles(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, Candle.class));
    }

    /**
     * Fetch candles directly (without page lifecycle).
     */
    public List<Candle> getCandles(String symbol, String timeframe, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/candles").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("timeframe", timeframe);

        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, Candle.class));
        }
    }

    /**
     * Fetch aggregated trades from a page.
     */
    public List<AggTrade> getAggTrades(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, AggTrade.class));
    }

    /**
     * Fetch aggregated trades directly (without page lifecycle).
     * Streams from response body to avoid allocating a huge intermediate byte[].
     */
    public List<AggTrade> getAggTrades(String symbol, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/aggtrades").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            // Stream from response body - avoids 2GB byte[] allocation for large datasets
            return msgpackMapper.readValue(response.body().byteStream(), msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, AggTrade.class));
        }
    }

    /**
     * Fetch funding rates from a page.
     */
    public List<FundingRate> getFundingRates(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, FundingRate.class));
    }

    /**
     * Fetch funding rates directly (without page lifecycle).
     */
    public List<FundingRate> getFundingRates(String symbol, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/funding").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, FundingRate.class));
        }
    }

    /**
     * Fetch open interest from a page.
     */
    public List<OpenInterest> getOpenInterest(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, OpenInterest.class));
    }

    /**
     * Fetch open interest directly (without page lifecycle).
     */
    public List<OpenInterest> getOpenInterest(String symbol, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/openinterest").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, OpenInterest.class));
        }
    }

    /**
     * Fetch premium index from a page.
     */
    public List<PremiumIndex> getPremiumIndex(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, PremiumIndex.class));
    }

    /**
     * Fetch premium index directly (without page lifecycle).
     */
    public List<PremiumIndex> getPremiumIndex(String symbol, String timeframe, Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/premium").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (timeframe != null) urlBuilder.addQueryParameter("timeframe", timeframe);
        if (start != null) urlBuilder.addQueryParameter("start", start.toString());
        if (end != null) urlBuilder.addQueryParameter("end", end.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return List.of();
            byte[] data = response.body().bytes();
            return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
                .constructCollectionType(List.class, PremiumIndex.class));
        }
    }

    // ==================== Symbol Resolution ====================

    /**
     * Resolve a canonical symbol to exchange-specific symbol.
     *
     * @param canonical Canonical symbol or CoinGecko ID (e.g., "BTC", "bitcoin")
     * @param exchange Target exchange (e.g., "binance", "okx")
     * @param marketType Market type: "spot" or "perp" (default: perp)
     * @param quote Quote currency (default: USDT)
     * @return The exchange-specific symbol, or empty if not found
     */
    public Optional<String> resolveSymbol(String canonical, String exchange, String marketType, String quote)
            throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/symbols/resolve").newBuilder()
            .addQueryParameter("canonical", canonical)
            .addQueryParameter("exchange", exchange);

        if (marketType != null) urlBuilder.addQueryParameter("market", marketType);
        if (quote != null) urlBuilder.addQueryParameter("quote", quote);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                return Optional.empty();
            }
            if (!response.isSuccessful()) {
                throw new IOException("Symbol resolution failed: " + response.code());
            }
            SymbolResolveResponse result = jsonMapper.readValue(response.body().string(), SymbolResolveResponse.class);
            return Optional.of(result.symbol());
        }
    }

    /**
     * Resolve a canonical symbol to exchange-specific symbol (using defaults).
     *
     * @param canonical Canonical symbol or CoinGecko ID
     * @param exchange Target exchange
     * @return The exchange-specific symbol for perp/USDT, or empty if not found
     */
    public Optional<String> resolveSymbol(String canonical, String exchange) throws IOException {
        return resolveSymbol(canonical, exchange, "perp", "USDT");
    }

    /**
     * Reverse resolve an exchange symbol to canonical info.
     *
     * @param exchangeSymbol Exchange-specific symbol (e.g., "BTC-USDT-SWAP")
     * @param exchange Exchange name
     * @return Symbol info with CoinGecko IDs, or empty if not found
     */
    public Optional<SymbolReverseResponse> reverseResolveSymbol(String exchangeSymbol, String exchange)
            throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/symbols/reverse").newBuilder()
            .addQueryParameter("symbol", exchangeSymbol)
            .addQueryParameter("exchange", exchange);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                return Optional.empty();
            }
            if (!response.isSuccessful()) {
                throw new IOException("Reverse resolution failed: " + response.code());
            }
            return Optional.of(jsonMapper.readValue(response.body().string(), SymbolReverseResponse.class));
        }
    }

    /**
     * Search for symbols.
     *
     * @param query Search query
     * @param exchange Optional exchange filter
     * @param limit Max results (default: 50)
     * @return List of matching symbols
     */
    public List<SymbolSearchResult> searchSymbols(String query, String exchange, Integer limit) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/symbols/search").newBuilder()
            .addQueryParameter("q", query);

        if (exchange != null) urlBuilder.addQueryParameter("exchange", exchange);
        if (limit != null) urlBuilder.addQueryParameter("limit", limit.toString());

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Symbol search failed: " + response.code());
            }
            SymbolSearchResponse result = jsonMapper.readValue(response.body().string(), SymbolSearchResponse.class);
            return result.results();
        }
    }

    /**
     * Search for symbols (simple form).
     */
    public List<SymbolSearchResult> searchSymbols(String query) throws IOException {
        return searchSymbols(query, null, null);
    }

    /**
     * Get symbol resolution statistics.
     */
    public SymbolStats getSymbolStats() throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/symbols/stats")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Symbol stats failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), SymbolStats.class);
        }
    }

    /**
     * Trigger a symbol sync.
     */
    public void triggerSymbolSync() throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/symbols/sync")
            .post(RequestBody.create("", MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 409) {
                LOG.info("Symbol sync already in progress");
                return;
            }
            if (!response.isSuccessful()) {
                throw new IOException("Symbol sync failed: " + response.code());
            }
            LOG.info("Symbol sync triggered");
        }
    }

    /**
     * Close the client.
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // Request/Response DTOs
    public record PageRequest(String dataType, String symbol, String timeframe, long startTime, long endTime) {}

    public record PageRequestBody(String dataType, String symbol, String timeframe, long startTime, long endTime,
                                   String consumerId, String consumerName) {}

    public record PageResponse(String pageKey, String state, int progress, boolean isNew) {}

    public record PageStatus(String pageKey, String state, int progress, long recordCount,
                              Long lastSyncTime, List<Consumer> consumers, Coverage coverage) {}

    public record Consumer(String id, String name) {}

    public record Coverage(long requestedStart, long requestedEnd, Long actualStart, Long actualEnd, List<Gap> gaps) {}

    public record Gap(long start, long end) {}

    // Symbol resolution DTOs
    public record SymbolResolveResponse(String canonical, String exchange, String marketType, String quote, String symbol) {}

    public record SymbolReverseResponse(String symbol, String exchange, String marketType, String base, String quote,
                                         String coingeckoBaseId, String coingeckoQuoteId) {}

    public record SymbolSearchResult(String symbol, String exchange, String marketType, String base, String quote,
                                      String coingeckoId) {}

    public record SymbolSearchResponse(String query, int count, List<SymbolSearchResult> results) {}

    public record SymbolStats(int totalPairs, int totalAssets, int totalCoins, boolean syncInProgress) {}
}
