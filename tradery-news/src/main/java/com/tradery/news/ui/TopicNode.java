package com.tradery.news.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual representation of a topic or coin in the upper half of the graph.
 */
public class TopicNode {

    private final String id;
    private final String label;
    private final String typeId;

    // Position
    private double x;
    private double y;
    private double vx = 0;

    // Visual state
    private boolean selected = false;
    private boolean hovered = false;
    private Color color;

    // Connected news articles
    private final List<NewsNode> connections = new ArrayList<>();
    private int articleCount = 0;

    public TopicNode(String id, String label, String typeId) {
        this.id = id;
        this.label = label;
        this.typeId = typeId;
    }

    public String id() { return id; }
    public String label() { return label; }
    public String typeId() { return typeId; }

    public double x() { return x; }
    public double y() { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public double vx() { return vx; }
    public void setVx(double vx) { this.vx = vx; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    public List<NewsNode> connections() { return connections; }
    public void addConnection(NewsNode node) {
        if (!connections.contains(node)) {
            connections.add(node);
            articleCount++;
        }
    }

    public int articleCount() { return articleCount; }

    /**
     * Get node radius based on article count.
     */
    public int getRadius() {
        if (articleCount >= 20) return 14;
        if (articleCount >= 10) return 11;
        if (articleCount >= 5) return 8;
        return 6;
    }

    public void setColor(Color color) { this.color = color; }

    /**
     * Get node color. Returns custom color if set, otherwise falls back to type-based default.
     */
    public Color getColor() {
        if (color != null) return color;
        if ("topic".equals(typeId)) return new Color(100, 140, 200);  // Blue for topics
        if ("coin".equals(typeId)) return new Color(200, 160, 80);    // Gold for coins
        return new Color(160, 160, 170);  // Gray for other types
    }

    /**
     * Check if point is within this node.
     */
    public boolean contains(double px, double py) {
        double r = getRadius() + 3;
        return Math.abs(px - x) < r && Math.abs(py - y) < r;
    }
}
