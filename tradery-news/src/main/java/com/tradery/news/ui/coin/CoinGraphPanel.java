package com.tradery.news.ui.coin;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * 2D spring-layout graph visualization for coin relationships.
 */
public class CoinGraphPanel extends JPanel {

    private final List<CoinEntity> entities = new ArrayList<>();
    private final List<CoinRelationship> relationships = new ArrayList<>();
    private final Map<String, CoinEntity> entityMap = new HashMap<>();

    private Timer physicsTimer;
    private CoinEntity hoveredEntity;
    private CoinEntity selectedEntity;
    private CoinEntity draggedEntity;
    private Consumer<CoinEntity> onEntitySelected;
    private Map<String, Integer> connectionDistance = new HashMap<>();  // Distance from selected node (0 = selected, 1 = direct, etc.)

    // View settings
    private boolean showLabels = true;
    private boolean showRelationshipLabels = false;
    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;
    private int lastMouseX, lastMouseY;
    private boolean panning = false;

    // Physics settings
    private double repulsion = 5000;
    private double attraction = 0.01;
    private double damping = 0.92;      // Higher = slower decay
    private double centerPull = 0.001;
    private double minVelocity = 0.1;   // Below this, stop moving

    public CoinGraphPanel() {
        setBackground(new Color(25, 27, 31));
        setPreferredSize(new Dimension(1200, 800));

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // Check if clicking on a node
                    CoinEntity entity = findEntityAt(e.getX(), e.getY());
                    if (entity != null) {
                        // Drag node
                        startDrag(e.getX(), e.getY());
                    } else {
                        // Pan background
                        panning = true;
                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                } else if (SwingUtilities.isMiddleMouseButton(e)) {
                    // Middle mouse always pans
                    panning = true;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panning) {
                    panX += (e.getX() - lastMouseX) / zoom;
                    panY += (e.getY() - lastMouseY) / zoom;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                } else {
                    handleDrag(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panning) {
                    panning = false;
                    setCursor(Cursor.getDefaultCursor());
                } else {
                    stopDrag();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // Double-click: zoom to connected nodes or fit all
                    CoinEntity entity = findEntityAt(e.getX(), e.getY());
                    if (entity != null) {
                        zoomToConnectedNodes(entity);
                    } else {
                        fitToView();
                    }
                } else {
                    handleClick(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
                zoom = Math.max(0.1, Math.min(5.0, zoom * factor));
                repaint();
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);

        // Physics timer
        physicsTimer = new Timer(32, e -> {
            boolean moving = runPhysicsStep();
            repaint();
            if (!moving) physicsTimer.stop();
        });
    }

    public void setData(List<CoinEntity> entities, List<CoinRelationship> relationships) {
        this.entities.clear();
        this.relationships.clear();
        this.entityMap.clear();

        this.entities.addAll(entities);
        this.relationships.addAll(relationships);

        for (CoinEntity entity : entities) {
            entityMap.put(entity.id(), entity);
        }

        // Count connections
        for (CoinRelationship rel : relationships) {
            CoinEntity from = entityMap.get(rel.fromId());
            CoinEntity to = entityMap.get(rel.toId());
            if (from != null) from.incrementConnectionCount();
            if (to != null) to.incrementConnectionCount();
        }

        // Initial random positions
        int w = getWidth() > 0 ? getWidth() : 1200;
        int h = getHeight() > 0 ? getHeight() : 800;
        Random rand = new Random();
        for (CoinEntity entity : entities) {
            entity.setX(w / 2.0 + (rand.nextDouble() - 0.5) * w * 0.8);
            entity.setY(h / 2.0 + (rand.nextDouble() - 0.5) * h * 0.8);
        }

        physicsTimer.start();
        repaint();
    }

    private boolean runPhysicsStep() {
        if (entities.isEmpty()) return false;

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        boolean anyMoving = false;

        for (CoinEntity entity : entities) {
            if (entity.isPinned() || entity == draggedEntity) continue;

            double fx = 0, fy = 0;

            // Repulsion from all other entities
            for (CoinEntity other : entities) {
                if (other == entity) continue;
                double dx = entity.x() - other.x();
                double dy = entity.y() - other.y();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;
                if (dist < 300) {
                    double force = repulsion / (dist * dist);
                    fx += (dx / dist) * force;
                    fy += (dy / dist) * force;
                }
            }

            // Attraction along relationships
            for (CoinRelationship rel : relationships) {
                CoinEntity other = null;
                if (rel.fromId().equals(entity.id())) {
                    other = entityMap.get(rel.toId());
                } else if (rel.toId().equals(entity.id())) {
                    other = entityMap.get(rel.fromId());
                }
                if (other != null) {
                    double dx = other.x() - entity.x();
                    double dy = other.y() - entity.y();
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > 50) {
                        fx += dx * attraction;
                        fy += dy * attraction;
                    }
                }
            }

            // Gentle pull toward center
            fx += (centerX - entity.x()) * centerPull;
            fy += (centerY - entity.y()) * centerPull;

            // Apply forces with damping
            double vx = (entity.vx() + fx) * damping;
            double vy = (entity.vy() + fy) * damping;

            // Clamp max velocity
            double maxSpeed = 8.0;
            double speed = Math.sqrt(vx * vx + vy * vy);
            if (speed > maxSpeed) {
                vx = (vx / speed) * maxSpeed;
                vy = (vy / speed) * maxSpeed;
            }

            // Stop jittering - if velocity is tiny, zero it out
            if (Math.abs(vx) < minVelocity) vx = 0;
            if (Math.abs(vy) < minVelocity) vy = 0;

            entity.setVx(vx);
            entity.setVy(vy);
            if (vx != 0 || vy != 0) {
                entity.setX(entity.x() + vx);
                entity.setY(entity.y() + vy);
                anyMoving = true;
            }
        }
        return anyMoving;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Apply zoom and pan
        g2.translate(getWidth() / 2.0, getHeight() / 2.0);
        g2.scale(zoom, zoom);
        g2.translate(-getWidth() / 2.0 + panX, -getHeight() / 2.0 + panY);

        // Draw relationships
        drawRelationships(g2);

        // Draw entities
        drawEntities(g2);

        // Draw tooltip
        g2.setTransform(new java.awt.geom.AffineTransform());  // Reset transform for tooltip
        drawTooltip(g2);

        // Draw legend
        drawLegend(g2);
    }

    private void drawRelationships(Graphics2D g2) {
        for (CoinRelationship rel : relationships) {
            CoinEntity from = entityMap.get(rel.fromId());
            CoinEntity to = entityMap.get(rel.toId());
            if (from == null || to == null) continue;

            boolean highlight = (from.isHovered() || from.isSelected() ||
                                to.isHovered() || to.isSelected());

            // Get the max distance of the two endpoints (use the further one)
            int fromDist = getConnectionDistance(rel.fromId());
            int toDist = getConnectionDistance(rel.toId());
            int maxDist = Math.max(fromDist, toDist);
            float opacity = getOpacityForDistance(maxDist);

            Color c = rel.getColor();
            int alpha;
            if (highlight) {
                alpha = (int)(200 * opacity);
            } else {
                alpha = (int)(60 * opacity);
            }
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(10, alpha)));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Shorten line to stop at node edge + gap
            double gap = 6;
            double dx = to.x() - from.x();
            double dy = to.y() - from.y();
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                double nx = dx / dist;  // normalized direction
                double ny = dy / dist;
                double startOffset = from.getRadius() + gap;
                double endOffset = to.getRadius() + gap;
                double x1 = from.x() + nx * startOffset;
                double y1 = from.y() + ny * startOffset;
                double x2 = to.x() - nx * endOffset;
                double y2 = to.y() - ny * endOffset;
                g2.draw(new Line2D.Double(x1, y1, x2, y2));
            }

            // Relationship label
            if (showRelationshipLabels && highlight) {
                double midX = (from.x() + to.x()) / 2;
                double midY = (from.y() + to.y()) / 2;
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.setColor(new Color(180, 180, 190));
                g2.drawString(rel.type().label(), (int)midX, (int)midY);
            }
        }
    }

    private void drawEntities(Graphics2D g2) {
        for (CoinEntity entity : entities) {
            int r = entity.getRadius();
            Color c = entity.getColor();
            int distance = getConnectionDistance(entity.id());
            float opacity = getOpacityForDistance(distance);

            // Glow for hovered/selected
            if (entity.isHovered() || entity.isSelected()) {
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
                g2.fillOval((int)entity.x() - r - 6, (int)entity.y() - r - 6, (r + 6) * 2, (r + 6) * 2);
            }

            // Pinned indicator
            if (entity.isPinned() && distance >= 0) {
                g2.setColor(new Color(255, 100, 100, (int)(100 * opacity)));
                g2.fillOval((int)entity.x() - r - 4, (int)entity.y() - r - 4, (r + 4) * 2, (r + 4) * 2);
            }

            // Node fill (with graduated opacity)
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(255 * opacity)));
            g2.fillOval((int)entity.x() - r, (int)entity.y() - r, r * 2, r * 2);

            // Node border
            Color darker = c.darker();
            g2.setColor(new Color(darker.getRed(), darker.getGreen(), darker.getBlue(), (int)(255 * opacity)));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval((int)entity.x() - r, (int)entity.y() - r, r * 2, r * 2);

            // Label (with graduated opacity, hide only for completely unconnected)
            if (showLabels && distance >= 0) {
                int labelAlpha = (int)(230 * Math.max(0.5f, opacity));  // Keep labels readable
                g2.setColor(new Color(220, 220, 230, labelAlpha));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                String label = entity.label();
                int labelWidth = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, (int)entity.x() - labelWidth / 2, (int)entity.y() + r + 14);
            }
        }
    }

    private void drawTooltip(Graphics2D g2) {
        if (hoveredEntity == null) return;

        String[] lines = {
            hoveredEntity.name(),
            "Type: " + hoveredEntity.type(),
            hoveredEntity.symbol() != null ? "Symbol: " + hoveredEntity.symbol() : null,
            hoveredEntity.marketCap() > 0 ? "Market Cap: $" + formatNumber(hoveredEntity.marketCap()) : null,
            "Connections: " + hoveredEntity.connectionCount(),
            "Double-click to zoom to connections"
        };

        // Filter nulls
        lines = Arrays.stream(lines).filter(Objects::nonNull).toArray(String[]::new);

        int padding = 8;
        int lineHeight = 16;
        int width = 250;
        int height = lines.length * lineHeight + padding * 2;

        // Position near mouse
        Point mouse = getMousePosition();
        if (mouse == null) return;
        int x = mouse.x + 15;
        int y = mouse.y - height / 2;
        if (x + width > getWidth() - 10) x = mouse.x - width - 15;
        if (y < 10) y = 10;
        if (y + height > getHeight() - 10) y = getHeight() - height - 10;

        g2.setColor(new Color(40, 42, 48, 240));
        g2.fillRoundRect(x, y, width, height, 8, 8);
        g2.setColor(new Color(70, 72, 78));
        g2.drawRoundRect(x, y, width, height, 8, 8);

        g2.setColor(new Color(220, 220, 230));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (int i = 0; i < lines.length; i++) {
            g2.drawString(lines[i], x + padding, y + padding + (i + 1) * lineHeight - 4);
        }
    }

    private void drawLegend(Graphics2D g2) {
        int x = 15;
        int y = 15;
        int boxSize = 12;
        int lineHeight = 18;

        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2.setColor(new Color(150, 150, 160));
        g2.drawString("ENTITY TYPES", x, y + 10);
        y += 20;

        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (CoinEntity.Type type : CoinEntity.Type.values()) {
            g2.setColor(type.color());
            g2.fillOval(x, y, boxSize, boxSize);
            g2.setColor(new Color(180, 180, 190));
            g2.drawString(type.name(), x + boxSize + 6, y + 10);
            y += lineHeight;
        }

        y += 10;
        g2.setColor(new Color(120, 120, 130));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.drawString("Scroll to zoom, drag background to pan", x, y);
        g2.drawString("Double-click node to zoom, background to fit all", x, y + 12);
    }

    private String formatNumber(double num) {
        if (num >= 1_000_000_000_000L) return String.format("%.1fT", num / 1_000_000_000_000L);
        if (num >= 1_000_000_000L) return String.format("%.1fB", num / 1_000_000_000L);
        if (num >= 1_000_000L) return String.format("%.1fM", num / 1_000_000L);
        return String.format("%.0f", num);
    }

    private CoinEntity findEntityAt(int mx, int my) {
        // Transform mouse coordinates
        double tx = (mx - getWidth() / 2.0) / zoom + getWidth() / 2.0 - panX;
        double ty = (my - getHeight() / 2.0) / zoom + getHeight() / 2.0 - panY;

        for (CoinEntity entity : entities) {
            if (entity.contains(tx, ty)) {
                return entity;
            }
        }
        return null;
    }

    private void updateHover(int mx, int my) {
        CoinEntity newHover = findEntityAt(mx, my);
        if (newHover != hoveredEntity) {
            if (hoveredEntity != null) hoveredEntity.setHovered(false);
            hoveredEntity = newHover;
            if (hoveredEntity != null) hoveredEntity.setHovered(true);
            setCursor(hoveredEntity != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
        }
    }

    private void startDrag(int mx, int my) {
        draggedEntity = findEntityAt(mx, my);
        if (draggedEntity != null && !physicsTimer.isRunning()) {
            physicsTimer.start();
        }
    }

    private void handleDrag(int mx, int my) {
        if (draggedEntity == null) return;
        double tx = (mx - getWidth() / 2.0) / zoom + getWidth() / 2.0 - panX;
        double ty = (my - getHeight() / 2.0) / zoom + getHeight() / 2.0 - panY;
        draggedEntity.setX(tx);
        draggedEntity.setY(ty);
        draggedEntity.setVx(0);
        draggedEntity.setVy(0);
        repaint();
    }

    private void stopDrag() {
        draggedEntity = null;
    }

    private void handleClick(int mx, int my) {
        if (selectedEntity != null) selectedEntity.setSelected(false);
        selectedEntity = findEntityAt(mx, my);
        if (selectedEntity != null) {
            selectedEntity.setSelected(true);
            if (onEntitySelected != null) {
                onEntitySelected.accept(selectedEntity);
            }
        }
        updateConnectedSet();
        repaint();
    }

    public void setOnEntitySelected(Consumer<CoinEntity> callback) {
        this.onEntitySelected = callback;
    }

    public void setShowLabels(boolean show) {
        this.showLabels = show;
        repaint();
    }

    public void setShowRelationshipLabels(boolean show) {
        this.showRelationshipLabels = show;
        repaint();
    }

    public void stopPhysics() {
        if (physicsTimer != null) physicsTimer.stop();
    }

    public void resetView() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
        repaint();
    }

    /**
     * Zoom to fit all nodes in view.
     */
    public void fitToView() {
        if (entities.isEmpty()) return;

        // Find bounding box
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (CoinEntity entity : entities) {
            minX = Math.min(minX, entity.x());
            maxX = Math.max(maxX, entity.x());
            minY = Math.min(minY, entity.y());
            maxY = Math.max(maxY, entity.y());
        }

        zoomToBounds(minX, minY, maxX, maxY);
    }

    /**
     * Zoom to show connected nodes (direct and indirect) from the given entity.
     * Caps at a reasonable level to show around 20 nodes.
     */
    private void zoomToConnectedNodes(CoinEntity startEntity) {
        // BFS to find connected nodes, cap at ~20 nodes
        Set<String> connectedIds = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startEntity.id());
        connectedIds.add(startEntity.id());

        int maxNodes = 20;

        while (!queue.isEmpty() && connectedIds.size() < maxNodes) {
            String current = queue.poll();

            for (CoinRelationship rel : relationships) {
                String neighbor = null;
                if (rel.fromId().equals(current)) neighbor = rel.toId();
                else if (rel.toId().equals(current)) neighbor = rel.fromId();

                if (neighbor != null && !connectedIds.contains(neighbor)) {
                    connectedIds.add(neighbor);
                    if (connectedIds.size() < maxNodes) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        // Collect entities for bounding box
        List<CoinEntity> connectedEntities = new ArrayList<>();
        for (String id : connectedIds) {
            CoinEntity entity = entityMap.get(id);
            if (entity != null) {
                connectedEntities.add(entity);
            }
        }

        if (connectedEntities.isEmpty()) return;

        // Find bounding box
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (CoinEntity entity : connectedEntities) {
            minX = Math.min(minX, entity.x());
            maxX = Math.max(maxX, entity.x());
            minY = Math.min(minY, entity.y());
            maxY = Math.max(maxY, entity.y());
        }

        zoomToBounds(minX, minY, maxX, maxY);

        // Also select the entity and update connected set
        if (selectedEntity != null) selectedEntity.setSelected(false);
        selectedEntity = startEntity;
        selectedEntity.setSelected(true);
        updateConnectedSet();
        if (onEntitySelected != null) {
            onEntitySelected.accept(startEntity);
        }
    }

    /**
     * Zoom and pan to fit the given bounds with padding.
     */
    private void zoomToBounds(double minX, double minY, double maxX, double maxY) {
        double padding = 80;
        double width = maxX - minX + padding * 2;
        double height = maxY - minY + padding * 2;

        if (width < 100) width = 100;
        if (height < 100) height = 100;

        double zoomX = getWidth() / width;
        double zoomY = getHeight() / height;
        zoom = Math.min(zoomX, zoomY);
        zoom = Math.max(0.1, Math.min(3.0, zoom));  // Cap zoom level

        // Center on the bounds
        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;
        panX = getWidth() / 2.0 - centerX;
        panY = getHeight() / 2.0 - centerY;

        repaint();
    }

    public void selectAndPanTo(String entityId) {
        CoinEntity entity = entityMap.get(entityId);
        if (entity == null) return;

        // Deselect current
        if (selectedEntity != null) selectedEntity.setSelected(false);

        // Select new
        selectedEntity = entity;
        selectedEntity.setSelected(true);

        // Pan to center on entity
        panX = getWidth() / 2.0 - entity.x();
        panY = getHeight() / 2.0 - entity.y();

        // Update connected set for opacity
        updateConnectedSet();

        // Notify callback
        if (onEntitySelected != null) {
            onEntitySelected.accept(entity);
        }

        repaint();
    }

    public List<CoinRelationship> getRelationshipsFor(String entityId) {
        List<CoinRelationship> result = new ArrayList<>();
        for (CoinRelationship rel : relationships) {
            if (rel.fromId().equals(entityId) || rel.toId().equals(entityId)) {
                result.add(rel);
            }
        }
        return result;
    }

    public CoinEntity getEntity(String id) {
        return entityMap.get(id);
    }

    private void updateConnectedSet() {
        connectionDistance.clear();
        if (selectedEntity == null) return;

        // Level 0: selected coin
        // Level 1: reachable WITHOUT traversing through another coin
        // Level 2: requires going through another coin to reach

        connectionDistance.put(selectedEntity.id(), 0);

        // Phase 1: BFS from selected - don't continue through other coins
        Set<String> level1Coins = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(selectedEntity.id());

        while (!queue.isEmpty()) {
            String current = queue.poll();
            CoinEntity currentEntity = entityMap.get(current);
            boolean currentIsCoin = currentEntity != null && currentEntity.type() == CoinEntity.Type.COIN;

            for (CoinRelationship rel : relationships) {
                String neighbor = null;
                if (rel.fromId().equals(current)) neighbor = rel.toId();
                else if (rel.toId().equals(current)) neighbor = rel.fromId();

                if (neighbor != null && !connectionDistance.containsKey(neighbor)) {
                    CoinEntity neighborEntity = entityMap.get(neighbor);
                    if (neighborEntity == null) continue;

                    boolean neighborIsCoin = neighborEntity.type() == CoinEntity.Type.COIN;

                    connectionDistance.put(neighbor, 1);

                    if (neighborIsCoin) {
                        // Track coins at level 1 for phase 2
                        level1Coins.add(neighbor);
                        // Don't continue BFS from coins (except selected)
                    } else {
                        // Continue BFS through non-coin nodes
                        queue.add(neighbor);
                    }
                }
            }
        }

        // Phase 2: BFS from level-1 coins to find level-2 nodes
        for (String coinId : level1Coins) {
            Queue<String> q2 = new LinkedList<>();
            q2.add(coinId);

            while (!q2.isEmpty()) {
                String current = q2.poll();

                for (CoinRelationship rel : relationships) {
                    String neighbor = null;
                    if (rel.fromId().equals(current)) neighbor = rel.toId();
                    else if (rel.toId().equals(current)) neighbor = rel.fromId();

                    if (neighbor != null && !connectionDistance.containsKey(neighbor)) {
                        connectionDistance.put(neighbor, 2);
                        q2.add(neighbor);
                    }
                }
            }
        }
    }

    private int getConnectionDistance(String entityId) {
        if (selectedEntity == null) return 0;  // No selection = full visibility
        return connectionDistance.getOrDefault(entityId, -1);  // -1 = not connected
    }

    private float getOpacityForDistance(int distance) {
        if (distance == -1) return 0.12f;  // Not connected
        if (distance == 0) return 1.0f;    // Selected
        if (distance == 1) return 0.85f;   // Level 1 (reachable without crossing coins)
        return 0.50f;                       // Level 2 (requires crossing a coin)
    }
}
