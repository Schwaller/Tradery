package com.tradery.data;

import com.tradery.core.model.*;
import com.tradery.forge.data.BybitExchangeClient;
import com.tradery.forge.data.OkxExchangeClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test hitting real Bybit/OKX APIs to verify response parsing.
 * Tagged "integration" so it doesn't run on every build.
 */
@Tag("integration")
@Timeout(30) // Kill hung tests after 30 seconds
class ExchangeClientSmokeTest {

    // ========== Bybit ==========

    @Test
    void bybitCandles() throws Exception {
        var client = new BybitExchangeClient(DataMarketType.FUTURES_PERP);
        long now = System.currentTimeMillis();
        long oneDayAgo = now - 24 * 60 * 60 * 1000L;

        List<Candle> candles = client.fetchCandles("BTCUSDT", "1h", oneDayAgo, now, 10);

        assertFalse(candles.isEmpty(), "Bybit should return candles");
        System.out.println("Bybit candles: " + candles.size());

        Candle first = candles.get(0);
        assertTrue(first.open() > 0, "Open should be positive");
        assertTrue(first.high() >= first.low(), "High >= Low");
        assertTrue(first.volume() > 0, "Volume should be positive");
        assertTrue(first.timestamp() > oneDayAgo - 3600000, "Timestamp in range");

        // Verify chronological order
        for (int i = 1; i < candles.size(); i++) {
            assertTrue(candles.get(i).timestamp() > candles.get(i - 1).timestamp(),
                "Candles should be chronological");
        }

        System.out.println("  First: " + first.timestamp() + " O=" + first.open() +
            " H=" + first.high() + " L=" + first.low() + " C=" + first.close() + " V=" + first.volume());
    }

    @Test
    void bybitTrades() throws Exception {
        var client = new BybitExchangeClient(DataMarketType.FUTURES_PERP);
        long now = System.currentTimeMillis();

        List<AggTrade> trades = client.fetchAggTrades("BTCUSDT", 0, now, 100);

        assertFalse(trades.isEmpty(), "Bybit should return trades");
        System.out.println("Bybit trades: " + trades.size());

        AggTrade first = trades.get(0);
        assertTrue(first.price() > 0, "Price should be positive");
        assertTrue(first.quantity() > 0, "Quantity should be positive");
        assertEquals(Exchange.BYBIT, first.exchange());
        assertEquals(DataMarketType.FUTURES_PERP, first.marketType());

        System.out.println("  First: ts=" + first.timestamp() + " P=" + first.price() +
            " Q=" + first.quantity() + " maker=" + first.isBuyerMaker());
    }

    @Test
    void bybitFundingRates() throws Exception {
        var client = new BybitExchangeClient(DataMarketType.FUTURES_PERP);
        long now = System.currentTimeMillis();
        long oneWeekAgo = now - 7 * 24 * 60 * 60 * 1000L;

        List<FundingRate> rates = client.fetchFundingRates("BTCUSDT", oneWeekAgo, now);

        assertFalse(rates.isEmpty(), "Bybit should return funding rates");
        System.out.println("Bybit funding rates: " + rates.size());

        FundingRate first = rates.get(0);
        assertTrue(first.fundingTime() > oneWeekAgo, "Funding time in range");
        assertNotEquals(0.0, first.fundingRate(), "Funding rate should not be 0");

        System.out.println("  First: ts=" + first.fundingTime() + " rate=" + first.fundingRate());
    }

    @Test
    void bybitOpenInterest() throws Exception {
        var client = new BybitExchangeClient(DataMarketType.FUTURES_PERP);
        long now = System.currentTimeMillis();
        long oneHourAgo = now - 60 * 60 * 1000L;

        List<OpenInterest> data = client.fetchOpenInterest("BTCUSDT", oneHourAgo, now);

        assertFalse(data.isEmpty(), "Bybit should return open interest");
        System.out.println("Bybit OI: " + data.size());

        OpenInterest first = data.get(0);
        assertTrue(first.openInterest() > 0, "OI should be positive");

        System.out.println("  First: ts=" + first.timestamp() + " OI=" + first.openInterest());
    }

    // ========== OKX ==========

    @Test
    void okxCandles() throws Exception {
        var client = new OkxExchangeClient(DataMarketType.FUTURES_PERP);
        long now = System.currentTimeMillis();
        long oneDayAgo = now - 24 * 60 * 60 * 1000L;

        List<Candle> candles = client.fetchCandles("BTC-USDT-SWAP", "1h", oneDayAgo, now, 10);

        assertFalse(candles.isEmpty(), "OKX should return candles");
        System.out.println("OKX candles: " + candles.size());

        Candle first = candles.get(0);
        assertTrue(first.open() > 0, "Open should be positive");
        assertTrue(first.high() >= first.low(), "High >= Low");
        assertTrue(first.volume() > 0, "Volume should be positive");

        // Verify chronological order
        for (int i = 1; i < candles.size(); i++) {
            assertTrue(candles.get(i).timestamp() > candles.get(i - 1).timestamp(),
                "Candles should be chronological");
        }

        System.out.println("  First: " + first.timestamp() + " O=" + first.open() +
            " H=" + first.high() + " L=" + first.low() + " C=" + first.close() + " V=" + first.volume());
    }

    @Test
    void okxTrades() throws Exception {
        var client = new OkxExchangeClient(DataMarketType.FUTURES_PERP);
        long now = System.currentTimeMillis();

        List<AggTrade> trades = client.fetchAggTrades("BTC-USDT-SWAP", 0, now, 100);

        assertFalse(trades.isEmpty(), "OKX should return trades");
        System.out.println("OKX trades: " + trades.size());

        AggTrade first = trades.get(0);
        assertTrue(first.price() > 0, "Price should be positive");
        assertTrue(first.quantity() > 0, "Quantity should be positive");
        assertEquals(Exchange.OKX, first.exchange());
        assertEquals(DataMarketType.FUTURES_PERP, first.marketType());

        System.out.println("  First: ts=" + first.timestamp() + " P=" + first.price() +
            " Q=" + first.quantity() + " maker=" + first.isBuyerMaker());
    }

    @Test
    void okxFundingRates() throws Exception {
        var client = new OkxExchangeClient(DataMarketType.FUTURES_PERP);
        long now = System.currentTimeMillis();
        // Use 1 day (3 funding intervals) to minimize API calls
        long oneDayAgo = now - 24 * 60 * 60 * 1000L;

        List<FundingRate> rates = client.fetchFundingRates("BTC-USDT-SWAP", oneDayAgo, now);

        assertFalse(rates.isEmpty(), "OKX should return funding rates");
        System.out.println("OKX funding rates: " + rates.size());

        FundingRate first = rates.get(0);
        assertTrue(first.fundingTime() > oneDayAgo, "Funding time in range");

        System.out.println("  First: ts=" + first.fundingTime() + " rate=" + first.fundingRate());
    }

    @Test
    void okxOpenInterest() throws Exception {
        var client = new OkxExchangeClient(DataMarketType.FUTURES_PERP);
        long now = System.currentTimeMillis();
        // Use 15 min to minimize API calls
        long fifteenMinAgo = now - 15 * 60 * 1000L;

        List<OpenInterest> data = client.fetchOpenInterest("BTC-USDT-SWAP", fifteenMinAgo, now);

        // OKX OI endpoint may not work for all instruments — just check no exceptions
        System.out.println("OKX OI: " + data.size() + " records");
        if (!data.isEmpty()) {
            OpenInterest first = data.get(0);
            assertTrue(first.openInterest() > 0, "OI should be positive");
            System.out.println("  First: ts=" + first.timestamp() + " OI=" + first.openInterest());
        }
    }

    // ========== Symbol Normalization ==========

    @Test
    void bybitSymbolNormalization() {
        var client = new BybitExchangeClient(DataMarketType.FUTURES_PERP);
        assertEquals("BTCUSDT", client.normalizeSymbol("BTC", "USDT", DataMarketType.FUTURES_PERP));
        assertEquals("ETHUSDT", client.normalizeSymbol("eth", "usdt", DataMarketType.SPOT));
    }

    @Test
    void okxSymbolNormalization() {
        var client = new OkxExchangeClient(DataMarketType.FUTURES_PERP);
        assertEquals("BTC-USDT-SWAP", client.normalizeSymbol("BTC", "USDT", DataMarketType.FUTURES_PERP));
        assertEquals("BTC-USDT", client.normalizeSymbol("BTC", "USDT", DataMarketType.SPOT));
        assertEquals("ETH-USDT-SWAP", client.normalizeSymbol("eth", "usdt", DataMarketType.FUTURES_PERP));
    }
}
