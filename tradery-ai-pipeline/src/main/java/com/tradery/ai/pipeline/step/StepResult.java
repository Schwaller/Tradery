package com.tradery.ai.pipeline.step;

/**
 * Outcome of a pipeline step execution.
 */
public record StepResult(Status status, String message) {

    public enum Status {
        /** Step completed successfully. */
        SUCCESS,
        /** Step was skipped (preconditions not met). */
        SKIP,
        /** Step failed but pipeline should continue. */
        FAIL_CONTINUE,
        /** Step failed and pipeline should abort. */
        FAIL_ABORT
    }

    public static StepResult success(String message) {
        return new StepResult(Status.SUCCESS, message);
    }

    public static StepResult skip(String message) {
        return new StepResult(Status.SKIP, message);
    }

    public static StepResult failContinue(String message) {
        return new StepResult(Status.FAIL_CONTINUE, message);
    }

    public static StepResult failAbort(String message) {
        return new StepResult(Status.FAIL_ABORT, message);
    }
}
