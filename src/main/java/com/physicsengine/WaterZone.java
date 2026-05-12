package com.physicsengine;
public class WaterZone {
    public AABB bounds;
    public double density = 1.2;

    public WaterZone(double x1, double y1, double x2, double y2) {
        this.bounds = new AABB(x1, y1, x2, y2);
    }

    public void apply(Ball b, double dt) {
        if (!bounds.intersects(b.getAABB())) return;

        // Buoyancy force = volume_displaced * fluid_density * gravity
        // We approximate volume displaced by the intersection area of AABBs
        AABB bb = b.getAABB();
        double overlapX = Math.min(bounds.maxX, bb.maxX) - Math.max(bounds.minX, bb.minX);
        double overlapY = Math.min(bounds.maxY, bb.maxY) - Math.max(bounds.minY, bb.minY);
        double displacedArea = overlapX * overlapY;

        double buoyancyForce = displacedArea * density * 0.1; // Scale factor
        b.setVelocity(b.getVelocity().add(new Vector2D(0, -buoyancyForce * dt * b.getInvMass())));

        // Viscous drag in water
        b.setVelocity(b.getVelocity().multiply(0.95));
        b.setAngularVelocity(b.getAngularVelocity() * 0.95);
    }
}
