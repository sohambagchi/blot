package app.screenscratch.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.PageSetup
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Stroke
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

    /** What pen drags do; the pen's cycle button changes it too. */
    var tool by mutableStateOf(Tool.PEN)

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
        val n = history.undo(_note.value) ?: return
        setNote(n)
    }

    fun redo() {
        val n = history.redo(_note.value) ?: return
        setNote(n)
    }

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

    /** Erases strokes touched by the canvas-unit capsule from a to b with radius [r]. */
    internal fun eraseAlong(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Int {
        val n = _note.value
        val hit = Eraser.hits(n.elements, outlines::outline, ax, ay, bx, by, r)
        if (hit.isEmpty()) return 0
        val ids = hit.toHashSet()
        setNote(n.copy(elements = n.elements.filterNot { it.id in ids }))
        return hit.size
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
