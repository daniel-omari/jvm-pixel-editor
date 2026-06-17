package com.danielomari.pixeleditor.commands;

import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.util.tools.SelectTool;

import java.util.Stack;
import java.util.ArrayList;

public class CommandManager {
    private static CommandManager instance;
    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();

    private CommandManager() {
    }

    public static CommandManager getInstance() {
        if (instance == null) {
            instance = new CommandManager();
        }
        return instance;
    }

    public void executeCommand(Command command) {
        undoStack.push(command);
        redoStack.clear();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public void showStack() {
//        System.out.println("Undo Stack" + undoStack + "Redo Stack" + redoStack); // For debugging
        }

    public void undo() {
        if (!undoStack.isEmpty()) {
            Command command = undoStack.pop();
            command.undo();
            SelectTool.getInstance().clearSelection();
            CanvasPanel.getInstance().repaint();
            redoStack.push(command);
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            Command command = redoStack.pop();
            command.redo();
            // Mirror undo(): clear any stale selection and refresh the canvas.
            SelectTool.getInstance().clearSelection();
            CanvasPanel.getInstance().repaint();
            undoStack.push(command);
        }
    }

    public boolean canUndo() { // Not used.
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
}
