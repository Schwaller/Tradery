# Trade Size Spectrum — Implementation Plan

## Context

We need to analyze the distribution of trade notionals from aggTrades data using logarithmic buckets. This reveals whale vs retail flow, size-based delta, and absorption patterns — information invisible in standard volume bars. The feature pre-aggregates trades into 10-second histograms at ingestion time, stores them in a dedicated `spectrum.db` per symbol, and merges at query time for any timeframe (time pyramid). Spectrum is a **first-class data type** in the page system — same lifecycle as Funding, OI, etc. A spectrogram-style chart panel visualizes it like a sound spectrum over time.

---

## Architecture Overview

```
                 AggTrades ingestion
                        │
                        ▼
              ┌─────────────────────┐
              │ SpectrumAggregator  │  10s windows, log10 buckets
              └─────────┬───────────┘
                        │
                        ▼
              ┌─────────────────────┐
              │  spectrum.db        │  trade_size_spectrum table
              │  (per symbol)       │  PK: (window_start, bucket_index)
              └─────────┬───────────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
  Page System       Backfill API     GET /spectrum
  (WS push to      POST /spectrum    (direct query)
   Forge pages)     /backfill
        │
        ▼
  SpectrumPageManager          SpectrumStore
  (Forge-side, like            (data-service, like
   FundingPageManager)          FundingRateStore)
        │
        ▼
  Spectrogram Chart Panel
  (heatmap: time × bucket → color)
```

---

## Data Model

### Log Bucket Strategy

```
Bucket index = floor(log10(trade_notional))

Index │ Range              │ Label
──────┼────────────────────┼───────
  0   │ $1 – $10           │ $1
  1   │ $10 – $100         │ $10
  2   │ $100 – $1K         │ $100
  3   │ $1K – $10K         │ $1K
  4   │ $10K – $100K       │ $10K
  5   │ $100K – $1M        │ $100K
  6   │ $1M – $10M         │ $1M
  7   │ $10M+              │ $10M
```

Scale-invariant: works for BTC ($100K trades), altcoins ($0.10 trades), and everything in between.

### SizeBucket

Per bucket per time window:
- `tradeCount` — number of trades
- `totalVolume` — sum of notionals (USD)
- `buyVolume` — taker buy notionals
- `sellVolume` — taker sell notionals

Derived: `delta = buyVolume - sellVolume`

### Time Pyramid

Base: 10-second windows. Merge by summing bucket stats:

```
10s → 1m (6 windows) → 5m (30) → 30m (180) → 1h (360) → 1d (8640)
```

Merging is done at **query time** via SQL `GROUP BY`, not pre-materialized. Keeps storage simple (~1.7 MB/day for BTCUSDT) while supporting arbitrary timeframes.

### SQLite Schema

```sql
-- In spectrum.db (per symbol, separate from agg_trades.db)
CREATE TABLE trade_size_spectrum (
    window_start  INTEGER NOT NULL,   -- 10s-aligned epoch ms
    bucket_index  INTEGER NOT NULL,   -- floor(log10(notional))
    trade_count   INTEGER NOT NULL,
    total_volume  REAL NOT NULL,
    buy_volume    REAL NOT NULL,
    sell_volume   REAL NOT NULL,
    PRIMARY KEY (window_start, bucket_index)
) WITHOUT ROWID;

CREATE INDEX idx_spectrum_window ON trade_size_spectrum(window_start);
```

Storage estimate: ~5 buckets/window × 8640 windows/day × ~40 bytes/row ≈ **1.7 MB/day**. Negligible vs aggTrades.

---

## Page System Integration

Spectrum follows the **Funding pattern** — the closest existing analogy:

| Aspect | Funding | Spectrum |
|--------|---------|----------|
| Timeframe | None (8h fixed) | None (10s fixed) |
| Per-symbol | Yes | Yes |
| Page manager | `FundingPageManager` | `SpectrumPageManager` |
| Store | `FundingRateStore` | `SpectrumStore` |
| DAO | `FundingRateDao` | `SpectrumDao` |
| DataType enum | `FUNDING` | `SPECTRUM` |
| DataStoreType | `FUNDING_RATES` | `SPECTRUM` |
| DB file | `funding_rates.db` | `spectrum.db` |
| Serialization | msgpack via WS | msgpack via WS |
| In-memory pages | Yes (small data) | Yes (small data) |

### Request Flow

```
Forge chart requests spectrum data
    ↓
SpectrumPageManager.request(symbol, null, startTime, endTime, listener)
    ↓ extends DataServicePageManager<SpectrumWindow>
    ↓
DataServiceClient.subscribePage(SPECTRUM, symbol, null, start, end, callback)
    ↓ WebSocket to data-service
    ↓
PageManager.loadPage() → key.isSpectrum() → loadSpectrum()
    ↓
SpectrumStore.getSpectrum(symbol, start, end)
    ├─ Check spectrum.db coverage
    ├─ If gaps: check if aggTrades exist for those gaps
    │   ├─ If yes: backfill spectrum from existing aggTrades
    │   └─ If no: return what we have (spectrum depends on aggTrades)
    └─ Return List<SpectrumWindow> from SQLite
    ↓
msgpack serialize → WS push → Forge deserializes
    ↓
SpectrumPageManager.updatePageData() → listeners notified on EDT
    ↓
SpectrumRenderer paints spectrogram
```

---

## Implementation Phases

### Phase 1: Core Model & Storage

#### 1.1 — Model classes

**New files in `tradery-core/.../model/`:**

- **`SizeBucket.java`** — record(tradeCount, totalVolume, buyVolume, sellVolume) with `merge()`, `delta()`
- **`SpectrumWindow.java`** — record(windowStart, Map<Integer, SizeBucket>) with `merge()`, static `bucketIndex(double notional)`, `bucketLabel(int index)`
- **`SpectrumMode.java`** — enum: COUNT, VOLUME, DELTA

#### 1.2 — DataStoreType.SPECTRUM

**Modify:** `tradery-data-service/.../data/sqlite/DataStoreType.java`
- Add `SPECTRUM("spectrum.db")` enum value
- Add `case "spectrum" -> SPECTRUM` in `fromCoverageKey()`

#### 1.3 — SQLite schema

**Modify:** `tradery-data-service/.../data/sqlite/SqliteSchema.java`
- Add `case SPECTRUM -> createSpectrumTables(stmt)`
- `createSpectrumTables()`: the table + index above, plus `data_coverage` table

#### 1.4 — SpectrumDao

**New file:** `tradery-data-service/.../data/sqlite/dao/SpectrumDao.java`

Following existing DAO patterns (FundingRateDao, CandleDao):

| Method | Purpose |
|--------|---------|
| `insertBatch(List<SpectrumRow>)` | Batch upsert 10s histogram rows |
| `queryFlat(start, end)` | Flat distribution: `GROUP BY bucket_index` |
| `queryAggregated(start, end, windowMs)` | Time-series: `GROUP BY period, bucket_index` |
| `queryWindows(start, end)` | Raw 10s windows for page serving |
| `countInRange(start, end)` | Fast count for coverage checks |
| `getTimeRange()` | Min/max window_start |
| `deleteAll()` | Purge |
| `deleteInRange(start, end)` | Partial cleanup |

Key SQL for time pyramid (query-time merge):
```sql
SELECT (window_start / :windowMs) * :windowMs AS period_start,
       bucket_index,
       SUM(trade_count), SUM(total_volume), SUM(buy_volume), SUM(sell_volume)
FROM trade_size_spectrum
WHERE window_start >= :start AND window_start < :end
GROUP BY period_start, bucket_index
ORDER BY period_start, bucket_index
```

#### 1.5 — Wire into SqliteDataStore

**Modify:** `tradery-data-service/.../data/sqlite/SqliteDataStore.java`

In `SymbolData` inner class:
- Add `spectrumConn`, `spectrumDao`, `spectrumCoverage` fields
- Initialize in constructor alongside other DAOs
- Add `spectrum()` accessor
- Update `coverageFor()`, `connectionFor()`, `close()` switch statements

Add facade methods:
- `getSpectrum(symbol, start, end)` → `List<SpectrumWindow>`
- `getSpectrumAggregated(symbol, start, end, windowMs)` → `List<AggregatedBucket>`
- `getSpectrumFlat(symbol, start, end)` → `List<FlatBucket>`
- `saveSpectrum(symbol, List<SpectrumRow>)`
- `countSpectrum(symbol, start, end)` → `long`

---

### Phase 2: Aggregation Pipeline

#### 2.1 — SpectrumAggregator

**New file:** `tradery-data-service/.../data/SpectrumAggregator.java`

Stateless processor: `List<AggTrade>` → `List<SpectrumRow>`.

```
For each trade:
  windowStart = (timestamp / 10_000) * 10_000
  bucket = floor(log10(trade.notional()))
  Accumulate into MutableBucket: tradeCount++, totalVolume += notional,
    buyVolume += (isBuyer ? notional : 0), sellVolume += (isSeller ? notional : 0)
Flatten to List<SpectrumRow>
```

Uses `trade.notional()` for bucket assignment. For cross-exchange data with `normalizedPrice` set, uses `trade.normalizedNotional()`.

#### 2.2 — Hook into AggTrades ingestion

**Modify:** `tradery-data-service/.../data/AggTradesStore.java`

In the method that persists aggTrades to SQLite (the funnel where all trades pass):

```java
// After saving aggTrades:
List<SpectrumRow> spectrumRows = spectrumAggregator.aggregate(trades);
if (!spectrumRows.isEmpty()) {
    sqliteStore.saveSpectrum(symbol, spectrumRows);
    // Record spectrum coverage matching aggTrades coverage
}
```

Spectrum is computed **inline** during ingestion — no double-reads, always in sync with aggTrades.

#### 2.3 — Backfill from existing aggTrades

**Add to `SpectrumAggregator`:**

```java
public void backfill(String symbol, long start, long end, SqliteDataStore dataStore) {
    // Stream aggTrades from SQLite in 10K chunks
    // Aggregate each chunk → spectrum rows
    // Save to spectrum.db
    // Record coverage
}
```

Handles migration: existing aggTrades without spectrum data.

---

### Phase 3: Data Service — SpectrumStore & Page Loading

#### 3.1 — SpectrumStore

**New file:** `tradery-data-service/.../data/SpectrumStore.java`

Following the `FundingRateStore` pattern (cache check → gap detection → backfill → serve):

```java
public class SpectrumStore {
    public List<SpectrumWindow> getSpectrum(String symbol, long start, long end) {
        // 1. Check spectrum.db coverage for gaps
        // 2. For gaps: check if aggTrades exist in those ranges
        //    - If yes: backfill spectrum from aggTrades (SpectrumAggregator)
        //    - If no: those gaps remain empty (spectrum depends on aggTrades)
        // 3. Return all spectrum data from SQLite
    }
}
```

Key difference from FundingRateStore: spectrum doesn't fetch from an external API — it's derived from aggTrades. So gap-filling means "compute from aggTrades" not "fetch from Binance."

#### 3.2 — Wire into PageManager

**Modify:** `tradery-data-service/.../page/PageManager.java`

In `loadPage()` dispatch:
```java
} else if (key.isSpectrum()) {
    data = loadSpectrum(key, page);
    recordCount = page.getRecordCount();
}
```

```java
private byte[] loadSpectrum(PageKey key, Page page) throws Exception {
    List<SpectrumWindow> windows = spectrumStore.getSpectrum(
        key.symbol(), key.getEffectiveStartTime(), key.getEffectiveEndTime());
    page.setRecordCount(windows.size());
    return msgpackMapper.writeValueAsBytes(windows);
}
```

#### 3.3 — PageKey extension

**Modify:** `tradery-data/.../page/PageKey.java`

Add: `public boolean isSpectrum() { return "SPECTRUM".equals(dataType); }`

---

### Phase 4: API Endpoints

#### 4.1 — SpectrumHandler

**New file:** `tradery-data-service/.../api/SpectrumHandler.java`

```
GET /spectrum?symbol=BTCUSDT&from=...&to=...&timeframe=1h&mode=VOLUME

Response (flat, no timeframe):
{
  "buckets": [
    { "label": "$100", "bucketIndex": 2, "value": 45230.50 },
    { "label": "$1K",  "bucketIndex": 3, "value": 892100.00 }
  ]
}

Response (time-series, with timeframe):
{
  "windows": [
    { "timestamp": 1700000000000, "buckets": [...] },
    ...
  ]
}

POST /spectrum/backfill?symbol=BTCUSDT&from=...&to=...
Response: { "rowsCreated": 43200, "durationMs": 12500 }
```

#### 4.2 — Register routes

**Modify:** `tradery-data-service/.../api/DataServiceServer.java`

Add `configureSpectrumRoutes()`:
```java
app.get("/spectrum", spectrumHandler::getSpectrum);
app.post("/spectrum/backfill", spectrumHandler::backfill);
```

---

### Phase 5: Forge Page Manager

#### 5.1 — SpectrumPageManager

**New file:** `tradery-forge/.../data/page/SpectrumPageManager.java`

Following the `FundingPageManager` pattern exactly:

```java
public class SpectrumPageManager extends DataServicePageManager<SpectrumWindow> {
    public SpectrumPageManager() {
        super(DataType.SPECTRUM, 2,        // 2 threads (small data)
            "data-service/spectrum", 128,   // ~128 bytes per window
            (mapper, data) -> mapper.readValue(data,
                mapper.getTypeFactory().constructCollectionType(
                    List.class, SpectrumWindow.class)));
    }
}
```

- Extends `DataServicePageManager<SpectrumWindow>`
- No timeframe (like Funding)
- Per-symbol (like Funding)
- Small data volume — in-memory pages (not streamed like AggTrades)

#### 5.2 — DataType.SPECTRUM

**Modify:** `tradery-data/.../page/DataType.java`

```java
SPECTRUM("Spectrum");

// requiresTimeframe() → false (fixed 10s intervals)
// isGlobal() → false (per-symbol)
// toWireFormat() → "SPECTRUM"
// fromWireFormat("SPECTRUM") → SPECTRUM
```

#### 5.3 — Wire into ApplicationContext

**Modify:** wherever page managers are created (likely `ApplicationContext` or chart data coordinator)

Create `SpectrumPageManager` instance alongside the existing page managers.

---

### Phase 6: Data Management Integration

#### 6.1 — Inventory support

**Modify:** `tradery-data-service/.../api/InventoryHandler.java`

- Add `record SpectrumInventory(long startTime, long endTime, long rowCount)`
- Add `spectrum` field to `SymbolInventory`
- Add `case "spectrum"` to `deleteData()` switch — calls `spectrumDao.deleteAll()`
- Build spectrum inventory from coverage table in `buildSymbolInventory()`

#### 6.2 — Data browser

**Modify:** `tradery-data-ui/.../DataBrowserPanel.java`

Add "Trade Size Spectrum" section after Premium Index:
```java
rows.add(RowData.sectionHeader("Trade Size Spectrum"));
for (SymbolInventory sym : symbols) {
    if (!sym.hasSpectrumData()) continue;
    rows.add(symbolHeaderWithSize(sym.symbol(), "spectrum.db"));
    rows.add(new RowData(..., "10s Histograms", coverage, COMPLETE_COLOR));
}
```

Users can select spectrum data per symbol, view coverage, and delete it.

#### 6.3 — Coverage heatmap

**Modify:** `tradery-data-ui/.../DataHealthPanel.java`

Add `"spectrum"` as a selectable data type for the hourly-resolution coverage heatmap.

#### 6.4 — Client DTOs

**Modify:** `tradery-data-client/.../DataServiceClient.java`

- Add `SpectrumInventory` record
- Add `spectrum` field to `SymbolInventory`
- Add `getSpectrum()`, `getSpectrumAggregated()`, `backfillSpectrum()` methods

---

### Phase 7: Preloading & Background Processing

#### 7.1 — PreloadRequest.SPECTRUM

**Modify:** `tradery-forge/.../data/PreloadRequest.java`

- Add `SPECTRUM` to `DataType` enum
- Add factory: `PreloadRequest.spectrum(symbol, start, end, priority)`

#### 7.2 — PreloadScheduler

**Modify:** `tradery-forge/.../data/PreloadScheduler.java`

- Add `case SPECTRUM -> processSpectrum(request)` in `processRequest()`
- `processSpectrum()`: check spectrum coverage gaps, call `POST /spectrum/backfill` for gaps
- After `processAggTrades()` completes: auto-queue `SPECTRUM` at LOW priority for same range

#### 7.3 — DataInventory

**Modify:** `tradery-forge/.../data/DataInventory.java`

- Add `spectrumCoverage` map (symbol → DateRangeSet)
- Add `recordSpectrumData()`, `hasSpectrumData()`, `getSpectrumGaps()`
- Persist across restarts

#### 7.4 — Auto-backfill detection

On startup or strategy open with orderflow enabled: if aggTrades coverage exists but spectrum doesn't, auto-queue backfill at LOW priority.

---

### Phase 8: Download Dashboard

#### 8.1 — Download events

Emit `DownloadEvent` records with `DataType.SPECTRUM` during:
- Inline aggregation (LOAD_COMPLETED with row count)
- Backfill operations (LOAD_STARTED → progress → LOAD_COMPLETED)
- Errors

Events automatically appear in DownloadLogPanel and DataTimelinePanel since they filter by DataType enum.

---

### Phase 9: Spectrogram Chart Panel

A sound-spectrogram-style visualization in a separate indicator panel below the price chart.

#### 9.1 — IndicatorType.SPECTRUM

**Modify:** `tradery-charts/.../core/IndicatorType.java`

Add: `SPECTRUM("Trade Size Spectrum", null)`

#### 9.2 — SpectrumCompute

**New file:** `tradery-charts/.../indicator/SpectrumCompute.java`

Extends `IndicatorCompute<SpectrumData>`. Requests spectrum page from `SpectrumPageManager` for the visible time range. Merges 10s windows to match the chart's timeframe.

#### 9.3 — SpectrumRenderer

**New file:** `tradery-charts/.../renderer/SpectrumRenderer.java`

Renders as a spectrogram heatmap:
- **X-axis:** time (aligned to chart candles)
- **Y-axis:** bucket labels ($1, $10, $100, $1K, $10K, $100K, $1M) — log scale
- **Color:** gradient from cold (low volume) to hot (high volume)
- **Mode toggle:** COUNT / VOLUME / DELTA
  - DELTA uses diverging color: red = net sell, green = net buy

Uses JFreeChart's `XYBlockRenderer` or custom painting on an `XYPlot`.

#### 9.4 — Chart config integration

**Modify:** `tradery-forge/.../ui/charts/IndicatorChartsManager.java`

Add `setSpectrumChartEnabled()`, `updateSpectrumChart()`, `redrawSpectrumChart()` following the existing RSI/Delta pattern.

**Modify:** chart config API to support:
```json
{ "indicators": { "SPECTRUM": { "enabled": true, "mode": "VOLUME" } } }
```

---

### Phase 10: DSL Functions (defer to later)

Bucket index = floor(log10(notional)): 0=$1, 1=$10, 2=$100, 3=$1K, 4=$10K, 5=$100K, 6=$1M

#### Range functions (min/max bucket indices)

| Function | Returns | Description |
|----------|---------|-------------|
| `SPECTRUM_VOLUME(min, max)` | double | Total notional in bucket range [min..max] for current candle |
| `SPECTRUM_COUNT(min, max)` | int | Trade count in bucket range [min..max] |
| `SPECTRUM_DELTA(min, max)` | double | Buy - sell notional in bucket range [min..max] |

#### Single-bucket / threshold functions

| Function | Returns | Description |
|----------|---------|-------------|
| `SPECTRUM_COUNT_ABOVE(bucket)` | int | Trade count in buckets >= bucket |
| `SPECTRUM_COUNT_AT(bucket)` | int | Trade count in exactly that bucket |
| `SPECTRUM_VOLUME_ABOVE(bucket)` | double | Total volume from buckets >= bucket |
| `SPECTRUM_VOLUME_AT(bucket)` | double | Volume in exactly that bucket |

#### Derived ratio

| Function | Returns | Description |
|----------|---------|-------------|
| `WHALE_RATIO(bucket)` | double | `SPECTRUM_VOLUME_ABOVE(bucket) / SPECTRUM_VOLUME_ABOVE(0)` (0-1 ratio) |

#### Example conditions

```
# Whale activity detection
SPECTRUM_COUNT_ABOVE(5) > 10 AND RSI(14) < 40        # 10+ whale trades ($100K+) while oversold
SPECTRUM_COUNT_AT(6) > 0                               # At least one $1M+ trade this candle
WHALE_RATIO(5) > 0.6                                   # Whales are 60%+ of total volume

# Size-based flow divergence
SPECTRUM_DELTA(5, 7) > 0 AND SPECTRUM_DELTA(1, 3) < 0 # Whales buying, retail selling
SPECTRUM_VOLUME_ABOVE(5) > SPECTRUM_VOLUME_ABOVE(5)[1] * 2  # Whale volume doubled vs prev bar

# Absorption detection
SPECTRUM_COUNT_ABOVE(5) > 50 AND ATR(1) < ATR(14) * 0.5  # High whale count, small price move
```

#### Touch points

1. `Lexer.java` — add SPECTRUM_VOLUME, SPECTRUM_COUNT, SPECTRUM_DELTA, SPECTRUM_COUNT_ABOVE, SPECTRUM_COUNT_AT, SPECTRUM_VOLUME_ABOVE, SPECTRUM_VOLUME_AT, WHALE_RATIO keywords
2. `Parser.java` — parse spectrum functions with 1 or 2 args
3. `AstNode.java` — add SpectrumFunctionCall node
4. `ConditionEvaluator.java` — dispatch to IndicatorEngine spectrum methods
5. `IndicatorEngine.java` — spectrum data getter methods per bar
6. `DslHelpDialog.java` — help content

---

## Edge Cases

| Scenario | Handling |
|----------|----------|
| No aggTrades exist | Spectrum pages return empty data (READY state, 0 records). No fake data. |
| Partial aggTrades coverage | Spectrum only covers ranges with aggTrades. Coverage tracking reflects gaps accurately. |
| Existing aggTrades, no spectrum (migration) | Auto-detect gap → backfill from aggTrades → correct spectrum data. No approximations. |
| Cross-exchange trades | Use `normalizedNotional()` for consistent USD values when `normalizedPrice` is set. |
| Concurrent writes | SQLite WAL mode + `executeInTransaction()` serializes writes. |
| Sub-dollar trades (bucket < 0) | Bucket 0 catches $1–$10. Sub-dollar trades get negative indices — valid, just rare. |
| Backfill of months of data | Stream aggTrades in 10K chunks — never load entire range into memory. |

---

## Verification Plan

1. **Compile**: `./gradlew compileJava` after each phase
2. **Ingestion test**: Fetch aggTrades for a symbol → verify spectrum.db created with rows
3. **Backfill test**: Symbol with existing aggTrades → `POST /spectrum/backfill` → verify spectrum populated
4. **Page test**: Request spectrum page in Forge → verify LOADING → READY lifecycle
5. **API test**: `GET /spectrum?symbol=BTCUSDT&from=...&to=...&timeframe=1h&mode=VOLUME` → verify response
6. **Data management**: Open DataManagementDialog → verify spectrum appears in browser, heatmap, can be deleted
7. **Preloading**: Enable orderflow → verify spectrum auto-queues after aggTrades load
8. **Chart**: Enable spectrum indicator → verify spectrogram renders with correct bucket distribution
9. **Download dashboard**: Verify SPECTRUM events appear in log panel with filtering

---

## File Summary

### New Files (9)
| File | Module | Purpose |
|------|--------|---------|
| `SizeBucket.java` | tradery-core/model | Bucket record with merge/delta |
| `SpectrumWindow.java` | tradery-core/model | 10s window record with bucket map |
| `SpectrumMode.java` | tradery-core/model | COUNT/VOLUME/DELTA enum |
| `SpectrumDao.java` | tradery-data-service/dao | SQLite DAO for spectrum table |
| `SpectrumAggregator.java` | tradery-data-service/data | AggTrade → 10s histogram processor + backfill |
| `SpectrumStore.java` | tradery-data-service/data | Cache + gap-fill store (like FundingRateStore) |
| `SpectrumHandler.java` | tradery-data-service/api | HTTP API handler |
| `SpectrumPageManager.java` | tradery-forge/data/page | Forge-side page manager (like FundingPageManager) |
| `SpectrumRenderer.java` | tradery-charts/renderer | Spectrogram heatmap chart |

### Modified Files (15+)
| File | Changes |
|------|---------|
| `DataStoreType.java` | Add SPECTRUM enum + fromCoverageKey |
| `SqliteSchema.java` | Add createSpectrumTables() |
| `SqliteDataStore.java` | Add SpectrumDao to SymbolData, facade methods |
| `AggTradesStore.java` | Hook spectrum aggregation into ingestion |
| `PageManager.java` (data-service) | Add loadSpectrum() dispatch |
| `PageKey.java` | Add isSpectrum() |
| `DataServiceServer.java` | Register /spectrum routes |
| `InventoryHandler.java` | Add spectrum to inventory + deletion |
| `DataType.java` | Add SPECTRUM enum value |
| `PreloadRequest.java` | Add SPECTRUM data type + factory |
| `PreloadScheduler.java` | Add processSpectrum() + auto-queue |
| `DataInventory.java` | Add spectrum coverage tracking |
| `DataBrowserPanel.java` | Add spectrum section to tree |
| `DataHealthPanel.java` | Add spectrum coverage heatmap |
| `DataServiceClient.java` | Add spectrum DTOs + API methods |
| `IndicatorType.java` | Add SPECTRUM |
| `IndicatorChartsManager.java` | Add spectrum chart management |
