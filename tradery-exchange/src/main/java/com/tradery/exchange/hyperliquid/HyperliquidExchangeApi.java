package com.tradery.exchange.hyperliquid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.exception.OrderRejectedException;
import com.tradery.exchange.model.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.tradery.exchange.hyperliquid.HyperliquidSigner.bytesToHex;
import static com.tradery.exchange.hyperliquid.HyperliquidSigner.keccak256;

/**
 * Hyperliquid Exchange API — write operations (requires EIP-712 signing).
 * Endpoint: POST /exchange
 */
public class HyperliquidExchangeApi {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidExchangeApi.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final HyperliquidSigner signer;
    private final String baseUrl;
    private final List<AssetInfo> assets;

    public HyperliquidExchangeApi(OkHttpClient httpClient, HyperliquidSigner signer,
                                   boolean testnet, List<AssetInfo> assets) {
        this.httpClient = httpClient;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.signer = signer;
        this.baseUrl = testnet
                ? "https://api.hyperliquid-testnet.xyz/exchange"
                : "https://api.hyperliquid.xyz/exchange";
        this.assets = assets;
    }

    /**
     * Place an order on Hyperliquid.
     */
    public OrderResponse placeOrder(OrderRequest request) throws ExchangeException {
        int assetIndex = resolveAssetIndex(request.symbol());
        boolean isBuy = request.side() == OrderSide.BUY;

        // Build the order wire format
        ObjectNode orderWire = mapper.createObjectNode();
        orderWire.put("a", assetIndex);
        orderWire.put("b", isBuy);
        orderWire.put("p", formatPrice(request.price() != null ? request.price() : 0, request.symbol()));
        orderWire.put("s", formatSize(request.quantity(), request.symbol()));
        orderWire.put("r", request.reduceOnly());

        // Order type
        ObjectNode orderType = mapper.createObjectNode();
        if (request.type() == OrderType.LIMIT) {
            ObjectNode limit = mapper.createObjectNode();
            limit.put("tif", mapTimeInForce(request.timeInForce()));
            orderType.set("limit", limit);
        } else if (request.type() == OrderType.MARKET) {
            // Hyperliquid doesn't have native market orders — use aggressive limit
            ObjectNode limit = mapper.createObjectNode();
            limit.put("tif", "Ioc");
            orderType.set("limit", limit);
            // Adjust price for market order slippage tolerance (0.3%)
            double slippagePrice = isBuy
                    ? request.price() * 1.003
                    : request.price() * 0.997;
            orderWire.put("p", formatPrice(slippagePrice, request.symbol()));
        } else if (request.type() == OrderType.STOP_MARKET || request.type() == OrderType.STOP_LIMIT) {
            ObjectNode trigger = mapper.createObjectNode();
            trigger.put("isMarket", request.type() == OrderType.STOP_MARKET);
            trigger.put("triggerPx", formatPrice(
                    request.triggerPrice() != null ? request.triggerPrice() : 0, request.symbol()));
            trigger.put("tpsl", "sl"); // Default stop-loss; could be parameterized
            orderType.set("trigger", trigger);
        }
        orderWire.set("t", orderType);

        // Build action
        long nonce = System.currentTimeMillis();

        ObjectNode action = mapper.createObjectNode();
        action.put("type", "order");
        ArrayNode ordersArray = action.putArray("orders");
        ordersArray.add(orderWire);
        action.put("grouping", "na");

        // Sign the action
        byte[] actionHash = hashAction(action, nonce);
        String signature = signer.signL1Action(actionHash, nonce, 0);

        // Build request body
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.set("action", action);
        requestBody.put("nonce", nonce);
        requestBody.put("signature", signature);

        // Send
        JsonNode response = post(requestBody);

        // Parse response
        return parseOrderResponse(response, request);
    }

    /**
     * Cancel an order.
     */
    public OrderResponse cancelOrder(String symbol, String orderId) throws ExchangeException {
        int assetIndex = resolveAssetIndex(symbol);
        long nonce = System.currentTimeMillis();

        ObjectNode action = mapper.createObjectNode();
        action.put("type", "cancel");
        ArrayNode cancels = action.putArray("cancels");
        ObjectNode cancel = mapper.createObjectNode();
        cancel.put("a", assetIndex);
        cancel.put("o", Long.parseLong(orderId));
        cancels.add(cancel);

        byte[] actionHash = hashAction(action, nonce);
        String signature = signer.signL1Action(actionHash, nonce, 0);

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.set("action", action);
        requestBody.put("nonce", nonce);
        requestBody.put("signature", signature);

        JsonNode response = post(requestBody);
        Instant now = Instant.now();

        // Check if cancel was successful
        if (response.has("status") && "ok".equals(response.get("status").asText())) {
            return new OrderResponse(orderId, null, symbol, null, null,
                    OrderStatus.CANCELLED, 0, 0, null, List.of(), now, now);
        }

        throw new ExchangeException("Cancel failed: " + response);
    }

    /**
     * Update leverage for an asset.
     */
    public void updateLeverage(String symbol, int leverage, MarginMode mode) throws ExchangeException {
        int assetIndex = resolveAssetIndex(symbol);
        long nonce = System.currentTimeMillis();

        ObjectNode action = mapper.createObjectNode();
        action.put("type", "updateLeverage");
        action.put("asset", assetIndex);
        action.put("isCross", mode == MarginMode.CROSS);
        action.put("leverage", leverage);

        byte[] actionHash = hashAction(action, nonce);
        String signature = signer.signL1Action(actionHash, nonce, 0);

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.set("action", action);
        requestBody.put("nonce", nonce);
        requestBody.put("signature", signature);

        JsonNode response = post(requestBody);
        log.debug("Update leverage response: {}", response);
    }

    private int resolveAssetIndex(String symbol) throws ExchangeException {
        for (AssetInfo asset : assets) {
            if (asset.symbol().equalsIgnoreCase(symbol)) {
                return asset.universeIndex();
            }
        }
        throw new ExchangeException("Unknown asset: " + symbol);
    }

    private String formatPrice(double price, String symbol) {
        // Find asset precision
        for (AssetInfo asset : assets) {
            if (asset.symbol().equalsIgnoreCase(symbol)) {
                return String.format("%." + asset.pxDecimals() + "f", price);
            }
        }
        return String.format("%.2f", price);
    }

    private String formatSize(double size, String symbol) {
        for (AssetInfo asset : assets) {
            if (asset.symbol().equalsIgnoreCase(symbol)) {
                return String.format("%." + asset.szDecimals() + "f", size);
            }
        }
        return String.format("%.4f", size);
    }

    private String mapTimeInForce(TimeInForce tif) {
        return switch (tif) {
            case GTC -> "Gtc";
            case IOC -> "Ioc";
            case ALO -> "Alo";
        };
    }

    private byte[] hashAction(ObjectNode action, long nonce) {
        try {
            byte[] actionBytes = mapper.writeValueAsBytes(action);
            byte[] nonceBytes = String.valueOf(nonce).getBytes();
            byte[] combined = new byte[actionBytes.length + nonceBytes.length];
            System.arraycopy(actionBytes, 0, combined, 0, actionBytes.length);
            System.arraycopy(nonceBytes, 0, combined, actionBytes.length, nonceBytes.length);
            return keccak256(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash action", e);
        }
    }

    private OrderResponse parseOrderResponse(JsonNode response, OrderRequest request) throws ExchangeException {
        Instant now = Instant.now();

        if (response.has("status") && "err".equals(response.get("status").asText())) {
            String error = response.has("response") ? response.get("response").asText() : response.toString();
            throw new OrderRejectedException("Order rejected: " + error, error);
        }

        // Successful response contains statuses array
        JsonNode statuses = response.path("response").path("data").path("statuses");
        if (statuses.isArray() && statuses.size() > 0) {
            JsonNode status = statuses.get(0);

            if (status.has("error")) {
                throw new OrderRejectedException("Order rejected: " + status.get("error").asText(),
                        status.get("error").asText());
            }

            if (status.has("resting")) {
                JsonNode resting = status.get("resting");
                String oid = resting.get("oid").asText();
                return new OrderResponse(oid, request.clientOrderId(), request.symbol(),
                        request.side(), request.type(), OrderStatus.OPEN,
                        request.quantity(), 0, null, List.of(), now, now);
            }

            if (status.has("filled")) {
                JsonNode filled = status.get("filled");
                String oid = filled.get("oid").asText();
                double avgPx = filled.has("avgPx") ? filled.get("avgPx").asDouble() : 0;
                double totalSz = filled.has("totalSz") ? filled.get("totalSz").asDouble() : request.quantity();
                return new OrderResponse(oid, request.clientOrderId(), request.symbol(),
                        request.side(), request.type(), OrderStatus.FILLED,
                        request.quantity(), totalSz, avgPx, List.of(), now, now);
            }
        }

        // Fallback — treat as pending
        return new OrderResponse("unknown", request.clientOrderId(), request.symbol(),
                request.side(), request.type(), OrderStatus.PENDING,
                request.quantity(), 0, null, List.of(), now, now);
    }

    private JsonNode post(ObjectNode body) throws ExchangeException {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .post(RequestBody.create(mapper.writeValueAsBytes(body), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.code() == 429) {
                        throw new com.tradery.exchange.exception.RateLimitException(
                                "Rate limited", 1000);
                    }
                    throw new ExchangeException("Hyperliquid exchange request failed: "
                            + response.code() + " " + responseBody);
                }
                return mapper.readTree(response.body().bytes());
            }
        } catch (ExchangeException e) {
            throw e;
        } catch (IOException e) {
            throw new ExchangeException("Hyperliquid exchange request failed: " + e.getMessage(), e);
        }
    }
}
