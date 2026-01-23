# AlpineBull Integration - Strategy Sharing & Bot Tracking Platform

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Data Models](#data-models)
4. [API Contracts](#api-contracts)
5. [Version Tree Model](#version-tree-model)
6. [Implementation Phases](#implementation-phases)
7. [Database Schema](#database-schema)
8. [Security Considerations](#security-considerations)
9. [Verification Plan](#verification-plan)
10. [File Inventory](#file-inventory)

---

## Overview

This document describes the integration between **Tradery** (desktop backtester) and **AlpineBull.com** (web platform) to enable:

1. **User Authentication** via Keycloak SSO (self-hosted Docker)
2. **Social Features** (friends, activity feed)
3. **Strategy Sharing** with Git-like version trees
4. **Live Bot Deployment Tracking** (bot execution nodes are a separate project - we build the API/monitoring)

### Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Authentication | Self-hosted Keycloak via Docker Compose | Full control, supports device flow for desktop apps |
| Bot Nodes | External/separate project | Separation of concerns - this plan covers reporting API and monitoring UI only |
| Scope | Full feature set | Auth + Social + Strategies + Bot Tracking |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    AlpineBull.com (Hub)                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ Keycloak │  │  Users/  │  │ Shared   │  │   Bot    │            │
│  │   SSO    │  │ Friends  │  │Strategies│  │ Tracking │            │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘            │
└───────┼─────────────┼─────────────┼─────────────┼──────────────────┘
        │             │             │             │
   ┌────┴────┐   ┌────┴────┐   ┌────┴────┐   ┌────┴────┐
   │ Tradery │   │   Web   │   │  Other  │   │   Bot   │
   │(desktop)│   │   UI    │   │ Tradery │   │  Nodes  │
   └─────────┘   └─────────┘   └─────────┘   └─────────┘
```

### Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| **Keycloak SSO** | Authentication, identity providers (Google, GitHub), token management |
| **Users/Friends** | User profiles, friend relationships, privacy settings |
| **Shared Strategies** | Strategy publishing, versioning, forking, discovery |
| **Bot Tracking** | Bot registration, heartbeat monitoring, trade recording |
| **Tradery (desktop)** | Local backtesting, strategy development, sync with AlpineBull |
| **Web UI** | Browser-based strategy browsing, bot monitoring, social features |
| **Bot Nodes** | External project - executes strategies on exchanges |

### Technology Stack

| Layer | Technology |
|-------|------------|
| **AlpineBull Backend** | Spring Boot 3.x, Spring Security OAuth2 Resource Server |
| **AlpineBull Frontend** | React/Vue (TBD), TypeScript |
| **Database** | PostgreSQL with JSONB for strategy storage |
| **Auth** | Keycloak 24.x |
| **Reverse Proxy** | Traefik with Let's Encrypt |
| **Tradery Desktop** | Java 21, Swing UI, OkHttp |

---

## Data Models

### AlpineBull Backend Entities

#### UserProfile

Represents a user in the AlpineBull system. The `id` is the Keycloak subject ID.

```java
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    private UUID id;                    // Keycloak subject ID

    @Column(unique = true, nullable = false, length = 50)
    private String username;            // Unique, changeable

    @Column(length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProfileVisibility visibility = ProfileVisibility.PUBLIC;

    private Instant createdAt = Instant.now();

    // Denormalized stats for performance
    private int sharedStrategyCount = 0;
    private int followerCount = 0;
    private int activeBotCount = 0;
}

public enum ProfileVisibility {
    PUBLIC,         // Anyone can see profile and strategies
    FRIENDS_ONLY,   // Only accepted friends
    PRIVATE         // Only self
}
```

#### FriendRelationship

Bidirectional friend relationships with request/accept flow.

```java
@Entity
@Table(name = "friend_relationships",
       uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
public class FriendRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private UserProfile requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private UserProfile addressee;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FriendStatus status = FriendStatus.PENDING;

    private Instant requestedAt = Instant.now();
    private Instant respondedAt;
}

public enum FriendStatus {
    PENDING,    // Request sent, awaiting response
    ACCEPTED,   // Both users are friends
    BLOCKED     // Requester blocked by addressee
}
```

#### SharedStrategy

Core entity for strategy sharing. Supports versioning and forking.

```java
@Entity
@Table(name = "shared_strategies",
       uniqueConstraints = @UniqueConstraint(columnNames = {"root_strategy_id", "version_id"}))
public class SharedStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // === Version Tree ===

    @Column(name = "version_id", nullable = false, length = 20)
    private String versionId;           // "1.0.0", "1.0.1", etc.

    @Column(name = "root_strategy_id")
    private UUID rootStrategyId;        // Original in tree (self for originals)

    @Column(name = "parent_version_id")
    private UUID parentVersionId;       // Previous version in this tree

    // === Fork Tracking ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forked_from_id")
    private SharedStrategy forkedFrom;  // Source strategy if forked

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_author_id")
    private UserProfile originalAuthor; // Attribution chain (preserved through forks)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserProfile author;         // Current author

    // === Content (Immutable Snapshot) ===

    @Column(name = "strategy_json", columnDefinition = "jsonb", nullable = false)
    private String strategyJson;        // Full strategy YAML as JSON

    @Column(name = "phases_json", columnDefinition = "jsonb")
    private String phasesJson;          // Custom phases bundle

    @Column(name = "hoops_json", columnDefinition = "jsonb")
    private String hoopsJson;           // Custom hoops bundle

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;         // SHA-256 for integrity/divergence detection

    // === Metadata ===

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String symbol;              // e.g., "BTCUSDT"

    @Column(length = 10)
    private String timeframe;           // e.g., "1h", "4h"

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StrategyVisibility visibility = StrategyVisibility.PUBLIC;

    // === Performance at Publish ===

    @Column(name = "backtest_metrics_json", columnDefinition = "jsonb")
    private String backtestMetricsJson;

    private Instant publishedAt = Instant.now();

    // === Denormalized Stats ===

    private int forkCount = 0;
    private int starCount = 0;
    private int deployedBotCount = 0;
}

public enum StrategyVisibility {
    PUBLIC,         // Anyone can see and fork
    FRIENDS_ONLY,   // Only friends can see and fork
    PRIVATE,        // Only author can see
    UNLISTED        // Accessible via direct link only
}
```

#### StrategyStar

Tracks which users have starred which strategies.

```java
@Entity
@Table(name = "strategy_stars",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "strategy_id"}))
public class StrategyStar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false)
    private SharedStrategy strategy;

    private Instant starredAt = Instant.now();
}
```

#### Bot

Represents a registered bot instance.

```java
@Entity
@Table(name = "bots")
public class Bot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserProfile owner;

    // === Authentication ===

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;          // bcrypt hash

    @Column(name = "api_key_prefix", nullable = false, length = 8)
    private String apiKeyPrefix;        // For display: "bot_abc1..."

    // === Configuration ===

    @Column(length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pinned_strategy_id")
    private SharedStrategy pinnedStrategy;

    @Column(name = "pinned_version_id", length = 20)
    private String pinnedVersionId;     // Exact version to run

    // === Status ===

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BotStatus status = BotStatus.REGISTERED;

    @Column(name = "node_identifier", length = 100)
    private String nodeIdentifier;      // Which node is running this bot

    // === Trading Config ===

    @Column(length = 50)
    private String exchange;            // "binance", "bybit", etc.

    @Column(name = "trading_pair", length = 20)
    private String tradingPair;         // "BTCUSDT"

    @Column(name = "capital_allocated", precision = 20, scale = 8)
    private BigDecimal capitalAllocated;

    @Column(name = "paper_trading")
    private boolean paperTrading = false;

    // === Monitoring ===

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    private Instant createdAt = Instant.now();
}

public enum BotStatus {
    REGISTERED,     // Created but never started
    RUNNING,        // Active and sending heartbeats
    PAUSED,         // Temporarily stopped by user
    STOPPED,        // Permanently stopped
    ERROR           // Error state, needs attention
}
```

#### BotTrade

Individual trades executed by a bot.

```java
@Entity
@Table(name = "bot_trades",
       uniqueConstraints = @UniqueConstraint(columnNames = {"bot_id", "exchange_trade_id"}))
public class BotTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;

    @Column(name = "exchange_trade_id", length = 100)
    private String exchangeTradeId;

    // === Trade Details ===

    @Column(length = 10)
    private String side;                // "LONG", "SHORT"

    @Column(name = "entry_time")
    private Instant entryTime;

    @Column(name = "entry_price", precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal quantity;

    // === Exit (nullable for open trades) ===

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(name = "exit_price", precision = 20, scale = 8)
    private BigDecimal exitPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal pnl;

    @Column(name = "pnl_percent", precision = 10, scale = 4)
    private BigDecimal pnlPercent;

    @Column(name = "exit_reason", length = 50)
    private String exitReason;          // "STOP_LOSS", "TAKE_PROFIT", "CONDITION", "MANUAL"

    // === Analysis ===

    @Column(name = "matches_backtest")
    private Boolean matchesBacktest;    // Compare to expected behavior

    private Instant recordedAt = Instant.now();
}
```

#### BotMetricSnapshot

Periodic snapshots of bot performance metrics.

```java
@Entity
@Table(name = "bot_metric_snapshots")
public class BotMetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;

    private Instant timestamp;

    @Column(precision = 20, scale = 8)
    private BigDecimal equity;

    @Column(name = "unrealized_pnl", precision = 20, scale = 8)
    private BigDecimal unrealizedPnl;

    @Column(name = "open_position_count")
    private int openPositionCount;

    @Column(name = "total_trades")
    private int totalTrades;

    @Column(name = "win_rate", precision = 5, scale = 2)
    private BigDecimal winRate;

    @Column(name = "profit_factor", precision = 10, scale = 4)
    private BigDecimal profitFactor;
}
```

#### ActivityEvent

Activity feed events for social features.

```java
@Entity
@Table(name = "activity_events")
public class ActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;           // Who performed the action

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ActivityType type;

    // Polymorphic references (nullable based on type)
    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_strategy_id")
    private UUID targetStrategyId;

    @Column(name = "target_bot_id")
    private UUID targetBotId;

    @Column(columnDefinition = "jsonb")
    private String metadata;            // Additional context

    private Instant createdAt = Instant.now();
}

public enum ActivityType {
    STRATEGY_PUBLISHED,
    STRATEGY_UPDATED,
    STRATEGY_FORKED,
    STRATEGY_STARRED,
    BOT_STARTED,
    BOT_STOPPED,
    BOT_TRADE_CLOSED,
    FRIEND_ADDED
}
```

### Tradery Desktop Files

#### ~/.tradery/account.json

Stores the user's AlpineBull account link and tokens.

```json
{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "trader123",
    "displayName": "Trader One",
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
    "tokenExpiresAt": "2024-01-15T10:30:00Z",
    "linkedAt": "2024-01-01T12:00:00Z"
}
```

#### ~/.tradery/strategies/{id}/upstream.json

Links a local strategy to its AlpineBull upstream.

```json
{
    "alpineBullStrategyId": "550e8400-e29b-41d4-a716-446655440001",
    "alpineBullVersionId": "1.0.2",
    "contentHash": "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd",
    "linkedAt": "2024-01-05T08:00:00Z",
    "lastSyncedAt": "2024-01-10T14:30:00Z",
    "hasLocalChanges": false,
    "upstreamName": "RSI Reversal Strategy",
    "upstreamAuthor": "trader456"
}
```

---

## API Contracts

### Authentication Endpoints

#### Device Flow (for Desktop Apps)

The OAuth 2.0 Device Authorization Grant allows Tradery desktop to authenticate users without handling passwords directly.

**Step 1: Initiate Device Login**

```http
POST /api/auth/device-login
Content-Type: application/json

{
    "clientId": "tradery-desktop"
}
```

Response:
```json
{
    "deviceCode": "GmRhmhcxhwAzkoEqiMEg_DnyEysNkuNhszIySk9eS",
    "userCode": "WDJB-MJHT",
    "verificationUrl": "https://auth.alpinebull.com/realms/alpinebull/device",
    "verificationUrlComplete": "https://auth.alpinebull.com/realms/alpinebull/device?user_code=WDJB-MJHT",
    "expiresIn": 600,
    "pollInterval": 5
}
```

**Step 2: Poll for Completion**

```http
POST /api/auth/device-poll
Content-Type: application/json

{
    "deviceCode": "GmRhmhcxhwAzkoEqiMEg_DnyEysNkuNhszIySk9eS"
}
```

Response (pending):
```json
{
    "status": "pending"
}
```

Response (success):
```json
{
    "status": "success",
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
    "expiresIn": 300,
    "user": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "username": "trader123",
        "displayName": "Trader One",
        "avatarUrl": "https://avatars.alpinebull.com/trader123.jpg"
    }
}
```

Response (denied):
```json
{
    "status": "denied",
    "error": "access_denied",
    "errorDescription": "User denied the authorization request"
}
```

**Step 3: Refresh Token**

```http
POST /api/auth/refresh
Content-Type: application/json

{
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

Response:
```json
{
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 300
}
```

**Step 4: Logout**

```http
POST /api/auth/logout
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

### User Profile Endpoints

**Get Current User**

```http
GET /api/users/me
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "trader123",
    "displayName": "Trader One",
    "bio": "Crypto trader since 2017",
    "avatarUrl": "https://avatars.alpinebull.com/trader123.jpg",
    "visibility": "PUBLIC",
    "createdAt": "2024-01-01T12:00:00Z",
    "stats": {
        "sharedStrategyCount": 5,
        "followerCount": 42,
        "activeBotCount": 2
    }
}
```

**Update Profile**

```http
PATCH /api/users/me
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "displayName": "Trader One Pro",
    "bio": "Full-time crypto trader",
    "visibility": "FRIENDS_ONLY"
}
```

**Get User by Username**

```http
GET /api/users/{username}
Authorization: Bearer {accessToken}
```

**Search Users**

```http
GET /api/users?q=trader&page=0&size=20
Authorization: Bearer {accessToken}
```

### Friend Endpoints

**Send Friend Request**

```http
POST /api/friends/request
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "addresseeId": "550e8400-e29b-41d4-a716-446655440001"
}
```

**Respond to Friend Request**

```http
POST /api/friends/respond
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "requestId": "550e8400-e29b-41d4-a716-446655440002",
    "accept": true
}
```

**Get Friends List**

```http
GET /api/friends?status=ACCEPTED&page=0&size=50
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "friends": [
        {
            "relationshipId": "...",
            "user": {
                "id": "...",
                "username": "trader456",
                "displayName": "Trader Four",
                "avatarUrl": "..."
            },
            "since": "2024-01-05T10:00:00Z"
        }
    ],
    "totalCount": 15,
    "page": 0,
    "size": 50
}
```

**Get Pending Requests**

```http
GET /api/friends/requests?direction=incoming
Authorization: Bearer {accessToken}
```

**Remove Friend / Block User**

```http
DELETE /api/friends/{relationshipId}
Authorization: Bearer {accessToken}

POST /api/friends/block
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "userId": "550e8400-e29b-41d4-a716-446655440001"
}
```

### Strategy Sharing Endpoints

**Publish Strategy**

```http
POST /api/strategies/publish
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "name": "RSI Reversal Strategy",
    "description": "Buys oversold conditions in uptrends",
    "visibility": "PUBLIC",
    "strategy": {
        "id": "rsi-reversal",
        "name": "RSI Reversal",
        "entrySettings": {
            "condition": "RSI(14) < 30 AND price > SMA(200)",
            "maxOpenTrades": 1
        },
        "exitSettings": {
            "zones": [
                {
                    "name": "Stop Loss",
                    "maxPnlPercent": -5.0,
                    "exitImmediately": true
                }
            ]
        },
        "backtestSettings": {
            "symbol": "BTCUSDT",
            "timeframe": "1h",
            "duration": "6m"
        }
    },
    "customPhases": [
        {
            "id": "my-trend-filter",
            "name": "My Trend Filter",
            "condition": "ADX(14) > 30",
            "timeframe": "4h"
        }
    ],
    "customHoops": [],
    "backtestMetrics": {
        "winRate": 62.5,
        "profitFactor": 2.1,
        "sharpeRatio": 1.85,
        "maxDrawdownPercent": 8.2,
        "totalTrades": 48
    }
}
```

Response:
```json
{
    "id": "550e8400-e29b-41d4-a716-446655440010",
    "versionId": "1.0.0",
    "contentHash": "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd",
    "publishedAt": "2024-01-10T15:00:00Z"
}
```

**Publish New Version**

```http
POST /api/strategies/{id}/versions
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "changeNotes": "Tightened stop loss from 5% to 3%",
    "strategy": { ... },
    "customPhases": [ ... ],
    "customHoops": [ ... ],
    "backtestMetrics": { ... }
}
```

Response:
```json
{
    "versionId": "1.0.1",
    "contentHash": "b2c3d4e5f6789012345678901234567890123456789012345678901234abcde",
    "publishedAt": "2024-01-12T09:00:00Z"
}
```

**Fork Strategy**

```http
POST /api/strategies/{id}/fork
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "name": "My Modified RSI Strategy",
    "visibility": "PRIVATE"
}
```

Response:
```json
{
    "forkedStrategyId": "550e8400-e29b-41d4-a716-446655440020",
    "versionId": "1.0.0",
    "contentHash": "...",
    "forkedFrom": {
        "id": "550e8400-e29b-41d4-a716-446655440010",
        "name": "RSI Reversal Strategy",
        "author": "trader123"
    }
}
```

**Browse Strategies**

```http
GET /api/strategies?visibility=PUBLIC&symbol=BTCUSDT&timeframe=1h&sort=stars&page=0&size=20
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "strategies": [
        {
            "id": "550e8400-e29b-41d4-a716-446655440010",
            "name": "RSI Reversal Strategy",
            "description": "Buys oversold conditions...",
            "author": {
                "id": "...",
                "username": "trader123",
                "displayName": "Trader One"
            },
            "symbol": "BTCUSDT",
            "timeframe": "1h",
            "currentVersionId": "1.0.1",
            "visibility": "PUBLIC",
            "metrics": {
                "winRate": 62.5,
                "profitFactor": 2.1
            },
            "stats": {
                "forkCount": 5,
                "starCount": 23,
                "deployedBotCount": 3
            },
            "publishedAt": "2024-01-10T15:00:00Z"
        }
    ],
    "totalCount": 156,
    "page": 0,
    "size": 20
}
```

**Get Strategy Details**

```http
GET /api/strategies/{id}
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "id": "550e8400-e29b-41d4-a716-446655440010",
    "name": "RSI Reversal Strategy",
    "description": "Buys oversold conditions in uptrends",
    "author": { ... },
    "originalAuthor": { ... },
    "forkedFrom": null,
    "currentVersion": {
        "versionId": "1.0.1",
        "contentHash": "...",
        "publishedAt": "2024-01-12T09:00:00Z",
        "changeNotes": "Tightened stop loss"
    },
    "strategy": { ... },
    "customPhases": [ ... ],
    "customHoops": [ ... ],
    "backtestMetrics": { ... },
    "versions": [
        { "versionId": "1.0.0", "publishedAt": "2024-01-10T15:00:00Z" },
        { "versionId": "1.0.1", "publishedAt": "2024-01-12T09:00:00Z" }
    ],
    "stats": { ... }
}
```

**Get Strategy Version Tree**

```http
GET /api/strategies/{id}/tree
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "root": {
        "id": "550e8400-e29b-41d4-a716-446655440010",
        "name": "RSI Reversal Strategy",
        "author": "trader123",
        "versions": ["1.0.0", "1.0.1"],
        "forks": [
            {
                "id": "550e8400-e29b-41d4-a716-446655440020",
                "name": "My Modified RSI Strategy",
                "author": "trader456",
                "versions": ["1.0.0"],
                "forks": []
            }
        ]
    }
}
```

**Star/Unstar Strategy**

```http
POST /api/strategies/{id}/star
Authorization: Bearer {accessToken}

DELETE /api/strategies/{id}/star
Authorization: Bearer {accessToken}
```

**Get Specific Version**

```http
GET /api/strategies/{id}/versions/{versionId}
Authorization: Bearer {accessToken}
```

### Bot Management Endpoints

**Register Bot**

```http
POST /api/bots
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "name": "My BTC Bot",
    "strategyId": "550e8400-e29b-41d4-a716-446655440010",
    "versionId": "1.0.1",
    "exchange": "binance",
    "tradingPair": "BTCUSDT",
    "capitalAllocated": 1000.00,
    "paperTrading": false
}
```

Response:
```json
{
    "botId": "550e8400-e29b-41d4-a716-446655440030",
    "apiKey": "bot_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6",
    "apiKeyPrefix": "bot_a1b2",
    "createdAt": "2024-01-15T10:00:00Z"
}
```

> **Warning**: The `apiKey` is shown only once! Store it securely.

**List My Bots**

```http
GET /api/bots
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "bots": [
        {
            "id": "550e8400-e29b-41d4-a716-446655440030",
            "name": "My BTC Bot",
            "strategy": {
                "id": "550e8400-e29b-41d4-a716-446655440010",
                "name": "RSI Reversal Strategy",
                "versionId": "1.0.1"
            },
            "status": "RUNNING",
            "exchange": "binance",
            "tradingPair": "BTCUSDT",
            "capitalAllocated": 1000.00,
            "paperTrading": false,
            "lastHeartbeatAt": "2024-01-15T12:00:00Z",
            "currentMetrics": {
                "equity": 1050.00,
                "unrealizedPnl": 15.00,
                "totalTrades": 10,
                "winRate": 60.0
            }
        }
    ]
}
```

**Get Bot Details**

```http
GET /api/bots/{id}
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "id": "550e8400-e29b-41d4-a716-446655440030",
    "name": "My BTC Bot",
    "strategy": { ... },
    "status": "RUNNING",
    "exchange": "binance",
    "tradingPair": "BTCUSDT",
    "capitalAllocated": 1000.00,
    "paperTrading": false,
    "lastHeartbeatAt": "2024-01-15T12:00:00Z",
    "nodeIdentifier": "node-us-east-1",
    "currentMetrics": { ... },
    "recentTrades": [
        {
            "id": "...",
            "side": "LONG",
            "entryTime": "2024-01-15T08:00:00Z",
            "entryPrice": 42000.00,
            "quantity": 0.01,
            "exitTime": "2024-01-15T10:00:00Z",
            "exitPrice": 42500.00,
            "pnl": 5.00,
            "pnlPercent": 1.19,
            "exitReason": "TAKE_PROFIT"
        }
    ],
    "backtestComparison": {
        "backtestWinRate": 62.5,
        "liveWinRate": 60.0,
        "backtestProfitFactor": 2.1,
        "liveProfitFactor": 1.8,
        "divergenceWarnings": [
            "Win rate 2.5% below backtest"
        ]
    }
}
```

**Update Bot**

```http
PATCH /api/bots/{id}
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "name": "My Renamed Bot",
    "versionId": "1.0.2"
}
```

**Control Bot**

```http
POST /api/bots/{id}/pause
Authorization: Bearer {accessToken}

POST /api/bots/{id}/resume
Authorization: Bearer {accessToken}

POST /api/bots/{id}/stop
Authorization: Bearer {accessToken}
```

**Regenerate API Key**

```http
POST /api/bots/{id}/regenerate-key
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "apiKey": "bot_new_key_here...",
    "apiKeyPrefix": "bot_newk"
}
```

**Delete Bot**

```http
DELETE /api/bots/{id}
Authorization: Bearer {accessToken}
```

### Bot Reporting Endpoints (API Key Auth)

These endpoints are called by bot nodes, not users.

**Send Heartbeat**

```http
POST /api/bot-reporting/heartbeat
X-Bot-API-Key: bot_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6
Content-Type: application/json

{
    "timestamp": "2024-01-15T12:00:00Z",
    "status": "RUNNING",
    "equity": 1050.00,
    "unrealizedPnl": 15.00,
    "openPositions": [
        {
            "side": "LONG",
            "entryPrice": 42000.00,
            "quantity": 0.01,
            "currentPrice": 42150.00,
            "unrealizedPnl": 1.50
        }
    ],
    "nodeIdentifier": "node-us-east-1"
}
```

Response:
```json
{
    "acknowledged": true,
    "serverTime": "2024-01-15T12:00:01Z",
    "strategyUpdateAvailable": true,
    "latestVersionId": "1.0.2",
    "commands": []
}
```

**Report Trade**

```http
POST /api/bot-reporting/trade
X-Bot-API-Key: bot_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6
Content-Type: application/json

{
    "exchangeTradeId": "binance_12345678",
    "side": "LONG",
    "entryTime": "2024-01-15T08:00:00Z",
    "entryPrice": 42000.00,
    "quantity": 0.01,
    "exitTime": "2024-01-15T10:00:00Z",
    "exitPrice": 42500.00,
    "pnl": 5.00,
    "pnlPercent": 1.19,
    "exitReason": "TAKE_PROFIT"
}
```

Response:
```json
{
    "recorded": true,
    "tradeId": "550e8400-e29b-41d4-a716-446655440040"
}
```

**Report Error**

```http
POST /api/bot-reporting/error
X-Bot-API-Key: bot_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6
Content-Type: application/json

{
    "timestamp": "2024-01-15T12:00:00Z",
    "errorCode": "EXCHANGE_CONNECTION_LOST",
    "message": "Failed to connect to Binance API",
    "severity": "WARNING",
    "recoverable": true
}
```

### Activity Feed Endpoints

**Get Feed**

```http
GET /api/feed?page=0&size=50
Authorization: Bearer {accessToken}
```

Response:
```json
{
    "events": [
        {
            "id": "...",
            "type": "STRATEGY_PUBLISHED",
            "user": {
                "username": "trader456",
                "displayName": "Trader Four"
            },
            "strategy": {
                "id": "...",
                "name": "EMA Crossover Strategy"
            },
            "createdAt": "2024-01-15T14:00:00Z"
        },
        {
            "id": "...",
            "type": "BOT_TRADE_CLOSED",
            "user": {
                "username": "trader123",
                "displayName": "Trader One"
            },
            "bot": {
                "id": "...",
                "name": "My BTC Bot"
            },
            "metadata": {
                "pnlPercent": 2.5,
                "side": "LONG"
            },
            "createdAt": "2024-01-15T13:00:00Z"
        }
    ],
    "totalCount": 234,
    "page": 0
}
```

---

## Version Tree Model

### Versioning Scheme

Strategies use semantic versioning with the format `MAJOR.MINOR.PATCH`:

- **MAJOR**: Breaking changes (different entry/exit logic)
- **MINOR**: New features (added phases, new exit zones)
- **PATCH**: Bug fixes, parameter tweaks

### Fork Behavior

| Scenario | rootStrategyId | forkedFromId | versionId |
|----------|----------------|--------------|-----------|
| Original strategy | self | null | "1.0.0" |
| New version | same as v1 | null | "1.0.1" |
| Fork | new (self) | source strategy | "1.0.0" |
| Fork's new version | same as fork | null | "1.0.1" |

**Key Rules:**
- Forking creates a new `rootStrategyId` (new tree)
- `forkedFromId` points to the source strategy (for attribution)
- `originalAuthor` is preserved through the fork chain
- Forks always start at version `1.0.0`

### Content Hash Calculation

```java
public String calculateContentHash(Strategy strategy, List<Phase> phases, List<Hoop> hoops) {
    // Normalize: remove whitespace, sort keys, remove metadata
    String normalizedStrategy = normalizeJson(objectMapper.writeValueAsString(strategy));
    String normalizedPhases = normalizeJson(objectMapper.writeValueAsString(phases));
    String normalizedHoops = normalizeJson(objectMapper.writeValueAsString(hoops));

    String combined = normalizedStrategy + "|" + normalizedPhases + "|" + normalizedHoops;

    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(hash);
}
```

### Divergence Detection

When syncing between Tradery and AlpineBull:

```
Local contentHash != Upstream contentHash → DIVERGED

Scenarios:
1. Local changes only → Can push new version
2. Upstream changes only → Can pull (fast-forward)
3. Both changed → CONFLICT
```

### Conflict Resolution Options

| Option | Description |
|--------|-------------|
| `KEEP_LOCAL` | Discard upstream changes, push local as new version |
| `ACCEPT_UPSTREAM` | Overwrite local with upstream |
| `FORK` | Keep both as separate strategies (fork from upstream) |

### Example Version Tree

```
RSI Reversal (trader123)
├── v1.0.0 (initial)
├── v1.0.1 (tightened stop loss)
├── v1.1.0 (added trend filter)
│
└── Fork: Modified RSI (trader456)
    ├── v1.0.0 (forked from trader123's v1.1.0)
    └── v1.0.1 (adjusted RSI threshold)
        │
        └── Fork: Aggressive RSI (trader789)
            └── v1.0.0 (forked from trader456's v1.0.1)
```

---

## Implementation Phases

### Phase 1: Authentication Foundation

**Goal**: Enable Tradery desktop users to authenticate with AlpineBull via Keycloak SSO.

#### AlpineBull Backend Files

| File | Purpose |
|------|---------|
| `src/main/java/com/alpinebull/auth/KeycloakConfig.java` | Spring Security + OAuth2 resource server configuration |
| `src/main/java/com/alpinebull/auth/DeviceAuthController.java` | Device flow endpoints |
| `src/main/java/com/alpinebull/auth/DeviceAuthService.java` | Device code management, polling logic |
| `src/main/java/com/alpinebull/user/UserProfileController.java` | Profile CRUD REST endpoints |
| `src/main/java/com/alpinebull/user/UserProfileService.java` | User management business logic |
| `src/main/java/com/alpinebull/entity/UserProfile.java` | JPA entity |
| `src/main/java/com/alpinebull/repository/UserProfileRepository.java` | Spring Data repository |

#### Tradery Desktop Files

| File | Purpose |
|------|---------|
| `src/main/java/com/tradery/alpinebull/AlpineBullAuthService.java` | OAuth handling, token refresh |
| `src/main/java/com/tradery/alpinebull/model/AccountLink.java` | Token storage model (maps to account.json) |
| `src/main/java/com/tradery/ui/AlpineBullLoginDialog.java` | Device login UI with QR code |

#### Docker/Keycloak Setup

```yaml
# docker-compose.yml additions
services:
  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: ${DB_USER}
      KC_DB_PASSWORD: ${DB_PASSWORD}
      KC_HOSTNAME: auth.alpinebull.com
      KC_PROXY: edge
    command: start
    depends_on:
      - postgres
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.keycloak.rule=Host(`auth.alpinebull.com`)"
      - "traefik.http.routers.keycloak.tls.certresolver=letsencrypt"
      - "traefik.http.services.keycloak.loadbalancer.server.port=8080"

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
```

**Realm Configuration** (keycloak-realm.json):
- Realm name: `alpinebull`
- Clients:
  - `alpinebull-web` - SPA client (public, PKCE)
  - `alpinebull-backend` - Confidential client (service account)
  - `tradery-desktop` - Public client with device flow enabled
- Identity Providers:
  - Google (social login)
  - GitHub (social login)

### Phase 2: Social Features

**Goal**: Friends system and activity feed for community engagement.

#### AlpineBull Backend Files

| File | Purpose |
|------|---------|
| `src/main/java/com/alpinebull/user/FriendController.java` | Friend request/accept/block endpoints |
| `src/main/java/com/alpinebull/user/FriendService.java` | Friendship business logic |
| `src/main/java/com/alpinebull/entity/FriendRelationship.java` | JPA entity |
| `src/main/java/com/alpinebull/repository/FriendRelationshipRepository.java` | Custom queries for friend lookup |
| `src/main/java/com/alpinebull/feed/ActivityFeedController.java` | Feed retrieval endpoint |
| `src/main/java/com/alpinebull/feed/ActivityFeedService.java` | Feed aggregation, filtering |
| `src/main/java/com/alpinebull/entity/ActivityEvent.java` | JPA entity |
| `src/main/java/com/alpinebull/repository/ActivityEventRepository.java` | Feed queries |

#### AlpineBull Frontend Modules

| Module | Components |
|--------|------------|
| `modules/auth/` | Login page, registration, auth store, protected routes |
| `modules/user/` | Profile page, profile edit, avatar upload, settings |
| `modules/friends/` | Friends list, pending requests, user search, friend card |
| `modules/feed/` | Activity feed, event cards, infinite scroll |

### Phase 3: Strategy Sharing

**Goal**: Enable publishing, versioning, forking, and importing strategies.

#### AlpineBull Backend Files

| File | Purpose |
|------|---------|
| `src/main/java/com/alpinebull/strategy/SharedStrategyController.java` | Strategy CRUD, publish, fork endpoints |
| `src/main/java/com/alpinebull/strategy/SharedStrategyService.java` | Publishing, visibility checks |
| `src/main/java/com/alpinebull/strategy/StrategyVersionService.java` | Version management, content hashing |
| `src/main/java/com/alpinebull/entity/SharedStrategy.java` | JPA entity |
| `src/main/java/com/alpinebull/entity/StrategyStar.java` | JPA entity |
| `src/main/java/com/alpinebull/repository/SharedStrategyRepository.java` | Search, filter queries |

#### Tradery Desktop Files (Modified)

| File | Changes |
|------|---------|
| `src/main/java/com/tradery/io/StrategyStore.java` | Add upstream link support, load/save upstream.json |
| `src/main/java/com/tradery/api/StrategyHandler.java` | Add sync status endpoint |

#### Tradery Desktop Files (New)

| File | Purpose |
|------|---------|
| `src/main/java/com/tradery/alpinebull/AlpineBullClient.java` | HTTP client for AlpineBull API |
| `src/main/java/com/tradery/alpinebull/AlpineBullSyncService.java` | Push/pull/conflict resolution logic |
| `src/main/java/com/tradery/alpinebull/StrategyBundle.java` | Strategy + phases + hoops packaging |
| `src/main/java/com/tradery/alpinebull/model/UpstreamLink.java` | Maps to upstream.json |
| `src/main/java/com/tradery/ui/StrategyShareDialog.java` | Publish/update UI |
| `src/main/java/com/tradery/ui/AlpineBullBrowserDialog.java` | Browse/import strategies |

#### AlpineBull Frontend Modules

| Module | Components |
|--------|------------|
| `modules/strategies/` | Strategy browser, detail page, version tree visualization |
| `modules/strategies/browser/` | Search, filters, sort, pagination |
| `modules/strategies/detail/` | Strategy view, versions, forks, metrics |
| `modules/strategies/publish/` | Publish form, visibility settings |

### Phase 4: Bot Tracking

**Goal**: Bot registration, heartbeat monitoring, trade recording, performance comparison.

> **Note**: Bot execution nodes are a separate project. This phase builds the API and monitoring UI only.

#### AlpineBull Backend Files

| File | Purpose |
|------|---------|
| `src/main/java/com/alpinebull/bot/BotController.java` | Bot registration, management endpoints |
| `src/main/java/com/alpinebull/bot/BotService.java` | Bot CRUD, API key generation |
| `src/main/java/com/alpinebull/bot/BotReportingController.java` | Heartbeat, trade ingestion endpoints |
| `src/main/java/com/alpinebull/bot/BotApiKeyAuthFilter.java` | API key authentication filter |
| `src/main/java/com/alpinebull/bot/BotMetricsService.java` | Metrics aggregation, comparison |
| `src/main/java/com/alpinebull/entity/Bot.java` | JPA entity |
| `src/main/java/com/alpinebull/entity/BotTrade.java` | JPA entity |
| `src/main/java/com/alpinebull/entity/BotMetricSnapshot.java` | JPA entity |
| `src/main/java/com/alpinebull/repository/BotRepository.java` | Bot queries |
| `src/main/java/com/alpinebull/repository/BotTradeRepository.java` | Trade history queries |

#### AlpineBull Frontend Modules

| Module | Components |
|--------|------------|
| `modules/bots/` | Bot dashboard, registration form |
| `modules/bots/detail/` | Bot detail, live status, controls |
| `modules/bots/trades/` | Trade history, filtering |
| `modules/bots/comparison/` | Live vs backtest metrics comparison |

#### Tradery Desktop Files

| File | Purpose |
|------|---------|
| `src/main/java/com/tradery/ui/BotDashboardDialog.java` | View bot status from desktop |

#### Bot Node Integration Contract

External bot nodes must:

1. **Register** via `POST /api/bots` (user provides API key to bot)
2. **Send heartbeats** via `POST /api/bot-reporting/heartbeat` (every 60 seconds)
3. **Report trades** via `POST /api/bot-reporting/trade` (on entry and exit)
4. **Check for updates** via heartbeat response (`strategyUpdateAvailable` flag)

### Phase 5: MCP Integration & Polish

**Goal**: Add MCP tools for Claude Code integration, polish overall experience.

#### Tradery MCP Server Additions

| Tool | Purpose |
|------|---------|
| `alpinebull_login` | Initiate device login flow |
| `alpinebull_logout` | Clear account link |
| `alpinebull_status` | Check login status, show username |
| `alpinebull_publish` | Publish current strategy to AlpineBull |
| `alpinebull_import` | Import strategy from AlpineBull by ID |
| `alpinebull_sync_status` | Check for local changes, upstream updates |
| `alpinebull_pull` | Pull upstream changes |
| `alpinebull_push` | Push local changes as new version |
| `alpinebull_browse` | Search public strategies |
| `alpinebull_bots` | List user's bots with status |

#### Polish Items

- Search improvements (full-text, relevance scoring)
- Performance optimization (caching, lazy loading)
- Error handling (retry logic, offline queue)
- Rate limiting (per-user, per-bot)
- Documentation (API docs, user guide)

---

## Database Schema

### PostgreSQL Schema

```sql
-- =============================================
-- USERS
-- =============================================

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,                            -- Keycloak subject ID
    username VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100),
    bio TEXT,
    avatar_url VARCHAR(500),
    visibility VARCHAR(20) DEFAULT 'PUBLIC',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    -- Denormalized stats
    shared_strategy_count INT DEFAULT 0,
    follower_count INT DEFAULT 0,
    active_bot_count INT DEFAULT 0
);

CREATE INDEX idx_user_profiles_username ON user_profiles(username);

-- =============================================
-- FRIENDS
-- =============================================

CREATE TABLE friend_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    UNIQUE(requester_id, addressee_id),
    CHECK (requester_id != addressee_id)
);

CREATE INDEX idx_friend_requester ON friend_relationships(requester_id, status);
CREATE INDEX idx_friend_addressee ON friend_relationships(addressee_id, status);

-- =============================================
-- STRATEGIES
-- =============================================

CREATE TABLE shared_strategies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Version tree
    version_id VARCHAR(20) NOT NULL,
    root_strategy_id UUID,                          -- Self for originals
    parent_version_id UUID REFERENCES shared_strategies(id),

    -- Fork tracking
    forked_from_id UUID REFERENCES shared_strategies(id),
    original_author_id UUID REFERENCES user_profiles(id),
    author_id UUID NOT NULL REFERENCES user_profiles(id),

    -- Content (immutable)
    strategy_json JSONB NOT NULL,
    phases_json JSONB,
    hoops_json JSONB,
    content_hash VARCHAR(64) NOT NULL,

    -- Metadata
    name VARCHAR(200) NOT NULL,
    description TEXT,
    symbol VARCHAR(20),
    timeframe VARCHAR(10),
    visibility VARCHAR(20) DEFAULT 'PUBLIC',

    -- Performance
    backtest_metrics_json JSONB,
    published_at TIMESTAMPTZ DEFAULT NOW(),

    -- Stats
    fork_count INT DEFAULT 0,
    star_count INT DEFAULT 0,
    deployed_bot_count INT DEFAULT 0,

    UNIQUE(root_strategy_id, version_id)
);

CREATE INDEX idx_strategies_author ON shared_strategies(author_id);
CREATE INDEX idx_strategies_visibility ON shared_strategies(visibility);
CREATE INDEX idx_strategies_symbol ON shared_strategies(symbol);
CREATE INDEX idx_strategies_published ON shared_strategies(published_at DESC);
CREATE INDEX idx_strategies_stars ON shared_strategies(star_count DESC);

-- Full-text search
CREATE INDEX idx_strategies_search ON shared_strategies
    USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

-- =============================================
-- STRATEGY STARS
-- =============================================

CREATE TABLE strategy_stars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    strategy_id UUID NOT NULL REFERENCES shared_strategies(id) ON DELETE CASCADE,
    starred_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, strategy_id)
);

CREATE INDEX idx_stars_user ON strategy_stars(user_id);
CREATE INDEX idx_stars_strategy ON strategy_stars(strategy_id);

-- =============================================
-- BOTS
-- =============================================

CREATE TABLE bots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,

    -- Auth
    api_key_hash VARCHAR(64) NOT NULL,
    api_key_prefix VARCHAR(8) NOT NULL,

    -- Config
    name VARCHAR(100),
    pinned_strategy_id UUID REFERENCES shared_strategies(id),
    pinned_version_id VARCHAR(20),

    -- Status
    status VARCHAR(20) DEFAULT 'REGISTERED',
    node_identifier VARCHAR(100),

    -- Trading
    exchange VARCHAR(50),
    trading_pair VARCHAR(20),
    capital_allocated DECIMAL(20, 8),
    paper_trading BOOLEAN DEFAULT FALSE,

    -- Monitoring
    last_heartbeat_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_bots_owner ON bots(owner_id);
CREATE INDEX idx_bots_status ON bots(status);
CREATE INDEX idx_bots_heartbeat ON bots(last_heartbeat_at);

-- =============================================
-- BOT TRADES
-- =============================================

CREATE TABLE bot_trades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    exchange_trade_id VARCHAR(100),

    -- Entry
    side VARCHAR(10),
    entry_time TIMESTAMPTZ,
    entry_price DECIMAL(20, 8),
    quantity DECIMAL(20, 8),

    -- Exit (nullable for open trades)
    exit_time TIMESTAMPTZ,
    exit_price DECIMAL(20, 8),
    pnl DECIMAL(20, 8),
    pnl_percent DECIMAL(10, 4),
    exit_reason VARCHAR(50),

    -- Analysis
    matches_backtest BOOLEAN,
    recorded_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(bot_id, exchange_trade_id)
);

CREATE INDEX idx_bot_trades_bot ON bot_trades(bot_id);
CREATE INDEX idx_bot_trades_time ON bot_trades(bot_id, entry_time DESC);
CREATE INDEX idx_bot_trades_open ON bot_trades(bot_id) WHERE exit_time IS NULL;

-- =============================================
-- BOT METRIC SNAPSHOTS
-- =============================================

CREATE TABLE bot_metric_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL,
    equity DECIMAL(20, 8),
    unrealized_pnl DECIMAL(20, 8),
    open_position_count INT,
    total_trades INT,
    win_rate DECIMAL(5, 2),
    profit_factor DECIMAL(10, 4)
);

CREATE INDEX idx_bot_metrics_bot ON bot_metric_snapshots(bot_id, timestamp DESC);

-- Partition by month for performance (optional)
-- CREATE TABLE bot_metric_snapshots_2024_01 PARTITION OF bot_metric_snapshots
--     FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- =============================================
-- ACTIVITY EVENTS
-- =============================================

CREATE TABLE activity_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,

    -- Polymorphic targets
    target_user_id UUID,
    target_strategy_id UUID,
    target_bot_id UUID,

    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_activity_user ON activity_events(user_id, created_at DESC);
CREATE INDEX idx_activity_time ON activity_events(created_at DESC);

-- Feed query: friends' activities
CREATE INDEX idx_activity_feed ON activity_events(created_at DESC)
    WHERE type IN ('STRATEGY_PUBLISHED', 'STRATEGY_FORKED', 'BOT_STARTED');

-- =============================================
-- DEVICE AUTH (for device flow)
-- =============================================

CREATE TABLE device_auth_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_code VARCHAR(64) UNIQUE NOT NULL,
    user_code VARCHAR(16) UNIQUE NOT NULL,
    client_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    user_id UUID REFERENCES user_profiles(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_device_auth_code ON device_auth_requests(device_code) WHERE status = 'PENDING';
CREATE INDEX idx_device_auth_user_code ON device_auth_requests(user_code) WHERE status = 'PENDING';

-- Cleanup expired requests
CREATE INDEX idx_device_auth_expires ON device_auth_requests(expires_at) WHERE status = 'PENDING';
```

### Indexes Summary

| Table | Index | Purpose |
|-------|-------|---------|
| user_profiles | username | Username lookup |
| friend_relationships | requester + status | Friend list queries |
| friend_relationships | addressee + status | Pending requests |
| shared_strategies | author | User's strategies |
| shared_strategies | visibility | Public browse |
| shared_strategies | symbol | Filter by symbol |
| shared_strategies | published_at DESC | Recent strategies |
| shared_strategies | star_count DESC | Popular strategies |
| shared_strategies | Full-text GIN | Search |
| bots | owner | User's bots |
| bots | status | Active bot queries |
| bots | last_heartbeat_at | Stale bot detection |
| bot_trades | bot + entry_time DESC | Trade history |
| activity_events | user + created_at DESC | User's activity |
| activity_events | created_at DESC | Feed queries |

---

## Security Considerations

### Authentication & Authorization

| Concern | Mitigation |
|---------|------------|
| Token security | Short-lived access tokens (5 min), refresh tokens (30 days) |
| Token storage (desktop) | Stored in `~/.tradery/account.json`, file permissions 600 |
| API authentication | Bearer tokens validated against Keycloak JWKS |
| Bot authentication | API keys with 256-bit entropy, bcrypt hashed |

### Bot API Key Security

```java
public class BotApiKeyService {
    private static final int KEY_LENGTH = 32;  // 256 bits
    private static final String PREFIX = "bot_";

    public ApiKeyPair generateApiKey() {
        byte[] bytes = new byte[KEY_LENGTH];
        SecureRandom.getInstanceStrong().nextBytes(bytes);
        String key = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = BCrypt.hashpw(key, BCrypt.gensalt(12));
        String prefix = key.substring(0, 8);
        return new ApiKeyPair(key, hash, prefix);
    }

    public boolean validateApiKey(String providedKey, String storedHash) {
        return BCrypt.checkpw(providedKey, storedHash);
    }
}
```

**API Key Rules:**
- Shown only once at creation
- Stored as bcrypt hash (cost factor 12)
- Prefix stored for display (`bot_a1b2...`)
- Revocable (regenerate creates new key)

### Visibility Enforcement

```java
@Service
public class StrategyVisibilityService {

    public boolean canView(UserProfile viewer, SharedStrategy strategy) {
        if (strategy.getAuthor().equals(viewer)) return true;

        return switch (strategy.getVisibility()) {
            case PUBLIC, UNLISTED -> true;
            case FRIENDS_ONLY -> friendService.areFriends(viewer, strategy.getAuthor());
            case PRIVATE -> false;
        };
    }

    public Specification<SharedStrategy> visibleTo(UserProfile viewer) {
        return (root, query, cb) -> {
            List<UUID> friendIds = friendService.getFriendIds(viewer.getId());

            return cb.or(
                cb.equal(root.get("visibility"), StrategyVisibility.PUBLIC),
                cb.equal(root.get("author"), viewer),
                cb.and(
                    cb.equal(root.get("visibility"), StrategyVisibility.FRIENDS_ONLY),
                    root.get("author").get("id").in(friendIds)
                )
            );
        };
    }
}
```

### Rate Limiting

| Endpoint | Limit | Window |
|----------|-------|--------|
| Device login | 5 | 1 minute |
| Strategy publish | 10 | 1 hour |
| Strategy browse | 100 | 1 minute |
| Bot heartbeat | 2 | 1 minute |
| Bot trade report | 60 | 1 minute |

### Data Integrity

| Concern | Mitigation |
|---------|------------|
| Strategy tampering | SHA-256 content hash verification |
| Trade duplication | Unique constraint on (bot_id, exchange_trade_id) |
| Version conflicts | Optimistic locking with content hash |

### Offline Support

Tradery desktop works fully offline:
- Strategies stored locally in `~/.tradery/strategies/`
- Sync status tracked in `upstream.json`
- Changes queued until reconnection
- Conflict resolution on sync

---

## Verification Plan

### Phase 1: Authentication

| Test | Steps | Expected Result |
|------|-------|-----------------|
| Device login | Open Tradery → Link Account → Scan QR/Enter code → Approve | `account.json` created with tokens |
| Token refresh | Wait for token expiry → Make API call | Auto-refresh, no re-login required |
| Logout | Tradery → Account → Logout | `account.json` deleted, UI updates |
| Invalid token | Manually corrupt token → Make API call | Prompt to re-login |

### Phase 2: Social

| Test | Steps | Expected Result |
|------|-------|-----------------|
| Send friend request | Search user → Send request | Pending in both UIs |
| Accept request | View requests → Accept | Friends list updated |
| Block user | Friends → Block | Blocked, hidden from search |
| Activity feed | Friend publishes strategy | Shows in feed |

### Phase 3: Strategy Sharing

| Test | Steps | Expected Result |
|------|-------|-----------------|
| Publish | Create strategy in Tradery → Publish | Appears on AlpineBull, `upstream.json` created |
| New version | Modify strategy → Publish update | Version incremented, changelog shown |
| Fork | Browse strategy → Fork | New strategy created, attribution preserved |
| Import | Browse → Import to Tradery | Strategy + phases + hoops downloaded |
| Conflict detection | Edit locally + edit upstream → Sync | Conflict dialog shown |
| Conflict resolution | Choose FORK | Both strategies preserved |

### Phase 4: Bot Tracking

| Test | Steps | Expected Result |
|------|-------|-----------------|
| Register bot | Create bot, copy API key | Bot appears in dashboard |
| Heartbeat | Bot sends heartbeat | Status updates to RUNNING |
| Trade recording | Bot reports trade | Trade appears in history |
| Stale detection | Stop sending heartbeats | Status changes to ERROR after timeout |
| Live vs backtest | View bot details | Comparison metrics shown |
| Strategy update | Publish new version | `strategyUpdateAvailable` in heartbeat response |

### Phase 5: Integration

| Test | Steps | Expected Result |
|------|-------|-----------------|
| MCP login | `alpinebull_login` via Claude | Login flow initiated |
| MCP publish | `alpinebull_publish` via Claude | Strategy published |
| MCP import | `alpinebull_import {id}` via Claude | Strategy imported |
| Search | `alpinebull_browse "RSI"` via Claude | Matching strategies returned |

---

## File Inventory

### AlpineBull Backend (New Files)

```
src/main/java/com/alpinebull/
├── auth/
│   ├── KeycloakConfig.java
│   ├── DeviceAuthController.java
│   └── DeviceAuthService.java
├── user/
│   ├── UserProfileController.java
│   ├── UserProfileService.java
│   ├── FriendController.java
│   └── FriendService.java
├── feed/
│   ├── ActivityFeedController.java
│   └── ActivityFeedService.java
├── strategy/
│   ├── SharedStrategyController.java
│   ├── SharedStrategyService.java
│   └── StrategyVersionService.java
├── bot/
│   ├── BotController.java
│   ├── BotService.java
│   ├── BotReportingController.java
│   ├── BotApiKeyAuthFilter.java
│   └── BotMetricsService.java
├── entity/
│   ├── UserProfile.java
│   ├── FriendRelationship.java
│   ├── SharedStrategy.java
│   ├── StrategyStar.java
│   ├── Bot.java
│   ├── BotTrade.java
│   ├── BotMetricSnapshot.java
│   └── ActivityEvent.java
└── repository/
    ├── UserProfileRepository.java
    ├── FriendRelationshipRepository.java
    ├── SharedStrategyRepository.java
    ├── StrategyStarRepository.java
    ├── BotRepository.java
    ├── BotTradeRepository.java
    ├── BotMetricSnapshotRepository.java
    └── ActivityEventRepository.java
```

### AlpineBull Frontend (New Modules)

```
src/
├── modules/
│   ├── auth/
│   │   ├── LoginPage.tsx
│   │   ├── RegisterPage.tsx
│   │   └── authStore.ts
│   ├── user/
│   │   ├── ProfilePage.tsx
│   │   ├── ProfileEditPage.tsx
│   │   └── SettingsPage.tsx
│   ├── friends/
│   │   ├── FriendsPage.tsx
│   │   ├── FriendRequestsPage.tsx
│   │   ├── UserSearchPage.tsx
│   │   └── FriendCard.tsx
│   ├── feed/
│   │   ├── ActivityFeed.tsx
│   │   └── EventCard.tsx
│   ├── strategies/
│   │   ├── StrategyBrowser.tsx
│   │   ├── StrategyDetail.tsx
│   │   ├── VersionTree.tsx
│   │   └── PublishForm.tsx
│   └── bots/
│       ├── BotDashboard.tsx
│       ├── BotDetail.tsx
│       ├── BotRegisterForm.tsx
│       ├── TradeHistory.tsx
│       └── BacktestComparison.tsx
```

### Tradery Desktop (New/Modified Files)

```
src/main/java/com/tradery/
├── alpinebull/
│   ├── AlpineBullAuthService.java      (NEW)
│   ├── AlpineBullClient.java           (NEW)
│   ├── AlpineBullSyncService.java      (NEW)
│   ├── StrategyBundle.java             (NEW)
│   └── model/
│       ├── AccountLink.java            (NEW)
│       └── UpstreamLink.java           (NEW)
├── io/
│   └── StrategyStore.java              (MODIFIED - add upstream support)
├── api/
│   └── StrategyHandler.java            (MODIFIED - add sync endpoints)
└── ui/
    ├── AlpineBullLoginDialog.java      (NEW)
    ├── StrategyShareDialog.java        (NEW)
    ├── AlpineBullBrowserDialog.java    (NEW)
    └── BotDashboardDialog.java         (NEW)
```

### MCP Server (Modified)

```
src/main/java/com/tradery/mcp/
└── TraderyMcpServer.java               (MODIFIED - add alpinebull_* tools)
```

### Docker/Infrastructure

```
docker/
├── docker-compose.yml                  (MODIFIED - add Keycloak)
├── keycloak/
│   └── realm-export.json               (NEW - realm config)
└── traefik/
    └── traefik.yml                     (MODIFIED - add Keycloak route)
```

### User Data Files

```
~/.tradery/
├── account.json                        (NEW - AlpineBull account link)
└── strategies/{id}/
    └── upstream.json                   (NEW - upstream link per strategy)
```

---

## Appendix: Keycloak Realm Configuration

```json
{
  "realm": "alpinebull",
  "enabled": true,
  "sslRequired": "external",
  "registrationAllowed": true,
  "registrationEmailAsUsername": false,
  "rememberMe": true,
  "verifyEmail": false,
  "loginWithEmailAllowed": true,
  "duplicateEmailsAllowed": false,
  "resetPasswordAllowed": true,
  "editUsernameAllowed": true,
  "bruteForceProtected": true,
  "permanentLockout": false,
  "maxFailureWaitSeconds": 900,
  "minimumQuickLoginWaitSeconds": 60,
  "waitIncrementSeconds": 60,
  "quickLoginCheckMilliSeconds": 1000,
  "maxDeltaTimeSeconds": 43200,
  "failureFactor": 5,
  "clients": [
    {
      "clientId": "alpinebull-web",
      "name": "AlpineBull Web",
      "enabled": true,
      "publicClient": true,
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "redirectUris": ["https://alpinebull.com/*", "http://localhost:3000/*"],
      "webOrigins": ["https://alpinebull.com", "http://localhost:3000"],
      "attributes": {
        "pkce.code.challenge.method": "S256"
      }
    },
    {
      "clientId": "alpinebull-backend",
      "name": "AlpineBull Backend",
      "enabled": true,
      "publicClient": false,
      "standardFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": true,
      "secret": "${BACKEND_CLIENT_SECRET}"
    },
    {
      "clientId": "tradery-desktop",
      "name": "Tradery Desktop",
      "enabled": true,
      "publicClient": true,
      "standardFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "attributes": {
        "oauth2.device.authorization.grant.enabled": "true",
        "oauth2.device.polling.interval": "5"
      }
    }
  ],
  "identityProviders": [
    {
      "alias": "google",
      "providerId": "google",
      "enabled": true,
      "trustEmail": true,
      "config": {
        "clientId": "${GOOGLE_CLIENT_ID}",
        "clientSecret": "${GOOGLE_CLIENT_SECRET}",
        "defaultScope": "openid email profile"
      }
    },
    {
      "alias": "github",
      "providerId": "github",
      "enabled": true,
      "trustEmail": true,
      "config": {
        "clientId": "${GITHUB_CLIENT_ID}",
        "clientSecret": "${GITHUB_CLIENT_SECRET}",
        "defaultScope": "user:email"
      }
    }
  ],
  "roles": {
    "realm": [
      { "name": "user", "description": "Regular user" },
      { "name": "premium", "description": "Premium subscriber" },
      { "name": "admin", "description": "Administrator" }
    ]
  },
  "defaultRoles": ["user"],
  "accessTokenLifespan": 300,
  "ssoSessionIdleTimeout": 1800,
  "ssoSessionMaxLifespan": 36000,
  "offlineSessionIdleTimeout": 2592000,
  "accessTokenLifespanForImplicitFlow": 900
}
```

---

## Appendix: Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `AUTH_DEVICE_EXPIRED` | 400 | Device code has expired |
| `AUTH_DEVICE_DENIED` | 403 | User denied authorization |
| `AUTH_TOKEN_INVALID` | 401 | Invalid or expired token |
| `AUTH_TOKEN_REFRESH_FAILED` | 401 | Refresh token invalid |
| `USER_NOT_FOUND` | 404 | User does not exist |
| `USER_BLOCKED` | 403 | User has blocked you |
| `FRIEND_ALREADY_REQUESTED` | 409 | Friend request already sent |
| `FRIEND_ALREADY_FRIENDS` | 409 | Already friends |
| `STRATEGY_NOT_FOUND` | 404 | Strategy does not exist |
| `STRATEGY_NOT_VISIBLE` | 403 | No permission to view |
| `STRATEGY_VERSION_EXISTS` | 409 | Version ID already exists |
| `STRATEGY_HASH_MISMATCH` | 409 | Content hash doesn't match |
| `BOT_NOT_FOUND` | 404 | Bot does not exist |
| `BOT_API_KEY_INVALID` | 401 | Invalid bot API key |
| `BOT_RATE_LIMITED` | 429 | Too many requests |
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
