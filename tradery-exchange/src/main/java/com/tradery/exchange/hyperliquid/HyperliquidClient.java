package com.tradery.exchange.hyperliquid;

import com.tradery.exchange.TradingClient;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.*;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Hyperliquid trading client — wires together Info API, Exchange API, and WebSocket.
 */
public class HyperliquidClient implements TradingClient {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidClient.class);

    private final TradingConfig.VenueConfig config;
    private final OkHttpClient httpClient;
    private final boolean testnet;

    private HyperliquidSigner signer;
    private HyperliquidInfoApi infoApi;
    private HyperliquidExchangeApi exchangeApi;
    private HyperliquidWebSocket webSocket;
    private List<AssetInfo> cachedAssets;
    private volatile boolean connected;

    public HyperliquidClient(TradingConfig.VenueConfig config) {
        this.config = config;
        this.testnet = config.isTestnet();
        this.httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public String getVenueName() {
        return testnet ? "hyperliquid-testnet" : "hyperliquid";
    }

    @Override
    public void connect() throws ExchangeException {
        log.info("Connecting to Hyperliquid (testnet={})", testnet);

        // Initialize signer
        if (config.getPrivateKey() != null && !config.getPrivateKey().isEmpty()) {
            signer = config.getAddress() != null && !config.getAddress().isEmpty()
                    ? new HyperliquidSigner(config.getPrivateKey(), config.getAddress(), testnet)
                    : new HyperliquidSigner(config.getPrivateKey(), testnet);
        }

        // Initialize info API (no auth needed)
        infoApi = new HyperliquidInfoApi(httpClient, testnet);

        // Fetch asset metadata
        cachedAssets = infoApi.getMeta();
        log.info("Loaded {} assets from Hyperliquid", cachedAssets.size());

        // Initialize exchange API (needs signer + assets)
        if (signer != null) {
            exchangeApi = new HyperliquidExchangeApi(httpClient, signer, testnet, cachedAssets);

            // Initialize WebSocket
            String address = config.getAddress() != null ? config.getAddress() : signer.getAddress();
            webSocket = new HyperliquidWebSocket(address, testnet);
            webSocket.connect();
        }

        connected = true;
        log.info("Connected to Hyperliquid ({} assets)", cachedAssets.size());
    }

    @Override
    public void disconnect() {
        connected = false;
        if (webSocket != null) {
            webSocket.disconnect();
        }
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        log.info("Disconnected from Hyperliquid");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public AccountState getAccountState() throws ExchangeException {
        String address = resolveAddress();
        HyperliquidInfoApi.ClearinghouseState state = infoApi.getClearinghouseState(address);

        double unrealizedPnl = state.positions().stream()
                .mapToDouble(ExchangePosition::unrealizedPnl)
                .sum();

        return new AccountState(
                state.totalRawUsd(),
                state.accountValue(),
                state.accountValue() - state.totalMarginUsed(),
                state.totalMarginUsed(),
                unrealizedPnl,
                state.withdrawable(),
                "USD",
                Instant.now()
        );
    }

    @Override
    public List<ExchangePosition> getPositions() throws ExchangeException {
        String address = resolveAddress();
        return infoApi.getClearinghouseState(address).positions();
    }

    @Override
    public List<AssetInfo> getAssets() throws ExchangeException {
        if (cachedAssets == null) {
            cachedAssets = infoApi.getMeta();
        }
        return cachedAssets;
    }

    @Override
    public OrderResponse placeOrder(OrderRequest request) throws ExchangeException {
        requireSigner();
        return exchangeApi.placeOrder(request);
    }

    @Override
    public OrderResponse cancelOrder(String symbol, String orderId) throws ExchangeException {
        requireSigner();
        return exchangeApi.cancelOrder(symbol, orderId);
    }

    @Override
    public List<OrderResponse> getOpenOrders(String symbol) throws ExchangeException {
        String address = resolveAddress();
        List<OrderResponse> allOrders = infoApi.getOpenOrders(address);
        if (symbol == null) return allOrders;
        return allOrders.stream()
                .filter(o -> symbol.equalsIgnoreCase(o.symbol()))
                .toList();
    }

    @Override
    public List<Fill> getFillHistory(String symbol, Instant from, Instant to) throws ExchangeException {
        String address = resolveAddress();
        List<Fill> fills = infoApi.getUserFills(address, from, to);
        if (symbol == null) return fills;
        return fills.stream()
                .filter(f -> symbol.equalsIgnoreCase(f.symbol()))
                .toList();
    }

    @Override
    public void setLeverage(String symbol, int leverage, MarginMode mode) throws ExchangeException {
        requireSigner();
        exchangeApi.updateLeverage(symbol, leverage, mode);
    }

    @Override
    public void subscribeOrderUpdates(Consumer<OrderUpdate> listener) {
        if (webSocket != null) {
            webSocket.subscribeOrderUpdates(listener);
        }
    }

    @Override
    public void subscribePositionUpdates(Consumer<ExchangePosition> listener) {
        if (webSocket != null) {
            webSocket.subscribePositionUpdates(listener);
        }
    }

    @Override
    public void subscribeFills(Consumer<Fill> listener) {
        if (webSocket != null) {
            webSocket.subscribeFills(listener);
        }
    }

    private String resolveAddress() throws ExchangeException {
        if (config.getAddress() != null && !config.getAddress().isEmpty()) {
            return config.getAddress();
        }
        if (signer != null) {
            return signer.getAddress();
        }
        throw new ExchangeException("No address configured for Hyperliquid");
    }

    private void requireSigner() throws ExchangeException {
        if (signer == null || exchangeApi == null) {
            throw new ExchangeException("Private key required for trading operations");
        }
    }
}
