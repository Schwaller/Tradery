# Tradery Strategy Optimizer

You are helping optimize trading strategies for a backtesting application. The app watches for file changes and automatically re-runs backtests when you modify strategy files.

## Session Startup (REQUIRED)

**On every new session**, before doing any work, call `tradery_get_context`. This single call returns everything you need: open windows, last focused strategy ID, chart config, the focused strategy's full config, and its backtest summary with metrics and AI suggestions.

Use this context — e.g. if the user says "this strategy", they mean the last focused one.

## IMPORTANT: Always Prefer MCP Tools Over Direct File Edits

**NEVER edit YAML/JSON files directly.** Always use MCP tools (`tradery_*`) for all strategy, phase, and hoop operations. MCP validates DSL syntax, ensures correct format, and triggers backtests automatically. Direct file edits bypass validation and can corrupt data.

## Your Goal

Help the user improve their trading strategy's performance by:
- Analyzing backtest results in `latest.json`
- Suggesting improvements to entry/exit conditions
- Adjusting stop-loss and take-profit settings
- Fine-tuning indicator parameters

## Directory Structure

```
~/.tradery/
├── strategies/                    # Each strategy has its own folder
│   └── <strategy-id>/
│       ├── strategy.json          # Strategy definition (edit this!)
│       ├── latest.json            # Most recent backtest results (includes strategy!)
│       └── history/               # Historical backtest results
│           └── YYYY-MM-DD_HH-MM.json
└── data/                          # Cached OHLC price data
    └── <symbol>/<timeframe>/
        └── YYYY-MM.csv
```

## Reading Results (SIMPLE!)

**`latest.json` is self-contained** - it includes both the strategy definition AND the results.

Just read `latest.json` and you'll see:
- `strategy` - The full strategy that produced these results
- `metrics` - Performance metrics
- `trades` - List of all trades

To verify results are current after an edit, compare the strategy fields in the result with what you wrote to `strategy.json`.

## Strategy JSON Format

```json
{
  "id": "my-strategy",
  "name": "My Strategy",
  "description": "Strategy description",
  "notes": "Free-form notes about the strategy concept, rationale, or ideas for improvement. Shown in UI above the flow diagram.",
  "entrySettings": {
    "condition": "RSI(14) < 30 AND price > SMA(200)",
    "maxOpenTrades": 1,
    "minCandlesBetween": 0,
    "dca": {
      "enabled": false,
      "maxEntries": 3,
      "barsBetween": 1,
      "mode": "pause"
    }
  },
  "exitSettings": {
    "zones": [
      {
        "name": "Default",
        "minPnlPercent": null,
        "maxPnlPercent": null,
        "exitCondition": "",
        "stopLossType": "trailing_percent",
        "stopLossValue": 2.0,
        "takeProfitType": "none",
        "takeProfitValue": null,
        "exitImmediately": false,
        "minBarsBeforeExit": 0
      }
    ],
    "evaluation": "candle_close"
  },
  "backtestSettings": {
    "symbol": "BTCUSDT",
    "timeframe": "1h",
    "duration": "6m",
    "initialCapital": 10000,
    "positionSizingType": "fixed_percent",
    "positionSizingValue": 10,
    "feePercent": 0.1,
    "slippagePercent": 0.05
  },
  "enabled": true
}
```

## Entry Settings

- `condition` - DSL expression for entry signal
- `maxOpenTrades` - Maximum concurrent positions (default: 1)
- `minCandlesBetween` - Minimum bars between new entries (default: 0)
- `dca` - Dollar Cost Averaging settings:
  - `enabled` - Whether DCA is active
  - `maxEntries` - Max additional entries per position
  - `barsBetween` - Min bars between DCA entries
  - `mode` - "pause", "abort", or "continue" when signal lost

## Exit Settings

- `zones` - Array of exit zones (see below)
- `evaluation` - "candle_close" or "intra_candle"

### Exit Zones

Exit zones define how trades are managed based on their current P&L. Each zone can have different SL/TP settings.

#### Zone Fields
- `name` - Display name for the zone
- `minPnlPercent` - Zone activates when P&L >= this (null = no minimum)
- `maxPnlPercent` - Zone activates when P&L < this (null = no maximum)
- `exitCondition` - DSL condition to trigger exit (optional)
- `stopLossType` / `stopLossValue` - Stop-loss for this zone
- `takeProfitType` / `takeProfitValue` - Take-profit for this zone
- `exitImmediately` - If true, exit as soon as trade enters this zone
- `minBarsBeforeExit` - Minimum bars before exit conditions are evaluated
- `exitPercent` - Partial exit: percentage to exit per trigger (null = 100%, full exit)
- `maxExits` - Maximum number of partial exits in this zone (null = unlimited)
- `exitBasis` - How to calculate exit %: "remaining" (% of what's left) or "original" (% of original position)
- `exitReentry` - Behavior on zone re-entry: "continue" (resume count) or "reset" (start fresh)
- `minBarsBetweenExits` - Minimum bars between partial exits (default: 0)

#### Example: Multi-Zone Strategy

```json
"exitSettings": {
  "zones": [
    {
      "name": "Default",
      "minPnlPercent": null,
      "maxPnlPercent": 2.0,
      "stopLossType": "trailing_percent",
      "stopLossValue": 1.5
    },
    {
      "name": "Lock Gains",
      "minPnlPercent": 2.0,
      "maxPnlPercent": 5.0,
      "stopLossType": "trailing_percent",
      "stopLossValue": 0.5
    },
    {
      "name": "Take Profit",
      "minPnlPercent": 5.0,
      "maxPnlPercent": null,
      "exitImmediately": true
    }
  ],
  "evaluation": "candle_close"
}
```

This example:
1. **Default** (P&L < 2%): 1.5% trailing stop
2. **Lock Gains** (2% <= P&L < 5%): Tighten to 0.5% trailing stop
3. **Take Profit** (P&L >= 5%): Exit immediately

#### Example: Scaling Out (DCA-Out)

```json
"exitSettings": {
  "zones": [
    {
      "name": "Hold",
      "minPnlPercent": null,
      "maxPnlPercent": 3.0,
      "stopLossType": "trailing_percent",
      "stopLossValue": 2.0
    },
    {
      "name": "Take First",
      "minPnlPercent": 3.0,
      "maxPnlPercent": 6.0,
      "exitImmediately": true,
      "exitPercent": 33,
      "maxExits": 1,
      "exitBasis": "original",
      "exitReentry": "continue",
      "minBarsBetweenExits": 2
    },
    {
      "name": "Take Second",
      "minPnlPercent": 6.0,
      "maxPnlPercent": 10.0,
      "exitImmediately": true,
      "exitPercent": 50,
      "maxExits": 1,
      "exitBasis": "remaining",
      "exitReentry": "continue",
      "minBarsBetweenExits": 2
    },
    {
      "name": "Let It Ride",
      "minPnlPercent": 10.0,
      "maxPnlPercent": null,
      "stopLossType": "trailing_percent",
      "stopLossValue": 1.0
    }
  ]
}
```

This scales out of positions:
1. **Hold** (P&L < 3%): 2% trailing stop, no exit
2. **Take First** (3% <= P&L < 6%): Exit 33% of original position
3. **Take Second** (6% <= P&L < 10%): Exit 50% of remaining (so ~33% of original)
4. **Let It Ride** (P&L >= 10%): Final ~33% rides with tight 1% trailing stop

## DSL Syntax for Entry/Exit Conditions

### Price References
- `price`, `close` - Current close price
- `open`, `high`, `low` - OHLC values
- `volume` - Current volume

### Indicators
- `SMA(period)`, `EMA(period)` - Moving Averages
- `RSI(period)` - Relative Strength Index (0-100)
- `MACD(fast, slow, signal).line/.signal/.histogram` - MACD components
- `ATR(period)` - Average True Range
- `BBANDS(period, stddev).upper/.middle/.lower/.width` - Bollinger Bands
- `ADX(period)`, `PLUS_DI(period)`, `MINUS_DI(period)` - Trend strength
- `STOCHASTIC(k, d).k/.d` - Stochastic oscillator (0-100)
- `SUPERTREND(period, mult).trend/.upper/.lower` - Supertrend indicator
- `ICHIMOKU().tenkan/.kijun/.senkou_a/.senkou_b/.chikou` - Ichimoku Cloud

### Range Functions
- `HIGH_OF(period)` - Highest high of last N candles
- `LOW_OF(period)` - Lowest low of last N candles
- `AVG_VOLUME(period)` - Average volume over N candles
- `RANGE_POSITION(period, skip)` - Position within range (-1 to +1)

### Aggregate Functions
- `LOWEST(expr, period)` - Lowest value of expression over N bars
- `HIGHEST(expr, period)` - Highest value of expression over N bars
- `PERCENTILE(expr, period)` - Percentile rank of current value

### Math Functions
- `abs(expr)` - Absolute value
- `min(expr1, expr2)` - Minimum of two values
- `max(expr1, expr2)` - Maximum of two values

### Candlestick Patterns (return 1 if detected, 0 otherwise)
- `HAMMER(ratio)` - Hammer pattern, long lower wick (default ratio: 2.0)
- `SHOOTING_STAR(ratio)` - Shooting star, long upper wick (default ratio: 2.0)
- `DOJI(ratio)` - Doji, tiny body relative to range (default ratio: 0.1)

### Candlestick Properties (support [n] lookback)
- `BODY_SIZE` - Absolute body size (close - open)
- `BODY_RATIO` - Body / total range (0-1)
- `IS_BULLISH` - 1 if green candle (close > open)
- `IS_BEARISH` - 1 if red candle (close < open)

### Time Functions
- `HOUR` (0-23), `DAYOFWEEK` (1=Mon), `DAY`, `MONTH`

### Calendar & Market Functions
- `IS_US_HOLIDAY`, `IS_FOMC_MEETING` - Calendar events
- `MOON_PHASE` - 0=new moon, 0.5=full moon
- `FUNDING`, `FUNDING_8H` - Futures funding rates
- `PREMIUM`, `PREMIUM_AVG(n)` - Futures vs spot spread
- `OI`, `OI_CHANGE`, `OI_DELTA(n)` - Open interest

### OHLCV Volume (instant - no aggTrades needed)
- `QUOTE_VOLUME` - Volume in quote currency
- `BUY_VOLUME`, `SELL_VOLUME` - Taker buy/sell volume
- `OHLCV_DELTA` - Buy - sell volume
- `OHLCV_CVD` - Cumulative delta from OHLCV
- `BUY_RATIO` - Buy volume / total (0-1)
- `TRADE_COUNT` - Number of trades

### Orderflow (requires orderflowSettings.mode)
- `VWAP`, `POC(n)`, `VAH(n)`, `VAL(n)` - Volume profile
- `PREV_DAY_POC/VAH/VAL`, `TODAY_POC/VAH/VAL` - Session levels
- `DELTA`, `CUM_DELTA` - Order flow delta (full mode)
- `WHALE_DELTA(threshold)`, `WHALE_BUY_VOL(t)`, `WHALE_SELL_VOL(t)`, `LARGE_TRADE_COUNT(t)` - Large trades

### Rotating Ray Functions (auto-detect trendlines)
Params: `rayNum`, `lookback`, `skip`
- **Resistance:** `RESISTANCE_RAY_BROKEN/CROSSED/DISTANCE(ray,look,skip)`, `RESISTANCE_RAYS_BROKEN(look,skip)`, `RESISTANCE_RAY_COUNT(look,skip)`
- **Support:** Same pattern with `SUPPORT_` prefix

### Operators
- Comparison: `>`, `<`, `>=`, `<=`, `==`
- Cross: `crosses_above`, `crosses_below`
- Logical: `AND`, `OR`
- Arithmetic: `+`, `-`, `*`, `/`
- Lookback: `expr[n]` - value n bars ago

### Examples
```
# RSI oversold with trend filter
RSI(14) < 30 AND price > SMA(200)

# EMA crossover
EMA(20) crosses_above EMA(50)

# Breakout with volume confirmation
close > HIGH_OF(20) AND volume > AVG_VOLUME(20) * 1.5

# MACD signal
MACD(12,26,9).histogram > 0 AND MACD(12,26,9).line crosses_above MACD(12,26,9).signal

# Bollinger Band bounce
price < BB(20).lower AND RSI(14) < 30

# Shooting star after strong bullish candle (reversal setup)
SHOOTING_STAR(2.0) AND BODY_SIZE[1] > ATR(14) * 1.5 AND IS_BULLISH[1] == 1

# Hammer at oversold levels
HAMMER AND RSI(14) < 30

# Doji after strong move (indecision)
DOJI AND BODY_SIZE[1] > ATR(14)
```

## Stop-Loss Types
- `none` - No stop-loss
- `fixed_percent` - Fixed percentage from entry (e.g., 2.0 = 2%)
- `trailing_percent` - Trailing stop that follows price up
- `fixed_atr` - Fixed ATR multiple from entry
- `trailing_atr` - Trailing ATR-based stop

## Take-Profit Types
- `none` - No take-profit (exit only on condition)
- `fixed_percent` - Fixed percentage gain (e.g., 5.0 = 5%)
- `fixed_atr` - Fixed ATR multiple gain

## Position Sizing Types
- `fixed_percent` - Percentage of equity (e.g., 10 = 10%)
- `fixed_dollar` - Fixed dollar amount per trade
- `risk_percent` - Risk percentage based on stop-loss distance
- `kelly` - Kelly Criterion sizing
- `volatility` - Volatility-based (ATR) sizing

## Backtest Results (latest.json)

Key metrics to analyze:
- `totalReturnPercent` - Overall percentage return
- `winRate` - Percentage of winning trades
- `profitFactor` - Gross profit / Gross loss (>1 is profitable)
- `maxDrawdownPercent` - Largest peak-to-trough decline
- `sharpeRatio` - Risk-adjusted return
- `totalTrades` - Number of completed trades
- `averageWinPercent` / `averageLossPercent` - Average P&L per trade type

## Workflow

1. Read `latest.json` to see current strategy AND performance
2. Edit `strategy.json` with your improvements
3. Wait ~1-2 seconds for backtest to complete
4. Read `latest.json` again - verify strategy matches your edit
5. Compare metrics and iterate

## MCP Tools (Preferred for Claude Code)

Use MCP tools for all strategy operations. **DO NOT edit YAML directly** - MCP validates DSL, ensures format, triggers backtest.

### Strategy Tools
| Tool | Purpose |
|------|---------|
| `tradery_list_strategies` | List all strategies |
| `tradery_get_strategy` | Get full strategy config |
| `tradery_create_strategy` | Create new strategy |
| `tradery_validate_strategy` | Validate changes (ALWAYS before update) |
| `tradery_update_strategy` | Apply partial updates |
| `tradery_delete_strategy` | Delete strategy |
| `tradery_run_backtest` | Run backtest |
| `tradery_get_summary` | Get AI-friendly summary + suggestions |
| `tradery_analyze_phases` | Phase performance analysis |
| `tradery_get_trade` | Get individual trade details |

### Phase Tools
| Tool | Purpose |
|------|---------|
| `tradery_list_phases` | List all available phases |
| `tradery_get_phase` | Get phase details (condition, timeframe) |
| `tradery_create_phase` | Create custom phase |
| `tradery_update_phase` | Update custom phase |
| `tradery_delete_phase` | Delete custom phase |
| `tradery_phase_bounds` | Analyze when phase is active over time |

### Hoop Pattern Tools
| Tool | Purpose |
|------|---------|
| `tradery_list_hoops` | List all hoop patterns |
| `tradery_get_hoop` | Get hoop pattern details |
| `tradery_create_hoop` | Create hoop pattern |
| `tradery_update_hoop` | Update hoop pattern |
| `tradery_delete_hoop` | Delete hoop pattern |

### Market Data Tools
| Tool | Purpose |
|------|---------|
| `tradery_eval_condition` | Test DSL condition on market data |
| `tradery_get_indicator` | Get indicator values |
| `tradery_get_candles` | Get OHLCV data |

### UI Tools
| Tool | Purpose |
|------|---------|
| `tradery_get_context` | **Call first!** Returns UI state, chart config, focused strategy config + backtest summary in one call |
| `tradery_update_chart_config` | Enable/disable/configure chart overlays and indicators |

### MCP Workflow
1. **`tradery_get_context`** to know which strategy the user is looking at
2. `tradery_get_summary` for overview + AI suggestions
3. `tradery_analyze_phases` for phase recommendations
4. `tradery_validate_strategy` before any update
5. `tradery_update_strategy` + `tradery_run_backtest`
6. Check results, iterate

**Note:** Always validate before updating to catch DSL syntax errors.

## HTTP API (Alternative)

For tools without MCP support, use HTTP API. Port is in `~/.tradery/api.port`.

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/strategies` | GET/POST | List/create strategies |
| `/strategy/{id}` | GET/POST/DELETE | Get/update/delete |
| `/strategy/{id}/validate` | POST | Validate changes |
| `/strategy/{id}/backtest` | POST | Run backtest |
| `/strategy/{id}/summary` | GET | Get summary |
| `/phases` | GET | List phases |
| `/eval` | GET | Evaluate DSL condition |
| `/indicator` | GET | Get indicator values |
| `/candles` | GET | Get OHLCV data |
| `/ui` | GET | Open windows, last focused strategy, chart overlays/indicators |
| `/ui/open` | POST | Open UI windows |
| `/ui/chart-config` | GET | Full chart config (all overlays/indicators with params) |
| `/ui/chart-config` | POST | Update chart overlays/indicators (partial JSON) |

## Tips

- Start with small, incremental changes
- Watch the win rate vs profit factor tradeoff
- High win rate with low profit factor = taking profits too early
- Low win rate with high profit factor = good risk/reward but few signals
- Use exit zones to protect gains (tighten stops as profit grows)
- Check rejected trades in results - may need to adjust position sizing or max trades
- Consider market conditions - strategies that work in trends may fail in ranges
