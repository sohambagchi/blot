package app.blot.core.export

import app.blot.core.geometry.Bezier
import app.blot.core.geometry.LineRoutes
import app.blot.core.geometry.PathData
import app.blot.core.geometry.PathSegment
import app.blot.core.geometry.SubPath
import app.blot.core.model.Arrowhead
import app.blot.core.model.Line
import app.blot.core.model.LineRoute
import app.blot.core.model.Point
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A [Line]'s shaft and arrowheads, which the canvas's `SceneRenderer` and the SVG writer both
 * draw. [Arrowhead.ARROW] is an open V stroked like the shaft, with arms of (L − width/2) so their
 * round caps stay within L; [Arrowhead.TRIANGLE] is filled, its base corners L from the tip, and
 * the shaft stops at its base. A head points along the line's last segment, or a curve's tangent
 * at its end.
 */
object LineGeometry {
    /** Half the angle between an arrowhead's arms. */
    const val ARROW_HALF_ANGLE = (PI / 7).toFloat()

    /** An arrowhead: [points] is an open polyline to stroke, or a closed polygon to fill when [filled]. */
    data class Head(val points: List<Point>, val filled: Boolean)

    /** How far a triangle head's base is from its tip, along the line. */
    private fun triangleBack(line: Line) = line.arrowheadLength * cos(ARROW_HALF_ANGLE)

    /** The line's points with the ends pulled back behind triangle heads. */
    fun shaft(line: Line): List<Point> {
        val pts = line.points.toMutableList()
        val n = pts.size
        val back = triangleBack(line)
        if (line.startArrow == Arrowhead.TRIANGLE) pts[0] = towards(pts[0], line.points[1], back)
        if (line.endArrow == Arrowhead.TRIANGLE) pts[n - 1] = towards(pts[n - 1], line.points[n - 2], back)
        return pts
    }

    /** The path the shaft draws: the line's [Line.centreline] stopping at triangle heads' bases. */
    fun shaftPath(line: Line): PathData {
        if (line.route != LineRoute.CURVE) return LineRoutes.path(shaft(line), line.route, line.cornerRadius)
        val path = line.centreline
        val trimStart = line.startArrow == Arrowhead.TRIANGLE
        val trimEnd = line.endArrow == Arrowhead.TRIANGLE
        if (!trimStart && !trimEnd) return path
        val sp = path.subpaths.first()
        val cubics = sp.segments.filterIsInstance<PathSegment.CubicTo>()
        if (cubics.size != sp.segments.size || cubics.isEmpty()) return path
        // Each segment as its four control points.
        val parts = ArrayList<List<Point>>()
        var from = sp.start
        for (c in cubics) {
            parts += listOf(from, c.c1, c.c2, c.end)
            from = c.end
        }
        val back = triangleBack(line)
        if (trimEnd) trimCubics(parts, back, atStart = false)
        if (trimStart && parts.isNotEmpty()) trimCubics(parts, back, atStart = true)
        if (parts.isEmpty()) return PathData(listOf(SubPath(sp.start, listOf(PathSegment.LineTo(sp.start)), false)))
        return PathData(
            listOf(SubPath(parts[0][0], parts.map { PathSegment.CubicTo(it[1], it[2], it[3]) }, false)),
        )
    }

    // Drops the end segments that lie within [back] of the tip and cuts the next one where it
    // leaves the circle of radius [back] around the tip.
    private fun trimCubics(parts: MutableList<List<Point>>, back: Float, atStart: Boolean) {
        val tip = if (atStart) parts.first()[0] else parts.last()[3]
        fun dist(p: Point) = hypot(p.x - tip.x, p.y - tip.y)
        while (parts.size > 1) {
            val seg = if (atStart) parts.first() else parts.last()
            val far = if (atStart) seg[3] else seg[0]
            if (dist(far) > back) break
            if (atStart) parts.removeAt(0) else parts.removeAt(parts.size - 1)
        }
        val i = if (atStart) 0 else parts.size - 1
        val c = parts[i]
        val far = if (atStart) c[3] else c[0]
        if (dist(far) <= back) {
            parts[i] = List(4) { far }
            return
        }
        // Bisection for where the segment crosses the circle; t runs from the far end towards the tip.
        var lo = 0f
        var hi = 1f
        repeat(32) {
            val mid = (lo + hi) / 2
            val p = Bezier.point(c[0], c[1], c[2], c[3], if (atStart) 1 - mid else mid)
            if (dist(p) > back) lo = mid else hi = mid
        }
        val t = (lo + hi) / 2
        parts[i] = if (atStart) Bezier.split(c[0], c[1], c[2], c[3], 1 - t, first = false)
        else Bezier.split(c[0], c[1], c[2], c[3], t, first = true)
    }

    /**
     * The unit direction in which the line runs into its start ([atStart]) or end tip, or null
     * when it has no length there.
     */
    fun tipDirection(line: Line, atStart: Boolean): Point? {
        val pts = line.points
        val tip = if (atStart) pts.first() else pts.last()
        val candidates: List<Point> = if (line.route == LineRoute.CURVE) {
            val sp = line.centreline.subpaths.first()
            val c = (if (atStart) sp.segments.firstOrNull() else sp.segments.lastOrNull()) as? PathSegment.CubicTo
            if (c == null) emptyList() else if (atStart) listOf(c.c1, c.c2, c.end) else listOf(c.c2, c.c1, sp.segments.getOrNull(sp.segments.size - 2)?.end ?: sp.start)
        } else {
            if (atStart) pts.drop(1) else pts.dropLast(1).reversed()
        }
        val from = candidates.firstOrNull { it != tip } ?: return null
        val len = hypot(tip.x - from.x, tip.y - from.y)
        return Point((tip.x - from.x) / len, (tip.y - from.y) / len)
    }

    /** The head at the start ([atStart]) or end, or null for none or a line with no length at that end. */
    fun head(line: Line, atStart: Boolean): Head? {
        val kind = if (atStart) line.startArrow else line.endArrow
        if (kind == Arrowhead.NONE) return null
        val tip = if (atStart) line.points.first() else line.points.last()
        val dir = tipDirection(line, atStart) ?: return null
        val back = atan2(-dir.y, -dir.x)
        val len = line.arrowheadLength
        fun at(r: Float, a: Float) = Point(tip.x + r * cos(a), tip.y + r * sin(a))
        return when (kind) {
            Arrowhead.ARROW -> {
                val arm = (len - line.width / 2).coerceAtLeast(0f)
                Head(listOf(at(arm, back + ARROW_HALF_ANGLE), tip, at(arm, back - ARROW_HALF_ANGLE)), filled = false)
            }
            Arrowhead.TRIANGLE -> Head(listOf(tip, at(len, back + ARROW_HALF_ANGLE), at(len, back - ARROW_HALF_ANGLE)), filled = true)
            Arrowhead.NONE -> null
        }
    }

    private fun towards(p: Point, target: Point, d: Float): Point {
        val len = hypot(target.x - p.x, target.y - p.y)
        if (len == 0f) return p
        val k = minOf(d, len) / len
        return Point(p.x + (target.x - p.x) * k, p.y + (target.y - p.y) * k)
    }
}
