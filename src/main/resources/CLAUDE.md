# Tradery Strategy Optimizer

You are helping optimize trading strategies for a backtesting application. The app watches for file changes and automatically re-runs backtests when you modify strategy files.

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
- `SMA(period)` - Simple Moving Average
- `EMA(period)` - Exponential Moving Average
- `RSI(period)` - Relative Strength Index (0-100)
- `MACD(fast, slow, signal)` - Returns object with `.line`, `.signal`, `.histogram`
- `ATR(period)` - Average True Range
- `BB(period)` - Bollinger Bands, returns `.upper`, `.middle`, `.lower`
- `HIGH_OF(period)` - Highest high of last N candles
- `LOW_OF(period)` - Lowest low of last N candles
- `AVG_VOLUME(period)` - Average volume over N candles

### Operators
- Comparison: `>`, `<`, `>=`, `<=`, `==`, `!=`
- Logical: `AND`, `OR`, `NOT`
- Arithmetic: `+`, `-`, `*`, `/`
- Special: `crosses_above`, `crosses_below`

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

## Tips

- Start with small, incremental changes
- Watch the win rate vs profit factor tradeoff
- High win rate with low profit factor = taking profits too early
- Low win rate with high profit factor = good risk/reward but few signals
- Use exit zones to protect gains (tighten stops as profit grows)
- Check rejected trades in results - may need to adjust position sizing or max trades
- Consider market conditions - strategies that work in trends may fail in ranges
