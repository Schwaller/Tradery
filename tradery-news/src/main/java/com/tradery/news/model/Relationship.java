package com.tradery.news.model;

import java.time.Instant;

/**
 * A relationship between two entities.
 */
public record Relationship(
    String id,
    String sourceId,
    String targetId,
    RelationType type,
    double strength,            // 0.0 to 1.0
    String context,             // "SEC filed lawsuit against..."
    Instant createdAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String sourceId;
        private String targetId;
        private RelationType type;
        private double strength = 1.0;
        private String context;
        private Instant createdAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder sourceId(String sourceId) { this.sourceId = sourceId; return this; }
        public Builder targetId(String targetId) { this.targetId = targetId; return this; }
        public Builder type(RelationType type) { this.type = type; return this; }
        public Builder strength(double strength) { this.strength = strength; return this; }
        public Builder context(String context) { this.context = context; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Relationship build() {
            return new Relationship(id, sourceId, targetId, type, strength, context, createdAt);
        }
    }
}
