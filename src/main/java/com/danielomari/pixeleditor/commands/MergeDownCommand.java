package com.danielomari.pixeleditor.commands;

import com.danielomari.pixeleditor.layers.Layer;
import com.danielomari.pixeleditor.layers.LayerStack;
import com.danielomari.pixeleditor.ui.CanvasPanel;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Undoable "merge down": the active layer was composited into the layer below it
 * and then removed. Undo restores the lower layer's pixels and re-inserts the
 * upper layer; redo re-applies the merged pixels and removes the upper layer.
 */
public class MergeDownCommand implements Command {
    private final CanvasPanel canvas;
    private final LayerStack stack;
    private final Layer upper;
    private final int upperIndex;
    private final Layer lower;
    private final BufferedImage lowerBefore;
    private final BufferedImage lowerAfter;

    public MergeDownCommand(CanvasPanel canvas, Layer upper, int upperIndex, Layer lower,
                            BufferedImage lowerBefore, BufferedImage lowerAfter) {
        this.canvas = canvas;
        this.stack = canvas.getLayers();
        this.upper = upper;
        this.upperIndex = upperIndex;
        this.lower = lower;
        this.lowerBefore = lowerBefore;
        this.lowerAfter = lowerAfter;
    }

    @Override
    public void execute() {
        // The merge + removal were already done before this command was pushed.
    }

    @Override
    public void undo() {
        lower.setImage(copy(lowerBefore));
        stack.insertAt(upperIndex, upper);
        stack.setActive(upperIndex);
        refresh();
    }

    @Override
    public void redo() {
        lower.setImage(copy(lowerAfter));
        int i = stack.indexOf(upper);
        if (i >= 0) stack.removeAt(i);
        stack.setActive(Math.max(0, upperIndex - 1));
        refresh();
    }

    private void refresh() {
        canvas.notifyLayersChanged();
        canvas.repaint();
    }

    private static BufferedImage copy(BufferedImage s) {
        BufferedImage c = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(s, 0, 0, null);
        g.dispose();
        return c;
    }
}
