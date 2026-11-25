package app.screenscratch.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import app.screenscratch.canvas.CanvasController
import app.screenscratch.canvas.pagesBounds
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.Orientation
import app.screenscratch.core.model.PageSetup
import app.screenscratch.core.model.Paper
import app.screenscratch.core.model.Rect
import app.screenscratch.ui.editor.AutoCollapse
import app.screenscratch.ui.editor.EditorUiState
import app.screenscratch.ui.editor.EraserMode
import app.screenscratch.ui.editor.ExportArea
import app.screenscratch.ui.editor.InMemoryEditor
import app.screenscratch.ui.editor.LineKind
import app.screenscratch.ui.editor.PenMode
import app.screenscratch.ui.editor.SelectMode
import app.screenscratch.ui.editor.ShapeKind
import app.screenscratch.ui.editor.Tool
import app.screenscratch.ui.library.PageKind
import kotlin.math.roundToInt
import app.screenscratch.canvas.LineArrows
import app.screenscratch.canvas.Tool as CanvasTool
import app.screenscratch.canvas.EraserMode as CanvasEraserMode
import app.screenscratch.canvas.SelectMode as CanvasSelectMode
import app.screenscratch.core.model.ShapeKind as ModelShape

/**
 * The editor's state holder: the chrome's [app.screenscratch.ui.editor.EditorActions] drive
 * [controller], and [uiState] is the chrome's state with the canvas's live values (tool after a
 * side-button cycle, undo and redo, zoom, page setup) laid over it. Main thread only.
 *
 * @param onExport runs an export of the current note with the dialog's options.
 * @param onPenOnlyChanged the "Pen only" toggle changed; it is remembered per device.
 * @param screenPageSize the size in dp of a new screen-sized page.
 */
class CanvasEditor(
    val controller: CanvasController,
    initial: EditorUiState,
    autoCollapse: AutoCollapse = AutoCollapse(),
    onBack: () -> Unit = {},
    onExport: (app.screenscratch.ui.editor.ExportOptions, ExportTarget) -> Unit = { _, _ -> },
    private val onPenOnlyChanged: (Boolean) -> Unit = {},
    private val onExportOpened: () -> Unit = {},
    private val screenPageSize: () -> Pair<Float, Float> = { 1280f to 800f },
) : InMemoryEditor(initial, autoCollapse, onBack, { onExport(it, ExportTarget.Folder) }, { onExport(it, ExportTarget.Share) }) {

    private var page by mutableStateOf(controller.note.value.page)

    /** The canvas view's size in pixels, for the visible-area export size. */
    var canvasSizePx: IntSize = IntSize.Zero

    init {
        controller.color = state.color
        controller.width = state.strokeWidth
        controller.penOnly = state.penOnly
        applyTool()
    }

    /** What the chrome draws. Reads Compose state, so composition follows the canvas. */
    val uiState: EditorUiState
        get() {
            val s = state
            val (tool, penMode) = uiTool(controller.tool, s.tool, s.penMode)
            val p = page
            return s.copy(
                tool = tool,
                penMode = penMode,
                canUndo = controller.canUndo,
                hasSelection = controller.hasSelection,
                canRedo = controller.canRedo,
                zoomPercent = (controller.viewport.zoom * 100).roundToInt(),
                pageLabel = pageLabel(p),
                pageKind = pageKind(p),
                pageCount = (p as? PageSetup.Pages)?.count ?: 1,
                pageLandscape = (p as? PageSetup.Pages)?.orientation == Orientation.LANDSCAPE,
            )
        }

    /** The note changed (an edit, undo or redo); call for every new value of [CanvasController.note]. */
    fun noteChanged(note: Note) {
        page = note.page
    }

    fun showMessage(text: String) {
        state = state.copy(message = text)
    }

    fun setExportPreview(preview: ImageBitmap?) {
        state = state.copy(exportInfo = state.exportInfo.copy(preview = preview))
    }

    private fun applyTool() {
        val s = state
        controller.eraserMode = if (s.eraserMode == EraserMode.PartOfStroke) CanvasEraserMode.PARTIAL else CanvasEraserMode.WHOLE_STROKE
        controller.selectMode = if (s.selectMode == SelectMode.Rectangle) CanvasSelectMode.RECTANGLE else CanvasSelectMode.LASSO
        controller.shapeKind = modelShape(s.shape)
        controller.lineArrows = lineArrows(s.line)
        controller.snapAngles = s.snapAngles
        controller.tool = canvasTool(s.tool, s.penMode)
    }

    override fun selectTool(tool: Tool) {
        super.selectTool(tool)
        applyTool()
    }

    override fun setPenMode(mode: PenMode) {
        super.setPenMode(mode)
        applyTool()
    }

    override fun setEraserMode(mode: EraserMode) {
        super.setEraserMode(mode)
        applyTool()
    }

    override fun setSelectMode(mode: SelectMode) {
        super.setSelectMode(mode)
        applyTool()
    }

    override fun setShape(kind: ShapeKind) {
        super.setShape(kind)
        applyTool()
        if (kind != ShapeKind.Rectangle && kind != ShapeKind.Ellipse) showMessage("Only rectangles and ellipses for now: drawing a rectangle")
    }

    override fun setLine(kind: LineKind) {
        super.setLine(kind)
        applyTool()
        if (kind == LineKind.Elbow || kind == LineKind.Curve) showMessage("Elbow and curved lines aren't available yet: drawing a straight line")
    }

    override fun setSnapAngles(on: Boolean) {
        super.setSnapAngles(on)
        controller.snapAngles = on
    }

    override fun setColor(color: Argb) {
        super.setColor(color)
        controller.color = color
    }

    override fun setStrokeWidth(width: Float) {
        super.setStrokeWidth(width)
        controller.width = width
    }

    override fun setPenOnly(on: Boolean) {
        super.setPenOnly(on)
        controller.penOnly = on
        onPenOnlyChanged(on)
    }

    override fun undo() = controller.undo()

    override fun redo() = controller.redo()

    override fun resetZoom() = controller.zoomTo(1f)

    override fun setExportOpen(open: Boolean) {
        super.setExportOpen(open)
        if (!open) return
        state = state.copy(exportInfo = state.exportInfo.copy(areaSizes = areaSizes()))
        onExportOpened()
    }

    override fun setPageKind(kind: PageKind) {
        val current = controller.note.value.page as? PageSetup.Pages
        val count = current?.count ?: 1
        val orientation = current?.orientation ?: Orientation.PORTRAIT
        applyPage(pageSetup(kind, orientation, count, screenPageSize()))
    }

    override fun setPageLandscape(landscape: Boolean) {
        val p = controller.note.value.page as? PageSetup.Pages ?: return
        if (p.paper == Paper.SCREEN || (p.orientation == Orientation.LANDSCAPE) == landscape) return
        applyPage(p.copy(width = p.height, height = p.width))
    }

    override fun addPage() {
        val p = controller.note.value.page as? PageSetup.Pages ?: return
        applyPage(p.copy(count = p.count + 1))
    }

    override fun removePage() {
        val p = controller.note.value.page as? PageSetup.Pages ?: return
        if (p.count > 1) applyPage(p.copy(count = p.count - 1))
    }

    private fun applyPage(p: PageSetup) {
        controller.setPage(p)
        page = controller.note.value.page
    }

    /** The canvas rectangle on screen, or null before the canvas has a size. */
    fun visibleArea(): Rect? =
        if (canvasSizePx.width > 0) controller.viewport.visibleRect(canvasSizePx.width, canvasSizePx.height) else null

    private fun areaSizes(): Map<ExportArea, IntSize> {
        val n = controller.note.value
        val out = HashMap<ExportArea, IntSize>()
        val whole = when (val p = n.page) {
            is PageSetup.Pages -> pagesBounds(p)
            PageSetup.Infinite -> n.elements.map { it.bounds }.reduceOrNull(Rect::union)
        }
        whole?.let { out[ExportArea.WholeCanvas] = IntSize(it.width.roundToInt(), it.height.roundToInt()) }
        if (canvasSizePx.width > 0) {
            val v = controller.viewport.visibleRect(canvasSizePx.width, canvasSizePx.height)
            out[ExportArea.VisibleArea] = IntSize(v.width.roundToInt(), v.height.roundToInt())
        }
        return out
    }

    companion object {
        /** The canvas tool for a toolbar choice. Image import isn't built yet, so the image tool selects. */
        fun canvasTool(tool: Tool, penMode: PenMode): CanvasTool = when (tool) {
            Tool.Pen -> when (penMode) {
                PenMode.Pen -> CanvasTool.PEN
                PenMode.Hand -> CanvasTool.PAN
                PenMode.Pointer -> CanvasTool.SELECT
            }
            Tool.Eraser -> CanvasTool.ERASER
            Tool.Shape -> CanvasTool.SHAPE
            Tool.Line -> CanvasTool.LINE
            Tool.Text -> CanvasTool.TEXT
            Tool.Select, Tool.Image -> CanvasTool.SELECT
        }

        /** The note's shape for a toolbar shape; the note has only rectangles and ellipses, so the rest draw rectangles. */
        fun modelShape(kind: ShapeKind): ModelShape = if (kind == ShapeKind.Ellipse) ModelShape.ELLIPSE else ModelShape.RECTANGLE

        /** Elbow and curved lines aren't built, so they draw straight lines. */
        fun lineArrows(kind: LineKind): LineArrows = when (kind) {
            LineKind.Arrow -> LineArrows.END
            LineKind.DoubleArrow -> LineArrows.BOTH
            LineKind.Line, LineKind.Elbow, LineKind.Curve -> LineArrows.NONE
        }

        /**
         * The toolbar's tool and pen mode for the canvas's [canvas] tool: the chosen ones while
         * they still map to it, otherwise (after the pen's cycle button) the pen in that mode.
         */
        fun uiTool(canvas: CanvasTool, tool: Tool, penMode: PenMode): Pair<Tool, PenMode> =
            if (canvasTool(tool, penMode) == canvas) tool to penMode
            else when (canvas) {
                CanvasTool.PEN -> Tool.Pen to PenMode.Pen
                CanvasTool.PAN -> Tool.Pen to PenMode.Hand
                CanvasTool.SELECT -> Tool.Pen to PenMode.Pointer
                CanvasTool.ERASER -> Tool.Eraser to penMode
                CanvasTool.SHAPE -> Tool.Shape to penMode
                CanvasTool.LINE -> Tool.Line to penMode
                CanvasTool.TEXT -> Tool.Text to penMode
            }

        fun pageKind(p: PageSetup): PageKind = when (p) {
            PageSetup.Infinite -> PageKind.Infinite
            is PageSetup.Pages -> when (p.paper) {
                Paper.A4 -> PageKind.A4
                Paper.LETTER -> PageKind.Letter
                Paper.SCREEN -> PageKind.Screen
            }
        }

        /** The page chip's text, e.g. "Infinite canvas", "A4" or "Letter · 3 pages". */
        fun pageLabel(p: PageSetup): String {
            if (p !is PageSetup.Pages) return "Infinite canvas"
            val name = when (p.paper) {
                Paper.A4 -> "A4"
                Paper.LETTER -> "Letter"
                Paper.SCREEN -> "Screen page"
            }
            return if (p.count == 1) name else "$name · ${p.count} pages"
        }

        /** The page setup for [kind]; [screen] is the window size in dp for a screen page. */
        fun pageSetup(kind: PageKind, orientation: Orientation = Orientation.PORTRAIT, count: Int = 1, screen: Pair<Float, Float> = 1280f to 800f): PageSetup =
            when (kind) {
                PageKind.Infinite -> PageSetup.Infinite
                PageKind.A4 -> PageSetup.Pages.a4(orientation, count)
                PageKind.Letter -> PageSetup.Pages.letter(orientation, count)
                PageKind.Screen -> PageSetup.Pages.screen(screen.first, screen.second, count)
            }
    }
}
