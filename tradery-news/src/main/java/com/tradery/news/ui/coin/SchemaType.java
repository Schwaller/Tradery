package com.tradery.news.ui.coin;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A dynamic type definition for entities or relationships, stored in the DB.
 * Replaces hardcoded enum usage for schema-level type info.
 */
public class SchemaType {

    public static final String KIND_ENTITY = "entity";
    public static final String KIND_RELATIONSHIP = "relationship";

    private String id;
    private String name;
    private Color color;
    private String kind;        // "entity" or "relationship"
    private String fromTypeId;  // relationship only
    private String toTypeId;    // relationship only
    private String label;       // relationship only (short verb)
    private String inverseLabel; // relationship only (reverse description verb, e.g. "L1 for")
    private String pluralLabel;  // UI list label from the 'from' side (e.g. "L2s", "ETFs")
    private String inversePluralLabel; // UI list label from the 'to' side
    private String searchDescription;  // AI prompt template (forward), use %s for entity name
    private String inverseSearchDescription; // AI prompt template (reverse)
    private List<String> searchHints = new ArrayList<>();  // web search query fragments (forward)
    private List<String> inverseSearchHints = new ArrayList<>(); // web search query fragments (reverse)
    private boolean hasMarketCap; // entity type gets market_cap attribute
    private int displayOrder;
    private final List<SchemaAttribute> attributes = new ArrayList<>();

    // Position on ERD canvas (persisted to DB)
    private double erdX;
    private double erdY;
    // Transient velocity and animation state
    private double erdVx;
    private double erdVy;
    private boolean erdPinned;
    private double erdTargetX;
    private double erdTargetY;
    private boolean erdAnimating;

    public SchemaType() {}

    public SchemaType(String id, String name, Color color, String kind) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.kind = kind;
    }

    public String id() { return id; }
    public void setId(String id) { this.id = id; }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public Color color() { return color; }
    public void setColor(Color color) { this.color = color; }

    public String kind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String fromTypeId() { return fromTypeId; }
    public void setFromTypeId(String fromTypeId) { this.fromTypeId = fromTypeId; }

    public String toTypeId() { return toTypeId; }
    public void setToTypeId(String toTypeId) { this.toTypeId = toTypeId; }

    public String label() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String inverseLabel() { return inverseLabel; }
    public void setInverseLabel(String inverseLabel) { this.inverseLabel = inverseLabel; }

    public String pluralLabel() { return pluralLabel; }
    public void setPluralLabel(String pluralLabel) { this.pluralLabel = pluralLabel; }

    public String inversePluralLabel() { return inversePluralLabel; }
    public void setInversePluralLabel(String inversePluralLabel) { this.inversePluralLabel = inversePluralLabel; }

    public String searchDescription() { return searchDescription; }
    public void setSearchDescription(String searchDescription) { this.searchDescription = searchDescription; }

    public String inverseSearchDescription() { return inverseSearchDescription; }
    public void setInverseSearchDescription(String inverseSearchDescription) { this.inverseSearchDescription = inverseSearchDescription; }

    public List<String> searchHints() { return searchHints; }
    public void setSearchHints(List<String> searchHints) { this.searchHints = searchHints != null ? searchHints : new ArrayList<>(); }

    public List<String> inverseSearchHints() { return inverseSearchHints; }
    public void setInverseSearchHints(List<String> inverseSearchHints) { this.inverseSearchHints = inverseSearchHints != null ? inverseSearchHints : new ArrayList<>(); }

    public boolean hasMarketCap() { return hasMarketCap; }
    public void setHasMarketCap(boolean hasMarketCap) { this.hasMarketCap = hasMarketCap; }

    public int displayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public List<SchemaAttribute> attributes() { return attributes; }

    public void addAttribute(SchemaAttribute attr) {
        attributes.add(attr);
    }

    public void removeAttribute(String attrName) {
        attributes.removeIf(a -> a.name().equals(attrName));
    }

    public double erdX() { return erdX; }
    public void setErdX(double erdX) { this.erdX = erdX; }

    public double erdY() { return erdY; }
    public void setErdY(double erdY) { this.erdY = erdY; }

    public double erdVx() { return erdVx; }
    public void setErdVx(double erdVx) { this.erdVx = erdVx; }

    public double erdVy() { return erdVy; }
    public void setErdVy(double erdVy) { this.erdVy = erdVy; }

    public boolean isErdPinned() { return erdPinned; }
    public void setErdPinned(boolean erdPinned) { this.erdPinned = erdPinned; }

    public double erdTargetX() { return erdTargetX; }
    public void setErdTargetX(double erdTargetX) { this.erdTargetX = erdTargetX; }

    public double erdTargetY() { return erdTargetY; }
    public void setErdTargetY(double erdTargetY) { this.erdTargetY = erdTargetY; }

    public boolean isErdAnimating() { return erdAnimating; }
    public void setErdAnimating(boolean erdAnimating) { this.erdAnimating = erdAnimating; }

    public boolean isEntity() { return KIND_ENTITY.equals(kind); }
    public boolean isRelationship() { return KIND_RELATIONSHIP.equals(kind); }

    /** Color as hex string for DB storage. */
    public String colorHex() {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** Parse hex color string. */
    public static Color parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.GRAY;
        return Color.decode(hex);
    }

    // ==================== DERIVED METHODS (for relationship types) ====================

    /** Get the plural label appropriate for the source entity type. */
    public String pluralLabelFor(String sourceEntityTypeId) {
        if (sourceEntityTypeId != null && sourceEntityTypeId.equals(fromTypeId)) {
            return pluralLabel != null ? pluralLabel : name;
        }
        return inversePluralLabel != null ? inversePluralLabel : (pluralLabel != null ? pluralLabel : name);
    }

    /** Get AI search description with entity name substituted. */
    public String searchDescriptionFor(String entityName, String sourceEntityTypeId) {
        String template = (sourceEntityTypeId != null && sourceEntityTypeId.equals(fromTypeId))
            ? searchDescription : inverseSearchDescription;
        if (template == null) template = searchDescription;
        return template != null ? String.format(template, entityName) : name + " of " + entityName;
    }

    /** Get web search queries with entity name substituted. */
    public List<String> searchQueriesFor(String entityName, String sourceEntityTypeId) {
        List<String> hints = (sourceEntityTypeId != null && sourceEntityTypeId.equals(fromTypeId))
            ? searchHints : inverseSearchHints;
        if (hints == null || hints.isEmpty()) hints = searchHints;
        if (hints == null || hints.isEmpty()) return List.of(entityName + " " + name);
        return hints.stream().map(h -> String.format(h, entityName)).collect(Collectors.toList());
    }

    /**
     * Create a relationship with correct direction based on source entity type.
     * If sourceTypeId matches fromTypeId, source is the 'from' entity.
     * Otherwise, source is the 'to' entity (inverse direction).
     */
    public CoinRelationship createDirected(String sourceId, String sourceTypeId,
                                            String targetId, CoinRelationship.Type relType, String note) {
        if (sourceTypeId != null && sourceTypeId.equals(fromTypeId)) {
            return new CoinRelationship(sourceId, targetId, relType, note);
        }
        return new CoinRelationship(targetId, sourceId, relType, note);
    }

    /** Describe the inverse relationship (e.g. "L1 for Arbitrum"). */
    public String inverseDescription(String otherName) {
        if (inverseLabel != null) return inverseLabel + " " + otherName;
        return label != null ? label + " " + otherName : name + " " + otherName;
    }
}
