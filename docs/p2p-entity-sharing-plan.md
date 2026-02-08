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

## Implementation Plan (Phased)

### Phase 1: Data Model Foundation
Make the entity model document-aware without any networking or accounts.

**Files to modify:**

- **`EntityStore.java`** (~965 LOC)
  - Refactor constructor to accept `Path dbPath` parameter. Add a second constructor `EntityStore(Path dbPath)` alongside the existing no-arg one (which becomes `this(Path.of(DB_PATH))`) for backward compat.
  - Add `upgradeToShared(String authorId)` method that:
    - Adds sharing columns via `addColumnIfMissing()` (uuid, author_id, version, signature, status on entities; uuid, author_id, version, signature on relationships; etc.)
    - Backfills UUID v7 on all existing rows where uuid IS NULL
    - Sets author_id on all existing rows
    - Called only when a document is upgraded from `local` → shared
  - Add `isShared()` method — checks if the uuid column exists on entities table (cheap PRAGMA check)
  - Update `mapEntityFromResultSet()` to conditionally read sharing columns (only if `isShared()`)
  - Update `mapRelationshipFromResultSet()` — same
  - Update `saveEntity()` — if shared, auto-generate UUID v7 for new entities, write sharing columns
  - Update `saveRelationship()` — same
  - Add `getEntityManifest()` → `Map<String, String>` (uuid → "contentHash:version") for sync
  - Add `getRelationshipManifest()` → same
  - **Local documents are unchanged** — no extra columns, no UUID generation, no overhead

- **`CoinEntity.java`** (~140 LOC)
  - Add optional fields: `uuid` (String, null for local), `authorId` (String, null for local), `version` (int, default 1), `signature` (String, null for local), `status` (String, default "published")
  - Add getters/setters for all new fields
  - Existing constructors unchanged — sharing fields default to null/1

- **`CoinRelationship.java`** (~72 LOC)
  - Add optional fields: `uuid` (String), `authorId` (String), `version` (int, default 1), `signature` (String)
  - Add getters/setters for sharing fields (mutable, since the rest of the class already has final fields for core data — sharing fields are metadata that gets set after construction)

- **`SchemaType.java`** (~118 LOC)
  - Add optional fields: `uuid` (String), `version` (int, default 1)
  - Add getters/setters

**New files:**

- **`tradery-news/.../network/EntitySigner.java`**
  - Canonical JSON serialization of entity content (deterministic key order, no whitespace)
  - Ed25519 sign/verify using `java.security.Signature` with "Ed25519" algorithm
  - `sign(entity, privateKey)` → signature string (base64)
  - `verify(entity, signature, publicKey)` → boolean
  - Content hash: SHA-256 of canonical JSON (used in manifests)
  - **Only used after document upgrade** — local documents never sign

- **`tradery-news/.../network/UuidGenerator.java`**
  - UUID v7 generation (timestamp + random): ~20 LOC pure Java
  - `generate()` → String (standard UUID format)

### Phase 2: Documents (no account required)
The document abstraction — multiple isolated entity databases. No networking, no accounts.

**New files:**

- **`tradery-news/.../network/Document.java`**
  - Record: `Document(String id, String name, String ownerId, Visibility visibility, Governance governance, long createdAt)`
  - `Visibility` enum: LOCAL, PRIVATE, FRIENDS, PUBLIC
  - `Governance` record: `Governance(Type type, double votingQuorum)` with `Type` enum: OPEN, ADMIN_APPROVED, VOTING
  - `ownerId` is null for LOCAL documents
  - YAML serialization via Jackson

- **`tradery-news/.../network/DocumentMember.java`**
  - Record: `DocumentMember(String userId, Role role)`
  - `Role` enum: OWNER, ADMIN, MEMBER, VIEWER
  - Only exists in members.yaml for non-LOCAL documents

- **`tradery-news/.../network/DocumentManager.java`**
  - Manages `~/.tradery/documents/` directory
  - `createDocument(name)` → creates dir, document.yaml (visibility=LOCAL), empty entities.db — **no account needed**
  - `openDocument(docId)` → returns `EntityStore` instance for that document's DB
  - `listDocuments()` → all known documents from index.yaml
  - `deleteDocument(docId)`
  - `upgradeDocument(docId, visibility, userSession)` → adds sharing columns, backfills UUIDs/author/signatures, creates members.yaml — **requires account**
  - Each document gets its own `EntityStore` + `SchemaRegistry` instance
  - **Important:** `DataSourceRegistry` needs to work per-document, since sources feed into a specific EntityStore

**Migration:**
- The existing `~/.tradery/entity-network.db` becomes the user's default LOCAL document
- On first startup: create `~/.tradery/documents/{generated-uuid}/`, move (or copy+verify) `entity-network.db` → `entities.db`, create `document.yaml` with visibility=LOCAL, ownerId=null
- `CoinCache` (`~/.tradery/coins.db`) stays separate — it's a fetch cache, not user data
- **No account prompt** — migration is completely offline

**UI changes:**
- Document switcher in IntelFrame header (dropdown showing all local documents)
- "New Document" dialog (just name — creates a LOCAL document)
- Document settings panel (name, and a "Share..." button that triggers upgrade flow)
- Each document opens its own `EntityStore` → `SchemaRegistry` → `DataSourceRegistry` chain
- When switching documents: `CoinGraphPanel`, `EntityManagerFrame`, `ErdPanel`, `DataStructureFrame` all rebind to the new EntityStore
- **Key concern:** Several UI classes receive `EntityStore`/`SchemaRegistry` in constructor. Need to make them rebindable (setter or event-based) rather than final.

### Phase 2b: Identity (opt-in for sharing)
Keycloak accounts, only needed when a user wants to share a document.

**New files:**

- **`tradery-news/.../network/UserSession.java`**
  - Keycloak login flow (OAuth2 device flow or browser redirect)
  - JWT token management (refresh, expiry)
  - Local Ed25519 keypair generation on first login
  - Public key registration with Keycloak
  - `getCurrentUserId()`, `getSigningKey()`, `getJwt()`
  - `isLoggedIn()` — false by default, true after first login
  - Persists session to `~/.tradery/session.yaml` (token, keypair path, user UUID)

**UI changes:**
- "Share..." button on document settings → triggers login if not already logged in
- Login dialog (email/password or "Create account" link)
- Account indicator in IntelFrame header (avatar/name when logged in, nothing when offline)
- After login + upgrade: document settings panel shows member management, visibility controls, governance options

### Phase 3: P2P Networking
Live peer-to-peer sync per document.

**New files:**

- **`tradery-news/.../network/PeerServer.java`** — TLS TCP server on random port
- **`tradery-news/.../network/PeerConnection.java`** — single peer connection (read/write length-prefixed JSON messages)
- **`tradery-news/.../network/PeerManager.java`** — connection lifecycle, peer state, reconnection
- **`tradery-news/.../network/RendezvousClient.java`** — HTTP client for rendezvous server (OkHttp)
- **`tradery-news/.../network/LanDiscovery.java`** — mDNS `_tradery._tcp` for LAN peers
- **`tradery-news/.../network/SyncEngine.java`** — per-document manifest diff, entity transfer, signature verification, conflict resolution
- **`tradery-news/.../network/NetworkMessage.java`** — sealed interface with record subtypes for each message type

**Sync considerations with current model:**
- `replaceEntitiesBySource()` and `replaceRelationshipsBySource()` do atomic source replacement — P2P entities should use a distinct source tag (e.g., `"p2p:{authorId}"`) so source-based bulk operations don't interfere
- `entity_attribute_values` with Origin priority: when syncing, remote attribute values arrive as SOURCE or AI origin — they won't overwrite local USER edits (priority already handled by EntityStore)
- `SchemaType` sync: when receiving schema types from a peer, merge strategy is version-wins. User-added attributes on received types are preserved.

**UI changes:**
- "Network" panel in IntelFrame — online peers per document, sync status
- Per-document sync controls (manual sync button, auto-sync toggle)
- Received entity staging area for admin_approved documents
- Member management (invite by Keycloak user ID/email)

### Phase 4: Governance
Voting, admin review, submissions — per document.

**New files:**

- **`tradery-news/.../network/SubmissionStore.java`** — pending submissions, votes (stored in each document's entities.db as additional tables)
- **`tradery-news/.../network/GovernanceEngine.java`** — apply governance rules based on document.yaml config

**New tables in entities.db (for non-OPEN governance):**

```sql
submissions (
    uuid TEXT PRIMARY KEY,
    entity_uuid TEXT NOT NULL,
    submitter_id TEXT NOT NULL,
    status TEXT DEFAULT 'pending',    -- pending, approved, rejected
    submitted_at INTEGER,
    resolved_at INTEGER
)

votes (
    submission_uuid TEXT NOT NULL,
    voter_id TEXT NOT NULL,
    approve INTEGER NOT NULL,         -- 1=approve, 0=reject
    voted_at INTEGER,
    PRIMARY KEY (submission_uuid, voter_id)
)
```

**UI changes:**
- Review queue panel for document admins
- Voting UI for voting-type documents
- Submission status indicators on entities (pending/approved/rejected badge in EntityManagerFrame tree + CoinGraphPanel)

---

## Dependencies

**No new external Java dependencies needed for core P2P:**
- Ed25519: `java.security` (JDK 21 built-in — `KeyPairGenerator.getInstance("Ed25519")`)
- TLS sockets: `javax.net.ssl` (built-in)
- JSON: Jackson (already present — `com.fasterxml.jackson.databind`)
- YAML: Jackson YAML (already present — `com.fasterxml.jackson.dataformat.yaml`)
- HTTP for rendezvous: OkHttp (already present — `okhttp3`)
- UUID v7: Pure Java implementation (~20 LOC)
- mDNS: `javax.jmdns` or JDK multicast (built-in)

**New infrastructure:**
- Keycloak instance (Docker, free)
- Rendezvous server (~200 LOC HTTP service, free-tier cloud)

**module-info.java changes:**
```java
exports com.tradery.news.network;
opens com.tradery.news.network to com.fasterxml.jackson.databind;  // for Document YAML
```

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Account requirement | Optional, only for sharing | Solo users never blocked by auth; zero friction for local-only use |
| Sharing columns | Added on upgrade, not eagerly | Local documents have zero overhead — same perf as today |
| Entity data transport | P2P (direct TCP) | Avoid hosting data traffic |
| Auth/identity | Keycloak + local signing key | Proper user management with P2P integrity |
| Peer discovery | Hybrid (rendezvous + mDNS) | Works behind NAT + free LAN discovery |
| Conflict resolution | Version + author-wins | Simple, predictable, no CRDTs needed |
| Wire protocol | Length-prefixed JSON over TLS | Simple, debuggable, secure |
| Entity ID | UUID v7 alongside existing string `id` | Backward compatible, time-sortable |
| Document governance | Pluggable (open/admin/voting) | Flexible per-community needs |
| Attribute sync | Respects existing Origin priority | USER edits never overwritten by remote SOURCE data |
| EntityStore refactor | Constructor overload with `Path` | Minimal disruption — existing no-arg constructor still works |
| Schema sync unit | Full SchemaType + attributes | Documents carry their own schema — peers don't need pre-shared type defs |

---

## Verification

**Phase 1 (data model):**
- Compile: `./gradlew :tradery-news:compileJava`
- Restart intel: `scripts/kill-intel.sh && scripts/start-intel.sh`
- Verify `EntityStore(Path)` constructor works: `new EntityStore(Path.of("/tmp/test-entities.db"))`
- Verify existing entities work normally (no new columns yet — local document)
- Verify `isShared()` returns false on existing DB

**Phase 2 (documents, no account):**
- Create a new LOCAL document via UI
- Verify `~/.tradery/documents/{uuid}/` directory created with `entities.db` + `document.yaml` (visibility=local, ownerId=null)
- Switch between documents, verify entity isolation (different entity sets in each)
- Verify existing DB migrated into default LOCAL document
- Verify no members.yaml created for LOCAL documents
- Verify DataSourceRegistry works per-document (CoinGecko refresh goes to active document)

**Phase 2b (identity + upgrade):**
- Click "Share..." on a LOCAL document → prompted to log in
- Create Keycloak account, log in
- Upgrade document to FRIENDS visibility
- Verify sharing columns added: `sqlite3 ~/.tradery/documents/{uuid}/entities.db "SELECT id, uuid, author_id FROM entities LIMIT 5"`
- Verify all existing entities have UUIDs and author_id backfilled
- Verify members.yaml created with user as owner
- Create new entity after upgrade → verify UUID, author_id, signature populated automatically

**Phase 3 (P2P):**
- Run two instances on same machine (different `~/.tradery` dirs via system property)
- Share a document between them (both users logged in, document upgraded to FRIENDS)
- Create entity in instance A → appears in instance B after sync
- Verify signature verification rejects tampered entities
- Test mDNS discovery on LAN
- Test attribute value priority during sync (USER edit on B not overwritten by SOURCE data from A)

**Phase 4 (governance):**
- Create admin_approved document, submit entity from non-admin member → appears in review queue
- Admin approves → entity status changes to published, syncs to all peers
- Create voting document, submit entity, cast votes from multiple members, verify quorum logic
