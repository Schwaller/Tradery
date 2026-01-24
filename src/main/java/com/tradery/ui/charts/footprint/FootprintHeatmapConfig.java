package com.tradery.ui.charts.footprint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradery.model.Exchange;

import java.awt.Color;

/**
 * Configuration for footprint heatmap visualization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FootprintHeatmapConfig {

    // Enable/disable
    private boolean enabled = false;

    // Display mode
    private FootprintDisplayMode displayMode = FootprintDisplayMode.COMBINED;

    // Single exchange selection (when mode = SINGLE_EXCHANGE)
    private Exchange selectedExchange = Exchange.BINANCE;

    // Tick size configuration
    private TickSizeMode tickSizeMode = TickSizeMode.AUTO;
    private double fixedTickSize = 50.0;
    private int targetBuckets = 20;

    // Display options
    private boolean showDeltaNumbers = false;      // Show delta values in cells
    private boolean showImbalanceMarkers = true;   // Show arrows for imbalances
    private boolean showPocLine = true;            // Highlight POC level
    private boolean showValueArea = true;          // Shade VAH-VAL region
    private double imbalanceThreshold = 3.0;       // Ratio for imbalance detection

    // Opacity
    private double opacity = 0.7;

    // Color thresholds for delta intensity
    private double strongBuyThreshold = 0.4;       // >40% delta = strong buy
    private double moderateBuyThreshold = 0.1;     // 10-40% = moderate buy
    // -10% to 10% = neutral
    // -40% to -10% = moderate sell
    // <-40% = strong sell

    /**
     * Tick size calculation mode.
     */
    public enum TickSizeMode {
        AUTO,   // Calculate based on ATR
        FIXED   // Use fixedTickSize value
    }

    // ===== Delta Color Scheme =====

    /** Strong buy (>40% delta) */
    public static final Color STRONG_BUY_COLOR = new Color(0x26, 0xA6, 0x5B);    // Green

    /** Moderate buy (10-40% delta) */
    public static final Color MODERATE_BUY_COLOR = new Color(0x7D, 0xCE, 0xA0);  // Light Green

    /** Neutral (-10% to 10% delta) */
    public static final Color NEUTRAL_COLOR = new Color(0x64, 0x64, 0x64);       // Gray

    /** Moderate sell (-40% to -10% delta) */
    public static final Color MODERATE_SELL_COLOR = new Color(0xF1, 0x94, 0x8A); // Light Red

    /** Strong sell (<-40% delta) */
    public static final Color STRONG_SELL_COLOR = new Color(0xE7, 0x4C, 0x3C);   // Red

    /** Divergence highlight color */
    public static final Color DIVERGENCE_COLOR = new Color(0xFF, 0xA5, 0x00);    // Orange

    /** POC line color */
    public static final Color POC_COLOR = new Color(0xFF, 0xFF, 0xFF, 200);      // White

    /** Value area shade color */
    public static final Color VALUE_AREA_COLOR = new Color(0xFF, 0xFF, 0xFF, 30);// Light white

    // ===== Constructors =====

    public FootprintHeatmapConfig() {}

    public FootprintHeatmapConfig(FootprintHeatmapConfig other) {
        this.enabled = other.enabled;
        this.displayMode = other.displayMode;
        this.selectedExchange = other.selectedExchange;
        this.tickSizeMode = other.tickSizeMode;
        this.fixedTickSize = other.fixedTickSize;
        this.targetBuckets = other.targetBuckets;
        this.showDeltaNumbers = other.showDeltaNumbers;
        this.showImbalanceMarkers = other.showImbalanceMarkers;
        this.showPocLine = other.showPocLine;
        this.showValueArea = other.showValueArea;
        this.imbalanceThreshold = other.imbalanceThreshold;
        this.opacity = other.opacity;
        this.strongBuyThreshold = other.strongBuyThreshold;
        this.moderateBuyThreshold = other.moderateBuyThreshold;
    }

    // ===== Color Methods =====

    /**
     * Get color for a delta percentage (-1.0 to 1.0).
     *
     * @param deltaPct Delta as percentage of total volume (-1 = all sell, +1 = all buy)
     * @param volumeIntensity Volume intensity for alpha (0.0 to 1.0)
     * @return Color for this delta level
     */
    @JsonIgnore
    public Color getDeltaColor(double deltaPct, double volumeIntensity) {
        Color baseColor;

        if (deltaPct >= strongBuyThreshold) {
            baseColor = STRONG_BUY_COLOR;
        } else if (deltaPct >= moderateBuyThreshold) {
            baseColor = MODERATE_BUY_COLOR;
        } else if (deltaPct <= -strongBuyThreshold) {
            baseColor = STRONG_SELL_COLOR;
        } else if (deltaPct <= -moderateBuyThreshold) {
            baseColor = MODERATE_SELL_COLOR;
        } else {
            baseColor = NEUTRAL_COLOR;
        }

        // Apply volume intensity to alpha (50-200 range)
        int alpha = (int) (50 + volumeIntensity * 150 * opacity);
        alpha = Math.max(50, Math.min(200, alpha));

        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
    }

    /**
     * Get alpha value (0-255) based on opacity setting.
     */
    @JsonIgnore
    public int getAlpha() {
        return (int) (opacity * 255);
    }

    // ===== Getters and Setters =====

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FootprintDisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(FootprintDisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    public Exchange getSelectedExchange() {
        return selectedExchange;
    }

    public void setSelectedExchange(Exchange selectedExchange) {
        this.selectedExchange = selectedExchange;
    }

    public TickSizeMode getTickSizeMode() {
        return tickSizeMode;
    }

    public void setTickSizeMode(TickSizeMode tickSizeMode) {
        this.tickSizeMode = tickSizeMode;
    }

    public double getFixedTickSize() {
        return fixedTickSize;
    }

    public void setFixedTickSize(double fixedTickSize) {
        this.fixedTickSize = Math.max(0.01, fixedTickSize);
    }

    public int getTargetBuckets() {
        return targetBuckets;
    }

    public void setTargetBuckets(int targetBuckets) {
        this.targetBuckets = Math.max(5, Math.min(100, targetBuckets));
    }

    public boolean isShowDeltaNumbers() {
        return showDeltaNumbers;
    }

    public void setShowDeltaNumbers(boolean showDeltaNumbers) {
        this.showDeltaNumbers = showDeltaNumbers;
    }

    public boolean isShowImbalanceMarkers() {
        return showImbalanceMarkers;
    }

    public void setShowImbalanceMarkers(boolean showImbalanceMarkers) {
        this.showImbalanceMarkers = showImbalanceMarkers;
    }

    public boolean isShowPocLine() {
        return showPocLine;
    }

    public void setShowPocLine(boolean showPocLine) {
        this.showPocLine = showPocLine;
    }

    public boolean isShowValueArea() {
        return showValueArea;
    }

    public void setShowValueArea(boolean showValueArea) {
        this.showValueArea = showValueArea;
    }

    public double getImbalanceThreshold() {
        return imbalanceThreshold;
    }

    public void setImbalanceThreshold(double imbalanceThreshold) {
        this.imbalanceThreshold = Math.max(1.5, imbalanceThreshold);
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.max(0, Math.min(1, opacity));
    }

    public double getStrongBuyThreshold() {
        return strongBuyThreshold;
    }

    public void setStrongBuyThreshold(double strongBuyThreshold) {
        this.strongBuyThreshold = strongBuyThreshold;
    }

    public double getModerateBuyThreshold() {
        return moderateBuyThreshold;
    }

    public void setModerateBuyThreshold(double moderateBuyThreshold) {
        this.moderateBuyThreshold = moderateBuyThreshold;
    }
}
