package com.tradery.exchange.model;

public record OrderRequest(
    String symbol,
    OrderSide side,
    OrderType type,
    double quantity,
    Double price,
    Double triggerPrice,
    TimeInForce timeInForce,
    boolean reduceOnly,
    String clientOrderId
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String symbol;
        private OrderSide side;
        private OrderType type = OrderType.MARKET;
        private double quantity;
        private Double price;
        private Double triggerPrice;
        private TimeInForce timeInForce = TimeInForce.GTC;
        private boolean reduceOnly;
        private String clientOrderId;

        public Builder symbol(String symbol) { this.symbol = symbol; return this; }
        public Builder side(OrderSide side) { this.side = side; return this; }
        public Builder type(OrderType type) { this.type = type; return this; }
        public Builder quantity(double quantity) { this.quantity = quantity; return this; }
        public Builder price(Double price) { this.price = price; return this; }
        public Builder triggerPrice(Double triggerPrice) { this.triggerPrice = triggerPrice; return this; }
        public Builder timeInForce(TimeInForce timeInForce) { this.timeInForce = timeInForce; return this; }
        public Builder reduceOnly(boolean reduceOnly) { this.reduceOnly = reduceOnly; return this; }
        public Builder clientOrderId(String clientOrderId) { this.clientOrderId = clientOrderId; return this; }

        public OrderRequest build() {
            if (symbol == null) throw new IllegalArgumentException("symbol is required");
            if (side == null) throw new IllegalArgumentException("side is required");
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            return new OrderRequest(symbol, side, type, quantity, price, triggerPrice,
                    timeInForce, reduceOnly, clientOrderId);
        }
    }
}
