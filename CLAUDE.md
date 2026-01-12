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
├── api.json                          # API connection info (port, token) - written on startup
├── strategies/                       # Strategy folders (one per strategy)
│   └── {id}/                         # Each strategy has its own folder
│       ├── strategy.json             # Strategy definition
│       ├── summary.json              # Metrics + pre-computed analysis (AI-friendly)
│       ├── latest.json               # Full backtest result (backward compat)
│       ├── trades/                   # Individual trade files with descriptive names
│       │   ├── 0001_WIN_+2p5pct_uptrend_weekdays.json
│       │   ├── 0002_LOSS_-1p2pct_ranging.json
│       │   └── 0003_REJECTED_0pct.json
│       └── history/                  # Historical backtest runs
│           └── YYYY-MM-DD_HH-mm.json # Timestamped full results
├── phases/                           # Phase definitions (market regimes)
│   └── {id}/phase.json
├── hoops/                            # Hoop pattern definitions
│   └── {id}/hoop.json
├── data/
│   └── BTCUSDT/
│       └── 1h/
│           └── 2024-01.csv           # Cached OHLC data
├── aggtrades/                        # Aggregated trade data for orderflow
│   └── BTCUSDT/
│       └── 2024-01.csv
├── funding/                          # Funding rate cache
│   └── BTCUSDT.csv
└── openinterest/                     # Open interest data (5m resolution)
    └── BTCUSDT.csv

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
│       ├── ResultStore.java          # Read/write backtest results per strategy
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

STOCHASTIC(kPeriod)                # Stochastic %K (0-100, default dPeriod=3)
STOCHASTIC(kPeriod, dPeriod)       # Stochastic with custom smoothing
STOCHASTIC(kPeriod, dPeriod).k     # Stochastic %K line
STOCHASTIC(kPeriod, dPeriod).d     # Stochastic %D line (smoothed %K)
```

### Range & Volume Functions
```
HIGH_OF(period)          # Highest high of last N bars
LOW_OF(period)           # Lowest low of last N bars
AVG_VOLUME(period)       # Average volume of last N bars
RANGE_POSITION(period)   # Position within range: -1=low, 0=mid, +1=high
RANGE_POSITION(period, skip)  # With skip bars before range calculation
```

**RANGE_POSITION vs STOCHASTIC:**
- `STOCHASTIC` is clamped to 0-100 (traditional momentum oscillator)
- `RANGE_POSITION` extends beyond ±1 for breakouts (returns -1.5 if below range, +1.5 if above)

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

**Daily Session Volume Profile (Tier 1):**
```
PREV_DAY_POC             # Previous day's Point of Control
PREV_DAY_VAH             # Previous day's Value Area High
PREV_DAY_VAL             # Previous day's Value Area Low
TODAY_POC                # Current day's developing POC (updates each bar)
TODAY_VAH                # Current day's developing VAH (updates each bar)
TODAY_VAL                # Current day's developing VAL (updates each bar)
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

### Open Interest Functions
Requires OI data (auto-fetched from Binance Futures API at 5-minute resolution).
Note: Binance limits historical OI to 30 days, but local cache persists indefinitely.
Running the app regularly builds unlimited historical OI data.

```
OI                       # Current open interest value (in billions USD)
OI_CHANGE                # OI change from previous bar
OI_DELTA(period)         # OI change over N bars
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

# Stochastic
STOCHASTIC(14).k < 20                     # Oversold condition
STOCHASTIC(14).k crosses_above STOCHASTIC(14).d  # Bullish crossover
STOCHASTIC(14, 3).d > 80                  # Overbought with custom smoothing

# Range Position (breakout detection)
RANGE_POSITION(20) > 1                    # Price above 20-bar range (breakout)
RANGE_POSITION(20) < -1                   # Price below 20-bar range (breakdown)
RANGE_POSITION(50, 5) > 0.8               # Near top of 50-bar range, skip last 5 bars

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

# Open Interest
OI_CHANGE > 0 AND close > close[1]        # Rising OI + rising price = new longs
OI_CHANGE < 0 AND close > close[1]        # Falling OI + rising price = short covering
OI_DELTA(12) < 0 AND close > SMA(20)      # OI decrease over 12 bars + price above MA

# Daily Session Volume Profile
close crosses_above PREV_DAY_POC          # Price reclaims yesterday's POC
close > PREV_DAY_VAH AND TODAY_POC > PREV_DAY_POC  # Acceptance above value
close < TODAY_VAL AND close > PREV_DAY_VAL         # Mean reversion setup
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

## Trade Analytics (AI-Friendly Data)

Each trade includes rich analytics data for AI analysis:

### Per-Trade Fields
```json
{
  "id": "trade-1234567890-1234",
  "side": "long",
  "entryBar": 100,
  "entryTime": 1704067200000,
  "entryPrice": 42000.0,
  "quantity": 0.1,
  "exitBar": 115,
  "exitTime": 1704153600000,
  "exitPrice": 43500.0,
  "pnl": 145.50,
  "pnlPercent": 3.57,
  "exitReason": "take_profit",
  "exitZone": "Take Profit",

  // Trade quality analytics
  "mfe": 4.2,              // Max Favorable Excursion (best unrealized P&L %)
  "mae": -1.5,             // Max Adverse Excursion (worst unrealized P&L %)
  "mfeBar": 110,           // Bar when MFE occurred
  "maeBar": 103,           // Bar when MAE occurred

  // Phase context for pattern analysis
  "activePhasesAtEntry": ["uptrend", "weekdays", "us-market-hours"],
  "activePhasesAtExit": ["uptrend", "weekdays"],

  // Indicator values at entry/exit (for AI analysis of what triggered the trade)
  // NOTE: Context indicators are ALWAYS captured regardless of DSL conditions:
  //   ATR(14), SMA(50), SMA(200), RSI(14), ADX(14), AVG_VOLUME(20)
  // Plus any indicators explicitly used in entry/exit conditions
  "entryIndicators": {
    "RSI(14)": 28.5,
    "SMA(50)": 41200.0,
    "SMA(200)": 41500.0,
    "ATR(14)": 850.0,
    "ADX(14)": 32.5,
    "AVG_VOLUME(20)": 1500000.0,
    "price": 42000.0,
    "volume": 1250000.0
  },
  "exitIndicators": {
    "RSI(14)": 72.3,
    "SMA(50)": 42000.0,
    "SMA(200)": 42100.0,
    "ATR(14)": 920.0,
    "ADX(14)": 28.1,
    "AVG_VOLUME(20)": 1800000.0,
    "price": 43500.0,
    "volume": 980000.0
  },

  // Indicator values at MFE/MAE points (for analyzing what led to best/worst moments)
  "mfeIndicators": {
    "RSI(14)": 68.2,
    "SMA(200)": 41800.0,
    "price": 43800.0,
    "volume": 1100000.0
  },
  "maeIndicators": {
    "RSI(14)": 32.1,
    "SMA(200)": 41550.0,
    "price": 41700.0,
    "volume": 850000.0
  }
}
```

**Derived metrics** (via Trade methods):
- `captureRatio()` = pnlPercent / mfe (how much of the move was captured)
- `painRatio()` = |mae| / mfe (drawdown relative to potential)
- `barsToMfe()` = mfeBar - entryBar (time to reach best price)
- `barsToMae()` = maeBar - entryBar (time to reach worst price)

### Performance Metrics
```json
{
  "totalTrades": 45,
  "winningTrades": 28,
  "losingTrades": 17,
  "winRate": 62.2,
  "profitFactor": 2.15,
  "totalReturnPercent": 34.5,
  "maxDrawdownPercent": 8.2,

  // Risk-adjusted metrics
  "sharpeRatio": 1.85,
  "sortinoRatio": 2.42,      // Uses only downside deviation

  // Streak analysis
  "maxConsecutiveWins": 7,
  "maxConsecutiveLosses": 3,

  // Aggregate trade quality
  "averageMfe": 3.8,         // Average best unrealized P&L %
  "averageMae": -2.1         // Average worst unrealized P&L %
}
```

### AI Analysis Patterns

**Using phase context to identify edge:**
```
If trades with activePhasesAtEntry containing "uptrend" have higher win rate:
  → Strategy works better in trending markets
  → Consider adding "uptrend" as required phase

If trades exiting during "us-market-hours" have better P&L:
  → US session exits capture more profit
  → Consider time-based exit logic
```

**Using MFE/MAE for exit optimization:**
```
If averageMfe >> pnlPercent (low captureRatio):
  → Exits are too early, leaving profit on table
  → Consider wider take-profit or trailing stop

If |averageMae| is high but winRate is good:
  → Trades endure significant drawdown before winning
  → Stop-loss might be appropriate or too wide

If maeBar consistently < mfeBar for winners:
  → Trades hit drawdown first, then recover
  → Normal pattern, validates hold strategy
```

**Using indicator values for entry/exit optimization:**
```
If winning trades have entryIndicators["RSI(14)"] < 25 on average:
  → Oversold entries perform better
  → Consider tightening RSI threshold from 30 to 25

If losing trades have entryIndicators["price"] > entryIndicators["SMA(200)"]:
  → Entries above SMA(200) underperform
  → Ensure "price > SMA(200)" is in entry condition

If exitIndicators["RSI(14)"] is consistently > 70 for profitable exits:
  → RSI overbought is a good exit signal
  → Consider adding RSI exit condition to take profit zone

Compare entryIndicators to exitIndicators:
  → Track which indicators moved most during profitable trades
  → Identify which indicator crossovers correlate with good exits

Analyze mfeIndicators to find what conditions led to peak profit:
  → If mfeIndicators["RSI(14)"] is consistently high at MFE
  → Consider adding RSI-based trailing stop or take profit
  → Compare mfeIndicators to exitIndicators to see what changed

Analyze maeIndicators to understand drawdown causes:
  → If maeIndicators show specific indicator levels at worst points
  → Identify early warning signals for exits
  → Use to optimize stop-loss placement
```

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
8. **Daily session levels** - PREV_DAY_POC/VAH/VAL, TODAY_POC/VAH/VAL (UTC day boundary)
9. **Large trade detection** - WHALE_DELTA, WHALE_BUY_VOL, WHALE_SELL_VOL, LARGE_TRADE_COUNT
10. **Funding rate** - FUNDING, FUNDING_8H + built-in funding phases (high/negative/extreme/neutral)
11. **Open Interest** - OI, OI_CHANGE, OI_DELTA (auto-fetched 5m resolution, cache persists)

**What's NOT yet implemented (potential future features):**
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

### HTTP API

When Tradery is running, an HTTP API is available for querying indicators, evaluating DSL conditions, and managing strategies. This uses the exact same calculation logic as backtests.

**Connection Info:**

The API uses dynamic port allocation and session-based authentication. On startup, Tradery writes `~/.tradery/api.json`:

```json
{
  "port": 7842,
  "token": "a1b2c3d4e5f6...",
  "baseUrl": "http://localhost:7842"
}
```

**Authentication:**

All endpoints require a session token. Provide via:
- Header: `X-Session-Token: <token>`
- Query param: `?token=<token>`

```bash
# Read connection info
API_INFO=$(cat ~/.tradery/api.json)
PORT=$(echo $API_INFO | jq -r '.port')
TOKEN=$(echo $API_INFO | jq -r '.token')

# Use header auth (recommended)
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/candles?symbol=BTCUSDT&timeframe=1h&bars=100"

# Or query param auth
curl "http://localhost:$PORT/candles?symbol=BTCUSDT&timeframe=1h&bars=100&token=$TOKEN"
```

**Endpoints:**

```bash
# Check if API is running
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/status"

# Get candle data
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/candles?symbol=BTCUSDT&timeframe=1h&bars=100"

# Get a single indicator
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/indicator?name=RSI(14)&symbol=BTCUSDT&timeframe=1h&bars=100"

# Get multiple indicators at once
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/indicators?names=RSI(14),SMA(200),ATR(14)&symbol=BTCUSDT&timeframe=1h&bars=100"

# Evaluate a DSL condition - find bars where condition is true
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/eval?condition=RSI(14)<30&symbol=BTCUSDT&timeframe=1h&bars=500"
```

**Supported indicators:**
- SMA(n), EMA(n), RSI(n), ATR(n)
- ADX(n), PLUS_DI(n), MINUS_DI(n)
- MACD(fast,slow,signal), MACD(12,26,9).line/signal/histogram
- BBANDS(period,stdDev), BBANDS(20,2).upper/middle/lower
- price, close, volume

**Example: Find oversold conditions**
```bash
curl "http://localhost:7842/eval?condition=RSI(14)<30%20AND%20price>SMA(200)&symbol=BTCUSDT&timeframe=1h&bars=1000"
# Returns: { "matches": [{ "bar": 45, "time": 1704067200000, "price": 42350 }, ...] }
```

**Strategy Management Endpoints:**

```bash
# List all strategies
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/strategies"
# Returns: { "strategies": [{ "id": "rsi-reversal", "name": "RSI Reversal", ... }] }

# Get a strategy
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/strategy/rsi-reversal"

# Update a strategy (partial update)
curl -X POST -H "X-Session-Token: $TOKEN" -H "Content-Type: application/json" \
  "http://localhost:$PORT/strategy/rsi-reversal" \
  -d '{"entrySettings": {"condition": "RSI(14) < 25"}}'

# Run backtest (blocking - waits for completion)
curl -X POST -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/strategy/rsi-reversal/backtest"
# Returns: { "success": true, "metrics": { "winRate": 62.5, ... }, "tradesCount": 48 }

# Get latest results
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/strategy/rsi-reversal/results"
```

**Phase Endpoints:**

```bash
# List all phases
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/phases"
# Returns: { "phases": [{ "id": "uptrend", "name": "Uptrend", "condition": "...", ... }] }

# Get a specific phase
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/phase/uptrend"

# Find where a phase is active (phase bounds)
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/phase/uptrend/bounds?symbol=BTCUSDT&bars=500"
# Returns time ranges when the phase condition was true:
# {
#   "phaseId": "uptrend",
#   "phaseName": "Uptrend",
#   "bounds": [
#     { "startBar": 50, "endBar": 120, "startTime": 1704067200000, "endTime": 1704319200000, "bars": 70 },
#     { "startBar": 200, "endBar": 350, "startTime": ..., "ongoing": true }
#   ],
#   "activeBars": 221,
#   "activePercent": 49.22,
#   "boundCount": 2
# }

# Evaluate phase on different timeframe/symbol
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/phase/uptrend/bounds?symbol=ETHUSDT&timeframe=4h&bars=200"
```

**AI Workflow Example:**
```bash
# Read connection info once
API_INFO=$(cat ~/.tradery/api.json)
PORT=$(echo $API_INFO | jq -r '.port')
TOKEN=$(echo $API_INFO | jq -r '.token')

# 1. Check current strategy
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/strategy/my-strategy"

# 2. Check when uptrend phase is active
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/phase/uptrend/bounds?bars=500"

# 3. Analyze market conditions
curl -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/eval?condition=RSI(14)<30&symbol=BTCUSDT&timeframe=1d&bars=200"

# 4. Update entry condition based on analysis
curl -X POST -H "X-Session-Token: $TOKEN" -H "Content-Type: application/json" \
  "http://localhost:$PORT/strategy/my-strategy" \
  -d '{"entrySettings": {"condition": "RSI(14) < 25 AND ADX(14) > 25"}}'

# 5. Run backtest to validate
curl -X POST -H "X-Session-Token: $TOKEN" "http://localhost:$PORT/strategy/my-strategy/backtest"

# 6. If improved, keep changes. If not, try different parameters.
```

### File-Based Access

With files on disk, Claude Code can:
- View/edit strategies: `~/.tradery/strategies/{id}/strategy.json`
- View summary + analysis: `~/.tradery/strategies/{id}/summary.json`
- Browse trades by filename: `~/.tradery/strategies/{id}/trades/`
- View full results: `~/.tradery/strategies/{id}/latest.json`
- View result history: `~/.tradery/strategies/{id}/history/*.json`
- View/edit phases: `~/.tradery/phases/{id}/phase.json`
- View/edit hoop patterns: `~/.tradery/hoops/{id}/hoop.json`
- Query OHLC data: `~/.tradery/data/{symbol}/{timeframe}/*.csv`

### AI-Friendly Result Structure

**summary.json** - Quick overview without parsing all trades:
```json
{
  "metrics": { "winRate": 62.5, "sharpeRatio": 1.85, ... },
  "tradeCounts": { "total": 48, "winners": 30, "losers": 18 },
  "analysis": {
    "byPhase": {
      "uptrend": { "count": 20, "winRate": 75, "vsOverall": +12.5 },
      "ranging": { "count": 15, "winRate": 46.7, "vsOverall": -15.8 }
    },
    "byHour": { "14": { "winRate": 80 }, "03": { "winRate": 40 } },
    "byDayOfWeek": { "Mon": { "winRate": 70 }, "Fri": { "winRate": 55 } },
    "byExitReason": { "take_profit": { "count": 20, "avgPnlPercent": 3.5 } },
    "suggestions": [
      "Consider requiring 'uptrend' phase (+13% win rate, 20 trades)",
      "Hour 15 UTC shows +18% win rate (12 trades) - consider time filter",
      "Mon shows +12% win rate (15 trades) - consider day filter",
      "Losers hit -4.5% MAE vs winners -1.2% - tighter stop-loss may cut losses earlier",
      "Quick trades (<=10 bars) have 72% win rate vs 55% for longer - consider time-based exit",
      "Tighten RSI entry threshold - winners avg 25 vs losers 35",
      "Add 'price > SMA(200)' filter - 70% of winners vs 45% of losers entered above",
      "Exits may be too early - capturing only 45% of average MFE"
    ]
  },
  "historyTrends": {
    "hasHistory": true,
    "comparedRuns": 10,
    "vsLastRun": {
      "winRate": { "current": 62.5, "previous": 58.0, "delta": 4.5, "improved": true, "display": "+4.50%" },
      "profitFactor": { "current": 2.1, "previous": 1.8, "delta": 0.3, "improved": true },
      "totalReturnPercent": { "current": 34.5, "previous": 28.2, "delta": 6.3, "improved": true },
      "maxDrawdownPercent": { "current": 8.2, "previous": 12.1, "delta": -3.9, "improved": true }
    },
    "configChanged": true,
    "configChanges": ["Entry condition changed", "Required phases: 0 → 1"],
    "trajectory": {
      "winRate": { "direction": "improving", "slope": -2.5, "values": [62.5, 58.0, 55.2, 52.0] },
      "profitFactor": { "direction": "stable", "slope": 0.1, "values": [2.1, 2.0, 1.9, 2.0] },
      "maxDrawdown": { "direction": "improving", "slope": 1.2, "values": [8.2, 10.5, 12.1, 11.0] }
    },
    "historical": {
      "bestWinRate": 62.5,
      "worstWinRate": 45.0,
      "isNewBest": true,
      "bestReturn": 34.5,
      "isNewBestReturn": true
    }
  }
}
```

**Suggestion Types Generated:**
| Type | Example | Trigger Condition |
|------|---------|-------------------|
| Phase | "Consider requiring 'uptrend' phase" | Win rate diff > 10% |
| Hour | "Hour 15 UTC shows +18% win rate" | Win rate diff > 12%, 5+ trades |
| Day | "Mon shows +12% win rate" | Win rate diff > 10%, 5+ trades |
| Exit Zone | "Stop losses avg -5% vs take profits +3%" | SL avg > 1.5x TP avg |
| MAE | "Losers hit -4.5% MAE vs winners -1.2%" | Loser MAE > winner MAE + 2% |
| Holding Period | "Quick trades have 72% win rate" | Short/long win rate diff > 12% |
| RSI | "Tighten RSI entry threshold" | Winner/loser avg diff > 5 |
| ADX | "Add ADX filter" | Winner/loser avg diff > 3 |
| ATR | "Winning entries have 20% lower ATR" | Volatility diff > 15% |
| SMA Position | "Add 'price > SMA(200)' filter" | Above/below ratio diff > 15% |
| MFE Capture | "Exits may be too early" | Capture ratio < 50% |
| Streaks | "Max 5 consecutive losses detected" | 5+ consecutive losses |

**History Trends Fields:**
| Field | Description |
|-------|-------------|
| `vsLastRun.*` | Delta vs previous backtest for each metric |
| `configChanged` | Whether strategy config changed since last run |
| `configChanges` | List of detected config differences |
| `trajectory.*.direction` | "improving", "stable", or "declining" based on linear regression |
| `trajectory.*.values` | Last 5-6 metric values (newest first) |
| `historical.isNewBest` | Whether current run beat all previous runs |

**trades/** - Individual files with descriptive names:
```
ls trades/
0001_WIN_+2p5pct_uptrend_weekdays.json
0002_LOSS_-1p2pct_ranging.json
0003_WIN_+4p1pct_uptrend_us-market-hours.json
0004_REJECTED_0pct.json
```

AI can glob filenames to quickly understand:
- `ls *_WIN_*` → Count/list winners
- `ls *_LOSS_*uptrend*` → Find losses during uptrend
- `ls *REJECTED*` → See rejected signals

### Workflow for AI Analysis

1. **Read summary.json** for overview and pre-computed suggestions
2. **Glob trade filenames** to understand distribution
3. **Sample specific trades** (e.g., read 5 biggest losses) for deeper analysis
4. **Modify strategy.json** based on findings
5. App auto-reloads and re-runs backtest

### Auto-Reload
FileWatcher monitors strategy/phase/hoop directories. External edits trigger automatic reload and backtest re-run.