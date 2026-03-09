package com.tradery.ai.challenges.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * An AI research challenge that can be run against any subject.
 * Defines what to ask, how to ask it (escalation tiers), and what output to expect.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Challenge {

    private String id;
    private String title;
    private String description;
    private List<String> targetTypeIds = new ArrayList<>();
    private ChallengeOutput output = new ChallengeOutput();
    private List<ChallengeEscalation> escalations = new ArrayList<>();
    private SignalConfig signalConfig;
    private Duration refreshInterval;
    private int refreshEscalationIndex;
    private int displayOrder;
    private boolean enabled = true;

    public Challenge() {}

    public Challenge(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String id() { return id; }
    public void setId(String id) { this.id = id; }

    public String title() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String description() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> targetTypeIds() { return targetTypeIds; }
    public void setTargetTypeIds(List<String> targetTypeIds) {
        this.targetTypeIds = targetTypeIds != null ? targetTypeIds : new ArrayList<>();
    }

    public ChallengeOutput output() { return output; }
    public void setOutput(ChallengeOutput output) { this.output = output; }

    public List<ChallengeEscalation> escalations() { return escalations; }
    public void setEscalations(List<ChallengeEscalation> escalations) {
        this.escalations = escalations != null ? escalations : new ArrayList<>();
    }

    public SignalConfig signalConfig() { return signalConfig; }
    public void setSignalConfig(SignalConfig signalConfig) { this.signalConfig = signalConfig; }

    public Duration refreshInterval() { return refreshInterval; }
    public void setRefreshInterval(Duration refreshInterval) { this.refreshInterval = refreshInterval; }

    public int refreshEscalationIndex() { return refreshEscalationIndex; }
    public void setRefreshEscalationIndex(int refreshEscalationIndex) { this.refreshEscalationIndex = refreshEscalationIndex; }

    public int displayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * Whether this challenge applies to the given subject type.
     * Empty targetTypeIds means applies to all types.
     */
    public boolean appliesTo(String typeId) {
        return targetTypeIds.isEmpty() || targetTypeIds.contains(typeId);
    }

    @Override
    public String toString() {
        return title != null ? title : id;
    }
}
