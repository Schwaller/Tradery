package com.tradery.ai.pipeline.step;

import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import com.tradery.ai.pipeline.schema.EntityTypeDescriptor;
import com.tradery.ai.pipeline.schema.RelationshipTypeDescriptor;
import com.tradery.ai.pipeline.schema.SchemaSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates and auto-corrects entities against the request's allowed types.
 * <p>
 * Entity types that don't match the allowed set are auto-corrected to the
 * relationship's expected target type (e.g., AI returns type="person" for
 * founded_by → corrected to "foundation"). Only rejects entities with
 * invalid relationship types.
 */
class SchemaValidateStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidateStep.class);

    @Override
    public StepResult execute(StepContext context) {
        List<DiscoveredEntity> entities = context.currentEntities();
        if (entities.isEmpty()) {
            return StepResult.skip("No entities to validate");
        }

        // Build allowed type sets from the request
        Set<String> allowedEntityTypes = context.request().targetEntityTypes() != null
            ? context.request().targetEntityTypes().stream()
                .map(EntityTypeDescriptor::id)
                .collect(Collectors.toSet())
            : null;

        Set<String> allowedRelTypes = context.request().relationshipTypes() != null
            ? context.request().relationshipTypes().stream()
                .map(RelationshipTypeDescriptor::id)
                .collect(Collectors.toSet())
            : null;

        // Build relationship → expected target type map for auto-correction
        Map<String, String> relToTargetType = buildRelTargetTypeMap(context);

        List<DiscoveredEntity> valid = new ArrayList<>();
        int rejected = 0;
        int corrected = 0;

        // Track unknown types for schema suggestions: typeId → entities with that type
        Map<String, List<DiscoveredEntity>> unknownTypeEntities = new HashMap<>();

        for (DiscoveredEntity entity : entities) {
            // Validate relationship type — this is a hard requirement
            boolean relOk = allowedRelTypes == null
                || allowedRelTypes.contains(entity.relationshipTypeId());

            if (!relOk) {
                rejected++;
                log.debug("SchemaValidate rejected '{}': unknown relType={}",
                    entity.name(), entity.relationshipTypeId());
                continue;
            }

            // Auto-correct entity type if it doesn't match allowed types
            boolean typeOk = allowedEntityTypes == null
                || allowedEntityTypes.contains(entity.typeId());

            if (typeOk) {
                valid.add(entity);
            } else {
                // Track this unknown type for suggesting
                unknownTypeEntities
                    .computeIfAbsent(entity.typeId(), k -> new ArrayList<>())
                    .add(entity);

                // Try to correct type based on the relationship's expected target
                String expectedType = relToTargetType.get(entity.relationshipTypeId());
                if (expectedType != null) {
                    corrected++;
                    log.debug("SchemaValidate corrected '{}': {} → {} (via rel {})",
                        entity.name(), entity.typeId(), expectedType, entity.relationshipTypeId());
                    valid.add(entity.withTypeId(expectedType));
                } else {
                    // No relationship mapping either — still keep it with best-effort type
                    valid.add(entity);
                    log.debug("SchemaValidate kept '{}' with unknown type={} (no rel mapping)",
                        entity.name(), entity.typeId());
                }
            }
        }

        // Generate schema suggestions for unknown entity types
        for (Map.Entry<String, List<DiscoveredEntity>> entry : unknownTypeEntities.entrySet()) {
            String typeId = entry.getKey();
            List<DiscoveredEntity> typeEntities = entry.getValue();

            List<String> exampleNames = typeEntities.stream()
                .map(DiscoveredEntity::name)
                .limit(5)
                .toList();

            List<String> relIds = typeEntities.stream()
                .map(DiscoveredEntity::relationshipTypeId)
                .distinct()
                .toList();

            SchemaSuggestion suggestion = SchemaSuggestion.of(
                typeId, typeEntities.size(), exampleNames, relIds);
            context.addSchemaSuggestion(suggestion);

            log.info("SchemaValidate suggests new type '{}': {} entities (e.g. {})",
                typeId, typeEntities.size(), exampleNames);
        }

        context.setCurrentEntities(valid);
        String msg = valid.size() + " valid, " + rejected + " rejected, " + corrected + " auto-corrected";
        if (!unknownTypeEntities.isEmpty()) {
            msg += ", " + unknownTypeEntities.size() + " type suggestions";
        }
        context.notifyProgress(name(), msg, 0.5);
        return StepResult.success(msg);
    }

    /**
     * Build a map from relationship type ID → expected target entity type ID.
     * Uses the source entity's type to determine direction.
     */
    private Map<String, String> buildRelTargetTypeMap(StepContext context) {
        Map<String, String> map = new HashMap<>();
        if (context.request().relationshipTypes() == null) return map;

        String sourceTypeId = context.request().entity().typeId();
        for (RelationshipTypeDescriptor rel : context.request().relationshipTypes()) {
            String targetTypeId = (sourceTypeId != null && sourceTypeId.equals(rel.fromTypeId()))
                ? rel.toTypeId() : rel.fromTypeId();
            if (targetTypeId != null) {
                map.put(rel.id(), targetTypeId);
            }
        }
        return map;
    }

    @Override
    public String name() { return "schema-validate"; }
}
