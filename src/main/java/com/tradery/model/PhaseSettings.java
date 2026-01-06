package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase filtering configuration for a Strategy.
 * Contains the list of required phase IDs that must all be active
 * for the strategy to enter trades.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhaseSettings {

    // List of required phase IDs - ALL must be active for entry
    private List<String> requiredPhaseIds;

    public PhaseSettings() {
        this.requiredPhaseIds = new ArrayList<>();
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

    public boolean hasRequiredPhases() {
        return requiredPhaseIds != null && !requiredPhaseIds.isEmpty();
    }

    public void addRequiredPhase(String phaseId) {
        if (requiredPhaseIds == null) {
            requiredPhaseIds = new ArrayList<>();
        }
        if (!requiredPhaseIds.contains(phaseId)) {
            requiredPhaseIds.add(phaseId);
        }
    }

    public void removeRequiredPhase(String phaseId) {
        if (requiredPhaseIds != null) {
            requiredPhaseIds.remove(phaseId);
        }
    }

    public static PhaseSettings defaults() {
        return new PhaseSettings();
    }
}
