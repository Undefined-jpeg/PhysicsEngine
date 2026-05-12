package com.physicsengine;
public class WallBody {
    private static int idCounter = 0;
    private final int id = idCounter++;

    // World-space endpoints
    public Vector2D p1, p2;
    private Vector2D position;
    private Vector2D velocity;
    private double angularVelocity;
    private double angle;

    private Vector2D localA;
    private Vector2D localB;

    public double mass;
    public double inertia;
    public double restitution;
    public double friction;
    public boolean anchored;
    public boolean isSleeping = false;

    private int sleepCounter = 0;
    private static final double SLEEP_TOL = 2.0;
    private static final int SLEEP_FRAMES = 60;

    public WallBody(Vector2D p1, Vector2D p2, double mass, double restitution, double friction, boolean anchored) {
        this.p1 = p1;
        this.p2 = p2;
        this.mass = mass;
        this.restitution = restitution;
        this.friction = friction;
        this.anchored = anchored;

        this.position = new Vector2D((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0);
        this.velocity = new Vector2D(0, 0);
        this.angularVelocity = 0;
        this.angle = 0;

        this.localA = p1.subtract(this.position);
        this.localB = p2.subtract(this.position);
        double len = p1.distanceTo(p2);
        this.inertia = (1.0 / 12.0) * mass * len * len;
    }

    public WallBody(Vector2D p1, Vector2D p2) {
        this(p1, p2, 5.0, 0.2, 0.4, true);
    }

    public void update(double dt) {
        if (anchored) return;

        // Optimized: using lengthSquared instead of magnitude
        double motionSq = velocity.lengthSquared() + (angularVelocity * angularVelocity);
        if (motionSq < SLEEP_TOL * SLEEP_TOL) {
            if (++sleepCounter >= SLEEP_FRAMES) {
                isSleeping = true;
                velocity = new Vector2D(0, 0);
                angularVelocity = 0;
            }
        } else {
            wakeUp();
        }

        if (!isSleeping) {
            // FIXED: Creating a new Vector2D instead of directly mutating velocity.y
            velocity = new Vector2D(velocity.x, velocity.y + PhysicsCore.GRAVITY * dt);
            
            position = position.add(velocity.multiply(dt));
            angle += angularVelocity * dt;
            angularVelocity *= 0.995;
        }

        rebuildEndpoints();
    }

    private void rebuildEndpoints() {
        // Use the new Vector2D.rotate method
        p1 = position.add(localA.rotate(angle));
        p2 = position.add(localB.rotate(angle));
    }

    public void wakeUp() { isSleeping = false; sleepCounter = 0; }

    public int getId() { return id; }
    public Vector2D getPosition() { return position; }
    public void setPosition(Vector2D p) {
        Vector2D delta = p.subtract(this.position);
        this.position = p;
        this.p1 = this.p1.add(delta);
        this.p2 = this.p2.add(delta);
        wakeUp();
    }

    public Vector2D getVelocity() { return velocity; }
    public void setVelocity(Vector2D v) { this.velocity = v; wakeUp(); }
    
    public double getAngularVelocity() { return angularVelocity; }
    public void setAngularVelocity(double av) { this.angularVelocity = av; wakeUp(); }
    
    public double getMass() { return mass; }
    public double getInertia() { return inertia; }
    public double getRestitution() { return restitution; }
    public double getFriction() { return friction; }
    public double getAngle() { return angle; }

    public void recalcFromEndpoints() {
        this.position = new Vector2D((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0);
        this.localA = p1.subtract(this.position);
        this.localB = p2.subtract(this.position);
        double len = p1.distanceTo(p2);
        this.inertia = (1.0 / 12.0) * mass * len * len;
    }

    public Vector2D closestPoint(Vector2D worldPt) {
        Vector2D ab = p2.subtract(p1);
        double lenSq = ab.lengthSquared(); // Optimized
        if (lenSq == 0) return p1;
        
        double t = Math.max(0, Math.min(1, worldPt.subtract(p1).dot(ab) / lenSq));
        return p1.add(ab.multiply(t));
    }

    public double distanceTo(Vector2D worldPt) {
        return worldPt.distanceTo(closestPoint(worldPt));
    }

    public AABB getAABB() {
        double minX = Math.min(p1.x, p2.x);
        double maxX = Math.max(p1.x, p2.x);
        double minY = Math.min(p1.y, p2.y);
        double maxY = Math.max(p1.y, p2.y);
        
        return new AABB(minX - 2, minY - 2, maxX + 2, maxY + 2);
    }
}