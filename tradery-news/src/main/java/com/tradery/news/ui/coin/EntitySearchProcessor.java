package com.tradery.news.ui.coin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.ai.AiClient;
import com.tradery.ai.AiException;
import com.tradery.ai.AiProfile;
import com.tradery.ai.DuckDuckGoSearchProvider;
import com.tradery.ai.WebSearchException;
import com.tradery.ai.WebSearchProvider;
import com.tradery.news.ui.IntelLogPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AI processor for entity discovery using AiClient.
 */
public class EntitySearchProcessor {

    private static final Logger log = LoggerFactory.getLogger(EntitySearchProcessor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final AiClient aiClient;

    public EntitySearchProcessor() {
        this.aiClient = AiClient.getInstance();
    }

    /**
     * Check if selected AI CLI is available.
     */
    public boolean isAvailable() {
        return aiClient.isAvailable();
    }

    /**
     * Get the name of the current AI provider.
     */
    public String getProviderName() {
        return aiClient.getProviderName();
    }

    /**
     * Search for entities related to the given entity.
     *
     * @param entity  The entity to search related entities for
     * @param relType The relationship type to search for (null for all)
     * @return Search result containing discovered entities
     */
    public SearchResult searchRelated(CoinEntity entity, CoinRelationship.Type relType) {
        return searchRelated(entity, relType, null);
    }

    /**
     * Search for entities related to the given entity with progress logging.
     *
     * @param entity  The entity to search related entities for
     * @param relType The relationship type to search for (null for all)
     * @param logger  Optional callback for progress logging
     * @return Search result containing discovered entities
     */
    public SearchResult searchRelated(CoinEntity entity, CoinRelationship.Type relType, Consumer<String> logger) {
        String prompt = buildPrompt(entity, relType);

        // Log prompt detail
        String searchType = relType != null ? relType.name() : "all relationships";
        String provider = getProviderName();
        if (logger != null) {
            logger.accept("[" + provider + "] Finding " + searchType + " for " + entity.name());
            // Show truncated prompt
            String shortPrompt = prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt;
            logger.accept("Prompt: " + shortPrompt.replace("\n", " "));
        }
        IntelLogPanel.logAI("Prompt: Find " + searchType + " for " + entity.name() + " (" + entity.type() + ")");

        try {
            if (logger != null) logger.accept("Calling " + provider + " CLI...");
            String response = aiClient.query(prompt);
            String json = extractJson(response);
            if (logger != null) logger.accept("Parsing JSON response...");
            SearchResult result = parseResult(json);

            // Log response summary
            if (!result.entities().isEmpty()) {
                StringBuilder sb = new StringBuilder("Found: ");
                int count = Math.min(5, result.entities().size());
                for (int i = 0; i < count; i++) {
                    if (i > 0) sb.append(", ");
                    DiscoveredEntity e = result.entities().get(i);
                    sb.append(e.name());
                    if (e.symbol() != null) sb.append(" (").append(e.symbol()).append(")");
                }
                if (result.entities().size() > 5) {
                    sb.append(" +").append(result.entities().size() - 5).append(" more");
                }
                IntelLogPanel.logData(sb.toString());
                if (logger != null) logger.accept(sb.toString());
            }

            return result;
        } catch (AiException e) {
            log.error("Failed to search for related entities: {}", e.getMessage());
            IntelLogPanel.logError("AI error: " + e.getMessage());
            return new SearchResult(List.of(), "Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to search for related entities: {}", e.getMessage());
            IntelLogPanel.logError("AI error: " + e.getMessage());
            return new SearchResult(List.of(), "Error: " + e.getMessage());
        }
    }

    /**
     * Deep search using the default AI profile.
     * Falls back to standard AI-only search if web search fails.
     */
    public SearchResult searchRelatedDeep(CoinEntity entity, CoinRelationship.Type relType, Consumer<String> logger) {
        return searchRelatedDeep(entity, relType, null, logger);
    }

    /**
     * Deep search using a specific AI profile.
     * Performs web research via DuckDuckGo, then feeds results as context to AI.
     * Falls back to standard AI-only search if web search fails.
     *
     * @param profile The AI profile to use, or null for default
     */
    public SearchResult searchRelatedDeep(CoinEntity entity, CoinRelationship.Type relType,
                                           AiProfile profile, Consumer<String> logger) {
        WebSearchProvider webSearch = new DuckDuckGoSearchProvider();

        // Generate smart search queries based on entity + relationship type
        List<String> queries = generateSearchQueries(entity, relType);
        List<WebSearchProvider.SearchResult> webResults = new ArrayList<>();

        for (String query : queries) {
            IntelLogPanel.logData("Web search: " + query);
            if (logger != null) logger.accept("Searching web: " + query);

            try {
                List<WebSearchProvider.SearchResult> results = webSearch.search(query, 8);
                webResults.addAll(results);
                IntelLogPanel.logData("  " + results.size() + " results from " + webSearch.getName());
            } catch (WebSearchException e) {
                log.warn("Web search failed for query '{}': {}", query, e.getMessage());
                IntelLogPanel.logError("Web search failed: " + e.getMessage());
            }
        }

        // If no web results at all, fall back to standard AI-only search
        if (webResults.isEmpty()) {
            IntelLogPanel.logAI("No web results — falling back to AI-only search");
            if (logger != null) logger.accept("Web search unavailable, using AI-only...");
            return searchRelated(entity, relType, logger);
        }

        IntelLogPanel.logData("Total web results: " + webResults.size() + " from " + queries.size() + " queries");

        // Build augmented prompt with web context
        String prompt = buildDeepPrompt(entity, relType, webResults);

        String searchType = relType != null ? relType.name() : "all relationships";
        String providerName = profile != null ? profile.getProvider().name() : getProviderName();
        if (logger != null) {
            logger.accept("[" + providerName + "] Deep search for " + searchType + " (" + webResults.size() + " web results)");
        }
        IntelLogPanel.logAI("Deep prompt: Find " + searchType + " for " + entity.name() + " with " + webResults.size() + " web results");

        try {
            if (logger != null) logger.accept("Calling " + providerName + " CLI...");
            String response = profile != null ? aiClient.query(prompt, profile) : aiClient.query(prompt);
            String json = extractJson(response);
            if (logger != null) logger.accept("Parsing JSON response...");
            SearchResult result = parseResult(json);

            if (!result.entities().isEmpty()) {
                StringBuilder sb = new StringBuilder("Found: ");
                int count = Math.min(5, result.entities().size());
                for (int i = 0; i < count; i++) {
                    if (i > 0) sb.append(", ");
                    DiscoveredEntity e = result.entities().get(i);
                    sb.append(e.name());
                    if (e.symbol() != null) sb.append(" (").append(e.symbol()).append(")");
                }
                if (result.entities().size() > 5) {
                    sb.append(" +").append(result.entities().size() - 5).append(" more");
                }
                IntelLogPanel.logData(sb.toString());
                if (logger != null) logger.accept(sb.toString());
            }

            return result;
        } catch (AiException e) {
            log.error("Deep search AI call failed: {}", e.getMessage());
            IntelLogPanel.logError("AI error: " + e.getMessage());
            return new SearchResult(List.of(), "Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Deep search failed: {}", e.getMessage());
            IntelLogPanel.logError("AI error: " + e.getMessage());
            return new SearchResult(List.of(), "Error: " + e.getMessage());
        }
    }

    private List<String> generateSearchQueries(CoinEntity entity, CoinRelationship.Type relType) {
        String name = entity.name();
        String symbol = entity.symbol();
        List<String> queries = new ArrayList<>();

        if (relType == null) {
            // General deep search
            queries.add(name + " cryptocurrency related projects ecosystem");
            if (symbol != null) {
                queries.add(symbol + " crypto partnerships investors VCs");
            }
            queries.add(name + " blockchain DeFi integrations 2024 2025");
        } else {
            queries.addAll(switch (relType) {
                case ECOSYSTEM -> List.of(
                    name + " ecosystem projects tokens DeFi",
                    name + " blockchain ecosystem dApps protocols"
                );
                case INVESTED_IN -> entity.type() == CoinEntity.Type.VC
                    ? List.of(
                        name + " crypto portfolio investments",
                        name + " blockchain investments funding rounds"
                    )
                    : List.of(
                        name + " investors venture capital funding",
                        name + " Series A B funding round crypto"
                    );
                case ETF_TRACKS -> List.of(
                    name + " cryptocurrency ETF list spot",
                    (symbol != null ? symbol : name) + " ETF approved SEC"
                );
                case ETP_TRACKS -> List.of(
                    name + " cryptocurrency ETP exchange traded product",
                    (symbol != null ? symbol : name) + " ETP Europe"
                );
                case L2_OF -> entity.type() == CoinEntity.Type.COIN
                    ? List.of(
                        name + " Layer 2 networks rollups",
                        name + " L2 scaling solutions"
                    )
                    : List.of(
                        name + " Layer 1 blockchain built on"
                    );
                case PARTNER -> List.of(
                    name + " strategic partnerships crypto",
                    name + " blockchain partners integrations"
                );
                case FORK_OF -> List.of(
                    name + " fork forked from blockchain",
                    name + " hard fork code fork crypto"
                );
                case FOUNDED_BY -> List.of(
                    name + " founders co-founders team",
                    name + " who founded created blockchain"
                );
                case BRIDGE -> List.of(
                    name + " cross-chain bridge interoperability",
                    name + " blockchain bridge protocols"
                );
                case COMPETITOR -> List.of(
                    name + " competitors alternatives crypto",
                    name + " vs comparison blockchain"
                );
            });
        }

        return queries;
    }

    private String buildDeepPrompt(CoinEntity entity, CoinRelationship.Type relType,
                                    List<WebSearchProvider.SearchResult> webResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a cryptocurrency and financial research assistant. ");
        sb.append("Given an entity and web research results, find related entities of specific types.\n\n");

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

        // Web research context (capped at ~3000 chars)
        sb.append("=== WEB RESEARCH CONTEXT ===\n");
        sb.append("Use these real web search results to ground your entity discovery.\n");
        sb.append("Prefer entities mentioned in these results.\n\n");

        int charBudget = 3000;
        int charsUsed = 0;
        String lastQuery = null;
        int resultNum = 0;

        for (WebSearchProvider.SearchResult result : webResults) {
            String line = (resultNum + 1) + ". " + result.title() + " - " + result.snippet() + " [" + result.url() + "]\n";

            if (charsUsed + line.length() > charBudget) break;

            sb.append(line);
            charsUsed += line.length();
            resultNum++;
        }

        sb.append("\n=== END WEB RESEARCH ===\n\n");

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

            Required JSON fields per entity: name (string), symbol (string or null), type (COIN|L2|ETF|ETP|DAT|VC|EXCHANGE|FOUNDATION|COMPANY), relationshipType (L2_OF|ETF_TRACKS|ETP_TRACKS|INVESTED_IN|FOUNDED_BY|PARTNER|FORK_OF|BRIDGE|ECOSYSTEM|COMPETITOR), reason (string), confidence (0.0-1.0).

            Guidelines:
            - Prioritize entities mentioned in the web research results (confidence 0.85+)
            - Entities not in web results but known to you: confidence 0.7-0.8
            - Include 5-20 entities maximum
            - For VCs, list their known crypto investments
            - For coins, list ETFs/ETPs that track them, VCs that invested, L2s built on them
            - For exchanges, list their native tokens and key partnerships
            """);

        return sb.toString();
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

            Required JSON fields per entity: name (string), symbol (string or null), type (COIN|L2|ETF|ETP|DAT|VC|EXCHANGE|FOUNDATION|COMPANY), relationshipType (L2_OF|ETF_TRACKS|ETP_TRACKS|INVESTED_IN|FOUNDED_BY|PARTNER|FORK_OF|BRIDGE|ECOSYSTEM|COMPETITOR), reason (string), confidence (0.0-1.0).

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
