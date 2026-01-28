# Levels Concept Fit Analysis

## Evaluation Criteria

**Levels should be:**
- Price zones with defined bounds (minPrice/maxPrice)
- Relatively stable or predictably recalculating
- Useful as "required" or "excluded" filters
- Evaluable as "price is in/near zone"

**Fit Ratings:**
- **Excellent** - Perfect fit, clear price zone, stable
- **Good** - Works well, minor caveats
- **Moderate** - Usable but has limitations
- **Poor** - Better as DSL function or indicator
- **Bad Fit** - Doesn't work as a level concept

---

## Price Structure / Extremes

| Level | Fit | Notes |
|-------|-----|-------|
| All-Time High (ATH) | **Excellent** | Clear price, proximity zone works perfectly |
| All-Time Low (ATL) | **Excellent** | Clear price, proximity zone works perfectly |
| Previous ATH | **Excellent** | Historical price point, great for S/R |
| 52-Week High | **Excellent** | Rolling but well-defined price |
| 52-Week Low | **Excellent** | Rolling but well-defined price |
| Yearly High | **Excellent** | Calendar-based, clear bounds |
| Yearly Low | **Excellent** | Calendar-based, clear bounds |
| Quarterly High/Low | **Excellent** | Calendar-based, clear bounds |
| Monthly High/Low | **Excellent** | Calendar-based, clear bounds |
| Weekly High/Low | **Excellent** | Calendar-based, clear bounds |
| Daily High/Low | **Excellent** | Previous day range, very common |
| Swing High/Low (with dominance) | **Excellent** | Core building block for many levels |

---

## Fibonacci Levels

| Level | Fit | Notes |
|-------|-----|-------|
| Fib Retracements (0.236-0.786) | **Excellent** | Classic price zones, clear bounds |
| Fib 50% | **Excellent** | Simple midpoint calculation |
| Fib Extensions (1.272, 1.618, 2.0) | **Excellent** | Target zones, clear prices |
| Fib Clusters | **Good** | Multiple fibs = stronger zone, needs confluence detection |
| Fib Time Zones | **Bad Fit** | Time-based, not price - this is a PHASE |
| Fib Speed Resistance Fan | **Moderate** | Dynamic angled lines, complex to define as zone |

---

## Higher Timeframe Structure

| Level | Fit | Notes |
|-------|-----|-------|
| Yearly Open | **Excellent** | Single price point, great institutional reference |
| Quarterly Open | **Excellent** | Single price point |
| Monthly Open | **Excellent** | Single price point |
| Weekly Open | **Excellent** | Single price point |
| Daily Open | **Excellent** | Single price point |
| Session Opens (London/NY/Tokyo) | **Excellent** | Single price points at session start |
| Opening Range (first hour H/L) | **Excellent** | Clear zone bounds |
| Previous Day Close | **Excellent** | Single price point |

---

## Volume/Orderflow Based

| Level | Fit | Notes |
|-------|-----|-------|
| POC (Point of Control) | **Excellent** | Single price, clear zone with proximity |
| Value Area High (VAH) | **Excellent** | Clear price boundary |
| Value Area Low (VAL) | **Excellent** | Clear price boundary |
| High Volume Node (HVN) | **Good** | Zone where volume clustered, needs clustering algo |
| Low Volume Node (LVN) | **Good** | Zone with low volume, needs detection |
| VWAP | **Moderate** | Moves every bar - better as DSL function |
| VWAP Bands (±1/2/3 std) | **Moderate** | Dynamic bands - better as DSL function |
| Anchored VWAP | **Good** | Fixed anchor makes it more stable |
| Previous Day POC/VAH/VAL | **Excellent** | Fixed for the day, great reference |
| Weekly POC | **Excellent** | Fixed for the week |
| Developing POC | **Poor** | Changes every bar - use DSL function instead |

---

## Technical Indicator Levels

| Level | Fit | Notes |
|-------|-----|-------|
| 200 SMA/EMA | **Poor** | Moves every bar - use DSL `price > SMA(200)` |
| 50 SMA/EMA | **Poor** | Moves every bar - use DSL |
| 20 SMA/EMA | **Poor** | Moves every bar - use DSL |
| Bollinger Bands | **Poor** | Dynamic bands - use DSL `BBANDS(20,2).upper` |
| Keltner Channels | **Poor** | Dynamic - use DSL |
| Ichimoku Cloud (Senkou A/B) | **Moderate** | Cloud edges are zones, but shift forward |
| Supertrend Line | **Poor** | Flips dynamically - better as DSL/phase |
| Parabolic SAR | **Poor** | Dots every bar - use DSL |

**Summary:** Most indicator-based "levels" are really just indicator values that move. Better accessed via DSL functions in conditions, not as static levels.

---

## Pivot Points

| Level | Fit | Notes |
|-------|-----|-------|
| Traditional Pivots (PP, R1-R3, S1-S3) | **Excellent** | Fixed for period, clear prices |
| Fibonacci Pivots | **Excellent** | Fixed for period |
| Camarilla Pivots | **Excellent** | Fixed for period |
| Woodie's Pivots | **Excellent** | Fixed for period |
| Central Pivot Range (CPR) | **Excellent** | Zone with TC/Pivot/BC bounds |

---

## Market Structure / SMC (ICT)

| Level | Fit | Notes |
|-------|-----|-------|
| Order Blocks | **Good** | Zone (candle range), but needs mitigation tracking |
| Fair Value Gaps (FVG) | **Good** | Zone (gap bounds), needs fill tracking |
| Breaker Blocks | **Moderate** | Failed OB, more complex lifecycle |
| Mitigation Blocks | **Moderate** | Partially filled, state tracking needed |
| Liquidity Pools | **Excellent** | Clear zone above/below swing H/L |
| Premium/Discount Zones | **Excellent** | 50% of range - simple calculation |
| Break of Structure (BOS) | **Good** | Price where break occurred |
| Change of Character (CHoCH) | **Good** | Price where character changed |

---

## Gap Levels

| Level | Fit | Notes |
|-------|-----|-------|
| Overnight Gap | **Excellent** | Clear zone (close to open range) |
| Weekend Gap | **Excellent** | Friday close to Sunday open |
| Earnings Gap | **Excellent** | Fixed historical zone |
| CME Futures Gap | **Excellent** | Well-known zones, clear bounds |
| Breakaway Gap | **Good** | Gap on breakout, needs classification |
| Exhaustion Gap | **Moderate** | Needs context to identify |

---

## Psychological / Round Numbers

| Level | Fit | Notes |
|-------|-----|-------|
| Major Round Numbers ($50k, $100k) | **Excellent** | Static, psychological significance |
| Minor Round Numbers ($51k, $52k) | **Excellent** | Static, simple to generate |
| Quarter Levels ($X.25, $X.50, $X.75) | **Excellent** | Static |
| Options Strikes (High OI) | **Good** | Needs external data, but clear prices |
| Liquidation Clusters | **Moderate** | Estimated, needs external data |

---

## Event-Based Levels

| Level | Fit | Notes |
|-------|-----|-------|
| FOMC High/Low | **Excellent** | Fixed historical range |
| CPI Release Range | **Excellent** | Fixed historical range |
| NFP Range | **Excellent** | Fixed historical range |
| ETF Approval/Denial Price | **Excellent** | Fixed historical price |
| Halving Price | **Excellent** | Fixed historical price |
| Major Crash Low (Covid, 2022) | **Excellent** | Fixed historical price |
| Cycle ATH | **Excellent** | Fixed historical price |

---

## Correlation / Cross-Market

| Level | Fit | Notes |
|-------|-----|-------|
| DXY Key Levels | **Moderate** | Different asset - needs data feed |
| SPX Key Levels | **Moderate** | Different asset - needs data feed |
| Gold Key Levels | **Moderate** | Different asset - needs data feed |
| Bond Yield Levels | **Moderate** | Different asset - needs data feed |
| Funding Rate Extremes | **Poor** | Not a price level - this is a PHASE |
| Open Interest Clusters | **Good** | Price levels with high OI |

---

## Pattern-Based Levels

| Level | Fit | Notes |
|-------|-----|-------|
| Necklines (H&S, Double Top) | **Good** | Clear price after pattern detected |
| Triangle Boundaries | **Moderate** | Angled lines, complex zone definition |
| Channel Lines | **Moderate** | Angled, better as ray concept |
| Trendline Breaks | **Moderate** | Already have Rays for this |
| Cup & Handle Rim | **Good** | Clear price level |
| Flag Pole Projection | **Good** | Calculated target zone |

---

## Historical / Anchored

| Level | Fit | Notes |
|-------|-----|-------|
| Previous Bear Market Low | **Excellent** | Fixed historical price |
| Previous Bull Market High | **Excellent** | Fixed historical price |
| Launch/ICO Price | **Excellent** | Fixed price (needs manual input) |
| MVRV Extremes | **Poor** | Valuation metric, not price level |
| Realized Price | **Good** | On-chain price level (needs data) |

---

## Advanced Orderflow Levels

| Level | Fit | Notes |
|-------|-----|-------|
| Cumulative Delta Extremes | **Poor** | Delta value, not price - use DSL |
| Delta Divergence Zones | **Moderate** | Where divergence happened - needs detection |
| Absorption Zones | **Good** | Price zones with absorption |
| Initiative vs Responsive | **Poor** | Classification, not price zone |
| Imbalance Stack Zones | **Good** | Price zones with stacked imbalances |
| Whale Trade Clusters | **Good** | Price zones with large trades |
| Liquidation Cascade Zones | **Good** | Where cascades occurred |
| Funding Rate Flip Levels | **Good** | Price when funding flipped |
| Open Interest Spike Levels | **Good** | Price at OI spikes |
| Footprint Absorption | **Poor** | Per-candle metric - use DSL |
| Delta Flip Levels | **Good** | Price when delta flipped |
| Volume Imbalance Zones | **Good** | Where order book was one-sided |

---

## Astronomical / Calendar Anchored

| Level | Fit | Notes |
|-------|-----|-------|
| Full Moon Price | **Excellent** | Price at specific time |
| New Moon Price | **Excellent** | Price at specific time |
| Lunar Cycle High/Low | **Excellent** | Range within lunar cycle |
| Solstice/Equinox Prices | **Excellent** | Price at specific dates |
| Mercury Retrograde Start/End | **Excellent** | Price at specific dates |
| Year Start Price (Astrological) | **Excellent** | Price at specific date |
| Eclipse Prices | **Excellent** | Price at specific events |

---

## News / Analyst Targets

| Level | Fit | Notes |
|-------|-----|-------|
| Analyst Price Targets | **Excellent** | Clear target prices |
| ETF Approval/Rejection Price | **Excellent** | Fixed historical price |
| Major News Event Price | **Excellent** | Fixed historical price |
| Percentage from News Target | **Excellent** | Calculated price zones |
| Consensus Target | **Excellent** | Average of targets |
| Bear Case Target | **Excellent** | Specific target price |
| Bull Case Target | **Excellent** | Specific target price |
| Round Target Zones | **Excellent** | Round number targets |
| Stock-to-Flow Target | **Moderate** | Model prediction, may need external calc |
| Rainbow Chart Bands | **Good** | Log regression bands, needs calculation |
| Power Law Corridor | **Good** | Power law bounds |

---

## Percentage/Ratio Projections

| Level | Fit | Notes |
|-------|-----|-------|
| X% from ATH (drawdown) | **Excellent** | Simple calculation from ATH |
| X% from ATL (rally) | **Excellent** | Simple calculation from ATL |
| X% of Range | **Excellent** | Quartiles of ATH-ATL range |
| X% Retracement of Last Move | **Good** | Like fib but simpler |
| Measured Move Targets | **Excellent** | 100%, 150%, 200% projections |
| Risk/Reward Ratio Levels | **Poor** | Per-trade, not market level |
| Average Daily Range (ADR) | **Moderate** | From open, dynamic reference |
| Average True Range (ATR) | **Poor** | Volatility measure, not price - use DSL |

---

## Summary by Fit Rating

### Excellent Fit (Core Level Types) - 67 items
Perfect for the levels concept:
- All HTF structure (opens, highs, lows)
- All ATH/ATL variations
- Fibonacci retracements/extensions
- Pivot points
- Round numbers
- Event-based historical prices
- Percentage projections from anchors
- Astronomical anchors
- Analyst targets

### Good Fit (Usable with Caveats) - 28 items
Works well but needs specific handling:
- Order Blocks, FVG (need fill/mitigation state)
- Volume profile nodes
- Anchored VWAP
- Pattern-based (necklines, projections)
- Orderflow price zones (absorption, whale clusters)

### Moderate Fit (Consider Carefully) - 18 items
May work but has limitations:
- Dynamic angled lines (triangles, channels)
- Cross-market levels (need external data)
- Some orderflow derived levels
- Ichimoku cloud edges

### Poor Fit (Use DSL Instead) - 17 items
Better as DSL functions or phases:
- Moving averages (SMA, EMA)
- Bollinger Bands
- VWAP (non-anchored)
- Supertrend, SAR
- CVD/Delta values
- Developing POC
- ATR (volatility measure)
- Funding rate extremes (→ PHASE)

### Bad Fit - 2 items
Doesn't work as level:
- Fib Time Zones (time-based → PHASE)
- MVRV (valuation ratio, not price)

---

## Recommendation

**Implement in Tier 1:**
- ATH/ATL (Excellent fit, foundation)
- Fibonacci (Excellent fit, widely used)
- HTF Structure (Excellent fit, institutional)
- S/R Zones (Excellent fit, swing-based)
- Round Numbers (Excellent fit, simple)
- Percentage Projections (Excellent fit, simple)

**Move to DSL functions (not levels):**
- Moving averages
- Bollinger/Keltner bands
- VWAP (non-anchored)
- Supertrend, SAR
- CVD/Delta values
- ATR

**Move to Phases (time-based):**
- Fib Time Zones
- Funding Rate Extremes
- MVRV Extremes
