package com.tradery.exchange.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {
    PENDING("pending"),
    OPEN("open"),
    PARTIALLY_FILLED("partially_filled"),
    FILLED("filled"),
    CANCELLED("cancelled"),
    REJECTED("rejected");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
