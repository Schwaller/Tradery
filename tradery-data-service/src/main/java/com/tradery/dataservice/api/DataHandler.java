package com.tradery.dataservice.api;

import com.tradery.dataservice.page.PageManager;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for direct data access endpoints.
 * These are simpler endpoints for one-off data requests without page lifecycle.
 */
public class DataHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DataHandler.class);

    private final PageManager pageManager;

    public DataHandler(PageManager pageManager) {
        this.pageManager = pageManager;
    }

    /**
     * GET /candles?symbol=X&timeframe=Y&start=Z&end=W
     */
    public void getCandles(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String timeframe = ctx.queryParam("timeframe");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);

            if (symbol == null || timeframe == null) {
                ctx.status(400).json(new ErrorResponse("symbol and timeframe are required"));
                return;
            }

            byte[] data = pageManager.getCandlesData(symbol, timeframe, start, end);
            if (data == null) {
                ctx.status(404).json(new ErrorResponse("No data available"));
                return;
            }

            ctx.contentType("application/msgpack");
            ctx.result(data);
        } catch (Exception e) {
            LOG.error("Failed to get candles", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
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

            byte[] data = pageManager.getAggTradesData(symbol, start, end);
            if (data == null) {
                ctx.status(404).json(new ErrorResponse("No data available"));
                return;
            }

            ctx.contentType("application/msgpack");
            ctx.result(data);
        } catch (Exception e) {
            LOG.error("Failed to get aggTrades", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /funding?symbol=X&start=Z&end=W
     */
    public void getFunding(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);

            if (symbol == null) {
                ctx.status(400).json(new ErrorResponse("symbol is required"));
                return;
            }

            byte[] data = pageManager.getFundingData(symbol, start, end);
            if (data == null) {
                ctx.status(404).json(new ErrorResponse("No data available"));
                return;
            }

            ctx.contentType("application/msgpack");
            ctx.result(data);
        } catch (Exception e) {
            LOG.error("Failed to get funding", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /openinterest?symbol=X&start=Z&end=W
     */
    public void getOpenInterest(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);

            if (symbol == null) {
                ctx.status(400).json(new ErrorResponse("symbol is required"));
                return;
            }

            byte[] data = pageManager.getOpenInterestData(symbol, start, end);
            if (data == null) {
                ctx.status(404).json(new ErrorResponse("No data available"));
                return;
            }

            ctx.contentType("application/msgpack");
            ctx.result(data);
        } catch (Exception e) {
            LOG.error("Failed to get open interest", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /premium?symbol=X&timeframe=Y&start=Z&end=W
     */
    public void getPremium(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String timeframe = ctx.queryParamAsClass("timeframe", String.class).getOrDefault("1h");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);

            if (symbol == null) {
                ctx.status(400).json(new ErrorResponse("symbol is required"));
                return;
            }

            byte[] data = pageManager.getPremiumData(symbol, timeframe, start, end);
            if (data == null) {
                ctx.status(404).json(new ErrorResponse("No data available"));
                return;
            }

            ctx.contentType("application/msgpack");
            ctx.result(data);
        } catch (Exception e) {
            LOG.error("Failed to get premium", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    public record ErrorResponse(String error) {}
}
