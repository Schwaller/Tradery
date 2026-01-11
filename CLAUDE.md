# Tradery - Java Desktop Trading Strategy Backtester

## Project Overview
A Java Swing desktop application for building and backtesting trading strategies with file-based storage that enables seamless Claude Code integration.

**Tech Stack:**
- Java 21 with Swing UI (native macOS look-and-feel)
- JFreeChart for charting
- Jackson for JSON serialization
- OkHttp for Binance API calls
- Gradle build system

## File Structure

```
~/.tradery/                           # User data directory (created on first run)
├── strategies/                       # Strategy JSON files
│   └── rsi-reversal.json
├── phases/                           # Phase definitions (market regimes)
│   └── {id}/phase.json
├── hoops/                            # Hoop pattern definitions
│   └── {id}/hoop.json
├── results/
│   └── latest.json                   # Most recent backtest result
├── data/
│   └── BTCUSDT/
│       └── 1h/
│           └── 2024-01.csv           # Cached OHLC data
└── aggtrades/                        # Aggregated trade data for orderflow
    └── BTCUSDT/
        └── 2024-01.csv

/Users/martinschwaller/Code/Tradery/  # Project source
├── build.gradle
├── src/main/java/com/tradery/
│   ├── TraderyApp.java               # Main entry point
│   ├── ui/
│   │   ├── MainFrame.java            # Main window with 4-panel layout
│   │   ├── StrategyPanel.java        # Strategy list & editor (left)
│   │   ├── ControlPanel.java         # Toolbar with symbol/timeframe
│   │   ├── ChartsPanel.java          # 5 charts (price, equity, comparison, capital usage, trade P&L)
│   │   ├── TradeTablePanel.java      # Trade list with rejected trades
│   │   └── MetricsPanel.java         # Performance stats + settings
│   ├── dsl/
│   │   ├── Lexer.java                # Tokenize DSL expressions
│   │   ├── Parser.java               # Build AST from tokens
│   │   └── AstNode.java              # AST node types
│   ├── indicators/
│   │   └── IndicatorEngine.java      # All indicator calculations
│   ├── engine/
│   │   ├── BacktestEngine.java       # Main simulation loop
│   │   ├── ConditionEvaluator.java   # Evaluate DSL conditions
│   │   ├── PhaseEvaluator.java       # Evaluate market phases
│   │   └── HoopPatternEvaluator.java # Evaluate hoop pattern matches
│   ├── data/
│   │   ├── BinanceClient.java        # Fetch OHLC from Binance API
│   │   ├── AggTradesClient.java      # Fetch aggregated trades for orderflow
│   │   └── CandleStore.java          # Read/write CSV, smart caching
│   ├── model/
│   │   ├── Strategy.java             # Strategy with grouped settings
│   │   ├── EntrySettings.java        # Entry conditions & DCA
│   │   ├── ExitSettings.java         # Exit zones & evaluation
│   │   ├── BacktestSettings.java     # Symbol, timeframe, capital, fees
│   │   ├── PhaseSettings.java        # Required/excluded phases
│   │   ├── HoopPatternSettings.java  # Hoop pattern configuration
│   │   ├── OrderflowSettings.java    # Orderflow mode & settings
│   │   ├── Trade.java                # Trade record
│   │   ├── Candle.java               # OHLC record
│   │   ├── AggTrade.java             # Aggregated trade for orderflow
│   │   ├── Phase.java                # Market phase definition
│   │   ├── Hoop.java                 # Single price checkpoint
│   │   └── HoopPattern.java          # Full pattern with hoops list
│   └── io/
│       ├── StrategyStore.java        # Read/write strategy JSON
│       ├── PhaseStore.java           # Read/write phase JSON
│       └── HoopPatternStore.java     # Read/write hoop pattern JSON
```

## Development Commands

```bash
# Compile
./gradlew compileJava

# Run the application
./gradlew run

# Build JAR
./gradlew build

# Clean
./gradlew clean
```

---

## DSL Reference (Complete)

The DSL (Domain Specific Language) is used for entry conditions, exit conditions, and phase definitions.

### Price References
```
price, close    # Current bar close price
open            # Current bar open price
high            # Current bar high price
low             # Current bar low price
volume          # Current bar volume
```

### Technical Indicators
```
SMA(period)              # Simple Moving Average
EMA(period)              # Exponential Moving Average
RSI(period)              # Relative Strength Index (0-100)
ATR(period)              # Average True Range

ADX(period)              # Average Directional Index (trend strength)
PLUS_DI(period)          # Positive Directional Indicator
MINUS_DI(period)         # Negative Directional Indicator

MACD(fast, slow, signal)           # MACD line value
MACD(fast, slow, signal).line      # MACD line (explicit)
MACD(fast, slow, signal).signal    # MACD signal line
MACD(fast, slow, signal).histogram # MACD histogram

BBANDS(period, stdDev)             # Bollinger middle band
BBANDS(period, stdDev).upper       # Bollinger upper band
BBANDS(period, stdDev).middle      # Bollinger middle band
BBANDS(period, stdDev).lower       # Bollinger lower band
```

### Range & Volume Functions
```
HIGH_OF(period)          # Highest high of last N bars
LOW_OF(period)           # Lowest low of last N bars
AVG_VOLUME(period)       # Average volume of last N bars
```

### Time Functions
```
HOUR                     # Hour of candle (0-23 UTC)
DAYOFWEEK                # Day of week (1=Monday, 7=Sunday)
DAY                      # Day of month (1-31)
MONTH                    # Month (1-12)
```

### Calendar Functions
```
IS_US_HOLIDAY            # 1 if US Federal Reserve holiday, 0 otherwise
IS_FOMC_MEETING          # 1 if FOMC meeting day, 0 otherwise
```

### Moon Functions
```
MOON_PHASE               # 0.0 = new moon, 0.5 = full moon, 1.0 = new moon
```

### Orderflow Functions
Require `orderflowSettings.mode` to be enabled in strategy.

**Tier 1 (calculated from OHLCV - instant):**
```
VWAP                     # Volume Weighted Average Price (session)
POC(period)              # Point of Control - highest volume price level
VAH(period)              # Value Area High (default 70% of volume)
VAL(period)              # Value Area Low
```

**Full (requires aggTrades sync):**
```
DELTA                    # Buy volume - sell volume for current bar
CUM_DELTA                # Cumulative delta from session start
WHALE_DELTA(threshold)   # Delta from trades > $threshold only
WHALE_BUY_VOL(threshold) # Buy volume from large trades only
WHALE_SELL_VOL(threshold)# Sell volume from large trades only
LARGE_TRADE_COUNT(threshold) # Count of trades > $threshold in bar
```

### Funding Rate Functions
Requires funding data (auto-fetched from Binance Futures API).

```
FUNDING                  # Current funding rate (as %, e.g., 0.01 = 0.01%)
FUNDING_8H               # 8-hour rolling average funding rate
```

### Operators

**Comparison:**
```
>   <   >=   <=   ==
```

**Cross Detection:**
```
crosses_above            # Left crossed above right this bar
crosses_below            # Left crossed below right this bar
```

**Logical:**
```
AND                      # Both conditions must be true
OR                       # Either condition can be true
```

**Arithmetic:**
```
+   -   *   /
```

### DSL Examples
```
# Basic indicators
RSI(14) < 30
price > SMA(200)
EMA(20) crosses_above EMA(50)

# MACD
MACD(12,26,9).histogram > 0
MACD(12,26,9).line crosses_above MACD(12,26,9).signal

# Bollinger Bands
close < BBANDS(20, 2).lower
close > BBANDS(20, 2).upper

# Trend detection with ADX
ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)

# Volume confirmation
volume > AVG_VOLUME(20) * 1.5
close > HIGH_OF(20)

# Time-based (typically used in phases)
HOUR >= 14 AND HOUR < 21
DAYOFWEEK >= 1 AND DAYOFWEEK <= 5

# Calendar events
IS_FOMC_MEETING == 1
IS_US_HOLIDAY == 0

# Moon phase
MOON_PHASE >= 0.48 AND MOON_PHASE <= 0.52

# Orderflow
close > VWAP
close > POC(20)
DELTA > 0 AND CUM_DELTA > 0

# Whale / Large Trade Detection
WHALE_DELTA(50000) > 0                    # Net buying from large trades
WHALE_BUY_VOL(100000) > WHALE_SELL_VOL(100000)  # Whales buying more
LARGE_TRADE_COUNT(50000) > 10             # Many large trades

# Funding rate
FUNDING > 0.05                            # High funding = overleveraged longs
FUNDING < 0 AND WHALE_DELTA(50000) > 0    # Shorts paying + whale buying
```

---

## Phase System

Phases are market regime conditions that filter when strategies can trade. They enable:
- **Multi-timeframe analysis**: A 1h strategy can require a daily uptrend phase
- **Calendar awareness**: Avoid trading during FOMC, holidays, weekends
- **Session filtering**: Only trade during London or US hours
- **Technical regimes**: Only trade during trends, avoid ranging markets

### How Phases Work
1. Each phase has its own DSL condition and timeframe (e.g., `SMA(50) > SMA(200)` on `1d`)
2. Phase is evaluated on its timeframe, producing true/false for each bar
3. Results are mapped down to the strategy's timeframe
4. Strategy entry only allowed when all required phases are active AND no excluded phases are active

### Phase Definition
```json
{
  "id": "uptrend",
  "name": "Uptrend",
  "description": "Strong uptrend using ADX",
  "category": "Technical",
  "condition": "ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)",
  "timeframe": "1d",
  "symbol": "BTCUSDT",
  "builtIn": true
}
```

### Built-In Phases (32 total)

**Session (5):**
| ID | Condition | Description |
|----|-----------|-------------|
| `asian-session` | `HOUR >= 0 AND HOUR < 9` | Tokyo/HK hours |
| `european-session` | `HOUR >= 7 AND HOUR < 16` | London/Frankfurt |
| `us-market-hours` | `HOUR >= 14 AND HOUR < 21` | NYSE/NASDAQ including open |
| `us-market-core` | `HOUR >= 15 AND HOUR < 21` | NYSE/NASDAQ core hours |
| `session-overlap` | `HOUR >= 14 AND HOUR < 16` | US/Europe overlap - highest liquidity |

**Time (9):**
| ID | Condition |
|----|-----------|
| `monday` - `sunday` | `DAYOFWEEK == N` |
| `weekdays` | `DAYOFWEEK >= 1 AND DAYOFWEEK <= 5` |
| `weekend` | `DAYOFWEEK >= 6` |

**Technical (7):**
| ID | Condition | Timeframe |
|----|-----------|-----------|
| `uptrend` | `ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)` | 1d |
| `downtrend` | `ADX(14) > 25 AND MINUS_DI(14) > PLUS_DI(14)` | 1d |
| `ranging` | `ADX(14) < 20` | 1d |
| `golden-cross` | `SMA(50) > SMA(200)` | 1d |
| `death-cross` | `SMA(50) < SMA(200)` | 1d |
| `overbought` | `RSI(14) > 70` | 1d |
| `oversold` | `RSI(14) < 30` | 1d |

**Calendar (7):**
| ID | Condition | Description |
|----|-----------|-------------|
| `month-start` | `DAY <= 5` | Fresh capital inflows |
| `month-end` | `DAY >= 25` | Rebalancing flows |
| `quarter-end` | `(MONTH == 3,6,9,12) AND DAY >= 25` | Window dressing |
| `us-bank-holiday` | `IS_US_HOLIDAY == 1` | Fed holidays |
| `fomc-meeting-day` | `IS_FOMC_MEETING == 1` | FOMC days (1d) |
| `fomc-meeting-hour` | `IS_FOMC_MEETING == 1` | FOMC days (1h precision) |
| `fomc-meeting-week` | `IS_FOMC_MEETING == 1` | FOMC weeks (1w) |

**Moon (4):**
| ID | Condition | Description |
|----|-----------|-------------|
| `full-moon-day` | `MOON_PHASE >= 0.48 AND MOON_PHASE <= 0.52` | ~1 day around full moon |
| `full-moon-hour` | `MOON_PHASE >= 0.499 AND MOON_PHASE <= 0.501` | ~1 hour precision |
| `new-moon-day` | `MOON_PHASE <= 0.02 OR MOON_PHASE >= 0.98` | ~1 day around new moon |
| `new-moon-hour` | `MOON_PHASE <= 0.001 OR MOON_PHASE >= 0.999` | ~1 hour precision |

**Funding (4):**
| ID | Condition | Description |
|----|-----------|-------------|
| `high-funding` | `FUNDING > 0.05` | Overleveraged longs |
| `negative-funding` | `FUNDING < 0` | Shorts paying longs |
| `extreme-funding` | `FUNDING > 0.1 OR FUNDING < -0.05` | Extreme positioning |
| `neutral-funding` | `FUNDING >= -0.01 AND FUNDING <= 0.02` | Balanced market |

### Strategy Phase Integration
```json
{
  "phaseSettings": {
    "requiredPhaseIds": ["uptrend", "weekdays"],
    "excludedPhaseIds": ["fomc-meeting-day", "us-bank-holiday"]
  }
}
```
- **requiredPhaseIds**: ALL must be active for entry
- **excludedPhaseIds**: NONE must be active for entry

---

## Orderflow System

Orderflow indicators provide insight into buying/selling pressure beyond standard OHLCV data.

### Modes
```json
{
  "orderflowSettings": {
    "mode": "tier1",           // "disabled", "tier1", or "full"
    "volumeProfilePeriod": 20,
    "valueAreaPercent": 70.0
  }
}
```

| Mode | Indicators | Data Source |
|------|------------|-------------|
| `disabled` | None | - |
| `tier1` | VWAP, POC, VAH, VAL | OHLCV (instant) |
| `full` | Tier 1 + DELTA, CUM_DELTA | Requires aggTrades sync |

### Indicator Descriptions
- **VWAP**: Volume-weighted average price for the session
- **POC**: Price level with highest volume (Point of Control)
- **VAH/VAL**: Value area high/low - range containing X% of volume
- **DELTA**: Buy volume minus sell volume per bar
- **CUM_DELTA**: Running total of delta from session start

---

## Strategy Settings Structure

Strategies use grouped settings for organization:

```json
{
  "id": "my-strategy",
  "name": "My Strategy",
  "enabled": true,

  "entrySettings": {
    "condition": "RSI(14) < 30 AND close > SMA(200)",
    "maxOpenTrades": 3,
    "minCandlesBetween": 5,
    "dca": {
      "enabled": false,
      "maxEntries": 3,
      "barsBetween": 10,
      "mode": "FIXED_PERCENT"
    }
  },

  "exitSettings": {
    "zones": [...],
    "evaluation": "FIRST_MATCH"
  },

  "backtestSettings": {
    "symbol": "BTCUSDT",
    "timeframe": "1h",
    "duration": "1y",
    "initialCapital": 10000,
    "positionSizingType": "PERCENT_EQUITY",
    "positionSizingValue": 10,
    "feePercent": 0.1,
    "slippagePercent": 0.05
  },

  "phaseSettings": {
    "requiredPhaseIds": ["uptrend"],
    "excludedPhaseIds": ["weekend"]
  },

  "hoopPatternSettings": {
    "entryMode": "DSL_ONLY",
    "exitMode": "DSL_ONLY",
    "requiredEntryPatternIds": [],
    "excludedEntryPatternIds": []
  },

  "orderflowSettings": {
    "mode": "tier1",
    "volumeProfilePeriod": 20,
    "valueAreaPercent": 70.0
  }
}
```

---

## Exit Zones

Exit zones define behavior at different P&L levels:

```json
{
  "zones": [
    {
      "name": "Stop Loss",
      "minPnlPercent": null,
      "maxPnlPercent": -5.0,
      "exitImmediately": true
    },
    {
      "name": "Take Profit",
      "minPnlPercent": 10.0,
      "maxPnlPercent": null,
      "exitImmediately": true
    },
    {
      "name": "Breakeven Exit",
      "minPnlPercent": 0.0,
      "maxPnlPercent": 5.0,
      "condition": "RSI(14) > 70",
      "exitImmediately": false
    }
  ]
}
```

**AI Guidance for Exit Zones:**
- Use `exitImmediately: true` for instant full-position exits
- Use `condition` for conditional exits within a P&L range
- Only use `exitPercent` for actual partial exits (scale-out)

---

## Hoop Patterns

Sequential price-checkpoint matching for detecting chart patterns.

```json
{
  "id": "double-bottom",
  "name": "Double Bottom",
  "hoops": [
    { "name": "first-low", "minPricePercent": -3.0, "maxPricePercent": -1.0,
      "distance": 5, "tolerance": 2, "anchorMode": "ACTUAL_HIT" },
    { "name": "middle-peak", "minPricePercent": 1.0, "maxPricePercent": 4.0,
      "distance": 7, "tolerance": 3, "anchorMode": "ACTUAL_HIT" },
    { "name": "second-low", "minPricePercent": -3.0, "maxPricePercent": 0.5,
      "distance": 7, "tolerance": 3, "anchorMode": "ACTUAL_HIT" },
    { "name": "breakout", "minPricePercent": 2.0, "maxPricePercent": null,
      "distance": 5, "tolerance": 3, "anchorMode": "ACTUAL_HIT" }
  ],
  "cooldownBars": 20,
  "allowOverlap": false
}
```

**Combine Modes:**
- `DSL_ONLY`: Ignore hoops, use only DSL
- `HOOP_ONLY`: Ignore DSL, use only hoops
- `AND`: Both must trigger
- `OR`: Either can trigger

---

## AI Guidance

**Before suggesting new features, check:**

1. **Time-based filtering** - Already implemented via phases with `HOUR`, `DAYOFWEEK`, `DAY`, `MONTH`
2. **Session filtering** - Built-in phases: asian-session, european-session, us-market-hours, etc.
3. **Calendar awareness** - `IS_US_HOLIDAY`, `IS_FOMC_MEETING` functions + built-in phases
4. **Multi-timeframe** - Phases can run on different timeframes than the strategy
5. **Trend detection** - ADX, PLUS_DI, MINUS_DI + built-in uptrend/downtrend phases
6. **Moon phases** - `MOON_PHASE` function + full-moon/new-moon phases
7. **Orderflow** - VWAP, POC, VAH, VAL, DELTA, CUM_DELTA (Tier 1 + Full modes)
8. **Large trade detection** - WHALE_DELTA, WHALE_BUY_VOL, WHALE_SELL_VOL, LARGE_TRADE_COUNT
9. **Funding rate** - FUNDING, FUNDING_8H + built-in funding phases (high/negative/extreme/neutral)

**What's NOT yet implemented (potential future features):**
- Open Interest (historical data limited to 30 days on Binance)
- Liquidation data (requires external API like Coinglass)
- Cross-symbol correlation (BTC dominance, ETH/BTC ratio)

---

## Maintenance Notes

### DSL Extension Checklist
When adding new DSL functions:
1. `dsl/Lexer.java` - Add keywords
2. `dsl/Parser.java` - Update grammar
3. `dsl/AstNode.java` - Add node type if needed
4. `engine/ConditionEvaluator.java` - Add evaluation logic
5. `indicators/IndicatorEngine.java` - Add calculation if needed
6. `ui/DslHelpDialog.java` - Update help content
7. **This file (CLAUDE.md)** - Document the new function

### Adding Built-in Phases
1. Create JSON in `~/.tradery/phases/{id}/phase.json`
2. Set `builtIn: true` and `version: "1.0"`
3. Update this file to document it

---

## Claude Code Integration

With files on disk, Claude Code can:
- View/edit strategies: `~/.tradery/strategies/*.json`
- View/edit phases: `~/.tradery/phases/*/phase.json`
- View/edit hoop patterns: `~/.tradery/hoops/*/hoop.json`
- See results: `~/.tradery/results/latest.json`
- Query OHLC data: `~/.tradery/data/{symbol}/{timeframe}/*.csv`

### Auto-Reload
FileWatcher monitors strategy/phase/hoop directories. External edits trigger automatic reload and backtest re-run.