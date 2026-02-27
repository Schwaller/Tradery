package com.tradery.layout;

import java.util.Collection;

/**
 * Bounding box utility for layout nodes.
 */
public class LayoutBounds {

    public final double minX, minY, maxX, maxY;

    public LayoutBounds(double minX, double minY, double maxX, double maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public double width() { return maxX - minX; }
    public double height() { return maxY - minY; }
    public double centerX() { return (minX + maxX) / 2; }
    public double centerY() { return (minY + maxY) / 2; }

    /**
     * Compute bounding box of all nodes with padding.
     */
    public static LayoutBounds of(Collection<? extends LayoutNode> nodes, double padding) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (LayoutNode node : nodes) {
            minX = Math.min(minX, node.x());
            maxX = Math.max(maxX, node.x());
            minY = Math.min(minY, node.y());
            maxY = Math.max(maxY, node.y());
        }

        if (minX == Double.MAX_VALUE) {
            return new LayoutBounds(0, 0, 800, 600);
        }
        return new LayoutBounds(minX - padding, minY - padding, maxX + padding, maxY + padding);
    }
}
