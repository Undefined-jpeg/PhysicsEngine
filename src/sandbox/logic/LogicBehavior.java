package sandbox.logic;
import sandbox.core.*;
import sandbox.physics.*;
import sandbox.physics.constraints.*;
import java.awt.Graphics2D;
import javax.swing.JPopupMenu;

public interface LogicBehavior {
    // Called before main evaluation, for inputs like sensors or key presses
    void preEvaluate(LogicNode node, EngineContainer engine, double dt);
    // Main evaluation logic for gates and other processing nodes
    void evaluate(LogicNode node, EngineContainer engine, double dt);
    // Called after main evaluation, for outputs like doors, cannons, etc.
    void postEvaluate(LogicNode node, EngineContainer engine, double dt);
}