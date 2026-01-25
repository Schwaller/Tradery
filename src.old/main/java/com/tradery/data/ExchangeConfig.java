package com.tradery.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tradery.TraderyApp;
import com.tradery.model.DataMarketType;
import com.tradery.model.Exchange;
import com.tradery.model.PriceNormalizationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Configuration for multi-exchange data sources.
 * Loads from ~/.tradery/exchanges.yaml
 *
 * Example configuration:
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
 * symbols:
 *   BTC:
 *     binance: BTCUSDT
 *     bybit: BTCUSDT
 *     okx: BTC-USDT-SWAP
 *
 * priceSettings:
 *   normalizationMode: USDT_AS_USD
 *   referenceExchange: binance
 * </pre>
 */
public class ExchangeConfig {

    private static final Logger log = LoggerFactory.getLogger(ExchangeConfig.class);
    private static final String CONFIG_FILE = "exchanges.yaml";

    private static ExchangeConfig instance;

    private final Map<Exchange, ExchangeSettings> exchanges = new EnumMap<>(Exchange.class);
    private final Map<String, Map<Exchange, String>> symbolMappings = new HashMap<>();
    private PriceSettings priceSettings = new PriceSettings();

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
        symbolMappings.clear();
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

            // Apply symbol mappings
            if (config.symbols != null) {
                for (Map.Entry<String, Map<String, String>> entry : config.symbols.entrySet()) {
                    String baseSymbol = entry.getKey().toUpperCase();
                    Map<Exchange, String> exchangeSymbols = new EnumMap<>(Exchange.class);

                    for (Map.Entry<String, String> mapping : entry.getValue().entrySet()) {
                        Exchange ex = Exchange.fromConfigKey(mapping.getKey());
                        if (ex != null) {
                            exchangeSymbols.put(ex, mapping.getValue());
                        }
                    }

                    symbolMappings.put(baseSymbol, exchangeSymbols);
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
     * Get the exchange-specific symbol for a base symbol.
     *
     * @param baseSymbol Base symbol (e.g., "BTC")
     * @param exchange Target exchange
     * @return Exchange-specific symbol (e.g., "BTCUSDT" for Binance, "BTC-USDT-SWAP" for OKX)
     */
    public String getSymbol(String baseSymbol, Exchange exchange) {
        Map<Exchange, String> mappings = symbolMappings.get(baseSymbol.toUpperCase());
        if (mappings != null && mappings.containsKey(exchange)) {
            return mappings.get(exchange);
        }
        // Default mappings
        return switch (exchange) {
            case BINANCE, BYBIT -> baseSymbol.toUpperCase() + "USDT";
            case OKX -> baseSymbol.toUpperCase() + "-USDT-SWAP";
            case COINBASE -> baseSymbol.toUpperCase() + "-USD";
            case KRAKEN -> baseSymbol.toUpperCase() + "USD";
            case BITFINEX -> "t" + baseSymbol.toUpperCase() + "USD";
        };
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
        Map<String, Map<String, String>> symbols;

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
    private static class PriceSettingsYaml {
        @JsonProperty("normalizationMode")
        String normalizationMode;

        @JsonProperty("referenceExchange")
        String referenceExchange;

        @JsonProperty("fetchStablecoinRates")
        Boolean fetchStablecoinRates;
    }
}
