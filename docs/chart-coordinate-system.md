# Chart Coordinate System

How candles, footprint heatmap, and daily volume profile overlays are positioned on the X (time) and Y (price) axes.

## Time Bucket Model

Every data element on the chart occupies a **time bucket**: `[timestamp, timestamp + interval)`.

- `timestamp` = candle open time (epoch ms), e.g. a 15m candle at 14:00 has `timestamp = 14:00`
- `interval` = candle duration in ms (e.g. 900,000 for 15m)

The bucket defines the logical time range. Each overlay fills a portion of this bucket, centered horizontally.

## Candle Positioning (JFreeChart CandlestickRenderer)

JFreeChart places candle bodies **centered on the timestamp** with an auto-calculated width (max ~7px).

```
Time bucket:  [timestamp .................. timestamp + interval)

Candle body:         |===========|
                     ↑ centered on timestamp
```

The candle body does NOT span the full bucket — it's a narrow bar centered on the point.

## Footprint Heatmap Positioning

Footprint buckets use **80% of the time bucket, centered** (10% padding each side).

```
Time bucket:  [timestamp .................. timestamp + interval)

Footprint:      |----|========================|----|
                 10%         80% filled          10%
```

Code (`FootprintHeatmapAnnotation.drawFootprint`):
```java
long candleInterval = (long) (halfIntervalMs / 0.4);
double leftX  = domainAxis.valueToJava2D(timestamp + candleInterval * 0.1, ...);
double rightX = domainAxis.valueToJava2D(timestamp + candleInterval * 0.9, ...);
```

`halfIntervalMs` = `candleInterval * 0.4`, derived from `estimateCandleInterval()` which measures the minimum gap between consecutive footprint timestamps.

## Daily Volume Profile Positioning

Daily volume profile histograms span a full UTC day `[dayStart, dayStart + 86,400,000ms)`. Each histogram is drawn as horizontal bars extending rightward from the day's left edge, width controlled by `histogramWidth` (pixels).

## Y-Axis (Price)

All overlays use JFreeChart's `rangeAxis.valueToJava2D(price, dataArea, RectangleEdge.LEFT)` for price-to-pixel conversion. The range axis auto-scales to visible candle data.

### Footprint Y buckets

Each footprint bucket spans `[priceLevel - tickSize/2, priceLevel + tickSize/2)`:

```java
double topY    = rangeAxis.valueToJava2D(priceLevel + tickSize / 2, ...);
double bottomY = rangeAxis.valueToJava2D(priceLevel - tickSize / 2, ...);
```

1px gap between adjacent buckets for visual separation (when height > 2px).

## Profile-to-Candle Alignment

When the chart timeframe exceeds the profile pyramid level (e.g. 15m chart → 5m profiles), multiple profiles fall within each candle's time bucket. The overlay aggregates them:

```
15m candle at 14:00:  [14:00 ........................ 14:15)
                       ↓         ↓         ↓
5m profiles:       [14:00-05) [14:05-10) [14:10-15)
                       └─────── merged ───────┘
```

Code (`FootprintHeatmapOverlay.computeFromProfiles`):
```java
TreeMap<Long, RawProfile> profileByTimestamp = new TreeMap<>(...);
var matchingProfiles = profileByTimestamp.subMap(candleStart, candleEnd);
var profile = mergeProfiles(matchingProfiles.values());
```

The fetch range extends to `lastCandle.timestamp() + candleInterval - 1` to include profiles within the last candle's full window.

### Profile Pyramid Levels

`ProfileHandler.resolveProfileTimeframe()` maps chart timeframes to the largest pyramid level that fits:

| Chart TF | Resolved Profile TF |
|----------|---------------------|
| 1m       | 1m                  |
| 5m       | 5m                  |
| 15m      | 5m                  |
| 30m      | 30m                 |
| 1h       | 1h                  |
| 4h       | 4h                  |
| 1d       | 1d                  |

Pyramid levels stored in SQLite: `10s, 1m, 5m, 30m, 1h, 4h, 1d`.
