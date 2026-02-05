package com.tradery.news.model;

import java.time.Instant;
import java.util.List;

/**
 * A named entity (person, company, coin, etc.) that appears in news.
 */
public record Entity(
    String id,                  // Normalized: "bitcoin", "vitalik-buterin"
    EntityType type,
    String name,                // Display name
    String symbol,              // "BTC", "ETH" for coins
    String description,
    List<String> aliases,       // Alternative names
    Instant createdAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private EntityType type;
        private String name;
        private String symbol;
        private String description;
        private List<String> aliases = List.of();
        private Instant createdAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder type(EntityType type) { this.type = type; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder symbol(String symbol) { this.symbol = symbol; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder aliases(List<String> aliases) { this.aliases = aliases; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Entity build() {
            return new Entity(id, type, name, symbol, description, aliases, createdAt);
        }
    }
}
