package sandbox.physics.joints;
import sandbox.core.*;
import sandbox.physics.*;
public class PrismaticJoint {
    public Ball a;
    public Ball b;

    public Vector2D localAnchorA;
    public Vector2D localAnchorB;
    public Vector2D localAxisA;

    public boolean limitEnabled = false;
    public double lowerTranslation = 0;
    public double upperTranslation = 0;

    public boolean motorEnabled = false;
    public double motorSpeed = 0;
    public double maxMotorForce = 100000;

    public PrismaticJoint(Ball a, Ball b, Vector2D worldAnchor, Vector2D worldAxis) {
        this.a = a;
        this.b = b;

        this.localAnchorA = worldAnchor.subtract(a.getPosition()).rotate(-a.getAngle());
        this.localAnchorB = worldAnchor.subtract(b.getPosition()).rotate(-b.getAngle());
        this.localAxisA = worldAxis.rotate(-a.getAngle()).normalize();
    }

    public void solve(double dt) {
        Vector2D pA = a.getPosition().add(localAnchorA.rotate(a.getAngle()));
        Vector2D pB = b.getPosition().add(localAnchorB.rotate(b.getAngle()));
        Vector2D axis = localAxisA.rotate(a.getAngle());
        Vector2D perp = new Vector2D(-axis.y, axis.x);

        Vector2D d = pB.subtract(pA);

        // Linear constraint (Stay on axis)
        double errorPerp = d.dot(perp);
        double invMassA = a.isStatic ? 0 : 1.0 / a.getMass();
        double invMassB = b.isStatic ? 0 : 1.0 / b.getMass();
        double massSum = invMassA + invMassB;

        if (massSum > 0) {
            Vector2D correction = perp.multiply(errorPerp * 0.5 / massSum);
            if (!a.isStatic) a.setPosition(a.getPosition().add(correction.multiply(invMassA)));
            if (!b.isStatic) b.setPosition(b.getPosition().subtract(correction.multiply(invMassB)));
        }

        // Angular constraint (Keep relative rotation fixed)
        double angleError = b.getAngle() - a.getAngle();
        double invInertiaA = a.isStatic ? 0 : 1.0 / a.getInertia();
        double invInertiaB = b.isStatic ? 0 : 1.0 / b.getInertia();
        double inertiaSum = invInertiaA + invInertiaB;

        if (inertiaSum > 0) {
            double impulse = angleError * 0.2 / inertiaSum;
            if (!a.isStatic) a.setAngularVelocity(a.getAngularVelocity() - impulse * invInertiaA);
            if (!b.isStatic) b.setAngularVelocity(b.getAngularVelocity() + impulse * invInertiaB);
        }

        // Translation limits
        double translation = d.dot(axis);
        if (limitEnabled) {
            if (translation < lowerTranslation || translation > upperTranslation) {
                double targetTranslation = (translation < lowerTranslation) ? lowerTranslation : upperTranslation;
                double translationError = targetTranslation - translation;
                if (massSum > 0) {
                    Vector2D correction = axis.multiply(translationError * 0.5 / massSum);
                    if (!a.isStatic) a.setPosition(a.getPosition().subtract(correction.multiply(invMassA)));
                    if (!b.isStatic) b.setPosition(b.getPosition().add(correction.multiply(invMassB)));
                }
            }
        }

        // Motor
        if (motorEnabled) {
            double relativeVelocity = b.getVelocity().dot(axis) - a.getVelocity().dot(axis);
            double velocityError = motorSpeed - relativeVelocity;
            if (massSum > 0) {
                double impulse = velocityError / massSum;
                double maxImpulse = maxMotorForce * dt;
                impulse = Math.max(-maxImpulse, Math.min(impulse, maxImpulse));
                if (!a.isStatic) a.setVelocity(a.getVelocity().subtract(axis.multiply(impulse * invMassA)));
                if (!b.isStatic) b.setVelocity(b.getVelocity().add(axis.multiply(impulse * invMassB)));
            }
        }

        a.wakeUp();
        b.wakeUp();
    }
}
