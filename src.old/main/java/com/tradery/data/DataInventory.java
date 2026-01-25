package com.tradery.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks what data is cached locally per symbol/timeframe/type.
 * Provides coverage queries to avoid redundant API calls.
 * Thread-safe for concurrent access.
 */
public final class DataInventory {

    private static final Logger log = LoggerFactory.getLogger(DataInventory.class);
    private static final String INVENTORY_FILE = "inventory.json";

    // Coverage maps - key format documented per type
    private final Map<String, DateRangeSet> candleCoverage = new ConcurrentHashMap<>();   // "BTCUSDT:1h"
    private final Map<String, DateRangeSet> aggTradesCoverage = new ConcurrentHashMap<>(); // "BTCUSDT"
    private final Map<String, DateRangeSet> fundingCoverage = new ConcurrentHashMap<>();   // "BTCUSDT"
    private final Map<String, DateRangeSet> oiCoverage = new ConcurrentHashMap<>();        // "BTCUSDT"

    private final File dataDir;
    private final ObjectMapper mapper;

    public DataInventory(File dataDir) {
        this.dataDir = dataDir;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ========== Candle Coverage ==========

    private String candleKey(String symbol, String timeframe) {
        return symbol + ":" + timeframe;
    }

    public void recordCandleData(String symbol, String timeframe, long start, long end) {
        String key = candleKey(symbol, timeframe);
        candleCoverage.computeIfAbsent(key, k -> new DateRangeSet()).add(start, end);
    }

    public boolean hasCandleData(String symbol, String timeframe, long start, long end) {
        DateRangeSet coverage = candleCoverage.get(candleKey(symbol, timeframe));
        return coverage != null && coverage.contains(start, end);
    }

    public List<DateRangeSet.Range> getCandleGaps(String symbol, String timeframe, long start, long end) {
        DateRangeSet coverage = candleCoverage.get(candleKey(symbol, timeframe));
        if (coverage == null) {
            return List.of(new DateRangeSet.Range(start, end));
        }
        return coverage.findGaps(start, end);
    }

    // ========== AggTrades Coverage ==========

    public void recordAggTradesData(String symbol, long start, long end) {
        aggTradesCoverage.computeIfAbsent(symbol, k -> new DateRangeSet()).add(start, end);
    }

    public boolean hasAggTradesData(String symbol, long start, long end) {
        DateRangeSet coverage = aggTradesCoverage.get(symbol);
        return coverage != null && coverage.contains(start, end);
    }

    public List<DateRangeSet.Range> getAggTradesGaps(String symbol, long start, long end) {
        DateRangeSet coverage = aggTradesCoverage.get(symbol);
        if (coverage == null) {
            return List.of(new DateRangeSet.Range(start, end));
        }
        return coverage.findGaps(start, end);
    }

    // ========== Funding Coverage ==========

    public void recordFundingData(String symbol, long start, long end) {
        fundingCoverage.computeIfAbsent(symbol, k -> new DateRangeSet()).add(start, end);
    }

    public boolean hasFundingData(String symbol, long start, long end) {
        DateRangeSet coverage = fundingCoverage.get(symbol);
        return coverage != null && coverage.contains(start, end);
    }

    public List<DateRangeSet.Range> getFundingGaps(String symbol, long start, long end) {
        DateRangeSet coverage = fundingCoverage.get(symbol);
        if (coverage == null) {
            return List.of(new DateRangeSet.Range(start, end));
        }
        return coverage.findGaps(start, end);
    }

    // ========== Open Interest Coverage ==========

    public void recordOIData(String symbol, long start, long end) {
        oiCoverage.computeIfAbsent(symbol, k -> new DateRangeSet()).add(start, end);
    }

    public boolean hasOIData(String symbol, long start, long end) {
        DateRangeSet coverage = oiCoverage.get(symbol);
        return coverage != null && coverage.contains(start, end);
    }

    public List<DateRangeSet.Range> getOIGaps(String symbol, long start, long end) {
        DateRangeSet coverage = oiCoverage.get(symbol);
        if (coverage == null) {
            return List.of(new DateRangeSet.Range(start, end));
        }
        return coverage.findGaps(start, end);
    }

    // ========== Stats ==========

    public record CoverageStats(
        int candleSymbols,
        int aggTradesSymbols,
        int fundingSymbols,
        int oiSymbols,
        long totalCandleCoverageHours,
        long totalAggTradesCoverageHours
    ) {}

    public CoverageStats getStats() {
        long candleHours = candleCoverage.values().stream()
            .mapToLong(DateRangeSet::getTotalCoverage)
            .sum() / 3600000;
        long aggTradesHours = aggTradesCoverage.values().stream()
            .mapToLong(DateRangeSet::getTotalCoverage)
            .sum() / 3600000;

        return new CoverageStats(
            candleCoverage.size(),
            aggTradesCoverage.size(),
            fundingCoverage.size(),
            oiCoverage.size(),
            candleHours,
            aggTradesHours
        );
    }

    // ========== Persistence ==========

    /**
     * Save inventory state to disk for fast startup.
     */
    public void save() {
        try {
            File file = new File(dataDir, INVENTORY_FILE);
            Map<String, Object> state = new HashMap<>();

            state.put("candles", serializeCoverage(candleCoverage));
            state.put("aggTrades", serializeCoverage(aggTradesCoverage));
            state.put("funding", serializeCoverage(fundingCoverage));
            state.put("oi", serializeCoverage(oiCoverage));

            mapper.writeValue(file, state);
        } catch (IOException e) {
            log.warn("Failed to save DataInventory: {}", e.getMessage());
        }
    }

    private Map<String, List<long[]>> serializeCoverage(Map<String, DateRangeSet> coverage) {
        Map<String, List<long[]>> result = new HashMap<>();
        for (var entry : coverage.entrySet()) {
            List<long[]> ranges = entry.getValue().getRanges().stream()
                .map(r -> new long[]{r.start(), r.end()})
                .toList();
            result.put(entry.getKey(), ranges);
        }
        return result;
    }

    /**
     * Load inventory state from disk.
     */
    @SuppressWarnings("unchecked")
    public void load() {
        File file = new File(dataDir, INVENTORY_FILE);
        if (!file.exists()) {
            return;
        }

        try {
            Map<String, Object> state = mapper.readValue(file, Map.class);

            deserializeCoverage((Map<String, List<List<Number>>>) state.get("candles"), candleCoverage);
            deserializeCoverage((Map<String, List<List<Number>>>) state.get("aggTrades"), aggTradesCoverage);
            deserializeCoverage((Map<String, List<List<Number>>>) state.get("funding"), fundingCoverage);
            deserializeCoverage((Map<String, List<List<Number>>>) state.get("oi"), oiCoverage);

            CoverageStats stats = getStats();
            log.info("DataInventory loaded: {} candle symbols, {} aggTrades symbols, {}h candle coverage",
                stats.candleSymbols(), stats.aggTradesSymbols(), stats.totalCandleCoverageHours());
        } catch (IOException e) {
            log.warn("Failed to load DataInventory: {}", e.getMessage());
        }
    }

    private void deserializeCoverage(Map<String, List<List<Number>>> data, Map<String, DateRangeSet> target) {
        if (data == null) return;

        for (var entry : data.entrySet()) {
            DateRangeSet rangeSet = new DateRangeSet();
            for (List<Number> range : entry.getValue()) {
                if (range.size() >= 2) {
                    rangeSet.add(range.get(0).longValue(), range.get(1).longValue());
                }
            }
            target.put(entry.getKey(), rangeSet);
        }
    }

    /**
     * Clear all coverage data (for testing).
     */
    public void clear() {
        candleCoverage.clear();
        aggTradesCoverage.clear();
        fundingCoverage.clear();
        oiCoverage.clear();
    }
}
