package com.tradery.dataservice.api;

import com.tradery.data.page.PageKey;
import com.tradery.dataservice.page.PageManager;
import com.tradery.dataservice.page.PageStatus;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Handler for page lifecycle endpoints.
 */
public class PageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PageHandler.class);

    private final PageManager pageManager;

    public PageHandler(PageManager pageManager) {
        this.pageManager = pageManager;
    }

    /**
     * POST /pages/request
     * Request a data page. Creates if new, returns existing if already loaded.
     */
    public void requestPage(Context ctx) {
        try {
            PageRequest request = ctx.bodyAsClass(PageRequest.class);

            // Convert startTime/endTime to endTime/windowDurationMillis
            PageKey key = new PageKey(
                request.dataType(),
                "binance",  // default exchange for HTTP API
                request.symbol(),
                request.timeframe(),
                "perp",  // default market type for HTTP API
                request.endTime(),  // anchored pages use endTime
                request.endTime() - request.startTime()  // windowDurationMillis
            );

            PageStatus status = pageManager.requestPage(key, request.consumerId(), request.consumerName());

            ctx.json(new PageResponse(
                key.toKeyString(),
                status.state().name(),
                status.progress(),
                status.isNew()
            ));
        } catch (Exception e) {
            LOG.error("Failed to request page", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * POST /pages/batch-request
     * Request multiple pages at once.
     */
    public void batchRequestPages(Context ctx) {
        try {
            BatchPageRequest request = ctx.bodyAsClass(BatchPageRequest.class);
            List<PageResponse> responses = request.requests().stream()
                .map(r -> {
                    // Convert startTime/endTime to endTime/windowDurationMillis
                    PageKey key = new PageKey(r.dataType(), "binance", r.symbol(), r.timeframe(), "perp", r.endTime(), r.endTime() - r.startTime());
                    PageStatus status = pageManager.requestPage(key, request.consumerId(), request.consumerName());
                    return new PageResponse(key.toKeyString(), status.state().name(), status.progress(), status.isNew());
                })
                .toList();

            ctx.json(new BatchPageResponse(responses));
        } catch (Exception e) {
            LOG.error("Failed to batch request pages", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * DELETE /pages/{key}
     * Release a consumer's hold on a page.
     */
    public void releasePage(Context ctx) {
        try {
            String keyString = ctx.pathParam("key");
            String consumerId = ctx.queryParam("consumerId");

            if (consumerId == null) {
                ctx.status(400).json(new ErrorResponse("consumerId is required"));
                return;
            }

            PageKey key = PageKey.fromKeyString(keyString);
            boolean released = pageManager.releasePage(key, consumerId);

            ctx.json(new ReleaseResponse(released));
        } catch (Exception e) {
            LOG.error("Failed to release page", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /pages/{key}/status
     * Get current status of a page.
     */
    public void getPageStatus(Context ctx) {
        try {
            String keyString = ctx.pathParam("key");
            PageKey key = PageKey.fromKeyString(keyString);

            PageStatus status = pageManager.getPageStatus(key);
            if (status == null) {
                ctx.status(404).json(new ErrorResponse("Page not found"));
                return;
            }

            ctx.json(new PageStatusResponse(
                keyString,
                status.state().name(),
                status.progress(),
                status.recordCount(),
                status.lastSyncTime(),
                status.consumers(),
                status.coverage()
            ));
        } catch (Exception e) {
            LOG.error("Failed to get page status", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /pages/{key}/data
     * Get actual data for a page. Returns MessagePack binary.
     */
    public void getPageData(Context ctx) {
        try {
            String keyString = ctx.pathParam("key");
            PageKey key = PageKey.fromKeyString(keyString);

            byte[] data = pageManager.getPageData(key);
            if (data == null) {
                ctx.status(404).json(new ErrorResponse("Page not found or not ready"));
                return;
            }

            ctx.contentType("application/msgpack");
            ctx.result(data);
        } catch (Exception e) {
            LOG.error("Failed to get page data", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /pages/status
     * Get status of all active pages.
     */
    public void getAllPagesStatus(Context ctx) {
        try {
            Map<String, PageStatus> allStatus = pageManager.getAllPageStatus();

            List<PageStatusResponse> responses = allStatus.entrySet().stream()
                .map(e -> new PageStatusResponse(
                    e.getKey(),
                    e.getValue().state().name(),
                    e.getValue().progress(),
                    e.getValue().recordCount(),
                    e.getValue().lastSyncTime(),
                    e.getValue().consumers(),
                    e.getValue().coverage()
                ))
                .toList();

            ctx.json(new AllPagesStatusResponse(responses));
        } catch (Exception e) {
            LOG.error("Failed to get all pages status", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    // Request/Response records
    public record PageRequest(
        String dataType,
        String symbol,
        String timeframe,
        long startTime,
        long endTime,
        String consumerId,
        String consumerName
    ) {}

    public record BatchPageRequest(
        String consumerId,
        String consumerName,
        List<PageRequest> requests
    ) {}

    public record PageResponse(
        String pageKey,
        String state,
        int progress,
        boolean isNew
    ) {}

    public record BatchPageResponse(List<PageResponse> pages) {}

    public record ReleaseResponse(boolean released) {}

    public record PageStatusResponse(
        String pageKey,
        String state,
        int progress,
        long recordCount,
        Long lastSyncTime,
        List<PageStatus.Consumer> consumers,
        PageStatus.Coverage coverage
    ) {}

    public record AllPagesStatusResponse(List<PageStatusResponse> pages) {}

    public record ErrorResponse(String error) {}
}
