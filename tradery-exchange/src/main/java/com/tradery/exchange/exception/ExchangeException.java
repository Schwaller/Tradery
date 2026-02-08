package com.tradery.exchange.exception;

public class ExchangeException extends Exception {

    public ExchangeException(String message) {
        super(message);
    }

    public ExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
