package com.tradery.layout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Visual demo of ForceDirectedLayout using plaiiin-graph.json.
 * Run: ./gradlew :tradery-spring-layout:demo
 */
public class LayoutDemo extends JPanel {

    static class SimpleNode implements LayoutNode {
        final String id;
        final String name;
        double x, y, vx, vy;
        boolean pinned;
        Color color;

        SimpleNode(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String id() { return id; }
        @Override public double x() { return x; }
        @Override public double y() { return y; }
        @Override public void setX(double x) { this.x = x; }
        @Override public void setY(double y) { this.y = y; }
        @Override public double vx() { return vx; }
        @Override public double vy() { return vy; }
        @Override public void setVx(double vx) { this.vx = vx; }
        @Override public void setVy(double vy) { this.vy = vy; }
        @Override public boolean isPinned() { return pinned; }
    }

    static class SimpleEdge implements LayoutEdge {
        final String fromId, toId;
        SimpleEdge(String fromId, String toId) {
            this.fromId = fromId;
            this.toId = toId;
        }
        @Override public String fromId() { return fromId; }
        @Override public String toId() { return toId; }
    }

    private final List<SimpleNode> nodes = new ArrayList<>();
    private final List<SimpleEdge> edges = new ArrayList<>();
    private final Map<String, SimpleNode> nodeMap = new HashMap<>();
    private final ForceDirectedLayout layout;
    private final Timer physicsTimer;
    private SimpleNode draggedNode;
    private SimpleNode hoveredNode;
    private int stepCount = 0;

    // View
    private double zoom = 1.0;
    private double panX = 0, panY = 0;
    private int lastMouseX, lastMouseY;
    private boolean panning;

    // Assign depth-based colors
    private static final Color[] DEPTH_COLORS = {
        new Color(255, 100, 100),  // root - red
        new Color(100, 180, 255),  // depth 1 - blue
        new Color(100, 220, 130),  // depth 2 - green
        new Color(255, 180, 80),   // depth 3 - orange
        new Color(180, 130, 255),  // depth 4 - purple
    };

    public LayoutDemo(List<SimpleNode> nodes, List<SimpleEdge> edges) {
        this.nodes.addAll(nodes);
        this.edges.addAll(edges);
        for (SimpleNode n : nodes) nodeMap.put(n.id, n);

        // Assign colors by depth from root
        assignDepthColors();

        layout = new ForceDirectedLayout(LayoutConfig.coinGraph());
        layout.initPositions(this.nodes, 600, 400, 800, 600);

        setBackground(new Color(30, 30, 35));
        setPreferredSize(new Dimension(1200, 800));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                SimpleNode hit = findNodeAt(e.getX(), e.getY());
                if (SwingUtilities.isLeftMouseButton(e) && hit != null) {
                    draggedNode = hit;
                } else {
                    panning = true;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggedNode != null) {
                    double tx = (e.getX() - getWidth() / 2.0) / zoom + getWidth() / 2.0 - panX;
                    double ty = (e.getY() - getHeight() / 2.0) / zoom + getHeight() / 2.0 - panY;
                    draggedNode.setX(tx);
                    draggedNode.setY(ty);
                    draggedNode.setVx(0);
                    draggedNode.setVy(0);
                    if (!physicsTimer.isRunning()) physicsTimer.start();
                } else if (panning) {
                    panX += (e.getX() - lastMouseX) / zoom;
                    panY += (e.getY() - lastMouseY) / zoom;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggedNode = null;
                panning = false;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                SimpleNode hit = findNodeAt(e.getX(), e.getY());
                if (hit != hoveredNode) {
                    hoveredNode = hit;
                    setCursor(hit != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                          : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
                zoom = Math.max(0.1, Math.min(5.0, zoom * factor));
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);

        physicsTimer = new Timer(32, e -> {
            boolean moving = layout.step(this.nodes, this.edges,
                getWidth() / 2.0, getHeight() / 2.0, draggedNode);
            stepCount++;
            repaint();
            if (!moving) ((Timer) e.getSource()).stop();
        });
        physicsTimer.start();
    }

    private void assignDepthColors() {
        // BFS from root to assign depth
        Map<String, Integer> depth = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        // Find root (node with id "-1")
        SimpleNode root = nodeMap.get("-1");
        if (root != null) {
            depth.put(root.id, 0);
            queue.add(root.id);
        }

        // Build adjacency (edges go source -> target, so target is parent)
        Map<String, List<String>> children = new HashMap<>();
        for (SimpleEdge e : edges) {
            children.computeIfAbsent(e.toId, k -> new ArrayList<>()).add(e.fromId);
        }

        while (!queue.isEmpty()) {
            String id = queue.poll();
            int d = depth.get(id);
            for (String childId : children.getOrDefault(id, List.of())) {
                if (!depth.containsKey(childId)) {
                    depth.put(childId, d + 1);
                    queue.add(childId);
                }
            }
        }

        for (SimpleNode n : nodes) {
            int d = depth.getOrDefault(n.id, 0);
            n.color = DEPTH_COLORS[Math.min(d, DEPTH_COLORS.length - 1)];
        }
    }

    private SimpleNode findNodeAt(int mx, int my) {
        double tx = (mx - getWidth() / 2.0) / zoom + getWidth() / 2.0 - panX;
        double ty = (my - getHeight() / 2.0) / zoom + getHeight() / 2.0 - panY;
        for (SimpleNode n : nodes) {
            double dx = tx - n.x, dy = ty - n.y;
            if (dx * dx + dy * dy < 15 * 15) return n;
        }
        return null;
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

        // Draw edges
        g2.setStroke(new BasicStroke(1.5f));
        for (SimpleEdge edge : edges) {
            SimpleNode from = nodeMap.get(edge.fromId);
            SimpleNode to = nodeMap.get(edge.toId);
            if (from == null || to == null) continue;

            boolean highlight = from == hoveredNode || to == hoveredNode;
            g2.setColor(highlight ? new Color(150, 150, 180, 180) : new Color(80, 80, 100, 100));
            g2.draw(new Line2D.Double(from.x, from.y, to.x, to.y));
        }

        // Draw nodes
        for (SimpleNode node : nodes) {
            int r = node.name.equals("-") ? 5 : (node.id.equals("-1") ? 12 : 8);
            Color c = node.color;

            if (node == hoveredNode) {
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
                g2.fillOval((int) node.x - r - 5, (int) node.y - r - 5, (r + 5) * 2, (r + 5) * 2);
            }

            g2.setColor(c);
            g2.fillOval((int) node.x - r, (int) node.y - r, r * 2, r * 2);

            g2.setColor(c.darker());
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval((int) node.x - r, (int) node.y - r, r * 2, r * 2);

            // Label (skip unnamed "-" nodes)
            if (!node.name.equals("-")) {
                g2.setColor(new Color(220, 220, 230));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                String label = node.name.length() > 20 ? node.name.substring(0, 20) + "..." : node.name;
                int labelW = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, (int) node.x - labelW / 2, (int) node.y + r + 14);
            }
        }

        // HUD
        g2.setTransform(new java.awt.geom.AffineTransform());
        g2.setColor(new Color(120, 120, 140));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.drawString("Nodes: " + nodes.size() + "  Edges: " + edges.size()
            + "  Steps: " + stepCount + "  Zoom: " + String.format("%.1f", zoom)
            + (physicsTimer.isRunning() ? "  [running]" : "  [settled]"), 12, 20);
        g2.drawString("Drag nodes, scroll to zoom, drag background to pan", 12, 36);
    }

    public static void main(String[] args) throws Exception {
        // Load graph from plaiiin-graph.json
        File graphFile = new File("plaiiin-graph.json");
        if (!graphFile.exists()) {
            System.err.println("plaiiin-graph.json not found in " + new File(".").getAbsolutePath());
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(graphFile);

        List<SimpleNode> nodes = new ArrayList<>();
        List<SimpleEdge> edges = new ArrayList<>();

        for (JsonNode n : root.get("nodes")) {
            String id = String.valueOf(n.get("id").asInt());
            String name = n.get("name").asText();
            nodes.add(new SimpleNode(id, name));
        }

        for (JsonNode e : root.get("edges")) {
            String source = String.valueOf(e.get("source").asInt());
            String target = String.valueOf(e.get("target").asInt());
            edges.add(new SimpleEdge(source, target));
        }

        System.out.println("Loaded " + nodes.size() + " nodes, " + edges.size() + " edges");

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("ForceDirectedLayout Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new LayoutDemo(nodes, edges));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
