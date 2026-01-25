# Levels Feature - Implementation Plan

## Brainstorm: All Potentially Useful Levels

### Price Structure / Extremes
| Level | Description | Trading Use |
|-------|-------------|-------------|
| All-Time High (ATH) | Highest price ever | Ultimate resistance, breakout = new paradigm |
| All-Time Low (ATL) | Lowest price ever | Ultimate support, capitulation zone |
| Previous ATH | ATH before current one | Often becomes support after breakout |
| 52-Week High/Low | Rolling yearly extremes | Institutional benchmark levels |
| Yearly High/Low | Calendar year extremes | Year-end flows, rebalancing |
| Quarterly High/Low | Q1/Q2/Q3/Q4 extremes | Fund rebalancing zones |
| Monthly High/Low | Calendar month extremes | Swing trader targets |
| Weekly High/Low | Calendar week extremes | Short-term structure |
| Daily High/Low | Previous day's range | Day trader reference |
| Swing High/Low (with dominance) | Significant pivots that held | Natural S/R levels |

### Fibonacci Levels
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Fib Retracements (0.236-0.786) | % pullback from swing | Entry zones on pullbacks |
| Fib 0.5 (50%) | Midpoint of swing | Mean reversion target |
| Fib Extensions (1.272, 1.618, 2.0) | Projected targets beyond swing | Take profit zones |
| Fib Clusters | Where multiple fibs from different swings converge | High-probability S/R |
| Fib Time Zones | Time-based fib projections | When to expect reversals |
| Fib Speed Resistance Fan | Angled fib lines | Dynamic S/R |

### Higher Timeframe Structure
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Yearly Open | First trade of year | Institutional benchmark |
| Quarterly Open | First trade of quarter | Fund flow reference |
| Monthly Open | First trade of month | Monthly bias level |
| Weekly Open | First trade of week | Weekly bias level |
| Daily Open | First trade of day (midnight UTC or session) | Intraday bias |
| Session Opens | London/NY/Tokyo open price | Session bias |
| Opening Range | First hour high/low | Breakout reference for day |
| Previous Day Close | Where yesterday ended | Gap reference |

### Volume/Orderflow Based
| Level | Description | Trading Use |
|-------|-------------|-------------|
| POC (Point of Control) | Highest volume price level | Fair value, magnet |
| Value Area High (VAH) | Upper 70% of volume | Resistance in range |
| Value Area Low (VAL) | Lower 70% of volume | Support in range |
| High Volume Node (HVN) | Price clusters with heavy trading | Strong S/R, slow moves |
| Low Volume Node (LVN) | Price with little trading | Fast moves through, air pockets |
| VWAP | Volume-weighted average price | Institutional fair value |
| VWAP Bands | ±1/2/3 std dev from VWAP | Mean reversion extremes |
| Anchored VWAP | VWAP from specific event (ATH, earnings, etc.) | Event-based fair value |
| Previous Day POC/VAH/VAL | Yesterday's volume structure | Continuation vs reversal |
| Weekly POC | Week's highest volume price | Swing trader reference |
| Developing POC | Current session's evolving POC | Intraday fair value |

### Technical Indicator Levels
| Level | Description | Trading Use |
|-------|-------------|-------------|
| 200 SMA/EMA | Long-term moving average | Trend definition, institutional level |
| 50 SMA/EMA | Medium-term MA | Intermediate trend |
| 20 SMA/EMA | Short-term MA | Near-term support |
| Bollinger Bands | 2 std dev bands | Mean reversion extremes |
| Keltner Channels | ATR-based bands | Volatility breakouts |
| Ichimoku Cloud (Senkou A/B) | Future cloud edges | Dynamic S/R zones |
| Supertrend Line | Trend-following level | Stop loss reference |
| Parabolic SAR | Trailing stop level | Exit signals |

### Pivot Points
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Traditional Pivots (PP, R1-R3, S1-S3) | Daily/weekly pivots | Day trader targets |
| Fibonacci Pivots | Fib-based pivot levels | More levels, fib confluence |
| Camarilla Pivots | Tight intraday levels | Scalping S/R |
| Woodie's Pivots | Close-weighted pivots | Alternative calculation |
| Central Pivot Range (CPR) | TC/Pivot/BC band | Trend vs range identifier |

### Market Structure / SMC (Smart Money Concepts)
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Order Blocks | Last opposite candle before impulse | Institutional entry zones |
| Fair Value Gaps (FVG) | 3-candle imbalances | Price tends to fill gaps |
| Breaker Blocks | Failed order blocks that flip | S/R flip zones |
| Mitigation Blocks | Partially filled order blocks | Re-entry zones |
| Liquidity Pools | Clustered stop losses (above swing highs, below swing lows) | Stop hunt targets |
| Premium/Discount Zones | Above/below 50% of range | Buy discount, sell premium |
| Break of Structure (BOS) | Where trend broke | New S/R level |
| Change of Character (CHoCH) | First opposite break | Trend change signal |

### Gap Levels
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Overnight Gap | Close to open gap (futures/stocks) | Gap fill targets |
| Weekend Gap | Friday close to Sunday open (crypto) | Gap fill probability |
| Earnings Gap | Gap after earnings (stocks) | Often acts as S/R |
| CME Futures Gap | Bitcoin CME gap zones | Gap fill tendency |
| Breakaway Gap | Gap on volume breakout | Often doesn't fill |
| Exhaustion Gap | Gap near trend end | Usually fills |

### Psychological / Round Numbers
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Major Round Numbers | $50k, $100k, etc. | Psychological barriers |
| Minor Round Numbers | $51k, $52k, etc. (1000s) | Smaller S/R |
| Quarter Levels | $X.25, $X.50, $X.75 | Micro structure |
| Options Strikes | High OI strike prices | Pinning, gamma exposure |
| Liquidation Clusters | Estimated liquidation prices | Cascade triggers |

### Event-Based Levels
| Level | Description | Trading Use |
|-------|-------------|-------------|
| FOMC High/Low | Range during Fed announcement | Volatility reference |
| CPI Release Range | Range during inflation data | Economic event S/R |
| NFP Range | Non-farm payrolls range | Jobs data reaction |
| ETF Approval/Denial Price | Price at major news | Sentiment anchor |
| Halving Price | BTC price at halving | Cycle reference |
| Major Crash Low | Covid crash, 2022 low, etc. | Historical support |
| Cycle ATH | Previous bull market high | Cycle structure |

### Correlation / Cross-Market
| Level | Description | Trading Use |
|-------|-------------|-------------|
| DXY Key Levels | Dollar index pivots | Macro correlation |
| SPX Key Levels | S&P 500 pivots | Risk-on/off correlation |
| Gold Key Levels | Gold pivots | Safe haven correlation |
| Bond Yield Levels | 10Y yield pivots | Rate sensitivity |
| Funding Rate Extremes | Historical funding extremes | Overleveraged zones |
| Open Interest Clusters | High OI price levels | Options/futures positioning |

### Pattern-Based Levels
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Necklines | Double top/bottom, H&S necklines | Pattern completion S/R |
| Triangle Boundaries | Ascending/descending triangle lines | Breakout levels |
| Channel Lines | Parallel channel boundaries | Range trading |
| Trendline Breaks | Key trendline levels | Trend change (Rays do this) |
| Cup & Handle Rim | Cup pattern high | Breakout level |
| Flag Pole Projection | Measured move target | Target calculation |

### Historical / Anchored
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Previous Bear Market Low | Cycle bottom | Ultimate support |
| Previous Bull Market High | Cycle top | Major resistance |
| Launch/ICO Price | Token launch price | Holder cost basis |
| MVRV Extremes | On-chain valuation extremes | Over/undervalued zones |
| Realized Price | Average cost basis on-chain | Holder break-even |

### Advanced Orderflow Levels
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Cumulative Delta Extremes | Historical CVD highs/lows | Sentiment exhaustion |
| Delta Divergence Zones | Where price and delta diverged | Failed moves, reversals |
| Absorption Zones | High volume + small price move | Institutional activity |
| Initiative vs Responsive | Where aggressive buying/selling started | Trend origin points |
| Imbalance Stack Zones | Multiple stacked imbalances | Strong directional conviction |
| Whale Trade Clusters | Where large trades concentrated | Smart money levels |
| Liquidation Cascade Zones | Where cascading liquidations occurred | Future liquidity magnets |
| Funding Rate Flip Levels | Price at funding flips (+/-) | Sentiment shift points |
| Open Interest Spike Levels | Price at major OI increases | Position building zones |
| Footprint Absorption | Candles with high absorption score | Hidden supply/demand |
| Delta Flip Levels | Where delta switched sign | Momentum shift points |
| Volume Imbalance (Ask vs Bid) | Where order book was one-sided | Institutional direction |

### Astronomical / Calendar Anchored
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Full Moon Price | Price at exact full moon | Lunar cycle S/R |
| New Moon Price | Price at exact new moon | Lunar cycle S/R |
| Lunar Cycle High/Low | High/low of each lunar cycle | Moon phase ranges |
| Solstice/Equinox Prices | Price at seasonal turns | Quarterly cycle anchors |
| Mercury Retrograde Start/End | Price at retrograde periods | Astro-trading levels |
| Year Start Price (Astrological) | Price at zodiac year start | Alternative yearly open |
| Eclipse Prices | Price at solar/lunar eclipses | Rare event anchors |

### News / Analyst Targets
| Level | Description | Trading Use |
|-------|-------------|-------------|
| Analyst Price Targets | Published targets from analysts | Consensus expectations |
| ETF Approval/Rejection Price | Price at regulatory news | Event anchors |
| Major News Event Price | Price at significant headlines | News reaction origin |
| Percentage from News Target | 50%, 75% of way to target | Intermediate milestones |
| Consensus Target | Average of multiple targets | Market expectation |
| Bear Case Target | Lowest analyst target | Downside expectation |
| Bull Case Target | Highest analyst target | Upside expectation |
| Round Target Zones | Targets at $100k, $150k, etc. | Round number targets |
| Stock-to-Flow Target | S2F model price prediction | Model-based level |
| Rainbow Chart Bands | Log regression bands | Long-term fair value |
| Power Law Corridor | Power law support/resistance | Cycle structure |

### Percentage/Ratio Projections
| Level | Description | Trading Use |
|-------|-------------|-------------|
| X% from ATH | 10%, 20%, 40%, 60% drawdown | Drawdown zones |
| X% from ATL | 2x, 3x, 5x, 10x from low | Rally milestones |
| X% of Range | 25%, 50%, 75% of ATH-ATL | Range quartiles |
| X% Retracement of Last Move | Quick fib-like levels | Pullback zones |
| Measured Move Targets | 100%, 150%, 200% of impulse | Extension targets |
| Risk/Reward Ratio Levels | 1:1, 1:2, 1:3 from entry | Trade management |
| Average Daily Range (ADR) | X% of ADR from open | Intraday targets |
| Average True Range (ATR) | X ATR from level | Volatility-adjusted S/R |

### Summary by Category
| Category | Count | Examples |
|----------|-------|----------|
| Price Structure/Extremes | 10 | ATH, ATL, swing highs/lows, weekly high/low |
| Fibonacci | 6 | Retracements, extensions, clusters |
| Higher Timeframe | 8 | Yearly/monthly/weekly opens, session opens |
| Volume/Orderflow (Basic) | 12 | POC, VAH/VAL, VWAP, anchored VWAP |
| Technical Indicators | 8 | MAs, Bollinger, Ichimoku cloud |
| Pivot Points | 5 | Traditional, Fibonacci, Camarilla |
| Market Structure (SMC) | 8 | Order blocks, FVG, liquidity pools |
| Gap Levels | 6 | Overnight, weekend, CME gaps |
| Psychological/Round | 5 | Round numbers, options strikes |
| Event-Based | 7 | FOMC range, crash lows, halving |
| Correlation/Cross-Market | 6 | DXY, SPX, funding extremes |
| Pattern-Based | 6 | Necklines, channel lines, trendlines |
| Historical/Anchored | 5 | Bear market low, realized price |
| **Advanced Orderflow** | 12 | CVD extremes, absorption zones, liquidation cascades |
| **Astronomical/Calendar** | 7 | Full moon price, eclipse prices, solstice |
| **News/Analyst Targets** | 11 | Analyst targets, S2F, rainbow chart bands |
| **Percentage Projections** | 8 | X% from ATH, measured moves, ATR-based |

**Total: ~130+ distinct level concepts**

### Priority Tiers for Implementation

**Tier 1 - High Value, Foundation** (implement first)
- ATH/ATL with dominance tracking
- Fibonacci retracements/extensions
- HTF structure (weekly/monthly/daily opens)
- S/R zones from swing clustering
- Round numbers
- Percentage projections (X% from ATH/ATL)

**Tier 2 - High Value, Uses Existing Data**
- Volume Profile levels (POC, VAH/VAL) - already have orderflow
- VWAP and anchored VWAP
- Pivot points (classic calculation)
- Previous day/week structure
- Cumulative delta extremes
- Funding rate flip levels
- Full moon / new moon prices (already have moon phase data)

**Tier 3 - Advanced Orderflow**
- Absorption zones
- Imbalance stack zones
- Delta divergence zones
- Whale trade clusters
- Liquidation cascade zones

**Tier 4 - Pattern/Structure Detection**
- Order blocks / FVG (SMC concepts)
- Gap detection and tracking
- Fib clusters (multiple fib confluence)
- Liquidity pool estimation

**Tier 5 - External Data / Models**
- Analyst price targets (manual input or API)
- Stock-to-Flow / Rainbow chart bands
- Options OI levels
- On-chain levels (MVRV, realized price)
- Cross-market correlation levels

---

## Concept Overview

**Phases** = Time axis filtering (when to trade)
**Levels** = Price axis filtering (where to trade)

Levels are a first-class concept parallel to phases, providing price-based structure detection and filtering for strategies.

### Core Principle: Every Level Has a Timeframe

Just like phases, **every level must specify a timeframe**. This determines:
- Which candle resolution is used for calculations (swing detection, ATH tracking, etc.)
- When the level recalculates (each bar on that timeframe)
- The granularity of price data used

```yaml
# Examples - timeframe is REQUIRED for all level types
id: fib-618-daily
type: fib
timeframe: 1d          # REQUIRED - calculated from daily candles

id: weekly-high
type: htf
timeframe: 1w          # REQUIRED - uses weekly candles

id: sr-zones-4h
type: sr
timeframe: 4h          # REQUIRED - swings detected on 4h chart
```

**Why this matters:**
- Same level type on different timeframes = different zones
- Daily ATH vs 4h ATH vs 1h ATH are all valid, different levels
- Consistent with phases: `uptrend` on 1d ≠ `uptrend` on 4h

---

## Level Types

### 1. ATH/ATL Levels (Historical Extremes)
Significant all-time highs/lows that held for minimum duration before being broken.

```yaml
id: previous-ath-zone
type: ath
timeframe: 1d           # REQUIRED - ATH calculated from daily candles
minDominance: 50        # Must have held 50+ bars
mode: latest            # latest | all | indexed(n)
proximity: 2%           # Active when price within 2%
```

**Modes:**
- `latest` - Most recent significant ATH/ATL
- `all` - Any significant ATH/ATL (level active if near ANY)
- `indexed(n)` - Specific one: 1=current, 2=previous, 3=earlier...

**Use cases:**
- "40% down from ATH" → `ATH(latest, 50) * 0.6`
- "Near any historical resistance" → mode=all, proximity=2%

### 2. Fibonacci Levels
Retracements and extensions from swing anchors.

```yaml
id: fib-618-retracement
type: fib
# Anchors: auto-detect by default, or explicit override
anchorHigh: auto                   # auto | DSL expression | static price
anchorLow: auto
ratios: [0.382, 0.5, 0.618]       # Which fib levels to include
mode: all                          # any of these ratios
proximity: 1%
recalculate: dynamic               # static | dynamic
```

**Common ratios:** 0.236, 0.382, 0.5, 0.618, 0.786, 1.0 (extensions: 1.272, 1.618, 2.0)

**Anchor options:**
- `auto` (default): System finds significant swings using SwingDetector
- DSL: `SWING_HIGH(lookback, minDominance)`, `ATH(latest, 50)`
- Static: `73000` (manual price)

### 3. Higher Timeframe Levels
Weekly/monthly/yearly structure projected to current timeframe.

```yaml
id: weekly-high-zone
type: htf
reference: WEEKLY_HIGH            # Which HTF level
proximity: 1.5%
# Optional: lookback for "previous week high" etc.
lookback: 0                       # 0=current, 1=previous, 2=two ago...
```

**References:**
| Function | Description |
|----------|-------------|
| `WEEKLY_HIGH/LOW/OPEN/CLOSE` | Current week |
| `MONTHLY_HIGH/LOW/OPEN/CLOSE` | Current month |
| `QUARTERLY_HIGH/LOW` | Current quarter |
| `YEARLY_HIGH/LOW/OPEN` | Current year |
| `PREV_WEEKLY_HIGH` | Previous week (shorthand for lookback=1) |

### 4. S/R Zones (Swing Clustering)
Auto-detected support/resistance from historical swing clustering. Builds on SR_ZONES_IMPLEMENTATION_PLAN.md.

```yaml
id: auto-sr-zones
type: sr
timeframe: 4h                 # REQUIRED - swings detected on 4h chart
swingLookback: 5              # Bars each side for swing detection
clusterTolerance: 1.0%        # Group swings within this %
minStrength: 50               # Minimum weighted strength
halfLifeBars: 100             # Recency decay
zoneType: all                 # resistance | support | all
```

**Algorithm (from SR_ZONES_IMPLEMENTATION_PLAN.md):**
1. **Swing detection**: Find local max/min over `lookback` bars each direction
2. **Dominance tracking**: Count how many bars each swing held before being exceeded
3. **Clustering**: Group swings within `tolerance%` of each other
4. **Strength calculation**: `strength = Σ (dominanceBars × recencyWeight)` where `recencyWeight = 1.0 / (1.0 + barsAgo / halfLifeBars)`
5. **Touch counting**: Count times price entered each zone

**Reusable components:**
- `SwingPoint(barIndex, timestamp, price, isHigh, dominanceBars)` - used by ATH, Fib, SR
- `SwingDetector` - shared across multiple level types
- Dominance calculation - applies to ATH/ATL tracking too

### 5. Historical Condition Projection
Transpose time-based conditions to price zones - "where did X happen before?"

```yaml
id: high-volume-accumulation
type: condition_projection
condition: volume > AVG_VOLUME(50) * 2 AND RSI(14) < 40
timeframe: 1d
lookback: 200
clusterTolerance: 2%          # Group nearby prices
minOccurrences: 3             # Need 3+ bars in cluster to form zone
```

**Algorithm:**
1. Scan `lookback` bars on `timeframe`
2. Find all bars where `condition` was true
3. Cluster prices within `clusterTolerance`
4. Zones with `minOccurrences`+ become levels
5. Level active when current price enters any zone

**Use cases:**
- "Where did capitulation happen?" → RSI < 20 AND volume spike
- "Where did smart money accumulate?" → OBV rising + price flat
- "Historical VWAP anchors" → high volume nodes

### 6. Price Projection (Calculated)
Direct price calculations using DSL expressions.

```yaml
id: forty-pct-drawdown
type: projection
minPrice: ATH(latest, 50) * 0.58
maxPrice: ATH(latest, 50) * 0.62
timeframe: 1d
```

**Expression support:**
- Arithmetic: `ATH(latest, 50) * 0.6`
- Functions: `FIB(0.618, SWING_HIGH(100), SWING_LOW(100))`
- HTF refs: `WEEKLY_HIGH * 1.02`

### 7. Ray-Based Levels
Integration with existing Rotating Ray system.

```yaml
id: near-resistance-ray
type: ray
timeframe: 1d                 # REQUIRED - ray calculated from daily candles
raySystem: resistance         # resistance | support
rayIndex: 1                   # 1=ATH ray, 2=next, etc. or "nearest"
proximity: 1%
lookback: 200
skip: 5
```

Leverages existing ray DSL functions internally.

### 8. Round Number Levels
Psychological price levels.

```yaml
id: round-numbers
type: round
timeframe: 1d                 # REQUIRED
base: 10000                   # $10k increments for BTC
proximity: 1%
# Generates zones at 50000, 60000, 70000, etc.
```

Or specific round levels:
```yaml
id: btc-100k
type: custom
timeframe: 1d                 # REQUIRED
minPrice: 99000
maxPrice: 101000
notes: "Psychological $100k level"
```

### 9. Custom/Manual Levels
User-defined static zones.

```yaml
id: my-accumulation-zone
type: custom
timeframe: 1d                 # REQUIRED - even for static levels
minPrice: 58000
maxPrice: 62000
notes: "Major accumulation zone from Q3 2024"
```

Simple but useful for marking known significant levels.

---

o## ICT / Smart Money Concept Levels

Based on docs/ICT_CONCEPTS.md, these additional level types capture institutional trading patterns:

### 10. Fair Value Gap (FVG) Levels
Imbalance zones where price moved too fast (3-candle gap pattern).

```yaml
id: bullish-fvg-zones
type: fvg
timeframe: 1h                 # REQUIRED
direction: bullish            # bullish | bearish | both
minGapPercent: 0.3            # Minimum gap size as % of price
requireImpulse: true          # Middle candle > 1.5x ATR
lookback: 100                 # How far back to track FVGs
showFilled: false             # Only show open (unfilled) FVGs
proximity: 0.1%               # Level active when price enters FVG zone
```

**Use case:** Enter when price retraces into unfilled FVG.

### 11. Order Block Levels
Last opposing candle before significant move (institutional entry zones).

```yaml
id: bullish-order-blocks
type: order_block
timeframe: 4h                 # REQUIRED
direction: bullish            # bullish | bearish | both
minBOSPercent: 1.5            # Minimum move size to qualify as BOS
lookback: 200
showMitigated: false          # Only show unmitigated OBs
proximity: 0%                 # Level active when price enters OB range
```

**Use case:** Enter when price retraces to unmitigated order block.

### 12. Liquidity Pool Levels
Clusters of stop-losses (swing highs/lows, equal highs/lows).

```yaml
id: equal-highs-liquidity
type: liquidity
timeframe: 1h                 # REQUIRED
liquidityType: equal_highs    # swing_high | swing_low | equal_highs | equal_lows
swingLookback: 10
equalTolerance: 0.2%          # Highs within 0.2% are "equal"
minCount: 2                   # Need 2+ touches to form EQH/EQL
proximity: 0.3%
```

**Use case:** Expect sweeps before reversals; don't place stops at obvious pools.

### 13. Structure Break Levels
Prices where BOS (Break of Structure) occurred.

```yaml
id: recent-bos-levels
type: structure_break
timeframe: 1h                 # REQUIRED
lookback: 50
bosType: both                 # bullish | bearish | both
minSwingDominance: 10         # Swing must have held 10+ bars
proximity: 0.5%               # Level active when near BOS price
```

**Use case:** BOS levels often act as support/resistance on retest.

---

## Zones vs Events: Important Distinction

**Levels = Persistent Zones** (price ranges that exist over time)
- FVG zones, Order Blocks, Liquidity pools, Fib zones
- Active when price is IN the zone
- Stored as minPrice/maxPrice bounds

**DSL Functions = Point-in-Time Events** (things that happen on a specific bar)
- Liquidity sweep, BOS, CHoCH, FVG formation
- Return 1/0 for "happened this bar"
- Used in entry/exit CONDITIONS, not as levels

This distinction is why some ICT concepts are level types and others are DSL functions.

---

## DSL Extensions Required

### ATH/ATL Functions
```
ATH(mode, minDominance)
ATL(mode, minDominance)

mode: "latest" | "all" | number (index)
minDominance: minimum bars it held before being broken

Examples:
ATH(latest, 50)      # Most recent ATH that held 50+ bars
ATH(2, 50)           # Second most recent (previous ATH)
ATH(all, 50)         # For level definitions - matches any
ATL(latest, 100)     # Most recent significant low
```

### Swing Functions
```
SWING_HIGH(lookback, minDominance)
SWING_LOW(lookback, minDominance)

lookback: how far back to search
minDominance: minimum bars it held as local extreme

Examples:
SWING_HIGH(100, 20)  # Recent swing high that held 20+ bars
SWING_LOW(200, 50)   # Significant swing low
```

### Fib Function
```
FIB(ratio, high, low)

ratio: 0.0 to 2.0+ (retracements and extensions)
high/low: price anchors (DSL expressions or numbers)

Examples:
FIB(0.618, ATH(latest, 50), ATL(latest, 50))
FIB(0.5, SWING_HIGH(100, 20), SWING_LOW(100, 20))
FIB(1.618, 73000, 52000)  # Extension target
```

### HTF Functions
```
WEEKLY_HIGH, WEEKLY_LOW, WEEKLY_OPEN, WEEKLY_CLOSE
MONTHLY_HIGH, MONTHLY_LOW, MONTHLY_OPEN, MONTHLY_CLOSE
QUARTERLY_HIGH, QUARTERLY_LOW
YEARLY_HIGH, YEARLY_LOW, YEARLY_OPEN

With optional lookback:
WEEKLY_HIGH(0)       # Current week (default)
WEEKLY_HIGH(1)       # Previous week
MONTHLY_LOW(2)       # Two months ago
```

### Level Reference Function
```
IN_LEVEL(levelId)
NEAR_LEVEL(levelId, proximity%)

Examples:
IN_LEVEL(fib-618-zone)           # Is price in this level?
NEAR_LEVEL(weekly-high, 1.5)     # Within 1.5% of level?
```

---

## Level Model

```java
public class Level implements Identifiable {
    // Core fields (REQUIRED - same pattern as Phase)
    private String id;                    // Unique identifier
    private String name;                  // Display name
    private String timeframe;             // REQUIRED - candle resolution (1m, 5m, 15m, 1h, 4h, 1d, 1w)
    private String category;              // Fib, ATH, HTF, SR, Custom, etc.
    private LevelType type;               // Enum: ATH, ATL, FIB, HTF, SR, CONDITION_PROJECTION, PROJECTION, RAY, ROUND, CUSTOM
    private RecalculateMode recalculate;  // STATIC or DYNAMIC

    // Type-specific config (union-style, only relevant fields used)
    private Integer minDominance;     // For ATH/ATL/SWING
    private String mode;              // latest, all, or index number
    private Double proximity;         // % range for activation
    private List<Double> ratios;      // For FIB
    private String anchorHigh;        // "auto" | DSL expression | static price
    private String anchorLow;         // "auto" | DSL expression | static price
    private String reference;         // For HTF (WEEKLY_HIGH, etc.)
    private Integer lookback;         // For various types
    private String condition;         // For condition_projection
    private Double clusterTolerance;  // For SR/condition_projection
    private Integer minOccurrences;   // For condition_projection
    private Double minStrength;       // For SR
    private String minPriceExpr;      // DSL expression for projection
    private String maxPriceExpr;      // DSL expression for projection
    private Double minPrice;          // Static for custom
    private Double maxPrice;          // Static for custom

    private boolean builtIn;
    private String notes;
}

public enum RecalculateMode {
    STATIC,   // Calculate once on data load, zones stay fixed
    DYNAMIC   // Recalculate each bar (within timeframe resolution)
}
```

**Recalculation behavior:**
- `STATIC`: Zones calculated once when data loads. Fast, stable, good for historical analysis.
- `DYNAMIC`: Zones update as new bars form. Adapts to new swings/ATH, but more computation.
- Default varies by type: Custom=STATIC, ATH/Fib/SR=DYNAMIC, HTF=DYNAMIC (follows period)

---

## Strategy Integration

### LevelSettings (parallel to PhaseSettings)
```java
public class LevelSettings {
    private List<String> requiredLevelIds;    // ALL must be active
    private List<String> excludedLevelIds;    // NONE must be active
}
```

### Usage in Strategy
```yaml
entrySettings:
  condition: RSI(14) < 30
  phaseSettings:
    requiredPhaseIds: [uptrend, weekdays]
  levelSettings:
    requiredLevelIds: [fib-618-zone]
    excludedLevelIds: [near-ath]

exitSettings:
  zones:
    - name: Take Profit at Resistance
      levelSettings:
        requiredLevelIds: [weekly-resistance]
      exitImmediately: true
```

---

## File Structure

```
~/.tradery/
├── levels/
│   ├── {id}/
│   │   └── level.yaml
│   └── ...
├── strategies/...
├── phases/...
```

Built-in levels in: `src/main/resources/levels/`

---

## Built-in Levels (Examples)

| ID | Type | Description |
|----|------|-------------|
| `fib-236` | fib | 23.6% retracement |
| `fib-382` | fib | 38.2% retracement |
| `fib-500` | fib | 50% retracement |
| `fib-618` | fib | 61.8% retracement |
| `fib-786` | fib | 78.6% retracement |
| `near-ath` | ath | Within 5% of significant ATH |
| `near-atl` | atl | Within 5% of significant ATL |
| `previous-ath` | ath | Previous ATH zone (mode=indexed(2)) |
| `weekly-high` | htf | Current week high zone |
| `weekly-low` | htf | Current week low zone |
| `monthly-high` | htf | Current month high zone |
| `monthly-low` | htf | Current month low zone |
| `auto-resistance` | sr | Auto-detected resistance zones |
| `auto-support` | sr | Auto-detected support zones |

---

## Implementation Files

### New Files (tradery-core)
| File | Purpose |
|------|---------|
| `model/Level.java` | Level model class |
| `model/LevelType.java` | Enum for level types |
| `model/LevelSettings.java` | Required/excluded level config |
| `model/LevelZone.java` | Calculated zone (minPrice, maxPrice, strength) |
| `model/SwingPoint.java` | Swing with dominance (from SR plan) |
| `engine/LevelEvaluator.java` | Evaluate if levels are active |
| `engine/LevelCalculator.java` | Calculate level zones from definitions |
| `engine/SwingDetector.java` | Find swing highs/lows with dominance (shared by ATH, Fib, SR) |
| `engine/ATHTracker.java` | Track historical ATH/ATL with dominance (uses SwingDetector) |
| `engine/ZoneClusterer.java` | Cluster prices into zones (from SR plan, used by SR + condition projection) |
| `io/LevelStore.java` | YAML persistence |

**Shared infrastructure (from SR_ZONES_IMPLEMENTATION_PLAN.md):**
- `SwingPoint` record captures swing price + dominance - reused by ATH/ATL, Fib anchors, SR zones
- `SwingDetector` finds swings with configurable lookback and dominance calculation
- `ZoneClusterer` groups nearby prices - used by SR zones and condition projection

### Modified Files (tradery-core)
| File | Changes |
|------|---------|
| `model/EntrySettings.java` | Add `levelSettings` field |
| `model/ExitZone.java` | Add `levelSettings` field |
| `model/Strategy.java` | Add top-level `levelSettings` |
| `dsl/Lexer.java` | New keywords (ATH, ATL, FIB, SWING_HIGH, etc.) |
| `dsl/Parser.java` | Parse new functions |
| `engine/ConditionEvaluator.java` | Evaluate new DSL functions |
| `engine/BacktestEngine.java` | Integrate level filtering |
| `indicators/IndicatorEngine.java` | HTF calculations |

### New Files (tradery-app)
| File | Purpose |
|------|---------|
| `ui/LevelListPanel.java` | List/manage levels |
| `ui/LevelEditorPanel.java` | Edit level definitions |
| `ui/charts/LevelOverlay.java` | Draw levels on chart |

### MCP Tools
| Tool | Purpose |
|------|---------|
| `tradery_list_levels` | List all levels |
| `tradery_get_level` | Get level config |
| `tradery_create_level` | Create level |
| `tradery_update_level` | Update level |
| `tradery_delete_level` | Delete level |
| `tradery_eval_level` | Check if level active at current price |

---

## Implementation Order

### Phase 1: Core Infrastructure
1. `Level.java`, `LevelType.java`, `LevelSettings.java` models
2. `LevelStore.java` for persistence
3. `LevelZone.java` for calculated zones

### Phase 2: DSL Extensions
1. Add ATH/ATL/FIB/SWING_HIGH/SWING_LOW to Lexer
2. Parse new functions in Parser
3. Basic evaluation in ConditionEvaluator

### Phase 3: Level Calculation Engine
1. `SwingDetector.java` - find swings with dominance
2. `ATHTracker.java` - track ATH/ATL history
3. `LevelCalculator.java` - calculate zones from level definitions
4. `LevelEvaluator.java` - check if levels active

### Phase 4: Strategy Integration
1. Add `levelSettings` to EntrySettings, ExitZone, Strategy
2. Integrate into BacktestEngine
3. Update trade analytics to capture active levels

### Phase 5: UI
1. LevelListPanel, LevelEditorPanel
2. LevelOverlay for charts
3. Level selection in strategy editor

### Phase 6: MCP/API
1. Level CRUD endpoints
2. Level evaluation endpoint

---

## Verification

1. Create custom level via MCP: `tradery_create_level`
2. Verify persistence in `~/.tradery/levels/`
3. Add level to strategy's `levelSettings.requiredLevelIds`
4. Run backtest: `tradery_run_backtest`
5. Check trades only occur when level was active
6. Verify level zones render on chart
7. Test each level type: ATH, Fib, HTF, SR, Condition Projection

---

## Integration with Existing Systems

### Phases → Levels Parallel
| Aspect | Phases | Levels |
|--------|--------|--------|
| Axis | Time | Price |
| Definition | DSL condition | DSL + price bounds + type-specific |
| Timeframe | Evaluation resolution | Price data resolution |
| Categories | Trend, Session, Calendar... | Fib, ATH, HTF, SR, Custom... |
| Strategy integration | phaseSettings | levelSettings |
| Active when | Condition true | Price in zone |

### Rotating Rays Integration
Ray-based levels (`type: ray`) delegate to existing ray system:
- Uses existing `RESISTANCE_RAY_DISTANCE`, `SUPPORT_RAY_DISTANCE` DSL functions
- Wraps ray proximity check as a level
- Benefits from existing ray calculation and caching

### SR Zones Integration
The SR_ZONES_IMPLEMENTATION_PLAN.md becomes one level type (`type: sr`):
- Same algorithm: swing detection → dominance → clustering → strength
- Same visualization: semi-transparent horizontal bands
- Now integrated into strategy filtering via `levelSettings`
- Reusable components: SwingPoint, SwingDetector, ZoneClusterer

---

## Design Decisions Made

1. **Fib anchors**: Auto-detect by default using SwingDetector, with explicit override option (DSL or static price)
2. **Level recalculation**: Configurable per level via `recalculate: static | dynamic`

## Remaining Questions (minor, can decide during implementation)

1. **Zone visualization**: Render as horizontal bands (like S/R zones plan) or price labels on Y-axis?
2. **Multi-zone levels**: When type returns multiple zones (mode=all), how to visualize?
3. **HTF data caching**: Strategy for fetching/caching higher timeframe candles for WEEKLY_HIGH etc.