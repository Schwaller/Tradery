package com.tradery.ai.challenges.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.ai.AiClient;
import com.tradery.ai.AiProfile;
import com.tradery.ai.DuckDuckGoSearchProvider;
import com.tradery.ai.GoogleNewsSearchProvider;
import com.tradery.ai.WebSearchProvider;
import com.tradery.ai.challenges.model.*;
import com.tradery.ai.challenges.subject.ChallengeSubject;
import com.tradery.ai.pipeline.config.TierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Executes TEXT and LIST challenges via direct AiClient queries.
 * Supports optional verification (a second AI call to cross-check the answer).
 */
public class TextQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(TextQueryExecutor.class);

    private final AiClient aiClient;
    private final TierResolver tierResolver;
    private final WebSearchProvider newsSearchProvider;
    private final WebSearchProvider webSearchProvider;

    public TextQueryExecutor(AiClient aiClient, TierResolver tierResolver) {
        this.aiClient = aiClient;
        this.tierResolver = tierResolver;
        this.newsSearchProvider = new GoogleNewsSearchProvider();
        this.webSearchProvider = new DuckDuckGoSearchProvider();
    }

    public ChallengeResult execute(Challenge challenge, ChallengeSubject subject,
                                    ChallengeEscalation escalation, Consumer<String> logger) {
        return execute(challenge, subject, escalation, logger, null);
    }

    public ChallengeResult execute(Challenge challenge, ChallengeSubject subject,
                                    ChallengeEscalation escalation, Consumer<String> logger,
                                    ChallengeResult previousResult) {
        long startTime = System.currentTimeMillis();
        String challengeId = challenge.id();
        String subjectId = subject.id();
        int escIndex = challenge.escalations().indexOf(escalation);

        try {
            AiProfile profile = tierResolver.resolve(escalation.tier());
            if (profile == null) {
                return ChallengeResult.error(challengeId, subjectId, escIndex,
                    challenge.output().type(), "No AI profile available for tier: " + escalation.tier(),
                    elapsed(startTime));
            }

            if (logger != null) {
                logger.accept("[" + escalation.label() + "] Running " + challenge.title()
                    + " on " + subject.name());
            }

            // 1. Web search for current context (Google News primary, DDG fallback)
            String searchContext = "";
            try {
                if (logger != null) logger.accept("Searching the web...");
                String searchQuery = challenge.description().length() > 80
                    ? challenge.title() + " latest news today"
                    : challenge.description() + " latest";

                List<WebSearchProvider.SearchResult> allResults = new ArrayList<>();

                // Google News RSS — returns real article descriptions
                try {
                    List<WebSearchProvider.SearchResult> newsResults = newsSearchProvider.search(searchQuery, 6);
                    allResults.addAll(newsResults);
                    if (logger != null) logger.accept("Google News: " + newsResults.size() + " results");
                } catch (Exception e) {
                    log.warn("Google News search failed: {}", e.getMessage());
                }

                // DDG web search — broader coverage, may add different sources
                try {
                    List<WebSearchProvider.SearchResult> webResults = webSearchProvider.search(searchQuery, 4);
                    // Deduplicate by URL
                    Set<String> existingUrls = new LinkedHashSet<>();
                    for (WebSearchProvider.SearchResult r : allResults) existingUrls.add(r.url());
                    for (WebSearchProvider.SearchResult r : webResults) {
                        if (!existingUrls.contains(r.url())) {
                            allResults.add(r);
                        }
                    }
                    if (logger != null) logger.accept("DuckDuckGo: " + webResults.size() + " results");
                } catch (Exception e) {
                    log.warn("DDG search failed: {}", e.getMessage());
                }

                if (!allResults.isEmpty()) {
                    StringBuilder ctx = new StringBuilder();
                    ctx.append("\nRecent news and web search results (use these for current information):\n\n");
                    for (int i = 0; i < allResults.size(); i++) {
                        WebSearchProvider.SearchResult sr = allResults.get(i);
                        ctx.append(i + 1).append(". ").append(sr.title()).append("\n");
                        if (sr.snippet() != null && !sr.snippet().isBlank()) {
                            ctx.append("   ").append(sr.snippet()).append("\n");
                        }
                        ctx.append("\n");
                    }
                    searchContext = ctx.toString();
                    if (logger != null) logger.accept("Total: " + allResults.size() + " unique results");
                }
            } catch (Exception e) {
                log.warn("Web search failed for challenge '{}': {}", challengeId, e.getMessage());
                if (logger != null) logger.accept("Web search failed: " + e.getMessage());
            }

            // 2. Build and execute main query with search context
            List<Map<String, String>> previousItems = (previousResult != null && previousResult.itemResults() != null)
                ? previousResult.itemResults() : null;
            String prompt = PromptAssembler.build(challenge, subject, searchContext, previousItems);
            String response = aiClient.query(prompt, profile);

            if (logger != null) {
                logger.accept("Response received (" + response.length() + " chars)");
            }

            // 2. Optional verification
            boolean verified = false;
            if (escalation.verify()) {
                if (logger != null) logger.accept("Verifying response...");
                String verifyPrompt = PromptAssembler.buildVerification(challenge, subject, response);
                String verifiedResponse = aiClient.query(verifyPrompt, profile);
                response = verifiedResponse;
                verified = true;
            }

            // 3. Parse output
            long duration = elapsed(startTime);
            if (challenge.output().type() == ChallengeOutput.Type.STRUCTURED) {
                // Check for AI-returned error
                String aiError = parseAiError(response);
                if (aiError != null) {
                    return ChallengeResult.error(challengeId, subjectId, escIndex,
                        challenge.output().type(), aiError, duration);
                }

                if (challenge.output().listMode()) {
                    // Parse as array of objects
                    List<Map<String, String>> items = parseStructuredList(response, challenge.output().fields());
                    ChallengeResult result = ChallengeResult.structuredList(challengeId, subjectId, escIndex,
                        items, null, duration);
                    if (items.isEmpty()) {
                        result.setTextResult(response);
                    }
                    result.setResolvedTier(escalation.tier());
                    result.setVerified(verified);
                    return result;
                }

                Map<String, String> fields = parseStructuredResponse(response, challenge.output().fields());
                // Extract signal from the first SCORE field
                Double signal = null;
                for (ChallengeOutput.Field f : challenge.output().fields()) {
                    if (f.type() == ChallengeOutput.Field.FieldType.SCORE && fields.containsKey(f.name())) {
                        try { signal = Double.parseDouble(fields.get(f.name())); } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
                if (signal == null) {
                    signal = SignalExtractor.extract(challenge.signalConfig(), response, null);
                }
                ChallengeResult result = ChallengeResult.structured(challengeId, subjectId, escIndex,
                    fields, signal, duration);
                // If structured parsing failed, store raw response as text so it's still visible
                if (fields.isEmpty()) {
                    result.setTextResult(response);
                }
                result.setResolvedTier(escalation.tier());
                result.setVerified(verified);
                return result;
            } else if (challenge.output().type() == ChallengeOutput.Type.LIST) {
                List<String> items = parseList(response);
                Double signal = SignalExtractor.extract(challenge.signalConfig(), response, items);
                ChallengeResult result = ChallengeResult.list(challengeId, subjectId, escIndex,
                    items, signal, duration);
                result.setResolvedTier(escalation.tier());
                result.setVerified(verified);
                return result;
            } else {
                Double signal = SignalExtractor.extract(challenge.signalConfig(), response, null);
                ChallengeResult result = ChallengeResult.text(challengeId, subjectId, escIndex,
                    response, signal, duration);
                result.setResolvedTier(escalation.tier());
                result.setVerified(verified);
                return result;
            }

        } catch (Exception e) {
            log.error("Challenge execution failed: {}/{}: {}", challengeId, subjectId, e.getMessage());
            if (logger != null) logger.accept("Error: " + e.getMessage());
            return ChallengeResult.error(challengeId, subjectId, escIndex,
                challenge.output().type(), e.getMessage(), elapsed(startTime));
        }
    }

    /**
     * Parse a list from AI response. Tries JSON array first, falls back to line-based parsing.
     */
    static List<String> parseList(String response) {
        if (response == null || response.isBlank()) return List.of();

        String trimmed = response.strip();

        // Try JSON array
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String jsonPart = trimmed.substring(start, end + 1);
            try {
                List<String> items = new ArrayList<>();
                // Simple JSON array parsing without full Jackson dependency
                String inner = jsonPart.substring(1, jsonPart.length() - 1).strip();
                if (!inner.isEmpty()) {
                    // Split on comma, respecting quoted strings
                    boolean inQuote = false;
                    StringBuilder current = new StringBuilder();
                    for (int i = 0; i < inner.length(); i++) {
                        char c = inner.charAt(i);
                        if (c == '"' && (i == 0 || inner.charAt(i - 1) != '\\')) {
                            inQuote = !inQuote;
                        } else if (c == ',' && !inQuote) {
                            String item = current.toString().strip();
                            if (!item.isEmpty()) items.add(unquote(item));
                            current.setLength(0);
                        } else {
                            current.append(c);
                        }
                    }
                    String last = current.toString().strip();
                    if (!last.isEmpty()) items.add(unquote(last));
                }
                if (!items.isEmpty()) return items;
            } catch (Exception ignored) {
                // Fall through to line-based parsing
            }
        }

        // Fall back: split by newlines, strip bullets/numbers
        List<String> items = new ArrayList<>();
        for (String line : trimmed.split("\n")) {
            String cleaned = line.strip()
                .replaceFirst("^[-*•]\\s*", "")     // Bullet points
                .replaceFirst("^\\d+[.)\\s]+", ""); // Numbered lists
            if (!cleaned.isEmpty()) {
                items.add(cleaned);
            }
        }
        return items;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Check if the AI returned an error object instead of the expected fields.
     * Returns the error message if found, null otherwise.
     */
    private static String parseAiError(String response) {
        if (response == null || response.isBlank()) return null;
        String trimmed = response.strip();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl > 0) trimmed = trimmed.substring(nl + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3).strip();
        }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = findMatchingBrace(trimmed, braceStart);
        if (braceStart >= 0 && braceEnd > braceStart) {
            String json = trimmed.substring(braceStart, braceEnd + 1);
            try {
                Map<String, Object> parsed = JSON.readValue(json, new TypeReference<>() {});
                // Only treat as error if it has "error" key and no other expected fields
                if (parsed.containsKey("error") && parsed.size() <= 2) {
                    return String.valueOf(parsed.get("error"));
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Parse a structured JSON response into a field map.
     * Expects the AI to return JSON like: {"headline":"...", "explanation":"...", "sentiment": -0.5}
     */
    static Map<String, String> parseStructuredResponse(String response, List<ChallengeOutput.Field> expectedFields) {
        Map<String, String> result = new LinkedHashMap<>();
        if (response == null || response.isBlank()) return result;

        String trimmed = response.strip();

        // Strip markdown code fences if present
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3).strip();
        }

        // Find JSON object in response
        int braceStart = trimmed.indexOf('{');
        int braceEnd = findMatchingBrace(trimmed, braceStart);

        if (braceStart >= 0 && braceEnd > braceStart) {
            String json = trimmed.substring(braceStart, braceEnd + 1);
            try {
                Map<String, Object> parsed = JSON.readValue(json, new TypeReference<>() {});
                for (ChallengeOutput.Field field : expectedFields) {
                    Object val = parsed.get(field.name());
                    if (val != null) {
                        result.put(field.name(), String.valueOf(val));
                    }
                    // Preserve _reason justification for numeric fields
                    Object reason = parsed.get(field.name() + "_reason");
                    if (reason != null) {
                        result.put(field.name() + "_reason", String.valueOf(reason));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse structured JSON response: {}", e.getMessage());
            }
        }

        // Fallback: try line-based "Key: Value" parsing
        if (result.isEmpty()) {
            for (ChallengeOutput.Field field : expectedFields) {
                for (String line : trimmed.split("\n")) {
                    String lower = line.strip().toLowerCase();
                    String fieldLabel = field.label() != null ? field.label().toLowerCase() : field.name().toLowerCase();
                    if (lower.startsWith(fieldLabel + ":") || lower.startsWith(field.name().toLowerCase() + ":")) {
                        int colonIdx = line.indexOf(':');
                        if (colonIdx > 0) {
                            result.put(field.name(), line.substring(colonIdx + 1).strip());
                        }
                        break;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Parse a JSON array of structured objects.
     * Expects: [{"name":"...", "intensity": 8, ...}, ...]
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, String>> parseStructuredList(String response, List<ChallengeOutput.Field> expectedFields) {
        List<Map<String, String>> results = new ArrayList<>();
        if (response == null || response.isBlank()) return results;

        String trimmed = response.strip();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl > 0) trimmed = trimmed.substring(nl + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3).strip();
        }

        // Find JSON array
        int start = trimmed.indexOf('[');
        int end = findMatchingBracket(trimmed, start);
        if (start < 0 || end <= start) return results;

        String json = trimmed.substring(start, end + 1);
        try {
            List<Object> parsed = JSON.readValue(json, new TypeReference<>() {});
            for (Object item : parsed) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (ChallengeOutput.Field field : expectedFields) {
                        Object val = raw.get(field.name());
                        if (val != null) {
                            row.put(field.name(), String.valueOf(val));
                        }
                        // Preserve _reason justification for numeric fields
                        Object reason = raw.get(field.name() + "_reason");
                        if (reason != null) {
                            row.put(field.name() + "_reason", String.valueOf(reason));
                        }
                    }
                    // Preserve _status for removed items
                    Object status = raw.get("_status");
                    if (status != null) {
                        row.put("_status", String.valueOf(status));
                    }
                    if (!row.isEmpty()) results.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse structured list response: {}", e.getMessage());
        }
        return results;
    }

    /** Find matching ] for a [, respecting nesting and strings. */
    private static int findMatchingBracket(String text, int openBracket) {
        if (openBracket < 0) return -1;
        int depth = 0;
        boolean inString = false;
        for (int i = openBracket; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '[') depth++;
                else if (c == ']') { depth--; if (depth == 0) return i; }
            }
        }
        return -1;
    }

    /**
     * Find the matching closing brace, respecting nested braces and strings.
     */
    private static int findMatchingBrace(String text, int openBrace) {
        if (openBrace < 0) return -1;
        int depth = 0;
        boolean inString = false;
        for (int i = openBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; } // skip escaped char
                if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) return i; }
            }
        }
        return -1;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
