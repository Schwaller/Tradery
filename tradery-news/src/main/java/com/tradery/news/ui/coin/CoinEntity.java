package com.tradery.news.ui.coin;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity in the coin relationship graph.
 */
public class CoinEntity {

    public enum Type {
        COIN(new Color(100, 180, 255)),      // Blue - cryptocurrencies
        L2(new Color(150, 130, 255)),         // Purple - Layer 2s
        ETF(new Color(80, 200, 120)),         // Green - ETFs
        ETP(new Color(100, 200, 150)),        // Teal - ETPs
        DAT(new Color(120, 200, 100)),        // Light green - DATs
        VC(new Color(255, 180, 80)),          // Orange - Venture Capital
        EXCHANGE(new Color(255, 140, 100)),   // Coral - Exchanges
        FOUNDATION(new Color(180, 150, 255)), // Lavender - Foundations
        COMPANY(new Color(200, 200, 120)),    // Yellow - Companies
        NEWS_SOURCE(new Color(220, 180, 220)); // Pink - News Sources

        private final Color color;
        Type(Color color) { this.color = color; }
        public Color color() { return color; }
    }

    private final String id;
    private final String name;
    private final String symbol;  // BTC, ETH, etc. (null for non-coins)
    private final Type type;
    private final String parentId; // For L2s, the L1 they're built on

    // Position for spring layout
    private double x;
    private double y;
    private double vx = 0;
    private double vy = 0;

    // Visual state
    private boolean selected = false;
    private boolean hovered = false;
    private boolean pinned = false;  // User can pin nodes in place

    // Metrics
    private double marketCap;  // For sizing
    private int connectionCount = 0;

    // Categories (e.g., "Stablecoins", "Meme", "DeFi", etc.)
    private final List<String> categories = new ArrayList<>();

    public CoinEntity(String id, String name, String symbol, Type type) {
        this(id, name, symbol, type, null);
    }

    public CoinEntity(String id, String name, String symbol, Type type, String parentId) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.type = type;
        this.parentId = parentId;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String symbol() { return symbol; }
    public Type type() { return type; }
    public String parentId() { return parentId; }

    public double x() { return x; }
    public double y() { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public double vx() { return vx; }
    public double vy() { return vy; }
    public void setVx(double vx) { this.vx = vx; }
    public void setVy(double vy) { this.vy = vy; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public double marketCap() { return marketCap; }
    public void setMarketCap(double marketCap) { this.marketCap = marketCap; }
    public int connectionCount() { return connectionCount; }
    public void incrementConnectionCount() { this.connectionCount++; }

    public List<String> categories() { return categories; }
    public void addCategory(String category) {
        if (!categories.contains(category)) {
            categories.add(category);
        }
    }
    public void setCategories(List<String> cats) {
        categories.clear();
        categories.addAll(cats);
    }

    /**
     * Get node radius based on market cap or connection count.
     */
    public int getRadius() {
        if (marketCap > 100_000_000_000L) return 20;  // >$100B
        if (marketCap > 10_000_000_000L) return 16;   // >$10B
        if (marketCap > 1_000_000_000L) return 12;    // >$1B
        if (marketCap > 100_000_000L) return 9;       // >$100M
        if (connectionCount > 10) return 10;
        if (connectionCount > 5) return 8;
        return 6;
    }

    /**
     * Get node color based on type (color stays consistent, only transparency changes for highlight).
     */
    public Color getColor() {
        return type.color();
    }

    /**
     * Check if point is within this node.
     */
    public boolean contains(double px, double py) {
        double r = getRadius() + 5;
        double dx = px - x;
        double dy = py - y;
        return dx * dx + dy * dy < r * r;
    }

    /**
     * Display label (symbol if available, otherwise name).
     */
    public String label() {
        return symbol != null ? symbol : name;
    }
}
