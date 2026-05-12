package com.physicsengine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

public class Main extends JPanel implements ActionListener {
    private EngineContainer engine;
    private Timer timer;

    public enum ToolMode { DRAG, PLACE, DELETE, SHOOT, CONFIGURE, LINK, DRAW }
    
    public enum SpawnType { 
        PHYSICS_STEEL_CUBE, PHYSICS_WOOD_TRI, PHYSICS_RUBBER_BALL, PHYSICS_GLASS, 
        INPUT_BUTTON, INPUT_PRESSURE_PLATE, INPUT_TRIPWIRE, INPUT_SENSOR, 
        INPUT_PROXIMITY, INPUT_SPEEDOMETER,
        INPUT_KEY_W, INPUT_KEY_A, INPUT_KEY_S, INPUT_KEY_D, 
        OUTPUT_DOOR, OUTPUT_PLATFORM, OUTPUT_PISTON, OUTPUT_CANNON,
        OUTPUT_THRUSTER, OUTPUT_TNT, OUTPUT_MOTOR, OUTPUT_LASER, OUTPUT_SPAWNER,
        OUTPUT_HOVER, OUTPUT_ATTRACTOR, OUTPUT_VOID, 
        LOGIC_POWER_SOURCE, LOGIC_LIGHTBULB, 
        LOGIC_AND, LOGIC_OR, LOGIC_NOT, LOGIC_XOR, LOGIC_TOGGLE, GATE_DELAY, GATE_TIMER, GATE_COUNTER, LOGIC_SR_LATCH, LOGIC_RELAY
    }
    
    private ToolMode currentMode = ToolMode.DRAG;
    private SpawnType currentSpawn = SpawnType.PHYSICS_STEEL_CUBE;
    private LogicNode wireStartNode = null; 
    private Ball linkStartNode = null; 

    private boolean isPlaying = true; 
    private double timeScale = 1.0;
    private boolean debugMode = false;

    private Ball activePlayer = null;
    private double shootForce = 1500.0; 

    private boolean keyW = false, keyA = false, keyS = false, keyD = false;
    private boolean keyI = false, keyO = false;
    
    private Ball hoveredBall = null;
    private boolean keyLeft = false, keyRight = false;
    private double placementRotation = 0.0;

    private List<Vector2D> customShapePoints = new ArrayList<>();
    private List<Ball> selectedBalls = new ArrayList<>();
    private Point selectionStart = null;
    private BallData clipboardData = null; 

    private double camX = 0, camY = 0;
    private double zoom = 1.0;
    private double targetZoom = 1.0;

    private Point lastMousePan = null;
    private Vector2D currentMouseWorldPos = new Vector2D(0, 0);

    private static final Color BG_COLOR = new Color(15, 32, 39);        
    private static final Color GRID_COLOR = new Color(32, 58, 67);      
    private static final Color WALL_COLOR = new Color(32, 58, 67);      
    private static final Color DYNAMIC_COLOR = new Color(219, 168, 0);  
    private static final Color NEON_GREEN = new Color(0, 255, 51);      
    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 180);
    private static final Color STATIC_COLOR = new Color(231, 76, 60);   
    
    private static final BasicStroke WALL_STROKE = new BasicStroke(4);
    private static final Font UI_FONT = new Font("Monospaced", Font.BOLD, 14);

    private Thread physicsThread;
    private volatile boolean running = true;

    public Main(int screenWidth, int screenHeight) {
        engine = new EngineContainer();
        try {
            audioEngine = new AudioEngine();
            audioEngine.loadSound("thud", "sounds/thud.ogg");
            audioEngine.loadSound("place", "sounds/place.ogg");
            audioEngine.loadSound("delete", "sounds/delete.ogg");
            PhysicsCore.audioEngine = audioEngine;
        } catch (Exception e) {
            System.err.println("Audio initialization failed: " + e.getMessage());
        }
        setFocusable(true);
        setBackground(BG_COLOR);

        engine.addWallSegment(new Vector2D(-2000, 500), new Vector2D(2000, 500));
        engine.addWaterZone(new WaterZone(500, 0, 1500, 500));
        spawnPlayer();

        setupMouseControls();
        setupKeyboardControls();

        // Multithreading: Physics and Logic on a separate thread
        physicsThread = new Thread(() -> {
            while (running) {
                if (isPlaying) {
                    long start = System.nanoTime();

                    if (activePlayer != null && !activePlayer.isDestroyed) {
                        Vector2D vel = activePlayer.getVelocity(); double targetVx = 0, targetVy = 0;
                        if (keyA) targetVx = -500; if (keyD) targetVx = 500;
                        if (keyW) targetVy = -500; if (keyS) targetVy = 500;
                        activePlayer.setVelocity(new Vector2D(vel.x + (targetVx - vel.x) * 0.15, vel.y + (targetVy - vel.y) * 0.15));
                        activePlayer.wakeUp();
                    }

                    engine.step(0.016 * timeScale);

                    if (activePlayer != null && !activePlayer.isDestroyed) {
                        camX += (activePlayer.getPosition().x - camX) * 0.05;
                        camY += (activePlayer.getPosition().y - camY) * 0.05;
                    }

                    long end = System.nanoTime();
                    long sleep = 16 - (end - start) / 1000000;
                    if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException ex) {}
                } else {
                    try { Thread.sleep(16); } catch (InterruptedException ex) {}
                }
            }
        });
        physicsThread.start();

        timer = new Timer(16, this);
        timer.start();
    }

    private void spawnPlayer() {
        if (activePlayer != null) engine.getBalls().remove(activePlayer);
        activePlayer = new Ball(0, -100, 20, Material.WOOD);
        activePlayer.setShape(Ball.ShapeType.CUBE);
        activePlayer.setLockRotation(true);
        activePlayer.isNoClip = false; 
        engine.addBall(activePlayer);
        camX = activePlayer.getPosition().x;
        camY = activePlayer.getPosition().y;
    }

    private void setupMouseControls() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Vector2D worldClick = screenToWorld(e.getPoint());
                Ball clickedBall = getBallAt(worldClick);

                if (SwingUtilities.isRightMouseButton(e)) {
                    if (currentMode == ToolMode.DRAW) {
                        finishCustomShape();
                        return;
                    }
                    if (currentMode == ToolMode.PLACE) showSpawnSelectionMenu(e.getX(), e.getY());
                    else if (currentMode == ToolMode.CONFIGURE && clickedBall != null && clickedBall.logicNode != null) showLogicConfigMenu(e.getX(), e.getY(), clickedBall.logicNode);
                    else if (clickedBall != null && clickedBall != activePlayer) showContextMenu(e.getX(), e.getY(), clickedBall);
                    else lastMousePan = e.getPoint();
                    return;
                }

                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (clickedBall != null && clickedBall != activePlayer && currentMode != ToolMode.CONFIGURE && currentMode != ToolMode.SHOOT && currentMode != ToolMode.DELETE && currentMode != ToolMode.LINK && currentMode != ToolMode.DRAW) {
                        if (selectedBalls.contains(clickedBall)) {
                            // Start moving group
                        } else {
                            selectedBalls.clear();
                            SpringConstraint joint = new SpringConstraint(clickedBall, worldClick, 5000.0, 200.0);
                            engine.setMouseJoint(joint);
                            return;
                        }
                    }

                    if (currentMode == ToolMode.DRAG && clickedBall == null) {
                        selectionStart = e.getPoint();
                    }

                    switch (currentMode) {
                        case DRAG -> {} 
                        case PLACE -> spawnEntity(worldClick);
                        case DELETE -> {
                            if (clickedBall != null && clickedBall != activePlayer) {
                                CommandHistory.executeCommand(new DeleteBallCommand(engine, clickedBall));
                                if (audioEngine != null) audioEngine.playSound("delete", 1.0f, 1.0f);
                            }
                        }
                        case SHOOT -> {
                            Vector2D rayOrigin = activePlayer != null ? activePlayer.getPosition() : screenToWorld(new Point(getWidth()/2, getHeight()/2));
                            Vector2D dir = worldClick.subtract(rayOrigin).normalize();
                            Ball proj = new Ball(rayOrigin.x + dir.x * 40, rayOrigin.y + dir.y * 40, 15, Material.STEEL);
                            proj.setVelocity(dir.multiply(shootForce));
                            engine.addBall(proj);
                        }
                        case CONFIGURE -> {
                            if (clickedBall != null && clickedBall.logicNode != null) {
                                if (wireStartNode == null) wireStartNode = clickedBall.logicNode;
                                else {
                                    CommandHistory.executeCommand(new WireCommand(wireStartNode, clickedBall.logicNode));
                                    wireStartNode = null;
                                }
                            } else wireStartNode = null; 
                        }
                        case LINK -> {
                            if (clickedBall != null && clickedBall != activePlayer) {
                                if (linkStartNode == null) linkStartNode = clickedBall;
                                else if (linkStartNode != clickedBall) {
                                    CommandHistory.executeCommand(new JointCommand(engine, new WeldConstraint(linkStartNode, clickedBall)));
                                    linkStartNode = null;
                                }
                            } else linkStartNode = null;
                        }
                        case DRAW -> {
                            customShapePoints.add(worldClick); 
                        }
                    }
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) lastMousePan = null;
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (engine.getMouseJoint() != null) engine.setMouseJoint(null);
                    if (selectionStart != null) {
                        Vector2D worldStart = screenToWorld(selectionStart);
                        Vector2D worldEnd = screenToWorld(e.getPoint());
                        AABB selectionBox = new AABB(Math.min(worldStart.x, worldEnd.x), Math.min(worldStart.y, worldEnd.y), Math.max(worldStart.x, worldEnd.x), Math.max(worldStart.y, worldEnd.y));
                        selectedBalls.clear();
                        for (Ball b : engine.getBalls()) {
                            if (b != activePlayer && selectionBox.intersects(b.getAABB())) {
                                selectedBalls.add(b);
                            }
                        }
                        selectionStart = null;
                    }
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                Vector2D prevMouseWorldPos = currentMouseWorldPos;
                currentMouseWorldPos = screenToWorld(e.getPoint());
                hoveredBall = getBallAt(currentMouseWorldPos); 

                if (SwingUtilities.isRightMouseButton(e) && lastMousePan != null) {
                    camX -= (e.getX() - lastMousePan.x) / zoom;
                    camY -= (e.getY() - lastMousePan.y) / zoom;
                    lastMousePan = e.getPoint();
                }

                if (SwingUtilities.isLeftMouseButton(e) && selectionStart == null && !selectedBalls.isEmpty() && engine.getMouseJoint() == null) {
                    Vector2D delta = currentMouseWorldPos.subtract(prevMouseWorldPos);
                    for (Ball b : selectedBalls) {
                        b.setPosition(b.getPosition().add(delta));
                        b.setVelocity(new Vector2D(0, 0));
                    }
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) { 
                currentMouseWorldPos = screenToWorld(e.getPoint()); 
                hoveredBall = getBallAt(currentMouseWorldPos); 
            }
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getPreciseWheelRotation() < 0) targetZoom *= 1.1;
                else targetZoom /= 1.1;
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
    }

    private void savePrefab() {
        if (selectedBalls.isEmpty()) return;

        SaveData.Prefab prefab = new SaveData.Prefab();
        List<Ball> list = new ArrayList<>(selectedBalls);

        for (Ball b : list) {
            SaveData.SavedBall sb = new SaveData.SavedBall();
            sb.id = b.id;
            sb.x = b.getPosition().x; sb.y = b.getPosition().y;
            sb.radius = b.getRadius(); sb.angle = b.getAngle();
            sb.materialType = b.material.name();
            sb.shapeType = b.shape.name();
            sb.isStatic = b.isStatic;
            if (b.logicNode != null) {
                sb.logicNodeType = b.logicNode.type.name();
                sb.nodeOutputSpeed = b.logicNode.outputSpeed;
                sb.nodeDelayTicks = b.logicNode.delayTicks;
            }
            prefab.balls.add(sb);
        }

        // Add constraints between selected balls
        for (Constraint c : engine.getConstraints()) {
            if (list.contains(c.a) && list.contains(c.b)) {
                SaveData.SavedJoint sj = new SaveData.SavedJoint();
                sj.type = "SPRING";
                sj.ballIdA = c.a.id; sj.ballIdB = c.b.id;
                sj.springRestLength = c.targetLength;
                prefab.joints.add(sj);
            }
        }

        for (WeldConstraint w : engine.getWeldConstraints()) {
            if (list.contains(w.a) && list.contains(w.b)) {
                SaveData.SavedJoint sj = new SaveData.SavedJoint();
                sj.type = "WELD";
                sj.ballIdA = w.a.id; sj.ballIdB = w.b.id;
                prefab.joints.add(sj);
            }
        }

        // Add wires
        for (Ball b : list) {
            if (b.logicNode != null) {
                for (LogicNode out : b.logicNode.connectedOutputs) {
                    if (list.contains(out.parentBody)) {
                        SaveData.SavedWire sw = new SaveData.SavedWire();
                        sw.outputNodeBallId = b.id;
                        sw.inputNodeBallId = out.parentBody.id;
                        prefab.wires.add(sw);
                    }
                }
            }
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(prefab);

        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (java.io.FileWriter writer = new java.io.FileWriter(fileChooser.getSelectedFile())) {
                writer.write(json);
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private void loadPrefab() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (java.io.FileReader reader = new java.io.FileReader(fileChooser.getSelectedFile())) {
                Gson gson = new Gson();
                SaveData.Prefab prefab = gson.fromJson(reader, SaveData.Prefab.class);

                java.util.Map<Integer, Ball> idMap = new java.util.HashMap<>();
                Vector2D center = currentMouseWorldPos;

                // Calculate original center of prefab to offset it to mouse
                double avgX = 0, avgY = 0;
                for (SaveData.SavedBall sb : prefab.balls) { avgX += sb.x; avgY += sb.y; }
                avgX /= prefab.balls.size(); avgY /= prefab.balls.size();
                Vector2D offset = center.subtract(new Vector2D(avgX, avgY));

                for (SaveData.SavedBall sb : prefab.balls) {
                    Ball b = new Ball(sb.x + offset.x, sb.y + offset.y, sb.radius, Material.valueOf(sb.materialType));
                    b.setShape(Ball.ShapeType.valueOf(sb.shapeType));
                    b.setAngle(sb.angle);
                    b.isStatic = sb.isStatic;
                    if (sb.logicNodeType != null) {
                        b.logicNode = new LogicNode(b, LogicNode.NodeType.valueOf(sb.logicNodeType));
                        b.logicNode.outputSpeed = sb.nodeOutputSpeed;
                        b.logicNode.delayTicks = sb.nodeDelayTicks;
                    }
                    engine.addBall(b);
                    idMap.put(sb.id, b);
                }

                for (SaveData.SavedJoint sj : prefab.joints) {
                    Ball a = idMap.get(sj.ballIdA);
                    Ball b = idMap.get(sj.ballIdB);
                    if (a != null && b != null) {
                        if ("SPRING".equals(sj.type)) engine.addConstraint(new Constraint(a, b, sj.springRestLength, 0.1, 0.05));
                        else if ("WELD".equals(sj.type)) engine.addWeldConstraint(new WeldConstraint(a, b));
                    }
                }

                for (SaveData.SavedWire sw : prefab.wires) {
                    Ball out = idMap.get(sw.outputNodeBallId);
                    Ball in = idMap.get(sw.inputNodeBallId);
                    if (out != null && in != null && out.logicNode != null && in.logicNode != null) {
                        out.logicNode.addConnectionTo(in.logicNode);
                    }
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private void finishCustomShape() {
        if (customShapePoints.size() < 3) {
            customShapePoints.clear(); 
            return; 
        }
        
        double cx = 0, cy = 0;
        for (Vector2D p : customShapePoints) { cx += p.x; cy += p.y; }
        cx /= customShapePoints.size();
        cy /= customShapePoints.size();

        Vector2D[] localVerts = new Vector2D[customShapePoints.size()];
        double maxRadius = 0;
        for (int i = 0; i < customShapePoints.size(); i++) {
            Vector2D p = customShapePoints.get(i);
            localVerts[i] = new Vector2D(p.x - cx, p.y - cy);
            maxRadius = Math.max(maxRadius, localVerts[i].length());
        }

        Ball b = new Ball(cx, cy, maxRadius, Material.WOOD); 
        b.setCustomVertices(localVerts);
        engine.addBall(b);
        
        customShapePoints.clear();
    }

    private class BallData {
        double radius; Material mat; Ball.ShapeType shape; Vector2D[] customVerts;
        boolean isStatic, hasMotor; double motorSpeed;
        
        LogicNode.NodeType logicType; 
        double massThreshold, outputDistance, outputSpeed;
        Vector2D outputDir; boolean isToggle; int delayTicks;
    }

    private void copyToClipboard(Ball b) {
        clipboardData = new BallData();
        clipboardData.radius = b.getRadius();
        clipboardData.mat = b.getMaterial();
        clipboardData.shape = b.shape;
        if (b.getTransformedVertices() != null && b.shape != Ball.ShapeType.CIRCLE && b.shape != Ball.ShapeType.CUBE && b.shape != Ball.ShapeType.TRIANGLE && b.shape != Ball.ShapeType.HEXAGON) {
            Vector2D[] worldV = b.getTransformedVertices();
            clipboardData.customVerts = new Vector2D[worldV.length];
            double cos = Math.cos(-b.getAngle());
            double sin = Math.sin(-b.getAngle());
            for (int i = 0; i < worldV.length; i++) {
                Vector2D diff = worldV[i].subtract(b.getPosition());
                clipboardData.customVerts[i] = new Vector2D(diff.x * cos - diff.y * sin, diff.x * sin + diff.y * cos);
            }
        }
        clipboardData.isStatic = b.isStatic;
        clipboardData.hasMotor = b.hasMotor;
        clipboardData.motorSpeed = b.motorSpeed;

        if (b.logicNode != null) {
            clipboardData.logicType = b.logicNode.type;
            clipboardData.massThreshold = b.logicNode.massThreshold;
            clipboardData.outputDistance = b.logicNode.outputDistance;
            clipboardData.outputSpeed = b.logicNode.outputSpeed;
            clipboardData.outputDir = b.logicNode.outputDirection;
            clipboardData.isToggle = b.logicNode.isToggleMode;
            clipboardData.delayTicks = b.logicNode.delayTicks;
        } else {
            clipboardData.logicType = null;
        }
    }

    private void pasteFromClipboard(Vector2D pos) {
        if (clipboardData == null) return;
        Ball b = new Ball(pos.x, pos.y, clipboardData.radius, clipboardData.mat);
        if (clipboardData.customVerts != null) {
            b.setCustomVertices(clipboardData.customVerts);
        } else {
            b.setShape(clipboardData.shape);
        }
        b.isStatic = clipboardData.isStatic;
        b.hasMotor = clipboardData.hasMotor;
        b.motorSpeed = clipboardData.motorSpeed;
        b.setAngle(placementRotation); 

        if (clipboardData.logicType != null) {
            b.logicNode = new LogicNode(b, clipboardData.logicType);
            b.logicNode.massThreshold = clipboardData.massThreshold;
            b.logicNode.outputDistance = clipboardData.outputDistance;
            b.logicNode.outputSpeed = clipboardData.outputSpeed;
            b.logicNode.outputDirection = clipboardData.outputDir;
            b.logicNode.isToggleMode = clipboardData.isToggle;
            b.logicNode.delayTicks = clipboardData.delayTicks;
        }
        
        engine.addBall(b);
    }

    private void spawnEntity(Vector2D pos) {
        if (audioEngine != null) audioEngine.playSound("place", 1.0f, 1.0f);
        Ball b = null;

        switch (currentSpawn) {
            case PHYSICS_STEEL_CUBE -> { b = new Ball(pos.x, pos.y, 25, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); }
            case PHYSICS_WOOD_TRI -> { b = new Ball(pos.x, pos.y, 30, Material.WOOD); b.setShape(Ball.ShapeType.TRIANGLE); }
            case PHYSICS_RUBBER_BALL -> { b = new Ball(pos.x, pos.y, 15, Material.RUBBER); }
            case PHYSICS_GLASS -> { b = new Ball(pos.x, pos.y, 35, Material.GLASS); b.setShape(Ball.ShapeType.CUBE); }
            
            case INPUT_BUTTON -> { b = new Ball(pos.x, pos.y, 14, Material.STEEL); b.setShape(Ball.ShapeType.CIRCLE); b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_BUTTON); }
            case INPUT_PRESSURE_PLATE -> { b = new Ball(pos.x, pos.y, 30, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_PRESSURE_PLATE); }
            case INPUT_TRIPWIRE -> {
                Ball p1 = new Ball(pos.x - 60, pos.y, 10, Material.STEEL); p1.setShape(Ball.ShapeType.CUBE); p1.logicNode = new LogicNode(p1, LogicNode.NodeType.INPUT_TRIPWIRE);
                Ball p2 = new Ball(pos.x + 60, pos.y, 10, Material.STEEL); p2.setShape(Ball.ShapeType.CUBE); p2.logicNode = new LogicNode(p2, LogicNode.NodeType.INPUT_TRIPWIRE);
                p1.logicNode.pairedNode = p2.logicNode; p2.logicNode.pairedNode = p1.logicNode;
                CommandHistory.executeCommand(new PlaceBallCommand(engine, p1));
                CommandHistory.executeCommand(new PlaceBallCommand(engine, p2));
                engine.addConstraint(new Constraint(p1, p2, 120, 0.1, 0.05));
                return;
            }
            case INPUT_SENSOR -> { b = new Ball(pos.x, pos.y, 22, Material.GLASS); b.setShape(Ball.ShapeType.HEXAGON); b.isStatic = true; b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_SENSOR); }
            case INPUT_PROXIMITY -> { b = new Ball(pos.x, pos.y, 20, Material.GLASS); b.setShape(Ball.ShapeType.CIRCLE); b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_PROXIMITY); b.logicNode.outputDistance = 200; }
            case INPUT_SPEEDOMETER -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.HEXAGON); b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_SPEEDOMETER); b.logicNode.outputSpeed = 500; }

            case INPUT_KEY_W -> { b = new Ball(pos.x, pos.y, 18, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_KEY_W); }
            case INPUT_KEY_A -> { b = new Ball(pos.x, pos.y, 18, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_KEY_A); }
            case INPUT_KEY_S -> { b = new Ball(pos.x, pos.y, 18, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_KEY_S); }
            case INPUT_KEY_D -> { b = new Ball(pos.x, pos.y, 18, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.INPUT_KEY_D); }

            case OUTPUT_DOOR -> { b = new Ball(pos.x, pos.y, 40, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.isStatic = true; b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_DOOR); }
            case OUTPUT_PLATFORM -> {
                b = new Ball(pos.x, pos.y, 40, Material.STEEL); b.isStatic = true;
                b.setCustomVertices(new Vector2D[]{ new Vector2D(-80, -10), new Vector2D(80, -10), new Vector2D(80, 10), new Vector2D(-80, 10) });
                b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_PLATFORM); b.logicNode.outputDirection = new Vector2D(1, 0); b.logicNode.outputDistance = 300.0;
            }
            case OUTPUT_PISTON -> { b = new Ball(pos.x, pos.y, 25, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.isStatic = true; b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_PISTON); b.logicNode.outputSpeed = 2000.0; }
            case OUTPUT_CANNON -> { b = new Ball(pos.x, pos.y, 35, Material.STEEL); b.setShape(Ball.ShapeType.HEXAGON); b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_CANNON); b.logicNode.outputSpeed = 2000.0; }
            case OUTPUT_THRUSTER -> {
                b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setCustomVertices(new Vector2D[]{ new Vector2D(-15, 20), new Vector2D(15, 20), new Vector2D(10, -20), new Vector2D(-10, -20) });
                b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_THRUSTER); b.logicNode.outputDirection = new Vector2D(0, -1); b.logicNode.outputSpeed = 1500.0;
            }
            case OUTPUT_TNT -> { b = new Ball(pos.x, pos.y, 25, Material.WOOD); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_TNT); }
            case OUTPUT_MOTOR -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.CIRCLE); b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_MOTOR); b.logicNode.outputSpeed = 500.0; }
            case OUTPUT_LASER -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.isStatic = true; b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_LASER); b.logicNode.outputDirection = new Vector2D(1, 0); }
            case OUTPUT_SPAWNER -> { b = new Ball(pos.x, pos.y, 30, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.isStatic = true; b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_SPAWNER); b.logicNode.outputDirection = new Vector2D(0, 1); }
            case OUTPUT_HOVER -> { b = new Ball(pos.x, pos.y, 25, Material.STEEL); b.setShape(Ball.ShapeType.HEXAGON); b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_HOVER); b.logicNode.outputSpeed = 200.0; }
            case OUTPUT_ATTRACTOR -> { b = new Ball(pos.x, pos.y, 30, Material.STEEL); b.setShape(Ball.ShapeType.CIRCLE); b.isStatic = true; b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_ATTRACTOR); b.logicNode.outputSpeed = 1000.0; }
            case OUTPUT_VOID -> { b = new Ball(pos.x, pos.y, 35, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.isStatic = true; b.logicNode = new LogicNode(b, LogicNode.NodeType.OUTPUT_VOID); }

            case LOGIC_POWER_SOURCE -> { b = new Ball(pos.x, pos.y, 18, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.POWER_SOURCE); }
            case LOGIC_LIGHTBULB -> { b = new Ball(pos.x, pos.y, 18, Material.GLASS); b.setShape(Ball.ShapeType.CIRCLE); b.logicNode = new LogicNode(b, LogicNode.NodeType.LIGHTBULB); }
            case LOGIC_AND -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.HEXAGON); b.logicNode = new LogicNode(b, LogicNode.NodeType.GATE_AND); }
            case LOGIC_OR -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.HEXAGON); b.logicNode = new LogicNode(b, LogicNode.NodeType.GATE_OR); }
            case LOGIC_NOT -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.TRIANGLE); b.logicNode = new LogicNode(b, LogicNode.NodeType.GATE_NOT); }
            case LOGIC_XOR -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.HEXAGON); b.logicNode = new LogicNode(b, LogicNode.NodeType.GATE_XOR); }
            case LOGIC_TOGGLE -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.GATE_TOGGLE); }
            case GATE_DELAY -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.GATE_DELAY); }
            case GATE_TIMER -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.GATE_TIMER); }
            case GATE_COUNTER -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.GATE_COUNTER); }
            case LOGIC_SR_LATCH -> { b = new Ball(pos.x, pos.y, 20, Material.STEEL); b.setShape(Ball.ShapeType.CUBE); b.logicNode = new LogicNode(b, LogicNode.NodeType.SR_LATCH); }
            case LOGIC_RELAY -> { b = new Ball(pos.x, pos.y, 10, Material.STEEL); b.setShape(Ball.ShapeType.CIRCLE); b.logicNode = new LogicNode(b, LogicNode.NodeType.RELAY); }
        }

        if (b != null) {
            b.setAngle(placementRotation);
            CommandHistory.executeCommand(new PlaceBallCommand(engine, b));
        }
    }

    private void setupKeyboardControls() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) isPlaying = !isPlaying;
                if (e.getKeyCode() == KeyEvent.VK_T) timeScale = (timeScale == 1.0) ? 0.1 : 1.0;
                if (e.getKeyCode() == KeyEvent.VK_Z && e.isControlDown()) {
                    if (e.isShiftDown()) CommandHistory.redo();
                    else CommandHistory.undo();
                }
                if (e.getKeyCode() == KeyEvent.VK_Y && e.isControlDown()) CommandHistory.redo();

                if (e.getKeyCode() == KeyEvent.VK_A) keyA = true; if (e.getKeyCode() == KeyEvent.VK_D) keyD = true;
                if (e.getKeyCode() == KeyEvent.VK_W) keyW = true; if (e.getKeyCode() == KeyEvent.VK_S) keyS = true;
                if (e.getKeyCode() == KeyEvent.VK_I) keyI = true; if (e.getKeyCode() == KeyEvent.VK_O) keyO = true;
                
                if (e.getKeyCode() == KeyEvent.VK_LEFT) keyLeft = true; 
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) keyRight = true;
                if (e.getKeyCode() == KeyEvent.VK_R) {
                    if (hoveredBall != null) hoveredBall.setAngle(Math.round((hoveredBall.getAngle() + Math.PI/4) / (Math.PI/4)) * (Math.PI/4));
                    else placementRotation += Math.PI / 4;
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
                    if (hoveredBall != null && hoveredBall != activePlayer) copyToClipboard(hoveredBall);
                }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                    if (clipboardData != null) pasteFromClipboard(currentMouseWorldPos);
                }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_S) {
                    savePrefab();
                }
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_L) {
                    loadPrefab();
                }

                if (currentMode == ToolMode.SHOOT) {
                    if (e.getKeyCode() == KeyEvent.VK_UP) shootForce += 100;
                    if (e.getKeyCode() == KeyEvent.VK_DOWN) shootForce = Math.max(100, shootForce - 100);
                }

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_1 -> { currentMode = ToolMode.PLACE; wireStartNode = null; linkStartNode = null; customShapePoints.clear(); }
                    case KeyEvent.VK_2 -> { currentMode = ToolMode.DELETE; wireStartNode = null; linkStartNode = null; customShapePoints.clear(); }
                    case KeyEvent.VK_3 -> { currentMode = ToolMode.SHOOT; wireStartNode = null; linkStartNode = null; customShapePoints.clear(); }
                    case KeyEvent.VK_4 -> { currentMode = ToolMode.CONFIGURE; wireStartNode = null; linkStartNode = null; customShapePoints.clear(); }
                    case KeyEvent.VK_5 -> { currentMode = ToolMode.LINK; wireStartNode = null; linkStartNode = null; customShapePoints.clear(); }
                    case KeyEvent.VK_6 -> { currentMode = ToolMode.DRAW; wireStartNode = null; linkStartNode = null; customShapePoints.clear(); } 
                    case KeyEvent.VK_ESCAPE -> { currentMode = ToolMode.DRAG; wireStartNode = null; linkStartNode = null; customShapePoints.clear(); }
                    case KeyEvent.VK_P -> spawnPlayer();
                    case KeyEvent.VK_F3 -> debugMode = !debugMode;
                    case KeyEvent.VK_M -> { 
                        engine.getBalls().clear(); engine.getConstraints().clear(); engine.getWeldConstraints().clear();
                        engine.getRevoluteJoints().clear(); engine.getParticles().clear();
                        engine.setMouseJoint(null); spawnPlayer(); wireStartNode = null; linkStartNode = null;
                        customShapePoints.clear();
                    }
                }
            }
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_A) keyA = false; if (e.getKeyCode() == KeyEvent.VK_D) keyD = false;
                if (e.getKeyCode() == KeyEvent.VK_W) keyW = false; if (e.getKeyCode() == KeyEvent.VK_S) keyS = false;
                if (e.getKeyCode() == KeyEvent.VK_I) keyI = false; if (e.getKeyCode() == KeyEvent.VK_O) keyO = false;
                
                if (e.getKeyCode() == KeyEvent.VK_LEFT) keyLeft = false; 
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) keyRight = false;
            }
        });
    }

    private void showSpawnSelectionMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        for (SpawnType t : SpawnType.values()) {
            JMenuItem item = new JMenuItem("Select: " + t.name().replace("_", " "));
            item.addActionListener(e -> currentSpawn = t);
            menu.add(item);
        }
        menu.show(this, x, y);
    }

    private void showLogicConfigMenu(int x, int y, LogicNode node) {
        JPopupMenu menu = new JPopupMenu("Logic Config: " + node.type.name());
        
        JMenuItem typeLabel = new JMenuItem("--- " + node.type.name() + " ---");
        typeLabel.setEnabled(false); menu.add(typeLabel); menu.addSeparator();

        if (node.type == LogicNode.NodeType.INPUT_BUTTON) {
            JCheckBoxMenuItem toggleItem = new JCheckBoxMenuItem("Button: Toggle Mode", node.isToggleMode);
            toggleItem.addActionListener(e -> node.isToggleMode = toggleItem.isSelected()); menu.add(toggleItem);
        }
        
        if (node.type == LogicNode.NodeType.INPUT_PROXIMITY) {
            JMenuItem rangeItem = new JMenuItem("Set Proximity Range (Current: " + node.outputDistance + ")");
            rangeItem.addActionListener(e -> { String val = JOptionPane.showInputDialog("Enter range in pixels:", node.outputDistance); if (val != null) try { node.outputDistance = Double.parseDouble(val); } catch(Exception ex){} });
            menu.add(rangeItem);
        }
        if (node.type == LogicNode.NodeType.INPUT_SPEEDOMETER) {
            JMenuItem speedItem = new JMenuItem("Set Speed Threshold (Current: " + node.outputSpeed + ")");
            speedItem.addActionListener(e -> { String val = JOptionPane.showInputDialog("Enter speed threshold:", node.outputSpeed); if (val != null) try { node.outputSpeed = Double.parseDouble(val); } catch(Exception ex){} });
            menu.add(speedItem);
        }
        if (node.type == LogicNode.NodeType.INPUT_PRESSURE_PLATE) {
            JMenuItem massItem = new JMenuItem("Set Mass Threshold (Current: " + node.massThreshold + ")");
            massItem.addActionListener(e -> { String val = JOptionPane.showInputDialog("Enter mass limit (e.g. 5, 20):", node.massThreshold); if (val != null) try { node.massThreshold = Double.parseDouble(val); } catch(Exception ex){} });
            menu.add(massItem);
        }

        if (node.type == LogicNode.NodeType.GATE_DELAY) {
            JMenuItem delayItem = new JMenuItem("Set Delay Ticks (Current: " + node.delayTicks + " max 60)");
            delayItem.addActionListener(e -> {
                String val = JOptionPane.showInputDialog("Enter delay in ticks (60 ticks = 1 second):", node.delayTicks);
                if (val != null) { try { node.delayTicks = Math.max(1, Math.min(60, Integer.parseInt(val))); } catch(Exception ex){} }
            });
            menu.add(delayItem);
        }

        if (node.type == LogicNode.NodeType.GATE_TIMER) {
            JMenuItem timerItem = new JMenuItem("Set Timer Interval (Current: " + node.timerIntervalTicks + " ticks)");
            timerItem.addActionListener(e -> {
                String val = JOptionPane.showInputDialog("Enter interval in ticks (60 ticks = 1 second):", node.timerIntervalTicks);
                if (val != null) { try { node.timerIntervalTicks = Math.max(1, Integer.parseInt(val)); } catch(Exception ex){} }
            });
            menu.add(timerItem);
        }
        if (node.type == LogicNode.NodeType.GATE_COUNTER) {
            JMenuItem counterItem = new JMenuItem("Set Counter Target (Current: " + node.counterTarget + " pulses)");
            counterItem.addActionListener(e -> { String val = JOptionPane.showInputDialog("Enter target pulse count:", node.counterTarget); if (val != null) try { node.counterTarget = Math.max(1, Integer.parseInt(val)); } catch(Exception ex){} });
            menu.add(counterItem);
        }

        if (node.type == LogicNode.NodeType.OUTPUT_DOOR || node.type == LogicNode.NodeType.OUTPUT_PLATFORM || 
            node.type == LogicNode.NodeType.OUTPUT_PISTON || node.type == LogicNode.NodeType.OUTPUT_CANNON || 
            node.type == LogicNode.NodeType.OUTPUT_THRUSTER || node.type == LogicNode.NodeType.OUTPUT_MOTOR ||
            node.type == LogicNode.NodeType.OUTPUT_LASER || node.type == LogicNode.NodeType.OUTPUT_SPAWNER) {
            
            if (node.type != LogicNode.NodeType.OUTPUT_MOTOR) {
                JMenuItem dirItem = new JMenuItem("Set Direction (Current: " + node.outputDirection.x + "," + node.outputDirection.y + ")");
                dirItem.addActionListener(e -> {
                    String val = JOptionPane.showInputDialog("Enter X,Y (e.g. 0,-1 for UP):", node.outputDirection.x + "," + node.outputDirection.y);
                    if (val != null && val.contains(",")) { try { String[] pts = val.split(","); node.outputDirection = new Vector2D(Double.parseDouble(pts[0]), Double.parseDouble(pts[1])).normalize(); } catch (Exception ex) {} }
                });
                menu.add(dirItem);
            }

            if (node.type == LogicNode.NodeType.OUTPUT_DOOR || node.type == LogicNode.NodeType.OUTPUT_PLATFORM || node.type == LogicNode.NodeType.OUTPUT_PISTON) {
                JMenuItem distItem = new JMenuItem("Set Travel Distance (Current: " + node.outputDistance + ")");
                distItem.addActionListener(e -> { String val = JOptionPane.showInputDialog("Enter distance in pixels:", node.outputDistance); if (val != null) try { node.outputDistance = Double.parseDouble(val); } catch(Exception ex){} });
                menu.add(distItem);
            }

            if (node.type != LogicNode.NodeType.OUTPUT_LASER && node.type != LogicNode.NodeType.OUTPUT_SPAWNER) {
                JMenuItem speedItem = new JMenuItem("Set Power/Speed (Current: " + node.outputSpeed + ")");
                speedItem.addActionListener(e -> { String val = JOptionPane.showInputDialog("Enter new power multiplier:", node.outputSpeed); if (val != null) try { node.outputSpeed = Double.parseDouble(val); } catch(Exception ex){} });
                menu.add(speedItem);
            }
        }
        
        if (node.type == LogicNode.NodeType.OUTPUT_HOVER || node.type == LogicNode.NodeType.OUTPUT_ATTRACTOR) {
            JMenuItem speedItem = new JMenuItem("Set Lift/Pull Power (Current: " + node.outputSpeed + ")");
            speedItem.addActionListener(e -> { String val = JOptionPane.showInputDialog("Enter power multiplier:", node.outputSpeed); if (val != null) try { node.outputSpeed = Double.parseDouble(val); } catch(Exception ex){} });
            menu.add(speedItem);
        }

        JMenuItem clearItem = new JMenuItem("Clear All Wires");
        clearItem.addActionListener(e -> {
            for (LogicNode out : node.connectedOutputs) out.connectedInputs.remove(node);
            for (LogicNode in : node.connectedInputs) in.connectedOutputs.remove(node);
            node.connectedInputs.clear(); node.connectedOutputs.clear();
        });
        
        menu.add(clearItem); menu.show(this, x, y);
    }

    private void showContextMenu(int x, int y, Ball ball) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem sizeItem = new JMenuItem("Edit Radius (Current: " + ball.getRadius() + ")");
        sizeItem.addActionListener(e -> { String val = JOptionPane.showInputDialog("Enter new radius:", ball.getRadius()); if (val != null) try { ball.setRadius(Double.parseDouble(val)); } catch(Exception ex){} });
        JCheckBoxMenuItem staticItem = new JCheckBoxMenuItem("Lock in Place (Static)", ball.isStatic);
        staticItem.addActionListener(e -> { ball.isStatic = staticItem.isSelected(); ball.wakeUp(); });
        JCheckBoxMenuItem motorItem = new JCheckBoxMenuItem("Enable Constant Spin", ball.hasMotor);
        motorItem.addActionListener(e -> {
            ball.hasMotor = motorItem.isSelected();
            if (ball.hasMotor) { String val = JOptionPane.showInputDialog("Enter Constant Spin Speed:", ball.motorSpeed == 0 ? 5.0 : ball.motorSpeed); if (val != null) { try { ball.motorSpeed = Double.parseDouble(val); } catch(Exception ex) { ball.hasMotor = false; } } else ball.hasMotor = false;
            } else ball.motorSpeed = 0; ball.wakeUp();
        });
        menu.add(sizeItem); menu.addSeparator(); menu.add(staticItem); menu.add(motorItem); menu.show(this, x, y);
    }

    private Vector2D screenToWorld(Point p) { return new Vector2D((p.x - getWidth() / 2.0) / zoom + camX, (p.y - getHeight() / 2.0) / zoom + camY); }
    private Ball getBallAt(Vector2D worldPos) {
        for (Ball b : engine.getBalls()) if (b.getPosition().distanceSquared(worldPos) <= (b.getRadius() + 5) * (b.getRadius() + 5)) return b;
        return null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (engine.getMouseJoint() != null) engine.getMouseJoint().updateTarget(currentMouseWorldPos);
        if (keyI) targetZoom *= 1.02; if (keyO) targetZoom /= 1.02; zoom += (targetZoom - zoom) * 0.1;

        engine.keyW = keyW; engine.keyA = keyA; engine.keyS = keyS; engine.keyD = keyD;

        if (keyLeft) {
            if (hoveredBall != null) hoveredBall.setAngle(hoveredBall.getAngle() - 0.05);
            else placementRotation -= 0.05;
        }
        if (keyRight) {
            if (hoveredBall != null) hoveredBall.setAngle(hoveredBall.getAngle() + 0.05);
            else placementRotation += 0.05;
        }

        // Physics step removed here, handled by physicsThread
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        AffineTransform oldTx1 = g2d.getTransform();
        g2d.translate(getWidth() / 2.0, getHeight() / 2.0); g2d.scale(zoom, zoom); g2d.translate(-camX, -camY);
        synchronized(engine.getWaterZones()) {
            for (WaterZone wz : engine.getWaterZones()) {
                g2d.setColor(new Color(0, 100, 255, 80));
                g2d.fillRect((int)wz.bounds.minX, (int)wz.bounds.minY, (int)(wz.bounds.maxX - wz.bounds.minX), (int)(wz.bounds.maxY - wz.bounds.minY));
            }
        }
        g2d.setTransform(oldTx1);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform oldTx = g2d.getTransform();
        g2d.translate(getWidth() / 2.0, getHeight() / 2.0); g2d.scale(zoom, zoom); g2d.translate(-camX, -camY);

        g2d.setColor(GRID_COLOR); g2d.setStroke(new BasicStroke((float)(1.0/zoom))); 
        int gridSize = 100;
        int startX = (int) ((camX - getWidth()/(2*zoom)) / gridSize) * gridSize;
        int startY = (int) ((camY - getHeight()/(2*zoom)) / gridSize) * gridSize;
        for (int x = startX - gridSize; x < camX + getWidth()/(2*zoom); x += gridSize)
            g2d.drawLine(x, (int)(camY - getHeight()/(2*zoom) - gridSize), x, (int)(camY + getHeight()/(2*zoom) + gridSize));
        for (int y = startY - gridSize; y < camY + getHeight()/(2*zoom); y += gridSize)
            g2d.drawLine((int)(camX - getWidth()/(2*zoom) - gridSize), y, (int)(camX + getWidth()/(2*zoom) + gridSize), y);

        g2d.setColor(WALL_COLOR); g2d.setStroke(WALL_STROKE);
        synchronized(engine.getWalls()) {
            for (WallBody wall : engine.getWalls()) g2d.drawLine((int)wall.p1.x, (int)wall.p1.y, (int)wall.p2.x, (int)wall.p2.y);
        }

        g2d.setColor(new Color(255, 140, 0, 180)); 
        g2d.setStroke(new BasicStroke(10.0f / (float)zoom, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        synchronized(engine.getWeldConstraints()) {
            for(WeldConstraint w : engine.getWeldConstraints()) {
                g2d.drawLine((int)w.a.getPosition().x, (int)w.a.getPosition().y, (int)w.b.getPosition().x, (int)w.b.getPosition().y);
            }
        }

        g2d.setColor(new Color(150, 150, 150)); 
        g2d.setStroke(new BasicStroke(6.0f / (float)zoom, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        synchronized(engine.getConstraints()) {
            for(Constraint c : engine.getConstraints()) {
                if(c.a.logicNode != null && c.a.logicNode.type == LogicNode.NodeType.INPUT_TRIPWIRE) continue;
                g2d.drawLine((int)c.a.getPosition().x, (int)c.a.getPosition().y, (int)c.b.getPosition().x, (int)c.b.getPosition().y);
            }
        }

        synchronized(engine.getBalls()) {
            for (Ball b : engine.getBalls()) {
            if (b.logicNode != null && b.logicNode.type == LogicNode.NodeType.OUTPUT_LASER && b.logicNode.laserEndPos != null) {
                g2d.setColor(new Color(255, 0, 0, 160)); 
                g2d.setStroke(new BasicStroke(6.0f / (float)zoom));
                Vector2D start = b.getPosition().add(b.logicNode.outputDirection.multiply(b.getRadius()));
                g2d.drawLine((int)start.x, (int)start.y, (int)b.logicNode.laserEndPos.x, (int)b.logicNode.laserEndPos.y);
            }
        }

            for (Ball b : engine.getBalls()) {
                if (b.logicNode != null && b.logicNode.type == LogicNode.NodeType.INPUT_TRIPWIRE && b.logicNode.pairedNode != null && !b.logicNode.pairedNode.parentBody.isDestroyed) {
                    if (b.hashCode() < b.logicNode.pairedNode.hashCode()) {
                        g2d.setColor(b.logicNode.currentState ? new Color(255, 0, 0, 50) : Color.RED);
                        g2d.setStroke(new BasicStroke(2.0f / (float)zoom));
                        g2d.drawLine((int)b.getPosition().x, (int)b.getPosition().y, (int)b.logicNode.pairedNode.parentBody.getPosition().x, (int)b.logicNode.pairedNode.parentBody.getPosition().y);
                    }
                }
            }

            for (Ball b : engine.getBalls()) {
                if (b.logicNode != null) {
                if (b.logicNode.type == LogicNode.NodeType.OUTPUT_PISTON && b.originalPosition != null) {
                    g2d.setColor(Color.GRAY); g2d.setStroke(new BasicStroke(16.0f / (float)zoom));
                    g2d.drawLine((int)b.originalPosition.x, (int)b.originalPosition.y, (int)b.getPosition().x, (int)b.getPosition().y);
                    g2d.setColor(STATIC_COLOR); g2d.fillRect((int)b.originalPosition.x - 15, (int)b.originalPosition.y - 15, 30, 30);
                }
                if (b.logicNode.type == LogicNode.NodeType.OUTPUT_CANNON) {
                    g2d.setColor(Color.DARK_GRAY); g2d.setStroke(new BasicStroke(20.0f / (float)zoom));
                    Vector2D barrelEnd = b.getPosition().add(b.logicNode.outputDirection.multiply(b.getRadius() + 15));
                    g2d.drawLine((int)b.getPosition().x, (int)b.getPosition().y, (int)barrelEnd.x, (int)barrelEnd.y);
                }
                if (b.logicNode.type == LogicNode.NodeType.OUTPUT_SPAWNER) {
                    g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(4.0f / (float)zoom));
                    Vector2D dirEnd = b.getPosition().add(b.logicNode.outputDirection.multiply(b.getRadius() + 15));
                    g2d.drawLine((int)b.getPosition().x, (int)b.getPosition().y, (int)dirEnd.x, (int)dirEnd.y);
                }
            }
        }

            for (Ball b : engine.getBalls()) {
                if (b.logicNode != null) {
                    for (LogicNode target : b.logicNode.connectedOutputs) {
                        Vector2D p1 = b.getPosition(); Vector2D p2 = target.parentBody.getPosition();

                        // Bezier curve for wires
                        double dist = p1.distanceTo(p2);
                        Vector2D ctrl1 = p1.add(new Vector2D(0, dist * 0.5));
                        Vector2D ctrl2 = p2.add(new Vector2D(0, dist * 0.5));

                        java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
                        path.moveTo(p1.x, p1.y);
                        path.curveTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, p2.x, p2.y);

                        g2d.setColor(b.logicNode.currentState ? NEON_GREEN : Color.GRAY);
                        g2d.setStroke(new BasicStroke((b.logicNode.currentState ? 4.0f : 2.0f) / (float)zoom));
                        g2d.draw(path);
                    }
                }
            }
        
        if (currentMode == ToolMode.CONFIGURE && wireStartNode != null) {
            Vector2D p1 = wireStartNode.parentBody.getPosition();
            g2d.setColor(Color.YELLOW); g2d.setStroke(new BasicStroke(2.0f / (float)zoom));
            g2d.drawLine((int)p1.x, (int)p1.y, (int)currentMouseWorldPos.x, (int)currentMouseWorldPos.y);
        }
        if (currentMode == ToolMode.LINK && linkStartNode != null) {
            g2d.setColor(Color.ORANGE); g2d.setStroke(new BasicStroke(4.0f / (float)zoom, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawLine((int)linkStartNode.getPosition().x, (int)linkStartNode.getPosition().y, (int)currentMouseWorldPos.x, (int)currentMouseWorldPos.y);
        }

        if (currentMode == ToolMode.DRAW && customShapePoints.size() > 0) {
            g2d.setColor(new Color(255, 255, 255, 180));
            g2d.setStroke(new BasicStroke(3.0f / (float)zoom));
            for (int i = 0; i < customShapePoints.size() - 1; i++) {
                g2d.drawLine((int)customShapePoints.get(i).x, (int)customShapePoints.get(i).y, 
                             (int)customShapePoints.get(i+1).x, (int)customShapePoints.get(i+1).y);
            }
            g2d.drawLine((int)customShapePoints.get(customShapePoints.size()-1).x, (int)customShapePoints.get(customShapePoints.size()-1).y, 
                         (int)currentMouseWorldPos.x, (int)currentMouseWorldPos.y);
        }

        SpringConstraint mj = engine.getMouseJoint();
        if (mj != null) {
            g2d.setColor(NEON_GREEN); g2d.setStroke(new BasicStroke(3.0f / (float)zoom));
            Vector2D anchor = mj.getCurrentAnchor();
            g2d.drawLine((int)anchor.x, (int)anchor.y, (int)mj.target.x, (int)mj.target.y);
        }

        for (Ball ball : engine.getBalls()) {
            if (ball == activePlayer) g2d.setColor(Color.WHITE); 
            else if (ball.logicNode != null) {
                if (ball.logicNode.type == LogicNode.NodeType.POWER_SOURCE) g2d.setColor(new Color(0, 200, 50)); 
                else if (ball.logicNode.type == LogicNode.NodeType.OUTPUT_TNT) g2d.setColor(new Color(220, 50, 50)); 
                else if (ball.logicNode.type == LogicNode.NodeType.OUTPUT_HOVER) g2d.setColor(new Color(0, 255, 255)); 
                else if (ball.logicNode.type == LogicNode.NodeType.OUTPUT_ATTRACTOR) g2d.setColor(new Color(150, 0, 255)); 
                else if (ball.logicNode.type == LogicNode.NodeType.OUTPUT_VOID) g2d.setColor(new Color(0, 0, 0)); 
                else if (ball.logicNode.type == LogicNode.NodeType.INPUT_KEY_W || ball.logicNode.type == LogicNode.NodeType.INPUT_KEY_A || 
                         ball.logicNode.type == LogicNode.NodeType.INPUT_KEY_S || ball.logicNode.type == LogicNode.NodeType.INPUT_KEY_D) g2d.setColor(new Color(50, 100, 200)); 
                else if (ball.logicNode.type == LogicNode.NodeType.INPUT_SENSOR) g2d.setColor(ball.logicNode.currentState ? NEON_GREEN : new Color(100, 150, 255, 150)); 
                else if (ball.logicNode.currentState) g2d.setColor(NEON_GREEN); 
                else g2d.setColor(new Color(80, 80, 80)); 
                
                if (ball.logicNode.type == LogicNode.NodeType.INPUT_BUTTON && ball.logicNode.currentState) g2d.setColor(Color.RED);
            }
            else g2d.setColor(ball.isStatic ? STATIC_COLOR : DYNAMIC_COLOR);
            
            if (ball.shape == Ball.ShapeType.CIRCLE) {
                int r = (int) ball.getRadius();
                if (ball.logicNode != null && ball.logicNode.type == LogicNode.NodeType.OUTPUT_MOTOR) {
                    g2d.fillOval((int)ball.getPosition().x - r, (int)ball.getPosition().y - r, r*2, r*2);
                    g2d.setColor(Color.BLACK); g2d.setStroke(new BasicStroke(4.0f / (float)zoom));
                    g2d.drawLine((int)ball.getPosition().x, (int)ball.getPosition().y, (int)(ball.getPosition().x + r * Math.cos(ball.getAngle())), (int)(ball.getPosition().y + r * Math.sin(ball.getAngle())));
                } else {
                    g2d.fillOval((int)ball.getPosition().x - r, (int)ball.getPosition().y - r, r*2, r*2);
                    if (ball.logicNode != null && ball.logicNode.type == LogicNode.NodeType.OUTPUT_ATTRACTOR) {
                        g2d.setColor(Color.MAGENTA); g2d.setStroke(new BasicStroke(3.0f / (float)zoom));
                        g2d.drawOval((int)ball.getPosition().x - r, (int)ball.getPosition().y - r, r*2, r*2);
                    }
                }
            } else {
                Vector2D[] verts = ball.getTransformedVertices();
                int[] xP = new int[verts.length], yP = new int[verts.length];
                for(int i=0; i<verts.length; i++) { xP[i] = (int)verts[i].x; yP[i] = (int)verts[i].y; }
                g2d.fillPolygon(xP, yP, verts.length);
                
                if (ball.logicNode != null && ball.logicNode.type == LogicNode.NodeType.OUTPUT_VOID) {
                    g2d.setColor(Color.MAGENTA); g2d.setStroke(new BasicStroke(4.0f / (float)zoom));
                } else {
                    g2d.setColor(BG_COLOR); g2d.setStroke(new BasicStroke(1.0f / (float)zoom));
                }
                g2d.drawPolygon(xP, yP, verts.length);
            }

            if (ball.logicNode != null) {
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Monospaced", Font.BOLD, 12));
                String label = ball.logicNode.type.name()
                    .replace("GATE_", "").replace("LOGIC_", "").replace("POWER_SOURCE", "PWR").replace("LIGHTBULB", "OUT")
                    .replace("INPUT_KEY_", "KEY_").replace("INPUT_", "").replace("OUTPUT_", "");
                int strW = g2d.getFontMetrics().stringWidth(label);
                g2d.drawString(label, (int)ball.getPosition().x - strW/2, (int)ball.getPosition().y + 4);
            }
        }
        
            // Draw secondary state for SR Latch
            if (debugMode) {
                for (Ball b : engine.getBalls()) {
                    if (b.logicNode != null && b.logicNode.type == LogicNode.NodeType.SR_LATCH) {
                        g2d.setColor(b.logicNode.secondaryState ? Color.RED : Color.BLUE); // Indicate Q-bar state
                        g2d.fillOval((int)b.getPosition().x + (int)b.getRadius() - 5, (int)b.getPosition().y - (int)b.getRadius() - 5, 10, 10);
                    }
                }
            }
        }

        if (hoveredBall != null && currentMode != ToolMode.PLACE && currentMode != ToolMode.DRAW) {
            g2d.setColor(new Color(0, 255, 255, 180)); 
            g2d.setStroke(new BasicStroke(2.0f / (float)zoom));
            int cx = (int)hoveredBall.getPosition().x; int cy = (int)hoveredBall.getPosition().y;
            int radius = (int)hoveredBall.getRadius() + 15;
            g2d.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
            double angle = hoveredBall.getAngle();
            g2d.drawLine(cx, cy, (int)(cx + Math.cos(angle) * radius), (int)(cy + Math.sin(angle) * radius));
            g2d.fillOval(cx - 4, cy - 4, 8, 8);
        }

        for (Ball b : selectedBalls) {
            g2d.setColor(new Color(255, 255, 0, 180));
            g2d.setStroke(new BasicStroke(3.0f / (float)zoom));
            int cx = (int)b.getPosition().x; int cy = (int)b.getPosition().y;
            int radius = (int)b.getRadius() + 10;
            g2d.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        }

        if (selectionStart != null) {
            Vector2D worldStart = screenToWorld(selectionStart);
            g2d.setColor(new Color(255, 255, 255, 100));
            g2d.setStroke(new BasicStroke(1.0f / (float)zoom));
            double x = Math.min(worldStart.x, currentMouseWorldPos.x);
            double y = Math.min(worldStart.y, currentMouseWorldPos.y);
            double w = Math.abs(worldStart.x - currentMouseWorldPos.x);
            double h = Math.abs(worldStart.y - currentMouseWorldPos.y);
            g2d.drawRect((int)x, (int)y, (int)w, (int)h);
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.fillRect((int)x, (int)y, (int)w, (int)h);
        }

        if (currentMode == ToolMode.PLACE) {
            g2d.setColor(new Color(255, 255, 255, 120)); 
            g2d.setStroke(new BasicStroke(2.0f / (float)zoom));
            int px = (int)currentMouseWorldPos.x; int py = (int)currentMouseWorldPos.y;
            int pr = 30; 
            g2d.drawOval(px - pr, py - pr, pr * 2, pr * 2);
            g2d.drawLine(px, py, (int)(px + Math.cos(placementRotation) * pr), (int)(py + Math.sin(placementRotation) * pr));
        }

        synchronized(engine.getParticles()) {
            for (Particle p : engine.getParticles()) {
                int alpha = Math.max(0, Math.min(255, (int)(255 * (p.life / p.maxLife))));
                g2d.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
                int s = (int)p.size; g2d.fillRect((int)p.position.x - s/2, (int)p.position.y - s/2, s, s);
            }
        }

        if (currentMode == ToolMode.SHOOT && activePlayer != null) {
            Vector2D dir = currentMouseWorldPos.subtract(activePlayer.getPosition()).normalize();
            g2d.setColor(STATIC_COLOR); g2d.setStroke(new BasicStroke(2.0f / (float)zoom));
            g2d.drawLine((int)activePlayer.getPosition().x, (int)activePlayer.getPosition().y, (int)(activePlayer.getPosition().x + dir.x * 200), (int)(activePlayer.getPosition().y + dir.y * 200));
        }

        g2d.setTransform(oldTx);
        
        if (currentMode == ToolMode.SHOOT && activePlayer != null) {
            int screenX = (int) ((activePlayer.getPosition().x - camX) * zoom + getWidth() / 2.0);
            int screenY = (int) ((activePlayer.getPosition().y - camY) * zoom + getHeight() / 2.0);
            g2d.setColor(OVERLAY_COLOR); g2d.fillRoundRect(screenX - 100, screenY - 110, 200, 50, 10, 10);
            g2d.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2d.setColor(DYNAMIC_COLOR); g2d.drawString("SHOOT FORCE: " + (int)shootForce, screenX - 90, screenY - 90);
            g2d.setColor(Color.WHITE); g2d.drawString("[UP/DOWN] to adjust", screenX - 90, screenY - 70);
        }

        g2d.setColor(OVERLAY_COLOR); g2d.fillRect(10, 10, 430, 460); 
        g2d.setColor(Color.WHITE); g2d.setFont(UI_FONT); int y = 30;
        g2d.setColor(isPlaying ? NEON_GREEN : STATIC_COLOR);
        g2d.drawString(isPlaying ? "  SIMULATION RUNNING" : "  EDITOR PAUSED", 20, y);
        if (timeScale != 1.0) {
            g2d.setColor(Color.YELLOW);
            g2d.drawString(" [SLOW MOTION]", 220, y);
        }
        y += 30;
        
        g2d.setColor(Color.WHITE); g2d.drawString("CURRENT TOOL MODE:", 20, y); y += 20;
        g2d.setColor(currentMode == ToolMode.PLACE ? DYNAMIC_COLOR : Color.GRAY);    g2d.drawString("[1] PLACE: " + currentSpawn.name().replace("_", " "), 20, y); y += 20;
        g2d.setColor(currentMode == ToolMode.DELETE ? DYNAMIC_COLOR : Color.GRAY);   g2d.drawString("[2] DELETE (Click Entity)", 20, y); y += 20;
        g2d.setColor(currentMode == ToolMode.SHOOT ? DYNAMIC_COLOR : Color.GRAY);    g2d.drawString("[3] SHOOT (Force: " + shootForce + ")", 20, y); y += 20;
        g2d.setColor(currentMode == ToolMode.CONFIGURE ? DYNAMIC_COLOR : Color.GRAY);g2d.drawString("[4] WIRE & CONFIGURE LOGIC", 20, y); y += 20;
        g2d.setColor(currentMode == ToolMode.LINK ? DYNAMIC_COLOR : Color.GRAY);     g2d.drawString("[5] WELD (Click 2 objects to rigid-link)", 20, y); y += 20;
        g2d.setColor(currentMode == ToolMode.DRAW ? DYNAMIC_COLOR : Color.GRAY);     g2d.drawString("[6] DRAW SHAPE (Right-click to finish)", 20, y); y += 30;

        g2d.setColor(Color.WHITE); g2d.drawString("INSTRUCTIONS:", 20, y); y += 20;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString("[Ctrl+C / Ctrl+V] Copy & Paste hovered item", 20, y); y += 20; 
        g2d.drawString("[Ctrl+S / Ctrl+L] Save & Load selection", 20, y); y += 20;
        g2d.drawString("[LEFT/RIGHT/R] Rotate Entity", 20, y); y += 20; 
        g2d.drawString("[Ctrl+Z/Y] Undo/Redo | [T] Slow Motion", 20, y); y += 20;
        g2d.drawString("Drag in DRAG mode: Box Select", 20, y); y += 20;
        g2d.drawString("WASD: Fly/Control Logic | I/O: Zoom", 20, y);
    }

    private LWJGLRenderer renderer;
    private AudioEngine audioEngine;
    private boolean useLWJGL = false;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("lwjgl")) {
            new Main(1280, 720).runLWJGL();
        } else {
            JFrame frame = new JFrame("Physics Logic Sandbox");
            Main editor = new Main(1280, 720);
            frame.add(editor); frame.setExtendedState(JFrame.MAXIMIZED_BOTH); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); frame.setVisible(true);
        }
    }

    public void runLWJGL() {
        useLWJGL = true;
        renderer = new LWJGLRenderer();
        renderer.init(1280, 720);

        while (!renderer.shouldClose() && running) {
            renderer.render(engine, camX, camY, zoom);
            try { Thread.sleep(10); } catch (InterruptedException e) {}
        }

        running = false;
        renderer.cleanup();
        System.exit(0);
    }
}