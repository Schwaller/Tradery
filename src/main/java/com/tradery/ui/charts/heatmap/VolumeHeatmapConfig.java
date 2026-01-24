package com.tradery.ui.charts.heatmap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuration for volume heatmap overlay on price chart.
 * Persisted as part of ChartConfig.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolumeHeatmapConfig {

    // Enable/disable
    private boolean enabled = false;

    // Display mode
    private HeatmapMode mode = HeatmapMode.DELTA;

    // Color ramp names (resolved to ColorRamp objects at runtime)
    private String buyRampName = "Greens";
    private String sellRampName = "Reds";
    private String totalRampName = "Blues";

    // Opacity (0.0 - 1.0)
    private double opacity = 0.6;

    // Color scale type
    private ColorRamp.Scale scale = ColorRamp.Scale.SQRT;

    // Draw behind candles (true) or in front (false)
    private boolean behindCandles = true;

    // Number of price level buckets per candle
    private int bucketCount = 20;

    // Tick size mode
    private TickSizeMode tickSizeMode = TickSizeMode.AUTO;

    // Fixed tick size (used when tickSizeMode = FIXED)
    private double fixedTickSize = 50.0;

    /**
     * Tick size calculation mode.
     */
    public enum TickSizeMode {
        AUTO,   // Calculate based on ATR
        FIXED   // Use fixedTickSize value
    }

    // ===== Constructors =====

    public VolumeHeatmapConfig() {}

    public VolumeHeatmapConfig(VolumeHeatmapConfig other) {
        this.enabled = other.enabled;
        this.mode = other.mode;
        this.buyRampName = other.buyRampName;
        this.sellRampName = other.sellRampName;
        this.totalRampName = other.totalRampName;
        this.opacity = other.opacity;
        this.scale = other.scale;
        this.behindCandles = other.behindCandles;
        this.bucketCount = other.bucketCount;
        this.tickSizeMode = other.tickSizeMode;
        this.fixedTickSize = other.fixedTickSize;
    }

    // ===== Runtime Color Ramp Access =====

    @JsonIgnore
    public ColorRamp getBuyRamp() {
        return ColorRamp.getPreset(buyRampName);
    }

    @JsonIgnore
    public ColorRamp getSellRamp() {
        return ColorRamp.getPreset(sellRampName);
    }

    @JsonIgnore
    public ColorRamp getTotalRamp() {
        return ColorRamp.getPreset(totalRampName);
    }

    /**
     * Get the appropriate color ramp for the current mode.
     */
    @JsonIgnore
    public ColorRamp getActiveRamp() {
        return switch (mode) {
            case BUY_VOLUME -> getBuyRamp();
            case SELL_VOLUME -> getSellRamp();
            case TOTAL_VOLUME -> getTotalRamp();
            case DELTA, SPLIT -> getBuyRamp(); // Delta uses both buy and sell ramps
        };
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

    public HeatmapMode getMode() {
        return mode;
    }

    public void setMode(HeatmapMode mode) {
        this.mode = mode;
    }

    public String getBuyRampName() {
        return buyRampName;
    }

    public void setBuyRampName(String buyRampName) {
        this.buyRampName = buyRampName;
    }

    public String getSellRampName() {
        return sellRampName;
    }

    public void setSellRampName(String sellRampName) {
        this.sellRampName = sellRampName;
    }

    public String getTotalRampName() {
        return totalRampName;
    }

    public void setTotalRampName(String totalRampName) {
        this.totalRampName = totalRampName;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.max(0, Math.min(1, opacity));
    }

    public ColorRamp.Scale getScale() {
        return scale;
    }

    public void setScale(ColorRamp.Scale scale) {
        this.scale = scale;
    }

    public boolean isBehindCandles() {
        return behindCandles;
    }

    public void setBehindCandles(boolean behindCandles) {
        this.behindCandles = behindCandles;
    }

    public int getBucketCount() {
        return bucketCount;
    }

    public void setBucketCount(int bucketCount) {
        this.bucketCount = Math.max(5, Math.min(100, bucketCount));
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
}
