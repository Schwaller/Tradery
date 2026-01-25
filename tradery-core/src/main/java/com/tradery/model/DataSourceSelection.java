package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Represents a selection of data sources (exchanges and market types).
 * Used for configuring charts and indicators to use specific exchange data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSourceSelection {

    @JsonProperty("sources")
    private List<DataSource> sources = new ArrayList<>();

    @JsonProperty("combineMode")
    private CombineMode combineMode = CombineMode.COMBINED;

    @JsonProperty("priceNormalization")
    private PriceNormalizationMode priceNormalization = PriceNormalizationMode.USDT_AS_USD;

    /**
     * How to combine data from multiple sources.
     */
    public enum CombineMode {
        /** Sum values across all sources */
        COMBINED,
        /** Keep sources separate for comparison */
        SEPARATE,
        /** Show only the primary/first source */
        PRIMARY_ONLY
    }

    /**
     * A single data source (exchange + market type).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataSource {
        @JsonProperty("exchange")
        private Exchange exchange;

        @JsonProperty("marketType")
        private DataMarketType marketType;

        public DataSource() {}

        public DataSource(Exchange exchange, DataMarketType marketType) {
            this.exchange = exchange;
            this.marketType = marketType;
        }

        public Exchange getExchange() { return exchange; }
        public void setExchange(Exchange exchange) { this.exchange = exchange; }

        public DataMarketType getMarketType() { return marketType; }
        public void setMarketType(DataMarketType marketType) { this.marketType = marketType; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DataSource that = (DataSource) o;
            return exchange == that.exchange && marketType == that.marketType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(exchange, marketType);
        }

        @Override
        public String toString() {
            return exchange.getDisplayName() + " " + marketType.getDisplayName();
        }
    }

    // ========== Constructors ==========

    public DataSourceSelection() {}

    /**
     * Create a selection with a single source.
     */
    public static DataSourceSelection single(Exchange exchange, DataMarketType marketType) {
        DataSourceSelection selection = new DataSourceSelection();
        selection.sources.add(new DataSource(exchange, marketType));
        return selection;
    }

    /**
     * Create a selection with all enabled exchanges.
     */
    public static DataSourceSelection allEnabled() {
        DataSourceSelection selection = new DataSourceSelection();
        // Will be populated from ExchangeConfig when used
        return selection;
    }

    /**
     * Create a Binance-only selection (default).
     */
    public static DataSourceSelection binanceDefault() {
        return single(Exchange.BINANCE, DataMarketType.FUTURES_PERP);
    }

    // ========== Getters/Setters ==========

    public List<DataSource> getSources() { return sources; }
    public void setSources(List<DataSource> sources) { this.sources = sources; }

    public CombineMode getCombineMode() { return combineMode; }
    public void setCombineMode(CombineMode combineMode) { this.combineMode = combineMode; }

    public PriceNormalizationMode getPriceNormalization() { return priceNormalization; }
    public void setPriceNormalization(PriceNormalizationMode priceNormalization) {
        this.priceNormalization = priceNormalization;
    }

    // ========== Helper Methods ==========

    /**
     * Add a data source.
     */
    public void addSource(Exchange exchange, DataMarketType marketType) {
        DataSource source = new DataSource(exchange, marketType);
        if (!sources.contains(source)) {
            sources.add(source);
        }
    }

    /**
     * Remove a data source.
     */
    public void removeSource(Exchange exchange, DataMarketType marketType) {
        sources.remove(new DataSource(exchange, marketType));
    }

    /**
     * Check if a specific source is included.
     */
    public boolean hasSource(Exchange exchange, DataMarketType marketType) {
        return sources.contains(new DataSource(exchange, marketType));
    }

    /**
     * Check if data from an exchange is included (any market type).
     */
    public boolean hasExchange(Exchange exchange) {
        return sources.stream().anyMatch(s -> s.exchange == exchange);
    }

    /**
     * Get all exchanges in this selection.
     */
    public Set<Exchange> getExchanges() {
        Set<Exchange> exchanges = EnumSet.noneOf(Exchange.class);
        for (DataSource source : sources) {
            exchanges.add(source.exchange);
        }
        return exchanges;
    }

    /**
     * Check if this is a multi-exchange selection.
     */
    public boolean isMultiExchange() {
        return getExchanges().size() > 1;
    }

    /**
     * Get a display label for this selection.
     */
    public String getDisplayLabel() {
        if (sources.isEmpty()) {
            return "All Sources";
        }
        if (sources.size() == 1) {
            return sources.get(0).toString();
        }
        Set<Exchange> exchanges = getExchanges();
        if (exchanges.size() == 1) {
            // Multiple market types from same exchange
            Exchange ex = exchanges.iterator().next();
            return ex.getDisplayName() + " (multiple)";
        }
        // Multiple exchanges
        return exchanges.size() + " exchanges";
    }

    /**
     * Filter AggTrades by this selection.
     */
    public boolean matchesTrade(AggTrade trade) {
        if (sources.isEmpty()) {
            return true; // All sources
        }
        for (DataSource source : sources) {
            if (trade.exchange() == source.exchange &&
                trade.marketType() == source.marketType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this selection represents "all sources" (empty list).
     */
    public boolean isAllSources() {
        return sources.isEmpty();
    }

    /**
     * Get the set of enabled exchanges in this selection.
     * Returns all Exchange values if sources is empty.
     */
    public Set<Exchange> getEnabledExchanges() {
        if (sources.isEmpty()) {
            return EnumSet.allOf(Exchange.class);
        }
        return getExchanges();
    }

    /**
     * Get a short description for display in chart headers.
     */
    public String getShortDescription() {
        if (sources.isEmpty()) {
            return "All";
        }
        if (sources.size() == 1) {
            DataSource s = sources.get(0);
            return s.exchange.getShortName() + " " + s.marketType.getShortName();
        }
        Set<Exchange> exchanges = getExchanges();
        if (exchanges.size() == 1) {
            return exchanges.iterator().next().getShortName();
        }
        return exchanges.size() + "x";
    }
}
