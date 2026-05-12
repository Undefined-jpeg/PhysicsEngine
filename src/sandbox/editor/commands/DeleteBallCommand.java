package sandbox.editor.commands;
import sandbox.physics.*;
import sandbox.physics.constraints.*;
import sandbox.physics.joints.*;
import sandbox.logic.*;
import java.util.ArrayList;
import java.util.List;

public class DeleteBallCommand implements Command {
    private EngineContainer engine;
    private Ball ball;
    private List<Constraint> savedConstraints = new ArrayList<>();
    private List<WeldConstraint> savedWeldConstraints = new ArrayList<>();
    private List<RevoluteJoint> savedRevoluteJoints = new ArrayList<>();
    private List<LogicNode> savedConnectedInputs = new ArrayList<>();
    private List<LogicNode> savedConnectedOutputs = new ArrayList<>();

    public DeleteBallCommand(EngineContainer engine, Ball ball) {
        this.engine = engine;
        this.ball = ball;
    }

    @Override
    public void execute() {
        engine.getBalls().remove(ball);

        savedConstraints.clear();
        for (Constraint c : engine.getConstraints()) {
            if (c.a == ball || c.b == ball) savedConstraints.add(c);
        }
        engine.getConstraints().removeAll(savedConstraints);

        savedWeldConstraints.clear();
        for (WeldConstraint w : engine.getWeldConstraints()) {
            if (w.a == ball || w.b == ball) savedWeldConstraints.add(w);
        }
        engine.getWeldConstraints().removeAll(savedWeldConstraints);

        savedRevoluteJoints.clear();
        for (RevoluteJoint rj : engine.getRevoluteJoints()) {
            if (rj.a == ball || rj.b == ball) savedRevoluteJoints.add(rj);
        }
        engine.getRevoluteJoints().removeAll(savedRevoluteJoints);

        if (ball.logicNode != null) {
            savedConnectedInputs = new ArrayList<>(ball.logicNode.connectedInputs);
            savedConnectedOutputs = new ArrayList<>(ball.logicNode.connectedOutputs);
            for (LogicNode in : savedConnectedInputs) in.connectedOutputs.remove(ball.logicNode);
            for (LogicNode out : savedConnectedOutputs) out.connectedInputs.remove(ball.logicNode);
        }
    }

    @Override
    public void undo() {
        engine.addBall(ball);
        engine.getConstraints().addAll(savedConstraints);
        engine.getWeldConstraints().addAll(savedWeldConstraints);
        engine.getRevoluteJoints().addAll(savedRevoluteJoints);
        if (ball.logicNode != null) {
            for (LogicNode in : savedConnectedInputs) in.addConnectionTo(ball.logicNode);
            for (LogicNode out : ball.logicNode.connectedOutputs) {} // Re-add logic handled by addConnectionTo
            ball.logicNode.connectedInputs = new ArrayList<>(savedConnectedInputs);
            ball.logicNode.connectedOutputs = new ArrayList<>(savedConnectedOutputs);
            for (LogicNode in : savedConnectedInputs) {
                if (!in.connectedOutputs.contains(ball.logicNode)) in.connectedOutputs.add(ball.logicNode);
            }
            for (LogicNode out : savedConnectedOutputs) {
                if (!out.connectedInputs.contains(ball.logicNode)) out.connectedInputs.add(ball.logicNode);
            }
        }
    }
}
