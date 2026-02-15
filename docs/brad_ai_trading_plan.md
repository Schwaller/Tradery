# Brad-Style AI Trading System – Planning Notes

This document captures Brad’s workflow, examples, and heuristics exactly as discussed.
Purpose: feed into Codex / planning for further system design.

---

## Core Philosophy

AI is NOT the edge.

AI is a **compression engine**:
- compress research time
- expand market coverage
- surface hidden themes
- rank candidates
- reduce noise

Final decisions remain human.

---

## Reasons a Stock Moves (Explicit Drivers)

Stocks move because of structural drivers — not randomness.

These must be modeled.

### A) Multiple Expansion / Rerating
Example:
- Hardware company → reclassified as software
- Software gets higher multiple → stock reprices

Implementation:
- Track historical P/S, P/E
- Compare current multiple vs 5Y average
- Flag rerating zones

---

### B) Share Count Change (Dilution vs Buybacks)

Future shares outstanding matter.

Drivers:
- Buybacks → fewer shares → EPS boost
- Dilution → more shares → EPS pressure

Implementation:
- Shares outstanding trend
- Net buyback rate
- Equity issuance
- Flag: improving vs worsening structure

---

### C) Analyst Growth Expectations

Used because they affect repricing, not because they’re correct.

Implementation:
- Forward growth estimates
- Revision trends
- Detect expectation expansion / compression

---

### D) Actual Growth

- Revenue growth
- Earnings growth
- Margin expansion

Distinguish:
- Multiple-driven move
- Earnings-driven move
- Share-structure-driven move

---

## Ruling Reason (Single Dominant Catalyst)

Each stock has ONE ruling reason.

Examples:
- Regulation easing
- Turnaround earnings
- Debt retired
- Dividend cut enabling growth
- Market perception shift
- Business model reclassification

Store per candidate:
- ruling_reason
- invalidation_condition

---

## Quality / “Only Game in Town” Filter

High-quality attributes:
- Monopoly or dominant position
- Only game in town
- Founder-led
- Pricing power
- Strong capital allocation

Add qualitative score:
- founder_ceo
- monopoly_flag
- pricing_power
- allocation_history

Output: quality_score

---

## Theme Breadth (Core Edge)

Not index breadth — **theme breadth**.

Metrics per basket:
- Advancers / decliners
- % above MA
- New highs vs lows
- Equal-weight vs cap-weight
- Return dispersion

Signal:
- Breadth oscillator crossing MA
- Participation expanding while price compresses

Interpretation:
- Narrow bars + improving breadth = accumulation
- Index up + weak breadth = fragile

---

## Entry Logic

### Narrow Bars
- Tight consolidation
- Volatility compression
- “Buy in mild times”

### Deep Retests
- First impulse
- 80–90% retrace
- Late contrarian entry
- Avoid early bottom fishing

### Anchored VWAP
- Anchored from major swing
- Used like modern Fibonacci
- Starter before level
- More through level
- Add on reclaim

### Avoid Chasing
If no retest:
- often skip

---

## Exit Logic (Different Business)

### Euphoria
- Everyone already in
- Narrative saturated
- Late-stage hype

### Extension Metrics
- ~50% above 10-week MA
- Large weekly gaps
- Runaway moves

Style:
- Trim into euphoria
- Trim on rounding
- Rare full liquidation

---

## Soft / Discretionary Signals

These are heuristics, not binary indicators.

### Rounding Off
Characteristics:
- Momentum fading
- Upper wicks
- Flattening slope
- Breadth weakening

Flag:
- rounding_state

Used for partial exits.

---

### Runaway Moves
- Vertical candles
- Large gaps

Flag:
- runaway_move

---

### Everyone Already In
Detected via:
- sentiment
- valuation stretch
- weakening breadth

---

### Neglect Zones (Entry)
- Boredom
- Narrow bars
- Cheap fundamentals

Flag:
- neglect_state

---

### Flush on Stupid News
- Sharp drop
- No business impact

Used for starter entries.

---

### Late Contrarian Rule
Require:
- impulse
- deep retrace
- consolidation

Never first collapse.

---

## Correlation Trap

“You think you’re diversified but it’s the same trade.”

Track exposure by:
- theme
- factor
- beta
- correlation cluster

---

## Psychology Architecture

Rules:
- Cut losers quickly
- Avoid psychological damage
- Trail trends
- Never get religious

AI helps by:
- reducing boredom trades
- delivering alerts
- enforcing structure

---

## System Pipeline

1. Theme discovery (clustering + LLM labeling)
2. Theme breadth engine
3. Fundamental engine:
   - multiples
   - dilution/buybacks
   - growth expectations
   - quality
4. Checklist score (0–10)
5. Ruling reason + invalidation
6. Candidate ranking (2–5/week)
7. Human filter
8. Execution
9. Separate exit engine

---

## Entry vs Exit Engines

Entry:
- breadth
- consolidation
- retests
- fundamentals

Exit:
- sentiment
- extension
- rounding
- maturity

Must be separate modules.

---

## Minimal DB Schema

themes
theme_members
theme_breadth
symbol_fundamentals
ai_checklist
candidates
alerts

---

## Build Priority

1. Manual theme baskets + breadth
2. Fundamental scoring
3. Ruling reason extraction
4. Quality scoring
5. Alert + digest
6. Auto clustering

---

## Final Gate

If you cannot answer:

- Why does it move?
- What is ruling reason?
- What invalidates?
- Is share structure helping?
- Is it only game in town?
- Are expectations too high/low?

It fails the filter.

---

AI = compression + discovery.
Human = conviction + risk.

