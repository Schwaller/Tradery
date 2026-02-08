package com.tradery.forge.data;

import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Factory for creating and managing ExchangeClient instances.
 * Caches clients for reuse and provides convenient access methods.
 */
public class ExchangeClientFactory {

    private static final Logger log = LoggerFactory.getLogger(ExchangeClientFactory.class);

    private static ExchangeClientFactory instance;

    private final Map<Exchange, ExchangeClient> clients = new EnumMap<>(Exchange.class);
    private final ExchangeConfig config;

    private ExchangeClientFactory() {
        this.config = ExchangeConfig.getInstance();
        initializeClients();
    }

    public static synchronized ExchangeClientFactory getInstance() {
        if (instance == null) {
            instance = new ExchangeClientFactory();
        }
        return instance;
    }

    /**
     * Initialize clients for all enabled exchanges.
     */
    private void initializeClients() {
        for (Exchange exchange : Exchange.values()) {
            if (config.isEnabled(exchange)) {
                ExchangeClient client = createClient(exchange);
                if (client != null) {
                    clients.put(exchange, client);
                    log.info("Initialized client for {}", exchange.getDisplayName());
                }
            }
        }
    }

    /**
     * Create a client for a specific exchange.
     */
    private ExchangeClient createClient(Exchange exchange) {
        ExchangeConfig.ExchangeSettings settings = config.getExchangeSettings(exchange);
        DataMarketType defaultMarket = settings.markets.isEmpty()
            ? DataMarketType.FUTURES_PERP
            : settings.markets.get(0);

        return switch (exchange) {
            case BINANCE -> new BinanceExchangeClient(defaultMarket);
            case BYBIT -> new BybitExchangeClient(defaultMarket);
            case OKX -> new OkxExchangeClient(defaultMarket);
            case COINBASE -> null;
            case KRAKEN -> null;
            case BITFINEX -> null;
        };
    }

    /**
     * Get client for a specific exchange.
     *
     * @param exchange The exchange
     * @return The client, or null if exchange is not enabled/supported
     */
    public ExchangeClient getClient(Exchange exchange) {
        return clients.get(exchange);
    }

    /**
     * Get all enabled exchange clients.
     */
    public List<ExchangeClient> getEnabledClients() {
        List<ExchangeClient> enabled = new ArrayList<>();
        for (Exchange exchange : config.getEnabledExchanges()) {
            ExchangeClient client = clients.get(exchange);
            if (client != null) {
                enabled.add(client);
            }
        }
        return enabled;
    }

    /**
     * Get the primary (highest priority) exchange client.
     */
    public ExchangeClient getPrimaryClient() {
        Exchange primary = config.getPrimaryExchange();
        return clients.get(primary);
    }

    /**
     * Check if a client is available for an exchange.
     */
    public boolean hasClient(Exchange exchange) {
        return clients.containsKey(exchange);
    }

    /**
     * Get set of exchanges with available clients.
     */
    public Set<Exchange> getAvailableExchanges() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    /**
     * Reload clients (e.g., after config change).
     */
    public void reload() {
        clients.clear();
        config.reload();
        initializeClients();
    }
}
