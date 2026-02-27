package com.tradery.layout;

/**
 * Configuration for a force-directed layout simulation.
 */
public class LayoutConfig {

    private double repulsion = 5000;
    private double attraction = 0.01;
    private double damping = 0.92;
    private double centerPull = 0.001;
    private double minVelocity = 0.1;
    private double maxSpeed = 8.0;
    private double repulsionRange = 300;
    private double edgeAttractionMinDist = 50;

    // Cooling (disabled by default: temperature = 1.0, coolingRate = 1.0)
    private double coolingRate = 1.0;
    private double minTemperature = 0.01;

    private LayoutConfig() {}

    /** Default config matching CoinGraphPanel physics. */
    public static LayoutConfig coinGraph() {
        return new LayoutConfig();
    }

    /** Config matching ErdLayoutEngine physics. */
    public static LayoutConfig erd() {
        LayoutConfig c = new LayoutConfig();
        c.repulsion = 80000;
        c.attraction = 0.005;
        c.damping = 0.80;
        c.centerPull = 0.0005;
        c.minVelocity = 0.15;
        c.maxSpeed = 10.0;
        c.repulsionRange = 500;
        c.edgeAttractionMinDist = 0;
        c.coolingRate = 0.97;
        c.minTemperature = 0.01;
        return c;
    }

    public double repulsion() { return repulsion; }
    public LayoutConfig repulsion(double v) { this.repulsion = v; return this; }

    public double attraction() { return attraction; }
    public LayoutConfig attraction(double v) { this.attraction = v; return this; }

    public double damping() { return damping; }
    public LayoutConfig damping(double v) { this.damping = v; return this; }

    public double centerPull() { return centerPull; }
    public LayoutConfig centerPull(double v) { this.centerPull = v; return this; }

    public double minVelocity() { return minVelocity; }
    public LayoutConfig minVelocity(double v) { this.minVelocity = v; return this; }

    public double maxSpeed() { return maxSpeed; }
    public LayoutConfig maxSpeed(double v) { this.maxSpeed = v; return this; }

    public double repulsionRange() { return repulsionRange; }
    public LayoutConfig repulsionRange(double v) { this.repulsionRange = v; return this; }

    public double edgeAttractionMinDist() { return edgeAttractionMinDist; }
    public LayoutConfig edgeAttractionMinDist(double v) { this.edgeAttractionMinDist = v; return this; }

    public double coolingRate() { return coolingRate; }
    public LayoutConfig coolingRate(double v) { this.coolingRate = v; return this; }

    public double minTemperature() { return minTemperature; }
    public LayoutConfig minTemperature(double v) { this.minTemperature = v; return this; }

    public boolean hasCooling() { return coolingRate < 1.0; }
}
