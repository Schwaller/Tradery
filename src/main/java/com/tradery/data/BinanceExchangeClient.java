package com.tradery.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.model.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Binance exchange client implementing the ExchangeClient interface.
 * Wraps existing Binance API calls with exchange-aware metadata.
 *
 * Supports both spot and futures APIs.
 */
public class BinanceExchangeClient implements ExchangeClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceExchangeClient.class);

    // API endpoints
    private static final String SPOT_BASE_URL = "https://api.binance.com/api/v3";
    private static final String FUTURES_BASE_URL = "https://fapi.binance.com/fapi/v1";

    private static final int MAX_TRADES_PER_REQUEST = 1000;
    private static final int MAX_CANDLES_PER_REQUEST = 1000;
    private static final long RATE_LIMIT_DELAY_MS = 50; // 1200 req/min = ~50ms between requests

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final ExchangeRateLimiter rateLimiter;
    private final DataMarketType defaultMarketType;

    public BinanceExchangeClient() {
        this(DataMarketType.FUTURES_PERP);
    }

    public BinanceExchangeClient(DataMarketType defaultMarketType) {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
        this.rateLimiter = ExchangeRateLimiter.fixedDelay(RATE_LIMIT_DELAY_MS);
        this.defaultMarketType = defaultMarketType;
    }

    @Override
    public Exchange getExchange() {
        return Exchange.BINANCE;
    }

    @Override
    public DataMarketType getDefaultMarketType() {
        return defaultMarketType;
    }

    @Override
    public String normalizeSymbol(String baseSymbol, String quoteSymbol, DataMarketType marketType) {
        // Binance uses simple concatenation: BTCUSDT
        return baseSymbol.toUpperCase() + quoteSymbol.toUpperCase();
    }

    @Override
    public boolean supportsBulkHistorical() {
        return true; // Binance Vision supports bulk downloads
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

        String baseUrl = getBaseUrl(defaultMarketType);
        StringBuilder url = new StringBuilder(baseUrl + "/aggTrades")
            .append("?symbol=").append(symbol)
            .append("&limit=").append(Math.min(limit, MAX_TRADES_PER_REQUEST));

        if (startTime > 0) {
            url.append("&startTime=").append(startTime);
        }
        if (endTime > 0) {
            url.append("&endTime=").append(endTime);
        }

        Request request = new Request.Builder()
            .url(url.toString())
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Binance API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            List<AggTrade> trades = new ArrayList<>();

            for (JsonNode trade : root) {
                long aggTradeId = trade.get("a").asLong();
                double price = Double.parseDouble(trade.get("p").asText());
                double quantity = Double.parseDouble(trade.get("q").asText());
                long firstTradeId = trade.get("f").asLong();
                long lastTradeId = trade.get("l").asLong();
                long timestamp = trade.get("T").asLong();
                boolean isBuyerMaker = trade.get("m").asBoolean();

                // Create with exchange metadata
                trades.add(AggTrade.withExchange(
                    aggTradeId, price, quantity, firstTradeId, lastTradeId, timestamp, isBuyerMaker,
                    Exchange.BINANCE, defaultMarketType, symbol
                ));
            }

            return trades;
        }
    }

    @Override
    public List<AggTrade> fetchAllAggTrades(String symbol, long startTime, long endTime,
                                             AtomicBoolean cancelled, Consumer<FetchProgress> onProgress)
            throws IOException {

        List<AggTrade> allTrades = new ArrayList<>();
        long currentStart = startTime;

        long estimatedTradesPerDay = SyncEstimator.getTradesPerDay(symbol);
        long days = Math.max(1, (endTime - startTime) / (24 * 60 * 60 * 1000));
        long estimatedTotal = days * estimatedTradesPerDay;

        log.info("Fetching {} aggTrades from Binance...", symbol);

        if (onProgress != null) {
            onProgress.accept(FetchProgress.starting(symbol, "aggTrades"));
        }

        while (currentStart < endTime) {
            if (cancelled != null && cancelled.get()) {
                log.debug("Fetch cancelled. Returning {} trades.", allTrades.size());
                if (onProgress != null) {
                    onProgress.accept(FetchProgress.cancelled(allTrades.size()));
                }
                return allTrades;
            }

            List<AggTrade> batch = fetchAggTrades(symbol, currentStart, endTime, MAX_TRADES_PER_REQUEST);

            if (batch.isEmpty()) {
                break;
            }

            allTrades.addAll(batch);

            AggTrade lastTrade = batch.get(batch.size() - 1);
            currentStart = lastTrade.timestamp() + 1;

            if (batch.size() < MAX_TRADES_PER_REQUEST) {
                break;
            }

            if (onProgress != null) {
                int percent = (int) Math.min(99, (allTrades.size() * 100) / estimatedTotal);
                String msg = "Fetching " + symbol + " trades: " + formatCount(allTrades.size()) + "...";
                onProgress.accept(new FetchProgress(allTrades.size(), (int) estimatedTotal, msg));
            }

            if (allTrades.size() % 50000 == 0) {
                log.debug("Fetched {} trades so far...", formatCount(allTrades.size()));
            }
        }

        log.info("Fetch complete. Total: {} trades", formatCount(allTrades.size()));

        if (onProgress != null) {
            onProgress.accept(FetchProgress.complete(allTrades.size()));
        }

        return allTrades;
    }

    // ========== Candles ==========

    @Override
    public List<Candle> fetchCandles(String symbol, String timeframe, long startTime, long endTime, int limit)
            throws IOException {
        rateLimiter.acquire();

        String baseUrl = getBaseUrl(defaultMarketType);
        StringBuilder url = new StringBuilder(baseUrl + "/klines")
            .append("?symbol=").append(symbol)
            .append("&interval=").append(timeframe)
            .append("&limit=").append(Math.min(limit, MAX_CANDLES_PER_REQUEST));

        if (startTime > 0) {
            url.append("&startTime=").append(startTime);
        }
        if (endTime > 0) {
            url.append("&endTime=").append(endTime);
        }

        Request request = new Request.Builder()
            .url(url.toString())
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Binance API error: " + response.code() + " " + response.message());
            }

            String body = response.body().string();
            JsonNode root = mapper.readTree(body);

            List<Candle> candles = new ArrayList<>();

            for (JsonNode kline : root) {
                long timestamp = kline.get(0).asLong();
                double open = Double.parseDouble(kline.get(1).asText());
                double high = Double.parseDouble(kline.get(2).asText());
                double low = Double.parseDouble(kline.get(3).asText());
                double close = Double.parseDouble(kline.get(4).asText());
                double volume = Double.parseDouble(kline.get(5).asText());
                double quoteVolume = kline.has(7) ? Double.parseDouble(kline.get(7).asText()) : -1;
                int tradeCount = kline.has(8) ? kline.get(8).asInt() : -1;
                double takerBuyVolume = kline.has(9) ? Double.parseDouble(kline.get(9).asText()) : -1;
                double takerBuyQuoteVolume = kline.has(10) ? Double.parseDouble(kline.get(10).asText()) : -1;

                candles.add(new Candle(timestamp, open, high, low, close, volume,
                    tradeCount, quoteVolume, takerBuyVolume, takerBuyQuoteVolume));
            }

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

        log.info("Fetching {} {} data from Binance...", symbol, timeframe);

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

    private String getBaseUrl(DataMarketType marketType) {
        return switch (marketType) {
            case SPOT -> SPOT_BASE_URL;
            case FUTURES_PERP, FUTURES_DATED -> FUTURES_BASE_URL;
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

    private String formatCount(int count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        } else if (count >= 1_000) {
            return String.format("%.1fK", count / 1_000.0);
        }
        return String.valueOf(count);
    }

    /**
     * Get server time from Binance
     */
    public long getServerTime() throws IOException {
        Request request = new Request.Builder()
            .url(SPOT_BASE_URL + "/time")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Binance API error: " + response.code());
            }

            JsonNode root = mapper.readTree(response.body().string());
            return root.get("serverTime").asLong();
        }
    }

    /**
     * Check if Binance API is accessible
     */
    public boolean ping() {
        Request request = new Request.Builder()
            .url(SPOT_BASE_URL + "/ping")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }
}
