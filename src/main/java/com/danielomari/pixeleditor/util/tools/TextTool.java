package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.Drawcommand;
import com.danielomari.pixeleditor.ui.CanvasPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Text tool with an MS-Paint / Photoshop style workflow:
 *
 *   - Click on the canvas to drop a text box that grows as you type, OR
 *   - Drag a rectangle to get a fixed box that the text wraps inside and
 *     stays confined to.
 *
 * While editing, a real {@link JTextArea} overlay sits exactly where you clicked
 * (mapped through the canvas zoom + centring), so what you see is what you get.
 * Enter inserts a new line; clicking away or pressing Esc commits the text -
 * it is rasterised onto the canvas image and the overlay is removed.
 */
public class TextTool implements Tool {
    private static TextTool instance;

    private static final int DEFAULT_BOX_W = 220; // click-mode box width (image px)
    private static final int DRAG_THRESHOLD = 4;  // below this, a drag counts as a click

    private CanvasPanel canvas;
    private JTextArea editor;          // the live editing overlay (null when not editing)
    private Rectangle boxImage;        // editing box in image coordinates
    private boolean autoGrow;          // click mode grows in height; box mode is fixed
    private boolean isTyping = false;

    private Point pressImg, curImg;    // drag anchor / current point (image coords)
    private boolean dragging = false;

    private int fontSize = 16;
    private String selectedFont = "Arial";

    // Dashed rubber-band shown while dragging out a text box.
    private final Consumer<Graphics2D> previewListener = g -> {
        if (!dragging || pressImg == null || curImg == null || canvas == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        double zoom = canvas.getZoom();
        g2.translate(canvas.getRenderOffsetX() / zoom, canvas.getRenderOffsetY() / zoom);
        g2.setColor(Color.GRAY);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, new float[]{3f}, 0f));
        Rectangle r = rectBetween(pressImg, curImg);
        g2.drawRect(r.x, r.y, r.width, r.height);
        g2.dispose();
    };

    public TextTool() {
        this.selectedFont = "Arial";
    }

    public static TextTool getInstance() {
        if (instance == null) {
            instance = new TextTool();
        }
        return instance;
    }

    // ---- configuration ----------------------------------------------------

    public void setSelectedFont(String font) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        List<String> available = Arrays.asList(ge.getAvailableFontFamilyNames());
        if (available.contains(font)) {
            this.selectedFont = font;
        } else {
            System.err.println("Font " + font + " not found. Falling back to Arial.");
            this.selectedFont = "Arial";
        }
    }

    public String getSelectedFont() {
        return selectedFont;
    }

    public void setFontSize(int size) {
        if (size > 0) this.fontSize = size;
    }

    public void setCanvas(CanvasPanel canvas) {
        this.canvas = canvas;
        if (canvas != null) {
            // A null layout lets the overlay sit at absolute pixel positions.
            canvas.setLayout(null);
            canvas.addPaintListener(previewListener); // addPaintListener de-duplicates
        }
    }

    /** Ask for a font size via a dialog. Used by the Text menu before placing text. */
    public void promptForFontSize() {
        String input = JOptionPane.showInputDialog(canvas, "Enter font size:", "Font Size", JOptionPane.PLAIN_MESSAGE);
        if (input == null) return; // cancelled
        try {
            int size = Integer.parseInt(input.trim());
            if (size > 0) {
                fontSize = size;
            } else {
                JOptionPane.showMessageDialog(canvas, "Please enter a positive number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(canvas, "Invalid number; keeping size " + fontSize + ".", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- mouse handling (coordinates arrive already in image space) --------

    @Override
    public void onPress(MouseEvent e) {
        if (canvas == null) canvas = CanvasPanel.getInstance();
        // Clicking anywhere while a box is open commits it first (MS Paint style).
        if (isTyping) {
            commit();
            return;
        }
        if (e.getButton() == MouseEvent.BUTTON3) { // right-click: set font size
            promptForFontSize();
            return;
        }
        if (e.getButton() != MouseEvent.BUTTON1) return;
        pressImg = new Point(e.getX(), e.getY());
        curImg = new Point(pressImg);
        dragging = true;
    }

    @Override
    public void onDrag(MouseEvent e) {
        if (!dragging) return;
        curImg = new Point(e.getX(), e.getY());
        if (canvas != null) canvas.repaint();
    }

    @Override
    public void onRelease(MouseEvent e) {
        if (!dragging) return;
        dragging = false;
        if (pressImg == null) return;

        Rectangle r = rectBetween(pressImg, curImg);
        Rectangle box;
        boolean grow;
        if (r.width < DRAG_THRESHOLD && r.height < DRAG_THRESHOLD) {
            // Plain click: a point box with a default width that grows in height.
            box = new Rectangle(pressImg.x, pressImg.y, DEFAULT_BOX_W, fontSize + 8);
            grow = true;
        } else {
            // Dragged box: text wraps and is confined to these bounds.
            box = r;
            grow = false;
        }
        if (canvas != null) canvas.repaint();
        startEditing(box, grow);
    }

    // ---- editing overlay --------------------------------------------------

    private void startEditing(Rectangle imageBox, boolean autoGrow) {
        canvas = CanvasPanel.getInstance();
        canvas.setLayout(null);
        this.boxImage = imageBox;
        this.autoGrow = autoGrow;
        isTyping = true;

        float zoom = canvas.getZoom();
        Point screen = canvas.imageToScreen(imageBox.x, imageBox.y);
        int sw = Math.max(20, Math.round(imageBox.width * zoom));
        int sh = Math.max(Math.round((fontSize + 8) * zoom), Math.round(imageBox.height * zoom));

        editor = new JTextArea();
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.setOpaque(false); // show the canvas through the box (WYSIWYG)
        editor.setForeground(ColorTool.getColor());
        editor.setCaretColor(ColorTool.getColor());
        editor.setFont(makeFont(Math.max(1, Math.round(fontSize * zoom))));
        editor.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 1f, 3f, 2f, false));
        editor.setBounds(screen.x, screen.y, sw, sh);
        canvas.add(editor);

        // Esc commits, and is consumed here so it doesn't also hit canvas key binds.
        editor.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "commitText");
        editor.getActionMap().put("commitText", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commit();
            }
        });

        // Clicking away from the box commits it.
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commit();
            }
        });

        // Click mode grows the box downward to fit the text as it is typed.
        if (autoGrow) {
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { fitHeight(); }
                @Override public void removeUpdate(DocumentEvent e) { fitHeight(); }
                @Override public void changedUpdate(DocumentEvent e) { fitHeight(); }
            });
        }

        canvas.revalidate();
        canvas.repaint();
        editor.requestFocusInWindow();
    }

    // Grow the (click-mode) editor's height so all typed lines stay visible.
    private void fitHeight() {
        if (editor == null) return;
        Dimension pref = editor.getPreferredSize();
        Rectangle b = editor.getBounds();
        if (pref.height != b.height) {
            editor.setBounds(b.x, b.y, b.width, pref.height);
            canvas.revalidate();
            canvas.repaint();
        }
    }

    /** Rasterise the overlay's text onto the canvas image and remove the overlay. */
    private void commit() {
        if (editor == null) return;
        JTextArea ed = editor;
        editor = null;
        isTyping = false;

        String text = ed.getText();
        // Map the overlay's on-screen bounds back to image coordinates so the
        // text lands exactly where the box was (undoes zoom + centring offset).
        Rectangle b = ed.getBounds();
        float zoom = canvas.getZoom();
        int ix = Math.round((b.x - canvas.getRenderOffsetX()) / zoom);
        int iy = Math.round((b.y - canvas.getRenderOffsetY()) / zoom);
        int iw = Math.round(b.width / zoom);
        int ih = Math.round(b.height / zoom);

        canvas.remove(ed);
        canvas.repaint();

        if (text != null && !text.trim().isEmpty()) {
            rasterize(text, new Rectangle(ix, iy, iw, ih));
        }
    }

    // Draw the text onto the canvas image, wrapped and clipped to the box.
    private void rasterize(String text, Rectangle box) {
        // Drawcommand snapshots the before-state in its constructor.
        Drawcommand command = new Drawcommand(canvas);

        Graphics2D g = canvas.getCanvasImage().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(makeFont(fontSize));
        g.setColor(ColorTool.getColor());

        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight();
        int pad = 3;

        // Keep the text inside the box so it stays confined to the drawn limits.
        Shape oldClip = g.getClip();
        g.setClip(box.x, box.y, box.width, box.height);
        int x = box.x + pad;
        int y = box.y + fm.getAscent() + pad;
        int maxWidth = Math.max(1, box.width - pad * 2);
        for (String paragraph : text.split("\n", -1)) {
            for (String line : wrapLine(paragraph, fm, maxWidth)) {
                g.drawString(line, x, y);
                y += lineH;
            }
        }
        g.setClip(oldClip);
        g.dispose();
        canvas.repaint();

        command.storeAfterState();
        CommandManager.getInstance().executeCommand(command);
    }

    // ---- helpers ----------------------------------------------------------

    // Word-wrap a single paragraph to the given pixel width.
    private List<String> wrapLine(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() == 0 || fm.stringWidth(candidate) <= maxWidth) {
                line = new StringBuilder(candidate);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    // Build the chosen font, falling back to a logical sans-serif if unavailable.
    private Font makeFont(int size) {
        Font f = new Font(selectedFont, Font.PLAIN, size);
        if (!f.getFamily().equalsIgnoreCase(selectedFont)) {
            f = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        }
        return f;
    }

    // Normalised rectangle between two points.
    private Rectangle rectBetween(Point a, Point b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        return new Rectangle(x, y, Math.abs(b.x - a.x), Math.abs(b.y - a.y));
    }
}
