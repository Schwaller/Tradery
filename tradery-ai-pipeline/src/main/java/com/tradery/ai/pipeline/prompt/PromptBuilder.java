package com.tradery.ai.pipeline.prompt;

import com.tradery.ai.WebSearchProvider;
import com.tradery.ai.pipeline.DiscoveryRequest;
import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import com.tradery.ai.pipeline.schema.EntityDescriptor;
import com.tradery.ai.pipeline.schema.EntityTypeDescriptor;
import com.tradery.ai.pipeline.schema.RelationshipTypeDescriptor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates all prompts for the discovery pipeline from schema descriptors.
 * No domain-specific knowledge — everything comes from the request's descriptors.
 */
public class PromptBuilder {

    private PromptBuilder() {}

    /**
     * Build the main query prompt for entity discovery.
     */
    public static String buildQueryPrompt(DiscoveryRequest request,
                                           List<WebSearchProvider.SearchResult> webContext) {
        StringBuilder sb = new StringBuilder();

        // System context
        if (request.systemContext() != null) {
            sb.append(request.systemContext()).append("\n");
        }
        sb.append("Given an entity, find related entities.\n\n");

        // Entity description
        appendEntityDescription(sb, request.entity());

        // Web research context if available
        if (webContext != null && !webContext.isEmpty()) {
            appendWebContext(sb, webContext);
        }

        // Relationship table: what to search for, with target types
        appendRelationshipTable(sb, request);

        // JSON format spec
        appendJsonFormat(sb, request);

        // Guidelines
        appendGuidelines(sb, webContext != null && !webContext.isEmpty());

        return sb.toString();
    }

    /**
     * Build an escalation prompt that asks for MORE entities beyond what was already found.
     */
    public static String buildEscalationPrompt(DiscoveryRequest request,
                                                List<DiscoveredEntity> previousResults,
                                                List<WebSearchProvider.SearchResult> webContext) {
        StringBuilder sb = new StringBuilder();

        if (request.systemContext() != null) {
            sb.append(request.systemContext()).append("\n");
        }
        sb.append("Given an entity, find ADDITIONAL related entities beyond what was already found.\n\n");

        appendEntityDescription(sb, request.entity());

        if (webContext != null && !webContext.isEmpty()) {
            appendWebContext(sb, webContext);
        }

        appendRelationshipTable(sb, request);

        // List already found entities
        sb.append("=== ALREADY FOUND (do NOT include these) ===\n");
        for (DiscoveredEntity e : previousResults) {
            sb.append("- ").append(e.name());
            if (e.symbol() != null) sb.append(" (").append(e.symbol()).append(")");
            sb.append(" [").append(e.typeId()).append("]\n");
        }
        sb.append("=== END ALREADY FOUND ===\n\n");

        sb.append("Find additional entities NOT in the above list.\n\n");

        appendJsonFormat(sb, request);
        appendGuidelines(sb, webContext != null && !webContext.isEmpty());

        return sb.toString();
    }

    /**
     * Build a challenge prompt to verify discovered entities.
     */
    public static String buildChallengePrompt(DiscoveryRequest request,
                                               List<DiscoveredEntity> entities) {
        StringBuilder sb = new StringBuilder();

        if (request.systemContext() != null) {
            sb.append(request.systemContext()).append("\n");
        }

        sb.append("Verify the following claimed entities related to ");
        sb.append(request.entity().name()).append(".\n");
        sb.append("Remove any that are incorrect, hallucinated, or not genuinely related.\n");
        sb.append("Return ONLY the valid ones as a JSON array.\n\n");

        sb.append("Entities to verify:\n");
        for (DiscoveredEntity e : entities) {
            sb.append("- ").append(e.name());
            if (e.symbol() != null) sb.append(" (").append(e.symbol()).append(")");
            sb.append(" [type=").append(e.typeId());
            sb.append(", rel=").append(e.relationshipTypeId());
            sb.append("]: ").append(e.reason()).append("\n");
        }
        sb.append("\n");

        appendJsonFormat(sb, request);

        return sb.toString();
    }

    // ==================== Private helpers ====================

    private static void appendEntityDescription(StringBuilder sb, EntityDescriptor entity) {
        sb.append("Entity: ").append(entity.name());
        if (entity.symbol() != null) {
            sb.append(" (").append(entity.symbol()).append(")");
        }
        sb.append("\nType: ").append(entity.typeId()).append("\n");

        if (entity.attributes() != null && !entity.attributes().isEmpty()) {
            for (var entry : entity.attributes().entrySet()) {
                sb.append(capitalize(entry.getKey())).append(": ").append(entry.getValue()).append("\n");
            }
        }
        sb.append("\n");
    }

    private static void appendWebContext(StringBuilder sb,
                                          List<WebSearchProvider.SearchResult> webResults) {
        sb.append("=== WEB RESEARCH CONTEXT ===\n");
        sb.append("Use these real web search results to ground your entity discovery.\n");
        sb.append("Prefer entities mentioned in these results.\n\n");

        int charBudget = 3000;
        int charsUsed = 0;
        int num = 0;
        for (WebSearchProvider.SearchResult result : webResults) {
            String line = (num + 1) + ". " + result.title() + " - " + result.snippet()
                + " [" + result.url() + "]\n";
            if (charsUsed + line.length() > charBudget) break;
            sb.append(line);
            charsUsed += line.length();
            num++;
        }
        sb.append("\n=== END WEB RESEARCH ===\n\n");
    }

    /**
     * Build the relationship table that maps each relationship to its target type.
     * Structure: [entity] → [relationship] → [target type]
     */
    private static void appendRelationshipTable(StringBuilder sb, DiscoveryRequest request) {
        if (request.relationshipTypes() == null || request.relationshipTypes().isEmpty()) {
            sb.append("Find: All related entities for ").append(request.entity().name()).append("\n\n");
            return;
        }

        String sourceTypeId = request.entity().typeId();
        String entityName = request.entity().name();

        sb.append("Find entities matching these relationships:\n\n");

        for (RelationshipTypeDescriptor relType : request.relationshipTypes()) {
            // Determine target type based on direction
            String targetTypeId = (sourceTypeId != null && sourceTypeId.equals(relType.fromTypeId()))
                ? relType.toTypeId() : relType.fromTypeId();

            // Resolve target type name from the request's entity types
            String targetTypeName = targetTypeId;
            if (request.targetEntityTypes() != null) {
                for (EntityTypeDescriptor et : request.targetEntityTypes()) {
                    if (et.id().equals(targetTypeId)) {
                        targetTypeName = et.name();
                        break;
                    }
                }
            }

            String description = relType.searchDescriptionFor(entityName, sourceTypeId);
            sb.append("  ").append(entityName)
              .append(" --[").append(relType.id()).append("]--> ")
              .append(targetTypeName)
              .append(": ").append(description).append("\n");
        }
        sb.append("\n");
    }

    private static void appendJsonFormat(StringBuilder sb, DiscoveryRequest request) {
        sb.append("Return ONLY a JSON array with discovered entities. No other text.\n");
        sb.append("Each entity should have:\n");
        sb.append("- name: Full name of the entity\n");
        sb.append("- symbol: Ticker symbol if applicable (null otherwise)\n");

        // Entity types from descriptors
        if (request.targetEntityTypes() != null && !request.targetEntityTypes().isEmpty()) {
            String types = request.targetEntityTypes().stream()
                .map(EntityTypeDescriptor::id)
                .collect(Collectors.joining("|"));
            sb.append("- type: One of ").append(types).append("\n");
        } else {
            sb.append("- type: Entity type identifier\n");
        }

        // Relationship types from descriptors
        if (request.relationshipTypes() != null && !request.relationshipTypes().isEmpty()) {
            String relTypes = request.relationshipTypes().stream()
                .map(RelationshipTypeDescriptor::id)
                .collect(Collectors.joining("|"));
            sb.append("- relationshipType: One of ").append(relTypes).append("\n");
        } else {
            sb.append("- relationshipType: Relationship type identifier\n");
        }

        sb.append("- reason: Brief explanation of why this entity is related (1 sentence)\n");
        sb.append("- confidence: Confidence score from 0.0 to 1.0\n\n");

        // Type hints per relationship (soft guidance, not strict)
        if (request.relationshipTypes() != null && !request.relationshipTypes().isEmpty()) {
            String sourceTypeId = request.entity().typeId();
            sb.append("Typical type for each relationship (use the most accurate type from the list above):\n");
            for (RelationshipTypeDescriptor relType : request.relationshipTypes()) {
                String targetTypeId = (sourceTypeId != null && sourceTypeId.equals(relType.fromTypeId()))
                    ? relType.toTypeId() : relType.fromTypeId();
                if (targetTypeId != null) {
                    sb.append("- ").append(relType.id()).append(" → typically ").append(targetTypeId).append("\n");
                }
            }
            sb.append("\n");
        }
    }

    private static void appendGuidelines(StringBuilder sb, boolean hasWebContext) {
        sb.append("Guidelines:\n");
        if (hasWebContext) {
            sb.append("- Prioritize entities mentioned in the web research results (confidence 0.85+)\n");
            sb.append("- Entities not in web results but known to you: confidence 0.7-0.8\n");
        } else {
            sb.append("- Only include entities you are highly confident about (confidence > 0.7)\n");
        }
        sb.append("- Prefer well-known, established entities over obscure ones\n");
        sb.append("- Include 5-20 entities maximum\n");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
