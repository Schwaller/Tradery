package com.tradery.ai.challenges.schedule;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * Tracks a subject's subscription to a challenge for auto-refresh.
 * Created when a user first runs a challenge on a subject.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ChallengeSubscription {

    private String challengeId;
    private String subjectId;
    private long lastRunTimestamp;
    private long nextRunTimestamp;

    public ChallengeSubscription() {}

    public ChallengeSubscription(String challengeId, String subjectId,
                                  long lastRunTimestamp, long nextRunTimestamp) {
        this.challengeId = challengeId;
        this.subjectId = subjectId;
        this.lastRunTimestamp = lastRunTimestamp;
        this.nextRunTimestamp = nextRunTimestamp;
    }

    public String challengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }

    public String subjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public long lastRunTimestamp() { return lastRunTimestamp; }
    public void setLastRunTimestamp(long lastRunTimestamp) { this.lastRunTimestamp = lastRunTimestamp; }

    public long nextRunTimestamp() { return nextRunTimestamp; }
    public void setNextRunTimestamp(long nextRunTimestamp) { this.nextRunTimestamp = nextRunTimestamp; }

    /** Whether this subscription is due for a refresh. */
    public boolean isDue() {
        return System.currentTimeMillis() >= nextRunTimestamp;
    }
}
