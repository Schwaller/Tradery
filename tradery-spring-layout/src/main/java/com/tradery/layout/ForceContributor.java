package com.tradery.layout;

import java.util.Map;

/**
 * Additional force contributor applied per-node after standard repulsion/attraction/center.
 * Write accumulated force into force[0] (x) and force[1] (y).
 */
@FunctionalInterface
public interface ForceContributor {
    void apply(LayoutNode node, Map<String, ? extends LayoutNode> nodeMap, double[] force);
}
