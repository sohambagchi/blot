package app.screenscratch.core.export

import app.screenscratch.core.model.Arrowhead
import app.screenscratch.core.model.Line
import app.screenscratch.core.model.Point
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A [Line]'s shaft and arrowheads as the canvas's `SceneRenderer` draws them, for writers that
 * cannot call it. [Arrowhead.ARROW] is an open V stroked like the shaft, with arms of
 * (L − width/2) so their round caps stay within L; [Arrowhead.TRIANGLE] is filled, its base
 * corners L from the tip, and the shaft stops at its base.
 */
object LineGeometry {
    /** Half the angle between an arrowhead's arms. */
    const val ARROW_HALF_ANGLE = (PI / 7).toFloat()

    /** An arrowhead: [points] is an open polyline to stroke, or a closed polygon to fill when [filled]. */
    data class Head(val points: List<Point>, val filled: Boolean)

    /** The shaft's points: the line's points with the ends pulled back behind triangle heads. */
    fun shaft(line: Line): List<Point> {
        val pts = line.points.toMutableList()
        val n = pts.size
        val back = line.arrowheadLength * cos(ARROW_HALF_ANGLE)
        if (line.startArrow == Arrowhead.TRIANGLE) pts[0] = towards(pts[0], line.points[1], back)
        if (line.endArrow == Arrowhead.TRIANGLE) pts[n - 1] = towards(pts[n - 1], line.points[n - 2], back)
        return pts
    }

    /** The head at the start ([atStart]) or end, or null for none or a zero-length end segment. */
    fun head(line: Line, atStart: Boolean): Head? {
        val n = line.points.size
        val kind = if (atStart) line.startArrow else line.endArrow
        val tip = if (atStart) line.points[0] else line.points[n - 1]
        val from = if (atStart) line.points[1] else line.points[n - 2]
        if (kind == Arrowhead.NONE || tip == from) return null
        val back = atan2(from.y - tip.y, from.x - tip.x)
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
