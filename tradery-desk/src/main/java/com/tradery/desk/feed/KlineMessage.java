package com.tradery.desk.feed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tradery.core.model.Candle;

/**
 * Binance Futures WebSocket kline message.
 *
 * Example message:
 * {
 *   "e": "kline",
 *   "E": 1704067200000,
 *   "s": "BTCUSDT",
 *   "k": {
 *     "t": 1704067200000,
 *     "T": 1704070799999,
 *     "s": "BTCUSDT",
 *     "i": "1h",
 *     "o": "42000.00",
 *     "c": "42300.00",
 *     "h": "42500.00",
 *     "l": "41800.00",
 *     "v": "1234.56",
 *     "n": 5000,
 *     "x": false,
 *     "q": "51234567.89",
 *     "V": "617.28",
 *     "Q": "25617283.45"
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KlineMessage {

    @JsonProperty("e")
    private String eventType;

    @JsonProperty("E")
    private long eventTime;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("k")
    private KlineData kline;

    public String getEventType() {
        return eventType;
    }

    public long getEventTime() {
        return eventTime;
    }

    public String getSymbol() {
        return symbol;
    }

    public KlineData getKline() {
        return kline;
    }

    public boolean isKlineEvent() {
        return "kline".equals(eventType);
    }

    /**
     * Convert to Candle model.
     */
    public Candle toCandle() {
        if (kline == null) {
            return null;
        }
        return kline.toCandle();
    }

    /**
     * Check if this kline is closed (completed).
     */
    public boolean isClosed() {
        return kline != null && kline.isClosed();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KlineData {

        @JsonProperty("t")
        private long startTime;

        @JsonProperty("T")
        private long endTime;

        @JsonProperty("s")
        private String symbol;

        @JsonProperty("i")
        private String interval;

        @JsonProperty("o")
        private String open;

        @JsonProperty("c")
        private String close;

        @JsonProperty("h")
        private String high;

        @JsonProperty("l")
        private String low;

        @JsonProperty("v")
        private String volume;

        @JsonProperty("n")
        private int tradeCount;

        @JsonProperty("x")
        private boolean closed;

        @JsonProperty("q")
        private String quoteVolume;

        @JsonProperty("V")
        private String takerBuyVolume;

        @JsonProperty("Q")
        private String takerBuyQuoteVolume;

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getInterval() {
            return interval;
        }

        public double getOpen() {
            return Double.parseDouble(open);
        }

        public double getClose() {
            return Double.parseDouble(close);
        }

        public double getHigh() {
            return Double.parseDouble(high);
        }

        public double getLow() {
            return Double.parseDouble(low);
        }

        public double getVolume() {
            return Double.parseDouble(volume);
        }

        public int getTradeCount() {
            return tradeCount;
        }

        public boolean isClosed() {
            return closed;
        }

        public double getQuoteVolume() {
            try {
                return Double.parseDouble(quoteVolume);
            } catch (Exception e) {
                return -1;
            }
        }

        public double getTakerBuyVolume() {
            try {
                return Double.parseDouble(takerBuyVolume);
            } catch (Exception e) {
                return -1;
            }
        }

        public double getTakerBuyQuoteVolume() {
            try {
                return Double.parseDouble(takerBuyQuoteVolume);
            } catch (Exception e) {
                return -1;
            }
        }

        /**
         * Convert to Candle model.
         */
        public Candle toCandle() {
            return new Candle(
                startTime,
                getOpen(),
                getHigh(),
                getLow(),
                getClose(),
                getVolume(),
                tradeCount,
                getQuoteVolume(),
                getTakerBuyVolume(),
                getTakerBuyQuoteVolume()
            );
        }
    }
}
