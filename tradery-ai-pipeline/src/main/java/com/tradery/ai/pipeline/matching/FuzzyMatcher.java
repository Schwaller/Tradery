package com.tradery.ai.pipeline.matching;

import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import com.tradery.ai.pipeline.schema.ExistingEntity;

import java.util.*;

/**
 * Generalized fuzzy matcher for detecting duplicates between discovered and existing entities.
 * Uses Jaro-Winkler similarity, normalized name matching, and suffix stripping.
 */
public class FuzzyMatcher {

    private static final Set<String> DEFAULT_STRIP_SUFFIXES = Set.of(
        "token", "coin", "protocol", "network", "chain", "finance",
        "dao", "labs", "foundation", "inc", "corp", "limited", "ltd"
    );

    private final List<ExistingEntity> existingEntities;
    private Set<String> stripSuffixes = DEFAULT_STRIP_SUFFIXES;
    private final Map<String, Map<String, Double>> compatibleTypes = new HashMap<>();

    public FuzzyMatcher(List<ExistingEntity> existingEntities) {
        this.existingEntities = existingEntities;
    }

    /**
     * Configure suffixes to strip during name normalization.
     */
    public FuzzyMatcher withStripSuffixes(Set<String> suffixes) {
        this.stripSuffixes = suffixes;
        return this;
    }

    /**
     * Configure compatible type pairs with a score multiplier.
     * E.g., withCompatibleTypes("coin", "l2", 0.9) means coin and l2 are compatible.
     */
    public FuzzyMatcher withCompatibleTypes(String typeA, String typeB, double multiplier) {
        compatibleTypes.computeIfAbsent(typeA, k -> new HashMap<>()).put(typeB, multiplier);
        compatibleTypes.computeIfAbsent(typeB, k -> new HashMap<>()).put(typeA, multiplier);
        return this;
    }

    /**
     * Find potential matches for a discovered entity.
     * Returns candidates sorted by score (highest first), limited to 5.
     */
    public List<MatchCandidate> findMatches(DiscoveredEntity discovered) {
        List<MatchCandidate> candidates = new ArrayList<>();

        for (ExistingEntity existing : existingEntities) {
            MatchCandidate match = scoreMatch(discovered, existing);
            if (match != null && match.score() >= 0.70) {
                candidates.add(match);
            }
        }

        return candidates.stream()
            .sorted(Comparator.comparingDouble(MatchCandidate::score).reversed())
            .limit(5)
            .toList();
    }

    private MatchCandidate scoreMatch(DiscoveredEntity discovered, ExistingEntity existing) {
        double typeMultiplier = getTypeMultiplier(discovered.typeId(), existing.typeId());

        // 1. Exact ID match
        if (discovered.generateId().equals(existing.id())) {
            return new MatchCandidate(existing.id(), existing.name(), existing.typeId(),
                1.0 * typeMultiplier, MatchReason.EXACT_ID);
        }

        // 2. Symbol match
        if (discovered.symbol() != null && existing.symbol() != null &&
            discovered.symbol().equalsIgnoreCase(existing.symbol())) {
            return new MatchCandidate(existing.id(), existing.name(), existing.typeId(),
                0.95 * typeMultiplier, MatchReason.SYMBOL_MATCH);
        }

        // 3. Normalized name match
        String discoveredNorm = normalize(discovered.name());
        String existingNorm = normalize(existing.name());
        if (!discoveredNorm.isEmpty() && discoveredNorm.equals(existingNorm)) {
            return new MatchCandidate(existing.id(), existing.name(), existing.typeId(),
                0.90 * typeMultiplier, MatchReason.NORMALIZED_NAME);
        }

        // 4. Name without suffixes match
        String discoveredStripped = stripSuffixes(discovered.name());
        String existingStripped = stripSuffixes(existing.name());
        if (!discoveredStripped.isEmpty() && discoveredStripped.equals(existingStripped)) {
            return new MatchCandidate(existing.id(), existing.name(), existing.typeId(),
                0.85 * typeMultiplier, MatchReason.NORMALIZED_NAME);
        }

        // 5. Fuzzy match (Jaro-Winkler)
        double similarity = jaroWinklerSimilarity(discoveredNorm, existingNorm);
        if (similarity >= 0.80) {
            double score = 0.70 + (similarity - 0.80) * 0.5;
            return new MatchCandidate(existing.id(), existing.name(), existing.typeId(),
                score * typeMultiplier, MatchReason.FUZZY_NAME);
        }

        return null;
    }

    private double getTypeMultiplier(String discoveredType, String existingType) {
        if (discoveredType == null || existingType == null) return 0.8;
        if (discoveredType.equalsIgnoreCase(existingType)) return 1.0;

        Map<String, Double> compat = compatibleTypes.get(discoveredType.toLowerCase());
        if (compat != null) {
            Double mult = compat.get(existingType.toLowerCase());
            if (mult != null) return mult;
        }

        return 0.5;
    }

    private String normalize(String name) {
        if (name == null) return "";
        return stripSuffixes(name)
            .toLowerCase()
            .replaceAll("[^a-z0-9]", "");
    }

    private String stripSuffixes(String name) {
        if (name == null) return "";
        String result = name.toLowerCase().trim();

        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : stripSuffixes) {
                if (result.endsWith(" " + suffix) || result.endsWith("-" + suffix)) {
                    result = result.substring(0, result.length() - suffix.length() - 1).trim();
                    changed = true;
                    break;
                }
                if (result.equals(suffix)) {
                    return "";
                }
            }
        }

        return result;
    }

    // ==================== Jaro-Winkler ====================

    private double jaroWinklerSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        double jaro = jaroSimilarity(s1, s2);

        int prefixLen = 0;
        int maxPrefix = Math.min(4, Math.min(s1.length(), s2.length()));
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefixLen++;
            else break;
        }

        return jaro + prefixLen * 0.1 * (1 - jaro);
    }

    private double jaroSimilarity(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0 && len2 == 0) return 1.0;
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchWindow = Math.max(len1, len2) / 2 - 1;
        if (matchWindow < 0) matchWindow = 0;

        boolean[] s1Matched = new boolean[len1];
        boolean[] s2Matched = new boolean[len2];

        int matches = 0;
        int transpositions = 0;

        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matched[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matched[i] = true;
                s2Matched[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matched[i]) continue;
            while (!s2Matched[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        return (matches / (double) len1 +
                matches / (double) len2 +
                (matches - transpositions / 2.0) / matches) / 3.0;
    }
}
