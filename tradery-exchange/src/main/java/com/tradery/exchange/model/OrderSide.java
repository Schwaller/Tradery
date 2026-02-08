package com.tradery.exchange.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderSide {
    BUY("buy"),
    SELL("sell");

    private final String value;

    OrderSide(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
