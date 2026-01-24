package com.tradery.ui.charts.heatmap;

import java.awt.Color;

/**
 * Color ramp for heatmap visualization.
 * Interpolates between colors based on value intensity.
 */
public class ColorRamp {

    /**
     * Scale type for mapping values to colors.
     */
    public enum Scale {
        LINEAR,     // Direct linear mapping
        LOG,        // Logarithmic (emphasizes lower values)
        SQRT        // Square root (balanced emphasis)
    }

    private final String name;
    private final Color lowColor;
    private final Color highColor;
    private final Scale scale;

    public ColorRamp(String name, Color lowColor, Color highColor, Scale scale) {
        this.name = name;
        this.lowColor = lowColor;
        this.highColor = highColor;
        this.scale = scale;
    }

    public ColorRamp(String name, Color lowColor, Color highColor) {
        this(name, lowColor, highColor, Scale.LINEAR);
    }

    /**
     * Get color for a normalized value (0.0 - 1.0).
     *
     * @param value Normalized value between 0 and 1
     * @param alpha Alpha value to apply (0-255)
     * @return Interpolated color
     */
    public Color getColor(double value, int alpha) {
        // Clamp value to 0-1 range
        value = Math.max(0, Math.min(1, value));

        // Apply scale transformation
        double t = switch (scale) {
            case LOG -> Math.log1p(value * 9) / Math.log(10);  // log10(1+9x) for 0→1 input gives 0→1 output
            case SQRT -> Math.sqrt(value);
            case LINEAR -> value;
        };

        // Interpolate between colors
        int r = (int) (lowColor.getRed() + t * (highColor.getRed() - lowColor.getRed()));
        int g = (int) (lowColor.getGreen() + t * (highColor.getGreen() - lowColor.getGreen()));
        int b = (int) (lowColor.getBlue() + t * (highColor.getBlue() - lowColor.getBlue()));

        return new Color(r, g, b, alpha);
    }

    /**
     * Get color with default alpha from low color.
     */
    public Color getColor(double value) {
        return getColor(value, lowColor.getAlpha());
    }

    public String getName() {
        return name;
    }

    public Color getLowColor() {
        return lowColor;
    }

    public Color getHighColor() {
        return highColor;
    }

    public Scale getScale() {
        return scale;
    }

    // ===== Built-in Presets =====

    /** Transparent to green */
    public static final ColorRamp GREENS = new ColorRamp(
        "Greens",
        new Color(38, 166, 91, 0),      // Transparent green
        new Color(38, 166, 91, 200)     // Solid green
    );

    /** Transparent to red */
    public static final ColorRamp REDS = new ColorRamp(
        "Reds",
        new Color(231, 76, 60, 0),      // Transparent red
        new Color(231, 76, 60, 200)     // Solid red
    );

    /** Transparent to blue */
    public static final ColorRamp BLUES = new ColorRamp(
        "Blues",
        new Color(52, 152, 219, 0),     // Transparent blue
        new Color(52, 152, 219, 200)    // Solid blue
    );

    /** Transparent to white */
    public static final ColorRamp WHITES = new ColorRamp(
        "Whites",
        new Color(255, 255, 255, 0),    // Transparent
        new Color(255, 255, 255, 180)   // Semi-transparent white
    );

    /** Purple to yellow (Viridis-inspired) */
    public static final ColorRamp VIRIDIS = new ColorRamp(
        "Viridis",
        new Color(68, 1, 84, 100),      // Dark purple
        new Color(253, 231, 37, 180)    // Yellow
    );

    /** Blue to red through purple (Plasma-inspired) */
    public static final ColorRamp PLASMA = new ColorRamp(
        "Plasma",
        new Color(13, 8, 135, 100),     // Deep blue
        new Color(240, 249, 33, 180)    // Yellow
    );

    /** Blue to red (Thermal) */
    public static final ColorRamp THERMAL = new ColorRamp(
        "Thermal",
        new Color(0, 0, 139, 100),      // Dark blue (cold)
        new Color(255, 69, 0, 180)      // Red-orange (hot)
    );

    /** All preset ramps */
    public static final ColorRamp[] PRESETS = {
        GREENS, REDS, BLUES, WHITES, VIRIDIS, PLASMA, THERMAL
    };

    /**
     * Get preset by name.
     */
    public static ColorRamp getPreset(String name) {
        for (ColorRamp preset : PRESETS) {
            if (preset.getName().equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return GREENS; // Default
    }
}
