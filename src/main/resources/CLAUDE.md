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

To verify results are current after an edit, compare `strategy.entry` (or other fields) in the result with what you wrote to `strategy.json`.

## Strategy JSON Format

```json
{
  "id": "my-strategy",
  "name": "My Strategy",
  "description": "Strategy description",
  "entry": "RSI(14) < 30 AND price > SMA(200)",
  "exitZones": [
    {
      "name": "Default",
      "exitCondition": "RSI(14) > 70",
      "stopLossType": "trailing_percent",
      "stopLossValue": 2.0,
      "takeProfitType": "fixed_percent",
      "takeProfitValue": 5.0
    }
  ],
  "maxOpenTrades": 1,
  "dcaEnabled": false,
  "backtestSettings": {
    "symbol": "BTCUSDT",
    "timeframe": "1h",
    "duration": "6m",
    "initialCapital": 10000,
    "feePercent": 0.1,
    "slippagePercent": 0.05
  }
}
```

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
4. Read `latest.json` again - verify `strategy.entry` matches your edit
5. Compare metrics and iterate

## Tips

- Start with small, incremental changes
- Watch the win rate vs profit factor tradeoff
- High win rate with low profit factor = taking profits too early
- Low win rate with high profit factor = good risk/reward but few signals
- Check rejected trades in results - may need to adjust position sizing or max trades
- Consider market conditions - strategies that work in trends may fail in ranges