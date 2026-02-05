package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;

/**
 * Reference price overlay.
 * Draws a horizontal line at a reference price (e.g., spot price for comparison).
 */
public class ReferencePriceOverlay implements ChartOverlay {

    private static final Color LINE_COLOR = new Color(65, 135, 245);  // Blue
    private static final Color LABEL_BG_COLOR = new Color(65, 135, 245);
    private static final Color LABEL_TEXT_COLOR = Color.WHITE;
    private static final BasicStroke LINE_STROKE = new BasicStroke(
        1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
        10.0f, new float[]{6.0f, 3.0f}, 0.0f);  // Dashed line
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    private final DecimalFormat priceFormat;
    private final String label;
    private ValueMarker marker;
    private volatile double referencePrice = Double.NaN;

    public ReferencePriceOverlay() {
        this("SPOT");
    }

    public ReferencePriceOverlay(String label) {
        this.label = label;
        this.priceFormat = new DecimalFormat("#,##0.00");
    }

    /**
     * Update the reference price. Call this when price changes.
     * Updates the marker directly if it exists.
     */
    public void setReferencePrice(double price) {
        this.referencePrice = price;
        // Update marker directly if it exists
        if (marker != null && !Double.isNaN(price)) {
            marker.setValue(price);
            marker.setLabel(label + " " + priceFormat.format(price));
        }
    }

    /**
     * Get the current reference price.
     */
    public double getReferencePrice() {
        return referencePrice;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        // Remove old marker if exists
        if (marker != null) {
            plot.removeRangeMarker(marker);
        }

        if (Double.isNaN(referencePrice)) {
            return;
        }

        // Create the value marker (horizontal line)
        marker = new ValueMarker(referencePrice);
        marker.setPaint(LINE_COLOR);
        marker.setStroke(LINE_STROKE);

        // Add price label
        String priceLabel = label + " " + priceFormat.format(referencePrice);
        marker.setLabel(priceLabel);
        marker.setLabelFont(LABEL_FONT);
        marker.setLabelPaint(LABEL_TEXT_COLOR);
        marker.setLabelBackgroundColor(LABEL_BG_COLOR);
        marker.setLabelAnchor(RectangleAnchor.LEFT);
        marker.setLabelTextAnchor(TextAnchor.CENTER_LEFT);
        marker.setLabelOffsetType(org.jfree.chart.ui.LengthAdjustmentType.EXPAND);

        // Add marker to plot
        plot.addRangeMarker(marker);
    }

    @Override
    public String getDisplayName() {
        return "Reference Price (" + label + ")";
    }

    @Override
    public int getDatasetCount() {
        return 0;  // Markers don't use datasets
    }

    @Override
    public void close() {
        marker = null;
    }
}
