package com.tradery.news.ui;

import java.util.ArrayList;
import java.util.List;

public class BandConfig {

    public enum LayoutMode { SPRING_PHYSICS, HORIZONTAL_ROWS, MAPPED_TO_FIELD }

    private String name;
    private String filter;
    private LayoutMode layoutMode;
    private double weight = 1.0;
    private boolean visible = true;

    // HORIZONTAL_ROWS
    private int maxRows = 3;

    // MAPPED_TO_FIELD
    private String yField;

    public BandConfig() {}

    public BandConfig(String name, String filter, LayoutMode layoutMode, double weight) {
        this.name = name;
        this.filter = filter;
        this.layoutMode = layoutMode;
        this.weight = weight;
    }

    public static List<BandConfig> defaultNewsBands() {
        var topics = new BandConfig("Topics", "topic", LayoutMode.HORIZONTAL_ROWS, 2);
        topics.setMaxRows(3);

        var coins = new BandConfig("Coins", "coin", LayoutMode.HORIZONTAL_ROWS, 3);
        coins.setMaxRows(4);

        var articles = new BandConfig("Articles", "articles", LayoutMode.SPRING_PHYSICS, 5);

        return new ArrayList<>(List.of(topics, coins, articles));
    }

    public static List<String> yFieldsForFilter(String filter) {
        if ("articles".equals(filter)) {
            return List.of("sentiment", "importance");
        } else {
            return List.of("articleCount");
        }
    }

    // Getters and setters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public LayoutMode getLayoutMode() { return layoutMode; }
    public void setLayoutMode(LayoutMode layoutMode) { this.layoutMode = layoutMode; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int maxRows) { this.maxRows = maxRows; }

    public String getYField() { return yField; }
    public void setYField(String yField) { this.yField = yField; }

    @Override
    public String toString() {
        return name;
    }
}
