package com.tradery.ai;

/**
 * Listener for AI activity events.
 * Used to decouple AI logging from specific UI implementations.
 */
@FunctionalInterface
public interface AiActivityListener {
    void onActivity(String summary, String prompt, String response);
}
