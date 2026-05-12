public class RevoluteJoint {
    public Ball a;
    public Ball b;
    
    // The exact local anchor points relative to the center of each body
    public Vector2D localAnchorA;
    public Vector2D localAnchorB;

    public boolean motorEnabled = false;
    public double motorSpeed = 0;     // Target speed in radians per second
    public double maxTorque = 50000;  // How much force the motor can apply

    public boolean limitEnabled = false;
    public double lowerAngle = 0;
    public double upperAngle = 0;
    public double referenceAngle = 0;

    public RevoluteJoint(Ball a, Ball b, Vector2D worldAnchor) {
        this.a = a;
        this.b = b;
        
        // Convert the world anchor point into local coordinates for Body A
        Vector2D diffA = worldAnchor.subtract(a.getPosition());
        this.localAnchorA = diffA.rotate(-a.getAngle());

        // Convert the world anchor point into local coordinates for Body B
        Vector2D diffB = worldAnchor.subtract(b.getPosition());
        this.localAnchorB = diffB.rotate(-b.getAngle());
    }

    public void solve(double dt) {
        // 1. Calculate where the anchor points are right now in the world
        Vector2D rA = localAnchorA.rotate(a.getAngle());
        Vector2D pA = a.getPosition().add(rA);
        Vector2D rB = localAnchorB.rotate(b.getAngle());
        Vector2D pB = b.getPosition().add(rB);

        // 2. Positional Correction (Snap them together)
        Vector2D error = pB.subtract(pA);
        double invMassA = a.isStatic ? 0 : 1.0 / a.getMass();
        double invMassB = b.isStatic ? 0 : 1.0 / b.getMass();
        double massSum = invMassA + invMassB;

        if (massSum > 0) {
            double stiffness = 0.5;
            Vector2D correction = error.multiply(stiffness / massSum);
            if (!a.isStatic) a.setPosition(a.getPosition().add(correction.multiply(invMassA)));
            if (!b.isStatic) b.setPosition(b.getPosition().subtract(correction.multiply(invMassB)));
        }

        // 3. Angle Limits
        if (limitEnabled) {
            double currentAngle = b.getAngle() - a.getAngle() - referenceAngle;
            // Normalize angle to -PI to PI
            while (currentAngle > Math.PI) currentAngle -= 2 * Math.PI;
            while (currentAngle < -Math.PI) currentAngle += 2 * Math.PI;

            if (currentAngle < lowerAngle || currentAngle > upperAngle) {
                double targetAngle = (currentAngle < lowerAngle) ? lowerAngle : upperAngle;
                double angleError = targetAngle - currentAngle;

                double invInertiaA = a.isStatic ? 0 : 1.0 / a.getInertia();
                double invInertiaB = b.isStatic ? 0 : 1.0 / b.getInertia();
                double inertiaSum = invInertiaA + invInertiaB;

                if (inertiaSum > 0) {
                    double impulse = angleError * 0.2 / inertiaSum; // Soft limit
                    if (!a.isStatic) a.setAngularVelocity(a.getAngularVelocity() - impulse * invInertiaA);
                    if (!b.isStatic) b.setAngularVelocity(b.getAngularVelocity() + impulse * invInertiaB);
                }
            }
        }

        // 4. Motor Logic (Apply torque to reach target speed)
        if (motorEnabled && (!a.isStatic || !b.isStatic)) {
            double relativeAngularVelocity = b.getAngularVelocity() - a.getAngularVelocity();
            double angularError = motorSpeed - relativeAngularVelocity;
            
            double invInertiaA = a.isStatic ? 0 : 1.0 / a.getInertia();
            double invInertiaB = b.isStatic ? 0 : 1.0 / b.getInertia();
            double inertiaSum = invInertiaA + invInertiaB;

            if (inertiaSum > 0) {
                double impulse = angularError / inertiaSum;
                
                // Cap the impulse so the motor doesn't apply infinite force
                double maxImpulse = maxTorque * dt;
                impulse = Math.max(-maxImpulse, Math.min(impulse, maxImpulse));

                if (!a.isStatic) a.setAngularVelocity(a.getAngularVelocity() - impulse * invInertiaA);
                if (!b.isStatic) b.setAngularVelocity(b.getAngularVelocity() + impulse * invInertiaB);
            }
        }
        
        a.wakeUp();
        b.wakeUp();
    }
}