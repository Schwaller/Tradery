package com.tradery.ai.challenges.execution;

import com.tradery.ai.challenges.model.SignalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a numeric signal value from challenge results.
 * Enables time-series tracking of AI assessments.
 */
public class SignalExtractor {

    private static final Logger log = LoggerFactory.getLogger(SignalExtractor.class);
    private static final Pattern SIGNAL_PATTERN = Pattern.compile("\\[SIGNAL:\\s*([\\d.+-]+)\\]");
    private static final Pattern CLASSIFICATION_PATTERN = Pattern.compile("\\[CLASSIFICATION:\\s*(.+?)\\]");

    private SignalExtractor() {}

    /**
     * Extract a numeric signal from a challenge result.
     *
     * @param config    Signal configuration
     * @param text      Text response (for TEXT output or raw response)
     * @param collection Collection result (for LIST items or discovered entities)
     * @return Extracted signal value, or null if extraction fails or is not configured
     */
    public static Double extract(SignalConfig config, String text, Collection<?> collection) {
        if (config == null || config.mode() == SignalConfig.Mode.NONE) return null;

        try {
            return switch (config.mode()) {
                case EXPLICIT -> extractExplicit(text);
                case COUNT -> extractCount(collection);
                case ORDINAL -> extractOrdinal(text, config.ordinalMap());
                case NONE -> null;
            };
        } catch (Exception e) {
            log.warn("Signal extraction failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse [SIGNAL: x] tag from text.
     */
    private static Double extractExplicit(String text) {
        if (text == null) return null;
        Matcher m = SIGNAL_PATTERN.matcher(text);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return null;
    }

    /**
     * Count items in a collection.
     */
    private static Double extractCount(Collection<?> collection) {
        if (collection == null) return null;
        return (double) collection.size();
    }

    /**
     * Find a classification token in text and map it to a numeric value.
     * First tries [CLASSIFICATION: VALUE] tag, then scans for any ordinal key.
     */
    private static Double extractOrdinal(String text, Map<String, Double> ordinalMap) {
        if (text == null || ordinalMap == null || ordinalMap.isEmpty()) return null;

        // Try explicit tag first
        Matcher m = CLASSIFICATION_PATTERN.matcher(text);
        if (m.find()) {
            String classification = m.group(1).trim();
            Double value = findOrdinalValue(classification, ordinalMap);
            if (value != null) return value;
        }

        // Fall back to scanning text for any ordinal key (case-insensitive, last match wins)
        String upperText = text.toUpperCase();
        Double lastMatch = null;
        int lastIndex = -1;
        for (Map.Entry<String, Double> entry : ordinalMap.entrySet()) {
            int idx = upperText.lastIndexOf(entry.getKey().toUpperCase());
            if (idx > lastIndex) {
                lastIndex = idx;
                lastMatch = entry.getValue();
            }
        }
        return lastMatch;
    }

    private static Double findOrdinalValue(String classification, Map<String, Double> ordinalMap) {
        // Exact match first
        Double val = ordinalMap.get(classification);
        if (val != null) return val;

        // Case-insensitive
        for (Map.Entry<String, Double> entry : ordinalMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(classification)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
