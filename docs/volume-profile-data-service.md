# Volume Profile Data Service — Implementation Spec

## Problem

Volume profile histograms (daily profiles, footprint heatmaps, floating POC) are computed on-the-fly from raw aggTrades at chart render time. For a single day of BTCUSDT at 1-second resolution, this means scanning ~500K+ trades every time the chart redraws. This is:

- **Slow** — scanning millions of trades to compute POC/VAH/VAL per visible candle
- **Redundant** — the same trades get re-bucketed every scroll/zoom/resize
- **Memory-intensive** — raw aggTrade arrays must be held in memory during computation

## Solution

Precompute canonical **volume-at-price profiles** at 10-second resolution using exchange-defined tick sizes, then aggregate into a time pyramid. Clients request pre-aggregated profiles and derive POC/VAH/VAL/histograms at query time with minimal work.

---

## Data Model

### CandleProfile (one 10-second window)

```
startTime:  1709251200000  (epoch ms, aligned to 10s boundary)
endTime:    1709251210000
tickSize:   0.10           (exchange-defined, e.g. BTCUSDT = $0.10)
levels:     Map<int priceTick, LevelVolume>
```

### LevelVolume

```
buyVolume:   54.2   (taker buy = !isBuyerMaker)
sellVolume:  43.1   (taker sell = isBuyerMaker)
```

### Integer Tick Encoding

Prices are stored as integer ticks: `priceTick = round(price / tickSize)`

Example: BTCUSDT at $97,123.40 with tickSize=0.10 → priceTick = 971234

This gives exact price-level resolution with integer arithmetic and compact storage.

---

## Time Pyramid

```
AggTrades (raw)
    │ bucket by tick, flush per 10s
    ▼
10s profiles (canonical, stored)
    │ merge 6×10s
    ▼
1m profiles (stored)
    │ merge 5×1m
    ▼
5m profiles (stored)
    │ merge 6×5m
    ▼
30m profiles (stored)
    │ merge 2×30m
    ▼
1h profiles (stored)
    │ merge 4×1h
    ▼
4h profiles (stored)
    │ merge 6×4h
    ▼
1d profiles (stored)
```

All levels are persisted in SQLite. Higher timeframes are computed by merging child maps — for each tick present in any child, sum `buyVolume` and `sellVolume`.

### Why store all levels?

Merging 6 ten-second profiles → 1 minute is trivial. But merging 8,640 ten-second profiles → 1 day at query time is expensive. Storing all levels trades ~30% more disk for O(1) access at any timeframe.

**Estimated storage** (BTCUSDT, 6 months):
| Level | Rows | Avg BLOB | Total |
|-------|------|----------|-------|
| 10s | 1.55M | ~400B | ~620MB |
| 1m | 259K | ~500B | ~130MB |
| 5m | 52K | ~600B | ~31MB |
| 30m+ | <20K | ~800B | ~16MB |
| **Total** | | | **~800MB** |

---

## SQLite Schema

New file per symbol: `~/.tradery/data/{SYMBOL}/volume_profiles.db`

```sql
CREATE TABLE IF NOT EXISTS volume_profiles (
    timeframe TEXT NOT NULL,         -- '10s','1m','5m','30m','1h','4h','1d'
    window_start INTEGER NOT NULL,   -- epoch ms, aligned to window boundary
    tick_size REAL NOT NULL,         -- exchange tick size used for this row
    total_buy_volume REAL NOT NULL DEFAULT 0,
    total_sell_volume REAL NOT NULL DEFAULT 0,
    level_count INTEGER NOT NULL DEFAULT 0,
    profile_data BLOB NOT NULL,      -- msgpack: Map<int, [double, double]>
    PRIMARY KEY (timeframe, window_start)
) WITHOUT ROWID;
```

The `profile_data` BLOB format is MessagePack:
```
{
  971230: [54.2, 43.1],   // priceTick → [buyVolume, sellVolume]
  971231: [12.0, 8.5],
  971232: [30.0, 25.0],
  ...
}
```

### Coverage tracking

Uses the standard `data_coverage` table (present in every data DB):
- `data_type = "volume_profiles"`, `sub_key = ""`
- Coverage ranges track which time windows have been profiled
- Incremental updates only compute for gap ranges

---

## Tick Size Resolution

Exchange-defined tick sizes are stored in `symbols.db` via a V3 schema migration:

```sql
ALTER TABLE trading_pairs ADD COLUMN tick_size REAL NOT NULL DEFAULT 0;
```

Extracted from Binance `/fapi/v1/exchangeInfo` → `filters[].PRICE_FILTER.tickSize` during periodic symbol sync.

A `TickSizeResolver` class provides `getTickSize(symbol)` with:
1. Primary: query `trading_pairs` table
2. Fallback: hardcoded map for top symbols (BTCUSDT=0.10, ETHUSDT=0.01, etc.)

---

## Page System Integration

Profiles are a **first-class data type** in the page system, delivered via WebSocket like all other types.

### DataType enum addition

```java
VOLUME_PROFILE("Profile")   // wire format: "PROFILE"
```

### PageKey additions

```java
public boolean isProfile() { return "PROFILE".equals(dataType); }

// Factory methods
PageKey.liveProfile("BTCUSDT", "1h", windowDurationMs)
PageKey.anchoredProfile("BTCUSDT", "1h", endTime, windowDurationMs)
```

### Load flow

```
Client subscribes to PROFILE page via WebSocket
    │
    ▼
PageManager.loadPage() dispatches to loadProfiles()
    │
    ├─→ ensureCached(): make sure aggTrades exist in SQLite
    ├─→ Check profile coverage gaps
    ├─→ VolumeProfileComputer.compute() for any gaps
    ├─→ Query VolumeProfileDao for requested timeframe/range
    ├─→ Serialize as msgpack array of ProfileRows
    │
    ▼
Page.setData(bytes) → READY
    │
    ▼
WebSocket delivers binary msgpack to client
```

### Client usage

Uses the generic `DataPage<T>` class (`tradery-data/.../page/DataPage.java`) — no new page class needed:

```java
// Create a typed DataPage<ProfileRow>
DataPage<ProfileRow> profilePage = new DataPage<>(DataType.VOLUME_PROFILE, "BTCUSDT", "1h", start, end);

// Subscribe via WebSocket — standard page flow
dataServiceClient.subscribePage(
    DataType.VOLUME_PROFILE, "BTCUSDT", "1h",
    startTime, endTime,
    new DataPageCallback() {
        void onData(byte[] msgpackData, long recordCount) {
            List<ProfileRow> profiles = deserialize(msgpackData);
            profilePage.setData(profiles);  // DataPage<ProfileRow> holds the typed data
        }
    }
);
```

---

## HTTP API Endpoints

Three endpoints for direct querying (complement WebSocket delivery):

### GET /profile

Raw tick-level profiles for a time range.

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| symbol | string | yes | e.g. BTCUSDT |
| timeframe | string | yes | 10s, 1m, 5m, 30m, 1h, 4h, 1d |
| start | long | yes | epoch ms |
| end | long | yes | epoch ms |

**Response:**
```json
{
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "tickSize": 0.10,
  "profiles": [
    {
      "windowStart": 1709251200000,
      "totalBuyVolume": 1234.5,
      "totalSellVolume": 1100.3,
      "levelCount": 42,
      "levels": {
        "971230": [54.2, 43.1],
        "971231": [12.0, 8.5]
      }
    }
  ]
}
```

### GET /profile/binned

Derived histogram with POC/VAH/VAL.

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| symbol | string | yes | | |
| timeframe | string | yes | | |
| start | long | yes | | |
| end | long | yes | | |
| mode | string | no | BIN_COUNT | BIN_COUNT or PRICE_DELTA |
| binParam | double | no | 96 | Number of bins (BIN_COUNT) or bin width in $ (PRICE_DELTA) |
| valueAreaPct | double | no | 70 | Value area percentage |

**Response:**
```json
{
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "start": 1709251200000,
  "end": 1709254800000,
  "tickSize": 0.10,
  "poc": 97123.0,
  "vah": 97180.0,
  "val": 97050.0,
  "totalBuyVolume": 1234.5,
  "totalSellVolume": 1100.3,
  "delta": 134.2,
  "bins": {
    "count": 96,
    "priceLevels": [97000.0, 97002.08, ...],
    "buyVolumes": [12.3, 5.6, ...],
    "sellVolumes": [10.1, 7.2, ...]
  }
}
```

### GET /profile/poc-series

POC price over time for charting.

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| symbol | string | yes | | |
| timeframe | string | yes | | |
| start | long | yes | | |
| end | long | yes | | |
| compositeDays | int | no | 0 | Rolling N-day composite (0 = per-window POC) |

**Response:**
```json
{
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "points": [
    {"timestamp": 1709251200000, "poc": 97123.0, "volume": 2334.8},
    {"timestamp": 1709254800000, "poc": 97145.0, "volume": 1890.2}
  ]
}
```

---

## Query-Time Derivations

All derived from the stored tick maps — no raw trade scanning needed.

### POC (Point of Control)
Tick with highest total volume (buy + sell).

### VAH / VAL (Value Area High / Low)
Starting from POC, expand outward comparing adjacent tick volumes until `valueAreaPct` (default 70%) of total volume is captured. VAH = upper bound, VAL = lower bound.

### Fixed Bin-Count Histogram
Divide price range [min_tick, max_tick] into N equal-width bins. Sum tick volumes into their bin.

### Fixed Price-Delta Histogram
Create bins of fixed width (e.g. $50). Each bin spans a $50 price range.

### Rolling Composite POC
Merge N days of profiles into one composite map, then find POC of the composite.

### Imbalances
For each tick level: `imbalanceRatio = buyVolume / sellVolume`. Significant if > 3.0 (buy) or < 0.33 (sell).

### Delta
Per-window: `totalBuyVolume - totalSellVolume`. Per-tick: `buyVolume - sellVolume`.

---

## Computation Flow

### Initial backfill

```
POST request PROFILE page for 6-month range
    → PageManager.loadProfiles()
    → aggTradesStore.ensureCached() for full range
    → Check profile coverage → gaps = full range
    → VolumeProfileComputer.compute(symbol, start, end):
        1. Resolve tickSize from SymbolsDB
        2. Stream aggTrades in 10k chunks
        3. For each trade:
           - priceTick = round(price / tickSize)
           - isBuyerMaker? → sellVolume : buyVolume
           - Add to current 10s window accumulator
           - On window boundary: flush → serialize → batch buffer
        4. Every 1000 rows: upsert batch to SQLite
        5. After all 10s rows: pyramid aggregation
           - For each parent TF: read child rows, merge maps, upsert
        6. Record coverage
```

### Incremental update

```
New aggTrades arrive (live or historical backfill)
    → loadProfiles() checks coverage
    → Only computes for uncovered time ranges
    → Re-aggregates affected parent windows
```

---

## Non-Goals

- **No OHLCV fallback** — profiles require real trades (aggTrades). If aggTrades aren't available, return empty.
- **No normalized fixed 1024 bins** — binning is done at query time with configurable params.
- **No per-user bin persistence** — bins are computed per-request from the canonical tick map.

---

## File Inventory

### New files (6)

| Path | Purpose |
|------|---------|
| `tradery-data-service/.../profile/TickSizeResolver.java` | Exchange tick size resolution |
| `tradery-data-service/.../profile/VolumeProfileComputer.java` | Compute profiles from aggTrades + pyramid |
| `tradery-data-service/.../profile/ProfileSerializer.java` | Msgpack encode/decode for tick maps |
| `tradery-data-service/.../profile/VolumeProfileAnalyzer.java` | Query-time POC/VAH/VAL/binning |
| `tradery-data-service/.../data/sqlite/dao/VolumeProfileDao.java` | SQLite DAO |
| `tradery-data-service/.../api/ProfileHandler.java` | HTTP endpoints |

### Modified files (11)

| Path | Change |
|------|--------|
| `tradery-data-service/.../symbols/TradingPair.java` | Add `tickSize` field |
| `tradery-data-service/.../data/sqlite/SymbolsSchema.java` | V3 migration |
| `tradery-data-service/.../data/sqlite/dao/SymbolDao.java` | Read/write tick_size |
| `tradery-data-service/.../symbols/SymbolSyncService.java` | Extract PRICE_FILTER |
| `tradery-data-service/.../data/sqlite/DataStoreType.java` | Add VOLUME_PROFILES |
| `tradery-data-service/.../data/sqlite/SqliteSchema.java` | Add table creation |
| `tradery-data-service/.../data/sqlite/SqliteDataStore.java` | Wire DAO + facade methods |
| `tradery-data/.../page/DataType.java` | Add VOLUME_PROFILE |
| `tradery-data/.../page/PageKey.java` | Add isProfile() + factories |
| `tradery-data-service/.../page/PageManager.java` | Add loadProfiles() |
| `tradery-data-service/.../api/DataServiceServer.java` | Register routes |
