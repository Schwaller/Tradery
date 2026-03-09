package com.tradery.ai.challenges.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * A single escalation level for a challenge.
 * Maps to a tier in the AI pipeline config (fast/standard/premium)
 * and optionally a named pipeline for ENTITY_SET output.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ChallengeEscalation {

    private String label;
    private String tier;
    private String pipeline;
    private boolean verify;
    private String description;

    public ChallengeEscalation() {}

    public ChallengeEscalation(String label, String tier) {
        this.label = label;
        this.tier = tier;
    }

    public String label() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String tier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    /** Pipeline name for ENTITY_SET challenges (e.g., "quick", "thorough", "deep"). */
    public String pipeline() { return pipeline; }
    public void setPipeline(String pipeline) { this.pipeline = pipeline; }

    /** Whether to run a verification query after the main query (TEXT/LIST). */
    public boolean verify() { return verify; }
    public void setVerify(boolean verify) { this.verify = verify; }

    public String description() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return label != null ? label : tier;
    }
}
