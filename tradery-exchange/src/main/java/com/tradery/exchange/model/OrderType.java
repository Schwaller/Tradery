package com.tradery.exchange.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderType {
    MARKET("market"),
    LIMIT("limit"),
    STOP_MARKET("stop_market"),
    STOP_LIMIT("stop_limit"),
    TRAILING("trailing");

    private final String value;

    OrderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
