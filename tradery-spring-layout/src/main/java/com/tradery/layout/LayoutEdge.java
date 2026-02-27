package com.tradery.layout;

/**
 * An edge connecting two nodes in a force-directed layout.
 */
public interface LayoutEdge {
    String fromId();
    String toId();
}
