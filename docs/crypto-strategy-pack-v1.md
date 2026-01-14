# Crypto Strategy Pack — High-Signal Set (v1)

Only realistic, volatility-robust, regime-aware strategies.
This document is the reference specification for the YAML DSL and the strategy algorithms.

## DSL Rules (Reference)

- Regime is a first-class filter. `IS_UPTREND` and `IS_DOWNTREND` are regime aliases.
- BBANDS width: `BBANDS(...).width = upper - lower` (no normalization).
- Lookback: `X[5]` means the value 5 bars ago (no averaging).
- Range functions: `HIGH(n)` and `LOW(n)` are highest/lowest of the last n bars.
- Offset: percent of current close. `+0.2%` -> `close * (1 + 0.002)`.
- ATR is shared across sizing and stop when configured.

---

## 1) Regime Squeeze Breakout (Optimized v2)

Type: Trend Continuation
Edge: Low-volatility squeezes -> explosive breakouts
Timeframes: 5m–1h
Works best on: BTC, ETH, SOL

Algorithm Summary
- Trade only in trend (regime filter)
- Detect compression using Bollinger Width percentile
- Enter on stop-based breakout confirmation
- Wide ATR stop to survive noise
- Profit handled via multi-zone trailing + partials
- No DCA (breakout strategy)

YAML
```yaml
strategy:
  name: "Regime Squeeze Breakout v2"
  description: "Trend filter + Strong volatility compression + Directional breakout"

  regime:
    allow:
      - UPTREND
      - DOWNTREND
    deny:
      - RANGING

  entry:
    long:
      conditions:
        - IS_UPTREND
        - BBANDS(20,2).width < LOWEST(BBANDS(20,2).width, 20) * 1.15
        - close > EMA(20)
        - close > HIGH(5)
      order:
        type: stop
        offset: +0.25%
        size: dynamic

    short:
      conditions:
        - IS_DOWNTREND
        - BBANDS(20,2).width < LOWEST(BBANDS(20,2).width, 20) * 1.15
        - close < EMA(20)
        - close < LOW(5)
      order:
        type: stop
        offset: -0.25%
        size: dynamic

  sizing:
    mode: risk_percent
    risk_percent: 0.5
    stop_source: ATR
    atr_period: 14
    atr_multiplier: 1.5

  stop:
    type: atr
    period: 14
    multiplier: 2.0

  takeprofit:
    zones:
      - range: [0%, 2.5%]
        trailing:
          type: atr
          atr_period: 5
          atr_multiplier: 2.0

      - range: [2.5%, 6%]
        partial_exit:
          percent: 50
          max_times: 1

      - range: [6%, 999%]
        trailing:
          type: atr
          atr_period: 5
          atr_multiplier: 1.5

  dca:
    enabled: false
```

---

## 2) EMA Trend Retracement (Pullback v1)

Type: Trend pullback entry
Edge: Exploits shallow retracements in strong trends
Timeframes: 15m–4h
Best on: BTC, ETH, BNB, LTC

Algorithm Summary
- Trade only in strong trend
- Enter when price tags EMA20/EMA50 depending on volatility
- Confirmation via RSI rebound and candle structure
- No breakout dependency -> more frequent trades
- Wide stop, simple runner logic

YAML
```yaml
strategy:
  name: "EMA Trend Retracement v1"
  description: "Strong trend -> buy the dip into EMA20/50"

  regime:
    allow:
      - UPTREND
    deny:
      - RANGING
      - DOWNTREND

  entry:
    long:
      conditions:
        - IS_UPTREND
        - close <= EMA(20)
        - RSI(14) < 45
        - close > open  # bullish rejection candle
      order:
        type: limit
        offset: -0.4%
        size: dynamic

  sizing:
    mode: risk_percent
    risk_percent: 0.5
    stop_source: ATR
    atr_period: 14
    atr_multiplier: 2.2

  stop:
    type: atr
    period: 14
    multiplier: 2.2

  takeprofit:
    zones:
      - range: [0%, 4%]
        trailing:
          type: atr
          atr_period: 6
          atr_multiplier: 2.0

      - range: [4%, 12%]
        partial_exit:
          percent: 40
          max_times: 1

      - range: [12%, 999%]
        trailing:
          type: atr
          atr_period: 8
          atr_multiplier: 1.5

  dca:
    enabled: true
    max_entries: 2
    bars_between: 12
    on_signal_loss: pause
```

---

## 3) Mean Reversion Extreme Wick Reversal (MR-XR v1)

Type: Counter-trend mean reversion
Edge: Captures liquidation spikes and overreactions
Timeframes: 5m–1h
Best on: BTC, ETH, SOL, DOGE

Algorithm Summary
- Trade only in RANGING regime
- Detect overextension via RSI extremes + wick imbalance
- Enter with limit after liquidation wick
- Tight stop + fast TP
- No runners (scalp/short swing)

YAML
```yaml
strategy:
  name: "MR-XR v1"
  description: "Mean reversion using extreme wicks + overextension"

  regime:
    allow:
      - RANGING
    deny:
      - UPTREND
      - DOWNTREND

  entry:
    long:
      conditions:
        - RSI(14) < 20
        - (high - close) > (close - low) * 2  # long wick down
      order:
        type: limit
        offset: -0.5%
        size: dynamic

    short:
      conditions:
        - RSI(14) > 80
        - (close - low) > (high - close) * 2  # long wick up
      order:
        type: limit
        offset: +0.5%
        size: dynamic

  sizing:
    mode: risk_percent
    risk_percent: 0.3
    stop_source: ATR
    atr_period: 10
    atr_multiplier: 1.2

  stop:
    type: atr
    period: 10
    multiplier: 1.2

  takeprofit:
    zones:
      - range: [0%, 1.5%]
        exit_full: true

  dca:
    enabled: false
```

---

## 4) Funding Rate Momentum (FR-Momo v1)

Type: Futures directional sentiment strategy
Edge: Extreme funding -> predictable unwind moves
Timeframes: 15m–4h
Best on: BTCUSDT, ETHUSDT perps

Algorithm Summary
- Extreme positive funding -> short mean reversion
- Extreme negative funding -> long mean reversion
- Enter via stop on momentum confirmation
- Only for perps with funding data

YAML
```yaml
strategy:
  name: "Funding Rate Momentum v1"
  description: "When funding extremes unwind, momentum follows"

  regime:
    allow:
      - ALL  # funding overrides trend filters

  entry:
    long:
      conditions:
        - FUNDING_RATE < -0.05
        - close > HIGH(3)
      order:
        type: stop
        offset: +0.15%
        size: dynamic

    short:
      conditions:
        - FUNDING_RATE > 0.1
        - close < LOW(3)
      order:
        type: stop
        offset: -0.15%
        size: dynamic

  sizing:
    mode: risk_percent
    risk_percent: 0.4
    stop_source: ATR
    atr_period: 14
    atr_multiplier: 1.8

  stop:
    type: atr
    period: 14
    multiplier: 1.8

  takeprofit:
    zones:
      - range: [0%, 3%]
        trailing:
          type: atr
          atr_period: 5
          atr_multiplier: 2.5

      - range: [3%, 999%]
        exit_full: true

  dca:
    enabled: false
```

Final Note — No Noise
- Trend Breakout
- Trend Pullback
- Mean Reversion with liquidation wicks
- Funding Rate unwind momentum
Anything else is weak, noisy, or not monetizable.

---

# SUPERREND HYBRID v2026 (Crypto Optimized)

Regime filter + Supertrend + Compression Breakout + ATR sizing + multi-zone TP.

Global Parameters (applies to all)
- Trend regime: EMA50 > EMA200
- Supertrend regime: supertrend = green/red
- Volatility compression: BBWidth < percentile (asset-specific)
- Trigger: breakout of HIGH/LOW last X bars
- Stop: ATR-based
- Partial exits + runner

============================================
BTCUSDT – 5m
============================================
```yaml
strategy:
  name: "ST_HYBRID_BTCUSDT_5m_v2026"
  description: "Supertrend + Regime Trend + Compression Breakout – BTC 5m"

  regime:
    conditions:
      - close > EMA(200)
      - EMA(50) > EMA(200)
      - SUPERTREND(10,3).trend == "up"

  entry:
    long:
      conditions:
        - BBWIDTH(20) < PERCENTILE(BBWIDTH(20), 60)
        - ATR(14) > ATR(14)[1]
        - close > HIGH(10)
      order:
        type: stop
        offset: +0.15%
        size: dynamic

    short:
      conditions:
        - BBWIDTH(20) < PERCENTILE(BBWIDTH(20), 60)
        - ATR(14) > ATR(14)[1]
        - close < LOW(10)
      order:
        type: stop
        offset: -0.15%
        size: dynamic

  sizing:
    mode: risk_percent
    risk_percent: 0.35
    stop_source: ATR
    atr_period: 14
    atr_multiplier: 2.5

  stop:
    type: atr
    period: 14
    multiplier: 2.5

  takeprofit:
    zones:
      - range: [0%, 2.2%]
        trailing:
          type: atr
          atr_period: 5
          atr_multiplier: 2.2

      - range: [2.2%, 5%]
        partial_exit:
          percent: 40
          max_times: 1

      - range: [5%, 999%]
        trailing:
          type: atr
          atr_period: 6
          atr_multiplier: 1.8

  dca:
    enabled: false
```

============================================
BTCUSDT – 15m
============================================
```yaml
strategy:
  name: "ST_HYBRID_BTCUSDT_15m_v2026"

  regime:
    conditions:
      - EMA(50) > EMA(200)
      - SUPERTREND(10,3).trend == "up"

  entry:
    long:
      conditions:
        - BBWIDTH(20) < PERCENTILE(BBWIDTH(20), 55)
        - ATR(14) rising
        - close > HIGH(12)
      order:
        type: stop
        offset: +0.18%

  sizing:
    mode: risk_percent
    risk_percent: 0.30
    stop_source: ATR
    atr_period: 14
    atr_multiplier: 2.2

  takeprofit:
    zones:
      - range: [0%, 3%]
        trailing:
          type: atr
          atr_period: 5
          atr_multiplier: 2.0

      - range: [3%, 7%]
        partial_exit:
          percent: 40
          max_times: 1

      - range: [7%, 999%]
        trailing:
          type: atr
          atr_period: 6
          atr_multiplier: 1.7
```

============================================
ETHUSDT – 5m
============================================
```yaml
strategy:
  name: "ST_HYBRID_ETHUSDT_5m_v2026"

  regime:
    conditions:
      - EMA(50) > EMA(200)
      - SUPERTREND(10,2.8).trend == "up"

  entry:
    long:
      conditions:
        - BBWIDTH(20) < PERCENTILE(BBWIDTH(20), 55)
        - ATR(14) > ATR(14)[1]
        - close > HIGH(9)
      order:
        type: stop
        offset: +0.12%

  sizing:
    mode: risk_percent
    risk_percent: 0.30
    stop_source: ATR
    atr_period: 14
    atr_multiplier: 2.2

  takeprofit:
    zones:
      - range: [0%, 1.8%]
        trailing:
          type: atr
          atr_period: 5
          atr_multiplier: 2.0

      - range: [1.8%, 4%]
        partial_exit:
          percent: 40

      - range: [4%, 999%]
        trailing:
          type: atr
          atr_period: 6
          atr_multiplier: 1.7
```

============================================
ETHUSDT – 15m
============================================
```yaml
strategy:
  name: "ST_HYBRID_ETHUSDT_15m_v2026"

  regime:
    conditions:
      - EMA(50) > EMA(200)
      - SUPERTREND(10,3).trend == "up"

  entry:
    long:
      conditions:
        - BBWIDTH(20) < PERCENTILE(BBWIDTH(20), 50)
        - ATR rising
        - close > HIGH(10)

  sizing:
    mode: risk_percent
    risk_percent: 0.28
    atr_period: 14
    atr_multiplier: 2.0

  takeprofit:
    zones:
      - range: [0%, 2.3%]
        trailing:
          type: atr
          multiplier: 1.8

      - range: [2.3%, 5.5%]
        partial_exit:
          percent: 40

      - range: [5.5%, 999%]
        trailing:
          type: atr
          multiplier: 1.6
```

============================================
SOLUSDT – 5m
============================================
```yaml
strategy:
  name: "ST_HYBRID_SOLUSDT_5m_v2026"

  regime:
    conditions:
      - EMA(50) > EMA(200)
      - SUPERTREND(10,3.2).trend == "up"

  entry:
    long:
      conditions:
        - BBWIDTH(20) < PERCENTILE(BBWIDTH(20), 70)
        - ATR rising
        - close > HIGH(14)
      order:
        type: stop
        offset: +0.22%

  sizing:
    mode: risk_percent
    risk_percent: 0.35
    atr_period: 14
    atr_multiplier: 3.0

  takeprofit:
    zones:
      - range: [0%, 3%]
        trailing:
          type: atr
          multiplier: 2.5

      - range: [3%, 8%]
        partial_exit:
          percent: 50

      - range: [8%, 999%]
        trailing:
          type: atr
          multiplier: 1.9
```

============================================
SOLUSDT – 15m
============================================
```yaml
strategy:
  name: "ST_HYBRID_SOLUSDT_15m_v2026"

  regime:
    conditions:
      - EMA(50) > EMA(200)
      - SUPERTREND(10,3.0).trend == "up"

  entry:
    long:
      conditions:
        - BBWIDTH(20) < PERCENTILE(BBWIDTH(20), 65)
        - ATR(14) > ATR(14)[1]
        - close > HIGH(12)
      order:
        type: stop
        offset: +0.20%

  sizing:
    mode: risk_percent
    risk_percent: 0.32
    atr_period: 14
    atr_multiplier: 2.7

  takeprofit:
    zones:
      - range: [0%, 3.5%]
        trailing:
          type: atr
          multiplier: 2.2

      - range: [3.5%, 9%]
        partial_exit:
          percent: 50

      - range: [9%, 999%]
        trailing:
          type: atr
          multiplier: 1.8
```
