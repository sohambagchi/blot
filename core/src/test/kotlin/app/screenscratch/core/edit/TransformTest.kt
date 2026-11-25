package app.screenscratch.core.edit

import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Line
import app.screenscratch.core.model.Point
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Shape
import app.screenscratch.core.model.ShapeKind
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import app.screenscratch.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

class TransformTest {
    private val stroke = Stroke(
        "s", listOf(StrokePoint(10f, 10f, 0.4f, 0.1f, 0.2f, 0), StrokePoint(20f, 30f, 0.6f, timeMs = 8)), Argb.BLACK, 2f,
    )

    @Test
    fun moveShiftsPointsAndKeepsEverythingElse() {
        val moved = stroke.transformed(Similarity.translate(5f, -3f)) as Stroke
        assertEquals("s", moved.id)
        assertEquals(listOf(15f, 25f), moved.points.map { it.x })
        assertEquals(listOf(7f, 27f), moved.points.map { it.y })
        assertEquals(2f, moved.width)
        assertEquals(stroke.points.map { it.pressure }, moved.points.map { it.pressure })
        assertEquals(0.1f, moved.points[0].tilt)
        assertEquals(8L, moved.points[1].timeMs)
    }

    @Test
    fun scaleAboutAnOriginScalesPointsAndWidth() {
        val t = Similarity.scale(2f, 10f, 10f)
        val s = stroke.transformed(t) as Stroke
        assertEquals(listOf(10f, 30f), s.points.map { it.x })
        assertEquals(listOf(10f, 50f), s.points.map { it.y })
        assertEquals(4f, s.width)
        // The bounds scale about the origin too: samples' box grown by the scaled width.
        assertEquals(Rect.fromEdges(6f, 6f, 34f, 54f), s.bounds)
    }

    @Test
    fun scaledStrokePointsSnapToTheFileGrid() {
        val s = stroke.transformed(Similarity.scale(1.01f, 0f, 0f)) as Stroke
        for (p in s.points) {
            assertEquals(p.x * 16, (p.x * 16).roundToInt().toFloat(), 0f)
            assertEquals(p.y * 16, (p.y * 16).roundToInt().toFloat(), 0f)
        }
    }

    @Test
    fun otherElementsScaleFramesWidthsAndFontSizes() {
        val t = Similarity(dx = 1f, dy = 2f, scale = 0.5f, originX = 0f, originY = 0f)
        val shape = Shape("r", ShapeKind.RECTANGLE, Rect(10f, 20f, 40f, 60f), Argb.BLACK, 4f, rotation = 0.3f).transformed(t) as Shape
        assertEquals(Rect(6f, 12f, 20f, 30f), shape.frame)
        assertEquals(2f, shape.width)
        assertEquals(0.3f, shape.rotation)
        val line = Line("l", listOf(Point(0f, 0f), Point(10f, 10f)), Argb.BLACK, 3f).transformed(t) as Line
        assertEquals(listOf(Point(1f, 2f), Point(6f, 7f)), line.points)
        assertEquals(1.5f, line.width)
        val text = TextBox("t", Rect(0f, 0f, 100f, 40f), "x", 20f, Argb.BLACK).transformed(t) as TextBox
        assertEquals(Rect(1f, 2f, 50f, 20f), text.frame)
        assertEquals(10f, text.fontSize)
    }

    @Test
    fun identityReturnsTheSameObject() {
        assertSame(stroke, stroke.transformed(Similarity.IDENTITY))
    }

    @Test
    fun cornerScaleProjectsOnTheDiagonalAndClamps() {
        // Box (0, 0)–(100, 50), dragging the bottom-right corner.
        assertEquals(2f, SelectionEdits.cornerScale(0f, 0f, 100f, 50f, 200f, 100f, 0.1f), 1e-5f)
        // Off the diagonal: only the component along it counts.
        assertEquals(1f, SelectionEdits.cornerScale(0f, 0f, 100f, 50f, 100f + 10f, 50f - 20f, 0.1f), 1e-5f)
        assertEquals(0.1f, SelectionEdits.cornerScale(0f, 0f, 100f, 50f, -100f, -50f, 0.1f), 1e-5f)
    }

    @Test
    fun duplicateAddsMovedCopiesOnTopWithNewIds() {
        val other = Shape("o", ShapeKind.ELLIPSE, Rect(0f, 0f, 10f, 10f), Argb.BLACK, 1f)
        val (out, ids) = SelectionEdits.duplicate(listOf(stroke, other), setOf("s"), 16f, 16f) { "copy" }
        assertEquals(listOf("s", "o", "copy"), out.map { it.id })
        assertEquals(listOf("copy"), ids)
        assertEquals(26f, (out[2] as Stroke).points[0].x)
        assertNotEquals(stroke, out[2])
    }

    @Test
    fun angleSnapsToFifteenDegreesAndProjects() {
        // 50° snaps to 45°; the point is the projection onto that ray.
        val a = Math.toRadians(50.0)
        val p = Snap.angle(0f, 0f, (100 * kotlin.math.cos(a)).toFloat(), (100 * kotlin.math.sin(a)).toFloat())
        assertEquals(45.0, Math.toDegrees(atan2(p.y.toDouble(), p.x.toDouble())), 1e-4)
        assertEquals(100 * kotlin.math.cos(Math.toRadians(5.0)), hypot(p.x, p.y).toDouble(), 1e-3)
        // Near-horizontal and near-vertical become exactly so.
        assertEquals(Point(100f, 5f), Snap.angle(0f, 5f, 100f, 7f))
        assertEquals(Point(3f, -80f), Snap.angle(3f, 0f, 5f, -80f))
        // Every result is a multiple of 15°.
        for (deg in 0 until 360 step 7) {
            val r = Math.toRadians(deg.toDouble())
            val q = Snap.angle(10f, 10f, 10f + (50 * kotlin.math.cos(r)).toFloat(), 10f + (50 * kotlin.math.sin(r)).toFloat())
            val got = Math.toDegrees(atan2((q.y - 10f).toDouble(), (q.x - 10f).toDouble()))
            val k = got / 15
            assertEquals("at $deg°", k.roundToInt().toDouble(), k, 1e-3)
        }
        assertEquals(PI / 12, Snap.ANGLE_STEP, 0.0)
    }

    @Test
    fun squareDragUsesTheLongerSideTowardsThePen() {
        assertEquals(Rect(10f, 10f, 40f, 40f), Snap.dragRect(10f, 10f, 50f, 30f, square = true))
        assertEquals(Rect(-30f, 10f, 40f, 40f), Snap.dragRect(10f, 10f, -30f, 20f, square = true))
        assertEquals(Rect(10f, 10f, 40f, 20f), Snap.dragRect(10f, 10f, 50f, 30f, square = false))
    }
}
