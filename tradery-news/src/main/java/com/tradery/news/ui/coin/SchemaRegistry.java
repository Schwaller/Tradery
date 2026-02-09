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
        // Seeding must bypass draft mode — these are system writes
        store.setDraftMode(false);
        try {
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

            // Re-read types to pick up any mutability migration changes
            types.clear();
            for (SchemaType t : store.loadSchemaTypes()) {
                types.put(t.id(), t);
            }
        } finally {
            store.setDraftMode(true);
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

            // Common attributes for all entity types (SOURCE = read-only, set by data sources)
            st.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0,
                null, null, SchemaAttribute.Mutability.SOURCE));
            st.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1,
                null, null, SchemaAttribute.Mutability.SOURCE));

            // Type-specific attributes + hasMarketCap flag
            switch (enumType) {
                case COIN, L2, ETF, ETP, DAT -> {
                    st.setHasMarketCap(true);
                    st.addAttribute(new SchemaAttribute("market_cap", SchemaAttribute.CURRENCY, false, 2,
                        Map.of("en", "Market Cap"),
                        Map.of("currencyCode", "USD", "currencySymbol", "$",
                               "symbolPosition", "prefix", "decimalPlaces", 0),
                        SchemaAttribute.Mutability.SOURCE));
                }
                case VC, EXCHANGE, FOUNDATION, COMPANY, NEWS_SOURCE, RISK, STRENGTH -> {
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

            // Determine from/to types + metadata based on relationship semantics
            switch (enumType) {
                case L2_OF -> {
                    st.setFromTypeId("l2"); st.setToTypeId("coin");
                    st.setInverseLabel("L1 for");
                    st.setPluralLabel("L1"); // from L2's perspective: "show me L1"
                    st.setInversePluralLabel("L2s"); // from coin's perspective: "show me L2s"
                    st.setSearchDescription("The L1 blockchain that %s is built on");
                    st.setInverseSearchDescription("Layer 2 networks built on %s");
                    st.setSearchHints(List.of("%s Layer 1 blockchain built on"));
                    st.setInverseSearchHints(List.of("%s Layer 2 networks rollups", "%s L2 scaling solutions"));
                }
                case ETF_TRACKS -> {
                    st.setFromTypeId("etf"); st.setToTypeId("coin");
                    st.setInverseLabel("tracked by");
                    st.setPluralLabel("Tracks"); // from ETF's perspective
                    st.setInversePluralLabel("ETFs"); // from coin's perspective
                    st.setSearchDescription("Cryptocurrencies that %s tracks");
                    st.setInverseSearchDescription("ETFs (Exchange-Traded Funds) that track %s");
                    st.setSearchHints(List.of("%s ETF holdings cryptocurrency"));
                    st.setInverseSearchHints(List.of("%s cryptocurrency ETF list spot", "%s ETF approved SEC"));
                }
                case ETP_TRACKS -> {
                    st.setFromTypeId("etp"); st.setToTypeId("coin");
                    st.setInverseLabel("tracked by");
                    st.setPluralLabel("Tracks");
                    st.setInversePluralLabel("ETPs");
                    st.setSearchDescription("Cryptocurrencies that %s tracks");
                    st.setInverseSearchDescription("ETPs (Exchange-Traded Products) that track %s");
                    st.setSearchHints(List.of("%s ETP holdings"));
                    st.setInverseSearchHints(List.of("%s cryptocurrency ETP exchange traded product", "%s ETP Europe"));
                }
                case INVESTED_IN -> {
                    st.setFromTypeId("vc"); st.setToTypeId("coin");
                    st.setInverseLabel("investor:");
                    st.setPluralLabel("Investments"); // from VC's perspective
                    st.setInversePluralLabel("VCs"); // from coin's perspective
                    st.setSearchDescription("Cryptocurrency projects that %s has invested in");
                    st.setInverseSearchDescription("Venture capital firms and investors that have funded %s");
                    st.setSearchHints(List.of("%s crypto portfolio investments", "%s blockchain investments funding rounds"));
                    st.setInverseSearchHints(List.of("%s investors venture capital funding", "%s Series A B funding round crypto"));
                }
                case FOUNDED_BY -> {
                    st.setFromTypeId("coin"); st.setToTypeId("foundation");
                    st.setInverseLabel("founded");
                    st.setPluralLabel("Founders"); // from coin's perspective
                    st.setInversePluralLabel("Projects"); // from foundation's perspective
                    st.setSearchDescription("Founders and founding organizations of %s");
                    st.setInverseSearchDescription("Projects founded or supported by %s");
                    st.setSearchHints(List.of("%s founders co-founders team", "%s who founded created blockchain"));
                    st.setInverseSearchHints(List.of("%s founded projects ecosystem"));
                }
                case PARTNER -> {
                    st.setFromTypeId("coin"); st.setToTypeId("coin");
                    st.setInverseLabel("partner");
                    st.setPluralLabel("Partners");
                    st.setInversePluralLabel("Partners");
                    st.setSearchDescription("Strategic partners of %s");
                    st.setInverseSearchDescription("Strategic partners of %s");
                    st.setSearchHints(List.of("%s strategic partnerships crypto", "%s blockchain partners integrations"));
                    st.setInverseSearchHints(List.of("%s strategic partnerships crypto", "%s blockchain partners integrations"));
                }
                case FORK_OF -> {
                    st.setFromTypeId("coin"); st.setToTypeId("coin");
                    st.setInverseLabel("forked to");
                    st.setPluralLabel("Forks");
                    st.setInversePluralLabel("Forks");
                    st.setSearchDescription("Projects that forked from %s or that %s forked from");
                    st.setInverseSearchDescription("Projects that forked from %s or that %s forked from");
                    st.setSearchHints(List.of("%s fork forked from blockchain", "%s hard fork code fork crypto"));
                    st.setInverseSearchHints(List.of("%s fork forked from blockchain"));
                }
                case BRIDGE -> {
                    st.setFromTypeId("coin"); st.setToTypeId("coin");
                    st.setInverseLabel("bridge");
                    st.setPluralLabel("Bridges");
                    st.setInversePluralLabel("Bridges");
                    st.setSearchDescription("Blockchain bridges connected to %s");
                    st.setInverseSearchDescription("Blockchain bridges connected to %s");
                    st.setSearchHints(List.of("%s cross-chain bridge interoperability", "%s blockchain bridge protocols"));
                    st.setInverseSearchHints(List.of("%s cross-chain bridge interoperability"));
                }
                case ECOSYSTEM -> {
                    st.setFromTypeId("coin"); st.setToTypeId("coin");
                    st.setInverseLabel("ecosystem:");
                    st.setPluralLabel("Ecosystem");
                    st.setInversePluralLabel("Ecosystem");
                    st.setSearchDescription("Projects and tokens in the %s ecosystem");
                    st.setInverseSearchDescription("Projects and tokens in the %s ecosystem");
                    st.setSearchHints(List.of("%s ecosystem projects tokens DeFi", "%s blockchain ecosystem dApps protocols"));
                    st.setInverseSearchHints(List.of("%s ecosystem projects tokens DeFi"));
                }
                case COMPETITOR -> {
                    st.setFromTypeId("coin"); st.setToTypeId("coin");
                    st.setInverseLabel("competes");
                    st.setPluralLabel("Competitors");
                    st.setInversePluralLabel("Competitors");
                    st.setSearchDescription("Direct competitors of %s");
                    st.setInverseSearchDescription("Direct competitors of %s");
                    st.setSearchHints(List.of("%s competitors alternatives crypto", "%s vs comparison blockchain"));
                    st.setInverseSearchHints(List.of("%s competitors alternatives crypto"));
                }
                case HAS_RISK -> {
                    st.setFromTypeId("coin"); st.setToTypeId("risk");
                    st.setInverseLabel("risk for");
                    st.setPluralLabel("Risks"); // from coin's perspective
                    st.setInversePluralLabel("Affected"); // from risk's perspective
                    st.setSearchDescription("Risks and concerns associated with %s");
                    st.setInverseSearchDescription("Cryptocurrencies affected by this risk");
                    st.setSearchHints(List.of("%s risks concerns vulnerabilities crypto", "%s regulatory risk security issues"));
                    st.setInverseSearchHints(List.of("%s risk affected cryptocurrencies"));
                }
                case HAS_STRENGTH -> {
                    st.setFromTypeId("coin"); st.setToTypeId("strength");
                    st.setInverseLabel("strength for");
                    st.setPluralLabel("Strengths"); // from coin's perspective
                    st.setInversePluralLabel("Benefits"); // from strength's perspective
                    st.setSearchDescription("Strengths and advantages of %s");
                    st.setInverseSearchDescription("Cryptocurrencies with this strength");
                    st.setSearchHints(List.of("%s strengths advantages bullish factors", "%s competitive advantage strong fundamentals"));
                    st.setInverseSearchHints(List.of("%s strength benefiting cryptocurrencies"));
                }
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
        inCat.setInverseLabel("contains");
        inCat.setPluralLabel("Categories");
        inCat.setInversePluralLabel("Coins");
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
            hp.setInverseLabel("traded on");
            hp.setPluralLabel("Pairs");
            hp.setInversePluralLabel("Exchanges");
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
            na.addAttribute(new SchemaAttribute("title", SchemaAttribute.TEXT, true, 0,
                null, null, SchemaAttribute.Mutability.SOURCE));
            na.addAttribute(new SchemaAttribute("url", SchemaAttribute.URL, false, 1,
                null, null, SchemaAttribute.Mutability.SOURCE));
            na.addAttribute(new SchemaAttribute("published_at", SchemaAttribute.DATETIME, false, 2,
                Map.of("en", "Published At"),
                Map.of("format", "yyyy-MM-dd HH:mm"),
                SchemaAttribute.Mutability.SOURCE));
            na.addAttribute(new SchemaAttribute("source", SchemaAttribute.TEXT, false, 3,
                null, null, SchemaAttribute.Mutability.SOURCE));
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

        // Migration: update mutability for source attributes on existing databases
        migrateMutability();

        // Migration: populate new metadata fields on relationship types if missing
        migrateRelationshipMetadata();
    }

    /** Set correct mutability for known source attributes (only updates if still MANUAL). */
    private void migrateMutability() {
        // Entity types with source fields
        for (String typeId : List.of("coin", "l2", "etf", "etp", "dat")) {
            store.updateMutabilityIfDefault(typeId, "name", SchemaAttribute.Mutability.SOURCE);
            store.updateMutabilityIfDefault(typeId, "symbol", SchemaAttribute.Mutability.SOURCE);
            store.updateMutabilityIfDefault(typeId, "market_cap", SchemaAttribute.Mutability.SOURCE);
        }
        // VC, exchange, foundation etc only have name/symbol
        for (String typeId : List.of("vc", "exchange", "foundation", "company", "news_source")) {
            store.updateMutabilityIfDefault(typeId, "name", SchemaAttribute.Mutability.SOURCE);
            store.updateMutabilityIfDefault(typeId, "symbol", SchemaAttribute.Mutability.SOURCE);
        }
        // News article source fields
        store.updateMutabilityIfDefault("news_article", "title", SchemaAttribute.Mutability.SOURCE);
        store.updateMutabilityIfDefault("news_article", "url", SchemaAttribute.Mutability.SOURCE);
        store.updateMutabilityIfDefault("news_article", "published_at", SchemaAttribute.Mutability.SOURCE);
        store.updateMutabilityIfDefault("news_article", "source", SchemaAttribute.Mutability.SOURCE);
    }

    /** Populate metadata fields on relationship types that were seeded before metadata was added. */
    private void migrateRelationshipMetadata() {
        for (SchemaType t : types.values()) {
            if (!t.isRelationship()) continue;
            // Check if metadata has been populated (use inverseLabel as sentinel)
            if (t.inverseLabel() != null) continue;

            // Re-seed metadata from a temporary fresh seed and copy fields
            SchemaType fresh = createFreshRelationshipMetadata(t.id());
            if (fresh == null) continue;

            t.setInverseLabel(fresh.inverseLabel());
            t.setPluralLabel(fresh.pluralLabel());
            t.setInversePluralLabel(fresh.inversePluralLabel());
            t.setSearchDescription(fresh.searchDescription());
            t.setInverseSearchDescription(fresh.inverseSearchDescription());
            t.setSearchHints(fresh.searchHints());
            t.setInverseSearchHints(fresh.inverseSearchHints());

            // Persist
            store.saveSchemaType(t);
        }

        // Also migrate entity hasMarketCap
        for (SchemaType t : types.values()) {
            if (!t.isEntity()) continue;
            String id = t.id();
            if ("coin".equals(id) || "l2".equals(id) || "etf".equals(id) || "etp".equals(id) || "dat".equals(id)) {
                if (!t.hasMarketCap()) {
                    t.setHasMarketCap(true);
                    store.saveSchemaType(t);
                }
            }
        }
    }

    /** Create metadata for a known relationship type ID. Returns null if unknown. */
    private SchemaType createFreshRelationshipMetadata(String id) {
        SchemaType st = new SchemaType();
        switch (id) {
            case "l2_of" -> {
                st.setInverseLabel("L1 for");
                st.setPluralLabel("L1"); st.setInversePluralLabel("L2s");
                st.setSearchDescription("The L1 blockchain that %s is built on");
                st.setInverseSearchDescription("Layer 2 networks built on %s");
                st.setSearchHints(List.of("%s Layer 1 blockchain built on"));
                st.setInverseSearchHints(List.of("%s Layer 2 networks rollups", "%s L2 scaling solutions"));
            }
            case "etf_tracks" -> {
                st.setInverseLabel("tracked by");
                st.setPluralLabel("Tracks"); st.setInversePluralLabel("ETFs");
                st.setSearchDescription("Cryptocurrencies that %s tracks");
                st.setInverseSearchDescription("ETFs (Exchange-Traded Funds) that track %s");
                st.setSearchHints(List.of("%s ETF holdings cryptocurrency"));
                st.setInverseSearchHints(List.of("%s cryptocurrency ETF list spot", "%s ETF approved SEC"));
            }
            case "etp_tracks" -> {
                st.setInverseLabel("tracked by");
                st.setPluralLabel("Tracks"); st.setInversePluralLabel("ETPs");
                st.setSearchDescription("Cryptocurrencies that %s tracks");
                st.setInverseSearchDescription("ETPs (Exchange-Traded Products) that track %s");
                st.setSearchHints(List.of("%s ETP holdings"));
                st.setInverseSearchHints(List.of("%s cryptocurrency ETP exchange traded product", "%s ETP Europe"));
            }
            case "invested_in" -> {
                st.setInverseLabel("investor:");
                st.setPluralLabel("Investments"); st.setInversePluralLabel("VCs");
                st.setSearchDescription("Cryptocurrency projects that %s has invested in");
                st.setInverseSearchDescription("Venture capital firms and investors that have funded %s");
                st.setSearchHints(List.of("%s crypto portfolio investments", "%s blockchain investments funding rounds"));
                st.setInverseSearchHints(List.of("%s investors venture capital funding", "%s Series A B funding round crypto"));
            }
            case "founded_by" -> {
                st.setInverseLabel("founded");
                st.setPluralLabel("Founders"); st.setInversePluralLabel("Projects");
                st.setSearchDescription("Founders and founding organizations of %s");
                st.setInverseSearchDescription("Projects founded or supported by %s");
                st.setSearchHints(List.of("%s founders co-founders team", "%s who founded created blockchain"));
                st.setInverseSearchHints(List.of("%s founded projects ecosystem"));
            }
            case "partner" -> {
                st.setInverseLabel("partner");
                st.setPluralLabel("Partners"); st.setInversePluralLabel("Partners");
                st.setSearchDescription("Strategic partners of %s");
                st.setInverseSearchDescription("Strategic partners of %s");
                st.setSearchHints(List.of("%s strategic partnerships crypto", "%s blockchain partners integrations"));
                st.setInverseSearchHints(List.of("%s strategic partnerships crypto"));
            }
            case "fork_of" -> {
                st.setInverseLabel("forked to");
                st.setPluralLabel("Forks"); st.setInversePluralLabel("Forks");
                st.setSearchDescription("Projects that forked from %s or that %s forked from");
                st.setInverseSearchDescription("Projects that forked from %s or that %s forked from");
                st.setSearchHints(List.of("%s fork forked from blockchain", "%s hard fork code fork crypto"));
                st.setInverseSearchHints(List.of("%s fork forked from blockchain"));
            }
            case "bridge" -> {
                st.setInverseLabel("bridge");
                st.setPluralLabel("Bridges"); st.setInversePluralLabel("Bridges");
                st.setSearchDescription("Blockchain bridges connected to %s");
                st.setInverseSearchDescription("Blockchain bridges connected to %s");
                st.setSearchHints(List.of("%s cross-chain bridge interoperability", "%s blockchain bridge protocols"));
                st.setInverseSearchHints(List.of("%s cross-chain bridge interoperability"));
            }
            case "ecosystem" -> {
                st.setInverseLabel("ecosystem:");
                st.setPluralLabel("Ecosystem"); st.setInversePluralLabel("Ecosystem");
                st.setSearchDescription("Projects and tokens in the %s ecosystem");
                st.setInverseSearchDescription("Projects and tokens in the %s ecosystem");
                st.setSearchHints(List.of("%s ecosystem projects tokens DeFi", "%s blockchain ecosystem dApps protocols"));
                st.setInverseSearchHints(List.of("%s ecosystem projects tokens DeFi"));
            }
            case "competitor" -> {
                st.setInverseLabel("competes");
                st.setPluralLabel("Competitors"); st.setInversePluralLabel("Competitors");
                st.setSearchDescription("Direct competitors of %s");
                st.setInverseSearchDescription("Direct competitors of %s");
                st.setSearchHints(List.of("%s competitors alternatives crypto", "%s vs comparison blockchain"));
                st.setInverseSearchHints(List.of("%s competitors alternatives crypto"));
            }
            case "has_risk" -> {
                st.setInverseLabel("risk for");
                st.setPluralLabel("Risks"); st.setInversePluralLabel("Affected");
                st.setSearchDescription("Risks and concerns associated with %s");
                st.setInverseSearchDescription("Cryptocurrencies affected by this risk");
                st.setSearchHints(List.of("%s risks concerns vulnerabilities crypto", "%s regulatory risk security issues"));
                st.setInverseSearchHints(List.of("%s risk affected cryptocurrencies"));
            }
            case "has_strength" -> {
                st.setInverseLabel("strength for");
                st.setPluralLabel("Strengths"); st.setInversePluralLabel("Benefits");
                st.setSearchDescription("Strengths and advantages of %s");
                st.setInverseSearchDescription("Cryptocurrencies with this strength");
                st.setSearchHints(List.of("%s strengths advantages bullish factors", "%s competitive advantage strong fundamentals"));
                st.setInverseSearchHints(List.of("%s strength benefiting cryptocurrencies"));
            }
            case "in_category" -> {
                st.setInverseLabel("contains");
                st.setPluralLabel("Categories"); st.setInversePluralLabel("Coins");
            }
            case "hosts_pair" -> {
                st.setInverseLabel("traded on");
                st.setPluralLabel("Pairs"); st.setInversePluralLabel("Exchanges");
            }
            case "mentions" -> {
                st.setInverseLabel("mentioned in");
                st.setPluralLabel("Coins"); st.setInversePluralLabel("Articles");
            }
            default -> { return null; }
        }
        return st;
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
