package com.tradery.ai.pipeline.schema;

import java.util.List;

/**
 * A suggestion from the AI pipeline to extend the schema with a new entity type.
 * Generated when the AI returns entities with type IDs not in the current schema.
 */
public record SchemaSuggestion(
    String typeId,
    String suggestedName,
    int entityCount,
    List<String> exampleEntityNames,
    List<String> sourceRelationshipIds
) {
    /**
     * Create from raw data. Capitalizes the type ID as the suggested name.
     */
    public static SchemaSuggestion of(String typeId, int entityCount,
                                       List<String> exampleNames, List<String> relIds) {
        String name = capitalize(typeId.replace('_', ' ').replace('-', ' '));
        return new SchemaSuggestion(typeId, name, entityCount, exampleNames, relIds);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1));
        }
        return sb.toString();
    }
}
