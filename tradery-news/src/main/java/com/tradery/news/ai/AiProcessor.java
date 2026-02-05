package com.tradery.news.ai;

import com.tradery.news.model.Article;

/**
 * Interface for AI-powered article processing.
 */
public interface AiProcessor {

    /**
     * Process an article and extract structured data.
     */
    ExtractionResult process(Article article);

    /**
     * Check if this processor is available/configured.
     */
    boolean isAvailable();
}
