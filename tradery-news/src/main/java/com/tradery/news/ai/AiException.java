package com.tradery.news.ai;

/**
 * Exception thrown by AiClient for AI-related errors.
 */
public class AiException extends Exception {

    public enum ErrorType {
        NOT_FOUND,       // CLI not found at configured path
        NOT_LOGGED_IN,   // Authentication required
        API_KEY_MISSING, // API key not configured
        TIMEOUT,         // Request timed out
        UNKNOWN          // Other errors
    }

    private final ErrorType type;

    public AiException(ErrorType type, String message) {
        super(message);
        this.type = type;
    }

    public AiException(ErrorType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public ErrorType getType() {
        return type;
    }

    /**
     * Check if this error suggests the user needs to authenticate.
     */
    public boolean requiresAuthentication() {
        return type == ErrorType.NOT_LOGGED_IN || type == ErrorType.API_KEY_MISSING;
    }
}
