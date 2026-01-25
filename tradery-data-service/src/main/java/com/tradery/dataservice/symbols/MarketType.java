package com.tradery.dataservice.symbols;

/**
 * Market type for trading pairs.
 * First-class dimension in symbol resolution.
 */
public enum MarketType {
    SPOT("spot"),
    PERP("perp");

    private final String value;

    MarketType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MarketType fromString(String value) {
        if (value == null) {
            return PERP; // Default
        }
        return switch (value.toLowerCase()) {
            case "spot" -> SPOT;
            case "perp", "perpetual", "futures", "swap" -> PERP;
            default -> PERP;
        };
    }
}
