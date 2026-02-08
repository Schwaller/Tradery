package com.tradery.dataservice.api;

import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.dao.CoverageDao;
import com.tradery.dataservice.page.PageManager;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handler for data coverage endpoints.
 */
public class CoverageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CoverageHandler.class);

    private final PageManager pageManager;
    private final SqliteDataStore dataStore;

    public CoverageHandler(PageManager pageManager, SqliteDataStore dataStore) {
        this.pageManager = pageManager;
        this.dataStore = dataStore;
    }

    /**
     * GET /coverage?symbol=X&dataType=Y
     * Get coverage information for a symbol/data type.
     */
    public void getCoverage(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String dataType = ctx.queryParam("dataType");

            if (symbol == null || dataType == null) {
                ctx.status(400).json(new ErrorResponse("symbol and dataType are required"));
                return;
            }

            CoverageInfo coverage = pageManager.getCoverage(symbol, dataType);
            if (coverage == null) {
                ctx.json(new CoverageInfo(symbol, dataType, null, null, List.of(), 0));
                return;
            }

            ctx.json(coverage);
        } catch (Exception e) {
            LOG.error("Failed to get coverage", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /coverage/symbols
     * Get list of symbols with available data.
     */
    public void getAvailableSymbols(Context ctx) {
        try {
            List<SymbolInfo> symbols = pageManager.getAvailableSymbols();
            ctx.json(new SymbolsResponse(symbols));
        } catch (Exception e) {
            LOG.error("Failed to get available symbols", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /coverage/ranges?symbol=X&dataType=Y&subKey=Z
     * Get raw coverage ranges for a symbol/data type.
     * Returns list of time ranges with complete/partial status.
     */
    public void getCoverageRanges(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String dataType = ctx.queryParam("dataType");
            String subKey = ctx.queryParamAsClass("subKey", String.class).getOrDefault("default");

            if (symbol == null || dataType == null) {
                ctx.status(400).json(new ErrorResponse("symbol and dataType are required"));
                return;
            }

            CoverageDao dao = dataStore.forSymbol(symbol).coverage();
            List<CoverageDao.CoverageRange> ranges = dao.getCoverageRanges(dataType, subKey);

            List<CoverageRangeResponse> response = ranges.stream()
                .map(r -> new CoverageRangeResponse(r.rangeStart(), r.rangeEnd(), r.isComplete()))
                .toList();

            ctx.json(new CoverageRangesResponse(response));
        } catch (Exception e) {
            LOG.error("Failed to get coverage ranges", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    // Response records
    public record CoverageInfo(
        String symbol,
        String dataType,
        Long earliestTime,
        Long latestTime,
        List<GapInfo> gaps,
        long totalRecords
    ) {}

    public record GapInfo(long startTime, long endTime) {}

    public record SymbolInfo(
        String symbol,
        boolean hasCandles,
        boolean hasAggTrades,
        boolean hasFunding,
        boolean hasOpenInterest,
        boolean hasPremium
    ) {}

    public record SymbolsResponse(List<SymbolInfo> symbols) {}

    public record CoverageRangeResponse(long rangeStart, long rangeEnd, boolean isComplete) {}

    public record CoverageRangesResponse(List<CoverageRangeResponse> ranges) {}

    public record ErrorResponse(String error) {}
}
