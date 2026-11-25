package app.blot.core.geometry

import app.blot.core.model.LineRoute
import app.blot.core.model.Point
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/** How a line's points become the path it draws, for each [LineRoute]. */
object LineRoutes {
    /**
     * The orthogonal route from ([ax], [ay]) to ([bx], [by]): horizontal, vertical, horizontal
     * when the drag is at least as wide as it is tall, otherwise vertical, horizontal, vertical,
     * with the middle segment halfway. Zero-length segments are left out, so a level or plumb drag
     * gives two points.
     */
    fun elbow(ax: Float, ay: Float, bx: Float, by: Float): List<Point> {
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0f || dy == 0f) return listOf(Point(ax, ay), Point(bx, by))
        return if (abs(dx) >= abs(dy)) {
            val mx = ax + dx / 2
            listOf(Point(ax, ay), Point(mx, ay), Point(mx, by), Point(bx, by))
        } else {
            val my = ay + dy / 2
            listOf(Point(ax, ay), Point(ax, my), Point(bx, my), Point(bx, by))
        }
    }

    /**
     * The path a line with [route] draws through [points]: straight segments, straight segments
     * with corners rounded to [cornerRadius] (each corner takes at most half of either segment),
     * or a smooth curve through every point.
     */
    fun path(points: List<Point>, route: LineRoute, cornerRadius: Float): PathData = when (route) {
        LineRoute.STRAIGHT -> polyline(points)
        LineRoute.ELBOW -> rounded(points, cornerRadius)
        LineRoute.CURVE -> curve(points)
    }

    fun polyline(points: List<Point>): PathData {
        val p = PathData.Builder().moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) p.lineTo(points[i].x, points[i].y)
        return p.build()
    }

    /** [points] joined by straight segments whose corners are circular arcs (as cubics) of radius up to [radius]. */
    fun rounded(points: List<Point>, radius: Float): PathData {
        val pts = distinct(points)
        val p = PathData.Builder().moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size - 1) {
            val a = pts[i - 1]
            val v = pts[i]
            val b = pts[i + 1]
            val la = hypot(v.x - a.x, v.y - a.y)
            val lb = hypot(b.x - v.x, b.y - v.y)
            val ux = (v.x - a.x) / la
            val uy = (v.y - a.y) / la
            val wx = (b.x - v.x) / lb
            val wy = (b.y - v.y) / lb
            val turn = acos((ux * wx + uy * wy).coerceIn(-1f, 1f))
            val t = min(radius, min(la, lb) / 2)
            if (turn < 1e-3f || t <= 0f) {
                p.lineTo(v.x, v.y)
                continue
            }
            // A circular fillet tangent to both segments, t from the corner; its cubic handles are
            // (4/3)·tan(turn/4)·r long, with r = t / tan(turn/2).
            val handle = 4f / 3f * tan(turn / 4) * t / tan(turn / 2)
            val ax2 = v.x - ux * t
            val ay2 = v.y - uy * t
            val bx2 = v.x + wx * t
            val by2 = v.y + wy * t
            p.lineTo(ax2, ay2)
            p.cubicTo(ax2 + ux * handle, ay2 + uy * handle, bx2 - wx * handle, by2 - wy * handle, bx2, by2)
        }
        p.lineTo(pts.last().x, pts.last().y)
        return p.build()
    }

    /**
     * A smooth curve through [points]: centripetal Catmull–Rom (α = ½) as cubic Béziers, which
     * never loops or cusps within a segment (Yuksel, Schaefer and Keyser, "Parameterization and
     * applications of Catmull–Rom curves", 2011). The ends use mirrored phantom points, so the
     * curve leaves the first point towards the second and arrives at the last from the one before.
     */
    fun curve(points: List<Point>): PathData {
        val pts = distinct(points)
        val p = PathData.Builder().moveTo(pts[0].x, pts[0].y)
        val n = pts.size
        if (n == 1) return p.lineTo(pts[0].x, pts[0].y).build()
        fun at(i: Int): Point = when {
            i < 0 -> Point(2 * pts[0].x - pts[1].x, 2 * pts[0].y - pts[1].y)
            i >= n -> Point(2 * pts[n - 1].x - pts[n - 2].x, 2 * pts[n - 1].y - pts[n - 2].y)
            else -> pts[i]
        }
        for (i in 0 until n - 1) {
            val p0 = at(i - 1)
            val p1 = at(i)
            val p2 = at(i + 1)
            val p3 = at(i + 2)
            val d0 = knot(p0, p1)
            val d1 = knot(p1, p2)
            val d2 = knot(p2, p3)
            // Tangents of the non-uniform Catmull–Rom spline, scaled to the segment's knot interval.
            val m1x = d1 * ((p1.x - p0.x) / d0 - (p2.x - p0.x) / (d0 + d1)) + (p2.x - p1.x)
            val m1y = d1 * ((p1.y - p0.y) / d0 - (p2.y - p0.y) / (d0 + d1)) + (p2.y - p1.y)
            val m2x = d1 * ((p3.x - p2.x) / d2 - (p3.x - p1.x) / (d1 + d2)) + (p2.x - p1.x)
            val m2y = d1 * ((p3.y - p2.y) / d2 - (p3.y - p1.y) / (d1 + d2)) + (p2.y - p1.y)
            p.cubicTo(p1.x + m1x / 3, p1.y + m1y / 3, p2.x - m2x / 3, p2.y - m2y / 3, p2.x, p2.y)
        }
        return p.build()
    }

    private fun knot(a: Point, b: Point): Float = hypot(b.x - a.x, b.y - a.y).toDouble().pow(0.5).toFloat().coerceAtLeast(1e-4f)

    /** [points] without consecutive repeats, keeping at least one. */
    private fun distinct(points: List<Point>): List<Point> {
        val out = ArrayList<Point>(points.size)
        for (q in points) if (out.isEmpty() || out.last() != q) out += q
        return out
    }
}
