package sandbox.physics;
import sandbox.core.*;
import sandbox.physics.constraints.*;
import sandbox.physics.joints.*;
import sandbox.logic.*;
import sandbox.system.*;
// Particle.java
import java.awt.Color;

public class Particle {
    public Vector2D position;
    public Vector2D velocity;
    public double life;
    public double maxLife;
    public double size;
    public Color color;

    public Particle(Vector2D position, Vector2D velocity, double life, double size, Color color) {
        this.position = position;
        this.velocity = velocity;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.color = color;
    }

    public void update(double dt) {
        velocity = new Vector2D(velocity.x, velocity.y + (PhysicsCore.GRAVITY * 0.5) * dt);
        velocity = velocity.multiply(0.98); 
        position = position.add(velocity.multiply(dt));
        life -= dt;
    }

    public boolean isDead() {
        return life <= 0;
    }
}