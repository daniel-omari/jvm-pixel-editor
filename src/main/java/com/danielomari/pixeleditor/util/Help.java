package com.danielomari.pixeleditor.util;

import com.danielomari.pixeleditor.PixelGraphicEditor;

import javax.swing.JOptionPane;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

// The Help dialog: a tool/menu picker on the left and HTML instructions on the right.
public class Help {
    private final JDialog helpDialog;
    private final JTextPane instructionPane;

    public Help(Frame parent) {
        helpDialog = new JDialog(parent, "Help Documentation", true);
        instructionPane = createInstructionPane();
        initializeDialog();
    }

    private JTextPane createInstructionPane() {
        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setText("<html><div style='padding:20px;text-align:center;'>"
                + "<h1>Pixel Graphic Editor</h1>"
                + "<h2>Help Documentation</h2>"
                + "<p>Open this any time from <b>Home &rarr; Help</b> or with <b>Ctrl + H</b>.</p>"
                + "<h2>Pick a menu or tool on the left/top to see how it works.</h2></div></html>");
        pane.setEditable(false);
        return pane;
    }

    private void initializeDialog() {
        helpDialog.setLayout(new BorderLayout());
        helpDialog.setSize(820, 620);
        helpDialog.setLocationRelativeTo(helpDialog.getParent());

        helpDialog.add(createHelpHorizontalBar(), BorderLayout.NORTH);
        helpDialog.add(createHelpVerticalBar(), BorderLayout.WEST);
        helpDialog.add(new JScrollPane(instructionPane), BorderLayout.CENTER);
        helpDialog.add(createBottomPanel(), BorderLayout.SOUTH);

        // ESC closes the dialog.
        helpDialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "CLOSE");
        helpDialog.getRootPane().getActionMap().put("CLOSE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helpDialog.dispose();
            }
        });
    }

    private JPanel createHelpHorizontalBar() {
        String[] buttons = {"Home", "File", "Undo", "Redo", "Exit"};
        JPanel horizontalBar = new JPanel(new GridLayout(1, buttons.length));
        for (String label : buttons) {
            JButton btn = new JButton(label);
            btn.setFont(new Font("Comic Sans", Font.BOLD, 14));
            btn.addActionListener(e -> updateInstructions(label));
            horizontalBar.add(btn);
        }
        return horizontalBar;
    }

    private JPanel createHelpVerticalBar() {
        String[] tools = {"Brush", "Pencil", "Eraser", "Eyedropper", "Colour", "Shape",
                "Select", "Text", "Zoom & Pan", "Rotate", "Layers", "Keybinds"};
        JPanel verticalBar = new JPanel(new GridLayout(tools.length, 1));
        for (String tool : tools) {
            JButton btn = new JButton(tool);
            btn.setFont(new Font("Comic Sans", Font.BOLD, 16));
            btn.addActionListener(e -> updateInstructions(tool));
            verticalBar.add(btn);
        }
        return verticalBar;
    }

    private JPanel createBottomPanel() {
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> helpDialog.dispose());
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(okButton);
        return bottomPanel;
    }

    private void updateInstructions(String componentName) {
        instructionPane.setText(getInstructionsForComponent(componentName));
        instructionPane.setCaretPosition(0);
    }

    private String getInstructionsForComponent(String componentName) {
        switch (componentName) {
            case "Home":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Home</h3>"
                    + "<p><b>Sub-menus</b></p>"
                    + "<ul>"
                    + "<li><b>Canvas Selection</b>: set the document size by hand or from a preset (e.g. 1920 x 1080). Resizing keeps your layers, anchored top-left.</li>"
                    + "<li><b>Auto Save</b>: toggle periodic automatic saving on/off.</li>"
                    + "<li><b>Reset Layout</b>: restore the tool / colour / layers panels to their default sizes.</li>"
                    + "<li><b>Exit</b>: close the editor.</li>"
                    + "<li><b>Help</b>: open this window (also <b>Ctrl + H</b>).</li>"
                    + "</ul></div></html>";

            case "File":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>File</h3>"
                    + "<p>The editor separates <b>flattened images</b> (for sharing / other apps) from <b>projects</b> (which keep your layers).</p>"
                    + "<ul>"
                    + "<li><b>New</b>: start a fresh blank document (Ctrl + N).</li>"
                    + "<li><b>Open Image</b>: bring a PNG / JPEG / BMP onto the canvas (Ctrl + O).</li>"
                    + "<li><b>Open Project</b>: open a <b>.pxe</b> project, restoring every layer.</li>"
                    + "<li><b>Save Image</b>: export the flattened picture (Ctrl + S).</li>"
                    + "<li><b>Save Image As</b>: export to a chosen format / location (Ctrl + Shift + S).</li>"
                    + "<li><b>Save Project</b>: save a <b>.pxe</b> project that preserves all layers, names, opacity and visibility so you can keep editing later.</li>"
                    + "</ul>"
                    + "<p><b>Image formats:</b> PNG (keeps transparency), JPEG and BMP (flattened onto white).</p>"
                    + "<p><b>Tip:</b> use <b>.pxe</b> while you work; export a PNG when you want to share it or open it in another app such as Photoshop.</p>"
                    + "</div></html>";

            case "Undo":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Undo</h3>"
                    + "<ul>"
                    + "<li>Reverts the last action (<b>Ctrl + Z</b>).</li>"
                    + "<li>Covers strokes, shapes, text, fills, and layer add / delete / duplicate / merge.</li>"
                    + "</ul></div></html>";

            case "Redo":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Redo</h3>"
                    + "<ul>"
                    + "<li>Re-applies the last undone action (<b>Ctrl + Y</b>).</li>"
                    + "</ul></div></html>";

            case "Exit":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Exit</h3>"
                    + "<ol>"
                    + "<li>Click <b>Exit</b> (or press <b>Ctrl + Q</b>).</li>"
                    + "<li>Choose whether to save before closing.</li>"
                    + "</ol></div></html>";

            case "Brush":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Brush</h3>"
                    + "<p>Click the <b>Brush</b> button (or press <b>B</b>). A settings panel opens from the button.</p>"
                    + "<p><b>Settings</b></p>"
                    + "<ul>"
                    + "<li><b>Brush type</b>: Natural, Spray, Dotted, or Oil.</li>"
                    + "<li><b>Size</b>: a continuous slider up to <b>300 px</b>.</li>"
                    + "<li><b>Opacity</b>: how strong the whole stroke is.</li>"
                    + "</ul>"
                    + "<p>A <b>ring at the cursor</b> previews the exact brush size. Draw with the left mouse button; the current colour comes from the Colour panel.</p>"
                    + "</div></html>";

            case "Pencil":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Pencil</h3>"
                    + "<p>Click the <b>Pencil</b> button (or press <b>P</b>) to open its settings panel.</p>"
                    + "<ul>"
                    + "<li>A <b>hard-edged</b> stroke (no soft anti-aliasing), good for crisp lines.</li>"
                    + "<li><b>Size</b> slider up to <b>200 px</b>, plus an <b>Opacity</b> slider.</li>"
                    + "<li>The cursor ring shows the current size.</li>"
                    + "</ul></div></html>";

            case "Eraser":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Eraser</h3>"
                    + "<p>Click the <b>Eraser</b> button (or press <b>E</b>) to open its settings panel.</p>"
                    + "<ul>"
                    + "<li>Erases to <b>transparent</b> on the active layer, revealing the layers (or checkerboard) beneath - it does not paint white.</li>"
                    + "<li><b>Size</b> slider up to <b>400 px</b>; the cursor ring shows the size.</li>"
                    + "</ul></div></html>";

            case "Eyedropper":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Eyedropper</h3>"
                    + "<p>Click the <b>Eyedropper</b> button (or press <b>I</b>).</p>"
                    + "<ul>"
                    + "<li>Click anywhere to sample that pixel's colour and make it the current colour.</li>"
                    + "<li>A <b>loupe</b> magnifier follows the cursor so you can pick precisely.</li>"
                    + "<li>It samples the composited image (all visible layers).</li>"
                    + "</ul></div></html>";

            case "Colour":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Colour</h3>"
                    + "<p>The <b>Colour panel</b> is docked at the top-right (above Layers).</p>"
                    + "<ul>"
                    + "<li>Pick a hue on the bar, then a shade in the <b>HSV square</b> (Photoshop-style).</li>"
                    + "<li>The <b>current colour</b> is shown, along with a row of <b>recent colours</b>.</li>"
                    + "<li><b>Custom...</b> opens the full system colour chooser.</li>"
                    + "<li>The chosen colour is used by the Brush, Pencil, Shape, Text and Fill tools.</li>"
                    + "</ul></div></html>";

            case "Shape":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Shape</h3>"
                    + "<p>Click the <b>Shape</b> button (or press <b>S</b>) to open its settings panel.</p>"
                    + "<p><b>Shapes</b>: Rectangle, Circle, Line, Triangle, Pentagon, Hexagon.</p>"
                    + "<ul>"
                    + "<li>Choose the shape and set the <b>stroke width</b>.</li>"
                    + "<li>Drag on the canvas to draw it in the current colour on the active layer.</li>"
                    + "</ul></div></html>";

            case "Select":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Select</h3>"
                    + "<p>Click the <b>Select</b> button (or press <b>C</b>).</p>"
                    + "<ol>"
                    + "<li>Drag to mark a rectangular area.</li>"
                    + "<li><b>Left-click + drag</b> inside it to move the contents.</li>"
                    + "<li><b>Right-click</b> for Copy / Cut / Paste / Delete.</li>"
                    + "<li><b>Ctrl + A</b> selects the whole canvas; <b>Esc</b> clears the selection.</li>"
                    + "</ol></div></html>";

            case "Text":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Text</h3>"
                    + "<p>Click the <b>Text</b> button (or press <b>T</b>).</p>"
                    + "<ol>"
                    + "<li><b>Left-click</b> the canvas to start a text box.</li>"
                    + "<li>Type your text, then press <b>Enter</b> to commit it (each text item lands on its own layer and stays re-editable).</li>"
                    + "<li><b>Right-click</b> the Text button for font, size and style options.</li>"
                    + "<li>Press <b>Esc</b> to finish.</li>"
                    + "</ol></div></html>";

            case "Zoom & Pan":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Zoom &amp; Pan</h3>"
                    + "<p><b>Zoom</b></p>"
                    + "<ul>"
                    + "<li><b>Mouse wheel</b> (or <b>Ctrl + wheel</b>) zooms in / out around the cursor - works with any tool.</li>"
                    + "<li>The <b>Zoom</b> tool (press <b>Z</b>): left-click to zoom in, right-click to zoom out, or drag to scrub.</li>"
                    + "<li>The <b>status bar</b> (bottom-right) shows the live zoom %, with <b>Fit</b> (fit the canvas to the window) and <b>100%</b> (actual size).</li>"
                    + "</ul>"
                    + "<p><b>Pan</b></p>"
                    + "<ul>"
                    + "<li>Hold <b>Spacebar</b> and drag, or drag with the <b>middle mouse button</b>, to move the view.</li>"
                    + "</ul></div></html>";

            case "Rotate":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Rotate</h3>"
                    + "<p><b>Options</b>: Rotate 90\u00B0 right, 90\u00B0 left, 180\u00B0, Flip Vertical, Flip Horizontal.</p>"
                    + "<p><b>Whole canvas:</b> click the <b>Rotate</b> button and choose an option.</p>"
                    + "<p><b>A selected area:</b> mark it with the <b>Select</b> tool first, then rotate.</p>"
                    + "</div></html>";

            case "Layers":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Layers</h3>"
                    + "<p>The <b>Layers panel</b> is docked at the bottom-right. The <b>active</b> layer (highlighted) is the one every tool draws on; layers are listed top-first.</p>"
                    + "<p><b>Buttons</b></p>"
                    + "<ul>"
                    + "<li><b>Add</b> / <b>Del</b>: new layer / delete the active one.</li>"
                    + "<li><b>Up</b> / <b>Dn</b>: move the active layer up or down the stack.</li>"
                    + "<li><b>Duplicate</b>: copy the active layer.</li>"
                    + "<li><b>Merge Down</b>: flatten the active layer into the one below it.</li>"
                    + "</ul>"
                    + "<p><b>Per layer</b></p>"
                    + "<ul>"
                    + "<li>Toggle the <b>eye</b> to show / hide it.</li>"
                    + "<li>Drag the <b>opacity</b> slider to blend it.</li>"
                    + "<li>Click the name of the active layer to <b>rename</b> it inline.</li>"
                    + "<li>Drag the <b>&#8801;</b> grip to reorder.</li>"
                    + "<li><b>Right-click</b> a layer for Duplicate / Merge Down / Rename / Delete.</li>"
                    + "</ul>"
                    + "<p>All layer changes are undoable with <b>Ctrl + Z</b>.</p>"
                    + "</div></html>";

            case "Keybinds":
                return "<html><div style='padding: 10px;'>"
                    + "<h3>Keybinds</h3>"
                    + "<p><b>General</b></p>"
                    + "<ul>"
                    + "<li><b>Ctrl + Z</b> - Undo</li>"
                    + "<li><b>Ctrl + Y</b> - Redo</li>"
                    + "<li><b>Ctrl + S</b> - Save Image</li>"
                    + "<li><b>Ctrl + Shift + S</b> - Save Image As</li>"
                    + "<li><b>Ctrl + N</b> - New File</li>"
                    + "<li><b>Ctrl + O</b> - Open Image</li>"
                    + "<li><b>Ctrl + A</b> - Select All</li>"
                    + "<li><b>Esc</b> - Deselect</li>"
                    + "<li><b>Ctrl + H</b> - Help</li>"
                    + "<li><b>Ctrl + Q</b> - Exit</li>"
                    + "</ul>"
                    + "<p><b>Tools</b></p>"
                    + "<ul>"
                    + "<li><b>B</b> - Brush</li>"
                    + "<li><b>P</b> - Pencil</li>"
                    + "<li><b>E</b> - Eraser</li>"
                    + "<li><b>I</b> - Eyedropper</li>"
                    + "<li><b>S</b> - Shape</li>"
                    + "<li><b>C</b> - Select</li>"
                    + "<li><b>Z</b> - Zoom</li>"
                    + "<li><b>T</b> - Text</li>"
                    + "</ul>"
                    + "<p><b>Navigation</b></p>"
                    + "<ul>"
                    + "<li><b>Mouse wheel</b> / <b>Ctrl + wheel</b> - Zoom to cursor</li>"
                    + "<li><b>G</b> - Toggle the pixel grid (shown from 800% zoom)</li>"
                    + "<li><b>Spacebar + drag</b> or <b>middle-mouse drag</b> - Pan the view</li>"
                    + "</ul></div></html>";

            default:
                return "<html><div style='padding: 10px;'>"
                    + "<h3>" + componentName + "</h3>"
                    + "<p>No specific instructions available for this component.</p>"
                    + "</div></html>";
        }
    }

    public void showHelp() {
        helpDialog.setVisible(true);
    }
}
