package com.tradery.news.ui;

import com.tradery.news.model.Article;
import com.tradery.news.model.ImportanceLevel;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual representation of a news article in the graph.
 */
public class NewsNode {

    private final String id;
    private final String title;
    private final String source;
    private final String sourceUrl;
    private final Instant publishedAt;
    private final ImportanceLevel importance;
    private final List<String> topics;
    private final List<String> coins;
    private final double sentiment;
    private final String summary;
    private final String content;

    // Position (x is fixed by time, y is computed by spring layout)
    private double x;
    private double y;
    private double vy = 0; // velocity for spring physics

    // Visual properties
    private boolean selected = false;
    private boolean hovered = false;

    // Connections to other nodes
    private final List<NewsNode> connections = new ArrayList<>();
    private final List<TopicNode> topicConnections = new ArrayList<>();

    public NewsNode(Article article) {
        this.id = article.id();
        this.title = article.title();
        this.source = article.sourceName();
        this.sourceUrl = article.sourceUrl();
        this.publishedAt = article.publishedAt();
        this.importance = article.importance() != null ? article.importance() : ImportanceLevel.MEDIUM;
        this.topics = article.topics() != null ? article.topics() : List.of();
        this.coins = article.coins() != null ? article.coins() : List.of();
        this.sentiment = article.sentimentScore();
        this.summary = article.summary();
        this.content = article.content();
    }

    public String id() { return id; }
    public String title() { return title; }
    public String source() { return source; }
    public String sourceUrl() { return sourceUrl; }
    public Instant publishedAt() { return publishedAt; }
    public ImportanceLevel importance() { return importance; }
    public List<String> topics() { return topics; }
    public List<String> coins() { return coins; }
    public double sentiment() { return sentiment; }
    public String summary() { return summary; }
    public String content() { return content; }

    public double x() { return x; }
    public double y() { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public double vy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    public List<NewsNode> connections() { return connections; }
    public void addConnection(NewsNode other) {
        if (!connections.contains(other)) {
            connections.add(other);
        }
    }

    public List<TopicNode> topicConnections() { return topicConnections; }
    public void addTopicConnection(TopicNode topicNode) {
        if (!topicConnections.contains(topicNode)) {
            topicConnections.add(topicNode);
        }
    }

    /**
     * Get node radius based on importance.
     */
    public int getRadius() {
        return switch (importance) {
            case CRITICAL -> 12;
            case HIGH -> 9;
            case MEDIUM -> 6;
            case LOW -> 4;
            case NOISE -> 3;
        };
    }

    /**
     * Get node color based on sentiment.
     */
    public Color getColor() {
        if (selected) return new Color(255, 200, 0);
        if (hovered) return new Color(100, 200, 255);

        // Sentiment gradient: red (negative) -> gray (neutral) -> green (positive)
        if (sentiment > 0.3) {
            int g = 150 + (int)(sentiment * 100);
            return new Color(80, Math.min(255, g), 80);
        } else if (sentiment < -0.3) {
            int r = 150 + (int)(Math.abs(sentiment) * 100);
            return new Color(Math.min(255, r), 80, 80);
        } else {
            return new Color(150, 150, 170);
        }
    }

    /**
     * Check if point is within this node.
     */
    public boolean contains(double px, double py) {
        double r = getRadius() + 3;
        return Math.abs(px - x) < r && Math.abs(py - y) < r;
    }

    /**
     * Check if this node shares topics or coins with another.
     */
    public boolean isRelatedTo(NewsNode other) {
        for (String topic : topics) {
            if (other.topics.contains(topic)) return true;
        }
        for (String coin : coins) {
            if (other.coins.contains(coin)) return true;
        }
        return false;
    }
}
