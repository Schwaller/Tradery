package com.tradery.news.ui.coin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interactive ERD canvas for editing schema types.
 * Entity types as rectangles, relationship types as diamonds, connected with arrows.
 */
public class ErdPanel extends JPanel {

    private static final Color BG_COLOR = new Color(25, 27, 31);
    private static final Color GRID_COLOR = new Color(40, 42, 46);
    private static final Color SELECTION_COLOR = new Color(100, 160, 255);
    private static final Color ARROW_COLOR = new Color(120, 120, 130);
    private static final Color PORT_COLOR = new Color(100, 160, 255, 180);
    private static final Color MINIMAP_BG = new Color(30, 32, 36, 200);
    private static final Color MINIMAP_VIEWPORT = new Color(100, 160, 255, 60);

    private static final int ENTITY_WIDTH = 180;
    private static final int ENTITY_HEADER_H = 28;
    private static final int ATTR_ROW_H = 20;
    private static final int DIAMOND_W = 150;
    private static final int DIAMOND_H = 80;
    private static final int PORT_RADIUS = 5;
    private static final int GRID_SPACING = 20;

    private SchemaRegistry registry;
    private Consumer<Void> onDataChanged;

    // View transform
    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;

    // Interaction state
    private SchemaType selectedType;
    private SchemaType hoveredType;
    private SchemaType draggedType;
    private double dragOffsetX, dragOffsetY;
    private boolean panning;
    private int lastMouseX, lastMouseY;

    // Port dragging for connections
    private boolean draggingPort;
    private SchemaType portDragSource;
    private int portDragEndX, portDragEndY;

    // Hover port info
    private SchemaType hoveredPortType;

    // Layout mode
    private enum LayoutMode { MANUAL, SPRING, TREE_ANIMATING }
    private LayoutMode layoutMode = LayoutMode.MANUAL;

    // Cached dot grid
    private java.awt.image.BufferedImage gridCache;
    private int gridCacheW, gridCacheH;

    // Physics/animation timer
    private javax.swing.Timer physicsTimer;
    private static final double ANIM_LERP = 0.12;
    private static final double ANIM_SNAP = 1.0;

    public ErdPanel() {
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(1200, 600));
        setupMouseHandlers();

        physicsTimer = new javax.swing.Timer(32, e -> {
            if (registry == null) { physicsTimer.stop(); return; }

            if (layoutMode == LayoutMode.SPRING) {
                double cx = getWidth() / 2.0;
                double cy = getHeight() / 2.0;
                boolean moving = ErdLayoutEngine.step(registry.allTypes(), cx, cy, draggedType);
                repaint();
                if (!moving) {
                    physicsTimer.stop();
                    savePositions();
                }
            } else if (layoutMode == LayoutMode.TREE_ANIMATING) {
                boolean anyMoving = false;
                for (SchemaType t : registry.allTypes()) {
                    if (!t.isErdAnimating()) continue;
                    double dx = t.erdTargetX() - t.erdX();
                    double dy = t.erdTargetY() - t.erdY();
                    if (Math.abs(dx) < ANIM_SNAP && Math.abs(dy) < ANIM_SNAP) {
                        t.setErdX(t.erdTargetX());
                        t.setErdY(t.erdTargetY());
                        t.setErdAnimating(false);
                    } else {
                        t.setErdX(t.erdX() + dx * ANIM_LERP);
                        t.setErdY(t.erdY() + dy * ANIM_LERP);
                        anyMoving = true;
                    }
                }
                repaint();
                if (!anyMoving) {
                    layoutMode = LayoutMode.MANUAL;
                    physicsTimer.stop();
                    savePositions();
                }
            } else {
                physicsTimer.stop();
            }
        });
    }

    public void setRegistry(SchemaRegistry registry) {
        this.registry = registry;
        // Positions are loaded from DB - no auto layout
        repaint();
    }

    public void setOnDataChanged(Consumer<Void> onDataChanged) {
        this.onDataChanged = onDataChanged;
    }

    public SchemaRegistry getRegistry() {
        return registry;
    }

    /** Switch to manual layout mode: stop all physics/animation. */
    public void manualLayout() {
        if (registry == null) return;
        layoutMode = LayoutMode.MANUAL;
        physicsTimer.stop();
        for (SchemaType t : registry.allTypes()) {
            t.setErdPinned(false);
            t.setErdVx(0);
            t.setErdVy(0);
            t.setErdAnimating(false);
        }
        repaint();
    }

    // ==================== RENDERING ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (registry == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw dot grid (in component coordinates, before our custom transform)
        drawDotGrid(g2);

        // Apply our pan/zoom transform on top of Swing's existing transform
        g2.translate(getWidth() / 2.0, getHeight() / 2.0);
        g2.scale(zoom, zoom);
        g2.translate(-getWidth() / 2.0 + panX, -getHeight() / 2.0 + panY);

        // Draw arrows first (behind boxes)
        for (SchemaType rel : registry.relationshipTypes()) {
            drawArrows(g2, rel);
        }

        // Draw entity type boxes
        for (SchemaType entityType : registry.entityTypes()) {
            drawEntityBox(g2, entityType);
        }

        // Draw relationship type diamonds
        for (SchemaType relType : registry.relationshipTypes()) {
            drawRelationshipDiamond(g2, relType);
        }

        // Draw port drag line
        if (draggingPort && portDragSource != null) {
            g2.setColor(PORT_COLOR);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{6, 4}, 0));
            Point2D src = getBoxCenter(portDragSource);
            Point2D end = screenToWorld(portDragEndX, portDragEndY);
            g2.draw(new Line2D.Double(src.getX(), src.getY(), end.getX(), end.getY()));
        }

        g2.dispose();

        // Hint text when not in manual mode
        if (layoutMode != LayoutMode.MANUAL) {
            Graphics2D sg = (Graphics2D) g;
            sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            sg.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String hint = "Click \"Manual\" to save current positions";
            FontMetrics fm = sg.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(hint)) / 2;
            int ty = getHeight() - 16;
            sg.setColor(new Color(130, 130, 140));
            sg.drawString(hint, tx, ty);
        }
    }

    private void drawDotGrid(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Rebuild cache only when size changes
        if (gridCache == null || gridCacheW != w || gridCacheH != h) {
            gridCache = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D gg = gridCache.createGraphics();
            gg.setColor(GRID_COLOR);
            for (int x = 0; x < w; x += GRID_SPACING) {
                for (int y = 0; y < h; y += GRID_SPACING) {
                    gg.fillRect(x, y, 1, 1);
                }
            }
            gg.dispose();
            gridCacheW = w;
            gridCacheH = h;
        }
        g2.drawImage(gridCache, 0, 0, null);
    }

    private void drawEntityBox(Graphics2D g2, SchemaType type) {
        double x = type.erdX();
        double y = type.erdY();
        int h = ENTITY_HEADER_H + type.attributes().size() * ATTR_ROW_H + 8;
        boolean isSelected = type == selectedType;
        boolean isHovered = type == hoveredType;

        // Shadow
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fill(new RoundRectangle2D.Double(x + 3, y + 3, ENTITY_WIDTH, h, 8, 8));

        // Body
        g2.setColor(new Color(38, 40, 44));
        g2.fill(new RoundRectangle2D.Double(x, y, ENTITY_WIDTH, h, 8, 8));

        // Header bar
        g2.setColor(type.color());
        g2.fill(new RoundRectangle2D.Double(x, y, ENTITY_WIDTH, ENTITY_HEADER_H, 8, 8));
        g2.fill(new Rectangle2D.Double(x, y + ENTITY_HEADER_H - 4, ENTITY_WIDTH, 4));

        // Header text
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        String headerText = type.name().toUpperCase();
        int textX = (int) (x + (ENTITY_WIDTH - fm.stringWidth(headerText)) / 2.0);
        g2.drawString(headerText, textX, (int) (y + ENTITY_HEADER_H - 8));

        // Attribute rows
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        fm = g2.getFontMetrics();
        for (int i = 0; i < type.attributes().size(); i++) {
            SchemaAttribute attr = type.attributes().get(i);
            int rowY = (int) (y + ENTITY_HEADER_H + 4 + i * ATTR_ROW_H);

            // Attribute name
            g2.setColor(new Color(180, 180, 190));
            String attrText = attr.name() + ": " + attr.dataType();
            if (attr.required()) attrText += " *";
            g2.drawString(attrText, (int) (x + 10), rowY + fm.getAscent());
        }

        // Border
        g2.setStroke(new BasicStroke(isSelected ? 2.5f : isHovered ? 1.5f : 1f));
        g2.setColor(isSelected ? SELECTION_COLOR : isHovered ? type.color().brighter() : type.color());
        g2.draw(new RoundRectangle2D.Double(x, y, ENTITY_WIDTH, h, 8, 8));

        // Port circles (visible on hover)
        if (isHovered || type == hoveredPortType) {
            drawPorts(g2, type);
        }
    }

    private void drawRelationshipDiamond(Graphics2D g2, SchemaType type) {
        double cx = type.erdX() + DIAMOND_W / 2.0;
        double cy = type.erdY() + DIAMOND_H / 2.0;
        boolean isSelected = type == selectedType;
        boolean isHovered = type == hoveredType;

        // Diamond shape
        Path2D diamond = new Path2D.Double();
        diamond.moveTo(cx, cy - DIAMOND_H / 2.0);
        diamond.lineTo(cx + DIAMOND_W / 2.0, cy);
        diamond.lineTo(cx, cy + DIAMOND_H / 2.0);
        diamond.lineTo(cx - DIAMOND_W / 2.0, cy);
        diamond.closePath();

        // Shadow
        AffineTransform shadowTx = AffineTransform.getTranslateInstance(3, 3);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fill(shadowTx.createTransformedShape(diamond));

        // Fill
        g2.setColor(new Color(35, 37, 41));
        g2.fill(diamond);

        // Border
        g2.setStroke(new BasicStroke(isSelected ? 2.5f : isHovered ? 1.5f : 1f));
        g2.setColor(isSelected ? SELECTION_COLOR : isHovered ? type.color().brighter() : type.color());
        g2.draw(diamond);

        // Name
        g2.setColor(type.color());
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        String name = type.name();
        g2.drawString(name, (int) (cx - fm.stringWidth(name) / 2.0), (int) (cy - 6));

        // Label
        if (type.label() != null) {
            g2.setColor(new Color(150, 150, 160));
            g2.setFont(new Font("SansSerif", Font.ITALIC, 10));
            fm = g2.getFontMetrics();
            String label = "\"" + type.label() + "\"";
            g2.drawString(label, (int) (cx - fm.stringWidth(label) / 2.0), (int) (cy + 10));
        }

        // Attributes below label
        if (!type.attributes().isEmpty()) {
            g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
            fm = g2.getFontMetrics();
            g2.setColor(new Color(140, 140, 150));
            int attrY = (int) (cy + 22);
            for (SchemaAttribute attr : type.attributes()) {
                String text = attr.name() + ": " + attr.dataType();
                g2.drawString(text, (int) (cx - fm.stringWidth(text) / 2.0), attrY);
                attrY += 12;
            }
        }
    }

    private void drawArrows(Graphics2D g2, SchemaType rel) {
        if (rel.fromTypeId() == null || rel.toTypeId() == null) return;

        SchemaType fromType = registry.getType(rel.fromTypeId());
        SchemaType toType = registry.getType(rel.toTypeId());
        if (fromType == null || toType == null) return;

        g2.setColor(new Color(rel.color().getRed(), rel.color().getGreen(), rel.color().getBlue(), 100));
        g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Diamond left/right midpoints (horizontal entry/exit)
        double relLeftX = rel.erdX();
        double relRightX = rel.erdX() + DIAMOND_W;
        double relMidY = rel.erdY() + DIAMOND_H / 2.0;

        // Entity centers as start/end points
        Point2D fromCenter = getBoxCenter(fromType);
        Point2D toCenter = getBoxCenter(toType);

        // Curve: from-entity center -> diamond left (arrives horizontally)
        double tangent = ENTITY_WIDTH;
        CubicCurve2D curve1 = new CubicCurve2D.Double(
            fromCenter.getX(), fromCenter.getY(),
            fromCenter.getX(), fromCenter.getY(),             // no constraint on entity side
            relLeftX - tangent, relMidY,                      // arrive at diamond horizontally
            relLeftX, relMidY
        );
        g2.draw(curve1);
        drawArrowHead(g2, relLeftX - tangent, relMidY, relLeftX, relMidY);
        // Curve: diamond right -> to-entity center (leaves horizontally)
        CubicCurve2D curve2 = new CubicCurve2D.Double(
            relRightX, relMidY,
            relRightX + tangent, relMidY,                     // leave diamond horizontally
            toCenter.getX(), toCenter.getY(),                 // no constraint on entity side
            toCenter.getX(), toCenter.getY()
        );
        g2.draw(curve2);
        drawArrowHead(g2, relRightX + tangent, relMidY, toCenter.getX(), toCenter.getY());
    }

    /** Get the closest edge point on an entity/diamond box toward a target point. */
    private Point2D getBoxEdgePoint(SchemaType type, double tx, double ty) {
        Point2D center = getBoxCenter(type);
        double cx = center.getX(), cy = center.getY();
        double hw, hh;

        if (type.isEntity()) {
            hw = ENTITY_WIDTH / 2.0;
            hh = (ENTITY_HEADER_H + type.attributes().size() * ATTR_ROW_H + 8) / 2.0;
        } else {
            hw = DIAMOND_W / 2.0;
            hh = DIAMOND_H / 2.0;
        }

        double dx = tx - cx;
        double dy = ty - cy;
        if (dx == 0 && dy == 0) dx = 1;

        // Intersect ray from center toward target with the box rectangle
        double scaleX = hw / Math.max(Math.abs(dx), 0.001);
        double scaleY = hh / Math.max(Math.abs(dy), 0.001);
        double scale = Math.min(scaleX, scaleY);

        return new Point2D.Double(cx + dx * scale, cy + dy * scale);
    }

    private void drawArrowHead(Graphics2D g2, double fromX, double fromY, double toX, double toY) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        double arrowLen = 16;
        double arrowAngle = Math.toRadians(25);

        double x1 = toX - arrowLen * Math.cos(angle - arrowAngle);
        double y1 = toY - arrowLen * Math.sin(angle - arrowAngle);
        double x2 = toX - arrowLen * Math.cos(angle + arrowAngle);
        double y2 = toY - arrowLen * Math.sin(angle + arrowAngle);

        Path2D head = new Path2D.Double();
        head.moveTo(toX, toY);
        head.lineTo(x1, y1);
        head.lineTo(x2, y2);
        head.closePath();
        Color c = g2.getColor();
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
        g2.fill(head);
        g2.setColor(c);
    }

    private void drawPorts(Graphics2D g2, SchemaType type) {
        g2.setColor(PORT_COLOR);
        Point2D[] ports = getPortPositions(type);
        for (Point2D p : ports) {
            g2.fill(new Ellipse2D.Double(p.getX() - PORT_RADIUS, p.getY() - PORT_RADIUS,
                PORT_RADIUS * 2, PORT_RADIUS * 2));
        }
    }

    private void drawMinimap(Graphics2D g2) {
        if (registry == null || registry.allTypes().isEmpty()) return;

        int mmW = 160;
        int mmH = 100;
        int mmX = getWidth() - mmW - 10;
        int mmY = getHeight() - mmH - 10;

        // Background
        g2.setColor(MINIMAP_BG);
        g2.fillRoundRect(mmX, mmY, mmW, mmH, 6, 6);
        g2.setColor(new Color(60, 62, 66));
        g2.drawRoundRect(mmX, mmY, mmW, mmH, 6, 6);

        // Compute bounds
        double[] bounds = ErdLayoutEngine.getBounds(registry.allTypes());
        double bw = bounds[2] - bounds[0];
        double bh = bounds[3] - bounds[1];
        if (bw < 1) bw = 1;
        if (bh < 1) bh = 1;

        double scale = Math.min((mmW - 10.0) / bw, (mmH - 10.0) / bh);

        // Draw type positions as dots
        for (SchemaType t : registry.allTypes()) {
            double mx = mmX + 5 + (t.erdX() - bounds[0]) * scale;
            double my = mmY + 5 + (t.erdY() - bounds[1]) * scale;
            g2.setColor(t.color());
            int size = t.isEntity() ? 4 : 3;
            g2.fillRect((int) mx, (int) my, size, size);
        }

        // Viewport rectangle
        double vpX = mmX + 5 + (-panX + getWidth() / 2.0 / zoom - getWidth() / 2.0 / zoom - bounds[0]) * scale;
        // Simplified: just show approximate viewport
        g2.setColor(MINIMAP_VIEWPORT);
        double vpW = (getWidth() / zoom) * scale;
        double vpH = (getHeight() / zoom) * scale;
        g2.fillRect((int) vpX, mmY + 5, (int) Math.min(vpW, mmW - 10), (int) Math.min(vpH, mmH - 10));
    }

    // ==================== GEOMETRY HELPERS ====================

    private Point2D getBoxCenter(SchemaType type) {
        if (type.isEntity()) {
            int h = ENTITY_HEADER_H + type.attributes().size() * ATTR_ROW_H + 8;
            return new Point2D.Double(type.erdX() + ENTITY_WIDTH / 2.0, type.erdY() + h / 2.0);
        } else {
            return new Point2D.Double(type.erdX() + DIAMOND_W / 2.0, type.erdY() + DIAMOND_H / 2.0);
        }
    }

    private Point2D[] getPortPositions(SchemaType type) {
        if (type.isEntity()) {
            int h = ENTITY_HEADER_H + type.attributes().size() * ATTR_ROW_H + 8;
            return new Point2D[]{
                new Point2D.Double(type.erdX() + ENTITY_WIDTH / 2.0, type.erdY()),          // top
                new Point2D.Double(type.erdX() + ENTITY_WIDTH / 2.0, type.erdY() + h),      // bottom
                new Point2D.Double(type.erdX(), type.erdY() + h / 2.0),                     // left
                new Point2D.Double(type.erdX() + ENTITY_WIDTH, type.erdY() + h / 2.0),      // right
            };
        }
        return new Point2D[0];
    }

    private boolean hitTestBox(SchemaType type, double wx, double wy) {
        if (type.isEntity()) {
            int h = ENTITY_HEADER_H + type.attributes().size() * ATTR_ROW_H + 8;
            return wx >= type.erdX() && wx <= type.erdX() + ENTITY_WIDTH
                && wy >= type.erdY() && wy <= type.erdY() + h;
        } else {
            // Diamond hit test
            double cx = type.erdX() + DIAMOND_W / 2.0;
            double cy = type.erdY() + DIAMOND_H / 2.0;
            double dx = Math.abs(wx - cx) / (DIAMOND_W / 2.0);
            double dy = Math.abs(wy - cy) / (DIAMOND_H / 2.0);
            return dx + dy <= 1.0;
        }
    }

    private boolean hitTestPort(SchemaType type, double wx, double wy) {
        Point2D[] ports = getPortPositions(type);
        for (Point2D p : ports) {
            double d = p.distance(wx, wy);
            if (d <= PORT_RADIUS + 3) return true;
        }
        return false;
    }

    private SchemaType findTypeAt(double wx, double wy) {
        if (registry == null) return null;
        // Check in reverse draw order (top-most first)
        List<SchemaType> all = new ArrayList<>(registry.relationshipTypes());
        all.addAll(registry.entityTypes());
        for (SchemaType t : all) {
            if (hitTestBox(t, wx, wy)) return t;
        }
        return null;
    }

    private Point2D screenToWorld(int sx, int sy) {
        double wx = (sx - getWidth() / 2.0) / zoom + getWidth() / 2.0 - panX;
        double wy = (sy - getHeight() / 2.0) / zoom + getHeight() / 2.0 - panY;
        return new Point2D.Double(wx, wy);
    }

    // ==================== MOUSE HANDLERS ====================

    private void setupMouseHandlers() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point2D wp = screenToWorld(e.getX(), e.getY());
                double wx = wp.getX(), wy = wp.getY();

                if (SwingUtilities.isRightMouseButton(e)) {
                    // Context menu
                    SchemaType type = findTypeAt(wx, wy);
                    selectedType = type;
                    repaint();
                    showContextMenu(e, type, wx, wy);
                    return;
                }

                if (SwingUtilities.isLeftMouseButton(e)) {
                    SchemaType type = findTypeAt(wx, wy);

                    if (type != null && type.isEntity() && hitTestPort(type, wx, wy)) {
                        // Start port drag for connections
                        draggingPort = true;
                        portDragSource = type;
                        portDragEndX = e.getX();
                        portDragEndY = e.getY();
                        return;
                    }

                    if (type != null) {
                        // Start dragging type
                        selectedType = type;
                        draggedType = type;
                        dragOffsetX = wx - type.erdX();
                        dragOffsetY = wy - type.erdY();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        if (layoutMode == LayoutMode.SPRING) { ErdLayoutEngine.reheat(); physicsTimer.start(); }
                    } else {
                        // Pan
                        selectedType = null;
                        panning = true;
                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                    repaint();
                } else if (SwingUtilities.isMiddleMouseButton(e)) {
                    panning = true;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingPort) {
                    portDragEndX = e.getX();
                    portDragEndY = e.getY();
                    // Update hover for target detection
                    Point2D wp = screenToWorld(e.getX(), e.getY());
                    SchemaType target = findTypeAt(wp.getX(), wp.getY());
                    hoveredType = (target != null && target.isEntity() && target != portDragSource) ? target : null;
                    repaint();
                } else if (panning) {
                    panX += (e.getX() - lastMouseX) / zoom;
                    panY += (e.getY() - lastMouseY) / zoom;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                } else if (draggedType != null) {
                    Point2D wp = screenToWorld(e.getX(), e.getY());
                    draggedType.setErdX(wp.getX() - dragOffsetX);
                    draggedType.setErdY(wp.getY() - dragOffsetY);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingPort && portDragSource != null) {
                    Point2D wp = screenToWorld(e.getX(), e.getY());
                    SchemaType target = findTypeAt(wp.getX(), wp.getY());
                    if (target != null && target.isEntity() && target != portDragSource) {
                        // Show connection type popup
                        showConnectionPopup(e, portDragSource, target);
                    }
                    draggingPort = false;
                    portDragSource = null;
                    hoveredType = null;
                    repaint();
                }

                if (panning) {
                    panning = false;
                    setCursor(Cursor.getDefaultCursor());
                }
                if (draggedType != null) {
                    draggedType = null;
                    setCursor(Cursor.getDefaultCursor());
                    savePositions();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    Point2D wp = screenToWorld(e.getX(), e.getY());
                    SchemaType type = findTypeAt(wp.getX(), wp.getY());
                    if (type != null) {
                        showTypeEditor(type);
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Point2D wp = screenToWorld(e.getX(), e.getY());
                double wx = wp.getX(), wy = wp.getY();

                SchemaType oldHover = hoveredType;
                hoveredType = findTypeAt(wx, wy);

                // Check for port hover
                SchemaType oldPortHover = hoveredPortType;
                hoveredPortType = null;
                if (hoveredType != null && hoveredType.isEntity() && hitTestPort(hoveredType, wx, wy)) {
                    hoveredPortType = hoveredType;
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                } else if (hoveredType != null) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    setCursor(Cursor.getDefaultCursor());
                }

                if (oldHover != hoveredType || oldPortHover != hoveredPortType) {
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
                zoom = Math.max(0.2, Math.min(4.0, zoom * factor));
                repaint();
            }
        };

        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
    }

    // ==================== CONTEXT MENUS ====================

    private void showContextMenu(MouseEvent e, SchemaType type, double wx, double wy) {
        JPopupMenu menu = new JPopupMenu();

        if (type != null) {
            JMenuItem editItem = new JMenuItem("Edit " + type.name() + "...");
            editItem.addActionListener(ev -> showTypeEditor(type));
            menu.add(editItem);

            if (type.isEntity()) {
                JMenuItem addAttrItem = new JMenuItem("Add Attribute...");
                addAttrItem.addActionListener(ev -> showAddAttributeDialog(type));
                menu.add(addAttrItem);
            }

            if (type.isRelationship()) {
                JMenuItem addAttrItem = new JMenuItem("Add Attribute...");
                addAttrItem.addActionListener(ev -> showAddAttributeDialog(type));
                menu.add(addAttrItem);
            }

            menu.addSeparator();

            JMenuItem deleteItem = new JMenuItem("Delete " + type.name());
            deleteItem.setForeground(new Color(255, 100, 100));
            deleteItem.addActionListener(ev -> deleteType(type));
            menu.add(deleteItem);
        } else {
            JMenuItem addEntityItem = new JMenuItem("Add Entity Type...");
            addEntityItem.addActionListener(ev -> showAddEntityTypeDialog(wx, wy));
            menu.add(addEntityItem);

            JMenuItem addRelItem = new JMenuItem("Add Relationship Type...");
            addRelItem.addActionListener(ev -> showAddRelationshipTypeDialog(wx, wy));
            menu.add(addRelItem);

            menu.addSeparator();

            JMenuItem treeItem = new JMenuItem("Tree Layout");
            treeItem.addActionListener(ev -> { treeLayout(); fitToView(); });
            menu.add(treeItem);

            JMenuItem springItem = new JMenuItem("Spring Layout");
            springItem.addActionListener(ev -> springLayout());
            menu.add(springItem);

            JMenuItem fitItem = new JMenuItem("Fit to View");
            fitItem.addActionListener(ev -> fitToView());
            menu.add(fitItem);
        }

        menu.show(this, e.getX(), e.getY());
    }

    // ==================== ACTIONS ====================

    public void springLayout() {
        if (registry != null) {
            layoutMode = LayoutMode.SPRING;
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            // Only scatter types that have no position yet; keep existing positions
            ErdLayoutEngine.initPositions(registry.allTypes(), cx, cy);
            ErdLayoutEngine.reheat();
            physicsTimer.start();
        }
    }

    public void treeLayout() {
        if (registry == null) return;
        // Compute target positions using a temporary copy approach:
        // save current positions, run tree layout to get targets, then restore
        double cx = 60;
        double cy = getHeight() > 0 ? getHeight() / 2.0 : 300;

        // Store current positions
        Map<String, double[]> currentPos = new HashMap<>();
        for (SchemaType t : registry.allTypes()) {
            currentPos.put(t.id(), new double[]{t.erdX(), t.erdY()});
        }

        // Compute tree target positions
        ErdLayoutEngine.treeLayout(registry.allTypes(), cx, cy);

        // Set targets and restore current positions for animation
        boolean needsAnimation = false;
        for (SchemaType t : registry.allTypes()) {
            t.setErdTargetX(t.erdX());
            t.setErdTargetY(t.erdY());
            double[] cur = currentPos.get(t.id());
            if (cur != null && (cur[0] != 0 || cur[1] != 0)) {
                t.setErdX(cur[0]);
                t.setErdY(cur[1]);
                double dx = t.erdTargetX() - t.erdX();
                double dy = t.erdTargetY() - t.erdY();
                if (Math.abs(dx) > ANIM_SNAP || Math.abs(dy) > ANIM_SNAP) {
                    needsAnimation = true;
                }
            }
            t.setErdAnimating(true);
            t.setErdVx(0);
            t.setErdVy(0);
        }

        if (needsAnimation) {
            layoutMode = LayoutMode.TREE_ANIMATING;
            physicsTimer.start();
        } else {
            // No animation needed (first open with no prior positions)
            layoutMode = LayoutMode.MANUAL;
            savePositions();
            repaint();
        }
    }

    public void fitToView() {
        if (registry == null || registry.allTypes().isEmpty()) return;

        double[] bounds = ErdLayoutEngine.getBounds(registry.allTypes());
        double bw = bounds[2] - bounds[0];
        double bh = bounds[3] - bounds[1];

        double zx = (getWidth() - 40.0) / bw;
        double zy = (getHeight() - 40.0) / bh;
        zoom = Math.min(zx, zy);
        zoom = Math.max(0.2, Math.min(4.0, zoom));

        double cx = (bounds[0] + bounds[2]) / 2.0;
        double cy = (bounds[1] + bounds[3]) / 2.0;
        panX = getWidth() / 2.0 / zoom - cx + getWidth() / 2.0 * (1 - 1.0 / zoom);
        panY = getHeight() / 2.0 / zoom - cy + getHeight() / 2.0 * (1 - 1.0 / zoom);

        // Simplified: center on bounds center
        panX = getWidth() / 2.0 - cx;
        panY = getHeight() / 2.0 - cy;

        repaint();
    }

    private void showTypeEditor(SchemaType type) {
        Window window = SwingUtilities.getWindowAncestor(this);
        SchemaTypeEditorDialog dialog = new SchemaTypeEditorDialog(window, registry, type);
        dialog.setVisible(true);
        repaint();
        fireDataChanged();
    }

    private void showAddEntityTypeDialog(double wx, double wy) {
        String name = JOptionPane.showInputDialog(this, "Entity type name:", "Add Entity Type",
            JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        String id = name.trim().toLowerCase().replaceAll("\\s+", "_");
        SchemaType type = new SchemaType(id, name.trim(), randomColor(), SchemaType.KIND_ENTITY);
        type.setDisplayOrder(registry.entityTypes().size());
        type.setErdX(wx);
        type.setErdY(wy);

        // Default attribute
        SchemaAttribute nameAttr = new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0);
        type.addAttribute(nameAttr);

        registry.save(type);
        registry.addAttribute(id, nameAttr);
        if (layoutMode == LayoutMode.SPRING) physicsTimer.start();
        fireDataChanged();
    }

    private void showAddRelationshipTypeDialog(double wx, double wy) {
        Window window = SwingUtilities.getWindowAncestor(this);

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
        panel.setBackground(new Color(45, 47, 51));

        JTextField nameField = new JTextField();
        JTextField labelField = new JTextField();

        List<SchemaType> entityTypes = registry.entityTypes();
        JComboBox<SchemaType> fromCombo = new JComboBox<>(entityTypes.toArray(new SchemaType[0]));
        JComboBox<SchemaType> toCombo = new JComboBox<>(entityTypes.toArray(new SchemaType[0]));

        fromCombo.setRenderer(new SchemaTypeListRenderer());
        toCombo.setRenderer(new SchemaTypeListRenderer());

        panel.add(new JLabel("Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Label (verb):"));
        panel.add(labelField);
        panel.add(new JLabel("From Type:"));
        panel.add(fromCombo);
        panel.add(new JLabel("To Type:"));
        panel.add(toCombo);

        int result = JOptionPane.showConfirmDialog(window, panel, "Add Relationship Type",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;

            String id = name.toLowerCase().replaceAll("\\s+", "_");
            SchemaType from = (SchemaType) fromCombo.getSelectedItem();
            SchemaType to = (SchemaType) toCombo.getSelectedItem();

            SchemaType type = new SchemaType(id, name, randomColor(), SchemaType.KIND_RELATIONSHIP);
            type.setLabel(labelField.getText().trim());
            type.setFromTypeId(from != null ? from.id() : null);
            type.setToTypeId(to != null ? to.id() : null);
            type.setDisplayOrder(registry.relationshipTypes().size());
            type.setErdX(wx);
            type.setErdY(wy);

            registry.save(type);
            if (layoutMode == LayoutMode.SPRING) physicsTimer.start();
            fireDataChanged();
        }
    }

    private void showAddAttributeDialog(SchemaType type) {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField nameField = new JTextField();
        JComboBox<String> dataTypeCombo = new JComboBox<>(new String[]{
            SchemaAttribute.TEXT, SchemaAttribute.NUMBER, SchemaAttribute.LIST, SchemaAttribute.BOOLEAN
        });
        JCheckBox requiredCheck = new JCheckBox("Required");

        panel.add(new JLabel("Attribute name:"));
        panel.add(nameField);
        panel.add(new JLabel("Data type:"));
        panel.add(dataTypeCombo);
        panel.add(new JLabel());
        panel.add(requiredCheck);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Attribute to " + type.name(),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;

            SchemaAttribute attr = new SchemaAttribute(
                name.toLowerCase().replaceAll("\\s+", "_"),
                (String) dataTypeCombo.getSelectedItem(),
                requiredCheck.isSelected(),
                type.attributes().size()
            );
            registry.addAttribute(type.id(), attr);
            repaint();
            fireDataChanged();
        }
    }

    private void deleteType(SchemaType type) {
        int result = JOptionPane.showConfirmDialog(this,
            "Delete type '" + type.name() + "'?\nThis cannot be undone.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            registry.deleteType(type.id());
            if (selectedType == type) selectedType = null;
            repaint();
            fireDataChanged();
        }
    }

    private void showConnectionPopup(MouseEvent e, SchemaType from, SchemaType to) {
        ConnectionTypePopup popup = new ConnectionTypePopup(registry, from, to, relType -> {
            repaint();
            fireDataChanged();
        });
        popup.show(this, e.getX(), e.getY());
    }

    private void savePositions() {
        if (registry != null) registry.savePositions();
    }

    private void fireDataChanged() {
        if (onDataChanged != null) {
            onDataChanged.accept(null);
        }
    }

    private Color randomColor() {
        Random rand = new Random();
        return new Color(100 + rand.nextInt(120), 100 + rand.nextInt(120), 100 + rand.nextInt(120));
    }

    // ==================== LIST RENDERER ====================

    private static class SchemaTypeListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SchemaType type) {
                setText(type.name());
                if (!isSelected) setForeground(type.color());
            }
            return this;
        }
    }
}
