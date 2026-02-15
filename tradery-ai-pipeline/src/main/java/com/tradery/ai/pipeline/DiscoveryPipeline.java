package com.tradery.ai.pipeline;

import com.tradery.ai.AiClient;
import com.tradery.ai.pipeline.config.PipelineConfig;
import com.tradery.ai.pipeline.config.PipelineStore;
import com.tradery.ai.pipeline.config.StepConfig;
import com.tradery.ai.pipeline.config.TierResolver;
import com.tradery.ai.pipeline.step.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the AI entity discovery pipeline.
 * Looks like a single call from outside; internally runs a configurable multi-step pipeline.
 */
public class DiscoveryPipeline {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryPipeline.class);

    private final PipelineConfig config;
    private final String pipelineName;

    private DiscoveryPipeline(PipelineConfig config, String pipelineName) {
        this.config = config;
        this.pipelineName = pipelineName;
    }

    /**
     * Create a pipeline from a named configuration in ai-pipeline.yaml.
     */
    public static DiscoveryPipeline create(String pipelineName) {
        PipelineStore store = PipelineStore.get();
        PipelineConfig config = store.getPipeline(pipelineName);
        if (config == null) {
            throw new IllegalArgumentException("Unknown pipeline: " + pipelineName
                + ". Available: " + store.getPipelines().keySet());
        }
        return new DiscoveryPipeline(config, pipelineName);
    }

    /**
     * Create a pipeline from an explicit config.
     */
    public static DiscoveryPipeline create(PipelineConfig config) {
        return new DiscoveryPipeline(config, "custom");
    }

    /**
     * Create the default pipeline.
     */
    public static DiscoveryPipeline createDefault() {
        PipelineStore store = PipelineStore.get();
        return new DiscoveryPipeline(store.getDefaultPipeline(), store.getDefaultPipelineName());
    }

    /**
     * Execute the pipeline with no listener.
     */
    public DiscoveryResult execute(DiscoveryRequest request) {
        return execute(request, null);
    }

    /**
     * Execute the pipeline with progress notifications.
     */
    public DiscoveryResult execute(DiscoveryRequest request, DiscoveryListener listener) {
        long startTime = System.currentTimeMillis();
        log.info("Starting '{}' pipeline for entity '{}'", pipelineName, request.entity().name());

        TierResolver tierResolver = PipelineStore.get().createTierResolver();
        AiClient aiClient = AiClient.getInstance();
        StepContext context = new StepContext(request, tierResolver, aiClient, listener);
        StepFactory factory = new StepFactory();

        List<PipelineStep> steps = new ArrayList<>();
        for (StepConfig stepConfig : config.getSteps()) {
            steps.add(factory.create(stepConfig));
        }

        String lastError = null;

        for (int i = 0; i < steps.size(); i++) {
            PipelineStep step = steps.get(i);
            long stepStart = System.currentTimeMillis();

            log.debug("Running step {}/{}: {}", i + 1, steps.size(), step.name());

            try {
                StepResult result = step.execute(context);
                long stepDuration = System.currentTimeMillis() - stepStart;
                context.addStepMetadata(new StepMetadata(
                    step.name(), result.status(), result.message(), stepDuration));

                log.debug("Step '{}' completed: {} - {}", step.name(), result.status(), result.message());

                if (result.status() == StepResult.Status.FAIL_ABORT) {
                    lastError = result.message();
                    break;
                }
            } catch (Exception e) {
                long stepDuration = System.currentTimeMillis() - stepStart;
                context.addStepMetadata(new StepMetadata(
                    step.name(), StepResult.Status.FAIL_ABORT, e.getMessage(), stepDuration));
                log.error("Step '{}' threw exception: {}", step.name(), e.getMessage(), e);
                lastError = step.name() + " failed: " + e.getMessage();
                break;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        PipelineMetadata metadata = new PipelineMetadata(
            totalDuration,
            context.stepMetadata(),
            context.isEscalated(),
            context.isSalvaged(),
            context.challengeFilteredCount()
        );

        if (lastError != null) {
            log.warn("Pipeline '{}' failed after {}ms: {}", pipelineName, totalDuration, lastError);
            // Return what we have even on failure
            if (!context.currentEntities().isEmpty()) {
                return DiscoveryResult.success(context.currentEntities(),
                    context.schemaSuggestions(), metadata);
            }
            return DiscoveryResult.failure(lastError, metadata);
        }

        log.info("Pipeline '{}' completed in {}ms: {} entities, {} type suggestions",
            pipelineName, totalDuration, context.currentEntities().size(),
            context.schemaSuggestions().size());
        return DiscoveryResult.success(context.currentEntities(),
            context.schemaSuggestions(), metadata);
    }
}
