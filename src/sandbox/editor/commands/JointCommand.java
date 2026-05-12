package sandbox.editor.commands;
import sandbox.physics.*;
import sandbox.physics.constraints.*;
import sandbox.physics.joints.*;
import sandbox.logic.*;
public class JointCommand implements Command {
    private EngineContainer engine;
    private Object joint; // WeldConstraint or Constraint or RevoluteJoint

    public JointCommand(EngineContainer engine, Object joint) {
        this.engine = engine;
        this.joint = joint;
    }

    @Override
    public void execute() {
        if (joint instanceof Constraint) engine.addConstraint((Constraint) joint);
        else if (joint instanceof WeldConstraint) engine.addWeldConstraint((WeldConstraint) joint);
        else if (joint instanceof RevoluteJoint) engine.addRevoluteJoint((RevoluteJoint) joint);
        else if (joint instanceof PrismaticJoint) engine.addPrismaticJoint((PrismaticJoint) joint);
    }

    @Override
    public void undo() {
        if (joint instanceof Constraint) engine.getConstraints().remove(joint);
        else if (joint instanceof WeldConstraint) engine.getWeldConstraints().remove(joint);
        else if (joint instanceof RevoluteJoint) engine.getRevoluteJoints().remove(joint);
        else if (joint instanceof PrismaticJoint) engine.getPrismaticJoints().remove(joint);
    }
}
