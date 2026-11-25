package app.screenscratch.core.edit

import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Arrowhead
import app.screenscratch.core.model.Line
import app.screenscratch.core.model.Point
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Shape
import app.screenscratch.core.model.ShapeKind
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import app.screenscratch.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    private fun stroke(id: String, vararg xy: Float) = Stroke(
        id, List(xy.size / 2) { StrokePoint(xy[it * 2], xy[it * 2 + 1], 0.5f, timeMs = it * 4L) }, Argb.BLACK, 2f,
    )

    // A square loop from (0, 0) to (100, 100).
    private val loop = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)

    @Test
    fun pointInPolygonByEvenOdd() {
        assertTrue(PlaneMath.insideEvenOdd(loop, 4, 50f, 50f))
        assertFalse(PlaneMath.insideEvenOdd(loop, 4, 150f, 50f))
        // A concave "C": the notch is outside.
        val c = floatArrayOf(0f, 0f, 100f, 0f, 100f, 30f, 30f, 30f, 30f, 70f, 100f, 70f, 100f, 100f, 0f, 100f)
        assertTrue(PlaneMath.insideEvenOdd(c, 8, 10f, 50f))
        assertFalse(PlaneMath.insideEvenOdd(c, 8, 60f, 50f))
        // A self-crossing figure eight's lobes are inside, as drawn.
        val eight = floatArrayOf(0f, 0f, 100f, 100f, 100f, 0f, 0f, 100f)
        assertTrue(PlaneMath.insideEvenOdd(eight, 4, 10f, 50f))
        assertFalse(PlaneMath.insideEvenOdd(eight, 4, 50f, 10f))
    }

    @Test
    fun lassoSelectsWhatItEnclosesOrTouches() {
        val inside = stroke("in", 20f, 20f, 40f, 40f)
        val crossing = stroke("cross", 90f, 50f, 130f, 50f)
        val outside = stroke("out", 200f, 200f, 220f, 220f)
        // Its bounds overlap the loop's, but its ink stays clear of it.
        val nearMiss = stroke("near", 101.5f, -50f, 101.5f, 150f)
        val ids = Selection.lasso(listOf(inside, crossing, outside, nearMiss), loop)
        assertEquals(listOf("in", "cross"), ids)
    }

    @Test
    fun lassoInsideABoxDoesNotSelectTheBox() {
        val box = Shape("box", ShapeKind.RECTANGLE, Rect(-50f, -50f, 200f, 200f), Argb.BLACK, 2f)
        val note = TextBox("t", Rect(-50f, -50f, 200f, 200f), "hi", 16f, Argb.BLACK)
        val word = stroke("w", 30f, 50f, 70f, 50f)
        assertEquals(listOf("w"), Selection.lasso(listOf(box, note, word), loop))
    }

    @Test
    fun ellipseIsTestedByItsOutlineNotItsFrame() {
        val oval = Shape("o", ShapeKind.ELLIPSE, Rect(0f, 0f, 200f, 100f), Argb.BLACK, 2f)
        // A small loop in the frame's corner, outside the ellipse.
        val corner = floatArrayOf(2f, 2f, 12f, 2f, 12f, 12f, 2f, 12f)
        assertTrue(Selection.lasso(listOf(oval), corner).isEmpty())
        // One around the ellipse's top edge.
        val edge = floatArrayOf(90f, -5f, 110f, -5f, 110f, 5f, 90f, 5f)
        assertEquals(listOf("o"), Selection.lasso(listOf(oval), edge))
    }

    @Test
    fun rectangleSelectionUsesTheSameRule() {
        val a = stroke("a", 10f, 10f, 20f, 20f)
        val b = stroke("b", 300f, 300f, 310f, 310f)
        assertEquals(listOf("a"), Selection.rectangle(listOf(a, b), Rect(0f, 0f, 50f, 50f)))
    }

    @Test
    fun tapPicksTheTopmostElementUnderIt() {
        // A square outline as the stroke's "outline" stand-in.
        val s = stroke("s", 0f, 50f, 100f, 50f)
        val outline = { _: Stroke -> floatArrayOf(0f, 48f, 100f, 48f, 100f, 52f, 0f, 52f) }
        val open = Shape("open", ShapeKind.RECTANGLE, Rect(20f, 20f, 60f, 60f), Argb.BLACK, 2f)
        val filled = Shape("filled", ShapeKind.RECTANGLE, Rect(200f, 0f, 60f, 60f), Argb.BLACK, 2f, fill = Argb.BLACK)
        val line = Line("l", listOf(Point(0f, 200f), Point(100f, 300f)), Argb.BLACK, 2f, endArrow = Arrowhead.ARROW)
        val els = listOf(s, open, filled, line)
        assertEquals("s", Selection.pick(els, 50f, 50f, 1f, outline)?.id)
        // Inside an unfilled rectangle, away from both: nothing.
        assertNull(Selection.pick(els, 40f, 35f, 1f, outline))
        // On its edge.
        assertEquals("open", Selection.pick(els, 20.5f, 35f, 1f, outline)?.id)
        assertEquals("filled", Selection.pick(els, 230f, 30f, 1f, outline)?.id)
        assertEquals("l", Selection.pick(els, 50f, 251f, 1f, outline)?.id)
        assertNull(Selection.pick(els, 50f, 260f, 1f, outline))
    }
}
