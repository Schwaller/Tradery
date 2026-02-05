package com.tradery.news.ai;

import com.tradery.news.model.EntityType;
import com.tradery.news.model.EventType;
import com.tradery.news.model.ImportanceLevel;
import com.tradery.news.model.RelationType;

import java.util.List;

/**
 * Result of AI extraction from an article.
 */
public record ExtractionResult(
    String summary,
    ImportanceLevel importance,
    List<String> coins,
    List<String> topics,
    List<String> categories,
    List<String> tags,
    double sentimentScore,
    List<ExtractedEvent> events,
    List<ExtractedEntity> entities,
    List<ExtractedRelationship> relationships
) {
    public record ExtractedEvent(
        EventType type,
        String title,
        String description,
        String startDate,
        String endDate,
        double sentimentScore,
        double impactScore
    ) {}

    public record ExtractedEntity(
        String name,
        EntityType type,
        String symbol
    ) {}

    public record ExtractedRelationship(
        String source,
        String target,
        RelationType type,
        String context
    ) {}
}
