import java.util.Stack;

public class CommandHistory {
    private static Stack<Command> undoStack = new Stack<>();
    private static Stack<Command> redoStack = new Stack<>();
    private static final int MAX_HISTORY = 50;

    public static void executeCommand(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.remove(0);
        }
    }

    public static void undo() {
        if (!undoStack.isEmpty()) {
            Command command = undoStack.pop();
            command.undo();
            redoStack.push(command);
        }
    }

    public static void redo() {
        if (!redoStack.isEmpty()) {
            Command command = redoStack.pop();
            command.execute();
            undoStack.push(command);
        }
    }
}
