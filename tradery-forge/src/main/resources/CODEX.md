# Tradery - Codex Instructions

## Session Startup (REQUIRED)

On every new session, before doing any work, query the running app to understand context:

```bash
PORT=$(cat ~/.tradery/api.port 2>/dev/null)
if [ -n "$PORT" ]; then
  curl -s "http://localhost:$PORT/ui"
fi
```

This tells you which strategy windows are open, which strategy was last focused (`lastFocusedStrategyId` — the one the user is looking at), and what chart overlays/indicators are enabled. Then read the focused strategy's config and summary:

```bash
curl -s "http://localhost:$PORT/strategy/$STRATEGY_ID"
curl -s "http://localhost:$PORT/strategy/$STRATEGY_ID/results"
```

## IMPORTANT: Use the HTTP API, Not Direct File Edits

**NEVER edit strategy/phase/hoop YAML files directly.** Always use the HTTP API. It validates DSL syntax, ensures correct format, and triggers backtests automatically.

Port is in `~/.tradery/api.port`.

### Key Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/ui` | GET | Open windows, last focused strategy, chart config |
| `/ui/chart-config` | GET/POST | Get or update chart overlays/indicators |
| `/strategies` | GET | List all strategies |
| `/strategy/{id}` | GET | Get full strategy config |
| `/strategy/{id}` | POST | Update strategy (partial merge) |
| `/strategy/{id}/validate` | POST | Validate before updating |
| `/strategy/{id}/backtest` | POST | Run backtest |
| `/strategy/{id}/results` | GET | Get backtest results |
| `/phases` | GET | List phases |
| `/eval` | GET | Test DSL condition (`?condition=RSI(14)<30&symbol=BTCUSDT&timeframe=1h`) |
| `/indicator` | GET | Get indicator values |
| `/candles` | GET | Get OHLCV data |

### Workflow

1. **Query UI state** (`GET /ui`) to know which strategy the user is looking at
2. `GET /strategy/{id}` to read the strategy
3. `POST /strategy/{id}/validate` before any update
4. `POST /strategy/{id}` to apply changes
5. `POST /strategy/{id}/backtest` to test
6. `GET /strategy/{id}/results` to check results

### Chart Config

```bash
# Enable SMA overlay with periods 50 and 200
curl -X POST "http://localhost:$PORT/ui/chart-config" \
  -d '{"overlays":{"SMA":{"enabled":true,"periods":[50,200]}}}'

# Enable RSI indicator
curl -X POST "http://localhost:$PORT/ui/chart-config" \
  -d '{"indicators":{"RSI":{"enabled":true,"period":14}}}'
```

## DSL Quick Reference

```
RSI(14) < 30 AND price > SMA(200)
MACD(12,26,9).line crosses_above MACD(12,26,9).signal
ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)
ATR(14) > ATR(14)[1]
```

See `CLAUDE.md` for full DSL reference.
