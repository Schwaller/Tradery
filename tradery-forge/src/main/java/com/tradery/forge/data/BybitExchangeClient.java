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
 * Bybit exchange client implementing the ExchangeClient interface.
 * Uses Bybit V5 API for candles and trades.
 *
 * API docs: https://bybit-exchange.github.io/docs/v5/intro
 */
public class BybitExchangeClient implements ExchangeClient {

    private static final Logger log = LoggerFactory.getLogger(BybitExchangeClient.class);

    private static final String BASE_URL = "https://api.bybit.com";

    // Bybit allows ~120 req/min for public endpoints; use 100ms for safety
    private static final long RATE_LIMIT_DELAY_MS = 100;
    private static final int MAX_TRADES_PER_REQUEST = 1000;
    private static final int MAX_CANDLES_PER_REQUEST = 1000;

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final ExchangeRateLimiter rateLimiter;
    private final DataMarketType defaultMarketType;

    public BybitExchangeClient() {
        this(DataMarketType.FUTURES_PERP);
    }

    public BybitExchangeClient(DataMarketType defaultMarketType) {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
        this.rateLimiter = ExchangeRateLimiter.fixedDelay(RATE_LIMIT_DELAY_MS);
        this.defaultMarketType = defaultMarketType;
    }

    @Override
    public Exchange getExchange() {
        return Exchange.BYBIT;
    }

    @Override
    public DataMarketType getDefaultMarketType() {
        return defaultMarketType;
    }

    @Override
    public String normalizeSymbol(String baseSymbol, String quoteSymbol, DataMarketType marketType) {
        // Bybit uses simple concatenation like Binance: BTCUSDT
        return baseSymbol.toUpperCase() + quoteSymbol.toUpperCase();
    }

    @Override
    public boolean supportsBulkHistorical() {
        return false; // Bybit has no Vision-like bulk download
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

        String category = getCategory(defaultMarketType);
        int effectiveLimit = Math.min(limit, MAX_TRADES_PER_REQUEST);

        // Bybit /v5/market/recent-trade only supports recent trades (no time range)
        String url = BASE_URL + "/v5/market/recent-trade"
            + "?category=" + category
            + "&symbol=" + symbol
            + "&limit=" + effectiveLimit;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Bybit API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            int retCode = root.has("retCode") ? root.get("retCode").asInt() : -1;
            if (retCode != 0) {
                String retMsg = root.has("retMsg") ? root.get("retMsg").asText() : "Unknown error";
                throw new IOException("Bybit API error: " + retCode + " " + retMsg);
            }

            JsonNode result = root.get("result");
            if (result == null || !result.has("list")) {
                return new ArrayList<>();
            }

            JsonNode list = result.get("list");
            List<AggTrade> trades = new ArrayList<>();

            for (JsonNode trade : list) {
                String execId = trade.get("execId").asText();
                double price = Double.parseDouble(trade.get("price").asText());
                double size = Double.parseDouble(trade.get("size").asText());
                String side = trade.get("side").asText();
                long time = Long.parseLong(trade.get("time").asText());

                // Bybit side: "Buy" = taker buy (aggressive buy), "Sell" = taker sell
                // isBuyerMaker: true = sell tick (buyer is maker), false = buy tick
                boolean isBuyerMaker = "Sell".equalsIgnoreCase(side);

                // Use hash of execId as trade ID since Bybit uses string IDs
                long tradeId = execId.hashCode() & 0xFFFFFFFFL;

                // Filter by time range if specified
                if (startTime > 0 && time < startTime) continue;
                if (endTime > 0 && time > endTime) continue;

                trades.add(AggTrade.withExchange(
                    tradeId, price, size, tradeId, tradeId, time, isBuyerMaker,
                    Exchange.BYBIT, defaultMarketType, symbol
                ));
            }

            // Bybit returns newest first, reverse for chronological order
            Collections.reverse(trades);
            return trades;
        }
    }

    @Override
    public List<AggTrade> fetchAllAggTrades(String symbol, long startTime, long endTime,
                                             AtomicBoolean cancelled, Consumer<FetchProgress> onProgress)
            throws IOException {

        log.info("Fetching {} aggTrades from Bybit (recent trades only)...", symbol);

        if (onProgress != null) {
            onProgress.accept(FetchProgress.starting(symbol, "aggTrades"));
        }

        // Bybit only supports recent trades - no time-range pagination
        // Fetch the maximum available (1000 trades)
        List<AggTrade> trades = fetchAggTrades(symbol, startTime, endTime, MAX_TRADES_PER_REQUEST);

        log.info("Bybit fetch complete. Total: {} trades (recent only)", trades.size());

        if (onProgress != null) {
            onProgress.accept(FetchProgress.complete(trades.size()));
        }

        return trades;
    }

    // ========== Candles ==========

    @Override
    public List<Candle> fetchCandles(String symbol, String timeframe, long startTime, long endTime, int limit)
            throws IOException {
        rateLimiter.acquire();

        String category = getCategory(defaultMarketType);
        String interval = mapTimeframe(timeframe);
        int effectiveLimit = Math.min(limit, MAX_CANDLES_PER_REQUEST);

        StringBuilder url = new StringBuilder(BASE_URL + "/v5/market/kline")
            .append("?category=").append(category)
            .append("&symbol=").append(symbol)
            .append("&interval=").append(interval)
            .append("&limit=").append(effectiveLimit);

        if (startTime > 0) {
            url.append("&start=").append(startTime);
        }
        if (endTime > 0) {
            url.append("&end=").append(endTime);
        }

        Request request = new Request.Builder()
            .url(url.toString())
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Bybit API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            int retCode = root.has("retCode") ? root.get("retCode").asInt() : -1;
            if (retCode != 0) {
                String retMsg = root.has("retMsg") ? root.get("retMsg").asText() : "Unknown error";
                throw new IOException("Bybit API error: " + retCode + " " + retMsg);
            }

            JsonNode result = root.get("result");
            if (result == null || !result.has("list")) {
                return new ArrayList<>();
            }

            JsonNode list = result.get("list");
            List<Candle> candles = new ArrayList<>();

            for (JsonNode kline : list) {
                // Bybit kline format: [startTime, open, high, low, close, volume, turnover]
                long timestamp = Long.parseLong(kline.get(0).asText());
                double open = Double.parseDouble(kline.get(1).asText());
                double high = Double.parseDouble(kline.get(2).asText());
                double low = Double.parseDouble(kline.get(3).asText());
                double close = Double.parseDouble(kline.get(4).asText());
                double volume = Double.parseDouble(kline.get(5).asText());
                double turnover = kline.has(6) ? Double.parseDouble(kline.get(6).asText()) : -1;

                // Bybit provides turnover (quote volume) but no taker buy data or trade count
                candles.add(new Candle(timestamp, open, high, low, close, volume,
                    -1, turnover, -1, -1));
            }

            // Bybit returns newest first, reverse for chronological order
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

        int estimatedTotal = (int) ((endTime - startTime) / intervalMs);

        log.info("Fetching {} {} data from Bybit...", symbol, timeframe);

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

            List<Candle> batch = fetchCandles(symbol, timeframe, currentStart, endTime, MAX_CANDLES_PER_REQUEST);

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

    // ========== Helper Methods ==========

    /**
     * Map Bybit category from market type.
     */
    private String getCategory(DataMarketType marketType) {
        return switch (marketType) {
            case SPOT -> "spot";
            case FUTURES_PERP, FUTURES_DATED -> "linear";
        };
    }

    /**
     * Map standard timeframe strings to Bybit interval format.
     */
    private String mapTimeframe(String timeframe) {
        return switch (timeframe) {
            case "1m" -> "1";
            case "3m" -> "3";
            case "5m" -> "5";
            case "15m" -> "15";
            case "30m" -> "30";
            case "1h" -> "60";
            case "2h" -> "120";
            case "4h" -> "240";
            case "6h" -> "360";
            case "12h" -> "720";
            case "1d" -> "D";
            case "1w" -> "W";
            case "1M" -> "M";
            default -> "60"; // Default to 1h
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
