package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A sequential price pattern defined by a series of hoops.
 * Stored as JSON in ~/.tradery/hoops/{id}/hoop.json
 *
 * Similar to Phase but for price-checkpoint patterns rather than DSL conditions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HoopPattern implements Identifiable {

    // Identity
    private String id;           // e.g., "double-bottom"
    private String name;         // e.g., "Double Bottom Pattern"
    private String description;  // e.g., "Detects W-shaped reversal pattern"

    // Pattern definition
    private List<Hoop> hoops = new ArrayList<>();
    private String symbol;       // Symbol to evaluate, e.g., "BTCUSDT"
    private String timeframe;    // Evaluation timeframe, e.g., "1h"

    // Pattern behavior
    private int cooldownBars = 0;        // Bars to wait after pattern completes before it can match again
    private boolean allowOverlap = false; // Can next pattern start before cooldown?

    // Price smoothing - reduces noise from wicks/spikes
    private PriceSmoothingType priceSmoothingType = PriceSmoothingType.NONE;
    private int priceSmoothingPeriod = 5;

    // Metadata
    private Instant created;
    private Instant updated;

    public HoopPattern() {
        // For Jackson
    }

    public HoopPattern(String id, String name, List<Hoop> hoops, String symbol, String timeframe) {
        this.id = id;
        this.name = name;
        this.hoops = hoops != null ? hoops : new ArrayList<>();
        this.symbol = symbol;
        this.timeframe = timeframe;
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

    // Pattern definition getters/setters

    public List<Hoop> getHoops() {
        if (hoops == null) {
            hoops = new ArrayList<>();
        }
        return hoops;
    }

    public void setHoops(List<Hoop> hoops) {
        this.hoops = hoops != null ? hoops : new ArrayList<>();
        this.updated = Instant.now();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
        this.updated = Instant.now();
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
        this.updated = Instant.now();
    }

    // Pattern behavior getters/setters

    public int getCooldownBars() {
        return cooldownBars;
    }

    public void setCooldownBars(int cooldownBars) {
        this.cooldownBars = Math.max(0, cooldownBars);
        this.updated = Instant.now();
    }

    public boolean isAllowOverlap() {
        return allowOverlap;
    }

    public void setAllowOverlap(boolean allowOverlap) {
        this.allowOverlap = allowOverlap;
        this.updated = Instant.now();
    }

    // Price smoothing getters/setters

    public PriceSmoothingType getPriceSmoothingType() {
        return priceSmoothingType != null ? priceSmoothingType : PriceSmoothingType.NONE;
    }

    public void setPriceSmoothingType(PriceSmoothingType priceSmoothingType) {
        this.priceSmoothingType = priceSmoothingType;
        this.updated = Instant.now();
    }

    public int getPriceSmoothingPeriod() {
        return priceSmoothingPeriod > 0 ? priceSmoothingPeriod : 5;
    }

    public void setPriceSmoothingPeriod(int priceSmoothingPeriod) {
        this.priceSmoothingPeriod = Math.max(1, priceSmoothingPeriod);
        this.updated = Instant.now();
    }

    // Metadata getters/setters

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

    // Utility methods

    /**
     * Calculate total expected bars for the full pattern.
     */
    public int getTotalExpectedBars() {
        return hoops.stream().mapToInt(Hoop::distance).sum();
    }

    /**
     * Calculate maximum possible bars (with all tolerances at max).
     */
    public int getMaxPatternBars() {
        return hoops.stream().mapToInt(h -> h.distance() + h.tolerance()).sum();
    }

    /**
     * Calculate minimum possible bars (with all tolerances at min).
     */
    public int getMinPatternBars() {
        return hoops.stream().mapToInt(h -> Math.max(1, h.distance() - h.tolerance())).sum();
    }

    /**
     * Check if this pattern has any hoops defined.
     */
    public boolean hasHoops() {
        return hoops != null && !hoops.isEmpty();
    }

    @Override
    public String toString() {
        return name;
    }
}
