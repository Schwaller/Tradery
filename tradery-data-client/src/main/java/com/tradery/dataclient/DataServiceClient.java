package com.tradery.dataclient;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
 * Client for the Plaiiin Data Service.
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
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
    /**
     * Subscribe to a page via WebSocket (defaults to binance/perp).
     */
    public CompletableFuture<byte[]> subscribePage(DataType dataType, String symbol, String timeframe,
                                                     long startTime, long endTime,
                                                     DataPageCallback callback) {
        return subscribePage(dataType, symbol, timeframe, null, null, startTime, endTime, callback);
    }

    /**
     * Subscribe to a page via WebSocket with exchange and market type.
     */
    public CompletableFuture<byte[]> subscribePage(DataType dataType, String symbol, String timeframe,
                                                     String exchange, String marketType,
                                                     long startTime, long endTime,
                                                     DataPageCallback callback) {
        DataServiceConnection conn = this.connection;
        if (conn == null || !conn.isConnected()) {
            CompletableFuture<byte[]> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("No WebSocket connection available"));
            return failed;
        }

        String ex = exchange != null ? exchange : "binance";
        String mt = marketType != null ? marketType : "perp";

        // WS path: subscribe to page updates + register data callback
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        conn.subscribePage(dataType, symbol, timeframe, ex, mt, startTime, endTime,
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
        String pageKey = makePageKey(dataType, symbol, timeframe, ex, mt, startTime, endTime);
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
     * Unsubscribe from a page (defaults to binance/perp).
     */
    public void unsubscribePage(DataType dataType, String symbol, String timeframe,
                                 long startTime, long endTime) {
        unsubscribePage(dataType, symbol, timeframe, null, null, startTime, endTime);
    }

    /**
     * Unsubscribe from a page with exchange and market type.
     */
    public void unsubscribePage(DataType dataType, String symbol, String timeframe,
                                 String exchange, String marketType,
                                 long startTime, long endTime) {
        DataServiceConnection conn = this.connection;
        if (conn != null) {
            String ex = exchange != null ? exchange : "binance";
            String mt = marketType != null ? marketType : "perp";
            conn.unsubscribePage(dataType, symbol, timeframe, ex, mt, startTime, endTime, null);
            String pageKey = makePageKey(dataType, symbol, timeframe, ex, mt, startTime, endTime);
            conn.removePageDataCallback(pageKey);
        }
    }

    private String makePageKey(DataType dataType, String symbol, String timeframe,
                                String exchange, String marketType,
                                long startTime, long endTime) {
        return new com.tradery.data.page.PageKey(
            dataType.toWireFormat(),
            exchange != null ? exchange : "binance",
            symbol.toUpperCase(),
            timeframe,
            marketType != null ? marketType : "perp",
            endTime, endTime - startTime
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
        return getAggTrades(symbol, null, null, start, end);
    }

    /**
     * Fetch aggregated trades for a specific exchange and market type.
     */
    public List<AggTrade> getAggTrades(String symbol, String exchange, String marketType,
                                        Long start, Long end) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/aggtrades").newBuilder()
            .addQueryParameter("symbol", symbol);

        if (exchange != null) urlBuilder.addQueryParameter("exchange", exchange);
        if (marketType != null) urlBuilder.addQueryParameter("marketType", marketType);
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

    // ==================== HL Trade Collection ====================

    /**
     * HL trade subscription info.
     */
    public record HlTradeSubscription(String coin, String exchange, String symbol, long tradeCount, long firstTradeTime, long lastTradeTime) {}

    /**
     * Get all active HL trade collection subscriptions.
     */
    public List<HlTradeSubscription> getHlTradeSubscriptions() throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/hl-trades/subscriptions")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HL trade subscriptions request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, HlTradeSubscription.class));
        }
    }

    /**
     * Add a new HL trade collection subscription.
     */
    public void addHlTradeSubscription(String coin, String exchange, String symbol) throws IOException {
        var body = new java.util.LinkedHashMap<String, String>();
        body.put("coin", coin);
        body.put("exchange", exchange);
        body.put("symbol", symbol);

        Request request = new Request.Builder()
            .url(baseUrl + "/hl-trades/subscriptions")
            .post(RequestBody.create(jsonMapper.writeValueAsString(body), MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Add HL subscription failed: " + response.code());
            }
        }
    }

    /**
     * Remove an HL trade collection subscription.
     */
    public void removeHlTradeSubscription(String coin) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/hl-trades/subscriptions/" + coin)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Remove HL subscription failed: " + response.code());
            }
        }
    }

    // ==================== Inventory ====================

    /**
     * Get a comprehensive inventory of all stored data.
     */
    public InventoryResponse getInventory() throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/inventory")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Inventory request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), InventoryResponse.class);
        }
    }

    /**
     * Get disk usage breakdown by symbol.
     */
    public DiskUsageResponse getDiskUsage() throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/inventory/disk-usage")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Disk usage request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), DiskUsageResponse.class);
        }
    }

    /**
     * Backfill spectrum data from existing aggTrades.
     * @return number of spectrum rows created
     */
    public long backfillSpectrum(String symbol, long from, long to) throws IOException {
        HttpUrl url = HttpUrl.parse(baseUrl + "/spectrum/backfill").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("from", String.valueOf(from))
            .addQueryParameter("to", String.valueOf(to))
            .build();

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create("", MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Spectrum backfill failed: " + response.code());
            }
            var node = jsonMapper.readTree(response.body().string());
            return node.get("rowsCreated").asLong();
        }
    }

    /**
     * Delete specific data from the data service.
     *
     * @param symbol    Symbol name (null for global data types like fearGreed)
     * @param dataType  Data type: candles, aggTrades, funding, openInterest, premiumIndex, fearGreed
     * @param timeframe Timeframe (for candles only, optional)
     * @param marketType Market type (for candles only, optional)
     * @param exchange  Exchange (for aggTrades only, optional)
     * @param interval  Interval (for premiumIndex only, optional)
     * @return Number of deleted records
     */
    public long deleteData(String symbol, String dataType, String timeframe, String marketType,
                           String exchange, String interval) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/data").newBuilder()
            .addQueryParameter("dataType", dataType);

        if (symbol != null) urlBuilder.addQueryParameter("symbol", symbol);
        if (timeframe != null) urlBuilder.addQueryParameter("timeframe", timeframe);
        if (marketType != null) urlBuilder.addQueryParameter("marketType", marketType);
        if (exchange != null) urlBuilder.addQueryParameter("exchange", exchange);
        if (interval != null) urlBuilder.addQueryParameter("interval", interval);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Delete failed: " + response.code());
            }
            DeleteResponse result = jsonMapper.readValue(response.body().string(), DeleteResponse.class);
            return result.deletedRecords();
        }
    }

    /**
     * Get raw coverage ranges for a symbol and data type.
     *
     * @param symbol   Symbol name
     * @param dataType Coverage data type (klines, agg_trades, funding_rates, open_interest, premium_index)
     * @param subKey   Sub key (timeframe for candles, interval for premium, "default" for others)
     */
    public CoverageRangesResponse getCoverageRanges(String symbol, String dataType, String subKey) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/coverage/ranges").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("dataType", dataType);
        if (subKey != null) urlBuilder.addQueryParameter("subKey", subKey);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Coverage ranges request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), CoverageRangesResponse.class);
        }
    }

    // ==================== Volume Profile endpoints ====================

    /**
     * GET /profile/binned — returns binned histogram with POC/VAH/VAL for a time range (defaults to "perp").
     */
    public BinnedProfileResponse getProfileBinned(String symbol, String timeframe,
            long start, long end, int binCount, double valueAreaPct) throws IOException {
        return getProfileBinned(symbol, timeframe, start, end, binCount, valueAreaPct, "perp");
    }

    /**
     * GET /profile/binned — returns binned histogram with POC/VAH/VAL for a time range.
     */
    public BinnedProfileResponse getProfileBinned(String symbol, String timeframe,
            long start, long end, int binCount, double valueAreaPct, String marketType) throws IOException {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/profile/binned").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("timeframe", timeframe)
            .addQueryParameter("start", Long.toString(start))
            .addQueryParameter("end", Long.toString(end))
            .addQueryParameter("binParam", Integer.toString(binCount))
            .addQueryParameter("valueAreaPct", Double.toString(valueAreaPct))
            .addQueryParameter("marketType", marketType);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Profile binned request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), BinnedProfileResponse.class);
        }
    }

    /**
     * GET /profile — returns raw tick-level profiles (defaults to "perp").
     */
    public List<RawProfileResponse> getProfiles(String symbol, String timeframe,
            long start, long end) throws IOException {
        return getProfiles(symbol, timeframe, start, end, "perp");
    }

    /**
     * GET /profile — returns raw tick-level profiles for a time range.
     * Each profile has a tick map (tickIndex → [buyVol, sellVol]) and window metadata.
     */
    public List<RawProfileResponse> getProfiles(String symbol, String timeframe,
            long start, long end, String marketType) throws IOException {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/profile").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("timeframe", timeframe)
            .addQueryParameter("start", Long.toString(start))
            .addQueryParameter("end", Long.toString(end))
            .addQueryParameter("marketType", marketType);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Profile request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, RawProfileResponse.class));
        }
    }

    /**
     * GET /profile/poc-series — returns POC time series (defaults to "perp").
     */
    public List<PocSeriesPoint> getProfilePocSeries(String symbol, String timeframe,
            long start, long end) throws IOException {
        return getProfilePocSeries(symbol, timeframe, start, end, "perp");
    }

    /**
     * GET /profile/poc-series — returns POC time series for a time range.
     */
    public List<PocSeriesPoint> getProfilePocSeries(String symbol, String timeframe,
            long start, long end, String marketType) throws IOException {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/profile/poc-series").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("timeframe", timeframe)
            .addQueryParameter("start", Long.toString(start))
            .addQueryParameter("end", Long.toString(end))
            .addQueryParameter("marketType", marketType);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("POC series request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, PocSeriesPoint.class));
        }
    }

    /**
     * GET /profile/daily-levels — returns POC/VAH/VAL per day (defaults to "perp").
     */
    public List<DailyLevelsPoint> getProfileDailyLevels(String symbol, long start, long end) throws IOException {
        return getProfileDailyLevels(symbol, start, end, "perp");
    }

    /**
     * GET /profile/daily-levels — returns POC/VAH/VAL per day for a time range.
     */
    public List<DailyLevelsPoint> getProfileDailyLevels(String symbol, long start, long end, String marketType) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/profile/daily-levels").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("start", Long.toString(start))
            .addQueryParameter("end", Long.toString(end))
            .addQueryParameter("marketType", marketType);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Daily levels request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, DailyLevelsPoint.class));
        }
    }

    /**
     * GET /profile/daily-binned — returns per-day binned histograms (defaults to "perp").
     */
    public List<DailyBinnedProfile> getProfileDailyBinned(String symbol, long start, long end,
            int binCount, double valueAreaPct) throws IOException {
        return getProfileDailyBinned(symbol, start, end, binCount, valueAreaPct, "perp");
    }

    /**
     * GET /profile/daily-binned — returns per-day binned histograms with POC/VAH/VAL
     * for the entire range in one call. Efficient: single ensureCoverage for the full range.
     */
    public List<DailyBinnedProfile> getProfileDailyBinned(String symbol, long start, long end,
            int binCount, double valueAreaPct, String marketType) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/profile/daily-binned").newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("start", Long.toString(start))
            .addQueryParameter("end", Long.toString(end))
            .addQueryParameter("binCount", Integer.toString(binCount))
            .addQueryParameter("valueAreaPct", Double.toString(valueAreaPct))
            .addQueryParameter("marketType", marketType);

        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Daily binned profile request failed: " + response.code());
            }
            return jsonMapper.readValue(response.body().string(), jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, DailyBinnedProfile.class));
        }
    }

    public record DailyBinnedProfile(
        long dayStart, double poc, double vah, double val, double delta, double totalVolume,
        BinnedBins bins
    ) {}

    public record BinnedBins(double[] priceLevels, double[] buyVolumes, double[] sellVolumes) {}

    /**
     * Close the client.
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // ==================== Inventory DTOs ====================

    public record InventoryResponse(
        List<SymbolInventory> symbols,
        FearGreedInventory fearGreed,
        long totalDiskUsage
    ) {}

    public record SymbolInventory(
        String symbol,
        List<CandleInventory> candles,
        List<AggTradesInventory> aggTrades,
        FundingInventory funding,
        OpenInterestInventory openInterest,
        List<PremiumIndexInventory> premiumIndex,
        List<VolumeProfileInventory> volumeProfiles,
        SpectrumInventory spectrum
    ) {}

    public record CandleInventory(
        String timeframe, String marketType, String exchange,
        long startTime, long endTime, int recordCount
    ) {}

    public record AggTradesInventory(
        String exchange, String marketType,
        long startTime, long endTime, long recordCount
    ) {}

    public record FundingInventory(long startTime, long endTime, int recordCount) {}

    public record OpenInterestInventory(long startTime, long endTime, int recordCount) {}

    public record PremiumIndexInventory(String interval, long startTime, long endTime, int recordCount) {}

    public record FearGreedInventory(long startTime, long endTime, int recordCount, int latestValue) {}

    public record VolumeProfileInventory(String timeframe, String marketType, long startTime, long endTime, long recordCount) {}

    public record SpectrumInventory(long startTime, long endTime, long recordCount) {}

    public record DiskUsageResponse(long totalBytes, java.util.Map<String, Long> bySymbol, java.util.Map<String, Long> byDataType, long volumeFreeBytes, long volumeTotalBytes) {}

    public record DeleteResponse(long deletedRecords) {}

    // ==================== Coverage DTOs ====================

    public record CoverageRange(long rangeStart, long rangeEnd, boolean isComplete) {}

    public record CoverageRangesResponse(List<CoverageRange> ranges) {}

    // ==================== Symbol resolution DTOs ====================
    public record SymbolResolveResponse(String canonical, String exchange, String marketType, String quote, String symbol) {}

    public record SymbolReverseResponse(String symbol, String exchange, String marketType, String base, String quote,
                                         String coingeckoBaseId, String coingeckoQuoteId) {}

    public record SymbolSearchResult(String symbol, String exchange, String marketType, String base, String quote,
                                      String coingeckoId) {}

    public record SymbolSearchResponse(String query, int count, List<SymbolSearchResult> results) {}

    public record ExchangeMarketStats(String marketType, int pairCount) {}

    public record SyncProgress(
        String step,
        String exchange,
        String marketType,
        int currentPage,
        int completedSteps,
        int totalSteps,
        int totalCoins,
        int pairsFoundSoFar,
        long startedAtMs
    ) {}

    public record SymbolStats(int totalPairs, int totalAssets, int totalCoins,
                              java.util.Map<String, java.util.List<ExchangeMarketStats>> byExchange,
                              boolean syncInProgress,
                              SyncProgress syncProgress) {}

    // ==================== Volume Profile DTOs ====================

    public record BinnedProfileResponse(
        double poc, double vah, double val, double delta,
        BinnedProfileBins bins
    ) {
        public record BinnedProfileBins(double[] priceLevels, double[] buyVolumes, double[] sellVolumes) {}
    }

    public record RawProfileResponse(
        long windowStart, double tickSize, double totalBuyVolume, double totalSellVolume,
        java.util.Map<String, double[]> levels
    ) {}

    public record PocSeriesPoint(long timestamp, double poc, double volume) {}

    public record DailyLevelsPoint(long dayStart, double poc, double vah, double val,
                                    double delta, double totalVolume) {}
}
