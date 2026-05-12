import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class LogicBehaviorFactory {

    public static LogicBehavior createBehavior(LogicNode.NodeType type) {
        return switch (type) {
            case POWER_SOURCE -> new PowerSourceBehavior();
            case LIGHTBULB -> new LightbulbBehavior();
            case GATE_AND -> new GateAndBehavior();
            case GATE_OR -> new GateOrBehavior();
            case GATE_XOR -> new GateXorBehavior();
            case GATE_NOT -> new GateNotBehavior();
            case GATE_TOGGLE -> new GateToggleBehavior();
            case GATE_DELAY -> new GateDelayBehavior();
            case INPUT_BUTTON -> new InputButtonBehavior();
            case INPUT_PRESSURE_PLATE -> new InputPressurePlateBehavior();
            case INPUT_TRIPWIRE -> new InputTripwireBehavior();
            case INPUT_KEY_W -> new InputKeyBehavior(InputKeyBehavior.KeyType.W);
            case INPUT_KEY_A -> new InputKeyBehavior(InputKeyBehavior.KeyType.A);
            case INPUT_KEY_S -> new InputKeyBehavior(InputKeyBehavior.KeyType.S);
            case INPUT_KEY_D -> new InputKeyBehavior(InputKeyBehavior.KeyType.D);
            case INPUT_SENSOR -> new InputSensorBehavior();
            case INPUT_PROXIMITY -> new InputProximityBehavior();
            case INPUT_SPEEDOMETER -> new InputSpeedometerBehavior();
            case OUTPUT_DOOR -> new OutputDoorBehavior();
            case OUTPUT_PLATFORM -> new OutputPlatformBehavior();
            case OUTPUT_PISTON -> new OutputPistonBehavior();
            case OUTPUT_CANNON -> new OutputCannonBehavior();
            case OUTPUT_THRUSTER -> new OutputThrusterBehavior();
            case OUTPUT_TNT -> new OutputTntBehavior();
            case OUTPUT_MOTOR -> new OutputMotorBehavior();
            case OUTPUT_LASER -> new OutputLaserBehavior();
            case OUTPUT_SPAWNER -> new OutputSpawnerBehavior();
            case OUTPUT_HOVER -> new OutputHoverBehavior();
            case OUTPUT_ATTRACTOR -> new OutputAttractorBehavior();
            case OUTPUT_VOID -> new OutputVoidBehavior();
            case SR_LATCH -> new SrLatchBehavior();
            case GATE_TIMER -> new GateTimerBehavior();
            case GATE_COUNTER -> new GateCounterBehavior();
            case RELAY -> new RelayBehavior();
            default -> new DefaultLogicBehavior();
        };
    }

    // --- Default Behavior (for types that don't need specific pre/post-evaluation) ---
    private static class DefaultLogicBehavior implements LogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) {}
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {}
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {}
        
        // Helper methods for common logic patterns
        protected boolean isActivated(LogicNode node) {
            for (LogicNode input : node.connectedInputs) if (input.currentState) return true;
            return false;
        }
        protected boolean wasActivated(LogicNode node) {
            for (LogicNode input : node.connectedInputs) if (input.previousState) return true;
            return false;
        }
        protected int countActiveInputs(LogicNode node) {
            int activeInputs = 0;
            for (LogicNode input : node.connectedInputs) if (input.currentState) activeInputs++;
            return activeInputs;
        }
    }

    // --- Input Behaviors ---
    private static class PowerSourceBehavior extends DefaultLogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) { node.currentState = true; }
    }

    private static class InputButtonBehavior extends DefaultLogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) {
            if (node.isToggleMode) {
                if (node.isPressedThisFrame && node.activeTimer == 0) {
                    node.currentState = !node.currentState;
                    node.activeTimer = 20; // Cooldown
                }
            } else {
                if (node.isPressedThisFrame) { node.currentState = true; node.activeTimer = 10; }
                if (node.activeTimer == 0) node.currentState = false;
            }
            if (node.activeTimer > 0) node.activeTimer--;
            node.isPressedThisFrame = false;
        }
    }

    private static class InputPressurePlateBehavior extends DefaultLogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) {
            double totalMass = 0;
            AABB pb = node.parentBody.getAABB();
            AABB sensorBox = new AABB(pb.minX - 2, pb.minY - 2, pb.maxX + 2, pb.maxY + 2);
            for (Ball other : engine.getBalls()) {
                if (other != node.parentBody && !other.isNoClip && sensorBox.intersects(other.getAABB())) {
                    totalMass += other.getMass();
                }
            }
            node.currentState = totalMass >= node.massThreshold;
        }
    }

    private static class InputTripwireBehavior extends DefaultLogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) {
            if (node.pairedNode != null && !node.pairedNode.parentBody.isDestroyed) {
                Ball hit = engine.raycast(node.parentBody.getPosition(), node.pairedNode.parentBody.getPosition(), node.parentBody, node.pairedNode.parentBody);
                node.currentState = (hit != null);
            } else {
                node.currentState = false;
            }
        }
    }

    private static class InputKeyBehavior extends DefaultLogicBehavior {
        public enum KeyType { W, A, S, D }
        private final KeyType keyType;
        public InputKeyBehavior(KeyType keyType) { this.keyType = keyType; }
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) {
            node.currentState = switch (keyType) {
                case W -> engine.keyW;
                case A -> engine.keyA;
                case S -> engine.keyS;
                case D -> engine.keyD;
            };
        }
    }

    private static class InputSensorBehavior extends DefaultLogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) { node.currentState = false; } // Reset sensor
    }

    private static class InputProximityBehavior extends DefaultLogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) {
            double range = node.outputDistance;
            AABB sensorBox = new AABB(node.parentBody.getPosition().x - range, node.parentBody.getPosition().y - range,
                                      node.parentBody.getPosition().x + range, node.parentBody.getPosition().y + range);
            node.currentState = false;
            for (Ball other : engine.getBalls()) {
                if (other != node.parentBody && !other.isNoClip && sensorBox.intersects(other.getAABB())) {
                    if (node.parentBody.getPosition().distanceTo(other.getPosition()) <= range) {
                        node.currentState = true;
                        break;
                    }
                }
            }
        }
    }

    private static class InputSpeedometerBehavior extends DefaultLogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) {
            double speed = node.parentBody.getVelocity().length();
            node.currentState = speed >= node.outputSpeed;
        }
    }

    // --- Gate Behaviors ---
    private static class LightbulbBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            node.currentState = isActivated(node);
        }
    }

    private static class GateAndBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            int activeInputs = countActiveInputs(node);
            node.currentState = (activeInputs == node.connectedInputs.size()) && (activeInputs > 0);
        }
    }

    private static class GateOrBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            node.currentState = isActivated(node);
        }
    }

    private static class GateXorBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            node.currentState = countActiveInputs(node) == 1;
        }
    }

    private static class GateNotBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            node.currentState = !isActivated(node);
        }
    }

    private static class GateToggleBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean anyOn = isActivated(node);
            boolean prevAnyOn = wasActivated(node);
            if (anyOn && !prevAnyOn) node.currentState = !node.currentState; // Only toggle on rising edge
        }
    }

    private static class GateDelayBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean inputState = isActivated(node);
            node.delayIndex = (node.delayIndex + 1) % node.delayBuffer.length;
            node.delayBuffer[node.delayIndex] = inputState;
            int readIndex = (node.delayIndex - node.delayTicks);
            if (readIndex < 0) readIndex += node.delayBuffer.length; // Ensure positive index
            readIndex %= node.delayBuffer.length;
            node.currentState = node.delayBuffer[readIndex];
        }
    }

    private static class GateTimerBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean inputActivated = isActivated(node);
            if (inputActivated) {
                node.currentTimerTick++;
                if (node.currentTimerTick >= node.timerIntervalTicks) {
                    node.currentState = true;
                    node.currentTimerTick = 0; // Reset timer
                } else {
                    node.currentState = false;
                }
            } else {
                node.currentState = false;
                node.currentTimerTick = 0; // Reset timer if input is off
            }
        }
    }

    private static class GateCounterBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean inputActivated = isActivated(node);
            if (inputActivated && !node.previousInputState) { // Rising edge detection
                node.currentCount++;
                if (node.currentCount >= node.counterTarget) {
                    node.currentState = true;
                    node.currentCount = 0; // Reset counter
                } else {
                    node.currentState = false;
                }
            } else {
                node.currentState = false;
            }
            node.previousInputState = inputActivated;
        }
    }

    private static class RelayBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            node.currentState = isActivated(node);
        }
    }

    private static class SrLatchBehavior extends DefaultLogicBehavior {
        @Override public void evaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean S = false; // Set input
            boolean R = false; // Reset input

            if (node.connectedInputs.size() > 0) S = node.connectedInputs.get(0).currentState;
            if (node.connectedInputs.size() > 1) R = node.connectedInputs.get(1).currentState;

            // NOR-based SR Latch logic
            if (S && !R) { // Set
                node.currentState = true;
                node.secondaryState = false;
            } else if (!S && R) { // Reset
                node.currentState = false;
                node.secondaryState = true;
            } else if (S && R) { // Forbidden state, typically both outputs go low or reset takes precedence
                node.currentState = false;
                node.secondaryState = false;
            } // If !S && !R, hold previous state (no change to currentState or secondaryState)
        }
    }

    // --- Output Behaviors ---
    private static class OutputDoorBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            Ball b = node.parentBody;
            if (b.originalPosition == null) b.originalPosition = b.getPosition();
            Vector2D targetPos = isActivated ? b.originalPosition.add(node.outputDirection.multiply(node.outputDistance)) : b.originalPosition;
            
            Vector2D diff = targetPos.subtract(b.getPosition());
            double dist = diff.length();
            if (dist > 2.0) {
                b.setVelocity(diff.normalize().multiply(Math.min(node.outputSpeed, dist * 15)));
            } else {
                b.setPosition(targetPos);
                b.setVelocity(new Vector2D(0,0));
            }
            b.wakeUp();
        }
    }

    private static class OutputPlatformBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            Ball b = node.parentBody;
            if (b.originalPosition == null) b.originalPosition = b.getPosition();
            Vector2D targetPos;
            if (isActivated) {
                Vector2D p1 = b.originalPosition, p2 = p1.add(node.outputDirection.multiply(node.outputDistance));
                targetPos = node.movingForward ? p2 : p1;
                if (b.getPosition().distanceTo(targetPos) < 5.0) node.movingForward = !node.movingForward;
            } else {
                targetPos = b.getPosition(); b.setVelocity(new Vector2D(0,0));
            }

            if (isActivated) { // Only move if activated
                Vector2D diff = targetPos.subtract(b.getPosition());
                double dist = diff.length();
                if (dist > 2.0) {
                    b.setVelocity(diff.normalize().multiply(Math.min(node.outputSpeed, dist * 15)));
                } else {
                    b.setPosition(targetPos);
                    b.setVelocity(new Vector2D(0,0));
                }
                b.wakeUp();
            }
        }
    }

    private static class OutputPistonBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            Ball b = node.parentBody;
            if (b.originalPosition == null) b.originalPosition = b.getPosition();
            Vector2D targetPos = isActivated ? b.originalPosition.add(node.outputDirection.multiply(node.outputDistance)) : b.originalPosition;

            Vector2D diff = targetPos.subtract(b.getPosition());
            double dist = diff.length();
            if (dist > 2.0) {
                b.setVelocity(diff.normalize().multiply(Math.min(node.outputSpeed, dist * 15)));
            } else {
                b.setPosition(targetPos);
                b.setVelocity(new Vector2D(0,0));
            }
            b.wakeUp();
        }
    }

    private static class OutputCannonBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            boolean prevActivated = wasActivated(node);
            if (isActivated && !prevActivated) {
                Ball b = node.parentBody;
                Vector2D spawnPos = b.getPosition().add(node.outputDirection.multiply(b.getRadius() + 15));
                Ball proj = new Ball(spawnPos.x, spawnPos.y, 10, Material.STEEL);
                proj.setVelocity(node.outputDirection.multiply(node.outputSpeed));
                engine.enqueueLogicSpawn(proj);
                if (!b.isStatic) {
                    double recoilVelocity = node.outputSpeed * (proj.getMass() / b.getMass());
                    b.setVelocity(b.getVelocity().subtract(node.outputDirection.multiply(recoilVelocity)));
                    b.wakeUp();
                }
                for (int i = 0; i < 15; i++) {
                    Vector2D pVel = node.outputDirection.multiply(-200).add(new Vector2D((Math.random()-0.5)*200, (Math.random()-0.5)*200));
                    engine.getParticles().add(new Particle(b.getPosition(), pVel, 0.2 + Math.random()*0.3, 3 + Math.random()*5, Color.LIGHT_GRAY));
                }
            }
        }
    }

    private static class OutputThrusterBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            if (isActivated) {
                Ball b = node.parentBody;
                b.setVelocity(b.getVelocity().add(node.outputDirection.multiply(node.outputSpeed * 0.01))); b.wakeUp();
                if (Math.random() < 0.6) {
                    Vector2D fireDir = node.outputDirection.multiply(-1);
                    Vector2D pVel = fireDir.multiply(400).add(new Vector2D((Math.random()-0.5)*100, (Math.random()-0.5)*100));
                    engine.getParticles().add(new Particle(b.getPosition().add(fireDir.multiply(b.getRadius() - 5)), pVel, 0.2 + Math.random()*0.2, 5 + Math.random()*5, new Color(255, 100 + (int)(Math.random()*100), 0)));
                }
            }
        }
    }

    private static class OutputTntBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            boolean prevActivated = wasActivated(node);
            if (isActivated && !prevActivated) {
                Ball b = node.parentBody;
                b.isDestroyed = true;
                for (Ball other : engine.getBalls()) {
                    if (other == b || other.isStatic || other.isNoClip) continue;
                    Vector2D diff = other.getPosition().subtract(b.getPosition());
                    double distSq = diff.lengthSquared();
                    if (distSq < 400 * 400 && distSq > 0) {
                        double force = 800000.0 / Math.max(distSq, 100);
                        other.setVelocity(other.getVelocity().add(diff.normalize().multiply(force / other.getMass())));
                        other.wakeUp();
                    }
                }
                for (int i = 0; i < 50; i++) {
                    Vector2D pVel = new Vector2D((Math.random()-0.5)*2000, (Math.random()-0.5)*2000);
                    engine.getParticles().add(new Particle(b.getPosition(), pVel, 0.4 + Math.random()*0.6, 6 + Math.random()*20, Color.ORANGE));
                    engine.getParticles().add(new Particle(b.getPosition(), pVel.multiply(0.5), 0.5 + Math.random()*1.0, 5 + Math.random()*15, Color.DARK_GRAY));
                }
            }
        }
    }

    private static class OutputMotorBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            if (isActivated) {
                node.parentBody.setAngularVelocity(node.outputSpeed * 0.01);
                node.parentBody.wakeUp();
            } else {
                node.parentBody.setAngularVelocity(0); // Stop motor if deactivated
            }
        }
    }

    private static class OutputLaserBehavior extends DefaultLogicBehavior {
        @Override public void preEvaluate(LogicNode node, EngineContainer engine, double dt) {
            node.laserEndPos = null; // Reset laser end position
        }
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            if (isActivated) {
                Ball b = node.parentBody;
                Vector2D start = b.getPosition().add(node.outputDirection.multiply(b.getRadius() + 1));
                Vector2D end = start.add(node.outputDirection.multiply(2500));
                Ball hit = engine.raycast(start, end, b);
                if (hit != null) {
                    node.laserEndPos = hit.getPosition();
                    if (hit.logicNode != null && hit.logicNode.type == LogicNode.NodeType.INPUT_SENSOR) {
                        hit.logicNode.currentState = true; // Activate sensor
                    }
                } else {
                    node.laserEndPos = end;
                }
            }
        }
    }

    private static class OutputSpawnerBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            boolean prevActivated = wasActivated(node);
            if (isActivated && !prevActivated) {
                Ball b = node.parentBody;
                Vector2D spawnPos = b.getPosition().add(node.outputDirection.multiply(b.getRadius() + 20));
                Ball crate = new Ball(spawnPos.x, spawnPos.y, 15, Material.WOOD);
                crate.setShape(Ball.ShapeType.CUBE);
                engine.enqueueLogicSpawn(crate);
            }
        }
    }

    private static class OutputHoverBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            Ball b = node.parentBody;
            if (isActivated && !b.isStatic) {
                b.setVelocity(b.getVelocity().add(new Vector2D(0, (-1000.0 * dt) - (node.outputSpeed * 0.01))));
                b.wakeUp();
                if (Math.random() < 0.3) {
                    Vector2D pVel = new Vector2D((Math.random()-0.5)*50, 100);
                    engine.getParticles().add(new Particle(b.getPosition().add(new Vector2D(0, b.getRadius())), pVel, 0.5 + Math.random()*0.5, 4 + Math.random()*6, new Color(0, 255, 255, 150)));
                }
            }
        }
    }

    private static class OutputAttractorBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            Ball b = node.parentBody;
            if (isActivated) {
                for (Ball other : engine.getBalls()) {
                    if (other == b || other.isStatic || other.isNoClip) continue;
                    Vector2D diff = b.getPosition().subtract(other.getPosition());
                    double distSq = diff.lengthSquared();
                    if (distSq > 100 && distSq < 1500 * 1500) {
                        double force = (node.outputSpeed * 500.0) / Math.max(distSq, 100);
                        other.setVelocity(other.getVelocity().add(diff.normalize().multiply(force)));
                        other.wakeUp();
                    }
                }
                if (Math.random() < 0.5) {
                    Vector2D offset = new Vector2D((Math.random()-0.5)*200, (Math.random()-0.5)*200);
                    engine.getParticles().add(new Particle(b.getPosition().add(offset), offset.multiply(-2), 0.3, 3, new Color(180, 0, 255)));
                }
            }
        }
    }

    private static class OutputVoidBehavior extends DefaultLogicBehavior {
        @Override public void postEvaluate(LogicNode node, EngineContainer engine, double dt) {
            boolean isActivated = isActivated(node);
            Ball b = node.parentBody;
            if (isActivated) {
                AABB voidBox = b.getAABB();
                List<Ball> ballsToDestroy = new ArrayList<>();
                for (Ball other : engine.getBalls()) {
                    if (other == b || other.isStatic) continue;
                    if (voidBox.intersects(other.getAABB())) {
                        ballsToDestroy.add(other);
                    }
                }
                for (Ball destroyedBall : ballsToDestroy) {
                    destroyedBall.isDestroyed = true;
                    for (int i = 0; i < 5; i++) engine.getParticles().add(new Particle(destroyedBall.getPosition(), new Vector2D((Math.random()-0.5)*300, (Math.random()-0.5)*300), 0.5, 5, Color.MAGENTA));
                }
            }
        }
    }
}