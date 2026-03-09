package com.tradery.ai.challenges.execution;

import com.tradery.ai.challenges.model.*;
import com.tradery.ai.challenges.subject.ChallengeSubject;
import com.tradery.ai.pipeline.DiscoveryPipeline;
import com.tradery.ai.pipeline.DiscoveryRequest;
import com.tradery.ai.pipeline.DiscoveryResult;
import com.tradery.ai.pipeline.schema.RelationshipTypeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Executes ENTITY_SET challenges by delegating to the existing DiscoveryPipeline.
 * The challenge description is injected as system context for the pipeline.
 */
public class EntitySetExecutor {

    private static final Logger log = LoggerFactory.getLogger(EntitySetExecutor.class);

    public EntitySetExecutor() {}

    public ChallengeResult execute(Challenge challenge, ChallengeSubject subject,
                                    ChallengeEscalation escalation, Consumer<String> logger) {
        long startTime = System.currentTimeMillis();
        String challengeId = challenge.id();
        String subjectId = subject.id();
        int escIndex = challenge.escalations().indexOf(escalation);

        try {
            String pipelineName = escalation.pipeline();
            if (pipelineName == null) pipelineName = "quick";

            if (logger != null) {
                logger.accept("[" + escalation.label() + "] Running " + challenge.title()
                    + " on " + subject.name() + " (pipeline: " + pipelineName + ")");
            }

            // Build discovery request with challenge context
            DiscoveryRequest.Builder requestBuilder = DiscoveryRequest.builder()
                .entity(subject.toEntityDescriptor());

            // Use challenge description as system context
            String systemContext = challenge.output().configValue("systemContext");
            if (systemContext == null) {
                systemContext = challenge.description();
            }
            requestBuilder.systemContext(systemContext);

            // Set relationship type if configured
            String relTypeId = challenge.output().configValue("relationshipTypeId");
            if (relTypeId != null) {
                requestBuilder.relationshipTypes(List.of(
                    new RelationshipTypeDescriptor(relTypeId, subject.typeId(), null,
                        challenge.description(), null, null, null)
                ));
            }

            DiscoveryRequest request = requestBuilder.build();

            // Execute pipeline
            DiscoveryPipeline pipeline = DiscoveryPipeline.create(pipelineName);
            DiscoveryResult result = pipeline.execute(request, (stepName, message, fraction) -> {
                if (logger != null) logger.accept("[" + stepName + "] " + message);
            });

            long duration = System.currentTimeMillis() - startTime;

            if (logger != null) {
                int count = result.entities() != null ? result.entities().size() : 0;
                logger.accept("Found " + count + " entities");
            }

            // Extract signal
            Double signal = SignalExtractor.extract(challenge.signalConfig(), null,
                result.entities());

            ChallengeResult cr = ChallengeResult.entitySet(challengeId, subjectId, escIndex,
                result, signal, duration);
            cr.setResolvedTier(escalation.tier());
            return cr;

        } catch (Exception e) {
            log.error("Entity set challenge failed: {}/{}: {}", challengeId, subjectId, e.getMessage());
            if (logger != null) logger.accept("Error: " + e.getMessage());
            return ChallengeResult.error(challengeId, subjectId, escIndex,
                ChallengeOutput.Type.ENTITY_SET, e.getMessage(),
                System.currentTimeMillis() - startTime);
        }
    }
}
