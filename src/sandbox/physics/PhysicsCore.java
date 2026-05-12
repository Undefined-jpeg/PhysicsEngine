package sandbox.physics;
import sandbox.core.*;
import sandbox.physics.constraints.*;
import sandbox.physics.joints.*;
import sandbox.logic.*;
import sandbox.system.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class PhysicsCore {
    public static AudioEngine audioEngine;
    public static final double GRAVITY = 9.81 * 100;
    public static final double ROLL_THRESHOLD = 40.0;

    // --- PHASE 7: WARM STARTING CACHES ---
    private static Map<Long, Double> normalImpulseCache = new HashMap<>();
    private static Map<Long, Double> frictionImpulseCache = new HashMap<>();
    public static HashSet<Long> activeContactsThisFrame = new HashSet<>();

    // Called once at the very beginning of the physics step
    public static void beginFrame() {
        activeContactsThisFrame.clear();
    }

    // Called once at the very end to clean up memory (forgets objects that aren't touching anymore)
    public static void endFrame() {
        normalImpulseCache.keySet().retainAll(activeContactsThisFrame);
        frictionImpulseCache.keySet().retainAll(activeContactsThisFrame);
    }

    // --- CONTINUOUS COLLISION DETECTION (CCD) ---
    public static void applyCCDWallCollision(Ball entity, WallBody wall) {
        if (entity.isStatic || entity.getPreviousPosition() == null) return;

        Vector2D prev = entity.getPreviousPosition();
        Vector2D curr = entity.getPosition();
        Vector2D dir = curr.subtract(prev);
        Vector2D wallDir = wall.p2.subtract(wall.p1);

        double denom = dir.cross(wallDir);
        if (denom == 0) return;

        Vector2D diff = prev.subtract(wall.p1);
        double t1 = diff.cross(wallDir) / denom;
        double t2 = diff.cross(dir) / denom;

        if (t1 >= 0 && t1 <= 1 && t2 >= 0 && t2 <= 1) {
            Vector2D intersectionPoint = prev.add(dir.multiply(t1));
            Vector2D wallNormal = new Vector2D(-wallDir.y, wallDir.x).normalize();
            
            if (wallNormal.dot(dir) > 0) wallNormal = wallNormal.multiply(-1);
            
            double buffer = (entity.shape == Ball.ShapeType.CIRCLE) ? entity.getRadius() : entity.getRadius() * 0.8;
            entity.setPosition(intersectionPoint.add(wallNormal.multiply(buffer)));
        }
    }

    public static void resolvePolygonWallCollision(Ball entity, WallBody wall) {
        if (entity.isStatic) return;

        Vector2D wallVector = wall.p2.subtract(wall.p1);
        double wallLenSq = wallVector.lengthSquared();
        if (wallLenSq == 0) return;

        Vector2D wallToCenter = entity.getPosition().subtract(wall.p1);
        double t = Math.max(0, Math.min(1, wallToCenter.dot(wallVector) / wallLenSq));
        Vector2D closestPoint = wall.p1.add(wallVector.multiply(t));
        Vector2D wallNormal;
        
        Vector2D[] shapeVertices = entity.getTransformedVertices();

        if (t > 0 && t < 1) {
            wallNormal = new Vector2D(-wallVector.y, wallVector.x).normalize();
            if (wallNormal.dot(wallToCenter) < 0) wallNormal = wallNormal.multiply(-1);
        } else {
            if (shapeVertices.length == 0) {
                wallNormal = entity.getPosition().subtract(closestPoint).normalize();
            } else {
                Vector2D closestVertex = shapeVertices[0];
                double minDistSq = closestPoint.distanceSquared(closestVertex);
                
                for (int i = 1; i < shapeVertices.length; i++) {
                    double distSq = closestPoint.distanceSquared(shapeVertices[i]);
                    if (distSq < minDistSq) { 
                        minDistSq = distSq; 
                        closestVertex = shapeVertices[i]; 
                    }
                }
                wallNormal = closestVertex.subtract(closestPoint).normalize();
            }
            if (wallNormal.x == 0 && wallNormal.y == 0) {
                wallNormal = entity.getPosition().subtract(closestPoint).normalize();
            }
        }

        double wallPosOnNormal = closestPoint.dot(wallNormal);
        double minShapeProj = (entity.shape == Ball.ShapeType.CIRCLE) 
            ? entity.getPosition().dot(wallNormal) - entity.getRadius() 
            : wallNormal.projectVertices(shapeVertices)[0];

        if (minShapeProj > wallPosOnNormal) return;

        double overlap = wallPosOnNormal - minShapeProj;
        
        // Lowered percent to 0.2 to let the new velocity solver handle the heavy lifting without jitter
        double percent = 0.2, slop = 0.05;
        double correctionMag = Math.max(overlap - slop, 0.0) * percent;
        
        entity.setPosition(entity.getPosition().add(wallNormal.multiply(correctionMag)));

        Vector2D[] contactPoints;
        if (entity.shape == Ball.ShapeType.CIRCLE) {
            contactPoints = new Vector2D[]{ entity.getPosition().subtract(wallNormal.multiply(entity.getRadius())) };
        } else {
            double maxProj = -Double.MAX_VALUE;
            Vector2D wallDir = wallNormal.multiply(-1);
            
            for (Vector2D v : shapeVertices) {
                double proj = v.dot(wallDir);
                if (proj > maxProj) maxProj = proj;
            }
            
            List<Vector2D> contacts = new ArrayList<>();
            for (Vector2D v : shapeVertices) {
                if (Math.abs(v.dot(wallDir) - maxProj) <= 1.5) contacts.add(v);
            }
            
            if (contacts.size() > 2) {
                Vector2D p1 = contacts.get(0), p2 = contacts.get(0);
                double maxDist = 0;
                for (int i = 0; i < contacts.size(); i++) {
                    for (int j = i + 1; j < contacts.size(); j++) {
                        double dSq = contacts.get(i).distanceSquared(contacts.get(j));
                        if (dSq > maxDist) {
                            maxDist = dSq; p1 = contacts.get(i); p2 = contacts.get(j);
                        }
                    }
                }
                contactPoints = new Vector2D[]{p1, p2};
            } else {
                contactPoints = contacts.toArray(new Vector2D[0]);
            }
        }

        // Apply impulse to wall contacts (Walls are static, so we simplify the math here)
        int contactCount = contactPoints.length;
        if (contactCount == 0) return;

        double invInertia = 1.0 / entity.getInertia();
        double invMass = 1.0 / entity.getMass();

        for (Vector2D contactPoint : contactPoints) {
            Vector2D rB = contactPoint.subtract(entity.getPosition());
            Vector2D angVelB = new Vector2D(-entity.getAngularVelocity() * rB.y, entity.getAngularVelocity() * rB.x);
            Vector2D relativeVelocity = entity.getVelocity().add(angVelB);
            double impact = relativeVelocity.dot(wallNormal);

            if (impact < 0) {
                if (audioEngine != null && Math.abs(impact) > 100) {
                    audioEngine.playSound("thud", (float)Math.min(1.0, Math.abs(impact) / 2000.0), 0.8f + (float)Math.random() * 0.4f);
                }
                double rBCrossN = rB.cross(wallNormal);
                double angularEffect = rBCrossN * rBCrossN * invInertia;
                double e = Math.abs(impact) < ROLL_THRESHOLD ? 0 : entity.getRestitution();
                
                double j = -(1 + e) * impact / (invMass + angularEffect);
                j /= contactCount;

                entity.setVelocity(entity.getVelocity().add(wallNormal.multiply(j * invMass)));
                entity.setAngularVelocity(entity.getAngularVelocity() + (rBCrossN * j * invInertia));

                Vector2D tangent = new Vector2D(-wallNormal.y, wallNormal.x);
                double relVelTangent = relativeVelocity.dot(tangent);
                if (relVelTangent > 0) { 
                    tangent = tangent.multiply(-1); 
                    relVelTangent = -relVelTangent; 
                }

                double rBCrossT = rB.cross(tangent);
                double angularEffectT = rBCrossT * rBCrossT * invInertia;
                double jt = -relVelTangent / (invMass + angularEffectT);
                jt /= contactCount;
                
                double maxFriction = j * entity.getFriction();
                jt = Math.max(-maxFriction, Math.min(jt, maxFriction));

                entity.setVelocity(entity.getVelocity().add(tangent.multiply(jt * invMass)));
                entity.setAngularVelocity(entity.getAngularVelocity() + (rBCrossT * jt * invInertia));
            }
        }
    }

    public static void resolveBallCollision(Ball a, Ball b) {
        if (a.isStatic && b.isStatic) return;

        Vector2D delta = b.getPosition().subtract(a.getPosition());
        double distSq = delta.lengthSquared();
        double radiiSum = a.getRadius() + b.getRadius();
        
        if (distSq >= radiiSum * radiiSum || distSq == 0) return;

        double distance = Math.sqrt(distSq);
        Vector2D normal = delta.multiply(1.0 / distance);
        Vector2D contactPoint = a.getPosition().add(normal.multiply(a.getRadius() - (radiiSum - distance) / 2));
        
        applyDynamics(a, b, normal, new Vector2D[]{contactPoint}, radiiSum - distance);
    }

    public static void resolveEntityCollision(Ball a, Ball b) {
        if (a.isStatic && b.isStatic) return;
        
        Vector2D[] vertsA = a.getTransformedVertices();
        Vector2D[] vertsB = b.getTransformedVertices();
        
        double overlap = Double.MAX_VALUE;
        Vector2D smallestAxis = null;

        Vector2D[] axesA = getEdgeNormals(vertsA);
        Vector2D[] axesB = getEdgeNormals(vertsB);
        Vector2D[] allAxes = new Vector2D[axesA.length + axesB.length];
        System.arraycopy(axesA, 0, allAxes, 0, axesA.length);
        System.arraycopy(axesB, 0, allAxes, axesA.length, axesB.length);

        for (Vector2D axis : allAxes) {
            double[] projA = axis.projectVertices(vertsA);
            double[] projB = axis.projectVertices(vertsB);
            
            if (projA[1] < projB[0] || projB[1] < projA[0]) return;

            double o = Math.min(projA[1], projB[1]) - Math.max(projA[0], projB[0]);
            if (o < overlap) { 
                overlap = o; 
                smallestAxis = axis; 
            }
        }

        if (smallestAxis.dot(b.getPosition().subtract(a.getPosition())) < 0) {
            smallestAxis = smallestAxis.multiply(-1);
        }
        
        Vector2D[] manifold = getContactManifold(vertsA, vertsB, smallestAxis);
        applyDynamics(a, b, smallestAxis, manifold, overlap);
    }

    public static void resolvePolygonCircleCollision(Ball a, Ball b) {
        Ball poly = (a.shape != Ball.ShapeType.CIRCLE) ? a : b;
        Ball circle = (a.shape == Ball.ShapeType.CIRCLE) ? a : b;
        
        if (poly.isStatic && circle.isStatic) return;
        
        Vector2D[] verts = poly.getTransformedVertices();
        Vector2D[] polyAxes = getEdgeNormals(verts);
        
        Vector2D closestVertex = verts[0];
        double minDistSq = circle.getPosition().distanceSquared(closestVertex);

        for (int i = 1; i < verts.length; i++) {
            double distSq = circle.getPosition().distanceSquared(verts[i]);
            if (distSq < minDistSq) { 
                minDistSq = distSq; 
                closestVertex = verts[i]; 
            }
        }

        Vector2D axisToCircle = circle.getPosition().subtract(closestVertex);
        
        Vector2D[] axes;
        if (axisToCircle.lengthSquared() > 0) {
            axes = new Vector2D[polyAxes.length + 1];
            System.arraycopy(polyAxes, 0, axes, 0, polyAxes.length);
            axes[polyAxes.length] = axisToCircle.normalize();
        } else {
            axes = polyAxes;
        }

        double overlap = Double.MAX_VALUE;
        Vector2D smallestAxis = null;

        for (Vector2D axis : axes) {
            double[] projPoly = axis.projectVertices(verts);
            double centerProj = circle.getPosition().dot(axis);
            double[] projCircle = new double[]{centerProj - circle.getRadius(), centerProj + circle.getRadius()};
            
            if (projPoly[1] < projCircle[0] || projCircle[1] < projPoly[0]) return;

            double o = Math.min(projPoly[1], projCircle[1]) - Math.max(projPoly[0], projCircle[0]);
            if (o < overlap) { 
                overlap = o; 
                smallestAxis = axis; 
            }
        }

        if (smallestAxis.dot(circle.getPosition().subtract(poly.getPosition())) < 0) {
            smallestAxis = smallestAxis.multiply(-1);
        }
        
        Vector2D contactPoint = circle.getPosition().subtract(smallestAxis.multiply(circle.getRadius()));
        applyDynamics(poly, circle, smallestAxis, new Vector2D[]{contactPoint}, overlap);
    }

    // --- PHASE 7: SEQUENTIAL IMPULSE SOLVER WITH WARM STARTING ---
    private static void applyDynamics(Ball a, Ball b, Vector2D normal, Vector2D[] contactPoints, double overlap) {
        double invMassA = a.isStatic ? 0 : 1.0 / a.getMass();
        double invMassB = b.isStatic ? 0 : 1.0 / b.getMass();
        double invInertiaA = a.isStatic ? 0 : 1.0 / a.getInertia();
        double invInertiaB = b.isStatic ? 0 : 1.0 / b.getInertia();
        
        if (invMassA == 0 && invMassB == 0) return;

        double massSum = invMassA + invMassB;
        
        // 1. Positional correction (Baumgarte Stabilization)
        // Increased percent slightly for better stability in stacking
        double percent = 0.3, slop = 0.01;
        Vector2D correction = normal.multiply(Math.max(overlap - slop, 0.0) * percent / massSum);
        if (!a.isStatic) a.setPosition(a.getPosition().subtract(correction.multiply(invMassA)));
        if (!b.isStatic) b.setPosition(b.getPosition().add(correction.multiply(invMassB)));

        int contactCount = contactPoints.length;
        if (contactCount == 0) return;

        // Create a unique identifier for these two specific objects colliding
        long pairKey = ((long) Math.min(System.identityHashCode(a), System.identityHashCode(b)) << 32) | 
                       (Math.max(System.identityHashCode(a), System.identityHashCode(b)) & 0xffffffffL);

        // 2. Velocity and Rotation Resolution (Iterative Accumulated Impulses)
        for (int i = 0; i < contactPoints.length; i++) {
            Vector2D contactPoint = contactPoints[i];
            
            // Differentiate between the multiple contact points on flat edges
            long contactId = pairKey + i; 
            activeContactsThisFrame.add(contactId);

            Vector2D rA = contactPoint.subtract(a.getPosition());
            Vector2D rB = contactPoint.subtract(b.getPosition());

            Vector2D relativeVelocity = b.getVelocity().add(new Vector2D(-b.getAngularVelocity() * rB.y, b.getAngularVelocity() * rB.x))
                    .subtract(a.getVelocity().add(new Vector2D(-a.getAngularVelocity() * rA.y, a.getAngularVelocity() * rA.x)));
            
            double impact = relativeVelocity.dot(normal);

            if (audioEngine != null && Math.abs(impact) > 100) {
                audioEngine.playSound("thud", (float)Math.min(1.0, Math.abs(impact) / 2000.0), 0.8f + (float)Math.random() * 0.4f);
            }

            // NORMAL IMPULSE (BOUNCE & RESTING)
            double rACrossN = rA.cross(normal);
            double rBCrossN = rB.cross(normal);
            double angularEffect = (rACrossN * rACrossN * invInertiaA) + (rBCrossN * rBCrossN * invInertiaB);
            double massNormal = massSum + angularEffect;
            
            double e = Math.abs(impact) < ROLL_THRESHOLD ? 0 : Math.min(a.getRestitution(), b.getRestitution());
            double j = -(1 + e) * impact / massNormal;
            
            // --- THE WARM START CLAMP ---
            double oldAccumulatedNormal = normalImpulseCache.getOrDefault(contactId, 0.0);
            double newAccumulatedNormal = Math.max(oldAccumulatedNormal + j, 0.0); // Never pull, only push
            double appliedNormalImpulse = newAccumulatedNormal - oldAccumulatedNormal;
            normalImpulseCache.put(contactId, newAccumulatedNormal);
            
            Vector2D impulse = normal.multiply(appliedNormalImpulse);
            
            if (!a.isStatic) { 
                a.setVelocity(a.getVelocity().subtract(impulse.multiply(invMassA))); 
                a.setAngularVelocity(a.getAngularVelocity() - (rACrossN * appliedNormalImpulse * invInertiaA)); 
            }
            if (!b.isStatic) { 
                b.setVelocity(b.getVelocity().add(impulse.multiply(invMassB))); 
                b.setAngularVelocity(b.getAngularVelocity() + (rBCrossN * appliedNormalImpulse * invInertiaB)); 
            }

            // FRICTION IMPULSE
            relativeVelocity = b.getVelocity().add(new Vector2D(-b.getAngularVelocity() * rB.y, b.getAngularVelocity() * rB.x))
                    .subtract(a.getVelocity().add(new Vector2D(-a.getAngularVelocity() * rA.y, a.getAngularVelocity() * rA.x)));

            Vector2D tangent = new Vector2D(-normal.y, normal.x);
            double relVelTangent = relativeVelocity.dot(tangent);
            if (relVelTangent > 0) { 
                tangent = tangent.multiply(-1); 
                relVelTangent = -relVelTangent; 
            }

            double rACrossT = rA.cross(tangent);
            double rBCrossT = rB.cross(tangent);
            double massTangent = massSum + (rACrossT * rACrossT * invInertiaA) + (rBCrossT * rBCrossT * invInertiaB);
            if (massTangent == 0) continue;
            double jt = -relVelTangent / massTangent;
            
            // Friction is clamped based on the actual normal force applied this frame (Coulomb's Law)
            // Using a slightly higher friction combining for stability
            double staticFriction = Math.sqrt(a.getFriction() * a.getFriction() + b.getFriction() * b.getFriction());
            double maxFriction = newAccumulatedNormal * staticFriction;
            
            double oldAccumulatedFriction = frictionImpulseCache.getOrDefault(contactId, 0.0);
            double newAccumulatedFriction = Math.max(-maxFriction, Math.min(oldAccumulatedFriction + jt, maxFriction));
            double appliedFrictionImpulse = newAccumulatedFriction - oldAccumulatedFriction;
            frictionImpulseCache.put(contactId, newAccumulatedFriction);

            Vector2D frictionImpulse = tangent.multiply(appliedFrictionImpulse);
            
            if (!a.isStatic) { 
                a.setVelocity(a.getVelocity().subtract(frictionImpulse.multiply(invMassA))); 
                a.setAngularVelocity(a.getAngularVelocity() - (rACrossT * appliedFrictionImpulse * invInertiaA)); 
            }
            if (!b.isStatic) { 
                b.setVelocity(b.getVelocity().add(frictionImpulse.multiply(invMassB))); 
                b.setAngularVelocity(b.getAngularVelocity() + (rBCrossT * appliedFrictionImpulse * invInertiaB)); 
            }
        }
    }

    private static Vector2D[] getEdgeNormals(Vector2D[] vertices) {
        Vector2D[] axes = new Vector2D[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            Vector2D current = vertices[i];
            Vector2D next = vertices[(i + 1) % vertices.length];
            axes[i] = new Vector2D(-(next.y - current.y), next.x - current.x).normalize();
        }
        return axes;
    }

    private static Vector2D[] getContactManifold(Vector2D[] vertsA, Vector2D[] vertsB, Vector2D normal) {
        List<Vector2D> contacts = new ArrayList<>();
        
        double maxA = -Double.MAX_VALUE;
        double maxB = -Double.MAX_VALUE;
        
        for (Vector2D v : vertsA) maxA = Math.max(maxA, v.dot(normal));
        for (Vector2D v : vertsB) maxB = Math.max(maxB, v.dot(normal.multiply(-1)));
        
        for (Vector2D v : vertsA) {
            if (Math.abs(v.dot(normal) - maxA) <= 1.5) contacts.add(v);
        }
        for (Vector2D v : vertsB) {
            if (Math.abs(v.dot(normal.multiply(-1)) - maxB) <= 1.5) contacts.add(v);
        }
        
        if (contacts.size() > 2) {
            Vector2D p1 = contacts.get(0);
            Vector2D p2 = contacts.get(0);
            double maxDist = 0;
            
            for (int i = 0; i < contacts.size(); i++) {
                for (int j = i + 1; j < contacts.size(); j++) {
                    double dSq = contacts.get(i).distanceSquared(contacts.get(j));
                    if (dSq > maxDist) {
                        maxDist = dSq;
                        p1 = contacts.get(i);
                        p2 = contacts.get(j);
                    }
                }
            }
            return new Vector2D[]{p1, p2};
        }
        
        return contacts.toArray(new Vector2D[0]);
    }
}