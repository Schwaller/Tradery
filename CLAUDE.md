# Plaiiin - Java Desktop Trading Strategy Backtester

## Engineering Standards — NON-NEGOTIABLE

**This is a financial application potentially managing millions. Zero tolerance for hacks, workarounds, or shortcuts.**

- **No timeout-based fallbacks.** If a service call is slow, fix the root cause. Never silently degrade to an approximation.
- **No silent data downgrades.** If the system promises precomputed tick-level profiles, it must deliver them — not quietly substitute candle-based estimates.
- **One computation path per metric.** POC, VAH, VAL, delta — each must have a single authoritative source. Multiple paths producing different numbers is a bug, not a feature.
- **Correctness over speed.** If a computation takes 5 minutes, it takes 5 minutes. The user waits for real data, not a fast lie.
- **No "fail fast and approximate."** Either the operation succeeds with correct data or it fails visibly with an error the user can act on.

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
./gradlew compileJava                # Compile all modules
./gradlew :tradery-forge:run         # Run main UI app
./gradlew :tradery-data-service:run  # Run data service
./gradlew :tradery-desk:run          # Run trading desk
./gradlew :tradery-news:run          # Run intel/news app (entity manager, coin graph)
./gradlew :tradery-runner:run        # Run strategy runner
./gradlew build                      # Build JAR

# Scripts (in /scripts directory)
scripts/start-forge.sh               # Start main UI
scripts/start-data-service.sh        # Start data service
scripts/start-desk.sh                # Start trading desk
scripts/start-intel.sh               # Start intel/news app
scripts/kill-forge.sh                # Kill main UI
scripts/kill-data-service.sh         # Kill data service
scripts/kill-desk.sh                 # Kill trading desk
scripts/kill-intel.sh                # Kill intel/news app
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
| **Math** | `abs(expr)`, `min(expr,expr)`, `max(expr,expr)` |
| **Candle Patterns** | `HAMMER(ratio)`, `SHOOTING_STAR(ratio)`, `DOJI(ratio)` - return 1 if detected |
| **Candle Props** | `BODY_SIZE`, `BODY_RATIO`, `IS_BULLISH`, `IS_BEARISH` - support `[n]` lookback |
| **Time** | `HOUR` (0-23), `DAYOFWEEK` (1=Mon), `DAY`, `MONTH` |
| **Calendar** | `IS_US_HOLIDAY`, `IS_FOMC_MEETING`, `MOON_PHASE` (0=new, 0.5=full) |
| **Funding** | `FUNDING`, `FUNDING_8H` |
| **Premium** | `PREMIUM`, `PREMIUM_AVG(n)` - futures vs spot spread |
| **Sentiment** | `FEAR_GREED` (0-100), `FEAR_GREED_AVG(n)` - Crypto Fear & Greed Index |
| **Relative Volume** | `RVOL(weeks)`, `RVOL(weeks,mode)`, `RVOL(weeks,mode,smooth)` - ratio vs historical (1.0=normal). `RVOL_PERCENTILE(...)` - 0-100. Mode: 0=ANY, 1=DOW, 2=DAYTYPE |
| **Open Interest** | `OI`, `OI_CHANGE`, `OI_DELTA(n)` |
| **OHLCV Volume** | `QUOTE_VOLUME`, `BUY_VOLUME`, `SELL_VOLUME`, `OHLCV_DELTA`, `OHLCV_CVD`, `BUY_RATIO`, `TRADE_COUNT` |

### OHLCV Volume Functions (instant - no aggTrades needed)
Extended volume data from Binance klines, available immediately:
- `QUOTE_VOLUME` - Volume in quote currency (USD for BTCUSDT)
- `BUY_VOLUME` / `SELL_VOLUME` - Taker buy/sell volume (aggressive traders)
- `OHLCV_DELTA` - Buy volume - sell volume (basic delta)
- `OHLCV_CVD` - Cumulative delta from OHLCV
- `BUY_RATIO` - Buy volume / total volume (0-1, where 0.5 = balanced)
- `TRADE_COUNT` - Number of trades in the bar

### Orderflow Functions (require `orderflowSettings.mode`)
| Tier 1 (OHLCV) | Full (aggTrades) |
|----------------|------------------|
| `VWAP`, `POC(n)`, `VAH(n)`, `VAL(n)` | `DELTA`, `CUM_DELTA` |
| `PREV_DAY_POC/VAH/VAL`, `TODAY_POC/VAH/VAL` | `WHALE_DELTA(threshold)`, `WHALE_BUY_VOL(t)`, `WHALE_SELL_VOL(t)`, `LARGE_TRADE_COUNT(t)` |

### Footprint Functions (require aggTrades)
Analyze trade flow at price levels within candles:
| Function | Returns | Description |
|----------|---------|-------------|
| `IMBALANCE_AT_POC` | ratio | Buy/sell ratio at POC (>1=buy dominant) |
| `IMBALANCE_AT_VAH` | ratio | Buy/sell ratio at VAH |
| `IMBALANCE_AT_VAL` | ratio | Buy/sell ratio at VAL |
| `STACKED_BUY_IMBALANCES(n)` | 0/1 | 1 if >= n consecutive buy imbalances |
| `STACKED_SELL_IMBALANCES(n)` | 0/1 | 1 if >= n consecutive sell imbalances |
| `ABSORPTION(vol, move)` | 0/1 | 1 if high volume + small price movement |
| `HIGH_VOLUME_NODE_COUNT(t)` | count | Price levels with volume > threshold |
| `VOLUME_ABOVE_POC_RATIO` | 0-1 | Ratio of volume above POC |
| `VOLUME_BELOW_POC_RATIO` | 0-1 | Ratio of volume below POC |
| `FOOTPRINT_DELTA` | value | Total delta from bucket aggregation |
| `FOOTPRINT_POC` | price | POC price from footprint |

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
FEAR_GREED < 25                                       # Extreme fear (contrarian buy)
FEAR_GREED_AVG(7) < 30 AND RSI(14) < 35             # Sustained fear + oversold
RVOL(52) > 2.0 AND RSI(14) < 30                     # Unusual volume + oversold
RVOL(52, 1) > 1.5                                    # 1.5x normal for this weekday+time
RVOL_PERCENTILE(52, 2) > 90                          # Top 10% volume for this day type
close crosses_above PREV_DAY_POC                     # Reclaim POC
RESISTANCE_RAY_CROSSED(1, 200, 5) == 1               # Breakout

# Candlestick patterns (built-in functions)
SHOOTING_STAR AND BODY_SIZE[1] > ATR(14) * 1.5 AND IS_BULLISH[1] == 1  # Reversal setup
HAMMER AND RSI(14) < 30                              # Hammer at oversold
DOJI AND BODY_SIZE[1] > ATR(14)                     # Indecision after strong move

# Footprint patterns (require aggTrades)
STACKED_BUY_IMBALANCES(4) == 1 AND price < FOOTPRINT_POC  # Strong buying below POC
ABSORPTION(100000, 0.3) == 1 AND VOLUME_ABOVE_POC_RATIO < 0.3  # Absorption
IMBALANCE_AT_POC > 3                                 # Strong buy imbalance at POC

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

### Built-In Phases (44+)
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
| **Sentiment** | `extreme-fear`, `fear`, `greed`, `extreme-greed` |

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
notes: |
  Free-form text describing the strategy concept, rationale,
  or any notes for future reference. Shown in UI above the flow diagram.
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

### Footprint Metrics (when aggTrades available)
Trades also capture footprint metrics at entry/exit/mfe/mae points:
- `imbalanceAtPoc` - Buy/sell ratio at Point of Control
- `stackedBuyImbalances` - Count of consecutive buy imbalances
- `stackedSellImbalances` - Count of consecutive sell imbalances
- `absorptionScore` - Whether absorption pattern detected
- `volumeAbovePocRatio` - Ratio of volume above POC
- `footprintDelta` - Total delta from footprint

### summary.json Structure
```json
{
  "metrics": { "winRate": 62.5, "profitFactor": 2.1, "sharpeRatio": 1.85, "maxDrawdownPercent": 8.2 },
  "analysis": {
    "byPhase": { "uptrend": { "winRate": 75, "vsOverall": +12.5 } },
    "byHour": { "14": { "winRate": 80 } },
    "byFootprint": {
      "byImbalance": { "buyImbalance": { "count": 45, "winRate": 72, "vsOverall": +10 } },
      "byStackedImbalances": { "stackedBuyImbalances": { "count": 20, "winRate": 80 } },
      "byAbsorption": { "withAbsorption": { "count": 15, "winRate": 73 } },
    },
    "suggestions": [
      "Consider requiring 'uptrend' phase",
      "Winners show stronger buy imbalance at POC (3.2 vs 1.1) - consider IMBALANCE_AT_POC > 2 filter"
    ]
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
- Footprint functions (IMBALANCE_AT_POC, STACKED_IMBALANCES, ABSORPTION, etc.)
- Footprint-aware trade analytics with AI suggestions
- Relative Volume (RVOL) — time-of-day volume profiling with DOW/DAYTYPE modes, chart indicator
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

### IMPORTANT: Always Prefer MCP Tools Over Direct File Edits
**NEVER edit YAML/JSON files directly.** Always use MCP tools (`tradery_*`) for all strategy, phase, and hoop operations. MCP validates DSL syntax, ensures correct format, and triggers backtests automatically. Direct file edits bypass validation and can corrupt data.

### MCP Tools (Preferred)

**Strategy Tools:**
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

**Phase Tools:**
| Tool | Purpose |
|------|---------|
| `tradery_list_phases` | List all available phases |
| `tradery_get_phase` | Get phase details (condition, timeframe) |
| `tradery_create_phase` | Create custom phase |
| `tradery_update_phase` | Update custom phase |
| `tradery_delete_phase` | Delete custom phase |
| `tradery_phase_bounds` | Analyze when phase is active over time |

**Hoop Pattern Tools:**
| Tool | Purpose |
|------|---------|
| `tradery_list_hoops` | List all hoop patterns |
| `tradery_get_hoop` | Get hoop pattern details |
| `tradery_create_hoop` | Create hoop pattern |
| `tradery_update_hoop` | Update hoop pattern |
| `tradery_delete_hoop` | Delete hoop pattern |

**Market Data Tools:**
| Tool | Purpose |
|------|---------|
| `tradery_eval_condition` | Test DSL condition on market data |
| `tradery_get_indicator` | Get indicator values |
| `tradery_get_candles` | Get OHLCV data |

**UI Tools:**
| Tool | Purpose |
|------|---------|
| `tradery_get_context` | **Call first!** Returns UI state, chart config, focused strategy config + backtest summary in one call |
| `tradery_update_chart_config` | Enable/disable/configure chart overlays and indicators |

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
| `/ui` | GET | Open windows, last focused strategy, enabled overlays/indicators |
| `/ui/open` | POST | Open UI windows (phases, hoops, settings, data, project) |
| `/ui/chart-config` | GET | Full chart config (all overlays/indicators with params & enabled state) |
| `/ui/chart-config` | POST | Update chart overlays/indicators (partial JSON, see below) |

#### Chart Config API
```bash
# Get current config
curl http://localhost:PORT/ui/chart-config

# Enable RSI and SMA overlays
curl -X POST http://localhost:PORT/ui/chart-config -d '{
  "overlays": { "SMA": { "enabled": true, "periods": [50, 200] } },
  "indicators": { "RSI": { "enabled": true, "period": 14 } }
}'
```
Available overlays: `SMA`, `EMA` (with `periods` array), `BBANDS`, `HighLow`, `Mayer` (with `period`), `VWAP`, `DailyPOC`, `FloatingPOC`, `Rays`, `Ichimoku`.
Available indicators: `RSI`, `ATR`, `ADX`, `RANGE_POSITION` (with `period`), `MACD` (with `fast`, `slow`, `signal`), `STOCHASTIC` (with `kPeriod`, `dPeriod`), `DELTA`, `CVD`, `FUNDING`, `OI`, `PREMIUM`, `FEAR_GREED`, `RVOL` (with `lookbackWeeks`, `mode`, `smooth`).

### Session Startup (IMPORTANT)
**On every new session**, before doing any work, call `tradery_get_context`. This single call returns everything you need: open windows, last focused strategy ID, chart config, the focused strategy's full config, and its backtest summary with metrics and AI suggestions. Use this context — e.g. if the user asks about "this strategy", they mean the last focused one.

### Permissions & Embedded Terminal

Claude Code can run in two contexts:
1. **Standalone** (user runs `claude` in a terminal) — permissions come from `.claude/settings.json` (project-level, checked in) and `.claude/settings.local.json` (user-level, not checked in)
2. **Embedded in Forge** (AI terminal panel) — launched by `AiTerminalPanel.java` with `--allowedTools` flag for runtime permissions, plus the project `settings.json` applies automatically

**`.claude/settings.json`** (checked into repo) pre-approves:
- All MCP tools: `mcp__tradery__*` — no prompts for strategy/phase/hoop/data operations
- File access: `Read(~/.tradery/**)`, `Edit`/`Write` on strategies, phases, hoops
- Bash: `cat`, `curl localhost`, `tail`, `head`, `ls` on `~/.tradery/**`, gradle, scripts
- Skills: `restart`, `analyze`

**Embedded terminal** (`tradery-agent/.../AiTerminalPanel.java`):
- Removes `CLAUDECODE` env var so Claude Code doesn't detect nesting and refuse to start
- Passes `--allowedTools` with file permissions, bash commands, and `mcp__tradery__*`
- Appends system prompt to call `tradery_get_context` on session start
- The `--allowedTools` flag and project `settings.json` stack — both apply

**Data flow when Claude updates a strategy:**
1. Claude calls `tradery_update_strategy` (MCP) → hits Forge HTTP API (`POST /strategy/{id}`)
2. `StrategyHandler` merges partial updates (entry, exit, phases, notes, hoops, orderflow, etc.) and saves YAML
3. `FileWatcher` detects the change → `onStrategyFileChanged()` reloads from disk
4. `loadStrategyData()` refreshes all UI panels (editor, flow diagram, notes, etc.)
5. Backtest runs automatically on the reloaded strategy

**Important:** `AutoSaveScheduler` guards against loops — saves from the API are flagged so the file watcher doesn't trigger a redundant reload cycle.

### AI Workflow
1. **`tradery_get_context`** to know which strategy the user is looking at
2. Read `summary.json` for overview + suggestions
3. Use `tradery_analyze_phases` for phase recommendations
4. Glob trade filenames to understand distribution
5. `tradery_validate_strategy` before any update
6. `tradery_update_strategy` + `tradery_run_backtest`
7. Check results, iterate

### Auto-Reload
FileWatcher monitors `~/.tradery/strategies/{id}/strategy.yaml` per open project window. External edits (Claude, text editor, MCP) trigger automatic reload and backtest. Phase directory is also watched — changes to required/excluded phases trigger re-evaluation.
