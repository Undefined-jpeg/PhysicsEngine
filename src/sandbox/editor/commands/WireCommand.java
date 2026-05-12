package sandbox.editor.commands;
import sandbox.physics.*;
import sandbox.physics.constraints.*;
import sandbox.physics.joints.*;
import sandbox.logic.*;
public class WireCommand implements Command {
    private LogicNode output;
    private LogicNode input;

    public WireCommand(LogicNode output, LogicNode input) {
        this.output = output;
        this.input = input;
    }

    @Override
    public void execute() {
        output.addConnectionTo(input);
    }

    @Override
    public void undo() {
        output.connectedOutputs.remove(input);
        input.connectedInputs.remove(output);
    }
}
