package com.tradery.news.ui.coin;

import com.tradery.ai.challenges.subject.ChallengeSubject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts CoinEntity to the reusable ChallengeSubject interface.
 */
public class CoinEntitySubject implements ChallengeSubject {

    private final CoinEntity entity;
    private final SchemaRegistry registry;
    private final EntityStore entityStore;

    public CoinEntitySubject(CoinEntity entity, SchemaRegistry registry, EntityStore entityStore) {
        this.entity = entity;
        this.registry = registry;
        this.entityStore = entityStore;
    }

    @Override
    public String id() {
        return entity.id();
    }

    @Override
    public String name() {
        return entity.name();
    }

    @Override
    public String symbol() {
        return entity.symbol();
    }

    @Override
    public String typeId() {
        return entity.type().name().toLowerCase();
    }

    @Override
    public Map<String, String> attributes() {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (!entity.categories().isEmpty()) {
            attrs.put("categories", String.join(", ", entity.categories()));
        }
        if (entity.marketCap() > 0) {
            attrs.put("marketCap", "$" + formatMarketCap(entity.marketCap()));
        }

        // Pull schema attribute values
        SchemaType schemaType = registry.getType(typeId());
        if (schemaType != null && entityStore != null) {
            Map<String, String> values = registry.getAttributeValues(entity.id(), schemaType.id());
            for (SchemaAttribute attr : schemaType.attributes()) {
                String val = values.get(attr.name());
                if (val != null && !val.isBlank()) {
                    attrs.put(attr.name(), val);
                }
            }
        }

        return attrs;
    }

    public CoinEntity entity() {
        return entity;
    }

    private static String formatMarketCap(double marketCap) {
        if (marketCap >= 1_000_000_000_000L) {
            return String.format("%.1fT", marketCap / 1_000_000_000_000L);
        } else if (marketCap >= 1_000_000_000L) {
            return String.format("%.1fB", marketCap / 1_000_000_000L);
        } else if (marketCap >= 1_000_000L) {
            return String.format("%.1fM", marketCap / 1_000_000L);
        }
        return String.format("%.0f", marketCap);
    }
}
