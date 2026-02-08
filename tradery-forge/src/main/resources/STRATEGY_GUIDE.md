# Plaiiin Strategy Guide

A comprehensive guide to building trading strategies in Plaiiin.

---

## Strategy Fields

| Field | Description |
|-------|-------------|
| **id** | Unique identifier (used for file paths) |
| **name** | Display name shown in UI |
| **description** | Brief description of the strategy |
| **notes** | Free-form notes for strategy concept, rationale, or ideas. Shown in UI above the flow diagram. Use this to document the "why" behind your strategy. |
| **enabled** | Whether the strategy is active |

---

## Entry Settings

### Entry Condition (DSL)
The entry condition is written in Plaiiin's Domain Specific Language (DSL). When this condition becomes true, an entry signal is generated.

**Example conditions:**
- `RSI(14) < 30` - RSI oversold
- `close > SMA(200)` - Price above 200 SMA
- `EMA(20) crosses_above EMA(50)` - Golden cross
- `RSI(14) < 30 AND close > SMA(200)` - Combined conditions

### Entry Order Types

| Type | Behavior | Use Case |
|------|----------|----------|
| **Market** | Enter immediately at signal bar close | "Enter now when conditions are met" |
| **Limit** | Enter when price drops X% below signal price | "RSI oversold, buy 1% lower for better fill" |
| **Stop** | Enter when price rises X% above signal price | "Breakout signal, confirm with push higher" |
| **Trailing** | Trail price down, enter on X% reversal up | "Catch falling knife after bounce confirms" |

**How non-Market orders work:**
1. DSL condition triggers - creates a pending order
2. Each subsequent bar checks if fill conditions are met
3. If filled - position opens, DCA takes over
4. If new signal while pending - old order replaced with new one
5. If expired (optional) - order cancelled, recorded as expired

### Order Offset (Limit/Stop)
- **Limit**: Negative offset (e.g., -1.0% means buy 1% below signal price)
- **Stop**: Positive offset (e.g., +0.5% means buy 0.5% above signal price)

### Trailing Reversal %
For Trailing entries: the percentage price must bounce up from the lowest point to trigger entry. For example, 1.5% means if price drops to $49,000 then rises to $49,735 (1.5% above the low), entry triggers.

### Expiration (Bars)
Optional. Cancel the pending order if not filled after X bars. Leave empty for no expiration (order stays until filled or replaced).

### Max Open Trades
Maximum number of positions that can be open simultaneously. Default is 1.

### Min Candles Between
Minimum number of bars between new trade entries. Helps avoid clustering trades during volatile periods.

---

## DCA (Dollar Cost Averaging)

DCA allows adding to an existing position after the initial entry.

### DCA Settings

| Setting | Description |
|---------|-------------|
| **Enabled** | Turn DCA on/off |
| **Max Entries** | Maximum additional entries per position |
| **Bars Between** | Minimum bars between DCA entries |
| **Signal Loss Mode** | What happens when entry condition is no longer true |

### Signal Loss Modes

| Mode | Behavior |
|------|----------|
| **Continue** | Keep adding DCA entries regardless of signal |
| **Pause** | Stop DCA entries while signal is lost, resume when true again |
| **Abort** | Stop all future DCA entries for this position |

**Important:** DCA only activates after the initial entry fills. If using pending orders (Limit/Stop/Trailing), DCA begins after the fill, not at signal time.

---

## Exit Zones

Exit zones define different trade management rules based on the current P&L of a position.

### How Zones Work
1. Each zone has a P&L range (min to max percent)
2. As trade P&L changes, different zones become active
3. Each zone can have its own stop-loss, take-profit, and exit rules
4. Zones are evaluated in order - first matching zone applies

### Zone Settings

| Setting | Description |
|---------|-------------|
| **Name** | Descriptive label for the zone |
| **Min P&L %** | Zone activates when P&L >= this (empty = no minimum) |
| **Max P&L %** | Zone activates when P&L < this (empty = no maximum) |
| **Exit Condition** | Optional DSL condition to trigger exit |
| **Exit Immediately** | Exit as soon as trade enters this zone |

### Stop-Loss Types

| Type | Description |
|------|-------------|
| **None** | No stop-loss in this zone |
| **Fixed %** | Exit if price drops X% from entry price |
| **Trailing %** | Exit if price drops X% from highest price since entry |
| **Fixed ATR** | Exit if price drops X * ATR from entry |
| **Trailing ATR** | Exit if price drops X * ATR from highest point |

### Take-Profit Types

| Type | Description |
|------|-------------|
| **None** | No automatic take-profit |
| **Fixed %** | Exit when profit reaches X% |
| **Fixed ATR** | Exit when profit reaches X * ATR |

### Partial Exits (Scaling Out)

| Setting | Description |
|---------|-------------|
| **Exit %** | Percentage of position to exit (empty = 100%) |
| **Max Exits** | Maximum partial exits in this zone |
| **Exit Basis** | "Remaining" (% of current position) or "Original" (% of initial size) |
| **Exit Re-entry** | "Continue" (resume count) or "Reset" (start fresh if re-entering zone) |
| **Min Bars Between** | Minimum bars between partial exits |

### Example Zone Setup

**Conservative Protection:**
1. **Default** (P&L < 1%): 2% trailing stop - let trade develop
2. **Lock Gains** (1-3%): 0.5% trailing stop - protect profits
3. **Take Profit** (>3%): Exit immediately

**Scaling Out:**
1. **Hold** (P&L < 3%): 2% trailing stop
2. **First Exit** (3-6%): Exit 33% of position
3. **Second Exit** (6-10%): Exit 50% of remaining
4. **Let Ride** (>10%): 1% trailing stop on final portion

---

## Phases

Phases are market regime filters that control WHEN a strategy can enter trades. They enable multi-timeframe analysis and calendar-aware trading.

### How Phases Work
1. Each phase has its own condition and timeframe
2. Phase is evaluated independently, producing true/false
3. Results are mapped to the strategy's timeframe
4. Entry only allowed when all required phases are active AND no excluded phases are active

### Using Phases
- **Required Phases**: ALL must be active to allow entry
- **Excluded Phases**: If ANY is active, entry is blocked

### Built-in Phases (32 total)

#### Session Phases
| Phase | Hours (UTC) | Description |
|-------|-------------|-------------|
| Asian Session | 0:00-9:00 | Tokyo/Hong Kong hours |
| European Session | 7:00-16:00 | London/Frankfurt hours |
| US Market Hours | 14:00-21:00 | NYSE/NASDAQ including pre-market |
| US Market Core | 15:00-21:00 | NYSE/NASDAQ core hours only |
| Session Overlap | 14:00-16:00 | US/Europe overlap - highest liquidity |

#### Day/Time Phases
| Phase | Description |
|-------|-------------|
| Monday - Sunday | Individual day filters |
| Weekdays | Monday through Friday |
| Weekend | Saturday and Sunday |

#### Technical Phases
| Phase | Condition | Timeframe |
|-------|-----------|-----------|
| Uptrend | ADX > 25 with +DI > -DI | Daily |
| Downtrend | ADX > 25 with -DI > +DI | Daily |
| Ranging | ADX < 20 | Daily |
| Golden Cross | 50 SMA > 200 SMA | Daily |
| Death Cross | 50 SMA < 200 SMA | Daily |
| Overbought | RSI > 70 | Daily |
| Oversold | RSI < 30 | Daily |

#### Calendar Phases
| Phase | Description |
|-------|-------------|
| Month Start | First 5 days - fresh capital inflows |
| Month End | Last 5 days - rebalancing flows |
| Quarter End | Last 5 days of quarter - window dressing |
| US Bank Holiday | Federal Reserve holidays |
| FOMC Meeting Day | Fed meeting days (daily precision) |
| FOMC Meeting Hour | Fed meeting days (hourly precision) |
| FOMC Meeting Week | Fed meeting weeks |

#### Moon Phases
| Phase | Description |
|-------|-------------|
| Full Moon Day | ~1 day around full moon |
| Full Moon Hour | ~1 hour precision around full moon |
| New Moon Day | ~1 day around new moon |
| New Moon Hour | ~1 hour precision around new moon |

#### Funding Rate Phases
| Phase | Condition | Description |
|-------|-----------|-------------|
| High Funding | > 0.05% | Overleveraged longs |
| Negative Funding | < 0% | Shorts paying longs |
| Extreme Funding | > 0.1% or < -0.05% | Extreme positioning |
| Neutral Funding | -0.01% to 0.02% | Balanced market |

### Phase Usage Examples

**Trend Following Strategy:**
- Required: Uptrend, Weekdays
- Excluded: FOMC Meeting Day, US Bank Holiday

**Mean Reversion Strategy:**
- Required: Ranging, US Market Hours
- Excluded: Weekend

---

## Hoop Patterns

Hoop patterns detect chart formations by defining a sequence of price checkpoints ("hoops") that price must pass through.

### How Hoops Work
1. Each hoop defines a target price zone relative to an anchor point
2. Price must reach each hoop in sequence
3. Each hoop has timing constraints (distance in bars, tolerance)
4. When all hoops are completed, the pattern matches

### Hoop Settings

| Setting | Description |
|---------|-------------|
| **Name** | Label for this checkpoint |
| **Min Price %** | Minimum price level relative to anchor |
| **Max Price %** | Maximum price level relative to anchor |
| **Distance** | Expected bars from previous hoop |
| **Tolerance** | Allowed deviation from expected distance |
| **Anchor Mode** | How the next hoop's anchor is determined |

### Anchor Modes

| Mode | Description |
|------|-------------|
| **Actual Hit** | Next anchor is where price actually touched this hoop |
| **Target Price** | Next anchor is the hoop's target price |
| **Previous Anchor** | Keep using the same anchor |

### Pattern Settings

| Setting | Description |
|---------|-------------|
| **Cooldown Bars** | Minimum bars after match before pattern can match again |
| **Allow Overlap** | Whether new pattern can start while previous is in cooldown |

### Combine Modes

| Mode | Description |
|------|-------------|
| **DSL Only** | Use only DSL condition, ignore hoops |
| **Hoop Only** | Use only hoop pattern, ignore DSL |
| **AND** | Both DSL and hoop pattern must trigger |
| **OR** | Either DSL or hoop pattern can trigger |

### Example: Double Bottom Pattern
1. **First Low**: Price drops 1-3% from start
2. **Middle Peak**: Price bounces 1-4% from first low
3. **Second Low**: Price drops back to within 0.5% of first low
4. **Breakout**: Price rises 2%+ from second low (entry signal)

---

## Orderflow Settings

Orderflow indicators provide insight into buying and selling pressure.

### Modes

| Mode | Indicators Available | Data Required |
|------|---------------------|---------------|
| **Disabled** | None | - |
| **Tier 1** | VWAP, POC, VAH, VAL | OHLCV only |
| **Full** | All Tier 1 + Delta, Cumulative Delta, Whale tracking | Aggregated trades |

### Tier 1 Indicators (Fast)
- **VWAP**: Volume-Weighted Average Price for the session
- **POC**: Point of Control - price level with highest volume
- **VAH/VAL**: Value Area High/Low - range containing X% of volume
- **PREV_DAY_POC/VAH/VAL**: Previous day's levels
- **TODAY_POC/VAH/VAL**: Current day's developing levels

### Full Mode Indicators (Require aggTrades)
- **DELTA**: Buy volume minus sell volume per bar
- **CUM_DELTA**: Running total of delta from session start
- **WHALE_DELTA**: Delta from trades above threshold only
- **WHALE_BUY_VOL/SELL_VOL**: Large trade volumes
- **LARGE_TRADE_COUNT**: Number of trades above threshold

---

## DSL Reference

### Price Data
`open`, `high`, `low`, `close` (or `price`), `volume`

### Indicators
- Moving Averages: `SMA(period)`, `EMA(period)`
- Oscillators: `RSI(period)`, `STOCHASTIC(k,d).k/.d`
- MACD: `MACD(fast, slow, signal).line/.signal/.histogram`
- Volatility: `ATR(period)`, `BBANDS(period, stdDev).upper/.middle/.lower/.width`
- Trend: `ADX(period)`, `PLUS_DI(period)`, `MINUS_DI(period)`, `SUPERTREND(period,mult).trend/.upper/.lower`
- Ichimoku: `ICHIMOKU().tenkan/.kijun/.senkou_a/.senkou_b/.chikou`
- Range: `HIGH_OF(period)`, `LOW_OF(period)`, `AVG_VOLUME(period)`, `RANGE_POSITION(period,skip)`
- Aggregate: `LOWEST(expr,n)`, `HIGHEST(expr,n)`, `PERCENTILE(expr,n)`

### Math Functions
- `abs(expr)` - Absolute value
- `min(expr1, expr2)` - Minimum of two values
- `max(expr1, expr2)` - Maximum of two values

### Candlestick Patterns
Return 1 if pattern detected, 0 otherwise:
- `HAMMER(ratio)` - Long lower wick (default ratio: 2.0)
- `SHOOTING_STAR(ratio)` - Long upper wick (default ratio: 2.0)
- `DOJI(ratio)` - Tiny body relative to range (default ratio: 0.1)

### Candlestick Properties
Support `[n]` lookback syntax:
- `BODY_SIZE` - Absolute body size
- `BODY_RATIO` - Body / total range (0-1)
- `IS_BULLISH` - 1 if green candle
- `IS_BEARISH` - 1 if red candle

Example: `SHOOTING_STAR AND BODY_SIZE[1] > ATR(14) * 1.5 AND IS_BULLISH[1] == 1`

### Time & Calendar Functions
- Time: `HOUR` (0-23), `DAYOFWEEK` (1=Mon), `DAY`, `MONTH`
- Calendar: `IS_US_HOLIDAY`, `IS_FOMC_MEETING`, `MOON_PHASE`

### Market Data Functions
- Funding: `FUNDING`, `FUNDING_8H`
- Premium: `PREMIUM`, `PREMIUM_AVG(n)`
- Open Interest: `OI`, `OI_CHANGE`, `OI_DELTA(n)`

### OHLCV Volume (instant)
`QUOTE_VOLUME`, `BUY_VOLUME`, `SELL_VOLUME`, `OHLCV_DELTA`, `OHLCV_CVD`, `BUY_RATIO`, `TRADE_COUNT`

### Orderflow (requires orderflowSettings.mode)
- Volume Profile: `VWAP`, `POC(n)`, `VAH(n)`, `VAL(n)`
- Session Levels: `PREV_DAY_POC/VAH/VAL`, `TODAY_POC/VAH/VAL`
- Delta: `DELTA`, `CUM_DELTA`
- Whales: `WHALE_DELTA(threshold)`, `WHALE_BUY_VOL(t)`, `WHALE_SELL_VOL(t)`, `LARGE_TRADE_COUNT(t)`

### Rotating Ray Functions (auto-detect trendlines)
- Resistance: `RESISTANCE_RAY_BROKEN/CROSSED/DISTANCE(ray,look,skip)`, `RESISTANCE_RAYS_BROKEN(look,skip)`
- Support: Same pattern with `SUPPORT_` prefix

### Operators
- Comparison: `>`, `<`, `>=`, `<=`, `==`
- Cross: `crosses_above`, `crosses_below`
- Logical: `AND`, `OR`
- Arithmetic: `+`, `-`, `*`, `/`
- Lookback: `expr[n]` - value n bars ago

---

## Position Sizing

| Type | Description |
|------|-------------|
| **Fixed %** | Use X% of current equity per trade |
| **Fixed $** | Use fixed dollar amount per trade |
| **Risk %** | Size based on stop-loss distance to risk X% |
| **Kelly** | Kelly Criterion - optimal sizing based on win rate |
| **Volatility** | ATR-based sizing for consistent risk |

---

## Backtest Settings

| Setting | Description |
|---------|-------------|
| **Symbol** | Trading pair (e.g., BTCUSDT) |
| **Timeframe** | Candle period (15s, 1m, 5m, 15m, 1h, 4h, 1d) |
| **Duration** | Backtest period (e.g., "3 days", "6 months", "1 year") |
| **Initial Capital** | Starting account balance |
| **Fee %** | Trading fee per side (e.g., 0.1% for maker) |
| **Slippage %** | Expected slippage per trade |

---

## Tips for Strategy Development

1. **Start simple** - Begin with one entry condition, add complexity gradually
2. **Use phases** - Filter out unfavorable market conditions
3. **Protect profits** - Use multiple exit zones to tighten stops as profit grows
4. **Avoid over-optimization** - Strategies that work on historical data may fail in live trading
5. **Consider fees** - High-frequency strategies need to overcome transaction costs
6. **Check rejected trades** - Too many rejections may indicate position sizing issues
7. **Test different timeframes** - Same logic may perform differently on 1h vs 4h
8. **Use pending orders** - Limit/Stop entries can improve fill prices
9. **Watch drawdown** - Maximum drawdown indicates worst-case scenario
10. **Diversify conditions** - Combine trend, momentum, and volume indicators
