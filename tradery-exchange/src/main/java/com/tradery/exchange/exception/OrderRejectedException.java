package com.tradery.exchange.exception;

public class OrderRejectedException extends ExchangeException {

    private final String rejectReason;

    public OrderRejectedException(String message, String rejectReason) {
        super(message);
        this.rejectReason = rejectReason;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
