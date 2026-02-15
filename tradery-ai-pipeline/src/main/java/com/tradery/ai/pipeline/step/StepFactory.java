package com.tradery.ai.pipeline.step;

import com.tradery.ai.pipeline.config.StepConfig;

/**
 * Creates PipelineStep instances from StepConfig.
 */
public class StepFactory {

    public PipelineStep create(StepConfig config) {
        return switch (config.getType()) {
            case "query" -> new QueryStep(config);
            case "json-parse" -> new JsonParseStep();
            case "json-salvage" -> new JsonSalvageStep(config);
            case "schema-validate" -> new SchemaValidateStep();
            case "quality-gate" -> new QualityGateStep(config);
            case "escalate" -> new EscalateStep(config);
            case "challenge" -> new ChallengeStep(config);
            case "dedup" -> new DedupStep();
            case "web-research" -> new WebResearchStep();
            default -> throw new IllegalArgumentException("Unknown step type: " + config.getType());
        };
    }
}
