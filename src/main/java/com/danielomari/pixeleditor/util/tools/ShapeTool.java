package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.Drawcommand;
import com.danielomari.pixeleditor.commands.TransformMouseEvent;
import com.danielomari.pixeleditor.util.tools.Tool;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.util.tools.ColorTool;
import com.danielomari.pixeleditor.util.tools.MagnifierTool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

// Draws outlined shapes (rectangle, circle, line, triangle, pentagon, hexagon) with a configurable stroke width.
public class ShapeTool implements Tool {
    private static ShapeTool instance;
    private final Color DEFAULT_COLOR = Color.BLACK;
    private final SelectTool selectTool;
    private CanvasPanel canvas;
    private Point startPoint;
    private Point endPoint;
    private String currentShape;
    private boolean isDrawing;
    private Color currentColor;
    private int strokeWidth = 3;
    private JPopupMenu shapeMenu;
    private Drawcommand currentCommand;
    // Store references to listeners so they can be removed
    private MouseAdapter mouseHandler;
    private Consumer<Graphics2D> paintListener;

    public ShapeTool() {
        this.canvas = CanvasPanel.getInstance();
        this.currentColor = DEFAULT_COLOR;
        this.currentShape = "Rectangle";
        this.selectTool = SelectTool.getInstance();
    }

    public static ShapeTool getInstance() {
        if (instance == null) {
            instance = new ShapeTool();
        }
        return instance;
    }

    public static void deactivateShapeTool() {
        if (instance != null) {
            instance.deactivate();
        }
    }

    public void setCanvas(CanvasPanel canvas) {
        // Remove listeners from old canvas if any
        if (this.canvas != null) {
            removeCanvasListeners();
        }

        this.canvas = canvas;
        selectTool.setCanvas(canvas);
    }

    public void setStrokeWidth(int strokeWidth) {
        this.strokeWidth = strokeWidth;
    }

    public int getStrokeWidth() { return strokeWidth; }

    public String getCurrentShape() { return currentShape; }

    public void setCurrentShape(String shape) {
        this.currentShape = shape;
        this.isDrawing = true;
    }

    private void setupShapeMenu() {
        shapeMenu = new JPopupMenu();
        String[] shapes = {"Rectangle", "Circle", "Line", "Triangle", "Pentagon", "Hexagon"};

        for (String shape : shapes) {
            JMenuItem shapeButton = new JMenuItem(shape);
            shapeButton.addActionListener(e -> {
                currentShape = shape;
                isDrawing = true;
                shapeMenu.setVisible(false);
            });
            shapeMenu.add(shapeButton);
        }
    }

    public void showShapeMenu(Component invoker) {
        if (shapeMenu == null) {
            setupShapeMenu();
        }
        if (invoker != null) {
            shapeMenu.show(invoker, invoker.getWidth(), 0);
        } else {
            // Show centered on screen if no invoker
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(canvas);
            if (frame != null) {
                shapeMenu.show(frame, frame.getWidth() / 2 - 50, frame.getHeight() / 2 - 50);
            }
        }
    }

    @Override
    public void onPress(MouseEvent e) {
        if (isDrawing) {

            // Mouse events arrive from CanvasPanel's dispatch already mapped to
            // image coordinates, so use them directly.
            startPoint = new Point(e.getX(), e.getY());
            endPoint = new Point(startPoint); // Initialise with the start point
            currentCommand = new Drawcommand(canvas);
        } else {
            selectTool.onPress(e);
        }
    }

    @Override
    public void onDrag(MouseEvent e) {
        if (isDrawing && startPoint != null) {
            endPoint = new Point(e.getX(), e.getY());
            canvas.repaint(); // Force repaint to show the preview
        } else {
            selectTool.onDrag(e);
        }
    }

    @Override
    public void onRelease(MouseEvent e) {
        if (isDrawing && startPoint != null) {
            endPoint = new Point(e.getX(), e.getY());
            drawFinalShape();
        } else {
            selectTool.onRelease(e);
        }
    }

    private void setupMouseListeners() {
        // Create the mouse handler if it doesn't exist
        if (mouseHandler == null) {
            mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onPress(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    onRelease(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    onDrag(e);
                }
            };
        }

        // Create paint listener if it doesn't exist
        if (paintListener == null) {
            paintListener = g -> {
                if (isDrawing && startPoint != null && endPoint != null) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    drawPreviewShape(g2d);
                    g2d.dispose();
                }
            };
        }

        // Only the paint listener is registered. Mouse events arrive through
        // CanvasPanel's central dispatch (already mapped to image coordinates),
        // so ShapeTool no longer adds its own mouse listeners, which previously
        // caused the same click to be handled twice on a different coord path.
        canvas.addPaintListener(paintListener);
    }

    private void removeCanvasListeners() {
        if (canvas != null) {
            if (mouseHandler != null) {
                canvas.removeMouseListener(mouseHandler);
                canvas.removeMouseMotionListener(mouseHandler);
            }

            if (paintListener != null) {
                canvas.removePaintListener(paintListener);
            }
        }
    }

    private void drawPreviewShape(Graphics2D g) {
        // The shared Graphics is already translated to the document origin and
        // zoomed, so image-space coordinates draw directly. The extra half-pixel
        // translate centres stroke geometry inside pixel cells, matching exactly
        // how the committed shape rasterises.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Render the stroke geometry literally: the default STROKE_NORMALIZE
        // silently re-rounds coordinates to pixel centres, which shifted the
        // half-pixel-centred path a further half pixel in hard-edge mode.
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(0.5, 0.5);
        g.setColor(setColor());
        g.setStroke(new BasicStroke(strokeWidth));
        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);

        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);

        // Ensure width and height are at least 1 to prevent rendering issues
        width = Math.max(1, width);
        height = Math.max(1, height);

        switch (currentShape) {
            case "Rectangle" -> g.drawRect(x, y, width, height);
            case "Circle" -> g.drawOval(x, y, width, height);
            case "Line" -> g.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);
            case "Triangle" -> {
                int[] xPoints = {x + width / 2, x, x + width};
                int[] yPoints = {y, y + height, y + height};
                g.drawPolygon(xPoints, yPoints, 3);
            }
            case "Pentagon" -> {
                int[] xPoints = new int[5];
                int[] yPoints = new int[5];
                for (int i = 0; i < 5; i++) {
                    xPoints[i] = (int) (x + (double) width / 2 + (double) width / 2 * Math.cos(i * 2 * Math.PI / 5 - Math.PI / 2));
                    yPoints[i] = (int) (y + (double) height / 2 + (double) height / 2 * Math.sin(i * 2 * Math.PI / 5 - Math.PI / 2));
                }
                g.drawPolygon(xPoints, yPoints, 5);
            }
            case "Hexagon" -> {
                int[] xPoints = new int[6];
                int[] yPoints = new int[6];
                for (int i = 0; i < 6; i++) {
                    xPoints[i] = (int) (x + (double) width / 2 + (double) width / 2 * Math.cos(i * 2 * Math.PI / 6));
                    yPoints[i] = (int) (y + (double) height / 2 + (double) height / 2 * Math.sin(i * 2 * Math.PI / 6));
                }
                g.drawPolygon(xPoints, yPoints, 6);
            }
        }
    }

    private Color setColor() {
        return ColorTool.getColor();
    }

    private void drawFinalShape() {
        if (startPoint == null || endPoint == null) return;

        Graphics2D g = CanvasPanel.getInstance().getCanvasImage().createGraphics();
        // Shapes are always anti-aliased (the Photoshop/modern-Paint convention;
        // the Pencil is the hard-edged tool). Half-pixel centring plus
        // STROKE_PURE makes the committed raster land exactly where the
        // preview showed it.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(0.5, 0.5);
        g.setColor(setColor());
        g.setStroke(new BasicStroke(strokeWidth));
        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);

        switch (currentShape) {
            case "Rectangle" -> g.drawRect(x, y, width, height);
            case "Circle" -> g.drawOval(x, y, width, height);
            case "Line" -> g.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);
            case "Triangle" -> {
                int[] xPoints = {x + width / 2, x, x + width};
                int[] yPoints = {y, y + height, y + height};
                g.drawPolygon(xPoints, yPoints, 3);
            }
            case "Pentagon" -> {
                int[] xPoints = new int[5];
                int[] yPoints = new int[5];
                for (int i = 0; i < 5; i++) {
                    xPoints[i] = (int) (x + (double) width / 2 + (double) width / 2 * Math.cos(i * 2 * Math.PI / 5 - Math.PI / 2));
                    yPoints[i] = (int) (y + (double) height / 2 + (double) height / 2 * Math.sin(i * 2 * Math.PI / 5 - Math.PI / 2));
                }
                g.drawPolygon(xPoints, yPoints, 5);
            }
            case "Hexagon" -> {
                int[] xPoints = new int[6];
                int[] yPoints = new int[6];
                for (int i = 0; i < 6; i++) {
                    xPoints[i] = (int) (x + (double) width / 2 + (double) width / 2 * Math.cos(i * 2 * Math.PI / 6));
                    yPoints[i] = (int) (y + (double) height / 2 + (double) height / 2 * Math.sin(i * 2 * Math.PI / 6));
                }
                g.drawPolygon(xPoints, yPoints, 6);
            }
        }
        g.dispose();
        canvas.repaint();

        if (currentCommand != null) {
            currentCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(currentCommand);
            currentCommand = null;
        }

        startPoint = null;
        endPoint = null;
    }

    public void activate() {
        // Make sure we deactivate first to clean up any existing listeners
        deactivate();

        // Then set up for drawing
        isDrawing = true;
        setupMouseListeners();
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

    }

    public void deactivate() {
//        canvas.setZoom(1.0f);
        isDrawing = false;
        removeCanvasListeners();
        startPoint = null;
        endPoint = null;
        if (canvas != null) {
            canvas.setCursor(Cursor.getDefaultCursor());
        }
    }

    public void setColor(Color color) {
        currentColor = color;
    }
}

