package com.tradery.model;

/**
 * Represents an entry condition for a trading strategy.
 * Supports multiple entry conditions with optional weighting for future use.
 */
public record EntryCondition(
    String name,              // e.g., "Primary", "Confirmation"
    String condition,         // DSL expression for entry signal
    boolean enabled           // Whether this condition is active
) {
    /**
     * Create a default entry condition.
     */
    public static EntryCondition defaultCondition() {
        return new EntryCondition("Primary", "", true);
    }

    /**
     * Create an entry condition with just a condition string.
     */
    public static EntryCondition of(String condition) {
        return new EntryCondition("Primary", condition != null ? condition : "", true);
    }

    /**
     * Create an entry condition builder for fluent construction.
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String condition = "";
        private boolean enabled = true;

        public Builder(String name) {
            this.name = name;
        }

        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public EntryCondition build() {
            return new EntryCondition(name, condition, enabled);
        }
    }
}
