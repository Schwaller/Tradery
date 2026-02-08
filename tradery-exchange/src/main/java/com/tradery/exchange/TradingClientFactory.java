package com.tradery.exchange;

import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.hyperliquid.HyperliquidClient;
import com.tradery.exchange.model.TradingConfig;

public class TradingClientFactory {

    public static TradingClient create(TradingConfig config) throws ExchangeException {
        if (config.getPaperTrading().isEnabled()) {
            return new PaperTradingClient(config);
        }

        String venue = config.getActiveVenue();
        TradingConfig.VenueConfig venueConfig = config.getActiveVenueConfig();

        if (venueConfig == null) {
            throw new ExchangeException("No configuration found for venue: " + venue);
        }
        if (!venueConfig.isEnabled()) {
            throw new ExchangeException("Venue is not enabled: " + venue);
        }

        return switch (venue) {
            case "hyperliquid" -> new HyperliquidClient(venueConfig);
            default -> throw new ExchangeException("Unsupported venue: " + venue);
        };
    }
}
