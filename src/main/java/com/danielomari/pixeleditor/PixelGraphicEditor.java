package com.danielomari.pixeleditor;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.danielomari.pixeleditor.ui.MenuBar.MenuBars;
import com.danielomari.pixeleditor.util.Configuration;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.formdev.flatlaf.FlatLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;


public class PixelGraphicEditor {
    private final JFrame mainFrame;
    private static CanvasPanel canvas;

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

        // Add components to frame
        mainFrame.add(MenuBars.verticalBar, BorderLayout.WEST);
        mainFrame.add(MenuBars.horizontalBar, BorderLayout.NORTH);

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
        });
    }

    private void createCanvas() {
        canvas = new CanvasPanel(); // Use our custom CanvasPanel
        mainFrame.getContentPane().add(canvas, BorderLayout.CENTER); // Add to main frame
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