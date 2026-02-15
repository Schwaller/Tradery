package com.tradery.ai.pipeline.schema;

import java.util.List;

/**
 * Describes an allowed relationship type with AI prompt metadata.
 * Carries both forward and inverse search descriptions/hints so the pipeline
 * can generate prompts from either direction.
 */
public record RelationshipTypeDescriptor(
    String id,
    String fromTypeId,
    String toTypeId,
    String searchDescription,
    String inverseSearchDescription,
    List<String> searchHints,
    List<String> inverseSearchHints
) {
    /**
     * Get the appropriate search description given the source entity type.
     * Uses forward description if sourceTypeId matches fromTypeId, inverse otherwise.
     *
     * @param entityName     the entity name to substitute into the template
     * @param sourceTypeId   the type ID of the source entity
     */
    public String searchDescriptionFor(String entityName, String sourceTypeId) {
        String template = (sourceTypeId != null && sourceTypeId.equals(fromTypeId))
            ? searchDescription : inverseSearchDescription;
        if (template == null) template = searchDescription;
        return template != null ? safeFormat(template, entityName) : id + " of " + entityName;
    }

    /**
     * Get web search query hints given the source entity type.
     */
    public List<String> searchHintsFor(String entityName, String sourceTypeId) {
        List<String> hints = (sourceTypeId != null && sourceTypeId.equals(fromTypeId))
            ? searchHints : inverseSearchHints;
        if (hints == null || hints.isEmpty()) hints = searchHints;
        if (hints == null || hints.isEmpty()) return List.of(entityName + " " + id);
        return hints.stream().map(h -> safeFormat(h, entityName)).toList();
    }

    /**
     * Safely substitute %s in a template. If no %s present or format fails,
     * append the entity name instead.
     */
    private static String safeFormat(String template, String entityName) {
        if (template.contains("%s")) {
            try {
                return String.format(template, entityName);
            } catch (Exception e) {
                // Fall through
            }
        }
        return template + " " + entityName;
    }
}
