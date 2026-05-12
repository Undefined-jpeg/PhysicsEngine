package com.physicsengine;
/**
 * A distance constraint that can connect:
 *   Ball  ↔ Ball
 *   Ball  ↔ WallBody (at wall centre)
 *   WallBody ↔ WallBody
 *
 * Either endpoint can be null-ball (meaning it is anchored to a WallBody centre),
 * or we hold them polymorphically using a small "Node" interface wrapper.
 */
public class WallConstraint {

    // ── Node wrapper so we can treat Ball and WallBody uniformly ─────────────

    public interface Node {
        Vector2D getPosition();
        void     setPosition(Vector2D p);
        Vector2D getVelocity();
        void     setVelocity(Vector2D v);
        double   getMass();
        boolean  isAnchored();   // true → infinite mass, immovable
        void     wakeUp();
    }

    public static Node wrap(Ball ball) {
        return new Node() {
            public Vector2D getPosition()          { return ball.getPosition(); }
            public void     setPosition(Vector2D p){ ball.setPosition(p); }
            public Vector2D getVelocity()          { return ball.getVelocity(); }
            public void     setVelocity(Vector2D v){ ball.setVelocity(v); }
            public double   getMass()              { return ball.getMass(); }
            public boolean  isAnchored()           { return ball.isStatic; }
            public void     wakeUp()               { ball.wakeUp(); }
        };
    }

    public static Node wrap(WallBody wall) {
        return new Node() {
            public Vector2D getPosition()          { return wall.getPosition(); }
            public void     setPosition(Vector2D p){ wall.setPosition(p); }
            public Vector2D getVelocity()          { return wall.getVelocity(); }
            public void     setVelocity(Vector2D v){ wall.setVelocity(v); }
            public double   getMass()              { return wall.getMass(); }
            public boolean  isAnchored()           { return wall.anchored; }
            public void     wakeUp()               { wall.wakeUp(); }
        };
    }

    // ── Constraint data ───────────────────────────────────────────────────────

    public Node   nodeA, nodeB;
    public double targetLength;
    public double stiffness;
    public double damping;

    public WallConstraint(Node a, Node b, double targetLength,
                          double stiffness, double damping) {
        this.nodeA        = a;
        this.nodeB        = b;
        this.targetLength = targetLength;
        this.stiffness    = stiffness;
        this.damping      = damping;
    }

    public void solve() {
        if (nodeA.isAnchored() && nodeB.isAnchored()) return;

        Vector2D delta = nodeB.getPosition().subtract(nodeA.getPosition());
        double currentDist = delta.magnitude();
        if (currentDist == 0) return;

        double   error  = currentDist - targetLength;
        Vector2D normal = delta.multiply(1.0 / currentDist);

        double invMassA = nodeA.isAnchored() ? 0 : 1.0 / nodeA.getMass();
        double invMassB = nodeB.isAnchored() ? 0 : 1.0 / nodeB.getMass();
        double massSum  = invMassA + invMassB;
        if (massSum == 0) return;

        double   corrAmt = error * stiffness;
        Vector2D corrVec = normal.multiply(corrAmt);

        if (!nodeA.isAnchored())
            nodeA.setPosition(nodeA.getPosition().add(corrVec.multiply(invMassA / massSum)));
        if (!nodeB.isAnchored())
            nodeB.setPosition(nodeB.getPosition().subtract(corrVec.multiply(invMassB / massSum)));

        Vector2D relVel      = nodeB.getVelocity().subtract(nodeA.getVelocity());
        double   velAlongN   = relVel.dot(normal);
        double   dampForce   = velAlongN * damping;
        Vector2D dampImpulse = normal.multiply(dampForce);

        if (!nodeA.isAnchored())
            nodeA.setVelocity(nodeA.getVelocity().add(dampImpulse.multiply(invMassA / massSum)));
        if (!nodeB.isAnchored())
            nodeB.setVelocity(nodeB.getVelocity().subtract(dampImpulse.multiply(invMassB / massSum)));

        if (Math.abs(error) > 1.0) { nodeA.wakeUp(); nodeB.wakeUp(); }
    }
}