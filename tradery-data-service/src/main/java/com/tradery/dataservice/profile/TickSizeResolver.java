package com.tradery.dataservice.profile;

import com.tradery.dataservice.data.sqlite.SymbolsConnection;
import com.tradery.dataservice.data.sqlite.dao.SymbolDao;
import com.tradery.dataservice.symbols.MarketType;
import com.tradery.dataservice.symbols.TradingPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves exchange-defined tick sizes for symbols.
 * Queries the symbols DB and caches results. Falls back to hardcoded
 * values for top symbols if the DB returns 0.
 */
public class TickSizeResolver {

    private static final Logger log = LoggerFactory.getLogger(TickSizeResolver.class);

    private static final Map<String, Double> FALLBACK_TICK_SIZES = Map.ofEntries(
        Map.entry("BTCUSDT", 0.10),
        Map.entry("ETHUSDT", 0.01),
        Map.entry("BNBUSDT", 0.010),
        Map.entry("SOLUSDT", 0.0010),
        Map.entry("XRPUSDT", 0.0001),
        Map.entry("DOGEUSDT", 0.000010),
        Map.entry("ADAUSDT", 0.00010),
        Map.entry("AVAXUSDT", 0.0010),
        Map.entry("DOTUSDT", 0.001),
        Map.entry("LINKUSDT", 0.001),
        Map.entry("MATICUSDT", 0.00010),
        Map.entry("LTCUSDT", 0.01),
        Map.entry("ARBUSDT", 0.00010),
        Map.entry("OPUSDT", 0.0001),
        Map.entry("NEARUSDT", 0.001),
        Map.entry("AAVEUSDT", 0.01),
        Map.entry("UNIUSDT", 0.001),
        Map.entry("ATOMUSDT", 0.001),
        Map.entry("APTUSDT", 0.0010),
        Map.entry("SUIUSDT", 0.00010)
    );

    private final SymbolsConnection symbolsConnection;
    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    public TickSizeResolver(SymbolsConnection symbolsConnection) {
        this.symbolsConnection = symbolsConnection;
    }

    /**
     * Get the tick size for a symbol.
     * Checks cache first, then queries DB, then falls back to hardcoded values.
     *
     * @param symbol Trading symbol (e.g., "BTCUSDT")
     * @return Tick size (e.g., 0.10 for BTCUSDT). Returns 0.01 as last resort.
     */
    public double getTickSize(String symbol) {
        return cache.computeIfAbsent(symbol, this::resolveTickSize);
    }

    private double resolveTickSize(String symbol) {
        // Try DB first
        try {
            SymbolDao dao = new SymbolDao(symbolsConnection);
            // Try perp first (most common for trading)
            Optional<TradingPair> pair = dao.reverseResolve(symbol, "binance");
            if (pair.isPresent() && pair.get().tickSize() > 0) {
                log.debug("Resolved tick size for {} from DB: {}", symbol, pair.get().tickSize());
                return pair.get().tickSize();
            }
        } catch (SQLException e) {
            log.warn("Failed to query tick size for {}: {}", symbol, e.getMessage());
        }

        // Fallback to hardcoded
        Double fallback = FALLBACK_TICK_SIZES.get(symbol.toUpperCase());
        if (fallback != null) {
            log.debug("Using fallback tick size for {}: {}", symbol, fallback);
            return fallback;
        }

        // Last resort
        log.warn("No tick size found for {}, using default 0.01", symbol);
        return 0.01;
    }
}
