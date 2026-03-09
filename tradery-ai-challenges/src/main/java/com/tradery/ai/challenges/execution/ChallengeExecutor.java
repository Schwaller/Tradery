package com.tradery.ai.challenges.execution;

import com.tradery.ai.AiClient;
import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeEscalation;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.subject.ChallengeSubject;
import com.tradery.ai.pipeline.config.PipelineStore;
import com.tradery.ai.pipeline.config.TierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Main entry point for executing challenges. Routes to the appropriate
 * executor based on output type.
 * <p>
 * Reusable: depends only on AiClient and pipeline infrastructure, not on any
 * specific domain model or UI framework.
 */
public class ChallengeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ChallengeExecutor.class);

    private final TextQueryExecutor textExecutor;
    private final EntitySetExecutor entitySetExecutor;

    public ChallengeExecutor() {
        AiClient aiClient = AiClient.getInstance();
        TierResolver tierResolver = PipelineStore.get().createTierResolver();
        this.textExecutor = new TextQueryExecutor(aiClient, tierResolver);
        this.entitySetExecutor = new EntitySetExecutor();
    }

    public ChallengeExecutor(AiClient aiClient, TierResolver tierResolver) {
        this.textExecutor = new TextQueryExecutor(aiClient, tierResolver);
        this.entitySetExecutor = new EntitySetExecutor();
    }

    /**
     * Execute a challenge against a subject at a specific escalation level.
     *
     * @param challenge       The challenge definition
     * @param subject         The thing being challenged
     * @param escalationIndex Index into challenge.escalations()
     * @param logger          Optional progress callback (for UI updates)
     * @return Result with output, signal value, and metadata
     */
    public ChallengeResult execute(Challenge challenge, ChallengeSubject subject,
                                    int escalationIndex, Consumer<String> logger) {
        return execute(challenge, subject, escalationIndex, logger, null);
    }

    /**
     * Execute with optional previous result for list mode continuity.
     */
    public ChallengeResult execute(Challenge challenge, ChallengeSubject subject,
                                    int escalationIndex, Consumer<String> logger,
                                    ChallengeResult previousResult) {
        if (escalationIndex < 0 || escalationIndex >= challenge.escalations().size()) {
            return ChallengeResult.error(challenge.id(), subject.id(), escalationIndex,
                challenge.output().type(),
                "Invalid escalation index: " + escalationIndex + " (available: " + challenge.escalations().size() + ")",
                0);
        }

        ChallengeEscalation escalation = challenge.escalations().get(escalationIndex);
        log.info("Executing challenge '{}' on '{}' at level '{}'",
            challenge.id(), subject.name(), escalation.label());

        return switch (challenge.output().type()) {
            case TEXT, LIST, STRUCTURED -> textExecutor.execute(challenge, subject, escalation, logger, previousResult);
            case ENTITY_SET -> entitySetExecutor.execute(challenge, subject, escalation, logger);
        };
    }

    /**
     * Check if AI is available for executing challenges.
     */
    public boolean isAvailable() {
        return AiClient.getInstance().isAvailable();
    }
}
