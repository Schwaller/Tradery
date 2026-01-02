# Tradery

A Java desktop application for building and backtesting trading strategies on cryptocurrency markets.

## Features

- **Visual Strategy Builder**: Define entry/exit conditions using a simple DSL
- **Multiple Chart Views**: Price, equity, strategy comparison, capital usage, and trade P&L
- **Risk Management**: Stop-loss, take-profit, position sizing, max open trades
- **Performance Analytics**: Win rate, profit factor, Sharpe ratio, drawdown, fees
- **Binance Integration**: Automatic OHLC data fetching with smart caching
- **File-Based Storage**: All data in plain files for easy editing and version control

## Requirements

- Java 21 or later
- macOS (optimized for native look-and-feel)

## Quick Start

```bash
# Clone and run
git clone <repo>
cd Tradery
./gradlew run
```

## Usage

1. **Create a Strategy**: Click "New" and define entry/exit conditions
2. **Configure Settings**: Set capital, position sizing, fees, stop-loss/take-profit
3. **Run Backtest**: Select symbol/timeframe and click "Run Backtest"
4. **Analyze Results**: Review charts, metrics, and trade list

## DSL Examples

```
# Simple RSI reversal
Entry: RSI(14) < 30
Exit:  RSI(14) > 70

# Moving average crossover with volume filter
Entry: EMA(20) crosses_above EMA(50) AND volume > AVG_VOLUME(20) * 1.5
Exit:  EMA(20) crosses_below EMA(50)

# Breakout strategy
Entry: close > HIGH_OF(20)
Exit:  close < LOW_OF(10) OR RSI(14) > 80
```

## Data Storage

All data is stored in `~/.tradery/`:
- `strategies/` - Strategy definitions (JSON)
- `results/` - Backtest results (JSON)
- `data/` - Cached OHLC data (CSV)

## License

MIT
