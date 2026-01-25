package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Macro phase definition for market regime detection.
 * Phases are evaluated on their own timeframe (e.g., daily) and act as
 * entry filters for strategies running on different timeframes.
 *
 * Stored as JSON in ~/.tradery/phases/{id}/phase.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Phase implements Identifiable {

    // Identity
    private String id;           // e.g., "trending-up"
    private String name;         // e.g., "Trending Up"
    private String description;  // e.g., "Price above 200 SMA with positive slope"
    private String category;     // e.g., "Trend", "Time", "Moon", "Custom"

    // Phase definition
    private String condition;    // DSL condition, e.g., "close > SMA(200) AND SMA(50) > SMA(200)"
    private String timeframe;    // Evaluation timeframe, e.g., "1d"
    private String symbol;       // Symbol to evaluate, e.g., "BTCUSDT"

    // Metadata
    private Instant created;
    private Instant updated;
    private boolean builtIn;  // True for preset phases that shouldn't be edited
    private String version;   // Version string for builtin phases (e.g., "1.0")

    public Phase() {
        // For Jackson
    }

    public Phase(String id, String name, String condition, String timeframe, String symbol) {
        this.id = id;
        this.name = name;
        this.condition = condition;
        this.timeframe = timeframe;
        this.symbol = symbol;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        this.updated = Instant.now();
    }

    // Phase definition getters/setters

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
        this.updated = Instant.now();
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
        this.updated = Instant.now();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return name;
    }
}
