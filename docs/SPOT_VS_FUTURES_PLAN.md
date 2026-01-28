# Plan: Spot vs Futures as First-Class Citizens

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    System Configuration                          │
│  "My data sources: Binance Futures, Bybit Futures, Coinbase Spot"│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Data Layer                                  │
│  AggTrades tagged with: Exchange + MarketType (SPOT/FUTURES)    │
│  Candles stored separately: BTCUSDT-spot vs BTCUSDT-futures     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      DSL Layer                                   │
│  SPOT_DELTA, FUTURES_DELTA, SPOT_FUTURES_DIVERGENCE             │
│  Returns NaN gracefully if market type not configured            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Charting Layer                               │
│  Spot chart / Futures chart / Combined - user preference         │
│  Each with appropriate market-specific overlays                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Data Model Enhancement

### 1.1 Extend Symbol/Market Concept

Currently symbols are strings like "BTCUSDT". Need to distinguish:
- `BTCUSDT` on spot market
- `BTCUSDT` on futures/perp market

**Option A: Composite Key**
```java
record MarketSymbol(String symbol, DataMarketType marketType) {}
// Usage: MarketSymbol("BTCUSDT", FUTURES_PERP)
```

**Option B: Separate Storage Paths**
```
~/.tradery/data/BTCUSDT/spot/1h/       # Spot candles
~/.tradery/data/BTCUSDT/futures/1h/    # Futures candles
~/.tradery/aggtrades/BTCUSDT/spot/     # Spot trades
~/.tradery/aggtrades/BTCUSDT/futures/  # Futures trades
```

**Recommendation:** Option B - cleaner separation, backward compatible (current = futures)

### 1.2 Data Source Configuration

Create new system-level config for data sources:

**File:** `~/.tradery/config/data-sources.yaml`
```yaml
futures:
  - exchange: BINANCE
    enabled: true
  - exchange: BYBIT
    enabled: true
  - exchange: OKX
    enabled: false

spot:
  - exchange: BINANCE
    enabled: true
  - exchange: COINBASE
    enabled: false
```

**Files to create/modify:**
- Create: `tradery-core/src/main/java/com/tradery/core/model/DataSourceConfig.java`
- Create: `tradery-forge/src/main/java/com/tradery/forge/io/DataSourceConfigStore.java`

---

## Phase 2: Data Fetching for Spot Markets

### 2.1 Add Spot API Endpoints

**BinanceExchangeClient** currently uses futures API only. Add spot support:

```java
// Existing (futures)
private static final String FUTURES_BASE = "https://fapi.binance.com/fapi/v1";
private static final String FUTURES_AGGTRADES = FUTURES_BASE + "/aggTrades";

// New (spot)
private static final String SPOT_BASE = "https://api.binance.com/api/v3";
private static final String SPOT_AGGTRADES = SPOT_BASE + "/aggTrades";
```

### 2.2 Extend AggTradesStore

Add market type parameter to fetching:

```java
public List<AggTrade> getAggTrades(String symbol, DataMarketType marketType,
                                    long startTime, long endTime)
```

**Files to modify:**
- `tradery-data-service/.../data/AggTradesStore.java`
- `tradery-forge/.../data/BinanceExchangeClient.java`

---

## Phase 3: New DSL Functions

### 3.1 Market-Type Aware Orderflow Functions

| Function | Description |
|----------|-------------|
| `SPOT_DELTA` | Delta from all configured spot exchanges |
| `FUTURES_DELTA` | Delta from all configured futures exchanges |
| `SPOT_VOLUME` | Volume from spot exchanges |
| `FUTURES_VOLUME` | Volume from futures exchanges |
| `SPOT_FUTURES_DIVERGENCE` | 1 if spot and futures disagree on direction, 0 otherwise |
| `SPOT_FUTURES_DELTA_SPREAD` | SPOT_DELTA - FUTURES_DELTA (positive = spot leading) |

### 3.2 Implementation

**Lexer.java** - Add new keywords:
```java
Map.entry("SPOT_DELTA", TokenType.MARKET_ORDERFLOW_FUNC),
Map.entry("FUTURES_DELTA", TokenType.MARKET_ORDERFLOW_FUNC),
Map.entry("SPOT_FUTURES_DIVERGENCE", TokenType.MARKET_ORDERFLOW_FUNC),
// etc.
```

**IndicatorEngine.java** - Add methods:
```java
public double getSpotDelta(int barIndex) {
    Footprint fp = getFootprintAt(barIndex);
    if (fp == null) return Double.NaN;
    return fp.getDeltaByMarketType(DataMarketType.SPOT);
}

public double getFuturesDelta(int barIndex) {
    Footprint fp = getFootprintAt(barIndex);
    if (fp == null) return Double.NaN;
    return fp.getDeltaByMarketType(DataMarketType.FUTURES_PERP);
}

public double getSpotFuturesDivergence(int barIndex) {
    double spot = getSpotDelta(barIndex);
    double futures = getFuturesDelta(barIndex);
    if (Double.isNaN(spot) || Double.isNaN(futures)) return Double.NaN;
    // Divergence = opposite signs
    return (spot > 0 && futures < 0) || (spot < 0 && futures > 0) ? 1.0 : 0.0;
}
```

### 3.3 Graceful Degradation

All functions return `Double.NaN` when:
- Market type not configured in data sources
- Data not available for requested period
- Strategy still runs, just without that signal

**Files to modify:**
- `tradery-core/.../dsl/Lexer.java`
- `tradery-core/.../dsl/Parser.java`
- `tradery-core/.../dsl/TokenType.java`
- `tradery-engine/.../ConditionEvaluator.java`
- `tradery-core/.../indicators/IndicatorEngine.java`

---

## Phase 4: Footprint Enhancement

### 4.1 Extend Footprint Model

Add market-type breakdown alongside exchange breakdown:

```java
public record Footprint(
    // Existing
    Map<Exchange, Double> deltaByExchange,
    double totalDelta,

    // New: market type breakdown
    Map<DataMarketType, Double> deltaByMarketType,
    Map<DataMarketType, Double> volumeByMarketType,
    double spotFuturesDivergenceScore  // 0-1
)
```

### 4.2 Update Footprint Generation

When building Footprint from AggTrades, aggregate by market type:

```java
Map<DataMarketType, Double> deltaByMarket = trades.stream()
    .filter(t -> t.marketType() != null)
    .collect(Collectors.groupingBy(
        AggTrade::marketType,
        Collectors.summingDouble(t -> t.isBuyerMaker() ? -t.quantity() : t.quantity())
    ));
```

**Files to modify:**
- `tradery-core/.../model/Footprint.java`
- `tradery-core/.../indicators/FootprintBuilder.java` (or equivalent)

---

## Phase 5: Charting Flexibility

### 5.1 Chart Data Source Selection

Allow charts to specify which market to display:

```java
public enum ChartDataSource {
    FUTURES,  // Default - current behavior
    SPOT,
    BOTH      // Overlay both price lines
}
```

### 5.2 UI Configuration

In chart settings panel:
- Dropdown: "Price data: Futures / Spot / Both"
- When "Both": futures = solid line, spot = dashed line

**Files to modify:**
- `tradery-forge/.../ui/charts/` - Chart configuration
- `tradery-forge/.../ui/settings/` - Settings panel

---

## Phase 6: Strategy Settings (Optional Enhancement)

### 6.1 Strategy Market Type

Allow strategy to specify which market it trades on:

```yaml
backtestSettings:
  symbol: BTCUSDT
  marketType: FUTURES  # or SPOT - affects price data used for entry/exit
  timeframe: 1h
```

This affects:
- Which price data is used for entry/exit simulation
- Commission/fee calculations (spot vs futures fees differ)
- Funding fee application (futures only)

---

## Implementation Order

1. **Data Model** - MarketSymbol, storage paths, DataSourceConfig
2. **Data Fetching** - Spot API endpoints, AggTradesStore enhancement
3. **Footprint** - Market type breakdown in Footprint model
4. **DSL Functions** - SPOT_DELTA, FUTURES_DELTA, SPOT_FUTURES_DIVERGENCE
5. **Charting** - Data source selection for price charts
6. **Strategy Settings** - Optional marketType in backtestSettings

---

## Verification

1. Configure spot data source (Binance spot)
2. Run backtest with `SPOT_FUTURES_DIVERGENCE == 1` as entry filter
3. Verify trades only occur when spot/futures disagree
4. Check graceful NaN when spot data not configured
5. Switch chart to spot view, verify correct prices displayed

---

## Key Files Summary

| File | Changes |
|------|---------|
| `tradery-core/.../model/DataSourceConfig.java` | Create - system config model |
| `tradery-core/.../model/Footprint.java` | Add deltaByMarketType |
| `tradery-core/.../dsl/Lexer.java` | Add SPOT_*/FUTURES_* keywords |
| `tradery-engine/.../ConditionEvaluator.java` | Handle new functions |
| `tradery-core/.../indicators/IndicatorEngine.java` | Add spot/futures delta methods |
| `tradery-data-service/.../AggTradesStore.java` | Market type parameter |
| `tradery-forge/.../BinanceExchangeClient.java` | Spot API endpoints |
| `tradery-forge/.../ui/charts/` | Data source selection |

---

## Future Enhancement: Per-Exchange Access

For users who want the "edge" of specific exchange signals:

```
COINBASE_SPOT_DELTA    # US retail signal
BINANCE_FUTURES_DELTA  # Asian leverage signal
OKX_FUTURES_DELTA      # Chinese trader signal
```

This builds on the same infrastructure but exposes finer granularity. Could be added later without architectural changes.
