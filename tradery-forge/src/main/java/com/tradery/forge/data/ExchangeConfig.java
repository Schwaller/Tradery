package com.tradery.forge.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tradery.forge.TraderyApp;
import com.tradery.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Configuration for multi-exchange data sources.
 * Loads from ~/.tradery/exchanges.yaml
 *
 * Example configuration (new format):
 * <pre>
 * exchanges:
 *   binance:
 *     enabled: true
 *     markets: [perp]
 *     priority: 1
 *   bybit:
 *     enabled: true
 *     markets: [perp]
 *     priority: 2
 *
 * symbolMappings:
 *   BTC:
 *     displayName: Bitcoin
 *     aliases: [XBT]
 *     binance:
 *       perp:
 *         USDT: BTCUSDT
 *         USDC: BTCUSDC
 *       spot:
 *         USDT: BTCUSDT
 *     okx:
 *       perp:
 *         USDT: BTC-USDT-SWAP
 *
 * derivation:
 *   enabled: false
 *   templates:
 *     binance: "{BASE}{QUOTE}"
 *     okx-perp: "{BASE}-{QUOTE}-SWAP"
 *
 * priceSettings:
 *   normalizationMode: USDT_AS_USD
 *   referenceExchange: binance
 * </pre>
 *
 * Also supports legacy format (auto-migrated):
 * <pre>
 * symbols:
 *   BTC:
 *     binance: BTCUSDT
 * </pre>
 */
public class ExchangeConfig {

    private static final Logger log = LoggerFactory.getLogger(ExchangeConfig.class);
    private static final String CONFIG_FILE = "exchanges.yaml";

    private static ExchangeConfig instance;

    private final Map<Exchange, ExchangeSettings> exchanges = new EnumMap<>(Exchange.class);
    private final SymbolResolver symbolResolver = new SymbolResolver();
    private PriceSettings priceSettings = new PriceSettings();

    // Legacy mappings for backward compatibility (deprecated)
    @Deprecated
    private final Map<String, Map<Exchange, String>> legacySymbolMappings = new HashMap<>();

    private ExchangeConfig() {
        loadDefaultConfig();
        loadConfigFile();
    }

    public static synchronized ExchangeConfig getInstance() {
        if (instance == null) {
            instance = new ExchangeConfig();
        }
        return instance;
    }

    /**
     * Reload configuration from file.
     */
    public void reload() {
        exchanges.clear();
        symbolResolver.clear();
        legacySymbolMappings.clear();
        priceSettings = new PriceSettings();
        loadDefaultConfig();
        loadConfigFile();
    }

    /**
     * Set default configuration (Binance enabled, others disabled).
     */
    private void loadDefaultConfig() {
        // Binance enabled by default
        ExchangeSettings binance = new ExchangeSettings();
        binance.enabled = true;
        binance.markets = List.of(DataMarketType.FUTURES_PERP);
        binance.priority = 1;
        exchanges.put(Exchange.BINANCE, binance);

        // Other exchanges disabled by default
        for (Exchange ex : Exchange.values()) {
            if (ex != Exchange.BINANCE) {
                ExchangeSettings settings = new ExchangeSettings();
                settings.enabled = false;
                settings.markets = List.of(DataMarketType.FUTURES_PERP);
                settings.priority = ex.ordinal() + 1;
                exchanges.put(ex, settings);
            }
        }
    }

    /**
     * Load configuration from file if it exists.
     */
    private void loadConfigFile() {
        File configFile = new File(TraderyApp.USER_DIR, CONFIG_FILE);
        if (!configFile.exists()) {
            log.debug("No exchanges.yaml found, using defaults");
            return;
        }

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            yamlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            ConfigFileModel config = yamlMapper.readValue(configFile, ConfigFileModel.class);

            // Apply exchange settings
            if (config.exchanges != null) {
                for (Map.Entry<String, ExchangeSettingsYaml> entry : config.exchanges.entrySet()) {
                    Exchange ex = Exchange.fromConfigKey(entry.getKey());
                    if (ex != null) {
                        ExchangeSettings settings = exchanges.get(ex);
                        ExchangeSettingsYaml yaml = entry.getValue();
                        if (yaml.enabled != null) settings.enabled = yaml.enabled;
                        if (yaml.markets != null) {
                            settings.markets = yaml.markets.stream()
                                .map(DataMarketType::fromConfigKey)
                                .filter(Objects::nonNull)
                                .toList();
                        }
                        if (yaml.priority != null) settings.priority = yaml.priority;
                        if (yaml.apiKey != null) settings.apiKey = yaml.apiKey;
                        if (yaml.apiSecret != null) settings.apiSecret = yaml.apiSecret;
                    }
                }
            }

            // Apply new symbol mappings format (preferred)
            if (config.symbolMappings != null) {
                loadSymbolMappings(config.symbolMappings);
            }

            // Apply legacy symbol mappings (auto-migrate to new format)
            if (config.symbols != null) {
                loadLegacySymbolMappings(config.symbols);
            }

            // Apply derivation settings
            if (config.derivation != null) {
                if (config.derivation.enabled != null) {
                    symbolResolver.setDerivationEnabled(config.derivation.enabled);
                }
                if (config.derivation.templates != null) {
                    for (Map.Entry<String, String> entry : config.derivation.templates.entrySet()) {
                        symbolResolver.setDerivationTemplate(entry.getKey(), entry.getValue());
                    }
                }
            }

            // Apply price settings
            if (config.priceSettings != null) {
                if (config.priceSettings.normalizationMode != null) {
                    try {
                        priceSettings.normalizationMode = PriceNormalizationMode.valueOf(
                            config.priceSettings.normalizationMode.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid normalization mode: {}", config.priceSettings.normalizationMode);
                    }
                }
                if (config.priceSettings.referenceExchange != null) {
                    Exchange ref = Exchange.fromConfigKey(config.priceSettings.referenceExchange);
                    if (ref != null) {
                        priceSettings.referenceExchange = ref;
                    }
                }
                if (config.priceSettings.fetchStablecoinRates != null) {
                    priceSettings.fetchStablecoinRates = config.priceSettings.fetchStablecoinRates;
                }
            }

            log.info("Loaded exchange configuration from {}", configFile);

        } catch (IOException e) {
            log.error("Failed to load exchange configuration: {}", e.getMessage());
        }
    }

    /**
     * Load new format symbol mappings.
     */
    private void loadSymbolMappings(Map<String, SymbolMappingYaml> yamlMappings) {
        for (Map.Entry<String, SymbolMappingYaml> entry : yamlMappings.entrySet()) {
            String canonicalId = entry.getKey().toUpperCase();
            SymbolMappingYaml yaml = entry.getValue();

            SymbolMapping mapping = new SymbolMapping(canonicalId);

            if (yaml.displayName != null) {
                mapping.setDisplayName(yaml.displayName);
            }
            if (yaml.aliases != null) {
                mapping.setAliases(yaml.aliases);
            }

            // Parse exchange mappings
            for (Exchange exchange : Exchange.values()) {
                String exchangeKey = exchange.getConfigKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> exchangeData = yaml.exchangeMappings != null ?
                        (Map<String, Object>) yaml.exchangeMappings.get(exchangeKey) : null;

                if (exchangeData != null) {
                    parseExchangeMappings(mapping, exchange, exchangeData);
                }
            }

            symbolResolver.addMapping(mapping);
        }
    }

    /**
     * Parse exchange-specific mappings from YAML.
     */
    @SuppressWarnings("unchecked")
    private void parseExchangeMappings(SymbolMapping mapping, Exchange exchange, Map<String, Object> exchangeData) {
        for (Map.Entry<String, Object> marketEntry : exchangeData.entrySet()) {
            String marketKey = marketEntry.getKey();
            DataMarketType marketType = DataMarketType.fromConfigKey(marketKey);

            if (marketType != null && marketEntry.getValue() instanceof Map) {
                Map<String, String> quoteData = (Map<String, String>) marketEntry.getValue();
                for (Map.Entry<String, String> quoteEntry : quoteData.entrySet()) {
                    String quoteKey = quoteEntry.getKey().toUpperCase();
                    try {
                        QuoteCurrency quote = QuoteCurrency.valueOf(quoteKey);
                        mapping.setSymbol(exchange, marketType, quote, quoteEntry.getValue());
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown quote currency: {}", quoteKey);
                    }
                }
            }
        }
    }

    /**
     * Load legacy format and auto-migrate.
     * Old format: symbols.BTC.binance: BTCUSDT
     * Migrated to: symbolMappings.BTC.binance.perp.USDT: BTCUSDT
     */
    private void loadLegacySymbolMappings(Map<String, Map<String, String>> legacyMappings) {
        log.info("Migrating legacy symbol mappings to new format (assuming perp/USDT)");

        for (Map.Entry<String, Map<String, String>> entry : legacyMappings.entrySet()) {
            String canonicalId = entry.getKey().toUpperCase();
            Map<Exchange, String> exchangeSymbols = new EnumMap<>(Exchange.class);

            // Check if already exists in new format
            SymbolMapping existing = symbolResolver.getMapping(canonicalId);
            SymbolMapping mapping = existing != null ? existing : new SymbolMapping(canonicalId);

            for (Map.Entry<String, String> exchangeEntry : entry.getValue().entrySet()) {
                Exchange ex = Exchange.fromConfigKey(exchangeEntry.getKey());
                if (ex != null) {
                    String symbol = exchangeEntry.getValue();
                    exchangeSymbols.put(ex, symbol);

                    // Auto-migrate to new format: assume perp + USDT
                    mapping.setSymbol(ex, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, symbol);
                }
            }

            // Store for backward compatibility
            legacySymbolMappings.put(canonicalId, exchangeSymbols);

            // Add to resolver if not already present
            if (existing == null) {
                symbolResolver.addMapping(mapping);
            }
        }
    }

    // ========== Getters ==========

    /**
     * Get settings for an exchange.
     */
    public ExchangeSettings getExchangeSettings(Exchange exchange) {
        return exchanges.get(exchange);
    }

    /**
     * Check if an exchange is enabled.
     */
    public boolean isEnabled(Exchange exchange) {
        ExchangeSettings settings = exchanges.get(exchange);
        return settings != null && settings.enabled;
    }

    /**
     * Get all enabled exchanges sorted by priority.
     */
    public List<Exchange> getEnabledExchanges() {
        return exchanges.entrySet().stream()
            .filter(e -> e.getValue().enabled)
            .sorted(Comparator.comparingInt(e -> e.getValue().priority))
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Get the primary (highest priority) exchange.
     */
    public Exchange getPrimaryExchange() {
        return getEnabledExchanges().stream().findFirst().orElse(Exchange.BINANCE);
    }

    /**
     * Get the symbol resolver instance.
     */
    public SymbolResolver getSymbolResolver() {
        return symbolResolver;
    }

    /**
     * Resolve a canonical symbol to an exchange-specific symbol.
     * Full resolution with market type and quote currency.
     *
     * @param canonical Canonical symbol (e.g., "BTC")
     * @param exchange Target exchange
     * @param marketType Market type (perp, spot, dated)
     * @param quoteCurrency Quote currency (USDT, USDC, USD)
     * @return Exchange-specific symbol
     * @throws SymbolResolutionException if no mapping exists and derivation is disabled
     */
    public String resolveSymbol(String canonical, Exchange exchange, DataMarketType marketType, QuoteCurrency quoteCurrency) {
        return symbolResolver.resolve(canonical, exchange, marketType, quoteCurrency);
    }

    /**
     * Try to resolve a symbol, returning null instead of throwing if not found.
     */
    public String tryResolveSymbol(String canonical, Exchange exchange, DataMarketType marketType, QuoteCurrency quoteCurrency) {
        return symbolResolver.tryResolve(canonical, exchange, marketType, quoteCurrency);
    }

    /**
     * Get the exchange-specific symbol for a base symbol.
     * Uses default market type (FUTURES_PERP) and quote currency (USDT).
     *
     * For backward compatibility with existing code.
     *
     * @param baseSymbol Base symbol (e.g., "BTC")
     * @param exchange Target exchange
     * @return Exchange-specific symbol (e.g., "BTCUSDT" for Binance, "BTC-USDT-SWAP" for OKX)
     */
    public String getSymbol(String baseSymbol, Exchange exchange) {
        // Try the resolver first
        String resolved = symbolResolver.tryResolve(baseSymbol, exchange, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);
        if (resolved != null) {
            return resolved;
        }

        // Fall back to legacy behavior for backward compatibility
        Map<Exchange, String> mappings = legacySymbolMappings.get(baseSymbol.toUpperCase());
        if (mappings != null && mappings.containsKey(exchange)) {
            return mappings.get(exchange);
        }

        // Default mappings (same as before)
        return switch (exchange) {
            case BINANCE, BYBIT -> baseSymbol.toUpperCase() + "USDT";
            case OKX -> baseSymbol.toUpperCase() + "-USDT-SWAP";
            case COINBASE -> baseSymbol.toUpperCase() + "-USD";
            case KRAKEN -> baseSymbol.toUpperCase() + "USD";
            case BITFINEX -> "t" + baseSymbol.toUpperCase() + "USD";
        };
    }

    /**
     * Reverse resolve an exchange symbol to its canonical form.
     */
    public Optional<String> reverseResolveSymbol(String exchangeSymbol, Exchange exchange) {
        return symbolResolver.reverseResolve(exchangeSymbol, exchange);
    }

    /**
     * Get price normalization mode.
     */
    public PriceNormalizationMode getNormalizationMode() {
        return priceSettings.normalizationMode;
    }

    /**
     * Get reference exchange for price normalization.
     */
    public Exchange getReferenceExchange() {
        return priceSettings.referenceExchange;
    }

    /**
     * Check if stablecoin rate fetching is enabled.
     */
    public boolean shouldFetchStablecoinRates() {
        return priceSettings.fetchStablecoinRates;
    }

    // ========== Model Classes ==========

    /**
     * Settings for a single exchange.
     */
    public static class ExchangeSettings {
        public boolean enabled = false;
        public List<DataMarketType> markets = new ArrayList<>();
        public int priority = 99;
        public String apiKey = null;
        public String apiSecret = null;
    }

    /**
     * Price normalization settings.
     */
    public static class PriceSettings {
        public PriceNormalizationMode normalizationMode = PriceNormalizationMode.USDT_AS_USD;
        public Exchange referenceExchange = Exchange.BINANCE;
        public boolean fetchStablecoinRates = false;
    }

    // ========== YAML Model Classes ==========

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ConfigFileModel {
        @JsonProperty("exchanges")
        Map<String, ExchangeSettingsYaml> exchanges;

        @JsonProperty("symbols")
        Map<String, Map<String, String>> symbols;  // Legacy format

        @JsonProperty("symbolMappings")
        Map<String, SymbolMappingYaml> symbolMappings;  // New format

        @JsonProperty("derivation")
        DerivationSettingsYaml derivation;

        @JsonProperty("priceSettings")
        PriceSettingsYaml priceSettings;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExchangeSettingsYaml {
        @JsonProperty("enabled")
        Boolean enabled;

        @JsonProperty("markets")
        List<String> markets;

        @JsonProperty("priority")
        Integer priority;

        @JsonProperty("apiKey")
        String apiKey;

        @JsonProperty("apiSecret")
        String apiSecret;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SymbolMappingYaml {
        @JsonProperty("displayName")
        String displayName;

        @JsonProperty("aliases")
        List<String> aliases;

        // Dynamic exchange mappings stored here
        // We use JsonAnySetter to capture exchange-specific keys
        @JsonIgnoreProperties(ignoreUnknown = true)
        Map<String, Object> exchangeMappings;

        @JsonProperty
        void setOther(String key, Object value) {
            if (exchangeMappings == null) {
                exchangeMappings = new HashMap<>();
            }
            // Store non-standard properties (exchange keys like "binance", "okx")
            if (!key.equals("displayName") && !key.equals("aliases")) {
                exchangeMappings.put(key, value);
            }
        }

        @com.fasterxml.jackson.annotation.JsonAnySetter
        void setAny(String key, Object value) {
            if (exchangeMappings == null) {
                exchangeMappings = new HashMap<>();
            }
            exchangeMappings.put(key, value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DerivationSettingsYaml {
        @JsonProperty("enabled")
        Boolean enabled;

        @JsonProperty("templates")
        Map<String, String> templates;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PriceSettingsYaml {
        @JsonProperty("normalizationMode")
        String normalizationMode;

        @JsonProperty("referenceExchange")
        String referenceExchange;

        @JsonProperty("fetchStablecoinRates")
        Boolean fetchStablecoinRates;
    }
}
