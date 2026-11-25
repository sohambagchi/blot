package app.screenscratch.canvas

import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import org.junit.Assert.assertEquals
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
}
