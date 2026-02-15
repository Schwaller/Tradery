package com.tradery.ai.pipeline.step;

import com.tradery.ai.pipeline.config.StepConfig;

/**
 * Checks whether the current entity count meets a minimum threshold.
 * Returns FAIL_CONTINUE if insufficient — this signals downstream EscalateStep to act.
 */
class QualityGateStep implements PipelineStep {

    private final int minResults;

    QualityGateStep(StepConfig config) {
        this.minResults = config.getIntParam("minResults", 5);
    }

    @Override
    public StepResult execute(StepContext context) {
        int count = context.currentEntities().size();
        if (count >= minResults) {
            context.setQualityGateFailed(false);
            return StepResult.success(count + " entities (>= " + minResults + " threshold)");
        }

        context.setQualityGateFailed(true);
        String msg = count + " entities (< " + minResults + " threshold)";
        context.notifyProgress(name(), msg, 0.55);
        return StepResult.failContinue(msg);
    }

    @Override
    public String name() { return "quality-gate"; }
}
