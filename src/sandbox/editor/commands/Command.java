package sandbox.editor.commands;
import sandbox.physics.*;
import sandbox.physics.constraints.*;
import sandbox.physics.joints.*;
import sandbox.logic.*;
public interface Command {
    void execute();
    void undo();
}
