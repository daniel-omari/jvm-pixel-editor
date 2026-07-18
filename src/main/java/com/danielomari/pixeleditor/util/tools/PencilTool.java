package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.Drawcommand;
import com.danielomari.pixeleditor.ui.CanvasPanel;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Pencil tool: a hard (aliased) round stroke with a configurable size and
 * opacity. Like the brush, the stroke is painted to a buffer and composited
 * once at the chosen opacity so it doesn't darken where segments overlap.
 */
public class PencilTool implements Tool {
    private static int sizePx = 2;
    private static float opacity = 1f;

    private int prevX = -1, prevY = -1;
    private CanvasPanel canvas;
    private BufferedImage strokeBuffer;
    private Graphics2D strokeG;
    private Drawcommand currentCommand;
    private boolean isDrawing = false;

    private final Consumer<Graphics2D> previewListener = g -> {
        if (strokeBuffer == null || canvas == null) return;
        // The shared Graphics is already translated to the document origin and
        // zoomed, so the buffer draws at (0, 0).
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampOpacity()));
        g2.drawImage(strokeBuffer, 0, 0, null);
        g2.dispose();
    };

    public static void setSize(int px) { if (px > 0) sizePx = px; }
    public static int getSize() { return sizePx; }
    public static void setOpacity(float o) { opacity = Math.max(0f, Math.min(1f, o)); }
    public static float getOpacity() { return opacity; }
    private static float clampOpacity() { return Math.max(0f, Math.min(1f, opacity)); }

    @Override
    public void onPress(MouseEvent e) {
        canvas = CanvasPanel.getInstance();
        currentCommand = new Drawcommand(canvas);
        BufferedImage layer = canvas.getCanvasImage();
        strokeBuffer = new BufferedImage(layer.getWidth(), layer.getHeight(), BufferedImage.TYPE_INT_ARGB);
        strokeG = strokeBuffer.createGraphics(); // no antialiasing: hard pencil edges
        canvas.addPaintListener(previewListener);
        isDrawing = true;
        prevX = e.getX();
        prevY = e.getY();
        paintSegment(prevX, prevY, prevX, prevY);
        canvas.repaint();
    }

    @Override
    public void onDrag(MouseEvent e) {
        if (!isDrawing) return;
        paintSegment(prevX, prevY, e.getX(), e.getY());
        prevX = e.getX();
        prevY = e.getY();
        canvas.repaint();
    }

    @Override
    public void onRelease(MouseEvent e) {
        if (!isDrawing) return;
        paintSegment(prevX, prevY, e.getX(), e.getY());

        Graphics2D lg = canvas.getCanvasImage().createGraphics();
        lg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampOpacity()));
        lg.drawImage(strokeBuffer, 0, 0, null);
        lg.dispose();

        canvas.removePaintListener(previewListener);
        if (strokeG != null) strokeG.dispose();
        strokeBuffer = null;
        strokeG = null;
        prevX = prevY = -1;
        isDrawing = false;

        if (currentCommand != null) {
            currentCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(currentCommand);
            currentCommand = null;
        }
        canvas.repaint();
    }

    private void paintSegment(int x1, int y1, int x2, int y2) {
        if (strokeG == null) return;
        strokeG.setColor(ColorTool.getColor());
        strokeG.setStroke(new BasicStroke(sizePx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        strokeG.drawLine(x1, y1, x2, y2);
    }

    public void setCanvas(CanvasPanel canvas) { this.canvas = canvas; }
}
