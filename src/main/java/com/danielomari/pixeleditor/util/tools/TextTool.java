package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.Drawcommand;
import com.danielomari.pixeleditor.ui.CanvasPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
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
 * Enter inserts a new line; clicking away or pressing Esc commits the text - it
 * is rasterised onto the canvas image and the overlay is removed.
 *
 * Committed text stays re-editable: each piece is remembered (content, box,
 * style) along with a snapshot of the pixels it was drawn over. Clicking an
 * existing text with the Text tool restores that backdrop (removing the baked
 * text) and re-opens it for editing.
 */
public class TextTool implements Tool {
    private static TextTool instance;

    private static final int DEFAULT_BOX_W = 220; // click-mode box width (image px)
    private static final int DRAG_THRESHOLD = 4;  // below this, a drag counts as a click
    private static final int MAX_ENTRIES = 100;   // cap on remembered text pieces

    private CanvasPanel canvas;
    private JTextArea editor;          // the live editing overlay (null when not editing)
    private Rectangle boxImage;        // editing box in image coordinates
    private boolean autoGrow;          // click mode grows in height; box mode is fixed
    private boolean isTyping = false;
    private long lastCommitAt = 0L;    // when a box was last committed (to swallow the dismiss-click)

    private Point pressImg, curImg;    // drag anchor / current point (image coords)
    private boolean dragging = false;

    private int fontSize = 16;
    private String selectedFont = "Arial";

    // Style of the edit currently in progress (so re-edits keep their own style).
    private String editFont = "Arial";
    private int editSize = 16;
    private Color editColor = Color.BLACK;
    private Drawcommand pendingCommand; // undo snapshot spanning a re-edit

    // Remembered committed text, newest last. Shared across tool instances.
    private static final List<TextEntry> committed = new ArrayList<>();

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

    /** Forget all re-editable text (e.g. after New File / clear, when it no longer matches). */
    public static void forgetCommittedText() {
        committed.clear();
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
        // Swallow the same click that just dismissed a box (e.g. via focus-lost),
        // so clicking/dragging away to finish typing doesn't start a new box.
        if (System.currentTimeMillis() - lastCommitAt < 250) return;
        if (e.getButton() == MouseEvent.BUTTON3) { // right-click: set font size
            promptForFontSize();
            return;
        }
        if (e.getButton() != MouseEvent.BUTTON1) return;

        // Clicking on an existing piece of text re-opens it for editing.
        Point img = new Point(e.getX(), e.getY());
        TextEntry existing = findEntryAt(img);
        if (existing != null) {
            beginReEdit(existing);
            return;
        }

        pressImg = img;
        curImg = new Point(img);
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
        startEditing(box, grow); // new (empty) text in the current style
    }

    // ---- re-editing committed text ----------------------------------------

    private TextEntry findEntryAt(Point p) {
        for (int i = committed.size() - 1; i >= 0; i--) {
            if (committed.get(i).box.contains(p)) return committed.get(i);
        }
        return null;
    }

    private void beginReEdit(TextEntry entry) {
        canvas = CanvasPanel.getInstance();
        // Snapshot for undo while the old text is still on the canvas.
        pendingCommand = new Drawcommand(canvas);
        // Remove the baked text by restoring what was underneath it.
        Rectangle clip = entry.box.intersection(canvasBounds());
        if (clip.width > 0 && clip.height > 0 && entry.underlay != null) {
            Graphics2D g = canvas.getCanvasImage().createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(entry.underlay, clip.x, clip.y, null);
            g.dispose();
        }
        committed.remove(entry);
        canvas.repaint();
        startEditing(entry.box, entry.autoGrow, entry.text, entry.font, entry.size, entry.color);
    }

    // ---- editing overlay --------------------------------------------------

    // New (empty) text in the tool's current style.
    private void startEditing(Rectangle imageBox, boolean autoGrow) {
        startEditing(imageBox, autoGrow, "", selectedFont, fontSize, ColorTool.getColor());
    }

    private void startEditing(Rectangle imageBox, boolean autoGrow, String text, String font, int size, Color color) {
        canvas = CanvasPanel.getInstance();
        canvas.setLayout(null);
        this.boxImage = imageBox;
        this.autoGrow = autoGrow;
        this.editFont = font;
        this.editSize = size;
        this.editColor = color;
        isTyping = true;

        float zoom = canvas.getZoom();
        Point screen = canvas.imageToScreen(imageBox.x, imageBox.y);
        int sw = Math.max(20, Math.round(imageBox.width * zoom));
        int sh = Math.max(Math.round((size + 8) * zoom), Math.round(imageBox.height * zoom));

        editor = new JTextArea(text);
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.setOpaque(false); // show the canvas through the box (WYSIWYG)
        editor.setForeground(color);
        editor.setCaretColor(color);
        editor.setFont(makeFont(font, Math.max(1, Math.round(size * zoom))));
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
        editor.setCaretPosition(editor.getDocument().getLength());
        if (autoGrow) fitHeight();
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
        lastCommitAt = System.currentTimeMillis();

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

        boolean hasText = text != null && !text.trim().isEmpty();
        if (hasText) {
            rasterize(text, new Rectangle(ix, iy, iw, ih));
        } else if (pendingCommand != null) {
            // Re-edit whose text was cleared: the old text was already removed when
            // we restored the underlay, so just finalise the command (the deletion).
            pendingCommand.storeAfterState();
            CommandManager.getInstance().executeCommand(pendingCommand);
            pendingCommand = null;
        }
    }

    // Draw the text onto the canvas image (wrapped, clipped to the box) and
    // remember it so it can be edited again later.
    private void rasterize(String text, Rectangle box) {
        Rectangle clip = box.intersection(canvasBounds());
        Drawcommand command = (pendingCommand != null) ? pendingCommand : new Drawcommand(canvas);
        pendingCommand = null;

        if (clip.width <= 0 || clip.height <= 0) {
            // Box is off-canvas; nothing to draw, but finalise so undo stays sane.
            command.storeAfterState();
            CommandManager.getInstance().executeCommand(command);
            return;
        }

        // Snapshot what's under the box BEFORE drawing, so this text stays editable.
        BufferedImage underlay = new BufferedImage(clip.width, clip.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ug = underlay.createGraphics();
        ug.setComposite(AlphaComposite.Src);
        ug.drawImage(canvas.getCanvasImage().getSubimage(clip.x, clip.y, clip.width, clip.height), 0, 0, null);
        ug.dispose();

        Graphics2D g = canvas.getCanvasImage().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(makeFont(editFont, editSize));
        g.setColor(editColor);
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

        // Remember this text so clicking it again re-opens it for editing.
        committed.add(new TextEntry(text, new Rectangle(clip), editFont, editSize, editColor, autoGrow, underlay));
        if (committed.size() > MAX_ENTRIES) committed.remove(0);

        command.storeAfterState();
        CommandManager.getInstance().executeCommand(command);
    }

    // ---- helpers ----------------------------------------------------------

    private Rectangle canvasBounds() {
        return new Rectangle(0, 0, canvas.getCanvasImage().getWidth(), canvas.getCanvasImage().getHeight());
    }

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
    private Font makeFont(String family, int size) {
        Font f = new Font(family, Font.PLAIN, size);
        if (!f.getFamily().equalsIgnoreCase(family)) {
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

    // One remembered piece of committed text (re-editable).
    private static final class TextEntry {
        final String text;
        final Rectangle box;        // on-canvas region, image coords
        final String font;
        final int size;
        final Color color;
        final boolean autoGrow;
        final BufferedImage underlay; // pixels that were under the text

        TextEntry(String text, Rectangle box, String font, int size, Color color, boolean autoGrow, BufferedImage underlay) {
            this.text = text;
            this.box = box;
            this.font = font;
            this.size = size;
            this.color = color;
            this.autoGrow = autoGrow;
            this.underlay = underlay;
        }
    }
}
