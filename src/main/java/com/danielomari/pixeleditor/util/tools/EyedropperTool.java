package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.ui.CanvasPanel;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Colour picker: clicking the canvas samples the colour at that pixel from the
 * flattened (composited) image and makes it the current drawing colour.
 */
public class EyedropperTool implements Tool {

    @Override
    public void onPress(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) return;
        // Coordinates arrive already in image space via CanvasPanel's dispatch.
        BufferedImage flat = CanvasPanel.getInstance().getFlattenedImage();
        int x = e.getX();
        int y = e.getY();
        if (x < 0 || y < 0 || x >= flat.getWidth() || y >= flat.getHeight()) return;
        Color sampled = new Color(flat.getRGB(x, y)); // RGB only (ignore alpha)
        ColorTool.pickColor(sampled);
    }

    @Override
    public void onRelease(MouseEvent e) {
    }

    @Override
    public void onDrag(MouseEvent e) {
    }
}
