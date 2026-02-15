package com.tradery.ai.pipeline.step;

import com.tradery.ai.AiException;
import com.tradery.ai.AiProfile;
import com.tradery.ai.pipeline.config.StepConfig;
import com.tradery.ai.pipeline.prompt.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the discovery prompt to an AI model and stores the raw response.
 */
class QueryStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(QueryStep.class);

    private final StepConfig config;

    QueryStep(StepConfig config) {
        this.config = config;
    }

    @Override
    public StepResult execute(StepContext context) {
        String tierName = config.getTier();
        AiProfile profile = tierName != null
            ? context.tierResolver().resolve(tierName)
            : null;

        if (profile == null && tierName != null) {
            return StepResult.failAbort("No available profile for tier: " + tierName);
        }

        String prompt = PromptBuilder.buildQueryPrompt(
            context.request(), context.webResearchContext()
        );

        context.notifyProgress(name(), "Querying AI...", 0.1);
        log.debug("QueryStep: sending prompt ({} chars) to tier '{}'", prompt.length(), tierName);

        try {
            String response = profile != null
                ? context.aiClient().query(prompt, profile)
                : context.aiClient().query(prompt);

            context.setLastRawResponse(response);
            context.notifyProgress(name(), "Response received", 0.3);
            return StepResult.success("Got " + response.length() + " chars");
        } catch (AiException e) {
            log.error("QueryStep failed: {}", e.getMessage());
            return StepResult.failAbort("AI query failed: " + e.getMessage());
        }
    }

    @Override
    public String name() { return "query"; }
}
