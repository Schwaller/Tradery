package com.tradery.news.ui.coin;

import com.tradery.layout.*;

import java.util.*;

/**
 * Force-directed spring layout engine for ERD canvas.
 * All schema types repel each other; relationship types are attracted
 * to the midpoint between their connected entity types.
 */
public class ErdLayoutEngine {

    private static final double ATTRACTION = 0.005;

    private static final ForceDirectedLayout layout = new ForceDirectedLayout(
        LayoutConfig.erd(),
        // Same-kind nodes repel 1.5x harder
        (a, b) -> {
            if (a instanceof SchemaType sa && b instanceof SchemaType sb) {
                return sa.kind().equals(sb.kind()) ? 1.5 : 1.0;
            }
            return 1.0;
        },
        List.of(
            // Relationship types: pull toward midpoint of connected entities
            ErdLayoutEngine::midpointAttraction,
            // Entity types: attracted to partners through shared relationships
            ErdLayoutEngine::entityThroughRelAttraction
        )
    );

    /**
     * Scatter types randomly around a center point for initial placement.
     * Resets the cooling temperature.
     */
    public static void initPositions(Collection<SchemaType> allTypes, double cx, double cy) {
        layout.initPositions(allTypes, cx, cy, 600, 400);
    }

    /** Reset temperature to let the simulation run hot again (e.g. when dragging). */
    public static void reheat() {
        layout.reheat();
    }

    /**
     * Run one step of the force-directed simulation.
     * Returns true if the system is still moving (not settled).
     */
    public static boolean step(Collection<SchemaType> allTypes, double cx, double cy,
                                SchemaType draggedType) {
        return layout.step(allTypes, List.of(), cx, cy, draggedType);
    }

    /** Relationship types: pull toward midpoint of connected entity types. */
    private static void midpointAttraction(LayoutNode node, Map<String, ? extends LayoutNode> nodeMap,
                                            double[] force) {
        if (!(node instanceof SchemaType type) || !type.isRelationship()) return;
        LayoutNode fromNode = nodeMap.get(type.fromTypeId());
        LayoutNode toNode = nodeMap.get(type.toTypeId());
        if (fromNode != null && toNode != null) {
            double midX = (fromNode.x() + toNode.x()) / 2.0;
            double midY = (fromNode.y() + toNode.y()) / 2.0;
            force[0] += (midX - node.x()) * ATTRACTION * 5;
            force[1] += (midY - node.y()) * ATTRACTION * 5;
        } else {
            LayoutNode anchor = fromNode != null ? fromNode : toNode;
            if (anchor != null) {
                force[0] += (anchor.x() - node.x()) * ATTRACTION * 3;
                force[1] += (anchor.y() - node.y()) * ATTRACTION * 3;
            }
        }
    }

    /** Entity types: gently attracted to partners through shared relationship types. */
    private static void entityThroughRelAttraction(LayoutNode node, Map<String, ? extends LayoutNode> nodeMap,
                                                    double[] force) {
        if (!(node instanceof SchemaType type) || !type.isEntity()) return;
        for (LayoutNode other : nodeMap.values()) {
            if (!(other instanceof SchemaType rel) || !rel.isRelationship()) continue;
            String partnerId = null;
            if (type.id().equals(rel.fromTypeId())) partnerId = rel.toTypeId();
            else if (type.id().equals(rel.toTypeId())) partnerId = rel.fromTypeId();
            if (partnerId != null) {
                LayoutNode partner = nodeMap.get(partnerId);
                if (partner != null) {
                    double dx = partner.x() - node.x();
                    double dy = partner.y() - node.y();
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > 200) {
                        force[0] += dx * ATTRACTION;
                        force[1] += dy * ATTRACTION;
                    }
                }
            }
        }
    }

    /**
     * Left-to-right tree layout. Entity types are placed in columns by depth,
     * relationship diamonds sit between the entities they connect.
     * Self-relations are grouped below their entity as one block.
     */
    public static void treeLayout(Collection<SchemaType> allTypes, double startX, double startY) {
        if (allTypes.isEmpty()) return;

        List<SchemaType> entities = new ArrayList<>();
        List<SchemaType> relationships = new ArrayList<>();
        List<SchemaType> selfRelationships = new ArrayList<>();
        Map<String, SchemaType> byId = new HashMap<>();

        for (SchemaType t : allTypes) {
            byId.put(t.id(), t);
            if (t.isEntity()) {
                entities.add(t);
            } else if (t.isRelationship() && t.fromTypeId() != null && t.fromTypeId().equals(t.toTypeId())) {
                selfRelationships.add(t);
            } else {
                relationships.add(t);
            }
        }

        if (entities.isEmpty()) return;

        // Self-relations grouped by entity id
        Map<String, List<SchemaType>> selfRelsByEntity = new LinkedHashMap<>();
        for (SchemaType sr : selfRelationships) {
            selfRelsByEntity.computeIfAbsent(sr.fromTypeId(), k -> new ArrayList<>()).add(sr);
        }

        // Build adjacency (excluding self-relations)
        Map<String, Set<String>> outgoing = new HashMap<>();
        Set<String> hasIncoming = new HashSet<>();
        for (SchemaType rel : relationships) {
            if (rel.fromTypeId() != null && rel.toTypeId() != null) {
                outgoing.computeIfAbsent(rel.fromTypeId(), k -> new LinkedHashSet<>()).add(rel.toTypeId());
                hasIncoming.add(rel.toTypeId());
            }
        }

        // Find roots: entities with no incoming edges
        List<SchemaType> roots = new ArrayList<>();
        for (SchemaType e : entities) {
            if (!hasIncoming.contains(e.id())) roots.add(e);
        }
        if (roots.isEmpty()) roots.add(entities.get(0));

        // BFS to assign depth levels
        Map<String, Integer> depth = new LinkedHashMap<>();
        Queue<String> queue = new LinkedList<>();
        for (SchemaType root : roots) {
            if (!depth.containsKey(root.id())) {
                depth.put(root.id(), 0);
                queue.add(root.id());
            }
        }
        while (!queue.isEmpty()) {
            String id = queue.poll();
            int d = depth.get(id);
            Set<String> neighbors = outgoing.getOrDefault(id, Set.of());
            for (String nid : neighbors) {
                if (!depth.containsKey(nid)) {
                    depth.put(nid, d + 1);
                    queue.add(nid);
                }
            }
        }
        for (SchemaType e : entities) {
            depth.putIfAbsent(e.id(), 0);
        }

        // Group entities by depth level
        Map<Integer, List<SchemaType>> levels = new TreeMap<>();
        for (SchemaType e : entities) {
            levels.computeIfAbsent(depth.get(e.id()), k -> new ArrayList<>()).add(e);
        }

        // Layout constants
        double entityColWidth = 250;    // width reserved for an entity column
        double relColWidth = 250;       // width reserved for the relation column between
        double rowSpacing = 50;
        double selfRelDiamondH = 90;    // vertical space per self-relation diamond

        // Position entities in columns, with self-relation diamonds split above/below
        int col = 0;
        for (Map.Entry<Integer, List<SchemaType>> entry : levels.entrySet()) {
            List<SchemaType> level = entry.getValue();

            // Compute total height including self-relations (split above/below)
            double totalHeight = 0;
            for (SchemaType e : level) {
                List<SchemaType> selfRels = selfRelsByEntity.getOrDefault(e.id(), List.of());
                int aboveCount = selfRels.size() / 2;
                int belowCount = selfRels.size() - aboveCount;
                double blockH = aboveCount * selfRelDiamondH + entityHeight(e) + belowCount * selfRelDiamondH;
                totalHeight += blockH + rowSpacing;
            }
            totalHeight -= rowSpacing;

            double y = startY - totalHeight / 2.0;
            double x = startX + col * (entityColWidth + relColWidth);
            double selfRelX = x + (entityColWidth - 150) / 2.0;  // center diamond under entity

            for (SchemaType e : level) {
                List<SchemaType> selfRels = selfRelsByEntity.getOrDefault(e.id(), List.of());
                int aboveCount = selfRels.size() / 2;

                // Place above self-relations
                for (int i = 0; i < aboveCount; i++) {
                    SchemaType sr = selfRels.get(i);
                    sr.setErdX(selfRelX);
                    sr.setErdY(y);
                    sr.setErdVx(0);
                    sr.setErdVy(0);
                    y += selfRelDiamondH;
                }

                // Place entity
                e.setErdX(x);
                e.setErdY(y);
                e.setErdVx(0);
                e.setErdVy(0);
                y += entityHeight(e);

                // Place below self-relations
                for (int i = aboveCount; i < selfRels.size(); i++) {
                    SchemaType sr = selfRels.get(i);
                    sr.setErdX(selfRelX);
                    sr.setErdY(y);
                    sr.setErdVx(0);
                    sr.setErdVy(0);
                    y += selfRelDiamondH;
                }

                y += rowSpacing;
            }
            col++;
        }

        // Position cross-entity relationship diamonds between their from/to entities.
        // Group by column pair so multiple rels between the same columns get stacked vertically.
        double diamondH = 80;
        double diamondSpacing = 30;
        Map<String, List<SchemaType>> relsByColumnPair = new LinkedHashMap<>();
        for (SchemaType rel : relationships) {
            SchemaType from = byId.get(rel.fromTypeId());
            SchemaType to = byId.get(rel.toTypeId());
            if (from != null && to != null) {
                int fromDepth = depth.getOrDefault(rel.fromTypeId(), 0);
                int toDepth = depth.getOrDefault(rel.toTypeId(), 0);
                String key = Math.min(fromDepth, toDepth) + ":" + Math.max(fromDepth, toDepth);
                relsByColumnPair.computeIfAbsent(key, k -> new ArrayList<>()).add(rel);
            }
        }

        for (List<SchemaType> group : relsByColumnPair.values()) {
            // Sort diamonds by the Y of the source entity in the lower-depth column
            // so diamond order matches entity order (avoids crossing arrows)
            group.sort((a, b) -> {
                SchemaType aFrom = byId.get(a.fromTypeId());
                SchemaType aTo = byId.get(a.toTypeId());
                SchemaType bFrom = byId.get(b.fromTypeId());
                SchemaType bTo = byId.get(b.toTypeId());
                // Use the average Y of both connected entities
                double aY = ((aFrom != null ? aFrom.erdY() : 0) + (aTo != null ? aTo.erdY() : 0)) / 2.0;
                double bY = ((bFrom != null ? bFrom.erdY() : 0) + (bTo != null ? bTo.erdY() : 0)) / 2.0;
                return Double.compare(aY, bY);
            });

            double totalGroupH = group.size() * diamondH + (group.size() - 1) * diamondSpacing;
            // Find the midpoint between the first from/to pair for x positioning
            SchemaType firstRel = group.get(0);
            SchemaType from = byId.get(firstRel.fromTypeId());
            SchemaType to = byId.get(firstRel.toTypeId());
            double mx = (from.erdX() + entityColWidth + to.erdX()) / 2.0 - 75;
            double groupStartY = startY - totalGroupH / 2.0;

            for (int i = 0; i < group.size(); i++) {
                SchemaType rel = group.get(i);
                rel.setErdX(mx);
                rel.setErdY(groupStartY + i * (diamondH + diamondSpacing));
                rel.setErdVx(0);
                rel.setErdVy(0);
            }
        }

        // Position unconnected relationship diamonds
        for (SchemaType rel : relationships) {
            SchemaType from = byId.get(rel.fromTypeId());
            SchemaType to = byId.get(rel.toTypeId());
            if (from == null || to == null) {
                SchemaType anchor = from != null ? from : to;
                if (anchor != null) {
                    rel.setErdX(anchor.erdX() + entityColWidth + 50);
                    rel.setErdY(anchor.erdY());
                }
                rel.setErdVx(0);
                rel.setErdVy(0);
            }
        }
    }

    private static double entityHeight(SchemaType e) {
        return 28 + e.attributes().size() * 20 + 8;  // ENTITY_HEADER_H + attrs + padding
    }

    /** Compute bounding box [minX, minY, maxX, maxY] of all types. */
    public static double[] getBounds(Collection<SchemaType> allTypes) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (SchemaType t : allTypes) {
            double x = t.erdX();
            double y = t.erdY();
            double w = t.isEntity() ? 180 : 140;
            double h = t.isEntity() ? 60 + t.attributes().size() * 20 : 80;

            minX = Math.min(minX, x - 20);
            minY = Math.min(minY, y - 20);
            maxX = Math.max(maxX, x + w + 20);
            maxY = Math.max(maxY, y + h + 20);
        }

        if (minX == Double.MAX_VALUE) {
            return new double[]{0, 0, 800, 600};
        }
        return new double[]{minX, minY, maxX, maxY};
    }
}
