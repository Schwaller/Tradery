package com.tradery.exchange.hyperliquid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hyperliquid Info API — read-only queries (no authentication required).
 * Endpoint: POST /info
 */
public class HyperliquidInfoApi {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidInfoApi.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public HyperliquidInfoApi(OkHttpClient httpClient, boolean testnet) {
        this.httpClient = httpClient;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.baseUrl = testnet
                ? "https://api.hyperliquid-testnet.xyz/info"
                : "https://api.hyperliquid.xyz/info";
    }

    /**
     * Get asset metadata (universe).
     * POST /info { "type": "meta" }
     */
    public List<AssetInfo> getMeta() throws ExchangeException {
        JsonNode response = post(mapper.createObjectNode().put("type", "meta"));
        JsonNode universe = response.get("universe");
        if (universe == null || !universe.isArray()) {
            throw new ExchangeException("Invalid meta response: missing universe");
        }

        List<AssetInfo> assets = new ArrayList<>();
        for (int i = 0; i < universe.size(); i++) {
            JsonNode asset = universe.get(i);
            assets.add(new AssetInfo(
                    asset.get("name").asText(),
                    asset.get("name").asText(),
                    "USD",
                    asset.get("szDecimals").asInt(),
                    asset.has("pxDecimals") ? asset.get("pxDecimals").asInt() : 6,
                    0, // minOrderSize — Hyperliquid determines dynamically
                    asset.has("maxLeverage") ? asset.get("maxLeverage").asInt() : 50,
                    true,
                    i
            ));
        }
        return assets;
    }

    /**
     * Get clearinghouse state (account, positions).
     * POST /info { "type": "clearinghouseState", "user": "0x..." }
     */
    public ClearinghouseState getClearinghouseState(String userAddress) throws ExchangeException {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", "clearinghouseState");
        body.put("user", userAddress);

        JsonNode response = post(body);

        // Parse margin summary
        JsonNode marginSummary = response.get("marginSummary");
        double accountValue = marginSummary.get("accountValue").asDouble();
        double totalMarginUsed = marginSummary.get("totalMarginUsed").asDouble();
        double totalNtlPos = marginSummary.get("totalNtlPos").asDouble();
        double totalRawUsd = marginSummary.get("totalRawUsd").asDouble();

        // Parse withdrawable
        double withdrawable = response.has("withdrawable") ? response.get("withdrawable").asDouble() : 0;

        // Parse positions
        JsonNode positionsNode = response.get("assetPositions");
        List<ExchangePosition> positions = new ArrayList<>();
        if (positionsNode != null && positionsNode.isArray()) {
            for (JsonNode posNode : positionsNode) {
                JsonNode pos = posNode.get("position");
                if (pos == null) continue;

                String symbol = pos.get("coin").asText();
                double szi = pos.get("szi").asDouble();
                if (szi == 0) continue;

                OrderSide side = szi > 0 ? OrderSide.BUY : OrderSide.SELL;
                double entryPx = pos.get("entryPx").asDouble();
                double positionValue = pos.has("positionValue") ? pos.get("positionValue").asDouble() : 0;
                double unrealizedPnl = pos.has("unrealizedPnl") ? pos.get("unrealizedPnl").asDouble() : 0;
                double returnOnEquity = pos.has("returnOnEquity") ? pos.get("returnOnEquity").asDouble() : 0;
                int leverage = pos.has("leverage") ? pos.get("leverage").get("value").asInt() : 1;
                String leverageType = pos.has("leverage") ? pos.get("leverage").get("type").asText() : "cross";
                double liqPx = pos.has("liquidationPx") && !pos.get("liquidationPx").isNull()
                        ? pos.get("liquidationPx").asDouble() : 0;
                double marginUsed = pos.has("marginUsed") ? pos.get("marginUsed").asDouble() : 0;

                MarginMode marginMode = "isolated".equals(leverageType) ? MarginMode.ISOLATED : MarginMode.CROSS;

                positions.add(new ExchangePosition(
                        symbol, side, Math.abs(szi), entryPx, 0, // markPrice filled separately
                        unrealizedPnl, 0, leverage, marginMode, liqPx, marginUsed, Instant.now()));
            }
        }

        return new ClearinghouseState(accountValue, totalMarginUsed, totalNtlPos,
                totalRawUsd, withdrawable, positions);
    }

    /**
     * Get open orders for a user.
     * POST /info { "type": "openOrders", "user": "0x..." }
     */
    public List<OrderResponse> getOpenOrders(String userAddress) throws ExchangeException {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", "openOrders");
        body.put("user", userAddress);

        JsonNode response = post(body);
        List<OrderResponse> orders = new ArrayList<>();

        if (response.isArray()) {
            for (JsonNode orderNode : response) {
                orders.add(parseOrderResponse(orderNode));
            }
        }
        return orders;
    }

    /**
     * Get fill history for a user within a time range.
     * POST /info { "type": "userFillsByTime", "user": "0x...", "startTime": ms, "endTime": ms }
     */
    public List<Fill> getUserFills(String userAddress, Instant from, Instant to) throws ExchangeException {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", "userFillsByTime");
        body.put("user", userAddress);
        body.put("startTime", from.toEpochMilli());
        if (to != null) {
            body.put("endTime", to.toEpochMilli());
        }

        JsonNode response = post(body);
        List<Fill> fills = new ArrayList<>();

        if (response.isArray()) {
            for (JsonNode fillNode : response) {
                fills.add(parseFill(fillNode));
            }
        }
        return fills;
    }

    private OrderResponse parseOrderResponse(JsonNode node) {
        String oid = node.has("oid") ? node.get("oid").asText() : "";
        String coin = node.get("coin").asText();
        String side = node.get("side").asText();
        double sz = node.get("sz").asDouble();
        double limitPx = node.get("limitPx").asDouble();
        long timestamp = node.has("timestamp") ? node.get("timestamp").asLong() : System.currentTimeMillis();

        OrderSide orderSide = "B".equals(side) || "buy".equalsIgnoreCase(side) ? OrderSide.BUY : OrderSide.SELL;
        Instant time = Instant.ofEpochMilli(timestamp);

        return new OrderResponse(oid, null, coin, orderSide, OrderType.LIMIT,
                OrderStatus.OPEN, sz, 0, limitPx, List.of(), time, time);
    }

    private Fill parseFill(JsonNode node) {
        String tid = node.has("tid") ? node.get("tid").asText() : "";
        String oid = node.has("oid") ? node.get("oid").asText() : "";
        String coin = node.get("coin").asText();
        String side = node.get("side").asText();
        double px = node.get("px").asDouble();
        double sz = node.get("sz").asDouble();
        double fee = node.has("fee") ? node.get("fee").asDouble() : 0;
        long time = node.get("time").asLong();

        OrderSide orderSide = "B".equals(side) || "buy".equalsIgnoreCase(side) ? OrderSide.BUY : OrderSide.SELL;

        return new Fill(tid, oid, coin, orderSide, px, sz, fee, "USD", Instant.ofEpochMilli(time));
    }

    private JsonNode post(JsonNode body) throws ExchangeException {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .post(RequestBody.create(mapper.writeValueAsBytes(body), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    throw new ExchangeException("Hyperliquid info request failed: " + response.code() + " " + responseBody);
                }
                return mapper.readTree(response.body().bytes());
            }
        } catch (ExchangeException e) {
            throw e;
        } catch (IOException e) {
            throw new ExchangeException("Hyperliquid info request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parsed clearinghouse state.
     */
    public record ClearinghouseState(
            double accountValue,
            double totalMarginUsed,
            double totalNtlPos,
            double totalRawUsd,
            double withdrawable,
            List<ExchangePosition> positions
    ) {}
}
