import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

public class EngineContainer {
    private List<Ball> balls = new ArrayList<>();
    private List<WallBody> walls = new ArrayList<>();
    private List<Constraint> constraints = new ArrayList<>();
    private List<WeldConstraint> weldConstraints = new ArrayList<>(); // --- PHASE 11 ---
    private List<RevoluteJoint> revoluteJoints = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    
    private List<Ball> logicSpawnQueue = new ArrayList<>();
    
    private SpatialHash spatialHash = new SpatialHash(100.0);
    private SpringConstraint mouseJoint = null;

    public boolean keyW = false, keyA = false, keyS = false, keyD = false;

    public void addBall(Ball ball) { balls.add(ball); }
    public void addWallSegment(Vector2D p1, Vector2D p2) { walls.add(new WallBody(p1, p2)); }
    public void addConstraint(Constraint c) { constraints.add(c); }
    public void addWeldConstraint(WeldConstraint w) { weldConstraints.add(w); } // --- PHASE 11 ---
    public void addRevoluteJoint(RevoluteJoint rj) { revoluteJoints.add(rj); }
    public void addParticle(Particle p) { particles.add(p); }

    public void enqueueLogicSpawn(Ball ball) { logicSpawnQueue.add(ball); }

    public List<Ball> getBalls() { return balls; }
    public List<WallBody> getWalls() { return walls; }
    public List<Constraint> getConstraints() { return constraints; }
    public List<WeldConstraint> getWeldConstraints() { return weldConstraints; } // --- PHASE 11 ---
    public List<RevoluteJoint> getRevoluteJoints() { return revoluteJoints; }
    public List<Particle> getParticles() { return particles; }
    
    public void setMouseJoint(SpringConstraint joint) { this.mouseJoint = joint; }
    public SpringConstraint getMouseJoint() { return mouseJoint; }

    public boolean isGrounded(Ball player) {
        if (player == null) return false;
        AABB box = player.getAABB();
        AABB footSensor = new AABB(box.minX + 4, box.maxY, box.maxX - 4, box.maxY + 4);
        for (WallBody w : walls) if (footSensor.intersects(w.getAABB())) return true;
        for (Ball b : balls) if (b != player && !b.isDestroyed && footSensor.intersects(b.getAABB())) return true;
        return false;
    }

    public Ball raycast(Vector2D start, Vector2D end, Ball... ignoreBalls) {
        Ball closest = null; double minFraction = 1.0; 
        Vector2D dir = end.subtract(start);
        if (dir.lengthSquared() == 0) return null;

        for (Ball b : balls) {
            if (b.isNoClip) continue; 
            boolean skip = false;
            for (Ball ig : ignoreBalls) if (b == ig) skip = true;
            if (skip) continue;

            AABB lineBox = new AABB(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.max(start.x, end.x), Math.max(start.y, end.y));
            if (!b.getAABB().intersects(lineBox)) continue;

            if (b.shape == Ball.ShapeType.CIRCLE) {
                Vector2D m = start.subtract(b.getPosition());
                double c = m.dot(m) - b.getRadius() * b.getRadius();
                double b_coef = m.dot(dir), a = dir.dot(dir), disc = b_coef * b_coef - a * c;
                if (disc >= 0) {
                    double t = (-b_coef - Math.sqrt(disc)) / a;
                    if (t >= 0 && t <= minFraction) { minFraction = t; closest = b; }
                }
            } else {
                Vector2D[] verts = b.getTransformedVertices();
                for (int i = 0; i < verts.length; i++) {
                    Vector2D p1 = verts[i], p2 = verts[(i + 1) % verts.length];
                    Vector2D v1 = start.subtract(p1), v2 = p2.subtract(p1), v3 = new Vector2D(-dir.y, dir.x);
                    double dot = v2.dot(v3);
                    if (Math.abs(dot) < 0.00001) continue;
                    double t1 = (v2.x * v1.y - v2.y * v1.x) / dot, t2 = (dir.x * v1.y - dir.y * v1.x) / dot;
                    if (t1 >= 0.0 && t1 <= minFraction && t2 >= 0.0 && t2 <= 1.0) { minFraction = t1; closest = b; }
                }
            }
        }
        return closest;
    }

    public void evaluateLogic(double dt) {
        for (Ball b : balls) {
            if (b.logicNode != null) {
                b.logicNode.previousState = b.logicNode.currentState; // Store previous state for edge detection
                b.logicNode.behavior.preEvaluate(b.logicNode, this, dt); // Handle inputs and resets
            }
        }

        // Main evaluation loop for logic gates and other processing nodes
        // The cascade loop is a simple way to ensure propagation, but a topological sort
        // would be more efficient for complex graphs.
        for (int cascade = 0; cascade < 3; cascade++) {
            for (Ball b : balls) {
                if (b.logicNode != null) {
                    b.logicNode.behavior.evaluate(b.logicNode, this, dt);
                }
            }
        }
        
        // Apply outputs based on final logic states
        for (Ball b : balls) {
            if (b.logicNode != null) {
                b.logicNode.behavior.postEvaluate(b.logicNode, this, dt);
            }
        }
    }

    public void step(double dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.update(dt);
            if (p.isDead()) particles.remove(i);
        }

        int subSteps = 8;
        double subDt = dt / subSteps;

        PhysicsCore.beginFrame();

        for (int step = 0; step < subSteps; step++) {
            for (Ball ball : balls) ball.update(subDt);
            if (mouseJoint != null) mouseJoint.solve(subDt);

            for (int i = 0; i < 3; i++) {
                for (Constraint c : constraints) c.solve();
                for (WeldConstraint w : weldConstraints) w.solve(); // --- PHASE 11 ---
                for (RevoluteJoint rj : revoluteJoints) rj.solve(subDt);
            }

            for (Ball ball : balls) {
                if (ball.isSleeping && !ball.isStatic) continue;
                if (ball.isNoClip) continue; 
                AABB ballBox = ball.getAABB(); 
                for (WallBody wall : walls) {
                    if (ballBox.intersects(wall.getAABB())) {
                        PhysicsCore.applyCCDWallCollision(ball, wall);
                        PhysicsCore.resolvePolygonWallCollision(ball, wall);
                    }
                }
            }

            if (!balls.isEmpty()) {
                double totalRadius = 0;
                for (Ball ball : balls) totalRadius += ball.getRadius();
                spatialHash.setCellSize((totalRadius / balls.size()) * 2.0);
            }
            spatialHash.clear();
            for (Ball ball : balls) spatialHash.insert(ball);
            HashSet<Long> checkedPairs = new HashSet<>();

            for (List<Ball> cellBalls : spatialHash.getGrid().values()) {
                for (int i = 0; i < cellBalls.size(); i++) {
                    for (int j = i + 1; j < cellBalls.size(); j++) {
                        Ball a = cellBalls.get(i), b = cellBalls.get(j);
                        if (a.isSleeping && b.isSleeping) continue;
                        if (a.isNoClip || b.isNoClip) continue; 

                        int id1 = System.identityHashCode(a), id2 = System.identityHashCode(b);
                        long pairKey = ((long) Math.min(id1, id2) << 32) | (Math.max(id1, id2) & 0xffffffffL);

                        if (checkedPairs.add(pairKey)) {
                            if (a.getAABB().intersects(b.getAABB())) {
                                if (a.isSleeping) a.wakeUp();
                                if (b.isSleeping) b.wakeUp();

                                Vector2D relVel = a.getVelocity().subtract(b.getVelocity());
                                if (relVel.lengthSquared() > 80000) { 
                                    if (a.logicNode != null && a.logicNode.type == LogicNode.NodeType.INPUT_BUTTON) a.logicNode.isPressedThisFrame = true;
                                    if (b.logicNode != null && b.logicNode.type == LogicNode.NodeType.INPUT_BUTTON) b.logicNode.isPressedThisFrame = true;
                                }

                                if (a.shape == Ball.ShapeType.CIRCLE && b.shape == Ball.ShapeType.CIRCLE) PhysicsCore.resolveBallCollision(a, b);
                                else if (a.shape != Ball.ShapeType.CIRCLE && b.shape != Ball.ShapeType.CIRCLE) PhysicsCore.resolveEntityCollision(a, b);
                                else PhysicsCore.resolvePolygonCircleCollision(a, b);
                            }
                        }
                    }
                }
            }
            evaluateLogic(subDt);
        }
        
        PhysicsCore.endFrame();

        if (!logicSpawnQueue.isEmpty()) {
            balls.addAll(logicSpawnQueue);
            logicSpawnQueue.clear();
        }

        List<Ball> newShards = new ArrayList<>();
        List<Ball> destroyed = new ArrayList<>();
        
        for (Ball b : balls) {
            if (b.isDestroyed) {
                destroyed.add(b);
                newShards.addAll(b.shatter());
                int particleCount = (int)(b.getRadius() * 1.5);
                for (int i = 0; i < particleCount; i++) {
                    Vector2D randomVel = new Vector2D((Math.random() - 0.5) * 800, (Math.random() - 0.5) * 800);
                    java.awt.Color pColor = new java.awt.Color(200, 220, 255, 200); 
                    particles.add(new Particle(b.getPosition(), randomVel, 0.5 + Math.random(), 2 + Math.random() * 4, pColor));
                }
            }
        }

        if (!destroyed.isEmpty()) {
            balls.removeAll(destroyed);
            constraints.removeIf(c -> c.a.isDestroyed || c.b.isDestroyed);
            weldConstraints.removeIf(w -> w.a.isDestroyed || w.b.isDestroyed); // --- PHASE 11 ---
            revoluteJoints.removeIf(rj -> rj.a.isDestroyed || rj.b.isDestroyed);
            if (mouseJoint != null && mouseJoint.body.isDestroyed) mouseJoint = null;
            
            for (Ball d : destroyed) {
                if (d.logicNode != null) {
                    for (LogicNode in : d.logicNode.connectedInputs) in.connectedOutputs.remove(d.logicNode);
                    for (LogicNode out : d.logicNode.connectedOutputs) out.connectedInputs.remove(d.logicNode);
                }
            }
            balls.addAll(newShards);
        }
    }
}