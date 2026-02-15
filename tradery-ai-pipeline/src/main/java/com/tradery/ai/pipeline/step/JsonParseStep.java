package com.tradery.ai.pipeline.step;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts a JSON array from the raw AI response and parses into DiscoveredEntity list.
 */
class JsonParseStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(JsonParseStep.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public StepResult execute(StepContext context) {
        String raw = context.lastRawResponse();
        if (raw == null || raw.isBlank()) {
            return StepResult.failContinue("No raw response to parse");
        }

        // Extract JSON array with brace matching
        String json = extractJsonArray(raw);
        if (json == null) {
            log.warn("JsonParseStep: no JSON array found in response");
            return StepResult.failContinue("No JSON array found in response");
        }

        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) {
                return StepResult.failContinue("Parsed JSON is not an array");
            }

            List<DiscoveredEntity> entities = new ArrayList<>();
            for (JsonNode node : root) {
                try {
                    DiscoveredEntity entity = parseEntity(node);
                    if (entity != null) {
                        entities.add(entity);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse entity node: {}", e.getMessage());
                }
            }

            context.setCurrentEntities(entities);
            context.notifyProgress(name(), "Parsed " + entities.size() + " entities", 0.4);
            return StepResult.success("Parsed " + entities.size() + " entities");
        } catch (Exception e) {
            log.warn("JsonParseStep failed: {}", e.getMessage());
            return StepResult.failContinue("JSON parse error: " + e.getMessage());
        }
    }

    private DiscoveredEntity parseEntity(JsonNode node) {
        String name = node.path("name").asText("");
        if (name.isEmpty()) return null;

        String symbol = node.has("symbol") && !node.path("symbol").isNull()
            ? node.path("symbol").asText() : null;
        String typeId = node.path("type").asText("").toLowerCase();
        String relTypeId = node.path("relationshipType").asText("").toLowerCase();
        String reason = node.path("reason").asText("");
        double confidence = node.path("confidence").asDouble(0.5);

        if (confidence < 0.5) return null;

        // Parse extra attributes
        Map<String, String> attrs = new HashMap<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String key = entry.getKey();
            if (!key.equals("name") && !key.equals("symbol") && !key.equals("type")
                && !key.equals("relationshipType") && !key.equals("reason")
                && !key.equals("confidence")) {
                attrs.put(key, entry.getValue().asText());
            }
        }

        return new DiscoveredEntity(name, symbol, typeId, relTypeId, reason, confidence,
            attrs.isEmpty() ? Map.of() : Map.copyOf(attrs));
    }

    /**
     * Extract the first JSON array from text, handling nested brackets.
     */
    static String extractJsonArray(String text) {
        int start = text.indexOf('[');
        if (start < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        // Fallback: simple extraction
        int end = text.lastIndexOf(']');
        if (end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    @Override
    public String name() { return "json-parse"; }
}
