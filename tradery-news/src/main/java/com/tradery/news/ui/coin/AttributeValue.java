package com.tradery.news.ui.coin;

/**
 * A stored attribute value with provenance metadata.
 * Tracks who set the value (source, AI, or user) and when.
 * Priority chain: USER > AI > SOURCE — a source refresh won't overwrite a user correction.
 */
public record AttributeValue(String value, Origin origin, long updatedAt) {

    public enum Origin {
        SOURCE(0), AI(1), USER(2);

        private final int priority;

        Origin(int priority) {
            this.priority = priority;
        }

        public int priority() { return priority; }

        /** Returns true if this origin can override the other. */
        public boolean overrides(Origin other) {
            return this.priority >= other.priority;
        }

        /** Returns true if a new value with newOrigin can replace a value with this origin. */
        public boolean canBeOverriddenBy(Origin newOrigin) {
            return newOrigin.priority >= this.priority;
        }
    }

    public static AttributeValue of(String value, Origin origin) {
        return new AttributeValue(value, origin, System.currentTimeMillis());
    }

    public static AttributeValue of(String value, Origin origin, long updatedAt) {
        return new AttributeValue(value, origin, updatedAt);
    }
}
