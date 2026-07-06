package com.danielomari.pixeleditor.layers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless unit tests for the Kotlin layer engine. These exercise the stack's
 * structural operations, the compositing rules, and canvas resizing - all pure
 * image/collection logic, so no display is required.
 */
class LayerStackTest {

    private LayerStack stack;

    @BeforeEach
    void setUp() {
        stack = new LayerStack(4, 4);
    }

    // ---- helpers ----

    private static void fill(Layer layer, Color color) {
        Graphics2D g = layer.getImage().createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, layer.getImage().getWidth(), layer.getImage().getHeight());
        g.dispose();
    }

    private List<String> names() {
        return stack.layers().stream().map(Layer::getName).toList();
    }

    // ---- initial state ----

    @Test
    void newStackHasOneOpaqueWhiteBackground() {
        assertEquals(1, stack.getSize());
        assertEquals("Background", stack.get(0).getName());
        assertEquals(0, stack.getActiveIndex());
        assertEquals(0xFFFFFFFF, stack.get(0).getImage().getRGB(0, 0));
    }

    // ---- adding layers ----

    @Test
    void addLayerInsertsAboveActiveAndBecomesActive() {
        Layer added = stack.addLayer();
        assertEquals(2, stack.getSize());
        assertEquals(1, stack.getActiveIndex());
        assertEquals(added, stack.active());
    }

    @Test
    void addedLayerIsTransparent() {
        Layer added = stack.addLayer();
        assertEquals(0, added.getImage().getRGB(0, 0) >>> 24, "new layer should be transparent");
    }

    @Test
    void autoNamesAreNeverReused() {
        stack.addLayer(); // Layer 1
        stack.addLayer(); // Layer 2
        stack.deleteActive(); // removes Layer 2
        Layer next = stack.addLayer();
        assertEquals("Layer 3", next.getName(), "deleted layer numbers must not be recycled");
    }

    @Test
    void addLayerAboveMiddleActive() {
        stack.addLayer("upper");
        stack.setActive(0);
        stack.addLayer("middle");
        assertEquals(List.of("Background", "middle", "upper"), names());
        assertEquals(1, stack.getActiveIndex());
    }

    // ---- deleting ----

    @Test
    void deleteActiveNeverRemovesLastLayer() {
        stack.addLayer();
        stack.deleteActive();
        assertEquals(1, stack.getSize());
        stack.deleteActive(); // last layer: must be a no-op
        assertEquals(1, stack.getSize());
    }

    @Test
    void deleteClampsActiveIndex() {
        stack.addLayer();
        stack.addLayer(); // active = 2 (top)
        stack.deleteActive();
        assertEquals(1, stack.getActiveIndex());
        assertTrue(stack.getActiveIndex() < stack.getSize());
    }

    // ---- move up / down ----

    @Test
    void moveActiveUpAndDownSwapNeighbours() {
        stack.addLayer("a"); // [Background, a], active a
        stack.moveActiveDown();
        assertEquals(List.of("a", "Background"), names());
        assertEquals(0, stack.getActiveIndex());
        stack.moveActiveUp();
        assertEquals(List.of("Background", "a"), names());
        assertEquals(1, stack.getActiveIndex());
    }

    @Test
    void moveAtBoundsIsNoOp() {
        stack.moveActiveDown(); // single layer, bottom
        assertEquals(1, stack.getSize());
        assertEquals(0, stack.getActiveIndex());
        stack.moveActiveUp(); // already top
        assertEquals(0, stack.getActiveIndex());
    }

    // ---- removeAt / insertAt (undo support) ----

    @Test
    void removeAtAndInsertAtRoundTrip() {
        stack.addLayer("a");
        stack.addLayer("b");
        Layer removed = stack.removeAt(1);
        assertNotNull(removed);
        assertEquals("a", removed.getName());
        assertEquals(List.of("Background", "b"), names());
        stack.insertAt(1, removed);
        assertEquals(List.of("Background", "a", "b"), names());
        assertEquals(1, stack.getActiveIndex());
    }

    @Test
    void removeAtRefusesLastLayerAndBadIndex() {
        assertNull(stack.removeAt(0), "must not remove the only layer");
        stack.addLayer();
        assertNull(stack.removeAt(7), "out-of-range index returns null");
    }

    // ---- drag-and-drop reorder ----

    @Test
    void moveLayerUpwardsLandsExactlyOnTarget() {
        stack.addLayer("a");
        stack.addLayer("b"); // [Background, a, b]
        stack.moveLayer(0, 2); // drag Background onto the top row
        assertEquals(List.of("a", "b", "Background"), names());
        assertEquals(2, stack.getActiveIndex());
    }

    @Test
    void moveLayerDownwardsLandsExactlyOnTarget() {
        stack.addLayer("a");
        stack.addLayer("b"); // [Background, a, b]
        stack.moveLayer(2, 0); // drag b onto the bottom row
        assertEquals(List.of("b", "Background", "a"), names());
        assertEquals(0, stack.getActiveIndex());
    }

    @Test
    void moveLayerWithInvalidIndicesIsNoOp() {
        stack.addLayer("a");
        stack.moveLayer(0, 5);
        stack.moveLayer(-1, 0);
        stack.moveLayer(1, 1);
        assertEquals(List.of("Background", "a"), names());
    }

    // ---- compositing ----

    @Test
    void flattenSkipsHiddenLayers() {
        Layer top = stack.addLayer("cover");
        fill(top, Color.BLACK);
        top.setVisible(false);
        BufferedImage flat = stack.flatten();
        assertEquals(0xFFFFFFFF, flat.getRGB(1, 1), "hidden layer must not be composited");
    }

    @Test
    void flattenHonoursLayerOpacity() {
        fill(stack.get(0), Color.RED);
        Layer top = stack.addLayer("veil");
        fill(top, Color.WHITE);
        top.setOpacity(0.5f);
        int rgb = stack.flatten().getRGB(1, 1);
        int green = (rgb >> 8) & 0xFF;
        assertEquals(255, (rgb >> 16) & 0xFF);
        assertTrue(green > 100 && green < 155,
                "50% white over red should blend green channel to ~128, was " + green);
    }

    // ---- resize ----

    @Test
    void resizeCropsAndExtendsAnchoredTopLeft() {
        stack.get(0).getImage().setRGB(1, 1, 0xFF112233);
        stack.resize(2, 2);
        assertEquals(2, stack.getWidth());
        assertEquals(0xFF112233, stack.get(0).getImage().getRGB(1, 1), "content kept top-left");

        stack.resize(4, 4);
        int alpha = stack.get(0).getImage().getRGB(3, 3) >>> 24;
        assertEquals(0, alpha, "extended area should be transparent");
    }

    @Test
    void resizeRejectsNonPositiveDimensions() {
        stack.resize(0, 5);
        stack.resize(5, -1);
        assertEquals(4, stack.getWidth());
        assertEquals(4, stack.getHeight());
    }

    @Test
    void setSizeChangesDimensionsWithoutTouchingPixels() {
        stack.setSize(9, 9);
        assertEquals(9, stack.getWidth());
        assertEquals(4, stack.get(0).getImage().getWidth(), "setSize must not rebuild images");
    }

    // ---- load / reset ----

    @Test
    void loadFromReplacesStackAndClampsActive() {
        Layer l = new Layer("imported", new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), true, 1f);
        stack.loadFrom(8, 8, List.of(l), 5);
        assertEquals(1, stack.getSize());
        assertEquals("imported", stack.active().getName());
        assertEquals(0, stack.getActiveIndex(), "out-of-range active index is clamped");
        assertEquals(8, stack.getWidth());
    }

    @Test
    void resetRestoresSingleWhiteBackground() {
        stack.addLayer();
        stack.addLayer();
        stack.reset();
        assertEquals(1, stack.getSize());
        assertEquals("Background", stack.get(0).getName());
        assertEquals(0, stack.getActiveIndex());
        assertFalse(names().contains("Layer 1"));
        assertEquals("Layer 1", stack.addLayer().getName(), "numbering restarts after reset");
    }
}
