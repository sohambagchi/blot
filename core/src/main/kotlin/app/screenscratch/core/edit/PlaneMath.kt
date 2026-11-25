package app.screenscratch.core.edit

import kotlin.math.min

/**
 * Plane geometry for hit testing, on flat `x0, y0, x1, y1, …` arrays and plain coordinates.
 * Everything is in canvas units.
 */
object PlaneMath {
    /** Winding number of the closed polygon of the first [count] points of [xy] around ([x], [y]); non-zero means inside. */
    fun winding(xy: FloatArray, count: Int, x: Float, y: Float): Int {
        if (count == 0) return 0
        var w = 0
        var px = xy[(count - 1) * 2]
        var py = xy[(count - 1) * 2 + 1]
        for (i in 0 until count) {
            val qx = xy[i * 2]
            val qy = xy[i * 2 + 1]
            val side = (qx - px) * (y - py) - (x - px) * (qy - py)
            if (py <= y) {
                if (qy > y && side > 0) w++
            } else if (qy <= y && side < 0) {
                w--
            }
            px = qx
            py = qy
        }
        return w
    }

    /** True when ([x], [y]) is inside the closed polygon of the first [count] points of [xy] by the even-odd rule. */
    fun insideEvenOdd(xy: FloatArray, count: Int, x: Float, y: Float): Boolean {
        if (count < 3) return false
        var inside = false
        var j = count - 1
        for (i in 0 until count) {
            val xi = xy[i * 2]
            val yi = xy[i * 2 + 1]
            val xj = xy[j * 2]
            val yj = xy[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * True when the capsule from ([ax], [ay]) to ([bx], [by]) with radius [r] touches the polygon
     * of the first [count] points of [xy], filled with the non-zero winding rule.
     */
    fun capsuleTouchesPolygon(xy: FloatArray, count: Int, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        if (count == 0) return false
        if (count == 1) return segmentPointDistanceSq(ax, ay, bx, by, xy[0], xy[1]) <= r * r
        if (winding(xy, count, ax, ay) != 0) return true
        val r2 = r * r
        var px = xy[(count - 1) * 2]
        var py = xy[(count - 1) * 2 + 1]
        for (i in 0 until count) {
            val qx = xy[i * 2]
            val qy = xy[i * 2 + 1]
            if (segmentSegmentDistanceSq(ax, ay, bx, by, px, py, qx, qy) <= r2) return true
            px = qx
            py = qy
        }
        return false
    }

    /**
     * True when a polyline through the first [count] points of [xy] (closed when [closed]) comes
     * within [r] of the segment from ([ax], [ay]) to ([bx], [by]).
     */
    fun polylineNear(xy: FloatArray, count: Int, closed: Boolean, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        if (count == 0) return false
        val r2 = r * r
        if (count == 1) return segmentPointDistanceSq(ax, ay, bx, by, xy[0], xy[1]) <= r2
        val segments = if (closed) count else count - 1
        for (k in 0 until segments) {
            val j = (k + 1) % count
            if (segmentSegmentDistanceSq(ax, ay, bx, by, xy[k * 2], xy[k * 2 + 1], xy[j * 2], xy[j * 2 + 1]) <= r2) return true
        }
        return false
    }

    fun segmentPointDistanceSq(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx - px
        val cy = ay + t * dy - py
        return cx * cx + cy * cy
    }

    fun segmentSegmentDistanceSq(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Float {
        if (segmentsCross(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return min(
            min(segmentPointDistanceSq(ax, ay, bx, by, cx, cy), segmentPointDistanceSq(ax, ay, bx, by, dx, dy)),
            min(segmentPointDistanceSq(cx, cy, dx, dy, ax, ay), segmentPointDistanceSq(cx, cy, dx, dy, bx, by)),
        )
    }

    private fun segmentsCross(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        // Collinear and touching cases are covered by the endpoint distances.
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun cross(ox: Float, oy: Float, ax: Float, ay: Float, bx: Float, by: Float): Float =
        (ax - ox) * (by - oy) - (ay - oy) * (bx - ox)
}
