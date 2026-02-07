## Strategy DSL Reference

### Indicators

| Indicator | Syntax | Description | Properties |
|-----------|--------|-------------|------------|
| SMA | `SMA(period)` | Simple Moving Average | |
| EMA | `EMA(period)` | Exponential Moving Average | |
| RSI | `RSI(period)` | Relative Strength Index (0-100) | |
| ATR | `ATR(period)` | Average True Range | |
| ADX | `ADX(period)` | Average Directional Index (0-100, trend strength) | |
| +DI | `PLUS_DI(period)` | Plus Directional Indicator (upward pressure) | |
| -DI | `MINUS_DI(period)` | Minus Directional Indicator (downward pressure) | |
| MACD | `MACD(fast,slow,signal)` | MACD | `.line` `.signal` `.histogram` |
| Bollinger | `BBANDS(period,stdDev)` | Bollinger Bands | `.upper` `.middle` `.lower` |
| Stochastic | `STOCHASTIC(kPeriod)` or `STOCHASTIC(kPeriod,dPeriod)` | Stochastic Oscillator (0-100) | `.k` `.d` |
| Supertrend | `SUPERTREND(period,mult)` | Supertrend trend indicator | `.trend` `.upper` `.lower` |
| Ichimoku | `ICHIMOKU()` | Ichimoku Cloud | `.tenkan` `.kijun` `.senkou_a` `.senkou_b` `.chikou` |

> **Example:**
> `MACD(12,26,9).histogram > 0`
> `close < BBANDS(20,2).lower`
> `ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)`
> `STOCHASTIC(14).k crosses_above STOCHASTIC(14).d`

### Price References

| Function | Description |
|----------|-------------|
| `price` or `close` | Closing price |
| `open` | Opening price |
| `high` | High price |
| `low` | Low price |
| `volume` | Volume |

### Range & Volume Functions

| Function | Description |
|----------|-------------|
| `HIGH_OF(period)` | Highest high over period |
| `LOW_OF(period)` | Lowest low over period |
| `AVG_VOLUME(period)` | Average volume over period |
| `RANGE_POSITION(period)` | Position in range: -1=low, 0=mid, +1=high (extends beyond for breakouts) |
| `RANGE_POSITION(period,skip)` | Same as above, but skip last N bars before calculating range |

> **Example:**
> `close > HIGH_OF(20)`
> `volume > AVG_VOLUME(20) * 1.5`
> `RANGE_POSITION(20) > 1`
> `RANGE_POSITION(50,5) < -1`

> **Note:** RANGE_POSITION extends beyond +/-1 for breakouts (e.g., 1.5 if 50% above range). Use STOCHASTIC (clamped 0-100) for traditional oscillator.

### Aggregate Functions

| Function | Description |
|----------|-------------|
| `LOWEST(expr, period)` | Lowest value of expression over N bars |
| `HIGHEST(expr, period)` | Highest value of expression over N bars |
| `PERCENTILE(expr, period)` | Percentile rank of current value |

> **Example:**
> `BBANDS(20,2).width < LOWEST(BBANDS(20,2).width, 20) * 1.15`
> `RSI(14) < LOWEST(RSI(14), 50) * 1.1`

### Orderflow Functions

Enable Orderflow Mode in strategy settings. Tier 1 = instant, Tier 2 = requires sync.

| Function | Tier | Description |
|----------|------|-------------|
| `VWAP` | 1 | Volume Weighted Average Price (session) |
| `POC(period)` | 1 | Point of Control - price level with most volume (default: 20) |
| `VAH(period)` | 1 | Value Area High - top of 70% volume zone (default: 20) |
| `VAL(period)` | 1 | Value Area Low - bottom of 70% volume zone (default: 20) |
| `DELTA` | 2 | Bar delta (buy volume - sell volume) |
| `CUM_DELTA` | 2 | Cumulative delta from session start |
| `WHALE_DELTA(threshold)` | 2 | Delta from trades > $threshold only |
| `WHALE_BUY_VOL(threshold)` | 2 | Buy volume from trades > $threshold |
| `WHALE_SELL_VOL(threshold)` | 2 | Sell volume from trades > $threshold |
| `LARGE_TRADE_COUNT(threshold)` | 2 | Number of trades > $threshold in bar |

> **Example:**
> `close > VWAP`
> `WHALE_DELTA(50000) > 0`
> `LARGE_TRADE_COUNT(100000) > 5`

### OHLCV Volume Functions

Extended volume data from Binance klines. Available instantly - no aggTrades download needed!

| Function | Description |
|----------|-------------|
| `QUOTE_VOLUME` | Volume in quote currency (USD for BTCUSDT) |
| `BUY_VOLUME` | Taker buy volume - aggressive buyers |
| `SELL_VOLUME` | Taker sell volume - aggressive sellers |
| `OHLCV_DELTA` | Buy volume - sell volume (basic delta from OHLCV) |
| `OHLCV_CVD` | Cumulative delta from OHLCV data |
| `BUY_RATIO` | Buy volume / total volume (0-1, where 0.5 = balanced) |
| `TRADE_COUNT` | Number of trades in the bar |

> **Example:**
> `BUY_RATIO > 0.6`
> `OHLCV_DELTA > 0 AND close > SMA(20)`
> `OHLCV_CVD > OHLCV_CVD[1]`

> **Note:** These are less granular than aggTrades-based DELTA/CUM_DELTA but available instantly!

### Daily Session Volume Profile

Key support/resistance levels from daily sessions (UTC day boundary).

| Function | Description |
|----------|-------------|
| `PREV_DAY_POC` | Previous day's Point of Control |
| `PREV_DAY_VAH` | Previous day's Value Area High |
| `PREV_DAY_VAL` | Previous day's Value Area Low |
| `TODAY_POC` | Current day's developing POC (updates each bar) |
| `TODAY_VAH` | Current day's developing VAH (updates each bar) |
| `TODAY_VAL` | Current day's developing VAL (updates each bar) |

> **Example:**
> `close crosses_above PREV_DAY_POC`
> `close > PREV_DAY_VAH AND TODAY_POC > PREV_DAY_POC`
> `close < TODAY_VAL AND close > PREV_DAY_VAL`

### Funding Rate Functions

Requires funding data (auto-fetched from Binance Futures).

| Function | Description |
|----------|-------------|
| `FUNDING` | Current funding rate (%, e.g., 0.01 = 0.01%) |
| `FUNDING_8H` | 8-hour rolling average funding rate |

> **Example:**
> `FUNDING > 0.05`
> `FUNDING < 0 AND WHALE_DELTA(50000) > 0`

> **Note:** Positive funding = longs pay shorts (overleveraged longs). Negative = shorts pay longs.

### Premium Functions

Futures vs spot price spread.

| Function | Description |
|----------|-------------|
| `PREMIUM` | Current futures premium over spot (%) |
| `PREMIUM_AVG(period)` | Average premium over N bars |

### Open Interest Functions

Requires OI data (auto-fetched from Binance Futures, 5m resolution).

| Function | Description |
|----------|-------------|
| `OI` | Current open interest value (in billions USD) |
| `OI_CHANGE` | OI change from previous bar |
| `OI_DELTA(period)` | OI change over N bars |

> **Example:**
> `OI_CHANGE > 0 AND close > close[1]`
> `OI_DELTA(12) < 0 AND close > SMA(20)`

> **Note:** Rising OI + rising price = new longs. Falling OI + rising price = short covering.

### Time Functions

| Function | Description |
|----------|-------------|
| `DAYOFWEEK` | Day of week (1=Mon, 2=Tue, ..., 7=Sun) |
| `HOUR` | Hour of day (0-23, UTC) |
| `DAY` | Day of month (1-31) |
| `MONTH` | Month of year (1-12) |

> **Example:**
> `DAYOFWEEK == 1`
> `HOUR >= 8 AND HOUR <= 16`

### Moon Functions

| Function | Description |
|----------|-------------|
| `MOON_PHASE` | Moon phase (0.0=new, 0.5=full, 1.0=new) |

> **Example:**
> `MOON_PHASE >= 0.48 AND MOON_PHASE <= 0.52`
> `MOON_PHASE <= 0.02 OR MOON_PHASE >= 0.98`

### Calendar Functions

| Function | Description |
|----------|-------------|
| `IS_US_HOLIDAY` | US Federal Reserve bank holiday (1=yes, 0=no) |
| `IS_FOMC_MEETING` | FOMC meeting day (1=yes, 0=no) |

> **Example:**
> `IS_US_HOLIDAY == 1`
> `IS_FOMC_MEETING == 1`

> **Note:** Holidays: New Year's, MLK Day, Presidents Day, Memorial Day, Juneteenth, July 4th, Labor Day, Columbus Day, Veterans Day, Thanksgiving, Christmas. FOMC: 8 meetings/year (2024-2026 schedule built-in).

### Footprint Functions

Requires aggTrades sync. Footprint shows buy/sell volume distribution at price levels within each candle.

| Function | Description |
|----------|-------------|
| `IMBALANCE_AT_POC` | Buy/sell ratio at POC (>1=buy dominant) |
| `IMBALANCE_AT_VAH` | Buy/sell ratio at Value Area High |
| `IMBALANCE_AT_VAL` | Buy/sell ratio at Value Area Low |
| `STACKED_BUY_IMBALANCES(n)` | 1 if >= n consecutive buy imbalances |
| `STACKED_SELL_IMBALANCES(n)` | 1 if >= n consecutive sell imbalances |
| `ABSORPTION(vol,move)` | 1 if high volume + small price movement |
| `HIGH_VOLUME_NODE_COUNT(t)` | Number of price levels with volume > threshold |
| `VOLUME_ABOVE_POC_RATIO` | Ratio of volume above POC (0-1) |
| `VOLUME_BELOW_POC_RATIO` | Ratio of volume below POC (0-1) |
| `FOOTPRINT_DELTA` | Total delta from bucket aggregation |
| `FOOTPRINT_POC` | POC price from footprint |

> **Example:**
> `STACKED_BUY_IMBALANCES(4) == 1 AND price < FOOTPRINT_POC`
> `ABSORPTION(100000, 0.3) == 1 AND VOLUME_ABOVE_POC_RATIO < 0.3`
> `IMBALANCE_AT_POC > 3`

> **Note:** Stacked imbalances indicate aggressive buying/selling at multiple price levels. Absorption suggests large orders absorbing price movement.

### Cross-Exchange Functions

Analyze and compare orderflow across multiple exchanges. Requires multi-exchange data sync.

| Function | Description |
|----------|-------------|
| `BINANCE_DELTA` | Delta from Binance trades only |
| `BYBIT_DELTA` | Delta from Bybit trades only |
| `OKX_DELTA` | Delta from OKX trades only |
| `COMBINED_DELTA` | Sum of delta across all enabled exchanges |
| `EXCHANGE_DELTA_SPREAD` | Max exchange delta - min exchange delta |
| `EXCHANGE_DIVERGENCE` | 1 if exchanges disagree on direction |
| `COMBINED_IMBALANCE_AT_POC` | Aggregated imbalance at combined POC |
| `EXCHANGES_WITH_BUY_IMBALANCE` | Count of exchanges showing buy imbalance |
| `EXCHANGES_WITH_SELL_IMBALANCE` | Count of exchanges showing sell imbalance |
| `WHALE_DELTA_COMBINED(t)` | Whale delta across all exchanges |
| `DOMINANT_EXCHANGE` | Exchange with largest absolute delta |

> **Example:**
> `BINANCE_DELTA < -10000 AND BYBIT_DELTA > 10000`
> `EXCHANGE_DIVERGENCE == 1 AND ADX(14) < 20`
> `EXCHANGES_WITH_BUY_IMBALANCE >= 2 AND RSI(14) < 40`

> **Note:** Exchange divergence (one exchange buying, another selling) can signal uncertainty or arbitrage opportunities. Convergence across exchanges strengthens signals.

### Spot vs Futures Functions

Compare orderflow between spot and futures markets. Requires aggTrades from both market types.

| Function | Description |
|----------|-------------|
| `SPOT_DELTA` | Delta from spot market trades only |
| `FUTURES_DELTA` | Delta from futures market trades (perp + dated) |
| `SPOT_VOLUME` | Volume from spot market trades |
| `FUTURES_VOLUME` | Volume from futures market trades |
| `SPOT_FUTURES_DIVERGENCE` | 1 if spot and futures delta have opposite signs |
| `SPOT_FUTURES_DELTA_SPREAD` | SPOT_DELTA - FUTURES_DELTA (positive = spot leading) |

> **Example:**
> `SPOT_FUTURES_DIVERGENCE == 1 AND RSI(14) < 40`
> `SPOT_FUTURES_DELTA_SPREAD > 10000`
> `SPOT_DELTA > 0 AND FUTURES_DELTA < 0`

> **Note:** Spot-futures divergence can signal smart money positioning. When spot buyers diverge from futures sellers, it may indicate accumulation. Returns NaN if market type data is unavailable.

### Rotating Ray Functions

Auto-detect trendlines from ATH/ATL. Parameters: `rayNum`, `lookback`, `skip`.

**Resistance rays:**

| Function | Description |
|----------|-------------|
| `RESISTANCE_RAY_BROKEN(ray,look,skip)` | 1 if resistance ray was broken |
| `RESISTANCE_RAY_CROSSED(ray,look,skip)` | 1 if price crossed resistance ray |
| `RESISTANCE_RAY_DISTANCE(ray,look,skip)` | Distance to resistance ray (%) |
| `RESISTANCE_RAYS_BROKEN(look,skip)` | Count of broken resistance rays |
| `RESISTANCE_RAY_COUNT(look,skip)` | Total resistance rays detected |

**Support rays:** Same pattern with `SUPPORT_` prefix.

> **Example:**
> `RESISTANCE_RAY_CROSSED(1, 200, 5) == 1`

### Comparison Operators

| Operator | Description |
|----------|-------------|
| `>` | Greater than |
| `<` | Less than |
| `>=` | Greater than or equal |
| `<=` | Less than or equal |
| `==` | Equal to |

### Cross Operators

| Operator | Description |
|----------|-------------|
| `crosses_above` | Value crosses above another |
| `crosses_below` | Value crosses below another |

> **Example:**
> `EMA(9) crosses_above EMA(21)`
> `RSI(14) crosses_below 30`

### Logical Operators

| Operator | Description |
|----------|-------------|
| `AND` | Both conditions must be true |
| `OR` | Either condition must be true |

> **Example:**
> `RSI(14) < 30 AND price > SMA(200)`
> `EMA(9) > EMA(21) OR MACD(12,26,9).histogram > 0`

### Arithmetic Operators

| Operator | Description |
|----------|-------------|
| `+` `-` `*` `/` | Add, subtract, multiply, divide |

> **Example:**
> `volume > AVG_VOLUME(20) * 1.2`
> `close > SMA(20) + ATR(14) * 2`

### Lookback Syntax

Use `[n]` to reference values from N bars ago:

> **Example:**
> `RSI(14)[1]` - RSI value one bar ago
> `ATR(14) > ATR(14)[1]` - Volatility expanding
> `close > close[5]` - Price higher than 5 bars ago

### Math Functions

| Function | Description |
|----------|-------------|
| `abs(expr)` | Absolute value |
| `min(expr1, expr2)` | Minimum of two values |
| `max(expr1, expr2)` | Maximum of two values |

> **Example:**
> `abs(close - open) > ATR(14) * 0.5`
> `min(open, close) - low > 2 * abs(close - open)`
> `max(RSI(14), RSI(14)[1]) < 30`

> **Note:** Useful for candlestick patterns: body = abs(close - open), lower_wick = min(open, close) - low

### Candlestick Patterns

Pattern functions return 1 if detected, 0 otherwise. Property functions support lookback `[n]`.

| Function | Description |
|----------|-------------|
| `HAMMER(ratio)` | Hammer pattern - long lower wick (default ratio: 2.0) |
| `SHOOTING_STAR(ratio)` | Shooting star - long upper wick (default ratio: 2.0) |
| `DOJI(ratio)` | Doji - tiny body relative to range (default ratio: 0.1) |
| `BODY_SIZE` | Absolute body size (close - open) |
| `BODY_RATIO` | Body / total range (0-1) |
| `IS_BULLISH` | 1 if green candle (close > open) |
| `IS_BEARISH` | 1 if red candle (close < open) |

> **Example:**
> `SHOOTING_STAR(2.0) AND BODY_SIZE[1] > ATR(14) * 1.5 AND IS_BULLISH[1] == 1`
> `HAMMER AND RSI(14) < 30`
> `DOJI AND BODY_SIZE[1] > ATR(14)`

> **Note:** Combine patterns with context: shooting star after strong bullish candle = reversal signal

### Example Strategies

> **Example:**
> **RSI Oversold:** `RSI(14) < 30`
>
> **Trend Following:** `EMA(9) crosses_above EMA(21) AND price > SMA(50)`
>
> **Bollinger Bounce:** `close < BBANDS(20,2).lower AND RSI(14) < 35`
>
> **Breakout:** `close > HIGH_OF(20) AND volume > AVG_VOLUME(20) * 1.5`
