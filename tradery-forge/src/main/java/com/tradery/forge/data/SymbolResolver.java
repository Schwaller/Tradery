package com.tradery.forge.data;

import com.tradery.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Resolves canonical symbols (BTC, ETH) to exchange-specific formats.
 * Supports:
 * - Explicit mappings from configuration
 * - Alias resolution (XBT -> BTC)
 * - Derivation fallback (when enabled)
 * - Reverse resolution (exchange symbol -> canonical)
 */
public class SymbolResolver {

    private static final Logger log = LoggerFactory.getLogger(SymbolResolver.class);

    // Canonical symbol ID -> SymbolMapping
    private final Map<String, SymbolMapping> mappings = new HashMap<>();

    // Alias -> Canonical symbol ID (e.g., "XBT" -> "BTC")
    private final Map<String, String> aliasIndex = new HashMap<>();

    // Exchange-specific symbol -> Canonical symbol ID (reverse lookup)
    private final Map<String, String> reverseIndex = new HashMap<>();

    // Derivation settings
    private boolean derivationEnabled = false;
    private final Map<String, String> derivationTemplates = new HashMap<>();

    public SymbolResolver() {
        // Initialize default templates
        derivationTemplates.put("binance", "{BASE}{QUOTE}");
        derivationTemplates.put("bybit", "{BASE}{QUOTE}");
        derivationTemplates.put("okx-perp", "{BASE}-{QUOTE}-SWAP");
        derivationTemplates.put("okx-spot", "{BASE}-{QUOTE}");
        derivationTemplates.put("coinbase", "{BASE}-{QUOTE}");
        derivationTemplates.put("kraken", "{BASE}/{QUOTE}");
        derivationTemplates.put("bitfinex", "t{BASE}{QUOTE}");
    }

    /**
     * Add a symbol mapping.
     */
    public void addMapping(SymbolMapping mapping) {
        if (mapping == null || mapping.getId() == null) return;

        String id = mapping.getId().toUpperCase();
        mappings.put(id, mapping);

        // Index aliases
        for (String alias : mapping.getAliases()) {
            aliasIndex.put(alias.toUpperCase(), id);
        }

        // Build reverse index for all mapped symbols
        for (Exchange exchange : mapping.getMappedExchanges()) {
            for (DataMarketType market : mapping.getMappedMarketTypes(exchange)) {
                for (QuoteCurrency quote : mapping.getMappedQuoteCurrencies(exchange, market)) {
                    String exchangeSymbol = mapping.getSymbol(exchange, market, quote);
                    if (exchangeSymbol != null) {
                        // Key format: exchange:symbol (e.g., "binance:BTCUSDT")
                        String key = exchange.getConfigKey() + ":" + exchangeSymbol.toUpperCase();
                        reverseIndex.put(key, id);
                    }
                }
            }
        }

        log.debug("Added symbol mapping: {} (aliases: {})", id, mapping.getAliases());
    }

    /**
     * Set derivation enabled/disabled.
     */
    public void setDerivationEnabled(boolean enabled) {
        this.derivationEnabled = enabled;
    }

    /**
     * Check if derivation is enabled.
     */
    public boolean isDerivationEnabled() {
        return derivationEnabled;
    }

    /**
     * Set a derivation template.
     *
     * @param key Template key (e.g., "binance", "okx-perp")
     * @param template Template string (e.g., "{BASE}{QUOTE}", "{BASE}-{QUOTE}-SWAP")
     */
    public void setDerivationTemplate(String key, String template) {
        derivationTemplates.put(key.toLowerCase(), template);
    }

    /**
     * Get all derivation templates.
     */
    public Map<String, String> getDerivationTemplates() {
        return Collections.unmodifiableMap(derivationTemplates);
    }

    /**
     * Resolve a canonical symbol to an exchange-specific symbol.
     *
     * @param canonical Canonical symbol (e.g., "BTC", "ETH", or alias like "XBT")
     * @param exchange Target exchange
     * @param marketType Market type (perp, spot, dated)
     * @param quoteCurrency Quote currency (USDT, USDC, USD)
     * @return Exchange-specific symbol
     * @throws SymbolResolutionException if no mapping exists and derivation is disabled
     */
    public String resolve(String canonical, Exchange exchange, DataMarketType marketType, QuoteCurrency quoteCurrency) {
        if (canonical == null || exchange == null || marketType == null || quoteCurrency == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }

        // Normalize and resolve alias
        String normalizedCanonical = resolveAlias(canonical.toUpperCase());

        // Try explicit mapping first
        SymbolMapping mapping = mappings.get(normalizedCanonical);
        if (mapping != null) {
            String symbol = mapping.getSymbol(exchange, marketType, quoteCurrency);
            if (symbol != null) {
                return symbol;
            }
        }

        // Try derivation if enabled
        if (derivationEnabled) {
            String derived = deriveSymbol(normalizedCanonical, exchange, marketType, quoteCurrency);
            if (derived != null) {
                log.debug("Derived symbol {} for {}/{}/{}/{}", derived, normalizedCanonical,
                        exchange.getConfigKey(), marketType.getConfigKey(), quoteCurrency.getSymbol());
                return derived;
            }
        }

        // Strict mode - throw exception
        throw new SymbolResolutionException(normalizedCanonical, exchange, marketType, quoteCurrency);
    }

    /**
     * Try to resolve a canonical symbol, returning null instead of throwing if not found.
     */
    public String tryResolve(String canonical, Exchange exchange, DataMarketType marketType, QuoteCurrency quoteCurrency) {
        try {
            return resolve(canonical, exchange, marketType, quoteCurrency);
        } catch (SymbolResolutionException e) {
            return null;
        }
    }

    /**
     * Resolve with defaults (FUTURES_PERP, USDT).
     * For backward compatibility with existing code.
     */
    public String resolve(String canonical, Exchange exchange) {
        return resolve(canonical, exchange, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);
    }

    /**
     * Resolve an alias to its canonical symbol.
     */
    public String resolveAlias(String symbolOrAlias) {
        String upper = symbolOrAlias.toUpperCase();
        return aliasIndex.getOrDefault(upper, upper);
    }

    /**
     * Reverse resolve an exchange symbol to its canonical form.
     *
     * @param exchangeSymbol Exchange-specific symbol (e.g., "BTCUSDT", "BTC-USDT-SWAP")
     * @param exchange Source exchange
     * @return Canonical symbol or empty if not found
     */
    public Optional<String> reverseResolve(String exchangeSymbol, Exchange exchange) {
        if (exchangeSymbol == null || exchange == null) {
            return Optional.empty();
        }

        String key = exchange.getConfigKey() + ":" + exchangeSymbol.toUpperCase();
        String canonical = reverseIndex.get(key);
        if (canonical != null) {
            return Optional.of(canonical);
        }

        // Try pattern-based reverse resolution
        return tryPatternReverseResolve(exchangeSymbol, exchange);
    }

    /**
     * Validate that all enabled exchanges have required symbol mappings.
     *
     * @param enabledExchanges List of enabled exchanges
     * @param requiredMarkets List of required market types
     * @param requiredQuotes List of required quote currencies
     * @return Validation result with any missing mappings
     */
    public ValidationResult validate(Collection<Exchange> enabledExchanges,
                                      Collection<DataMarketType> requiredMarkets,
                                      Collection<QuoteCurrency> requiredQuotes) {
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String canonicalId : mappings.keySet()) {
            SymbolMapping mapping = mappings.get(canonicalId);

            for (Exchange exchange : enabledExchanges) {
                for (DataMarketType market : requiredMarkets) {
                    for (QuoteCurrency quote : requiredQuotes) {
                        String symbol = mapping.getSymbol(exchange, market, quote);
                        if (symbol == null) {
                            if (derivationEnabled) {
                                warnings.add(String.format("%s: %s/%s/%s will use derivation",
                                        canonicalId, exchange.getConfigKey(), market.getConfigKey(), quote.getSymbol()));
                            } else {
                                missing.add(String.format("%s: %s/%s/%s",
                                        canonicalId, exchange.getConfigKey(), market.getConfigKey(), quote.getSymbol()));
                            }
                        }
                    }
                }
            }
        }

        return new ValidationResult(missing.isEmpty(), missing, warnings);
    }

    /**
     * Get all canonical symbol IDs.
     */
    public Set<String> getCanonicalSymbols() {
        return Collections.unmodifiableSet(mappings.keySet());
    }

    /**
     * Get mapping for a canonical symbol.
     */
    public SymbolMapping getMapping(String canonical) {
        return mappings.get(canonical.toUpperCase());
    }

    /**
     * Check if a canonical symbol exists (including aliases).
     */
    public boolean hasSymbol(String symbolOrAlias) {
        String resolved = resolveAlias(symbolOrAlias);
        return mappings.containsKey(resolved);
    }

    /**
     * Clear all mappings.
     */
    public void clear() {
        mappings.clear();
        aliasIndex.clear();
        reverseIndex.clear();
    }

    // ========== Private Methods ==========

    private String deriveSymbol(String canonical, Exchange exchange, DataMarketType marketType, QuoteCurrency quoteCurrency) {
        // Determine template key
        String templateKey;
        if (exchange == Exchange.OKX) {
            templateKey = marketType == DataMarketType.SPOT ? "okx-spot" : "okx-perp";
        } else {
            templateKey = exchange.getConfigKey();
        }

        String template = derivationTemplates.get(templateKey);
        if (template == null) {
            log.warn("No derivation template for {}", templateKey);
            return null;
        }

        return template
                .replace("{BASE}", canonical)
                .replace("{QUOTE}", quoteCurrency.getSymbol());
    }

    private Optional<String> tryPatternReverseResolve(String exchangeSymbol, Exchange exchange) {
        String upper = exchangeSymbol.toUpperCase();

        // Try common patterns based on exchange
        return switch (exchange) {
            case BINANCE, BYBIT -> {
                // BTCUSDT, ETHUSDT -> BTC, ETH
                for (QuoteCurrency quote : QuoteCurrency.values()) {
                    if (upper.endsWith(quote.getSymbol())) {
                        String base = upper.substring(0, upper.length() - quote.getSymbol().length());
                        String resolved = resolveAlias(base);
                        if (mappings.containsKey(resolved)) {
                            yield Optional.of(resolved);
                        }
                    }
                }
                yield Optional.empty();
            }
            case OKX -> {
                // BTC-USDT-SWAP, BTC-USDT -> BTC
                String[] parts = upper.split("-");
                if (parts.length >= 2) {
                    String base = parts[0];
                    String resolved = resolveAlias(base);
                    if (mappings.containsKey(resolved)) {
                        yield Optional.of(resolved);
                    }
                }
                yield Optional.empty();
            }
            case COINBASE -> {
                // BTC-USD -> BTC
                String[] parts = upper.split("-");
                if (parts.length >= 1) {
                    String resolved = resolveAlias(parts[0]);
                    if (mappings.containsKey(resolved)) {
                        yield Optional.of(resolved);
                    }
                }
                yield Optional.empty();
            }
            case KRAKEN -> {
                // BTCUSD, BTC/USD -> BTC
                String cleaned = upper.replace("/", "");
                for (QuoteCurrency quote : QuoteCurrency.values()) {
                    if (cleaned.endsWith(quote.getSymbol())) {
                        String base = cleaned.substring(0, cleaned.length() - quote.getSymbol().length());
                        String resolved = resolveAlias(base);
                        if (mappings.containsKey(resolved)) {
                            yield Optional.of(resolved);
                        }
                    }
                }
                yield Optional.empty();
            }
            case BITFINEX -> {
                // tBTCUSD -> BTC
                String cleaned = upper.startsWith("T") ? upper.substring(1) : upper;
                for (QuoteCurrency quote : QuoteCurrency.values()) {
                    if (cleaned.endsWith(quote.getSymbol())) {
                        String base = cleaned.substring(0, cleaned.length() - quote.getSymbol().length());
                        String resolved = resolveAlias(base);
                        if (mappings.containsKey(resolved)) {
                            yield Optional.of(resolved);
                        }
                    }
                }
                yield Optional.empty();
            }
            case HYPERLIQUID -> {
                // Hyperliquid uses plain base symbol
                String resolved = resolveAlias(upper);
                yield mappings.containsKey(resolved) ? Optional.of(resolved) : Optional.empty();
            }
        };
    }

    // ========== Result Classes ==========

    /**
     * Result of validation check.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> missingMappings;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> missingMappings, List<String> warnings) {
            this.valid = valid;
            this.missingMappings = Collections.unmodifiableList(missingMappings);
            this.warnings = Collections.unmodifiableList(warnings);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMissingMappings() {
            return missingMappings;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        @Override
        public String toString() {
            if (valid && warnings.isEmpty()) {
                return "ValidationResult{valid=true}";
            }
            return "ValidationResult{" +
                    "valid=" + valid +
                    ", missing=" + missingMappings.size() +
                    ", warnings=" + warnings.size() +
                    '}';
        }
    }
}
