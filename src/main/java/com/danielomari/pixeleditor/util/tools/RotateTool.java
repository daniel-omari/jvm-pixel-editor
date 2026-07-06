package com.danielomari.pixeleditor.util.tools;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.MoveCommand;
import com.danielomari.pixeleditor.commands.ResizeCanvasCommand;
import com.danielomari.pixeleditor.layers.Layer;
import com.danielomari.pixeleditor.layers.LayerStack;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.util.List;

// Rotates or flips the whole canvas (all layers) or the current selection.
public class RotateTool implements Tool {
    private static BufferedImage appliedImage;
    private static CanvasPanel canvasPanel;
    private static SelectTool selectedTool;
    private static int width, height;
    private static Rectangle clipped;
    private static MoveCommand currentCommand;
    private static BufferedImage selectedContent;

    public RotateTool() {
        selectedTool = SelectTool.getInstance();
        selectedContent = selectedTool.getselectedContent();
    }

    public enum RotateType {RIGHT, LEFT, OPPOSITE, FLIPV, FLIPH};
    private static RotateType selectedRotateType = RotateType.RIGHT; // default rotate type

    public static void setRotateType(RotateType rotateType) {
        selectedRotateType = rotateType;
    }

    @Override
    public void onPress(MouseEvent e) {
        // Clicking with the Rotate tool clears any selection so the next rotate
        // applies to the whole canvas.
        selectedTool.clearSelection();
        clipped = null;
        selectedContent = null;
    }

    @Override
    public void onDrag(MouseEvent e) {
        // no action while dragging
    }

    public void rotate() {
        canvasPanel = CanvasPanel.getInstance();

        // No active selection -> rotate the whole document (every layer).
        if (selectedContent == null) {
            rotateWholeCanvas();
            return;
        }

        // Otherwise rotate just the selected region on the active layer.
        rotateSelection();
    }

    // Rotate/flip every layer together and adopt the (possibly swapped) document
    // size, so a quarter-turn rotates the whole canvas rather than leaving the
    // layers mismatched. Undoable as a single step.
    private void rotateWholeCanvas() {
        LayerStack stack = canvasPanel.getLayers();
        List<Layer> layers = stack.layers();
        if (layers.isEmpty()) return;

        int oldW = stack.getWidth(), oldH = stack.getHeight();
        BufferedImage[] before = new BufferedImage[layers.size()];
        BufferedImage[] after = new BufferedImage[layers.size()];

        for (int i = 0; i < layers.size(); i++) {
            before[i] = layers.get(i).getImage();
            BufferedImage rotated = rotateImageAffine(before[i], selectedRotateType);
            layers.get(i).setImage(rotated);
            after[i] = rotated;
        }

        int newW = after[0].getWidth(), newH = after[0].getHeight();
        stack.setSize(newW, newH);

        CommandManager.getInstance().executeCommand(
                new ResizeCanvasCommand(canvasPanel, oldW, oldH, newW, newH, before, after));

        selectedTool.clearSelection();
        selectedContent = null;
        canvasPanel.notifyLayersChanged();
        canvasPanel.revalidate();
        canvasPanel.repaint();
    }

    private void rotateSelection() {
        currentCommand = new MoveCommand(canvasPanel, 0, 0);
        currentCommand.storeBeforeState();

        BufferedImage wholeCanvas = canvasPanel.getCanvasImage();
        appliedImage = selectedContent;
        clipped = selectedTool.getClippedLocation();
        if (appliedImage == null) return;

        BufferedImage rotatedImage = rotateImageAffine(appliedImage, selectedRotateType);

        // Clear the region the selection came from, then paste the rotated copy.
        selectedTool.clearSelection();
        Graphics2D g2dCanvas = wholeCanvas.createGraphics();
        g2dCanvas.setColor(Color.WHITE);
        width = appliedImage.getWidth();
        height = appliedImage.getHeight();
        int safeWidth = Math.min(width, wholeCanvas.getWidth() - clipped.x);
        int safeHeight = Math.min(height, wholeCanvas.getHeight() - clipped.y);
        g2dCanvas.fillRect(clipped.x, clipped.y, safeWidth, safeHeight);
        g2dCanvas.dispose();

        g2dCanvas = wholeCanvas.createGraphics();
        g2dCanvas.drawImage(rotatedImage, clipped.x, clipped.y,
                rotatedImage.getWidth(), rotatedImage.getHeight(), null);
        g2dCanvas.dispose();
        canvasPanel.setCanvasImage(wholeCanvas);

        selectedTool.deactivate();
        selectedContent = null;
        canvasPanel.repaint();

        if (currentCommand != null) {
            currentCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(currentCommand);
            currentCommand = null;
        }
    }

    @Override
    public void onRelease(MouseEvent e) {
    }

    // Rotate/flip an image about its centre, returning a new ARGB image sized to
    // fit the result (a quarter-turn swaps width/height). The background is left
    // transparent so layers stay see-through where there are no pixels.
    private static BufferedImage rotateImageAffine(BufferedImage image, RotateType type) {
        int width = image.getWidth();
        int height = image.getHeight();

        double theta = 0;
        boolean flipHorizontal = false, flipVertical = false;
        switch (type) {
            case RIGHT:    theta = Math.PI / 2; break;
            case LEFT:     theta = -Math.PI / 2; break;
            case OPPOSITE: theta = Math.PI; break;
            case FLIPH:    flipHorizontal = true; break;
            case FLIPV:    flipVertical = true; break;
        }

        // Rotate about the centre (with optional flip).
        AffineTransform transform = new AffineTransform();
        transform.translate(width / 2.0, height / 2.0);
        transform.rotate(theta);
        if (flipHorizontal) transform.scale(-1, 1);
        if (flipVertical) transform.scale(1, -1);
        transform.translate(-width / 2.0, -height / 2.0);

        // Size the destination to the transformed bounds.
        Rectangle bounds = transform.createTransformedShape(new Rectangle(0, 0, width, height)).getBounds();
        BufferedImage rotated = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();

        // Keep the image centred in the output.
        AffineTransform centered = new AffineTransform();
        centered.translate(-bounds.x, -bounds.y);
        centered.concatenate(transform);
        g2d.drawImage(image, centered, null);
        g2d.dispose();
        return rotated;
    }
}
