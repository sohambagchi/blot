package app.blot.shell

import app.blot.canvas.LineArrows
import app.blot.ui.editor.EraserMode
import app.blot.ui.editor.LineKind
import app.blot.ui.editor.ShapeKind
import app.blot.canvas.CanvasController
import app.blot.core.model.Argb
import app.blot.core.model.Orientation
import app.blot.core.model.PageSetup
import app.blot.core.model.Paper
import app.blot.ui.editor.EditorUiState
import app.blot.ui.editor.ExportOptions
import app.blot.ui.editor.PenMode
import app.blot.ui.editor.SelectMode
import app.blot.ui.editor.SelectionCommand
import app.blot.ui.editor.Tool
import app.blot.ui.library.PageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import app.blot.canvas.Tool as CanvasTool

@RunWith(RobolectricTestRunner::class)
class CanvasEditorTest {
    private val controller = CanvasController(CanvasController.newNote())
    private var penOnlySaved: Boolean? = null
    private val exports = ArrayList<Pair<ExportOptions, ExportTarget>>()
    private val editor = CanvasEditor(
        controller,
        EditorUiState(penOnly = false, strokeWidth = 4f),
        onExport = { o, t -> exports += o to t },
        onPenOnlyChanged = { penOnlySaved = it },
        screenPageSize = { 800f to 1280f },
    )

    @Test
    fun startPushesTheChromeStateToTheCanvas() {
        assertEquals(CanvasTool.PEN, controller.tool)
        assertEquals(4f, controller.width)
        assertFalse(controller.penOnly)
        assertEquals(EditorUiState().color, controller.color)
    }

    @Test
    fun toolbarToolsMapToCanvasTools() {
        editor.setPenMode(PenMode.Hand)
        assertEquals(CanvasTool.PAN, controller.tool)
        editor.setSelectMode(SelectMode.Rectangle)
        assertEquals(CanvasTool.SELECT, controller.tool)
        assertEquals(app.blot.canvas.SelectMode.RECTANGLE, controller.selectMode)
        editor.selectTool(Tool.Eraser)
        assertEquals(CanvasTool.ERASER, controller.tool)
        editor.selectTool(Tool.Shape)
        assertEquals(CanvasTool.SHAPE, controller.tool)
        assertEquals(Tool.Shape, editor.uiState.tool)
        editor.setShape(ShapeKind.Ellipse)
        assertEquals(app.blot.core.model.ShapeKind.ELLIPSE, controller.shapeKind)
        editor.setShape(ShapeKind.Triangle)
        assertEquals(app.blot.core.model.ShapeKind.RECTANGLE, controller.shapeKind)
        editor.selectTool(Tool.Line)
        assertEquals(CanvasTool.LINE, controller.tool)
        editor.setLine(LineKind.DoubleArrow)
        assertEquals(LineArrows.BOTH, controller.lineArrows)
        editor.setLine(LineKind.Line)
        assertEquals(LineArrows.NONE, controller.lineArrows)
        editor.selectTool(Tool.Text)
        assertEquals(CanvasTool.TEXT, controller.tool)
        editor.selectTool(Tool.Eraser)
        editor.setEraserMode(EraserMode.PartOfStroke)
        assertEquals(app.blot.canvas.EraserMode.PARTIAL, controller.eraserMode)
        // The pen keeps its last mode.
        editor.selectTool(Tool.Pen)
        assertEquals(CanvasTool.PAN, controller.tool)
        editor.setPenMode(PenMode.Pen)
        assertEquals(CanvasTool.PEN, controller.tool)
    }

    @Test
    fun pointerButtonShowsThePointerOnTheToolbarUntilAnotherToolIsPicked() {
        editor.setSelectMode(SelectMode.Rectangle)
        editor.selectTool(Tool.Pen)
        controller.switchToPointer()
        assertEquals(Tool.Select, editor.uiState.tool)
        assertEquals(PenMode.Pen, editor.uiState.penMode)
        assertEquals(SelectMode.Rectangle, editor.uiState.selectMode)
        // It stays the pointer; tapping the pen picks the pen again.
        assertEquals(Tool.Select, editor.uiState.tool)
        editor.selectTool(Tool.Pen)
        assertEquals(CanvasTool.PEN, controller.tool)
        assertEquals(Tool.Pen, editor.uiState.tool)
        // From the hand too.
        editor.setPenMode(PenMode.Hand)
        controller.switchToPointer()
        assertEquals(Tool.Select, editor.uiState.tool)
        // A toolbar tool the canvas maps to SELECT stays shown while the canvas agrees.
        editor.selectTool(Tool.Image)
        assertEquals(Tool.Image, editor.uiState.tool)
        controller.switchToPointer()
        assertEquals(Tool.Image, editor.uiState.tool)
    }

    @Test
    fun selectionMenuShowsGroupOrUngroupAndItsCommandsAreOneUndoStepEach() {
        val els = listOf(
            app.blot.core.model.Shape("a", app.blot.core.model.ShapeKind.RECTANGLE, app.blot.core.model.Rect(0f, 0f, 10f, 10f), Argb.BLACK, 1f),
            app.blot.core.model.Shape("b", app.blot.core.model.ShapeKind.RECTANGLE, app.blot.core.model.Rect(5f, 5f, 10f, 10f), Argb.BLACK, 1f),
        )
        controller.load(controller.note.value.copy(elements = els))
        controller.onViewSize(1000, 800, 2f)
        editor.selectTool(Tool.Select)
        controller.select(setOf("a", "b"))
        assertEquals(null, editor.uiState.selectionMenu)
        controller.openSelectionMenu()
        val menu = editor.uiState.selectionMenu!!
        assertTrue(menu.canGroup)
        assertFalse(menu.canUngroup)
        // The anchor is the selection box in dp: 0–15 canvas units at 100% grown by half the outline, padded by 6 dp.
        assertEquals(-6.5f, menu.anchor.left, 0.01f)
        assertEquals(21.5f, menu.anchor.right, 0.01f)

        editor.selectionCommand(SelectionCommand.Group)
        assertEquals(null, editor.uiState.selectionMenu)
        assertTrue(controller.note.value.elements.all { it.group != null })
        controller.clearSelection()
        controller.select(setOf("a"))
        assertEquals(setOf("a", "b"), controller.selection)
        controller.openSelectionMenu()
        assertTrue(editor.uiState.selectionMenu!!.canUngroup)
        assertFalse(editor.uiState.selectionMenu!!.canGroup)
        editor.selectionCommand(SelectionCommand.Ungroup)
        assertTrue(controller.note.value.elements.all { it.group == null })
        controller.undo()
        assertTrue(controller.note.value.elements.all { it.group != null })
        controller.undo()
        assertTrue(controller.note.value.elements.all { it.group == null })

        controller.select(setOf("a"))
        controller.openSelectionMenu()
        assertFalse(editor.uiState.selectionMenu!!.canGroup)
        assertFalse(editor.uiState.selectionMenu!!.canUngroup)
        editor.selectionCommand(SelectionCommand.Duplicate)
        assertEquals(3, controller.note.value.elements.size)
        editor.selectionCommand(SelectionCommand.Delete)
        assertEquals(2, controller.note.value.elements.size)
        controller.undo()
        controller.undo()
        assertEquals(listOf("a", "b"), controller.note.value.elements.map { it.id })
        editor.closeSelectionMenu()
        assertFalse(controller.selectionMenuOpen)
    }

    @Test
    fun colourWidthAndPenOnlyReachTheCanvas() {
        editor.setColor(Argb(0xFFD93F3F.toInt()))
        editor.setStrokeWidth(7f)
        editor.setPenOnly(true)
        assertEquals(Argb(0xFFD93F3F.toInt()), controller.color)
        assertEquals(7f, controller.width)
        assertTrue(controller.penOnly)
        assertEquals(true, penOnlySaved)
    }

    @Test
    fun undoRedoAndZoomFollowTheCanvas() {
        controller.edit { it.copy(page = PageSetup.Pages.a4()) }
        assertTrue(editor.uiState.canUndo)
        editor.undo()
        assertEquals(PageSetup.Infinite, controller.note.value.page)
        assertTrue(editor.uiState.canRedo)
        editor.redo()
        assertEquals(PageSetup.Pages.a4(), controller.note.value.page)

        controller.zoomBy(2f, 0f, 0f)
        assertEquals(200, editor.uiState.zoomPercent)
        editor.resetZoom()
        assertEquals(100, editor.uiState.zoomPercent)
    }

    @Test
    fun titleIsSavedButNotUndone() {
        val created = controller.note.value.modified
        controller.edit { it.copy(page = PageSetup.Pages.a4()) }
        editor.setTitleEditing(true)
        editor.setTitle("  Raft\nnotes ")
        assertEquals("Raft notes", controller.note.value.title)
        assertEquals("Raft notes", editor.uiState.noteTitle)
        assertFalse(editor.uiState.titleEditing)
        assertTrue(controller.note.value.modified >= created)
        // Undo takes back the page change only; the title is not a step.
        editor.undo()
        assertEquals(PageSetup.Infinite, controller.note.value.page)
        assertEquals("Raft notes", controller.note.value.title)
        assertFalse(editor.uiState.canUndo)
        editor.redo()
        assertEquals("Raft notes", controller.note.value.title)
        editor.setTitle("")
        assertEquals("", controller.note.value.title)
        assertEquals("", editor.uiState.noteTitle)
    }

    @Test
    fun cancellingTheTitleEditKeepsTheTitle() {
        editor.setTitle("Raft")
        editor.setTitleEditing(true)
        editor.setTitleEditing(false)
        assertEquals("Raft", controller.note.value.title)
        assertFalse(editor.uiState.titleEditing)
    }

    @Test
    fun pageMenuChangesThePageSetupUndoably() {
        editor.setPageKind(PageKind.A4)
        assertEquals(PageSetup.Pages.a4(), controller.note.value.page)
        assertEquals("A4", editor.uiState.pageLabel)
        editor.addPage()
        editor.addPage()
        assertEquals(3, (controller.note.value.page as PageSetup.Pages).count)
        assertEquals("A4 · 3 pages", editor.uiState.pageLabel)
        editor.removePage()
        assertEquals(2, editor.uiState.pageCount)
        editor.setPageLandscape(true)
        val p = controller.note.value.page as PageSetup.Pages
        assertEquals(Orientation.LANDSCAPE, p.orientation)
        assertEquals(2, p.count)
        assertTrue(editor.uiState.pageLandscape)
        editor.setPageKind(PageKind.Screen)
        assertEquals(PageSetup.Pages(Paper.SCREEN, 800f, 1280f, 2), controller.note.value.page)
        assertEquals(PageKind.Screen, editor.uiState.pageKind)

        editor.undo()
        editor.noteChanged(controller.note.value)
        assertEquals(PageKind.A4, editor.uiState.pageKind)
        editor.setPageKind(PageKind.Infinite)
        assertEquals(PageSetup.Infinite, controller.note.value.page)
        assertEquals("Infinite canvas", editor.uiState.pageLabel)
    }

    @Test
    fun pageChipTogglesTheMenu() {
        editor.openPageSettings()
        assertTrue(editor.uiState.pageMenuOpen)
        editor.openPageSettings()
        assertFalse(editor.uiState.pageMenuOpen)
    }

    @Test
    fun exportActionsGoToTheExporterWithTheirTarget() {
        editor.setExportOpen(true)
        editor.exportToFolder()
        editor.share()
        assertEquals(listOf(ExportTarget.Folder, ExportTarget.Share), exports.map { it.second })
    }

    @Test
    fun messagesShowUntilDismissed() {
        editor.showMessage("Couldn't save")
        assertEquals("Couldn't save", editor.uiState.message)
        editor.dismissMessage()
        assertEquals(null, editor.uiState.message)
    }

    @Test
    fun writingLongEnoughCollapsesTheToolbar() {
        for (t in 0L..16_000L step 500) editor.onWritingActivity(t)
        assertTrue(editor.uiState.toolbarCollapsed)
    }
}
