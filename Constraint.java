public class Constraint {
    public Ball a;
    public Ball b;
    public double targetLength;
    public double stiffness; 
    public double damping;   

    public Constraint(Ball a, Ball b, double targetLength, double stiffness, double damping) {
        this.a = a;
        this.b = b;
        this.targetLength = targetLength;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void solve() {
        if (a.isStatic && b.isStatic) return;

        Vector2D delta = b.getPosition().subtract(a.getPosition());
        double currentDist = delta.magnitude();
        if (currentDist == 0) return; 

        double error = currentDist - targetLength;
        Vector2D normal = delta.multiply(1.0 / currentDist);

        double invMassA = a.isStatic ? 0 : 1.0 / a.getMass();
        double invMassB = b.isStatic ? 0 : 1.0 / b.getMass();
        double massSum = invMassA + invMassB;
        if (massSum == 0) return;

        double correctionAmount = error * stiffness;
        Vector2D correctionVector = normal.multiply(correctionAmount);

        if (!a.isStatic) a.setPosition(a.getPosition().add(correctionVector.multiply(invMassA / massSum)));
        if (!b.isStatic) b.setPosition(b.getPosition().subtract(correctionVector.multiply(invMassB / massSum)));

        Vector2D relativeVelocity = b.getVelocity().subtract(a.getVelocity());
        double velAlongNormal = relativeVelocity.dot(normal);
        
        double dampForce = velAlongNormal * damping;
        Vector2D dampImpulse = normal.multiply(dampForce);

        if (!a.isStatic) a.setVelocity(a.getVelocity().add(dampImpulse.multiply(invMassA / massSum)));
        if (!b.isStatic) b.setVelocity(b.getVelocity().subtract(dampImpulse.multiply(invMassB / massSum)));

        if (Math.abs(error) > 1.0) { a.wakeUp(); b.wakeUp(); }
    }
}