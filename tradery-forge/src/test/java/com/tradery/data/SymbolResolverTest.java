package com.tradery.data;

import com.tradery.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SymbolResolver.
 */
class SymbolResolverTest {

    private SymbolResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SymbolResolver();
    }

    @Nested
    @DisplayName("Explicit Mapping Tests")
    class ExplicitMappingTests {

        @Test
        @DisplayName("Should resolve explicit mapping for BTC/Binance/perp/USDT")
        void resolvesExplicitMapping() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When
            String result = resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);

            // Then
            assertEquals("BTCUSDT", result);
        }

        @Test
        @DisplayName("Should resolve OKX swap format")
        void resolvesOkxSwapFormat() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.OKX, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTC-USDT-SWAP");
            resolver.addMapping(btc);

            // When
            String result = resolver.resolve("BTC", Exchange.OKX, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);

            // Then
            assertEquals("BTC-USDT-SWAP", result);
        }

        @Test
        @DisplayName("Should resolve different quote currencies")
        void resolvesDifferentQuoteCurrencies() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDC, "BTCUSDC");
            resolver.addMapping(btc);

            // When/Then
            assertEquals("BTCUSDT", resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT));
            assertEquals("BTCUSDC", resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDC));
        }

        @Test
        @DisplayName("Should resolve spot vs perp for same exchange")
        void resolvesSpotVsPerp() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.OKX, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTC-USDT-SWAP");
            btc.setSymbol(Exchange.OKX, DataMarketType.SPOT, QuoteCurrency.USDT, "BTC-USDT");
            resolver.addMapping(btc);

            // When/Then
            assertEquals("BTC-USDT-SWAP", resolver.resolve("BTC", Exchange.OKX, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT));
            assertEquals("BTC-USDT", resolver.resolve("BTC", Exchange.OKX, DataMarketType.SPOT, QuoteCurrency.USDT));
        }
    }

    @Nested
    @DisplayName("Alias Resolution Tests")
    class AliasResolutionTests {

        @Test
        @DisplayName("Should resolve alias XBT to BTC")
        void resolvesAliasToBTC() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setAliases(List.of("XBT"));
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When
            String result = resolver.resolve("XBT", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);

            // Then
            assertEquals("BTCUSDT", result);
        }

        @Test
        @DisplayName("Should be case-insensitive for aliases")
        void aliasIsCaseInsensitive() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setAliases(List.of("XBT"));
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When/Then
            assertEquals("BTCUSDT", resolver.resolve("xbt", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT));
            assertEquals("BTCUSDT", resolver.resolve("Xbt", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT));
        }

        @Test
        @DisplayName("Should resolve canonical symbol directly")
        void resolveAliasReturnsCanonical() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setAliases(List.of("XBT"));
            resolver.addMapping(btc);

            // When/Then
            assertEquals("BTC", resolver.resolveAlias("XBT"));
            assertEquals("BTC", resolver.resolveAlias("BTC"));
            assertEquals("UNKNOWN", resolver.resolveAlias("UNKNOWN")); // Returns as-is if not found
        }
    }

    @Nested
    @DisplayName("Strict Mode Tests")
    class StrictModeTests {

        @Test
        @DisplayName("Should throw when mapping not found in strict mode")
        void throwsWhenMappingNotFound() {
            // Given - no mappings added, derivation disabled by default

            // When/Then
            SymbolResolutionException ex = assertThrows(
                SymbolResolutionException.class,
                () -> resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT)
            );

            assertEquals("BTC", ex.getCanonicalSymbol());
            assertEquals(Exchange.BINANCE, ex.getExchange());
            assertEquals(DataMarketType.FUTURES_PERP, ex.getMarketType());
            assertEquals(QuoteCurrency.USDT, ex.getQuoteCurrency());
        }

        @Test
        @DisplayName("Should throw when specific combination not mapped")
        void throwsWhenCombinationNotMapped() {
            // Given - BTC mapped for perp but not spot
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When/Then - SPOT not mapped
            assertThrows(
                SymbolResolutionException.class,
                () -> resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.SPOT, QuoteCurrency.USDT)
            );
        }

        @Test
        @DisplayName("tryResolve should return null instead of throwing")
        void tryResolveReturnsNull() {
            // Given - no mappings

            // When
            String result = resolver.tryResolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Derivation Fallback Tests")
    class DerivationFallbackTests {

        @Test
        @DisplayName("Should derive symbol when derivation enabled")
        void derivesSymbolWhenEnabled() {
            // Given
            resolver.setDerivationEnabled(true);
            // Add empty mapping to register BTC as known symbol
            resolver.addMapping(new SymbolMapping("BTC"));

            // When
            String binanceResult = resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);
            String okxResult = resolver.resolve("BTC", Exchange.OKX, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);

            // Then
            assertEquals("BTCUSDT", binanceResult);
            assertEquals("BTC-USDT-SWAP", okxResult);
        }

        @Test
        @DisplayName("Should derive OKX spot format correctly")
        void derivesOkxSpotFormat() {
            // Given
            resolver.setDerivationEnabled(true);
            resolver.addMapping(new SymbolMapping("ETH"));

            // When
            String result = resolver.resolve("ETH", Exchange.OKX, DataMarketType.SPOT, QuoteCurrency.USDT);

            // Then
            assertEquals("ETH-USDT", result);
        }

        @Test
        @DisplayName("Should use custom template when set")
        void usesCustomTemplate() {
            // Given
            resolver.setDerivationEnabled(true);
            resolver.setDerivationTemplate("binance", "{BASE}_{QUOTE}");
            resolver.addMapping(new SymbolMapping("BTC"));

            // When
            String result = resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);

            // Then
            assertEquals("BTC_USDT", result);
        }

        @Test
        @DisplayName("Explicit mapping takes precedence over derivation")
        void explicitMappingTakesPrecedence() {
            // Given
            resolver.setDerivationEnabled(true);
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "CUSTOM_BTC");
            resolver.addMapping(btc);

            // When
            String result = resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT);

            // Then
            assertEquals("CUSTOM_BTC", result);
        }
    }

    @Nested
    @DisplayName("Reverse Resolution Tests")
    class ReverseResolutionTests {

        @Test
        @DisplayName("Should reverse resolve Binance symbol")
        void reverseResolveBinance() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When
            Optional<String> result = resolver.reverseResolve("BTCUSDT", Exchange.BINANCE);

            // Then
            assertTrue(result.isPresent());
            assertEquals("BTC", result.get());
        }

        @Test
        @DisplayName("Should reverse resolve OKX swap symbol")
        void reverseResolveOkx() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.OKX, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTC-USDT-SWAP");
            resolver.addMapping(btc);

            // When
            Optional<String> result = resolver.reverseResolve("BTC-USDT-SWAP", Exchange.OKX);

            // Then
            assertTrue(result.isPresent());
            assertEquals("BTC", result.get());
        }

        @Test
        @DisplayName("Should return empty for unknown symbol")
        void reverseResolveReturnsEmptyForUnknown() {
            // Given - no mappings

            // When
            Optional<String> result = resolver.reverseResolve("UNKNOWN", Exchange.BINANCE);

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle alias in reverse resolution via pattern matching")
        void reverseResolveWithAlias() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setAliases(List.of("XBT"));
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When - try to resolve a symbol that matches pattern
            Optional<String> result = resolver.reverseResolve("BTCUSD", Exchange.BINANCE);

            // Then - should find BTC via pattern matching
            assertTrue(result.isPresent());
            assertEquals("BTC", result.get());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should report missing mappings in strict mode")
        void reportsMissingMappings() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            // Missing: BYBIT perp USDT
            resolver.addMapping(btc);

            // When
            SymbolResolver.ValidationResult result = resolver.validate(
                List.of(Exchange.BINANCE, Exchange.BYBIT),
                List.of(DataMarketType.FUTURES_PERP),
                List.of(QuoteCurrency.USDT)
            );

            // Then
            assertFalse(result.isValid());
            assertTrue(result.getMissingMappings().stream()
                .anyMatch(m -> m.contains("BTC") && m.contains("bybit")));
        }

        @Test
        @DisplayName("Should pass validation when all mappings present")
        void passesWhenComplete() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            btc.setSymbol(Exchange.BYBIT, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When
            SymbolResolver.ValidationResult result = resolver.validate(
                List.of(Exchange.BINANCE, Exchange.BYBIT),
                List.of(DataMarketType.FUTURES_PERP),
                List.of(QuoteCurrency.USDT)
            );

            // Then
            assertTrue(result.isValid());
            assertTrue(result.getMissingMappings().isEmpty());
        }

        @Test
        @DisplayName("Should warn about derivation when enabled")
        void warnsAboutDerivation() {
            // Given
            resolver.setDerivationEnabled(true);
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            // Missing BYBIT - will use derivation
            resolver.addMapping(btc);

            // When
            SymbolResolver.ValidationResult result = resolver.validate(
                List.of(Exchange.BINANCE, Exchange.BYBIT),
                List.of(DataMarketType.FUTURES_PERP),
                List.of(QuoteCurrency.USDT)
            );

            // Then
            assertTrue(result.isValid()); // Valid because derivation enabled
            assertFalse(result.getWarnings().isEmpty());
            assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("BTC") && w.contains("derivation")));
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Resolve with only exchange uses defaults (perp/USDT)")
        void resolveWithDefaultsUsesPerp() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When
            String result = resolver.resolve("BTC", Exchange.BINANCE);

            // Then
            assertEquals("BTCUSDT", result);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null parameters")
        void handlesNullParameters() {
            assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(null, Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT));
            assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("BTC", null, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT));
            assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("BTC", Exchange.BINANCE, null, QuoteCurrency.USDT));
            assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("BTC", Exchange.BINANCE, DataMarketType.FUTURES_PERP, null));
        }

        @Test
        @DisplayName("Should clear all data")
        void clearRemovesAllData() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setAliases(List.of("XBT"));
            btc.setSymbol(Exchange.BINANCE, DataMarketType.FUTURES_PERP, QuoteCurrency.USDT, "BTCUSDT");
            resolver.addMapping(btc);

            // When
            resolver.clear();

            // Then
            assertFalse(resolver.hasSymbol("BTC"));
            assertFalse(resolver.hasSymbol("XBT"));
            assertTrue(resolver.getCanonicalSymbols().isEmpty());
        }

        @Test
        @DisplayName("Should check symbol existence with aliases")
        void hasSymbolIncludesAliases() {
            // Given
            SymbolMapping btc = new SymbolMapping("BTC");
            btc.setAliases(List.of("XBT"));
            resolver.addMapping(btc);

            // Then
            assertTrue(resolver.hasSymbol("BTC"));
            assertTrue(resolver.hasSymbol("XBT"));
            assertTrue(resolver.hasSymbol("btc")); // Case-insensitive
            assertFalse(resolver.hasSymbol("ETH"));
        }
    }
}
