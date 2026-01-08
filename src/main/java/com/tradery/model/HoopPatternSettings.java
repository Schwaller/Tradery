package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Hoop pattern filtering configuration for a Strategy.
 * Mirrors PhaseSettings structure for consistency but adds
 * combine modes and separate entry/exit pattern lists.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HoopPatternSettings {

    /**
     * How hoop patterns combine with DSL conditions.
     */
    public enum CombineMode {
        DSL_ONLY("dsl_only"),       // Only check DSL conditions (hoops ignored) - default
        HOOP_ONLY("hoop_only"),     // Only check hoop patterns (DSL ignored)
        AND("and"),                  // Both hoop pattern AND DSL must trigger
        OR("or");                    // Either hoop pattern OR DSL can trigger

        private final String value;

        CombineMode(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public static CombineMode fromString(String value) {
            for (CombineMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return DSL_ONLY;
        }
    }

    // Mode selection
    private CombineMode entryMode = CombineMode.DSL_ONLY;
    private CombineMode exitMode = CombineMode.DSL_ONLY;

    // Entry hoop patterns - ALL required must complete, NONE of excluded must be active
    private List<String> requiredEntryPatternIds = new ArrayList<>();
    private List<String> excludedEntryPatternIds = new ArrayList<>();

    // Exit hoop patterns - ALL required must complete, NONE of excluded must be active
    private List<String> requiredExitPatternIds = new ArrayList<>();
    private List<String> excludedExitPatternIds = new ArrayList<>();

    public HoopPatternSettings() {
        // For Jackson
    }

    // Entry mode

    public CombineMode getEntryMode() {
        return entryMode != null ? entryMode : CombineMode.DSL_ONLY;
    }

    public void setEntryMode(CombineMode mode) {
        this.entryMode = mode != null ? mode : CombineMode.DSL_ONLY;
    }

    // Exit mode

    public CombineMode getExitMode() {
        return exitMode != null ? exitMode : CombineMode.DSL_ONLY;
    }

    public void setExitMode(CombineMode mode) {
        this.exitMode = mode != null ? mode : CombineMode.DSL_ONLY;
    }

    // Entry pattern IDs

    public List<String> getRequiredEntryPatternIds() {
        if (requiredEntryPatternIds == null) {
            requiredEntryPatternIds = new ArrayList<>();
        }
        return requiredEntryPatternIds;
    }

    public void setRequiredEntryPatternIds(List<String> ids) {
        this.requiredEntryPatternIds = ids != null ? ids : new ArrayList<>();
    }

    public List<String> getExcludedEntryPatternIds() {
        if (excludedEntryPatternIds == null) {
            excludedEntryPatternIds = new ArrayList<>();
        }
        return excludedEntryPatternIds;
    }

    public void setExcludedEntryPatternIds(List<String> ids) {
        this.excludedEntryPatternIds = ids != null ? ids : new ArrayList<>();
    }

    // Exit pattern IDs

    public List<String> getRequiredExitPatternIds() {
        if (requiredExitPatternIds == null) {
            requiredExitPatternIds = new ArrayList<>();
        }
        return requiredExitPatternIds;
    }

    public void setRequiredExitPatternIds(List<String> ids) {
        this.requiredExitPatternIds = ids != null ? ids : new ArrayList<>();
    }

    public List<String> getExcludedExitPatternIds() {
        if (excludedExitPatternIds == null) {
            excludedExitPatternIds = new ArrayList<>();
        }
        return excludedExitPatternIds;
    }

    public void setExcludedExitPatternIds(List<String> ids) {
        this.excludedExitPatternIds = ids != null ? ids : new ArrayList<>();
    }

    // Convenience checks

    public boolean hasEntryPatterns() {
        return !getRequiredEntryPatternIds().isEmpty() || !getExcludedEntryPatternIds().isEmpty();
    }

    public boolean hasExitPatterns() {
        return !getRequiredExitPatternIds().isEmpty() || !getExcludedExitPatternIds().isEmpty();
    }

    public boolean usesHoopsForEntry() {
        return entryMode != CombineMode.DSL_ONLY && hasEntryPatterns();
    }

    public boolean usesHoopsForExit() {
        return exitMode != CombineMode.DSL_ONLY && hasExitPatterns();
    }

    public boolean hasAnyPatterns() {
        return hasEntryPatterns() || hasExitPatterns();
    }

    /**
     * Get all unique pattern IDs referenced by this settings object.
     */
    public List<String> getAllPatternIds() {
        List<String> allIds = new ArrayList<>();
        allIds.addAll(getRequiredEntryPatternIds());
        allIds.addAll(getExcludedEntryPatternIds());
        allIds.addAll(getRequiredExitPatternIds());
        allIds.addAll(getExcludedExitPatternIds());
        return allIds.stream().distinct().toList();
    }

    public static HoopPatternSettings defaults() {
        return new HoopPatternSettings();
    }
}
