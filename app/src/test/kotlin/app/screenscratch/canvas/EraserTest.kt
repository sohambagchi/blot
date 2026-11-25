package app.screenscratch.canvas

import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Shape
import app.screenscratch.core.model.ShapeKind
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserTest {
    // A 10 × 10 square, clockwise in y-down space.
    private val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)

    @Test
    fun capsuleInsideThePolygonTouches() {
        assertTrue(Eraser.touches(square, 4, 5f, 5f, 5f, 5f, 0.1f))
    }

    @Test
    fun capsuleNearAnEdgeTouchesWithinItsRadius() {
        assertTrue(Eraser.touches(square, 4, 12f, 5f, 12f, 5f, 2.01f))
        assertFalse(Eraser.touches(square, 4, 12f, 5f, 12f, 5f, 1.99f))
    }

    @Test
    fun segmentCrossingThePolygonTouchesEvenWithEndsOutside() {
        assertTrue(Eraser.touches(square, 4, -5f, 5f, 15f, 5f, 0.01f))
        assertFalse(Eraser.touches(square, 4, -5f, -5f, 15f, -5f, 1f))
    }

    @Test
    fun windingCountsAsPathFillsIt() {
        assertEquals(0, Eraser.winding(square, 4, 20f, 5f))
        assertTrue(Eraser.winding(square, 4, 5f, 5f) != 0)
        // Counter-clockwise too.
        val ccw = floatArrayOf(0f, 0f, 0f, 10f, 10f, 10f, 10f, 0f)
        assertTrue(Eraser.winding(ccw, 4, 5f, 5f) != 0)
    }

    @Test
    fun distances() {
        assertEquals(4f, Eraser.segmentPointDistanceSq(0f, 0f, 10f, 0f, 5f, 2f), 1e-5f)
        assertEquals(8f, Eraser.segmentPointDistanceSq(0f, 0f, 10f, 0f, 12f, 2f), 1e-5f)
        assertEquals(0f, Eraser.segmentSegmentDistanceSq(0f, 0f, 10f, 10f, 0f, 10f, 10f, 0f), 1e-5f)
        assertEquals(9f, Eraser.segmentSegmentDistanceSq(0f, 0f, 10f, 0f, 0f, 3f, 10f, 3f), 1e-5f)
    }

    private fun stroke(id: String, vararg xy: Float) = Stroke(
        id,
        List(xy.size / 2) { StrokePoint(xy[it * 2], xy[it * 2 + 1], 0.5f, timeMs = it * 4L) },
        Argb.BLACK,
        width = 2f,
    )

    @Test
    fun hitsAreTestedAgainstOutlinesNotBounds() {
        val outlines = OutlineCache()
        // A diagonal stroke: its bounds' corners are far from the ink.
        val diagonal = stroke("d", *FloatArray(42) { if (it % 2 == 0) it / 2 * 5f else it / 2 * 5f })
        val corner = diagonal.bounds.right - 3f to diagonal.bounds.top + 3f
        assertTrue(diagonal.bounds.intersects(Rect(corner.first, corner.second, 0f, 0f)))
        assertTrue(Eraser.hits(listOf(diagonal), outlines::outline, corner.first, corner.second, corner.first, corner.second, 1f).isEmpty())
        // Crossing the line hits it.
        assertEquals(listOf("d"), Eraser.hits(listOf(diagonal), outlines::outline, 40f, 60f, 60f, 40f, 1f))
    }

    @Test
    fun onlyStrokesAreErasedInStackingOrder() {
        val outlines = OutlineCache()
        val a = stroke("a", 0f, 50f, 100f, 50f)
        val shape = Shape("s", ShapeKind.RECTANGLE, Rect(0f, 0f, 100f, 100f), Argb.BLACK, 2f)
        val b = stroke("b", 0f, 55f, 100f, 55f)
        val c = stroke("c", 0f, 500f, 100f, 500f)
        val hits = Eraser.hits(listOf(a, shape, b, c), outlines::outline, 50f, 0f, 50f, 100f, 2f)
        assertEquals(listOf("a", "b"), hits)
    }
}
