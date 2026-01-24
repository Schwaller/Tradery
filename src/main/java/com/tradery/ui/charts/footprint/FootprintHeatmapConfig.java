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
    private FootprintDisplayMode displayMode = FootprintDisplayMode.SPLIT;

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

    // ===== Color Scheme (CryptoPage style) =====

    /** Buy color for split mode - lime green */
    public static final Color BUY_COLOR = new Color(0xAA, 0xFF, 0x33);           // Lime green

    /** Sell color for split mode - red */
    public static final Color SELL_COLOR = new Color(0xFF, 0x00, 0x00);          // Red

    // Volume intensity ramp (high → low): red → orange → yellow → cyan → blue
    public static final Color[] VOLUME_RAMP = {
        new Color(0xdc, 0x26, 0x26),  // Bright red (highest)
        new Color(0xf9, 0x73, 0x16),  // Orange
        new Color(0xfb, 0xbf, 0x24),  // Golden yellow
        new Color(0x22, 0xd3, 0xee),  // Cyan
        new Color(0x3b, 0x82, 0xf6),  // Bright blue
        new Color(0x1e, 0x3a, 0x8a),  // Deep blue (lowest)
    };

    /** Strong buy (>40% delta) - for backward compatibility */
    public static final Color STRONG_BUY_COLOR = BUY_COLOR;

    /** Strong sell (<-40% delta) - for backward compatibility */
    public static final Color STRONG_SELL_COLOR = SELL_COLOR;

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
     * Uses buy/sell colors blended based on delta direction and intensity.
     *
     * @param deltaPct Delta as percentage of total volume (-1 = all sell, +1 = all buy)
     * @param volumeIntensity Volume intensity for alpha (0.0 to 1.0)
     * @return Color for this delta level
     */
    @JsonIgnore
    public Color getDeltaColor(double deltaPct, double volumeIntensity) {
        // Determine color based on delta direction
        Color baseColor;
        if (deltaPct > 0.05) {
            // Buy dominant - green
            baseColor = BUY_COLOR;
        } else if (deltaPct < -0.05) {
            // Sell dominant - red
            baseColor = SELL_COLOR;
        } else {
            // Neutral - blend between buy and sell (grayish)
            baseColor = new Color(0x80, 0x80, 0x80);
        }

        // Apply volume intensity to alpha (50-220 range for better visibility)
        int alpha = (int) (50 + volumeIntensity * 170 * opacity);
        alpha = Math.max(50, Math.min(220, alpha));

        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
    }

    /**
     * Get color from the thermal volume ramp based on volume intensity.
     * High volume = hot (red), low volume = cold (blue).
     *
     * @param volumeIntensity Volume intensity (0.0 = lowest, 1.0 = highest)
     * @return Color from the thermal ramp
     */
    @JsonIgnore
    public Color getVolumeRampColor(double volumeIntensity) {
        // Clamp to [0, 1]
        volumeIntensity = Math.max(0, Math.min(1, volumeIntensity));

        // Map intensity to ramp index (0 = highest volume = red, 5 = lowest = blue)
        // Invert so high volume = index 0 (red)
        double index = (1.0 - volumeIntensity) * (VOLUME_RAMP.length - 1);
        int lowIdx = (int) Math.floor(index);
        int highIdx = Math.min(lowIdx + 1, VOLUME_RAMP.length - 1);
        double t = index - lowIdx;

        // Interpolate between colors
        Color low = VOLUME_RAMP[lowIdx];
        Color high = VOLUME_RAMP[highIdx];

        int r = (int) (low.getRed() + t * (high.getRed() - low.getRed()));
        int g = (int) (low.getGreen() + t * (high.getGreen() - low.getGreen()));
        int b = (int) (low.getBlue() + t * (high.getBlue() - low.getBlue()));

        int alpha = (int) (100 + volumeIntensity * 155 * opacity);
        alpha = Math.max(100, Math.min(255, alpha));

        return new Color(r, g, b, alpha);
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
