package com.danielomari.pixeleditor.util.KeyBinds;

import com.danielomari.pixeleditor.PixelGraphicEditor;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.ui.CanvasPanel;
import com.danielomari.pixeleditor.util.Help;
import com.danielomari.pixeleditor.util.SafeExit;
import com.danielomari.pixeleditor.util.Save;
import com.danielomari.pixeleditor.util.tools.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

// Registers all keyboard shortcuts (tool letters and Ctrl-based file/edit actions) on the canvas.
public class KeyBindings { // Moved the creation and configuration of keybinds away from CanvasPanel to reduce clutter.
    private final CanvasPanel canvasPanel;
    private SelectTool selectTool = new SelectTool();
    public KeyBindings(CanvasPanel canvasPanel) {
        this.canvasPanel = canvasPanel;
        createKeyBinds();
    }

    public void createKeyBinds() {
        if (canvasPanel != null) {
            InputMap inputMap = canvasPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap actionMap = canvasPanel.getActionMap();
            brushKeyBinds(inputMap, actionMap);
            pencilKeyBinds(inputMap, actionMap);
            eraserKeyBinds(inputMap, actionMap);
            shapeKeyBinds(inputMap, actionMap);
            selectKeyBinds(inputMap, actionMap);
            zoomKeyBinds(inputMap, actionMap);
            textKeyBinds(inputMap, actionMap);
            eyedropperKeyBinds(inputMap, actionMap);
        }
    }

    public void createShortcutKeyBinds() {
        if (canvasPanel != null) {
            InputMap inputMap = canvasPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap actionMap = canvasPanel.getActionMap();
            selectAllKeyBind(inputMap, actionMap);
            gridKeyBind(inputMap, actionMap);
            escKeyBind(inputMap, actionMap);
            createHelp(inputMap, actionMap);
            undoKeyBind(inputMap, actionMap);
            redoKeyBind(inputMap, actionMap);
            saveKeyBind(inputMap, actionMap);
            saveAsKeyBind(inputMap, actionMap);
            newFileKeyBind(inputMap, actionMap);
            openFileKeyBind(inputMap, actionMap);
            exitKeyBind(inputMap, actionMap);
        }
    }

    // Single-letter tool shortcuts are window-wide, so they would otherwise fire
    // while the user is typing into a text box (e.g. an 's' would switch to the
    // Shape tool mid-sentence). Skip them whenever a text component has focus.
    private boolean typingInTextField() {
        java.awt.Component f =
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return f instanceof javax.swing.text.JTextComponent;
    }

    private void brushKeyBinds(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("B"), "switchToBrush");
        actionMap.put("switchToBrush", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore tool shortcuts while typing
                canvasPanel.setTool(new BrushTool());
            }
        });
    }
    private void pencilKeyBinds(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("P"), "switchToPencil");
        actionMap.put("switchToPencil", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore tool shortcuts while typing
                canvasPanel.setTool(new PencilTool());
            }
        });
    }
    private void eraserKeyBinds(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("E"), "switchToEraser");
        actionMap.put("switchToEraser", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore tool shortcuts while typing
                canvasPanel.setTool(new EraserTool());
            }
        });
    }
    private void shapeKeyBinds(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("S"), "switchToShape");
        actionMap.put("switchToShape", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore tool shortcuts while typing
                canvasPanel.setTool(new ShapeTool());
            }
        });
    }
    private void selectKeyBinds(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("C"), "switchToSelect");
        actionMap.put("switchToSelect", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore tool shortcuts while typing
                canvasPanel.setTool(new SelectTool());
            }
        });
    }
    private void zoomKeyBinds(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("Z"), "switchToZoom");
        actionMap.put("switchToZoom", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore tool shortcuts while typing
                canvasPanel.setTool(new MagnifierTool(canvasPanel));
            }
        });
    }
    private void textKeyBinds(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("T"), "switchToText");
        actionMap.put("switchToText", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore tool shortcuts while typing
                canvasPanel.setTool(new TextTool());
            }
        });
    }
    private void eyedropperKeyBinds(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("I"), "switchToEyedropper");
        actionMap.put("switchToEyedropper", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore tool shortcuts while typing
                canvasPanel.setTool(new EyedropperTool());
            }
        });
    }

    // Additional Keybinds.
    private void selectAllKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control A"), "selectAll");
        actionMap.put("selectAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvasPanel.setTool(new SelectTool());
                selectTool.selectAll(canvasPanel);
                canvasPanel.repaint();
            }
        });
    }

    private void gridKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("G"), "toggleGrid");
        actionMap.put("toggleGrid", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (typingInTextField()) return; // ignore shortcuts while typing
                canvasPanel.toggleGrid();
            }
        });
    }

    private void escKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "deselect");
        actionMap.put("deselect", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectTool.clearSelection();
                canvasPanel.repaint();
            }
        });
    }

    private void createHelp(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control H"), "help");
        actionMap.put("help", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(PixelGraphicEditor.getCanvas());
                Help help = new Help(parentFrame);
                help.showHelp();
            }
        });
    }

    private void undoKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control Z"), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CommandManager.getInstance().undo();
                canvasPanel.repaint();
            }
        });
    }

    private void redoKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control Y"), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CommandManager.getInstance().redo();
                canvasPanel.repaint();
            }
        });
    }

    private void saveKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control S"), "save");
        actionMap.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Save.saveImage();
            }
        });
    }

    private void saveAsKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control shift S"), "saveAs");
        actionMap.put("saveAs", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Save.saveImageAs();
            }
        });
    }

    private void newFileKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control N"), "newFile");
        actionMap.put("newFile", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CommandManager.getInstance().clear();
                canvasPanel.clearCanvas();
                Save.getResetSave();
            }
        });
    }

    private void openFileKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control O"), "openFile");
        actionMap.put("openFile", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                InsertTool insertTool = new InsertTool();
                insertTool.insert();
            }
        });
    }

    private void exitKeyBind(InputMap inputMap, ActionMap actionMap) {
        inputMap.put(KeyStroke.getKeyStroke("control Q"), "exit");
        actionMap.put("exit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SafeExit safeExit = new SafeExit();
                safeExit.actionPerformed(e);
            }
        });
    }
}
