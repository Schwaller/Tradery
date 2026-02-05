package com.tradery.news.model;

import java.time.Instant;
import java.util.List;

/**
 * A cluster of articles covering the same event/topic.
 */
public record Story(
    String id,
    String title,               // AI-generated canonical title
    String summary,             // Merged summary across articles
    StoryType type,
    Instant firstSeen,          // Earliest article
    Instant lastUpdated,        // Most recent article
    Instant peakTime,           // When most articles published
    int articleCount,
    double avgSentiment,
    ImportanceLevel importance, // Highest importance from articles
    List<String> articleIds,
    List<String> coins,
    List<String> topics,
    List<String> tags,
    List<String> eventIds,
    boolean isActive            // Still receiving new articles?
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String title;
        private String summary;
        private StoryType type = StoryType.BREAKING;
        private Instant firstSeen;
        private Instant lastUpdated;
        private Instant peakTime;
        private int articleCount = 1;
        private double avgSentiment;
        private ImportanceLevel importance = ImportanceLevel.MEDIUM;
        private List<String> articleIds = List.of();
        private List<String> coins = List.of();
        private List<String> topics = List.of();
        private List<String> tags = List.of();
        private List<String> eventIds = List.of();
        private boolean isActive = true;

        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder type(StoryType type) { this.type = type; return this; }
        public Builder firstSeen(Instant firstSeen) { this.firstSeen = firstSeen; return this; }
        public Builder lastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; return this; }
        public Builder peakTime(Instant peakTime) { this.peakTime = peakTime; return this; }
        public Builder articleCount(int articleCount) { this.articleCount = articleCount; return this; }
        public Builder avgSentiment(double avgSentiment) { this.avgSentiment = avgSentiment; return this; }
        public Builder importance(ImportanceLevel importance) { this.importance = importance; return this; }
        public Builder articleIds(List<String> articleIds) { this.articleIds = articleIds; return this; }
        public Builder coins(List<String> coins) { this.coins = coins; return this; }
        public Builder topics(List<String> topics) { this.topics = topics; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }
        public Builder eventIds(List<String> eventIds) { this.eventIds = eventIds; return this; }
        public Builder isActive(boolean isActive) { this.isActive = isActive; return this; }

        public Story build() {
            return new Story(id, title, summary, type, firstSeen, lastUpdated, peakTime,
                articleCount, avgSentiment, importance, articleIds, coins, topics, tags, eventIds, isActive);
        }
    }
}
