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
 * Client for the Tradery Data Service.
 * All data delivery uses WebSocket push (binary msgpack frames).
 * HTTP is used only for health checks, symbol resolution, and aggTrades cache access.
 */
public class DataServiceClient {
    private static final Logger LOG = LoggerFactory.getLogger(DataServiceClient.class);

    private final String host;
    private final int port;
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper msgpackMapper;

    // WebSocket connection for push-based data delivery
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
     * @param dataType      Data type (CANDLES, FUNDING, etc.)
     * @param symbol        Trading symbol
     * @param timeframe     Timeframe (null for non-timeframe types)
     * @param startTime     Start time in milliseconds
     * @param endTime       End time in milliseconds
     * @param callback      Callback for page lifecycle + data delivery
     * @return A future that completes with the raw msgpack bytes when data arrives
     * @throws IllegalStateException if no WebSocket connection is available
     */
    public CompletableFuture<byte[]> subscribePage(DataType dataType, String symbol, String timeframe,
                                                     long startTime, long endTime,
                                                     DataPageCallback callback) {
        DataServiceConnection conn = this.connection;
        if (conn == null || !conn.isConnected()) {
            CompletableFuture<byte[]> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("No WebSocket connection available"));
            return failed;
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
        conn.setPageDataCallback(pageKey, new DataServiceConnection.PageDataCallback() {
            @Override
            public void onBinaryData(String key, String dt, long recordCount, byte[] msgpackData) {
                // Non-chunked: single frame delivery (candles, funding, OI, premium)
                callback.onData(msgpackData, recordCount);
                future.complete(msgpackData);
                conn.removePageDataCallback(key);
            }

            @Override
            public void onBinaryChunk(String key, String dt, int chunkIndex, int totalChunks,
                                       long chunkRecordCount, byte[] msgpackData) {
                // Chunked: deliver each chunk to caller for immediate deserialization
                callback.onChunk(msgpackData, chunkIndex, totalChunks);
            }

            @Override
            public void onBinaryChunksComplete(String key, String dt, int totalChunks) {
                // All chunks received — signal completion
                callback.onChunksComplete(totalChunks);
                future.complete(null); // null signals chunked delivery (data already delivered via onChunk)
                conn.removePageDataCallback(key);
            }
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

    private String makePageKey(DataType dataType, String symbol, String timeframe, long startTime, long endTime) {
        return new com.tradery.data.page.PageKey(
            dataType.toWireFormat(), "binance", symbol.toUpperCase(), timeframe,
            "perp", endTime, endTime - startTime
        ).toKeyString();
    }

    /**
     * Callback for unified page data delivery via WebSocket.
     */
    public interface DataPageCallback {
        /** Called when page state changes (LOADING, READY, ERROR). */
        void onStateChanged(String state, int progress);

        /** Called when msgpack data is received in a single frame (candles, funding, OI, premium). */
        void onData(byte[] msgpackData, long recordCount);

        /** Called on error. */
        void onError(String message);

        /** Called for each chunk of data as it arrives (aggTrades). Deserialize immediately to avoid OOM. */
        default void onChunk(byte[] msgpackData, int chunkIndex, int totalChunks) {}

        /** Called when all chunks have been received (aggTrades). */
        default void onChunksComplete(int totalChunks) {}

        /** Called when an incomplete/forming candle is updated (live pages). */
        default void onLiveUpdate(Candle candle) {}

        /** Called when a new completed candle is appended (live pages). */
        default void onLiveAppend(Candle candle, List<Long> removedTimestamps) {}
    }

    // ==================== HTTP endpoints (health, symbols, aggTrades cache) ====================

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
     * Fetch aggregated trades directly via HTTP (for cache access).
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

    // ==================== Symbol Resolution ====================

    /**
     * Resolve a canonical symbol to exchange-specific symbol.
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
     */
    public Optional<String> resolveSymbol(String canonical, String exchange) throws IOException {
        return resolveSymbol(canonical, exchange, "perp", "USDT");
    }

    /**
     * Reverse resolve an exchange symbol to canonical info.
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

    // Symbol resolution DTOs
    public record SymbolResolveResponse(String canonical, String exchange, String marketType, String quote, String symbol) {}

    public record SymbolReverseResponse(String symbol, String exchange, String marketType, String base, String quote,
                                         String coingeckoBaseId, String coingeckoQuoteId) {}

    public record SymbolSearchResult(String symbol, String exchange, String marketType, String base, String quote,
                                      String coingeckoId) {}

    public record SymbolSearchResponse(String query, int count, List<SymbolSearchResult> results) {}

    public record SymbolStats(int totalPairs, int totalAssets, int totalCoins, boolean syncInProgress) {}
}
