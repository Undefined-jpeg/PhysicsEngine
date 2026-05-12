package com.physicsengine;
import java.util.ArrayList;
import java.util.List;

public class LogicNode {
    public enum NodeType {
        NONE, POWER_SOURCE, LIGHTBULB, 
        GATE_AND, GATE_OR, GATE_NOT, GATE_XOR, GATE_TOGGLE, GATE_DELAY, 
        GATE_TIMER, GATE_COUNTER, RELAY, // --- NEW PHASE 1 ADDITIONS ---
        INPUT_BUTTON, INPUT_PRESSURE_PLATE, INPUT_TRIPWIRE,
        INPUT_KEY_W, INPUT_KEY_A, INPUT_KEY_S, INPUT_KEY_D, INPUT_SENSOR, 
        INPUT_PROXIMITY, INPUT_SPEEDOMETER,
        OUTPUT_DOOR, OUTPUT_PLATFORM, OUTPUT_PISTON, OUTPUT_CANNON,
        OUTPUT_THRUSTER, OUTPUT_TNT, OUTPUT_MOTOR, OUTPUT_LASER, OUTPUT_SPAWNER,
        OUTPUT_HOVER, OUTPUT_ATTRACTOR, OUTPUT_VOID // --- PHASE 10 ---
, SR_LATCH
    }

    public NodeType type;
    public Ball parentBody; 
    public LogicBehavior behavior; // New field for behavior

    public boolean currentState = false;
    public boolean secondaryState = false; // For Q-bar output of SR Latch
    public boolean previousState = false;

    public List<LogicNode> connectedOutputs = new ArrayList<>();
    public List<LogicNode> connectedInputs = new ArrayList<>();

    public double massThreshold = 10.0; 
    public boolean isToggleMode = false; 
    public boolean isPressedThisFrame = false; 
    public int activeTimer = 0; 
    public LogicNode pairedNode = null; 

    public Vector2D outputDirection = new Vector2D(0, -1); 
    public double outputDistance = 150.0;
    public double outputSpeed = 1000.0; 
    public boolean movingForward = true; 

    public boolean[] delayBuffer = new boolean[60]; 
    public int delayIndex = 0;
    public int delayTicks = 15; 
    
    public Vector2D laserEndPos = null;

    // --- NEW TIMER / COUNTER VARIABLES ---
    public int timerIntervalTicks = 60;  // Default 1 second (assuming 60 TPS)
    public int currentTimerTick = 0;

    public int counterTarget = 5;        // How many pulses before outputting
    public int currentCount = 0;
    public boolean previousInputState = false; // Used to detect rising edges for the counter
    // -------------------------------------

    public LogicNode(Ball parent, NodeType type) {
        this.parentBody = parent;
        this.type = type;
        this.behavior = LogicBehaviorFactory.createBehavior(type); // Assign behavior
        if (type == NodeType.POWER_SOURCE) this.currentState = true; 
    }

    public void addConnectionTo(LogicNode target) {
        if (!this.connectedOutputs.contains(target)) {
            this.connectedOutputs.add(target);
            target.connectedInputs.add(this);
        }
    }
}