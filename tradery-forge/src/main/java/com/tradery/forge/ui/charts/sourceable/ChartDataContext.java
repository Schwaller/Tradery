package com.tradery.forge.ui.charts.sourceable;

import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
import com.tradery.core.model.DataSourceSelection;
import com.tradery.core.model.Exchange;
import com.tradery.core.model.FootprintResult;

import java.util.List;
import java.util.Set;

/**
 * Provides data context for sourceable charts.
 * Encapsulates candles, aggTrades, and indicator engine access.
 */
public class ChartDataContext {

    private final List<Candle> candles;
    private final List<AggTrade> aggTrades;
    private final IndicatorEngine indicatorEngine;
    private final String symbol;
    private final String timeframe;
    private final long startTime;
    private final long endTime;

    public ChartDataContext(List<Candle> candles, List<AggTrade> aggTrades,
                           IndicatorEngine indicatorEngine,
                           String symbol, String timeframe,
                           long startTime, long endTime) {
        this.candles = candles;
        this.aggTrades = aggTrades;
        this.indicatorEngine = indicatorEngine;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public List<Candle> getCandles() {
        return candles;
    }

    public List<AggTrade> getAggTrades() {
        return aggTrades;
    }

    /**
     * Get filtered aggTrades based on data source selection.
     */
    public List<AggTrade> getFilteredAggTrades(DataSourceSelection sources) {
        if (aggTrades == null || sources == null || sources.isAllSources()) {
            return aggTrades;
        }

        Set<Exchange> enabledExchanges = sources.getEnabledExchanges();
        if (enabledExchanges.isEmpty()) {
            return aggTrades;
        }

        return aggTrades.stream()
            .filter(t -> t.exchange() == null || enabledExchanges.contains(t.exchange()))
            .toList();
    }

    public IndicatorEngine getIndicatorEngine() {
        return indicatorEngine;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public boolean hasCandles() {
        return candles != null && !candles.isEmpty();
    }

    public boolean hasAggTrades() {
        return aggTrades != null && !aggTrades.isEmpty();
    }

    /**
     * Get delta values per bar from filtered aggTrades.
     */
    public double[] getDeltaValues(DataSourceSelection sources) {
        if (!hasCandles() || !hasAggTrades()) {
            return new double[0];
        }

        List<AggTrade> filtered = getFilteredAggTrades(sources);
        double[] deltas = new double[candles.size()];

        // Group aggTrades by bar
        int currentBarIndex = 0;
        long barEndTime = candles.get(0).timestamp() + getIntervalMs();

        for (AggTrade trade : filtered) {
            // Find the bar this trade belongs to
            while (currentBarIndex < candles.size() - 1 &&
                   trade.timestamp() >= candles.get(currentBarIndex + 1).timestamp()) {
                currentBarIndex++;
                if (currentBarIndex < candles.size()) {
                    barEndTime = candles.get(currentBarIndex).timestamp() + getIntervalMs();
                }
            }

            if (currentBarIndex < deltas.length) {
                deltas[currentBarIndex] += trade.delta();
            }
        }

        return deltas;
    }

    /**
     * Get CVD (Cumulative Volume Delta) values per bar.
     */
    public double[] getCvdValues(DataSourceSelection sources) {
        double[] deltas = getDeltaValues(sources);
        double[] cvd = new double[deltas.length];

        double cumulative = 0;
        for (int i = 0; i < deltas.length; i++) {
            cumulative += deltas[i];
            cvd[i] = cumulative;
        }

        return cvd;
    }

    /**
     * Get volume values per bar from filtered aggTrades.
     */
    public double[] getVolumeValues(DataSourceSelection sources) {
        if (!hasCandles()) {
            return new double[0];
        }

        // If no aggTrades or using all sources, use candle volume
        if (!hasAggTrades() || sources == null || sources.isAllSources()) {
            return candles.stream()
                .mapToDouble(Candle::volume)
                .toArray();
        }

        // Calculate from filtered aggTrades
        List<AggTrade> filtered = getFilteredAggTrades(sources);
        double[] volumes = new double[candles.size()];

        int currentBarIndex = 0;
        for (AggTrade trade : filtered) {
            while (currentBarIndex < candles.size() - 1 &&
                   trade.timestamp() >= candles.get(currentBarIndex + 1).timestamp()) {
                currentBarIndex++;
            }

            if (currentBarIndex < volumes.length) {
                volumes[currentBarIndex] += trade.quantity();
            }
        }

        return volumes;
    }

    /**
     * Get whale delta values (trades above threshold).
     */
    public double[] getWhaleDeltaValues(DataSourceSelection sources, double threshold) {
        if (!hasCandles() || !hasAggTrades()) {
            return new double[0];
        }

        List<AggTrade> filtered = getFilteredAggTrades(sources);
        double[] whaleDeltas = new double[candles.size()];

        int currentBarIndex = 0;
        for (AggTrade trade : filtered) {
            while (currentBarIndex < candles.size() - 1 &&
                   trade.timestamp() >= candles.get(currentBarIndex + 1).timestamp()) {
                currentBarIndex++;
            }

            // Check if this is a whale trade (value above threshold)
            double tradeValue = trade.price() * trade.quantity();
            if (tradeValue >= threshold && currentBarIndex < whaleDeltas.length) {
                whaleDeltas[currentBarIndex] += trade.delta();
            }
        }

        return whaleDeltas;
    }

    /**
     * Get trade count values per bar.
     */
    public int[] getTradeCountValues(DataSourceSelection sources) {
        if (!hasCandles() || !hasAggTrades()) {
            return new int[0];
        }

        List<AggTrade> filtered = getFilteredAggTrades(sources);
        int[] counts = new int[candles.size()];

        int currentBarIndex = 0;
        for (AggTrade trade : filtered) {
            while (currentBarIndex < candles.size() - 1 &&
                   trade.timestamp() >= candles.get(currentBarIndex + 1).timestamp()) {
                currentBarIndex++;
            }

            if (currentBarIndex < counts.length) {
                counts[currentBarIndex]++;
            }
        }

        return counts;
    }

    private long getIntervalMs() {
        return switch (timeframe) {
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
            default -> 3_600_000L;
        };
    }
}
