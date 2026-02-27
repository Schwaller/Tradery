package com.tradery.layout;

/**
 * Optional modifier for repulsion force between two nodes.
 * Return a multiplier (e.g., 1.5 for stronger repulsion between same-kind nodes).
 */
@FunctionalInterface
public interface RepulsionModifier {
    double multiplier(LayoutNode a, LayoutNode b);
}
