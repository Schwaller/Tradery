package com.tradery.dataclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.*;
import com.tradery.data.page.DataType;
import com.tradery.dataclient.page.DataServiceConnection;
import okhttp3.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the Tradery Data Service.
 * Provides synchronous methods to request pages and fetch data.
 */
public class DataServiceClient {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceClient.class);

    private final String host;
    private final int port;
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper msgpackMapper;

    // Optional WebSocket connection for push-based data delivery
    private volatile DataServiceConnection connection;

    public DataServiceClient(String host, int port) {
        this.host = host;
        this.port = port;
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
     * Set the WebSocket connection for push-based data delivery.
     * When set, subscribePage() will use WS instead of HTTP polling.
     */
    public void setConnection(DataServiceConnection connection) {
        this.connection = connection;
    }

    /**
     * Get the WebSocket connection, or null if not set.
     */
    public DataServiceConnection getConnection() {
        return connection;
    }

    /**
     * Get the msgpack ObjectMapper for deserializing binary data.
     */
    public ObjectMapper getMsgpackMapper() {
        return msgpackMapper;
    }

    /**
     * Check if a WebSocket connection is active.
     */
    public boolean hasActiveConnection() {
        return connection != null && connection.isConnected();
    }

    // ==================== WS-based Page Subscription ====================

    /**
     * Subscribe to a page via WebSocket. Data is pushed to the callback
     * as binary msgpack frames — no HTTP round-trip needed.
     *
     * Falls back to HTTP polling if no WS connection is available.
     *
     * @param dataType      Data type (CANDLES, FUNDING, etc.)
     * @param symbol        Trading symbol
     * @param timeframe     Timeframe (null for non-timeframe types)
     * @param startTime     Start time in milliseconds
     * @param endTime       End time in milliseconds
     * @param callback      Callback for page lifecycle + data delivery
     * @return A future that completes with the raw msgpack bytes when data arrives
     */
    public CompletableFuture<byte[]> subscribePage(DataType dataType, String symbol, String timeframe,
                                                     long startTime, long endTime,
                                                     DataPageCallback callback) {
        DataServiceConnection conn = this.connection;
        if (conn == null || !conn.isConnected()) {
            // No WS — fall back to synchronous HTTP
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return subscribePageViaHttp(dataType, symbol, timeframe, startTime, endTime, callback);
                } catch (Exception e) {
                    callback.onError("HTTP fallback failed: " + e.getMessage());
                    throw new java.util.concurrent.CompletionException(e);
                }
            });
        }

        // WS path: subscribe to page updates + register data callback
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        conn.subscribePage(dataType, symbol, timeframe, startTime, endTime,
            new DataServiceConnection.PageUpdateCallback() {
                @Override
                public void onStateChanged(String state, int progress) {
                    callback.onStateChanged(state, progress);
                }

                @Override
                public void onDataReady(long recordCount) {
                    // Binary data will arrive via PageDataCallback
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                    future.completeExceptionally(new IOException(message));
                }

                @Override
                public void onEvicted() {
                    callback.onError("Page evicted");
                }

                @Override
                public void onLiveUpdate(Candle candle) {
                    callback.onLiveUpdate(candle);
                }

                @Override
                public void onLiveAppend(Candle candle, List<Long> removedTimestamps) {
                    callback.onLiveAppend(candle, removedTimestamps);
                }
            });

        // Register for binary data push
        String pageKey = makePageKey(dataType, symbol, timeframe, startTime, endTime);
        conn.setPageDataCallback(pageKey, (key, dt, recordCount, msgpackData) -> {
            callback.onData(msgpackData, recordCount);
            future.complete(msgpackData);
            // Remove callback after first data delivery (page data is one-shot for anchored pages)
            conn.removePageDataCallback(key);
        });

        return future;
    }

    /**
     * Unsubscribe from a page.
     */
    public void unsubscribePage(DataType dataType, String symbol, String timeframe,
                                 long startTime, long endTime) {
        DataServiceConnection conn = this.connection;
        if (conn != null) {
            conn.unsubscribePage(dataType, symbol, timeframe, startTime, endTime, null);
            String pageKey = makePageKey(dataType, symbol, timeframe, startTime, endTime);
            conn.removePageDataCallback(pageKey);
        }
    }

    /**
     * HTTP fallback for subscribePage when WS is not available.
     */
    private byte[] subscribePageViaHttp(DataType dataType, String symbol, String timeframe,
                                          long startTime, long endTime,
                                          DataPageCallback callback) throws IOException, InterruptedException {
        callback.onStateChanged("LOADING", 0);

        // Request page
        var response = requestPage(
            new PageRequest(dataType.toWireFormat(), symbol, timeframe, startTime, endTime),
            "http-fallback-" + System.currentTimeMillis(),
            "DataServiceClient"
        );

        // Poll for completion
        String dsPageKey = response.pageKey();
        int lastProgress = 0;

        while (true) {
            var status = getPageStatus(dsPageKey);
            if (status == null) {
                throw new IOException("Page status not found: " + dsPageKey);
            }

            if (status.progress() > lastProgress) {
                lastProgress = status.progress();
                callback.onStateChanged("LOADING", Math.min(lastProgress, 95));
            }

            if ("READY".equals(status.state())) {
                break;
            } else if ("ERROR".equals(status.state())) {
                throw new IOException("Page load error: " + dsPageKey);
            }

            Thread.sleep(100);
        }

        // Fetch data
        byte[] data = getPageData(dsPageKey);
        if (data != null) {
            callback.onData(data, 0);
        }
        return data;
    }

    private String makePageKey(DataType dataType, String symbol, String timeframe, long startTime, long endTime) {
        return new com.tradery.data.page.PageKey(
            dataType.toWireFormat(), "binance", symbol.toUpperCase(), timeframe,
            "perp", endTime, endTime - startTime
        ).toKeyString();
    }

    /**
     * Callback for unified page data delivery.
     * Works for both WS push and HTTP fallback paths.
     */
    public interface DataPageCallback {
        /** Called when page state changes (LOADING, READY, ERROR). */
        void onStateChanged(String state, int progress);

        /** Called when raw msgpack data is received. */
        void onData(byte[] msgpackData, long recordCount);

        /** Called on error. */
        void onError(String message);

        /** Called when an incomplete/forming candle is updated (live pages). */
        default void onLiveUpdate(Candle candle) {}

        /** Called when a new completed candle is appended (live pages). */
        default void onLiveAppend(Candle candle, List<Long> removedTimestamps) {}
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
     * Fetch open interest from a page.
     */
    public List<OpenInterest> getOpenInterest(String pageKey) throws IOException {
        byte[] data = getPageData(pageKey);
        if (data == null) return List.of();
        return msgpackMapper.readValue(data, msgpackMapper.getTypeFactory()
            .constructCollectionType(List.class, OpenInterest.class));
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
