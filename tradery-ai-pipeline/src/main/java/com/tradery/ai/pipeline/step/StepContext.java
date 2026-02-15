package com.tradery.ai.pipeline.step;

import com.tradery.ai.AiClient;
import com.tradery.ai.WebSearchProvider;
import com.tradery.ai.pipeline.DiscoveryListener;
import com.tradery.ai.pipeline.DiscoveryRequest;
import com.tradery.ai.pipeline.config.TierResolver;
import com.tradery.ai.pipeline.schema.DiscoveredEntity;
import com.tradery.ai.pipeline.schema.SchemaSuggestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable context that flows through pipeline steps.
 * Each step reads from and writes to this context.
 */
public class StepContext {

    // -- Immutable inputs --
    private final DiscoveryRequest request;
    private final TierResolver tierResolver;
    private final AiClient aiClient;
    private final DiscoveryListener listener;

    // -- Mutable state --
    private String lastRawResponse;
    private List<DiscoveredEntity> currentEntities = new ArrayList<>();
    private List<DiscoveredEntity> previousAttemptEntities;
    private int attemptNumber = 1;
    private List<WebSearchProvider.SearchResult> webResearchContext;

    // -- Tracking --
    private final List<StepMetadata> stepMetadata = new ArrayList<>();
    private boolean escalated;
    private boolean salvaged;
    private int challengeFilteredCount;
    private boolean qualityGateFailed;
    private final List<SchemaSuggestion> schemaSuggestions = new ArrayList<>();

    public StepContext(DiscoveryRequest request, TierResolver tierResolver,
                       AiClient aiClient, DiscoveryListener listener) {
        this.request = request;
        this.tierResolver = tierResolver;
        this.aiClient = aiClient;
        this.listener = listener;
    }

    // -- Inputs --
    public DiscoveryRequest request() { return request; }
    public TierResolver tierResolver() { return tierResolver; }
    public AiClient aiClient() { return aiClient; }

    // -- Listener --
    public void notifyProgress(String stepName, String message, double fraction) {
        if (listener != null) {
            listener.onProgress(stepName, message, fraction);
        }
    }

    // -- Mutable state accessors --
    public String lastRawResponse() { return lastRawResponse; }
    public void setLastRawResponse(String response) { this.lastRawResponse = response; }

    public List<DiscoveredEntity> currentEntities() { return currentEntities; }
    public void setCurrentEntities(List<DiscoveredEntity> entities) { this.currentEntities = entities; }

    public List<DiscoveredEntity> previousAttemptEntities() { return previousAttemptEntities; }
    public void setPreviousAttemptEntities(List<DiscoveredEntity> entities) { this.previousAttemptEntities = entities; }

    public int attemptNumber() { return attemptNumber; }
    public void incrementAttempt() { this.attemptNumber++; }

    public List<WebSearchProvider.SearchResult> webResearchContext() { return webResearchContext; }
    public void setWebResearchContext(List<WebSearchProvider.SearchResult> results) { this.webResearchContext = results; }

    // -- Tracking --
    public List<StepMetadata> stepMetadata() { return stepMetadata; }
    public void addStepMetadata(StepMetadata metadata) { stepMetadata.add(metadata); }

    public boolean isEscalated() { return escalated; }
    public void setEscalated(boolean escalated) { this.escalated = escalated; }

    public boolean isSalvaged() { return salvaged; }
    public void setSalvaged(boolean salvaged) { this.salvaged = salvaged; }

    public int challengeFilteredCount() { return challengeFilteredCount; }
    public void setChallengeFilteredCount(int count) { this.challengeFilteredCount = count; }

    public boolean isQualityGateFailed() { return qualityGateFailed; }
    public void setQualityGateFailed(boolean failed) { this.qualityGateFailed = failed; }

    public List<SchemaSuggestion> schemaSuggestions() { return schemaSuggestions; }
    public void addSchemaSuggestion(SchemaSuggestion suggestion) { schemaSuggestions.add(suggestion); }
}
