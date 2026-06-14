package com.danielomari.pixeleditor.commands;

import com.danielomari.pixeleditor.ui.CanvasPanel;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class TransformMouseEvent {
    public static MouseEvent transform(MouseEvent e, CanvasPanel canvasPanel) {
        // Delegate to the canvas's single source of truth for the screen -> image
        // mapping (zoom + centering offset + bounds clamp), so this conversion is
        // never duplicated.
        Point imagePoint = canvasPanel.screenToImage(e.getX(), e.getY());
        int originalX = imagePoint.x;
        int originalY = imagePoint.y;

        return new MouseEvent(
                e.getComponent(),
                e.getID(),
                e.getWhen(),
                e.getModifiersEx(),
                originalX,
                originalY,
                e.getXOnScreen(),
                e.getYOnScreen(),
                e.getClickCount(),
                e.isPopupTrigger(),
                e.getButton()
        );
    }

    // NOTE: legacy helper used only by ShapeTool. It applies zoom but NOT the
    // centering offset, so it sits on a different (inconsistent) coordinate path
    // from screenToImage. Replace it with CanvasPanel.screenToImage when the
    // canvas is decoupled from the window size.
    public static Point transformPoint(Point p) {
        double scaleFactor = CanvasPanel.getInstance().getZoom();
        return new Point(
                (int) (p.x / scaleFactor),
                (int) (p.y / scaleFactor)
        );
    }
}
