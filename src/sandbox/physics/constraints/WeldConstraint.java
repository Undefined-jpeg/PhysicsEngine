package sandbox.physics.constraints;
import sandbox.core.*;
import sandbox.physics.*;
public class WeldConstraint {
    public Ball a, b;
    private Vector2D localOffsetBFromA; 
    private double initialAngleDiff;
    public double stiffness = 1.0; 

    public WeldConstraint(Ball a, Ball b) {
        this.a = a;
        this.b = b;
        
        // Calculate B's position in A's local rotated space
        Vector2D diff = b.getPosition().subtract(a.getPosition());
        this.localOffsetBFromA = diff.rotate(-a.getAngle());
        this.initialAngleDiff = b.getAngle() - a.getAngle();
    }

    public void solve() {
        if (a.isDestroyed || b.isDestroyed) return;

        // 1. Solve Rotation
        double currentDiff = b.getAngle() - a.getAngle();
        double angleErr = currentDiff - initialAngleDiff;
        
        // Keep error within -PI to PI
        while (angleErr > Math.PI) angleErr -= Math.PI * 2;
        while (angleErr < -Math.PI) angleErr += Math.PI * 2;
        
        double angleCorrection = angleErr * 0.5 * stiffness;
        if (!a.isStatic) a.setAngle(a.getAngle() + angleCorrection);
        if (!b.isStatic) b.setAngle(b.getAngle() - angleCorrection);

        // 2. Solve Position (Force B to stay exactly at the rigid offset from A)
        Vector2D worldTargetB = a.getPosition().add(localOffsetBFromA.rotate(a.getAngle()));

        Vector2D posErr = worldTargetB.subtract(b.getPosition());
        
        if (!a.isStatic && !b.isStatic) {
            a.setPosition(a.getPosition().subtract(posErr.multiply(0.5 * stiffness)));
            b.setPosition(b.getPosition().add(posErr.multiply(0.5 * stiffness)));
        } else if (!a.isStatic) {
            a.setPosition(a.getPosition().subtract(posErr.multiply(stiffness)));
        } else if (!b.isStatic) {
            b.setPosition(b.getPosition().add(posErr.multiply(stiffness)));
        }
    }
}