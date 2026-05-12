import java.util.ArrayList;
import java.util.List;

public class PlaceBallCommand implements Command {
    private EngineContainer engine;
    private Ball ball;

    public PlaceBallCommand(EngineContainer engine, Ball ball) {
        this.engine = engine;
        this.ball = ball;
    }

    @Override
    public void execute() {
        engine.addBall(ball);
    }

    @Override
    public void undo() {
        engine.getBalls().remove(ball);
        // Clean up wires/constraints if any
        if (ball.logicNode != null) {
            for (LogicNode in : ball.logicNode.connectedInputs) in.connectedOutputs.remove(ball.logicNode);
            for (LogicNode out : ball.logicNode.connectedOutputs) out.connectedInputs.remove(ball.logicNode);
        }
        engine.getConstraints().removeIf(c -> c.a == ball || c.b == ball);
        engine.getWeldConstraints().removeIf(w -> w.a == ball || w.b == ball);
        engine.getRevoluteJoints().removeIf(rj -> rj.a == ball || rj.b == ball);
    }
}
