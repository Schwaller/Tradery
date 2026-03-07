## Markets & Price

### What Is a Market?

A market is wherever buyers and sellers meet to exchange an asset at an agreed price. In crypto, this happens on exchanges like Binance, where an order book matches bids (buy orders) and asks (sell orders). The current price is simply the last price at which a trade occurred.

Every price movement reflects a shift in supply and demand. When more buyers are aggressive (hitting the ask), price rises. When sellers dominate (hitting the bid), price falls. Understanding this auction process is the foundation of all trading.

### OHLCV Candles

Price data is organized into **candles** (also called bars), each representing a fixed time period. Every candle captures five values:

- **Open** — the first trade price in the period
- **High** — the highest price reached
- **Low** — the lowest price reached
- **Close** — the last trade price in the period
- **Volume** — total amount traded during the period

![Candlestick Anatomy](images/candlestick-anatomy.svg)

A **bullish** candle (green) closes higher than it opens — buyers won the period. A **bearish** candle (red) closes lower than it opens — sellers won. The thin lines above and below the body are **wicks** (or shadows), showing the range that was rejected.

The wicks tell a story. A long lower wick means sellers tried to push price down but got overwhelmed by buyers. A long upper wick means buyers tried to push up but got rejected. Learning to read what the wicks are telling you is one of the most valuable skills in chart reading.

### Timeframes

The same market can be viewed at different timeframes. A 1-hour chart shows one candle per hour; a daily chart shows one per day. Lower timeframes (1m, 5m, 15m) reveal short-term noise. Higher timeframes (4h, 1d, 1w) show the bigger trend.

There is no "best" timeframe — it depends on your trading style. A scalper lives on the 1-minute chart and wouldn't dream of looking at the daily. A swing trader might check the daily once in the morning and not look again until evening. The timeframe you choose shapes everything: your stress level, time commitment, and what kind of edge you're looking for.

### Long and Short

**Going long** means buying, expecting price to rise. You profit from upward movement. **Going short** means selling (or borrowing and selling), expecting price to fall. You profit from downward movement.

In crypto futures, both directions are equally accessible — you can short as easily as you can go long. This is a fundamental difference from traditional stock markets, where shorting requires borrowing shares and comes with restrictions. In crypto, bears can be just as aggressive as bulls, which is why crypto markets can move violently in both directions.


## Reading Charts

### Candlestick Patterns

Individual candles and short sequences form recognizable patterns that suggest what might happen next:

- **Hammer** — small body at the top with a long lower wick. Appears in downtrends and suggests buyers are stepping in. The long wick shows sellers pushed price down but buyers drove it back up. It's the market saying "we tried going lower and nobody wanted to sell at those prices."
- **Shooting Star** — small body at the bottom with a long upper wick. Appears in uptrends and suggests sellers are pushing back. The mirror image of a hammer. The market tried going higher and got slapped down.
- **Doji** — tiny body where open and close are nearly equal. Neither side won the period. Often signals indecision before a reversal. Think of it as the market taking a breath before deciding direction.
- **Engulfing** — a candle whose body completely covers the previous candle's body. A bullish engulfing (green engulfing red) is a strong reversal signal — it says "the buyers just showed up and completely overwhelmed yesterday's sellers."

![Candlestick Patterns](images/candlestick-patterns.svg)

> **Tip:** No single candle pattern is reliable on its own. A hammer at a major support level after a long downtrend is a completely different signal than a hammer in the middle of nowhere. Context is everything.

### Support and Resistance

**Support** is a price level where buying pressure historically prevents further decline. **Resistance** is where selling pressure prevents further advance. These levels form because traders remember significant prices and place orders around them.

![Support and Resistance](images/support-resistance.svg)

When price breaks through resistance, that level often becomes new support (and vice versa). This "polarity flip" is one of the most reliable patterns in technical analysis. Why? Because everyone who sold at resistance is now underwater and likely to buy if price comes back to that level to "break even."

Key support/resistance sources:
- Previous highs and lows
- Round numbers (e.g., $50,000 for BTC — humans love round numbers)
- Volume profile levels (POC, VAH, VAL)
- Moving averages (especially the 200-day SMA, which institutional traders watch)
- Trendlines

### Trendlines

A trendline connects two or more swing lows (uptrend) or swing highs (downtrend). The more times price respects the line, the more significant it becomes. A break of a well-established trendline often signals a trend change.

Draw trendlines using wicks (not just bodies) for accuracy, and remember: a trendline needs at least two touch points to be valid and three to be confirmed.


## Trading Styles

Not all traders are the same, and that's by design. Different personalities, risk tolerances, and lifestyles lead to fundamentally different approaches. Understanding trading styles helps you find what fits you — and recognize what doesn't.

![Trading Styles](images/trading-styles.svg)

### The Scalper

Scalpers are the sprinters of trading. They operate on 1-minute to 5-minute charts, making dozens of trades per day, each targeting tiny moves — often just a few ticks. A scalper might hold a position for 30 seconds.

**What they watch:** Orderflow, footprint charts, delta, bid/ask imbalances. They care about *who is buying and selling right now*, not what RSI says. The order book is their primary tool.

**Personality fit:** High focus, fast reflexes, comfortable with rapid decision-making. Scalpers need to be emotionally flat — there's no time to agonize over a loss because the next trade is in 10 seconds.

**Edge:** Scalpers exploit microstructure — brief imbalances between aggressive buyers and sellers that vanish in seconds. Their edge is speed and reading the tape, not predicting where price goes in a week.

**In Strategy Forge:** Scalping strategies use short timeframes (1m, 5m) with orderflow functions like `DELTA`, `IMBALANCE_AT_POC`, and `STACKED_BUY_IMBALANCES(4)`. Tight stops (0.5-1%), small targets (0.3-0.8%), and `maxOpenTrades: 1`.

### The Day Trader

Day traders work on 15-minute to 1-hour charts. They take a handful of trades each day, riding moves that last minutes to hours. The defining rule: no positions held overnight. Everything is closed before the end of their session.

**What they watch:** Support and resistance levels, volume profile (especially the previous day's POC/VAH/VAL), RSI, MACD. Day traders plan their levels before the session and then execute when price arrives.

**Personality fit:** Patient but engaged. Day traders spend their morning identifying setups and then wait — sometimes for hours — for price to reach their levels. The discipline to not trade when there's no setup separates profitable day traders from gambling ones.

**Edge:** Day traders exploit intraday patterns — the open drive, the midday lull, the US session surge. They know that 2pm UTC is a different market than 8am UTC, and they position accordingly.

**In Strategy Forge:** Day trading strategies often use session phases (`us-market-hours`, `session-overlap`) as filters. Conditions combine technical levels with momentum: `close crosses_above PREV_DAY_POC AND RSI(14) > 50`. Exit zones target the session's range.

### The Swing Trader

Swing traders work on 4-hour to daily charts. They hold positions for days to weeks, riding the "swings" between support and resistance. They might check their charts twice a day — morning and evening.

**What they watch:** Moving averages, ADX for trend strength, Bollinger Bands, market phases. Swing traders care about the trend and where they are within it. They want to buy the dips in an uptrend and sell the rallies in a downtrend.

**Personality fit:** Patient and detached. Swing traders need to stomach seeing their position go red for a day or two before the thesis plays out. They can't be glued to the screen — that leads to emotional decisions and cutting winners too early.

**Edge:** Swing traders capture multi-day trends that intraday noise obscures. A move that looks chaotic on the 15-minute chart often shows a clean setup on the daily. They profit from the fact that most retail traders are too impatient to hold through normal pullbacks.

**In Strategy Forge:** Swing strategies use higher timeframes (4h, 1d) with trend filters: `ADX(14) > 25 AND price > SMA(200)`. They use trailing stops to let winners run and required phases like `uptrend` or `golden-cross` to stay with the trend. Position sizing is smaller because stops are wider.

### The Position Trader

Position traders operate on daily to weekly charts. They make 1-3 trades per month, holding for weeks to months. They're the closest thing to investors who still actively time entries and exits.

**What they watch:** The big picture — SMA(200) for long-term trend, Fear & Greed index for sentiment extremes, funding rates for leverage positioning, macro news. A position trader might enter because "funding is at extreme levels while Fear & Greed is below 20" — conditions that play out over weeks, not minutes.

**Personality fit:** Extremely patient, high conviction. Position traders need to hold through multiple sessions of their position going against them. They also need the discipline to wait months for the right setup rather than forcing trades.

**Edge:** Position traders exploit sentiment extremes and macro cycles that shorter-term traders ignore. When everyone is panicking (extreme fear), position traders are building positions. When everyone is euphoric, they're taking profits.

**In Strategy Forge:** Position strategies use daily or weekly timeframes with sentiment filters: `FEAR_GREED < 25 AND RSI(14) < 35 AND price > SMA(200)`. Wide stops (10-15%), large targets (20-50%), and phases like `extreme-fear` or `negative-funding`.

### Finding Your Style

Most beginners start with day trading because it feels active and engaging. Many eventually migrate to swing trading when they realize that watching charts 8 hours a day leads to overtrading and exhaustion. There's no shame in being a swing trader who checks charts twice a day — many of the most profitable independent traders operate exactly this way.

> **Tip:** Your trading style should match your personality and lifestyle. If you have a full-time job, trying to scalp during work hours is a recipe for losses. Swing trading with alerts and daily chart reviews might be your edge. Strategy Forge lets you backtest across all timeframes, so you can objectively see which holding period works best for your ideas.


## Technical Indicators

Indicators are mathematical calculations applied to price and volume data. They help quantify what the chart is showing and remove some subjectivity from analysis. But remember: indicators are derived from price. They don't predict the future — they describe the present and recent past in different ways.

### Moving Averages

A **moving average** smooths price over a period, showing the average trend direction. Two main types:

- **SMA (Simple Moving Average)** — equal weight to all periods. `SMA(200)` is the average of the last 200 closes. It's slow to react, which is the point — it filters out noise and shows the true trend.
- **EMA (Exponential Moving Average)** — more weight on recent prices, reacts faster to changes. Better for shorter-term signals but more prone to false signals.

![Moving Averages](images/moving-averages.svg)

**Key signals:**
- Price above its moving average = bullish bias
- Price below = bearish bias
- **Golden Cross** — short MA (e.g., 50) crosses above long MA (e.g., 200). Bullish signal. Institutional traders pay attention to this on the daily chart.
- **Death Cross** — short MA crosses below long MA. Bearish signal.

Common periods: 20 (short-term, what day traders watch), 50 (medium, swing trader territory), 200 (the long-term trend line that the entire market respects).

> **Tip:** The 200-day SMA is arguably the most important line on any chart. In crypto bull markets, BTC almost always stays above it. A sustained break below often signals a regime change. Use `price > SMA(200)` as a trend filter in your strategies.

### RSI (Relative Strength Index)

RSI measures momentum on a scale of 0 to 100. It compares the magnitude of recent gains to recent losses.

![RSI Zones](images/rsi-zones.svg)

- **Above 70** — overbought. Price may be overextended to the upside.
- **Below 30** — oversold. Price may be overextended to the downside.
- **Divergence** — when price makes a new high but RSI makes a lower high, it suggests weakening momentum (bearish divergence). This is one of the most powerful signals in technical analysis — it means price is rising on fumes.

RSI works best in ranging markets. In strong trends, RSI can stay overbought or oversold for extended periods — a common trap for beginners who short every RSI reading above 70 in a bull market.

**How different traders use RSI:**
- **Mean reversion trader:** Buys when RSI drops below 30 in an uptrend. The "oversold bounce" is their bread and butter.
- **Momentum trader:** Buys when RSI crosses above 50 from below — momentum turning bullish. They don't care about overbought; in a trend, RSI above 50 is bullish.
- **Swing trader:** Uses RSI divergence to time exits. If their long position is profitable and RSI shows bearish divergence, they tighten stops.

### MACD (Moving Average Convergence Divergence)

MACD tracks the relationship between two EMAs (typically 12 and 26 period). It has three components:

- **MACD Line** — the difference between the two EMAs
- **Signal Line** — an EMA of the MACD line (typically 9 period)
- **Histogram** — the difference between MACD and Signal lines

![MACD Signals](images/macd-signals.svg)

**Key signals:**
- MACD crossing above Signal = bullish momentum
- MACD crossing below Signal = bearish momentum
- Histogram growing = momentum accelerating
- Histogram shrinking = momentum weakening (early warning, even before a cross)

MACD is a favorite of swing traders because it confirms trends without being too noisy. A MACD cross on the daily chart is a significant event. On the 5-minute chart, it happens so often it's nearly useless.

### Bollinger Bands

Bollinger Bands plot a moving average with bands at a set number of standard deviations above and below (typically 2). They adapt to volatility — bands widen in volatile markets and narrow in quiet ones.

![Bollinger Bands](images/bollinger-bands.svg)

- **Squeeze** — bands narrow significantly, indicating low volatility. Often precedes a breakout. Breakout traders live for the squeeze — they set alerts for when `BBANDS(20,2).width` hits a 50-period low and then wait for the explosive move.
- **Walk the band** — price staying near the upper or lower band in a strong trend. This is not a reversal signal — it means the trend is strong.
- **Mean reversion** — price returning to the middle band after touching an outer band. Range traders use this: buy at the lower band, sell at the upper band, but only when ADX confirms the market is actually ranging.

### ATR (Average True Range)

ATR measures volatility — how much price typically moves per period. It does not indicate direction, only the magnitude of movement.

This is one of the most underappreciated indicators. Most beginners ignore it; experienced traders consider it essential.

Uses:
- **Stop loss sizing** — set stops at a multiple of ATR (e.g., 2x ATR below entry). This means your stop automatically widens in volatile markets and tightens in calm ones — exactly what you want.
- **Position sizing** — smaller positions when ATR is high (volatile market). If ATR doubled, you should halve your position size to maintain the same dollar risk.
- **Trade filtering** — some traders only trade when ATR is above its average (volatility expanding = bigger moves = more profit potential).

### ADX (Average Directional Index)

ADX measures trend strength (not direction) on a scale of 0 to 100.

![ADX Zones](images/adx-zones.svg)

- **Below 20** — no trend (ranging market). Mean reversion strategies work best. Trend followers should sit on their hands.
- **20-40** — trending. Trend-following strategies work best. Mean reversion traders should be cautious.
- **Above 40** — strong trend. Stay with the trend, avoid counter-trend trades entirely.

The companion indicators +DI and -DI show direction: +DI > -DI means bullish trend, -DI > +DI means bearish.

> **Tip:** ADX is the ultimate "which strategy type should I use right now?" indicator. If you build two strategies — one trend-following, one mean reversion — use ADX phases to activate the right one for current conditions.


## Entry Strategies

### Trend Following

Trade in the direction of the established trend. Buy in uptrends, sell in downtrends. This is the most reliable strategy class — and the hardest psychologically, because you're always entering after the move has already started.

**The mindset:** A trend follower doesn't try to catch the bottom. They wait for confirmation that a trend exists and then ride it. They accept missing the first 20% of a move in exchange for catching the middle 60%.

**Example conditions:**
- `ADX(14) > 25 AND PLUS_DI(14) > MINUS_DI(14)` — confirmed uptrend
- `price > SMA(200) AND EMA(20) > EMA(50)` — multiple MA alignment (strong conviction)
- `SUPERTREND(10,3).trend == 1 AND RSI(14) > 40` — supertrend bullish with momentum support

> **Tip:** Trend following has a lower win rate (often 40-50%) but larger winners. A good trend-following strategy might lose 6 out of 10 trades and still be profitable because the 4 winners are each 3-5x larger than the losses. Use trailing stops to capture the full trend — cutting winners early is the biggest mistake trend followers make.

### Mean Reversion

Trade the return to the mean after an overextension. Buy when oversold, sell when overbought. Works best in ranging markets.

**The mindset:** A mean reversion trader is a contrarian at heart. When everyone is panicking and price drops sharply, they buy. When everyone is euphoric and price spikes, they sell. They believe that extreme moves are temporary and price always returns to "fair value."

**Example conditions:**
- `RSI(14) < 30 AND price > SMA(200)` — oversold but still in uptrend (buying a dip, not catching a falling knife)
- `price < BBANDS(20,2).lower AND ADX(14) < 20` — below lower band in a range
- `RANGE_POSITION(20) < -0.8` — near the bottom of the recent range

> **Tip:** Mean reversion has a higher win rate (60-70%) but smaller winners. Use fixed take-profit targets rather than trailing stops. The key danger is that what looks like a mean reversion opportunity might actually be the start of a new trend — which is why the `ADX(14) < 20` filter is so important. If the market is trending, you're not mean-reverting, you're catching a falling knife.

### Momentum

Enter when momentum is accelerating, catching the "meat" of a move. This is like trend following but with more emphasis on timing — you want to enter at the moment the trend kicks into higher gear.

**The mindset:** Momentum traders look for acceleration, not just direction. They want to see not just that price is going up, but that it's going up *faster*. MACD histograms growing, RSI crossing above 50, volume surging — these are their signals.

**Example conditions:**
- `MACD(12,26,9).line crosses_above MACD(12,26,9).signal AND ADX(14) > 20`
- `RSI(14) crosses_above 50 AND price > SMA(50)` — momentum turning bullish

### Breakout

Enter when price breaks through a significant level (support, resistance, or range). Breakout traders wait for the market to show its hand and then jump on the move.

**The mindset:** Breakout traders believe that when price breaks a level it's been respecting, something fundamental has changed. The energy that was being contained within a range now has direction. The key challenge is false breakouts — price breaks a level, triggers entries, and then reverses.

**Example conditions:**
- `close > HIGH_OF(20)` — breakout above 20-period high
- `BBANDS(20,2).width < LOWEST(BBANDS(20,2).width, 50) * 1.1` — volatility squeeze (precedes breakout)
- `RESISTANCE_RAY_CROSSED(1, 200, 5) == 1` — breaking through a resistance trendline

> **Tip:** The best breakouts come after extended periods of compression (low volatility). In Strategy Forge, the Bollinger Band width squeeze is one of the best breakout filters. Combine it with a volume surge (`volume > AVG_VOLUME(20) * 1.5`) to filter out false breakouts.


## Risk Management

Risk management is what separates surviving traders from bankrupt ones. No strategy, no matter how brilliant, is useful without proper risk controls. You will have losing streaks. The question is whether you survive them.

### Stop Losses

A **stop loss** is a predetermined exit point that limits your loss on a trade. Every trade must have one. Period. No exceptions. "I'll just watch it" is not a stop loss strategy.

![Stop Loss Types](images/stop-loss-types.svg)

- **Fixed stop** — set at a specific price level (e.g., below support). Doesn't move. Simple and clear. Mean reversion traders often use these because they have defined invalidation levels.
- **Trailing stop** — moves with price as the trade goes in your favor. Locks in profit while giving the trade room to run. The go-to for trend followers who want to let winners run.
- **ATR-based stop** — set at a multiple of ATR from entry. Adapts to current volatility. A 2x ATR stop on a volatile day is wider than on a calm day — exactly what you want.

> **Tip:** In Strategy Forge exit zones, use `stopLossType: trailing_percent` for trend-following strategies and `stopLossType: fixed_percent` for mean reversion. Many experienced traders use a fixed stop for the initial risk and switch to a trailing stop once the trade moves into profit.

### Take Profit

A **take profit** level is where you close a winning trade. Setting it too tight leaves money on the table; too wide and winners turn into losers.

Approaches:
- **Fixed target** — e.g., +5% from entry. Simple and backtestable. Works well for mean reversion.
- **Risk multiple** — e.g., 2x your stop loss distance. This guarantees your winners are larger than losers.
- **Technical level** — at the next resistance/support level. The most logical but harder to automate.
- **Trailing exit** — let the trade run with a trailing stop. Best for trend following but requires accepting that you'll give back some profit at the end.

### Risk-Reward Ratio

The **risk-reward ratio** (R:R) compares potential loss to potential gain. A 1:2 R:R means you risk $1 to make $2.

![Risk Reward](images/risk-reward.svg)

With a 1:2 R:R, you only need to win 34% of trades to break even. With 1:3 R:R, just 26%. This is why R:R matters more than win rate — and why experienced traders happily accept strategies with 40% win rates.

**Minimum standards:**
- Never take a trade below 1:1 R:R
- Aim for 1:2 or better for trend following
- 1:1 to 1:1.5 is acceptable for high win-rate mean reversion (70%+)

### Position Sizing

Position sizing determines how much capital to allocate per trade. The goal is to survive losing streaks — because they will come.

- **Fixed percentage** — risk a fixed % of equity per trade (e.g., 1-2%). This is the industry standard. If you have $10,000 and risk 2% per trade, you lose $200 per losing trade. After 10 straight losses (unlikely but possible), you've lost 20% — painful but survivable.
- **Volatility-adjusted** — smaller positions in volatile markets (using ATR). If BTC's ATR doubles, halve your position size. Your dollar risk stays constant.
- **Kelly criterion** — mathematically optimal sizing based on win rate and R:R. In practice, use quarter Kelly — full Kelly is too aggressive and one bad streak wipes you out.

> **Tip:** Professional traders typically risk 0.5-2% per trade. Beginners often risk 5-10%, which feels exciting on winners but is catastrophic during the inevitable losing streak. In Strategy Forge, `positionSizingType: PERCENT_EQUITY` with `positionSizingValue: 10` risks 10% of equity per trade, which is fine for backtesting. For real trading, 1-2% is the standard.


## Volume & Orderflow

Volume and orderflow are where you move from "what happened" (price) to "why it happened" (who is buying and selling). This is where scalpers and institutional traders spend most of their time.

### Volume Basics

Volume confirms price moves. A breakout on high volume is more likely to sustain than one on low volume. Think of volume as conviction — if price moves up on massive volume, a lot of traders believe in the move.

- **Rising price + rising volume** = strong uptrend. Buyers are committed.
- **Rising price + falling volume** = weakening uptrend (divergence). Price is drifting up, but fewer people are participating. Smart money may already be selling.
- **Spike in volume** = significant event, often at reversals or breakouts. Someone big just entered or exited.

### Volume Profile

Volume profile shows how much volume traded at each price level, revealing where the market spent the most time — and therefore where the "fair value" is.

![Volume Profile](images/volume-profile.svg)

- **POC (Point of Control)** — the price level with the highest traded volume. Acts as a magnet — price tends to return to it. Think of POC as the price where the market was most comfortable.
- **VAH (Value Area High)** — upper boundary of the value area (typically 70% of volume)
- **VAL (Value Area Low)** — lower boundary of the value area

**How different traders use volume profile:**
- **Day traders** use the previous day's POC, VAH, and VAL as key levels. `close crosses_above PREV_DAY_POC` is a classic day-trading entry — it means the market just reclaimed yesterday's fair value.
- **Swing traders** use the volume profile to identify high-volume nodes (strong support) and low-volume nodes (where price moves fast because there's no "agreement" at those levels).
- **Mean reversion traders** use POC as their target — if price deviates far from POC, it's likely to return.

### Delta and Orderflow

**Delta** is the difference between aggressive buying volume and aggressive selling volume at each price level. This is the raw signal of who is in control right now.

![Delta & Orderflow](images/delta-orderflow.svg)

- **Positive delta** — more aggressive buyers (hitting the ask). Buyers are willing to pay the higher price to get filled immediately.
- **Negative delta** — more aggressive sellers (hitting the bid). Sellers are dumping.
- **Cumulative delta (CVD)** — running total of delta over time

Delta divergences are powerful signals and a favorite of experienced traders: if price is making new highs but cumulative delta is making lower highs, the move is being driven by short covering or passive buying, not aggressive buying. It often reverses.

### Footprint Charts

Footprint charts show the buy/sell breakdown at each price level within a candle. They're the highest-resolution view of market activity, and they're what separates retail chart-reading from professional orderflow analysis.

- **Imbalances** — where one side overwhelmingly dominates (e.g., 3:1 buy:sell ratio at a price level). Stacked imbalances (several levels in a row) show strong directional conviction.
- **Absorption** — high volume with minimal price movement. This means a large player is absorbing the opposing flow — taking the other side of a massive amount of orders without letting price move. It's often a sign that a big player is accumulating or distributing.
- **Stacked imbalances** — multiple consecutive price levels all showing the same imbalance direction. This is one of the strongest orderflow signals — it means aggressive buying or selling is happening across multiple price levels simultaneously.


## Market Regimes

### Trending vs Ranging

Markets alternate between two states, and the best strategy depends on which state is active. Using a trend-following strategy in a ranging market is like bringing a surfboard to a swimming pool — wrong tool for the conditions.

![Market Phases](images/market-phases.svg)

**Trending market:**
- ADX > 25
- Price respects moving averages (bouncing off them during pullbacks)
- Higher highs and higher lows (uptrend) or lower highs and lower lows (downtrend)
- Best strategies: trend following, breakout, momentum

**Ranging market:**
- ADX < 20
- Price oscillates between support and resistance
- Moving averages are flat and intertwined
- Best strategies: mean reversion, range trading

> **Tip:** Most markets spend 60-70% of their time ranging and only 30-40% trending. This is why pure trend-following systems have lower win rates — they generate false signals during all that range time. Using phases in Strategy Forge to filter by market regime is one of the highest-impact optimizations you can make.

### Trading Sessions

Crypto trades 24/7, but volume and volatility cluster around traditional market hours. Different sessions have different personalities:

- **Asian session** (00:00-08:00 UTC) — the quiet hours. Lowest volatility, range-bound. Mean reversion strategies tend to work well here. Many breakouts during Asia reverse when Europe opens.
- **European session** (07:00-16:00 UTC) — moderate volatility. Often sets the direction for the day. London traders are influential in forex markets and increasingly in crypto.
- **US session** (13:00-21:00 UTC) — the main event. Highest volatility, most volume. This is where the big moves happen, driven by US institutional money. If a breakout happens during the US session on high volume, it's far more likely to sustain than one during Asia.
- **Session overlap** (13:00-16:00 UTC) — European + US overlap, peak liquidity. The most action-packed 3 hours of the day.

> **Tip:** Many successful day traders only trade during specific sessions. If your backtest shows dramatically better performance during `us-market-hours`, require that phase and skip the rest. No point leaving a strategy on during low-probability hours.

### Multi-Timeframe Analysis

The most reliable setups align across multiple timeframes. This is how professionals think about markets:

1. **Higher timeframe** (daily/4h) — determine the trend direction. "Am I bullish or bearish overall?"
2. **Trading timeframe** (1h/15m) — find entry signals. "Is there a setup right now?"
3. **Lower timeframe** (5m/1m) — fine-tune entries. "Can I get a better entry?"

![Multi-Timeframe Analysis](images/multi-timeframe.svg)

A setup that lines up on all three is much higher probability than one that only shows up on a single timeframe. If the daily shows an uptrend, the 4-hour shows a pullback to support, and the 1-hour shows a hammer — that's a confluence of signals across timeframes.

In Strategy Forge, use **phases** for multi-timeframe filtering. A phase on the daily timeframe (e.g., `uptrend` with `timeframe: 1d`) can filter entries on an hourly strategy. This is one of the most powerful features — your entry condition fires on the 1h chart, but it only triggers when the daily is also in an uptrend.


## Backtesting

### Why Backtest?

Backtesting applies your strategy rules to historical data to see how they would have performed. It answers: "Would this idea have made money?" But more importantly, it tells you *how* it would have made money — the win rate, the drawdowns, the longest losing streak.

Benefits:
- Validate ideas before risking real capital
- Understand win rate, drawdown, and risk profile
- Identify which market conditions favor your strategy
- Compare variations objectively — stop arguing with yourself and let the data decide

Every trader has ideas. "What if I bought when RSI is below 30?" is an idea. Without backtesting, you're guessing. With backtesting, you know: it works in ranging markets (62% win rate, 1.8 profit factor) but fails in trending ones (38% win rate, 0.7 profit factor). Now you can add the right phase filter and have a viable strategy.

### Key Metrics

- **Win Rate** — percentage of trades that are profitable. Higher is better, but meaningless without context. A 30% win rate with 1:5 R:R is excellent (those 30% of winners are huge). A 70% win rate with 1:0.5 R:R means you're slowly bleeding.
- **Profit Factor** — gross profit / gross loss. Above 1.5 is good, above 2.0 is excellent. Below 1.0 means you're losing money. This is arguably the single most important metric.
- **Sharpe Ratio** — risk-adjusted return. Above 1.0 is acceptable, above 2.0 is strong. It measures whether your returns justify the volatility you experienced getting them.
- **Max Drawdown** — the largest peak-to-trough decline. Indicates worst-case pain. Keep below 20% for real trading. If your backtest shows 40% max drawdown, your real drawdown will likely be worse.
- **Trade Count** — too few trades means results are not statistically significant. Aim for 30+ trades minimum. A strategy with 8 trades and 100% win rate proves nothing.
- **Capture Ratio** — actual profit vs maximum favorable excursion (MFE). Shows how well you're timing exits. If MFE averages 8% but your average win is 3%, you're leaving a lot on the table — maybe switch to trailing stops.

### Avoiding Curve Fitting

**Curve fitting** (overfitting) is the biggest trap in backtesting. It means your strategy is perfectly tuned to historical data but will fail on new data. It's the equivalent of memorizing test answers instead of understanding the material.

Warning signs:
- Too many conditions (5+ AND clauses). Each condition halves your trade count and doubles the chance you're fitting noise.
- Very specific parameter values (RSI(17) instead of RSI(14)). Why 17? Can you explain it?
- Strategy only works on one symbol or timeframe. If your BTCUSDT 1h strategy doesn't work at all on ETHUSDT 1h, you've probably fit BTC's specific history.
- Adding conditions that improve backtest but have no logical basis. "I added `HOUR > 7 AND HOUR < 19` because it improved results" — can you explain *why* those hours matter?
- Tiny sample size with amazing results. 12 trades, 100% win rate, 50% profit. You've found luck, not an edge.

Prevention:
- Keep rules simple and logical. 2-3 conditions is usually enough.
- Use standard indicator periods (14, 20, 50, 200). These are standard because lots of traders watch them, creating self-fulfilling effects.
- Test on multiple symbols and timeframes. If it works on BTC, ETH, and SOL, the logic is probably sound.
- Use out-of-sample testing (backtest on 6 months, validate on next 6). Strategy Forge's history tracking makes this easy.
- Ask: "Does this make sense from a market structure perspective?" If you can't explain *why* your strategy works, it probably doesn't.

> **Tip:** Strategy Forge's history tracking compares each backtest run to previous ones. If metrics swing wildly with small parameter changes (changing RSI from 14 to 15 flips the strategy from profitable to unprofitable), you're overfitting. A robust strategy shows gradual, not dramatic, sensitivity to parameter changes.


## Crypto Specifics

Crypto markets have unique features that traditional markets don't — perpetual futures with funding, 24/7 trading, extreme sentiment swings, and transparent on-chain data. Understanding these crypto-specific dynamics gives you edges that stock traders don't have.

### Funding Rates

In perpetual futures, **funding rates** are periodic payments between long and short holders to keep the futures price anchored to spot. This is a feature unique to crypto and one of the most useful trading signals available.

![Funding Rate & Open Interest](images/funding-oi.svg)

- **Positive funding** — longs pay shorts. The market is overleveraged long. Everyone and their neighbor is bullish.
- **Negative funding** — shorts pay longs. The market is overleveraged short.
- **Extreme funding** — often precedes a correction as the overleveraged side gets squeezed. When funding is 0.1%+ per 8 hours (which annualizes to 100%+), the long side is paying an unsustainable cost. A flush is coming.

**How traders use funding:**
- **Contrarian position traders** look for extreme positive funding combined with bearish technical signals — the overleveraged longs are about to get wiped.
- **Scalpers** fade funding spikes — when funding is extreme, the side paying funding is likely to capitulate soon.
- **Cash-and-carry arbitrageurs** buy spot and short futures to collect funding — a completely different strategy that profits from the funding rate itself rather than price direction.

Use `FUNDING` and `FUNDING_8H` in Strategy Forge conditions.

### Open Interest

**Open interest** is the total number of outstanding futures contracts. Changes in OI reveal who is entering and exiting the market, something price alone doesn't tell you.

- **Rising OI + rising price** — new money entering long. Trend is supported. This is the healthiest kind of rally.
- **Rising OI + falling price** — new money entering short. Downtrend is strengthening. Bears are getting aggressive.
- **Falling OI + rising price** — shorts covering (not new buying). The rally is driven by people exiting losing short positions. It often fizzles once the short covering is done.
- **Falling OI + falling price** — longs closing. Capitulation. Can signal a bottom when extreme — once everyone who wanted to sell has sold, only buyers remain.

Use `OI`, `OI_CHANGE`, and `OI_DELTA(n)` in your conditions.

### Fear & Greed Index

The Crypto Fear & Greed Index measures market sentiment on a scale of 0 (extreme fear) to 100 (extreme greed). It aggregates volatility, market momentum, social media, surveys, and BTC dominance.

- **0-25: Extreme Fear** — the crowd is panicking. Headlines are apocalyptic. This is historically one of the best times to buy. Warren Buffett's "be greedy when others are fearful" applies perfectly.
- **25-45: Fear** — cautious market, potential accumulation zone. Smart money is buying quietly.
- **55-75: Greed** — market is optimistic, trends tend to continue. Most of a bull market is spent here.
- **75-100: Extreme Greed** — "this time is different!" is being said unironically. Market is euphoric, corrections become more likely. Not necessarily a sell signal (extreme greed can persist for weeks), but definitely time to tighten stops.

Combine with technical signals for powerful setups:

```
FEAR_GREED < 25 AND RSI(14) < 30 AND price > SMA(200)
```

This finds extreme-fear oversold conditions in an overall uptrend — historically one of the strongest buy signals in crypto. The market is panicking during what is still a bull market.

### Liquidations and Squeezes

When leveraged positions get liquidated, they create forced buying or selling that amplifies moves. This is the explosive mechanism behind crypto's violent moves.

- **Long squeeze** — price drops, triggering long liquidations, which create more selling, which triggers more liquidations. A cascade. A 3% move becomes a 15% crash in minutes.
- **Short squeeze** — the mirror image. Price rises, shorts get liquidated, the forced buying pushes price higher, triggering more liquidations. This is how 20% candles happen.

Extreme funding rates combined with delta divergence often precede squeezes:
- High positive funding + negative delta = potential long squeeze. Everyone is long, but aggressive selling is emerging. When the dam breaks, the longs get washed out.
- High negative funding + positive delta = potential short squeeze. Everyone is short, but aggressive buying is emerging.

> **Tip:** Squeezes are one of the most profitable events to trade, but also one of the most dangerous to be on the wrong side of. In Strategy Forge, combine funding phases (`extreme-funding`) with delta conditions to detect squeeze setups. The key is being positioned before the cascade starts, not during it.
