package com.tradery.ai.challenges.execution;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.SignalConfig;
import com.tradery.ai.challenges.subject.ChallengeSubject;

import java.util.List;
import java.util.Map;

/**
 * Builds prompts from challenge definitions and subject context.
 * Domain-agnostic: works with any ChallengeSubject implementation.
 */
public class PromptAssembler {

    private PromptAssembler() {}

    /**
     * Build the main query prompt for a challenge.
     */
    public static String build(Challenge challenge, ChallengeSubject subject) {
        return build(challenge, subject, "");
    }

    public static String build(Challenge challenge, ChallengeSubject subject, String searchContext) {
        return build(challenge, subject, searchContext, null);
    }

    public static String build(Challenge challenge, ChallengeSubject subject, String searchContext,
                                List<Map<String, String>> previousItems) {
        StringBuilder sb = new StringBuilder();

        // Subject context
        sb.append("Subject: ").append(subject.name());
        if (subject.symbol() != null) {
            sb.append(" (").append(subject.symbol()).append(")");
        }
        sb.append("\nType: ").append(subject.typeId()).append("\n");

        Map<String, String> attrs = subject.attributes();
        if (attrs != null && !attrs.isEmpty()) {
            sb.append("\nContext:\n");
            attrs.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }

        // Web search context
        if (searchContext != null && !searchContext.isBlank()) {
            sb.append(searchContext);
        }
        sb.append("\n");

        // Challenge instruction
        sb.append(challenge.description()).append("\n");

        // Output format instruction
        appendOutputInstructions(sb, challenge.output());

        // Forward-feed previous items for tracking list mode continuity
        if (challenge.output().isTracking() && previousItems != null && !previousItems.isEmpty()) {
            appendPreviousItems(sb, challenge.output(), previousItems);
        }

        // Signal extraction instruction (not needed for STRUCTURED — signal comes from fields)
        if (challenge.output().type() != ChallengeOutput.Type.STRUCTURED) {
            appendSignalInstructions(sb, challenge.signalConfig());
        }

        return sb.toString();
    }

    /**
     * Build a verification prompt that cross-checks a previous response.
     */
    public static String buildVerification(Challenge challenge, ChallengeSubject subject,
                                            String previousResponse) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are verifying the accuracy of an AI-generated response.\n\n");
        sb.append("Subject: ").append(subject.name());
        if (subject.symbol() != null) {
            sb.append(" (").append(subject.symbol()).append(")");
        }
        sb.append("\nType: ").append(subject.typeId()).append("\n\n");

        sb.append("Original question: ").append(challenge.description()).append("\n\n");
        sb.append("Previous response:\n").append(previousResponse).append("\n\n");

        sb.append("Please verify the above response for accuracy and completeness. ");
        sb.append("Correct any errors, fill in missing information, and provide an improved version. ");

        appendOutputInstructions(sb, challenge.output());
        if (challenge.output().type() != ChallengeOutput.Type.STRUCTURED) {
            appendSignalInstructions(sb, challenge.signalConfig());
        }

        return sb.toString();
    }

    private static void appendOutputInstructions(StringBuilder sb, ChallengeOutput output) {
        switch (output.type()) {
            case TEXT -> sb.append("\nProvide your answer as clear, concise text.\n");
            case LIST -> {
                sb.append("\nReturn your answer as a JSON array of strings. ");
                sb.append("Example: [\"item1\", \"item2\", \"item3\"]\n");
                sb.append("Return ONLY the JSON array, no other text.\n");
            }
            case STRUCTURED -> {
                sb.append("\nUse the web search results above for current information. ");
                sb.append("Synthesize the search results into a coherent answer.\n");
                List<ChallengeOutput.Field> fields = output.fields();
                if (output.listMode()) {
                    // List of structured objects
                    sb.append("Return your answer as a JSON array of objects. Each object has these fields:\n");
                    sb.append("[\n  {\n");
                    appendFieldExamples(sb, fields, "    ", output.reasonDetail());
                    sb.append("  },\n  ...\n]\n");
                    sb.append("Include ALL relevant items. Return ONLY the JSON array, no other text.\n");
                    sb.append("If you cannot answer, return: {\"error\": \"reason why\"}\n");
                } else {
                    // Single structured object
                    sb.append("Return your answer as a JSON object with exactly these fields:\n");
                    sb.append("{\n");
                    appendFieldExamples(sb, fields, "  ", output.reasonDetail());
                    sb.append("}\n");
                    sb.append("If you cannot answer, return: {\"error\": \"reason why\"}\n");
                    sb.append("Return ONLY the JSON object, no other text.\n");
                }
            }
            case ENTITY_SET -> {} // Handled by DiscoveryPipeline's own prompt building
        }
    }

    /**
     * Forward-feed previous list items so the AI can correlate and track entities over time.
     */
    /**
     * Append JSON field examples with optional _reason fields for each numeric field.
     */
    private static void appendFieldExamples(StringBuilder sb, List<ChallengeOutput.Field> fields,
                                             String indent, ChallengeOutput.ReasonDetail reasonDetail) {
        boolean wantReasons = reasonDetail != null && reasonDetail != ChallengeOutput.ReasonDetail.NONE;
        for (int i = 0; i < fields.size(); i++) {
            ChallengeOutput.Field f = fields.get(i);
            boolean isNumeric = f.type() == ChallengeOutput.Field.FieldType.NUMBER
                || f.type() == ChallengeOutput.Field.FieldType.SCORE;
            String example = switch (f.type()) {
                case TEXT -> "\"...\"";
                case NUMBER, SCORE -> String.valueOf((f.minValue() + f.maxValue()) / 2);
            };
            sb.append(indent).append("\"").append(f.name()).append("\": ").append(example).append(",");
            sb.append("  // ").append(f.label());
            if (isNumeric) {
                sb.append(" (").append(f.minValue()).append(" to ").append(f.maxValue()).append(")");
            }
            sb.append("\n");
            if (isNumeric && wantReasons) {
                String detail = switch (reasonDetail) {
                    case BRIEF -> "one sentence justification";
                    case DETAILED -> "a few sentences with supporting evidence";
                    case VERBOSE -> "thorough analysis with data points and reasoning";
                    default -> "";
                };
                sb.append(indent).append("\"").append(f.name()).append("_reason\": \"...\",");
                sb.append("  // ").append(detail).append(" for the ").append(f.label()).append(" value\n");
            }
        }
    }

    private static void appendPreviousItems(StringBuilder sb, ChallengeOutput output,
                                             List<Map<String, String>> previousItems) {
        // Find the primary/name field
        String nameField = null;
        for (ChallengeOutput.Field f : output.fields()) {
            if (f.primary()) { nameField = f.name(); break; }
        }
        if (nameField == null && !output.fields().isEmpty()) {
            nameField = output.fields().getFirst().name();
        }

        sb.append("\nIMPORTANT — Previous assessment found these items:\n");
        for (int i = 0; i < previousItems.size(); i++) {
            Map<String, String> item = previousItems.get(i);
            String status = item.getOrDefault("_status", "active");
            sb.append(i + 1).append(". ");
            if (nameField != null) {
                sb.append(item.getOrDefault(nameField, "?"));
            }
            if ("removed".equals(status)) {
                sb.append(" [was removed]");
            } else {
                // Show previous values
                for (ChallengeOutput.Field f : output.fields()) {
                    if (f.name().equals(nameField)) continue;
                    String v = item.get(f.name());
                    if (v != null) sb.append(", ").append(f.label()).append(": ").append(v);
                }
            }
            sb.append("\n");
        }
        sb.append("\nYou MUST include ALL items from the previous list above, with updated values. ");
        sb.append("Add new items if relevant. ");
        sb.append("If an item is no longer relevant or active, still include it but add ");
        sb.append("\"_status\": \"removed\" to that item's JSON object. ");
        sb.append("Active items should NOT have a _status field.\n");
    }

    private static void appendSignalInstructions(StringBuilder sb, SignalConfig config) {
        if (config == null || config.mode() == SignalConfig.Mode.NONE) return;

        switch (config.mode()) {
            case EXPLICIT -> {
                String instruction = config.signalInstruction();
                if (instruction == null) {
                    instruction = "End your response with [SIGNAL: X] where X is a numeric score from 0 to 10.";
                }
                sb.append("\n").append(instruction).append("\n");
            }
            case ORDINAL -> {
                if (!config.ordinalMap().isEmpty()) {
                    sb.append("\nClassify your assessment as exactly one of: ");
                    sb.append(String.join(", ", config.ordinalMap().keySet()));
                    sb.append("\nInclude your classification on a separate line as: [CLASSIFICATION: VALUE]\n");
                }
            }
            case COUNT, NONE -> {} // No prompt modification needed
        }
    }
}
