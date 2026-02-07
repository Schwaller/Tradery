package com.tradery.news.ui.coin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Fuzzy matcher for finding existing entities that may match a discovered entity.
 * Used to prevent duplicates and allow linking to existing entities.
 */
public class EntityMatcher {

    private final EntityStore store;

    // Suffixes to strip when normalizing names
    private static final Set<String> STRIP_SUFFIXES = Set.of(
        "token", "coin", "protocol", "network", "chain", "finance",
        "dao", "labs", "foundation", "inc", "corp", "limited", "ltd"
    );

    // Compatible entity types (can match across these)
    private static final Set<Set<CoinEntity.Type>> COMPATIBLE_TYPES = Set.of(
        Set.of(CoinEntity.Type.COIN, CoinEntity.Type.L2)  // Both are crypto assets
    );

    public EntityMatcher(EntityStore store) {
        this.store = store;
    }

    /**
     * Find potential matches for a discovered entity.
     * Returns candidates sorted by score (highest first), limited to 5.
     */
    public List<MatchCandidate> findMatches(EntitySearchProcessor.DiscoveredEntity discovered) {
        List<MatchCandidate> candidates = new ArrayList<>();

        for (CoinEntity existing : store.loadAllEntities()) {
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

    /**
     * Score a single match between a discovered entity and an existing entity.
     * Returns null if no reasonable match found.
     */
    private MatchCandidate scoreMatch(EntitySearchProcessor.DiscoveredEntity discovered, CoinEntity existing) {
        double typeMultiplier = getTypeMultiplier(discovered.type(), existing.type());

        // 1. Exact ID match
        if (discovered.generateId().equals(existing.id())) {
            return new MatchCandidate(existing, 1.0 * typeMultiplier, MatchReason.EXACT_ID);
        }

        // 2. Symbol match (case-insensitive)
        if (discovered.symbol() != null && existing.symbol() != null &&
            discovered.symbol().equalsIgnoreCase(existing.symbol())) {
            return new MatchCandidate(existing, 0.95 * typeMultiplier, MatchReason.SYMBOL_MATCH);
        }

        // 3. Normalized name match
        String discoveredNorm = normalize(discovered.name());
        String existingNorm = normalize(existing.name());
        if (!discoveredNorm.isEmpty() && discoveredNorm.equals(existingNorm)) {
            return new MatchCandidate(existing, 0.90 * typeMultiplier, MatchReason.NORMALIZED_NAME);
        }

        // 4. Name without suffixes match
        String discoveredStripped = stripSuffixes(discovered.name());
        String existingStripped = stripSuffixes(existing.name());
        if (!discoveredStripped.isEmpty() && discoveredStripped.equals(existingStripped)) {
            return new MatchCandidate(existing, 0.85 * typeMultiplier, MatchReason.NORMALIZED_NAME);
        }

        // 5. Fuzzy match (Jaro-Winkler similarity)
        double similarity = jaroWinklerSimilarity(discoveredNorm, existingNorm);
        if (similarity >= 0.80) {
            // Score: 0.70-0.80 based on similarity
            double score = 0.70 + (similarity - 0.80) * 0.5;
            return new MatchCandidate(existing, score * typeMultiplier, MatchReason.FUZZY_NAME);
        }

        return null;
    }

    /**
     * Get multiplier based on type compatibility.
     * Same type = 1.0, compatible types = 0.9, incompatible = 0.5
     */
    private double getTypeMultiplier(CoinEntity.Type discovered, CoinEntity.Type existing) {
        if (discovered == existing) {
            return 1.0;
        }

        for (Set<CoinEntity.Type> compatible : COMPATIBLE_TYPES) {
            if (compatible.contains(discovered) && compatible.contains(existing)) {
                return 0.9;
            }
        }

        return 0.5;  // Incompatible types
    }

    /**
     * Normalize a name for comparison:
     * - Lowercase
     * - Remove non-alphanumeric characters
     * - Strip common suffixes
     */
    private String normalize(String name) {
        if (name == null) return "";
        return stripSuffixes(name)
            .toLowerCase()
            .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Strip common suffixes from a name (case-insensitive).
     */
    private String stripSuffixes(String name) {
        if (name == null) return "";
        String result = name.toLowerCase().trim();

        // Iteratively strip suffixes
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : STRIP_SUFFIXES) {
                if (result.endsWith(" " + suffix) || result.endsWith("-" + suffix)) {
                    result = result.substring(0, result.length() - suffix.length() - 1).trim();
                    changed = true;
                    break;
                }
                if (result.equals(suffix)) {
                    return "";  // Name is just a suffix
                }
            }
        }

        return result;
    }

    /**
     * Calculate Jaro-Winkler similarity between two strings.
     * Returns a value between 0.0 (no similarity) and 1.0 (identical).
     */
    private double jaroWinklerSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        double jaro = jaroSimilarity(s1, s2);

        // Winkler modification: boost for common prefix
        int prefixLen = 0;
        int maxPrefix = Math.min(4, Math.min(s1.length(), s2.length()));
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLen++;
            } else {
                break;
            }
        }

        return jaro + prefixLen * 0.1 * (1 - jaro);
    }

    /**
     * Calculate Jaro similarity between two strings.
     */
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

        // Find matches
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

        // Count transpositions
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matched[i]) continue;
            while (!s2Matched[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) {
                transpositions++;
            }
            k++;
        }

        return (matches / (double) len1 +
                matches / (double) len2 +
                (matches - transpositions / 2.0) / matches) / 3.0;
    }

    /**
     * A potential match candidate with score and reason.
     */
    public record MatchCandidate(
        CoinEntity existing,      // The existing entity
        double score,             // 0.0 - 1.0
        MatchReason reason        // Why it matched
    ) {}

    /**
     * Reason for match.
     */
    public enum MatchReason {
        EXACT_ID("Exact ID match"),
        SYMBOL_MATCH("Same symbol"),
        NORMALIZED_NAME("Same name"),
        FUZZY_NAME("Similar name");

        private final String description;

        MatchReason(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }
}
