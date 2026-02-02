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

## What Already Exists

The codebase has significant infrastructure for spot vs futures already in place:

### Data Model (DONE)
- **`DataMarketType`** enum: `SPOT`, `FUTURES_PERP`, `FUTURES_DATED` — with `fromConfigKey()` and `detect()` methods. Classifies **data sources** (where orderflow/aggTrades come from).
- **`AggTrade`** record: already has `exchange`, `marketType`, `rawSymbol`, `normalizedPrice` fields, plus `isSpot()` and `isFutures()` helpers
- **`Exchange`** enum: `BINANCE`, `BYBIT`, `OKX`, `COINBASE`, `KRAKEN`, `BITFINEX`
- **`MarketType`** enum (backtest sim): `SPOT`, `FUTURES`, `MARGIN` — with `hasHoldingCosts()`, `usesFunding()`. This is a **simulation parameter** for cost modeling, intentionally decoupled from data source.
- **`BacktestSettings`**: already has `exchange`, `symbolMarket` ("spot"/"perp"), `marketType` (MarketType for sim)

### Two Distinct Concepts (Important)
- **`DataMarketType`** = where data comes from (spot exchange vs futures exchange). Affects aggTrades tagging and orderflow analysis.
- **`MarketType`** = what market you're simulating trading on. Affects fee structure, funding costs, margin interest. Uses the **same price candles** regardless — spot and futures prices track closely, so this is intentional. Lets you quickly compare "what if I traded this on spot vs futures?" without re-fetching data.

### Data Fetching (PARTIAL)
- **`BinanceExchangeClient`**: already has `SPOT_BASE_URL` and `FUTURES_BASE_URL`, with `getBaseUrl(DataMarketType)` switching between them. Constructor takes `DataMarketType defaultMarketType`. However, `fetchAggTrades()` and `fetchCandles()` use `defaultMarketType` — no per-call override.
- **`AggTradesStore`**: `getAggTrades(symbol, startTime, endTime)` — **no marketType parameter**. Uses Binance Vision (futures only) for bulk download. SQLite storage.

### Footprint (EXCHANGE-ONLY, no market type)
- **`FootprintBucket`**: tracks `buyVolumeByExchange` / `sellVolumeByExchange` — per-Exchange breakdown only, no DataMarketType breakdown
- **`Footprint`**: has `deltaByExchange` (Map<Exchange, Double>) — no `deltaByMarketType`
- **`FootprintIndicator.calculate()`**: accepts `Set<Exchange> exchangeFilter` — no market type filter. Groups trades by bar using exchange filter, buckets by price+exchange.

### DSL (EXCHANGE-ONLY, no market type)
- Existing cross-exchange functions: `BINANCE_DELTA`, `BYBIT_DELTA`, `OKX_DELTA`, `COMBINED_DELTA`, `EXCHANGE_DELTA_SPREAD`, `EXCHANGE_DIVERGENCE`, `COMBINED_IMBALANCE_AT_POC`, `EXCHANGES_WITH_BUY_IMBALANCE`, `EXCHANGES_WITH_SELL_IMBALANCE`, `WHALE_DELTA_COMBINED`, `DOMINANT_EXCHANGE`
- Token type: `EXCHANGE_FUNC` — no `MARKET_TYPE_FUNC` equivalent
- **No spot/futures-specific DSL functions exist yet**

---

## What Needs to Be Built

### Phase 1: AggTradesStore — Spot Data Fetching

The main gap: `AggTradesStore` only fetches futures aggTrades. Need to support fetching and storing spot aggTrades alongside futures.

#### 1.1 Add marketType parameter to AggTradesStore

```java
// Current
public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime)

// Needed
public List<AggTrade> getAggTrades(String symbol, DataMarketType marketType,
                                    long startTime, long endTime)
```

The backward-compatible overload can default to `FUTURES_PERP`.

#### 1.2 SQLite storage separation

AggTrades are stored in SQLite via `SqliteDataStore`. Need to either:
- Add a `market_type` column to the existing table and filter queries
- Or use separate SQLite files per market type (simpler, no migration)

**Recommendation:** Separate SQLite files — `~/.tradery/aggtrades/BTCUSDT/futures/` (current) and `~/.tradery/aggtrades/BTCUSDT/spot/`. Current files are backward-compatible as futures.

#### 1.3 Spot-specific BinanceExchangeClient instance

`BinanceExchangeClient` already supports `DataMarketType` in constructor. The `AggTradesStore` needs to instantiate a spot client when fetching spot data:

```java
var spotClient = new BinanceExchangeClient(DataMarketType.SPOT);
```

#### 1.4 Binance Vision for spot

Currently hardcoded to futures Vision URL: `https://data.binance.vision/data/futures/um/daily/aggTrades`. Need spot equivalent: `https://data.binance.vision/data/spot/daily/aggTrades`.

**Files to modify:**
- `tradery-forge/src/main/java/com/tradery/forge/data/AggTradesStore.java` — marketType param, storage paths, Vision URL switching
- `tradery-forge/src/main/java/com/tradery/forge/data/BinanceExchangeClient.java` — minor (already supports spot, may need per-call marketType override)

---

### Phase 2: Footprint — Market Type Breakdown

#### 2.1 Extend FootprintBucket

Add market-type-level volume tracking alongside exchange-level:

```java
public record FootprintBucket(
    // ... existing fields ...

    // NEW: market type breakdown
    Map<DataMarketType, Double> buyVolumeByMarketType,
    Map<DataMarketType, Double> sellVolumeByMarketType
)
```

**Builder** changes:
```java
// In Builder, alongside existing exchange tracking:
private final Map<DataMarketType, Double> buyVolumeByMarket = new EnumMap<>(DataMarketType.class);
private final Map<DataMarketType, Double> sellVolumeByMarket = new EnumMap<>(DataMarketType.class);

public Builder addBuyVolume(Exchange exchange, DataMarketType marketType, double volume) {
    buyVolume.merge(exchange, volume, Double::sum);
    if (marketType != null) buyVolumeByMarket.merge(marketType, volume, Double::sum);
    return this;
}
```

#### 2.2 Extend Footprint

```java
public record Footprint(
    // ... existing fields ...

    // NEW: market type breakdown
    Map<DataMarketType, Double> deltaByMarketType,
    Map<DataMarketType, Double> volumeByMarketType
) {
    public double getDeltaForMarketType(DataMarketType mt) {
        return deltaByMarketType.getOrDefault(mt, 0.0);
    }

    public double getVolumeForMarketType(DataMarketType mt) {
        return volumeByMarketType.getOrDefault(mt, 0.0);
    }
}
```

#### 2.3 Update FootprintIndicator

In `calculateFootprintForBar()`, pass `trade.marketType()` to bucket builder:

```java
Exchange exchange = trade.exchange() != null ? trade.exchange() : Exchange.BINANCE;
DataMarketType marketType = trade.marketType(); // NEW

if (trade.isBuyerMaker()) {
    bucketBuilder.addSellVolume(exchange, marketType, trade.quantity());
} else {
    bucketBuilder.addBuyVolume(exchange, marketType, trade.quantity());
}

// Track per-market-type delta (NEW)
builder.addDelta(exchange, trade.delta());
builder.addMarketTypeDelta(marketType, trade.delta()); // NEW
```

Optionally add `Set<DataMarketType> marketTypeFilter` parameter to `calculate()`:

```java
public static FootprintResult calculate(
    List<Candle> candles,
    List<AggTrade> aggTrades,
    String resolution,
    int targetBuckets,
    Double fixedTickSize,
    Set<Exchange> exchangeFilter,
    Set<DataMarketType> marketTypeFilter)  // NEW
```

**Files to modify:**
- `tradery-core/src/main/java/com/tradery/core/model/FootprintBucket.java` — add market type maps + builder
- `tradery-core/src/main/java/com/tradery/core/model/Footprint.java` — add deltaByMarketType, volumeByMarketType
- `tradery-core/src/main/java/com/tradery/core/indicators/FootprintIndicator.java` — pass marketType through, optional filter

---

### Phase 3: DSL Functions

#### 3.1 New market-type orderflow functions

| Function | Description |
|----------|-------------|
| `SPOT_DELTA` | Delta from all spot-market trades |
| `FUTURES_DELTA` | Delta from all futures-market trades |
| `SPOT_VOLUME` | Volume from spot exchanges |
| `FUTURES_VOLUME` | Volume from futures exchanges |
| `SPOT_FUTURES_DIVERGENCE` | 1 if spot and futures delta have opposite signs |
| `SPOT_FUTURES_DELTA_SPREAD` | SPOT_DELTA - FUTURES_DELTA |

#### 3.2 TokenType

Add new token type (or reuse `EXCHANGE_FUNC` since it's the same pattern):

```java
// Option A: new token type
MARKET_TYPE_FUNC,

// Option B: reuse EXCHANGE_FUNC — simpler, same dispatch pattern
// Just add new keywords mapping to EXCHANGE_FUNC
```

**Recommendation:** Add to `EXCHANGE_FUNC` — the evaluation path is nearly identical.

#### 3.3 Lexer.java — Add keywords

```java
Map.entry("SPOT_DELTA", TokenType.EXCHANGE_FUNC),
Map.entry("FUTURES_DELTA", TokenType.EXCHANGE_FUNC),
Map.entry("SPOT_VOLUME", TokenType.EXCHANGE_FUNC),
Map.entry("FUTURES_VOLUME", TokenType.EXCHANGE_FUNC),
Map.entry("SPOT_FUTURES_DIVERGENCE", TokenType.EXCHANGE_FUNC),
Map.entry("SPOT_FUTURES_DELTA_SPREAD", TokenType.EXCHANGE_FUNC),
```

#### 3.4 IndicatorEngine.java — Add methods

```java
public double getSpotDelta(int barIndex) {
    Footprint fp = getFootprintAt(barIndex);
    if (fp == null) return Double.NaN;
    double d = fp.getDeltaForMarketType(DataMarketType.SPOT);
    return d == 0.0 ? Double.NaN : d;  // NaN if no spot data present
}

public double getFuturesDelta(int barIndex) {
    Footprint fp = getFootprintAt(barIndex);
    if (fp == null) return Double.NaN;
    double perp = fp.getDeltaForMarketType(DataMarketType.FUTURES_PERP);
    double dated = fp.getDeltaForMarketType(DataMarketType.FUTURES_DATED);
    double total = perp + dated;
    return total == 0.0 ? Double.NaN : total;
}

public double getSpotVolume(int barIndex) {
    Footprint fp = getFootprintAt(barIndex);
    if (fp == null) return Double.NaN;
    double v = fp.getVolumeForMarketType(DataMarketType.SPOT);
    return v == 0.0 ? Double.NaN : v;
}

public double getFuturesVolume(int barIndex) {
    Footprint fp = getFootprintAt(barIndex);
    if (fp == null) return Double.NaN;
    double perp = fp.getVolumeForMarketType(DataMarketType.FUTURES_PERP);
    double dated = fp.getVolumeForMarketType(DataMarketType.FUTURES_DATED);
    double total = perp + dated;
    return total == 0.0 ? Double.NaN : total;
}

public double getSpotFuturesDivergence(int barIndex) {
    double spot = getSpotDelta(barIndex);
    double futures = getFuturesDelta(barIndex);
    if (Double.isNaN(spot) || Double.isNaN(futures)) return Double.NaN;
    return (spot > 0 && futures < 0) || (spot < 0 && futures > 0) ? 1.0 : 0.0;
}

public double getSpotFuturesDeltaSpread(int barIndex) {
    double spot = getSpotDelta(barIndex);
    double futures = getFuturesDelta(barIndex);
    if (Double.isNaN(spot) || Double.isNaN(futures)) return Double.NaN;
    return spot - futures;
}
```

#### 3.5 ConditionEvaluator.java — Dispatch

Add cases to `evaluateExchangeFunction()`:

```java
case "SPOT_DELTA" -> engine.getSpotDelta(barIndex);
case "FUTURES_DELTA" -> engine.getFuturesDelta(barIndex);
case "SPOT_VOLUME" -> engine.getSpotVolume(barIndex);
case "FUTURES_VOLUME" -> engine.getFuturesVolume(barIndex);
case "SPOT_FUTURES_DIVERGENCE" -> engine.getSpotFuturesDivergence(barIndex);
case "SPOT_FUTURES_DELTA_SPREAD" -> engine.getSpotFuturesDeltaSpread(barIndex);
```

#### 3.6 Graceful Degradation

All functions return `Double.NaN` when:
- No aggTrades with that market type are present (footprint has no data for that market type)
- Orderflow mode is disabled
- Strategy still runs; NaN comparisons evaluate to false, so conditions silently skip

**Files to modify:**
- `tradery-core/src/main/java/com/tradery/core/dsl/Lexer.java` — add 6 keywords
- `tradery-core/src/main/java/com/tradery/core/dsl/Parser.java` — no change needed (reuses EXCHANGE_FUNC path)
- `tradery-core/src/main/java/com/tradery/core/dsl/TokenType.java` — no change if reusing EXCHANGE_FUNC
- `tradery-engine/src/main/java/com/tradery/engine/ConditionEvaluator.java` — add 6 cases
- `tradery-core/src/main/java/com/tradery/core/indicators/IndicatorEngine.java` — add 6 methods

---

### Phase 4: Data Source Configuration (Optional)

System-level config for which data sources to enable:

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

This governs which aggTrades are fetched when `orderflowSettings.mode` is enabled. Could be deferred — initially just fetch Binance spot alongside Binance futures when orderflow is enabled.

**Files to create:**
- `tradery-core/src/main/java/com/tradery/core/model/DataSourceConfig.java`
- `tradery-forge/src/main/java/com/tradery/forge/io/DataSourceConfigStore.java`

---

### Phase 5: Charting Flexibility (Optional)

#### 5.1 Chart Data Source Selection

Allow charts to specify which market's price data to display:

```java
public enum ChartDataSource {
    FUTURES,  // Default — current behavior
    SPOT,
    BOTH      // Overlay both price lines
}
```

#### 5.2 UI Configuration

In `ChartConfig` (singleton, persisted to `~/.tradery/chart-config.json`):
- Add `chartDataSource` field
- In chart settings panel: dropdown "Price data: Futures / Spot / Both"
- When "Both": futures = solid candlesticks, spot = dashed line overlay

Requires fetching spot candles via `BinanceExchangeClient(DataMarketType.SPOT)`.

**Files to modify:**
- `tradery-forge/src/main/java/com/tradery/forge/ui/charts/ChartConfig.java` — add chartDataSource
- `tradery-forge/src/main/java/com/tradery/forge/ui/ChartsPanel.java` — render spot price overlay
- `tradery-forge/src/main/java/com/tradery/forge/ui/controls/IndicatorSelectorPopup.java` — add option

---

## Implementation Order

1. **Footprint model** (Phase 2) — Add market type breakdown to FootprintBucket/Footprint/FootprintIndicator
2. **DSL functions** (Phase 3) — Add SPOT_DELTA, FUTURES_DELTA, etc. to Lexer/ConditionEvaluator/IndicatorEngine
3. **AggTradesStore** (Phase 1) — Add spot data fetching + storage separation
4. **Data source config** (Phase 4) — Optional, can hardcode Binance spot initially
5. **Charting** (Phase 5) — Optional, spot price overlay

Phase 1 is the most work (storage, fetching, Vision URLs). Phases 2-3 can be done first since the footprint model just needs to handle whatever marketType tags are on the AggTrades it receives — currently all FUTURES_PERP, but once spot data starts flowing in, it'll work automatically.

---

## Key Files Summary

| File | Status | Changes Needed |
|------|--------|----------------|
| `tradery-core/.../model/DataMarketType.java` | DONE | No changes |
| `tradery-core/.../model/AggTrade.java` | DONE | No changes (has marketType, isSpot(), isFutures()) |
| `tradery-core/.../model/Exchange.java` | DONE | No changes |
| `tradery-core/.../model/BacktestSettings.java` | DONE | No changes (has symbolMarket, marketType) |
| `tradery-core/.../model/FootprintBucket.java` | MODIFY | Add buyVolumeByMarketType, sellVolumeByMarketType + Builder |
| `tradery-core/.../model/Footprint.java` | MODIFY | Add deltaByMarketType, volumeByMarketType |
| `tradery-core/.../indicators/FootprintIndicator.java` | MODIFY | Pass marketType through bucketing, optional filter |
| `tradery-core/.../dsl/Lexer.java` | MODIFY | Add 6 keywords (SPOT_DELTA, etc.) |
| `tradery-core/.../dsl/Parser.java` | OK | No change (reuses EXCHANGE_FUNC path) |
| `tradery-core/.../dsl/TokenType.java` | OK | No change (reuses EXCHANGE_FUNC) |
| `tradery-engine/.../ConditionEvaluator.java` | MODIFY | Add 6 cases to evaluateExchangeFunction() |
| `tradery-core/.../indicators/IndicatorEngine.java` | MODIFY | Add 6 methods (getSpotDelta, etc.) |
| `tradery-forge/.../data/AggTradesStore.java` | MODIFY | Add marketType param, storage paths, Vision URL switching |
| `tradery-forge/.../data/BinanceExchangeClient.java` | OK | Already supports spot via constructor DataMarketType |
| `tradery-core/.../model/DataSourceConfig.java` | CREATE | Optional — system config model |
| `tradery-forge/.../io/DataSourceConfigStore.java` | CREATE | Optional — YAML store |
| `tradery-forge/.../ui/charts/ChartConfig.java` | MODIFY | Optional — add chartDataSource |
| `tradery-forge/.../ui/ChartsPanel.java` | MODIFY | Optional — spot price overlay |
| `tradery-forge/.../ui/DslHelpDialog.java` | MODIFY | Document new functions |

---

## Verification

1. Ensure existing aggTrades (all FUTURES_PERP) still work — footprint model backward compatible
2. Fetch Binance spot aggTrades alongside futures
3. Verify `SPOT_DELTA` and `FUTURES_DELTA` return correct values when both data types present
4. Verify `SPOT_FUTURES_DIVERGENCE == 1` triggers only when spot/futures delta signs differ
5. Verify graceful NaN when only futures data is present (SPOT_DELTA returns NaN, conditions skip)
6. Backtest with `SPOT_FUTURES_DIVERGENCE == 1 AND RSI(14) < 40` — only trades when divergence + oversold

---

## Future Enhancement: Per-Exchange + Per-Market Granularity

Combines exchange and market type for maximum signal granularity:

```
COINBASE_SPOT_DELTA    # US retail signal
BINANCE_FUTURES_DELTA  # Asian leverage signal
OKX_FUTURES_DELTA      # Chinese trader signal
BINANCE_SPOT_DELTA     # Asian spot signal
```

This builds on the same infrastructure (FootprintBucket already tracks by exchange, will also track by market type). A combined filter `(Exchange, DataMarketType)` pair can be added to `getDeltaForExchangeAndMarket()` without architectural changes.
