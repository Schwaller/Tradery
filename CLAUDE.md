# Tradery - Java Desktop Trading Strategy Backtester

## Project Overview
Java Swing desktop app for backtesting trading strategies. File-based storage enables Claude Code integration.

**Tech Stack:** Java 21, Swing UI, JFreeChart, Jackson (YAML/JSON), OkHttp (Binance API), Gradle

## File Structure
```
~/.tradery/                           # User data directory
├── api.port                          # API port (written on startup)
├── strategies/{id}/                  # Strategy folders
│   ├── strategy.yaml                 # Strategy definition
│   ├── summary.json                  # Metrics + AI-friendly analysis
│   ├── trades/                       # Individual trade files (0001_WIN_+2p5pct_uptrend.json)
│   └── history/                      # Historical backtest runs
├── phases/{id}/phase.yaml            # Phase definitions
├── hoops/{id}/hoop.yaml              # Hoop pattern definitions
├── data/{symbol}/{timeframe}/        # Cached OHLC data
├── aggtrades/{symbol}/               # Aggregated trades (orderflow)
├── funding/{symbol}.csv              # Funding rate cache
└── openinterest/{symbol}.csv         # Open interest (5m resolution)

src/main/java/com/tradery/
├── TraderyApp.java                   # Entry point
├── ui/                               # Swing UI (MainFrame, StrategyPanel, ChartsPanel, etc.)
├── dsl/                              # DSL parser (Lexer, Parser, AstNode)
├── indicators/IndicatorEngine.java   # All indicator calculations
├── engine/                           # Backtest + condition evaluation
├── data/                             # Binance clients + caching
├── model/                            # Strategy, Trade, Candle, Phase, etc.
└── io/                               # YAML/JSON stores
```

## Development Commands
```bash
./gradlew compileJava    # Compile
./gradlew run            # Run app
./gradlew build          # Build JAR
```

---

## DSL Reference

DSL is used for entry/exit conditions and phase definitions.

### Core Functions
| Category | Functions |
|----------|-----------|
| **Price** | `price`/`close`, `open`, `high`, `low`, `volume` |
| **Moving Avg** | `SMA(n)`, `EMA(n)` |
| **Momentum** | `RSI(n)` (0-100), `STOCHASTIC(k,d).k/.d` (0-100) |
| **Volatility** | `ATR(n)`, `BBANDS(n,std).upper/.middle/.lower/.width` |
| **Trend** | `ADX(n)`, `PLUS_DI(n)`, `MINUS_DI(n)`, `SUPERTREND(n,mult).trend/.upper/.lower` |
| **MACD** | `MACD(fast,slow,sig).line/.signal/.histogram` |
| **Ichimoku** | `ICHIMOKU().tenkan/.kijun/.senkou_a/.senkou_b/.chikou` |
| **Range** | `HIGH_OF(n)`, `LOW_OF(n)`, `AVG_VOLUME(n)`, `RANGE_POSITION(n,skip)` |
| **Aggregate** | `LOWEST(expr,n)`, `HIGHEST(expr,n)`, `PERCENTILE(expr,n)` |
| **Time** | `HOUR` (0-23), `DAYOFWEEK` (1=Mon), `DAY`, `MONTH` |
| **Calendar** | `IS_US_HOLIDAY`, `IS_FOMC_MEETING`, `MOON_PHASE` (0=new, 0.5=full) |
| **Funding** | `FUNDING`, `FUNDING_8H` |
| **Premium** | `PREMIUM`, `PREMIUM_AVG(n)` - futures vs spot spread |
| **Open Interest** | `OI`, `OI_CHANGE`, `OI_DELTA(n)` |

### Orderflow Functions (require `orderflowSettings.mode`)
| Tier 1 (OHLCV) | Full (aggTrades) |
|----------------|------------------|
| `VWAP`, `POC(n)`, `VAH(n)`, `VAL(n)` | `DELTA`, `CUM_DELTA` |
| `PREV_DAY_POC/VAH/VAL`, `TODAY_POC/VAH/VAL` | `WHALE_DELTA(threshold)`, `WHALE_BUY_VOL(t)`, `WHALE_SELL_VOL(t)`, `LARGE_TRADE_COUNT(t)` |

### Rotating Ray Functions
Auto-detect trendlines from ATH/ATL. Params: `rayNum`, `lookback`, `skip`
- **Resistance:** `RESISTANCE_RAY_BROKEN/CROSSED/DISTANCE(ray,look,skip)`, `RESISTANCE_RAYS_BROKEN(look,skip)`, `RESISTANCE_RAY_COUNT(look,skip)`
- **Support:** Same pattern with `SUPPORT_` prefix

### Operators
| Type | Operators |
|------|-----------|
| Comparison | `>`, `<`, `>=`, `<=`, `==` |
| Cross | `crosses_above`, `crosses_below` |
| Logical | `AND`, `OR` |
| Arithmetic | `+`, `-`, `*`, `/` |
| Lookback | `expr[n]` - value n bars ago |

### DSL Examples
```
RSI(14) < 30 AND price > SMA(200)                    # Basic entry
MACD(12,26,9).line crosses_above MACD(12,26,9).signal
ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)         # Trend filter
BBANDS(20,2).width < LOWEST(BBANDS(20,2).width, 20) * 1.15  # Squeeze
ATR(14) > ATR(14)[1]                                 # Volatility expanding
SUPERTREND(10,3).trend == 1                          # Uptrend
FUNDING > 0.05 AND DELTA < 0                         # Overleveraged + selling
close crosses_above PREV_DAY_POC                     # Reclaim POC
RESISTANCE_RAY_CROSSED(1, 200, 5) == 1               # Breakout
```

---

## Phase System

Phases filter when strategies can trade (multi-timeframe, sessions, calendar, regimes).

```yaml
# Phase definition (~/.tradery/phases/{id}/phase.yaml)
id: uptrend
condition: ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)
timeframe: 1d
```

### Built-In Phases (38)
| Category | IDs |
|----------|-----|
| **Session** | `asian-session`, `european-session`, `us-market-hours`, `us-market-core`, `session-overlap` |
| **Time** | `monday`-`sunday`, `weekdays`, `weekend` |
| **Trend** | `uptrend`, `downtrend`, `ranging` |
| **Crossover** | `golden-cross`, `death-cross` |
| **RSI** | `overbought`, `oversold` |
| **Ray** | `resistance-breakout`, `support-breakdown`, `multi-ray-breakout`, `near-daily-resistance`, `ray-uptrend`, `ray-downtrend` |
| **Calendar** | `month-start`, `month-end`, `quarter-end`, `us-bank-holiday`, `fomc-meeting-day/hour/week` |
| **Moon** | `full-moon-day/hour`, `new-moon-day/hour` |
| **Funding** | `high-funding`, `negative-funding`, `extreme-funding`, `neutral-funding` |

### Strategy Integration
```yaml
phaseSettings:
  requiredPhaseIds: [uptrend, weekdays]    # ALL must be active
  excludedPhaseIds: [fomc-meeting-day]     # NONE must be active
```

---

## Strategy Structure

```yaml
id: my-strategy
name: My Strategy
enabled: true

entrySettings:
  condition: RSI(14) < 30 AND close > SMA(200)
  maxOpenTrades: 3
  minCandlesBetween: 5

exitSettings:
  zones:
    - name: Stop Loss
      maxPnlPercent: -5.0
      exitImmediately: true
    - name: Take Profit
      minPnlPercent: 10.0
      exitImmediately: true
    - name: Conditional Exit
      minPnlPercent: 0.0
      maxPnlPercent: 5.0
      condition: RSI(14) > 70

backtestSettings:
  symbol: BTCUSDT
  timeframe: 1h
  duration: 1y
  initialCapital: 10000
  positionSizingType: PERCENT_EQUITY
  positionSizingValue: 10

orderflowSettings:
  mode: tier1  # disabled, tier1, or full
```

---

## Hoop Patterns

Sequential price checkpoints for chart pattern detection.

```yaml
id: double-bottom
hoops:
  - name: first-low
    minPricePercent: -3.0
    maxPricePercent: -1.0
    distance: 5
    tolerance: 2
  # ... more hoops
priceSmoothingType: NONE  # or SMA, EMA, HLC3
```

**Combine modes:** `DSL_ONLY`, `HOOP_ONLY`, `AND`, `OR`

---

## Trade Analytics

Each trade captures: `entryPrice`, `exitPrice`, `pnlPercent`, `mfe` (max favorable), `mae` (max adverse), `activePhasesAtEntry`, indicator values at entry/exit/mfe/mae.

**Derived metrics:** `captureRatio` = pnl/mfe, `painRatio` = |mae|/mfe

### summary.json Structure
```json
{
  "metrics": { "winRate": 62.5, "profitFactor": 2.1, "sharpeRatio": 1.85, "maxDrawdownPercent": 8.2 },
  "analysis": {
    "byPhase": { "uptrend": { "winRate": 75, "vsOverall": +12.5 } },
    "byHour": { "14": { "winRate": 80 } },
    "suggestions": ["Consider requiring 'uptrend' phase", "Exits may be too early"]
  },
  "historyTrends": { "vsLastRun": {...}, "trajectory": {...} }
}
```

**Trade files:** `trades/0001_WIN_+2p5pct_uptrend_weekdays.json` - glob to filter by outcome/phase.

---

## AI Guidance

**Already implemented:**
- Time/session filtering via phases
- Calendar awareness (`IS_US_HOLIDAY`, `IS_FOMC_MEETING`)
- Multi-timeframe (phases on different TF)
- Trend detection (ADX, Supertrend, phases)
- Moon phases, Funding, Premium, Open Interest
- Orderflow (VWAP, POC, DELTA, whale detection)
- Aggregate functions, lookback syntax

**Not implemented:**
- Liquidation data (needs external API)
- Cross-symbol correlation
- Separate long/short conditions

---

## Maintenance

### DSL Extension Checklist
1. `dsl/Lexer.java` - Add keywords
2. `dsl/Parser.java` - Update grammar
3. `dsl/AstNode.java` - Add node type
4. `engine/ConditionEvaluator.java` - Evaluation logic
5. `indicators/IndicatorEngine.java` - Calculation
6. `ui/DslHelpDialog.java` - Help content
7. **CLAUDE.md** - Document

---

## Claude Code Integration

### IMPORTANT: Use MCP/API for Strategy Changes
**DO NOT edit YAML directly.** API validates DSL, ensures format, triggers backtest.

### MCP Tools (Preferred)
| Tool | Purpose |
|------|---------|
| `tradery_list_strategies` | List all strategies |
| `tradery_get_strategy` | Get full strategy config |
| `tradery_validate_strategy` | Validate changes (ALWAYS before update) |
| `tradery_update_strategy` | Apply partial updates |
| `tradery_create_strategy` | Create new strategy |
| `tradery_run_backtest` | Run backtest |
| `tradery_get_summary` | Get AI-friendly summary + suggestions |
| `tradery_analyze_phases` | Phase performance analysis |
| `tradery_get_trade` | Get individual trade details |
| `tradery_list_phases` | List available phases |
| `tradery_eval_condition` | Test DSL condition on market data |
| `tradery_get_indicator` | Get indicator values |
| `tradery_get_candles` | Get OHLCV data |
| `tradery_open_window` | Open UI windows (phases, hoops, settings, project, etc.) |

### HTTP API
Port in `~/.tradery/api.port`. Key endpoints:
| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/strategies` | GET/POST | List/create strategies |
| `/strategy/{id}` | GET/POST/DELETE | Get/update/delete |
| `/strategy/{id}/validate` | POST | Validate changes |
| `/strategy/{id}/backtest` | POST | Run backtest |
| `/strategy/{id}/results` | GET | Get results |
| `/phases` | GET | List phases |
| `/phase/{id}/bounds` | GET | When phase is active |
| `/eval` | GET | Evaluate DSL condition |
| `/indicator` | GET | Get indicator values |
| `/candles` | GET | Get OHLCV data |
| `/ui/open` | POST | Open UI windows (phases, hoops, settings, data, project) |

### AI Workflow
1. Read `summary.json` for overview + suggestions
2. Use `tradery_analyze_phases` for phase recommendations
3. Glob trade filenames to understand distribution
4. `tradery_validate_strategy` before any update
5. `tradery_update_strategy` + `tradery_run_backtest`
6. Check results, iterate

### Auto-Reload
FileWatcher monitors directories. External edits trigger automatic reload and backtest.
