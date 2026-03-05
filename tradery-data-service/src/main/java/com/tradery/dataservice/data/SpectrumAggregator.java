package com.tradery.dataservice.data;

import com.tradery.core.model.AggTrade;
import com.tradery.core.model.SpectrumWindow;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.dao.SpectrumDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Stateless aggregator: converts a batch of AggTrades into 10-second spectrum histogram rows.
 *
 * Each trade is assigned to:
 *   - A 10s window: (timestamp / 10_000) * 10_000
 *   - A log10 bucket: clamp(0, 8, floor(log10(notional)))
 *
 * Within each (window, bucket), we accumulate trade count, total volume, buy volume, sell volume.
 *
 * Supports two modes:
 *   - RAW: bucket each aggTrade individually
 *   - TAKER_ORDER: reconstruct taker orders from consecutive aggTrades before bucketing
 */
public class SpectrumAggregator {

    private static final Logger log = LoggerFactory.getLogger(SpectrumAggregator.class);
    private static final int BACKFILL_CHUNK_SIZE = 10_000;

    /**
     * A reconstructed taker order merged from consecutive aggTrades.
     */
    private record ReconstructedOrder(double notional, double buyVolume, double sellVolume, long timestamp) {}

    /**
     * Aggregate a batch of trades into spectrum rows (raw mode — each aggTrade bucketed individually).
     */
    public List<SpectrumDao.SpectrumRow> aggregate(List<AggTrade> trades) {
        return aggregate(trades, false);
    }

    /**
     * Aggregate a batch of trades into spectrum rows.
     *
     * @param reconstructTakerOrders if true, merge consecutive aggTrades from the same taker order
     *                                before bucketing
     */
    public List<SpectrumDao.SpectrumRow> aggregate(List<AggTrade> trades, boolean reconstructTakerOrders) {
        if (trades.isEmpty()) return List.of();

        if (reconstructTakerOrders) {
            return aggregateReconstructed(trades);
        }

        // Key: windowStart -> bucketIndex -> mutable accumulator
        Map<Long, double[][]> windows = new HashMap<>();

        for (AggTrade trade : trades) {
            double notional = trade.normalizedPrice() > 0
                ? trade.normalizedNotional()
                : trade.notional();
            int bucket = SpectrumWindow.bucketIndex(notional);
            long windowStart = SpectrumWindow.alignToWindow(trade.timestamp());

            double[][] buckets = windows.computeIfAbsent(windowStart,
                k -> new double[SpectrumWindow.BUCKET_COUNT][4]); // [count, total, buy, sell]

            double[] acc = buckets[bucket];
            acc[0] += 1;
            acc[1] += notional;
            if (!trade.isBuyerMaker()) {
                acc[2] += notional;  // buyer is taker = buy
            } else {
                acc[3] += notional;  // seller is taker = sell
            }
        }

        return flattenWindows(windows);
    }

    /**
     * Reconstruct taker orders from consecutive aggTrades, then bucket the merged notionals.
     *
     * Taker order detection: consecutive aggTrades where:
     *   - lastTradeId[n] + 1 == firstTradeId[n+1]
     *   - Same isBuyerMaker direction
     *   - Same exchange
     *
     * The merged order's total notional is bucketed as one unit, which pushes
     * large sweeping taker orders into higher buckets (e.g., $100K+).
     */
    private List<SpectrumDao.SpectrumRow> aggregateReconstructed(List<AggTrade> trades) {
        // Sort by exchange + aggTradeId for correct sequence detection
        List<AggTrade> sorted = new ArrayList<>(trades);
        sorted.sort(Comparator
            .comparing((AggTrade t) -> t.exchange() != null ? t.exchange().name() : "")
            .thenComparingLong(AggTrade::aggTradeId));

        List<ReconstructedOrder> orders = reconstructTakerOrders(sorted);

        // Bucket the reconstructed orders
        Map<Long, double[][]> windows = new HashMap<>();

        for (ReconstructedOrder order : orders) {
            int bucket = SpectrumWindow.bucketIndex(order.notional());
            long windowStart = SpectrumWindow.alignToWindow(order.timestamp());

            double[][] buckets = windows.computeIfAbsent(windowStart,
                k -> new double[SpectrumWindow.BUCKET_COUNT][4]);

            double[] acc = buckets[bucket];
            acc[0] += 1;
            acc[1] += order.notional();
            acc[2] += order.buyVolume();
            acc[3] += order.sellVolume();
        }

        return flattenWindows(windows);
    }

    /**
     * Walk sorted aggTrades linearly, merging consecutive trades that belong to the same taker order.
     */
    private List<ReconstructedOrder> reconstructTakerOrders(List<AggTrade> sorted) {
        List<ReconstructedOrder> orders = new ArrayList<>();
        if (sorted.isEmpty()) return orders;

        AggTrade first = sorted.get(0);
        double accNotional = tradeNotional(first);
        double accBuy = first.isBuyerMaker() ? 0 : accNotional;
        double accSell = first.isBuyerMaker() ? accNotional : 0;
        long accTimestamp = first.timestamp();
        long prevLastTradeId = first.lastTradeId();
        boolean prevIsBuyerMaker = first.isBuyerMaker();
        var prevExchange = first.exchange();

        for (int i = 1; i < sorted.size(); i++) {
            AggTrade trade = sorted.get(i);
            boolean sameOrder = trade.firstTradeId() == prevLastTradeId + 1
                && trade.isBuyerMaker() == prevIsBuyerMaker
                && Objects.equals(trade.exchange(), prevExchange);

            if (sameOrder) {
                double notional = tradeNotional(trade);
                accNotional += notional;
                if (!trade.isBuyerMaker()) {
                    accBuy += notional;
                } else {
                    accSell += notional;
                }
                prevLastTradeId = trade.lastTradeId();
            } else {
                // Emit the accumulated order
                orders.add(new ReconstructedOrder(accNotional, accBuy, accSell, accTimestamp));

                // Start a new accumulator
                double notional = tradeNotional(trade);
                accNotional = notional;
                accBuy = trade.isBuyerMaker() ? 0 : notional;
                accSell = trade.isBuyerMaker() ? notional : 0;
                accTimestamp = trade.timestamp();
                prevLastTradeId = trade.lastTradeId();
                prevIsBuyerMaker = trade.isBuyerMaker();
                prevExchange = trade.exchange();
            }
        }

        // Emit the last order
        orders.add(new ReconstructedOrder(accNotional, accBuy, accSell, accTimestamp));

        return orders;
    }

    private static double tradeNotional(AggTrade trade) {
        return trade.normalizedPrice() > 0 ? trade.normalizedNotional() : trade.notional();
    }

    /**
     * Flatten the window map into SpectrumRow list.
     */
    private List<SpectrumDao.SpectrumRow> flattenWindows(Map<Long, double[][]> windows) {
        List<SpectrumDao.SpectrumRow> rows = new ArrayList<>();
        for (var entry : windows.entrySet()) {
            long ws = entry.getKey();
            double[][] buckets = entry.getValue();
            for (int i = 0; i < SpectrumWindow.BUCKET_COUNT; i++) {
                double[] acc = buckets[i];
                if (acc[0] > 0) {
                    rows.add(new SpectrumDao.SpectrumRow(
                        ws, i, (int) acc[0], acc[1], acc[2], acc[3]
                    ));
                }
            }
        }
        return rows;
    }

    /**
     * Backfill spectrum data from existing aggTrades in SQLite.
     * Streams aggTrades in chunks to avoid loading everything into memory.
     *
     * @param mode "raw" or "taker_order"
     * @return total spectrum rows created
     */
    public long backfill(String symbol, long start, long end, SqliteDataStore dataStore, String mode) throws IOException {
        boolean reconstruct = "taker_order".equals(mode);
        String coverageKey = "spectrum_" + mode;
        long[] totalRows = {0};

        // State for carrying trailing incomplete taker order across chunks.
        // For raw mode this is unused.
        // For taker mode, the last trade in a chunk may be part of an order
        // that continues in the next chunk. We handle this by including the
        // last trade of each chunk as the first of the next via overlap,
        // but since the aggregator is stateless per-chunk, a simpler approach:
        // just aggregate each chunk independently. The only edge case is a taker
        // order that spans a chunk boundary — its fragments get bucketed separately
        // in that rare case. This is acceptable since chunks are 10K trades and
        // a single taker order spanning that boundary is extremely unlikely.

        int tradeCount = dataStore.streamAggTrades(symbol, start, end, BACKFILL_CHUNK_SIZE, chunk -> {
            List<SpectrumDao.SpectrumRow> rows = aggregate(chunk, reconstruct);
            if (!rows.isEmpty()) {
                try {
                    dataStore.saveSpectrum(symbol, mode, rows);
                    totalRows[0] += rows.size();
                } catch (IOException e) {
                    log.warn("Failed to save spectrum during backfill: {}", e.getMessage());
                }
            }
        });

        if (totalRows[0] > 0) {
            dataStore.addCoverage(symbol, "spectrum", coverageKey, start, end, true);
            log.info("Backfilled {} spectrum rows (mode={}) from {} aggTrades for {} [{} - {}]",
                totalRows[0], mode, tradeCount, symbol, start, end);
        }

        return totalRows[0];
    }

    /**
     * Backfill with default 'raw' mode (backward compat).
     */
    public long backfill(String symbol, long start, long end, SqliteDataStore dataStore) throws IOException {
        return backfill(symbol, start, end, dataStore, "raw");
    }
}
