# Wyckoff Pattern Detection - Implementation Plan

## Overview

Implement institutional Wyckoff pattern detection by extending the DSL with Volume Spread Analysis (VSA) functions, adding Wyckoff event detection, creating hoop patterns for accumulation/distribution schematics, and defining Wyckoff phases.

**Key Architecture Decision**: Align with ICT/Levels plan - use DSL functions for point-in-time events, hoops for sequential patterns, phases for market regimes, and (future) levels for trading range zones.

---

## Implementation Tiers

### Tier 1: VSA Foundation (DSL Functions)
Core volume spread analysis - building blocks for all Wyckoff detection.

### Tier 2: Wyckoff Events (DSL Functions)
Point-in-time event detection (climax, spring, upthrust, etc.).

### Tier 3: Wyckoff Schematics (Hoop Patterns)
Sequential patterns for accumulation/distribution phases.

### Tier 4: Wyckoff Phases
Market regime states (in-accumulation, in-distribution, markup, markdown).

### Tier 5: Trade Analytics
Capture Wyckoff metrics at entry/exit for AI analysis.

---

## Tier 1: VSA Foundation

### New DSL Functions

| Function | Returns | Description |
|----------|---------|-------------|
| `SPREAD` | double | High - Low (bar range) |
| `SPREAD_VS_AVG(n)` | ratio | SPREAD / avg spread over n bars |
| `NARROW_SPREAD(n, t)` | 0/1 | 1 if spread < t * avg spread |
| `WIDE_SPREAD(n, t)` | 0/1 | 1 if spread > t * avg spread |
| `VOLUME_VS_AVG(n)` | ratio | volume / AVG_VOLUME(n) |
| `HIGH_VOLUME(n, t)` | 0/1 | 1 if volume > t * AVG_VOLUME(n) |
| `LOW_VOLUME(n, t)` | 0/1 | 1 if volume < t * AVG_VOLUME(n) |
| `EFFORT_VS_RESULT(n)` | ratio | (vol/avg_vol) / (spread/avg_spread) |
| `NO_DEMAND(n)` | 0/1 | Narrow spread + low volume + up close |
| `NO_SUPPLY(n)` | 0/1 | Narrow spread + low volume + down close |
| `STOPPING_VOLUME(n)` | 0/1 | Wide spread + high vol + close off extreme |
| `CLOSE_POSITION` | 0-1 | Where close is within bar range |

### Files to Modify

```
tradery-core/src/main/java/com/tradery/dsl/TokenType.java
  → Add: VSA_FUNC

tradery-core/src/main/java/com/tradery/dsl/Lexer.java
  → Add keywords: SPREAD, SPREAD_VS_AVG, NARROW_SPREAD, WIDE_SPREAD,
                  VOLUME_VS_AVG, HIGH_VOLUME, LOW_VOLUME, EFFORT_VS_RESULT,
                  NO_DEMAND, NO_SUPPLY, STOPPING_VOLUME, CLOSE_POSITION

tradery-core/src/main/java/com/tradery/dsl/AstNode.java
  → Add: record VsaFunctionCall(String func, Integer period, Double threshold)

tradery-core/src/main/java/com/tradery/dsl/Parser.java
  → Add: vsaFunctionCall() method, handle VSA_FUNC in term()

tradery-core/src/main/java/com/tradery/engine/ConditionEvaluator.java
  → Add: evaluateVsaFunction() method

tradery-core/src/main/java/com/tradery/indicators/IndicatorEngine.java
  → Add: getSpread(), getSpreadVsAvg(), getEffortVsResult(), etc.

NEW: tradery-core/src/main/java/com/tradery/indicators/VsaIndicators.java
  → Static calculation methods for all VSA functions
```

---

## Tier 2: Wyckoff Events

### New DSL Functions

| Function | Returns | Description |
|----------|---------|-------------|
| `SELLING_CLIMAX(n)` | 0/1 | Wide spread + ultra high vol + close near low |
| `BUYING_CLIMAX(n)` | 0/1 | Wide spread + ultra high vol + close near high |
| `SPRING(n, tol%)` | 0/1 | False breakdown below range low + recovery |
| `UPTHRUST(n, tol%)` | 0/1 | False breakout above range high + rejection |
| `SECONDARY_TEST(n)` | 0/1 | Retest of low on lower volume |
| `SIGN_OF_STRENGTH(n)` | 0/1 | Strong rally + high volume + breaks structure |
| `SIGN_OF_WEAKNESS(n)` | 0/1 | Strong decline + high volume + breaks structure |
| `IN_TRADING_RANGE(n, tol%)` | 0/1 | Price contained within range |
| `RANGE_HIGH(n)` | price | Upper boundary of trading range |
| `RANGE_LOW(n)` | price | Lower boundary of trading range |
| `RANGE_DURATION(n)` | bars | How long in current range |

### Files to Modify

```
tradery-core/src/main/java/com/tradery/dsl/TokenType.java
  → Add: WYCKOFF_FUNC

tradery-core/src/main/java/com/tradery/dsl/Lexer.java
  → Add keywords: SELLING_CLIMAX, BUYING_CLIMAX, SPRING, UPTHRUST,
                  SECONDARY_TEST, SIGN_OF_STRENGTH, SIGN_OF_WEAKNESS,
                  IN_TRADING_RANGE, RANGE_HIGH, RANGE_LOW, RANGE_DURATION

tradery-core/src/main/java/com/tradery/dsl/AstNode.java
  → Add: record WyckoffFunctionCall(String func, Integer lookback, Double tolerance)

tradery-core/src/main/java/com/tradery/dsl/Parser.java
  → Add: wyckoffFunctionCall() method

tradery-core/src/main/java/com/tradery/engine/ConditionEvaluator.java
  → Add: evaluateWyckoffFunction() method

NEW: tradery-core/src/main/java/com/tradery/indicators/WyckoffIndicators.java
  → Static calculation methods for Wyckoff events
```

### Detection Logic Examples

**SPRING Detection:**
```java
// 1. Establish trading range from HIGH_OF(n) / LOW_OF(n)
// 2. Price dips below LOW_OF(n) by tolerance%
// 3. Close recovers back above LOW_OF(n)
// 4. Optional: LOW_VOLUME confirms (no supply)
```

**SELLING_CLIMAX Detection:**
```java
// 1. WIDE_SPREAD(20, 1.5) == 1 (spread > 1.5x average)
// 2. HIGH_VOLUME(20, 2.0) == 1 (volume > 2x average)
// 3. CLOSE_POSITION < 0.25 (close in lower 25%)
// 4. Prior downtrend context (price < SMA(20))
```

---

## Tier 3: Wyckoff Schematics (Hoops)

### Accumulation Schematic #1

```yaml
# ~/.tradery/hoops/wyckoff-accumulation/hoop.yaml
id: wyckoff-accumulation
name: Wyckoff Accumulation Schematic
symbol: BTCUSDT
timeframe: 4h
priceSmoothingType: HLC3
priceSmoothingPeriod: 3
cooldownBars: 50

hoops:
  - name: selling-climax
    minPricePercent: null    # Anchor point
    maxPricePercent: null
    distance: 1
    tolerance: 0
    anchorMode: actual_hit

  - name: automatic-rally
    minPricePercent: 3.0
    maxPricePercent: 12.0
    distance: 8
    tolerance: 5
    anchorMode: actual_hit

  - name: secondary-test
    minPricePercent: -8.0    # Back toward SC
    maxPricePercent: -2.0
    distance: 15
    tolerance: 10
    anchorMode: actual_hit

  - name: spring
    minPricePercent: -3.0    # Below ST
    maxPricePercent: 1.0
    distance: 20
    tolerance: 15
    anchorMode: actual_hit

  - name: sign-of-strength
    minPricePercent: 5.0
    maxPricePercent: 15.0
    distance: 10
    tolerance: 7
    anchorMode: actual_hit
```

### Distribution Schematic #1

```yaml
# ~/.tradery/hoops/wyckoff-distribution/hoop.yaml
id: wyckoff-distribution
name: Wyckoff Distribution Schematic
symbol: BTCUSDT
timeframe: 4h

hoops:
  - name: buying-climax
    minPricePercent: null
    maxPricePercent: null
    distance: 1
    tolerance: 0
    anchorMode: actual_hit

  - name: automatic-reaction
    minPricePercent: -12.0
    maxPricePercent: -3.0
    distance: 8
    tolerance: 5
    anchorMode: actual_hit

  - name: secondary-test-dist
    minPricePercent: 2.0
    maxPricePercent: 8.0
    distance: 15
    tolerance: 10
    anchorMode: actual_hit

  - name: upthrust
    minPricePercent: -1.0
    maxPricePercent: 3.0
    distance: 20
    tolerance: 15
    anchorMode: actual_hit

  - name: sign-of-weakness
    minPricePercent: -15.0
    maxPricePercent: -5.0
    distance: 10
    tolerance: 7
    anchorMode: actual_hit
```

### Re-Accumulation (Mid-Trend)

```yaml
id: wyckoff-reaccumulation
name: Wyckoff Re-Accumulation
timeframe: 1h
# Shorter, shallower pattern for trend continuation

hoops:
  - name: trend-pause
    minPricePercent: null
    maxPricePercent: null
    distance: 1
    tolerance: 0
    anchorMode: actual_hit

  - name: shakeout
    minPricePercent: -5.0
    maxPricePercent: -1.0
    distance: 10
    tolerance: 5
    anchorMode: actual_hit

  - name: test
    minPricePercent: -2.0
    maxPricePercent: 1.0
    distance: 8
    tolerance: 4
    anchorMode: actual_hit

  - name: breakout
    minPricePercent: 2.0
    maxPricePercent: null
    distance: 6
    tolerance: 4
    anchorMode: actual_hit
```

---

## Tier 4: Wyckoff Phases

### Phase Definitions

```yaml
# ~/.tradery/phases/wyckoff-accumulation-likely/phase.yaml
id: wyckoff-accumulation-likely
name: Wyckoff Accumulation Likely
category: Wyckoff
timeframe: 4h
condition: |
  IN_TRADING_RANGE(50, 3) == 1 AND
  RANGE_DURATION(50) > 20 AND
  SMA(50) < SMA(200) AND
  LOW_VOLUME(20, 0.8) == 1
```

```yaml
# ~/.tradery/phases/wyckoff-distribution-likely/phase.yaml
id: wyckoff-distribution-likely
name: Wyckoff Distribution Likely
category: Wyckoff
timeframe: 4h
condition: |
  IN_TRADING_RANGE(50, 3) == 1 AND
  RANGE_DURATION(50) > 20 AND
  SMA(50) > SMA(200) AND
  LOW_VOLUME(20, 0.8) == 1
```

```yaml
# ~/.tradery/phases/wyckoff-spring-active/phase.yaml
id: wyckoff-spring-active
name: Wyckoff Spring Active
category: Wyckoff
timeframe: 1h
condition: SPRING(30, 2) == 1 AND NO_SUPPLY(10) == 1
```

```yaml
# ~/.tradery/phases/wyckoff-upthrust-active/phase.yaml
id: wyckoff-upthrust-active
name: Wyckoff Upthrust Active
category: Wyckoff
timeframe: 1h
condition: UPTHRUST(30, 2) == 1 AND NO_DEMAND(10) == 1
```

```yaml
# ~/.tradery/phases/wyckoff-markup-phase/phase.yaml
id: wyckoff-markup-phase
name: Wyckoff Markup Phase
category: Wyckoff
timeframe: 4h
condition: |
  SIGN_OF_STRENGTH(50) == 1 AND
  close > RANGE_HIGH(100) AND
  ADX(14) > 25 AND
  PLUS_DI(14) > MINUS_DI(14)
```

```yaml
# ~/.tradery/phases/wyckoff-markdown-phase/phase.yaml
id: wyckoff-markdown-phase
name: Wyckoff Markdown Phase
category: Wyckoff
timeframe: 4h
condition: |
  SIGN_OF_WEAKNESS(50) == 1 AND
  close < RANGE_LOW(100) AND
  ADX(14) > 25 AND
  MINUS_DI(14) > PLUS_DI(14)
```

---

## Tier 5: Trade Analytics

### Metrics Captured at Entry/Exit

```java
// Add to Trade.java
Map<String, Double> wyckoffMetrics:
  - spreadVsAvg
  - volumeVsAvg
  - effortVsResult
  - closePosition
  - inTradingRange (0/1)
  - rangeDuration
  - distanceFromRangeHigh (%)
  - distanceFromRangeLow (%)
```

### Summary.json Analysis

```json
{
  "analysis": {
    "byWyckoff": {
      "entryAfterSpring": { "count": 5, "winRate": 80 },
      "entryInAccumulation": { "count": 12, "winRate": 75 },
      "entryWithLowEffort": { "count": 15, "winRate": 73 }
    },
    "suggestions": [
      "Spring entries show 80% win rate - consider requiring wyckoff-spring-active phase"
    ]
  }
}
```

---

## Integration with ICT/Levels Plan

The ICT levels plan (docs/LEVELS_IMPLEMENTATION_PLAN.md) shares infrastructure:

| Shared Component | Usage |
|-----------------|-------|
| `SwingDetector` | Range boundaries, structure breaks |
| `ZoneClusterer` | Accumulation/distribution zones |
| BOS/CHoCH detection | Sign of Strength/Weakness confirmation |

**Future Level Type:**
```yaml
type: wyckoff_range
rangeType: accumulation | distribution
priorTrendDirection: down | up
```

---

## File Changes Summary

### New Files
| File | Purpose |
|------|---------|
| `tradery-core/.../indicators/VsaIndicators.java` | VSA calculations |
| `tradery-core/.../indicators/WyckoffIndicators.java` | Wyckoff calculations |
| `~/.tradery/phases/wyckoff-*.yaml` | Wyckoff phases (6 files) |
| `~/.tradery/hoops/wyckoff-*.yaml` | Wyckoff schematics (3 files) |

### Modified Files
| File | Changes |
|------|---------|
| `TokenType.java` | Add VSA_FUNC, WYCKOFF_FUNC |
| `Lexer.java` | Add ~25 keywords |
| `AstNode.java` | Add VsaFunctionCall, WyckoffFunctionCall |
| `Parser.java` | Add vsaFunctionCall(), wyckoffFunctionCall() |
| `ConditionEvaluator.java` | Add evaluation methods |
| `IndicatorEngine.java` | Delegate to VSA/Wyckoff indicators |
| `CLAUDE.md` | Document new functions |
| `DslHelpDialog.java` | Add help content |

---

## Example Strategy

```yaml
id: wyckoff-spring-entry
name: Wyckoff Spring Entry
entrySettings:
  condition: |
    SPRING(30, 2) == 1 AND
    NO_SUPPLY(10) == 1 AND
    EFFORT_VS_RESULT(20) < 0.8
  maxOpenTrades: 1

phaseSettings:
  requiredPhaseIds: [wyckoff-accumulation-likely]
  excludedPhaseIds: [fomc-meeting-day]

exitSettings:
  zones:
    - name: Take Profit
      condition: close > RANGE_HIGH(50) * 1.02
    - name: Stop Loss
      maxPnlPercent: -3.0

backtestSettings:
  symbol: BTCUSDT
  timeframe: 1h
  duration: 1y
```

---

## Verification Plan

1. **Unit Tests**: Test each VSA/Wyckoff function calculation
2. **Historical Validation**: Test on known Wyckoff events (BTC Q4 2022, March 2020)
3. **Backtest**: Run strategies with Wyckoff filters, compare win rates
4. **MCP Tools**: Verify `tradery_eval_condition` works with new functions
5. **UI**: Confirm DslHelpDialog shows new functions
