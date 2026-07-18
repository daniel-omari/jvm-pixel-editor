package com.danielomari.pixeleditor.util.tools

import com.danielomari.pixeleditor.commands.CommandManager
import com.danielomari.pixeleditor.commands.MoveCommand
import com.danielomari.pixeleditor.ui.CanvasPanel
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Selection / clipboard tool, rewritten in Kotlin around one clear model.
 *
 * At any moment a selection is either nothing, or a FLOATING LAYER: a
 * [floatingContent] image at [selectionBounds], drawn as an overlay and only
 * stamped into the canvas when committed. While the mouse is down the tool is
 * in one [Drag] mode.
 *
 * [liftedFromCanvas] records whether the canvas pixels under a floating layer
 * have been cleared. A captured selection lifts on its first move; a pasted
 * layer is additive (lifted from the start) so moving it never erases what is
 * underneath -- the exact behaviour the old nine-flag version never got right.
 *
 * Selection state lives in the companion object so the several SelectTool
 * instances created around the app share one coherent selection (this mirrors
 * the original's static fields, but consistently and in one place).
 */
class SelectTool : Tool {

    private enum class Drag { NONE, SELECTING, MOVING, RESIZING }

    init {
        instance = this
    }

    // ---- canvas wiring ----------------------------------------------------

    fun setCanvas(canvasPanel: CanvasPanel?) {
        canvas = canvasPanel
        if (canvasPanel != null) installKeyboardShortcuts()
    }

    fun activate() {
        val c = canvas ?: return
        c.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        // Only hover-cursor feedback is registered here; press/drag/release come
        // through CanvasPanel's central dispatch already in image coordinates.
        val listener = hoverListener ?: object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) = updateCursor(c.screenToImage(e.x, e.y))
        }.also { hoverListener = it }
        c.removeMouseMotionListener(listener) // never stack duplicates
        c.addMouseMotionListener(listener)
    }

    fun deactivate() {
        commitFloating()
        clearSelection()
        canvas?.let { c ->
            hoverListener?.let { c.removeMouseMotionListener(it) }
            c.cursor = Cursor.getDefaultCursor()
        }
    }

    // ---- mouse handling (coordinates are already image-space) -------------

    override fun onPress(e: MouseEvent) {
        val c = canvas ?: return
        val p = Point(e.x, e.y)

        if (e.button == MouseEvent.BUTTON3) {
            val bounds = selectionBounds
            when {
                bounds != null && bounds.contains(p) -> showContextMenu(p)
                clipboardContent != null -> showPasteMenu(p)
            }
            return
        }
        if (e.button != MouseEvent.BUTTON1) return

        val bounds = selectionBounds
        // Clicking outside an existing selection commits it and starts fresh.
        if (bounds != null && !bounds.contains(p) && getResizeHandle(p) == -1) {
            commitFloating()
            clearSelection()
        }

        val current = selectionBounds
        if (current != null) {
            val handle = getResizeHandle(p)
            if (handle != -1) {
                drag = Drag.RESIZING
                resizeHandle = handle
                beginGeometryCommand(p, current)
                return
            }
            if (current.contains(p)) {
                drag = Drag.MOVING
                beginGeometryCommand(p, current)
                return
            }
        }

        // Begin a brand-new selection rectangle.
        drag = Drag.SELECTING
        startPoint = p
        selectionBounds = Rectangle(p)
    }

    override fun onDrag(e: MouseEvent) {
        val p = Point(e.x, e.y)
        when (drag) {
            Drag.SELECTING -> {
                selectionBounds = rectBetween(startPoint ?: p, p)
                canvas?.repaint()
            }
            Drag.MOVING -> {
                val bounds = selectionBounds ?: return
                liftIfNeeded()
                bounds.setLocation(dragOrigin.x + (p.x - dragStart.x), dragOrigin.y + (p.y - dragStart.y))
                canvas?.repaint()
            }
            Drag.RESIZING -> {
                val newBounds = calculateResizedBounds(resizeHandle, p.x - dragStart.x, p.y - dragStart.y)
                if (newBounds.width >= MIN_SIZE && newBounds.height >= MIN_SIZE) {
                    liftIfNeeded()
                    selectionBounds = newBounds
                    resizeSelectedContent()
                    canvas?.repaint()
                }
            }
            Drag.NONE -> {}
        }
    }

    override fun onRelease(e: MouseEvent) {
        when (drag) {
            Drag.SELECTING -> {
                val bounds = selectionBounds
                if (bounds != null && bounds.width > MIN_SIZE && bounds.height > MIN_SIZE) {
                    captureSelectedContent()
                } else {
                    clearSelection()
                }
            }
            Drag.MOVING, Drag.RESIZING -> finishGeometryCommand()
            Drag.NONE -> {}
        }
        drag = Drag.NONE
        canvas?.repaint()
    }

    // ---- selection capture / lift / commit --------------------------------

    /** Copy the canvas region under the selection into a floating layer. */
    fun captureSelectedContent() {
        val c = canvas ?: return
        val bounds = selectionBounds ?: return
        val image = c.canvasImage
        val clip = bounds.intersection(Rectangle(0, 0, image.width, image.height))
        if (clip.width <= 0 || clip.height <= 0) return

        val content = BufferedImage(clip.width, clip.height, BufferedImage.TYPE_INT_ARGB)
        content.createGraphics().apply {
            drawImage(image.getSubimage(clip.x, clip.y, clip.width, clip.height), 0, 0, null)
            dispose()
        }
        makeWhiteTransparent(content)

        floatingContent = content
        originalContent = deepCopy(content)
        selectionBounds = Rectangle(clip)
        liftedFromCanvas = false
    }

    /** On the first move of a captured selection, clear the pixels it lifted. */
    private fun liftIfNeeded() {
        if (liftedFromCanvas) return
        val c = canvas ?: return
        val bounds = selectionBounds ?: return
        c.canvasImage.createGraphics().apply {
            color = Color.WHITE
            fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
            dispose()
        }
        liftedFromCanvas = true
    }

    /** Stamp the floating layer into the canvas at its current position. */
    private fun commitFloating() {
        val c = canvas ?: return
        val content = floatingContent ?: return
        val bounds = selectionBounds ?: return
        c.canvasImage.createGraphics().apply {
            drawImage(content, bounds.x, bounds.y, null)
            dispose()
        }
        c.repaint()
    }

    // ---- rendering --------------------------------------------------------

    fun drawSelection(g: Graphics2D) {
        val c = canvas ?: return
        val bounds = selectionBounds ?: return
        val saved: AffineTransform = g.transform
        // The shared Graphics is already translated to the document origin and
        // zoomed, so image-space coordinates draw directly.
        try {
            if (drag == Drag.SELECTING) {
                g.color = Color.BLACK
                g.stroke = dashedStroke()
                g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
            } else if (floatingContent != null) {
                g.drawImage(floatingContent, bounds.x, bounds.y, null)
                g.color = Color.BLACK
                g.stroke = dashedStroke()
                g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
                drawHandles(g, bounds)
            }
        } finally {
            g.transform = saved
        }
    }

    private fun dashedStroke() =
        BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(3f), 0f)

    private fun drawHandles(g: Graphics2D, b: Rectangle) {
        g.stroke = BasicStroke(1f)
        for (h in handleRects(b)) {
            g.color = Color.WHITE
            g.fillRect(h.x, h.y, h.width, h.height)
            g.color = Color.BLACK
            g.drawRect(h.x, h.y, h.width, h.height)
        }
    }

    // ---- resize handles ---------------------------------------------------

    private fun handleRects(b: Rectangle): Array<Rectangle> {
        val s = HANDLE_SIZE
        val half = s / 2
        val midX = b.x + b.width / 2
        val midY = b.y + b.height / 2
        val right = b.x + b.width
        val bottom = b.y + b.height
        // order: NW, N, NE, E, SE, S, SW, W
        return arrayOf(
            Rectangle(b.x - half, b.y - half, s, s),
            Rectangle(midX - half, b.y - half, s, s),
            Rectangle(right - half, b.y - half, s, s),
            Rectangle(right - half, midY - half, s, s),
            Rectangle(right - half, bottom - half, s, s),
            Rectangle(midX - half, bottom - half, s, s),
            Rectangle(b.x - half, bottom - half, s, s),
            Rectangle(b.x - half, midY - half, s, s),
        )
    }

    private fun getResizeHandle(p: Point): Int {
        val bounds = selectionBounds ?: return -1
        return handleRects(bounds).indexOfFirst { it.contains(p) }
    }

    private fun calculateResizedBounds(handle: Int, dx: Int, dy: Int): Rectangle {
        val r = Rectangle(dragOrigin)
        when (handle) {
            0 -> { r.x = dragOrigin.x + dx; r.y = dragOrigin.y + dy; r.width = dragOrigin.width - dx; r.height = dragOrigin.height - dy }
            1 -> { r.y = dragOrigin.y + dy; r.height = dragOrigin.height - dy }
            2 -> { r.y = dragOrigin.y + dy; r.width = dragOrigin.width + dx; r.height = dragOrigin.height - dy }
            3 -> { r.width = dragOrigin.width + dx }
            4 -> { r.width = dragOrigin.width + dx; r.height = dragOrigin.height + dy }
            5 -> { r.height = dragOrigin.height + dy }
            6 -> { r.x = dragOrigin.x + dx; r.width = dragOrigin.width - dx; r.height = dragOrigin.height + dy }
            7 -> { r.x = dragOrigin.x + dx; r.width = dragOrigin.width - dx }
        }
        return r
    }

    private fun resizeSelectedContent() {
        val original = originalContent ?: return
        val bounds = selectionBounds ?: return
        if (bounds.width <= 0 || bounds.height <= 0) return
        val resized = BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB)
        resized.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(original, 0, 0, bounds.width, bounds.height, null)
            dispose()
        }
        floatingContent = resized
    }

    // ---- clipboard --------------------------------------------------------

    private fun copySelection() {
        val content = floatingContent ?: return
        val bounds = selectionBounds ?: return
        clipboardContent = deepCopy(content)
        clipboardBounds = Rectangle(bounds)
    }

    private fun cutSelection() {
        if (floatingContent == null) return
        copySelection()
        deleteSelection()
    }

    /** Paste from the keyboard: appears just off the copied location. */
    private fun pasteSelection() {
        val base = clipboardBounds ?: Rectangle(0, 0, 0, 0)
        pasteAt(Point(base.x + PASTE_OFFSET, base.y + PASTE_OFFSET))
    }

    private fun pasteSelectionAt(location: Point) = pasteAt(location)

    private fun pasteAt(location: Point) {
        val c = canvas ?: return
        val clip = clipboardContent ?: return

        commitFloating()
        clearSelection()

        val image = c.canvasImage
        val x = max(0, min(location.x, image.width - clip.width))
        val y = max(0, min(location.y, image.height - clip.height))

        floatingContent = deepCopy(clip)
        originalContent = deepCopy(clip)
        selectionBounds = Rectangle(x, y, clip.width, clip.height)
        // A paste is additive: nothing under it should ever be cleared.
        liftedFromCanvas = true

        val command = MoveCommand(c, x, y)
        command.storeAfterState()
        CommandManager.getInstance().executeCommand(command)
        c.repaint()
    }

    private fun deleteSelection() {
        val c = canvas ?: return
        val bounds = selectionBounds ?: return
        val command = MoveCommand(c, bounds.x, bounds.y)
        c.canvasImage.createGraphics().apply {
            color = Color.WHITE
            fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
            dispose()
        }
        clearSelection()
        command.storeAfterState()
        CommandManager.getInstance().executeCommand(command)
        c.repaint()
    }

    // ---- colour / external hooks ------------------------------------------

    fun recolourSelection(newColor: Color) {
        val c = canvas ?: return
        val content = floatingContent ?: return
        val bounds = selectionBounds ?: return
        // Snapshot the canvas BEFORE committing the recolour so undo can revert it.
        val command = MoveCommand(c, bounds.x, bounds.y)
        command.storeBeforeState()
        val recoloured = BufferedImage(content.width, content.height, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until content.width) {
            for (y in 0 until content.height) {
                val pixel = content.getRGB(x, y)
                if (pixel ushr 24 != 0) {
                    recoloured.setRGB(x, y, (pixel and 0xFF000000.toInt()) or (newColor.rgb and 0x00FFFFFF))
                }
            }
        }
        floatingContent = recoloured
        originalContent = deepCopy(recoloured)
        commitFloating()
        command.storeAfterState()
        CommandManager.getInstance().executeCommand(command)
    }

    fun selectAll(canvasPanel: CanvasPanel?) {
        val c = canvasPanel ?: return
        val image = c.canvasImage
        selectionBounds = Rectangle(0, 0, image.width, image.height)
        captureSelectedContent()
    }

    fun getSelectionBounds(): Rectangle? = selectionBounds

    fun getselectedContent(): BufferedImage? = floatingContent

    /** Used by RotateTool: make sure a selection exists, then return its bounds. */
    fun getClippedLocation(): Rectangle {
        if (selectionBounds == null) {
            val image = canvas?.canvasImage
            selectionBounds = if (image != null) Rectangle(0, 0, image.width, image.height) else Rectangle()
        }
        if (floatingContent == null) captureSelectedContent()
        return selectionBounds ?: Rectangle()
    }

    fun clearSelection() {
        selectionBounds = null
        floatingContent = null
        originalContent = null
        drag = Drag.NONE
        resizeHandle = -1
        liftedFromCanvas = false
        currentCommand = null
        startPoint = null
    }

    // ---- cursor -----------------------------------------------------------

    fun updateCursor(p: Point) {
        val c = canvas ?: return
        val bounds = selectionBounds
        if (bounds != null) {
            val handle = getResizeHandle(p)
            if (handle != -1) {
                c.cursor = RESIZE_CURSORS[handle]; return
            }
            if (bounds.contains(p)) {
                c.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR); return
            }
        }
        c.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
    }

    // ---- undo command helpers ---------------------------------------------

    private fun beginGeometryCommand(p: Point, bounds: Rectangle) {
        dragStart = p
        dragOrigin = Rectangle(bounds)
        if (currentCommand == null) {
            currentCommand = MoveCommand(canvas, bounds.x, bounds.y).also { it.storeBeforeState() }
        }
    }

    private fun finishGeometryCommand() {
        currentCommand?.let {
            it.storeAfterState()
            CommandManager.getInstance().executeCommand(it)
        }
        currentCommand = null
    }

    // ---- context menu + keyboard ------------------------------------------

    private fun showContextMenu(location: Point) {
        contextMenu().show(canvas, location.x, location.y)
    }

    private fun showPasteMenu(location: Point) {
        JPopupMenu().apply {
            add(JMenuItem("Paste").apply { addActionListener { pasteSelectionAt(location) } })
            show(canvas, location.x, location.y)
        }
    }

    private fun contextMenu(): JPopupMenu = JPopupMenu().apply {
        add(JMenuItem("Copy").apply { addActionListener { copySelection() } })
        add(JMenuItem("Cut").apply { addActionListener { cutSelection() } })
        add(JMenuItem("Paste").apply {
            isEnabled = clipboardContent != null
            addActionListener { pasteSelection() }
        })
        addSeparator()
        add(JMenuItem("Delete").apply { addActionListener { deleteSelection() } })
    }

    private fun installKeyboardShortcuts() {
        if (keyboardInstalled) return
        keyboardInstalled = true
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { e ->
            if (e.id != KeyEvent.KEY_PRESSED) return@addKeyEventDispatcher false
            val ctrl = (e.modifiersEx and (KeyEvent.CTRL_DOWN_MASK or KeyEvent.META_DOWN_MASK)) != 0
            // Paste needs only the clipboard, so handle it regardless of selection.
            if (ctrl && e.keyCode == KeyEvent.VK_V) {
                if (clipboardContent != null) { pasteSelection(); return@addKeyEventDispatcher true }
                return@addKeyEventDispatcher false
            }
            if (selectionBounds == null) return@addKeyEventDispatcher false
            when {
                ctrl && e.keyCode == KeyEvent.VK_C -> { copySelection(); true }
                ctrl && e.keyCode == KeyEvent.VK_X -> { cutSelection(); true }
                e.keyCode == KeyEvent.VK_DELETE -> { deleteSelection(); true }
                e.keyCode == KeyEvent.VK_ESCAPE -> { clearSelection(); canvas?.repaint(); true }
                else -> false
            }
        }
    }

    // ---- small helpers ----------------------------------------------------

    private fun rectBetween(a: Point, b: Point): Rectangle =
        Rectangle(min(a.x, b.x), min(a.y, b.y), abs(b.x - a.x), abs(b.y - a.y))

    private fun deepCopy(source: BufferedImage): BufferedImage {
        val copy = BufferedImage(source.width, source.height, source.type)
        copy.createGraphics().apply { drawImage(source, 0, 0, null); dispose() }
        return copy
    }

    private fun makeWhiteTransparent(image: BufferedImage) {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val c = Color(image.getRGB(x, y), true)
                if (c.red > 250 && c.green > 250 && c.blue > 250) image.setRGB(x, y, 0)
            }
        }
    }

    companion object {
        private const val HANDLE_SIZE = 8
        private const val MIN_SIZE = 5
        private const val PASTE_OFFSET = 16

        private var instance: SelectTool? = null

        @JvmStatic
        fun getInstance(): SelectTool = instance ?: SelectTool()

        // --- shared selection state (one selection across all instances) ---
        private var canvas: CanvasPanel? = null
        private var selectionBounds: Rectangle? = null
        private var floatingContent: BufferedImage? = null
        private var originalContent: BufferedImage? = null
        private var clipboardContent: BufferedImage? = null
        private var clipboardBounds: Rectangle? = null
        private var startPoint: Point? = null
        private var drag: Drag = Drag.NONE
        private var resizeHandle: Int = -1
        private var liftedFromCanvas: Boolean = false
        private var dragStart: Point = Point(0, 0)
        private var dragOrigin: Rectangle = Rectangle()
        private var currentCommand: MoveCommand? = null
        private var hoverListener: MouseMotionAdapter? = null
        private var keyboardInstalled: Boolean = false

        private val RESIZE_CURSORS: Array<Cursor> = intArrayOf(
            Cursor.NW_RESIZE_CURSOR, Cursor.N_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR,
            Cursor.E_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR,
            Cursor.SW_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR,
        ).map { Cursor.getPredefinedCursor(it) }.toTypedArray()

        /** Hook used by RotateTool: replace the selection with a rotated image. */
        @JvmStatic
        fun updateSelectionAfterRotation(x: Int, y: Int, width: Int, height: Int, rotatedImage: BufferedImage) {
            selectionBounds = Rectangle(x, y, width, height)
            floatingContent = rotatedImage
            originalContent = rotatedImage
        }

        /** Hook used by InsertTool: drop an inserted image in as a selection. */
        @JvmStatic
        fun updateSelectionAfterInsertion(x: Int, y: Int, width: Int, height: Int, insertedImage: BufferedImage) {
            selectionBounds = Rectangle(x, y, width, height)
            floatingContent = insertedImage
            originalContent = insertedImage
            liftedFromCanvas = true
        }

        @JvmStatic
        fun resetStartPointAfterInsertion() {
            startPoint = Point(0, 0)
        }
    }
}
