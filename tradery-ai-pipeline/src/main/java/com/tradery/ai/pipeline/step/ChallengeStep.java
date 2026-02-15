package com.tradery.ai.pipeline.step;

import com.tradery.ai.AiException;
import com.tradery.ai.AiProfile;
import com.tradery.ai.pipeline.config.StepConfig;
import com.tradery.ai.pipeline.prompt.PromptBuilder;
import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sends discovered entities to a verifier AI to filter out hallucinated or incorrect ones.
 */
class ChallengeStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(ChallengeStep.class);

    private final StepConfig config;

    ChallengeStep(StepConfig config) {
        this.config = config;
    }

    @Override
    public StepResult execute(StepContext context) {
        List<DiscoveredEntity> entities = context.currentEntities();
        if (entities.isEmpty()) {
            return StepResult.skip("No entities to challenge");
        }

        String tierName = config.getTier();
        AiProfile profile = tierName != null
            ? context.tierResolver().resolve(tierName)
            : null;

        String prompt = PromptBuilder.buildChallengePrompt(context.request(), entities);
        context.notifyProgress(name(), "Verifying " + entities.size() + " entities...", 0.7);

        try {
            String response = profile != null
                ? context.aiClient().query(prompt, profile)
                : context.aiClient().query(prompt);

            // Parse the verified list
            String json = JsonParseStep.extractJsonArray(response);
            if (json == null) {
                log.warn("ChallengeStep: verifier returned no JSON, keeping all entities");
                return StepResult.failContinue("Verifier response had no JSON array");
            }

            // Parse verified entity names from response
            Set<String> verifiedNames = new HashSet<>();
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = mapper.readTree(json);
                if (root.isArray()) {
                    for (var node : root) {
                        String name = node.path("name").asText("");
                        if (!name.isEmpty()) {
                            verifiedNames.add(name.toLowerCase().trim());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("ChallengeStep: failed to parse verifier response: {}", e.getMessage());
                return StepResult.failContinue("Failed to parse verifier response");
            }

            if (verifiedNames.isEmpty()) {
                log.warn("ChallengeStep: verifier returned empty list, keeping all entities");
                return StepResult.failContinue("Verifier returned no entities");
            }

            // Filter to only verified entities
            List<DiscoveredEntity> verified = entities.stream()
                .filter(e -> verifiedNames.contains(e.name().toLowerCase().trim()))
                .toList();

            int filtered = entities.size() - verified.size();
            context.setChallengeFilteredCount(filtered);
            context.setCurrentEntities(verified);

            String msg = verified.size() + " verified, " + filtered + " filtered";
            context.notifyProgress(name(), msg, 0.8);
            return StepResult.success(msg);
        } catch (AiException e) {
            log.warn("ChallengeStep failed: {}, keeping all entities", e.getMessage());
            return StepResult.failContinue("Challenge AI call failed: " + e.getMessage());
        }
    }

    @Override
    public String name() { return "challenge"; }
}
