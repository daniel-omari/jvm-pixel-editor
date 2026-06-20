package com.danielomari.pixeleditor.layers

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/**
 * Ordered stack of [Layer]s (index 0 = bottom, drawn first). Exactly one layer
 * is "active" - the surface every drawing tool operates on. The panel composites
 * the visible layers bottom-to-top for display, and [flatten] does the same into
 * a single image for saving/export.
 *
 * The stack always keeps at least one layer; the bottom one starts as an opaque
 * white "Background" so a fresh document looks like a blank page.
 */
class LayerStack(var width: Int, var height: Int) {

    private val items = ArrayList<Layer>()
    var activeIndex = 0
        private set
    private var nextNumber = 1 // never-reused counter for default layer names (Photoshop-style)

    init {
        items.add(blank("Background", opaqueWhite = true))
    }

    // ---- queries ----
    val size: Int get() = items.size
    fun layers(): List<Layer> = items
    fun active(): Layer = items[activeIndex.coerceIn(0, items.size - 1)]
    fun get(i: Int): Layer = items[i]
    fun indexOf(layer: Layer): Int = items.indexOf(layer)

    /** Set the document dimensions without touching pixels (used by undo). */
    fun setSize(w: Int, h: Int) {
        width = w
        height = h
    }

    /** Resize the canvas: rebuild every layer at the new size, anchoring content
     *  top-left (transparent where extended, cropped where shrunk). */
    fun resize(newW: Int, newH: Int) {
        if (newW <= 0 || newH <= 0) return
        width = newW
        height = newH
        for (layer in items) {
            val resized = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
            val g = resized.createGraphics()
            g.drawImage(layer.image, 0, 0, null)
            g.dispose()
            layer.image = resized
        }
    }

    // ---- active-layer selection ----
    fun setActive(i: Int) { if (i in items.indices) activeIndex = i }

    // ---- structural operations ----

    /** Add a transparent layer, auto-named with a never-reused counter (Photoshop-style). */
    fun addLayer(): Layer = addLayer("Layer ${nextNumber++}")

    /** Add a transparent layer just above the active one and make it active. */
    fun addLayer(name: String): Layer {
        val layer = blank(name, opaqueWhite = false)
        val at = activeIndex + 1
        items.add(at, layer)
        activeIndex = at
        return layer
    }

    /** Delete the active layer (never the last remaining one). */
    fun deleteActive() {
        if (items.size <= 1) return
        items.removeAt(activeIndex)
        activeIndex = activeIndex.coerceIn(0, items.size - 1)
    }

    fun moveActiveUp() {
        if (activeIndex < items.size - 1) { swap(activeIndex, activeIndex + 1); activeIndex++ }
    }

    fun moveActiveDown() {
        if (activeIndex > 0) { swap(activeIndex, activeIndex - 1); activeIndex-- }
    }

    /** Remove the layer at [i] (never the last one) and return it, or null. */
    fun removeAt(i: Int): Layer? {
        if (items.size <= 1 || i !in items.indices) return null
        val removed = items.removeAt(i)
        activeIndex = activeIndex.coerceIn(0, items.size - 1)
        return removed
    }

    /** Re-insert [layer] at index [i] and make it active (used by undo). */
    fun insertAt(i: Int, layer: Layer) {
        val at = i.coerceIn(0, items.size)
        items.add(at, layer)
        activeIndex = at
    }

    /** Move the layer at [from] so it lands at index [to] (drag-and-drop reorder). */
    fun moveLayer(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val layer = items.removeAt(from)
        val dest = (if (from < to) to - 1 else to).coerceIn(0, items.size)
        items.add(dest, layer)
        activeIndex = dest
    }

    /** Replace the whole stack with the given layers (used when opening a .pxe
     *  project). Keeps at least one layer and clamps the active index. */
    fun loadFrom(w: Int, h: Int, newLayers: List<Layer>, newActive: Int) {
        if (newLayers.isEmpty()) return
        width = w
        height = h
        items.clear()
        items.addAll(newLayers)
        activeIndex = newActive.coerceIn(0, items.size - 1)
        nextNumber = items.size + 1
    }

    /** Collapse back to a single blank white background (used by New File). */
    fun reset() {
        items.clear()
        items.add(blank("Background", opaqueWhite = true))
        activeIndex = 0
        nextNumber = 1
    }

    // ---- compositing ----

    /** Flatten the visible layers into one image (for saving/export). */
    fun flatten(): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        paint(g)
        g.dispose()
        return out
    }

    /** Paint visible layers bottom-to-top (honouring opacity) onto g at (0,0). */
    fun paint(g: Graphics2D) {
        for (layer in items) {
            if (!layer.visible) continue
            val old = g.composite
            if (layer.opacity < 1f) {
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.opacity.coerceIn(0f, 1f))
            }
            g.drawImage(layer.image, 0, 0, null)
            g.composite = old
        }
    }

    private fun swap(a: Int, b: Int) {
        val t = items[a]; items[a] = items[b]; items[b] = t
    }

    private fun blank(name: String, opaqueWhite: Boolean): Layer {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        if (opaqueWhite) {
            val g = img.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.dispose()
        }
        return Layer(name, img)
    }
}
