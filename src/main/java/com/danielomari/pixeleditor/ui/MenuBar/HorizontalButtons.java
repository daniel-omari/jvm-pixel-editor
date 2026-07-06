package com.danielomari.pixeleditor.ui.MenuBar;

import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.util.AutomaticSave;
import com.danielomari.pixeleditor.util.Save;
import com.danielomari.pixeleditor.util.Project;
import com.danielomari.pixeleditor.util.tools.InsertTool;
import com.danielomari.pixeleditor.PixelGraphicEditor;

import com.danielomari.pixeleditor.util.Help;
import com.danielomari.pixeleditor.util.Configuration;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.JOptionPane;
import javax.swing.*;
import java.awt.*;


// Top menu-bar actions: the Home and File popups (canvas size, auto-save, layout, open/save image & project).
public class HorizontalButtons {
    private AutoSaveType selectedAutoSaveType = AutoSaveType.OFF;
    private AutomaticSave autoSave = null;
    private final Save save = new Save();


    public HorizontalButtons() {
    }

    // Home Button Logic
    private void HomeButtonLogic(JButton button) {
        JPopupMenu popupMenu = new JPopupMenu();
        String[] options = {"Canvas Selection", "Auto Save: ", "Theme: ", "Reset Layout", "Exit", "Help"};
        for (String option : options) {
            JMenuItem item = new JMenuItem(option);
            popupMenu.add(item);
            switch (option) {
                case "Canvas Selection":
                    item.addActionListener(e -> HomeButtonCanvasSelection());
                    break;
                case "Auto Save: ":
                    item.addActionListener(e -> HomeButtonAutoSave());
                    item.setText("Auto Save: " + selectedAutoSaveType);
                    break;
                case "Theme: ":
                    item.setText("Theme: " + (isDarkTheme() ? "Dark" : "Light"));
                    item.addActionListener(e -> toggleTheme());
                    break;
                case "Reset Layout":
                    item.addActionListener(e -> PixelGraphicEditor.resetLayout());
                    break;
                case "Exit":
                    item.addActionListener(e -> System.exit(0));
                    break;
                case "Help":
                    item.addActionListener(e -> {
                        //parentMenu.setVisible(false);  // Close popup menu
                        Help(popupMenu);
                        });
                    break;
            }
        }
        popupMenu.setVisible(true);
        popupMenu.show(button, 0, button.getHeight());
    }

    private void HomeButtonCanvasSelection() {
        CanvasPanel canvas = CanvasPanel.getInstance();
        int curW = canvas.getCanvasImage().getWidth();
        int curH = canvas.getCanvasImage().getHeight();

        JComboBox<String> presets = new JComboBox<>(new String[]{
                "Custom", "800 x 600", "1024 x 768", "1280 x 720", "1920 x 1080", "500 x 500"
        });
        JSpinner widthSpin = new JSpinner(new SpinnerNumberModel(curW, 1, 5000, 1));
        JSpinner heightSpin = new JSpinner(new SpinnerNumberModel(curH, 1, 5000, 1));
        presets.addActionListener(e -> {
            String s = (String) presets.getSelectedItem();
            if (s != null && s.contains("x")) {
                String[] parts = s.split("x");
                try {
                    widthSpin.setValue(Integer.parseInt(parts[0].trim()));
                    heightSpin.setValue(Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        });

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.add(new JLabel("Preset:"));
        panel.add(presets);
        panel.add(new JLabel("Width (px):"));
        panel.add(widthSpin);
        panel.add(new JLabel("Height (px):"));
        panel.add(heightSpin);

        int result = JOptionPane.showConfirmDialog(null, panel, "Canvas Size",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            canvas.resizeCanvas((Integer) widthSpin.getValue(), (Integer) heightSpin.getValue());
        }
    }

    private void HomeButtonAutoSave() {
        // Auto save functionality
        if (selectedAutoSaveType == AutoSaveType.OFF) {
            selectedAutoSaveType = AutoSaveType.ON;
            autoSave = new AutomaticSave();
        } else {
            selectedAutoSaveType = AutoSaveType.OFF;
            if (autoSave != null) {
                autoSave.stopAutoSave();
                return;
            }
        }
    }

    // The persisted theme choice; light unless the user switched to dark.
    private static boolean isDarkTheme() {
        return "dark".equals(Configuration.getInstance().getString("ui.theme", "light"));
    }

    // Switch between the FlatLaf dark and light themes at runtime and persist
    // the choice, so the editor reopens the way it was left.
    private static void toggleTheme() {
        boolean toLight = isDarkTheme();
        Configuration config = Configuration.getInstance();
        config.putString("ui.theme", toLight ? "light" : "dark");
        config.save();
        try {
            UIManager.setLookAndFeel(toLight ? new FlatMacLightLaf() : new FlatMacDarkLaf());
            FlatLaf.updateUI();
        } catch (UnsupportedLookAndFeelException ex) {
            // Switching failed; keep the current theme.
        }
    }

    public void getHomeButton(JButton button) {
        HomeButtonLogic(button);
    }

    // File Button Logic
    private void FileButtonLogic(JButton button) {
        JPopupMenu popupMenu = new JPopupMenu();
        // "Open Image"/"Save Image"/"Save Image As" act on flattened images
        // (PNG/JPEG/BMP) for interchange with other apps; "Open Project"/"Save
        // Project" use the native .pxe format that preserves the whole layer stack.
        String[] options = {"New", "Open Image", "Open Project", "Save Image", "Save Image As", "Save Project"};
        for (String option : options) {
            JMenuItem item = new JMenuItem(option);
            popupMenu.add(item);

            switch (option) {
                case "New":
                    item.addActionListener(e -> FileButtonNew());
                    break;
                case "Open Image":
                    item.addActionListener(e -> FileButtonOpen());
                    break;
                case "Open Project":
                    item.addActionListener(e -> Project.openProject());
                    break;
                case "Save Image":
                    item.addActionListener(e -> FileButtonSave());
                    break;
                case "Save Image As":
                    item.addActionListener(e -> FileButtonSaveAs());
                    break;
                case "Save Project":
                    item.addActionListener(e -> Project.saveProject());
                    break;
            }
        }
        popupMenu.setVisible(true);
        popupMenu.show(button, 0, button.getHeight());
    }

    private void FileButtonNew() {
        int confirm = JOptionPane.showConfirmDialog(null, "<html><b>Are you sure you want to create a new file? This will clear the current canvas, any unsaved work will be lost.</b><html>", "Confirm New File", JOptionPane.YES_NO_OPTION);
        if(confirm == JOptionPane.YES_OPTION) {
            CommandManager.getInstance().clear();
            CanvasPanel.getInstance().clearCanvas();
            CanvasPanel.getInstance().repaint();

            save.getResetSave();
        } if(confirm == JOptionPane.NO_OPTION) {
        }
    }

    private void FileButtonOpen() {
        InsertTool insertTool = new InsertTool();
        insertTool.insert();
    }

    private void FileButtonSave() {
        Save.saveImage();
    }

    private void FileButtonSaveAs() {
        Save.saveImageAs();
    }

    public void getFileButton(JButton button) {
        FileButtonLogic(button);
    }

    private void Help(JPopupMenu parentMenu) {
        parentMenu.setVisible(false); // Close the popup first

        // Get the parent frame correctly
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(PixelGraphicEditor.getCanvas());

        // Create and show help dialog
        Help helpTool = new Help(parentFrame);
        helpTool.showHelp();
    }


    private enum AutoSaveType {ON, OFF}
}
