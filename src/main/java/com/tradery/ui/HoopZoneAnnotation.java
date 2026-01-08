package com.tradery.ui;

import org.jfree.chart.annotations.AbstractXYAnnotation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Custom JFreeChart annotation for drawing hoop zones as filled rectangles.
 * Draws a semi-transparent filled rectangle with an optional border for selection.
 */
public class HoopZoneAnnotation extends AbstractXYAnnotation {

    private final long startTime;
    private final long endTime;
    private final double minPrice;
    private final double maxPrice;
    private final Color fillColor;
    private final Color borderColor;
    private final boolean selected;
    private final int hoopIndex;
    private final String hoopName;

    // Store computed screen bounds for hit testing
    private Rectangle2D screenBounds;

    public HoopZoneAnnotation(long startTime, long endTime, double minPrice, double maxPrice,
                              Color fillColor, Color borderColor, boolean selected,
                              int hoopIndex, String hoopName) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.selected = selected;
        this.hoopIndex = hoopIndex;
        this.hoopName = hoopName;
    }

    @Override
    public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                     ValueAxis domainAxis, ValueAxis rangeAxis,
                     int rendererIndex, PlotRenderingInfo info) {

        // Convert data coordinates to screen coordinates
        double x1 = domainAxis.valueToJava2D(startTime, dataArea, plot.getDomainAxisEdge());
        double x2 = domainAxis.valueToJava2D(endTime, dataArea, plot.getDomainAxisEdge());
        double y1 = rangeAxis.valueToJava2D(maxPrice, dataArea, plot.getRangeAxisEdge());
        double y2 = rangeAxis.valueToJava2D(minPrice, dataArea, plot.getRangeAxisEdge());

        // Ensure proper ordering (y1 should be top, which is smaller in screen coords)
        double left = Math.min(x1, x2);
        double right = Math.max(x1, x2);
        double top = Math.min(y1, y2);
        double bottom = Math.max(y1, y2);

        double width = right - left;
        double height = bottom - top;

        // Store screen bounds for hit testing
        screenBounds = new Rectangle2D.Double(left, top, width, height);

        // Clip to data area
        Rectangle2D clipped = screenBounds.createIntersection(dataArea);
        if (clipped.isEmpty()) return;

        // Draw filled rectangle
        g2.setColor(fillColor);
        g2.fill(clipped);

        // Draw border
        if (selected) {
            // Thicker border when selected
            g2.setStroke(new BasicStroke(2.5f));
            g2.setColor(borderColor);
        } else {
            g2.setStroke(new BasicStroke(1.0f));
            g2.setColor(new Color(borderColor.getRed(), borderColor.getGreen(),
                                   borderColor.getBlue(), 120));
        }
        g2.draw(clipped);

        // Draw hoop name label at top-left of zone
        if (hoopName != null && !hoopName.isEmpty()) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g2.setColor(borderColor.darker());

            FontMetrics fm = g2.getFontMetrics();
            int labelX = (int) (clipped.getX() + 4);
            int labelY = (int) (clipped.getY() + fm.getAscent() + 2);

            // Background for readability
            int labelWidth = fm.stringWidth(hoopName) + 4;
            int labelHeight = fm.getHeight();
            g2.setColor(new Color(255, 255, 255, 180));
            g2.fillRect(labelX - 2, labelY - fm.getAscent(), labelWidth, labelHeight);

            g2.setColor(borderColor.darker());
            g2.drawString(hoopName, labelX, labelY);
        }

        // Draw index number in center
        String indexLabel = String.valueOf(hoopIndex + 1);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();
        int cx = (int) (clipped.getCenterX() - fm.stringWidth(indexLabel) / 2.0);
        int cy = (int) (clipped.getCenterY() + fm.getAscent() / 2.0);

        // Semi-transparent background circle
        int circleSize = 24;
        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillOval(cx - 4, cy - fm.getAscent() - 2, circleSize, circleSize);
        g2.setColor(borderColor);
        g2.drawOval(cx - 4, cy - fm.getAscent() - 2, circleSize, circleSize);
        g2.drawString(indexLabel, cx + 2, cy + 2);
    }

    public Rectangle2D getScreenBounds() {
        return screenBounds;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public int getHoopIndex() {
        return hoopIndex;
    }

    public boolean isSelected() {
        return selected;
    }
}
