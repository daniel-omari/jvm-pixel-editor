package com.danielomari.pixeleditor.util.tools;
import com.danielomari.pixeleditor.util.tools.Tool;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.util.tools.SelectTool;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.Drawcommand;
import com.danielomari.pixeleditor.commands.MoveCommand;
import com.danielomari.pixeleditor.PixelGraphicEditor;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;

public class RotateTool implements Tool {
    private static BufferedImage appliedImage, rotatedImage;
    private static CanvasPanel canvasPanel;
    private static SelectTool selectedTool;
    private static int width,height;
    private static Rectangle clipped;
    private static MoveCommand currentCommand;
    //create new image with new dimension
    private static BufferedImage transformedImage;
    private static BufferedImage selectedContent;

    public RotateTool() {
        selectedTool = SelectTool.getInstance();
        selectedContent = selectedTool.getselectedContent();
    }

    public enum RotateType {RIGHT, LEFT, OPPOSITE, FLIPV, FLIPH};
    private static RotateType selectedRotateType = RotateType.RIGHT; // default rotate type

    public static void setRotateType(RotateType rotateType) {
        selectedRotateType = rotateType;
        System.out.println("Selected type: " + selectedRotateType);
    }

    @Override
    public void onPress(MouseEvent e) {
        //remove select bound from select tool
        selectedTool.clearSelection();
        clipped=null;
        selectedContent = null;
        System.out.println("Selected content = null");
    }

    @Override
    public void onDrag(MouseEvent e) {
        //no action while drags
    }

    public void rotate() {
        canvasPanel = CanvasPanel.getInstance();

        // Snapshot the whole image so undo/redo restores it exactly, even when a
        // quarter-turn changes the canvas dimensions (e.g. 800x600 -> 600x800).
        currentCommand = new MoveCommand(canvasPanel, 0, 0);
        currentCommand.storeBeforeState();

        //selectedContent = selectedTool.getselectedContent();
        BufferedImage wholeCanvas = canvasPanel.getCanvasImage();

        //update canvas before rotation
        canvasPanel.repaint();
        //System.out.println("Canvas repaint before rotate, width: "+canvasPanel.getWidth() + " height: "+canvasPanel.getHeight());
        canvasPanel.doLayout();
        //System.out.println("Canvas dolayout(), width: "+canvasPanel.getWidth() + " height: "+canvasPanel.getHeight());

        boolean isWholeCanvas = (selectedContent == null);
        
        if (isWholeCanvas) {
            appliedImage = wholeCanvas;
            System.out.println("Applied Image = whole canvas");
        } else {
            canvasPanel.repaint();
            appliedImage = selectedContent;
            clipped = selectedTool.getClippedLocation();
            System.out.println("Applied Image from select tool");
            System.out.println("Rotate tool: getClippedLocation: Clipped.x: " + clipped.x + " clipped.y: " + clipped.y);
        }

        if (appliedImage == null) return;

        // Apply rotation using AffineTransformOp
        BufferedImage rotatedImage = rotateImageAffine(appliedImage, selectedRotateType, isWholeCanvas);

        // Set the rotated image to the canvas
        Graphics2D g2d = canvasPanel.getCanvasImage().createGraphics();

        if(isWholeCanvas){
            // Adopt the rotated image as the new canvas; its size may differ
            // (a quarter-turn swaps width and height), so we do NOT crop it back.
            g2d.dispose();
            canvasPanel.setCanvasImage(rotatedImage);

        }else{
            //remove select bound from select tool
            selectedTool.clearSelection();
            //remove the selected content (make that area become white color)
            Graphics2D g2dCanvas = wholeCanvas.createGraphics();
        
            //g2dCanvas.setComposite(AlphaComposite.Src);
            g2dCanvas.setColor(Color.WHITE);

            width = appliedImage.getWidth();
            height = appliedImage.getHeight();

            int safeWidth = Math.min(width, wholeCanvas.getWidth() - clipped.x);
            int safeHeight = Math.min(height, wholeCanvas.getHeight() - clipped.y);

            g2dCanvas.fillRect(clipped.x, clipped.y, safeWidth, safeHeight);
            //System.out.println("clipped x"+clipped.x + " clipped y: "+clipped.y+" safeWidth: "+safeWidth + " safeHeight: "+safeHeight);
            //System.out.println("width: " + width + " height: "+height);

            g2dCanvas.dispose();

            canvasPanel.setCanvasImage(wholeCanvas);
            canvasPanel.repaint();  // Ensure the UI updates
            
            //capture recent canvas
            g2dCanvas = wholeCanvas.createGraphics();

            //paste the rotated image
            g2dCanvas.drawImage(rotatedImage, clipped.x, clipped.y, rotatedImage.getWidth(),rotatedImage.getHeight(),null);

            //System.out.println("Paste rotated image, clipped.x: "+clipped.x + " clipped.y: "+clipped.y);
            g2dCanvas.dispose();
            canvasPanel.setCanvasImage(wholeCanvas);

            //reset select bound so the image can be rotated again
            //SelectTool.updateSelectionAfterRotation(clipped.x, clipped.y, rotatedImage.getWidth(),rotatedImage.getHeight(), rotatedImage);
            //System.out.println("For rotated again: clipped x:" + clipped.x + " clipped.y: "+clipped.y);

            //make the select button active so that all functions about selection can be used
            // Set the tool as current in CanvasPanel
            //PixelGraphicEditor.getCanvas().setTool(selectedTool);
            //selectedTool.activate();
            //System.out.println("Control shift to select tool");

            //deactivate SelectTool
            selectedTool.deactivate();
            selectedContent = null;

            System.out.println("Finish rotation");
        }
        
        canvasPanel.repaint();

        //System.out.println("After rotation: Canvas width:" + canvasPanel.getWidth() + " Canvas height: "+canvasPanel.getHeight());

        // Complete command
        if (currentCommand != null) {
            currentCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(currentCommand);
            currentCommand = null;
        }
    }

    @Override
    public void onRelease(MouseEvent e) {
    }

    private static BufferedImage rotateImageAffine(BufferedImage image, RotateType type, boolean isWholeCanvas) {
        int width = image.getWidth();
        int height = image.getHeight();

        double theta = 0;
        boolean flipHorizontal = false, flipVertical = false;

        // Determine transformation
        switch (type) {
            case RIGHT:    theta = Math.PI / 2; break;
            case LEFT:     theta = -Math.PI / 2; break;
            case OPPOSITE: theta = Math.PI; break;
            case FLIPH:    flipHorizontal = true; break;
            case FLIPV:    flipVertical = true; break;
        }

        // Initial transform (rotate about center)
        AffineTransform transform = new AffineTransform();
        transform.translate(width / 2.0, height / 2.0);
        transform.rotate(theta);
        if (flipHorizontal) transform.scale(-1, 1);
        if (flipVertical) transform.scale(1, -1);
        transform.translate(-width / 2.0, -height / 2.0);

        // Get bounds of transformed image
        Rectangle rotatedBounds = transform.createTransformedShape(new Rectangle(0, 0, width, height)).getBounds();

        // Create destination image big enough for transformed result
        BufferedImage rotated = new BufferedImage(rotatedBounds.width, rotatedBounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, rotatedBounds.width, rotatedBounds.height);

        // Shift transform to keep the image centered in the output
        AffineTransform centeredTransform = new AffineTransform();
        centeredTransform.translate(-rotatedBounds.x, -rotatedBounds.y);
        centeredTransform.concatenate(transform);

        // Apply transformed image
        g2d.drawImage(image, centeredTransform, null);
        g2d.dispose();

        // Return the rotated image at its natural size. For a 90/270 turn the
        // canvas therefore adopts the swapped dimensions (like MS Paint) rather
        // than cropping the rotation back into the old size and clipping content.
        return rotated;
    }

}
