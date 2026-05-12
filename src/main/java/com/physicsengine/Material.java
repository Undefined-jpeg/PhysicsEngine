package com.physicsengine;
public enum Material {
    WOOD(0.004, 0.2, 0.6, 15000),             // Can break under extreme pressure
    STEEL(0.025, 0.1, 0.4, Double.MAX_VALUE), // Unbreakable
    RUBBER(0.006, 0.8, 0.85, Double.MAX_VALUE),
    BOUNCY_BALL(0.002, 0.95, 0.3, Double.MAX_VALUE),
    GLASS(0.005, 0.05, 0.1, 1500);            // Very brittle! Shatters easily.

    public final double density;
    public final double restitution;
    public final double friction;
    public final double breakThreshold;       // NEW: Structural integrity

    Material(double density, double restitution, double friction, double breakThreshold) {
        this.density = density;
        this.restitution = restitution;
        this.friction = friction;
        this.breakThreshold = breakThreshold;
    }
}