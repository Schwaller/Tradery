package com.tradery.ai.pipeline.step;

import com.tradery.ai.pipeline.matching.FuzzyMatcher;
import com.tradery.ai.pipeline.matching.MatchCandidate;
import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import com.tradery.ai.pipeline.schema.ExistingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs fuzzy matching against existing entities and annotates matches
 * in the discovered entity's attributes map.
 */
class DedupStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(DedupStep.class);

    @Override
    public StepResult execute(StepContext context) {
        List<ExistingEntity> existing = context.request().existingEntities();
        if (existing == null || existing.isEmpty()) {
            return StepResult.skip("No existing entities for dedup");
        }

        List<DiscoveredEntity> entities = context.currentEntities();
        if (entities.isEmpty()) {
            return StepResult.skip("No discovered entities to dedup");
        }

        FuzzyMatcher matcher = new FuzzyMatcher(existing);
        List<DiscoveredEntity> annotated = new ArrayList<>();
        int matchCount = 0;

        for (DiscoveredEntity entity : entities) {
            List<MatchCandidate> matches = matcher.findMatches(entity);
            if (!matches.isEmpty()) {
                MatchCandidate best = matches.get(0);
                // Annotate entity with match info
                var attrs = new java.util.HashMap<>(entity.attributes());
                attrs.put("_matchedEntityId", best.existingId());
                attrs.put("_matchScore", String.format("%.2f", best.score()));
                attrs.put("_matchReason", best.reason().name());
                annotated.add(new DiscoveredEntity(
                    entity.name(), entity.symbol(), entity.typeId(),
                    entity.relationshipTypeId(), entity.reason(), entity.confidence(),
                    Map.copyOf(attrs)));
                matchCount++;
            } else {
                annotated.add(entity);
            }
        }

        context.setCurrentEntities(annotated);
        String msg = matchCount + " of " + entities.size() + " matched existing entities";
        context.notifyProgress(name(), msg, 0.9);
        return StepResult.success(msg);
    }

    @Override
    public String name() { return "dedup"; }
}
