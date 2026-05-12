import java.util.ArrayList;
import java.util.List;

public class SaveData {
    public List<SavedBall> balls = new ArrayList<>();
    public List<SavedJoint> joints = new ArrayList<>();
    public List<SavedWire> wires = new ArrayList<>();
    
    public static class SavedBall {
        public int id;
        public double x, y, radius, angle;
        public String materialType;
        public String shapeType;
        public boolean isStatic;
        
        // For custom drawn polygons
        public double[] customVertsX;
        public double[] customVertsY;
        
        public String logicNodeType; 
        public double nodeOutputSpeed;
        public int nodeDelayTicks;
    }

    public static class SavedJoint {
        public String type; 
        public int ballIdA;
        public int ballIdB;
        public double worldAnchorAX, worldAnchorAY;
        public double worldAnchorBX, worldAnchorBY;
        public double springRestLength;
    }

    public static class SavedWire {
        public int outputNodeBallId;
        public int inputNodeBallId;
    }

    public static class Prefab {
        public List<SavedBall> balls = new ArrayList<>();
        public List<SavedJoint> joints = new ArrayList<>();
        public List<SavedWire> wires = new ArrayList<>();
    }
}