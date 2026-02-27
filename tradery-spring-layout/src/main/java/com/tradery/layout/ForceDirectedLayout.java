package com.tradery.layout;

import java.util.*;

/**
 * Generic force-directed layout engine.
 * Applies repulsion, edge attraction, center pull, damping, and anti-jitter.
 * Optionally supports cooling (temperature decay) and pluggable force modifiers.
 */
public class ForceDirectedLayout {

    private final LayoutConfig config;
    private final RepulsionModifier repulsionModifier;
    private final List<ForceContributor> forceContributors;
    private double temperature = 1.0;

    public ForceDirectedLayout(LayoutConfig config) {
        this(config, null, List.of());
    }

    public ForceDirectedLayout(LayoutConfig config, RepulsionModifier repulsionModifier,
                                List<ForceContributor> forceContributors) {
        this.config = config;
        this.repulsionModifier = repulsionModifier;
        this.forceContributors = forceContributors;
    }

    /**
     * Scatter nodes randomly around a center point. Resets velocity and temperature.
     */
    public void initPositions(Collection<? extends LayoutNode> nodes, double cx, double cy,
                               double spreadX, double spreadY) {
        temperature = 1.0;
        Random rand = new Random();
        for (LayoutNode node : nodes) {
            if (node.x() == 0 && node.y() == 0) {
                node.setX(cx + (rand.nextDouble() - 0.5) * spreadX);
                node.setY(cy + (rand.nextDouble() - 0.5) * spreadY);
            }
            node.setVx(0);
            node.setVy(0);
        }
    }

    /** Reset temperature to let the simulation run hot again. */
    public void reheat() {
        temperature = Math.max(temperature, 0.5);
    }

    /**
     * Run one step of the force-directed simulation.
     * @param nodes all layout nodes
     * @param edges edges connecting nodes
     * @param cx center X for center pull
     * @param cy center Y for center pull
     * @param excludedNode node to skip (e.g., currently dragged), or null
     * @return true if any node is still moving
     */
    public boolean step(Collection<? extends LayoutNode> nodes,
                         Collection<? extends LayoutEdge> edges,
                         double cx, double cy,
                         LayoutNode excludedNode) {
        if (nodes.isEmpty()) return false;

        List<? extends LayoutNode> nodeList = nodes instanceof List
            ? (List<? extends LayoutNode>) nodes : new ArrayList<>(nodes);
        Map<String, LayoutNode> nodeMap = new HashMap<>();
        for (LayoutNode n : nodeList) nodeMap.put(n.id(), n);

        // Cool down
        if (config.hasCooling()) {
            temperature = Math.max(temperature * config.coolingRate(), config.minTemperature());
        }

        boolean anyMoving = false;

        for (LayoutNode node : nodeList) {
            if (node.isPinned() || node == excludedNode) continue;

            double fx = 0, fy = 0;

            // Repulsion from all other nodes
            for (LayoutNode other : nodeList) {
                if (other == node) continue;
                double dx = node.x() - other.x();
                double dy = node.y() - other.y();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;
                if (dist < config.repulsionRange()) {
                    double mult = repulsionModifier != null
                        ? repulsionModifier.multiplier(node, other) : 1.0;
                    double force = (config.repulsion() * mult) / (dist * dist);
                    fx += (dx / dist) * force;
                    fy += (dy / dist) * force;
                }
            }

            // Attraction along edges
            for (LayoutEdge edge : edges) {
                LayoutNode other = null;
                if (edge.fromId().equals(node.id())) {
                    other = nodeMap.get(edge.toId());
                } else if (edge.toId().equals(node.id())) {
                    other = nodeMap.get(edge.fromId());
                }
                if (other != null) {
                    double dx = other.x() - node.x();
                    double dy = other.y() - node.y();
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > config.edgeAttractionMinDist()) {
                        fx += dx * config.attraction();
                        fy += dy * config.attraction();
                    }
                }
            }

            // Custom force contributors
            double[] extra = {0, 0};
            for (ForceContributor fc : forceContributors) {
                fc.apply(node, nodeMap, extra);
            }
            fx += extra[0];
            fy += extra[1];

            // Center pull
            fx += (cx - node.x()) * config.centerPull();
            fy += (cy - node.y()) * config.centerPull();

            // Scale forces by temperature (if cooling enabled)
            if (config.hasCooling()) {
                fx *= temperature;
                fy *= temperature;
            }

            // Apply forces with damping
            double vx = (node.vx() + fx) * config.damping();
            double vy = (node.vy() + fy) * config.damping();

            // Clamp speed
            double maxSpd = config.hasCooling() ? config.maxSpeed() * temperature : config.maxSpeed();
            double speed = Math.sqrt(vx * vx + vy * vy);
            if (speed > maxSpd) {
                vx = (vx / speed) * maxSpd;
                vy = (vy / speed) * maxSpd;
            }

            // Anti-jitter
            if (Math.abs(vx) < config.minVelocity()) vx = 0;
            if (Math.abs(vy) < config.minVelocity()) vy = 0;

            node.setVx(vx);
            node.setVy(vy);

            if (vx != 0 || vy != 0) {
                node.setX(node.x() + vx);
                node.setY(node.y() + vy);
                anyMoving = true;
            }
        }

        return anyMoving;
    }
}
