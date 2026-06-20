package com.danielomari.pixeleditor.ui.MenuBar;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.ui.Icons;
import com.danielomari.pixeleditor.util.Configuration;
import com.danielomari.pixeleditor.PixelGraphicEditor;
import com.danielomari.pixeleditor.util.Help;
import com.danielomari.pixeleditor.util.tools.PencilTool;
import com.danielomari.pixeleditor.util.tools.BrushTool;
import com.danielomari.pixeleditor.util.tools.EraserTool;
import com.danielomari.pixeleditor.util.tools.ColorTool;
import com.danielomari.pixeleditor.util.Save;
import com.danielomari.pixeleditor.util.tools.SelectTool;
import com.danielomari.pixeleditor.util.tools.InsertTool;
import com.danielomari.pixeleditor.util.tools.ShapeTool;
import com.danielomari.pixeleditor.util.SafeExit;
import com.danielomari.pixeleditor.util.tools.RotateTool;
import com.danielomari.pixeleditor.util.tools.MagnifierTool;
import com.danielomari.pixeleditor.util.tools.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;
import java.util.List;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MenuBars {
    public static JPanel verticalBar;
    public static JPanel horizontalBar;
    public static JButton toggleDarkModeButton;

    private static MenuBars instance;
    private static boolean iconMode = false; // Toggle between text+icon and icon-only mode

    public static JButton brush;
    public static JPopupMenu brushMenu, sizeMenu, brushSizeMenu;
    public static Point buttonLocation; //find button position

    public static JButton eraserSmall, eraserMedium, eraserLarge, eraserExtreme;
    public static JPopupMenu eraserSizeMenu;
    public static JPopupMenu rotateTypeMenu;

    //public static JComboBox<String> textFontMenu; //create drop-down list
    private static JPopupMenu fontPopup;
    private JDialog textSettingsDialog; // floating Text settings panel
    private JDialog toolDialog;          // floating Brush/Pencil settings panel

    private MenuBars() {}

//    private static void toggleMenuMode() {
//        iconMode = !iconMode;
//        for (Component comp : verticalBar.getComponents()) {
//            if (comp instanceof JButton button) {
//                // Save original text if not saved yet
//                if (button.getClientProperty("originalText") == null) {
//                    button.putClientProperty("originalText", button.getText());
//                }
//
//                // Toggle text visibility
//                String originalText = (String) button.getClientProperty("originalText");
//                if (iconMode) {
//                    //Set icon here and remove text
//                    button.setIcon(loadIcon("test.png",32,32)); // Assign icon based on text
//                    //test.png (credit: Freepik) is for internal use only, will be replaced with other icons
//                    button.setText(""); // Hide text
//
//                } else {
//                    button.setText(originalText); // Restore text
//                    button.setIcon(null); // Remove icon when text mode is enabled
//                }
//            }
//        }
//    }

    private static ImageIcon loadIcon(String fileName, int width, int height) {
        try {
            ImageIcon icon = new ImageIcon(Objects.requireNonNull(MenuBars.class.getResource("/icons/" + fileName)));
            Image image = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(image);
        } catch (Exception e) {
            System.err.println("Error loading icon: " + fileName);
            return null;
        }
    }

    private static void toggleDarkMode() {
        try {
            // Loads the config file as an instance.
            Configuration config = Configuration.getInstance(); // Makes more sense to initiate here.
            boolean isDarkMode = config.is("ui.dark.mode.disabled", true); // Starts as true.
            UIManager.setLookAndFeel(isDarkMode ? new FlatMacLightLaf() : new FlatMacDarkLaf());
            config.properties.setProperty("ui.dark.mode.disabled", String.valueOf(!isDarkMode));
            // Update configuration file.
            config.getUpdatedConfiguration();
            // Update UI - it may be better to handle this in a different place and make a setter and getter.
            config.updateUI();
            // Icon-only button: no text to flip.
        } catch (UnsupportedLookAndFeelException e) {
            throw new RuntimeException("Error occurred whilst swapping appearance", e);
        }
    }

    public static void getToggleDarkMode() {
        toggleDarkMode();
    }
    public void createVerticalMenuBar() {
        verticalBar = new JPanel();
        verticalBar.setLayout(new BoxLayout(verticalBar, BoxLayout.Y_AXIS));
        // All Vertical Menu Items

        brush = toolButton(Icons.brush(), "Brush - freehand drawing");
        brush.addActionListener(e -> {
            PixelGraphicEditor.getCanvas().setTool(new BrushTool());
            showBrushSettings(brush);
        });
        verticalBar.add(brush);

        JButton pencil = toolButton(Icons.pencil(), "Pencil");
        pencil.addActionListener(e -> {
            PixelGraphicEditor.getCanvas().setTool(new PencilTool());
            showPencilSettings(pencil);
        });
        verticalBar.add(pencil);

        JButton eraser = toolButton(Icons.eraser(), "Eraser");
        eraser.addActionListener(e -> {
            PixelGraphicEditor.getCanvas().setTool(new EraserTool());
            showEraserSettings(eraser);
        });
        verticalBar.add(eraser);

        JButton fill = toolButton(Icons.fill(), "Fill - click the canvas to flood-fill with the current colour");
        ColorTool colorTool = new ColorTool();
        fill.addActionListener(e -> PixelGraphicEditor.getCanvas().setTool(colorTool));
        verticalBar.add(fill);

        JButton eyedropper = toolButton(Icons.eyedropper(), "Eyedropper - pick colour from canvas (I)");
        eyedropper.addActionListener(e -> PixelGraphicEditor.getCanvas().setTool(new EyedropperTool()));
        verticalBar.add(eyedropper);

        JButton shape = toolButton(Icons.shape(), "Shape - drag to draw");
        shape.addActionListener(e -> {
            ShapeTool st = ShapeTool.getInstance();
            st.setCanvas(PixelGraphicEditor.getCanvas());
            PixelGraphicEditor.getCanvas().setTool(st);
            st.activate();
            showShapeSettings(shape, st);
        });
        verticalBar.add(shape);
        JButton select = toolButton(Icons.select(), "Select - move / resize / copy a region");
        select.addActionListener(e -> {
            // Create and configure the select tool
            SelectTool selectTool = SelectTool.getInstance();
            selectTool.setCanvas(CanvasPanel.getInstance());

            // Set the tool as current in CanvasPanel
            PixelGraphicEditor.getCanvas().setTool(selectTool);

            // The canvas is now properly set, so we can activate
            selectTool.activate();
            System.out.println("Select tool selected");
        });
        verticalBar.add(select);

        JButton zoom = toolButton(Icons.zoom(), "Zoom (M)");

        Action zoomAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PixelGraphicEditor.getCanvas().setTool(new MagnifierTool(PixelGraphicEditor.getCanvas()));
            }
        };

        // Attach action to button click
        zoom.addActionListener(zoomAction);

        verticalBar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke('M'), "activateMagnifier");
        verticalBar.getActionMap().put("activateMagnifier", zoomAction);

        verticalBar.add(zoom);

        //Roatate type buttons
        rotateTypeMenu = new JPopupMenu();

        JButton rotate = toolButton(Icons.rotate(), "Rotate / flip");
        rotate.addActionListener(e -> {
            RotateTool rotateTool = new RotateTool();
            PixelGraphicEditor.getCanvas().setTool(rotateTool);
            showRotateOptions(rotate, rotateTool);
        });

        verticalBar.add(rotate);

        JButton text = toolButton(Icons.text(), "Text");
        text.addActionListener(e -> {
            TextTool textTool = TextTool.getInstance();
            textTool.setCanvas(PixelGraphicEditor.getCanvas());
            PixelGraphicEditor.getCanvas().setTool(textTool);
            showTextSettings(text);
        });

        verticalBar.add(text);

        verticalBar.add(Box.createVerticalGlue()); // keep buttons compact at the top
    }

    // A compact, icon-only tool button with a hover tooltip.
    private JButton toolButton(Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMargin(new Insets(2, 2, 2, 2));
        Dimension d = new Dimension(40, 40); // compact, square icon buttons
        b.setMinimumSize(d);
        b.setPreferredSize(d);
        b.setMaximumSize(d);
        return b;
    }

    // Show a tool's submenu just to the right of its button.
    private static void showSubmenu(JButton button, JPopupMenu menu) {
        if (menu != null) menu.show(button, button.getWidth(), 0);
    }

    public void createHorizontalMenuBar() {
        // All Horizontal Menu Items
        horizontalBar = new JPanel(new GridLayout(1, 8));
        JButton homeButton = new JButton("Home");
        homeButton.setToolTipText("""
                Home
                Let's you toggle auto saving, access the help menu and exit.
                See File->Help->Home for more information.
                """);
        homeButton.setFont(new Font("Comic Sans", Font.BOLD, 14));
        HorizontalButtons homeButtonAction = new HorizontalButtons();
        homeButton.addActionListener(e -> homeButtonAction.getHomeButton(homeButton));
        horizontalBar.add(homeButton);
        JButton fileButton = new JButton("File");
        fileButton.setToolTipText("""
                File
                Let's you open, save, insert, undo, redo and exit.
                See File->Help->File for more information.
                """);
        fileButton.setFont(new Font("Comic Sans", Font.BOLD, 14));
        HorizontalButtons fileButtonAction = new HorizontalButtons();
        fileButton.addActionListener(e -> fileButtonAction.getFileButton(fileButton));
        horizontalBar.add(fileButton);
        JButton insertButton = new JButton("Insert");
        insertButton.setToolTipText("""
                Insert
                Let's you insert an image to the canvas.
                See File->Help->Insert for more information.
                """);
        insertButton.setFont(new Font("Comic Sans", Font.BOLD, 14));
        insertButton.addActionListener(e-> {
            InsertTool InsertTool = new InsertTool();
            PixelGraphicEditor.getCanvas().setTool(InsertTool);
            InsertTool.insert();
        });
        horizontalBar.add(insertButton);
        JButton saveButton = new JButton("Save");
        saveButton.setToolTipText("""
                Save
                Save the current canvas as an image.
                See File->Help->Save for more information.
                """);
        saveButton.setFont(new Font("Comic Sans", Font.BOLD, 14));
        saveButton.addActionListener(new saveButton());
        horizontalBar.add(saveButton);
        JButton undoButton = new JButton("Undo");
        undoButton.setToolTipText("""
                Undo
                Undo the last action.
                See File->Help->Undo for more information.
                """);
        undoButton.setFont(new Font("Comic Sans", Font.BOLD, 14));
        undoButton.addActionListener(new undoButton());
        horizontalBar.add(undoButton);
        JButton redoButton = new JButton("Redo");
        redoButton.setToolTipText("""
                Redo
                Redo the last action.
                See File->Help->Redo for more information.
                """);
        redoButton.setFont(new Font("Comic Sans", Font.BOLD, 14));
        redoButton.addActionListener(new redoButton());
        horizontalBar.add(redoButton);
        JButton exitButton = new JButton("Exit");
        exitButton.setToolTipText("""
                Exit
                Exit the application.
                See File->Help->Exit for more information.
                """);
        exitButton.setFont(new Font("Comic Sans", Font.BOLD, 14));
        exitButton.addActionListener(new SafeExit());
        horizontalBar.add(exitButton);
        
    }

    private static class saveButton implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Save.saveImage();
            System.out.println("Save menu item clicked");
        }
    }

    private static class undoButton implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            System.out.println("Undo menu item clicked");
            CommandManager.getInstance().undo();
            CommandManager.getInstance().showStack();
        }
    }

    private static class redoButton implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            System.out.println("Redo menu item clicked");
            CommandManager.getInstance().redo();
            CommandManager.getInstance().showStack();
        }
    }

    private static class shapeTool implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            ShapeTool shapeTool = ShapeTool.getInstance();
            shapeTool.setCanvas(PixelGraphicEditor.getCanvas());
            PixelGraphicEditor.getCanvas().setTool(shapeTool);
            shapeTool.activate();
            shapeTool.showShapeMenu((Component) e.getSource());
            System.out.println("Shape tool selected");
        }
    }

//    private static void deactivateShapeTool() {
//        ShapeTool shapeTool = ShapeTool.getInstance();
//        shapeTool.deactivate();
//    }

    private static class toggleDarkModeListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            getToggleDarkMode();
        }
    }

    private static void showButtonOptions(JButton button){
        JPopupMenu menuToShow = null;

        if (button.getText().equals("Brush")) {
            menuToShow = brushMenu;
        } else if (button.getText().equals("Eraser")) {
            menuToShow = eraserSizeMenu;
        } else if (button.getText().equals("Text")){
            menuToShow = fontPopup;
        }

        // Show the menu to the **right** of the brush button
        if (menuToShow != null) {
            // Get button position relative to its parent (verticalBar)
            buttonLocation = button.getLocationOnScreen();

            // Show the menu to the **right** of the selected tool's button
            menuToShow.show(button, button.getWidth(), 0);
        }
    }

    private static void showRotateOptions(JButton parentButton, RotateTool rotateTool) {
        JPopupMenu rotateMenu = new JPopupMenu();

        String[] rotateTypeNames = {
                "Rotate Right 90\u00B0", "Rotate Left 90\u00B0", "Rotate 180\u00B0", "Flip vertical","Flip Horizontal"
        };
        RotateTool.RotateType[] rotateTypes = {
                RotateTool.RotateType.RIGHT, RotateTool.RotateType.LEFT, RotateTool.RotateType.OPPOSITE, RotateTool.RotateType.FLIPV, RotateTool.RotateType.FLIPH
        };

        for (int i = 0; i < rotateTypeNames.length; i++) {
            JMenuItem menuItem = new JMenuItem(rotateTypeNames[i]);
            RotateTool.RotateType rotateType = rotateTypes[i];

            menuItem.addActionListener(e -> {
                RotateTool.setRotateType(rotateType);
                rotateTool.rotate();  // Perform rotation when sub-button is clicked
            });
            rotateMenu.add(menuItem);
        }
        rotateMenu.show(parentButton, parentButton.getWidth(), 0);
    }

    private void createFontSelectionMenu() {
        System.out.println("Enter createFontSelectionMenu");

        //test for available font
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();
        //System.out.println("Available fonts: " + Arrays.toString(fontNames));
        
        // Filter out symbol fonts dynamically
        List<String> filteredFonts = Arrays.stream(fontNames)
            .filter(font -> !isSymbolFont(font)) // Keep fonts that are NOT symbols
            .sorted() // Optional: Sort alphabetically
            .collect(Collectors.toList());

        // Create a scrollable list of fonts
        JList<String> fontList = new JList<>(filteredFonts.toArray(new String[0]));
        fontList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // Allow single selection

        // Add scroll pane to the popup
        JScrollPane scrollPane = new JScrollPane(fontList);
        scrollPane.setPreferredSize(new Dimension(250, 200)); // Set max height to 200px

        // Create the popup menu and add dropdown to it
        fontPopup = new JPopupMenu();
        fontPopup.add(scrollPane);

        // Listen for font selection
        fontList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // Ensure event fires only once per selection
                String selectedFont = fontList.getSelectedValue();
                TextTool textTool = TextTool.getInstance();
                textTool.setSelectedFont(selectedFont);
                System.out.println("Selected font: " + selectedFont);
                fontPopup.setVisible(false); // Close the popup after selection

                // Show font size dialog after font selection
                textTool.promptForFontSize();
            }
        });

    }

    // Helper method to set the default font
    private void setDefaultFont(String[] fontNames, JList<String> fontList) {
        int arialIndex = -1;
        for (int i = 0; i < fontNames.length; i++) {
            if (fontNames[i].equalsIgnoreCase("Arial")) {
                arialIndex = i;
                break;
            }
        }

        if (arialIndex != -1) {
            fontList.setSelectedIndex(arialIndex); // Highlight Arial in the list
            TextTool.getInstance().setSelectedFont("Arial"); // Set default in TextTool
        }
    }

    private boolean isSymbolFont(String fontName) {
        try {
            // Create a temporary font instance
            Font font = new Font(fontName, Font.PLAIN, 12);
            
            // Test if the font can display basic Latin characters
            String testCharacters = "AaBbCcXxYyZz0123456789!@#$%^&*()";
            return font.canDisplayUpTo(testCharacters) != -1; // Returns true if it's a symbol font
        } catch (Exception e) {
            // Handle invalid fonts gracefully
            System.err.println("Error checking font: " + fontName);
            return true; // Exclude problematic fonts
        }
    }
    
    public static synchronized MenuBars getInstance() {
        if (instance == null) {
            instance = new MenuBars();
        }
        return instance;
    }

    // Floating Text settings panel anchored to the Text button: font, size,
    // bold / italic / underline, and a custom colour. Applies live to new text.
    private void showTextSettings(JButton anchor) {
        if (textSettingsDialog != null && textSettingsDialog.isShowing()) {
            textSettingsDialog.toFront();
            return;
        }
        TextTool tt = TextTool.getInstance();

        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        List<String> fonts = Arrays.stream(ge.getAvailableFontFamilyNames())
                .filter(f -> !isSymbolFont(f)).sorted().collect(Collectors.toList());
        JComboBox<String> fontCombo = new JComboBox<>(fonts.toArray(new String[0]));
        fontCombo.setSelectedItem(tt.getSelectedFont());
        fontCombo.addActionListener(e -> {
            Object v = fontCombo.getSelectedItem();
            if (v != null) tt.setSelectedFont(v.toString());
        });
        panel.add(labeledField("Font", fontCombo));

        JSpinner size = new JSpinner(new SpinnerNumberModel(tt.getFontSize(), 1, 500, 1));
        size.addChangeListener(e -> tt.setFontSize((Integer) size.getValue()));
        panel.add(labeledField("Size", size));

        JToggleButton boldBtn = new JToggleButton("B", tt.isBold());
        boldBtn.setFont(boldBtn.getFont().deriveFont(Font.BOLD, 13f));
        boldBtn.addActionListener(e -> tt.setBold(boldBtn.isSelected()));
        JToggleButton italicBtn = new JToggleButton("I", tt.isItalic());
        italicBtn.setFont(italicBtn.getFont().deriveFont(Font.ITALIC, 13f));
        italicBtn.addActionListener(e -> tt.setItalic(italicBtn.isSelected()));
        JToggleButton underlineBtn = new JToggleButton("<html><u>U</u></html>", tt.isUnderline());
        underlineBtn.addActionListener(e -> tt.setUnderline(underlineBtn.isSelected()));
        JPanel styleRow = new JPanel(new GridLayout(1, 3, 4, 0));
        styleRow.add(boldBtn);
        styleRow.add(italicBtn);
        styleRow.add(underlineBtn);
        panel.add(labeledField("Style", styleRow));

        JButton colourBtn = new JButton("Choose...");
        colourBtn.setBackground(tt.getTextColor());
        colourBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(anchor, "Text colour", tt.getTextColor());
            if (c != null) {
                tt.setTextColor(c);
                colourBtn.setBackground(c);
            }
        });
        panel.add(labeledField("Colour", colourBtn));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(anchor), "Text Settings");
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        dialog.setContentPane(panel);
        dialog.pack();
        Point loc = anchor.getLocationOnScreen();
        dialog.setLocation(loc.x + anchor.getWidth() + 4, loc.y);
        dialog.setVisible(true);
        textSettingsDialog = dialog;
    }

    private JPanel labeledField(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(54, 1));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    // Brush settings: style + size + opacity, in one floating panel.
    private void showBrushSettings(JButton anchor) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] styleNames = {"Natural", "Spray", "Dotted", "Oil", "Stars"};
        BrushTool.BrushType[] types = {
                BrushTool.BrushType.option1, BrushTool.BrushType.option2, BrushTool.BrushType.option3,
                BrushTool.BrushType.option4, BrushTool.BrushType.option5
        };
        JComboBox<String> style = new JComboBox<>(styleNames);
        style.setSelectedIndex(BrushTool.getBrushType().ordinal());
        style.addActionListener(e -> BrushTool.setBrushType(types[style.getSelectedIndex()]));
        panel.add(labeledField("Style", style));

        JSlider size = new JSlider(1, 300, BrushTool.getSizePx());
        size.addChangeListener(e -> BrushTool.setSizePx(size.getValue()));
        panel.add(labeledField("Size", size));

        JSlider opacity = new JSlider(0, 100, Math.round(BrushTool.getOpacity() * 100));
        opacity.addChangeListener(e -> BrushTool.setOpacity(opacity.getValue() / 100f));
        panel.add(labeledField("Opacity", opacity));

        showToolDialog("Brush", anchor, panel);
    }

    // Pencil settings: size + opacity.
    private void showPencilSettings(JButton anchor) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JSlider size = new JSlider(1, 200, PencilTool.getSize());
        size.addChangeListener(e -> PencilTool.setSize(size.getValue()));
        panel.add(labeledField("Size", size));

        JSlider opacity = new JSlider(0, 100, Math.round(PencilTool.getOpacity() * 100));
        opacity.addChangeListener(e -> PencilTool.setOpacity(opacity.getValue() / 100f));
        panel.add(labeledField("Opacity", opacity));

        showToolDialog("Pencil", anchor, panel);
    }

    // Eraser settings: size.
    private void showEraserSettings(JButton anchor) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JSlider size = new JSlider(1, 400, EraserTool.getSize());
        size.addChangeListener(e -> EraserTool.setSize(size.getValue()));
        panel.add(labeledField("Size", size));
        showToolDialog("Eraser", anchor, panel);
    }

    // Shape settings: which shape to draw + the stroke width.
    private void showShapeSettings(JButton anchor, ShapeTool shapeTool) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        String[] shapes = {"Rectangle", "Circle", "Line", "Triangle", "Pentagon", "Hexagon"};
        JComboBox<String> shapeBox = new JComboBox<>(shapes);
        shapeBox.setSelectedItem(shapeTool.getCurrentShape());
        shapeBox.addActionListener(e -> {
            Object v = shapeBox.getSelectedItem();
            if (v != null) shapeTool.setCurrentShape(v.toString());
        });
        panel.add(labeledField("Shape", shapeBox));
        JSlider stroke = new JSlider(1, 30, shapeTool.getStrokeWidth());
        stroke.addChangeListener(e -> shapeTool.setStrokeWidth(stroke.getValue()));
        panel.add(labeledField("Stroke", stroke));
        showToolDialog("Shape", anchor, panel);
    }

    // Shared floating settings dialog anchored to a tool button.
    private void showToolDialog(String title, JButton anchor, JComponent content) {
        if (toolDialog != null) toolDialog.dispose();
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(anchor), title);
        d.setModalityType(Dialog.ModalityType.MODELESS);
        d.setContentPane(content);
        d.pack();
        Point loc = anchor.getLocationOnScreen();
        d.setLocation(loc.x + anchor.getWidth() + 4, loc.y);
        d.setVisible(true);
        toolDialog = d;
    }

    public void showHelpDocumentation() {
        // Create and show the help dialog using the existing Help class
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(PixelGraphicEditor.getCanvas());
        Help helpTool = new Help(parentFrame);
        helpTool.showHelp();
    }
}
