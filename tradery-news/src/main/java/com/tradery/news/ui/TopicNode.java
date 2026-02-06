package com.tradery.news.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual representation of a topic or coin in the upper half of the graph.
 */
public class TopicNode {

    public enum Type { TOPIC, COIN }

    private final String id;
    private final String label;
    private final Type type;

    // Position
    private double x;
    private double y;
    private double vx = 0;

    // Visual state
    private boolean selected = false;
    private boolean hovered = false;

    // Connected news articles
    private final List<NewsNode> connections = new ArrayList<>();
    private int articleCount = 0;

    public TopicNode(String id, String label, Type type) {
        this.id = id;
        this.label = label;
        this.type = type;
    }

    public String id() { return id; }
    public String label() { return label; }
    public Type type() { return type; }

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

    /**
     * Get node color based on type (color stays consistent, only transparency changes for highlight).
     */
    public Color getColor() {
        return switch (type) {
            case TOPIC -> new Color(100, 140, 200);  // Blue for topics
            case COIN -> new Color(200, 160, 80);    // Gold for coins
        };
    }

    /**
     * Check if point is within this node.
     */
    public boolean contains(double px, double py) {
        double r = getRadius() + 3;
        return Math.abs(px - x) < r && Math.abs(py - y) < r;
    }
}
