package com.tradery.news.ui.coin;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Singleton registry of dynamic schema types loaded from the EntityStore DB.
 * Provides lookup methods that gradually replace enum usage.
 */
public class SchemaRegistry {

    private final EntityStore store;
    private final Map<String, SchemaType> types = new LinkedHashMap<>();

    public SchemaRegistry(EntityStore store) {
        this.store = store;
        reload();
    }

    /** Reload all types from DB. Seeds defaults if tables are empty. */
    public void reload() {
        types.clear();
        List<SchemaType> loaded = store.loadSchemaTypes();
        if (loaded.isEmpty()) {
            seedFromEnums();
            loaded = store.loadSchemaTypes();
        }
        for (SchemaType t : loaded) {
            types.put(t.id(), t);
        }
        // Incremental migrations for types added after initial seed
        seedIfMissing();
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
        store.saveSchemaType(type);
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

    public Map<String, String> getAttributeValues(String entityId, String typeId) {
        return store.getAttributeValues(entityId, typeId);
    }

    // ==================== SEED FROM EXISTING ENUMS ====================

    private void seedFromEnums() {
        int order = 0;

        // Entity types from CoinEntity.Type
        for (CoinEntity.Type enumType : CoinEntity.Type.values()) {
            SchemaType st = new SchemaType(
                enumType.name().toLowerCase(),
                formatEnumName(enumType.name()),
                enumType.color(),
                SchemaType.KIND_ENTITY
            );
            st.setDisplayOrder(order++);

            // Common attributes for all entity types
            st.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0));
            st.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1));

            // Type-specific attributes
            switch (enumType) {
                case COIN, L2, ETF, ETP, DAT -> {
                    st.addAttribute(new SchemaAttribute("market_cap", SchemaAttribute.CURRENCY, false, 2,
                        Map.of("en", "Market Cap"),
                        Map.of("currencyCode", "USD", "currencySymbol", "$",
                               "symbolPosition", "prefix", "decimalPlaces", 0)));
                }
                case VC, EXCHANGE, FOUNDATION, COMPANY, NEWS_SOURCE -> {
                    // no extra attributes beyond name/symbol
                }
            }

            store.saveSchemaType(st);
            for (SchemaAttribute attr : st.attributes()) {
                store.saveSchemaAttribute(st.id(), attr);
            }
        }

        // Relationship types from CoinRelationship.Type
        order = 0;
        for (CoinRelationship.Type enumType : CoinRelationship.Type.values()) {
            SchemaType st = new SchemaType(
                enumType.name().toLowerCase(),
                formatEnumName(enumType.name()),
                enumType.color(),
                SchemaType.KIND_RELATIONSHIP
            );
            st.setLabel(enumType.label());
            st.setDisplayOrder(order++);

            // Determine from/to types based on relationship semantics
            switch (enumType) {
                case L2_OF -> { st.setFromTypeId("l2"); st.setToTypeId("coin"); }
                case ETF_TRACKS -> { st.setFromTypeId("etf"); st.setToTypeId("coin"); }
                case ETP_TRACKS -> { st.setFromTypeId("etp"); st.setToTypeId("coin"); }
                case INVESTED_IN -> { st.setFromTypeId("vc"); st.setToTypeId("coin"); }
                case FOUNDED_BY -> { st.setFromTypeId("coin"); st.setToTypeId("foundation"); }
                case PARTNER -> { st.setFromTypeId("coin"); st.setToTypeId("coin"); }
                case FORK_OF -> { st.setFromTypeId("coin"); st.setToTypeId("coin"); }
                case BRIDGE -> { st.setFromTypeId("coin"); st.setToTypeId("coin"); }
                case ECOSYSTEM -> { st.setFromTypeId("coin"); st.setToTypeId("coin"); }
                case COMPETITOR -> { st.setFromTypeId("coin"); st.setToTypeId("coin"); }
            }

            // Add note attribute to all relationship types
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));

            store.saveSchemaType(st);
            for (SchemaAttribute attr : st.attributes()) {
                store.saveSchemaAttribute(st.id(), attr);
            }
        }

        // Crypto Category entity type (replaces categories LIST attribute)
        SchemaType catType = new SchemaType("crypto_category", "Crypto Category",
            new Color(180, 200, 140), SchemaType.KIND_ENTITY);
        catType.setDisplayOrder(order);
        catType.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0));
        store.saveSchemaType(catType);
        for (SchemaAttribute attr : catType.attributes()) {
            store.saveSchemaAttribute(catType.id(), attr);
        }

        // "in_category" relationship: coin -> crypto_category
        SchemaType inCat = new SchemaType("in_category", "In Category",
            new Color(160, 190, 130), SchemaType.KIND_RELATIONSHIP);
        inCat.setLabel("in");
        inCat.setFromTypeId("coin");
        inCat.setToTypeId("crypto_category");
        inCat.setDisplayOrder(order + 1);
        inCat.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
        store.saveSchemaType(inCat);
        for (SchemaAttribute attr : inCat.attributes()) {
            store.saveSchemaAttribute(inCat.id(), attr);
        }
    }

    /** Add types that were missing from the initial seed. */
    private void seedIfMissing() {
        // Exchange -> Coin relationship ("hosts pair")
        if (!types.containsKey("hosts_pair")) {
            SchemaType hp = new SchemaType("hosts_pair", "Hosts Pair",
                new Color(200, 160, 100), SchemaType.KIND_RELATIONSHIP);
            hp.setLabel("hosts");
            hp.setFromTypeId("exchange");
            hp.setToTypeId("coin");
            hp.setDisplayOrder(types.size());
            hp.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            store.saveSchemaType(hp);
            for (SchemaAttribute attr : hp.attributes()) store.saveSchemaAttribute(hp.id(), attr);
            types.put(hp.id(), hp);
        }

        // News Article entity type
        if (!types.containsKey("news_article")) {
            SchemaType na = new SchemaType("news_article", "News Article",
                new Color(220, 180, 100), SchemaType.KIND_ENTITY);
            na.setDisplayOrder(types.size());
            na.addAttribute(new SchemaAttribute("title", SchemaAttribute.TEXT, true, 0));
            na.addAttribute(new SchemaAttribute("url", SchemaAttribute.URL, false, 1));
            na.addAttribute(new SchemaAttribute("published_at", SchemaAttribute.DATETIME, false, 2,
                Map.of("en", "Published At"),
                Map.of("format", "yyyy-MM-dd HH:mm")));
            na.addAttribute(new SchemaAttribute("source", SchemaAttribute.TEXT, false, 3));
            store.saveSchemaType(na);
            for (SchemaAttribute attr : na.attributes()) store.saveSchemaAttribute(na.id(), attr);
            types.put(na.id(), na);
        }

        // News Article -> Coin relationship ("mentions")
        if (!types.containsKey("mentions")) {
            SchemaType m = new SchemaType("mentions", "Mentions",
                new Color(210, 170, 90), SchemaType.KIND_RELATIONSHIP);
            m.setLabel("mentions");
            m.setFromTypeId("news_article");
            m.setToTypeId("coin");
            m.setDisplayOrder(types.size());
            m.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            store.saveSchemaType(m);
            for (SchemaAttribute attr : m.attributes()) store.saveSchemaAttribute(m.id(), attr);
            types.put(m.id(), m);
        }

        // Topic entity type
        if (!types.containsKey("topic")) {
            SchemaType topic = new SchemaType("topic", "Topic",
                new Color(140, 180, 220), SchemaType.KIND_ENTITY);
            topic.setDisplayOrder(types.size());
            topic.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0));
            store.saveSchemaType(topic);
            for (SchemaAttribute attr : topic.attributes()) store.saveSchemaAttribute(topic.id(), attr);
            types.put(topic.id(), topic);
        }

        // News Article -> Topic relationship ("tagged")
        if (!types.containsKey("tagged")) {
            SchemaType tagged = new SchemaType("tagged", "Tagged",
                new Color(130, 170, 210), SchemaType.KIND_RELATIONSHIP);
            tagged.setLabel("tagged");
            tagged.setFromTypeId("news_article");
            tagged.setToTypeId("topic");
            tagged.setDisplayOrder(types.size());
            tagged.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            store.saveSchemaType(tagged);
            for (SchemaAttribute attr : tagged.attributes()) store.saveSchemaAttribute(tagged.id(), attr);
            types.put(tagged.id(), tagged);
        }

        // News Article -> News Source relationship ("published by")
        if (!types.containsKey("published_by")) {
            SchemaType pb = new SchemaType("published_by", "Published By",
                new Color(200, 180, 120), SchemaType.KIND_RELATIONSHIP);
            pb.setLabel("published by");
            pb.setFromTypeId("news_article");
            pb.setToTypeId("news_source");
            pb.setDisplayOrder(types.size());
            pb.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            store.saveSchemaType(pb);
            for (SchemaAttribute attr : pb.attributes()) store.saveSchemaAttribute(pb.id(), attr);
            types.put(pb.id(), pb);
        }
    }

    private static String formatEnumName(String enumName) {
        String[] parts = enumName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
