# Rotating Ray Trendline System - Research & Design

## The Core Concept

An algorithm for **automatic descending trendline detection**:

1. Start from the All-Time High (or highest point in lookback, excluding recent bars)
2. Imagine a ray pointing straight UP from that peak
3. Rotate the ray CLOCKWISE until it touches the next significant peak
4. That peak becomes the END of Ray 1 and the START of Ray 2
5. From that new peak, start another vertical ray, rotate clockwise
6. Continue until reaching current price
7. Each ray has its **own slope** - they form a connected chain, not uniform angles
8. **Breaking a ray** = potential trend change signal

```
ATH ○━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ray1 projection (flattest)
     \
      \  ray1
       \
        ○ Peak1
         \
          \━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ray2 projection
           \
            \  ray2
             \
              \
               ○ Peak2
                \
                 \━━━━━━━━━━━━━━━━━  ray3 projection
                  \
                   \  ray3
                    \
                     \
                      \
                       ○ Peak3
                        \
                         \━━━━━━  ray4 projection
                          \
                           ○ current price
```

**Key:** Each ray connects two peaks and has its own unique slope based on where the market actually put those peaks. Rays are NOT uniform - they reflect the character of each decline phase.

---

## The Skip Parameter Problem

**Problem:** If we calculate rays from the highest point in lookback, we can NEVER be "above" that point - because if we were, it would become the new ATH and reset everything.

**Solution:** Skip recent bars when finding the ATH:

```
         ATH found in this window                    skip zone
    |◄───────────────────────────────────────►|◄──────────────►|

    ○ ATH
     \
      \  ray1
       \
        ○━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━○ current
         ray1 projection extends into skip zone    (can be above ray!)
```

With `skip=5`:
- Find ATH in bars `[lookback ... 5]` (excluding last 5 bars)
- Rays project forward into the "skip zone"
- Current price (in skip zone) can freely be above/below rays
- Structure remains stable as price moves

This is similar to how `RANGE_POSITION(period, skip)` already works in the DSL.

---

## Ray Hierarchy & Importance

Not all rays are equal. The **ATH ray is the most significant**:

```
ATH ○━━━━━━━━━━━━━━━━━━━━━━━━━  ray1: THE big one (break = regime change)
     \                           ↑ magnet effect
      \                          |
       ○━━━━━━━━━━━━━━━━━━━━━━   ray2: significant
        \                        ↑ magnet
         \                       |
          ○━━━━━━━━━━━━━━━━━━    ray3: moderate importance
           \                     ↑ magnet
            \                    |
             ○━━━━━━━━━━━━━━     ray4: early warning
              \                  ↑ break this first...
               \                 |
                ○ current price
```

| Ray Position | Break Meaning | Significance |
|--------------|---------------|--------------|
| ray1 (ATH) | Regime change | Highest - full trend reversal |
| ray2 | Trend building | High - momentum confirmed |
| ray3 | Continuation | Moderate - building strength |
| ray4+ (recent) | Early signal | Lower - but precursor to bigger moves |

---

## The Magnet Effect

Rays act as **attractors**. Breaking one ray pulls price toward the next:

1. Break ray4 → price gets "pulled" toward ray3
2. Even if ray3 holds → the TEST happened, momentum was there
3. Failed test at ray3 → potential exit signal (resistance confirmed)
4. Break ray3 → now ray2 becomes the magnet
5. Eventually testing the ATH ray

**Trading implications:**
- Enter on ray4 break
- Target ray3 as first take-profit (even if it won't break)
- If ray3 breaks, trail stop and target ray2
- Failed test at higher ray = exit signal

---

## The Cascade Effect

When a shorter ray breaks (more recent peak), the probability of breaking older rays increases:

```
Break ray4 ──► 60% chance of testing ray3
Break ray3 ──► 70% chance of testing ray2
Break ray2 ──► 80% chance of testing ray1
Break ray1 ──► Full trend reversal
```

**Why cascades happen:**
- Shorter rays (recent peaks) are "weaker" resistance
- Less historical weight, fewer traders anchored there
- Each break builds momentum and buyer confidence
- Shorts start covering → self-reinforcing loop

**Cascade speed matters:**
- 2+ rays broken in 5 bars = strong momentum, likely continues
- 2+ rays broken in 50 bars = slow grind, less conviction

---

## Phases as Primary Interface

Rather than complex DSL functions, expose the ray system through **phases**:

### Position Phases
```
above-all-rays       # Price above even the ATH ray - full bull mode
above-ath-ray        # Broke the big one - regime change confirmed
above-2-rays         # Solid momentum building
above-1-ray          # Early reversal signal
below-all-rays       # Full downtrend intact
```

### Event Phases
```
ray-break            # A ray was broken this bar
ray-cascade          # 2+ rays broken in last N bars
ray-rejection        # Tested a ray and got rejected
```

### Proximity Phases
```
near-ath-ray         # Within X% of the ATH ray (big test coming)
near-any-ray         # Approaching resistance
```

### Strategy Example

```json
{
  "entrySettings": {
    "condition": "RSI(14) < 40 AND volume > AVG_VOLUME(20)"
  },
  "phaseSettings": {
    "requiredPhaseIds": ["above-2-rays", "weekdays"],
    "excludedPhaseIds": ["near-ath-ray"]
  }
}
```

*"Enter on RSI dip, but only if we've already broken 2 rays, and NOT if we're about to test the ATH ray (likely rejection)"*

### Why Phases Work Well

- Integrates with existing phase infrastructure
- Can run on different timeframe (daily rays, hourly entries)
- Easy to combine with other phases (uptrend, sessions, etc.)
- Clear semantics for required/excluded
- Skip parameter can be configured per-phase

---

## Dual Ray System (Resistance + Support)

Two parallel ray systems work together:

```
RESISTANCE RAYS (from ATH, clockwise)        SUPPORT RAYS (from ATL, counter-clockwise)

ATH ○━━━━━━━━━━━━━━ res_ray1                                        ○ current
     \                                                             /
      \                                                           /
       ○━━━━━━━━━━ res_ray2                            sup_ray3 ━○
        \                                                       /
         \                                                     /
          ○━━━━━ res_ray3                           sup_ray2 ━○
           \                                                 /
            \                                               /
             ○ current                        sup_ray1 ━━━○ ATL
```

**Ray numbering convention:**
- `ray 1` = ATH/ATL ray (oldest, most significant)
- `ray 2` = next in chain
- `ray N` = most recent (closest to current price)

---

## DSL Functions

### Resistance Rays (breaking UP = bullish)

```dsl
# State: Is price above this ray?
RESISTANCE_RAY_BROKEN(ray, lookback, skip)    # 1 if price above ray, 0 if not
RESISTANCE_RAY_BROKEN(1, 200, 5)              # Is price above ATH ray?

# Event: Did price cross through this ray THIS bar?
RESISTANCE_RAY_CROSSED(ray, lookback, skip)   # 1 if crossed this bar
RESISTANCE_RAY_CROSSED(1, 200, 5)             # Did price cross above ATH ray this bar?

# Distance from price to ray
RESISTANCE_RAY_DISTANCE(ray, lookback, skip)  # % distance (negative = below)
RESISTANCE_RAY_DISTANCE(1, 200, 5)            # How far from ATH ray?

# Count functions (no ray number)
RESISTANCE_RAYS_BROKEN(lookback, skip)        # Count of rays price is above
RESISTANCE_RAY_COUNT(lookback, skip)          # How many resistance rays exist
```

### Support Rays (breaking DOWN = bearish)

```dsl
# State: Is price below this ray?
SUPPORT_RAY_BROKEN(ray, lookback, skip)       # 1 if price below ray, 0 if not
SUPPORT_RAY_BROKEN(1, 200, 5)                 # Is price below ATL ray?

# Event: Did price cross through this ray THIS bar?
SUPPORT_RAY_CROSSED(ray, lookback, skip)      # 1 if crossed this bar
SUPPORT_RAY_CROSSED(1, 200, 5)                # Did price cross below ATL ray this bar?

# Distance from price to ray
SUPPORT_RAY_DISTANCE(ray, lookback, skip)     # % distance (positive = above)
SUPPORT_RAY_DISTANCE(1, 200, 5)               # How far from ATL ray?

# Count functions
SUPPORT_RAYS_BROKEN(lookback, skip)           # Count of rays price is below
SUPPORT_RAY_COUNT(lookback, skip)             # How many support rays exist
```

### Example Conditions

**Strong uptrend - above resistance, holding support:**
```dsl
RESISTANCE_RAYS_BROKEN(200, 5) >= 2 AND SUPPORT_RAYS_BROKEN(200, 5) == 0
```

**Conservative entry - only after ATH ray broken:**
```dsl
RESISTANCE_RAY_BROKEN(1, 200, 5) == 1 AND RSI(14) < 40
```

**Entry on crossing event with volume:**
```dsl
RESISTANCE_RAY_CROSSED(1, 200, 5) == 1 AND volume > AVG_VOLUME(20) * 1.5
```

**Breakdown starting - losing support:**
```dsl
SUPPORT_RAY_CROSSED(1, 200, 5) == 1
```

**Approaching ATH ray (caution zone):**
```dsl
RESISTANCE_RAY_BROKEN(1, 200, 5) == 0 AND RESISTANCE_RAY_DISTANCE(1, 200, 5) > -3.0
```

**Choppy/ranging - broken both directions:**
```dsl
RESISTANCE_RAYS_BROKEN(200, 5) >= 1 AND SUPPORT_RAYS_BROKEN(200, 5) >= 1
```

---

## Similar Concepts in Technical Analysis

### 1. Convex Hull Trendlines

**What it is:** Mathematical concept - the "rubber band" stretched around price peaks

```
         ○ ATH
        / \
       /   ○───○
      /         \
     ○           \
                  ○ current
```

The upper convex hull connects peaks such that no price data exists above the line segments. The "rotating ray" algorithm naturally finds this hull.

**Difference:** Convex hull is static geometry; rays are sequential and can be analyzed individually (age, slope, break sequence).

---

### 2. Tom DeMark TD Lines (TD Supply/Demand Lines)

**What it is:** Automatic trendline drawing connecting "TD Points" (specific pivot definitions)

**TD Point rules:**
- TD Point High: High surrounded by lower highs on both sides
- Connect most recent two TD Point Highs for resistance line

```
      ○ TD Point      ○ TD Point
     /|\             /|\
    / | \           / | \
   /  |  \         /  |  \
      |                |
      └────────────────┘  TD Supply Line
```

**Similarity:** Automatic peak detection + trendline drawing
**Difference:** DeMark uses fixed pivot rules; rotating ray uses geometric rotation

**Reference:** "The New Science of Technical Analysis" by Tom DeMark

---

### 3. Darvas Box Theory

**What it is:** Nicolas Darvas's method of boxing price consolidations

```
┌─────────────┐
│    HIGH     │ ← Box top (resistance)
│             │
│  ○ ○  ○ ○   │ ← Price consolidates
│             │
│    LOW      │ ← Box bottom (support)
└─────────────┘
       │
       ▼ Breakout!
```

**Trading rule:** Buy when price breaks above the box top

**Similarity:** Identifies resistance levels that, when broken, signal momentum
**Difference:** Boxes are horizontal; rays are angled

---

### 4. Andrews' Pitchfork (Median Line Method)

**What it is:** Three parallel lines drawn from a significant pivot

```
           /
          / ← Upper parallel
         /
   ○────/──────── Median line
       /\
      /  \
     /    ○ Pivot 2
    ○
  Pivot 1
```

**Similarity:** Uses pivots to project future resistance/support
**Difference:** Fixed angle relationships; rays adapt to each peak

---

### 5. Gann Fan Lines

**What it is:** W.D. Gann's rays at specific angles (1x1, 2x1, 1x2, etc.)

```
         ○ Starting point
        /|\
       / | \
      /  |  \
     /   |   \   1x4
    /    |    \  1x2
   / 1x1 |     \ 1x1
  /      |      \
```

Each line represents a specific price/time ratio.

**Similarity:** Rays emanating from significant points
**Difference:** Gann uses predetermined angles; rotating rays find natural market angles

---

### 6. Linear Regression Channels

**What it is:** Statistical best-fit line with standard deviation bands

```
    ════════════════════ +2 std dev
       ○    ○
    ──────○───○─────────── Regression line
         ○
    ══════════════════════ -2 std dev
```

**Similarity:** Identifies trend direction and deviation
**Difference:** Single line fit to all data; rotating rays create segmented lines

---

### 7. Ichimoku Cloud (Senkou Spans)

**What it is:** Japanese system projecting support/resistance into the future

The "cloud" acts as dynamic support/resistance. Price breaking through the cloud is a major signal.

**Similarity:** Dynamic resistance levels; breaking through = signal
**Difference:** Based on moving averages, not peak connections

---

### 8. Point and Figure Trendlines

**What it is:** 45-degree trendlines on P&F charts

```
    X
    X O
    X O X
    X O X O
  ──X─O─X─O──  45° support line
      O X
        X
```

**Similarity:** Automatic trendline construction from price action
**Difference:** Fixed 45° angles on non-time-based charts

---

## Algorithmic Relatives

### A. Peak Detection Algorithms

The "rotating ray" implicitly does peak detection. Common methods:

| Method | Description |
|--------|-------------|
| **Local maxima** | Point higher than N neighbors on each side |
| **Zig-zag indicator** | Only marks peaks with > X% reversal |
| **Fractals (Bill Williams)** | 5-bar pattern: middle bar highest |
| **Pivot Points** | High/low of specific periods |

### B. Support/Resistance Detection

| Method | Description |
|--------|-------------|
| **Volume Profile POC** | Price level with most trading volume |
| **Fibonacci retracements** | Mathematical ratios from swing high/low |
| **Round numbers** | Psychological levels ($50k, $100k) |
| **Historical pivots** | Previous day/week/month high/low |

### C. Breakout Detection

| Method | Description |
|--------|-------------|
| **Donchian Channel** | Break of N-bar high/low |
| **Bollinger Band break** | Close outside bands |
| **ATR breakout** | Move > N × ATR from reference |
| **Volume confirmation** | Break + above-average volume |

---

## Reverse Direction (Support Rays)

Same algorithm from All-Time LOW, rotating COUNTER-clockwise:

```
                      ○ current price
                     /
                    /  ray3
                   /
          ○───────○ ray2
         /
        /
       /  ray1
      /
ATL  ○━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

Breaking DOWN through these = bearish signal.

Phases:
- `below-all-support-rays` - broke all support, full bear mode
- `below-1-support-ray` - early breakdown signal

---

## Chart Visualization

Toggle-able indicator overlay on the price chart (like other indicators).

### Visual Elements

```
Price Chart with Rays
─────────────────────────────────────────────────────────────
     ○ ATH                                    [✓] Show Rays
      \
       \━━━━━━━━━━━━━━━━━━━━━ res_ray1 (dark red, solid → dashed)
        \      ╭─╮
         ○────╯  ╰─╮
          \━━━━━━━━╰──━━━━━━ res_ray2 (red)
           \        ╭──╮
            \      ╯   ╰╮
             ○━━━━━━━━━━╰━━ res_ray3 (orange) BROKEN → grayed
              ╲          ╭╮
               ╲        ╯ ╰─ current price
                ╲      ╱
                 ○────○━━━━━ sup_ray3 (light green) BROKEN → grayed
                      ╲
                       ○━━━━ sup_ray2 (green)
                        ╲
                         ○━ sup_ray1 (dark green)
                        ATL
─────────────────────────────────────────────────────────────
```

### Line Styles

| Element | Style |
|---------|-------|
| Unbroken ray | Solid line |
| **Broken ray** | **Dashed line, brighter color** |
| Ray projection (into future) | Thinner / semi-transparent |
| Peak points | Small circle markers |

### Color Scheme

Broken rays are **dashed + brighter** to highlight "this level was conquered":

| Ray Type | Unbroken | Broken |
|----------|----------|--------|
| Resistance ray1 (ATH) | Solid dark red | Dashed bright red |
| Resistance ray2 | Solid red | Dashed bright red |
| Resistance ray3+ | Solid orange | Dashed bright orange |
| Support ray1 (ATL) | Solid dark green | Dashed bright green |
| Support ray2 | Solid green | Dashed bright green |
| Support ray3+ | Solid light green | Dashed lime |

```
Unbroken:  ────────────────  (solid, standard)
Broken:    ╌ ╌ ╌ ╌ ╌ ╌ ╌ ╌  (dashed, brighter - draws attention)
```

### UI Integration

- Checkbox toggle in indicator panel: `[✓] Rotating Rays`
- When enabled, draws rays on main price chart
- Settings popup for: lookback, skip, show/hide broken rays

---

## Implementation Considerations

### 1. Peak Detection Method
- Use candle HIGHs (wicks) for peaks
- Minimum distance between peaks to avoid noise (e.g., 3 bars apart)
- Or require X% reversal between peaks

### 2. Ray Projection
- Each ray defined by: start peak (bar, price) → end peak (bar, price)
- Ray value at any bar: linear interpolation/extrapolation
- `ray_price = start_price + slope * (current_bar - start_bar)`

### 3. "Breaking" Definition
- **Close above:** More confirmation, fewer false signals (recommended)
- **High above:** Earlier signal, more noise

### 4. Skip Parameter
- Default: 5 bars (gives structure stability)
- Configurable per phase/function
- Similar to RANGE_POSITION skip

### 5. Caching Strategy
- Rays only change when new peaks form outside skip zone
- Cache ray definitions, update incrementally
- Invalidate when lookback window shifts

---

## Open Questions

1. **Minimum peak prominence?** Should small wiggles count, or require X% reversal?

2. **Visualization?** Draw the rays on the chart in Tradery UI?

3. **Default skip value?** 5 bars? 10? Percentage of lookback?

4. **Slope analysis?** Track ray slopes for regime characterization? (maybe v2)

---

## Next Steps

1. **Prototype the algorithm** - Java class to calculate rays, validate geometry
2. **Add as phases** - Implement `above-N-rays` phases first
3. **Add DSL functions** - `RAYS_BROKEN`, `RAY_DISTANCE` for fine control
4. **Visualize** - Draw rays on price chart for debugging/validation
5. **Backtest** - Validate cascade observation with real data

---

## References for Further Reading

- **Tom DeMark** - "The New Science of Technical Analysis" (TD Lines)
- **Nicolas Darvas** - "How I Made $2,000,000 in the Stock Market" (Box Theory)
- **W.D. Gann** - "Truth of the Stock Tape" (Gann Angles)
- **Alan Andrews** - Median Line studies (Pitchfork)
- **Convex Hull algorithms** - Graham scan, gift wrapping (computational geometry)
