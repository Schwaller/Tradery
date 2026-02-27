package com.tradery.layout;

/**
 * A node in a force-directed layout. Provides position, velocity, and pinning.
 */
public interface LayoutNode {
    String id();

    double x();
    double y();
    void setX(double x);
    void setY(double y);

    double vx();
    double vy();
    void setVx(double vx);
    void setVy(double vy);

    boolean isPinned();
}
