package com.tradery.news.ui.coin;

import java.awt.*;

/**
 * Relationship between two entities in the coin graph.
 * Uses string-based typeId resolved from SchemaRegistry.
 */
public class CoinRelationship {

    private final String fromId;
    private final String toId;
    private final String typeId;
    private final String note;  // Optional description

    public CoinRelationship(String fromId, String toId, String typeId) {
        this(fromId, toId, typeId, null);
    }

    public CoinRelationship(String fromId, String toId, String typeId, String note) {
        this.fromId = fromId;
        this.toId = toId;
        this.typeId = typeId;
        this.note = note;
    }

    public String fromId() { return fromId; }
    public String toId() { return toId; }
    public String typeId() { return typeId; }
    public String note() { return note; }

    /** Resolve color from schema registry. Falls back to gray. */
    public Color getColor(SchemaRegistry registry) {
        if (registry != null) {
            SchemaType schema = registry.getType(typeId);
            if (schema != null) return schema.color();
        }
        return Color.GRAY;
    }

    /** Resolve label from schema registry. Falls back to typeId. */
    public String getLabel(SchemaRegistry registry) {
        if (registry != null) {
            SchemaType schema = registry.getType(typeId);
            if (schema != null && schema.label() != null) return schema.label();
        }
        return typeId;
    }
}
