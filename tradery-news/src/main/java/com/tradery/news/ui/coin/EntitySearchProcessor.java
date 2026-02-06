package com.tradery.news.ui.coin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI processor for entity discovery using Claude CLI.
 */
public class EntitySearchProcessor {

    private static final Logger log = LoggerFactory.getLogger(EntitySearchProcessor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String claudePath;
    private final int timeoutSeconds;

    public EntitySearchProcessor() {
        this("claude", 60);
    }

    public EntitySearchProcessor(String claudePath, int timeoutSeconds) {
        this.claudePath = claudePath;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Check if Claude CLI is available.
     */
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(claudePath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Search for entities related to the given entity.
     *
     * @param entity  The entity to search related entities for
     * @param relType The relationship type to search for (null for all)
     * @return Search result containing discovered entities
     */
    public SearchResult searchRelated(CoinEntity entity, CoinRelationship.Type relType) {
        String prompt = buildPrompt(entity, relType);

        try {
            String json = runClaude(prompt);
            return parseResult(json);
        } catch (Exception e) {
            log.error("Failed to search for related entities: {}", e.getMessage());
            return new SearchResult(List.of(), "Error: " + e.getMessage());
        }
    }

    private String buildPrompt(CoinEntity entity, CoinRelationship.Type relType) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a cryptocurrency and financial research assistant. ");
        sb.append("Given an entity, find related entities of specific types.\n\n");

        sb.append("Entity: ").append(entity.name());
        if (entity.symbol() != null) {
            sb.append(" (").append(entity.symbol()).append(")");
        }
        sb.append("\nType: ").append(entity.type().name()).append("\n");

        if (!entity.categories().isEmpty()) {
            sb.append("Categories: ").append(String.join(", ", entity.categories())).append("\n");
        }
        if (entity.marketCap() > 0) {
            sb.append("Market Cap: $").append(formatMarketCap(entity.marketCap())).append("\n");
        }
        sb.append("\n");

        sb.append("Find: ");
        if (relType == null) {
            sb.append(getSearchDescriptionForAllTypes(entity));
        } else {
            sb.append(getSearchDescriptionForType(entity, relType));
        }
        sb.append("\n\n");

        sb.append("""
            Return ONLY a JSON array with discovered entities. No other text.
            Each entity should have:
            - name: Full name of the entity
            - symbol: Ticker symbol if applicable (null otherwise)
            - type: One of COIN, L2, ETF, ETP, DAT, VC, EXCHANGE, FOUNDATION, COMPANY
            - relationshipType: One of L2_OF, ETF_TRACKS, ETP_TRACKS, INVESTED_IN, FOUNDED_BY, PARTNER, FORK_OF, BRIDGE, ECOSYSTEM, COMPETITOR
            - reason: Brief explanation of why this entity is related (1 sentence)
            - confidence: Confidence score from 0.0 to 1.0

            Example:
            [
              {"name": "BlackRock iShares Bitcoin Trust", "symbol": "IBIT", "type": "ETF", "relationshipType": "ETF_TRACKS", "reason": "Spot Bitcoin ETF approved Jan 2024", "confidence": 0.99},
              {"name": "Grayscale Bitcoin Trust", "symbol": "GBTC", "type": "ETF", "relationshipType": "ETF_TRACKS", "reason": "First Bitcoin trust converted to spot ETF", "confidence": 0.99}
            ]

            Guidelines:
            - Only include entities you are highly confident about (confidence > 0.7)
            - Prefer well-known, established entities over obscure ones
            - Include 5-15 entities maximum
            - For VCs, list their known crypto investments
            - For coins, list ETFs/ETPs that track them, VCs that invested, L2s built on them
            - For exchanges, list their native tokens and key partnerships
            """);

        return sb.toString();
    }

    private String getSearchDescriptionForAllTypes(CoinEntity entity) {
        return switch (entity.type()) {
            case COIN, L2 -> """
                All related entities including:
                - ETFs and ETPs that track this cryptocurrency
                - Venture capital firms that have invested in this project
                - Layer 2 networks built on this chain (if L1)
                - Projects in the same ecosystem
                - Known forks of this project
                - Key partners and integrations""";
            case VC -> """
                All cryptocurrencies and blockchain projects this VC has invested in.
                Include both direct investments and fund participation.""";
            case EXCHANGE -> """
                - The exchange's native token (if any)
                - Key blockchain projects the exchange has invested in or partnered with
                - Major ecosystem partnerships""";
            case ETF, ETP, DAT -> "The cryptocurrency assets this fund tracks";
            case FOUNDATION -> """
                - Projects founded or supported by this foundation
                - Ecosystem partners and grantees""";
            case COMPANY -> """
                - Blockchain investments made by this company
                - Crypto partnerships and integrations""";
            default -> "Related entities in the cryptocurrency ecosystem";
        };
    }

    private String getSearchDescriptionForType(CoinEntity entity, CoinRelationship.Type relType) {
        return switch (relType) {
            case ETF_TRACKS -> "ETFs (Exchange-Traded Funds) that track " + entity.name();
            case ETP_TRACKS -> "ETPs (Exchange-Traded Products) that track " + entity.name();
            case INVESTED_IN -> entity.type() == CoinEntity.Type.VC
                ? "Cryptocurrency projects that " + entity.name() + " has invested in"
                : "Venture capital firms and investors that have funded " + entity.name();
            case L2_OF -> entity.type() == CoinEntity.Type.COIN
                ? "Layer 2 networks built on " + entity.name()
                : "The Layer 1 blockchain that " + entity.name() + " is built on";
            case ECOSYSTEM -> "Projects and tokens in the " + entity.name() + " ecosystem";
            case PARTNER -> "Strategic partners of " + entity.name();
            case FORK_OF -> "Projects that forked from " + entity.name() + " or that " + entity.name() + " forked from";
            case FOUNDED_BY -> "Founders and founding organizations of " + entity.name();
            case BRIDGE -> "Blockchain bridges connected to " + entity.name();
            case COMPETITOR -> "Direct competitors of " + entity.name();
        };
    }

    private String formatMarketCap(double marketCap) {
        if (marketCap >= 1_000_000_000_000L) {
            return String.format("%.1fT", marketCap / 1_000_000_000_000L);
        } else if (marketCap >= 1_000_000_000L) {
            return String.format("%.1fB", marketCap / 1_000_000_000L);
        } else if (marketCap >= 1_000_000L) {
            return String.format("%.1fM", marketCap / 1_000_000L);
        }
        return String.format("%.0f", marketCap);
    }

    private String runClaude(String prompt) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            claudePath,
            "--print",
            "--output-format", "text",
            "--model", "haiku"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes());
            stdin.flush();
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Claude CLI timed out after " + timeoutSeconds + " seconds");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Claude CLI failed: " + output);
        }

        return extractJson(output.toString());
    }

    private String extractJson(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new RuntimeException("No JSON array found in response: " + truncate(text, 200));
    }

    private SearchResult parseResult(String json) throws Exception {
        List<DiscoveredEntity> entities = new ArrayList<>();
        JsonNode root = mapper.readTree(json);

        if (root.isArray()) {
            for (JsonNode node : root) {
                try {
                    String name = node.path("name").asText();
                    String symbol = node.has("symbol") && !node.path("symbol").isNull()
                        ? node.path("symbol").asText() : null;
                    CoinEntity.Type type = parseEntityType(node.path("type").asText("COIN"));
                    CoinRelationship.Type relType = parseRelationType(node.path("relationshipType").asText("PARTNER"));
                    String reason = node.path("reason").asText("");
                    double confidence = node.path("confidence").asDouble(0.5);

                    if (!name.isEmpty() && confidence >= 0.5) {
                        entities.add(new DiscoveredEntity(name, symbol, type, relType, reason, confidence));
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse entity: {}", e.getMessage());
                }
            }
        }

        return new SearchResult(entities, null);
    }

    private CoinEntity.Type parseEntityType(String s) {
        try {
            return CoinEntity.Type.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return CoinEntity.Type.COIN;
        }
    }

    private CoinRelationship.Type parseRelationType(String s) {
        try {
            return CoinRelationship.Type.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return CoinRelationship.Type.PARTNER;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * Result of an entity search operation.
     */
    public record SearchResult(List<DiscoveredEntity> entities, String error) {
        public boolean hasError() {
            return error != null && !error.isEmpty();
        }
    }

    /**
     * An entity discovered through AI search.
     */
    public record DiscoveredEntity(
        String name,
        String symbol,
        CoinEntity.Type type,
        CoinRelationship.Type relationshipType,
        String reason,
        double confidence
    ) {
        /**
         * Generate a unique ID for this entity based on name and type.
         */
        public String generateId() {
            String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
            return base.length() > 50 ? base.substring(0, 50) : base;
        }
    }
}
