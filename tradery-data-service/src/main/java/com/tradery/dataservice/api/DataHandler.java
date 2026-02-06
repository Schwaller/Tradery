package com.tradery.dataservice.api;

import com.tradery.dataservice.page.PageManager;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for direct data access endpoints.
 * Only aggTrades needs a direct endpoint (page system returns null for aggTrades
 * because the data is too large for in-memory pages; it must be streamed).
 */
public class DataHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DataHandler.class);

    private final PageManager pageManager;

    public DataHandler(PageManager pageManager) {
        this.pageManager = pageManager;
    }

    /**
     * GET /aggtrades?symbol=X&start=Z&end=W
     */
    public void getAggTrades(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);

            if (symbol == null) {
                ctx.status(400).json(new ErrorResponse("symbol is required"));
                return;
            }

            ctx.contentType("application/msgpack");
            int count = pageManager.writeAggTradesData(symbol, start, end, ctx.outputStream());
            if (count < 0) {
                // Stream may have already started — log the error
                LOG.error("Failed to stream aggTrades for {}", symbol);
            }
        } catch (Exception e) {
            LOG.error("Failed to get aggTrades", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    public record ErrorResponse(String error) {}
}
