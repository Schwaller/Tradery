## Overview

**Tradery** is a backtesting engine for cryptocurrency trading strategies. It lets you define trading rules using a simple DSL (Domain Specific Language), test them against historical data, and analyze the results to improve your approach.

### How It Works

The backtester simulates your strategy bar-by-bar through historical price data:

Load Candles → Check Phases → Evaluate Entry → Manage Position → Check Exit → Record Trade

**For each bar, the engine:**
- Evaluates all **phase conditions** to determine market regime
- Checks if **entry conditions** are met (and phases allow trading)
- Manages open positions through **exit zones** based on P&L
- Applies **stop losses**, **take profits**, and **trailing stops**
- Records detailed trade data for analysis

> **Note:** Data Sources: OHLCV candles from Binance (auto-cached), plus optional funding rates, open interest, and aggregated trades for orderflow analysis.

### Key Concepts

| Concept | What It Does | Example |
|---------|--------------|---------|
| **DSL Condition** | Formula that evaluates to true/false | RSI(14) < 30 AND close > SMA(200) |
| **Entry Signal** | When to open a new position | Buy when RSI oversold in uptrend |
| **Exit Zone** | Rules based on current P&L | If P&L > 5%, use trailing stop |
| **Phase** | Market filter (multi-timeframe) | Only trade during "Uptrend" on daily |
| **Hoop Pattern** | Chart pattern detection | Detect double bottoms, flags |
| **Candle Pattern** | Built-in candlestick detection | SHOOTING_STAR AND IS_BULLISH[1] |

> **Tip:** The Strategy Flow: `Phases` filter *when* you can trade → `Entry` defines *what* triggers a trade → `Exit Zones` control *how* you manage and close

**After backtesting, you get:**
- **Metrics:** Win rate, profit factor, Sharpe ratio, max drawdown
- **Trade Analysis:** Performance by hour, day, and active phases
- **Individual Trades:** Entry/exit prices, MFE/MAE, indicator values
- **Suggestions:** AI-generated improvement recommendations

> **Note:** Workflow: Create strategy → Run backtest → Analyze results → Refine conditions → Repeat

---

## Charts & Visualizations

Tradery provides multiple charts to help you understand strategy performance and market conditions.

### Price Chart

The main chart displays **candlesticks** showing Open, High, Low, Close prices for each bar:
- **Green candles:** Close > Open (bullish/up move)
- **Red candles:** Close < Open (bearish/down move)
- **Wicks:** High and low prices reached during the bar

**Trade markers** show entry/exit points:
- Green triangle up: Entry point
- Red triangle down: Exit point
- Connecting line shows trade duration and direction

**Volume bars** (below price) show trading activity per bar. Higher volume = stronger conviction.

### Equity Curve

Shows your strategy's account value over time:
- **Line rising:** Account growing (winning trades)
- **Line falling:** Drawdown period (losing trades)
- **Steepness:** Rate of gains/losses

> **Note:** What to look for:
> - **Smooth upward slope:** Consistent returns, good strategy
> - **Jagged/volatile:** Inconsistent, may need refinement
> - **Long flat periods:** Strategy not finding trades
> - **Sharp drops:** Drawdowns - check if acceptable

### Indicator Charts

Sub-charts below the price chart showing technical indicators:

| Chart | What It Shows | How to Read |
|-------|---------------|-------------|
| **RSI** | Momentum (0-100) | <30 oversold, >70 overbought |
| **MACD** | Trend momentum | Line crosses signal = trend change |
| **ATR** | Volatility | Higher = more volatile, wider stops needed |
| **Stochastic** | Momentum (0-100) | <20 oversold, >80 overbought |
| **ADX** | Trend strength | >25 trending, <20 ranging |
| **Range Position** | Position in price range | -1 at low, +1 at high |
| **Delta** | Buy vs sell pressure | Positive = buyers dominate |
| **CVD** | Cumulative delta | Rising = sustained buying |
| **Funding** | Futures funding rate | High = overleveraged longs |

> **Tip:** Right-click on the chart area to enable/disable indicator charts and configure their parameters.

### Chart Overlays

Overlays are drawn directly on the price chart:

| Overlay | What It Shows | Usage |
|---------|---------------|-------|
| **SMA/EMA** | Moving averages | Trend direction, support/resistance |
| **Bollinger Bands** | Volatility envelope | Upper/lower bands show overbought/oversold |
| **High/Low** | N-period high/low | Range breakout levels |
| **VWAP** | Volume-weighted average price | Fair value, institutional reference |
| **Daily POC** | Point of control | Price level with most volume |
| **Ichimoku** | Cloud system | Trend, support/resistance, momentum |
| **Rays** | Auto-detected trendlines | Dynamic support/resistance from highs/lows |

---

## Performance Metrics

After running a backtest, you'll see these metrics. Understanding them is key to improving your strategy.

### Core Metrics

| Metric | What It Measures | Good Values |
|--------|------------------|-------------|
| **Total Trades** | Number of completed trades | Enough for statistical significance (30+) |
| **Win Rate** | Percentage of profitable trades | >50%, but depends on risk:reward |
| **Profit Factor** | Gross profits / Gross losses | >1.5 good, >2.0 excellent |
| **Total Return** | Overall P&L as % of initial capital | Context-dependent (vs buy & hold) |
| **Max Drawdown** | Largest peak-to-trough decline | <20% conservative, <30% aggressive |
| **Sharpe Ratio** | Risk-adjusted returns | >1.0 good, >2.0 excellent |
| **Avg Win** | Average profit on winning trades | Should be > Avg Loss for low win rates |
| **Avg Loss** | Average loss on losing trades | Controls risk per trade |

> **Note:** Key relationships:
> - **Win Rate + Avg Win/Loss:** A 30% win rate is fine if wins are 3x larger than losses
> - **Profit Factor:** Directly shows if strategy makes money (must be >1.0)
> - **Sharpe Ratio:** Accounts for volatility - high returns with low volatility = better

### Trade Quality Metrics

These metrics help you optimize exit timing:

| Metric | What It Measures | What It Tells You |
|--------|------------------|-------------------|
| **MFE** | Max Favorable Excursion | Best unrealized profit during trade |
| **MAE** | Max Adverse Excursion | Worst unrealized drawdown during trade |
| **Capture Ratio** | Actual P&L / MFE | How much of the move you captured |
| **Pain Ratio** | \|MAE\| / MFE | How much pain you endured vs potential gain |

> **Tip:** MFE/MAE Analysis:
> - **Low capture ratio (<50%):** Exits are too early, consider trailing stops
> - **High MAE on winners:** Tight stops might be cutting winners short
> - **MFE > 0 on losers:** Trades went profitable but reversed - tighten take profits

**Additional metrics:**

| Metric | Description |
|--------|-------------|
| **Total Fees** | Trading fees (maker/taker) deducted from P&L |
| **Holding Costs** | Funding rate payments (futures) - can be positive (cost) or negative (earnings) |
| **Max Capital** | Peak capital used - shows how much of your account is at risk |
| **Final Equity** | Ending account value after all trades |

---

## Indicators Overview

Indicators are the building blocks of your DSL conditions. Here's a quick reference of the most commonly used ones. For full syntax, use the **DSL Reference** (click ? in the condition editor).

### Price & Trend

| Indicator | Syntax | What It Does |
|-----------|--------|--------------|
| **Price** | price, close, open, high, low | Current bar's OHLC values |
| **SMA** | SMA(period) | Simple moving average - smooths price |
| **EMA** | EMA(period) | Exponential MA - faster response to changes |
| **ADX** | ADX(period) | Trend strength (0-100). >25 = trending |
| **PLUS_DI/MINUS_DI** | PLUS_DI(14), MINUS_DI(14) | Directional indicators. +DI > -DI = uptrend |
| **Supertrend** | SUPERTREND(n,mult).trend | Trend direction. 1 = up, -1 = down |

### Momentum & Volatility

| Indicator | Syntax | What It Does |
|-----------|--------|--------------|
| **RSI** | RSI(period) | Momentum (0-100). <30 oversold, >70 overbought |
| **Stochastic** | STOCHASTIC(k,d).k | Momentum oscillator (0-100) |
| **MACD** | MACD(12,26,9).line | Trend momentum. Line crossing signal = change |
| **ATR** | ATR(period) | Average True Range - measures volatility |
| **Bollinger** | BBANDS(20,2).upper/lower | Volatility bands around price |
| **Range Position** | RANGE_POSITION(20) | Where price is in recent range (-1 to +1) |

### Orderflow & Volume

| Indicator | Syntax | What It Does |
|-----------|--------|--------------|
| **Volume** | volume | Current bar's trading volume |
| **Avg Volume** | AVG_VOLUME(20) | Average volume over N bars |
| **VWAP** | VWAP | Volume-weighted average price |
| **Delta** | DELTA | Buy volume - sell volume |
| **CVD** | CUM_DELTA | Cumulative delta (running total) |
| **Funding** | FUNDING | Current funding rate (futures) |
| **OI** | OI, OI_CHANGE | Open interest and change |

> **Note:** DSL Syntax Tips:
> - Use `[n]` for lookback: `RSI(14)[1]` = RSI one bar ago
> - Use `crosses_above` / `crosses_below` for crossover signals
> - Combine with `AND` / `OR` for complex conditions
> - Click the **?** button in condition editors for full DSL reference

---

## Entry Settings

### Entry Condition

A DSL formula that evaluates to true/false each bar. When true, a trade signal fires.

> **Example:** RSI(14) < 30 AND close > SMA(200)
> Looks for oversold conditions in an uptrend.

> **Tip:** Candlestick Patterns: Use built-in functions for candle detection:
> `HAMMER`, `SHOOTING_STAR`, `DOJI` - return 1 if pattern detected
> `BODY_SIZE`, `BODY_RATIO`, `IS_BULLISH`, `IS_BEARISH` - candle properties (support [n] lookback)
>
> **Example:** SHOOTING_STAR AND BODY_SIZE[1] > ATR(14) * 1.5 AND IS_BULLISH[1] == 1
> Shooting star after a strong bullish candle = reversal signal

### Trade Limits

| Setting | Description |
|---------|-------------|
| **Max Open Trades** | Maximum concurrent positions (DCA groups count as one) |
| **Min Candles Between** | Minimum bars between new position entries |

### Order Types

Controls **how** you enter after a signal fires:

| Type | Behavior | Settings |
|------|----------|----------|
| **Market** | Enter immediately at current price | None |
| **Limit** | Enter when price drops to X% below signal price | Offset % (negative) |
| **Stop** | Enter when price rises to X% above signal price | Offset % (positive) |
| **Trailing** | Trail price down, enter on X% reversal up | Reversal % |

**Expiration:** For non-market orders, cancel if not filled within X bars.

### DCA (Dollar Cost Averaging)

Add to a position over multiple entries instead of going all-in at once.

| Setting | Description |
|---------|-------------|
| **Enabled** | Turn DCA on/off |
| **Max Entries** | Maximum entries per position (e.g., 3) |
| **Bars Between** | Minimum bars between DCA entries |
| **Signal Loss** | What to do if entry signal turns false: |

- **Pause:** Wait for signal to return before adding
- **Abort:** Close entire position if signal lost
- **Continue:** Keep adding regardless of signal

---

## Exit Settings

### Exit Zones

Zones define behavior at different P&L levels. Each zone has a range and exit rules.

> **Note:** Example Setup:
> Zone 1: P&L < -5% → Exit immediately (stop loss)
> Zone 2: P&L 0% to 5% → Exit if RSI > 70
> Zone 3: P&L > 10% → Exit immediately (take profit)

### Zone Settings

| Setting | Description |
|---------|-------------|
| **P&L Range** | Min/Max P&L % where this zone applies |
| **Market Exit** | Exit at market price (ignores SL/TP levels) |
| **Exit Condition** | DSL condition that must be true to exit |
| **Min Bars Before Exit** | Wait X bars after entry before allowing exit |

### Stop Loss Types

| Type | Description |
|------|-------------|
| **None** | No stop loss in this zone |
| **Fixed %** | Stop X% below entry price |
| **Fixed ATR** | Stop X * ATR(14) below entry price |
| **Trailing %** | Trail X% below highest price since entry |
| **Trailing ATR** | Trail X * ATR(14) below highest price |
| **Clear** | Reset trailing stop when entering this zone |

### Take Profit Types

| Type | Description |
|------|-------------|
| **None** | No take profit target in this zone |
| **Fixed %** | Exit at X% above entry price |
| **Fixed ATR** | Exit at X * ATR(14) above entry price |

### Partial Exits

Close only a portion of the position in a zone:

| Setting | Description |
|---------|-------------|
| **Exit %** | Percentage of position to close (e.g., 50%) |
| **Basis** | **Original:** % of original size / **Remaining:** % of what's left |
| **Max Exits** | Maximum partial exits in this zone |
| **Min Bars Between** | Minimum bars between partial exits |
| **Re-entry** | **Block:** No re-exit in same zone / **Reset:** Allow if price leaves and returns |

### Zone Phase Filters

Each zone can have its own phase requirements (e.g., only trigger stop loss during high volatility).

---

## Phases (Market Filters)

Phases are **optional** filters that control **when** your strategy can trade. Without phases, your strategy trades purely based on the DSL entry condition.

> **Note:** Simple strategy (no phases): Entry fires whenever `RSI(14) < 30` is true.
> Filtered strategy: Entry fires when `RSI(14) < 30` is true *AND* required phases are active.

When phases are configured:
- **Required phases:** ALL must be active to allow entry
- **Excluded phases:** NONE must be active to allow entry

> **Tip:** Multi-timeframe analysis: Phases can run on different timeframes than your strategy. Use a daily uptrend phase to filter 1h entries.

### Session Phases

| Phase | Hours (UTC) | Usage Ideas |
|-------|-------------|-------------|
| **Asian Session** | 00:00 - 09:00 | Range-bound strategies; lower volatility scalping; accumulation plays |
| **European Session** | 07:00 - 16:00 | Breakout strategies at London open; trend continuation; high volume plays |
| **US Market Hours** | 14:00 - 21:00 | High volatility momentum; news-driven moves; trend strategies |
| **US Core Hours** | 15:00 - 21:00 | Most liquid period; tighter spreads; institutional flow |
| **Session Overlap** | 14:00 - 16:00 | Maximum liquidity; best fills; strongest directional moves |

> **Example:** Require "US Market Hours" for momentum strategies, exclude for mean-reversion.
> **Example:** Require "Session Overlap" for breakout plays needing high volume confirmation.

### Day/Time Phases

| Phase | Usage Ideas |
|-------|-------------|
| **Monday** | Gap fills from weekend; cautious positioning; wait for direction |
| **Tuesday - Thursday** | Core trading days; strongest trends; most reliable signals |
| **Friday** | Position squaring; reduced late-day entries; watch for reversals |
| **Weekdays** | Exclude weekend noise; focus on institutional flow |
| **Weekend** | Lower liquidity plays; retail-driven moves; gap risk strategies |

> **Example:** Exclude Monday + Friday for trend strategies to avoid choppy opens/closes.
> **Example:** Require "Weekdays" to avoid weekend low-liquidity wicks.

### Technical Phases

Evaluated on the **daily timeframe** for macro context:

| Phase | Condition | Usage Ideas |
|-------|-----------|-------------|
| **Uptrend** | ADX > 25, +DI > -DI | Long-only strategies; buy dips; trend following |
| **Downtrend** | ADX > 25, -DI > +DI | Short-only strategies; sell rallies; avoid longs |
| **Ranging** | ADX < 20 | Mean-reversion; Bollinger bounces; avoid breakouts |
| **Golden Cross** | SMA(50) > SMA(200) | Bull market bias; aggressive longs; higher risk tolerance |
| **Death Cross** | SMA(50) < SMA(200) | Bear market caution; defensive positioning; tighter stops |
| **Overbought** | RSI(14) > 70 | Avoid new longs; look for shorts; take profits |
| **Oversold** | RSI(14) < 30 | Avoid new shorts; look for longs; accumulation zones |

> **Example:** Require "Uptrend" + "Golden Cross" for maximum bullish confluence.
> **Example:** Exclude "Overbought" from long entries to avoid buying tops.
> **Example:** Require "Ranging" for mean-reversion strategies.

### Calendar Phases

| Phase | Description | Usage Ideas |
|-------|-------------|-------------|
| **Month Start** | Days 1-5 | Fresh capital inflows; bullish bias; institutional buying |
| **Month End** | Days 25+ | Rebalancing flows; window dressing; potential reversals |
| **Quarter End** | Last week of Mar/Jun/Sep/Dec | Major rebalancing; increased volatility; fund flows |
| **US Bank Holiday** | Fed holidays | Low liquidity; avoid or use wider stops; reduced position size |
| **FOMC Meeting** | Fed meeting days | Extreme volatility; avoid or trade the event; wait for clarity |
| **Full Moon** | ~1 day/hour window | Sentiment extremes; contrarian signals; lunar cycle analysis |
| **New Moon** | ~1 day/hour window | Fresh starts; trend initiations; cycle beginnings |

> **Example:** Exclude "FOMC Meeting" to avoid unpredictable Fed volatility.
> **Example:** Require "Month Start" for momentum strategies (capital inflow bias).
> **Example:** Exclude "US Bank Holiday" to avoid low-liquidity traps.

### Funding Phases

Futures-specific phases based on funding rates:

| Phase | Condition | Usage Ideas |
|-------|-----------|-------------|
| **High Funding** | > 0.05% | Overleveraged longs; contrarian shorts; funding arbitrage |
| **Negative Funding** | < 0 | Shorts paying longs; bullish bias; long entries favored |
| **Extreme Funding** | > 0.1% or < -0.05% | Imminent squeeze risk; reduce exposure; wait for normalization |
| **Neutral Funding** | -0.01% to 0.02% | Balanced market; trend strategies work best; normal conditions |

> **Example:** Exclude "High Funding" from longs (crowded trade risk).
> **Example:** Require "Negative Funding" for long entries (shorts paying you).
> **Example:** Exclude "Extreme Funding" entirely (squeeze risk both ways).

### Custom Phases

Create your own phases with **any DSL condition** on **any timeframe**. Open the Phases window to add custom phases.

> **Note:** Custom Phase Examples:
>
> **Strong Trend (4h):** ADX(14) > 30 AND ATR(14) > ATR(14)[20]
> Use: Only trade when trend is strong and volatility expanding
>
> **Whale Accumulation:** WHALE_DELTA(100000) > 0 AND close < SMA(20)
> Use: Large players buying while price is below average
>
> **Volatility Squeeze:** BBANDS(20,2).width < LOWEST(BBANDS(20,2).width, 50) * 1.1
> Use: Breakout imminent, prepare for directional move
>
> **Volume Spike:** volume > AVG_VOLUME(20) * 2
> Use: Require confirmation of significant participation
>
> **Near Support Ray:** SUPPORT_RAY_DISTANCE(1, 200, 5) < 1.0
> Use: Price approaching dynamic support trendline
>
> **Hammer at Support (4h):** HAMMER AND RSI(14) < 40
> Use: Bullish reversal candle at oversold levels
>
> **Large Candle (4h):** BODY_SIZE > ATR(14) * 1.5
> Use: Significant directional move, momentum confirmation

> **Tip:** Phase timeframes: Use higher timeframes (4h, 1d) for context, lower timeframes (15m, 1h) for precision timing.

---

## Hoop Patterns

Hoops detect **chart patterns** by checking if price passes through a sequence of "hoops" (price checkpoints). Each hoop defines a price range and timing window.

### How Hoops Work

A hoop pattern is a series of checkpoints that price must hit in sequence:
- **Anchor:** Starting reference point (first price that enters hoop 1)
- **Checkpoints:** Each subsequent hoop defines a price range relative to the anchor
- **Timing:** Distance and tolerance control how many bars between hoops
- **Completion:** Pattern fires when all hoops are hit in order within timing constraints

> **Note:** Visual Example - V-Bottom:
> ```
>      Anchor ─────┐
>                  │  Hoop 1: -2% to -4%
>                  ▼
>               ╲    ╱  Hoop 2: -1% to +1%
>                ╲  ╱
>                 ╲╱     ← Bottom (low point)
>                        Hoop 3: +2% to +5% (breakout)
> ```

### Hoop Settings

| Setting | Description | Tips |
|---------|-------------|------|
| **Min Price %** | Minimum % from anchor | Use negative for drops, positive for rises |
| **Max Price %** | Maximum % from anchor | Leave null for "at least X%" breakouts |
| **Distance** | Expected bars from previous hoop | Based on your timeframe (e.g., 10 bars on 1h = 10 hours) |
| **Tolerance** | +/- bars allowed from distance | Higher = looser pattern, more matches |
| **Anchor Mode** | How next anchor is set | **Actual:** Use real hit price, **Target:** Use zone midpoint |

**Pattern-level settings:**

| Setting | Description |
|---------|-------------|
| **Cooldown Bars** | Wait N bars after pattern completes before detecting another |
| **Allow Overlap** | Can a new pattern start while previous is incomplete? |
| **Price Smoothing** | Apply SMA/EMA/HLC3 to price for smoother detection |

### Pattern Examples

> **Note:** Double Bottom - Classic reversal pattern
>
> | Hoop | Price % | Distance | Description |
> |------|---------|----------|-------------|
> | 1 | -1% to -3% | 5 bars | First low |
> | 2 | +1% to +4% | 7 bars | Middle peak |
> | 3 | -3% to +0.5% | 7 bars | Second low (near first) |
> | 4 | +2% to null | 5 bars | Breakout confirmation |
>
> Use: Long entry on hoop 4 completion with stop below the lows.

> **Note:** Bull Flag - Trend continuation pattern
>
> | Hoop | Price % | Distance | Description |
> |------|---------|----------|-------------|
> | 1 | +3% to +8% | 5 bars | Flagpole (strong move up) |
> | 2 | +1% to +5% | 10 bars | Consolidation (flag) |
> | 3 | +6% to null | 5 bars | Breakout above flag |
>
> Use: Continuation long after flag breakout.

> **Note:** Head and Shoulders - Reversal pattern
>
> | Hoop | Price % | Distance | Description |
> |------|---------|----------|-------------|
> | 1 | +2% to +4% | 5 bars | Left shoulder |
> | 2 | -1% to +1% | 5 bars | Neckline |
> | 3 | +4% to +7% | 5 bars | Head (higher high) |
> | 4 | -1% to +1% | 5 bars | Return to neckline |
> | 5 | +2% to +4% | 5 bars | Right shoulder (lower) |
> | 6 | -2% to null | 5 bars | Breakdown |
>
> Use: Short entry on neckline breakdown.

### Combine Modes

How to combine hoop patterns with DSL conditions:

| Mode | Behavior | Use Case |
|------|----------|----------|
| **DSL Only** | Ignore hoops entirely | Traditional indicator-based strategies |
| **Hoop Only** | Ignore DSL entirely | Pure pattern recognition |
| **AND** | Both must trigger | Pattern + confirmation (e.g., double bottom + RSI oversold) |
| **OR** | Either can trigger | Multiple entry signals (pattern or indicator) |

> **Tip:** Best practice: Use AND mode with a confirming indicator (RSI, volume) to filter false pattern signals.

---

## Tips

> **Tip:** Start simple. A clear entry with basic zones beats a complex system you don't understand.

> **Tip:** Use phases for context. A strategy that works in uptrends may fail in downtrends.

> **Tip:** Watch the metrics. Win rate alone doesn't matter - look at profit factor and max drawdown.

> **Tip:** Test order types. Limit entries can improve average entry price but reduce fill rate.
