package com.danielomari.pixeleditor.ui;

import com.danielomari.pixeleditor.layers.Layer;
import com.danielomari.pixeleditor.layers.LayerStack;
import com.danielomari.pixeleditor.util.tools.SelectTool;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.DeleteLayerCommand;
import com.danielomari.pixeleditor.commands.DuplicateLayerCommand;
import com.danielomari.pixeleditor.commands.MergeDownCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Photoshop-style Layers dock (right side of the window).
 *
 * Shows the layer stack top-first, lets you add / delete / reorder layers,
 * toggle each layer's visibility, set the active layer's opacity, and rename a
 * layer. All edits go through the {@link LayerStack} and repaint the canvas.
 */
public class LayersPanel extends JPanel {
    private final CanvasPanel canvas;
    private final LayerStack stack;

    private final JPanel listPanel = new JPanel();
    private final JSlider opacity = new JSlider(0, 100, 100);
    private boolean adjusting = false; // guards the opacity slider during refresh
    private boolean renaming = false;  // guards against double-committing an inline rename
    private JButton mergeBtn;          // disabled when the active layer is the bottom one

    private static final Color ACTIVE_BG = new Color(70, 110, 160);

    public LayersPanel(CanvasPanel canvas) {
        this.canvas = canvas;
        this.stack = canvas.getLayers();

        setPreferredSize(new Dimension(210, 0));
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel title = new JLabel("Layers");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setBorder(new EmptyBorder(0, 2, 6, 0));
        add(title, BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        add(new JScrollPane(listPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

        add(buildControls(), BorderLayout.SOUTH);

        // Keep the panel in sync when the stack is reset elsewhere (e.g. New File).
        canvas.setOnLayersChanged(this::refresh);

        // Del deletes the active layer when the Layers panel (or a row) has focus;
        // Ctrl+Z brings it back. The canvas keeps its own Del = clear selection.
        setFocusable(true);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteLayer");
        getActionMap().put("deleteLayer", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteActiveLayer();
            }
        });

        refresh();
    }

    private JComponent buildControls() {
        JPanel controls = new JPanel(new BorderLayout(0, 4));

        JPanel opacityRow = new JPanel(new BorderLayout(4, 0));
        opacityRow.add(new JLabel("Opacity"), BorderLayout.WEST);
        opacity.addChangeListener(e -> {
            if (adjusting) return; // ignore programmatic updates from refresh()
            stack.active().setOpacity(opacity.getValue() / 100f);
            canvas.repaint();
        });
        opacityRow.add(opacity, BorderLayout.CENTER);
        controls.add(opacityRow, BorderLayout.NORTH);

        JPanel row1 = new JPanel(new GridLayout(1, 4, 4, 0));
        row1.add(button("Add", "Add a new layer", e -> { stack.addLayer(); refresh(); canvas.repaint(); }));
        row1.add(button("Del", "Delete the active layer (Del)", e -> deleteActiveLayer()));
        row1.add(button("Up", "Move the active layer up", e -> { stack.moveActiveUp(); refresh(); canvas.repaint(); }));
        row1.add(button("Dn", "Move the active layer down", e -> { stack.moveActiveDown(); refresh(); canvas.repaint(); }));

        JPanel row2 = new JPanel(new GridLayout(1, 2, 4, 0));
        row2.add(button("Duplicate", "Duplicate the active layer", e -> duplicateActiveLayer()));
        mergeBtn = button("Merge Down", "Merge the active layer into the one below it", e -> mergeDownActiveLayer());
        row2.add(mergeBtn);

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 4));
        buttons.add(row1);
        buttons.add(row2);
        controls.add(buttons, BorderLayout.SOUTH);
        return controls;
    }

    private JButton button(String text, String tip, ActionListener a) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.addActionListener(a);
        b.setMargin(new Insets(2, 2, 2, 2));   // tight margins so labels aren't clipped to "..."
        b.setFont(b.getFont().deriveFont(11f));
        b.setFocusable(false);                  // keep keyboard focus on the panel (for Del)
        return b;
    }

    // Delete the active layer as an undoable command (Ctrl+Z restores it).
    private void deleteActiveLayer() {
        if (stack.getSize() <= 1) return; // always keep at least one layer
        DeleteLayerCommand cmd = new DeleteLayerCommand(canvas, stack.getActiveIndex());
        cmd.execute();
        CommandManager.getInstance().executeCommand(cmd);
        refresh();
        canvas.repaint();
    }

    // Duplicate the active layer: an exact copy sits just above it and becomes
    // active. Undoable (Ctrl+Z removes the copy).
    private void duplicateActiveLayer() {
        Layer src = stack.active();
        int srcIndex = stack.getActiveIndex();
        Layer copy = new Layer(src.getName() + " copy", deepCopy(src.getImage()),
                src.getVisible(), src.getOpacity());
        int at = srcIndex + 1;
        stack.insertAt(at, copy); // inserts and makes the copy active
        CommandManager.getInstance().executeCommand(new DuplicateLayerCommand(canvas, copy, at));
        refresh();
        canvas.repaint();
    }

    // Merge the active layer down into the layer beneath it (honouring the
    // active layer's opacity/visibility), then remove it. Undoable.
    private void mergeDownActiveLayer() {
        int upperIndex = stack.getActiveIndex();
        if (upperIndex <= 0) return; // nothing below the bottom layer
        Layer upper = stack.active();
        Layer lower = stack.get(upperIndex - 1);

        BufferedImage lowerBefore = deepCopy(lower.getImage());
        if (upper.getVisible() && upper.getOpacity() > 0f) {
            Graphics2D g = lower.getImage().createGraphics();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.max(0f, Math.min(1f, upper.getOpacity()))));
            g.drawImage(upper.getImage(), 0, 0, null);
            g.dispose();
        }
        BufferedImage lowerAfter = deepCopy(lower.getImage());

        stack.removeAt(upperIndex);
        stack.setActive(upperIndex - 1);
        CommandManager.getInstance().executeCommand(
                new MergeDownCommand(canvas, upper, upperIndex, lower, lowerBefore, lowerAfter));
        refresh();
        canvas.repaint();
    }

    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    /** Rebuild the row list (top of stack first) and sync the opacity slider. */
    public void refresh() {
        listPanel.removeAll();
        int active = stack.getActiveIndex();
        for (int i = stack.getSize() - 1; i >= 0; i--) {
            listPanel.add(buildRow(i, i == active));
        }
        adjusting = true;
        opacity.setValue(Math.round(stack.active().getOpacity() * 100));
        adjusting = false;
        if (mergeBtn != null) mergeBtn.setEnabled(stack.getActiveIndex() > 0);
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JComponent buildRow(int index, boolean isActive) {
        Layer layer = stack.get(index);

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.DARK_GRAY),
                new EmptyBorder(4, 4, 4, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        if (isActive) row.setBackground(ACTIVE_BG);
        row.putClientProperty("stackIndex", index);

        JCheckBox eye = new JCheckBox("", layer.getVisible());
        eye.setOpaque(false);
        eye.setToolTipText("Show / hide this layer");
        eye.addActionListener(e -> { layer.setVisible(eye.isSelected()); canvas.repaint(); });
        row.add(eye, BorderLayout.WEST);

        // Display a truncated name so a long one can't push the row past the panel;
        // the full name shows on hover and is what you edit when renaming.
        String fullName = layer.getName();
        String shown = fullName.length() > 16 ? fullName.substring(0, 16) + "..." : fullName;
        JLabel name = new JLabel(shown);
        name.setToolTipText(fullName);
        if (isActive) name.setForeground(Color.WHITE);
        row.add(name, BorderLayout.CENTER);

        // Clicking the name selects the layer, or renames it inline if already active.
        name.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                LayersPanel.this.requestFocusInWindow();
                if (SwingUtilities.isRightMouseButton(e)) {
                    // Convert the click point before refresh() rebuilds the rows,
                    // then anchor the popup on the panel (which stays on screen).
                    Point p = SwingUtilities.convertPoint(name, e.getX(), e.getY(), LayersPanel.this);
                    stack.setActive(index);
                    refresh();
                    rowMenu(layer).show(LayersPanel.this, p.x, p.y);
                } else if (stack.getActiveIndex() == index) {
                    startInlineRename(row, name, layer);
                } else {
                    selectLayer(index);
                }
            }
        });

        // Clicking elsewhere on the row just selects it (or shows the menu).
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                LayersPanel.this.requestFocusInWindow();
                if (SwingUtilities.isRightMouseButton(e)) {
                    Point p = SwingUtilities.convertPoint(row, e.getX(), e.getY(), LayersPanel.this);
                    stack.setActive(index);
                    refresh();
                    rowMenu(layer).show(LayersPanel.this, p.x, p.y);
                } else {
                    selectLayer(index);
                }
            }
        });
        // Drag handle on the right: drag to reorder layers; a plain click selects.
        JLabel grip = new JLabel("\u2261", SwingConstants.CENTER);
        grip.setForeground(isActive ? Color.WHITE : Color.GRAY);
        grip.setToolTipText("Drag to reorder");
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        grip.setPreferredSize(new Dimension(34, 1));
        row.add(grip, BorderLayout.EAST);

        MouseAdapter dragHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                LayersPanel.this.requestFocusInWindow(); // no refresh here, so the drag grab stays intact
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                Point inList = SwingUtilities.convertPoint(grip, e.getPoint(), listPanel);
                int target = stackIndexAt(inList.y, index);
                if (target != index) {
                    stack.moveLayer(index, target);
                    refresh();
                    canvas.repaint();
                } else {
                    selectLayer(index); // a click without dragging just selects
                }
            }
        };
        grip.addMouseListener(dragHandler);

        return row;
    }

    // Stack index of the row under a y-coordinate in the list panel (for drag-drop).
    private int stackIndexAt(int yInList, int fallback) {
        Component[] comps = listPanel.getComponents();
        if (comps.length == 0) return fallback;
        for (Component c : comps) {
            if (yInList >= c.getY() && yInList < c.getY() + c.getHeight() && c instanceof JComponent) {
                Object si = ((JComponent) c).getClientProperty("stackIndex");
                if (si instanceof Integer) return (Integer) si;
            }
        }
        Component first = comps[0]; // above all rows -> topmost
        if (yInList < first.getY() && first instanceof JComponent) {
            Object si = ((JComponent) first).getClientProperty("stackIndex");
            if (si instanceof Integer) return (Integer) si;
        }
        Component last = comps[comps.length - 1]; // below all rows -> bottom
        if (last instanceof JComponent) {
            Object si = ((JComponent) last).getClientProperty("stackIndex");
            if (si instanceof Integer) return (Integer) si;
        }
        return fallback;
    }

    private void selectLayer(int index) {
        stack.setActive(index);
        // Switching the active layer drops any in-progress selection so it can't
        // bleed across layers.
        SelectTool.getInstance().clearSelection();
        refresh();
        canvas.repaint();
    }

    // Swap the name label for a text field; commit on Enter / focus loss, cancel on Esc.
    private void startInlineRename(JPanel row, JLabel nameLabel, Layer layer) {
        renaming = true;
        JTextField field = new JTextField(layer.getName());
        field.selectAll();
        row.remove(nameLabel);
        row.add(field, BorderLayout.CENTER);
        row.revalidate();
        row.repaint();
        field.requestFocusInWindow();

        field.addActionListener(e -> commitRename(layer, field)); // Enter
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitRename(layer, field); }
        });
        field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelRename");
        field.getActionMap().put("cancelRename", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { renaming = false; refresh(); }
        });
    }

    private void commitRename(Layer layer, JTextField field) {
        if (!renaming) return; // Enter + focus-loss can both fire; only act once
        renaming = false;
        String n = field.getText().trim();
        if (!n.isEmpty()) layer.setName(n);
        refresh();
    }

    private JPopupMenu rowMenu(Layer layer) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem duplicate = new JMenuItem("Duplicate Layer");
        duplicate.addActionListener(e -> duplicateActiveLayer());
        JMenuItem mergeDown = new JMenuItem("Merge Down");
        mergeDown.setEnabled(stack.getActiveIndex() > 0); // nothing below the bottom layer
        mergeDown.addActionListener(e -> mergeDownActiveLayer());
        JMenuItem rename = new JMenuItem("Rename...");
        rename.addActionListener(e -> rename(layer));
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> deleteActiveLayer());
        menu.add(duplicate);
        menu.add(mergeDown);
        menu.addSeparator();
        menu.add(rename);
        menu.add(delete);
        return menu;
    }

    private void rename(Layer layer) {
        String n = JOptionPane.showInputDialog(this, "Layer name:", layer.getName());
        if (n != null && !n.trim().isEmpty()) {
            layer.setName(n.trim());
            refresh();
        }
    }
}
