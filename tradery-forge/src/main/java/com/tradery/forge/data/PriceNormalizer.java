package com.tradery.forge.data;

import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Exchange;
import com.tradery.core.model.PriceNormalizationMode;
import com.tradery.core.model.QuoteCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normalizes prices across different quote currencies and exchanges.
 * Used when aggregating orderflow data from multiple exchanges.
 *
 * Normalization modes:
 * - RAW: No normalization, use original prices
 * - USDT_AS_USD: Treat USDT/USDC as $1.00 (simple, fast)
 * - LIVE_RATE: Use actual USDT/USD exchange rates
 * - REFERENCE_EXCHANGE: Align to a reference exchange's price levels
 */
public class PriceNormalizer {

    private static final Logger log = LoggerFactory.getLogger(PriceNormalizer.class);

    private static PriceNormalizer instance;

    private final StablecoinRateProvider rateProvider;
    private final ExchangeConfig exchangeConfig;

    // Cache for reference prices (exchange -> timestamp -> price)
    private final Map<Exchange, Map<Long, Double>> referencePrices = new ConcurrentHashMap<>();

    private PriceNormalizer() {
        this.rateProvider = StablecoinRateProvider.getInstance();
        this.exchangeConfig = ExchangeConfig.getInstance();
    }

    public static synchronized PriceNormalizer getInstance() {
        if (instance == null) {
            instance = new PriceNormalizer();
        }
        return instance;
    }

    /**
     * Normalize a price based on the configured normalization mode.
     *
     * @param price The raw price
     * @param quoteCurrency The quote currency of the price
     * @param exchange The source exchange
     * @param timestamp The timestamp
     * @return The normalized price
     */
    public double normalize(double price, QuoteCurrency quoteCurrency, Exchange exchange, long timestamp) {
        PriceNormalizationMode mode = exchangeConfig.getNormalizationMode();
        return normalize(price, quoteCurrency, exchange, timestamp, mode);
    }

    /**
     * Normalize a price using a specific normalization mode.
     *
     * @param price The raw price
     * @param quoteCurrency The quote currency of the price
     * @param exchange The source exchange
     * @param timestamp The timestamp
     * @param mode The normalization mode
     * @return The normalized price
     */
    public double normalize(double price, QuoteCurrency quoteCurrency, Exchange exchange,
                           long timestamp, PriceNormalizationMode mode) {
        return switch (mode) {
            case RAW -> price;
            case USDT_AS_USD -> normalizeUsdtAsUsd(price, quoteCurrency);
            case LIVE_RATE -> normalizeLiveRate(price, quoteCurrency, timestamp);
            case REFERENCE_EXCHANGE -> normalizeToReference(price, exchange, timestamp);
        };
    }

    /**
     * Normalize an AggTrade, returning a copy with normalized price set.
     */
    public AggTrade normalizeTrade(AggTrade trade) {
        if (trade.exchange() == null) {
            return trade; // Already default (Binance)
        }

        QuoteCurrency quoteCurrency = QuoteCurrency.detect(trade.rawSymbol(), trade.exchange());
        if (quoteCurrency == null) {
            quoteCurrency = QuoteCurrency.USDT; // Default
        }

        double normalizedPrice = normalize(trade.price(), quoteCurrency, trade.exchange(), trade.timestamp());
        return trade.withNormalizedPrice(normalizedPrice);
    }

    /**
     * Normalize a list of AggTrades in place.
     */
    public List<AggTrade> normalizeTrades(List<AggTrade> trades) {
        return trades.stream()
            .map(this::normalizeTrade)
            .toList();
    }

    // ========== Normalization Strategies ==========

    /**
     * Simple normalization: treat USDT/USDC as $1.00.
     */
    private double normalizeUsdtAsUsd(double price, QuoteCurrency quoteCurrency) {
        // All USD-denominated currencies are treated as equivalent
        if (quoteCurrency.isUsdDenominated()) {
            return price;
        }
        // For non-USD (EUR, etc.), use a rough conversion
        return switch (quoteCurrency) {
            case EUR -> price * 1.08; // Rough EUR/USD rate
            default -> price;
        };
    }

    /**
     * Use live/historical USDT/USD rates.
     */
    private double normalizeLiveRate(double price, QuoteCurrency quoteCurrency, long timestamp) {
        if (quoteCurrency == QuoteCurrency.USD) {
            return price;
        }
        double rate = rateProvider.getRate(quoteCurrency, timestamp);
        return price * rate;
    }

    /**
     * Normalize to reference exchange price levels.
     * Useful for visualizing cross-exchange orderflow on same chart.
     */
    private double normalizeToReference(double price, Exchange exchange, long timestamp) {
        Exchange refExchange = exchangeConfig.getReferenceExchange();

        if (exchange == refExchange) {
            return price; // Already reference
        }

        // Get reference price at this timestamp
        Double refPrice = getReferencePrice(refExchange, timestamp);
        Double exchPrice = getReferencePrice(exchange, timestamp);

        if (refPrice == null || exchPrice == null || exchPrice == 0) {
            return price; // No reference available
        }

        // Scale the price to align with reference exchange
        double ratio = refPrice / exchPrice;
        return price * ratio;
    }

    /**
     * Get or fetch reference price for an exchange at a timestamp.
     */
    private Double getReferencePrice(Exchange exchange, long timestamp) {
        // Round to nearest minute for caching
        long minuteKey = (timestamp / 60_000) * 60_000;

        Map<Long, Double> priceMap = referencePrices.computeIfAbsent(exchange,
            k -> new ConcurrentHashMap<>());

        return priceMap.get(minuteKey);
    }

    /**
     * Set a reference price (called when processing candles).
     */
    public void setReferencePrice(Exchange exchange, long timestamp, double price) {
        long minuteKey = (timestamp / 60_000) * 60_000;
        referencePrices.computeIfAbsent(exchange, k -> new ConcurrentHashMap<>())
            .put(minuteKey, price);
    }

    /**
     * Clear reference price cache.
     */
    public void clearReferenceCache() {
        referencePrices.clear();
    }

    /**
     * Get the display name for a normalization mode.
     */
    public static String getModeDescription(PriceNormalizationMode mode) {
        return mode.getDescription();
    }
}
