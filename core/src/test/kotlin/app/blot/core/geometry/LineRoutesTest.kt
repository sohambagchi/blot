package app.blot.core.geometry

import app.blot.core.edit.PlaneMath
import app.blot.core.edit.Selection
import app.blot.core.edit.Similarity
import app.blot.core.edit.transformed
import app.blot.core.export.LineGeometry
import app.blot.core.model.Argb
import app.blot.core.model.Arrowhead
import app.blot.core.model.Line
import app.blot.core.model.LineRoute
import app.blot.core.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class LineRoutesTest {
    private fun assertPoint(e: Point, a: Point, tol: Float = 1e-3f) {
        assertEquals("x of $a", e.x, a.x, tol)
        assertEquals("y of $a", e.y, a.y, tol)
    }

    private fun Polyline.first() = Point(xy[0], xy[1])
    private fun Polyline.last() = Point(xy[xy.size - 2], xy[xy.size - 1])

    @Test
    fun elbowGoesAcrossFirstWhenTheDragIsWide() {
        val r = LineRoutes.elbow(10f, 20f, 210f, 120f)
        assertEquals(listOf(Point(10f, 20f), Point(110f, 20f), Point(110f, 120f), Point(210f, 120f)), r)
        // Leftwards and upwards too.
        assertEquals(listOf(Point(210f, 120f), Point(110f, 120f), Point(110f, 20f), Point(10f, 20f)), LineRoutes.elbow(210f, 120f, 10f, 20f))
    }

    @Test
    fun elbowGoesDownFirstWhenTheDragIsTall() {
        val r = LineRoutes.elbow(10f, 20f, 60f, 220f)
        assertEquals(listOf(Point(10f, 20f), Point(10f, 120f), Point(60f, 120f), Point(60f, 220f)), r)
        assertEquals(listOf(Point(0f, 0f), Point(0f, -50f), Point(-10f, -50f), Point(-10f, -100f)), LineRoutes.elbow(0f, 0f, -10f, -100f))
    }

    @Test
    fun elbowDropsZeroLengthSegments() {
        assertEquals(listOf(Point(0f, 5f), Point(90f, 5f)), LineRoutes.elbow(0f, 5f, 90f, 5f))
        assertEquals(listOf(Point(5f, 0f), Point(5f, -90f)), LineRoutes.elbow(5f, 0f, 5f, -90f))
        assertEquals(listOf(Point(5f, 5f), Point(5f, 5f)), LineRoutes.elbow(5f, 5f, 5f, 5f))
        // Drawn and rendered without trouble.
        for (pts in listOf(LineRoutes.elbow(5f, 5f, 5f, 5f), LineRoutes.elbow(0f, 0f, 1f, 1f))) {
            val l = Line("l", pts, Argb.BLACK, 2f, Arrowhead.TRIANGLE, Arrowhead.ARROW, route = LineRoute.ELBOW)
            LineGeometry.shaftPath(l).flatten()
            Selection.drawnOf(l)
            assertTrue(l.bounds.width.isFinite())
        }
    }

    @Test
    fun elbowCornersAreRoundedAndTheEndsStay() {
        val pts = LineRoutes.elbow(0f, 0f, 200f, 100f)
        val l = Line("l", pts, Argb.BLACK, 2f, route = LineRoute.ELBOW)
        val flat = l.centreline.flatten().single()
        assertPoint(Point(0f, 0f), flat.first())
        assertPoint(Point(200f, 100f), flat.last())
        val r = l.cornerRadius
        // The sharp corner is cut: the path passes r(1 − 1/√2) inside it.
        assertFalse(PlaneMath.polylineNear(flat.xy, flat.count, false, 100f, 0f, 100f, 0f, 1f))
        assertTrue(PlaneMath.polylineNear(flat.xy, flat.count, false, 100f - r * (1 - 1 / kotlin.math.sqrt(2f)), r * (1 - 1 / kotlin.math.sqrt(2f)), 100f - r * (1 - 1 / kotlin.math.sqrt(2f)), r * (1 - 1 / kotlin.math.sqrt(2f)), 0.1f))
        // Short segments shrink the corner: never more than half a segment.
        val tight = Line("t", LineRoutes.elbow(0f, 0f, 10f, 6f), Argb.BLACK, 2f, route = LineRoute.ELBOW).centreline.flatten().single()
        assertPoint(Point(10f, 6f), tight.last())
        for (i in 0 until tight.count) assertTrue(tight.xy[i * 2] in -1e-3f..10.001f && tight.xy[i * 2 + 1] in -1e-3f..6.001f)
    }

    @Test
    fun elbowArrowheadsAlignWithTheEndSegments() {
        val l = Line("l", LineRoutes.elbow(0f, 0f, 200f, 100f), Argb.BLACK, 2f, Arrowhead.ARROW, Arrowhead.TRIANGLE, route = LineRoute.ELBOW)
        assertPoint(Point(1f, 0f), LineGeometry.tipDirection(l, atStart = false)!!)
        assertPoint(Point(-1f, 0f), LineGeometry.tipDirection(l, atStart = true)!!)
        val tall = Line("l", LineRoutes.elbow(0f, 0f, 50f, -200f), Argb.BLACK, 2f, endArrow = Arrowhead.ARROW, route = LineRoute.ELBOW)
        assertPoint(Point(0f, -1f), LineGeometry.tipDirection(tall, atStart = false)!!)
        val head = LineGeometry.head(l, atStart = false)!!
        assertTrue(head.filled)
        // The triangle's base is upright, behind the tip.
        assertEquals(head.points[1].x, head.points[2].x, 1e-3f)
        assertTrue(head.points[1].x < 200f)
        // The shaft stops at the base.
        val shaft = LineGeometry.shaftPath(l).flatten().single()
        assertEquals(200f - l.arrowheadLength * cos(LineGeometry.ARROW_HALF_ANGLE), shaft.last().x, 1e-3f)
    }

    @Test
    fun curvePassesThroughEveryPoint() {
        val pts = listOf(Point(0f, 0f), Point(60f, -40f), Point(140f, 30f), Point(200f, -10f))
        val path = LineRoutes.curve(pts)
        val segs = path.subpaths.single().segments
        assertEquals(3, segs.size)
        assertTrue(segs.all { it is PathSegment.CubicTo })
        assertEquals(pts.drop(1), segs.map { it.end })
        assertEquals(pts[0], path.subpaths.single().start)
        // Smooth: at each inner point the handles on either side are collinear with it.
        for (i in 0 until segs.size - 1) {
            val a = segs[i] as PathSegment.CubicTo
            val b = segs[i + 1] as PathSegment.CubicTo
            val p = a.end
            val cross = (p.x - a.c2.x) * (b.c1.y - p.y) - (p.y - a.c2.y) * (b.c1.x - p.x)
            assertEquals(0f, cross, 1e-2f)
        }
    }

    @Test
    fun curveLeavesTowardsTheSecondPointAndArrivesFromTheOneBefore() {
        val pts = listOf(Point(0f, 0f), Point(100f, 100f), Point(200f, 100f))
        val l = Line("c", pts, Argb.BLACK, 2f, Arrowhead.ARROW, Arrowhead.ARROW, route = LineRoute.CURVE)
        val start = LineGeometry.tipDirection(l, atStart = true)!!
        assertPoint(Point(-1 / kotlin.math.sqrt(2f), -1 / kotlin.math.sqrt(2f)), start)
        assertPoint(Point(1f, 0f), LineGeometry.tipDirection(l, atStart = false)!!)
        // The end head's arms sit either side of the tangent.
        val head = LineGeometry.head(l, atStart = false)!!
        assertEquals(200f, head.points[1].x)
        assertEquals(head.points[0].y - 100f, 100f - head.points[2].y, 1e-3f)
    }

    @Test
    fun aTriangleHeadTrimsTheCurveAtItsBase() {
        val pts = listOf(Point(0f, 0f), Point(80f, 60f), Point(160f, 0f))
        val l = Line("c", pts, Argb.BLACK, 3f, Arrowhead.TRIANGLE, Arrowhead.TRIANGLE, route = LineRoute.CURVE)
        val shaft = LineGeometry.shaftPath(l).flatten().single()
        val back = l.arrowheadLength * cos(LineGeometry.ARROW_HALF_ANGLE)
        assertEquals(back, hypot(shaft.first().x, shaft.first().y), 0.01f)
        assertEquals(back, hypot(shaft.last().x - 160f, shaft.last().y), 0.01f)
        // The rest of the shaft follows the untrimmed curve.
        val full = l.centreline.flatten().single()
        assertTrue(PlaneMath.polylineNear(full.xy, full.count, false, 80f, 60f, 80f, 60f, 0.01f))
        assertTrue(PlaneMath.polylineNear(shaft.xy, shaft.count, false, 80f, 60f, 80f, 60f, 0.01f))
        // A curve shorter than the heads leaves a dot, not a crash.
        val tiny = Line("t", listOf(Point(0f, 0f), Point(2f, 1f), Point(4f, 0f)), Argb.BLACK, 3f, Arrowhead.TRIANGLE, Arrowhead.TRIANGLE, route = LineRoute.CURVE)
        LineGeometry.shaftPath(tiny).flatten()
    }

    @Test
    fun curveBoundsHoldTheWholeCurve() {
        // Catmull–Rom overshoots its points; the bounds must still hold it.
        val pts = listOf(Point(0f, 0f), Point(10f, 100f), Point(100f, 100f), Point(110f, 0f))
        val l = Line("c", pts, Argb.BLACK, 4f, route = LineRoute.CURVE)
        val b = l.bounds
        val flat = l.centreline.flatten().single()
        for (i in 0 until flat.count) {
            assertTrue(flat.xy[i * 2] - 2f >= b.left && flat.xy[i * 2] + 2f <= b.right)
            assertTrue(flat.xy[i * 2 + 1] - 2f >= b.top && flat.xy[i * 2 + 1] + 2f <= b.bottom)
        }
        assertTrue(flat.xy.toList().chunked(2).maxOf { it[1] } > 100f)
    }

    @Test
    fun aStraightDragFitsFewPointsAndBowsGently() {
        val n = 50
        val xy = FloatArray(n * 2) { if (it % 2 == 0) 10f + (it / 2) * 6f else 40f + 1.5f * sin((it / 2) * PI / (n - 1)).toFloat() }
        val pts = CurveFit.fit(xy, tolerance = 4f)
        assertEquals(3, pts.size)
        assertEquals(Point(10f, 40f), pts.first())
        assertPoint(Point(10f + (n - 1) * 6f, 40f), pts.last())
        val chord = (n - 1) * 6f
        // Bowed by BOW of the length, to the side the pen strayed to (down, right of a rightward drag).
        assertEquals(40f + CurveFit.BOW * chord, pts[1].y, 1f)
        // A perfectly straight drag bows left of its direction.
        val straight = FloatArray(20) { if (it % 2 == 0) it * 10f else 0f }
        val s = CurveFit.fit(straight, 4f)
        assertTrue(s[1].y < 0f)
    }

    @Test
    fun aWavyDragKeepsItsTurnsUpToTheLimit() {
        val n = 400
        val xy = FloatArray(n * 2) { i -> val k = i / 2; if (i % 2 == 0) k * 2f else 80f * sin(k * 2 * PI / 250).toFloat() }
        val pts = CurveFit.fit(xy, tolerance = 3f)
        assertTrue("${pts.size}", pts.size in 4..CurveFit.MAX_POINTS)
        assertEquals(Point(0f, 0f), pts.first())
        assertEquals(Point(xy[n * 2 - 2], xy[n * 2 - 1]), pts.last())
        // The curve through them follows the drag closely.
        val flat = LineRoutes.curve(pts).flatten().single()
        for (k in 0 until n step 7) assertTrue("sample $k", PlaneMath.polylineNear(flat.xy, flat.count, false, xy[k * 2], xy[k * 2 + 1], xy[k * 2], xy[k * 2 + 1], 15f))
        // A tighter limit simplifies harder.
        assertTrue(CurveFit.fit(xy, tolerance = 0.5f, maxPoints = 4).size <= 4)
    }

    @Test
    fun shortAndOddDragsDoNotCrash() {
        assertEquals(2, CurveFit.fit(floatArrayOf(5f, 5f), 3f).size)
        assertEquals(2, CurveFit.fit(floatArrayOf(5f, 5f, 5f, 5f, 5f, 5f), 3f).size)
        val loop = FloatArray(42) { i -> val a = (i / 2) * 2 * PI / 20; if (i % 2 == 0) (50 * cos(a)).toFloat() else (50 * sin(a)).toFloat() }
        val pts = CurveFit.fit(loop, 3f)
        assertTrue(pts.size >= 3)
        val l = Line("c", pts, Argb.BLACK, 2f, Arrowhead.TRIANGLE, Arrowhead.TRIANGLE, route = LineRoute.CURVE)
        LineGeometry.shaftPath(l).flatten()
        assertNotNull(LineGeometry.head(l, atStart = false))
    }

    @Test
    fun curvesAndElbowsTransformWithTheirPoints() {
        val c = Line("c", listOf(Point(0f, 0f), Point(50f, 40f), Point(100f, 0f)), Argb.BLACK, 2f, endArrow = Arrowhead.ARROW, route = LineRoute.CURVE)
        val t = Similarity(dx = 30f, dy = -10f, scale = 2f, originX = 50f, originY = 0f, rotation = 0.5f)
        val moved = c.transformed(t) as Line
        assertEquals(LineRoute.CURVE, moved.route)
        // Centripetal Catmull–Rom commutes with similarities: the turned curve is the curve of the turned points.
        val a = moved.centreline.flatten().single()
        val b = c.centreline.flatten().single()
        for (i in 0 until a.count) {
            assertEquals(t.mapX(b.xy[i * 2], b.xy[i * 2 + 1]), a.xy[i * 2], 1e-2f)
            assertEquals(t.mapY(b.xy[i * 2], b.xy[i * 2 + 1]), a.xy[i * 2 + 1], 1e-2f)
        }
        val e = Line("e", LineRoutes.elbow(0f, 0f, 100f, 60f), Argb.BLACK, 2f, route = LineRoute.ELBOW)
        val turned = e.transformed(Similarity.rotate((PI / 2).toFloat(), 0f, 0f)) as Line
        assertEquals(LineRoute.ELBOW, turned.route)
        val d = LineGeometry.tipDirection(turned, atStart = false)!!
        assertEquals((PI / 2).toFloat(), atan2(d.y, d.x), 1e-4f)
    }

    @Test
    fun hitTestsFollowTheRoute() {
        val curve = Line("c", listOf(Point(0f, 0f), Point(100f, 60f), Point(200f, 0f)), Argb.BLACK, 2f, route = LineRoute.CURVE)
        fun hit(l: Line, x: Float, y: Float) = Selection.hits(l, x, y, 3f) { FloatArray(0) }
        assertTrue(hit(curve, 100f, 60f))
        assertFalse(hit(curve, 100f, 0f))
        val elbow = Line("e", LineRoutes.elbow(0f, 0f, 200f, 100f), Argb.BLACK, 2f, route = LineRoute.ELBOW)
        assertTrue(hit(elbow, 100f, 50f))
        assertFalse(hit(elbow, 50f, 50f))
        // The lasso takes a curve it touches only on the bulge.
        val ring = floatArrayOf(90f, 50f, 110f, 50f, 110f, 70f, 90f, 70f)
        assertEquals(listOf("c"), Selection.lasso(listOf(curve), ring))
        assertEquals(emptyList<String>(), Selection.lasso(listOf(curve.copy(route = LineRoute.STRAIGHT, points = listOf(Point(0f, 0f), Point(200f, 0f)))), ring))
    }

    @Test
    fun straightLinesDrawAsBefore() {
        val l = Line("l", listOf(Point(0f, 0f), Point(40.5f, -3f), Point(90f, 12f)), Argb.BLACK, 3f, Arrowhead.ARROW, Arrowhead.TRIANGLE)
        val flat = l.centreline.flatten().single()
        assertEquals(listOf(0f, 0f, 40.5f, -3f, 90f, 12f), flat.xy.toList())
        val shaft = LineGeometry.shaftPath(l).flatten().single()
        assertEquals(LineGeometry.shaft(l).flatMap { listOf(it.x, it.y) }, shaft.xy.toList())
        assertTrue(abs(shaft.xy[4] - 90f) > 1f)
    }
}
