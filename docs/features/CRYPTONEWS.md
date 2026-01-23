# CryptoNews - AI-Powered News Intelligence

> Network graph visualization of crypto news events, entities, and relationships with timeline analysis and market screener features.

## Vision

Transform raw crypto news into structured, actionable intelligence:

```
                    ┌─────────────────────────────────────────────────┐
                    │                 CryptoNews                       │
                    │                                                  │
  RSS Feeds         │   ┌─────────┐    ┌──────────┐    ┌───────────┐  │
  ───────────────►  │   │  Fetch  │───►│    AI    │───►│  Storage  │  │
  CoinDesk          │   │ Sources │    │ Extract  │    │  SQLite   │  │
  CoinTelegraph     │   └─────────┘    └──────────┘    └─────┬─────┘  │
  The Block         │                                        │        │
                    │        ┌───────────────────────────────┤        │
                    │        ▼                               ▼        │
                    │   ┌─────────┐    ┌──────────┐    ┌──────────┐   │
                    │   │ Network │    │ Timeline │    │ Screener │   │
                    │   │  Graph  │    │   View   │    │ & Alerts │   │
                    │   └─────────┘    └──────────┘    └──────────┘   │
                    └─────────────────────────────────────────────────┘
```

## Key Features

### 1. Network Graph Visualization

Interactive force-directed graph showing:
- **Nodes**: Events (hacks, partnerships, launches) and Entities (coins, companies, people)
- **Edges**: Relationships (partners with, invests in, regulates, etc.)
- **Colors**: By type (regulatory=red, partnership=green, etc.)
- **Size**: By impact score or mention count

```
                    ┌─────────┐
            ┌───────│  SEC    │───────┐
            │       └─────────┘       │
         regulates              regulates
            │                         │
            ▼                         ▼
      ┌──────────┐             ┌──────────┐
      │ Coinbase │─────────────│ Binance  │
      └────┬─────┘  competes   └────┬─────┘
           │                        │
        lists                    lists
           │                        │
           ▼                        ▼
      ┌─────────┐              ┌─────────┐
      │   BTC   │──────────────│   ETH   │
      └─────────┘   mentioned  └─────────┘
                    together
```

### 2. Timeline View

Gantt-style visualization of events over time:

```
  Event                  Jan        Feb        Mar        Apr
  ─────────────────────────────────────────────────────────────
  ETH Denver 2024        ████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
  SEC vs Ripple          ██████████████████████████████░░░░░░░░
  Bitcoin Halving        ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░████░░░░
  Solana Outage          ░░░░░░░░░█░░░░░░░░░░░░░░░░░░░░░░░░░░░░
  └── Past events ───────┴───── Current ──────┴── Predicted ──┘
```

### 3. Screener & Alerts

- **Sentiment Filters**: Show coins with positive/negative news momentum
- **Event Alerts**: Notify on hacks, regulatory actions, major partnerships
- **Correlation Finder**: Link news events to price movements

```
  Alert: BTC sentiment turned negative
  ├── Event: "Mt. Gox Distribution Begins"
  ├── Sentiment: -0.72 (Very Negative)
  ├── Related: 23 articles in 24h
  └── Price Impact: -4.2% within 6h
```

---

## Architecture

### Module Strategy

**Phase 1: Fully Separate** (Now)
- CryptoNews is its own standalone project
- No dependencies on Tradery code
- Can be developed and tested independently
- Own entry point, own data directory

**Phase 2: Shared Utilities** (When Needed)
- Extract common code into `shared` module
- Both apps depend on shared, not on each other
- Examples: SQLite helpers, OkHttp config, theme system

**Phase 3: Integration** (Optional)
- CryptoNews can optionally embed in Tradery
- Communication via MCP or shared data directory

```
┌─────────────────────────────────────────────────────────────────┐
│                     Project Structure                            │
│                                                                  │
│  Phase 1 (Now):           Phase 2 (Later):                      │
│                                                                  │
│  Tradery/                 Tradery/                               │
│  CryptoNews/              ├── shared/      ◄── Common utilities │
│  (separate)               ├── tradery/     ◄── Depends on shared│
│                           └── cryptonews/  ◄── Depends on shared│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Module Structure

**For now: Separate project, clean boundary**

```
~/Code/
├── Tradery/                        # Existing - DON'T TOUCH
│   └── ...
│
└── CryptoNews/                     # NEW: Completely separate
    ├── build.gradle
    ├── settings.gradle
    ├── CLAUDE.md                   # Project instructions for Claude
    └── src/main/java/com/cryptonews/
        │
        ├── CryptoNewsApp.java      # Main entry point
        │
        ├── model/                  # Data models
        │   ├── Article.java
        │   ├── Story.java          # Clustered articles
        │   ├── NewsEvent.java
        │   ├── Entity.java
        │   ├── Relationship.java
        │   └── enums/
        │       ├── ImportanceLevel.java
        │       ├── EntityType.java
        │       └── RelationshipType.java
        │
        ├── fetch/                  # News source fetchers
        │   ├── Fetcher.java        # Base interface
        │   ├── RssFetcher.java
        │   ├── GoogleSearchFetcher.java
        │   ├── XSearchFetcher.java
        │   ├── FetcherRegistry.java
        │   └── FetchScheduler.java
        │
        ├── token/                  # Token launch tracking
        │   ├── TokenTracker.java
        │   ├── PumpFunClient.java
        │   ├── BreakoutDetector.java
        │   ├── TokenResearchAgent.java
        │   └── ContractAnalyzer.java
        │
        ├── ai/                     # AI processing (CLI-based)
        │   ├── AiProcessor.java
        │   ├── ClaudeCliProcessor.java
        │   ├── CodexCliProcessor.java
        │   ├── StoryClustering.java
        │   └── PromptTemplates.java
        │
        ├── store/                  # SQLite storage
        │   ├── Database.java       # Connection management
        │   ├── ArticleStore.java
        │   ├── StoryStore.java
        │   ├── EventStore.java
        │   ├── EntityStore.java
        │   └── QueryService.java   # Derived data queries
        │
        ├── graph/                  # Network visualization
        │   ├── NewsGraph.java
        │   ├── GraphStyler.java
        │   └── GraphFilter.java
        │
        ├── screener/               # Alerts & analysis
        │   ├── SentimentScreener.java
        │   ├── AlertManager.java
        │   └── TrendAnalyzer.java
        │
        └── ui/                     # Swing UI
            ├── CryptoNewsFrame.java
            ├── panels/
            │   ├── FeedPanel.java
            │   ├── StoriesPanel.java
            │   ├── GraphPanel.java
            │   ├── TimelinePanel.java
            │   ├── TokenTrackerPanel.java
            │   └── ScreenerPanel.java
            ├── components/
            │   ├── FilterBar.java
            │   ├── ArticleDetail.java
            │   └── StoryCard.java
            └── theme/
                └── ThemeManager.java
```

### Data Storage

```
~/.tradery/cryptonews/
├── config.yaml                 # Settings (AI provider, sources, intervals)
u├── cryptonews.db               # SQLite database (schema below)
├── articles/                   # Full article content cache (JSON)
└── exports/                    # Graph exports
```

### SQLite Schema

```sql
-- Core tables
CREATE TABLE articles (
    id TEXT PRIMARY KEY,
    source_url TEXT NOT NULL,
    source_id TEXT NOT NULL,
    source_name TEXT,
    title TEXT NOT NULL,
    content TEXT,                   -- Full original content
    author TEXT,
    summary TEXT,                   -- AI-generated
    importance TEXT DEFAULT 'MEDIUM',
    sentiment_score REAL,
    published_at INTEGER NOT NULL,
    fetched_at INTEGER NOT NULL,
    processed_at INTEGER,
    status TEXT DEFAULT 'PENDING'
);

CREATE TABLE events (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,             -- HACK, REGULATORY, PARTNERSHIP, etc.
    title TEXT NOT NULL,
    description TEXT,
    start_date INTEGER,
    end_date INTEGER,
    sentiment_score REAL,
    impact_score REAL,
    created_at INTEGER NOT NULL
);

CREATE TABLE entities (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,             -- COIN, PERSON, COMPANY, EXCHANGE, etc.
    name TEXT NOT NULL,
    symbol TEXT,                    -- BTC, ETH for coins
    description TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE relationships (
    id TEXT PRIMARY KEY,
    source_id TEXT NOT NULL,
    target_id TEXT NOT NULL,
    type TEXT NOT NULL,             -- PARTNERS_WITH, INVESTS_IN, etc.
    strength REAL,
    context TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (source_id) REFERENCES entities(id),
    FOREIGN KEY (target_id) REFERENCES entities(id)
);

-- Junction tables for many-to-many
CREATE TABLE article_coins (
    article_id TEXT,
    coin TEXT,                      -- "BTC", "ETH", etc.
    PRIMARY KEY (article_id, coin)
);

CREATE TABLE article_tags (
    article_id TEXT,
    tag TEXT,                       -- "#etf", "#hack", etc.
    PRIMARY KEY (article_id, tag)
);

CREATE TABLE article_categories (
    article_id TEXT,
    category TEXT,                  -- "Regulatory", "DeFi", etc.
    PRIMARY KEY (article_id, category)
);

CREATE TABLE article_events (
    article_id TEXT,
    event_id TEXT,
    PRIMARY KEY (article_id, event_id)
);

CREATE TABLE article_entities (
    article_id TEXT,
    entity_id TEXT,
    mention_count INTEGER DEFAULT 1,
    PRIMARY KEY (article_id, entity_id)
);

CREATE TABLE entity_aliases (
    entity_id TEXT,
    alias TEXT,
    PRIMARY KEY (entity_id, alias)
);

-- Indexes for fast queries
CREATE INDEX idx_articles_importance ON articles(importance);
CREATE INDEX idx_articles_published ON articles(published_at DESC);
CREATE INDEX idx_articles_source ON articles(source_id);
CREATE INDEX idx_events_type ON events(type);
CREATE INDEX idx_events_start ON events(start_date DESC);
CREATE INDEX idx_entities_type ON entities(type);
CREATE INDEX idx_entities_symbol ON entities(symbol);
CREATE INDEX idx_relationships_source ON relationships(source_id);
CREATE INDEX idx_relationships_target ON relationships(target_id);
CREATE INDEX idx_article_coins_coin ON article_coins(coin);
CREATE INDEX idx_article_tags_tag ON article_tags(tag);
```

### Derived Data Queries

**Coin List (all coins mentioned across articles):**
```sql
-- Get all coins with mention counts
SELECT coin, COUNT(*) as mentions,
       MAX(a.published_at) as last_mentioned
FROM article_coins ac
JOIN articles a ON a.id = ac.article_id
GROUP BY coin
ORDER BY mentions DESC;

-- Coins by importance (weighted)
SELECT coin,
       SUM(CASE importance
           WHEN 'CRITICAL' THEN 5
           WHEN 'HIGH' THEN 4
           WHEN 'MEDIUM' THEN 3
           ELSE 1 END) as weighted_score
FROM article_coins ac
JOIN articles a ON a.id = ac.article_id
WHERE a.published_at > :since
GROUP BY coin
ORDER BY weighted_score DESC;
```

**Partners (find all partnerships for an entity):**
```sql
-- Direct partners
SELECT e.name, e.symbol, r.context, r.strength
FROM relationships r
JOIN entities e ON e.id = r.target_id
WHERE r.source_id = :entity_id
  AND r.type = 'PARTNERS_WITH';

-- All relationships for an entity (graph edges)
SELECT
    e1.name as source_name,
    e2.name as target_name,
    r.type,
    r.strength,
    r.context
FROM relationships r
JOIN entities e1 ON e1.id = r.source_id
JOIN entities e2 ON e2.id = r.target_id
WHERE r.source_id = :entity_id OR r.target_id = :entity_id;
```

**Sentiment by Coin (aggregated):**
```sql
-- Average sentiment for a coin over time
SELECT
    coin,
    AVG(a.sentiment_score) as avg_sentiment,
    COUNT(*) as article_count,
    SUM(CASE WHEN a.sentiment_score > 0.3 THEN 1 ELSE 0 END) as positive,
    SUM(CASE WHEN a.sentiment_score < -0.3 THEN 1 ELSE 0 END) as negative
FROM article_coins ac
JOIN articles a ON a.id = ac.article_id
WHERE a.published_at > :since
GROUP BY coin;
```

**Tag Trends:**
```sql
-- Trending tags in last 24h vs previous 24h
WITH recent AS (
    SELECT tag, COUNT(*) as count
    FROM article_tags at
    JOIN articles a ON a.id = at.article_id
    WHERE a.published_at > :now - 86400
    GROUP BY tag
),
previous AS (
    SELECT tag, COUNT(*) as count
    FROM article_tags at
    JOIN articles a ON a.id = at.article_id
    WHERE a.published_at BETWEEN :now - 172800 AND :now - 86400
    GROUP BY tag
)
SELECT r.tag, r.count as recent, COALESCE(p.count, 0) as previous,
       (r.count - COALESCE(p.count, 0)) as change
FROM recent r
LEFT JOIN previous p ON r.tag = p.tag
ORDER BY change DESC;
```

**Entity Graph (for visualization):**
```sql
-- Get nodes (entities involved with a coin)
SELECT DISTINCT e.id, e.type, e.name, e.symbol
FROM entities e
JOIN article_entities ae ON ae.entity_id = e.id
JOIN article_coins ac ON ac.article_id = ae.article_id
WHERE ac.coin = :coin;

-- Get edges (relationships between those entities)
SELECT r.source_id, r.target_id, r.type, r.strength
FROM relationships r
WHERE r.source_id IN (:entity_ids)
   OR r.target_id IN (:entity_ids);
```

**Event Timeline:**
```sql
-- Events affecting a coin, ordered by date
SELECT e.*, GROUP_CONCAT(ac.coin) as coins
FROM events e
JOIN article_events ae ON ae.event_id = e.id
JOIN article_coins ac ON ac.article_id = ae.article_id
WHERE ac.coin = :coin
GROUP BY e.id
ORDER BY e.start_date DESC;
```

**Writer Analytics:**
```sql
-- All writers with article counts and avg sentiment
SELECT
    author,
    COUNT(*) as article_count,
    AVG(sentiment_score) as avg_sentiment,
    GROUP_CONCAT(DISTINCT source_id) as outlets
FROM articles
WHERE author IS NOT NULL
GROUP BY author
ORDER BY article_count DESC;

-- Writer's coin coverage (what coins do they write about?)
SELECT
    a.author,
    ac.coin,
    COUNT(*) as articles,
    AVG(a.sentiment_score) as avg_sentiment
FROM articles a
JOIN article_coins ac ON ac.article_id = a.id
WHERE a.author = :author
GROUP BY ac.coin
ORDER BY articles DESC;

-- Find writers bullish on a coin
SELECT author, AVG(sentiment_score) as sentiment, COUNT(*) as articles
FROM articles a
JOIN article_coins ac ON ac.article_id = a.id
WHERE ac.coin = :coin AND a.author IS NOT NULL
GROUP BY author
HAVING sentiment > 0.3
ORDER BY sentiment DESC;
```

**Outlet Analytics:**
```sql
-- All outlets with stats
SELECT
    source_id,
    source_name,
    COUNT(*) as article_count,
    AVG(sentiment_score) as avg_sentiment,
    COUNT(DISTINCT author) as writer_count
FROM articles
GROUP BY source_id
ORDER BY article_count DESC;

-- Outlet's coin coverage
SELECT
    a.source_name,
    ac.coin,
    COUNT(*) as articles,
    AVG(a.sentiment_score) as coin_sentiment
FROM articles a
JOIN article_coins ac ON ac.article_id = a.id
WHERE a.source_id = :outlet
GROUP BY ac.coin
ORDER BY articles DESC;

-- Compare outlet sentiment on a coin
SELECT source_name, AVG(sentiment_score) as sentiment, COUNT(*) as articles
FROM articles a
JOIN article_coins ac ON ac.article_id = a.id
WHERE ac.coin = :coin
GROUP BY source_id
ORDER BY sentiment DESC;
-- "CoinDesk is more bullish on ETH than CoinTelegraph"
```

**Writer/Outlet in Graph:**
```sql
-- Writers can be nodes in the graph
-- Edges: WRITES_FOR (outlet), COVERS (coin), MENTIONS (entity)
INSERT INTO entities (id, type, name)
VALUES ('writer_' || :author_slug, 'WRITER', :author_name);

INSERT INTO relationships (source_id, target_id, type, context)
VALUES ('writer_nikhilesh', 'outlet_coindesk', 'WRITES_FOR', 'Staff writer');
```

### Query API (Java)

```java
public interface NewsQueryService {
    // Coin queries
    List<CoinMention> getAllCoins();
    List<CoinMention> getCoinsRankedByImportance(Instant since);
    CoinSentiment getSentimentForCoin(String coin, Instant since);

    // Entity queries
    List<Entity> getPartnersOf(String entityId);
    List<Relationship> getRelationshipsFor(String entityId);
    EntityGraph getGraphForCoin(String coin);

    // Tag queries
    List<TagTrend> getTrendingTags(Duration window);
    List<Article> getArticlesByTag(String tag, int limit);

    // Timeline queries
    List<NewsEvent> getEventsForCoin(String coin, Instant since);
    List<NewsEvent> getEventsByType(String type, Instant since);

    // Full-text search
    List<Article> search(String query, SearchFilters filters);
}

record CoinMention(String coin, int mentions, Instant lastMentioned, double weightedScore) {}
record CoinSentiment(String coin, double avgSentiment, int articleCount, int positive, int negative) {}
record TagTrend(String tag, int recentCount, int previousCount, int change) {}
```

---

## Data Models

### Tagging System

News items are tagged for filtering and importance ranking:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Tagging Hierarchy                         │
│                                                                  │
│  ┌─ Importance Level ─┐   ┌─ Coins ─────┐   ┌─ Categories ────┐ │
│  │ 🔴 CRITICAL        │   │ BTC         │   │ Regulatory      │ │
│  │ 🟠 HIGH            │   │ ETH         │   │ Exchange        │ │
│  │ 🟡 MEDIUM          │   │ SOL         │   │ DeFi            │ │
│  │ 🟢 LOW             │   │ (any coin)  │   │ NFT             │ │
│  │ ⚪ NOISE           │   └─────────────┘   │ Security        │ │
│  └────────────────────┘                     │ Institutional   │ │
│                                             │ Technical       │ │
│  ┌─ Event Tags ───────────────────────┐     └─────────────────┘ │
│  │ #etf #regulation #hack #launch     │                         │
│  │ #partnership #listing #lawsuit     │                         │
│  │ #upgrade #fork #halving #airdrop   │                         │
│  └────────────────────────────────────┘                         │
└─────────────────────────────────────────────────────────────────┘
```

**Importance Levels:**

| Level | Description | Examples |
|-------|-------------|----------|
| 🔴 CRITICAL | Market-moving, needs immediate attention | ETF approval, major hack, regulatory action |
| 🟠 HIGH | Significant news, affects specific assets | Exchange listing, protocol upgrade, partnership |
| 🟡 MEDIUM | Notable but not urgent | Developer updates, minor partnerships |
| 🟢 LOW | Background noise, FYI | Opinion pieces, minor updates |
| ⚪ NOISE | Filtered out, low value | Spam, duplicates, clickbait |

**Tag Examples:**

```
Article: "SEC Approves Spot Bitcoin ETF"
├── Importance: CRITICAL
├── Coins: [BTC]
├── Categories: [Regulatory, Institutional]
└── Tags: [#etf, #sec, #approval, #institutional]

Article: "Solana Network Outage Lasts 5 Hours"
├── Importance: HIGH
├── Coins: [SOL]
├── Categories: [Technical, Security]
└── Tags: [#outage, #network, #downtime]

Article: "New Memecoin Launches on Pump.fun"
├── Importance: LOW
├── Coins: [SOL, $NEWMEME]
├── Categories: [DeFi, Memecoin]
└── Tags: [#memecoin, #launch, #pumpfun]
```

### Article (Core Unit)

Every piece of news becomes an Article that preserves the original while adding structure:

```java
record Article(
    // === ORIGINAL CONTENT (always preserved) ===
    String id,                      // SHA256(url)
    String sourceUrl,               // Original URL - ALWAYS kept for reference
    String sourceId,                // "coindesk", "cointelegraph", "x-elonmusk"
    String sourceName,              // "CoinDesk", "X/@elonmusk"
    String title,                   // Original title
    String content,                 // FULL original text/tweet - never truncated
    String author,                  // Original author if available
    Instant publishedAt,            // Original publish time

    // === AI-EXTRACTED STRUCTURE ===
    String summary,                 // AI-generated 2-3 sentence summary
    ImportanceLevel importance,     // CRITICAL, HIGH, MEDIUM, LOW, NOISE
    List<String> coins,             // ["BTC", "ETH", "SOL"]
    List<String> categories,        // ["Regulatory", "DeFi"]
    List<String> tags,              // ["#etf", "#sec", "#approval"]
    double sentimentScore,          // -1.0 to +1.0
    List<String> eventIds,          // Links to extracted NewsEvent records
    List<String> entityIds,         // Links to extracted Entity records

    // === METADATA ===
    Instant fetchedAt,              // When we fetched it
    Instant processedAt,            // When AI processed it
    ProcessingStatus status         // PENDING, PROCESSING, COMPLETE, ERROR
)
```

**Key Principle: Original Content is Sacred**

```
┌─────────────────────────────────────────────────────────────────┐
│                      Article Record                              │
│                                                                  │
│  ┌─ Original (Immutable) ──────────────────────────────────────┐│
│  │ sourceUrl: "https://coindesk.com/markets/2024/01/..."      ││
│  │ title: "SEC Approves Spot Bitcoin ETFs in Historic..."     ││
│  │ content: "The Securities and Exchange Commission on        ││
│  │          Wednesday approved the first U.S. spot bitcoin    ││
│  │          exchange-traded funds, marking a watershed..."    ││
│  │ author: "Nikhilesh De"                                     ││
│  │ publishedAt: 2024-01-10T16:00:00Z                          ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│                              ▼                                   │
│  ┌─ AI-Extracted Structure ────────────────────────────────────┐│
│  │ summary: "SEC approved 11 spot Bitcoin ETFs including..."  ││
│  │ importance: CRITICAL                                        ││
│  │ coins: ["BTC"]                                              ││
│  │ tags: ["#etf", "#sec", "#approval", "#institutional"]      ││
│  │ sentiment: +0.92                                            ││
│  │ events: ["evt_btc_etf_approval_2024"]                       ││
│  │ entities: ["ent_sec", "ent_blackrock", "ent_fidelity"]     ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

**UI: Click any item → See original + source link**

```
┌─ Article Detail ─────────────────────────────────────────────────┐
│ 🔴 CRITICAL │ BTC │ #etf #sec                                    │
│                                                                   │
│ SEC Approves Spot Bitcoin ETFs in Historic Decision              │
│ ─────────────────────────────────────────────────────────────── │
│ CoinDesk • Nikhilesh De • Jan 10, 2024 4:00 PM                  │
│ [🔗 Open Original Article]                                       │
│                                                                   │
│ ┌─ AI Summary ─────────────────────────────────────────────────┐ │
│ │ SEC approved 11 spot Bitcoin ETFs including BlackRock and   │ │
│ │ Fidelity offerings. Trading begins tomorrow. Historic       │ │
│ │ moment after 10+ years of rejections.                       │ │
│ └───────────────────────────────────────────────────────────────┘ │
│                                                                   │
│ ┌─ Original Content ───────────────────────────────────────────┐ │
│ │ The Securities and Exchange Commission on Wednesday         │ │
│ │ approved the first U.S. spot bitcoin exchange-traded        │ │
│ │ funds, marking a watershed moment for the crypto industry   │ │
│ │ after more than a decade of failed attempts...              │ │
│ │                                                              │ │
│ │ [Show Full Article ▼]                                        │ │
│ └───────────────────────────────────────────────────────────────┘ │
│                                                                   │
│ Related: [BlackRock] [Fidelity] [SEC] [Gary Gensler]            │
└──────────────────────────────────────────────────────────────────┘
```

```java
enum ImportanceLevel {
    CRITICAL(5),   // ETF-level news, major hacks, regulatory actions
    HIGH(4),       // Significant moves, listings, partnerships
    MEDIUM(3),     // Notable updates, minor news
    LOW(2),        // Background noise
    NOISE(1);      // Filtered out

    final int weight;
}
```

### Story Clustering

When multiple articles cover the same "thing", they're grouped into a **Story**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Story Clustering                              │
│                                                                  │
│  Article 1: "SEC Approves Bitcoin ETFs" (CoinDesk)              │
│  Article 2: "Bitcoin ETF Finally Gets Green Light" (Decrypt)    │  ──► Story: "Bitcoin ETF Approval"
│  Article 3: "Historic Day: BTC ETFs Approved" (CoinTelegraph)   │       │
│  Article 4: "What the ETF Approval Means" (The Block)           │       ├── 23 articles
│                                                                  │       ├── Sentiment: +0.78
│                                                                  │       └── Peak: Jan 10-11, 2024
└─────────────────────────────────────────────────────────────────┘
```

```java
record Story(
    String id,                      // Auto-generated cluster ID
    String title,                   // AI-generated canonical title
    String summary,                 // Merged summary across articles
    StoryType type,                 // BREAKING, DEVELOPING, ANALYSIS, OPINION
    Instant firstSeen,              // Earliest article
    Instant lastUpdated,            // Most recent article
    Instant peakTime,               // When most articles published
    int articleCount,               // Total articles in cluster
    double avgSentiment,            // Aggregated sentiment
    ImportanceLevel importance,     // Highest importance from articles
    List<String> articleIds,        // All articles in this story
    List<String> coins,             // Merged coin list
    List<String> tags,              // Merged tags
    List<String> eventIds,          // Related events
    boolean isActive                // Still receiving new articles?
)

enum StoryType {
    BREAKING,       // New story, happening now
    DEVELOPING,     // Ongoing, new articles coming in
    ANALYSIS,       // Post-event analysis pieces
    OPINION,        // Opinion/editorial cluster
    HISTORICAL      // Old story, no longer active
}
```

**Clustering Algorithm:**

```java
public class StoryClustering {

    // When a new article is processed:
    public Story clusterArticle(Article article) {
        // 1. Find candidate stories (same coins, recent, similar tags)
        List<Story> candidates = findCandidateStories(article);

        // 2. Use AI to check if article belongs to existing story
        for (Story candidate : candidates) {
            String prompt = """
                Does this new article belong to the same story?

                EXISTING STORY:
                Title: %s
                Summary: %s
                Coins: %s

                NEW ARTICLE:
                Title: %s
                Content: %s

                Answer: SAME_STORY or NEW_STORY
                If SAME_STORY, is it: UPDATE, ANALYSIS, or DUPLICATE
                """.formatted(...);

            ClusterResult result = aiProcessor.classify(prompt);
            if (result.isSameStory()) {
                return mergeIntoStory(candidate, article, result.articleRole());
            }
        }

        // 3. No match - create new story
        return createNewStory(article);
    }

    // Merge article into existing story
    private Story mergeIntoStory(Story story, Article article, ArticleRole role) {
        return story.toBuilder()
            .articleIds(story.articleIds().add(article.id()))
            .articleCount(story.articleCount() + 1)
            .lastUpdated(article.publishedAt())
            .avgSentiment(recalculateSentiment(story, article))
            .importance(max(story.importance(), article.importance()))
            .coins(merge(story.coins(), article.coins()))
            .tags(merge(story.tags(), article.tags()))
            .isActive(true)
            .build();
    }
}
```

**Story SQLite Schema:**

```sql
CREATE TABLE stories (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    summary TEXT,
    type TEXT DEFAULT 'BREAKING',
    first_seen INTEGER NOT NULL,
    last_updated INTEGER NOT NULL,
    peak_time INTEGER,
    article_count INTEGER DEFAULT 1,
    avg_sentiment REAL,
    importance TEXT,
    is_active INTEGER DEFAULT 1,
    created_at INTEGER NOT NULL
);

CREATE TABLE story_articles (
    story_id TEXT,
    article_id TEXT,
    role TEXT,                      -- ORIGINAL, UPDATE, ANALYSIS, OPINION, DUPLICATE
    added_at INTEGER,
    PRIMARY KEY (story_id, article_id)
);

CREATE TABLE story_coins (
    story_id TEXT,
    coin TEXT,
    PRIMARY KEY (story_id, coin)
);

-- Find story for UI display
CREATE INDEX idx_stories_active ON stories(is_active, last_updated DESC);
CREATE INDEX idx_stories_importance ON stories(importance, last_updated DESC);
```

**Story Queries:**

```sql
-- Active stories ranked by importance
SELECT s.*, GROUP_CONCAT(sc.coin) as coins
FROM stories s
LEFT JOIN story_coins sc ON sc.story_id = s.id
WHERE s.is_active = 1
GROUP BY s.id
ORDER BY
    CASE s.importance
        WHEN 'CRITICAL' THEN 5
        WHEN 'HIGH' THEN 4
        ELSE 3
    END DESC,
    s.last_updated DESC;

-- Get all articles in a story
SELECT a.*, sa.role
FROM articles a
JOIN story_articles sa ON sa.article_id = a.id
WHERE sa.story_id = :story_id
ORDER BY a.published_at DESC;

-- Find stories for a coin
SELECT s.*
FROM stories s
JOIN story_coins sc ON sc.story_id = s.id
WHERE sc.coin = :coin
ORDER BY s.last_updated DESC;
```

**UI: Story View**

```
┌─ Stories ────────────────────────────────────────────────────────┐
│ ┌─ 🔴 CRITICAL ──────────────────────────────────────────────┐   │
│ │ Bitcoin ETF Approval                                       │   │
│ │ 23 articles • First: Jan 10 • Latest: 2h ago • Developing │   │
│ │ [CoinDesk] [Decrypt] [The Block] +20 more                  │   │
│ │ Coins: BTC  │  Sentiment: +0.78  │  [Expand ▼]             │   │
│ └────────────────────────────────────────────────────────────┘   │
│                                                                   │
│ ┌─ 🟠 HIGH ──────────────────────────────────────────────────┐   │
│ │ Solana Network Recovers After 5-Hour Outage                │   │
│ │ 8 articles • First: Jan 9 • Latest: 12h ago • Historical   │   │
│ │ Coins: SOL  │  Sentiment: -0.42  │  [Expand ▼]             │   │
│ └────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### NewsEvent
```java
record NewsEvent(
    String id,
    EventType type,         // HACK, REGULATORY, PARTNERSHIP, LAUNCH, etc.
    String title,
    String description,
    Instant startDate,      // When event started
    Instant endDate,        // When event concluded (or null if ongoing)
    double sentimentScore,  // -1.0 (very negative) to +1.0 (very positive)
    double impactScore,     // 0.0 to 1.0 estimated market impact
    List<String> articleIds,
    List<String> entityIds,
    String storyId          // Link to parent story if part of one
)
```

### Entity
```java
record Entity(
    String id,              // Normalized: "bitcoin", "vitalik-buterin"
    EntityType type,        // COIN, PERSON, COMPANY, EXCHANGE, REGULATOR
    String name,            // Display name
    String symbol,          // "BTC", "ETH" for coins
    List<String> aliases    // Alternative names
)
```

### Relationship
```java
record Relationship(
    String sourceId,
    String targetId,
    RelationType type,      // PARTNERS_WITH, INVESTS_IN, REGULATES, etc.
    double strength,        // 0.0 to 1.0
    String context          // "SEC filed lawsuit against..."
)
```

---

## AI Processing

### Cost-Efficient CLI Approach

Instead of expensive API calls, leverage existing CLI subscriptions:

```java
public class ClaudeCliProcessor implements AiProcessor {

    public ExtractionResult process(Article article) {
        // Write article to temp file
        Path temp = Files.createTempFile("article-", ".txt");
        Files.writeString(temp, article.title() + "\n\n" + article.content());

        // Invoke Claude CLI
        ProcessBuilder pb = new ProcessBuilder(
            "claude", "-p", EXTRACTION_PROMPT, "--no-markdown"
        );
        pb.redirectInput(temp.toFile());

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());

        return parseJson(output);
    }
}
```

### Extraction Prompt

```
Analyze this crypto news article. Return JSON:

{
  "importance": "CRITICAL|HIGH|MEDIUM|LOW|NOISE",
  "coins": ["BTC", "ETH"],           // Coins mentioned or affected
  "categories": ["Regulatory"],       // Regulatory, Exchange, DeFi, NFT, Security, Institutional, Technical
  "tags": ["#etf", "#sec"],          // Relevant hashtags

  "events": [{
    "type": "HACK|REGULATORY|PARTNERSHIP|LAUNCH|FUNDING|LISTING|EXPLOIT|ETF|UPGRADE|HALVING|...",
    "title": "Brief title",
    "description": "1-2 sentences",
    "startDate": "ISO date",
    "endDate": "ISO date or null",
    "sentiment": {"score": -1.0 to 1.0, "label": "..."},
    "impactScore": 0.0 to 1.0
  }],
  "entities": [{
    "name": "Normalized name",
    "type": "COIN|PERSON|COMPANY|EXCHANGE|REGULATOR",
    "symbol": "ticker if coin"
  }],
  "relationships": [{
    "source": "entity name",
    "target": "entity name",
    "type": "PARTNERS_WITH|INVESTS_IN|REGULATES|...",
    "context": "brief explanation"
  }]
}

Importance levels:
- CRITICAL: ETF approvals, major hacks (>$100M), regulatory actions against major exchanges
- HIGH: Exchange listings, protocol upgrades, significant partnerships
- MEDIUM: Developer updates, minor news
- LOW: Opinion pieces, minor updates
- NOISE: Spam, duplicates, clickbait
```

### Configurable Providers

```yaml
# ~/.tradery/cryptonews/config.yaml
ai:
  provider: claude        # claude | openai | ollama (future)
  model: default          # or specific model name
  maxConcurrent: 2        # parallel processing limit
```

---

## News Sources

### Source Types

```
┌─────────────────────────────────────────────────────────────────┐
│                       News Source Layer                          │
├─────────────────┬─────────────────┬─────────────────────────────┤
│   RSS Feeds     │  Search Queries │      Social Media           │
│                 │                 │                             │
│  • CoinDesk     │  • Google News  │  • X.com (Twitter)          │
│  • CoinTelegraph│  • Bing News    │  • Reddit (future)          │
│  • The Block    │  • DuckDuckGo   │  • Discord (future)         │
│  • Decrypt      │                 │                             │
└────────┬────────┴────────┬────────┴──────────────┬──────────────┘
         │                 │                       │
         ▼                 ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Unified Article Model                         │
└─────────────────────────────────────────────────────────────────┘
```

### 1. RSS Feeds (Free, No Auth)

| Source | RSS URL | Focus |
|--------|---------|-------|
| CoinDesk | `coindesk.com/arc/outboundfeeds/rss/` | General crypto news |
| CoinTelegraph | `cointelegraph.com/rss` | News & analysis |
| The Block | `theblock.co/rss.xml` | Institutional focus |
| Decrypt | `decrypt.co/feed` | Consumer crypto |

### 2. Search Queries (CLI-Based, Free)

Use search CLI tools to find recent crypto news:

```java
// GoogleSearchFetcher.java
public class GoogleSearchFetcher implements NewsFetcher {

    public List<Article> fetchLatest(String query) {
        // Use googler CLI or web scraping
        // Query: "bitcoin news" OR "ethereum news" site:coindesk.com OR site:cointelegraph.com
        ProcessBuilder pb = new ProcessBuilder(
            "googler", "--json", "-n", "20",
            query + " crypto news"
        );
        // Parse JSON results → Article objects
    }
}
```

**Search Query Templates:**
```
# Breaking news
"bitcoin" OR "ethereum" OR "crypto" news last 24 hours

# Specific events
"SEC" AND ("bitcoin" OR "ethereum") regulatory

# Entity-focused
"Coinbase" OR "Binance" announcement

# Custom user queries (configurable)
{user_query} crypto news
```

**CLI Tools:**
- `googler` - Google search from terminal (open source)
- `ddgr` - DuckDuckGo search from terminal
- Direct HTTP to Google News RSS: `news.google.com/rss/search?q=bitcoin`

### 3. X.com (Twitter) Search

Real-time social sentiment from crypto Twitter:

```java
// XSearchFetcher.java
public class XSearchFetcher implements NewsFetcher {

    public List<Article> fetchLatest(String query) {
        // Option 1: Use Nitter (open source Twitter frontend)
        // Option 2: Use X API (requires auth, has costs)
        // Option 3: CLI tools like twint (deprecated) or snscrape

        // Query crypto influencers and news accounts
        String searchQuery = query + " (from:coindesk OR from:caboracoin OR from:whale_alert)";

        // Parse tweets into Article format
        // - id: tweet ID
        // - content: tweet text
        // - publishedAt: tweet timestamp
        // - sourceId: "x-{username}"
    }
}
```

**Useful X Queries:**
```
# Whale alerts
from:whale_alert

# Crypto news accounts
from:coindesk OR from:caboracoin OR from:TheBlock__

# Breaking news with engagement
(bitcoin OR ethereum) min_faves:1000 -filter:replies

# Specific coin mentions
$BTC OR $ETH -filter:retweets min_faves:100
```

**Implementation Options:**
| Method | Cost | Rate Limits | Notes |
|--------|------|-------------|-------|
| Nitter scraping | Free | Moderate | Unofficial, may break |
| X API Free tier | Free | 1500 tweets/mo | Very limited |
| X API Basic | $100/mo | 10K tweets/mo | Good for production |
| snscrape | Free | None | Python tool, works well |

### 4. Token Launch Platforms (Memecoin Tracking)

Track new token launches and breakouts on platforms like pump.fun:

```
┌─────────────────────────────────────────────────────────────────┐
│                   Token Launch Tracker                           │
│                                                                  │
│  pump.fun ───┐                                                   │
│  moonshot ───┼──► New Token ──► Breakout? ──► Deep Research     │
│  believe ────┤     Detected      Detected      Triggered         │
│  raydium ────┘                      │              │             │
│                                     │              ▼             │
│                              ┌──────┴──────┐  ┌────────────┐    │
│                              │ Volume Spike │  │ AI Analysis │    │
│                              │ Price +100%  │  │ - Tokenomics│    │
│                              │ Holder Growth│  │ - Team/Socials   │
│                              └─────────────┘  │ - Contract  │    │
│                                               │ - Red Flags │    │
│                                               └────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

**Platforms to Track:**

| Platform | Chain | Focus | Data Source |
|----------|-------|-------|-------------|
| pump.fun | Solana | Memecoins | API/WebSocket |
| moonshot | Solana | New launches | API |
| believe.fun | Solana | AI agents | API |
| Raydium | Solana | DEX launches | On-chain |
| Uniswap | Ethereum | New pairs | On-chain |
| PancakeSwap | BSC | New pairs | On-chain |

**Breakout Detection Criteria:**

```java
// BreakoutDetector.java
public record BreakoutSignal(
    String tokenAddress,
    String symbol,
    String platform,
    BreakoutType type,
    double priceChangePercent,
    double volumeUsd,
    int holderCount,
    int holderGrowthPercent,
    Instant detectedAt
) {}

public enum BreakoutType {
    VOLUME_SPIKE,      // Volume > 10x average
    PRICE_SURGE,       // Price +100% in < 1h
    HOLDER_GROWTH,     // Holders +50% in < 1h
    WHALE_ENTRY,       // Large wallet buys in
    TRENDING,          // Appears on platform trending
    KOL_MENTION        // Mentioned by crypto influencer
}
```

**Automated Deep Research (AI-Powered):**

When a breakout is detected, trigger automated research:

```java
// TokenResearchAgent.java
public class TokenResearchAgent {

    public TokenResearchReport research(String tokenAddress, String chain) {
        // 1. Fetch on-chain data
        TokenInfo token = fetchTokenInfo(tokenAddress);

        // 2. Analyze contract (via Claude CLI)
        ContractAnalysis contract = analyzeContract(token.contractCode());

        // 3. Check socials
        SocialPresence socials = checkSocials(token.twitter(), token.telegram());

        // 4. Lookup team/deployer
        DeployerHistory deployer = analyzeDeployer(token.deployerAddress());

        // 5. Generate AI summary
        String prompt = """
            Analyze this new token for potential red flags and opportunity:

            Token: %s (%s)
            Chain: %s
            Contract: %s
            Holders: %d
            Top 10 wallet concentration: %.1f%%
            Deployer history: %d previous tokens, %d rugs

            Social presence:
            - Twitter: %s followers, %s account age
            - Telegram: %s members

            Contract flags: %s

            Provide:
            1. Risk score (1-10)
            2. Red flags found
            3. Potential legitimate use case
            4. Recommendation (AVOID / CAUTIOUS / WORTH_WATCHING)
            """.formatted(...);

        return runCliAnalysis(prompt);
    }
}
```

**Research Report Output:**

```java
record TokenResearchReport(
    String tokenAddress,
    String symbol,
    String chain,

    // Risk assessment
    int riskScore,              // 1-10 (10 = highest risk)
    List<String> redFlags,      // "Honeypot detected", "Deployer rugged 3 tokens"
    List<String> positives,     // "Verified contract", "Active community"

    // On-chain analysis
    int holderCount,
    double top10Concentration,  // % held by top 10 wallets
    boolean isHoneypot,
    boolean isMintable,
    boolean hasTaxes,
    double buyTaxPercent,
    double sellTaxPercent,

    // Deployer analysis
    String deployerAddress,
    int deployerPreviousTokens,
    int deployerRugCount,
    double deployerSuccessRate,

    // Social analysis
    String twitterHandle,
    int twitterFollowers,
    String telegramLink,
    int telegramMembers,

    // AI recommendation
    String recommendation,      // AVOID, CAUTIOUS, WORTH_WATCHING, PROMISING
    String summary,             // AI-generated summary paragraph

    Instant researchedAt
)
```

**Integration with Graph:**

Breakout tokens appear in the network graph:
- Node color: Risk score (red=high risk, green=low risk)
- Edges to: Deployer's other tokens, mentioned influencers, related projects
- Timeline: Launch date → breakout detection → research completion

**Configuration:**

```yaml
# ~/.tradery/cryptonews/config.yaml
tokenTracker:
  enabled: true
  platforms:
    - id: pumpfun
      enabled: true
      websocket: wss://pump.fun/ws
      minVolumeUsd: 10000
    - id: raydium
      enabled: true
      rpc: https://api.mainnet-beta.solana.com

  breakoutRules:
    priceChangePercent: 100    # +100% triggers alert
    volumeMultiplier: 10       # 10x normal volume
    holderGrowthPercent: 50    # +50% holders in 1h
    minVolumeUsd: 5000         # Minimum volume to consider

  autoResearch:
    enabled: true
    triggerOn: [VOLUME_SPIKE, PRICE_SURGE, KOL_MENTION]
    aiProvider: claude         # Use Claude CLI for analysis
    contractAnalysis: true     # Analyze smart contract
    deployerHistory: true      # Check deployer's track record
```

**UI: Token Tracker Panel**

```
┌─ Token Tracker ──────────────────────────────────────────────────┐
│ ┌─ Live Feed ────────────────────────────────────────────────┐   │
│ │ 🚀 $PEPE2 +340% | Vol: $45K | Holders: 234 (+89%)          │   │
│ │ ⚠️  $SCAM -99% | HONEYPOT DETECTED | Avoid                 │   │
│ │ 📊 $BASED +120% | Research: Low Risk (3/10) | Watching     │   │
│ └────────────────────────────────────────────────────────────┘   │
│                                                                   │
│ ┌─ Research Queue ───────────────────────────────────────────┐   │
│ │ $NEWCOIN - Analyzing contract... (2/5 steps)               │   │
│ │ $MEME - Checking deployer history... (4/5 steps)           │   │
│ └────────────────────────────────────────────────────────────┘   │
│                                                                   │
│ ┌─ Recent Reports ───────────────────────────────────────────┐   │
│ │ Token      Risk  Holders  Recommendation   Report          │   │
│ │ $BASED    3/10   1,234    WORTH_WATCHING   [View]          │   │
│ │ $RUGGED   9/10   45       AVOID            [View]          │   │
│ │ $LEGIT    2/10   5,678    PROMISING        [View]          │   │
│ └────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 5. Future: APIs & Premium Sources

| Source | Type | Cost | Data |
|--------|------|------|------|
| CoinGecko | API | Free tier | News, prices, metadata |
| Messari | API | Paid | Research, intel |
| Santiment | API | Paid | Social sentiment data |
| LunarCrush | API | Paid | Social metrics |
| The TIE | API | Enterprise | Sentiment analytics |

### Source Configuration

```yaml
# ~/.tradery/cryptonews/config.yaml
sources:
  rss:
    enabled: true
    feeds:
      - id: coindesk
        url: https://www.coindesk.com/arc/outboundfeeds/rss/
        interval: 5m
      - id: cointelegraph
        url: https://cointelegraph.com/rss
        interval: 5m

  search:
    enabled: true
    google:
      enabled: true
      queries:
        - "bitcoin cryptocurrency news"
        - "ethereum defi news"
        - "crypto regulation SEC"
      interval: 15m

  twitter:
    enabled: false  # Disabled by default (requires setup)
    method: nitter  # nitter | api | snscrape
    queries:
      - "from:whale_alert"
      - "$BTC min_faves:500"
    accounts:
      - coindesk
      - whale_alert
      - VitalikButerin
    interval: 10m
```

### Fetcher Architecture

```java
// NewsFetcher.java - Common interface
public interface NewsFetcher {
    String getSourceId();
    SourceType getSourceType();  // RSS, SEARCH, SOCIAL
    List<Article> fetchLatest(int limit);
    boolean isEnabled();
}

// Implementations
public class RssFetcher implements NewsFetcher { }
public class GoogleSearchFetcher implements NewsFetcher { }
public class XSearchFetcher implements NewsFetcher { }

// Registry manages all fetchers
public class FetcherRegistry {
    private final List<NewsFetcher> fetchers = new ArrayList<>();

    public void registerFromConfig(NewsConfig config) {
        // Create fetchers based on enabled sources
    }

    public List<Article> fetchAll() {
        return fetchers.parallelStream()
            .filter(NewsFetcher::isEnabled)
            .flatMap(f -> f.fetchLatest(20).stream())
            .distinct()  // Dedupe by URL
            .toList();
    }
}

---

## UI Components

### Main Window (Standalone)

```
┌─ CryptoNews ──────────────────────────────────────────────────────┐
│ ┌────────┬────────┬──────────┬─────────┬──────────┬──────────┐   │
│ │  Feed  │ Events │ Entities │  Graph  │ Timeline │ Screener │   │
│ └────────┴────────┴──────────┴─────────┴──────────┴──────────┘   │
│ ┌─────────────────────────────────────────────────────────────┐   │
│ │                                                             │   │
│ │                   [Active Tab Content]                      │   │
│ │                                                             │   │
│ │                                                             │   │
│ └─────────────────────────────────────────────────────────────┘   │
│ ┌─ Status ─────────────────────────────────────────────────────┐  │
│ │ Last fetch: 2 min ago │ 1,234 articles │ 567 events │ 89 entities │
│ └─────────────────────────────────────────────────────────────────┘│
└───────────────────────────────────────────────────────────────────┘
```

### Graph Panel

```
┌─ Graph ────────────────────────────────────────────────────────────┐
│ ┌─ Toolbar ──────────────────────────────────────────────────────┐ │
│ │ [Zoom+] [Zoom-] [Fit] │ Layout: [Force ▼] │ Filter: [All ▼]    │ │
│ └────────────────────────────────────────────────────────────────┘ │
│ ┌─ Canvas ───────────────────────────────────────────────────────┐ │
│ │                                                                 │ │
│ │         ◯────────◯                                             │ │
│ │        /          \                                            │ │
│ │       ◯            ●─────●                                     │ │
│ │        \          /                                            │ │
│ │         ◯────────◯                                             │ │
│ │                                                                 │ │
│ └─────────────────────────────────────────────────────────────────┘ │
│ ┌─ Legend ───────────────────────────────────────────────────────┐ │
│ │ ● Coin  ◯ Company  ◆ Person  ■ Event                           │ │
│ └────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

### Filter Bar (All Panels)

```
┌─ Filters ─────────────────────────────────────────────────────────┐
│ Importance: [🔴] [🟠] [🟡] [ ] [ ]  │  Coins: [BTC ×] [ETH ×] [+] │
│ Tags: [#etf ×] [#regulation ×] [+]  │  Time: [Last 24h ▼]         │
│ Sources: [All ▼]  │  Categories: [Regulatory ×] [Institutional ×] │
└───────────────────────────────────────────────────────────────────┘

Example queries:
• "Show me all CRITICAL news for BTC tagged #etf"
• "ETH news in last 7 days, HIGH importance or above"
• "All regulatory news affecting SOL from CoinDesk"
```

### Tradery Integration

Compact panel showing sentiment for the strategy's symbol:

```
┌─ News Sentiment ─────────────────────────┐
│ BTC: ████████░░ +0.62 (Positive)         │
│ Recent: "ETF inflows hit new high"       │
│ Events: 12 in 24h (3 regulatory)         │
│                        [Open Full View]  │
└──────────────────────────────────────────┘
```

---

## MCP Tools

For Claude Desktop integration:

| Tool | Description |
|------|-------------|
| `cryptonews_list_events` | List events with filters (type, entity, sentiment, time) |
| `cryptonews_get_event` | Get event details with related articles |
| `cryptonews_get_entity` | Get entity info and connections |
| `cryptonews_get_sentiment` | Get sentiment summary for entity or market |
| `cryptonews_search` | Full-text search across articles and events |
| `cryptonews_get_graph` | Get graph data (nodes, edges) for custom rendering |
| `cryptonews_create_alert` | Create sentiment/event alert |
| `cryptonews_fetch_now` | Trigger immediate news fetch |

---

## Implementation Phases

### Phase 1: Foundation
- [ ] Gradle submodule setup (`cryptonews/`)
- [ ] Data models with tagging support
- [ ] SQLite schema and stores
- [ ] RSS fetching (CoinDesk, CoinTelegraph, The Block, Decrypt)
- [ ] Background fetch scheduler

### Phase 2: Expanded Sources
- [ ] Google News search fetcher (googler CLI or HTTP)
- [ ] X.com/Twitter search fetcher (Nitter or snscrape)
- [ ] Source configuration system
- [ ] Deduplication across sources

### Phase 3: AI Processing & Tagging
- [ ] Claude CLI processor
- [ ] OpenAI CLI processor (Codex)
- [ ] Extraction prompts (events, entities, tags, importance)
- [ ] Processing pipeline with queue
- [ ] Provider configuration (settings UI)

### Phase 4: Basic UI
- [ ] CryptoNewsFrame (main window)
- [ ] NewsFeedPanel with importance filtering
- [ ] EventsPanel (event list by tag/coin)
- [ ] EntitiesPanel (entity browser)
- [ ] Tag filter bar
- [ ] Theme integration

### Phase 5: Graph Visualization
- [ ] JGraphX integration
- [ ] GraphPanel with controls
- [ ] Node styling by importance/type
- [ ] Layout algorithms (force, hierarchical)
- [ ] Filtering by tags/coins
- [ ] Export (PNG, JSON)

### Phase 6: Token Tracker
- [ ] pump.fun WebSocket client
- [ ] Breakout detection rules
- [ ] TokenResearchAgent (AI-powered)
- [ ] Contract analysis integration
- [ ] Deployer history lookup
- [ ] Token Tracker Panel UI

### Phase 7: Timeline & Screener
- [ ] TimelinePanel (Gantt-style)
- [ ] SentimentScreener by coin
- [ ] Alert system (importance, tags, coins)
- [ ] Price correlation finder

### Phase 8: Integration & Polish
- [ ] Tradery integration panel
- [ ] MCP server tools (15+ tools)
- [ ] CLI commands for manual fetch/research
- [ ] Full documentation
- [ ] Performance optimization

---

## Dependencies

```groovy
// CryptoNews/build.gradle (standalone project)
plugins {
    id 'java'
    id 'application'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = 'com.cryptonews.CryptoNewsApp'
}

dependencies {
    // HTTP client
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    // RSS parsing
    implementation 'com.rometools:rome:2.1.0'

    // HTML extraction & web scraping
    implementation 'org.jsoup:jsoup:1.17.2'

    // JSON parsing
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0'

    // SQLite
    implementation 'org.xerial:sqlite-jdbc:3.45.1.0'

    // Graph visualization
    implementation 'com.github.jgraph:jgraphx:4.2.2'

    // WebSocket for pump.fun, etc.
    implementation 'org.java-websocket:Java-WebSocket:1.5.6'

    // Swing look and feel
    implementation 'com.formdev:flatlaf:3.4'
    implementation 'com.formdev:flatlaf-intellij-themes:3.4'

    // Charting (optional, for timeline)
    implementation 'org.jfree:jfreechart:1.5.4'
}
```

**External CLI Tools (Optional):**
```bash
# Google search from terminal
brew install googler

# DuckDuckGo search
pip install ddgr

# Twitter/X scraping (Python)
pip install snscrape
```

---

## Open Questions

1. **Future event prediction**: Should AI attempt to predict upcoming events based on patterns?
2. **Social media**: Add Twitter/X as a source? (API costs)
3. **Multi-language**: Support non-English news sources?
4. **Collaborative**: Share graphs/alerts between users?