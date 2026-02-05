package com.tradery.news.model;

import java.time.Instant;
import java.util.List;

/**
 * Core news article unit. Preserves original content while adding AI-extracted structure.
 */
public record Article(
    // === ORIGINAL CONTENT (always preserved) ===
    String id,                      // SHA256(url)
    String sourceUrl,               // Original URL - always kept for reference
    String sourceId,                // "coindesk", "cointelegraph", "x-elonmusk"
    String sourceName,              // "CoinDesk", "X/@elonmusk"
    String title,                   // Original title
    String content,                 // Full original text - never truncated
    String author,                  // Original author if available
    Instant publishedAt,            // Original publish time

    // === AI-EXTRACTED STRUCTURE ===
    String summary,                 // AI-generated 2-3 sentence summary
    ImportanceLevel importance,     // CRITICAL, HIGH, MEDIUM, LOW, NOISE
    List<String> coins,             // ["BTC", "ETH", "SOL"]
    List<String> topics,            // ["crypto", "crypto.regulation"]
    List<String> categories,        // ["Regulatory", "DeFi"]
    List<String> tags,              // ["#etf", "#sec", "#approval"]
    double sentimentScore,          // -1.0 to +1.0
    List<String> eventIds,          // Links to extracted NewsEvent records
    List<String> entityIds,         // Links to extracted Entity records

    // === METADATA ===
    Instant fetchedAt,              // When we fetched it
    Instant processedAt,            // When AI processed it
    ProcessingStatus status         // PENDING, PROCESSING, COMPLETE, ERROR
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String sourceUrl;
        private String sourceId;
        private String sourceName;
        private String title;
        private String content;
        private String author;
        private Instant publishedAt;
        private String summary;
        private ImportanceLevel importance = ImportanceLevel.MEDIUM;
        private List<String> coins = List.of();
        private List<String> topics = List.of();
        private List<String> categories = List.of();
        private List<String> tags = List.of();
        private double sentimentScore;
        private List<String> eventIds = List.of();
        private List<String> entityIds = List.of();
        private Instant fetchedAt;
        private Instant processedAt;
        private ProcessingStatus status = ProcessingStatus.PENDING;

        public Builder id(String id) { this.id = id; return this; }
        public Builder sourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
        public Builder sourceId(String sourceId) { this.sourceId = sourceId; return this; }
        public Builder sourceName(String sourceName) { this.sourceName = sourceName; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder author(String author) { this.author = author; return this; }
        public Builder publishedAt(Instant publishedAt) { this.publishedAt = publishedAt; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder importance(ImportanceLevel importance) { this.importance = importance; return this; }
        public Builder coins(List<String> coins) { this.coins = coins; return this; }
        public Builder topics(List<String> topics) { this.topics = topics; return this; }
        public Builder categories(List<String> categories) { this.categories = categories; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }
        public Builder sentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; return this; }
        public Builder eventIds(List<String> eventIds) { this.eventIds = eventIds; return this; }
        public Builder entityIds(List<String> entityIds) { this.entityIds = entityIds; return this; }
        public Builder fetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; return this; }
        public Builder processedAt(Instant processedAt) { this.processedAt = processedAt; return this; }
        public Builder status(ProcessingStatus status) { this.status = status; return this; }

        public Article build() {
            return new Article(id, sourceUrl, sourceId, sourceName, title, content, author,
                publishedAt, summary, importance, coins, topics, categories, tags, sentimentScore,
                eventIds, entityIds, fetchedAt, processedAt, status);
        }
    }
}
