package com.tradery.news.ui.coin;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
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
    private double damping = 0.85;
    private double centerPull = 0.001;

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
                if (SwingUtilities.isMiddleMouseButton(e) ||
                    (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown())) {
                    panning = true;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else {
                    startDrag(e.getX(), e.getY());
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
                    // Double-click to pin/unpin
                    CoinEntity entity = findEntityAt(e.getX(), e.getY());
                    if (entity != null) {
                        entity.setPinned(!entity.isPinned());
                        repaint();
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
        physicsTimer = new Timer(16, e -> {
            runPhysicsStep();
            repaint();
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

    private void runPhysicsStep() {
        if (entities.isEmpty()) return;

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

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

            // Apply forces
            entity.setVx((entity.vx() + fx) * damping);
            entity.setVy((entity.vy() + fy) * damping);
            entity.setX(entity.x() + entity.vx());
            entity.setY(entity.y() + entity.vy());
        }
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

            Color c = rel.getColor();
            int alpha = highlight ? 180 : 50;
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            g2.setStroke(new BasicStroke(highlight ? 2f : 1f));
            g2.draw(new Line2D.Double(from.x(), from.y(), to.x(), to.y()));

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

            // Glow for hovered/selected
            if (entity.isHovered() || entity.isSelected()) {
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
                g2.fillOval((int)entity.x() - r - 6, (int)entity.y() - r - 6, (r + 6) * 2, (r + 6) * 2);
            }

            // Pinned indicator
            if (entity.isPinned()) {
                g2.setColor(new Color(255, 100, 100, 100));
                g2.fillOval((int)entity.x() - r - 4, (int)entity.y() - r - 4, (r + 4) * 2, (r + 4) * 2);
            }

            // Node fill
            g2.setColor(c);
            g2.fillOval((int)entity.x() - r, (int)entity.y() - r, r * 2, r * 2);

            // Node border
            g2.setColor(c.darker());
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval((int)entity.x() - r, (int)entity.y() - r, r * 2, r * 2);

            // Label
            if (showLabels) {
                g2.setColor(new Color(220, 220, 230));
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
            hoveredEntity.isPinned() ? "(Pinned - double-click to unpin)" : "(Double-click to pin)"
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
        g2.drawString("Scroll to zoom, Shift+drag to pan", x, y);
        g2.drawString("Double-click to pin/unpin node", x, y + 12);
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
}
