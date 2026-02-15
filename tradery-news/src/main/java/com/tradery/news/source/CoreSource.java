package com.tradery.news.source;

import com.tradery.news.ui.coin.SchemaAttribute;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;

import java.awt.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Core data source that owns schema types not tied to any external API.
 * Produces entity types (vc, foundation, company, person, risk, strength, crypto_category)
 * and relationship types (invested_in, founded_by, partner, bridge, competitor, has_risk, has_strength, in_category).
 */
public class CoreSource implements DataSource {

    @Override
    public String id() { return "core"; }

    @Override
    public String name() { return "Core Schema"; }

    @Override
    public List<String> producedEntityTypes() {
        return List.of("vc", "foundation", "company", "person", "risk", "strength", "crypto_category");
    }

    @Override
    public List<String> producedRelationshipTypes() {
        return List.of("invested_in", "founded_by", "partner", "bridge", "competitor", "has_risk", "has_strength", "in_category");
    }

    @Override
    public Duration cacheTTL() { return Duration.ZERO; }

    @Override
    public FetchResult fetch(FetchContext ctx) {
        return new FetchResult(0, 0, "Core source has no data to fetch");
    }

    @Override
    public void seedSchemaTypes(SchemaRegistry registry) {
        int order = 100; // Start after CoinGecko types

        // ==================== ENTITY TYPES ====================

        if (registry.getType("vc") == null) {
            SchemaType vc = new SchemaType("vc", "Vc", new Color(255, 180, 80), SchemaType.KIND_ENTITY);
            vc.setDisplayOrder(order++);
            vc.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0, null, null, SchemaAttribute.Mutability.SOURCE));
            vc.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1, null, null, SchemaAttribute.Mutability.SOURCE));
            registry.save(vc);
        }

        if (registry.getType("foundation") == null) {
            SchemaType foundation = new SchemaType("foundation", "Foundation", new Color(180, 150, 255), SchemaType.KIND_ENTITY);
            foundation.setDisplayOrder(order++);
            foundation.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0, null, null, SchemaAttribute.Mutability.SOURCE));
            foundation.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1, null, null, SchemaAttribute.Mutability.SOURCE));
            registry.save(foundation);
        }

        if (registry.getType("company") == null) {
            SchemaType company = new SchemaType("company", "Company", new Color(200, 160, 120), SchemaType.KIND_ENTITY);
            company.setDisplayOrder(order++);
            company.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0, null, null, SchemaAttribute.Mutability.SOURCE));
            company.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1, null, null, SchemaAttribute.Mutability.SOURCE));
            registry.save(company);
        }

        if (registry.getType("person") == null) {
            SchemaType person = new SchemaType("person", "Person", new Color(220, 170, 130), SchemaType.KIND_ENTITY);
            person.setDisplayOrder(order++);
            person.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0, null, null, SchemaAttribute.Mutability.SOURCE));
            person.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1, null, null, SchemaAttribute.Mutability.SOURCE));
            registry.save(person);
        }

        if (registry.getType("risk") == null) {
            SchemaType risk = new SchemaType("risk", "Risk", new Color(220, 100, 100), SchemaType.KIND_ENTITY);
            risk.setDisplayOrder(order++);
            risk.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0, null, null, SchemaAttribute.Mutability.SOURCE));
            risk.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1, null, null, SchemaAttribute.Mutability.SOURCE));
            registry.save(risk);
        }

        if (registry.getType("strength") == null) {
            SchemaType strength = new SchemaType("strength", "Strength", new Color(100, 200, 160), SchemaType.KIND_ENTITY);
            strength.setDisplayOrder(order++);
            strength.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0, null, null, SchemaAttribute.Mutability.SOURCE));
            strength.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1, null, null, SchemaAttribute.Mutability.SOURCE));
            registry.save(strength);
        }

        if (registry.getType("crypto_category") == null) {
            SchemaType cat = new SchemaType("crypto_category", "Crypto Category", new Color(180, 200, 140), SchemaType.KIND_ENTITY);
            cat.setDisplayOrder(order++);
            cat.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0));
            registry.save(cat);
        }

        // ==================== RELATIONSHIP TYPES ====================

        order = 100;

        if (registry.getType("invested_in") == null) {
            SchemaType st = new SchemaType("invested_in", "Invested In", new Color(255, 180, 80), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("invested"); st.setFromTypeId("vc"); st.setToTypeId("coin");
            st.setInverseLabel("investor:"); st.setPluralLabel("Investments"); st.setInversePluralLabel("VCs");
            st.setSearchDescription("Cryptocurrency projects that %s has invested in");
            st.setInverseSearchDescription("Venture capital firms and investors that have funded %s");
            st.setSearchHints(List.of("%s crypto portfolio investments", "%s blockchain investments funding rounds"));
            st.setInverseSearchHints(List.of("%s investors venture capital funding", "%s Series A B funding round crypto"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("founded_by") == null) {
            SchemaType st = new SchemaType("founded_by", "Founded By", new Color(180, 150, 255), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("founded"); st.setFromTypeId("coin"); st.setToTypeId("foundation");
            st.setInverseLabel("founded"); st.setPluralLabel("Founders"); st.setInversePluralLabel("Projects");
            st.setSearchDescription("Founders and founding organizations of %s");
            st.setInverseSearchDescription("Projects founded or supported by %s");
            st.setSearchHints(List.of("%s founders co-founders team", "%s who founded created blockchain"));
            st.setInverseSearchHints(List.of("%s founded projects ecosystem"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("partner") == null) {
            SchemaType st = new SchemaType("partner", "Partner", new Color(150, 150, 200), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("partner"); st.setFromTypeId("coin"); st.setToTypeId("coin");
            st.setInverseLabel("partner"); st.setPluralLabel("Partners"); st.setInversePluralLabel("Partners");
            st.setSearchDescription("Strategic partners of %s");
            st.setInverseSearchDescription("Strategic partners of %s");
            st.setSearchHints(List.of("%s strategic partnerships crypto", "%s blockchain partners integrations"));
            st.setInverseSearchHints(List.of("%s strategic partnerships crypto"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("bridge") == null) {
            SchemaType st = new SchemaType("bridge", "Bridge", new Color(150, 200, 200), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("bridge"); st.setFromTypeId("coin"); st.setToTypeId("coin");
            st.setInverseLabel("bridge"); st.setPluralLabel("Bridges"); st.setInversePluralLabel("Bridges");
            st.setSearchDescription("Blockchain bridges connected to %s");
            st.setInverseSearchDescription("Blockchain bridges connected to %s");
            st.setSearchHints(List.of("%s cross-chain bridge interoperability", "%s blockchain bridge protocols"));
            st.setInverseSearchHints(List.of("%s cross-chain bridge interoperability"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("competitor") == null) {
            SchemaType st = new SchemaType("competitor", "Competitor", new Color(200, 100, 100), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("competes"); st.setFromTypeId("coin"); st.setToTypeId("coin");
            st.setInverseLabel("competes"); st.setPluralLabel("Competitors"); st.setInversePluralLabel("Competitors");
            st.setSearchDescription("Direct competitors of %s");
            st.setInverseSearchDescription("Direct competitors of %s");
            st.setSearchHints(List.of("%s competitors alternatives crypto", "%s vs comparison blockchain"));
            st.setInverseSearchHints(List.of("%s competitors alternatives crypto"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("has_risk") == null) {
            SchemaType st = new SchemaType("has_risk", "Has Risk", new Color(220, 100, 100), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("has risk"); st.setFromTypeId("coin"); st.setToTypeId("risk");
            st.setInverseLabel("risk for"); st.setPluralLabel("Risks"); st.setInversePluralLabel("Affected");
            st.setSearchDescription("Risks and concerns associated with %s");
            st.setInverseSearchDescription("Cryptocurrencies affected by this risk");
            st.setSearchHints(List.of("%s risks concerns vulnerabilities crypto", "%s regulatory risk security issues"));
            st.setInverseSearchHints(List.of("%s risk affected cryptocurrencies"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("has_strength") == null) {
            SchemaType st = new SchemaType("has_strength", "Has Strength", new Color(100, 200, 160), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("has strength"); st.setFromTypeId("coin"); st.setToTypeId("strength");
            st.setInverseLabel("strength for"); st.setPluralLabel("Strengths"); st.setInversePluralLabel("Benefits");
            st.setSearchDescription("Strengths and advantages of %s");
            st.setInverseSearchDescription("Cryptocurrencies with this strength");
            st.setSearchHints(List.of("%s strengths advantages bullish factors", "%s competitive advantage strong fundamentals"));
            st.setInverseSearchHints(List.of("%s strength benefiting cryptocurrencies"));
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }

        if (registry.getType("in_category") == null) {
            SchemaType st = new SchemaType("in_category", "In Category", new Color(160, 190, 130), SchemaType.KIND_RELATIONSHIP);
            st.setLabel("in"); st.setFromTypeId("coin"); st.setToTypeId("crypto_category");
            st.setInverseLabel("contains"); st.setPluralLabel("Categories"); st.setInversePluralLabel("Coins");
            st.setDisplayOrder(order++);
            st.addAttribute(new SchemaAttribute("note", SchemaAttribute.TEXT, false, 0));
            registry.save(st);
        }
    }
}
