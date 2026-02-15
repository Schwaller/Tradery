package com.tradery.ai.pipeline.step;

import com.tradery.ai.DuckDuckGoSearchProvider;
import com.tradery.ai.WebSearchException;
import com.tradery.ai.WebSearchProvider;
import com.tradery.ai.pipeline.schema.RelationshipTypeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs web research via DuckDuckGo using search hints from the request's
 * relationship type descriptors. Stores results in context for the next QueryStep.
 */
class WebResearchStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(WebResearchStep.class);

    @Override
    public StepResult execute(StepContext context) {
        var request = context.request();
        String entityName = request.entity().name();
        String sourceTypeId = request.entity().typeId();

        // Generate queries from relationship type search hints
        List<String> queries = new ArrayList<>();
        if (request.relationshipTypes() != null) {
            for (RelationshipTypeDescriptor relType : request.relationshipTypes()) {
                queries.addAll(relType.searchHintsFor(entityName, sourceTypeId));
            }
        }

        if (queries.isEmpty()) {
            // Generic fallback
            queries.add(entityName + " related entities ecosystem");
        }

        // Limit to a reasonable number of queries
        if (queries.size() > 5) {
            queries = queries.subList(0, 5);
        }

        WebSearchProvider webSearch = new DuckDuckGoSearchProvider();
        List<WebSearchProvider.SearchResult> allResults = new ArrayList<>();

        for (String query : queries) {
            context.notifyProgress(name(), "Searching: " + query, 0.05);
            try {
                List<WebSearchProvider.SearchResult> results = webSearch.search(query, 8);
                allResults.addAll(results);
                log.debug("WebResearch '{}': {} results", query, results.size());
            } catch (WebSearchException e) {
                log.warn("Web search failed for '{}': {}", query, e.getMessage());
            }
        }

        if (allResults.isEmpty()) {
            return StepResult.failContinue("No web research results found");
        }

        context.setWebResearchContext(allResults);
        String msg = allResults.size() + " results from " + queries.size() + " queries";
        context.notifyProgress(name(), msg, 0.1);
        return StepResult.success(msg);
    }

    @Override
    public String name() { return "web-research"; }
}
