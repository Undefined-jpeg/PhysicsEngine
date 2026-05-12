package sandbox.physics;
import sandbox.core.*;
import sandbox.physics.constraints.*;
import sandbox.physics.joints.*;
import sandbox.logic.*;
import sandbox.system.*;
import java.util.ArrayList;
import java.util.List;

public class Ball {
    public enum ShapeType { CIRCLE, CUBE, TRIANGLE, HEXAGON }

    private static int globalIdCounter = 0;
    public int id = globalIdCounter++;
    
    private Vector2D position;
    private Vector2D previousPosition;
    private Vector2D velocity;
    private double radius;
    private double mass, invMass;
    
    private double angle = 0;
    private double angularVelocity = 0;
    private double momentOfInertia, invI;
    
    public Material material; 
    public ShapeType shape = ShapeType.CIRCLE;
    private Vector2D[] localVertices = null;
    
    public double restitution = 0.5;
    public double friction = 0.2;
    
    public boolean isStatic = false;
    public boolean isSleeping = false;
    public boolean isDestroyed = false;
    public boolean isNoClip = false;
    
    public boolean hasMotor = false;
    public double motorSpeed = 0.0;
    
    private double sleepTimer = 0;
    private boolean lockRotation = false;
    
    public LogicNode logicNode = null;
    public Vector2D originalPosition = null;

    public Ball(double x, double y, double radius, Material material) {
        this.position = new Vector2D(x, y);
        this.previousPosition = new Vector2D(x, y);
        this.velocity = new Vector2D(0, 0);
        this.radius = radius;
        this.material = material; 
        
        if (this.material == null) this.material = Material.WOOD; 
        
        double area = Math.PI * radius * radius;
        this.mass = area * this.material.density;
        this.invMass = 1.0 / this.mass;
        
        this.momentOfInertia = 0.5 * this.mass * radius * radius;
        this.invI = 1.0 / this.momentOfInertia;
    }

    public Material getMaterial() { return this.material; }
    public Vector2D getPreviousPosition() { return previousPosition; }
    public double getInertia() { return momentOfInertia; }
    public double getRestitution() { return restitution; }
    public double getFriction() { return friction; }

    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
        if (isStatic) {
            this.invMass = 0;
            this.invI = 0;
        } else {
            this.invMass = 1.0 / this.mass;
            this.invI = lockRotation ? 0 : 1.0 / this.momentOfInertia;
        }
    }

    public void setLockRotation(boolean lock) {
        this.lockRotation = lock;
        if (lock) this.invI = 0;
        else if (!isStatic) this.invI = 1.0 / this.momentOfInertia;
    }

    public static Vector2D windVector = new Vector2D(0, 0);

    public void update(double dt) {
        if (isStatic) {
            if (hasMotor) angle += motorSpeed * dt;
            return;
        }
        
        if (isSleeping) return;

        this.previousPosition = new Vector2D(this.position.x, this.position.y);

        if (hasMotor) angularVelocity = motorSpeed;

        // Gravity
        velocity = velocity.add(new Vector2D(0, PhysicsCore.GRAVITY * dt));

        // Aerodynamics (Lift and Drag)
        applyAerodynamics(dt);

        velocity = velocity.multiply(0.9995);
        angularVelocity *= 0.995;

        position = position.add(velocity.multiply(dt));
        angle += angularVelocity * dt;

        if (velocity.lengthSquared() < 2.0 && Math.abs(angularVelocity) < 0.1) {
            sleepTimer += dt;
            if (sleepTimer > 0.5) isSleeping = true;
        } else {
            sleepTimer = 0;
        }
    }

    public void wakeUp() {
        isSleeping = false;
        sleepTimer = 0;
    }

    public void applyImpulse(Vector2D impulse, Vector2D contactVector) {
        if (isStatic) return;
        wakeUp();
        velocity = velocity.add(impulse.multiply(invMass));
        angularVelocity += invI * (contactVector.x * impulse.y - contactVector.y * impulse.x);
    }

    public Vector2D getPosition() { return position; }
    public void setPosition(Vector2D pos) { this.position = pos; wakeUp(); }
    public Vector2D getVelocity() { return velocity; }
    public void setVelocity(Vector2D vel) { this.velocity = vel; wakeUp(); }
    public double getAngle() { return angle; }
    public void setAngle(double angle) { this.angle = angle; wakeUp(); }
    public void setAngularVelocity(double w) { this.angularVelocity = w; wakeUp(); }
    public double getAngularVelocity() { return angularVelocity; }
    
    public double getRadius() { return radius; }
    public void setRadius(double r) {
        this.radius = r;
        double area = Math.PI * radius * radius;
        this.mass = area * this.material.density;
        if (!isStatic) {
            this.invMass = 1.0 / this.mass;
            this.momentOfInertia = 0.5 * this.mass * radius * radius;
            this.invI = lockRotation ? 0 : 1.0 / this.momentOfInertia;
        }
        if (shape != ShapeType.CIRCLE && localVertices == null) {
            setShape(shape); 
        }
        wakeUp();
    }
    
    public double getMass() { return isStatic ? Double.POSITIVE_INFINITY : mass; }
    public double getInvMass() { return invMass; }
    public double getInvI() { return invI; }

    public void setShape(ShapeType newShape) {
        this.shape = newShape;
        if (newShape == ShapeType.CIRCLE) {
            this.localVertices = null;
            return;
        }
        
        int sides = 4;
        if (newShape == ShapeType.TRIANGLE) sides = 3;
        else if (newShape == ShapeType.HEXAGON) sides = 6;
        
        localVertices = new Vector2D[sides];
        double angleOffset = (newShape == ShapeType.CUBE) ? Math.PI / 4 : 0; 
        if (newShape == ShapeType.TRIANGLE) angleOffset = -Math.PI / 2; 
        
        for (int i = 0; i < sides; i++) {
            double theta = angleOffset + (i * 2 * Math.PI / sides);
            localVertices[i] = new Vector2D(Math.cos(theta) * radius, Math.sin(theta) * radius);
        }
    }
    
    public void setCustomVertices(Vector2D[] verts) {
        this.shape = ShapeType.CUBE; 
        this.localVertices = verts;
    }

    public Vector2D[] getTransformedVertices() {
        if (shape == ShapeType.CIRCLE || localVertices == null) return null;
        Vector2D[] transformed = new Vector2D[localVertices.length];
        for (int i = 0; i < localVertices.length; i++) {
            transformed[i] = position.add(localVertices[i].rotate(angle));
        }
        return transformed;
    }

    private void applyAerodynamics(double dt) {
        Vector2D relVel = velocity.subtract(windVector);
        double speedSq = relVel.lengthSquared();
        if (speedSq < 0.001) return;

        double speed = Math.sqrt(speedSq);
        Vector2D dir = relVel.multiply(1.0 / speed);

        // Simple area-based drag
        double area = (shape == ShapeType.CIRCLE) ? radius * 2 : radius * 2.5;
        double dragCoeff = 0.47; // Sphere-like
        double dragMag = 0.5 * 1.225 * speedSq * dragCoeff * area * 0.0001; // Scale factor for engine

        velocity = velocity.subtract(dir.multiply(dragMag * dt * invMass));

        // Lift (very basic approximation for non-circles)
        if (shape != ShapeType.CIRCLE) {
            Vector2D liftDir = new Vector2D(-dir.y, dir.x);
            double liftCoeff = Math.sin(angle * 2); // Angle of attack approx
            double liftMag = 0.5 * 1.225 * speedSq * liftCoeff * area * 0.0001;
            velocity = velocity.add(liftDir.multiply(liftMag * dt * invMass));
        }
    }

    public AABB getAABB() {
        if (shape == ShapeType.CIRCLE || localVertices == null) {
            return new AABB(position.x - radius, position.y - radius, position.x + radius, position.y + radius);
        }
        Vector2D[] verts = getTransformedVertices();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vector2D v : verts) {
            if (v.x < minX) minX = v.x;
            if (v.y < minY) minY = v.y;
            if (v.x > maxX) maxX = v.x;
            if (v.y > maxY) maxY = v.y;
        }
        return new AABB(minX, minY, maxX, maxY);
    }

    public List<Ball> shatter() {
        List<Ball> shards = new ArrayList<>();
        if (material == Material.RUBBER || radius < 15) return shards;
        
        int pieces = (int)(Math.random() * 3) + 3;
        double newRadius = radius / 2.0;
        
        for (int i = 0; i < pieces; i++) {
            double offsetX = (Math.random() - 0.5) * radius;
            double offsetY = (Math.random() - 0.5) * radius;
            Ball shard = new Ball(position.x + offsetX, position.y + offsetY, newRadius, material);
            shard.setVelocity(velocity.add(new Vector2D(offsetX * 5, offsetY * 5)));
            
            if (shape != ShapeType.CIRCLE) {
                ShapeType[] st = {ShapeType.TRIANGLE, ShapeType.CUBE};
                shard.setShape(st[(int)(Math.random() * st.length)]);
            }
            shards.add(shard);
        }
        return shards;
    }
}