package com.tradery.indicators;

import com.tradery.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Footprint chart calculation engine.
 *
 * Aggregates AggTrades into price-level buckets per candle, calculating:
 * - Buy/sell volume per level
 * - Delta and imbalances
 * - POC, VAH, VAL
 * - Stacked imbalances
 * - Per-exchange breakdown
 *
 * Uses auto-calculated tick size based on ATR for consistent bucketing.
 */
public class FootprintIndicator {

    private static final Logger log = LoggerFactory.getLogger(FootprintIndicator.class);

    /**
     * "Nice" tick sizes for human-readable price levels.
     */
    private static final double[] NICE_TICKS = {
        0.01, 0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000
    };

    /**
     * Default target number of buckets per candle.
     */
    private static final int DEFAULT_TARGET_BUCKETS = 20;

    /**
     * Calculate footprints for all candles using auto tick size.
     *
     * @param candles List of candles
     * @param aggTrades List of aggregated trades
     * @param resolution Candle timeframe (e.g., "1h")
     * @return FootprintResult containing all footprints
     */
    public static FootprintResult calculate(List<Candle> candles, List<AggTrade> aggTrades, String resolution) {
        return calculate(candles, aggTrades, resolution, DEFAULT_TARGET_BUCKETS, null, null);
    }

    /**
     * Calculate footprints with custom settings.
     *
     * @param candles List of candles
     * @param aggTrades List of aggregated trades
     * @param resolution Candle timeframe
     * @param targetBuckets Target number of buckets per candle
     * @param fixedTickSize Optional fixed tick size (null for auto)
     * @param exchangeFilter Optional set of exchanges to include (null for all)
     * @return FootprintResult containing all footprints
     */
    public static FootprintResult calculate(
            List<Candle> candles,
            List<AggTrade> aggTrades,
            String resolution,
            int targetBuckets,
            Double fixedTickSize,
            Set<Exchange> exchangeFilter) {

        if (candles == null || candles.isEmpty()) {
            return new FootprintResult.Builder().build();
        }

        // Calculate ATR for auto tick size
        double atr = calculateATR(candles, 14);
        double tickSize = fixedTickSize != null ? fixedTickSize : calculateTickSize(atr, targetBuckets);

        log.debug("Footprint calculation: ATR={}, tickSize={}, candles={}, trades={}",
            String.format("%.2f", atr), String.format("%.2f", tickSize),
            candles.size(), aggTrades != null ? aggTrades.size() : 0);

        // Build bar timestamp index for efficient trade assignment
        long[] barTimestamps = new long[candles.size()];
        long intervalMs = getIntervalMs(resolution);
        for (int i = 0; i < candles.size(); i++) {
            barTimestamps[i] = candles.get(i).timestamp();
        }

        // Group trades by bar index
        Map<Integer, List<AggTrade>> tradesByBar = groupTradesByBar(aggTrades, barTimestamps, intervalMs, exchangeFilter);

        // Calculate footprint for each candle
        String symbol = null; // Will be set from trades
        FootprintResult.Builder resultBuilder = new FootprintResult.Builder()
            .tickSize(tickSize)
            .timeframe(resolution);

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            List<AggTrade> barTrades = tradesByBar.getOrDefault(i, Collections.emptyList());

            Footprint footprint = calculateFootprintForBar(candle, barTrades, tickSize, i);
            resultBuilder.addFootprint(footprint);

            // Get symbol from first trade
            if (symbol == null && !barTrades.isEmpty()) {
                symbol = barTrades.get(0).rawSymbol();
            }
        }

        if (symbol != null) {
            resultBuilder.symbol(symbol);
        }

        return resultBuilder.build();
    }

    /**
     * Calculate footprint for a single bar.
     */
    private static Footprint calculateFootprintForBar(
            Candle candle,
            List<AggTrade> trades,
            double tickSize,
            int barIndex) {

        Footprint.Builder builder = new Footprint.Builder()
            .timestamp(candle.timestamp())
            .barIndex(barIndex)
            .high(candle.high())
            .low(candle.low())
            .tickSize(tickSize);

        if (trades.isEmpty()) {
            // Create empty footprint with buckets covering the candle range
            createEmptyBuckets(candle, tickSize, builder);
            return builder.build();
        }

        // Group trades by price bucket
        Map<Double, FootprintBucket.Builder> bucketBuilders = new TreeMap<>();
        // First, create empty buckets covering the full candle range
        // This ensures boxes align with the candle's high/low visually
        double low = snapToTickSize(candle.low(), tickSize);
        double high = snapToTickSize(candle.high(), tickSize);
        for (double price = low; price <= high + tickSize / 2; price += tickSize) {
            bucketBuilders.computeIfAbsent(price, FootprintBucket.Builder::new);
        }

        for (AggTrade trade : trades) {
            double bucketPrice = snapToTickSize(trade.price(), tickSize);
            FootprintBucket.Builder bucketBuilder = bucketBuilders.computeIfAbsent(
                bucketPrice, FootprintBucket.Builder::new);

            Exchange exchange = trade.exchange() != null ? trade.exchange() : Exchange.BINANCE;

            if (trade.isBuyerMaker()) {
                // Seller is taker (aggressive sell)
                bucketBuilder.addSellVolume(exchange, trade.quantity());
            } else {
                // Buyer is taker (aggressive buy)
                bucketBuilder.addBuyVolume(exchange, trade.quantity());
            }

            // Track per-exchange delta
            builder.addDelta(exchange, trade.delta());
        }

        // Build all buckets
        for (FootprintBucket.Builder bucketBuilder : bucketBuilders.values()) {
            builder.addBucket(bucketBuilder.build());
        }

        return builder.build();
    }

    /**
     * Group trades by bar index using binary search.
     */
    private static Map<Integer, List<AggTrade>> groupTradesByBar(
            List<AggTrade> aggTrades,
            long[] barTimestamps,
            long intervalMs,
            Set<Exchange> exchangeFilter) {

        Map<Integer, List<AggTrade>> result = new HashMap<>();

        if (aggTrades == null || aggTrades.isEmpty() || barTimestamps.length == 0) {
            return result;
        }

        int currentBarIndex = 0;
        long currentBarEnd = barTimestamps[0] + intervalMs;

        for (AggTrade trade : aggTrades) {
            // Apply exchange filter
            if (exchangeFilter != null && !exchangeFilter.isEmpty()) {
                if (trade.exchange() != null && !exchangeFilter.contains(trade.exchange())) {
                    continue;
                }
            }

            // Find the bar this trade belongs to
            while (currentBarIndex < barTimestamps.length - 1 &&
                   trade.timestamp() >= barTimestamps[currentBarIndex + 1]) {
                currentBarIndex++;
                currentBarEnd = barTimestamps[currentBarIndex] + intervalMs;
            }

            // Check if trade is within current bar
            if (trade.timestamp() >= barTimestamps[currentBarIndex] &&
                trade.timestamp() < currentBarEnd) {
                result.computeIfAbsent(currentBarIndex, k -> new ArrayList<>()).add(trade);
            }
        }

        return result;
    }

    /**
     * Create empty buckets covering the candle's price range.
     */
    private static void createEmptyBuckets(Candle candle, double tickSize, Footprint.Builder builder) {
        double low = snapToTickSize(candle.low(), tickSize);
        double high = snapToTickSize(candle.high(), tickSize);

        for (double price = low; price <= high + tickSize / 2; price += tickSize) {
            builder.addBucket(FootprintBucket.empty(price));
        }
    }

    /**
     * Snap a price to the nearest tick size.
     */
    private static double snapToTickSize(double price, double tickSize) {
        return Math.round(price / tickSize) * tickSize;
    }

    /**
     * Calculate auto tick size based on ATR.
     */
    public static double calculateTickSize(double atr, int targetBuckets) {
        if (atr <= 0 || targetBuckets <= 0) {
            return 1.0; // Fallback
        }

        double idealTick = atr / targetBuckets;

        // Find nearest nice tick size
        double bestTick = NICE_TICKS[0];
        double bestDiff = Math.abs(idealTick - bestTick);

        for (double tick : NICE_TICKS) {
            double diff = Math.abs(idealTick - tick);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestTick = tick;
            }
        }

        return bestTick;
    }

    /**
     * Calculate ATR (Average True Range) for tick size calculation.
     */
    private static double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < 2) {
            // Fallback: use high-low range of last candle
            Candle last = candles.get(candles.size() - 1);
            return last.high() - last.low();
        }

        double sum = 0;
        int count = 0;
        int start = Math.max(1, candles.size() - period);

        for (int i = start; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle prev = candles.get(i - 1);

            double tr = Math.max(
                current.high() - current.low(),
                Math.max(
                    Math.abs(current.high() - prev.close()),
                    Math.abs(current.low() - prev.close())
                )
            );

            sum += tr;
            count++;
        }

        return count > 0 ? sum / count : 1.0;
    }

    /**
     * Get interval duration in milliseconds.
     */
    private static long getIntervalMs(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "3m" -> 180_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "30m" -> 1_800_000L;
            case "1h" -> 3_600_000L;
            case "2h" -> 7_200_000L;
            case "4h" -> 14_400_000L;
            case "6h" -> 21_600_000L;
            case "8h" -> 28_800_000L;
            case "12h" -> 43_200_000L;
            case "1d" -> 86_400_000L;
            case "3d" -> 259_200_000L;
            case "1w" -> 604_800_000L;
            default -> 3_600_000L;
        };
    }
}
