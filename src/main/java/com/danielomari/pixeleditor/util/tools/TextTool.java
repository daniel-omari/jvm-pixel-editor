package com.danielomari.pixeleditor.util.tools;

import com.danielomari.pixeleditor.commands.AddTextLayerCommand;
import com.danielomari.pixeleditor.commands.CommandManager;
import com.danielomari.pixeleditor.commands.Drawcommand;
import com.danielomari.pixeleditor.layers.Layer;
import com.danielomari.pixeleditor.layers.LayerStack;
import com.danielomari.pixeleditor.ui.CanvasPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Text tool with an MS-Paint / Photoshop style workflow.
 *
 * Click to drop a text box that grows as you type, or drag a rectangle to get a
 * fixed box the text wraps inside. A live {@link JTextArea} overlay sits exactly
 * where you clicked; Enter is a new line, clicking away or Esc commits.
 *
 * Each committed text block lives on ITS OWN layer (auto-named from the text),
 * created just above the working layer. Because text never shares a layer with
 * paint, painting elsewhere can't destroy it, and re-editing only clears that
 * text's own box. Clicking existing text with the Text tool re-opens it.
 */
public class TextTool implements Tool {
    private static TextTool instance;

    private static final int DEFAULT_BOX_W = 220; // click-mode box width (image px)
    private static final int DRAG_THRESHOLD = 4;  // below this, a drag counts as a click
    private static final int MAX_ENTRIES = 200;   // cap on remembered text pieces

    private CanvasPanel canvas;
    private JTextArea editor;          // the live editing overlay (null when not editing)
    private boolean autoGrow;          // click mode grows in height; box mode is fixed
    private boolean isTyping = false;
    private long lastCommitAt = 0L;    // when a box was last committed (to swallow the dismiss-click)

    private Point pressImg, curImg;    // drag anchor / current point (image coords)
    private boolean dragging = false;

    private int fontSize = 16;
    private String selectedFont = "Arial";
    private Color textColor = Color.BLACK; // current text colour (set in the Text settings)
    private boolean bold = false;
    private boolean italic = false;
    private boolean underline = false;

    // Style of the edit currently in progress (so re-edits keep their own style).
    private String editFont = "Arial";
    private int editSize = 16;
    private Color editColor = Color.BLACK;
    private boolean editBold = false;
    private boolean editItalic = false;
    private boolean editUnderline = false;

    private TextEntry editingEntry;     // non-null while re-editing an existing text
    private int prevActiveIndex;        // working layer to restore after a re-edit
    private Drawcommand pendingCommand; // undo snapshot spanning a re-edit

    // Remembered committed text, newest last. Shared across tool instances.
    private static final List<TextEntry> committed = new ArrayList<>();

    // Dashed rubber-band shown while dragging out a text box.
    private final Consumer<Graphics2D> previewListener = g -> {
        if (!dragging || pressImg == null || curImg == null || canvas == null) return;
        // The shared Graphics is already translated to the document origin and
        // zoomed, so image-space coordinates draw directly.
        Graphics2D g2 = (Graphics2D) g.create();
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

    /** Forget all re-editable text (e.g. after New File / clear). */
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

    public String getSelectedFont() { return selectedFont; }
    public void setFontSize(int size) { if (size > 0) this.fontSize = size; }
    public int getFontSize() { return fontSize; }
    public Color getTextColor() { return textColor; }
    public void setTextColor(Color c) { if (c != null) this.textColor = c; }
    public boolean isBold() { return bold; }
    public void setBold(boolean b) { this.bold = b; }
    public boolean isItalic() { return italic; }
    public void setItalic(boolean b) { this.italic = b; }
    public boolean isUnderline() { return underline; }
    public void setUnderline(boolean b) { this.underline = b; }

    public void setCanvas(CanvasPanel canvas) {
        this.canvas = canvas;
        if (canvas != null) {
            canvas.setLayout(null); // overlay sits at absolute pixel positions
            canvas.addPaintListener(previewListener); // addPaintListener de-duplicates
        }
    }

    /** Quick font-size dialog (right-click shortcut; the Text settings panel is the main way). */
    public void promptForFontSize() {
        String input = JOptionPane.showInputDialog(canvas, "Enter font size:", "Font Size", JOptionPane.PLAIN_MESSAGE);
        if (input == null) return;
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
        if (isTyping) { // clicking while a box is open commits it first
            commit();
            return;
        }
        // Swallow the same click that just dismissed a box so it doesn't start a new one.
        if (System.currentTimeMillis() - lastCommitAt < 250) return;
        if (e.getButton() == MouseEvent.BUTTON3) {
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
            box = new Rectangle(pressImg.x, pressImg.y, DEFAULT_BOX_W, fontSize + 8);
            grow = true; // click: grow in height as you type
        } else {
            box = r;
            grow = false; // dragged box: wrap + confine
        }
        if (canvas != null) canvas.repaint();
        startEditing(box, grow);
    }

    // ---- re-editing committed text ----------------------------------------

    private TextEntry findEntryAt(Point p) {
        LayerStack stack = (canvas != null) ? canvas.getLayers() : null;
        for (int i = committed.size() - 1; i >= 0; i--) {
            TextEntry entry = committed.get(i);
            if (!entry.box.contains(p)) continue;
            if (stack != null && (stack.indexOf(entry.layer) < 0 || !entry.layer.getVisible())) {
                continue; // text layer was deleted or is hidden
            }
            return entry;
        }
        return null;
    }

    private void beginReEdit(TextEntry entry) {
        canvas = CanvasPanel.getInstance();
        LayerStack stack = canvas.getLayers();
        int idx = stack.indexOf(entry.layer);
        if (idx < 0) { committed.remove(entry); return; } // stale (layer deleted)

        editingEntry = entry;
        prevActiveIndex = stack.getActiveIndex();
        stack.setActive(idx); // edits target the text's own layer
        pendingCommand = new Drawcommand(canvas); // before = text layer with the old text
        clearBox(entry.layer.getImage(), entry.box); // remove the old text from its own layer
        committed.remove(entry);
        canvas.notifyLayersChanged();
        canvas.repaint();
        startEditing(entry.box, entry.autoGrow, entry.text, entry.font, entry.size, entry.color,
                entry.bold, entry.italic, entry.underline);
    }

    // ---- editing overlay --------------------------------------------------

    private void startEditing(Rectangle imageBox, boolean autoGrow) {
        editingEntry = null; // brand-new text, not a re-edit
        startEditing(imageBox, autoGrow, "", selectedFont, fontSize, textColor, bold, italic, underline);
    }

    private void startEditing(Rectangle imageBox, boolean autoGrow, String text, String font, int size,
                              Color color, boolean b, boolean i, boolean u) {
        canvas = CanvasPanel.getInstance();
        canvas.setLayout(null);
        this.autoGrow = autoGrow;
        this.editFont = font;
        this.editSize = size;
        this.editColor = color;
        this.editBold = b;
        this.editItalic = i;
        this.editUnderline = u;
        isTyping = true;

        float zoom = canvas.getZoom();
        Point screen = canvas.imageToScreen(imageBox.x, imageBox.y);
        int sw = Math.max(20, Math.round(imageBox.width * zoom));
        int sh = Math.max(Math.round((size + 8) * zoom), Math.round(imageBox.height * zoom));

        editor = new JTextArea(text);
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.setOpaque(false);
        editor.setForeground(color);
        editor.setCaretColor(color);
        editor.setFont(makeFont(font, Math.max(1, Math.round(size * zoom))));
        editor.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 1f, 3f, 2f, false));
        editor.setBounds(screen.x, screen.y, sw, sh);
        canvas.add(editor);

        editor.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "commitText");
        editor.getActionMap().put("commitText", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { commit(); }
        });
        editor.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commit(); }
        });
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

    /** Commit the overlay: draw onto the text's own layer and remove the overlay. */
    private void commit() {
        if (editor == null) return;
        JTextArea ed = editor;
        editor = null;
        isTyping = false;
        lastCommitAt = System.currentTimeMillis();

        String text = ed.getText();
        Rectangle b = ed.getBounds();
        float zoom = canvas.getZoom();
        int ix = Math.round((b.x - canvas.getRenderOffsetX()) / zoom);
        int iy = Math.round((b.y - canvas.getRenderOffsetY()) / zoom);
        int iw = Math.round(b.width / zoom);
        int ih = Math.round(b.height / zoom);
        Rectangle box = new Rectangle(ix, iy, iw, ih);

        canvas.remove(ed);
        canvas.repaint();

        boolean hasText = text != null && !text.trim().isEmpty();
        LayerStack stack = canvas.getLayers();

        if (editingEntry != null) {
            // RE-EDIT: the text layer is active and its box was cleared in beginReEdit.
            if (hasText) {
                drawText(canvas.getActiveLayer().getImage(), text, box);
                committed.add(new TextEntry(text, new Rectangle(box), editFont, editSize, editColor,
                        editBold, editItalic, editUnderline, autoGrow, canvas.getActiveLayer()));
            }
            if (pendingCommand != null) {
                pendingCommand.storeAfterState();
                CommandManager.getInstance().executeCommand(pendingCommand);
                pendingCommand = null;
            }
            stack.setActive(Math.min(prevActiveIndex, stack.getSize() - 1)); // back to working layer
            editingEntry = null;
            canvas.notifyLayersChanged();
            canvas.repaint();
            return;
        }

        // NEW text: drop it on its own layer just above the working layer.
        if (!hasText) return;
        int prev = stack.getActiveIndex();
        Layer textLayer = stack.addLayer(layerName(text)); // becomes active, above prev
        int textIndex = stack.getActiveIndex();
        drawText(textLayer.getImage(), text, box);
        committed.add(new TextEntry(text, new Rectangle(box), editFont, editSize, editColor,
                editBold, editItalic, editUnderline, autoGrow, textLayer));
        if (committed.size() > MAX_ENTRIES) committed.remove(0);
        CommandManager.getInstance().executeCommand(new AddTextLayerCommand(canvas, textLayer, textIndex, prev));
        stack.setActive(Math.min(prev, stack.getSize() - 1)); // keep painting on the working layer
        canvas.notifyLayersChanged();
        canvas.repaint();
    }

    // ---- drawing helpers --------------------------------------------------

    // Draw the text onto a layer image (wrapped + clipped to the box).
    private void drawText(BufferedImage target, String text, Rectangle box) {
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(makeFont(editFont, editSize));
        g.setColor(editColor);
        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight();
        int pad = 3;
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
    }

    // Clear a box region of a layer to transparent (removes old text on re-edit).
    private void clearBox(BufferedImage target, Rectangle box) {
        Graphics2D g = target.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(box.x, box.y, box.width, box.height);
        g.dispose();
    }

    // Name a text layer after its content (first line, trimmed).
    private String layerName(String text) {
        String first = text.trim();
        int nl = first.indexOf('\n');
        if (nl >= 0) first = first.substring(0, nl).trim();
        if (first.isEmpty()) return "Text";
        return first.length() > 24 ? first.substring(0, 24) + "..." : first;
    }

    private List<String> wrapLine(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            // A word wider than the box is hard-broken by characters (like the editor),
            // so long runs with no spaces still wrap instead of being clipped.
            if (fm.stringWidth(word) > maxWidth) {
                if (line.length() > 0) { lines.add(line.toString()); line = new StringBuilder(); }
                StringBuilder chunk = new StringBuilder();
                for (int i = 0; i < word.length(); i++) {
                    char c = word.charAt(i);
                    if (chunk.length() > 0 && fm.stringWidth(chunk.toString() + c) > maxWidth) {
                        lines.add(chunk.toString());
                        chunk = new StringBuilder();
                    }
                    chunk.append(c);
                }
                line = chunk; // leftover starts the next line so following words can join
                continue;
            }
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

    // Build the edit's font with its style; fall back to a logical family if missing.
    private Font makeFont(String family, int size) {
        String resolved = family;
        if (!new Font(family, Font.PLAIN, size).getFamily().equalsIgnoreCase(family)) {
            resolved = Font.SANS_SERIF;
        }
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.FAMILY, resolved);
        attrs.put(TextAttribute.SIZE, (float) size);
        if (editBold) attrs.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        if (editItalic) attrs.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
        if (editUnderline) attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        return new Font(attrs);
    }

    private Rectangle rectBetween(Point a, Point b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        return new Rectangle(x, y, Math.abs(b.x - a.x), Math.abs(b.y - a.y));
    }

    // One remembered text block, bound to its own layer.
    private static final class TextEntry {
        final String text;
        final Rectangle box;
        final String font;
        final int size;
        final Color color;
        final boolean bold, italic, underline;
        final boolean autoGrow;
        final Layer layer;

        TextEntry(String text, Rectangle box, String font, int size, Color color,
                  boolean bold, boolean italic, boolean underline, boolean autoGrow, Layer layer) {
            this.text = text;
            this.box = box;
            this.font = font;
            this.size = size;
            this.color = color;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.autoGrow = autoGrow;
            this.layer = layer;
        }
    }
}
