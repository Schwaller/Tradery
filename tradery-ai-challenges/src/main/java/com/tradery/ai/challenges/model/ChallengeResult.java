package com.tradery.ai.challenges.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.tradery.ai.pipeline.DiscoveryResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a single challenge execution. Immutable once created.
 * Every execution is stored, building a history over time.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ChallengeResult {

    private long id;
    private String challengeId;
    private String subjectId;
    private int escalationIndex;
    private ChallengeOutput.Type outputType;

    // Output — one populated based on outputType
    private String textResult;
    private List<String> listResult;
    private DiscoveryResult entityResult;
    private Map<String, String> fields;
    /** For STRUCTURED listMode: array of objects, each with the defined fields. */
    private List<Map<String, String>> itemResults;

    // Signal
    private Double signalValue;

    // Metadata
    private long timestamp;
    private long durationMs;
    private String resolvedTier;
    private boolean verified;
    private String error;

    public ChallengeResult() {}

    public long id() { return id; }
    public void setId(long id) { this.id = id; }

    public String challengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }

    public String subjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public int escalationIndex() { return escalationIndex; }
    public void setEscalationIndex(int escalationIndex) { this.escalationIndex = escalationIndex; }

    public ChallengeOutput.Type outputType() { return outputType; }
    public void setOutputType(ChallengeOutput.Type outputType) { this.outputType = outputType; }

    public String textResult() { return textResult; }
    public void setTextResult(String textResult) { this.textResult = textResult; }

    public List<String> listResult() { return listResult; }
    public void setListResult(List<String> listResult) { this.listResult = listResult; }

    public DiscoveryResult entityResult() { return entityResult; }
    public void setEntityResult(DiscoveryResult entityResult) { this.entityResult = entityResult; }

    public Map<String, String> fields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }

    public List<Map<String, String>> itemResults() { return itemResults; }
    public void setItemResults(List<Map<String, String>> itemResults) { this.itemResults = itemResults; }

    public Double signalValue() { return signalValue; }
    public void setSignalValue(Double signalValue) { this.signalValue = signalValue; }

    public long timestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long durationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String resolvedTier() { return resolvedTier; }
    public void setResolvedTier(String resolvedTier) { this.resolvedTier = resolvedTier; }

    public boolean verified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String error() { return error; }
    public void setError(String error) { this.error = error; }

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    public boolean hasSignal() {
        return signalValue != null;
    }

    // ==================== Factory Methods ====================

    public static ChallengeResult text(String challengeId, String subjectId, int escalationIndex,
                                        String text, Double signal, long durationMs) {
        ChallengeResult r = new ChallengeResult();
        r.challengeId = challengeId;
        r.subjectId = subjectId;
        r.escalationIndex = escalationIndex;
        r.outputType = ChallengeOutput.Type.TEXT;
        r.textResult = text;
        r.signalValue = signal;
        r.timestamp = System.currentTimeMillis();
        r.durationMs = durationMs;
        return r;
    }

    public static ChallengeResult list(String challengeId, String subjectId, int escalationIndex,
                                        List<String> items, Double signal, long durationMs) {
        ChallengeResult r = new ChallengeResult();
        r.challengeId = challengeId;
        r.subjectId = subjectId;
        r.escalationIndex = escalationIndex;
        r.outputType = ChallengeOutput.Type.LIST;
        r.listResult = items;
        r.signalValue = signal;
        r.timestamp = System.currentTimeMillis();
        r.durationMs = durationMs;
        return r;
    }

    public static ChallengeResult structured(String challengeId, String subjectId, int escalationIndex,
                                              Map<String, String> fields, Double signal, long durationMs) {
        ChallengeResult r = new ChallengeResult();
        r.challengeId = challengeId;
        r.subjectId = subjectId;
        r.escalationIndex = escalationIndex;
        r.outputType = ChallengeOutput.Type.STRUCTURED;
        r.fields = fields;
        r.signalValue = signal;
        r.timestamp = System.currentTimeMillis();
        r.durationMs = durationMs;
        return r;
    }

    public static ChallengeResult structuredList(String challengeId, String subjectId, int escalationIndex,
                                                  List<Map<String, String>> items, Double signal, long durationMs) {
        ChallengeResult r = new ChallengeResult();
        r.challengeId = challengeId;
        r.subjectId = subjectId;
        r.escalationIndex = escalationIndex;
        r.outputType = ChallengeOutput.Type.STRUCTURED;
        r.itemResults = items;
        r.signalValue = signal;
        r.timestamp = System.currentTimeMillis();
        r.durationMs = durationMs;
        return r;
    }

    public static ChallengeResult entitySet(String challengeId, String subjectId, int escalationIndex,
                                             DiscoveryResult result, Double signal, long durationMs) {
        ChallengeResult r = new ChallengeResult();
        r.challengeId = challengeId;
        r.subjectId = subjectId;
        r.escalationIndex = escalationIndex;
        r.outputType = ChallengeOutput.Type.ENTITY_SET;
        r.entityResult = result;
        r.signalValue = signal;
        r.timestamp = System.currentTimeMillis();
        r.durationMs = durationMs;
        return r;
    }

    public static ChallengeResult error(String challengeId, String subjectId, int escalationIndex,
                                         ChallengeOutput.Type outputType, String error, long durationMs) {
        ChallengeResult r = new ChallengeResult();
        r.challengeId = challengeId;
        r.subjectId = subjectId;
        r.escalationIndex = escalationIndex;
        r.outputType = outputType;
        r.error = error;
        r.timestamp = System.currentTimeMillis();
        r.durationMs = durationMs;
        return r;
    }
}
