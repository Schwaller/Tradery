package com.tradery.forge.ui.charts;

import java.awt.*;

/**
 * Represents a single overlay instance on the price chart.
 * Tracks the overlay type, period, dataset index in the chart, and assigned color.
 */
public record OverlayInstance(
    String type,        // "SMA" or "EMA"
    int period,
    int datasetIndex,
    Color color
) {}
