package com.tradery.news.topic;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Registry of topics for classifying articles.
 * Loaded from ~/.cryptonews/topics.json (or uses defaults).
 */
public class TopicRegistry {

    private final Map<String, Topic> rootTopics;
    private final Path configPath;

    public TopicRegistry(Path configPath) {
        this.configPath = configPath;
        this.rootTopics = new LinkedHashMap<>();
        load();
    }

    /**
     * Get all root topics.
     */
    public Collection<Topic> getRootTopics() {
        return rootTopics.values();
    }

    /**
     * Get a topic by path (e.g., "crypto.defi").
     */
    public Optional<Topic> getTopic(String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 0) return Optional.empty();

        Topic current = rootTopics.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            current = current.subtopics().get(parts[i]);
        }
        return Optional.ofNullable(current);
    }

    /**
     * Classify text and return matching topic paths.
     */
    public List<String> classify(String text) {
        List<String> matches = new ArrayList<>();
        for (Topic root : rootTopics.values()) {
            classifyRecursive(root, "", text, matches);
        }
        return matches;
    }

    private void classifyRecursive(Topic topic, String parentPath, String text, List<String> matches) {
        String path = topic.path(parentPath);
        if (topic.matches(text)) {
            matches.add(path);
        }
        for (Topic subtopic : topic.subtopics().values()) {
            classifyRecursive(subtopic, path, text, matches);
        }
    }

    /**
     * Load topics from config file or use defaults.
     */
    private void load() {
        if (Files.exists(configPath)) {
            try {
                loadFromFile();
                return;
            } catch (IOException e) {
                // Fall through to defaults
            }
        }
        loadDefaults();
    }

    private void loadFromFile() throws IOException {
        // TODO: Implement JSON loading
    }

    /**
     * Save current topics to config file.
     */
    public void save() throws IOException {
        Files.createDirectories(configPath.getParent());
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(),
            Map.of("topics", rootTopics));
    }

    /**
     * Load default topic taxonomy.
     */
    private void loadDefaults() {
        rootTopics.put("crypto", buildCryptoTopic());
    }

    private Topic buildCryptoTopic() {
        Map<String, Topic> subtopics = new LinkedHashMap<>();

        subtopics.put("defi", Topic.builder()
            .id("defi")
            .name("DeFi")
            .keywords(List.of("defi", "yield", "liquidity", "amm", "lending", "borrowing", "dex", "swap"))
            .subtopics(Map.of())
            .build());

        subtopics.put("nft", Topic.builder()
            .id("nft")
            .name("NFTs")
            .keywords(List.of("nft", "opensea", "blur", "collectible", "pfp", "ordinals"))
            .subtopics(Map.of())
            .build());

        subtopics.put("regulation", Topic.builder()
            .id("regulation")
            .name("Regulation")
            .keywords(List.of("sec", "cftc", "regulation", "lawsuit", "enforcement", "compliance", "ban"))
            .subtopics(Map.of())
            .build());

        subtopics.put("exchange", Topic.builder()
            .id("exchange")
            .name("Exchanges")
            .keywords(List.of("binance", "coinbase", "kraken", "okx", "bybit", "exchange", "listing", "delist"))
            .subtopics(Map.of())
            .build());

        subtopics.put("layer1", Topic.builder()
            .id("layer1")
            .name("Layer 1s")
            .keywords(List.of("ethereum", "solana", "avalanche", "cardano", "layer1", "l1", "mainnet"))
            .subtopics(Map.of())
            .build());

        subtopics.put("layer2", Topic.builder()
            .id("layer2")
            .name("Layer 2s")
            .keywords(List.of("arbitrum", "optimism", "zksync", "rollup", "l2", "base", "polygon"))
            .subtopics(Map.of())
            .build());

        subtopics.put("stablecoins", Topic.builder()
            .id("stablecoins")
            .name("Stablecoins")
            .keywords(List.of("usdt", "usdc", "dai", "stablecoin", "depeg", "tether"))
            .subtopics(Map.of())
            .build());

        subtopics.put("memecoins", Topic.builder()
            .id("memecoins")
            .name("Memecoins")
            .keywords(List.of("doge", "shib", "pepe", "memecoin", "meme", "bonk", "wif"))
            .subtopics(Map.of())
            .build());

        subtopics.put("institutional", Topic.builder()
            .id("institutional")
            .name("Institutional")
            .keywords(List.of("etf", "blackrock", "fidelity", "grayscale", "institutional", "custody"))
            .subtopics(Map.of())
            .build());

        subtopics.put("security", Topic.builder()
            .id("security")
            .name("Security")
            .keywords(List.of("hack", "exploit", "vulnerability", "rug", "scam", "drain", "stolen"))
            .subtopics(Map.of())
            .build());

        return Topic.builder()
            .id("crypto")
            .name("Cryptocurrency")
            .keywords(List.of("bitcoin", "ethereum", "blockchain", "crypto", "cryptocurrency", "btc", "eth"))
            .subtopics(subtopics)
            .build();
    }
}
