package com.tradery.news.ui.coin;

import java.awt.Color;

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
