package com.danielomari.pixeleditor.ui;

import com.danielomari.pixeleditor.commands.TransformMouseEvent;
import com.danielomari.pixeleditor.util.KeyBinds.KeyBindings;
import com.danielomari.pixeleditor.util.tools.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;


public class CanvasPanel extends JPanel {
    @Serial
    private static final long serialVersionUID = 1L;
    private static CanvasPanel instance;
    private final List<Consumer<Graphics2D>> paintListeners = new ArrayList<>();
    private SelectTool selectTool = new SelectTool();
    private Tool currentTool = selectTool; // Default tool is set to null initially
    private BufferedImage canvasImage;
    private float currentZoomFactor  = 1.0f;
    private RotateTool rotateTool/*  = new RotateTool()*/;
    private InsertTool insertTool/*  = new InsertTool()*/;

    public CanvasPanel() {
        instance = this;
        canvasImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        // The document is a fixed-size page; fill it white up front. The
        // surrounding panel is the gray workspace. (Previously the white fill
        // happened lazily as the image grew to fill the window, which no longer
        // happens now that the document is decoupled from the window size.)
        Graphics2D initGraphics = canvasImage.createGraphics();
        initGraphics.setColor(Color.WHITE);
        initGraphics.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
        initGraphics.dispose();
        setBackground(Color.GRAY);
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

        int canvasWidth = canvasImage.getWidth();
        int canvasHeight = canvasImage.getHeight();

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

    public BufferedImage getCanvasImage() {
        return canvasImage;
    }

    public void setCanvasImage(BufferedImage image) {
        if (image != null) {
            this.canvasImage = image;
            repaint();
        }
    }

    public float getZoom() {
        return currentZoomFactor;
    }

    public void setZoom(float zoomLevel) {
        if (zoomLevel > 0) {
            this.currentZoomFactor = zoomLevel;
            repaint();
        }
    }

    // --- Single source of truth for the screen <-> image coordinate mapping. ---
    // The canvas image is drawn scaled by the zoom factor and centred in the
    // panel. These helpers convert between on-screen (panel) pixels and image
    // pixels so the maths is never duplicated across tools and the renderer.
    // When the canvas is later decoupled from the window size, only these
    // methods change and everything that routes through them follows.

    // Top-left position of the (scaled) image within the panel.
    public int getRenderOffsetX() {
        return (getWidth() - (int) (canvasImage.getWidth() * currentZoomFactor)) / 2;
    }

    public int getRenderOffsetY() {
        return (getHeight() - (int) (canvasImage.getHeight() * currentZoomFactor)) / 2;
    }

    // Panel (screen) pixel -> image pixel, clamped to the image bounds.
    public Point screenToImage(int screenX, int screenY) {
        int imageX = (int) ((screenX - getRenderOffsetX()) / currentZoomFactor);
        int imageY = (int) ((screenY - getRenderOffsetY()) / currentZoomFactor);
        imageX = Math.max(0, Math.min(imageX, canvasImage.getWidth() - 1));
        imageY = Math.max(0, Math.min(imageY, canvasImage.getHeight() - 1));
        return new Point(imageX, imageY);
    }

    // Image pixel -> panel (screen) pixel.
    public Point imageToScreen(int imageX, int imageY) {
        int screenX = (int) (imageX * currentZoomFactor) + getRenderOffsetX();
        int screenY = (int) (imageY * currentZoomFactor) + getRenderOffsetY();
        return new Point(screenX, screenY);
    }
    // Set the current tool and deactivate ShapeTool if it is the current tool
    public void setTool(Tool tool) {
        System.out.println("Incoming tool: " + tool);
        System.out.println("Current tool before change: " + currentTool);

        ShapeTool.deactivateShapeTool();
        
        // Update tool reference FIRST
        Tool previousTool = currentTool;

        // Deactivate SelectTool if currently active except current tool = RoateTool and InsertTool
        if (previousTool instanceof SelectTool && 
        !(tool instanceof RotateTool) && 
        !(tool instanceof InsertTool)) {
            
            System.out.println("Deactivating SelectTool (standard case)");
            ((SelectTool) previousTool).deactivate();
            //test: the above method include clearSelection method, comment below command
            //SelectTool.getInstance().clearSelection();
        }

        
        this.currentTool = tool;

        // New condition: Deactivate SelectTool when switching between RotateTool instances
        if (previousTool instanceof RotateTool && tool instanceof RotateTool) {
            System.out.println("Deactivating SelectTool (rotate-to-rotate case)");
            selectTool.deactivate(); // Deactivate using the instance reference
        }

        // New condition 2: Deactivate when switching from InsertTool to RotateTool
        /* 
        if (previousTool instanceof SelectTool && tool instanceof RotateTool) {
            //finalise position of image

            System.out.println("Deactivating SelectTool (insert-to-rotate case)");
            selectTool.deactivate();
        }*/
            

        // Deactivate MagnifierTool if currently active
        //if (currentTool instanceof MagnifierTool) {
        //    ((MagnifierTool) currentTool).deactivate();
        //}

        // Default to selectTool if tool is null
        if (tool == null) {
            tool = selectTool;
        }

        //this.currentTool = tool;

        // Activate/setup based on new tool type
        /* 
        if (currentTool instanceof ShapeTool) {
            ((ShapeTool) tool).setCanvas(this);
            ((ShapeTool) tool).activate();
        } else if (currentTool instanceof PencilTool) {
            ((PencilTool) tool).setCanvas(this);
            //test
            System.out.println("Pencil tool set");
        } else if (currentTool instanceof MagnifierTool) {
            ((MagnifierTool) tool).activate();
        }*/
        if (tool instanceof ShapeTool) {
            ((ShapeTool) tool).setCanvas(this);
            ((ShapeTool) tool).activate();
        } else if (tool instanceof MagnifierTool) {
            ((MagnifierTool) tool).activate();
        }
        
        // Clean up previous tool if needed
        if (previousTool instanceof MagnifierTool) {
            ((MagnifierTool) previousTool).deactivate();
        }

        // Reset cursor: the Text tool gets an I-beam so it's clear it is active.
        if (tool instanceof TextTool) {
            setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }

    }

    // Add a paint listener to the list
    public void addPaintListener(Consumer<Graphics2D> listener) {
        if (!paintListeners.contains(listener)) { // prevents duplicate event listeners
            paintListeners.add(listener);
//            System.out.println("CanvasPanel: Paint listener added, total: " + paintListeners.size());
        }
    }

    // Remove a paint listener
    public void removePaintListener(Consumer<Graphics2D> listener) {
        paintListeners.remove(listener);
//        System.out.println("CanvasPanel: Paint listener removed, remaining: " + paintListeners.size());
    }

    private void setupDrawingListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (currentTool != null) {
                    MouseEvent transformedEvent = TransformMouseEvent.transform(e, CanvasPanel.this);
                    currentTool.onPress(transformedEvent);
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (currentTool != null) {
                    MouseEvent transformedEvent = TransformMouseEvent.transform(e, CanvasPanel.this);
                    currentTool.onRelease(transformedEvent);
                }
                repaint();
            }
        };

        MouseMotionAdapter mouseMotionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (currentTool != null) {
                    MouseEvent transformedEvent = TransformMouseEvent.transform(e, CanvasPanel.this);
                    currentTool.onDrag(transformedEvent);
                }
                // Note: repaint is called by the tool's onDrag method
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseMotionAdapter);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();

        // Fill background
        g2d.setColor(Color.GRAY);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        Rectangle selectionBounds = selectTool.getSelectionBounds();

        // Ensure canvasImage is not null before getting dimensions
        if (canvasImage != null) {
            int offsetX = getRenderOffsetX();
            int offsetY = getRenderOffsetY();

            // Apply zoom transformation
            g2d.scale(currentZoomFactor, currentZoomFactor);

            // Draw the canvas image with scaling and centering offsets
            g2d.drawImage(canvasImage, (int) (offsetX / currentZoomFactor), (int) (offsetY / currentZoomFactor), this);

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
        Graphics2D g2d = canvasImage.createGraphics();

        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());

        g2d.setComposite(AlphaComposite.SrcOver);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());

        g2d.dispose();
        revalidate();
        repaint();
    }

    public List<Consumer<Graphics2D>> getPaintListeners() {
        return paintListeners;
    }
}