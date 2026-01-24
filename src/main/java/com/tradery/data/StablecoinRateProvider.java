package com.tradery.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.model.QuoteCurrency;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Provides stablecoin exchange rates (USDT/USD, USDC/USD) for price normalization.
 * Fetches rates from exchange APIs and caches them.
 *
 * In most market conditions, USDT and USDC trade very close to $1.00.
 * However, during depeg events (like the 2023 USDC depeg), rates can deviate significantly.
 */
public class StablecoinRateProvider {

    private static final Logger log = LoggerFactory.getLogger(StablecoinRateProvider.class);

    // Binance USDT/TUSD pair as proxy for USDT/USD (usually ~$1.00)
    private static final String USDT_RATE_URL = "https://api.binance.com/api/v3/ticker/price?symbol=USDCUSDT";

    private static StablecoinRateProvider instance;

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;

    // Current rates (live)
    private final Map<QuoteCurrency, Double> currentRates = new ConcurrentHashMap<>();

    // Historical rates cache: timestamp -> (currency -> rate)
    private final Map<Long, Map<QuoteCurrency, Double>> historicalRates = new ConcurrentHashMap<>();

    // Rate update interval
    private static final long UPDATE_INTERVAL_MS = 60_000; // 1 minute

    private volatile boolean running = false;

    private StablecoinRateProvider() {
        this.client = HttpClientFactory.getClient();
        this.mapper = HttpClientFactory.getMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StablecoinRateProvider");
            t.setDaemon(true);
            return t;
        });

        // Initialize default rates (assume $1.00)
        currentRates.put(QuoteCurrency.USD, 1.0);
        currentRates.put(QuoteCurrency.USDT, 1.0);
        currentRates.put(QuoteCurrency.USDC, 1.0);
        currentRates.put(QuoteCurrency.EUR, 1.08); // Rough estimate
    }

    public static synchronized StablecoinRateProvider getInstance() {
        if (instance == null) {
            instance = new StablecoinRateProvider();
        }
        return instance;
    }

    /**
     * Start fetching live rates.
     */
    public void start() {
        if (running) return;
        running = true;

        // Fetch immediately, then periodically
        scheduler.execute(this::fetchLiveRates);
        scheduler.scheduleAtFixedRate(
            this::fetchLiveRates,
            UPDATE_INTERVAL_MS,
            UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        log.info("StablecoinRateProvider started");
    }

    /**
     * Stop fetching rates.
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        log.info("StablecoinRateProvider stopped");
    }

    /**
     * Get the current rate for converting a currency to USD.
     *
     * @param currency The source currency
     * @return The rate (multiply by this to get USD value)
     */
    public double getCurrentRate(QuoteCurrency currency) {
        return currentRates.getOrDefault(currency, 1.0);
    }

    /**
     * Get the historical rate for a timestamp.
     * Falls back to current rate if historical data not available.
     *
     * @param currency The source currency
     * @param timestamp The timestamp in milliseconds
     * @return The rate at that time
     */
    public double getRate(QuoteCurrency currency, long timestamp) {
        // Round to nearest hour for historical lookup
        long hourKey = (timestamp / 3600_000) * 3600_000;

        Map<QuoteCurrency, Double> hourRates = historicalRates.get(hourKey);
        if (hourRates != null && hourRates.containsKey(currency)) {
            return hourRates.get(currency);
        }

        // Fall back to current rate
        return getCurrentRate(currency);
    }

    /**
     * Convert a price from one currency to USD.
     *
     * @param price The price in the source currency
     * @param currency The source currency
     * @param timestamp The timestamp (for historical conversion)
     * @return The price in USD
     */
    public double toUsd(double price, QuoteCurrency currency, long timestamp) {
        if (currency == QuoteCurrency.USD) {
            return price;
        }
        return price * getRate(currency, timestamp);
    }

    /**
     * Fetch live stablecoin rates from exchanges.
     */
    private void fetchLiveRates() {
        try {
            // USDC/USDT rate from Binance
            // If USDCUSDT = 0.999, then USDC is worth 0.999 USDT
            // Assuming USDT = $1.00, USDC = $0.999
            double usdcUsdtRate = fetchBinancePrice("USDCUSDT");
            if (usdcUsdtRate > 0) {
                // In this pair, if price < 1.0, USDC is worth less than USDT
                currentRates.put(QuoteCurrency.USDC, usdcUsdtRate);
            }

            // For now, assume USDT = $1.00 (Binance doesn't have direct USDT/USD)
            // In production, you'd want to fetch from an exchange with fiat pairs
            currentRates.put(QuoteCurrency.USDT, 1.0);
            currentRates.put(QuoteCurrency.USD, 1.0);

            // Store as historical rate
            long hourKey = (System.currentTimeMillis() / 3600_000) * 3600_000;
            historicalRates.computeIfAbsent(hourKey, k -> new ConcurrentHashMap<>())
                .putAll(currentRates);

            // Clean up old historical rates (keep last 30 days)
            long cutoff = System.currentTimeMillis() - (30L * 24 * 3600_000);
            historicalRates.keySet().removeIf(ts -> ts < cutoff);

            log.debug("Updated stablecoin rates: USDT={}, USDC={}",
                currentRates.get(QuoteCurrency.USDT),
                currentRates.get(QuoteCurrency.USDC));

        } catch (Exception e) {
            log.warn("Failed to fetch stablecoin rates: {}", e.getMessage());
        }
    }

    /**
     * Fetch a single price from Binance.
     */
    private double fetchBinancePrice(String symbol) throws IOException {
        String url = "https://api.binance.com/api/v3/ticker/price?symbol=" + symbol;
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Binance API error: " + response.code());
            }
            JsonNode root = mapper.readTree(response.body().string());
            return Double.parseDouble(root.get("price").asText());
        }
    }

    /**
     * Check if historical rate data is available for a time range.
     *
     * @param startTime Start timestamp in milliseconds
     * @param endTime End timestamp in milliseconds
     * @return true if data is available
     */
    public boolean hasHistoricalData(long startTime, long endTime) {
        long startHour = (startTime / 3600_000) * 3600_000;
        long endHour = (endTime / 3600_000) * 3600_000;

        for (long hour = startHour; hour <= endHour; hour += 3600_000) {
            if (!historicalRates.containsKey(hour)) {
                return false;
            }
        }
        return true;
    }
}
