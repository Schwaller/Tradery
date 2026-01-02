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
├── results/
│   └── latest.json                   # Most recent backtest result
└── data/
    └── BTCUSDT/
        └── 1h/
            └── 2024-01.csv           # Cached OHLC data

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
│   │   └── IndicatorEngine.java      # SMA, EMA, RSI, MACD, Bollinger Bands
│   ├── engine/
│   │   ├── BacktestEngine.java       # Main simulation loop
│   │   └── ConditionEvaluator.java   # Evaluate DSL conditions
│   ├── data/
│   │   ├── BinanceClient.java        # Fetch OHLC from Binance API
│   │   └── CandleStore.java          # Read/write CSV, smart caching
│   ├── model/
│   │   ├── Strategy.java             # Strategy with entry/exit DSL, SL/TP settings
│   │   ├── Trade.java                # Trade record (open, close, rejected)
│   │   ├── BacktestConfig.java       # Backtest configuration
│   │   ├── BacktestResult.java       # Results with trades and metrics
│   │   ├── PerformanceMetrics.java   # Calculated performance stats
│   │   └── Candle.java               # OHLC record
│   └── io/
│       ├── StrategyStore.java        # Read/write strategy JSON
│       ├── ResultStore.java          # Save/load backtest results
│       └── FileWatcher.java          # Watch for external file changes
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

## Key Features

### DSL Syntax for Entry/Exit Conditions
```
RSI(14) < 30
price > SMA(200)
EMA(20) crosses_above EMA(50)
MACD(12,26,9).histogram > 0
close > HIGH_OF(20) AND volume > AVG_VOLUME(20) * 1.5
```

### Strategy Settings
- **Entry/Exit conditions**: DSL expressions
- **Stop Loss**: None, Fixed %, Trailing %, Fixed ATR, Trailing ATR
- **Take Profit**: None, Fixed %, Fixed ATR
- **Max Open Trades**: Limit concurrent positions (default: 1)
- **Min Candles Between**: Minimum bars between new entries (default: 0)

### Position Sizing Options
- Fixed percent of equity (1%, 5%, 10%)
- Fixed dollar amount ($100, $500, $1000)
- Risk-based (1%, 2% risk per trade)
- Kelly Criterion
- Volatility-based (ATR)

### Charts (5 panels, synced crosshair)
1. **Price**: Candlesticks with trade entry/exit lines (green=win, red=loss)
2. **Equity**: Portfolio value over time
3. **Strategy vs Buy & Hold**: Comparison chart with legend
4. **Capital Usage**: % of capital invested
5. **Trade P&L %**: Each trade's P&L progression from entry to exit

### Performance Metrics
- Total trades, win rate, profit factor
- Total return, max drawdown
- Sharpe ratio
- Average win/loss
- Total fees paid
- Final equity

### Fee Configuration
- **Fee**: Default 0.10% (Binance spot rate)
- **Slippage**: Default 0.05%
- Both configurable in the settings panel

### Trade Table
- Shows all executed trades with entry/exit times and prices
- Rejected trades (no capital available) shown grayed out with "NO CAPITAL"
- P&L colored green/red for wins/losses

## Architecture Notes

### File-Based Storage
All data stored as plain files for Claude Code integration:
- Strategies: JSON in `~/.tradery/strategies/`
- Results: JSON in `~/.tradery/results/`
- OHLC data: CSV in `~/.tradery/data/{symbol}/{timeframe}/`

### Auto-Reload
FileWatcher monitors `~/.tradery/strategies/` for changes. When a strategy file is modified externally (e.g., by Claude Code), the app automatically reloads and re-runs the backtest.

### Smart Data Caching
CandleStore only fetches missing months from Binance API. Historical months that are complete are never re-downloaded.

### Multiple Open Trades
BacktestEngine supports multiple concurrent positions with:
- Per-trade trailing stop tracking
- Max open trades limit
- Minimum candle distance between entries

## Claude Code Integration

With files on disk, Claude Code can:
- View/edit strategies: `~/.tradery/strategies/*.json`
- See results: `~/.tradery/results/latest.json`
- Query OHLC data: `~/.tradery/data/{symbol}/{timeframe}/*.csv`

## Recent Changes (Latest Session)
- Added 5th chart for Trade P&L % tracking
- Added rejected trades display (no capital)
- Added max open trades and min candles between entries settings
- Added fee and slippage configuration
- Added total fees to performance metrics
- Fixed stop-loss/take-profit to use current UI values
- Improved trade table formatting and alignment
