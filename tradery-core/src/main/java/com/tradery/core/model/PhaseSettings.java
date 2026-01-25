package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase filtering configuration for a Strategy.
 * Contains required phase IDs (all must be active) and
 * excluded phase IDs (none must be active) for entry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhaseSettings {

    // List of required phase IDs - ALL must be active for entry
    private List<String> requiredPhaseIds;

    // List of excluded phase IDs - NONE must be active for entry
    private List<String> excludedPhaseIds;

    public PhaseSettings() {
        this.requiredPhaseIds = new ArrayList<>();
        this.excludedPhaseIds = new ArrayList<>();
    }

    public List<String> getRequiredPhaseIds() {
        if (requiredPhaseIds == null) {
            requiredPhaseIds = new ArrayList<>();
        }
        return requiredPhaseIds;
    }

    public void setRequiredPhaseIds(List<String> requiredPhaseIds) {
        this.requiredPhaseIds = requiredPhaseIds != null ? requiredPhaseIds : new ArrayList<>();
    }

    public List<String> getExcludedPhaseIds() {
        if (excludedPhaseIds == null) {
            excludedPhaseIds = new ArrayList<>();
        }
        return excludedPhaseIds;
    }

    public void setExcludedPhaseIds(List<String> excludedPhaseIds) {
        this.excludedPhaseIds = excludedPhaseIds != null ? excludedPhaseIds : new ArrayList<>();
    }

    @JsonIgnore
    public boolean hasPhaseFilters() {
        return (requiredPhaseIds != null && !requiredPhaseIds.isEmpty())
            || (excludedPhaseIds != null && !excludedPhaseIds.isEmpty());
    }

    @JsonIgnore
    public boolean hasRequiredPhases() {
        return requiredPhaseIds != null && !requiredPhaseIds.isEmpty();
    }

    @JsonIgnore
    public boolean hasExcludedPhases() {
        return excludedPhaseIds != null && !excludedPhaseIds.isEmpty();
    }

    public static PhaseSettings defaults() {
        return new PhaseSettings();
    }
}
