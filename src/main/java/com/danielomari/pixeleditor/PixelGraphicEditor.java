package com.danielomari.pixeleditor;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.danielomari.pixeleditor.ui.MenuBar.MenuBars;
import com.danielomari.pixeleditor.util.Configuration;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.ui.LayersPanel;
import com.danielomari.pixeleditor.ui.ColourPanel;
import com.formdev.flatlaf.FlatLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;


public class PixelGraphicEditor {
    private final JFrame mainFrame;
    private JLabel zoomLabel; // live zoom % in the status bar
    private static CanvasPanel canvas;
    private static JSplitPane leftSplit;   // tools | (canvas + right dock)
    private static JSplitPane rightSplit;  // canvas | right dock
    private static JSplitPane rightDock;   // colour (top) / layers (bottom)
    private static final String LEFT_DIVIDER = "layout.left.divider.v2"; // bumped: icon toolbar default
    private static final String RIGHT_DIVIDER = "layout.right.divider";
    private static final String DOCK_DIVIDER = "layout.dock.divider";
    private static final int DEFAULT_LAYERS_W = 220;

    public PixelGraphicEditor() {
        mainFrame = new JFrame("Pixel Graphic Editor");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Size the window to fit within the screen minus any bars/docks/etc
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();

        Rectangle screenBounds = gc.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

        int usableWidth = screenBounds.width - screenInsets.left - screenInsets.right;
        int usableHeight = screenBounds.height - screenInsets.top - screenInsets.bottom;

        mainFrame.setSize(usableWidth, usableHeight);
        mainFrame.setLocation(screenBounds.x + screenInsets.left, screenBounds.y + screenInsets.top);

        mainFrame.setMinimumSize(new Dimension(200, 200));
        mainFrame.setLayout(new BorderLayout());
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setAlwaysOnTop(true);

        // Create and attach the UI components.
        MenuBars menuBars = MenuBars.getInstance();
        menuBars.createVerticalMenuBar();
        menuBars.createHorizontalMenuBar();
        createCanvas();

        // Resizable layout: tools | ( canvas | [ colour / layers ] ), draggable dividers.
        LayersPanel layersPanel = new LayersPanel(canvas);
        ColourPanel colourPanel = new ColourPanel();
        rightDock = new JSplitPane(JSplitPane.VERTICAL_SPLIT, colourPanel, layersPanel);
        rightDock.setResizeWeight(0.0);    // colour stays on top, layers grows below
        rightDock.setContinuousLayout(true);
        rightDock.setOneTouchExpandable(true);

        rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, rightDock);
        rightSplit.setResizeWeight(1.0);   // canvas absorbs window resizing
        rightSplit.setContinuousLayout(true);
        rightSplit.setOneTouchExpandable(true);

        leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, MenuBars.verticalBar, rightSplit);
        leftSplit.setResizeWeight(0.0);    // the tool column keeps its width
        leftSplit.setContinuousLayout(true);
        leftSplit.setOneTouchExpandable(true);

        mainFrame.add(MenuBars.horizontalBar, BorderLayout.NORTH);
        mainFrame.add(leftSplit, BorderLayout.CENTER);
        mainFrame.add(buildStatusBar(), BorderLayout.SOUTH);

        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                canvas.repaint(); // Re-centre the fixed document when the window resizes.
            }
        });
        mainFrame.setResizable(true);
        mainFrame.setVisible(true);
        EventQueue.invokeLater(() -> {
            mainFrame.setAlwaysOnTop(false);

            // Show the help window only on the first ever launch, then remember it
            // (persisted in the config file) so it does not reopen every time.
            Configuration config = Configuration.getInstance();
            if (!config.is("help.shown", false)) {
                Timer timer = new Timer(500, e -> MenuBars.getInstance().showHelpDocumentation());
                timer.setRepeats(false);
                timer.start();
                config.properties.setProperty("help.shown", "true");
                try {
                    config.getUpdatedConfiguration();
                } catch (RuntimeException ex) {
                    // Non-fatal: if the flag can't be saved, help simply shows again next launch.
                }
            }

            // Restore saved panel sizes (or sensible defaults), then persist future changes.
            leftSplit.setDividerLocation(
                    config.getInt(LEFT_DIVIDER, MenuBars.verticalBar.getPreferredSize().width));
            rightSplit.setDividerLocation(
                    config.getInt(RIGHT_DIVIDER, Math.max(0, rightSplit.getWidth() - DEFAULT_LAYERS_W)));
            rightDock.setDividerLocation(
                    config.getInt(DOCK_DIVIDER, rightDock.getTopComponent().getPreferredSize().height));
            wireLayoutSaving(config);
        });
    }

    // Persist divider positions a moment after they change (debounced, so dragging
    // doesn't hammer the config file).
    private void wireLayoutSaving(Configuration config) {
        Timer saveTimer = new Timer(400, e -> {
            config.putInt(LEFT_DIVIDER, leftSplit.getDividerLocation());
            config.putInt(RIGHT_DIVIDER, rightSplit.getDividerLocation());
            config.putInt(DOCK_DIVIDER, rightDock.getDividerLocation());
            config.save();
        });
        saveTimer.setRepeats(false);
        leftSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> saveTimer.restart());
        rightSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> saveTimer.restart());
        rightDock.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> saveTimer.restart());
    }

    // Reset the panels to their default sizes (Home -> Reset Layout).
    public static void resetLayout() {
        if (leftSplit == null || rightSplit == null) return;
        int defLeft = MenuBars.verticalBar.getPreferredSize().width;
        int defRight = Math.max(0, rightSplit.getWidth() - DEFAULT_LAYERS_W);
        leftSplit.setDividerLocation(defLeft);
        rightSplit.setDividerLocation(defRight);
        Configuration config = Configuration.getInstance();
        config.putInt(LEFT_DIVIDER, defLeft);
        config.putInt(RIGHT_DIVIDER, defRight);
        if (rightDock != null) {
            int defDock = rightDock.getTopComponent().getPreferredSize().height;
            rightDock.setDividerLocation(defDock);
            config.putInt(DOCK_DIVIDER, defDock);
        }
        config.save();
    }

    private void createCanvas() {
        canvas = new CanvasPanel(); // the split-pane layout adds it to the frame
    }

    // Bottom status bar: a live zoom % readout plus Fit / 100% shortcuts, so the
    // zoom level is never blind.
    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        zoomLabel = new JLabel(zoomText());

        JButton fit = new JButton("Fit");
        fit.setFocusable(false);
        fit.setToolTipText("Fit the whole canvas in the window");
        fit.addActionListener(e -> canvas.fitToWindow());

        JButton actual = new JButton("100%");
        actual.setFocusable(false);
        actual.setToolTipText("Zoom to actual size (1:1)");
        actual.addActionListener(e -> canvas.actualSize());

        bar.add(zoomLabel);
        bar.add(fit);
        bar.add(actual);

        canvas.setOnZoomChanged(() -> zoomLabel.setText(zoomText()));
        return bar;
    }

    private String zoomText() {
        return "Zoom: " + Math.round(canvas.getZoom() * 100) + "%";
    }

    public static CanvasPanel getCanvas() {
        return canvas;
    }

    public static void main(String[] args) {
        try {
            UIManager.put("Button.arc", 4);
            FlatLaf lookAndFeel;
            if (Configuration.getInstance().is("ui.dark.mode.disabled", true)) {
                lookAndFeel = new FlatMacDarkLaf();
            } else {
                lookAndFeel = new FlatMacLightLaf();
            }
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
            UIManager.setLookAndFeel(lookAndFeel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Only load when everything is initialised.
        SwingUtilities.invokeLater(PixelGraphicEditor::new);
    }
}