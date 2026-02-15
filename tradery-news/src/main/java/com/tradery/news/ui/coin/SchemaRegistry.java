package com.tradery.news.ui.coin;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Singleton registry of dynamic schema types loaded from the EntityStore DB.
 * Types are seeded by individual DataSource implementations via seedSchemaTypes().
 */
public class SchemaRegistry {

    private final EntityStore store;
    private final Map<String, SchemaType> types = new LinkedHashMap<>();

    public SchemaRegistry(EntityStore store) {
        this.store = store;
        reload();
    }

    /** Reload all types from DB. */
    public void reload() {
        types.clear();
        List<SchemaType> loaded = store.loadSchemaTypes();
        for (SchemaType t : loaded) {
            types.put(t.id(), t);
        }
    }

    public SchemaType getType(String id) {
        return types.get(id);
    }

    public Collection<SchemaType> allTypes() {
        return types.values();
    }

    public List<SchemaType> entityTypes() {
        return types.values().stream()
            .filter(SchemaType::isEntity)
            .sorted(Comparator.comparingInt(SchemaType::displayOrder))
            .collect(Collectors.toList());
    }

    public List<SchemaType> relationshipTypes() {
        return types.values().stream()
            .filter(SchemaType::isRelationship)
            .sorted(Comparator.comparingInt(SchemaType::displayOrder))
            .collect(Collectors.toList());
    }

    /** Get relationship types where fromTypeId or toTypeId matches the given entity type. */
    public List<SchemaType> getRelationshipTypesFor(String entityTypeId) {
        return types.values().stream()
            .filter(SchemaType::isRelationship)
            .filter(t -> entityTypeId.equals(t.fromTypeId()) || entityTypeId.equals(t.toTypeId()))
            .collect(Collectors.toList());
    }

    /** Get relationship types that connect fromTypeId -> toTypeId. */
    public List<SchemaType> getRelationshipTypesBetween(String fromTypeId, String toTypeId) {
        return types.values().stream()
            .filter(SchemaType::isRelationship)
            .filter(t -> (fromTypeId.equals(t.fromTypeId()) && toTypeId.equals(t.toTypeId()))
                      || (fromTypeId.equals(t.toTypeId()) && toTypeId.equals(t.fromTypeId())))
            .collect(Collectors.toList());
    }

    public void save(SchemaType type) {
        store.setDraftMode(false);
        try {
            store.saveSchemaType(type);
            for (SchemaAttribute attr : type.attributes()) {
                store.saveSchemaAttribute(type.id(), attr);
            }
        } finally {
            store.setDraftMode(true);
        }
        types.put(type.id(), type);
    }

    public void deleteType(String id) {
        store.deleteSchemaType(id);
        types.remove(id);
    }

    public void addAttribute(String typeId, SchemaAttribute attr) {
        store.saveSchemaAttribute(typeId, attr);
        SchemaType type = types.get(typeId);
        if (type != null) {
            type.removeAttribute(attr.name());
            type.addAttribute(attr);
        }
    }

    /** Persist a single type's ERD position to DB. */
    public void savePosition(SchemaType type) {
        store.saveSchemaPosition(type);
    }

    /** Persist current ERD positions to DB. */
    public void savePositions() {
        store.saveSchemaPositions(types.values());
    }

    public void removeAttribute(String typeId, String attrName) {
        store.removeSchemaAttribute(typeId, attrName);
        SchemaType type = types.get(typeId);
        if (type != null) {
            type.removeAttribute(attrName);
        }
    }

    // ==================== ATTRIBUTE VALUE PASS-THROUGH ====================

    public void saveAttributeValue(String entityId, String typeId, String attrName, String value) {
        store.saveAttributeValue(entityId, typeId, attrName, value);
    }

    public void saveAttributeValue(String entityId, String typeId, String attrName, String value, AttributeValue.Origin origin) {
        store.saveAttributeValue(entityId, typeId, attrName, value, origin);
    }

    public Map<String, String> getAttributeValues(String entityId, String typeId) {
        return store.getAttributeValues(entityId, typeId);
    }

    public AttributeValue getAttributeValue(String entityId, String typeId, String attrName) {
        return store.getAttributeValue(entityId, typeId, attrName);
    }

    public Map<String, AttributeValue> getAttributeValuesRich(String entityId, String typeId) {
        return store.getAttributeValuesRich(entityId, typeId);
    }
}
