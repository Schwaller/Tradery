# ICT (Inner Circle Trader) Concepts - Reference Document

## Overview

ICT (Inner Circle Trader) is a trading methodology created by Michael J. Huddleston that focuses on institutional order flow and market structure. It's extremely popular on YouTube trading education channels and has spawned the broader "Smart Money Concepts" (SMC) movement.

The core premise: **Institutional traders (banks, hedge funds) leave "footprints" in price action that retail traders can learn to read.** These footprints appear as order blocks, liquidity grabs, imbalances, and structure shifts.

---

## ICT Concepts Explained

### 1. Fair Value Gap (FVG)

**What it is:** A 3-candle pattern where price moved so fast that it left an "imbalance" - a gap between candle 1's wick and candle 3's wick.

**Visual:**
```
Bullish FVG:           Bearish FVG:
       ┌──┐                ┌──┐
       │  │                │  │ <- Candle 1
   GAP │  │ <- Candle 2    └──┘
       │  │                GAP
       └──┘                ┌──┐
                           │  │ <- Candle 3
                           │  │
                           └──┘
```

**Detection Logic:**
- Bullish FVG: `candle[0].low > candle[2].high` (gap up)
- Bearish FVG: `candle[0].high < candle[2].low` (gap down)
- The middle candle (candle[1]) is typically large/impulsive

**Trading Use:**
- Price tends to return to "fill" these gaps (rebalancing)
- Entry when price retraces into an open FVG
- Unfilled FVGs act as support/resistance

**States:**
- **Open FVG**: Gap hasn't been filled yet
- **Filled/Mitigated FVG**: Price has returned through the gap

**Fit with Levels Architecture:**
```yaml
id: bullish-fvg-zones
type: fvg
timeframe: 1h
direction: bullish        # bullish | bearish | both
minGapPercent: 0.3        # Minimum gap size as % of price
requireImpulse: true      # Middle candle > 1.5x ATR
lookback: 100             # How far back to track FVGs
showFilled: false         # Only show open (unfilled) FVGs
proximity: 0.1%           # Level active when price enters FVG zone
```

---

### 2. Order Blocks (OB)

**What it is:** The last opposing candle before a significant directional move. ICT theory: this is where institutional orders were placed.

**Visual:**
```
Bullish Order Block:        Bearish Order Block:
                                    ┌──┐
        ┌──┐                        │  │ <- Order Block (last bullish before down)
        │  │ impulse up             └──┘
        │  │                        ┌──┐
 OB ->  └──┘ last bearish           │  │ impulse down
        ┌──┐ candle                 │  │
        │  │                        └──┘
```

**Detection Logic:**
1. Find a significant move (Break of Structure)
2. Identify the last opposing candle before that move
3. The OB zone = that candle's range (high to low)
4. Valid only if price hasn't returned to "mitigate" it yet

**Trading Use:**
- When price retraces to an unmitigated OB, look for entries
- OB + FVG confluence = highest probability setup
- Failed OBs become "breaker blocks" (S/R flip)

**Variants:**
- **Standard OB**: Last opposing candle before impulse
- **Breaker Block**: Failed OB that price broke through, now acts as opposite S/R
- **Mitigation Block**: Partially retraced OB

**Fit with Levels Architecture:**
```yaml
id: bullish-order-blocks
type: order_block
timeframe: 4h
direction: bullish
minBOSPercent: 1.5        # Minimum move size to qualify as BOS
lookback: 200
showMitigated: false      # Only show unmitigated OBs
proximity: 0%             # Level active when price enters OB range
```

---

### 3. Liquidity

**What it is:** Clusters of stop-loss orders that institutional traders target. They know where retail traders place stops.

**Types:**
| Type | Location | Description |
|------|----------|-------------|
| **Buy-Side Liquidity (BSL)** | Above swing highs | Stop losses from shorts, buy stops from breakout traders |
| **Sell-Side Liquidity (SSL)** | Below swing lows | Stop losses from longs, sell stops from breakdown traders |
| **Equal Highs (EQH)** | Multiple highs at same level | Obvious liquidity pool |
| **Equal Lows (EQL)** | Multiple lows at same level | Obvious liquidity pool |

**Visual:**
```
BSL (buy-side liquidity) above:
   ============================== resistance (EQH)
         /\      /\      /\
        /  \    /  \    /  \       <- Stop losses clustered here
       /    \  /    \  /    \
      /      \/      \/      \

SSL (sell-side liquidity) below:
      \      /\      /\      /
       \    /  \    /  \    /       <- Stop losses clustered here
        \  /    \  /    \  /
         \/      \/      \/
   ============================== support (EQL)
```

**Detection Logic:**
- Swing highs/lows = liquidity pools
- Equal highs: 2+ highs within tolerance% = stronger pool
- Equal lows: 2+ lows within tolerance% = stronger pool

**Trading Use:**
- Expect price to "sweep" these levels before reversing
- Don't place stops at obvious swing highs/lows
- After liquidity is swept, look for reversal setups

**Fit with Levels Architecture:**
```yaml
id: equal-highs-liquidity
type: liquidity
timeframe: 1h
liquidityType: equal_highs   # swing_high | swing_low | equal_highs | equal_lows
swingLookback: 10
equalTolerance: 0.2%          # Highs within 0.2% are "equal"
minCount: 2                   # Need 2+ touches to form EQH/EQL
proximity: 0.3%
```

---

### 4. Liquidity Sweep / Stop Hunt

**What it is:** Price briefly breaks a liquidity level, triggers stops, then reverses. This is the "manipulation" phase in ICT's Power of Three.

**Visual:**
```
Bullish Liquidity Sweep:
        ┌──┐
        │  │
        │  │ <- Close back above sweep level
        │  │
   ─────┼──┼───── Previous low (liquidity)
        │  │
        └──┘ <- Wick sweeps below, triggers stops
```

**Detection Logic:**
```
Bullish Sweep: low < previous_swing_low AND close > previous_swing_low
Bearish Sweep: high > previous_swing_high AND close < previous_swing_high
```
(Same bar: swept the level but closed back above/below)

**Trading Use:**
- Sweep + reversal = high-probability entry
- Combine with OB/FVG for confluence
- Best during kill zones (high-volume sessions)

**Fit with Levels Architecture:**
Sweeps are better as DSL functions than static levels:
```
LIQUIDITY_SWEEP_BULLISH(50)    # Swept a swing low within 50 bars, closed above
LIQUIDITY_SWEEP_BEARISH(50)    # Swept a swing high within 50 bars, closed below
```

---

### 5. Break of Structure (BOS)

**What it is:** When price breaks a previous swing high (bullish BOS) or swing low (bearish BOS). Confirms trend continuation.

**Visual:**
```
Bullish BOS:                    Bearish BOS:
                 BOS ->              ┌──┐
                ╔════╗              │  │
                ║    ║              └──┘ <- Previous high
        ┌──┐────╫────╫──           ─────────────────
        │  │    ║    ║                      │
        └──┘    ╚════╝                      │ BOS
                                            v
```

**Detection Logic:**
```
Bullish BOS: close > previous_swing_high
Bearish BOS: close < previous_swing_low
```

**Trading Use:**
- BOS in trend direction = continuation signal
- Wait for pullback after BOS for entry
- Multiple BOS = strong trend

**Fit with Levels Architecture:**
BOS as DSL functions:
```
BOS_UP(20)       # Broke a swing high within 20 bars
BOS_DOWN(20)     # Broke a swing low within 20 bars
```

The level this creates can be tracked:
```yaml
id: recent-bos-levels
type: structure_break
timeframe: 1h
lookback: 50
bosType: both          # bullish | bearish | both
minSwingDominance: 10  # Swing must have held 10+ bars
proximity: 0.5%        # Level active when near BOS price
```

---

### 6. Change of Character (CHoCH)

**What it is:** The FIRST break of structure in the opposite direction - signals potential trend reversal.

**Visual:**
```
Bearish CHoCH (in uptrend):
        ┌──┐
        │  │ <- Previous high (HH)
        └──┘
   ─────┬──┬───── Previous low (HL)
        │  │
        │  │ CHoCH: First break of HL in uptrend
        │  │
        └──┘
```

**Detection Logic:**
1. Identify the trend (higher highs/higher lows or lower highs/lower lows)
2. CHoCH = first BOS in the opposite direction
3. Bullish CHoCH: In downtrend, price breaks above most recent lower high
4. Bearish CHoCH: In uptrend, price breaks below most recent higher low

**Trading Use:**
- CHoCH = early reversal signal (before trend officially changes)
- More aggressive than waiting for full trend change
- Requires confirmation (OB, FVG, or sweep)

**Fit with Levels Architecture:**
CHoCH as DSL functions:
```
CHOCH_BULLISH(50)     # First break above LH in downtrend within 50 bars
CHOCH_BEARISH(50)     # First break below HL in uptrend within 50 bars
```

---

### 7. Premium & Discount Zones

**What it is:** Dividing a swing range into zones. Above 50% = premium (overpriced), below 50% = discount (underpriced).

**Visual:**
```
Swing High ──────────────────────── 100% (Premium)
                │                     │
                │     PREMIUM         │ 75%
                │     ZONE            │
           ─────┼─────────────────────┼ 50% (Equilibrium)
                │                     │
                │     DISCOUNT        │ 25%
                │     ZONE            │
Swing Low  ──────────────────────── 0% (Discount)
```

**Detection Logic:**
```
equilibrium = (swing_high + swing_low) / 2
discount_zone = price < equilibrium
premium_zone = price > equilibrium
```

**Trading Use:**
- Buy in discount zone, sell in premium zone
- OTE (Optimal Trade Entry) = 61.8%-78.6% retracement = deep discount/premium

**Already Supported:**
Tradery already has `RANGE_POSITION(period)` which returns -1 to +1 based on position in range. This is essentially the same concept.

**Additional DSL:**
```
OTE_ZONE(50)          # In 61.8%-78.6% retracement zone
OTE_DISCOUNT(50)      # In discount OTE (61.8%-78.6% from high)
OTE_PREMIUM(50)       # In premium OTE (61.8%-78.6% from low)
```

---

### 8. Kill Zones (Already Supported)

**What it is:** Specific time windows when institutional activity is highest.

| Kill Zone | Time (EST) | Description |
|-----------|------------|-------------|
| **Asian** | 7pm-12am | Lower volume, range formation |
| **London** | 2am-5am | First major session, often sets daily high/low |
| **New York** | 7am-10am | Highest volume, trend days |
| **London Close** | 10am-12pm | Reversal window |

**Already Supported via Phases:**
```yaml
phaseSettings:
  requiredPhaseIds: [us-market-hours]
  # or [asian-session], [european-session], [session-overlap]
```

---

### 9. Power of Three (PO3) / AMD

**What it is:** ICT's model of how institutional traders move markets in three phases:

1. **Accumulation**: Quiet range, institutions building positions
2. **Manipulation**: Fake breakout / stop hunt (liquidity sweep)
3. **Distribution/Delivery**: Real move in intended direction

**Visual (bullish day):**
```
                                    DISTRIBUTION
                                    (real move up)
                                         /
                                        /
                     MANIPULATION      /
                     (fake breakdown) /
                            \        /
   ACCUMULATION              \      /
   (morning range)            \    /
   ======================================
   |<- Asian session ->|<- NY open ->|
```

**Trading Use:**
- Don't trade during accumulation (wait for manipulation)
- Enter after manipulation completes (sweep + reversal)
- Target opposite side of range for distribution

**Implementation:**
This is more of a conceptual framework than a single indicator. It combines:
- Kill zones (time filtering)
- Liquidity sweeps (manipulation detection)
- BOS/CHoCH (distribution confirmation)

---

## Integration with Tradery Levels Architecture

The existing LEVELS_IMPLEMENTATION_PLAN.md already identifies ICT/SMC concepts as a level category (lines 77-87). Here's how each ICT concept maps:

### As Level Types

| ICT Concept | Level Type | Key Parameters |
|-------------|------------|----------------|
| **Fair Value Gap** | `fvg` | direction, minGapPercent, lookback, showFilled |
| **Order Block** | `order_block` | direction, minBOSPercent, showMitigated |
| **Liquidity Pool** | `liquidity` | liquidityType, equalTolerance, minCount |
| **BOS Level** | `structure_break` | bosType, minSwingDominance |
| **Premium/Discount** | `zone` | Use FIB type with ratios [0, 0.5, 1.0] |

### As DSL Functions

| Function | Description | Returns |
|----------|-------------|---------|
| `FVG_BULLISH` | Bullish FVG at current bar | 1 or 0 |
| `FVG_BEARISH` | Bearish FVG at current bar | 1 or 0 |
| `FVG_BULLISH_OPEN(n)` | Open bullish FVG within n bars | 1 or 0 |
| `FVG_BEARISH_OPEN(n)` | Open bearish FVG within n bars | 1 or 0 |
| `PRICE_IN_FVG` | Price currently in any open FVG | 1 or 0 |
| `SWING_HIGH(period)` | Most recent swing high price | price |
| `SWING_LOW(period)` | Most recent swing low price | price |
| `SWING_HIGH_BARS(period)` | Bars since swing high | count |
| `SWING_LOW_BARS(period)` | Bars since swing low | count |
| `BOS_UP(period)` | Broke swing high within period | 1 or 0 |
| `BOS_DOWN(period)` | Broke swing low within period | 1 or 0 |
| `CHOCH_BULLISH(period)` | Change of character bullish | 1 or 0 |
| `CHOCH_BEARISH(period)` | Change of character bearish | 1 or 0 |
| `LIQUIDITY_SWEEP_BULLISH(period)` | Swept lows, closed above | 1 or 0 |
| `LIQUIDITY_SWEEP_BEARISH(period)` | Swept highs, closed below | 1 or 0 |
| `EQUAL_HIGHS(period, tol)` | Equal highs detected | 1 or 0 |
| `EQUAL_LOWS(period, tol)` | Equal lows detected | 1 or 0 |
| `ORDER_BLOCK_BULLISH(period)` | In bullish OB zone | 1 or 0 |
| `ORDER_BLOCK_BEARISH(period)` | In bearish OB zone | 1 or 0 |
| `OTE_ZONE(period)` | In OTE (61.8%-78.6%) | 1 or 0 |

---

## Shared Infrastructure with Levels

The Levels plan already defines reusable components that ICT needs:

| Component | From Levels Plan | ICT Usage |
|-----------|------------------|-----------|
| `SwingPoint` | Record with dominance | Swing highs/lows for BOS, OB, liquidity |
| `SwingDetector` | Find swings with lookback | All ICT concepts need this |
| `ZoneClusterer` | Group nearby prices | Equal highs/lows, OB zones |
| `LevelZone` | Price range + metadata | FVG zones, OB zones, liquidity pools |

### SwingDetector Enhancement for ICT

The basic SwingDetector from SR_ZONES plan needs slight enhancement:
```java
public class SwingDetector {
    // Existing from SR plan
    public List<SwingPoint> detectSwings(List<Candle> candles, int lookback);

    // Additional for ICT
    public Optional<SwingPoint> getMostRecentSwingHigh(int maxBarsAgo);
    public Optional<SwingPoint> getMostRecentSwingLow(int maxBarsAgo);
    public boolean isBroken(SwingPoint swing, int atBarIndex);  // For BOS detection
}
```

---

## Example ICT Strategies in Tradery

### 1. FVG Entry with OB Confluence
```yaml
id: ict-fvg-ob-entry
name: ICT FVG + Order Block Entry

entrySettings:
  condition: |
    FVG_BULLISH_OPEN(50) == 1 AND
    ORDER_BLOCK_BULLISH(50) == 1 AND
    OTE_DISCOUNT(100) == 1

phaseSettings:
  requiredPhaseIds: [us-market-hours, uptrend]

exitSettings:
  zones:
    - name: Take Profit
      minPnlPercent: 3.0
      exitImmediately: true
    - name: Stop Loss
      maxPnlPercent: -1.5
      exitImmediately: true
```

### 2. Liquidity Sweep Reversal
```yaml
id: ict-liquidity-sweep
name: ICT Liquidity Sweep Reversal

entrySettings:
  condition: |
    LIQUIDITY_SWEEP_BULLISH(30) == 1 AND
    BOS_UP(10) == 1

phaseSettings:
  requiredPhaseIds: [us-market-hours]
  excludedPhaseIds: [fomc-meeting-day]
```

### 3. CHoCH Early Reversal
```yaml
id: ict-choch-reversal
name: ICT Change of Character

entrySettings:
  condition: |
    CHOCH_BULLISH(50) == 1 AND
    RSI(14) < 40 AND
    PRICE_IN_FVG == 1
```

---

## Implementation Priority

Given the Levels architecture is already planned, ICT fits naturally:

### Tier 1: Foundation (use existing SwingDetector from Levels)
1. `SWING_HIGH(period)`, `SWING_LOW(period)` - basic swing functions
2. `BOS_UP(period)`, `BOS_DOWN(period)` - structure breaks
3. `LIQUIDITY_SWEEP_BULLISH(period)`, `LIQUIDITY_SWEEP_BEARISH(period)`

### Tier 2: Core ICT (extend Levels types)
4. `FVG_BULLISH`, `FVG_BEARISH` - FVG detection
5. `FVG_BULLISH_OPEN(n)`, `FVG_BEARISH_OPEN(n)` - open FVG tracking
6. `fvg` level type in Levels system
7. `ORDER_BLOCK_BULLISH(period)`, `ORDER_BLOCK_BEARISH(period)`
8. `order_block` level type

### Tier 3: Advanced ICT
9. `CHOCH_BULLISH(period)`, `CHOCH_BEARISH(period)`
10. `EQUAL_HIGHS(period, tolerance)`, `EQUAL_LOWS(period, tolerance)`
11. `liquidity` level type
12. `OTE_ZONE(period)`, `OTE_DISCOUNT`, `OTE_PREMIUM`

---

## Sources

- [ICT Trading Strategy Guide 2025](https://forextester.com/blog/ict-trading/)
- [Fair Value Gap Explained](https://www.writofinance.com/fair-value-gap-in-trading/)
- [Order Blocks Complete Guide](https://www.xs.com/en/blog/order-block-guide/)
- [ICT Concepts Explained - LuxAlgo](https://www.luxalgo.com/blog/ict-trader-concepts-order-blocks-unpacked/)
- [Liquidity Sweep Trading Strategy](https://seacrestmarkets.io/blog/liquidity-sweep-trading-strategy-complete-ict-guide-2025)
- [Smart Money Concepts Python Package](https://github.com/joshyattridge/smart-money-concepts)
