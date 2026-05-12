public class SpringConstraint {
    public Ball body;
    public Vector2D target; 
    public double stiffness; 
    public double damping;   
    
    // NEW: We store exactly where you clicked relative to the body's center
    private double localDist;
    private double localAngleOffset;

    public SpringConstraint(Ball body, Vector2D target, double stiffness, double damping) {
        this.body = body;
        this.target = target;
        this.stiffness = stiffness;
        this.damping = damping;
        
        // Calculate the offset from the center of the object to the mouse click
        Vector2D offset = target.subtract(body.getPosition());
        this.localDist = offset.magnitude();
        
        // Save the angle difference so the anchor point rotates with the object
        this.localAngleOffset = Math.atan2(offset.y, offset.x) - body.getAngle();
    }

    public void updateTarget(Vector2D newTarget) {
        this.target = newTarget;
    }

    public void solve(double dt) {
        if (body == null || body.isStatic) return;

        // 1. Find where our grabbed point is right now in the world
        Vector2D currentAnchor = getCurrentAnchor();

        // 2. Calculate the distance from that point to the mouse
        Vector2D delta = target.subtract(currentAnchor);
        
        // 3. Standard spring force (pulling toward mouse)
        Vector2D springForce = delta.multiply(stiffness);
        
        // 4. Calculate the actual velocity at the grabbed point (center velocity + rotational velocity)
        Vector2D r = currentAnchor.subtract(body.getPosition());
        Vector2D pointVelocity = body.getVelocity().add(new Vector2D(-body.getAngularVelocity() * r.y, body.getAngularVelocity() * r.x));
        
        // 5. Apply damping to stop it from jittering
        Vector2D dampForce = pointVelocity.multiply(-damping);
        Vector2D totalForce = springForce.add(dampForce);
        
        // 6. Apply Linear Force (Slide the object)
        Vector2D acceleration = totalForce.multiply(1.0 / body.getMass());
        body.setVelocity(body.getVelocity().add(acceleration.multiply(dt)));
        
        // 7. Apply Angular Force (Spin the object using Torque)
        // Torque is the cross product of the lever arm (r) and the force
        double torque = r.cross(totalForce);
        double angularAcceleration = torque / body.getInertia();
        body.setAngularVelocity(body.getAngularVelocity() + angularAcceleration * dt);
        
        body.wakeUp(); 
    }
    
    // Helper method to get the current world position of the grabbed point
    public Vector2D getCurrentAnchor() {
        double currentAngle = body.getAngle() + localAngleOffset;
        return new Vector2D(
            body.getPosition().x + localDist * Math.cos(currentAngle),
            body.getPosition().y + localDist * Math.sin(currentAngle)
        );
    }
}