package com.tradery.forge.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * OKX exchange client implementing the ExchangeClient interface.
 * Uses OKX V5 API for candles and trades.
 *
 * API docs: https://www.okx.com/docs-v5/en/
 */
public class OkxExchangeClient implements ExchangeClient {

    private static final Logger log = LoggerFactory.getLogger(OkxExchangeClient.class);

    private static final String BASE_URL = "https://www.okx.com";

    // OKX allows 20 req/2sec for public endpoints; use 100ms for safety
    private static final long RATE_LIMIT_DELAY_MS = 100;
    private static final int MAX_TRADES_PER_REQUEST = 100; // history-trades limit
    private static final int MAX_RECENT_TRADES = 500;      // recent trades limit
    private static final int MAX_CANDLES_PER_REQUEST = 300;
    private static final int MAX_HISTORY_CANDLES_PER_REQUEST = 100;

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final ExchangeRateLimiter rateLimiter;
    private final DataMarketType defaultMarketType;

    public OkxExchangeClient() {
        this(DataMarketType.FUTURES_PERP);
    }

    public OkxExchangeClient(DataMarketType defaultMarketType) {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
        this.rateLimiter = ExchangeRateLimiter.fixedDelay(RATE_LIMIT_DELAY_MS);
        this.defaultMarketType = defaultMarketType;
    }

    @Override
    public Exchange getExchange() {
        return Exchange.OKX;
    }

    @Override
    public DataMarketType getDefaultMarketType() {
        return defaultMarketType;
    }

    @Override
    public String normalizeSymbol(String baseSymbol, String quoteSymbol, DataMarketType marketType) {
        String base = baseSymbol.toUpperCase();
        String quote = quoteSymbol.toUpperCase();
        return switch (marketType) {
            case FUTURES_PERP -> base + "-" + quote + "-SWAP";
            case FUTURES_DATED -> base + "-" + quote; // Dated futures need specific expiry suffix
            case SPOT -> base + "-" + quote;
        };
    }

    @Override
    public boolean supportsBulkHistorical() {
        return false; // OKX has no Vision-like bulk download
    }

    @Override
    public ExchangeRateLimiter getRateLimiter() {
        return rateLimiter;
    }

    @Override
    public int getMaxTradesPerRequest() {
        return MAX_TRADES_PER_REQUEST;
    }

    @Override
    public int getMaxCandlesPerRequest() {
        return MAX_CANDLES_PER_REQUEST;
    }

    // ========== AggTrades ==========

    @Override
    public List<AggTrade> fetchAggTrades(String symbol, long startTime, long endTime, int limit)
            throws IOException {
        rateLimiter.acquire();

        // Use recent trades endpoint for simple fetches
        int effectiveLimit = Math.min(limit, MAX_RECENT_TRADES);

        String url = BASE_URL + "/api/v5/market/trades"
            + "?instId=" + symbol
            + "&limit=" + effectiveLimit;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OKX API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            String code = root.has("code") ? root.get("code").asText() : "-1";
            if (!"0".equals(code)) {
                String msg = root.has("msg") ? root.get("msg").asText() : "Unknown error";
                throw new IOException("OKX API error: " + code + " " + msg);
            }

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return new ArrayList<>();
            }

            List<AggTrade> trades = new ArrayList<>();

            for (JsonNode trade : data) {
                String tradeIdStr = trade.get("tradeId").asText();
                double price = Double.parseDouble(trade.get("px").asText());
                double size = Double.parseDouble(trade.get("sz").asText());
                String side = trade.get("side").asText();
                long time = Long.parseLong(trade.get("ts").asText());

                // OKX side: "buy" = taker buy, "sell" = taker sell
                boolean isBuyerMaker = "sell".equalsIgnoreCase(side);

                long tradeId;
                try {
                    tradeId = Long.parseLong(tradeIdStr);
                } catch (NumberFormatException e) {
                    tradeId = tradeIdStr.hashCode() & 0xFFFFFFFFL;
                }

                // Filter by time range if specified
                if (startTime > 0 && time < startTime) continue;
                if (endTime > 0 && time > endTime) continue;

                trades.add(AggTrade.withExchange(
                    tradeId, price, size, tradeId, tradeId, time, isBuyerMaker,
                    Exchange.OKX, defaultMarketType, symbol
                ));
            }

            // OKX returns newest first for recent trades, reverse for chronological order
            Collections.reverse(trades);
            return trades;
        }
    }

    @Override
    public List<AggTrade> fetchAllAggTrades(String symbol, long startTime, long endTime,
                                             AtomicBoolean cancelled, Consumer<FetchProgress> onProgress)
            throws IOException {

        log.info("Fetching {} aggTrades from OKX...", symbol);

        if (onProgress != null) {
            onProgress.accept(FetchProgress.starting(symbol, "aggTrades"));
        }

        List<AggTrade> allTrades = new ArrayList<>();

        // First, fetch recent trades (up to 500)
        List<AggTrade> recentTrades = fetchAggTrades(symbol, startTime, endTime, MAX_RECENT_TRADES);
        allTrades.addAll(recentTrades);

        // Then paginate backwards using history-trades endpoint
        // OKX history-trades uses tradeId-based pagination (after = return trades before this ID)
        if (!recentTrades.isEmpty()) {
            long oldestTradeId = recentTrades.get(0).aggTradeId();

            // Paginate backwards to collect more historical trades
            int maxPages = 50; // Safety limit: 50 pages * 100 trades = 5000 trades max
            for (int page = 0; page < maxPages; page++) {
                if (cancelled != null && cancelled.get()) {
                    log.debug("Fetch cancelled. Returning {} trades.", allTrades.size());
                    if (onProgress != null) {
                        onProgress.accept(FetchProgress.cancelled(allTrades.size()));
                    }
                    return allTrades;
                }

                List<AggTrade> batch = fetchHistoryTrades(symbol, oldestTradeId);
                if (batch.isEmpty()) {
                    break;
                }

                // Filter by time range
                List<AggTrade> filtered = new ArrayList<>();
                for (AggTrade trade : batch) {
                    if (trade.timestamp() >= startTime && trade.timestamp() <= endTime) {
                        filtered.add(trade);
                    }
                }

                if (filtered.isEmpty()) {
                    break; // We've gone past the requested time range
                }

                // Insert at the beginning since we're going backwards
                allTrades.addAll(0, filtered);
                oldestTradeId = batch.get(0).aggTradeId();

                if (onProgress != null) {
                    String msg = "Fetching " + symbol + " trades: " + allTrades.size() + "...";
                    onProgress.accept(new FetchProgress(allTrades.size(), allTrades.size() + 1000, msg));
                }

                // If we got fewer than max, we've reached the end
                if (batch.size() < MAX_TRADES_PER_REQUEST) {
                    break;
                }
            }
        }

        // Sort by timestamp to ensure chronological order
        allTrades.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));

        log.info("OKX fetch complete. Total: {} trades", allTrades.size());

        if (onProgress != null) {
            onProgress.accept(FetchProgress.complete(allTrades.size()));
        }

        return allTrades;
    }

    /**
     * Fetch historical trades using OKX history-trades endpoint.
     * Paginates backwards from a given trade ID.
     */
    private List<AggTrade> fetchHistoryTrades(String symbol, long afterTradeId) throws IOException {
        rateLimiter.acquire();

        String url = BASE_URL + "/api/v5/market/history-trades"
            + "?instId=" + symbol
            + "&limit=" + MAX_TRADES_PER_REQUEST
            + "&after=" + afterTradeId;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OKX API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            String code = root.has("code") ? root.get("code").asText() : "-1";
            if (!"0".equals(code)) {
                String msg = root.has("msg") ? root.get("msg").asText() : "Unknown error";
                throw new IOException("OKX API error: " + code + " " + msg);
            }

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return new ArrayList<>();
            }

            List<AggTrade> trades = new ArrayList<>();

            for (JsonNode trade : data) {
                String tradeIdStr = trade.get("tradeId").asText();
                double price = Double.parseDouble(trade.get("px").asText());
                double size = Double.parseDouble(trade.get("sz").asText());
                String side = trade.get("side").asText();
                long time = Long.parseLong(trade.get("ts").asText());

                boolean isBuyerMaker = "sell".equalsIgnoreCase(side);

                long tradeId;
                try {
                    tradeId = Long.parseLong(tradeIdStr);
                } catch (NumberFormatException e) {
                    tradeId = tradeIdStr.hashCode() & 0xFFFFFFFFL;
                }

                trades.add(AggTrade.withExchange(
                    tradeId, price, size, tradeId, tradeId, time, isBuyerMaker,
                    Exchange.OKX, defaultMarketType, symbol
                ));
            }

            // OKX returns newest first, reverse for chronological order
            Collections.reverse(trades);
            return trades;
        }
    }

    // ========== Candles ==========

    @Override
    public List<Candle> fetchCandles(String symbol, String timeframe, long startTime, long endTime, int limit)
            throws IOException {
        rateLimiter.acquire();

        String bar = mapTimeframe(timeframe);
        int effectiveLimit = Math.min(limit, MAX_CANDLES_PER_REQUEST);

        // OKX uses 'before' (return data newer than ts) and 'after' (return data older than ts)
        // For forward pagination: use 'before' = startTime to get candles after that time
        StringBuilder url = new StringBuilder(BASE_URL + "/api/v5/market/candles")
            .append("?instId=").append(symbol)
            .append("&bar=").append(bar)
            .append("&limit=").append(effectiveLimit);

        if (startTime > 0) {
            // 'before' returns records newer than this timestamp
            url.append("&before=").append(startTime - 1);
        }
        if (endTime > 0) {
            // 'after' returns records older than this timestamp
            url.append("&after=").append(endTime + 1);
        }

        Request request = new Request.Builder()
            .url(url.toString())
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OKX API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            String code = root.has("code") ? root.get("code").asText() : "-1";
            if (!"0".equals(code)) {
                String msg = root.has("msg") ? root.get("msg").asText() : "Unknown error";
                throw new IOException("OKX API error: " + code + " " + msg);
            }

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return new ArrayList<>();
            }

            List<Candle> candles = new ArrayList<>();

            for (JsonNode kline : data) {
                // OKX kline format: [ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]
                long timestamp = Long.parseLong(kline.get(0).asText());
                double open = Double.parseDouble(kline.get(1).asText());
                double high = Double.parseDouble(kline.get(2).asText());
                double low = Double.parseDouble(kline.get(3).asText());
                double close = Double.parseDouble(kline.get(4).asText());
                double volume = Double.parseDouble(kline.get(5).asText());
                // volCcy = volume in currency (quote volume for USDT pairs)
                double quoteVolume = kline.has(6) ? Double.parseDouble(kline.get(6).asText()) : -1;

                // OKX doesn't provide taker buy data or trade count in klines
                candles.add(new Candle(timestamp, open, high, low, close, volume,
                    -1, quoteVolume, -1, -1));
            }

            // OKX returns newest first, reverse for chronological order
            Collections.reverse(candles);
            return candles;
        }
    }

    @Override
    public List<Candle> fetchAllCandles(String symbol, String timeframe, long startTime, long endTime,
                                         AtomicBoolean cancelled, Consumer<FetchProgress> onProgress)
            throws IOException {

        List<Candle> allCandles = new ArrayList<>();
        long currentStart = startTime;
        long intervalMs = getIntervalMs(timeframe);
        boolean useHistoryEndpoint = false;

        int estimatedTotal = (int) ((endTime - startTime) / intervalMs);

        log.info("Fetching {} {} data from OKX...", symbol, timeframe);

        if (onProgress != null) {
            onProgress.accept(FetchProgress.starting(symbol, timeframe));
        }

        while (currentStart < endTime) {
            if (cancelled != null && cancelled.get()) {
                log.debug("Fetch cancelled. Returning {} candles.", allCandles.size());
                if (onProgress != null) {
                    onProgress.accept(FetchProgress.cancelled(allCandles.size()));
                }
                return allCandles;
            }

            List<Candle> batch;
            if (useHistoryEndpoint) {
                batch = fetchHistoryCandles(symbol, timeframe, currentStart, endTime);
            } else {
                batch = fetchCandles(symbol, timeframe, currentStart, endTime, MAX_CANDLES_PER_REQUEST);
                // If we get empty results from the main endpoint, try history endpoint
                if (batch.isEmpty() && allCandles.isEmpty()) {
                    useHistoryEndpoint = true;
                    batch = fetchHistoryCandles(symbol, timeframe, currentStart, endTime);
                }
            }

            if (batch.isEmpty()) {
                break;
            }

            allCandles.addAll(batch);

            Candle lastCandle = batch.get(batch.size() - 1);
            currentStart = lastCandle.timestamp() + intervalMs;

            if (onProgress != null) {
                String msg = "Fetching " + symbol + " " + timeframe + ": " + allCandles.size() + " candles...";
                onProgress.accept(new FetchProgress(allCandles.size(), estimatedTotal, msg));
            }

            log.debug("Fetched {} candles so far...", allCandles.size());
        }

        log.info("Fetch complete. Total: {} candles", allCandles.size());

        if (onProgress != null) {
            onProgress.accept(FetchProgress.complete(allCandles.size()));
        }

        return allCandles;
    }

    /**
     * Fetch historical candles using OKX history-candles endpoint.
     * Used for data older than what the main candles endpoint provides.
     */
    private List<Candle> fetchHistoryCandles(String symbol, String timeframe, long startTime, long endTime)
            throws IOException {
        rateLimiter.acquire();

        String bar = mapTimeframe(timeframe);
        int limit = MAX_HISTORY_CANDLES_PER_REQUEST;

        StringBuilder url = new StringBuilder(BASE_URL + "/api/v5/market/history-candles")
            .append("?instId=").append(symbol)
            .append("&bar=").append(bar)
            .append("&limit=").append(limit);

        if (startTime > 0) {
            url.append("&before=").append(startTime - 1);
        }
        if (endTime > 0) {
            url.append("&after=").append(endTime + 1);
        }

        Request request = new Request.Builder()
            .url(url.toString())
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OKX API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            String code = root.has("code") ? root.get("code").asText() : "-1";
            if (!"0".equals(code)) {
                String msg = root.has("msg") ? root.get("msg").asText() : "Unknown error";
                throw new IOException("OKX API error: " + code + " " + msg);
            }

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return new ArrayList<>();
            }

            List<Candle> candles = new ArrayList<>();

            for (JsonNode kline : data) {
                long timestamp = Long.parseLong(kline.get(0).asText());
                double open = Double.parseDouble(kline.get(1).asText());
                double high = Double.parseDouble(kline.get(2).asText());
                double low = Double.parseDouble(kline.get(3).asText());
                double close = Double.parseDouble(kline.get(4).asText());
                double volume = Double.parseDouble(kline.get(5).asText());
                double quoteVolume = kline.has(6) ? Double.parseDouble(kline.get(6).asText()) : -1;

                candles.add(new Candle(timestamp, open, high, low, close, volume,
                    -1, quoteVolume, -1, -1));
            }

            // OKX returns newest first, reverse for chronological order
            Collections.reverse(candles);
            return candles;
        }
    }

    // ========== Helper Methods ==========

    /**
     * Map standard timeframe strings to OKX bar format.
     */
    private String mapTimeframe(String timeframe) {
        return switch (timeframe) {
            case "1m" -> "1m";
            case "3m" -> "3m";
            case "5m" -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1h" -> "1H";
            case "2h" -> "2H";
            case "4h" -> "4H";
            case "6h" -> "6H";
            case "12h" -> "12H";
            case "1d" -> "1Dutc";
            case "1w" -> "1Wutc";
            case "1M" -> "1Mutc";
            default -> "1H"; // Default to 1h
        };
    }

    private long getIntervalMs(String interval) {
        return switch (interval) {
            case "1m" -> Duration.ofMinutes(1).toMillis();
            case "3m" -> Duration.ofMinutes(3).toMillis();
            case "5m" -> Duration.ofMinutes(5).toMillis();
            case "15m" -> Duration.ofMinutes(15).toMillis();
            case "30m" -> Duration.ofMinutes(30).toMillis();
            case "1h" -> Duration.ofHours(1).toMillis();
            case "2h" -> Duration.ofHours(2).toMillis();
            case "4h" -> Duration.ofHours(4).toMillis();
            case "6h" -> Duration.ofHours(6).toMillis();
            case "8h" -> Duration.ofHours(8).toMillis();
            case "12h" -> Duration.ofHours(12).toMillis();
            case "1d" -> Duration.ofDays(1).toMillis();
            case "3d" -> Duration.ofDays(3).toMillis();
            case "1w" -> Duration.ofDays(7).toMillis();
            case "1M" -> Duration.ofDays(30).toMillis();
            default -> Duration.ofHours(1).toMillis();
        };
    }
}
