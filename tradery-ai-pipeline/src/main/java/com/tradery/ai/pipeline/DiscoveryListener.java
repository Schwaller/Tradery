package com.tradery.ai.pipeline;

/**
 * Callback for pipeline progress notifications.
 */
@FunctionalInterface
public interface DiscoveryListener {
    void onProgress(String stepName, String message, double progressFraction);
}
