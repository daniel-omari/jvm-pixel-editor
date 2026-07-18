package com.danielomari.pixeleditor.ui;

import com.danielomari.pixeleditor.commands.TransformMouseEvent;
import com.danielomari.pixeleditor.util.KeyBinds.KeyBindings;
import com.danielomari.pixeleditor.util.tools.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;
import java.awt.geom.Ellipse2D;
import java.util.function.Consumer;
import com.danielomari.pixeleditor.layers.Layer;
import com.danielomari.pixeleditor.layers.LayerStack;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.ResizeCanvasCommand;


// The drawing surface: owns the layer stack, paints the composited document, and routes mouse / wheel / pan input to the active tool.
public class CanvasPanel extends JPanel {
    @Serial
    private static final long serialVersionUID = 1L;
    private static CanvasPanel instance;
    private final List<Consumer<Graphics2D>> paintListeners = new ArrayList<>();
    private SelectTool selectTool = new SelectTool();
    private Tool currentTool = selectTool; // Default tool is set to null initially
    private LayerStack layers; // the document is a stack of layers (index 0 = bottom)
    private Runnable onLayersChanged; // notified when the stack changes structurally (e.g. New File)
    private Runnable onZoomChanged;   // notified when the zoom factor changes (status-bar %)

    // Workspace styling: a dark grey surround, a checkerboard behind the document
    // (so a transparent canvas is still visible), and a thin document outline.
    private static final Color WORKSPACE_BG = new Color(75, 75, 75);
    private static final Color DOC_BORDER = new Color(40, 40, 40);
    private static final TexturePaint CHECKER = makeChecker();

    private static TexturePaint makeChecker() {
        int s = 8; // square size in screen pixels
        BufferedImage tile = new BufferedImage(s * 2, s * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = tile.createGraphics();
        g.setColor(new Color(205, 205, 205));
        g.fillRect(0, 0, s * 2, s * 2);
        g.setColor(new Color(165, 165, 165));
        g.fillRect(0, 0, s, s);
        g.fillRect(s, s, s, s);
        g.dispose();
        return new TexturePaint(tile, new Rectangle(0, 0, s * 2, s * 2));
    }
    private float currentZoomFactor  = 1.0f;
    private static final float MIN_ZOOM = 0.05f, MAX_ZOOM = 32f;
    private int panX = 0, panY = 0;            // user pan offset on top of centring
    private boolean spaceDown = false;         // spacebar held -> drag to pan
    private Point panStart;                    // anchor for a pan drag
    private Point hoverScreen;                 // last cursor pos (brush-size ring)
    private RotateTool rotateTool/*  = new RotateTool()*/;
    private InsertTool insertTool/*  = new InsertTool()*/;

    public CanvasPanel() {
        instance = this;
        // The document is a stack of layers; the bottom one is an opaque white
        // "Background". Every tool draws on whichever layer is currently active.
        layers = new LayerStack(800, 600);
        setBackground(WORKSPACE_BG);
        setupDrawingListeners();

        selectTool = new SelectTool();
        selectTool.setCanvas(this);

        setTool(selectTool);

        KeyBindings keyBindings = new KeyBindings(instance);
        keyBindings.createKeyBinds();
        keyBindings.createShortcutKeyBinds();


//        SwingUtilities.invokeLater(this::setWindowDimensions); // Disabled for now.
    }

    public static CanvasPanel getInstance() {
        return instance;
    }

    public void setWindowDimensions() {
        int windowWidth = this.getWidth();
        int windowHeight = this.getHeight();

        int canvasWidth = getCanvasImage().getWidth();
        int canvasHeight = getCanvasImage().getHeight();

        if (windowWidth == 0 || windowHeight == 0) {
            return; // Avoid division by zero if the panel isn't fully initialized
        }

        float newZoomFactorX = (float) windowWidth / canvasWidth;
        float newZoomFactorY = (float) windowHeight / canvasHeight;
        float newZoomFactor = Math.min(newZoomFactorX, newZoomFactorY);

        // Prevent unnecessary shrinking when resizing
        if (newZoomFactor < currentZoomFactor) {
            return;
        }

        setZoom(newZoomFactor);
    }

    // The "canvas image" is the ACTIVE layer's image, so every existing tool and
    // command that reads/writes getCanvasImage() now operates on the active layer.
    public BufferedImage getCanvasImage() {
        return layers.active().getImage();
    }

    public void setCanvasImage(BufferedImage image) {
        if (image != null) {
            layers.active().setImage(image);
            repaint();
        }
    }

    // The layer model and the currently active layer (used by tools and commands).
    public LayerStack getLayers() {
        return layers;
    }

    public Layer getActiveLayer() {
        return layers.active();
    }

    // Flattened view of all visible layers, for saving/export.
    public BufferedImage getFlattenedImage() {
        return layers.flatten();
    }

    // Resize the document (all layers) to w x h, anchoring content top-left. Undoable.
    public void resizeCanvas(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int oldW = layers.getWidth();
        int oldH = layers.getHeight();
        BufferedImage[] before = snapshotLayerImages();
        layers.resize(w, h);
        resetPan();
        BufferedImage[] after = snapshotLayerImages();
        CommandManager.getInstance().executeCommand(
                new ResizeCanvasCommand(this, oldW, oldH, w, h, before, after));
        if (onLayersChanged != null) onLayersChanged.run();
        revalidate();
        repaint();
    }

    private BufferedImage[] snapshotLayerImages() {
        List<Layer> ls = layers.layers();
        BufferedImage[] arr = new BufferedImage[ls.size()];
        for (int i = 0; i < ls.size(); i++) arr[i] = ls.get(i).getImage();
        return arr;
    }

    // Let the Layers panel re-sync when the stack changes from outside it.
    public void setOnLayersChanged(Runnable r) {
        this.onLayersChanged = r;
    }

    // Trigger a Layers-panel refresh (used by structural layer commands on undo/redo).
    public void notifyLayersChanged() {
        if (onLayersChanged != null) onLayersChanged.run();
    }

    public float getZoom() {
        return currentZoomFactor;
    }

    public void setZoom(float zoomLevel) {
        if (zoomLevel > 0) {
            this.currentZoomFactor = zoomLevel;
            if (onZoomChanged != null) onZoomChanged.run();
            repaint();
        }
    }

    // Notified whenever the zoom factor changes (drives the status-bar indicator).
    public void setOnZoomChanged(Runnable r) {
        this.onZoomChanged = r;
    }

    // Fit the whole document inside the current viewport (may shrink), leaving a
    // small margin so it isn't flush against the edges.
    public void fitToWindow() {
        int vw = getWidth() - 16, vh = getHeight() - 16;
        BufferedImage img = getCanvasImage();
        if (vw <= 0 || vh <= 0 || img.getWidth() == 0 || img.getHeight() == 0) return;
        float fit = Math.min((float) vw / img.getWidth(), (float) vh / img.getHeight());
        if (fit > 0) { resetPan(); setZoom(fit); }
    }

    // Reset to a 1:1 pixel mapping (100%), re-centred.
    public void actualSize() {
        resetPan();
        setZoom(1.0f);
    }

    private static float clampZoom(float z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    // Zoom so that the document point currently under the screen pixel (sx, sy)
    // stays under that same pixel afterwards (zoom-to-cursor for the mouse wheel).
    public void zoomAtPoint(float newZoom, int sx, int sy) {
        newZoom = clampZoom(newZoom);
        // Image-space point under the cursor at the current zoom/offset.
        double imgX = (sx - getRenderOffsetX()) / (double) currentZoomFactor;
        double imgY = (sy - getRenderOffsetY()) / (double) currentZoomFactor;
        currentZoomFactor = newZoom;
        // Re-solve the pan so (imgX, imgY) maps back onto (sx, sy).
        int centredX = (getWidth() - (int) (getCanvasImage().getWidth() * newZoom)) / 2;
        int centredY = (getHeight() - (int) (getCanvasImage().getHeight() * newZoom)) / 2;
        panX = (int) Math.round(sx - imgX * newZoom - centredX);
        panY = (int) Math.round(sy - imgY * newZoom - centredY);
        if (onZoomChanged != null) onZoomChanged.run();
        repaint();
    }

    // Shift the view by a screen-pixel delta (spacebar / middle-button drag).
    public void panBy(int dx, int dy) {
        panX += dx;
        panY += dy;
        repaint();
    }

    public void resetPan() {
        panX = 0;
        panY = 0;
    }

    // --- Single source of truth for the screen <-> image coordinate mapping. ---
    // The canvas image is drawn scaled by the zoom factor and centred in the
    // panel. These helpers convert between on-screen (panel) pixels and image
    // pixels so the maths is never duplicated across tools and the renderer.
    // When the canvas is later decoupled from the window size, only these
    // methods change and everything that routes through them follows.

    // Top-left position of the (scaled) image within the panel.
    public int getRenderOffsetX() {
        return (getWidth() - (int) (getCanvasImage().getWidth() * currentZoomFactor)) / 2 + panX;
    }

    public int getRenderOffsetY() {
        return (getHeight() - (int) (getCanvasImage().getHeight() * currentZoomFactor)) / 2 + panY;
    }

    // Panel (screen) pixel -> image pixel. NOT clamped to the image: tools get the
    // true coordinate, so a stroke that runs past the edge is clipped naturally by
    // the canvas graphics instead of snapping onto the border. Tools that must stay
    // in-bounds (selection capture, flood fill) clamp/bounds-check themselves.
    public Point screenToImage(int screenX, int screenY) {
        int imageX = (int) ((screenX - getRenderOffsetX()) / currentZoomFactor);
        int imageY = (int) ((screenY - getRenderOffsetY()) / currentZoomFactor);
        return new Point(imageX, imageY);
    }

    // Image pixel -> panel (screen) pixel.
    public Point imageToScreen(int imageX, int imageY) {
        int screenX = (int) (imageX * currentZoomFactor) + getRenderOffsetX();
        int screenY = (int) (imageY * currentZoomFactor) + getRenderOffsetY();
        return new Point(screenX, screenY);
    }
    // Switch the active tool: tear down the previous tool, wire up the new one,
    // then refresh the cursor. SelectTool is the fallback when null is passed.
    public void setTool(Tool tool) {
        ShapeTool.deactivateShapeTool();
        Tool previousTool = currentTool;

        // Leaving Select ends its selection, unless we hand off to Rotate/Insert
        // which act on the current selection.
        if (previousTool instanceof SelectTool
                && !(tool instanceof RotateTool)
                && !(tool instanceof InsertTool)) {
            ((SelectTool) previousTool).deactivate();
        }

        this.currentTool = tool;

        // Rotate -> Rotate also drops any lingering selection.
        if (previousTool instanceof RotateTool && tool instanceof RotateTool) {
            selectTool.deactivate();
        }

        if (tool == null) {
            tool = selectTool; // never leave the canvas tool-less
        }

        // Set up the incoming tool.
        if (tool instanceof ShapeTool) {
            ((ShapeTool) tool).setCanvas(this);
            ((ShapeTool) tool).activate();
        } else if (tool instanceof MagnifierTool) {
            ((MagnifierTool) tool).activate();
        } else if (tool instanceof EyedropperTool) {
            ((EyedropperTool) tool).activate(this);
        }

        // Tear down the outgoing tool.
        if (previousTool instanceof MagnifierTool) {
            ((MagnifierTool) previousTool).deactivate();
        }
        if (previousTool instanceof EyedropperTool) {
            ((EyedropperTool) previousTool).deactivate();
        }

        applyToolCursor();
    }

    // Cursor for the active state: a move cursor while panning, an I-beam for the
    // Text tool, and a crosshair for the pixel tools (so the brush-size ring is
    // the visual guide rather than a clashing arrow).
    private void applyToolCursor() {
        if (spaceDown || panStart != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else if (currentTool instanceof TextTool) {
            setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        } else if (currentTool instanceof EyedropperTool
                || currentTool instanceof BrushTool
                || currentTool instanceof PencilTool
                || currentTool instanceof EraserTool) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    // True when the user is requesting a view pan (spacebar held, or middle drag)
    // rather than a tool action.
    private boolean isPanGesture(MouseEvent e) {
        return spaceDown || SwingUtilities.isMiddleMouseButton(e);
    }

    // Add a paint listener to the list
    public void addPaintListener(Consumer<Graphics2D> listener) {
        if (!paintListeners.contains(listener)) { // prevents duplicate event listeners
            paintListeners.add(listener);
        }
    }

    // Remove a paint listener
    public void removePaintListener(Consumer<Graphics2D> listener) {
        paintListeners.remove(listener);
    }

    private void setupDrawingListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isPanGesture(e)) {              // start a view pan, not a tool action
                    panStart = e.getPoint();
                    applyToolCursor();
                    return;
                }
                if (currentTool != null) {
                    MouseEvent transformedEvent = TransformMouseEvent.transform(e, CanvasPanel.this);
                    currentTool.onPress(transformedEvent);
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panStart != null) {            // finish a pan
                    panStart = null;
                    applyToolCursor();
                    return;
                }
                if (currentTool != null) {
                    MouseEvent transformedEvent = TransformMouseEvent.transform(e, CanvasPanel.this);
                    currentTool.onRelease(transformedEvent);
                }
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverScreen = null;                 // hide the brush-size ring
                repaint();
            }
        };

        MouseMotionAdapter mouseMotionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                hoverScreen = e.getPoint();
                if (panStart != null) {            // panning the view
                    panBy(e.getX() - panStart.x, e.getY() - panStart.y);
                    panStart = e.getPoint();
                    return;
                }
                if (currentTool != null) {
                    MouseEvent transformedEvent = TransformMouseEvent.transform(e, CanvasPanel.this);
                    currentTool.onDrag(transformedEvent);
                }
                // Note: repaint is called by the tool's onDrag method
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hoverScreen = e.getPoint();
                if (showsBrushRing()) repaint();    // update the brush-size ring
            }
        };

        // Mouse wheel zooms about the cursor (plain wheel or Ctrl+wheel).
        addMouseWheelListener((MouseWheelEvent e) -> {
            double factor = Math.pow(1.1, -e.getPreciseWheelRotation());
            zoomAtPoint((float) (currentZoomFactor * factor), e.getX(), e.getY());
        });

        // Spacebar held = pan mode (released = back to the tool). Window-wide so it
        // works without clicking the canvas first; text fields still get their
        // spaces because a focused text component consumes the key first.
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("pressed SPACE"), "panOn");
        im.put(KeyStroke.getKeyStroke("released SPACE"), "panOff");
        am.put("panOn", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!spaceDown) { spaceDown = true; applyToolCursor(); }
            }
        });
        am.put("panOff", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                spaceDown = false; applyToolCursor();
            }
        });

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseMotionAdapter);
    }

    // ---- pixel grid overlay ----
    // Visible from 8x zoom, where one image pixel spans >= 8 screen pixels.
    private static final float GRID_MIN_ZOOM = 8f;
    private boolean gridEnabled =
            com.danielomari.pixeleditor.util.Configuration.getInstance().is("ui.grid", true);

    public boolean isGridEnabled() {
        return gridEnabled;
    }

    public void setGridEnabled(boolean enabled) {
        gridEnabled = enabled;
        com.danielomari.pixeleditor.util.Configuration.getInstance()
                .putString("ui.grid", String.valueOf(enabled));
        com.danielomari.pixeleditor.util.Configuration.getInstance().save();
        repaint();
    }

    public void toggleGrid() {
        setGridEnabled(!gridEnabled);
    }

    // Whether the active tool should show the brush-size ring at the cursor.
    private boolean showsBrushRing() {
        return currentTool instanceof BrushTool
                || currentTool instanceof PencilTool
                || currentTool instanceof EraserTool;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();

        // Fill the workspace with a dark grey, distinct from the document area.
        g2d.setColor(WORKSPACE_BG);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        Rectangle selectionBounds = selectTool.getSelectionBounds();

        if (layers != null) {
            int offsetX = getRenderOffsetX();
            int offsetY = getRenderOffsetY();
            int docW = getCanvasImage().getWidth();
            int docH = getCanvasImage().getHeight();
            int dw = (int) (docW * currentZoomFactor);
            int dh = (int) (docH * currentZoomFactor);

            // Checkerboard backdrop so the document is visible even when its only
            // layer is transparent; thin outline marks the document edge.
            g2d.setPaint(CHECKER);
            g2d.fillRect(offsetX, offsetY, dw, dh);
            g2d.setColor(DOC_BORDER);
            g2d.drawRect(offsetX, offsetY, dw - 1, dh - 1);

            // One exact transform for all document-space drawing: translate to
            // the document origin, then zoom. The old code truncated the offset
            // to whole image pixels ((int)(offset / zoom)), which could shift
            // the rendered document up to a full cell away from where
            // screenToImage places the cursor at high zoom.
            g2d.translate(offsetX, offsetY);
            g2d.scale(currentZoomFactor, currentZoomFactor);

            // Composite the layers bottom-to-top, honouring each layer's
            // visibility and opacity.
            for (Layer layer : layers.layers()) {
                if (!layer.getVisible()) continue;
                Composite previous = g2d.getComposite();
                if (layer.getOpacity() < 1f) {
                    g2d.setComposite(AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, layer.getOpacity()))));
                }
                g2d.drawImage(layer.getImage(), 0, 0, this);
                g2d.setComposite(previous);
            }

            // Draw the selection overlay (box + handles). drawSelection applies
            // the centering offset itself, so it lines up with the document.
            if (selectionBounds != null && selectionBounds.width > 0 && selectionBounds.height > 0) {
                SelectTool.getInstance().drawSelection(g2d);
            }
        }

        // Call all registered paint listeners
        for (Consumer<Graphics2D> listener : paintListeners) {
            if (listener != null) {
                listener.accept(g2d);
            }
        }

        g2d.dispose();

        // Pixel grid over the document at high zoom, then the brush-size ring,
        // both in screen space so they sit on top of everything.
        drawPixelGrid(g);
        drawBrushRing(g);
    }

    // One thin line per image-pixel boundary, only for the rows/columns that
    // are actually inside the viewport. Aligned to the same (ox * zoom)
    // convention the layer renderer uses, so lines sit exactly on the edges of
    // the rendered pixels.
    private void drawPixelGrid(Graphics g) {
        if (!gridEnabled || currentZoomFactor < GRID_MIN_ZOOM || layers == null) return;

        int docW = getCanvasImage().getWidth();
        int docH = getCanvasImage().getHeight();
        int offsetX = getRenderOffsetX();
        int offsetY = getRenderOffsetY();

        // Boundary col/row range actually inside the viewport (0 is the
        // document's left/top edge, docW/docH its right/bottom).
        int firstCol = Math.max(0, (int) Math.floor((0 - offsetX) / (double) currentZoomFactor));
        int lastCol = Math.min(docW, (int) Math.ceil((getWidth() - offsetX) / (double) currentZoomFactor));
        int firstRow = Math.max(0, (int) Math.floor((0 - offsetY) / (double) currentZoomFactor));
        int lastRow = Math.min(docH, (int) Math.ceil((getHeight() - offsetY) / (double) currentZoomFactor));
        if (firstCol > lastCol || firstRow > lastRow) return;

        int top = offsetY + Math.round(firstRow * currentZoomFactor);
        int bottom = offsetY + Math.round(lastRow * currentZoomFactor);
        int left = offsetX + Math.round(firstCol * currentZoomFactor);
        int right = offsetX + Math.round(lastCol * currentZoomFactor);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(128, 128, 128, 80));
        for (int col = firstCol; col <= lastCol; col++) {
            int x = offsetX + Math.round(col * currentZoomFactor);
            g2.drawLine(x, top, x, bottom);
        }
        for (int row = firstRow; row <= lastRow; row++) {
            int y = offsetY + Math.round(row * currentZoomFactor);
            g2.drawLine(left, y, right, y);
        }
        g2.dispose();
    }

    private void drawBrushRing(Graphics g) {
        if (!showsBrushRing() || hoverScreen == null || spaceDown || panStart != null) return;
        int size = activeToolSize();
        if (size <= 0) return;
        double d = Math.max(2.0, size * currentZoomFactor); // on-screen diameter
        double r = d / 2.0;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Ellipse2D ring = new Ellipse2D.Double(hoverScreen.x - r, hoverScreen.y - r, d, d);
        // A dark + light pair so the ring is visible over any background.
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(0, 0, 0, 140));
        g2.draw(ring);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.draw(ring);
        g2.dispose();
    }

    private int activeToolSize() {
        if (currentTool instanceof BrushTool) return BrushTool.getSizePx();
        if (currentTool instanceof PencilTool) return PencilTool.getSize();
        if (currentTool instanceof EraserTool) return EraserTool.getSize();
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(getParent().getWidth(), getParent().getHeight());
    }

    // NOTE: intentionally no doLayout / image-resize override. The document is a
    // fixed size and is centred in the panel by paintComponent, so resizing the
    // window changes only the surrounding workspace, never the document or its
    // pixels. (This is what decouples the canvas from the window size.)

    public void clearCanvas() {
        TextTool.forgetCommittedText(); // re-editable text no longer matches a cleared canvas
        // New File: collapse back to a single blank white background layer.
        layers.reset();
        resetPan();
        if (onLayersChanged != null) onLayersChanged.run();
        revalidate();
        repaint();
    }

    public List<Consumer<Graphics2D>> getPaintListeners() {
        return paintListeners;
    }
}