package com.tradery.news.ui;

import java.util.*;

public class PanelConfig {

    public enum PanelType { NEWS_MAP, COIN_GRAPH }

    private String id;
    private String name;
    private PanelType type;

    // NEWS_MAP settings
    private int maxArticles = 500;

    // COIN_GRAPH settings
    private Set<String> entityTypeFilter;    // null = all
    private Set<String> entitySourceFilter;  // null = all
    private Set<String> relationshipTypeFilter; // null = all

    // NEWS_MAP band configuration
    private List<BandConfig> bands;

    // Shared display
    private boolean showLabels = true;
    private boolean showConnections = true;

    public PanelConfig() {}

    public PanelConfig(String id, String name, PanelType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public static List<PanelConfig> defaults() {
        var newsMap = new PanelConfig("news-default", "News", PanelType.NEWS_MAP);
        newsMap.setBands(BandConfig.defaultNewsBands());
        return new ArrayList<>(List.of(
            newsMap,
            new PanelConfig("coin-default", "Coin Relations", PanelType.COIN_GRAPH)
        ));
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PanelType getType() { return type; }
    public void setType(PanelType type) { this.type = type; }

    public int getMaxArticles() { return maxArticles; }
    public void setMaxArticles(int maxArticles) { this.maxArticles = maxArticles; }

    public Set<String> getEntityTypeFilter() { return entityTypeFilter; }
    public void setEntityTypeFilter(Set<String> entityTypeFilter) { this.entityTypeFilter = entityTypeFilter; }

    public Set<String> getEntitySourceFilter() { return entitySourceFilter; }
    public void setEntitySourceFilter(Set<String> entitySourceFilter) { this.entitySourceFilter = entitySourceFilter; }

    public Set<String> getRelationshipTypeFilter() { return relationshipTypeFilter; }
    public void setRelationshipTypeFilter(Set<String> relationshipTypeFilter) { this.relationshipTypeFilter = relationshipTypeFilter; }

    public List<BandConfig> getBands() { return bands; }
    public void setBands(List<BandConfig> bands) { this.bands = bands; }

    public boolean isShowLabels() { return showLabels; }
    public void setShowLabels(boolean showLabels) { this.showLabels = showLabels; }

    public boolean isShowConnections() { return showConnections; }
    public void setShowConnections(boolean showConnections) { this.showConnections = showConnections; }

    @Override
    public String toString() {
        return name;
    }
}
