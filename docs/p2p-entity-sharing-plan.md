# P2P Entity Sharing Architecture for Tradery Intel

> **Status:** Ready for implementation
> **Created:** 2026-02-08
> **Last updated:** 2026-02-08 (synced to current entity model)

## Context

The Intel app is a **general-purpose entity/relationship platform** with a fully dynamic schema system, pluggable data ingestion (`DataSource` interface), and schema-aware UI. The next major leap is **multi-user collaboration** where users can share entities with selective visibility (private, friends, public) and data flows **peer-to-peer** between clients to avoid centralized hosting of entity traffic.

Key constraints:
- **Keycloak** handles user accounts (email registration, auth)
- **Documents** provide governance models for shared entity collections
- **P2P sync** keeps data distribution off our servers (Napster/BitTorrent model)
- A tiny **rendezvous server** + **mDNS** handles peer discovery (hybrid)
- Hundreds to low thousands of users, not millions

---

## Current Entity Model (as-is)

Understanding the existing model is critical — all P2P work builds on top of it without breaking it.

### Storage: EntityStore (~965 LOC)

Single SQLite database at `~/.tradery/entity-network.db` (hardcoded path in constructor). WAL mode, JDBC connection.

**Tables:**

```sql
entities (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    symbol TEXT,
    type TEXT NOT NULL,            -- enum name from CoinEntity.Type
    parent_id TEXT,
    market_cap REAL,
    source TEXT NOT NULL DEFAULT 'manual',  -- 'manual', 'coingecko', 'ai-discovery', etc.
    created_at INTEGER,
    updated_at INTEGER
)

entity_categories (
    entity_id TEXT NOT NULL,
    category TEXT NOT NULL,
    PRIMARY KEY (entity_id, category)
)

relationships (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    from_id TEXT NOT NULL,
    to_id TEXT NOT NULL,
    type TEXT NOT NULL,            -- enum name from CoinRelationship.Type
    note TEXT,
    source TEXT NOT NULL DEFAULT 'manual',
    created_at INTEGER,
    UNIQUE(from_id, to_id, type)
)

cache_meta (key TEXT PRIMARY KEY, value TEXT)

schema_types (
    id TEXT PRIMARY KEY,           -- lowercase enum name or user-defined
    name TEXT NOT NULL,
    color TEXT NOT NULL,           -- hex like "#64B4FF"
    kind TEXT NOT NULL,            -- "entity" or "relationship"
    from_type_id TEXT,             -- relationship only
    to_type_id TEXT,               -- relationship only
    label TEXT,                    -- relationship short verb
    display_order INTEGER DEFAULT 0,
    erd_x REAL DEFAULT 0,          -- ERD canvas position
    erd_y REAL DEFAULT 0
)

schema_attributes (
    type_id TEXT NOT NULL,
    name TEXT NOT NULL,
    data_type TEXT NOT NULL,       -- TEXT, NUMBER, CURRENCY, PERCENTAGE, BOOLEAN, DATE, TIME, DATETIME, DATETIME_TZ, URL, ENUM, LIST
    required INTEGER DEFAULT 0,
    display_order INTEGER DEFAULT 0,
    labels TEXT,                   -- JSON: {"en":"Market Cap","de":"Marktkapitalisierung"}
    config TEXT,                   -- JSON: {"currencyCode":"USD","currencySymbol":"$","decimalPlaces":0}
    mutability TEXT DEFAULT 'MANUAL',  -- SOURCE, DERIVED, MANUAL
    PRIMARY KEY (type_id, name)
)

entity_attribute_values (
    entity_id TEXT NOT NULL,
    type_id TEXT NOT NULL,
    attr_name TEXT NOT NULL,
    value TEXT,
    origin TEXT DEFAULT 'USER',    -- SOURCE, AI, USER (priority: USER > AI > SOURCE)
    updated_at INTEGER DEFAULT 0,
    PRIMARY KEY (entity_id, type_id, attr_name)
)
```

### Domain Classes

**`CoinEntity`** — mutable class for graph visualization:
- Fields: `id` (String), `name`, `symbol`, `type` (enum), `parentId`, `x/y/vx/vy` (layout), `selected/hovered/pinned` (UI state), `marketCap`, `connectionCount`, `categories` (List)
- Inner enum `Type`: COIN, L2, ETF, ETP, DAT, VC, EXCHANGE, FOUNDATION, COMPANY, NEWS_SOURCE — each with a color
- Has `getRadius()` (market-cap-based sizing), `contains()` (hit testing), `label()` (symbol or name)

**`CoinRelationship`** — immutable value class:
- Fields: `fromId`, `toId`, `type` (enum), `note`
- Inner enum `Type`: L2_OF, ETF_TRACKS, ETP_TRACKS, INVESTED_IN, FOUNDED_BY, PARTNER, FORK_OF, BRIDGE, ECOSYSTEM, COMPETITOR — each with color + label
- `Type.getSearchableTypes(CoinEntity.Type)` for AI search context

**`SchemaType`** — dynamic type definition (replaces enum for schema):
- Fields: `id`, `name`, `color`, `kind` ("entity"/"relationship"), `fromTypeId/toTypeId` (rel only), `label`, `displayOrder`, `attributes` (List<SchemaAttribute>)
- ERD canvas fields: `erdX/erdY/erdVx/erdVy/erdPinned/erdTargetX/erdTargetY/erdAnimating`
- Constants: `KIND_ENTITY`, `KIND_RELATIONSHIP`

**`SchemaAttribute`** — attribute definition within a type:
- Fields: `name`, `dataType`, `required`, `displayOrder`, `labels` (i18n map), `config` (formatting map), `mutability`
- Data types: TEXT, NUMBER, CURRENCY, PERCENTAGE, BOOLEAN, DATE, TIME, DATETIME, DATETIME_TZ, URL, ENUM, LIST
- `Mutability` enum: SOURCE (read-only), DERIVED (computed), MANUAL (user-editable)
- Rich formatting: `formatValue(rawValue)` handles currency symbols, decimal places, date patterns, etc.
- i18n: `displayName(Locale)` with fallback chain (exact tag → language → english → programmatic name)

**`AttributeValue`** — record with provenance:
- `record AttributeValue(String value, Origin origin, long updatedAt)`
- `Origin` enum: SOURCE(0), AI(1), USER(2) — priority system where higher overrides lower
- EntityStore respects priority on write: a SOURCE write won't overwrite a USER correction

### Schema Management: SchemaRegistry

- Loads all `SchemaType` records from DB on init
- Seeds defaults from `CoinEntity.Type` and `CoinRelationship.Type` enums if DB is empty
- Incremental migrations via `seedIfMissing()` — adds types introduced after initial seed: `hosts_pair`, `news_article`, `mentions`, `topic`, `tagged`, `published_by`
- `migrateMutability()` — sets correct mutability for known source attributes
- Pass-through methods to EntityStore for attribute value CRUD

### Data Sources: DataSource Interface + DataSourceRegistry

**`DataSource`** interface:
- `id()`, `name()`, `producedEntityTypes()`, `producedRelationshipTypes()`, `cacheTTL()`
- `fetch(FetchContext)` → `FetchResult(entitiesAdded, relationshipsAdded, message)`
- `seedSchemaTypes(SchemaRegistry)` — optional hook on registration
- `FetchContext` provides `EntityStore` + `SchemaRegistry` + `ProgressCallback`

**`DataSourceRegistry`** — orchestrator:
- `register(source)` — registers + calls `seedSchemaTypes`
- `refresh(sourceId, force, progress)` — checks cache TTL, calls `fetch()`
- `refreshAll()`, `getSourcesForType(schemaTypeId)`

**Implementations:** `CoinGeckoSource`, `RssNewsSource`

### Dual Type System Note

The codebase has **two parallel representations** of types:
1. **Hardcoded enums** (`CoinEntity.Type`, `CoinRelationship.Type`) — used for type-safe Java code, colors, search logic
2. **Dynamic DB records** (`SchemaType` + `SchemaAttribute`) — used for ERD editing, attribute definitions, user-defined types

SchemaRegistry bridges them: seeds DB from enums on first run, then the DB becomes source of truth for schema-level operations. Entity `type` column stores the enum name (e.g., "COIN"), while schema_types.id stores lowercase (e.g., "coin").

### Separate News Model (not used for P2P)

`com.tradery.news.model` has a separate set of records (`Entity`, `Relationship`, `EntityType`, `RelationType`) used for news article AI extraction. These are **not** the coin graph entities and are **not** part of the P2P sharing plan.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                  CENTRAL (lightweight)               │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Keycloak │  │  Rendezvous  │  │  Doc Index   │  │
│  │ (auth)   │  │  (peer IPs)  │  │  (metadata)  │  │
│  └──────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────┘
         │                │                │
         ▼                ▼                ▼
┌─────────────┐    P2P TCP/TLS     ┌─────────────┐
│   Client A  │◄══════════════════►│   Client B  │
│  (SQLite)   │  entity snapshots  │  (SQLite)   │
│             │  signed by author  │             │
└─────────────┘                    └─────────────┘
         ▲
         │ mDNS (LAN discovery)
         ▼
┌─────────────┐
│   Client C  │
│  (same LAN) │
└─────────────┘
```

**Central services** (tiny footprint):
- **Keycloak** — user registration, email confirm, JWT tokens, friend management
- **Rendezvous** — stores `{userId, ip:port, lastSeen}` for online peers (~1KB/user)
- **Doc Index** — metadata about public/discoverable documents (name, description, owner, governance type). No entity data.

**Peer-to-peer** (all entity data):
- Entities, relationships, schema types, attribute values flow directly between clients
- Signed by author's key for integrity
- Manifest-based diff sync (only transfer what's missing/changed)

---

## Core Concepts

### 1. User Identity (Progressive)

Identity is **optional until sharing**. Solo users never need an account.

**Offline mode (default):**
- No account required — the app works exactly as today
- Documents are `local` visibility — purely local, no sharing columns, no signing
- `author_id` is null on all entities (single-user, no ambiguity)
- No keypair, no JWT, no network traffic

**Online mode (opt-in when user wants to share):**
- User creates a Keycloak account (email registration)
- **User UUID** — assigned by Keycloak, used as `author_id` on entities
- **Ed25519 signing keypair** — generated locally on first login, public key registered with Keycloak as a user attribute
- Existing local documents can be **upgraded** to `private`/`friends`/`public` — this backfills `author_id` on all entities and signs them

### 2. Documents

Each **Document** is a fully self-contained SQLite database with its own entity graph, schema, member list, and governance. Think of it like a Google Doc — each is independent, separately shared, with its own permissions.

```
~/.tradery/documents/
├── index.yaml                          # List of known documents + metadata
├── {doc-uuid}/
│   ├── document.yaml                   # Document metadata (below)
│   ├── entities.db                     # Self-contained SQLite (same schema as entity-network.db + sharing columns)
│   └── members.yaml                    # Member list + roles (synced via P2P)
├── {another-doc-uuid}/
│   ├── document.yaml
│   ├── entities.db
│   └── members.yaml
```

```yaml
# document.yaml
id: "550e8400-e29b-41d4-a716-446655440000"
name: "Crypto Intelligence"
owner_id: "user-uuid"              # null for local documents (no account)
visibility: local | private | friends | public
governance:                         # only meaningful for friends/public
  type: open | admin_approved | voting
  voting_quorum: 0.51
created_at: 1707350400000
```

```yaml
# members.yaml
members:
  - user_id: "user-uuid-1"
    role: owner        # full control, can delete document
  - user_id: "user-uuid-2"
    role: admin        # can approve submissions, manage members
  - user_id: "user-uuid-3"
    role: member       # can submit/publish based on governance type
  - user_id: "user-uuid-4"
    role: viewer       # read-only access
```

**Key properties:**
- Each document is its **own SQLite database** — completely isolated, separately synced
- Each has its **own member list and admin structure** — independent of other documents
- The existing `~/.tradery/entity-network.db` becomes the user's default `local` document
- Documents can be opened side-by-side (multiple windows or tabbed)
- P2P sync operates **per-document** — you sync specific documents with specific peers
- `local` documents have **no sharing overhead** — no UUIDs, no signatures, no author_id, no members.yaml

**Visibility levels (progressive):**
- `local` — purely local, no account needed, no sharing columns on entities (default)
- `private` — account required, entities get UUIDs/signatures, but only you can access (useful for backup/sync across your own devices)
- `friends` — account required, only explicitly listed members can access
- `public` — account required, discoverable by anyone, anyone can request to join

**Upgrade path:** A `local` document can be upgraded to any shared visibility at any time. This triggers:
1. Prompt user to log in / create Keycloak account (if not already)
2. Backfill `uuid` (UUID v7) on all entities, relationships, schema types, attribute values
3. Set `author_id` to the user's Keycloak UUID on all existing rows
4. Sign all entities with the user's Ed25519 key
5. Create `members.yaml` with the user as `owner`
6. Change `visibility` in `document.yaml`

This is a one-time migration per document — after upgrade, new entities are created with sharing columns from the start.

**Governance types** (only meaningful for `friends`/`public`):
- `open` — any member can publish entities directly
- `admin_approved` — submissions go to a review queue, admins accept/reject
- `voting` — members vote on submissions, accepted at quorum

### 3. Entities with Global Identity

**Local documents** use the exact same table schema as today — no extra columns, zero overhead.

**Shared documents** (`private`/`friends`/`public`) add sharing columns via `addColumnIfMissing()` migration when a document is upgraded:

```sql
-- Sharing columns on entities table
ALTER TABLE entities ADD COLUMN uuid TEXT;            -- UUID v7 (globally unique)
ALTER TABLE entities ADD COLUMN author_id TEXT;        -- Keycloak user UUID
ALTER TABLE entities ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE entities ADD COLUMN signature TEXT;        -- Ed25519 sig of content hash
ALTER TABLE entities ADD COLUMN status TEXT DEFAULT 'published';
  -- published | pending_review | rejected

-- Sharing columns on relationships table
ALTER TABLE relationships ADD COLUMN uuid TEXT;
ALTER TABLE relationships ADD COLUMN author_id TEXT;
ALTER TABLE relationships ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE relationships ADD COLUMN signature TEXT;

-- Sharing columns on schema_types table
ALTER TABLE schema_types ADD COLUMN uuid TEXT;
ALTER TABLE schema_types ADD COLUMN version INTEGER DEFAULT 1;

-- Sharing columns on entity_attribute_values table
-- (origin and updated_at already exist)
ALTER TABLE entity_attribute_values ADD COLUMN uuid TEXT;
ALTER TABLE entity_attribute_values ADD COLUMN author_id TEXT;
ALTER TABLE entity_attribute_values ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE entity_attribute_values ADD COLUMN signature TEXT;
```

These columns are **only added when a document is upgraded from `local` to a shared visibility**. The `addColumnIfMissing()` pattern already used in EntityStore handles this gracefully — columns are added idempotently.

Note: no `space_id` or `visibility` on entities — the **document** itself is the isolation boundary. All entities in a document share the document's visibility and governance.

**What gets synced per document** (shared documents only):
- `entities` rows + `entity_categories` rows
- `relationships` rows
- `schema_types` + `schema_attributes` rows (the full dynamic schema)
- `entity_attribute_values` rows (with provenance: origin, updated_at)

### 4. P2P Sync Protocol

**Wire protocol** — length-prefixed JSON over TLS TCP:

```
HELLO         → {peerId, publicKey, keycloakToken, documentIds:[...]}
MANIFEST      → {documentId, entityHashes:{uuid→"contentHash:version"}, relHashes:{...}, schemaHashes:{...}}
REQUEST       → {documentId, entityUuids:[...], relUuids:[...], schemaTypeIds:[...]}
ENTITIES      → {documentId, entities:[signed...], relationships:[signed...], schemaTypes:[...], attrValues:[...]}
SUBMIT        → {documentId, entities:[...]}  (for admin_approved/voting documents)
VOTE          → {documentId, entityUuid, approve:bool}
MEMBER_UPDATE → {documentId, members:[...]}   (admin syncs member list changes)
```

**Sync flow:**
1. Client authenticates with Keycloak, gets JWT
2. Announces to rendezvous: `{userId, ip:port, documentIds:[...], jwt}`
3. Discovers peers who share the same documents (rendezvous query or mDNS scan)
4. Opens TLS connection, exchanges HELLO (validates JWT + document memberships)
5. For each shared document: exchange MANIFESTs, diff, transfer missing entities
6. Received entities verified via signature before storage
7. Member list changes propagated via MEMBER_UPDATE messages

**Per-document sync:** Each document syncs independently. If you share Document A with Alice and Document B with Bob, Alice never sees Document B's data — it's completely isolated at the database level.

**Conflict resolution:** Version number + author-wins. If two users edit the same entity, the original author's edit takes priority. Non-author edits create a fork (copy with new UUID). For `entity_attribute_values`, the existing `Origin` priority chain (USER > AI > SOURCE) is respected during merge.

---

## Module Architecture

All networking/P2P code lives in **separate Gradle modules**. The existing `tradery-news` module gets small, natural additions to the data model (provenance fields that belong on entities regardless of sharing), plus a `Path`-based EntityStore constructor. Everything P2P/identity/governance is additive in new modules.

**What belongs in the core model vs add-on modules:**

| Field | Where | Why |
|-------|-------|-----|
| `uuid` | **Core** (EntityStore) | Globally unique ID is fundamental provenance — useful even without sharing (dedup, import/export, cross-doc references) |
| `author_id` | **Core** (EntityStore) | Who created this is basic metadata — null until an account exists, filled in on upgrade |
| `version` | **Core** (EntityStore) | Version tracking is a general data management concern |
| `signature` | **Add-on** (tradery-sharing) | Only meaningful for P2P integrity verification |
| `status` | **Add-on** (tradery-sharing) | published/pending_review/rejected is governance-specific |

```
┌───────────────────────────────────────────────────────────────────┐
│ tradery-news (EXISTING — small model additions)                   │
│   EntityStore, CoinEntity, CoinRelationship, SchemaType,         │
│   SchemaRegistry, DataSourceRegistry, all UI panels              │
│   Changes: EntityStore(Path) constructor, uuid/author_id/version  │
│   columns on entities + relationships tables                      │
└───────────────────┬───────────────────────────────────────────────┘
                    │ depends on (uses EntityStore, SchemaRegistry)
                    ▼
┌───────────────────────────────────────────────────────────────────┐
│ tradery-documents (NEW — Phase 2, no account needed)              │
│   Document, DocumentManager, DocumentMember                       │
│   Manages ~/.tradery/documents/, opens EntityStore per doc        │
│   Document switcher UI (panel injected into IntelFrame)           │
└───────────────────┬───────────────────────────────────────────────┘
                    │ depends on (uses DocumentManager, EntityStore)
                    ▼
┌───────────────────────────────────────────────────────────────────┐
│ tradery-sharing (NEW — Phase 3+4, requires account)               │
│   UserSession, EntitySigner, SharingUpgrade                       │
│   PeerServer, PeerConnection, PeerManager, SyncEngine             │
│   RendezvousClient, LanDiscovery, NetworkMessage                  │
│   GovernanceEngine, SubmissionStore                               │
│   Sharing UI (login, network panel, review queue)                 │
└───────────────────────────────────────────────────────────────────┘
```

**Dependency direction:**
- `tradery-news` → **no new dependencies** (stays standalone)
- `tradery-documents` → depends on `tradery-news` (uses EntityStore, SchemaRegistry)
- `tradery-sharing` → depends on `tradery-documents` + `tradery-news`
- `tradery-news` remains independently runnable — documents/sharing are optional add-ons

**Integration point:** `tradery-news` exposes a hook/callback for add-on modules to inject UI elements (document switcher in header, network panel in sidebar). Alternatively, a thin `tradery-intel` app module composes all three at launch time.

---

## Implementation Plan (Phased)

### Phase 1: EntityStore Path Refactor (tradery-news — minimal change)
The **only** change to existing code. Make EntityStore openable on arbitrary DB paths.

**Files to modify in `tradery-news`:**

- **`EntityStore.java`** (~965 LOC)
  - Add constructor overload: `EntityStore(Path dbPath)` — the existing no-arg constructor becomes `this(Path.of(DB_PATH))`
  - Extract `DB_PATH` into a `public static Path defaultPath()` method so external code can reference it
  - **That's it.** No sharing columns, no UUID fields, no new methods. EntityStore stays exactly as-is otherwise.

- **No changes** to CoinEntity, CoinRelationship, SchemaType, SchemaAttribute, SchemaRegistry, or any UI classes.

### Phase 2: Document Management (tradery-documents — new module)
Multiple isolated entity databases. No networking, no accounts.

**New module: `tradery-documents`**

```
tradery-documents/
├── build.gradle
└── src/main/java/
    ├── module-info.java
    └── com/tradery/documents/
        ├── Document.java              # Record: id, name, ownerId, visibility, governance, createdAt
        ├── DocumentManager.java       # Manages ~/.tradery/documents/ directory
        ├── DocumentMember.java        # Record: userId, role (owner/admin/member/viewer)
        ├── DocumentWorkspace.java     # Binds EntityStore + SchemaRegistry + DataSourceRegistry for one doc
        ├── UuidGenerator.java         # UUID v7 (~20 LOC)
        └── ui/
            └── DocumentSwitcherPanel.java  # Dropdown for switching active document
```

```groovy
// tradery-documents/build.gradle
dependencies {
    implementation project(':tradery-news')
    implementation "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${jacksonVersion}"
}
```

```java
// module-info.java
module com.tradery.documents {
    requires com.tradery.news;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires java.desktop;

    exports com.tradery.documents;
    exports com.tradery.documents.ui;
    opens com.tradery.documents to com.fasterxml.jackson.databind;
}
```

**Key classes:**

- **`Document.java`**
  - Record: `Document(String id, String name, String ownerId, Visibility visibility, Governance governance, long createdAt)`
  - `Visibility` enum: LOCAL, PRIVATE, FRIENDS, PUBLIC
  - `Governance` record: `Governance(Type type, double votingQuorum)` with `Type` enum: OPEN, ADMIN_APPROVED, VOTING
  - `ownerId` is null for LOCAL documents
  - `isLocal()` → `visibility == LOCAL`
  - YAML serialization via Jackson

- **`DocumentManager.java`**
  - Manages `~/.tradery/documents/` directory + `index.yaml`
  - `createDocument(name)` → creates dir, `document.yaml` (visibility=LOCAL), empty `entities.db` — **no account needed**
  - `openDocument(docId)` → returns `DocumentWorkspace` (EntityStore + SchemaRegistry bound to that DB)
  - `listDocuments()` → all known documents
  - `deleteDocument(docId)`
  - `migrateDefault()` → on first startup, moves `entity-network.db` into `documents/{uuid}/entities.db`, creates LOCAL document.yaml

- **`DocumentWorkspace.java`**
  - Holds one open document's full stack: `EntityStore` + `SchemaRegistry` + `DataSourceRegistry`
  - Created by `DocumentManager.openDocument()`
  - UI panels bind to the active workspace
  - `close()` cleans up connections

**Migration:**
- The existing `~/.tradery/entity-network.db` becomes the user's default LOCAL document
- On first startup: `DocumentManager.migrateDefault()` creates `~/.tradery/documents/{uuid}/`, moves `entity-network.db` → `entities.db`, writes `document.yaml` (visibility=LOCAL, ownerId=null)
- `CoinCache` (`~/.tradery/coins.db`) stays separate — it's a fetch cache, not user data
- **No account prompt** — migration is completely offline

**UI integration:**
- `DocumentSwitcherPanel` — dropdown showing all local documents, "New Document" button
- IntelFrame gets a small integration point: the document switcher goes in the header bar
- When switching documents: the `DocumentWorkspace` swaps, and UI panels rebind via a listener/callback pattern
- **Key concern:** Several UI classes in `tradery-news` receive `EntityStore`/`SchemaRegistry` in constructor. Need to make them rebindable (setter or event-driven). This is the main UI refactor needed in `tradery-news`.

### Phase 3: Sharing & P2P (tradery-sharing — new module)
Identity, upgrade, networking, governance. All opt-in.

**New module: `tradery-sharing`**

```
tradery-sharing/
├── build.gradle
└── src/main/java/
    ├── module-info.java
    └── com/tradery/sharing/
        ├── identity/
        │   ├── UserSession.java           # Keycloak login, JWT, Ed25519 keypair
        │   └── KeyPairStore.java          # Local keypair storage (~/.tradery/keys/)
        ├── upgrade/
        │   ├── SharingUpgrade.java        # Adds sharing columns to a document's DB
        │   └── EntitySigner.java          # Canonical JSON + Ed25519 sign/verify
        ├── sync/
        │   ├── PeerServer.java            # TLS TCP server on random port
        │   ├── PeerConnection.java        # Single peer connection (read/write messages)
        │   ├── PeerManager.java           # Connection lifecycle, peer state, reconnect
        │   ├── SyncEngine.java            # Per-document manifest diff, entity transfer
        │   └── NetworkMessage.java        # Sealed interface with record subtypes
        ├── discovery/
        │   ├── RendezvousClient.java      # HTTP client for rendezvous server
        │   └── LanDiscovery.java          # mDNS _tradery._tcp for LAN peers
        ├── governance/
        │   ├── GovernanceEngine.java       # Apply governance rules per document
        │   └── SubmissionStore.java        # Pending submissions + votes (extra DB tables)
        └── ui/
            ├── LoginDialog.java           # Keycloak login UI
            ├── ShareDialog.java           # Upgrade doc to shared + set visibility
            ├── NetworkPanel.java          # Online peers, sync status
            ├── MemberPanel.java           # Member management per document
            └── ReviewQueuePanel.java      # Admin review for admin_approved docs
```

```groovy
// tradery-sharing/build.gradle
dependencies {
    implementation project(':tradery-news')
    implementation project(':tradery-documents')
    implementation "com.squareup.okhttp3:okhttp:${okhttpVersion}"           // rendezvous client
    implementation "com.fasterxml.jackson.databind:jackson-databind:${jacksonVersion}"
}
```

```java
// module-info.java
module com.tradery.sharing {
    requires com.tradery.news;
    requires com.tradery.documents;
    requires com.fasterxml.jackson.databind;
    requires okhttp3;
    requires java.desktop;

    exports com.tradery.sharing;
    exports com.tradery.sharing.identity;
    exports com.tradery.sharing.sync;
    exports com.tradery.sharing.ui;
    opens com.tradery.sharing to com.fasterxml.jackson.databind;
}
```

**Key classes:**

- **`SharingUpgrade.java`** — the document upgrade logic:
  - Takes an EntityStore (from DocumentWorkspace) and adds sharing columns via `addColumnIfMissing()`
  - Backfills UUID v7 on all existing entities/relationships/schema_types/attribute_values
  - Sets author_id on all existing rows
  - Signs all entities with user's Ed25519 key
  - Creates members.yaml with user as owner
  - Updates document.yaml visibility
  - **This is the only code that touches EntityStore's DB schema** — and it does so externally via ALTER TABLE, not by modifying EntityStore itself

- **`UserSession.java`** — Keycloak integration:
  - OAuth2 device flow or browser redirect
  - JWT token management (refresh, expiry)
  - Local Ed25519 keypair generation on first login
  - `getCurrentUserId()`, `getSigningKey()`, `getJwt()`, `isLoggedIn()`
  - Persists to `~/.tradery/session.yaml`

- **`SyncEngine.java`** — per-document P2P sync:
  - Reads directly from document's SQLite via DocumentWorkspace's EntityStore
  - Manifest generation: queries entities/relationships for uuid + content hash
  - Entity transfer: serializes CoinEntity/CoinRelationship to JSON with sharing fields
  - Signature verification before storage
  - Conflict resolution: version + author-wins
  - P2P source tag `"p2p:{authorId}"` so `replaceEntitiesBySource()` doesn't interfere
  - Respects `AttributeValue.Origin` priority during merge

**Sharing columns** (added by SharingUpgrade to an existing entities.db):

```sql
-- On entities table
ALTER TABLE entities ADD COLUMN uuid TEXT;
ALTER TABLE entities ADD COLUMN author_id TEXT;
ALTER TABLE entities ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE entities ADD COLUMN signature TEXT;
ALTER TABLE entities ADD COLUMN status TEXT DEFAULT 'published';

-- On relationships table
ALTER TABLE relationships ADD COLUMN uuid TEXT;
ALTER TABLE relationships ADD COLUMN author_id TEXT;
ALTER TABLE relationships ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE relationships ADD COLUMN signature TEXT;

-- On schema_types table
ALTER TABLE schema_types ADD COLUMN uuid TEXT;
ALTER TABLE schema_types ADD COLUMN version INTEGER DEFAULT 1;

-- On entity_attribute_values table (origin + updated_at already exist)
ALTER TABLE entity_attribute_values ADD COLUMN uuid TEXT;
ALTER TABLE entity_attribute_values ADD COLUMN author_id TEXT;
ALTER TABLE entity_attribute_values ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE entity_attribute_values ADD COLUMN signature TEXT;
```

**Governance tables** (added by GovernanceEngine for non-OPEN documents):

```sql
submissions (
    uuid TEXT PRIMARY KEY,
    entity_uuid TEXT NOT NULL,
    submitter_id TEXT NOT NULL,
    status TEXT DEFAULT 'pending',
    submitted_at INTEGER,
    resolved_at INTEGER
)

votes (
    submission_uuid TEXT NOT NULL,
    voter_id TEXT NOT NULL,
    approve INTEGER NOT NULL,
    voted_at INTEGER,
    PRIMARY KEY (submission_uuid, voter_id)
)
```

**UI integration:**
- "Share..." button in document settings → opens `ShareDialog` → triggers login if needed → runs `SharingUpgrade`
- `NetworkPanel` injected into IntelFrame sidebar (only when module is present)
- `MemberPanel`, `ReviewQueuePanel` — per-document panels in document settings
- Account indicator in header (avatar/name when logged in, absent when offline)

---

## Changes to tradery-news (minimal)

The **only** change to existing code:

| File | Change | Invasiveness |
|------|--------|-------------|
| `EntityStore.java` | Add `EntityStore(Path dbPath)` constructor overload | 1 line + extract constant |
| `settings.gradle` | Add `include 'tradery-documents'` and `include 'tradery-sharing'` | 2 lines |

Everything else — documents, sharing, identity, P2P, governance, all new UI — lives in the new modules.

**Future optional refactor:** To support live document switching in the UI (rebinding panels to a different EntityStore), some `tradery-news` UI classes may need setter methods or listener patterns. But this can be done incrementally and isn't required for the initial document support (first doc = default, no switching needed at first).

---

## Dependencies

**tradery-documents:**
- `tradery-news` (uses EntityStore, SchemaRegistry, DataSourceRegistry)
- Jackson YAML (already in project)
- No new external dependencies

**tradery-sharing:**
- `tradery-documents` + `tradery-news`
- OkHttp (already in project — for rendezvous HTTP)
- Ed25519: `java.security` (JDK 21 built-in)
- TLS sockets: `javax.net.ssl` (built-in)
- UUID v7: Pure Java (~20 LOC in tradery-documents)
- mDNS: JDK multicast (built-in)
- **No new external dependencies**

**New infrastructure (only needed for Phase 3):**
- Keycloak instance (Docker, free)
- Rendezvous server (~200 LOC HTTP service, free-tier cloud)

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Code organization | Separate modules (`tradery-documents`, `tradery-sharing`) | Non-invasive to existing `tradery-news` code; add-on architecture |
| tradery-news changes | Only `EntityStore(Path)` constructor overload | One-line change; everything else is additive in new modules |
| Sharing columns | Added externally by `SharingUpgrade`, not baked into EntityStore | EntityStore stays unaware of sharing; upgrade runs ALTER TABLE from outside |
| Account requirement | Optional, only for sharing | Solo users never blocked by auth; zero friction for local-only use |
| Entity data transport | P2P (direct TCP) | Avoid hosting data traffic |
| Auth/identity | Keycloak + local signing key | Proper user management with P2P integrity |
| Peer discovery | Hybrid (rendezvous + mDNS) | Works behind NAT + free LAN discovery |
| Conflict resolution | Version + author-wins | Simple, predictable, no CRDTs needed |
| Wire protocol | Length-prefixed JSON over TLS | Simple, debuggable, secure |
| Entity ID | UUID v7 alongside existing string `id` | Backward compatible, time-sortable |
| Document governance | Pluggable (open/admin/voting) | Flexible per-community needs |
| Attribute sync | Respects existing Origin priority | USER edits never overwritten by remote SOURCE data |
| Schema sync unit | Full SchemaType + attributes | Documents carry their own schema — peers don't need pre-shared type defs |

---

## Verification

**Phase 1 (EntityStore Path — tradery-news):**
- Compile: `./gradlew :tradery-news:compileJava`
- Verify `new EntityStore(Path.of("/tmp/test-entities.db"))` creates a working DB
- Verify existing no-arg `EntityStore()` still works (backward compat)
- Restart intel: `scripts/kill-intel.sh && scripts/start-intel.sh` — everything works as before

**Phase 2 (documents — tradery-documents module):**
- Compile: `./gradlew :tradery-documents:compileJava`
- `DocumentManager.migrateDefault()` — verify existing `entity-network.db` moved into `~/.tradery/documents/{uuid}/entities.db`
- Create new LOCAL document via `DocumentManager.createDocument("Test")`
- Verify `~/.tradery/documents/{uuid}/` created with `entities.db` + `document.yaml` (visibility=local, ownerId=null)
- Verify no members.yaml for LOCAL documents
- Open two DocumentWorkspaces, verify entity isolation (different entity sets)
- UI: document switcher shows all local documents, switching works

**Phase 3 (sharing + P2P — tradery-sharing module):**
- Compile: `./gradlew :tradery-sharing:compileJava`
- Click "Share..." on a LOCAL document → login dialog appears
- Create Keycloak account, log in
- `SharingUpgrade` runs: verify sharing columns added to DB
- Verify: `sqlite3 ~/.tradery/documents/{uuid}/entities.db "SELECT id, uuid, author_id FROM entities LIMIT 5"`
- Verify members.yaml created with user as owner
- Run two instances (different `~/.tradery` dirs), share a FRIENDS document
- Create entity in A → appears in B after sync
- Verify signature verification rejects tampered entities
- Test mDNS discovery on LAN
- Test attribute value priority (USER edit on B not overwritten by SOURCE data from A)

**Phase 4 (governance — part of tradery-sharing):**
- Create admin_approved document, submit entity from non-admin → review queue
- Admin approves → entity status → published, syncs to peers
- Create voting document, submit, cast votes, verify quorum logic
