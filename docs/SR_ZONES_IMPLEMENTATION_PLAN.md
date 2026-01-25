# Support/Resistance Zone Overlay Implementation

## Overview
Add automatic horizontal S/R zone detection based on swing high/low clustering, displayed as semi-transparent bands on the price chart.

## Approach
- **Detection**: Find local swing highs/lows, cluster nearby prices into zones
- **Visualization**: Semi-transparent horizontal bands (red=resistance, green=support)
- **Filtering**: Show all zones above configurable strength threshold

---

## Files to Create

### 1. Model Classes (`tradery-core/src/main/java/com/tradery/model/`)

**SwingPoint.java**
```java
public record SwingPoint(
    int barIndex,
    long timestamp,
    double price,
    boolean isHigh,
    int dominanceBars  // how many bars this remained the local extreme before being exceeded
) {}
```

**SupportResistanceZone.java**
```java
public record SupportResistanceZone(
    ZoneType type,           // RESISTANCE or SUPPORT
    double priceMin,         // Zone lower bound
    double priceMax,         // Zone upper bound
    double strength,         // Weighted strength (dominance × recency)
    int swingCount,          // Raw number of swings in cluster
    int touchCount,          // Times price entered zone
    List<SwingPoint> swings
) {
    public enum ZoneType { RESISTANCE, SUPPORT }
}
```

**SRZoneSet.java**
```java
public record SRZoneSet(
    List<SupportResistanceZone> resistanceZones,
    List<SupportResistanceZone> supportZones,
    int swingLookback,
    double clusterTolerance
) {}
```

### 2. Indicator (`tradery-core/src/main/java/com/tradery/indicators/SupportResistanceZones.java`)

Algorithm:
1. **Swing detection**: For each bar, check if it's a local max/min over `lookback` bars each direction
2. **Dominance tracking**: For each swing, count how many bars it remained the extreme before being exceeded
3. **Clustering**: Group swings within `tolerance%` of each other
4. **Strength calculation**:
   ```
   strength = Σ (dominanceBars × recencyWeight)
   recencyWeight = 1.0 / (1.0 + barsAgo / halfLifeBars)
   ```
   - Swings that held longer contribute more
   - Recent swings weighted higher, but old proven levels still count
5. **Touch counting**: Count times price entered each zone

### 3. Overlay Classes (`tradery-app/src/main/java/com/tradery/ui/charts/`)

**SRZoneAnnotation.java** - Custom Graphics2D annotation
- Draw semi-transparent rectangles spanning chart width
- Red for resistance, green for support
- Opacity scales with zone strength
- Label shows "S:N T:N" (strength, touches)

**SRZoneOverlay.java** - Following existing pattern
- Enable/disable, parameter setters
- `requestData()` via IndicatorPageManager
- `redraw()` / `clear()` methods
- PageListener for background computation

---

## Files to Modify

### `ChartConfig.java`
Add fields:
```java
private boolean srZonesEnabled = false;
private int srZonesSwingLookback = 5;       // bars each side to confirm swing
private double srZonesClusterTolerance = 1.0;  // percent - group swings within this %
private double srZonesMinStrength = 50.0;   // minimum weighted strength to display
private int srZonesHalfLifeBars = 100;      // recency decay (higher = old swings matter more)
```
Plus getters/setters.

### `ChartStyles.java`
Add colors:
```java
public static final Color SR_RESISTANCE_FILL = new Color(244, 67, 54, 60);
public static final Color SR_SUPPORT_FILL = new Color(76, 175, 80, 60);
```

### `IndicatorType.java`
Add: `SR_ZONES("SR_ZONES", DataDependency.CANDLES)`

### `OverlayManager.java`
- Add `private SRZoneOverlay srZoneOverlay;`
- Instantiate in constructor
- Add `setSrZoneOverlay()` method
- Wire redraw coordination with other overlays

### `ChartsPanel.java`
- Call `overlayManager.setSrZoneOverlay()` in `applySavedOverlays()`
- Add public toggle methods

---

## Implementation Order

1. Create model records (SwingPoint, SupportResistanceZone, SRZoneSet)
2. Create `SupportResistanceZones.java` indicator with detection algorithm
3. Add `SR_ZONES` to IndicatorType enum
4. Create `SRZoneAnnotation.java` for rendering
5. Create `SRZoneOverlay.java` following RayOverlay pattern
6. Add config fields to `ChartConfig.java`
7. Wire into `OverlayManager.java` and `ChartsPanel.java`

---

## Default Parameters
- `swingLookback`: 5 bars (check 5 bars each side for local extremes)
- `clusterTolerance`: 1.0% (group swings within 1% of each other)
- `minStrength`: 50.0 (weighted strength threshold - roughly equivalent to 1 swing that dominated for 50 bars)
- `halfLifeBars`: 100 (a 100-bar-old swing has 50% weight, 200-bar-old has 33% weight, etc.)

**Example strength calculations:**
- Single swing, 100 bars dominance, 50 bars ago: `100 × (1/(1+50/100))` = 67
- Two swings, 30 bars dominance each, 20 bars ago: `2 × 30 × (1/(1+20/100))` = 50
- Old proven level, 200 bars dominance, 300 bars ago: `200 × (1/(1+300/100))` = 50

---

## Verification
1. Run app: `./gradlew run`
2. Load any strategy with candles
3. Enable S/R zones overlay
4. Verify zones appear as semi-transparent bands at swing cluster levels
5. Test parameters (lookback, tolerance, min strength) affect zone detection
6. Verify zones update when data window changes
