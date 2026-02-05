package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Hoop pattern filtering configuration for a Strategy.
 * Mirrors PhaseSettings structure - hoops are always AND'ed with DSL conditions.
 * If required patterns are set, they must complete AND the DSL must trigger.
 * If excluded patterns are set, entry is blocked when they're active.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HoopPatternSettings {

    // Entry hoop patterns - ALL required must complete, NONE of excluded must be active
    private List<String> requiredEntryPatternIds = new ArrayList<>();
    private List<String> excludedEntryPatternIds = new ArrayList<>();

    // Exit hoop patterns - ALL required must complete, NONE of excluded must be active
    private List<String> requiredExitPatternIds = new ArrayList<>();
    private List<String> excludedExitPatternIds = new ArrayList<>();

    public HoopPatternSettings() {
        // For Jackson
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

    @JsonIgnore
    public boolean hasEntryPatterns() {
        return !getRequiredEntryPatternIds().isEmpty() || !getExcludedEntryPatternIds().isEmpty();
    }

    @JsonIgnore
    public boolean hasExitPatterns() {
        return !getRequiredExitPatternIds().isEmpty() || !getExcludedExitPatternIds().isEmpty();
    }

    @JsonIgnore
    public boolean hasAnyPatterns() {
        return hasEntryPatterns() || hasExitPatterns();
    }

    /**
     * Get all unique pattern IDs referenced by this settings object.
     * Not serialized - computed on demand.
     */
    @JsonIgnore
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
