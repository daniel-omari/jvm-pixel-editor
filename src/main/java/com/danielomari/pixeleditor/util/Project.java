package com.danielomari.pixeleditor.util;

import com.danielomari.pixeleditor.layers.Layer;
import com.danielomari.pixeleditor.layers.LayerStack;
import com.danielomari.pixeleditor.ui.CanvasPanel;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Native layered project format (.pxe). A .pxe file is just a ZIP archive that
 * stores one PNG per layer plus a small text manifest describing the canvas
 * size, layer order, names, opacity and visibility. Saving and re-opening a
 * .pxe therefore round-trips the full layer stack (unlike a flattened PNG/JPEG
 * export), so work can be put down and picked back up without losing layers.
 */
public final class Project {
    private static final String EXT = "pxe";
    private static final String MANIFEST = "manifest.properties";
    private static File lastProjectFile = null;

    private Project() {}

    // ---- Save ----
    public static void saveProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Project");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Pixel Editor project (*.pxe)", EXT));
        if (lastProjectFile != null) chooser.setSelectedFile(lastProjectFile);

        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + EXT)) {
            file = new File(file.getAbsolutePath() + "." + EXT);
        }

        LayerStack stack = CanvasPanel.getInstance().getLayers();
        List<Layer> layers = stack.layers();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            // One PNG per layer (ARGB, so transparency is preserved).
            for (int i = 0; i < layers.size(); i++) {
                zos.putNextEntry(new ZipEntry("layer_" + i + ".png"));
                ImageIO.write(layers.get(i).getImage(), "png", zos);
                zos.closeEntry();
            }
            // Manifest describing the stack.
            Properties m = new Properties();
            m.setProperty("canvas.width", Integer.toString(stack.getWidth()));
            m.setProperty("canvas.height", Integer.toString(stack.getHeight()));
            m.setProperty("active", Integer.toString(stack.getActiveIndex()));
            m.setProperty("layer.count", Integer.toString(layers.size()));
            for (int i = 0; i < layers.size(); i++) {
                Layer l = layers.get(i);
                m.setProperty("layer." + i + ".name", l.getName());
                m.setProperty("layer." + i + ".visible", Boolean.toString(l.getVisible()));
                m.setProperty("layer." + i + ".opacity", Float.toString(l.getOpacity()));
                m.setProperty("layer." + i + ".file", "layer_" + i + ".png");
            }
            zos.putNextEntry(new ZipEntry(MANIFEST));
            m.store(zos, "Pixel Editor project");
            zos.closeEntry();

            lastProjectFile = file;
            System.out.println("Project saved to: " + file.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to save project:\n" + ex.getMessage(),
                    "Save Project", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Open ----
    public static void openProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Project");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Pixel Editor project (*.pxe)", EXT));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();

        try (ZipFile zf = new ZipFile(file)) {
            ZipEntry manifestEntry = zf.getEntry(MANIFEST);
            if (manifestEntry == null) {
                JOptionPane.showMessageDialog(null, "Not a valid .pxe project (no manifest).",
                        "Open Project", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Properties m = new Properties();
            try (InputStream in = zf.getInputStream(manifestEntry)) {
                m.load(in);
            }

            int w = Integer.parseInt(m.getProperty("canvas.width", "800"));
            int h = Integer.parseInt(m.getProperty("canvas.height", "600"));
            int count = Integer.parseInt(m.getProperty("layer.count", "0"));
            int active = Integer.parseInt(m.getProperty("active", "0"));
            if (count <= 0) {
                JOptionPane.showMessageDialog(null, "Project has no layers.",
                        "Open Project", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<Layer> loaded = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String entryName = m.getProperty("layer." + i + ".file", "layer_" + i + ".png");
                ZipEntry imgEntry = zf.getEntry(entryName);
                if (imgEntry == null) continue;
                BufferedImage img;
                try (InputStream in = zf.getInputStream(imgEntry)) {
                    img = ImageIO.read(in);
                }
                if (img == null) continue;
                BufferedImage argb = toArgb(img, w, h);
                String name = m.getProperty("layer." + i + ".name", "Layer " + (i + 1));
                boolean visible = Boolean.parseBoolean(m.getProperty("layer." + i + ".visible", "true"));
                float opacity = parseFloat(m.getProperty("layer." + i + ".opacity", "1.0"), 1f);
                loaded.add(new Layer(name, argb, visible, opacity));
            }
            if (loaded.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Project layers could not be read.",
                        "Open Project", JOptionPane.ERROR_MESSAGE);
                return;
            }

            CanvasPanel canvas = CanvasPanel.getInstance();
            canvas.getLayers().loadFrom(w, h, loaded, active);
            canvas.notifyLayersChanged();
            canvas.revalidate();
            canvas.repaint();
            lastProjectFile = file;
            System.out.println("Project opened: " + file.getAbsolutePath());
        } catch (IOException | NumberFormatException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to open project:\n" + ex.getMessage(),
                    "Open Project", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Normalise any decoded image to an ARGB buffer of the document size so the
    // layer pixels line up with the canvas regardless of how the PNG decoded.
    private static BufferedImage toArgb(BufferedImage src, int w, int h) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB
                && src.getWidth() == w && src.getHeight() == h) {
            return src;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
