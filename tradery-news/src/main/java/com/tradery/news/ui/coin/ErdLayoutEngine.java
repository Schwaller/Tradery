package com.tradery.news.ui.coin;

import java.util.*;

/**
 * Force-directed spring layout engine for ERD canvas.
 * All schema types repel each other; relationship types are attracted
 * to the midpoint between their connected entity types.
 */
public class ErdLayoutEngine {

    // Physics constants
    private static final double REPULSION = 80000;
    private static final double ATTRACTION = 0.005;
    private static final double DAMPING = 0.80;
    private static final double CENTER_PULL = 0.0005;
    private static final double MIN_VELOCITY = 0.15;
    private static final double MAX_SPEED = 10.0;
    private static final double MIN_DIST = 1.0;
    private static final double REPULSION_RANGE = 500;

    // Cooling: temperature starts at 1.0, decays each step
    private static double temperature = 1.0;
    private static final double COOLING_RATE = 0.97;
    private static final double MIN_TEMPERATURE = 0.01;

    /**
     * Scatter types randomly around a center point for initial placement.
     * Resets the cooling temperature.
     */
    public static void initPositions(Collection<SchemaType> allTypes, double cx, double cy) {
        temperature = 1.0;
        Random rand = new Random();
        for (SchemaType t : allTypes) {
            if (t.erdX() == 0 && t.erdY() == 0) {
                t.setErdX(cx + (rand.nextDouble() - 0.5) * 600);
                t.setErdY(cy + (rand.nextDouble() - 0.5) * 400);
            }
            t.setErdVx(0);
            t.setErdVy(0);
        }
    }

    /** Reset temperature to let the simulation run hot again (e.g. when dragging). */
    public static void reheat() {
        temperature = Math.max(temperature, 0.5);
    }

    /**
     * Run one step of the force-directed simulation.
     * Returns true if the system is still moving (not settled).
     */
    public static boolean step(Collection<SchemaType> allTypes, double cx, double cy,
                                SchemaType draggedType) {
        if (allTypes.isEmpty()) return false;

        List<SchemaType> types = new ArrayList<>(allTypes);
        Map<String, SchemaType> byId = new HashMap<>();
        for (SchemaType t : types) byId.put(t.id(), t);

        // Cool down
        temperature = Math.max(temperature * COOLING_RATE, MIN_TEMPERATURE);

        boolean anyMoving = false;

        for (SchemaType type : types) {
            if (type.isErdPinned() || type == draggedType) continue;

            double fx = 0, fy = 0;

            // Repulsion from all other types
            for (SchemaType other : types) {
                if (other == type) continue;
                double dx = type.erdX() - other.erdX();
                double dy = type.erdY() - other.erdY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < MIN_DIST) dist = MIN_DIST;
                if (dist < REPULSION_RANGE) {
                    double mult = type.kind().equals(other.kind()) ? 1.5 : 1.0;
                    double force = (REPULSION * mult) / (dist * dist);
                    fx += (dx / dist) * force;
                    fy += (dy / dist) * force;
                }
            }

            // Relationship types: pull toward midpoint of connected entities
            if (type.isRelationship()) {
                SchemaType fromType = byId.get(type.fromTypeId());
                SchemaType toType = byId.get(type.toTypeId());
                if (fromType != null && toType != null) {
                    double midX = (fromType.erdX() + toType.erdX()) / 2.0;
                    double midY = (fromType.erdY() + toType.erdY()) / 2.0;
                    double dx = midX - type.erdX();
                    double dy = midY - type.erdY();
                    fx += dx * ATTRACTION * 5;
                    fy += dy * ATTRACTION * 5;
                } else {
                    // Only one side connected - pull toward that one
                    SchemaType anchor = fromType != null ? fromType : toType;
                    if (anchor != null) {
                        double dx = anchor.erdX() - type.erdX();
                        double dy = anchor.erdY() - type.erdY();
                        fx += dx * ATTRACTION * 3;
                        fy += dy * ATTRACTION * 3;
                    }
                }
            }

            // Entity types that share a relationship are gently attracted
            if (type.isEntity()) {
                for (SchemaType rel : types) {
                    if (!rel.isRelationship()) continue;
                    String partnerId = null;
                    if (type.id().equals(rel.fromTypeId())) partnerId = rel.toTypeId();
                    else if (type.id().equals(rel.toTypeId())) partnerId = rel.fromTypeId();
                    if (partnerId != null) {
                        SchemaType partner = byId.get(partnerId);
                        if (partner != null) {
                            double dx = partner.erdX() - type.erdX();
                            double dy = partner.erdY() - type.erdY();
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            if (dist > 200) {
                                fx += dx * ATTRACTION;
                                fy += dy * ATTRACTION;
                            }
                        }
                    }
                }
            }

            // Gentle pull toward center
            fx += (cx - type.erdX()) * CENTER_PULL;
            fy += (cy - type.erdY()) * CENTER_PULL;

            // Scale forces by temperature
            fx *= temperature;
            fy *= temperature;

            // Apply forces with damping
            double vx = (type.erdVx() + fx) * DAMPING;
            double vy = (type.erdVy() + fy) * DAMPING;

            // Clamp speed (also scaled by temperature)
            double maxSpd = MAX_SPEED * temperature;
            double speed = Math.sqrt(vx * vx + vy * vy);
            if (speed > maxSpd) {
                vx = (vx / speed) * maxSpd;
                vy = (vy / speed) * maxSpd;
            }

            // Stop jittering
            if (Math.abs(vx) < MIN_VELOCITY) vx = 0;
            if (Math.abs(vy) < MIN_VELOCITY) vy = 0;

            type.setErdVx(vx);
            type.setErdVy(vy);

            if (vx != 0 || vy != 0) {
                type.setErdX(type.erdX() + vx);
                type.setErdY(type.erdY() + vy);
                anyMoving = true;
            }
        }

        return anyMoving;
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
