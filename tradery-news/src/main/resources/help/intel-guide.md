## Overview

**Intelligence** is a research tool for mapping the crypto ecosystem. It combines a **news timeline**, a **coin relationship graph**, and **AI-powered entity discovery** to help you understand connections between projects, investors, and exchanges.

### Views

| View | What It Shows |
|------|---------------|
| **News** | Timeline of articles from RSS feeds, with topic clustering |
| **Coin Relations** | Interactive graph of entities and their relationships |

Switch between views using the toggle in the top-left toolbar.

---

## News View

The news timeline visualizes articles as nodes on a time axis, clustered by topic.

### Controls

| Control | Action |
|---------|--------|
| **Show** dropdown | Limit number of articles displayed |
| **Fetch New** | Fetch latest articles from all RSS sources |
| Click node | View article details in the right panel |

> **Tip:** Articles are automatically processed by AI to extract entity mentions, topics, and events.

### Topics & Events

Articles are tagged with **topics** (DeFi, Layer 2, Regulation, etc.) and **events** (Hack, Launch, Partnership, etc.). These appear in the detail panel when you select an article.

---

## Coin Relations View

An interactive force-directed graph showing entities (coins, VCs, exchanges, people) and their relationships.

### Navigation

| Action | Effect |
|--------|--------|
| **Drag** | Pan the view |
| **Scroll** | Zoom in/out |
| **Click node** | Select entity, show details |
| **Double-click node** | Zoom to entity and neighbors |
| **Double-click background** | Fit all entities in view |
| **Reset View** | Return to default zoom and position |

### Entity Types

| Type | Description |
|------|-------------|
| **Coin** | Cryptocurrency or token project |
| **VC** | Venture capital firm or fund |
| **Exchange** | Centralized or decentralized exchange |
| **Person** | Notable individual in crypto |
| **Protocol** | DeFi protocol or infrastructure |
| **Organization** | Foundation, DAO, or company |

### Relationship Types

| Type | Meaning |
|------|---------|
| **INVESTED_IN** | VC invested in a project |
| **LISTED_ON** | Token listed on an exchange |
| **PARTNER_OF** | Strategic partnership |
| **FORK_OF** | Codebase forked from another project |
| **BUILT_ON** | Built on top of another protocol |
| **FOUNDED_BY** | Project founded by a person |
| **MEMBER_OF** | Person is part of an organization |

### AI Discovery

Right-click an entity or use the API to run **AI-powered discovery**. This searches for related entities and suggests new connections. Discovered entities can be reviewed and added to the graph.

> **Tip:** Discovery works best starting from well-known entities. Try discovering investors for a major coin, then discover their other portfolio companies to map the ecosystem.

---

## Data Structure (ERD)

The **Data Structure** window shows the entity-relationship schema as an interactive diagram. Use it to understand and edit the types and attributes that define your entity model.

---

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| **Escape** | Deselect current entity/article |

---

## API Access

The Intel app exposes an HTTP API for programmatic access. Endpoints include:

| Endpoint | Description |
|----------|-------------|
| `GET /stats` | Entity and article counts |
| `GET /entities` | List entities with optional type/search filter |
| `GET /entity/{id}/graph` | Get entity neighborhood graph |
| `GET /articles` | List articles with filters |
| `GET /topics` | Topic counts |
| `POST /entity/{id}/discover` | Run AI entity discovery |

> **Note:** API port is written to `~/.tradery/intel-api.port` on startup.
