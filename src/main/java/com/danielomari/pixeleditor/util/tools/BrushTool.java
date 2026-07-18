package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.Drawcommand;
import com.danielomari.pixeleditor.ui.CanvasPanel;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Brush tool: four styles (natural, spray, dotted, oil), a continuous size, and
 * a stroke opacity. The whole stroke is painted to an off-screen buffer at full
 * strength and composited onto the active layer once, at the chosen opacity, so
 * overlapping segments don't darken at the joints. Stamp-based styles space their
 * dabs by distance travelled along the whole stroke (not per segment), so the
 * spacing stays consistent no matter how fast or slow you draw.
 */
public class BrushTool implements Tool {
    public enum BrushType { option1, option2, option3, option4 }

    private static BrushType selectedBrushType = BrushType.option1;
    private static int sizePx = 10;
    private static float opacity = 1f;

    private int prevX = -1, prevY = -1;
    private double leftover = 0; // distance until the next stamp (carried across segments)
    private CanvasPanel canvas;
    private BufferedImage strokeBuffer; // the in-progress stroke (full strength)
    private Graphics2D strokeG;
    private Drawcommand currentCommand;
    private boolean isDrawing = false;

    // Live preview: draw the buffer over the canvas at the chosen opacity.
    private final Consumer<Graphics2D> previewListener = g -> {
        if (strokeBuffer == null || canvas == null) return;
        // The shared Graphics is already translated to the document origin and
        // zoomed, so the buffer draws at (0, 0).
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampOpacity()));
        g2.drawImage(strokeBuffer, 0, 0, null);
        g2.dispose();
    };

    // ---- settings ----
    public static void setBrushType(BrushType t) { selectedBrushType = t; }
    public static BrushType getBrushType() { return selectedBrushType; }
    public static void setSizePx(int px) { if (px > 0) sizePx = px; }
    public static int getSizePx() { return sizePx; }
    public static void setOpacity(float o) { opacity = Math.max(0f, Math.min(1f, o)); }
    public static float getOpacity() { return opacity; }
    private static float clampOpacity() { return Math.max(0f, Math.min(1f, opacity)); }

    @Override
    public void onPress(MouseEvent e) {
        canvas = CanvasPanel.getInstance();
        currentCommand = new Drawcommand(canvas);
        BufferedImage layer = canvas.getCanvasImage();
        strokeBuffer = new BufferedImage(layer.getWidth(), layer.getHeight(), BufferedImage.TYPE_INT_ARGB);
        strokeG = strokeBuffer.createGraphics();
        strokeG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.addPaintListener(previewListener);
        isDrawing = true;
        leftover = 0; // stamp the very first dab immediately
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

        // Composite the finished stroke onto the active layer once, at the opacity.
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

    // Paint one stroke segment onto the buffer at full strength.
    private void paintSegment(int x1, int y1, int x2, int y2) {
        if (strokeG == null) return;
        int size = sizePx;
        strokeG.setColor(ColorTool.getColor());

        if (selectedBrushType == BrushType.option1) { // natural: one connected line
            strokeG.setStroke(new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            strokeG.drawLine(x1, y1, x2, y2);
            return;
        }

        // Stamp-based styles: dotted wants clear gaps; spray/oil stay dense.
        double spacing = (selectedBrushType == BrushType.option3)
                ? Math.max(3.0, size)        // dotted
                : Math.max(1.0, size * 0.25); // spray / oil
        stampAlong(x1, y1, x2, y2, spacing, size);
    }

    // Place stamps every `spacing` pixels measured along the whole stroke, carrying
    // the remainder ("leftover") between segments so the rhythm never resets.
    private void stampAlong(int x1, int y1, int x2, int y2, double spacing, int size) {
        double dx = x2 - x1, dy = y2 - y1;
        double dist = Math.hypot(dx, dy);
        if (dist < 1e-9) { // a single click / the initial dab
            if (leftover <= 0) { stamp(strokeG, x1, y1, size); leftover = spacing; }
            return;
        }
        double ux = dx / dist, uy = dy / dist;
        double pos = 0;
        while (leftover <= dist - pos) {
            pos += leftover;
            stamp(strokeG, (int) Math.round(x1 + ux * pos), (int) Math.round(y1 + uy * pos), size);
            leftover = spacing;
        }
        leftover -= (dist - pos);
    }

    private void stamp(Graphics2D g, int px, int py, int size) {
        switch (selectedBrushType) {
            case option2 -> { // spray: random dot cluster
                for (int j = 0; j < 3; j++) {
                    int ox = (int) (Math.random() * size - size / 2.0);
                    int oy = (int) (Math.random() * size - size / 2.0);
                    int dot = (int) (size * 0.5 + Math.random() * 2);
                    g.fill(new Ellipse2D.Double(px + ox, py + oy, dot, dot));
                }
            }
            case option3 -> { // dotted: one round dot per step, with clear gaps
                double dot = Math.max(2.0, size * 0.5);
                g.fill(new Ellipse2D.Double(px - dot / 2.0, py - dot / 2.0, dot, dot));
            }
            case option4 -> { // oil: translucent bristle cluster
                Color base = ColorTool.getColor();
                for (int i = 0; i < 10; i++) {
                    int ox = (int) (Math.random() * size - size / 2.0);
                    int oy = (int) (Math.random() * size - size / 2.0);
                    int alpha = (int) (Math.random() * 150 + 100);
                    g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                    g.fillOval(px + ox, py + oy, size, size);
                }
                g.setColor(base);
            }
            default -> { }
        }
    }
}
