package com.tradery.forge.ui;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Helper class for storing hoop zone bounds and performing hit testing
 * for edge detection during drag operations.
 */
public class HoopZoneBounds {

    public enum Edge {
        NONE,
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    private final int hoopIndex;
    private final int startBar;
    private final int endBar;
    private final double minPrice;
    private final double maxPrice;
    private final long startTime;
    private final long endTime;
    private Rectangle2D screenBounds;

    public HoopZoneBounds(int hoopIndex, int startBar, int endBar,
                          double minPrice, double maxPrice,
                          long startTime, long endTime) {
        this.hoopIndex = hoopIndex;
        this.startBar = startBar;
        this.endBar = endBar;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void setScreenBounds(Rectangle2D bounds) {
        this.screenBounds = bounds;
    }

    public Rectangle2D getScreenBounds() {
        return screenBounds;
    }

    /**
     * Check if a point is near an edge of this zone.
     * @param point The point in screen coordinates
     * @param tolerance Pixel tolerance for edge detection
     * @return The edge that the point is near, or NONE
     */
    public Edge getNearEdge(Point2D point, double tolerance) {
        if (screenBounds == null) return Edge.NONE;

        double x = point.getX();
        double y = point.getY();

        double left = screenBounds.getMinX();
        double right = screenBounds.getMaxX();
        double top = screenBounds.getMinY();
        double bottom = screenBounds.getMaxY();

        // Check if point is within the horizontal bounds (with tolerance)
        boolean inHorizontalRange = x >= left - tolerance && x <= right + tolerance;
        // Check if point is within the vertical bounds (with tolerance)
        boolean inVerticalRange = y >= top - tolerance && y <= bottom + tolerance;

        // Check top edge (maxPrice in data space, but min Y in screen space)
        if (inHorizontalRange && Math.abs(y - top) <= tolerance) {
            return Edge.TOP;
        }

        // Check bottom edge (minPrice in data space, but max Y in screen space)
        if (inHorizontalRange && Math.abs(y - bottom) <= tolerance) {
            return Edge.BOTTOM;
        }

        // Check left edge
        if (inVerticalRange && Math.abs(x - left) <= tolerance) {
            return Edge.LEFT;
        }

        // Check right edge
        if (inVerticalRange && Math.abs(x - right) <= tolerance) {
            return Edge.RIGHT;
        }

        return Edge.NONE;
    }

    /**
     * Check if a point is inside this zone (not just near an edge).
     */
    public boolean contains(Point2D point) {
        if (screenBounds == null) return false;
        return screenBounds.contains(point);
    }

    /**
     * Check if a data point (bar index, price) is inside this zone.
     */
    public boolean containsDataPoint(int barIndex, double price) {
        return barIndex >= startBar && barIndex <= endBar
            && price >= minPrice && price <= maxPrice;
    }

    public int getHoopIndex() {
        return hoopIndex;
    }

    public int getStartBar() {
        return startBar;
    }

    public int getEndBar() {
        return endBar;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }
}
