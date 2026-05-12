package sandbox.physics.joints;
import sandbox.core.*;
import sandbox.physics.*;
public class SpringJoint {
    public Ball a;
    public Ball b;
    
    public Vector2D localAnchorA;
    public Vector2D localAnchorB;
    
    public double restLength;
    public double stiffness = 150.0; // Spring constant (k) - Adjust for rigidity
    public double damping = 5.0;     // Friction within the spring to stop infinite bouncing

    public SpringJoint(Ball a, Ball b, Vector2D worldAnchorA, Vector2D worldAnchorB, double restLength) {
        this.a = a;
        this.b = b;
        this.restLength = restLength;
        
        // Convert world anchors to local coordinates (similar to RevoluteJoint)
        Vector2D diffA = worldAnchorA.subtract(a.getPosition());
        Vector2D diffB = worldAnchorB.subtract(b.getPosition());
        this.localAnchorA = diffA.rotate(-a.getAngle());
        this.localAnchorB = diffB.rotate(-b.getAngle());
    }

    public void solve(double dt) {
        // Calculate current world anchor positions
        Vector2D pA = a.getPosition().add(localAnchorA.rotate(a.getAngle()));
        Vector2D pB = b.getPosition().add(localAnchorB.rotate(b.getAngle()));
        
        Vector2D delta = pB.subtract(pA);
        double currentLength = delta.magnitude();
        
        if (currentLength == 0) return;
        
        // Hooke's Law: F = -k * x
        double x = currentLength - restLength;
        double springForce = x * stiffness;
        
        // Damping: opposes the relative velocity along the spring
        Vector2D vA = a.getVelocity(); 
        Vector2D vB = b.getVelocity();
        Vector2D relativeVelocity = vB.subtract(vA);
        
        Vector2D normalizedDelta = delta.multiply(1.0 / currentLength);
        
        // FIXED: Changed .dotProduct() to .dot() to match your Vector2D class
        double dampingForce = relativeVelocity.dot(normalizedDelta) * damping;
        
        // Total force scalar
        double totalForce = springForce + dampingForce;
        Vector2D forceVector = normalizedDelta.multiply(totalForce * dt);
        
        // Apply impulses directly
        if (!a.isStatic) {
            a.setVelocity(a.getVelocity().add(forceVector.multiply(1.0 / a.getMass())));
            a.wakeUp();
        }
        if (!b.isStatic) {
            b.setVelocity(b.getVelocity().subtract(forceVector.multiply(1.0 / b.getMass())));
            b.wakeUp();
        }
    }
}