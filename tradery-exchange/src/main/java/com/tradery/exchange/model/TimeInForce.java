package com.tradery.exchange.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeInForce {
    GTC("gtc"),
    IOC("ioc"),
    ALO("alo");   // Add Liquidity Only (post-only)

    private final String value;

    TimeInForce(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
