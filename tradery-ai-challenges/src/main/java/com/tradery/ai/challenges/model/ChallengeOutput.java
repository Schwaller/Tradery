package com.tradery.ai.challenges.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines what a challenge produces and where to store the result.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ChallengeOutput {

    public enum Type {
        /** Free-form text answer stored as an attribute value. */
        TEXT,
        /** List of strings stored as a list attribute. */
        LIST,
        /** Set of discovered entities + relationships (delegates to DiscoveryPipeline). */
        ENTITY_SET,
        /** Structured output with named fields (headline, explanation, score, etc.). */
        STRUCTURED
    }

    /**
     * A named field in a structured output.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class Field {
        public enum FieldType { TEXT, NUMBER, SCORE }
        /** How to handle missing data points in charts for this field. */
        public enum GapMode {
            /** Draw straight line between present points (default). */
            CONNECT,
            /** Treat absence as 0. */
            ZERO,
            /** Show dashed line over gaps. */
            GAP
        }

        private String name;
        private String label;
        private FieldType type = FieldType.TEXT;
        private double minValue;
        private double maxValue;
        private boolean primary;
        private GapMode gapMode = GapMode.CONNECT;

        public Field() {}

        public Field(String name, String label, FieldType type) {
            this.name = name;
            this.label = label;
            this.type = type;
        }

        public String name() { return name; }
        public void setName(String name) { this.name = name; }

        public String label() { return label; }
        public void setLabel(String label) { this.label = label; }

        public FieldType type() { return type; }
        public void setType(FieldType type) { this.type = type; }

        /** Range for NUMBER/SCORE fields. */
        public double minValue() { return minValue; }
        public void setMinValue(double minValue) { this.minValue = minValue; }

        public double maxValue() { return maxValue; }
        public void setMaxValue(double maxValue) { this.maxValue = maxValue; }

        /** If true, this field is shown prominently (e.g., as the box headline). */
        public boolean primary() { return primary; }
        public void setPrimary(boolean primary) { this.primary = primary; }

        /** How charts handle missing data points (only applies to NUMBER/SCORE). */
        public GapMode gapMode() { return gapMode; }
        public void setGapMode(GapMode gapMode) { this.gapMode = gapMode != null ? gapMode : GapMode.CONNECT; }

        public static Field text(String name, String label) {
            return new Field(name, label, FieldType.TEXT);
        }

        public static Field text(String name, String label, boolean primary) {
            Field f = new Field(name, label, FieldType.TEXT);
            f.setPrimary(primary);
            return f;
        }

        public static Field number(String name, String label, double min, double max) {
            Field f = new Field(name, label, FieldType.NUMBER);
            f.setMinValue(min);
            f.setMaxValue(max);
            return f;
        }

        public static Field score(String name, String label, double min, double max) {
            Field f = new Field(name, label, FieldType.SCORE);
            f.setMinValue(min);
            f.setMaxValue(max);
            return f;
        }
    }

    /** How detailed the _reason justification for numeric fields should be. */
    public enum ReasonDetail {
        /** No justification. */
        NONE,
        /** One sentence. */
        BRIEF,
        /** A few sentences with supporting evidence. */
        DETAILED,
        /** Thorough multi-sentence analysis. */
        VERBOSE
    }

    /** How a structured list behaves across runs. */
    public enum ListBehavior {
        /** Entities persist across runs. Forward-feeds previous items, tracks removed, temporal charts. */
        TRACKING,
        /** Each run is a fresh independent list. No forward-feed, no charts, no removed tracking. */
        SNAPSHOT
    }

    private Type type = Type.TEXT;
    private Map<String, String> config = new LinkedHashMap<>();
    private List<Field> fields = new ArrayList<>();
    /** When true with STRUCTURED type, the AI returns a JSON array of objects instead of a single object. */
    private boolean listMode;
    private ListBehavior listBehavior = ListBehavior.TRACKING;
    private ReasonDetail reasonDetail = ReasonDetail.NONE;

    public ChallengeOutput() {}

    public ChallengeOutput(Type type) {
        this.type = type;
    }

    public Type type() { return type; }
    public void setType(Type type) { this.type = type; }

    /**
     * Type-specific configuration:
     * <ul>
     *   <li>TEXT: {@code attributeName} — entity attribute to write the result to</li>
     *   <li>LIST: {@code attributeName} — entity attribute to write the list to</li>
     *   <li>ENTITY_SET: {@code relationshipTypeId} — relationship type for discovered entities,
     *       {@code systemContext} — optional system prompt override</li>
     * </ul>
     */
    public Map<String, String> config() { return config; }
    public void setConfig(Map<String, String> config) {
        this.config = config != null ? config : new LinkedHashMap<>();
    }

    /** For STRUCTURED type: the expected output fields. */
    public List<Field> fields() { return fields; }
    public void setFields(List<Field> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }

    /** When true, STRUCTURED returns a JSON array of objects instead of a single object. */
    public boolean listMode() { return listMode; }
    public void setListMode(boolean listMode) { this.listMode = listMode; }

    /** How the list behaves across runs (only relevant when listMode=true). */
    public ListBehavior listBehavior() { return listBehavior; }
    public void setListBehavior(ListBehavior listBehavior) {
        this.listBehavior = listBehavior != null ? listBehavior : ListBehavior.TRACKING;
    }

    public boolean isTracking() { return listMode && listBehavior == ListBehavior.TRACKING; }
    public boolean isSnapshot() { return listMode && listBehavior == ListBehavior.SNAPSHOT; }

    /** How detailed the _reason justifications should be for numeric fields. */
    public ReasonDetail reasonDetail() { return reasonDetail; }
    public void setReasonDetail(ReasonDetail reasonDetail) {
        this.reasonDetail = reasonDetail != null ? reasonDetail : ReasonDetail.NONE;
    }

    public String configValue(String key) {
        return config.get(key);
    }

    public String configValue(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }
}
