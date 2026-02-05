package com.tradery.news.model;

import java.time.Instant;
import java.util.List;

/**
 * An event extracted from one or more articles.
 */
public record NewsEvent(
    String id,
    EventType type,
    String title,
    String description,
    Instant startDate,          // When event started
    Instant endDate,            // When event concluded (or null if ongoing)
    double sentimentScore,      // -1.0 to +1.0
    double impactScore,         // 0.0 to 1.0 estimated market impact
    List<String> articleIds,    // Source articles
    List<String> entityIds,     // Related entities
    String storyId,             // Parent story if part of one
    Instant createdAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private EventType type;
        private String title;
        private String description;
        private Instant startDate;
        private Instant endDate;
        private double sentimentScore;
        private double impactScore;
        private List<String> articleIds = List.of();
        private List<String> entityIds = List.of();
        private String storyId;
        private Instant createdAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder type(EventType type) { this.type = type; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder startDate(Instant startDate) { this.startDate = startDate; return this; }
        public Builder endDate(Instant endDate) { this.endDate = endDate; return this; }
        public Builder sentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; return this; }
        public Builder impactScore(double impactScore) { this.impactScore = impactScore; return this; }
        public Builder articleIds(List<String> articleIds) { this.articleIds = articleIds; return this; }
        public Builder entityIds(List<String> entityIds) { this.entityIds = entityIds; return this; }
        public Builder storyId(String storyId) { this.storyId = storyId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public NewsEvent build() {
            return new NewsEvent(id, type, title, description, startDate, endDate,
                sentimentScore, impactScore, articleIds, entityIds, storyId, createdAt);
        }
    }
}
