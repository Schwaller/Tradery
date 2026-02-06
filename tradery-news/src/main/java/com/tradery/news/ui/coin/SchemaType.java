package com.tradery.news.ui.coin;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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
}
