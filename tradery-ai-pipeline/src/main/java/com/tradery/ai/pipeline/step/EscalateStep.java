package com.tradery.ai.pipeline.step;

import com.tradery.ai.AiException;
import com.tradery.ai.AiProfile;
import com.tradery.ai.pipeline.config.StepConfig;
import com.tradery.ai.pipeline.prompt.PromptBuilder;
import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Only runs if the quality gate failed. Sends an escalation prompt to a higher tier,
 * asking for additional entities beyond what was already found.
 */
class EscalateStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(EscalateStep.class);

    private final StepConfig config;

    EscalateStep(StepConfig config) {
        this.config = config;
    }

    @Override
    public StepResult execute(StepContext context) {
        if (!context.isQualityGateFailed()) {
            return StepResult.skip("Quality gate passed, no escalation needed");
        }

        String tierName = config.getTier();
        AiProfile profile = tierName != null
            ? context.tierResolver().resolve(tierName)
            : null;

        if (profile == null && tierName != null) {
            return StepResult.failContinue("No available profile for escalation tier: " + tierName);
        }

        // Save current entities as previous attempt
        context.setPreviousAttemptEntities(new ArrayList<>(context.currentEntities()));
        context.incrementAttempt();

        String prompt = PromptBuilder.buildEscalationPrompt(
            context.request(), context.currentEntities(), context.webResearchContext()
        );

        context.notifyProgress(name(), "Escalating to " + tierName + "...", 0.6);
        log.info("EscalateStep: sending escalation prompt to tier '{}'", tierName);

        try {
            String response = profile != null
                ? context.aiClient().query(prompt, profile)
                : context.aiClient().query(prompt);

            context.setLastRawResponse(response);

            // Parse escalation results
            JsonParseStep parser = new JsonParseStep();
            List<DiscoveredEntity> previous = new ArrayList<>(context.currentEntities());
            StepResult parseResult = parser.execute(context);

            if (parseResult.status() == StepResult.Status.SUCCESS) {
                // Merge: previous + new (dedup by name)
                List<DiscoveredEntity> merged = mergeEntities(previous, context.currentEntities());
                context.setCurrentEntities(merged);
                context.setEscalated(true);
                int added = merged.size() - previous.size();
                return StepResult.success("Escalation added " + added + " entities (total " + merged.size() + ")");
            }

            // Parsing failed, keep previous
            context.setCurrentEntities(previous);
            return StepResult.failContinue("Escalation response could not be parsed");
        } catch (AiException e) {
            log.warn("EscalateStep failed: {}", e.getMessage());
            return StepResult.failContinue("Escalation AI call failed: " + e.getMessage());
        }
    }

    private List<DiscoveredEntity> mergeEntities(List<DiscoveredEntity> existing,
                                                   List<DiscoveredEntity> newEntities) {
        Set<String> seen = new HashSet<>();
        List<DiscoveredEntity> merged = new ArrayList<>();

        for (DiscoveredEntity e : existing) {
            String key = e.name().toLowerCase().trim();
            if (seen.add(key)) merged.add(e);
        }
        for (DiscoveredEntity e : newEntities) {
            String key = e.name().toLowerCase().trim();
            if (seen.add(key)) merged.add(e);
        }

        return merged;
    }

    @Override
    public String name() { return "escalate"; }
}
