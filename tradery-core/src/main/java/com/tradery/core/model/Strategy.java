package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Trading strategy with DSL-based entry/exit conditions.
 * Stored as JSON in ~/.tradery/strategies/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Strategy implements Identifiable {

    // Identity
    private String id;
    private String name;
    private String description;
    private String notes;

    // Preset tracking
    private String presetId;
    private String presetVersion;

    // Grouped settings
    private EntrySettings entrySettings = new EntrySettings();
    private ExitSettings exitSettings = new ExitSettings();
    private BacktestSettings backtestSettings = new BacktestSettings();
    private PhaseSettings phaseSettings = new PhaseSettings();
    private HoopPatternSettings hoopPatternSettings = new HoopPatternSettings();
    private OrderflowSettings orderflowSettings = new OrderflowSettings();

    // Metadata
    private boolean enabled;
    private Instant created;
    private Instant updated;

    public Strategy() {
        // For Jackson
    }

    public Strategy(String id, String name, String description, String entry, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.entrySettings = EntrySettings.of(entry);
        this.enabled = enabled;
        this.created = Instant.now();
        this.updated = Instant.now();
    }

    // Identity getters/setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updated = Instant.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updated = Instant.now();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        this.updated = Instant.now();
    }

    // Preset getters/setters

    public String getPresetId() {
        return presetId;
    }

    public void setPresetId(String presetId) {
        this.presetId = presetId;
    }

    public String getPresetVersion() {
        return presetVersion;
    }

    public void setPresetVersion(String presetVersion) {
        this.presetVersion = presetVersion;
    }

    @JsonIgnore
    public boolean isPreset() {
        return presetId != null && !presetId.isEmpty();
    }

    // Entry settings

    public EntrySettings getEntrySettings() {
        if (entrySettings == null) {
            entrySettings = new EntrySettings();
        }
        return entrySettings;
    }

    public void setEntrySettings(EntrySettings entrySettings) {
        this.entrySettings = entrySettings != null ? entrySettings : new EntrySettings();
        this.updated = Instant.now();
    }

    // Exit settings

    public ExitSettings getExitSettings() {
        if (exitSettings == null) {
            exitSettings = new ExitSettings();
        }
        return exitSettings;
    }

    public void setExitSettings(ExitSettings exitSettings) {
        this.exitSettings = exitSettings != null ? exitSettings : new ExitSettings();
        this.updated = Instant.now();
    }

    // Backtest settings

    public BacktestSettings getBacktestSettings() {
        if (backtestSettings == null) {
            backtestSettings = new BacktestSettings();
        }
        return backtestSettings;
    }

    public void setBacktestSettings(BacktestSettings backtestSettings) {
        this.backtestSettings = backtestSettings != null ? backtestSettings : new BacktestSettings();
        this.updated = Instant.now();
    }

    // Phase settings

    public PhaseSettings getPhaseSettings() {
        if (phaseSettings == null) {
            phaseSettings = new PhaseSettings();
        }
        return phaseSettings;
    }

    public void setPhaseSettings(PhaseSettings phaseSettings) {
        this.phaseSettings = phaseSettings != null ? phaseSettings : new PhaseSettings();
        this.updated = Instant.now();
    }

    // Hoop pattern settings

    public HoopPatternSettings getHoopPatternSettings() {
        if (hoopPatternSettings == null) {
            hoopPatternSettings = new HoopPatternSettings();
        }
        return hoopPatternSettings;
    }

    public void setHoopPatternSettings(HoopPatternSettings hoopPatternSettings) {
        this.hoopPatternSettings = hoopPatternSettings != null ? hoopPatternSettings : new HoopPatternSettings();
        this.updated = Instant.now();
    }

    // Orderflow settings

    public OrderflowSettings getOrderflowSettings() {
        if (orderflowSettings == null) {
            orderflowSettings = new OrderflowSettings();
        }
        return orderflowSettings;
    }

    public void setOrderflowSettings(OrderflowSettings orderflowSettings) {
        this.orderflowSettings = orderflowSettings != null ? orderflowSettings : new OrderflowSettings();
        this.updated = Instant.now();
    }

    // Metadata getters/setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updated = Instant.now();
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    // Convenience methods - Entry settings delegates (JsonIgnore to prevent duplicate serialization)

    @JsonIgnore
    public String getEntry() {
        return getEntrySettings().getCondition();
    }

    public void setEntry(String entry) {
        getEntrySettings().setCondition(entry);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public int getMaxOpenTrades() {
        return getEntrySettings().getMaxOpenTrades();
    }

    public void setMaxOpenTrades(int maxOpenTrades) {
        getEntrySettings().setMaxOpenTrades(maxOpenTrades);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public int getMinCandlesBetweenTrades() {
        return getEntrySettings().getMinCandlesBetween();
    }

    public void setMinCandlesBetweenTrades(int minCandlesBetween) {
        getEntrySettings().setMinCandlesBetween(minCandlesBetween);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public TradeDirection getDirection() {
        return getEntrySettings().getDirection();
    }

    public void setDirection(TradeDirection direction) {
        getEntrySettings().setDirection(direction);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public boolean isLong() {
        return getDirection().isLong();
    }

    @JsonIgnore
    public boolean isShort() {
        return getDirection().isShort();
    }

    // DCA delegates

    @JsonIgnore
    public boolean isDcaEnabled() {
        return getEntrySettings().getDca().isEnabled();
    }

    public void setDcaEnabled(boolean enabled) {
        getEntrySettings().getDca().setEnabled(enabled);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public int getDcaMaxEntries() {
        return getEntrySettings().getDca().getMaxEntries();
    }

    public void setDcaMaxEntries(int maxEntries) {
        getEntrySettings().getDca().setMaxEntries(maxEntries);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public int getDcaBarsBetween() {
        return getEntrySettings().getDca().getBarsBetween();
    }

    public void setDcaBarsBetween(int barsBetween) {
        getEntrySettings().getDca().setBarsBetween(barsBetween);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public DcaMode getDcaMode() {
        return getEntrySettings().getDca().getMode();
    }

    public void setDcaMode(DcaMode mode) {
        getEntrySettings().getDca().setMode(mode);
        this.updated = Instant.now();
    }

    // Convenience methods - Exit settings delegates

    @JsonIgnore
    public java.util.List<ExitZone> getExitZones() {
        return getExitSettings().getZones();
    }

    public void setExitZones(java.util.List<ExitZone> zones) {
        getExitSettings().setZones(zones);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public ZoneEvaluation getZoneEvaluation() {
        return getExitSettings().getEvaluation();
    }

    public void setZoneEvaluation(ZoneEvaluation evaluation) {
        getExitSettings().setEvaluation(evaluation);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public ExitZone findMatchingZone(double pnlPercent) {
        return getExitSettings().findMatchingZone(pnlPercent);
    }

    // Convenience methods - Backtest settings delegates

    @JsonIgnore
    public String getExchange() {
        return getBacktestSettings().getExchange();
    }

    public void setExchange(String exchange) {
        getBacktestSettings().setExchange(exchange);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public String getSymbolMarket() {
        return getBacktestSettings().getSymbolMarket();
    }

    public void setSymbolMarket(String symbolMarket) {
        getBacktestSettings().setSymbolMarket(symbolMarket);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public String getSymbol() {
        return getBacktestSettings().getSymbol();
    }

    public void setSymbol(String symbol) {
        getBacktestSettings().setSymbol(symbol);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public String getTimeframe() {
        return getBacktestSettings().getTimeframe();
    }

    public void setTimeframe(String timeframe) {
        getBacktestSettings().setTimeframe(timeframe);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public String getDuration() {
        return getBacktestSettings().getDuration();
    }

    public void setDuration(String duration) {
        getBacktestSettings().setDuration(duration);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public double getInitialCapital() {
        return getBacktestSettings().getInitialCapital();
    }

    public void setInitialCapital(double initialCapital) {
        getBacktestSettings().setInitialCapital(initialCapital);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public PositionSizingType getPositionSizingType() {
        return getBacktestSettings().getPositionSizingType();
    }

    public void setPositionSizingType(PositionSizingType type) {
        getBacktestSettings().setPositionSizingType(type);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public double getPositionSizingValue() {
        return getBacktestSettings().getPositionSizingValue();
    }

    public void setPositionSizingValue(double value) {
        getBacktestSettings().setPositionSizingValue(value);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public double getFeePercent() {
        return getBacktestSettings().getFeePercent();
    }

    public void setFeePercent(double feePercent) {
        getBacktestSettings().setFeePercent(feePercent);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public double getSlippagePercent() {
        return getBacktestSettings().getSlippagePercent();
    }

    public void setSlippagePercent(double slippagePercent) {
        getBacktestSettings().setSlippagePercent(slippagePercent);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public double getTotalCommission() {
        return getBacktestSettings().getTotalCommission();
    }

    @JsonIgnore
    public MarketType getMarketType() {
        return getBacktestSettings().getMarketType();
    }

    public void setMarketType(MarketType marketType) {
        getBacktestSettings().setMarketType(marketType);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public double getMarginInterestHourly() {
        return getBacktestSettings().getMarginInterestHourly();
    }

    public void setMarginInterestHourly(double hourlyRate) {
        getBacktestSettings().setMarginInterestHourly(hourlyRate);
        this.updated = Instant.now();
    }

    // Legacy getter for backward compatibility
    @JsonIgnore
    public Double getMarginInterestApr() {
        return getBacktestSettings().getMarginInterestApr();
    }

    public void setMarginInterestApr(Double apr) {
        getBacktestSettings().setMarginInterestApr(apr);
        this.updated = Instant.now();
    }

    // Convenience methods - Phase settings delegates

    @JsonIgnore
    public java.util.List<String> getRequiredPhaseIds() {
        return getPhaseSettings().getRequiredPhaseIds();
    }

    public void setRequiredPhaseIds(java.util.List<String> ids) {
        getPhaseSettings().setRequiredPhaseIds(ids);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public boolean hasRequiredPhases() {
        return getPhaseSettings().hasRequiredPhases();
    }

    @JsonIgnore
    public java.util.List<String> getExcludedPhaseIds() {
        return getPhaseSettings().getExcludedPhaseIds();
    }

    public void setExcludedPhaseIds(java.util.List<String> ids) {
        getPhaseSettings().setExcludedPhaseIds(ids);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public boolean hasExcludedPhases() {
        return getPhaseSettings().hasExcludedPhases();
    }

    // Convenience methods - Hoop pattern settings delegates

    @JsonIgnore
    public java.util.List<String> getRequiredEntryPatternIds() {
        return getHoopPatternSettings().getRequiredEntryPatternIds();
    }

    public void setRequiredEntryPatternIds(java.util.List<String> ids) {
        getHoopPatternSettings().setRequiredEntryPatternIds(ids);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public java.util.List<String> getExcludedEntryPatternIds() {
        return getHoopPatternSettings().getExcludedEntryPatternIds();
    }

    public void setExcludedEntryPatternIds(java.util.List<String> ids) {
        getHoopPatternSettings().setExcludedEntryPatternIds(ids);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public java.util.List<String> getRequiredExitPatternIds() {
        return getHoopPatternSettings().getRequiredExitPatternIds();
    }

    public void setRequiredExitPatternIds(java.util.List<String> ids) {
        getHoopPatternSettings().setRequiredExitPatternIds(ids);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public java.util.List<String> getExcludedExitPatternIds() {
        return getHoopPatternSettings().getExcludedExitPatternIds();
    }

    public void setExcludedExitPatternIds(java.util.List<String> ids) {
        getHoopPatternSettings().setExcludedExitPatternIds(ids);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public boolean hasEntryHoopPatterns() {
        return getHoopPatternSettings().hasEntryPatterns();
    }

    @JsonIgnore
    public boolean hasExitHoopPatterns() {
        return getHoopPatternSettings().hasExitPatterns();
    }

    // Convenience methods - Orderflow settings delegates

    @JsonIgnore
    public OrderflowSettings.Mode getOrderflowMode() {
        return getOrderflowSettings().getMode();
    }

    public void setOrderflowMode(OrderflowSettings.Mode mode) {
        getOrderflowSettings().setMode(mode);
        this.updated = Instant.now();
    }

    @JsonIgnore
    public boolean isOrderflowEnabled() {
        return getOrderflowSettings().isEnabled();
    }

    /**
     * Check if strategy requires aggTrades data.
     * Auto-detects from DSL conditions - no manual checkbox needed.
     *
     * AggTrades required for:
     * - Tier 2 orderflow: DELTA, CUM_DELTA, WHALE_DELTA, WHALE_BUY_VOL, WHALE_SELL_VOL, LARGE_TRADE_COUNT
     * - Sub-minute timeframes (checked separately in BacktestCoordinator)
     *
     * NOT required for Tier 1 (calculated from candles):
     * - VWAP, POC, VAH, VAL, PREV_DAY_*, TODAY_*
     */
    @JsonIgnore
    public boolean requiresAggTrades() {
        // Check entry condition
        String entry = getEntrySettings().getCondition();
        if (entry != null && containsAggTradesFunction(entry)) {
            return true;
        }

        // Check exit zone conditions
        for (ExitZone zone : getExitZones()) {
            String condition = zone.exitCondition();
            if (condition != null && containsAggTradesFunction(condition)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a DSL condition contains functions that require aggTrades.
     * Tier 2 orderflow functions that need tick-level data.
     */
    private boolean containsAggTradesFunction(String condition) {
        String upper = condition.toUpperCase();
        // Only delta-based functions need aggTrades
        // VWAP, POC, VAH, VAL, PREV_DAY_*, TODAY_* are Tier 1 (candle-based)
        return upper.contains("DELTA") ||           // DELTA, CUM_DELTA, WHALE_DELTA
               upper.contains("WHALE_BUY_VOL") ||
               upper.contains("WHALE_SELL_VOL") ||
               upper.contains("LARGE_TRADE_COUNT");
    }

    /**
     * Check if strategy uses any orderflow functions (Tier 1 or Tier 2).
     * Used for informational display, not data requirements.
     */
    @JsonIgnore
    public boolean usesOrderflow() {
        String entry = getEntrySettings().getCondition();
        if (entry != null && containsOrderflowFunction(entry)) {
            return true;
        }

        for (ExitZone zone : getExitZones()) {
            String condition = zone.exitCondition();
            if (condition != null && containsOrderflowFunction(condition)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsOrderflowFunction(String condition) {
        String upper = condition.toUpperCase();
        return upper.contains("VWAP") ||
               upper.contains("POC") ||
               upper.contains("VAH") ||
               upper.contains("VAL") ||
               upper.contains("DELTA") ||
               upper.contains("WHALE") ||
               upper.contains("LARGE_TRADE_COUNT") ||
               upper.contains("PREV_DAY_") ||
               upper.contains("TODAY_");
    }

    /**
     * Check if strategy uses Open Interest functions (OI, OI_CHANGE, OI_DELTA).
     */
    @JsonIgnore
    public boolean usesOpenInterest() {
        // Check entry condition
        String entry = getEntrySettings().getCondition();
        if (entry != null && containsOiFunction(entry)) {
            return true;
        }

        // Check exit zone conditions
        for (ExitZone zone : getExitZones()) {
            String condition = zone.exitCondition();
            if (condition != null && containsOiFunction(condition)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsOiFunction(String condition) {
        String upper = condition.toUpperCase();
        return upper.contains("OI_CHANGE") || upper.contains("OI_DELTA") ||
               upper.matches(".*\\bOI\\b.*");
    }

    /**
     * Check if strategy DSL requires Open Interest data.
     * Alias for usesOpenInterest() for clearer API in data requirements.
     */
    @JsonIgnore
    public boolean requiresOpenInterest() {
        return usesOpenInterest();
    }

    /**
     * Check if strategy DSL uses Funding Rate functions (FUNDING, FUNDING_8H).
     */
    @JsonIgnore
    public boolean requiresFunding() {
        // Check entry condition
        String entry = getEntrySettings().getCondition();
        if (entry != null && containsFundingFunction(entry)) {
            return true;
        }

        // Check exit zone conditions
        for (ExitZone zone : getExitZones()) {
            String condition = zone.exitCondition();
            if (condition != null && containsFundingFunction(condition)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsFundingFunction(String condition) {
        String upper = condition.toUpperCase();
        return upper.contains("FUNDING");
    }

    /**
     * Check if strategy DSL uses Premium Index functions (PREMIUM, PREMIUM_AVG).
     */
    @JsonIgnore
    public boolean requiresPremium() {
        // Check entry condition
        String entry = getEntrySettings().getCondition();
        if (entry != null && containsPremiumFunction(entry)) {
            return true;
        }

        // Check exit zone conditions
        for (ExitZone zone : getExitZones()) {
            String condition = zone.exitCondition();
            if (condition != null && containsPremiumFunction(condition)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsPremiumFunction(String condition) {
        String upper = condition.toUpperCase();
        return upper.contains("PREMIUM");
    }

    @Override
    public String toString() {
        return name;
    }
}
