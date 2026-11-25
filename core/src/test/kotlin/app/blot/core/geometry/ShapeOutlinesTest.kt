package app.blot.core.geometry

import app.blot.core.edit.PlaneMath
import app.blot.core.edit.Selection
import app.blot.core.model.Argb
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class ShapeOutlinesTest {
    private val frame = Rect(100f, 50f, 180f, 160f)
    private val w = frame.width
    private val h = frame.height

    private fun outline(kind: ShapeKind, f: Rect = frame) = ShapeOutlines.of(kind, f).flatten()

    private fun silhouette(kind: ShapeKind, f: Rect = frame): Polyline = outline(kind, f).single { it.closed }

    private fun area(p: Polyline): Float {
        var a = 0.0
        val n = p.count
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += p.xy[i * 2].toDouble() * p.xy[j * 2 + 1] - p.xy[j * 2].toDouble() * p.xy[i * 2 + 1]
        }
        return (a / 2).toFloat()
    }

    private fun perimeter(p: Polyline): Float {
        var s = 0f
        val n = p.count
        for (i in 0 until n) {
            val j = (i + 1) % n
            s += hypot(p.xy[j * 2] - p.xy[i * 2], p.xy[j * 2 + 1] - p.xy[i * 2 + 1])
        }
        return s
    }

    private fun assertXy(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertEquals("[$i]", expected[i], actual[i], 1e-4f)
    }

    private fun onOutline(lines: List<Polyline>, x: Float, y: Float, tol: Float = 0.05f) =
        lines.any { PlaneMath.polylineNear(it.xy, it.count, it.closed, x, y, x, y, tol) }

    @Test
    fun everyOutlineIsClosedAndInsideItsFrame() {
        for (kind in ShapeKind.entries) {
            val lines = outline(kind)
            assertEquals(kind.name, 1, lines.count { it.closed })
            for (p in lines) for (i in 0 until p.count) {
                val x = p.xy[i * 2]
                val y = p.xy[i * 2 + 1]
                assertTrue("$kind $x,$y", x >= frame.left - 1e-3f && x <= frame.right + 1e-3f)
                assertTrue("$kind $x,$y", y >= frame.top - 1e-3f && y <= frame.bottom + 1e-3f)
            }
            // Each outline touches all four sides of its frame.
            val all = lines.flatMap { p -> List(p.count) { p.xy[it * 2] to p.xy[it * 2 + 1] } }
            assertEquals(kind.name, frame.left, all.minOf { it.first }, 0.01f)
            assertEquals(kind.name, frame.right, all.maxOf { it.first }, 0.01f)
            assertEquals(kind.name, frame.top, all.minOf { it.second }, 0.01f)
            assertEquals(kind.name, frame.bottom, all.maxOf { it.second }, 0.01f)
        }
    }

    @Test
    fun closedOutlinesRunClockwiseOnScreen() {
        // y grows down, so a clockwise loop on screen has a positive shoelace area.
        for (kind in ShapeKind.entries) assertTrue(kind.name, area(silhouette(kind)) > 0f)
    }

    @Test
    fun areasMatchTheGeometry() {
        val k = ShapeOutlines.cornerRadius(frame)
        val ry = ShapeOutlines.cylinderCap(frame)
        val expected = mapOf(
            ShapeKind.RECTANGLE to w * h,
            ShapeKind.ROUNDED_RECTANGLE to w * h - (4 - PI.toFloat()) * k * k,
            ShapeKind.ELLIPSE to PI.toFloat() * w / 2 * h / 2,
            ShapeKind.TRIANGLE to w * h / 2,
            ShapeKind.DIAMOND to w * h / 2,
            ShapeKind.HEXAGON to w * h * 0.75f,
            ShapeKind.PARALLELOGRAM to w * h * 7f / 9f,
            ShapeKind.CYLINDER to w * (h - 2 * ry) + PI.toFloat() * w / 2 * ry,
        )
        for ((kind, a) in expected) assertEquals(kind.name, a, area(silhouette(kind)), a * 0.005f)
    }

    @Test
    fun perimetersMatchTheGeometry() {
        assertEquals(2 * (w + h), perimeter(silhouette(ShapeKind.RECTANGLE)), 0.01f)
        assertEquals(4 * hypot(w / 2, h / 2), perimeter(silhouette(ShapeKind.DIAMOND)), 0.01f)
        assertEquals(w + 2 * hypot(w / 2, h), perimeter(silhouette(ShapeKind.TRIANGLE)), 0.01f)
        val a = w / 2
        val b = h / 2
        // Ramanujan's approximation.
        val ellipse = PI.toFloat() * (3 * (a + b) - sqrt((3 * a + b) * (a + 3 * b)))
        assertEquals(ellipse, perimeter(silhouette(ShapeKind.ELLIPSE)), ellipse * 0.002f)
        val k = ShapeOutlines.cornerRadius(frame)
        assertEquals(2 * (w + h) - 8 * k + 2 * PI.toFloat() * k, perimeter(silhouette(ShapeKind.ROUNDED_RECTANGLE)), 0.5f)
    }

    @Test
    fun proportionsFollowTheGlyphs() {
        // The design's 24-unit glyphs, scaled to their own frames.
        val hex = silhouette(ShapeKind.HEXAGON, Rect(3f, 4f, 18f, 16f))
        assertXy(floatArrayOf(7.5f, 4f, 16.5f, 4f, 21f, 12f, 16.5f, 20f, 7.5f, 20f, 3f, 12f), hex.xy)
        val para = silhouette(ShapeKind.PARALLELOGRAM, Rect(3f, 6f, 18f, 12f))
        assertXy(floatArrayOf(7f, 6f, 21f, 6f, 17f, 18f, 3f, 18f), para.xy)
        val tri = silhouette(ShapeKind.TRIANGLE, Rect(3f, 4f, 18f, 16f))
        assertXy(floatArrayOf(12f, 4f, 21f, 20f, 3f, 20f), tri.xy)
        val diamond = silhouette(ShapeKind.DIAMOND, Rect(3f, 3f, 18f, 18f))
        assertXy(floatArrayOf(12f, 3f, 21f, 12f, 12f, 21f, 3f, 12f), diamond.xy)
        assertEquals(4f, ShapeOutlines.cornerRadius(Rect(4f, 5f, 16f, 14f)), 1e-5f)
        assertEquals(2.5f, ShapeOutlines.cylinderCap(Rect(5f, 3.5f, 14f, 17f)), 1e-5f)
        // Large frames cap the corner radius and keep a flat cylinder's caps within its height.
        assertEquals(ShapeOutlines.ROUND_CORNER_MAX, ShapeOutlines.cornerRadius(Rect(0f, 0f, 900f, 600f)))
        assertEquals(25f, ShapeOutlines.cylinderCap(Rect(0f, 0f, 400f, 100f)))
    }

    @Test
    fun symmetricWhereTheGlyphIs() {
        val mirrorX = setOf(ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECTANGLE, ShapeKind.ELLIPSE, ShapeKind.TRIANGLE, ShapeKind.DIAMOND, ShapeKind.HEXAGON, ShapeKind.CYLINDER)
        val mirrorY = setOf(ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECTANGLE, ShapeKind.ELLIPSE, ShapeKind.DIAMOND, ShapeKind.HEXAGON)
        val cx = frame.centerX
        val cy = frame.centerY
        for (kind in ShapeKind.entries) {
            val lines = outline(kind)
            fun symmetric(map: (Float, Float) -> Pair<Float, Float>) = lines.all { s ->
                (0 until s.count).all { i -> map(s.xy[i * 2], s.xy[i * 2 + 1]).let { (x, y) -> onOutline(lines, x, y, 0.2f) } }
            }
            assertEquals("$kind left-right", kind in mirrorX, symmetric { x, y -> 2 * cx - x to y })
            assertEquals("$kind top-bottom", kind in mirrorY, symmetric { x, y -> x to 2 * cy - y })
            if (kind == ShapeKind.PARALLELOGRAM) assertTrue(symmetric { x, y -> 2 * cx - x to 2 * cy - y })
        }
    }

    @Test
    fun cylinderShowsTheTopCapWholeAndTheBottomCapsFrontHalf() {
        val lines = outline(ShapeKind.CYLINDER)
        val ry = ShapeOutlines.cylinderCap(frame)
        val cx = frame.centerX
        assertEquals(1, lines.count { !it.closed })
        // The top cap's front and back, the bottom cap's front only.
        assertTrue(onOutline(lines, cx, frame.top))
        assertTrue(onOutline(lines, cx, frame.top + 2 * ry))
        assertTrue(onOutline(lines, cx, frame.bottom))
        assertFalse(onOutline(lines, cx, frame.bottom - 2 * ry, 1f))
    }

    @Test
    fun rotationTurnsTheOutlineAboutTheFrameCentre() {
        val a = 0.7f
        val c = cos(a)
        val s = sin(a)
        for (kind in ShapeKind.entries) {
            val flat = ShapeOutlines.of(kind, frame).flatten()
            val turned = ShapeOutlines.of(kind, frame).flatten(a, frame.centerX, frame.centerY)
            for ((p, q) in flat.zip(turned)) for (i in 0 until p.count) {
                val dx = p.xy[i * 2] - frame.centerX
                val dy = p.xy[i * 2 + 1] - frame.centerY
                assertEquals(frame.centerX + dx * c - dy * s, q.xy[i * 2], 1e-3f)
                assertEquals(frame.centerY + dx * s + dy * c, q.xy[i * 2 + 1], 1e-3f)
            }
        }
    }

    @Test
    fun boundsContainTheStrokedOutlineTurnedAnyWay() {
        for (kind in ShapeKind.entries) for (rot in listOf(0f, 0.4f, -2f)) {
            val shape = Shape("s", kind, frame, Argb.BLACK, 6f, rotation = rot)
            val b = shape.bounds
            for (p in Selection.shapeOutline(shape)) for (i in 0 until p.count) {
                val x = p.xy[i * 2]
                val y = p.xy[i * 2 + 1]
                assertTrue("$kind $rot", x - 3f >= b.left - 0.01f && x + 3f <= b.right + 0.01f && y - 3f >= b.top - 0.01f && y + 3f <= b.bottom + 0.01f)
            }
            // And not much more: within the miter reach of the turned frame.
            val loose = frame.rotatedBounds(rot).inflate(3f * 4f)
            assertTrue("$kind $rot $b", b.left >= loose.left && b.right <= loose.right && b.top >= loose.top && b.bottom <= loose.bottom)
        }
    }

    @Test
    fun aTrianglesApexMitreCountsInItsBounds() {
        val shape = Shape("t", ShapeKind.TRIANGLE, frame, Argb.BLACK, 10f)
        val halfApex = atan(w / 2 / h)
        assertEquals(frame.top - 5f / sin(halfApex), shape.bounds.top, 0.01f)
        assertEquals(frame.bottom + 5f, shape.bounds.bottom, 0.01f)
    }

    @Test
    fun hitTestsFollowTheOutline() {
        for (kind in ShapeKind.entries) {
            val open = Shape("s", kind, frame, Argb.BLACK, 2f)
            val filled = open.copy(fill = Argb.WHITE)
            fun hit(s: Shape, x: Float, y: Float) = Selection.hits(s, x, y, 2f) { FloatArray(0) }
            // The top middle is on every outline.
            assertTrue(kind.name, hit(open, frame.centerX, frame.top))
            assertTrue(kind.name, hit(filled, frame.centerX, frame.centerY))
            assertFalse(kind.name, hit(open, frame.centerX, frame.centerY))
            // The frame's top-left corner is outside everything but a rectangle.
            assertEquals(kind.name, kind == ShapeKind.RECTANGLE, hit(filled, frame.left + 1f, frame.top + 1f))
        }
        // A cylinder's open front edge of the top cap counts too.
        val cyl = Shape("c", ShapeKind.CYLINDER, frame, Argb.BLACK, 2f)
        assertTrue(Selection.hits(cyl, frame.centerX, frame.top + 2 * ShapeOutlines.cylinderCap(frame), 2f) { FloatArray(0) })
        // A turned triangle's apex moves with it.
        val turned = Shape("t", ShapeKind.TRIANGLE, frame, Argb.BLACK, 2f, rotation = (PI / 2).toFloat())
        assertTrue(Selection.hits(turned, frame.centerX + h / 2, frame.centerY, 2f) { FloatArray(0) })
        assertFalse(Selection.hits(turned, frame.centerX, frame.top, 2f) { FloatArray(0) })
    }

    @Test
    fun theLassoSelectsWhatTouchesTheOutlineNotTheFrame() {
        for (kind in ShapeKind.entries - ShapeKind.RECTANGLE) {
            val s = Shape("s", kind, frame, Argb.BLACK, 2f, fill = Argb.WHITE)
            // A small loop in the frame's top-left corner, clear of the outline.
            val corner = floatArrayOf(frame.left - 5f, frame.top - 5f, frame.left + 6f, frame.top - 5f, frame.left + 6f, frame.top + 6f, frame.left - 5f, frame.top + 6f)
            assertEquals(kind.name, emptyList<String>(), Selection.lasso(listOf(s), corner))
            // A loop across the top middle.
            val top = floatArrayOf(frame.centerX - 5f, frame.top - 5f, frame.centerX + 5f, frame.top - 5f, frame.centerX + 5f, frame.top + 5f, frame.centerX - 5f, frame.top + 5f)
            assertEquals(kind.name, listOf("s"), Selection.lasso(listOf(s), top))
        }
    }

    @Test
    fun degenerateFramesDoNotBreak() {
        for (kind in ShapeKind.entries) for (f in listOf(Rect(5f, 5f, 0f, 0f), Rect(5f, 5f, 40f, 0f), Rect(5f, 5f, 0f, 40f))) {
            val s = Shape("s", kind, f, Argb.BLACK, 2f, rotation = 0.3f)
            assertTrue(kind.name, s.bounds.width.isFinite() && s.bounds.height.isFinite())
            Selection.hits(s, 5f, 5f, 2f) { FloatArray(0) }
            assertTrue(abs(area(silhouette(kind, f))) < 1e-3f)
        }
    }
}
