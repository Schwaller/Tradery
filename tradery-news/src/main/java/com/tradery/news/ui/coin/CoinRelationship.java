package com.tradery.news.ui.coin;

import java.awt.*;

/**
 * Relationship between two entities in the coin graph.
 */
public class CoinRelationship {

    public enum Type {
        L2_OF(new Color(150, 130, 255), "L2 of"),           // L2 built on L1
        ETF_TRACKS(new Color(80, 200, 120), "tracks"),       // ETF tracks coin
        ETP_TRACKS(new Color(100, 200, 150), "tracks"),      // ETP tracks coin
        INVESTED_IN(new Color(255, 180, 80), "invested"),    // VC invested in
        FOUNDED_BY(new Color(180, 150, 255), "founded"),     // Founded by
        PARTNER(new Color(150, 150, 200), "partner"),        // Partnership
        FORK_OF(new Color(200, 150, 150), "fork of"),        // Forked from
        BRIDGE(new Color(150, 200, 200), "bridge"),          // Bridge connection
        ECOSYSTEM(new Color(180, 180, 150), "ecosystem"),    // Part of ecosystem
        COMPETITOR(new Color(200, 100, 100), "competes");    // Competitors

        private final Color color;
        private final String label;
        Type(Color color, String label) {
            this.color = color;
            this.label = label;
        }
        public Color color() { return color; }
        public String label() { return label; }

        /**
         * Get relationship types that are searchable for a given entity type.
         */
        public static java.util.List<Type> getSearchableTypes(CoinEntity.Type entityType) {
            return switch (entityType) {
                case COIN, L2 -> java.util.List.of(ETF_TRACKS, ETP_TRACKS, INVESTED_IN, L2_OF, ECOSYSTEM, PARTNER, FORK_OF);
                case VC -> java.util.List.of(INVESTED_IN, FOUNDED_BY, PARTNER);
                case EXCHANGE -> java.util.List.of(ECOSYSTEM, PARTNER);
                case ETF, ETP, DAT -> java.util.List.of(ETF_TRACKS, ETP_TRACKS);
                case FOUNDATION -> java.util.List.of(FOUNDED_BY, ECOSYSTEM, PARTNER);
                case COMPANY -> java.util.List.of(INVESTED_IN, PARTNER, FOUNDED_BY);
                default -> java.util.List.of();
            };
        }
    }

    private final String fromId;
    private final String toId;
    private final Type type;
    private final String note;  // Optional description

    public CoinRelationship(String fromId, String toId, Type type) {
        this(fromId, toId, type, null);
    }

    public CoinRelationship(String fromId, String toId, Type type, String note) {
        this.fromId = fromId;
        this.toId = toId;
        this.type = type;
        this.note = note;
    }

    public String fromId() { return fromId; }
    public String toId() { return toId; }
    public Type type() { return type; }
    public String note() { return note; }

    public Color getColor() {
        return type.color();
    }
}
