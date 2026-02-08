# P2P Entity Sharing Architecture for Tradery Intel

> **Status:** Planning — awaiting entity model refactor completion before implementation
> **Created:** 2026-02-08

## Context

The Intel app is evolving from a crypto-specific tool into a **general-purpose entity/relationship platform** — the schema system is already fully dynamic, data ingestion is pluggable, and the UI is schema-aware. The next major leap is **multi-user collaboration** where users can share entities with selective visibility (private, friends, public) and data flows **peer-to-peer** between clients to avoid centralized hosting of entity traffic.

Key constraints:
- **Keycloak** handles user accounts (email registration, auth)
- **Spaces** provide governance models for shared entity collections
- **P2P sync** keeps data distribution off our servers (Napster/BitTorrent model)
- A tiny **rendezvous server** + **mDNS** handles peer discovery (hybrid)
- Hundreds to low thousands of users, not millions

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
- Entities, relationships, schema types flow directly between clients
- Signed by author's key for integrity
- Manifest-based diff sync (only transfer what's missing/changed)

---

## Core Concepts

### 1. User Identity (Keycloak)

Each user has:
- **Keycloak account** — email, display name, avatar
- **User UUID** — assigned by Keycloak, used as `author_id` on entities
- **Ed25519 signing keypair** — generated locally on first login, public key registered with Keycloak as a user attribute. Used to sign entities for P2P integrity verification.

### 2. Documents (Spaces)

Each **Document** is a fully self-contained SQLite database with its own entity graph, schema, member list, and governance. Think of it like a Google Doc — each is independent, separately shared, with its own permissions.

```
~/.tradery/documents/
├── index.yaml                          # List of known documents + metadata
├── {doc-uuid}/
│   ├── document.yaml                   # Document metadata (below)
│   ├── entities.db                     # Self-contained SQLite (entities, rels, schema)
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
owner_id: "user-uuid"
visibility: private | friends | public
governance:
  type: open | admin_approved | voting
  # open: any invited member can publish
  # admin_approved: owner/admins must approve
  # voting: members vote, accepted at quorum
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
- The existing `~/.tradery/entity-network.db` becomes the user's default private document
- Documents can be opened side-by-side (multiple windows or tabbed)
- P2P sync operates **per-document** — you sync specific documents with specific peers

**Visibility levels:**
- `private` — only you (default local workspace)
- `friends` — only explicitly listed members
- `public` — discoverable by anyone, anyone can request to join

**Governance types:**
- `open` — any member can publish entities directly
- `admin_approved` — submissions go to a review queue, admins accept/reject
- `voting` — members vote on submissions, accepted at quorum

### 3. Entities with Global Identity

Each document's `entities.db` has the same schema as today's `entity-network.db`, plus sharing columns:

```sql
-- Extended entities table (per-document DB)
ALTER TABLE entities ADD COLUMN uuid TEXT;            -- UUID v7 (globally unique)
ALTER TABLE entities ADD COLUMN author_id TEXT;        -- Keycloak user UUID
ALTER TABLE entities ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE entities ADD COLUMN signature TEXT;        -- Ed25519 sig of content hash
ALTER TABLE entities ADD COLUMN status TEXT DEFAULT 'published';
  -- published | pending_review | rejected

-- Same for relationships
ALTER TABLE relationships ADD COLUMN uuid TEXT;
ALTER TABLE relationships ADD COLUMN author_id TEXT;
ALTER TABLE relationships ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE relationships ADD COLUMN signature TEXT;
```

Note: no `space_id` or `visibility` on entities — the **document** itself is the isolation boundary. All entities in a document share the document's visibility and governance.

### 4. P2P Sync Protocol

**Wire protocol** — length-prefixed JSON over TLS TCP:

```
HELLO         → {peerId, publicKey, keycloakToken, documentIds:[...]}
MANIFEST      → {documentId, entityHashes:{uuid→"contentHash:version"}}
REQUEST       → {documentId, entityUuids:[...], relUuids:[...]}
ENTITIES      → {documentId, entities:[signed...], relationships:[signed...], schemaTypes:[...]}
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

**Conflict resolution:** Version number + author-wins. If two users edit the same entity, the original author's edit takes priority. Non-author edits create a fork (copy with new UUID).

---

## Implementation Plan (Phased)

### Phase 1: Data Model Foundation
Make the entity model sharing-ready without any networking.

**Files to modify:**
- `tradery-news/.../coin/EntityStore.java` — add UUID, author_id, version, signature, status columns via `addColumnIfMissing()` migration. Refactor constructor to accept a DB path (currently hardcoded) so multiple document DBs can be opened.
- `tradery-news/.../coin/CoinEntity.java` — add fields: uuid, authorId, version, signature, status
- `tradery-news/.../coin/CoinRelationship.java` — add fields: uuid, authorId, version, signature

**New files:**
- `tradery-news/.../network/EntitySigner.java` — canonical JSON serialization + Ed25519 sign/verify
- `tradery-news/.../network/UuidGenerator.java` — UUID v7 generation (JDK 21, ~20 lines)

**Changes:**
- EntityStore constructor takes `Path dbPath` instead of hardcoded `~/.tradery/entity-network.db`
- EntityStore.saveEntity() auto-generates UUID if null, sets author_id to current user
- EntityStore.mapEntityFromResultSet() reads new columns
- Existing entities get UUIDs on migration

### Phase 2: Documents & Identity
The document abstraction and Keycloak user accounts.

**New files:**
- `tradery-news/.../network/Document.java` — document metadata record (id, name, owner, visibility, governance)
- `tradery-news/.../network/DocumentManager.java` — manages `~/.tradery/documents/` directory, creates/opens/lists documents, each backed by its own EntityStore instance
- `tradery-news/.../network/DocumentMember.java` — member record (userId, role: owner/admin/member/viewer)
- `tradery-news/.../network/UserSession.java` — Keycloak login, JWT management, local Ed25519 keypair generation

**Migration:** The existing `~/.tradery/entity-network.db` becomes the user's default private document, moved into `~/.tradery/documents/{generated-uuid}/entities.db`.

**UI changes:**
- Document switcher in IntelFrame header (dropdown showing all local documents)
- "New Document" dialog (name, governance type)
- Document settings panel (manage members, visibility, governance)
- Each document opens its own EntityStore — UI panels rebind when switching

### Phase 3: P2P Networking
Live peer-to-peer sync per document.

**New files:**
- `tradery-news/.../network/PeerServer.java` — TLS TCP server on random port
- `tradery-news/.../network/PeerConnection.java` — single peer connection (read/write JSON messages)
- `tradery-news/.../network/PeerManager.java` — connection lifecycle, peer state
- `tradery-news/.../network/RendezvousClient.java` — HTTP client for rendezvous server
- `tradery-news/.../network/LanDiscovery.java` — mDNS `_tradery._tcp` for LAN peers
- `tradery-news/.../network/SyncEngine.java` — per-document manifest diff, entity transfer, conflict resolution
- `tradery-news/.../network/NetworkMessage.java` — message type records

**UI changes:**
- "Network" panel in IntelFrame — online peers per document, sync status
- Per-document sync controls (manual sync button, auto-sync toggle)
- Received entity staging area for admin_approved documents
- Member management (invite by Keycloak user ID/email)

### Phase 4: Governance
Voting, admin review, submissions — per document.

**New files:**
- `tradery-news/.../network/SubmissionStore.java` — pending submissions, votes (stored in each document's DB)
- `tradery-news/.../network/GovernanceEngine.java` — apply governance rules based on document.yaml config

**UI changes:**
- Review queue panel for document admins
- Voting UI for voting-type documents
- Submission status indicators on entities (pending/approved/rejected badge)

---

## Dependencies

**No new external Java dependencies needed for core P2P:**
- Ed25519: `java.security` (JDK 21 built-in)
- TLS sockets: `javax.net.ssl` (built-in)
- JSON: Jackson (already present)
- HTTP for rendezvous: OkHttp (already present)
- UUID v7: Pure Java implementation (~20 LOC)
- mDNS: `javax.jmdns` or JDK multicast (built-in)

**New infrastructure:**
- Keycloak instance (Docker, free)
- Rendezvous server (~200 LOC HTTP service, free-tier cloud)

**module-info.java** — add: `exports com.tradery.news.network;`

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Entity data transport | P2P (direct TCP) | Avoid hosting data traffic |
| Auth/identity | Keycloak + local signing key | Proper user management with P2P integrity |
| Peer discovery | Hybrid (rendezvous + mDNS) | Works behind NAT + free LAN discovery |
| Conflict resolution | Version + author-wins | Simple, predictable, no CRDTs needed |
| Wire protocol | Length-prefixed JSON over TLS | Simple, debuggable, secure |
| Entity ID | UUID v7 alongside existing string ID | Backward compatible, time-sortable |
| Document governance | Pluggable (open/admin/voting) | Flexible per-community needs |

---

## Verification

**Phase 1 (data model):**
- Compile: `./gradlew :tradery-news:compileJava`
- Restart intel: `scripts/kill-intel.sh && scripts/start-intel.sh`
- Verify existing entities get UUIDs: `sqlite3 ~/.tradery/entity-network.db "SELECT id, uuid FROM entities LIMIT 5"`
- Create new entity via UI → verify UUID, author_id, signature populated
- Verify EntityStore works with custom DB path (unit test or manual)

**Phase 2 (documents):**
- Create a new document via UI
- Verify `~/.tradery/documents/{uuid}/` directory created with `entities.db` + `document.yaml`
- Switch between documents, verify entity isolation
- Verify existing DB migrated into default document

**Phase 3 (P2P):**
- Run two instances on same machine (different data dirs)
- Share a document between them
- Create entity in instance A, sync to instance B
- Verify signature verification on received entities
- Test mDNS discovery on LAN

**Phase 4 (governance):**
- Create admin_approved document, submit entity from non-admin member, verify review queue
- Create voting document, submit entity, cast votes, verify quorum logic

---

## TODO: Update After Entity Model Refactor

> **IMPORTANT:** The entity model (CoinEntity, EntityStore, SchemaAttribute, SchemaRegistry, etc.) is currently being refactored. This plan needs to be updated once the new model stabilizes to reflect:
> - New attribute/entity type structures
> - Any renamed classes or changed DB schema
> - Updated file paths and package names
