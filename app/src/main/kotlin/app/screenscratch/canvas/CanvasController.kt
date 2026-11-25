package app.screenscratch.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.screenscratch.core.edit.PartialErase
import app.screenscratch.core.edit.Selection
import app.screenscratch.core.edit.SelectionEdits
import app.screenscratch.core.edit.Similarity
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.PageSetup
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.bringForward
import app.screenscratch.core.model.bringToFront
import app.screenscratch.core.model.sendBackward
import app.screenscratch.core.model.sendToBack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The canvas's state and commands, for the Compose UI around [NoteCanvas]. The note is
 * observable as [note]; tool and appearance settings are Compose state. Use from the UI thread.
 */
class CanvasController(initial: Note) {
    private val _note = MutableStateFlow(initial)

    /** The note being edited; a new value after every edit, undo, redo or [load]. */
    val note: StateFlow<Note> = _note.asStateFlow()

    private val history = History()
    private val outlines = OutlineCache()
    internal val outlineCache: OutlineCache get() = outlines

    private val gestures = ToolGestures(this)
    internal val toolGestures: ToolGestures get() = gestures

    private var _tool by mutableStateOf(Tool.PEN)

    /**
     * What pen drags do; the pen's cycle button changes it too. Leaving [Tool.SELECT] clears the
     * selection.
     */
    var tool: Tool
        get() = _tool
        set(v) {
            if (v == _tool) return
            _tool = v
            // A cycle-button press mid-drag lets the drag finish on the selection first; see toolEnd.
            if (v != Tool.SELECT && !gestures.active) clearSelection()
        }

    /** What [Tool.ERASER] and the pen's erase button take. */
    var eraserMode by mutableStateOf(EraserMode.WHOLE_STROKE)

    /** How [Tool.SELECT] selects. */
    var selectMode by mutableStateOf(SelectMode.LASSO)

    /** Ids of the selected elements, in no particular order. Every id is in the note. */
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set

    val hasSelection: Boolean get() = selection.isNotEmpty()

    /** The union of the selected elements' bounds, or null when nothing is selected. */
    val selectionBounds: Rect? get() = if (selection.isEmpty()) null else Selection.bounds(_note.value.elements, selection)

    /** Colour of new strokes. */
    var color by mutableStateOf(Argb.BLACK)

    /** Nominal width of new strokes, canvas units. */
    var width by mutableFloatStateOf(DEFAULT_WIDTH)

    /** When on, only a stylus draws and fingers pan and zoom. */
    var penOnly by mutableStateOf(true)

    /** Swaps the pen's side buttons: the secondary erases and the primary cycles tools. */
    var swapStylusButtons by mutableStateOf(false)

    private var _darkMode by mutableStateOf(false)

    /** Draws the page dark and ink through the dark-mode colour transform. Never changes the note. */
    var darkMode: Boolean
        get() = _darkMode
        set(v) {
            if (v == _darkMode) return
            _darkMode = v
            listener?.onAppearanceChanged()
        }

    private var _frontBuffer by mutableStateOf(FrontBufferContent.SCENE)

    /** What the low-latency buffer holds while inking; see [FrontBufferContent]. */
    var frontBufferContent: FrontBufferContent
        get() = _frontBuffer
        set(v) {
            if (v == _frontBuffer) return
            _frontBuffer = v
            listener?.onAppearanceChanged()
        }

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    var viewport by mutableStateOf(Viewport())
        private set

    /** Latency and frame-time measurements from the canvas view. */
    val stats = CanvasStats()

    /** Eraser radius in view pixels. */
    var eraserRadiusPx = 0f
        internal set

    internal var listener: Listener? = null
    private var viewWidth = 0
    private var viewHeight = 0
    private var density = 1f
    private var fitPending = true
    private var gestureStart: Note? = null

    /** What the canvas view hears about. */
    internal interface Listener {
        fun onNoteChanged()
        fun onViewportChanged()
        fun onAppearanceChanged()
    }

    /** Replaces the note, clears the history and fits the view to it. */
    fun load(note: Note) {
        history.clear()
        gestureStart = null
        gestures.cancel()
        selection = emptySet()
        setNote(note)
        fitPending = true
        if (viewWidth > 0) initialFit()
    }

    /** Applies [edit] to the note as one undoable step. Does nothing if it returns the same note. */
    fun edit(edit: (Note) -> Note) {
        val before = _note.value
        val after = edit(before)
        if (after == before) return
        history.push(before)
        setNote(touch(after))
    }

    fun undo() {
        gestures.cancel()
        val n = history.undo(_note.value) ?: return
        setNote(n)
    }

    fun redo() {
        gestures.cancel()
        val n = history.redo(_note.value) ?: return
        setNote(n)
    }

    // Selection commands. Each edit is one undo step; selection itself is not in the history.

    /** Selects the elements with [ids] that are in the note, replacing the selection. */
    fun select(ids: Set<String>) {
        val present = if (ids.isEmpty()) emptySet() else _note.value.elements.mapNotNullTo(HashSet()) { e -> e.id.takeIf { it in ids } }
        if (present == selection) return
        selection = present
        overlayChanged()
    }

    fun selectAll() = select(_note.value.elements.mapTo(HashSet()) { it.id })

    fun clearSelection() = select(emptySet())

    /** Removes the selected elements. */
    fun deleteSelection() {
        val ids = selection
        if (ids.isEmpty()) return
        edit { it.copy(elements = SelectionEdits.delete(it.elements, ids)) }
    }

    /** Copies the selected elements on top of everything, offset by [DUPLICATE_OFFSET], and selects the copies. */
    fun duplicateSelection() {
        val ids = selection
        if (ids.isEmpty()) return
        val (elements, copies) = SelectionEdits.duplicate(_note.value.elements, ids, DUPLICATE_OFFSET, DUPLICATE_OFFSET)
        edit { it.copy(elements = elements) }
        select(copies.toSet())
    }

    fun bringToFront() = restack { it.bringToFront(selection) }

    /** Moves the selection above the next element that overlaps it. */
    fun bringForward() = restack { it.bringForward(selection) }

    /** Moves the selection below the next element that overlaps it. */
    fun sendBackward() = restack { it.sendBackward(selection) }

    fun sendToBack() = restack { it.sendToBack(selection) }

    private fun restack(f: (List<Element>) -> List<Element>) {
        if (selection.isEmpty()) return
        edit { it.copy(elements = f(it.elements)) }
    }

    /** Moves the selection by ([dx], [dy]) canvas units. */
    fun moveSelection(dx: Float, dy: Float) = transformSelection(selection, Similarity.translate(dx, dy))

    /** Scales the selection by [factor] (> 0) about ([originX], [originY]), canvas units; strokes' widths scale too. */
    fun scaleSelection(factor: Float, originX: Float, originY: Float) =
        transformSelection(selection, Similarity.scale(factor, originX, originY))

    internal fun transformSelection(ids: Set<String>, t: Similarity) {
        if (ids.isEmpty() || t.isIdentity) return
        edit { it.copy(elements = SelectionEdits.transform(it.elements, ids, t)) }
    }

    /** What the canvas view's scene draws differently while a gesture runs. */
    internal fun scenePreview(): ScenePreview? {
        val t = gestures.transform
        if (t.isIdentity) return null
        return ScenePreview(transformed = selection, transform = t)
    }

    internal fun overlayChanged() {
        listener?.onAppearanceChanged()
    }

    // Tool gestures from the canvas view, in canvas units.

    internal fun toolStart(x: Float, y: Float) = gestures.start(x, y)

    internal fun toolMove(x: Float, y: Float) = gestures.move(x, y)

    internal fun toolEnd() {
        gestures.end()
        if (tool != Tool.SELECT) clearSelection()
    }

    internal fun toolCancel() = gestures.cancel()

    /** Removes every element, undoably. */
    fun clear() = edit { if (it.elements.isEmpty()) it else it.copy(elements = emptyList()) }

    /** Changes the page setup, undoably, and fits the view to it. */
    fun setPage(page: PageSetup) {
        edit { if (it.page == page) it else it.copy(page = page) }
        fit()
    }

    /** Cycles [tool] as the pen's side button does. */
    fun cycleTool() {
        tool = tool.next()
    }

    // Viewport commands, in view pixels.

    fun panBy(dx: Float, dy: Float) = applyViewport(viewport.panBy(dx, dy))

    fun zoomBy(factor: Float, focusX: Float = viewWidth / 2f, focusY: Float = viewHeight / 2f) =
        applyViewport(viewport.zoomBy(factor, focusX, focusY))

    fun zoomTo(zoom: Float, focusX: Float = viewWidth / 2f, focusY: Float = viewHeight / 2f) =
        applyViewport(viewport.zoomTo(zoom, focusX, focusY))

    /** See [Viewport.transform]. */
    fun transform(dx: Float, dy: Float, factor: Float, focusX: Float, focusY: Float) =
        applyViewport(viewport.transform(dx, dy, factor, focusX, focusY))

    /** Shows the first page whole, or all content on an infinite canvas, or 100% at the origin when empty. */
    fun fit() {
        if (viewWidth <= 0) {
            fitPending = true
            return
        }
        val n = _note.value
        val margin = FIT_MARGIN_DP * density
        val page = n.page
        applyViewport(
            when {
                page is PageSetup.Pages -> Viewport.fit(page.pageRect(0), viewWidth, viewHeight, density, margin)
                n.elements.isEmpty() -> Viewport(density = density)
                else -> Viewport.fit(n.elements.map { it.bounds }.reduce(Rect::union), viewWidth, viewHeight, density, margin, maxZoom = 1f)
            },
        )
    }

    /** Shows [rect] (canvas units) whole, centred. */
    fun fitRect(rect: Rect) {
        if (viewWidth > 0) applyViewport(Viewport.fit(rect, viewWidth, viewHeight, density, FIT_MARGIN_DP * density))
    }

    // Called by the canvas view.

    internal fun onViewSize(width: Int, height: Int, density: Float) {
        val densityChanged = density != this.density
        viewWidth = width
        viewHeight = height
        this.density = density
        if (densityChanged) applyViewport(viewport.copy(density = density))
        if (fitPending && width > 0) initialFit()
    }

    private fun initialFit() {
        fitPending = false
        val page = _note.value.page
        if (page is PageSetup.Pages) {
            applyViewport(Viewport.fitWidth(page.pageRect(0), viewWidth, density, FIT_MARGIN_DP * density, maxZoom = 1f))
        } else {
            applyViewport(Viewport(density = density))
        }
    }

    internal fun commitStroke(stroke: Stroke) = edit { it.copy(elements = it.elements + stroke) }

    /** Starts an erase gesture: erasing updates the note live, and [endErase] records one undo step. */
    internal fun beginErase() {
        gestureStart = _note.value
    }

    /**
     * Erases along the canvas-unit capsule from a to b with radius [r], by [eraserMode]: whole
     * strokes it touches, or the parts of strokes it covers. Returns the strokes it changed.
     */
    internal fun eraseAlong(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Int {
        val n = _note.value
        if (eraserMode == EraserMode.PARTIAL) return erasePartAlong(n, ax, ay, bx, by, r)
        val hit = Eraser.hits(n.elements, outlines::outline, ax, ay, bx, by, r)
        if (hit.isEmpty()) return 0
        val ids = hit.toHashSet()
        setNote(n.copy(elements = n.elements.filterNot { it.id in ids }))
        return hit.size
    }

    private fun erasePartAlong(n: Note, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Int {
        var changed = 0
        var out: ArrayList<Element>? = null
        for ((i, e) in n.elements.withIndex()) {
            val pieces = if (e is Stroke) PartialErase.erase(e, ax, ay, bx, by, r) else null
            if (pieces == null) {
                out?.add(e)
                continue
            }
            if (out == null) out = ArrayList<Element>(n.elements.size + 1).apply { addAll(n.elements.subList(0, i)) }
            out.addAll(pieces)
            changed++
        }
        if (out != null) setNote(n.copy(elements = out))
        return changed
    }

    internal fun endErase() {
        val before = gestureStart ?: return
        gestureStart = null
        if (before.elements != _note.value.elements) {
            history.push(before)
            setNote(touch(_note.value))
        }
    }

    internal fun cancelErase() {
        val before = gestureStart ?: return
        gestureStart = null
        if (before != _note.value) setNote(before)
    }

    private fun touch(n: Note): Note = n.copy(modified = Instant.now().truncatedTo(ChronoUnit.MILLIS))

    private fun setNote(n: Note) {
        _note.value = n
        if (selection.isNotEmpty()) {
            val ids = n.elements.mapTo(HashSet()) { it.id }
            if (!ids.containsAll(selection)) selection = selection.filterTo(HashSet()) { it in ids }
        }
        canUndo = history.canUndo
        canRedo = history.canRedo
        outlines.retain(n.elements.mapNotNullTo(HashSet()) { (it as? Stroke)?.id })
        listener?.onNoteChanged()
    }

    private fun applyViewport(v: Viewport) {
        if (v == viewport) return
        viewport = v
        listener?.onViewportChanged()
    }

    companion object {
        const val DEFAULT_WIDTH = 2.5f
        const val FIT_MARGIN_DP = 24f

        /** How far [duplicateSelection] offsets the copies, right and down, canvas units. */
        const val DUPLICATE_OFFSET = 16f

        /** A new empty note. */
        fun newNote(page: PageSetup = PageSetup.Infinite): Note {
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            return Note(id = Element.newId(), created = now, modified = now, page = page)
        }
    }
}

/** What `LowLatencyCanvasView`'s single buffer holds while a stroke is drawn. */
enum class FrontBufferContent {
    /** The visible note, re-rendered on the first stroke after any change, then the ink: as the ink ADR has it. */
    SCENE,

    /** Only the ink, on a transparent buffer over the canvas view; nothing to re-render after a pan. */
    INK_ONLY,
}
