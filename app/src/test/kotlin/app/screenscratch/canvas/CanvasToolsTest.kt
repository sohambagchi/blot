package app.screenscratch.canvas

import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Shape
import app.screenscratch.core.model.ShapeKind
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The editing tools driven through [CanvasController], as the canvas view drives them, in canvas units at 100%. */
class CanvasToolsTest {
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")

    private fun hStroke(id: String, x0: Float, x1: Float, y: Float, width: Float = 2f) = Stroke(
        id, List(((x1 - x0) / 2).toInt() + 1) { StrokePoint(x0 + it * 2f, y, 0.5f, timeMs = it * 4L) }, Argb.BLACK, width,
    )

    private fun controller(vararg elements: Element) =
        CanvasController(Note("n", created = t0, modified = t0, elements = elements.toList()))

    private val CanvasController.elements get() = note.value.elements
    private val CanvasController.ids get() = elements.map { it.id }

    private fun CanvasController.drag(vararg xy: Float) {
        toolStart(xy[0], xy[1])
        for (i in 1 until xy.size / 2) toolMove(xy[i * 2], xy[i * 2 + 1])
        toolEnd()
    }

    private fun CanvasController.tap(x: Float, y: Float) = drag(x, y, x, y)

    /** Checks that undo goes back to [before] and redo forward to the current elements. */
    private fun CanvasController.assertUndoRedo(before: List<Element>) {
        val after = elements
        assertTrue(canUndo)
        undo()
        assertEquals(before, elements)
        redo()
        assertEquals(after, elements)
    }

    // Partial eraser.

    @Test
    fun partialEraseSplitsAStrokeAndUndoesAsOneStep() {
        val c = controller(hStroke("a", 0f, 200f, 50f), hStroke("b", 0f, 200f, 300f))
        val before = c.elements
        c.eraserMode = EraserMode.PARTIAL
        c.beginErase()
        assertEquals(1, c.eraseAlong(100f, 0f, 100f, 48f, 4f))
        c.eraseAlong(100f, 48f, 100f, 100f, 4f)
        c.endErase()
        val strokes = c.elements.filterIsInstance<Stroke>()
        assertEquals(3, strokes.size)
        // The pieces sit where the stroke was in the stacking order, with new ids; "b" is untouched.
        assertEquals("b", c.ids.last())
        assertTrue(c.ids.take(2).none { it == "a" })
        assertEquals(95f, strokes[0].points.last().x, 1 / 16f)
        assertEquals(105f, strokes[1].points.first().x, 1 / 16f)
        c.assertUndoRedo(before)
    }

    @Test
    fun wholeStrokeEraseIsStillTheDefault() {
        val c = controller(hStroke("a", 0f, 200f, 50f))
        assertEquals(EraserMode.WHOLE_STROKE, c.eraserMode)
        c.beginErase()
        c.eraseAlong(100f, 0f, 100f, 100f, 4f)
        c.endErase()
        assertTrue(c.elements.isEmpty())
    }

    // Selection.

    @Test
    fun lassoSelectsTheStrokesItEncloses() {
        val c = controller(hStroke("in", 20f, 60f, 50f), hStroke("out", 300f, 340f, 50f))
        c.tool = Tool.SELECT
        c.drag(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f, 0f, 1f)
        assertEquals(setOf("in"), c.selection)
        assertEquals(c.elements[0].bounds, c.selectionBounds)
        // Lassoing empty space clears it.
        c.drag(500f, 500f, 600f, 500f, 600f, 600f)
        assertTrue(c.selection.isEmpty())
    }

    @Test
    fun rectangleSelectMode() {
        val c = controller(hStroke("a", 20f, 60f, 50f), hStroke("b", 300f, 340f, 50f))
        c.tool = Tool.SELECT
        c.selectMode = SelectMode.RECTANGLE
        c.drag(250f, 0f, 400f, 100f)
        assertEquals(setOf("b"), c.selection)
    }

    @Test
    fun tapSelectsTheElementUnderThePenAndTapOnNothingClears() {
        val shape = Shape("box", ShapeKind.RECTANGLE, Rect(100f, 100f, 100f, 100f), Argb.BLACK, 2f)
        val c = controller(hStroke("s", 0f, 60f, 20f), shape)
        c.tool = Tool.SELECT
        c.tap(30f, 21f)
        assertEquals(setOf("s"), c.selection)
        c.tap(100f, 150f)
        assertEquals(setOf("box"), c.selection)
        c.tap(400f, 400f)
        assertTrue(c.selection.isEmpty())
    }

    @Test
    fun draggingInsideTheSelectionMovesItAsOneUndoStep() {
        val c = controller(hStroke("s", 0f, 60f, 20f))
        val before = c.elements
        c.tool = Tool.SELECT
        c.select(setOf("s"))
        c.drag(30f, 20f, 40f, 30f, 55f, 45f)
        val moved = c.elements[0] as Stroke
        assertEquals("s", moved.id)
        assertEquals(25f, moved.points[0].x)
        assertEquals(45f, moved.points[0].y)
        assertEquals(2f, moved.width)
        assertEquals(setOf("s"), c.selection)
        c.assertUndoRedo(before)
    }

    @Test
    fun theMoveIsOnlyAPreviewUntilPenUp() {
        val c = controller(hStroke("s", 0f, 60f, 20f))
        c.tool = Tool.SELECT
        c.select(setOf("s"))
        c.toolStart(30f, 20f)
        c.toolMove(50f, 20f)
        assertEquals(0f, (c.elements[0] as Stroke).points[0].x)
        val p = c.scenePreview()!!
        assertEquals(setOf("s"), p.transformed)
        assertEquals(20f, p.transform.dx)
        c.toolCancel()
        assertNull(c.scenePreview())
        assertFalse(c.canUndo)
    }

    @Test
    fun cornerHandleScalesUniformlyAboutTheOppositeCorner() {
        // Bounds of the stroke: (−2, 18)–(62, 22) grown by its width 2.
        val c = controller(hStroke("s", 0f, 60f, 20f))
        val before = c.elements
        c.tool = Tool.SELECT
        c.select(setOf("s"))
        val b = c.selectionBounds!!
        val pad = ToolGestures.SELECTION_PAD_DP
        // Grab the bottom-right handle and drag it out along the diagonal to double the box.
        val ax = b.left - pad
        val ay = b.top - pad
        val cx = b.right + pad
        val cy = b.bottom + pad
        c.drag(cx, cy, ax + (cx - ax) * 2, ay + (cy - ay) * 2)
        val s = c.elements[0] as Stroke
        assertEquals(4f, s.width, 1e-3f)
        assertEquals(ax + (0f - ax) * 2, s.points.first().x, 1 / 16f)
        assertEquals(ax + (60f - ax) * 2, s.points.last().x, 1 / 16f)
        assertEquals(ay + (20f - ay) * 2, s.points.first().y, 1 / 16f)
        c.assertUndoRedo(before)
    }

    @Test
    fun deleteDuplicateAndStackingThroughTheController() {
        val a = Shape("a", ShapeKind.RECTANGLE, Rect(0f, 0f, 50f, 50f), Argb.BLACK, 2f)
        val b = Shape("b", ShapeKind.RECTANGLE, Rect(20f, 20f, 50f, 50f), Argb.BLACK, 2f)
        val d = Shape("d", ShapeKind.RECTANGLE, Rect(40f, 40f, 50f, 50f), Argb.BLACK, 2f)
        val c = controller(a, b, d)
        val start = c.elements

        c.select(setOf("a"))
        c.bringToFront()
        assertEquals(listOf("b", "d", "a"), c.ids)
        c.sendToBack()
        assertEquals(listOf("a", "b", "d"), c.ids)
        c.bringForward()
        assertEquals(listOf("b", "a", "d"), c.ids)
        c.sendBackward()
        assertEquals(listOf("a", "b", "d"), c.ids)
        // Each command was a step of its own.
        repeat(4) { c.undo() }
        assertEquals(start, c.elements)
        repeat(4) { c.redo() }

        c.select(setOf("b"))
        c.duplicateSelection()
        assertEquals(4, c.elements.size)
        val copy = c.elements.last() as Shape
        assertEquals(setOf(copy.id), c.selection)
        assertEquals(Rect(36f, 36f, 50f, 50f), copy.frame)
        c.assertUndoRedo(start)

        c.select(setOf(copy.id))
        c.deleteSelection()
        assertEquals(listOf("a", "b", "d"), c.ids)
        assertTrue(c.selection.isEmpty())
        c.undo()
        assertEquals(4, c.elements.size)
    }

    @Test
    fun leavingTheSelectToolClearsTheSelection() {
        val c = controller(hStroke("s", 0f, 60f, 20f))
        c.tool = Tool.SELECT
        c.select(setOf("s", "missing"))
        assertEquals(setOf("s"), c.selection)
        c.cycleTool()
        assertEquals(Tool.PEN, c.tool)
        assertTrue(c.selection.isEmpty())
    }
}
