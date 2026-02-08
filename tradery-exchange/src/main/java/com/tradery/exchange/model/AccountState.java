package com.tradery.exchange.model;

import java.time.Instant;

public record AccountState(
    double balance,
    double equity,
    double availableMargin,
    double usedMargin,
    double unrealizedPnl,
    double withdrawable,
    String currency,
    Instant timestamp
) {
    public static AccountState empty(String currency) {
        return new AccountState(0, 0, 0, 0, 0, 0, currency, Instant.now());
    }
}
