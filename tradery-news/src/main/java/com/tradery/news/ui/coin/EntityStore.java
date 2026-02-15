package com.tradery.news.ui.coin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.List;

/**
 * Entity storage backed by an append-only fact log with materialized current state.
 * Public API is unchanged from the original — callers (SchemaRegistry,
 * CoinGeckoSource, API handlers, etc.) require no changes.
 */
public class EntityStore {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final FactStore factStore;
    private boolean draftMode = true;
    private boolean curatedMode = false;
    private Runnable onPendingChanged;

    public EntityStore() {
        this.factStore = new FactStore();
    }

    public EntityStore(Path dbPath) {
        this.factStore = new FactStore(dbPath);
    }

    public FactStore factStore() { return factStore; }

    public void setDraftMode(boolean enabled) { this.draftMode = enabled; }
    public boolean isDraftMode() { return draftMode; }

    public void setOnPendingChanged(Runnable callback) { this.onPendingChanged = callback; }

    public boolean isCuratedMode() { return curatedMode; }

    /**
     * Enable or disable curated mode. When enabling for the first time,
     * bulk-accepts all existing entities to prevent data loss on transition.
     */
    public void setCuratedMode(boolean enabled) {
        this.curatedMode = enabled;
        if (enabled) {
            // If no entities are accepted yet, bulk-accept all existing ones (transition safety)
            if (factStore.getAcceptedCount() == 0) {
                List<CoinEntity> all = loadAllEntities();
                if (!all.isEmpty()) {
                    List<String> ids = new ArrayList<>();
                    for (CoinEntity e : all) ids.add(e.id());
                    factStore.acceptEntities(ids);
                }
            }
        }
    }

    // ==================== CURATED ENTITY QUERIES ====================

    /** Load only entities that are accepted in the local view (USER_CURATED mode). */
    public List<CoinEntity> loadAcceptedEntities() {
        Set<String> accepted = factStore.getAcceptedEntityIds();
        List<CoinEntity> all = loadAllEntities();
        return all.stream().filter(e -> accepted.contains(e.id())).toList();
    }

    /** Load entities that are NOT accepted (the "pool" in USER_CURATED mode). */
    public List<CoinEntity> loadUnacceptedEntities() {
        Set<String> accepted = factStore.getAcceptedEntityIds();
        List<CoinEntity> all = loadAllEntities();
        return all.stream().filter(e -> !accepted.contains(e.id())).toList();
    }

    /** Load relationships where both endpoints are accepted. */
    public List<CoinRelationship> loadAcceptedRelationships() {
        Set<String> accepted = factStore.getAcceptedEntityIds();
        List<CoinRelationship> all = loadAllRelationships();
        return all.stream()
                .filter(r -> accepted.contains(r.fromId()) && accepted.contains(r.toId()))
                .toList();
    }

    /** Accept an entity into the local view. */
    public void acceptEntity(String entityId) {
        factStore.acceptEntity(entityId);
    }

    /** Remove an entity from the local view. */
    public void unacceptEntity(String entityId) {
        factStore.unacceptEntity(entityId);
    }

    /** Check if an entity is accepted. */
    public boolean isEntityAccepted(String entityId) {
        return factStore.isEntityAccepted(entityId);
    }

    /** Get count of accepted entities. */
    public int getAcceptedCount() {
        return factStore.getAcceptedCount();
    }

    /** Get all accepted entity IDs. */
    public Set<String> getAcceptedEntityIds() {
        return factStore.getAcceptedEntityIds();
    }

    private void notifyPendingChanged() {
        if (onPendingChanged != null) {
            javax.swing.SwingUtilities.invokeLater(onPendingChanged);
        }
    }

    private boolean shouldStage(String source) {
        if (!draftMode) return false;
        return !"coingecko".equals(source) && !"auto".equals(source)
            && !"system".equals(source) && !"source".equals(source);
    }

    /** Route a single fact write through stage or append based on source. */
    private String routeFact(String entityId, String attribute, String value, String source) {
        if (shouldStage(source)) {
            String id = factStore.stageFact(entityId, attribute, value, source);
            notifyPendingChanged();
            return id;
        }
        return factStore.appendFact(entityId, attribute, value, source);
    }

    /** Route a batch fact write through stage or append based on source. */
    private void routeFacts(List<FactStore.PendingFact> facts, String source) {
        if (shouldStage(source)) {
            factStore.stageFacts(facts);
            notifyPendingChanged();
        } else {
            factStore.appendFacts(facts);
        }
    }

    // ==================== COMMIT / ROLLBACK ====================

    public String commit() {
        String commitId = factStore.commit();
        notifyPendingChanged();
        return commitId;
    }

    public void rollback() {
        factStore.rollback();
        notifyPendingChanged();
    }

    public int getPendingCount() { return factStore.getPendingCount(); }

    public List<FactStore.PendingChange> getPendingSummary() { return factStore.getPendingSummary(); }

    // ==================== CACHE VALIDITY ====================

    public boolean isSourceCacheValid(String sourceId, Duration ttl) {
        String ts = factStore.getCurrent("_meta", "cache:" + sourceId + "_last_fetch");
        if (ts == null) return false;
        try {
            long lastFetch = Long.parseLong(ts);
            return System.currentTimeMillis() - lastFetch < ttl.toMillis();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void updateSourceFetchTime(String sourceId) {
        routeFact("_meta", "cache:" + sourceId + "_last_fetch",
                String.valueOf(System.currentTimeMillis()), "system");
    }

    // ==================== ENTITY CRUD ====================

    public List<CoinEntity> loadAllEntities() {
        return loadEntitiesWithType(null);
    }

    public List<CoinEntity> loadEntitiesBySource(String source) {
        return loadEntitiesWithType(source);
    }

    private List<CoinEntity> loadEntitiesWithType(String sourceFilter) {
        List<CoinEntity> entities = new ArrayList<>();

        // Get all entity_ids that have a 'type' attribute (= data entities)
        // We query the current table for attribute='type'
        List<String> candidates;
        if (sourceFilter != null) {
            candidates = factStore.findByAttribute("_source", sourceFilter);
        } else {
            // All entities with a 'type' attribute — but we need to find them
            // Use a raw query approach: find entity_ids where attribute = 'type'
            candidates = findEntityIdsWithAttribute("type");
        }

        for (String eid : candidates) {
            if (eid.startsWith("_")) continue; // Skip _type:, _rel:, _meta
            if (factStore.isDeleted(eid)) continue;
            if (sourceFilter != null) {
                // Verify it has a 'type' attribute (it's actually a data entity)
                String typeVal = factStore.getCurrent(eid, "type");
                if (typeVal == null) continue;
            }
            CoinEntity entity = reconstructEntity(eid);
            if (entity != null) entities.add(entity);
        }
        return entities;
    }

    private List<String> findEntityIdsWithAttribute(String attribute) {
        return factStore.findByAttributeNotNull(attribute);
    }

    private CoinEntity reconstructEntity(String eid) {
        Map<String, String> attrs = factStore.getCurrentMap(eid);
        String typeStr = attrs.get("type");
        if (typeStr == null) return null;

        CoinEntity.Type type;
        try {
            type = CoinEntity.Type.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String name = attrs.getOrDefault("name", eid);
        String symbol = attrs.get("symbol");
        String parentId = attrs.get("parent");

        CoinEntity entity = new CoinEntity(eid, name, symbol, type, parentId);

        String marketCapStr = attrs.get("market_cap");
        if (marketCapStr != null) {
            try {
                entity.setMarketCap(Double.parseDouble(marketCapStr));
            } catch (NumberFormatException ignored) {}
        }

        // Load categories from cat:* attributes
        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            if (entry.getKey().startsWith("cat:") && "1".equals(entry.getValue())) {
                entity.addCategory(entry.getKey().substring(4));
            }
        }

        return entity;
    }

    public CoinEntity getEntity(String id) {
        if (id == null) return null;
        if (factStore.isDeleted(id)) return null;
        String typeStr = factStore.getCurrent(id, "type");
        if (typeStr == null) return null;
        return reconstructEntity(id);
    }

    public void saveEntity(CoinEntity entity, String source) {
        List<FactStore.PendingFact> facts = new ArrayList<>();
        facts.add(new FactStore.PendingFact(entity.id(), "type", entity.type().name(), source));
        facts.add(new FactStore.PendingFact(entity.id(), "name", entity.name(), source));
        facts.add(new FactStore.PendingFact(entity.id(), "symbol", entity.symbol(), source));
        facts.add(new FactStore.PendingFact(entity.id(), "parent", entity.parentId(), source));
        facts.add(new FactStore.PendingFact(entity.id(), "market_cap", String.valueOf(entity.marketCap()), source));
        facts.add(new FactStore.PendingFact(entity.id(), "_source", source, source));

        // Remove _deleted if it was previously set
        facts.add(new FactStore.PendingFact(entity.id(), "_deleted", null, source));

        // Categories
        for (String cat : entity.categories()) {
            facts.add(new FactStore.PendingFact(entity.id(), "cat:" + cat, "1", source));
        }

        routeFacts(facts, source);

        // Auto-accept locally created entities in curated mode
        if (curatedMode) {
            factStore.acceptEntity(entity.id());
        }
    }

    public void replaceEntitiesBySource(String sourceId, List<CoinEntity> entities) {
        // Get current entity IDs with this source
        List<String> currentIds = factStore.findByAttribute("_source", sourceId);
        Set<String> currentEntityIds = new HashSet<>();
        for (String id : currentIds) {
            if (!id.startsWith("_") && !factStore.isDeleted(id)) {
                // Verify it's an actual entity (has 'type' attribute)
                if (factStore.getCurrent(id, "type") != null) {
                    currentEntityIds.add(id);
                }
            }
        }

        Set<String> newIds = new HashSet<>();
        for (CoinEntity e : entities) newIds.add(e.id());

        List<FactStore.PendingFact> facts = new ArrayList<>();

        // Mark removed entities as deleted
        for (String oldId : currentEntityIds) {
            if (!newIds.contains(oldId)) {
                facts.add(new FactStore.PendingFact(oldId, "_deleted", "1", sourceId));
            }
        }

        // Add/update entities
        for (CoinEntity entity : entities) {
            facts.add(new FactStore.PendingFact(entity.id(), "type", entity.type().name(), sourceId));
            facts.add(new FactStore.PendingFact(entity.id(), "name", entity.name(), sourceId));
            facts.add(new FactStore.PendingFact(entity.id(), "symbol", entity.symbol(), sourceId));
            facts.add(new FactStore.PendingFact(entity.id(), "parent", entity.parentId(), sourceId));
            facts.add(new FactStore.PendingFact(entity.id(), "market_cap", String.valueOf(entity.marketCap()), sourceId));
            facts.add(new FactStore.PendingFact(entity.id(), "_source", sourceId, sourceId));
            facts.add(new FactStore.PendingFact(entity.id(), "_deleted", null, sourceId));

            for (String cat : entity.categories()) {
                facts.add(new FactStore.PendingFact(entity.id(), "cat:" + cat, "1", sourceId));
            }
        }

        // Update cache time
        facts.add(new FactStore.PendingFact("_meta", "cache:" + sourceId + "_last_fetch",
                String.valueOf(System.currentTimeMillis()), "system"));

        routeFacts(facts, sourceId);
        System.out.println("Saved " + entities.size() + " entities from source '" + sourceId + "'");
    }

    public void deleteEntity(String id) {
        List<FactStore.PendingFact> facts = new ArrayList<>();
        facts.add(new FactStore.PendingFact(id, "_deleted", "1", "manual"));

        // Also delete relationships involving this entity
        List<String> relIds = factStore.findByEntityIdPattern("_rel:" + id + ":%");
        for (String relId : relIds) {
            facts.add(new FactStore.PendingFact(relId, "_deleted", "1", "manual"));
        }
        // Also check for relationships where this entity is the target
        List<String> relIds2 = factStore.findByEntityIdPattern("_rel:%:%" + id);
        for (String relId : relIds2) {
            // Verify this relationship actually references our entity as 'to'
            String to = factStore.getCurrent(relId, "to");
            if (id.equals(to)) {
                facts.add(new FactStore.PendingFact(relId, "_deleted", "1", "manual"));
            }
        }

        routeFacts(facts, "manual");
    }

    public boolean entityExists(String id) {
        if (id == null) return false;
        if (factStore.isDeleted(id)) return false;
        return factStore.getCurrent(id, "type") != null;
    }

    // ==================== RELATIONSHIP CRUD ====================

    public List<CoinRelationship> loadAllRelationships() {
        List<CoinRelationship> rels = new ArrayList<>();
        List<String> relIds = factStore.findByEntityIdPattern("_rel:%");
        for (String relId : relIds) {
            if (factStore.isDeleted(relId)) continue;
            CoinRelationship rel = reconstructRelationship(relId);
            if (rel != null) rels.add(rel);
        }
        return rels;
    }

    public List<CoinRelationship> loadRelationshipsBySource(String source) {
        List<CoinRelationship> rels = new ArrayList<>();
        List<String> relIds = factStore.findByEntityIdPattern("_rel:%");
        for (String relId : relIds) {
            if (factStore.isDeleted(relId)) continue;
            String src = factStore.getCurrent(relId, "_source");
            if (!source.equals(src)) continue;
            CoinRelationship rel = reconstructRelationship(relId);
            if (rel != null) rels.add(rel);
        }
        return rels;
    }

    private CoinRelationship reconstructRelationship(String relId) {
        Map<String, String> attrs = factStore.getCurrentMap(relId);
        String fromId = attrs.get("from");
        String toId = attrs.get("to");
        String typeStr = attrs.get("rel_type");
        String note = attrs.get("note");

        if (fromId == null || toId == null || typeStr == null) return null;

        // Normalize enum-style names (e.g. "ETF_TRACKS") to lowercase schema IDs ("etf_tracks")
        String typeId = typeStr.toLowerCase();
        return new CoinRelationship(fromId, toId, typeId, note);
    }

    private static String relEntityId(String fromId, String typeStr, String toId) {
        return "_rel:" + fromId + ":" + typeStr.toLowerCase() + ":" + toId;
    }

    public void saveRelationship(CoinRelationship rel, String source) {
        String relId = relEntityId(rel.fromId(), rel.typeId(), rel.toId());
        List<FactStore.PendingFact> facts = List.of(
                new FactStore.PendingFact(relId, "from", rel.fromId(), source),
                new FactStore.PendingFact(relId, "to", rel.toId(), source),
                new FactStore.PendingFact(relId, "rel_type", rel.typeId(), source),
                new FactStore.PendingFact(relId, "_source", source, source),
                new FactStore.PendingFact(relId, "note", rel.note(), source),
                new FactStore.PendingFact(relId, "_deleted", null, source)
        );
        routeFacts(new ArrayList<>(facts), source);
    }

    public void replaceRelationshipsBySource(String sourceId, List<CoinRelationship> relationships) {
        // Get current relationship IDs with this source
        List<String> allRelIds = factStore.findByEntityIdPattern("_rel:%");
        Set<String> currentRelIds = new HashSet<>();
        for (String relId : allRelIds) {
            if (!factStore.isDeleted(relId)) {
                String src = factStore.getCurrent(relId, "_source");
                if (sourceId.equals(src)) {
                    currentRelIds.add(relId);
                }
            }
        }

        Set<String> newRelIds = new HashSet<>();
        for (CoinRelationship rel : relationships) {
            newRelIds.add(relEntityId(rel.fromId(), rel.typeId(), rel.toId()));
        }

        List<FactStore.PendingFact> facts = new ArrayList<>();

        // Mark removed relationships as deleted
        for (String oldId : currentRelIds) {
            if (!newRelIds.contains(oldId)) {
                facts.add(new FactStore.PendingFact(oldId, "_deleted", "1", sourceId));
            }
        }

        // Add/update relationships
        for (CoinRelationship rel : relationships) {
            String relId = relEntityId(rel.fromId(), rel.typeId(), rel.toId());
            facts.add(new FactStore.PendingFact(relId, "from", rel.fromId(), sourceId));
            facts.add(new FactStore.PendingFact(relId, "to", rel.toId(), sourceId));
            facts.add(new FactStore.PendingFact(relId, "rel_type", rel.typeId(), sourceId));
            facts.add(new FactStore.PendingFact(relId, "_source", sourceId, sourceId));
            facts.add(new FactStore.PendingFact(relId, "note", rel.note(), sourceId));
            facts.add(new FactStore.PendingFact(relId, "_deleted", null, sourceId));
        }

        routeFacts(facts, sourceId);
        System.out.println("Saved " + relationships.size() + " relationships from source '" + sourceId + "'");
    }

    public void deleteRelationship(String fromId, String toId, String typeId) {
        String relId = relEntityId(fromId, typeId, toId);
        routeFact(relId, "_deleted", "1", "manual");
    }

    public boolean relationshipExists(String fromId, String toId, String typeId) {
        String relId = relEntityId(fromId, typeId, toId);
        if (factStore.isDeleted(relId)) return false;
        return factStore.getCurrent(relId, "rel_type") != null;
    }

    // ==================== STATS ====================

    public int getEntityCount() {
        return factStore.countByAttributeNotNull("type");
    }

    public int getManualEntityCount() {
        return factStore.countByAttributeNotNullAndSource("type", "manual");
    }

    public int getRelationshipCount() {
        List<String> relIds = factStore.findByEntityIdPattern("_rel:%");
        int count = 0;
        for (String relId : relIds) {
            if (!factStore.isDeleted(relId)) count++;
        }
        return count;
    }

    public int getManualRelationshipCount() {
        List<String> relIds = factStore.findByEntityIdPattern("_rel:%");
        int count = 0;
        for (String relId : relIds) {
            if (!factStore.isDeleted(relId) && "manual".equals(factStore.getCurrent(relId, "_source"))) {
                count++;
            }
        }
        return count;
    }

    // ==================== SCHEMA TYPE CRUD ====================

    public List<SchemaType> loadSchemaTypes() {
        List<SchemaType> types = new ArrayList<>();
        List<String> typeIds = factStore.findByEntityIdPattern("_type:%");
        for (String eid : typeIds) {
            if (factStore.isDeleted(eid)) continue;
            SchemaType st = reconstructSchemaType(eid);
            if (st != null) types.add(st);
        }
        types.sort(Comparator.comparingInt(SchemaType::displayOrder));
        return types;
    }

    private SchemaType reconstructSchemaType(String eid) {
        Map<String, String> attrs = factStore.getCurrentMap(eid);
        String name = attrs.get("name");
        String kind = attrs.get("kind");
        if (name == null || kind == null) return null;

        String id = eid.substring("_type:".length());
        SchemaType type = new SchemaType();
        type.setId(id);
        type.setName(name);
        type.setColor(SchemaType.parseColor(attrs.get("color")));
        type.setKind(kind);
        type.setFromTypeId(attrs.get("from_type"));
        type.setToTypeId(attrs.get("to_type"));
        type.setLabel(attrs.get("label"));

        String displayOrder = attrs.get("display_order");
        if (displayOrder != null) {
            try { type.setDisplayOrder(Integer.parseInt(displayOrder)); } catch (NumberFormatException ignored) {}
        }

        // Metadata fields
        type.setInverseLabel(attrs.get("inverse_label"));
        type.setPluralLabel(attrs.get("plural_label"));
        type.setInversePluralLabel(attrs.get("inverse_plural_label"));
        type.setSearchDescription(attrs.get("search_desc"));
        type.setInverseSearchDescription(attrs.get("inverse_search_desc"));
        type.setHasMarketCap("true".equals(attrs.get("has_market_cap")));

        // Search hints (stored as JSON arrays)
        String searchHintsJson = attrs.get("search_hints");
        if (searchHintsJson != null) {
            type.setSearchHints(parseStringList(searchHintsJson));
        }
        String inverseSearchHintsJson = attrs.get("inverse_search_hints");
        if (inverseSearchHintsJson != null) {
            type.setInverseSearchHints(parseStringList(inverseSearchHintsJson));
        }

        String erdX = attrs.get("erd_x");
        String erdY = attrs.get("erd_y");
        if (erdX != null) {
            try { type.setErdX(Double.parseDouble(erdX)); } catch (NumberFormatException ignored) {}
        }
        if (erdY != null) {
            try { type.setErdY(Double.parseDouble(erdY)); } catch (NumberFormatException ignored) {}
        }

        // Load attributes from attr:* entries
        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            if (entry.getKey().startsWith("attr:") && entry.getValue() != null) {
                String attrName = entry.getKey().substring("attr:".length());
                SchemaAttribute attr = parseSchemaAttributeJson(attrName, entry.getValue());
                if (attr != null) type.addAttribute(attr);
            }
        }

        // Load form layouts
        String formLayoutsJson = attrs.get("form_layouts");
        if (formLayoutsJson != null) {
            type.setFormLayouts(parseFormLayouts(formLayoutsJson));
        }

        return type;
    }

    private SchemaAttribute parseSchemaAttributeJson(String attrName, String json) {
        try {
            Map<String, Object> blob = JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            String dataType = (String) blob.getOrDefault("dataType", SchemaAttribute.TEXT);
            boolean required = Boolean.TRUE.equals(blob.get("required"));
            int displayOrder = blob.containsKey("displayOrder") ? ((Number) blob.get("displayOrder")).intValue() : 0;

            @SuppressWarnings("unchecked")
            Map<String, String> labels = (Map<String, String>) blob.get("labels");
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) blob.get("config");

            SchemaAttribute.Mutability mut = SchemaAttribute.Mutability.MANUAL;
            String mutStr = (String) blob.get("mutability");
            if (mutStr != null) {
                try { mut = SchemaAttribute.Mutability.valueOf(mutStr); } catch (IllegalArgumentException ignored) {}
            }

            return new SchemaAttribute(attrName, dataType, required, displayOrder, labels, config, mut);
        } catch (Exception e) {
            System.err.println("Failed to parse schema attribute JSON for '" + attrName + "': " + e.getMessage());
            return null;
        }
    }

    private String schemaAttributeToJson(SchemaAttribute attr) {
        try {
            Map<String, Object> blob = new LinkedHashMap<>();
            blob.put("dataType", attr.dataType());
            blob.put("required", attr.required());
            blob.put("displayOrder", attr.displayOrder());
            blob.put("labels", attr.labels());
            blob.put("config", attr.config());
            blob.put("mutability", attr.mutability().name());
            return JSON.writeValueAsString(blob);
        } catch (Exception e) {
            return null;
        }
    }

    public void saveSchemaType(SchemaType type) {
        String eid = "_type:" + type.id();
        List<FactStore.PendingFact> facts = new ArrayList<>();
        facts.add(new FactStore.PendingFact(eid, "name", type.name(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "color", type.colorHex(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "kind", type.kind(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "from_type", type.fromTypeId(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "to_type", type.toTypeId(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "label", type.label(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "inverse_label", type.inverseLabel(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "plural_label", type.pluralLabel(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "inverse_plural_label", type.inversePluralLabel(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "search_desc", type.searchDescription(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "inverse_search_desc", type.inverseSearchDescription(), "manual"));
        facts.add(new FactStore.PendingFact(eid, "search_hints", toJsonStringList(type.searchHints()), "manual"));
        facts.add(new FactStore.PendingFact(eid, "inverse_search_hints", toJsonStringList(type.inverseSearchHints()), "manual"));
        facts.add(new FactStore.PendingFact(eid, "has_market_cap", type.hasMarketCap() ? "true" : null, "manual"));
        facts.add(new FactStore.PendingFact(eid, "display_order", String.valueOf(type.displayOrder()), "manual"));
        facts.add(new FactStore.PendingFact(eid, "erd_x", String.valueOf(type.erdX()), "manual"));
        facts.add(new FactStore.PendingFact(eid, "erd_y", String.valueOf(type.erdY()), "manual"));
        facts.add(new FactStore.PendingFact(eid, "form_layouts", formLayoutsToJson(type.formLayouts()), "manual"));
        facts.add(new FactStore.PendingFact(eid, "_deleted", null, "manual"));
        routeFacts(facts, "manual");
    }

    public void deleteSchemaType(String id) {
        String eid = "_type:" + id;
        routeFact(eid, "_deleted", "1", "manual");
    }

    public void saveSchemaAttribute(String typeId, SchemaAttribute attr) {
        String eid = "_type:" + typeId;
        String json = schemaAttributeToJson(attr);
        routeFact(eid, "attr:" + attr.name(), json, "manual");
    }

    public void saveSchemaPosition(SchemaType type) {
        String eid = "_type:" + type.id();
        List<FactStore.PendingFact> facts = List.of(
            new FactStore.PendingFact(eid, "erd_x", String.valueOf(type.erdX()), "manual"),
            new FactStore.PendingFact(eid, "erd_y", String.valueOf(type.erdY()), "manual")
        );
        routeFacts(facts, "manual");
    }

    public void saveSchemaPositions(Collection<SchemaType> types) {
        if (types.isEmpty()) return;
        List<FactStore.PendingFact> facts = new ArrayList<>();
        for (SchemaType t : types) {
            String eid = "_type:" + t.id();
            facts.add(new FactStore.PendingFact(eid, "erd_x", String.valueOf(t.erdX()), "manual"));
            facts.add(new FactStore.PendingFact(eid, "erd_y", String.valueOf(t.erdY()), "manual"));
        }
        routeFacts(facts, "manual");
    }

    public void removeSchemaAttribute(String typeId, String attrName) {
        String eid = "_type:" + typeId;
        // Tombstone the attribute by setting value to null
        routeFact(eid, "attr:" + attrName, null, "manual");
    }

    // ==================== ATTRIBUTE VALUE CRUD ====================

    public void saveAttributeValue(String entityId, String typeId, String attrName, String value) {
        saveAttributeValue(entityId, typeId, attrName, value, AttributeValue.Origin.USER);
    }

    public void saveAttributeValue(String entityId, String typeId, String attrName, String value, AttributeValue.Origin origin) {
        // Check current origin, enforce priority (SOURCE < AI < USER)
        String currentOriginStr = factStore.getCurrent(entityId, "val:" + typeId + ":" + attrName + ":origin");
        if (currentOriginStr != null) {
            try {
                AttributeValue.Origin existingOrigin = AttributeValue.Origin.valueOf(currentOriginStr);
                if (!existingOrigin.canBeOverriddenBy(origin)) {
                    return; // Skip: existing value has higher priority
                }
            } catch (IllegalArgumentException ignored) {}
        }

        String source = originToSource(origin);
        routeFacts(List.of(
                new FactStore.PendingFact(entityId, "val:" + typeId + ":" + attrName, value, source),
                new FactStore.PendingFact(entityId, "val:" + typeId + ":" + attrName + ":origin", origin.name(), source)
        ), source);
    }

    public AttributeValue getAttributeValue(String entityId, String typeId, String attrName) {
        String value = factStore.getCurrent(entityId, "val:" + typeId + ":" + attrName);
        if (value == null) return null;
        String originStr = factStore.getCurrent(entityId, "val:" + typeId + ":" + attrName + ":origin");
        AttributeValue.Origin origin = AttributeValue.Origin.USER;
        if (originStr != null) {
            try { origin = AttributeValue.Origin.valueOf(originStr); } catch (IllegalArgumentException ignored) {}
        }
        // We don't store updatedAt separately in the fact model; use 0 as placeholder
        return AttributeValue.of(value, origin, 0);
    }

    public Map<String, AttributeValue> getAttributeValuesRich(String entityId, String typeId) {
        Map<String, AttributeValue> values = new LinkedHashMap<>();
        String prefix = "val:" + typeId + ":";
        Map<String, String> attrs = factStore.getCurrentByPrefix(entityId, prefix);

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String key = entry.getKey().substring(prefix.length());
            if (key.endsWith(":origin")) continue; // Skip origin entries
            String value = entry.getValue();
            String originStr = attrs.get(prefix + key + ":origin");
            AttributeValue.Origin origin = AttributeValue.Origin.USER;
            if (originStr != null) {
                try { origin = AttributeValue.Origin.valueOf(originStr); } catch (IllegalArgumentException ignored) {}
            }
            values.put(key, AttributeValue.of(value, origin, 0));
        }
        return values;
    }

    public void updateMutabilityIfDefault(String typeId, String attrName, SchemaAttribute.Mutability mut) {
        // Load the current schema attribute JSON, check mutability, update if MANUAL
        String eid = "_type:" + typeId;
        String json = factStore.getCurrent(eid, "attr:" + attrName);
        if (json == null) return;

        SchemaAttribute current = parseSchemaAttributeJson(attrName, json);
        if (current == null) return;
        if (current.mutability() != SchemaAttribute.Mutability.MANUAL) return;

        // Re-create with updated mutability
        SchemaAttribute updated = new SchemaAttribute(
                current.name(), current.dataType(), current.required(), current.displayOrder(),
                current.labels(), current.config(), mut);
        String updatedJson = schemaAttributeToJson(updated);
        routeFact(eid, "attr:" + attrName, updatedJson, "manual");
    }

    public Map<String, String> getAttributeValues(String entityId, String typeId) {
        Map<String, String> values = new LinkedHashMap<>();
        String prefix = "val:" + typeId + ":";
        Map<String, String> attrs = factStore.getCurrentByPrefix(entityId, prefix);

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String key = entry.getKey().substring(prefix.length());
            if (key.endsWith(":origin")) continue;
            values.put(key, entry.getValue());
        }
        return values;
    }

    public Map<String, String> getAllAttributeValues(String entityId) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> attrs = factStore.getCurrentByPrefix(entityId, "val:");

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String key = entry.getKey().substring("val:".length());
            if (key.endsWith(":origin")) continue;
            // Extract just the attr_name part (after typeId:)
            int colonIdx = key.indexOf(':');
            if (colonIdx >= 0) {
                values.put(key.substring(colonIdx + 1), entry.getValue());
            }
        }
        return values;
    }

    // ==================== HELPERS ====================

    private List<FormLayout> parseFormLayouts(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            List<Map<String, Object>> raw = JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<FormLayout> layouts = new ArrayList<>();
            for (Map<String, Object> layoutMap : raw) {
                String name = (String) layoutMap.get("name");
                @SuppressWarnings("unchecked")
                List<Map<String, String>> rawFields = (List<Map<String, String>>) layoutMap.get("fields");
                List<FormLayout.FormLayoutField> fields = new ArrayList<>();
                if (rawFields != null) {
                    for (Map<String, String> f : rawFields) {
                        fields.add(new FormLayout.FormLayoutField(f.get("attr"), f.get("group")));
                    }
                }
                layouts.add(new FormLayout(name, fields));
            }
            return layouts.isEmpty() ? null : layouts;
        } catch (Exception e) {
            System.err.println("Failed to parse form_layouts JSON: " + e.getMessage());
            return null;
        }
    }

    private String formLayoutsToJson(List<FormLayout> layouts) {
        if (layouts == null || layouts.isEmpty()) return null;
        try {
            List<Map<String, Object>> raw = new ArrayList<>();
            for (FormLayout layout : layouts) {
                Map<String, Object> layoutMap = new LinkedHashMap<>();
                layoutMap.put("name", layout.name());
                List<Map<String, String>> rawFields = new ArrayList<>();
                for (FormLayout.FormLayoutField f : layout.fields()) {
                    Map<String, String> fm = new LinkedHashMap<>();
                    fm.put("attr", f.attributeName());
                    if (f.group() != null) fm.put("group", f.group());
                    rawFields.add(fm);
                }
                layoutMap.put("fields", rawFields);
                raw.add(layoutMap);
            }
            return JSON.writeValueAsString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJsonStringList(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    private static String originToSource(AttributeValue.Origin origin) {
        return switch (origin) {
            case SOURCE -> "source";
            case AI -> "ai";
            case USER -> "manual";
        };
    }

    public void close() {
        factStore.close();
    }
}
