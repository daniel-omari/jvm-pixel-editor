package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.util.tools.Tool;
import com.danielomari.pixeleditor.ui.CanvasPanel;

import java.awt.*;
import java.awt.event.MouseEvent;

// Zoom tool. Left-click zooms in by a fixed step, right-click zooms out, and
// dragging left/right adjusts the zoom continuously. The level is clamped
// between MIN_ZOOM and MAX_ZOOM and applied to the shared canvas.
public class MagnifierTool implements Tool {
    private static final float ZOOM_INCREMENT = 0.1f;
    private static final float MAX_ZOOM = 3.0f;
    private static final float MIN_ZOOM = 0.1f; // 10% min, matches the Help docs
    private final CanvasPanel canvasPanel;
    private static float zoomLevel = 1.0f;
    private Point dragStartPoint;

    public MagnifierTool(CanvasPanel canvasPanel) {
        this.canvasPanel = canvasPanel;
    }

    @Override
    public void onPress(MouseEvent e) {
        dragStartPoint = e.getPoint(); // anchor for continuous drag-zoom
        if (e.getButton() == MouseEvent.BUTTON1) {
            zoomIn();
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            zoomOut();
        }
    }

    @Override
    public void onRelease(MouseEvent e) {
    }

    // Left-drag scales the zoom in proportion to the horizontal drag distance.
    @Override
    public void onDrag(MouseEvent e) {
        // getButton() reads 0 during a drag, so don't gate on it. Adjust zoom by
        // the incremental horizontal movement since the last drag event.
        if (dragStartPoint == null) return;
        int dragDistance = e.getX() - dragStartPoint.x;
        zoomLevel += dragDistance * 0.005f;
        zoomLevel = Math.max(MIN_ZOOM, Math.min(zoomLevel, MAX_ZOOM));
        applyZoom();
        dragStartPoint = e.getPoint();
    }

    public void activate() {
        applyZoom();
    }
    public void deactivate() {
        applyDefaultZoom(); // Reset zoom level when deactivated
    }

    public void zoomIn() {
        zoomLevel = Math.min(zoomLevel + ZOOM_INCREMENT, MAX_ZOOM);
        applyZoom();
    }

    public void zoomOut() {
        zoomLevel = Math.max(zoomLevel - ZOOM_INCREMENT, MIN_ZOOM);
        applyZoom();
    }

    public float getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(float zoomLevel) {
        this.zoomLevel = Math.max(MIN_ZOOM, Math.min(zoomLevel, MAX_ZOOM)); // Keep within limits
        canvasPanel.repaint();  // Redraw the canvas with new zoom
    }

    public void setDragStartPoint(Point point) {
        dragStartPoint = point;
    }

    private void applyZoom() {
        if (canvasPanel != null) {
            canvasPanel.setZoom(zoomLevel); // Set zoom level in CanvasPanel
            canvasPanel.repaint();
        }
    }

    private void applyDefaultZoom() {
        if(canvasPanel != null) {
            canvasPanel.setZoom(1.0f); // Reset zoom level in CanvasPanel
            canvasPanel.repaint();
        }
    }
}