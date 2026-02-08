package com.tradery.exchange.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MarginMode {
    CROSS("cross"),
    ISOLATED("isolated");

    private final String value;

    MarginMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
