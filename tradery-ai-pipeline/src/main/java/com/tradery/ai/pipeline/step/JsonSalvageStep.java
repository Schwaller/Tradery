package com.tradery.ai.pipeline.step;

import com.tradery.ai.AiException;
import com.tradery.ai.AiProfile;
import com.tradery.ai.pipeline.config.StepConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attempts to repair a malformed AI response by sending it to an AI with a salvage prompt.
 * Only runs if currentEntities is empty and lastRawResponse exists.
 */
class JsonSalvageStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(JsonSalvageStep.class);

    private final StepConfig config;

    JsonSalvageStep(StepConfig config) {
        this.config = config;
    }

    @Override
    public StepResult execute(StepContext context) {
        // Only salvage if parsing failed (no entities) but we have a raw response
        if (!context.currentEntities().isEmpty()) {
            return StepResult.skip("Entities already parsed, no salvage needed");
        }
        if (context.lastRawResponse() == null || context.lastRawResponse().isBlank()) {
            return StepResult.skip("No raw response to salvage");
        }

        String tierName = config.getTier();
        AiProfile profile = tierName != null
            ? context.tierResolver().resolve(tierName)
            : null;

        String prompt = buildSalvagePrompt(context.lastRawResponse());
        context.notifyProgress(name(), "Salvaging malformed response...", 0.35);
        log.info("JsonSalvageStep: attempting to repair response");

        try {
            String response = profile != null
                ? context.aiClient().query(prompt, profile)
                : context.aiClient().query(prompt);

            context.setLastRawResponse(response);
            context.setSalvaged(true);

            // Re-run JSON parse inline
            String json = JsonParseStep.extractJsonArray(response);
            if (json != null) {
                // Delegate to a temporary parse step
                JsonParseStep parser = new JsonParseStep();
                StepResult parseResult = parser.execute(context);
                if (parseResult.status() == StepResult.Status.SUCCESS) {
                    return StepResult.success("Salvaged " + context.currentEntities().size() + " entities");
                }
            }

            return StepResult.failContinue("Salvage produced no parseable JSON");
        } catch (AiException e) {
            log.warn("JsonSalvageStep failed: {}", e.getMessage());
            return StepResult.failContinue("Salvage AI call failed: " + e.getMessage());
        }
    }

    private String buildSalvagePrompt(String brokenResponse) {
        return """
            The following AI response was supposed to be a JSON array of discovered entities, \
            but it is malformed. Please extract and repair it into valid JSON.

            Expected format: A JSON array where each element has these fields:
            - name (string): Entity name
            - symbol (string or null): Ticker symbol
            - type (string): Entity type ID
            - relationshipType (string): Relationship type ID
            - reason (string): Why this entity is related
            - confidence (number 0.0-1.0): Confidence score

            Return ONLY the repaired JSON array, no other text.

            === BROKEN RESPONSE ===
            """ + truncate(brokenResponse, 3000);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Override
    public String name() { return "json-salvage"; }
}
