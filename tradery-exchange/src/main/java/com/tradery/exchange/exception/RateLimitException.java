package com.tradery.exchange.exception;

public class RateLimitException extends ExchangeException {

    private final long retryAfterMs;

    public RateLimitException(String message, long retryAfterMs) {
        super(message);
        this.retryAfterMs = retryAfterMs;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}
