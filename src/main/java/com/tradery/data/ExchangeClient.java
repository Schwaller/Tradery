package com.tradery.data;

import com.tradery.model.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Interface for cryptocurrency exchange clients.
 * Provides a unified API for fetching market data from different exchanges.
 */
public interface ExchangeClient {

    /**
     * Get the exchange this client connects to.
     */
    Exchange getExchange();

    /**
     * Get the default market type for this exchange.
     */
    DataMarketType getDefaultMarketType();

    /**
     * Normalize a symbol to the exchange's expected format.
     * Example: "BTC" -> "BTCUSDT" for Binance, "BTC-USDT-SWAP" for OKX
     *
     * @param baseSymbol The base symbol (e.g., "BTC", "ETH")
     * @param quoteSymbol The quote symbol (e.g., "USDT", "USD")
     * @param marketType The market type
     * @return The exchange-specific symbol
     */
    String normalizeSymbol(String baseSymbol, String quoteSymbol, DataMarketType marketType);

    /**
     * Check if this exchange supports bulk historical data downloads.
     * (e.g., Binance Vision for aggTrades)
     */
    boolean supportsBulkHistorical();

    /**
     * Get the rate limiter for this exchange.
     */
    ExchangeRateLimiter getRateLimiter();

    /**
     * Fetch aggregated trades from the exchange.
     *
     * @param symbol    Trading pair in exchange format
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @param limit     Max number of trades per request
     * @return List of aggregated trades
     */
    List<AggTrade> fetchAggTrades(String symbol, long startTime, long endTime, int limit)
            throws IOException;

    /**
     * Fetch all aggregated trades between start and end time.
     * Handles pagination automatically.
     *
     * @param symbol     Trading pair in exchange format
     * @param startTime  Start time in milliseconds
     * @param endTime    End time in milliseconds
     * @param cancelled  Optional AtomicBoolean to signal cancellation
     * @param onProgress Optional callback for progress updates
     * @return List of aggregated trades
     */
    List<AggTrade> fetchAllAggTrades(String symbol, long startTime, long endTime,
                                      AtomicBoolean cancelled, Consumer<FetchProgress> onProgress)
            throws IOException;

    /**
     * Fetch OHLCV candles from the exchange.
     *
     * @param symbol    Trading pair in exchange format
     * @param timeframe Timeframe (e.g., "1h", "4h", "1d")
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @param limit     Max number of candles per request
     * @return List of candles
     */
    List<Candle> fetchCandles(String symbol, String timeframe, long startTime, long endTime, int limit)
            throws IOException;

    /**
     * Fetch all candles between start and end time.
     * Handles pagination automatically.
     *
     * @param symbol     Trading pair in exchange format
     * @param timeframe  Timeframe (e.g., "1h", "4h", "1d")
     * @param startTime  Start time in milliseconds
     * @param endTime    End time in milliseconds
     * @param cancelled  Optional AtomicBoolean to signal cancellation
     * @param onProgress Optional callback for progress updates
     * @return List of candles
     */
    List<Candle> fetchAllCandles(String symbol, String timeframe, long startTime, long endTime,
                                  AtomicBoolean cancelled, Consumer<FetchProgress> onProgress)
            throws IOException;

    /**
     * Get the quote currency for a symbol.
     *
     * @param symbol The exchange-specific symbol
     * @return The quote currency
     */
    default QuoteCurrency getQuoteCurrency(String symbol) {
        return QuoteCurrency.detect(symbol, getExchange());
    }

    /**
     * Get max trades per request for this exchange.
     */
    int getMaxTradesPerRequest();

    /**
     * Get max candles per request for this exchange.
     */
    int getMaxCandlesPerRequest();
}
