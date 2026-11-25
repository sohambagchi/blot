package app.blot.core.edit

import app.blot.core.model.Argb
import app.blot.core.model.AssetRef
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class RotationTest {
    private val quarter = (PI / 2).toFloat()
    private val asset = AssetRef("ab".repeat(32), "png")

    private fun assertRect(expected: Rect, actual: Rect, tol: Float = 1e-3f) {
        assertEquals("x", expected.x, actual.x, tol)
        assertEquals("y", expected.y, actual.y, tol)
        assertEquals("width", expected.width, actual.width, tol)
        assertEquals("height", expected.height, actual.height, tol)
    }

    @Test
    fun similarityTurnsPointsClockwiseOnScreenAboutTheOrigin() {
        val t = Similarity.rotate(quarter, 10f, 10f)
        // y grows down, so a clockwise quarter turn takes "right of the origin" to "below it".
        assertEquals(10f, t.mapX(20f, 10f), 1e-4f)
        assertEquals(20f, t.mapY(20f, 10f), 1e-4f)
        assertEquals(0f, t.mapX(10f, 20f), 1e-4f)
        assertEquals(10f, t.mapY(10f, 20f), 1e-4f)
    }

    @Test
    fun scaleAndTurnCompose() {
        val t = Similarity(dx = 1f, dy = 2f, scale = 2f, originX = 0f, originY = 0f, rotation = quarter)
        assertEquals(1f, t.mapX(5f, 0f), 1e-4f)
        assertEquals(12f, t.mapY(5f, 0f), 1e-4f)
    }

    @Test
    fun strokesAndLinesTurnTheirPoints() {
        val s = Stroke("s", listOf(StrokePoint(20f, 10f, 0.5f, orientation = 0.2f, timeMs = 0), StrokePoint(30f, 10f, 0.5f, timeMs = 4)), Argb.BLACK, 2f)
        val t = Similarity.rotate(quarter, 10f, 10f)
        val turned = s.transformed(t) as Stroke
        assertEquals(listOf(10f, 10f), turned.points.map { it.x })
        assertEquals(listOf(20f, 30f), turned.points.map { it.y })
        assertEquals(0.2f + quarter, turned.points[0].orientation, 1e-5f)
        assertEquals(2f, turned.width)
        val line = Line("l", listOf(Point(20f, 10f), Point(10f, 0f)), Argb.BLACK, 1f).transformed(t) as Line
        assertEquals(10f, line.points[0].x, 1e-4f)
        assertEquals(20f, line.points[0].y, 1e-4f)
        assertEquals(20f, line.points[1].x, 1e-4f)
        assertEquals(10f, line.points[1].y, 1e-4f)
    }

    @Test
    fun framedElementsMoveTheirCentreAndAddTheTurn() {
        val t = Similarity.rotate(quarter, 0f, 0f)
        // Frame centred on (50, 0): the centre turns to (0, 50); the size stays.
        val shape = Shape("r", ShapeKind.RECTANGLE, Rect(40f, -5f, 20f, 10f), Argb.BLACK, 1f, rotation = 0.1f).transformed(t) as Shape
        assertRect(Rect(-10f, 45f, 20f, 10f), shape.frame)
        assertEquals(0.1f + quarter, shape.rotation, 1e-5f)
        val text = TextBox("t", Rect(40f, -5f, 20f, 10f), "x", 12f, Argb.BLACK).transformed(t) as TextBox
        assertRect(Rect(-10f, 45f, 20f, 10f), text.frame)
        assertEquals(quarter, text.rotation, 1e-5f)
        assertEquals(12f, text.fontSize)
        val img = Image("i", asset, Rect(40f, -5f, 20f, 10f)).transformed(Similarity(scale = 2f, rotation = quarter)) as Image
        assertRect(Rect(-20f, 90f, 40f, 20f), img.frame)
        assertEquals(quarter, img.rotation, 1e-5f)
    }

    @Test
    fun rotationWrapsIntoMinusPiToPi() {
        val text = TextBox("t", Rect(0f, 0f, 10f, 10f), "x", 12f, Argb.BLACK, rotation = 3f)
        val turned = text.transformed(Similarity.rotate(1f, 5f, 5f)) as TextBox
        assertEquals((4.0 - 2 * PI).toFloat(), turned.rotation, 1e-5f)
        assertEquals(PI.toFloat(), Angles.normalize(-PI.toFloat()), 1e-6f)
        assertEquals(0f, Angles.normalize((4 * PI).toFloat()), 1e-5f)
    }

    @Test
    fun boundsOfRotatedTextImageAndShape() {
        val frame = Rect(0f, 0f, 100f, 20f)
        // A quarter turn about the centre (50, 10) stands the box up: 20 × 100.
        val upright = Rect(40f, -40f, 20f, 100f)
        assertRect(upright, TextBox("t", frame, "x", 12f, Argb.BLACK, rotation = quarter).bounds)
        assertRect(upright, Image("i", asset, frame, rotation = quarter).bounds)
        // 45°: each half extent is (50 + 10) / √2.
        val h = 60f / sqrt(2f)
        assertRect(Rect.around(50f, 10f, h, h), Image("i", asset, frame, rotation = (PI / 4).toFloat()).bounds)
        // A shape adds half its outline width on each side.
        assertRect(upright.inflate(1f), Shape("r", ShapeKind.RECTANGLE, frame, Argb.BLACK, 2f, rotation = quarter).bounds)
        assertEquals(frame, TextBox("t", frame, "x", 12f, Argb.BLACK).bounds)
    }

    @Test
    fun hitTestingFollowsTheTurnedFrame() {
        // A 100 × 20 box at 45° about (50, 10).
        val img = Image("i", asset, Rect(0f, 0f, 100f, 20f), rotation = (PI / 4).toFloat())
        val text = TextBox("t", Rect(0f, 0f, 100f, 20f), "x", 12f, Argb.BLACK, rotation = (PI / 4).toFloat())
        for (e in listOf(img, text)) {
            assertTrue(Selection.hits(e, 50f, 10f, 0f) { FloatArray(0) })
            // 30 units along the turned long axis.
            assertTrue(Selection.hits(e, 50f + 21.2f, 10f + 21.2f, 0f) { FloatArray(0) })
            // Inside the bounds, outside the turned box.
            assertTrue(e.bounds.left < 85f && e.bounds.right > 85f && e.bounds.top < -20f)
            assertFalse(Selection.hits(e, 85f, -20f, 0f) { FloatArray(0) })
            assertTrue(Selection.hits(e, 50f + 21.2f + 2f, 10f + 21.2f - 9f, 3f) { FloatArray(0) })
        }
    }

    @Test
    fun lassoMeetsTheTurnedFrameOnly() {
        val img = Image("i", asset, Rect(0f, 0f, 100f, 20f), rotation = quarter)
        // The upright box spans x 40–60, y −40–60. A loop at x 0–20 overlaps the unturned frame only.
        val loop = floatArrayOf(0f, 0f, 20f, 0f, 20f, 20f, 0f, 20f)
        assertEquals(emptyList<String>(), Selection.lasso(listOf(img), loop))
        val onTop = floatArrayOf(30f, -45f, 50f, -45f, 50f, -30f, 30f, -30f)
        assertEquals(listOf("i"), Selection.lasso(listOf(img), onTop))
    }

    @Test
    fun boundsOfATurnedRectangle() {
        val t = Similarity.rotate(quarter, 0f, 0f)
        assertRect(Rect(-20f, 0f, 20f, 10f), t.bounds(Rect(0f, 0f, 10f, 20f)))
    }

    @Test
    fun rotationSnapsTheResultingAngleToFifteenDegrees() {
        val deg = (PI / 180).toFloat()
        // Unturned, 20° snaps to 15°.
        assertEquals(15 * deg, Snap.rotation(0f, 20 * deg), 1e-5f)
        // Already at 7°, a 20° turn ends at 27°, which snaps to 30°: a 23° turn.
        assertEquals(23 * deg, Snap.rotation(7 * deg, 20 * deg), 1e-5f)
        assertEquals(-15 * deg, Snap.rotation(0f, -8 * deg), 1e-5f)
        assertEquals(0f, Snap.rotation(0f, 7 * deg), 1e-5f)
    }
}
