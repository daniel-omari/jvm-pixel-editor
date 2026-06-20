package com.danielomari.pixeleditor.commands;

import com.danielomari.pixeleditor.layers.Layer;
import com.danielomari.pixeleditor.layers.LayerStack;
import com.danielomari.pixeleditor.ui.CanvasPanel;

/**
 * Undoable "duplicate layer". The copy is already inserted (just above the
 * original) before this command is pushed; undo removes it, redo re-inserts it.
 */
public class DuplicateLayerCommand implements Command {
    private final CanvasPanel canvas;
    private final LayerStack stack;
    private final Layer copy;
    private final int index; // where the copy sits

    public DuplicateLayerCommand(CanvasPanel canvas, Layer copy, int index) {
        this.canvas = canvas;
        this.stack = canvas.getLayers();
        this.copy = copy;
        this.index = index;
    }

    @Override
    public void execute() {
        // The copy was already inserted before this command was pushed.
    }

    @Override
    public void undo() {
        int i = stack.indexOf(copy);
        if (i >= 0) stack.removeAt(i);
        stack.setActive(Math.max(0, Math.min(index - 1, stack.getSize() - 1)));
        refresh();
    }

    @Override
    public void redo() {
        stack.insertAt(Math.min(index, stack.getSize()), copy); // becomes active
        refresh();
    }

    private void refresh() {
        canvas.notifyLayersChanged();
        canvas.repaint();
    }
}
