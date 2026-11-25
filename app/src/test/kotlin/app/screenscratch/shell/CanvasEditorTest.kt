package app.screenscratch.shell

import app.screenscratch.canvas.CanvasController
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Orientation
import app.screenscratch.core.model.PageSetup
import app.screenscratch.core.model.Paper
import app.screenscratch.ui.editor.EditorUiState
import app.screenscratch.ui.editor.ExportOptions
import app.screenscratch.ui.editor.PenMode
import app.screenscratch.ui.editor.Tool
import app.screenscratch.ui.library.PageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import app.screenscratch.canvas.Tool as CanvasTool

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
        editor.setPenMode(PenMode.Pointer)
        assertEquals(CanvasTool.SELECT, controller.tool)
        editor.selectTool(Tool.Eraser)
        assertEquals(CanvasTool.ERASER, controller.tool)
        editor.selectTool(Tool.Shape)
        assertEquals(CanvasTool.SELECT, controller.tool)
        assertEquals(Tool.Shape, editor.uiState.tool)
        // The pen keeps its last mode.
        editor.selectTool(Tool.Pen)
        assertEquals(CanvasTool.SELECT, controller.tool)
        editor.setPenMode(PenMode.Pen)
        assertEquals(CanvasTool.PEN, controller.tool)
    }

    @Test
    fun sideButtonCycleShowsOnTheToolbar() {
        controller.cycleTool()
        assertEquals(Tool.Pen to PenMode.Hand, editor.uiState.let { it.tool to it.penMode })
        controller.cycleTool()
        assertEquals(Tool.Pen to PenMode.Pointer, editor.uiState.let { it.tool to it.penMode })
        controller.cycleTool()
        assertEquals(Tool.Pen to PenMode.Pen, editor.uiState.let { it.tool to it.penMode })
        // A toolbar tool the canvas maps to SELECT stays shown while the canvas agrees.
        editor.selectTool(Tool.Text)
        assertEquals(Tool.Text, editor.uiState.tool)
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
        controller.edit { it.copy(title = "x") }
        assertTrue(editor.uiState.canUndo)
        editor.undo()
        assertEquals("", controller.note.value.title)
        assertTrue(editor.uiState.canRedo)
        editor.redo()
        assertEquals("x", controller.note.value.title)

        controller.zoomBy(2f, 0f, 0f)
        assertEquals(200, editor.uiState.zoomPercent)
        editor.resetZoom()
        assertEquals(100, editor.uiState.zoomPercent)
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
