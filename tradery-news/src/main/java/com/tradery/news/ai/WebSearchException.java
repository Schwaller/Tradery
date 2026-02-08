package com.tradery.news.ai;

/**
 * Exception thrown when a web search operation fails.
 */
public class WebSearchException extends Exception {

    public WebSearchException(String message) {
        super(message);
    }

    public WebSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
