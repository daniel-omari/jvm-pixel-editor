package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.util.tools.Tool;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.Drawcommand;
import com.danielomari.pixeleditor.ui.MenuBar.HorizontalButtons;
import com.danielomari.pixeleditor.util.tools.SelectTool;
import com.danielomari.pixeleditor.PixelGraphicEditor;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;


public class InsertTool implements Tool {
    private static CanvasPanel canvasPanel;
    private BufferedImage insertedImage;
    private BufferedImage originalCanvasImage;
    private int imageX = 0, imageY = 0; // Position of the image
    private int startX, startY; // Mouse start position
    private boolean dragging = false;
    private Drawcommand currentCommand;
    private SelectTool selectedTool;


    public InsertTool() {
        canvasPanel = CanvasPanel.getInstance();
        selectedTool = SelectTool.getInstance();
    }

    public void insert() {
        currentCommand = new Drawcommand(canvasPanel);

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select an Image to Insert");
        int result = fileChooser.showOpenDialog(null);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            originalCanvasImage = new BufferedImage(
                    canvasPanel.getCanvasImage().getWidth(),
                    canvasPanel.getCanvasImage().getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g2d = originalCanvasImage.createGraphics();
            g2d.drawImage(canvasPanel.getCanvasImage(), 0, 0, null);
            g2d.dispose();

            loadImage(selectedFile);

            //reset select bound so the image can be selected without clicking select again
            SelectTool.updateSelectionAfterInsertion(0, 0, insertedImage.getWidth(),insertedImage.getHeight(), insertedImage);
            SelectTool.resetStartPointAfterInsertion();

            //make the select button active so that all functions about selection can be used
            //Set the tool as current in CanvasPanel
            PixelGraphicEditor.getCanvas().setTool(selectedTool);
            selectedTool.activate();
            System.out.println("Control shift to select tool");

            //setCanvas
            selectedTool.setCanvas(canvasPanel);
            
        }

        // Complete command
        if (currentCommand != null) {
            currentCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(currentCommand);
            currentCommand = null;
        }
    }

    public void loadImage(File file) {
        try {
            insertedImage = ImageIO.read(file);

            if (insertedImage != null) {
                // Fit to the document (the fixed page) rather than the window,
                // since the canvas image is decoupled from the panel size.
                int canvasWidth = canvasPanel.getCanvasImage().getWidth();
                int canvasHeight = canvasPanel.getCanvasImage().getHeight();
                int imgWidth = insertedImage.getWidth();
                int imgHeight = insertedImage.getHeight();

                int newWidth = imgWidth;
                int newHeight = imgHeight;

                if (imgWidth > canvasWidth || imgHeight > canvasHeight) {
                    double scaleX = (double) canvasWidth / imgWidth;
                    double scaleY = (double) canvasHeight / imgHeight;
                    double scale = Math.min(scaleX, scaleY);

                    newWidth = (int) (imgWidth * scale);
                    newHeight = (int) (imgHeight * scale);
                }

                // Create a new BufferedImage to store the resized image
                BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, insertedImage.getType());
                Graphics2D g2d = resizedImage.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.drawImage(insertedImage, 0, 0, newWidth, newHeight, null);
                g2d.dispose();

                // Now update insertedImage to the resized version
                insertedImage = resizedImage;
                // Draw it onto the canvas at (0,0)
                g2d = canvasPanel.getCanvasImage().createGraphics();
                g2d.drawImage(insertedImage, imageX, imageY, null);
                g2d.dispose();

                canvasPanel.repaint();
            }

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to load image!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onPress(MouseEvent e) {
        // Check if the user clicks inside the image
        /* 
        if (insertedImage != null && e.getX() >= imageX && e.getX() <= imageX + insertedImage.getWidth()
                && e.getY() >= imageY && e.getY() <= imageY + insertedImage.getHeight()) {
            dragging = true;

            startX = e.getX();
            startY = e.getY();

            // Store a new command before dragging starts
            currentCommand = new Drawcommand(canvasPanel);
            currentCommand.storeAfterState();  // Store before moving
        }
        */
    }

    @Override
    public void onDrag(MouseEvent e) {
    /*      
        if (dragging && insertedImage != null) {
            // Calculate new position
            int dx = e.getX() - startX;
            int dy = e.getY() - startY;
            imageX += dx;
            imageY += dy;

            startX = e.getX();
            startY = e.getY();

            Graphics2D g2d = canvasPanel.getCanvasImage().createGraphics();

            canvasPanel.clearCanvas(); // Clear previous image position
            // Restore original canvas before drawing the image  (without clearing it)
            if (originalCanvasImage != null) {
                g2d.drawImage(originalCanvasImage, 0, 0, null);
            }

            g2d.drawImage(insertedImage, imageX, imageY, null);

            g2d.dispose();
            canvasPanel.repaint();
        }
    */    
    }

    @Override
    public void onRelease(MouseEvent e) {
    /*      
        dragging = false; // Stop dragging when mouse is released

        //save command for undo/redo
        if (currentCommand != null) {
            currentCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(currentCommand);
            currentCommand = null;
        }
     */
    }
}