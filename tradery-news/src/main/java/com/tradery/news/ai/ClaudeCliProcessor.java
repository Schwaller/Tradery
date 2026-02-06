package com.tradery.news.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.news.model.Article;
import com.tradery.news.model.EntityType;
import com.tradery.news.model.EventType;
import com.tradery.news.model.ImportanceLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * AI processor using AiClient for news extraction.
 */
public class ClaudeCliProcessor implements AiProcessor {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliProcessor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String EXTRACTION_PROMPT = """
        Analyze this crypto news article and extract structured data. Return ONLY valid JSON, no other text.

        ARTICLE:
        Title: %s
        Source: %s
        Content: %s

        Return this exact JSON structure:
        {
          "summary": "2-3 sentence summary",
          "importance": "CRITICAL|HIGH|MEDIUM|LOW|NOISE",
          "coins": ["BTC", "ETH", "SOL"],
          "categories": ["Regulatory", "DeFi", "Exchange", "Security", "Institutional", "Technical"],
          "tags": ["#etf", "#hack"],
          "sentimentScore": 0.5,
          "events": [
            {
              "type": "REGULATORY|HACK|PARTNERSHIP|LAUNCH|LISTING|ETF|UPGRADE|EXPLOIT|FUNDING|LAWSUIT",
              "title": "Brief event title",
              "description": "1-2 sentences",
              "impactScore": 0.8
            }
          ],
          "entities": [
            {
              "name": "Entity Name",
              "type": "COIN|PERSON|COMPANY|EXCHANGE|REGULATOR|PROTOCOL",
              "symbol": "BTC or null"
            }
          ]
        }

        COIN EXTRACTION - IMPORTANT:
        Extract ALL cryptocurrencies mentioned using their ticker symbols:
        - Bitcoin, BTC -> "BTC"
        - Ethereum, Ether, ETH -> "ETH"
        - Solana, SOL -> "SOL"
        - XRP, Ripple -> "XRP"
        - Cardano, ADA -> "ADA"
        - Dogecoin, DOGE -> "DOGE"
        - Polygon, MATIC -> "MATIC"
        - Avalanche, AVAX -> "AVAX"
        - Chainlink, LINK -> "LINK"
        - Polkadot, DOT -> "DOT"
        - Other coins: use standard ticker symbol
        Include coins even if only mentioned briefly or in context (e.g., "ETH gas fees", "BTC dominance").

        Importance guide:
        - CRITICAL: ETF approvals, major hacks (>$100M), regulatory actions on major exchanges
        - HIGH: Listings, protocol upgrades, significant partnerships, price milestones
        - MEDIUM: Developer updates, minor news, analysis pieces
        - LOW: Opinion pieces, minor updates
        - NOISE: Clickbait, duplicates, non-news

        Sentiment: -1.0 (very negative) to +1.0 (very positive)
        """;

    private final AiClient aiClient;

    public ClaudeCliProcessor() {
        this.aiClient = AiClient.getInstance();
    }

    @Override
    public boolean isAvailable() {
        return aiClient.isAvailable();
    }

    @Override
    public ExtractionResult process(Article article) {
        String prompt = String.format(EXTRACTION_PROMPT,
            article.title(),
            article.sourceName(),
            truncate(article.content(), 3000)
        );

        try {
            String response = aiClient.query(prompt);
            String json = extractJson(response);
            return parseResult(json);
        } catch (AiException e) {
            log.error("Failed to process article {}: {}", article.id(), e.getMessage());
            return emptyResult();
        } catch (Exception e) {
            log.error("Failed to process article {}: {}", article.id(), e.getMessage());
            return emptyResult();
        }
    }

    private String extractJson(String text) {
        // Find JSON block in response
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new RuntimeException("No JSON found in response: " + truncate(text, 200));
    }

    private ExtractionResult parseResult(String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        String summary = root.path("summary").asText(null);
        ImportanceLevel importance = parseImportance(root.path("importance").asText("MEDIUM"));
        double sentiment = root.path("sentimentScore").asDouble(0);

        List<String> coins = parseStringList(root.path("coins"));
        List<String> categories = parseStringList(root.path("categories"));
        List<String> tags = parseStringList(root.path("tags"));

        List<ExtractionResult.ExtractedEvent> events = new ArrayList<>();
        for (JsonNode e : root.path("events")) {
            events.add(new ExtractionResult.ExtractedEvent(
                parseEventType(e.path("type").asText("LAUNCH")),
                e.path("title").asText(""),
                e.path("description").asText(""),
                null, null,
                e.path("sentimentScore").asDouble(sentiment),
                e.path("impactScore").asDouble(0.5)
            ));
        }

        List<ExtractionResult.ExtractedEntity> entities = new ArrayList<>();
        for (JsonNode e : root.path("entities")) {
            entities.add(new ExtractionResult.ExtractedEntity(
                e.path("name").asText(""),
                parseEntityType(e.path("type").asText("COMPANY")),
                e.has("symbol") && !e.path("symbol").isNull() ? e.path("symbol").asText() : null
            ));
        }

        return new ExtractionResult(
            summary, importance, coins, List.of(), categories, tags,
            sentiment, events, entities, List.of()
        );
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    list.add(item.asText());
                }
            }
        }
        return list;
    }

    private ImportanceLevel parseImportance(String s) {
        try {
            return ImportanceLevel.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return ImportanceLevel.MEDIUM;
        }
    }

    private EventType parseEventType(String s) {
        try {
            return EventType.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return EventType.LAUNCH;
        }
    }

    private EntityType parseEntityType(String s) {
        try {
            return EntityType.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return EntityType.COMPANY;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private ExtractionResult emptyResult() {
        return new ExtractionResult(
            null, ImportanceLevel.MEDIUM, List.of(), List.of(), List.of(), List.of(),
            0, List.of(), List.of(), List.of()
        );
    }
}
