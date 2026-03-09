package com.tradery.ai.challenges.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for extracting a numeric signal value from challenge results.
 * Enables tracking values over time to produce trends.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class SignalConfig {

    public enum Mode {
        /** AI includes a [SIGNAL: x] tag in the response. */
        EXPLICIT,
        /** Signal = count of items (LIST size or ENTITY_SET count). */
        COUNT,
        /** Map text tokens to numeric values (e.g., LOW=1, HIGH=3). */
        ORDINAL,
        /** No signal extraction. */
        NONE
    }

    private Mode mode = Mode.NONE;
    private Map<String, Double> ordinalMap = new LinkedHashMap<>();
    private String signalInstruction;

    public SignalConfig() {}

    public SignalConfig(Mode mode) {
        this.mode = mode;
    }

    public Mode mode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    /** For ORDINAL mode: maps text tokens to numeric values. Case-insensitive matching. */
    public Map<String, Double> ordinalMap() { return ordinalMap; }
    public void setOrdinalMap(Map<String, Double> ordinalMap) {
        this.ordinalMap = ordinalMap != null ? ordinalMap : new LinkedHashMap<>();
    }

    /**
     * For EXPLICIT mode: instruction appended to the prompt telling the AI
     * to include a signal value. Example: "End your response with [SIGNAL: X] where X is 0-10."
     */
    public String signalInstruction() { return signalInstruction; }
    public void setSignalInstruction(String signalInstruction) { this.signalInstruction = signalInstruction; }

    // ==================== Factory Methods ====================

    public static SignalConfig explicit(String instruction) {
        SignalConfig c = new SignalConfig(Mode.EXPLICIT);
        c.setSignalInstruction(instruction);
        return c;
    }

    public static SignalConfig count() {
        return new SignalConfig(Mode.COUNT);
    }

    public static SignalConfig ordinal(Map<String, Double> map) {
        SignalConfig c = new SignalConfig(Mode.ORDINAL);
        c.setOrdinalMap(map);
        return c;
    }

    public static SignalConfig none() {
        return new SignalConfig(Mode.NONE);
    }
}
