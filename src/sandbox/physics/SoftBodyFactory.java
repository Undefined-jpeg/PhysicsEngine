package sandbox.physics;
import sandbox.core.*;
import sandbox.physics.constraints.*;
import sandbox.physics.joints.*;
import sandbox.logic.*;
import sandbox.system.*;
import java.util.ArrayList;
import java.util.List;

public class SoftBodyFactory {
    public static List<Ball> createSoftCircle(EngineContainer engine, double x, double y, double radius, int points, Material mat) {
        List<Ball> balls = new ArrayList<>();
        double angleStep = 2.0 * Math.PI / points;

        Ball center = new Ball(x, y, radius * 0.2, mat);
        engine.addBall(center);
        balls.add(center);

        for (int i = 0; i < points; i++) {
            double angle = i * angleStep;
            Ball b = new Ball(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, radius * 0.2, mat);
            engine.addBall(b);
            balls.add(b);

            // Connect to center
            engine.addConstraint(new Constraint(center, b, radius, 0.5, 0.1));

            // Connect to neighbors
            if (i > 0) {
                double neighborAngle = (i - 1) * angleStep;
                double dist = new Vector2D(Math.cos(angle) * radius, Math.sin(angle) * radius)
                              .distanceTo(new Vector2D(Math.cos(neighborAngle) * radius, Math.sin(neighborAngle) * radius));
                engine.addConstraint(new Constraint(b, balls.get(i), dist, 0.5, 0.1));
            }
        }
        // Close the loop
        double dist = new Vector2D(Math.cos(0) * radius, Math.sin(0) * radius)
                      .distanceTo(new Vector2D(Math.cos((points - 1) * angleStep) * radius, Math.sin((points - 1) * angleStep) * radius));
        engine.addConstraint(new Constraint(balls.get(1), balls.get(points), dist, 0.5, 0.1));

        return balls;
    }
}
