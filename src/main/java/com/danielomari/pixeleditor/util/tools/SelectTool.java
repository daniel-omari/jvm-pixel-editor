package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.MoveCommand;
import com.danielomari.pixeleditor.commands.Drawcommand;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class SelectTool implements Tool {
    private static SelectTool instance;
    private static Rectangle selectionBounds;//change: make this variable static
    private static BufferedImage selectedContent; //change: make this variable static
    private static Rectangle clipped; //change: make clipped a global variable
    private static BufferedImage clipboardContent;
    private static Rectangle clipboardBounds;
    private final Cursor[] resizeCursors = {
            Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR),
            Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR),
            Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR),
            Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR),
            Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR),
            Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR),
            Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR),
            Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
    };
    private static CanvasPanel canvas; //change: change canvas as static
    private static Point startPoint; //change: make static
    private static Point endPoint; //change: make static
    private boolean isSelecting = false;
    private boolean isMoving = false;
    private boolean isResizing = false;
    private static BufferedImage originalContent; // Store original for proper resizing //change: make this variable static
    private Point dragStart;
    private MoveCommand currentCommand;
    // Context menu and clipboard
    private JPopupMenu contextMenu;
    // Resize state tracking
    private int resizeHandle = -1; // -1: not resizing, 0-7: corner/edge handles
    // Keep track of whether the original area has been cleared
    private boolean hasCleared = false;
    private boolean hasStoppedCreatingNew = false;
    private boolean selectionFinalized = false;
    private boolean shouldAddToUndoStack = false; // Flag to control whether to add to undo stack
    private boolean firstTime = true; // Flag to control whether to add to undo stack
    // Store original location of selection for undo/redo
    private int originalX, originalY, originalWidth, originalHeight;
    private boolean isFloatingPaste = false; // Flag to indicate if the pasted content is floating

    public SelectTool() {
        // Private constructor for singleton
        initContextMenu();
    }

    public void setCanvas(CanvasPanel canvas) {
        SelectTool.canvas = canvas;
        if (canvas != null) {
            setupKeyboardListeners();
        }
    }

    public static SelectTool getInstance() {
        if (instance == null) {
            instance = new SelectTool();
        }
        return instance;
    }

    // Mouse event handling
    @Override
    public void onPress(MouseEvent e) {
//        System.out.println("onPress() called, bounds: " + selectionBounds);
        if (selectionBounds != null && !selectionBounds.contains(e.getPoint()) && currentCommand != null && e.getButton() == MouseEvent.BUTTON1) {
            // If the selection is not null and the left-click is outside of the selection
            if (selectedContent != null) {
                shouldAddToUndoStack = true;
                applySelectedContent();
            }
//            if(selectedContent == null) // Is this doing anything?
            currentCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(currentCommand);
            CommandManager.getInstance().showStack();
            System.out.println();
            selectedContent = null;
            currentCommand = null;
            clearSelection();
//            System.out.println("Tracking mouse press outside selection, clearing selection");
            canvas.repaint();
//            System.out.println("Returning");
            return;
        }

        if (e.getButton() == MouseEvent.BUTTON3) {
            if (selectionBounds != null && selectionBounds.contains(e.getPoint())) {
                showContextMenu(e.getPoint());
                return;
            } else if (clipboardContent != null) {
                showPasteMenu(e.getPoint());
                return;
            }
            if (selectionBounds == null) {
//                System.out.println("Right clicked.");
            }
        }

        resizeHandle = getResizeHandle(e.getPoint());
        if (resizeHandle != -1 && selectionBounds != null) {
            isResizing = true;
            dragStart = e.getPoint();
            originalWidth = selectionBounds.width;
            originalHeight = selectionBounds.height;
            originalX = selectionBounds.x;
            originalY = selectionBounds.y;

            currentCommand = new MoveCommand(canvas, selectionBounds.x, selectionBounds.y);
            currentCommand.storeBeforeState();
//            System.out.println("Resizing selection and storing before state");
            return;
        }

        if (selectionBounds != null && selectionBounds.contains(e.getPoint())) { // Clicked within the bounds of the selection
            if (currentCommand == null) {
                currentCommand = new MoveCommand(canvas, selectionBounds.x, selectionBounds.y);
                currentCommand.storeBeforeState();
//                System.out.println("Started moving selection and storing before state");
            }

            isMoving = true;
            dragStart = e.getPoint();
            originalX = selectionBounds.x;
            originalY = selectionBounds.y;
            return;
        }

        startPoint = e.getPoint();
        endPoint = startPoint;
        selectionBounds = new Rectangle(startPoint);
        isSelecting = true;

    }

    @Override
    public void onDrag(MouseEvent e) {
        if (isSelecting) {
            System.out.println("Is Selecting is true");
            endPoint = e.getPoint();
            updateSelectionBounds();
            canvas.repaint();
        } else if (isMoving && selectionBounds != null) {
            int dx = e.getX() - dragStart.x;
            int dy = e.getY() - dragStart.y;

            // Only clear the original area if we haven't done so already
            if (!isFloatingPaste && !hasCleared) {
                clearSelectedArea();
                hasCleared = true;
            }

            // Update the selection bounds location without modifying startPoint or endPoint
            selectionBounds.setLocation(originalX + dx, originalY + dy);

            canvas.repaint();
        } else if (isResizing && selectionBounds != null) {
            int dx = e.getX() - dragStart.x;
            int dy = e.getY() - dragStart.y;

            Rectangle newBounds = calculateResizedBounds(resizeHandle, dx, dy);

            if (newBounds.width >= 10 && newBounds.height >= 10) {
                if (!hasCleared && !isFloatingPaste) {

                    clearSelectedArea();
                    hasCleared = true;
                }

                selectionBounds = newBounds;
                resizeSelectedContent();
                canvas.repaint();
            }
        }
    }

    @Override
    public void onRelease(MouseEvent e) {
        if (isSelecting) {
            isSelecting = false;
            endPoint = e.getPoint();
            System.out.println("onRelease: endPoint = "+endPoint + " startPoint = " + startPoint);

            // Capture the selected content
            if (selectionBounds.width > 5 && selectionBounds.height > 5) {
                captureSelectedContent();

            } else {
                clearSelection();
            }
        }
        if (isMoving) {
            isMoving = false;
            isFloatingPaste = false;

            if (currentCommand != null && shouldAddToUndoStack || firstTime) {
                firstTime = false;

                currentCommand.storeAfterState();
                CommandManager.getInstance().executeCommand(currentCommand);
//                currentCommand = null;
//                System.out.println("Finished moving selection moved and stored after state");
            }

            hasCleared = false;
            System.out.println("isMoving in SelectTool");

            //change: update start point
            //startPoint = selectionBounds.getLocation();
            //change: update end point
            //endPoint = new Point();
            //endPoint.x = startPoint.x + selectionBounds.x;
            //endPoint.y = startPoint.y + selectionBounds.y;

            //System.out.println("onDrag: selection: "+selectionBounds.x + " " + selectionBounds.y);
            //System.out.println("On drag Start point: "+startPoint.x + " "+ startPoint.y);


        } else if (isResizing) {
            isResizing = false;
            resizeHandle = -1;

            hasCleared = false;

            System.out.println("isResizing in SelectTool");
        }

        //change: update start point
        //test:
        if (selectionBounds==null){
            return;
        }

        startPoint = selectionBounds.getLocation();
        //change: update end point
        endPoint = new Point();
        endPoint.x = startPoint.x + selectionBounds.x;
        endPoint.y = startPoint.y + selectionBounds.y;

        //System.out.println("onDrag: selection: "+selectionBounds.x + " " + selectionBounds.y);
        //System.out.println("On drag Start point: "+startPoint.x + " "+ startPoint.y);
        canvas.repaint();
    }

    // Activate and deactivate methods
    public void activate() {
        if (canvas != null) {
            // Set crosshair cursor by default when select tool is activated
            canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            canvas.addPaintListener(this::drawSelection);
            canvas.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateCursor(e.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    onDrag(e);
                }
            });
        }
    }

    public void deactivate() {
        System.out.println("deactivate function in SelectTool apply");

        if (!selectionFinalized && selectedContent != null) {
            if (currentCommand != null) {
                applySelectedContent(); // this draws the final image

                currentCommand.storeAfterState(); // store the canvas WITH the moved content
                CommandManager.getInstance().executeCommand(currentCommand);
//                System.out.println("Deactivated select tool, moved content to canvas, currentCommand was not null"); // Still need to fix this tool.
//                currentCommand = null;
            }

            if (currentCommand == null) {
                applySelectedContent(); // this draws the final image
            }
            applySelectedContent();

//            currentCommand.storeAfterState();
//            CommandManager.getInstance().executeCommand(currentCommand);
            currentCommand = null;
        }

        clearSelection();
        selectionFinalized = false;
        if (canvas != null) {
            canvas.setCursor(Cursor.getDefaultCursor());
        }
    }

    //
    public Rectangle getSelectionBounds() {
        return selectionBounds;
    }

    // Determine which resize handle (if any) is under the mouse pointer
    private int getResizeHandle(Point p) {
        if (selectionBounds == null) return -1;

        // Define handle detection areas (8 handles: 4 corners, 4 edges)
        int handleSize = 8;
        Rectangle[] handles = new Rectangle[8];

        // Corners: NW, NE, SE, SW
        handles[0] = new Rectangle(selectionBounds.x - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
        handles[2] = new Rectangle(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
        handles[4] = new Rectangle(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);
        handles[6] = new Rectangle(selectionBounds.x - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);

        // Edges: N, E, S, W
        handles[1] = new Rectangle(selectionBounds.x + selectionBounds.width / 2 - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
        handles[3] = new Rectangle(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y + selectionBounds.height / 2 - handleSize / 2, handleSize, handleSize);
        handles[5] = new Rectangle(selectionBounds.x + selectionBounds.width / 2 - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);
        handles[7] = new Rectangle(selectionBounds.x - handleSize / 2, selectionBounds.y + selectionBounds.height / 2 - handleSize / 2, handleSize, handleSize);

        // Check if the point is within any handle
        for (int i = 0; i < handles.length; i++) {
            if (handles[i].contains(p)) {
                return i;
            }
        }

        return -1;
    }

    private Rectangle calculateResizedBounds(int handle, int dx, int dy) {
        Rectangle newBounds = new Rectangle(selectionBounds);

        // Apply changes based on which handle is being dragged
        switch (handle) {
            case 0: // NW corner
                newBounds.x = originalX + dx;
                newBounds.y = originalY + dy;
                newBounds.width = originalWidth - dx;
                newBounds.height = originalHeight - dy;
                break;
            case 1: // N edge
                newBounds.y = originalY + dy;
                newBounds.height = originalHeight - dy;
                break;
            case 2: // NE corner
                newBounds.y = originalY + dy;
                newBounds.width = originalWidth + dx;
                newBounds.height = originalHeight - dy;
                break;
            case 3: // E edge
                newBounds.width = originalWidth + dx;
                break;
            case 4: // SE corner
                newBounds.width = originalWidth + dx;
                newBounds.height = originalHeight + dy;
                break;
            case 5: // S edge
                newBounds.height = originalHeight + dy;
                break;
            case 6: // SW corner
                newBounds.x = originalX + dx;
                newBounds.width = originalWidth - dx;
                newBounds.height = originalHeight + dy;
                break;
            case 7: // W edge
                newBounds.x = originalX + dx;
                newBounds.width = originalWidth - dx;
                break;
        }

        return newBounds;
    }

    public Rectangle getClippedLocation() {
        if (clipped == null) {
            clipped = new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight());
            System.out.println("clipped is null");
        }

        updateSelectionBounds();
        captureSelectedContent();
        System.out.println("From getClipped function: Clipped x = " + clipped.x + "Clipped.y: " + clipped.y);
        return clipped;
    }

    private void resizeSelectedContent() {
        if (selectedContent != null && originalContent != null) {
            // Create a new image with the new dimensions
            BufferedImage resized = new BufferedImage(
                    selectionBounds.width,
                    selectionBounds.height,
                    BufferedImage.TYPE_INT_ARGB);

            // Use Graphics2D for high-quality resizing
            Graphics2D g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw the original content scaled to the new size
            g2d.drawImage(originalContent, 0, 0, selectionBounds.width, selectionBounds.height, null);
            g2d.dispose();

            // Update the selected content with the resized version
            selectedContent = resized;
        }
    }

    private void updateSelectionBounds() {
        //change: set up startPoint and endPoint for rotation
        if (startPoint == null) {
            startPoint = new Point(0, 0);
            System.out.println("start point is null");
        }

        if (endPoint == null) {
            endPoint = new Point(selectedContent.getWidth(), selectedContent.getHeight());
        }

        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);
        selectionBounds = new Rectangle(x, y, width, height);
    }

    public void captureSelectedContent() {
        BufferedImage canvasImage = canvas.getCanvasImage();
        // Ensure selection is within canvas bounds

        //change: make the variable clipped a global variable
        clipped = selectionBounds.intersection(new Rectangle(0, 0, canvasImage.getWidth(), canvasImage.getHeight()));
        System.out.println("From captureSelectContent: clipped.x = "+clipped.x + " clipped.y = " + clipped.y);

        if (clipped.width <= 0 || clipped.height <= 0) {
//            clearSelection();
            return;
        }

        // Create a copy of the selected area with transparency support
        selectedContent = new BufferedImage(clipped.width, clipped.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = selectedContent.createGraphics();
        g.drawImage(canvasImage.getSubimage(clipped.x, clipped.y, clipped.width, clipped.height), 0, 0, null);
        g.dispose();

        // Store original content for resizing
        originalContent = deepCopy(selectedContent);

        // Make white/background pixels transparent
        for (int x = 0; x < selectedContent.getWidth(); x++) {
            for (int y = 0; y < selectedContent.getHeight(); y++) {
                Color pixelColor = new Color(selectedContent.getRGB(x, y), true);
                // If pixel is white or very close to white, make it transparent
                if (pixelColor.getRed() > 250 && pixelColor.getGreen() > 250 && pixelColor.getBlue() > 250) {
                    selectedContent.setRGB(x, y, 0);  // Fully transparent
                }
            }
        }

        // Apply the same transparency to our original content
        for (int x = 0; x < originalContent.getWidth(); x++) {
            for (int y = 0; y < originalContent.getHeight(); y++) {
                Color pixelColor = new Color(originalContent.getRGB(x, y), true);
                // If pixel is white or very close to white, make it transparent
                if (pixelColor.getRed() > 250 && pixelColor.getGreen() > 250 && pixelColor.getBlue() > 250) {
                    originalContent.setRGB(x, y, 0);  // Fully transparent
                }
            }
        }

        clipped = selectionBounds;
        hasCleared = false;
    }

    private void clearSelectedArea() {
        if (isFloatingPaste) {
//            System.out.println("Skipping clearSelectedArea for pasted content.");
            return;
        }

        if (selectionBounds != null) {
            BufferedImage image = canvas.getCanvasImage();

            if (!hasStoppedCreatingNew) {
                Graphics2D g = image.createGraphics();
                g.fillRect(selectionBounds.x, selectionBounds.y, selectionBounds.width, selectionBounds.height);
                g.dispose();
                hasStoppedCreatingNew = true;
            } else {
                // Even this block can cause clearing effects — so skip this entirely for pasted selections
                for (int y = selectionBounds.y; y < selectionBounds.y + selectionBounds.height; y++) {
                    for (int x = selectionBounds.x; x < selectionBounds.x + selectionBounds.width; x++) {
                        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
                            int pixel = image.getRGB(x, y);
                            if (pixel == Color.WHITE.getRGB()) {
                                image.setRGB(x, y, 0xFFFFFFFF); // Fully transparent
                            }
                        }
                    }
                }
            }

            canvas.repaint();
        }
    }


    // Apply the selected content to the canvas at its current position
    public void applySelectedContent() {
        if (!selectionFinalized && selectionBounds != null && selectedContent != null) {
            Graphics2D g = canvas.getCanvasImage().createGraphics();
            g.drawImage(selectedContent, selectionBounds.x, selectionBounds.y, null);
            g.dispose();
            canvas.repaint();
//            selectionFinalized = true;
        }
    }

    private void applyCopiedContent() { // Don't need this currently.
        if (clipboardContent != null && clipboardBounds != null) {
            Graphics2D g = canvas.getCanvasImage().createGraphics();
            g.drawImage(clipboardContent, clipboardBounds.x, clipboardBounds.y, null);
            g.dispose();
            canvas.repaint();
        }
    }

    //change: make this method public
    private void setupKeyboardListeners() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
//            if(clipboardContent != null) {
//                if(e.getID() == KeyEvent.KEY_PRESSED) {
//                    if (e.getKeyCode() == KeyEvent.VK_V) {
//                        if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 || (e.getModifiersEx() & KeyEvent.META_DOWN_MASK) != 0) {
//                            Point mousePoint = canvas.getMousePosition();
//                            if (mousePoint == null) {
//                                mousePoint = new Point(
//                                (canvas.getWidth() - clipboardContent.getWidth()) / 2,
//                                        (canvas.getHeight() - clipboardContent.getHeight()) / 2
//                                        );
//                            }
//                            isFloatingPaste = true;
//                            pasteSelectionAt(mousePoint);
//                            return true;
//                        }
//                    }
//                }
//            }
            if (selectionBounds == null)
                return false;

            if (e.getID() == KeyEvent.KEY_PRESSED) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DELETE:
                        if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) == 0 || (e.getModifiersEx() & KeyEvent.META_DOWN_MASK) != 0) {
                            deleteSelection();
                            return true;
                        }
                        break;
                    case KeyEvent.VK_C:
                        if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 || (e.getModifiersEx() & KeyEvent.META_DOWN_MASK) != 0) {
                            copySelection();
                            return true;
                        }
                        break;
                    case KeyEvent.VK_X:
                        if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 || (e.getModifiersEx() & KeyEvent.META_DOWN_MASK) != 0) {
                            cutSelection();
                            return true;
                        }
                        break;
                    case KeyEvent.VK_V:
                        if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 || (e.getModifiersEx() & KeyEvent.META_DOWN_MASK) != 0) {
                            pasteSelection();
                            return true;
                        }
                        break;
                    case KeyEvent.VK_ESCAPE:
                        if (isSelecting) {
                            clearSelection();
                            canvas.repaint();
                            return true;
                        } else if (isMoving) {
                            isMoving = false;
                            selectionBounds = null;
                            selectedContent = null;
                            originalContent = null;
                            hasCleared = false;
                            canvas.repaint();
                            return true;
                        } else if (isResizing) {
                            isResizing = false;
                            selectionBounds = null;
                            selectedContent = null;
                            originalContent = null;
                            hasCleared = false;
                            canvas.repaint();
                            return true;
                        }
                        break;
//                    case KeyEvent.VK_ENTER:
//                       applySelectedContent();
//                        return true;
                }
            }
            return false;
        });
    }

    public void selectAll(CanvasPanel canvas) {
        if (canvas == null) return;
        selectionBounds = new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight());
        captureSelectedContent(); // Add handling for this to be perma loaded.
    }

    public void drawSelection(Graphics2D g) {
        if (isSelecting) {
            // Draw selection rectangle
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0));
            g.drawRect(selectionBounds.x, selectionBounds.y, selectionBounds.width, selectionBounds.height);
        } else if (selectionBounds != null && selectedContent != null) {

            //change: comment the drawImage command
            // Draw selected content
            g.drawImage(selectedContent, selectionBounds.x, selectionBounds.y, null);

            // Draw selection border
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0));
            g.drawRect(selectionBounds.x, selectionBounds.y, selectionBounds.width, selectionBounds.height);

            // Draw resize handles
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1));

            // Draw the 8 resize handles (4 corners, 4 sides)
            int handleSize = 6;

            // Corners
            g.fillRect(selectionBounds.x - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
            g.fillRect(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
            g.fillRect(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);
            g.fillRect(selectionBounds.x - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);

            // Edges
            g.fillRect(selectionBounds.x + selectionBounds.width / 2 - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
            g.fillRect(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y + selectionBounds.height / 2 - handleSize / 2, handleSize, handleSize);
            g.fillRect(selectionBounds.x + selectionBounds.width / 2 - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);
            g.fillRect(selectionBounds.x - handleSize / 2, selectionBounds.y + selectionBounds.height / 2 - handleSize / 2, handleSize, handleSize);

            // Draw black border around handles
            g.setColor(Color.BLACK);

            // Corners outline
            g.drawRect(selectionBounds.x - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
            g.drawRect(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
            g.drawRect(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);
            g.drawRect(selectionBounds.x - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);

            // Edges outline
            g.drawRect(selectionBounds.x + selectionBounds.width / 2 - handleSize / 2, selectionBounds.y - handleSize / 2, handleSize, handleSize);
            g.drawRect(selectionBounds.x + selectionBounds.width - handleSize / 2, selectionBounds.y + selectionBounds.height / 2 - handleSize / 2, handleSize, handleSize);
            g.drawRect(selectionBounds.x + selectionBounds.width / 2 - handleSize / 2, selectionBounds.y + selectionBounds.height - handleSize / 2, handleSize, handleSize);
            g.drawRect(selectionBounds.x - handleSize / 2, selectionBounds.y + selectionBounds.height / 2 - handleSize / 2, handleSize, handleSize);
        }
    }

    // Update cursor based on mouse position
    public void updateCursor(Point p) {
        if (selectionBounds != null) {
            int handle = getResizeHandle(p);
            if (handle != -1) {
                canvas.setCursor(resizeCursors[handle]);
                return;
            } else if (selectionBounds.contains(p)) {
                canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                return;
            }
        }
        // Set crosshair cursor when not over a selection or handle
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }
    // Methods for activation and deactivation

    public void recolourSelection(Color newColor) {
        if (selectionBounds != null && selectedContent != null) {
            // Create a temp image to avoid modifying during iteration
            BufferedImage recoloredImage = new BufferedImage(
                    selectedContent.getWidth(),
                    selectedContent.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );

            // Process each pixel
            for (int x = 0; x < selectedContent.getWidth(); x++) {
                for (int y = 0; y < selectedContent.getHeight(); y++) {
                    int pixel = selectedContent.getRGB(x, y);
                    // Only modify non-transparent pixels
                    if ((pixel >> 24) != 0) {
                        // Keep alpha channel but replace RGB with new color
                        int alpha = pixel & 0xFF000000; // I don't see this being needed when we are replacing with white.
                        int rgb = newColor.getRGB() & 0x00FFFFFF;
                        recoloredImage.setRGB(x, y, alpha | rgb);
                    }
                }
            }

            // Replace the selected content with recolored version
            selectedContent = recoloredImage;
            originalContent = deepCopy(recoloredImage);
            applySelectedContent();

            // Record command for undo/redo
            currentCommand = new MoveCommand(canvas, selectionBounds.x, selectionBounds.y);
            currentCommand.storeBeforeState();
            currentCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(currentCommand);

//            System.out.println("Selection recolored to: " + newColor);
        }
    }
    // Helper method for deep copying BufferedImages

    private BufferedImage deepCopy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                source.getType()
        );

        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();

        return copy;
    }
    //change: helper function for rotation

    public BufferedImage getselectedContent() {
        if (selectedContent != null) {
            System.out.println("get selected content successful");
            //change: update selection bound
            updateSelectionBounds();
            return selectedContent;
        } else {
            System.out.println("get selected content unsuccessful");
            return null;
        }
    }

    public static void updateSelectionAfterRotation(int x, int y, int width, int height, BufferedImage rotatedImage) {
        //update selection bounds
        selectionBounds = new Rectangle(x, y, width, height);

        //update selectedContent
        selectedContent = rotatedImage;
        //System.out.println("update Selection After Rotation");

        //update originalContent
        originalContent = rotatedImage;
    }

    public static void updateSelectionAfterInsertion(int x, int y, int width, int height, BufferedImage insertedImage) {
        //update selection bounds
        selectionBounds = new Rectangle(x, y, width, height);
        //System.out.println("x: "+selectionBounds.x + " y: "+ selectionBounds.y);

        //update selectedContent
        selectedContent = insertedImage;
        //System.out.println("update Selection After Insertion");

        //update originalContent
        originalContent = insertedImage;
    }

    public static void resetStartPointAfterInsertion() {
        if (startPoint == null) {
            startPoint = new Point(0, 0);
        } else {
            startPoint.setLocation(0, 0);
        }
    }

    // Right click features/context handling
    private void initContextMenu() {
        contextMenu = new JPopupMenu();

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> copySelection());

        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.addActionListener(e -> cutSelection());

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.addActionListener(e -> pasteSelection());

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> deleteSelection());

        contextMenu.add(copyItem);
        contextMenu.add(cutItem);
        contextMenu.add(pasteItem);
        contextMenu.addSeparator();
        contextMenu.add(deleteItem);
    }

    private void showContextMenu(Point location) {
        // Enable/disable menu items based on state
        contextMenu.getComponent(2).setEnabled(clipboardContent != null); // Paste option
        contextMenu.show(canvas, location.x, location.y);
    }

    private void copySelection() {
        if (selectionBounds != null && selectedContent != null) {
            clipboardContent = deepCopy(selectedContent);
            clipboardBounds = new Rectangle(selectionBounds);
            System.out.println("Selection copied to clipboard");
        }
    }

    private void cutSelection() {
        if (selectionBounds != null && selectedContent != null) {
            copySelection();
            deleteSelection();
            System.out.println("Selection cut to clipboard");
        }
    }

    private void showPasteMenu(Point location) {
        JPopupMenu pasteMenu = new JPopupMenu();
        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.addActionListener(e -> pasteSelectionAt(location));
        pasteMenu.add(pasteItem);
        pasteMenu.show(canvas, location.x, location.y);
    }

    private void pasteSelection() {
        if (clipboardContent != null) {
            clearSelection();
            // Create command for undo/redo
            Drawcommand command = new Drawcommand(canvas);

            // Determine the paste location.
            // TODO (canvas detach): getMousePosition() and the fallback below use
            // PANEL coordinates/size; route these through CanvasPanel.screenToImage
            // and the image dimensions once the canvas is decoupled from the window.
            Point location = canvas.getMousePosition();
            if (location == null) {
                // Default to canvas center if mouse position is unavailable
                location = new Point(
                        (canvas.getWidth() - clipboardContent.getWidth()) / 2,
                        (canvas.getHeight() - clipboardContent.getHeight()) / 2
                );
            }

            // Create new selection with clipboard content
            selectedContent = deepCopy(clipboardContent);
            originalContent = deepCopy(clipboardContent);

            // Set bounds at the determined location
            selectionBounds = new Rectangle(
                    location.x,
                    location.y,
                    clipboardContent.getWidth(),
                    clipboardContent.getHeight()
            );

            // Do not clear the area below; just set the selection bounds
            hasStoppedCreatingNew = true;
            command.storeAfterState();
            CommandManager.getInstance().executeCommand(command);
            canvas.repaint();
        }
    }

    private void pasteSelectionAt(Point location) {
        if (clipboardContent != null) {
            clearSelection();
            isFloatingPaste = true;
//            System.out.println("Clipboard is: " + clipboardContent);
            // Create command for undo/redo
            Drawcommand command = new Drawcommand(canvas);
            // Clear current selection if any
            // Create new selection with clipboard content
            selectedContent = deepCopy(clipboardContent);
            originalContent = deepCopy(clipboardContent);

            // Set bounds at the specified location
            selectionBounds = new Rectangle(
                    location.x,
                    location.y,
                    clipboardContent.getWidth(),
                    clipboardContent.getHeight()
            );

            // Apply the content to canvas
            hasStoppedCreatingNew = true;
            command.storeAfterState();
            CommandManager.getInstance().executeCommand(command);
            canvas.repaint();
//            System.out.println("Pasted at location: " + location.x + "," + location.y);
        }
    }

    private void deleteSelection() {
        if (selectionBounds != null) {
            // Create command for undo/redo
            Drawcommand command = new Drawcommand(canvas);

            // Clear the selected area from canvas
            clearSelectedArea();

            // Clear selection state
            clearSelection();

            command.storeAfterState();
            CommandManager.getInstance().executeCommand(command);
            canvas.repaint();
            System.out.println("Selection deleted");
        }
    }

    public void clearSelection() {
        selectionBounds = null;
        originalContent = null;
        isSelecting = false;
        isMoving = false;
        isResizing = false;
        hasCleared = false;
        shouldAddToUndoStack = false;
        selectionFinalized = false;
        hasStoppedCreatingNew = false;
        firstTime = true;
        currentCommand = null;
        originalX = 0;
        originalY = 0;
        originalWidth = 0;
        originalHeight = 0;
        startPoint = null;
        endPoint = null;
        clipped = null;
        selectedContent=null;
    }
}